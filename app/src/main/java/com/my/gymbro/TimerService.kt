package com.my.gymbro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.util.Log
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.my.gymbro.ui.AllenamentoAttivo
import com.my.gymbro.ui.FaseTimer
import com.my.gymbro.ui.Segnale
import com.my.gymbro.ui.StatoTimer
import com.my.gymbro.ui.TimerEngine
import com.my.gymbro.ui.descriviTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Foreground service che tiene vivo il processo mentre è attivo il Timer a serie oppure un
 * allenamento di una scheda: il conteggio prosegue con schermo bloccato, app ridotta o su
 * un'altra schermata. Tiene un wake lock parziale (la CPU resta sveglia), riproduce beep e
 * vibrazione e mostra lo stato in notifica. Si spegne da solo quando non c'è più niente di attivo.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var osservaJob: Job? = null
    private var segnaliJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tono: ToneGenerator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SKIP -> TimerEngine.skip()
            ACTION_FINE_SERIE -> TimerEngine.fineSerie()
            ACTION_AVANTI -> TimerEngine.avanti()
            ACTION_STOP -> TimerEngine.stop()
        }

        creaCanale()
        // Va sempre chiamato dopo startForegroundService, anche se poi ci si spegne subito.
        startForeground(NOTIF_ID, costruisciNotifica(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        if (!qualcosaAttivo()) {
            spegni()
            return START_NOT_STICKY
        }

        tieniSveglio()
        avviaOsservazione()
        return START_STICKY
    }

    private fun qualcosaAttivo(): Boolean = TimerEngine.attivo || AllenamentoAttivo.info.value != null

    private fun avviaOsservazione() {
        if (osservaJob == null) {
            osservaJob = scope.launch {
                combine(TimerEngine.stato, AllenamentoAttivo.info) { _, _ -> Unit }.collect {
                    if (qualcosaAttivo()) notifManager().notify(NOTIF_ID, costruisciNotifica())
                    else spegni()
                }
            }
        }
        if (segnaliJob == null) {
            segnaliJob = scope.launch {
                merge(TimerEngine.segnali, AllenamentoAttivo.segnali).collect { feedback(it) }
            }
        }
    }

    // ---------- beep + vibrazione (funzionano anche con l'app in background) ----------
    // Canale sveglia: i beep si sentono anche con il volume dei media a zero.

    private fun feedback(s: Segnale) {
        val vibratore = runCatching {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        }.getOrNull()
        when (s) {
            Segnale.TICK -> {
                beep(ToneGenerator.TONE_PROP_BEEP, 120)
                vibratore?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            Segnale.FINE -> {
                beep(ToneGenerator.TONE_PROP_BEEP2, 250)
                vibratore?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private var streamTono = -1

    /**
     * Suona sul canale sveglia; se il volume sveglia è a zero ripiega sui media, così il beep non
     * sparisce in silenzio. Il ToneGenerator si può invalidare (es. riavvio del server audio):
     * se il tono non parte lo si ricrea e si riprova una volta.
     */
    private fun beep(tipo: Int, durataMs: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val stream = if (audio != null && audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0)
            AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
        if (stream != streamTono) {
            tono?.release()
            tono = null
        }
        repeat(2) {
            if (tono == null) {
                tono = runCatching { ToneGenerator(stream, 100) }.getOrNull()
                streamTono = stream
            }
            val partito = runCatching { tono?.startTone(tipo, durataMs) == true }.getOrDefault(false)
            if (partito) return
            Log.w("GymBro", "Beep non partito (stream $stream): ricreo il ToneGenerator")
            tono?.release()
            tono = null
        }
    }

    // ---------- wake lock ----------

    private fun tieniSveglio() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymBro:timer").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L) // sicurezza: massimo 4 ore
        }
    }

    private fun rilascia() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun spegni() {
        rilascia()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- notifica ----------

    private fun costruisciNotifica(): Notification {
        val apri = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CANALE_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(apri)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val s = TimerEngine.stato.value
        val allenamento = AllenamentoAttivo.info.value
        if (s.fase != FaseTimer.CONFIG) {
            builder.setContentTitle("GymBro Timer").setContentText(descriviTimer(s))
            aggiungiAzioniTimer(builder, s)
        } else if (allenamento != null) {
            builder.setContentTitle("Allenamento · ${allenamento.nomeScheda}").setContentText(allenamento.testo)
        } else {
            builder.setContentTitle("GymBro").setContentText("")
        }
        return builder.build()
    }

    private fun aggiungiAzioniTimer(builder: NotificationCompat.Builder, s: StatoTimer) {
        when {
            s.fase == FaseTimer.ATTESA -> builder.addAction(0, "Avanti", azione(ACTION_AVANTI))
            s.fase == FaseTimer.SERIE && s.lavoroLibero ->
                builder.addAction(0, if (s.ultimaSerie) "Fine" else "Recupero", azione(ACTION_FINE_SERIE))
            else -> builder.addAction(0, "Salta", azione(ACTION_SKIP))
        }
        builder.addAction(0, "Stop", azione(ACTION_STOP))
    }

    private fun azione(action: String): PendingIntent {
        val i = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getForegroundService(
            this, action.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun creaCanale() {
        val canale = NotificationChannel(CANALE_ID, "Timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Timer e allenamento in corso"
            setShowBadge(false)
        }
        notifManager().createNotificationChannel(canale)
    }

    override fun onDestroy() {
        osservaJob?.cancel()
        segnaliJob?.cancel()
        scope.cancel()
        rilascia()
        tono?.release()
        tono = null
    }

    companion object {
        private const val CANALE_ID = "timer"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.my.gymbro.action.TIMER_START"
        const val ACTION_SKIP = "com.my.gymbro.action.TIMER_SKIP"
        const val ACTION_AVANTI = "com.my.gymbro.action.TIMER_AVANTI"
        const val ACTION_FINE_SERIE = "com.my.gymbro.action.TIMER_FINE_SERIE"
        const val ACTION_STOP = "com.my.gymbro.action.TIMER_STOP"

        fun start(context: Context) {
            val i = Intent(context, TimerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
