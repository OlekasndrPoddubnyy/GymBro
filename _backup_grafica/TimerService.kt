package com.my.gymbro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.my.gymbro.ui.FaseTimer
import com.my.gymbro.ui.StatoTimer
import com.my.gymbro.ui.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service che tiene vivo il processo durante il Timer a serie (così il conteggio
 * prosegue con schermo bloccato / app in background) e ne riflette lo stato in notifica.
 * Lo stato vero è in [TimerEngine]; il service osserva e si autospegne quando si torna a CONFIG.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var osservaJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SKIP -> TimerEngine.skip()
            ACTION_AVANTI -> TimerEngine.avanti()
            ACTION_STOP -> TimerEngine.stop()
        }

        creaCanale()

        if (!TimerEngine.attivo) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIF_ID,
            costruisciNotifica(TimerEngine.stato.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        avviaOsservazione()
        return START_STICKY
    }

    private fun avviaOsservazione() {
        if (osservaJob != null) return
        osservaJob = scope.launch {
            TimerEngine.stato.collect { s ->
                if (s.fase == FaseTimer.CONFIG) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notifManager().notify(NOTIF_ID, costruisciNotifica(s))
                }
            }
        }
    }

    private fun costruisciNotifica(s: StatoTimer): Notification {
        val fase = when (s.fase) {
            FaseTimer.SERIE -> "Lavoro"
            FaseTimer.RECUPERO -> "Recupero"
            FaseTimer.ATTESA -> "Pronto?"
            FaseTimer.CONFIG -> "Timer"
        }
        val tempo = if (s.fase == FaseTimer.ATTESA) "—" else "%d:%02d".format(s.secondiRimanenti / 60, s.secondiRimanenti % 60)
        val testo = "$fase · $tempo · Serie ${s.serieCorrente + 1}/${s.serieTotali}"

        val apri = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CANALE_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("GymBro Timer")
            .setContentText(testo)
            .setContentIntent(apri)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (s.fase == FaseTimer.ATTESA) {
            builder.addAction(0, "Avanti", azione(ACTION_AVANTI))
        } else {
            builder.addAction(0, "Salta", azione(ACTION_SKIP))
        }
        builder.addAction(0, "Stop", azione(ACTION_STOP))
        return builder.build()
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
            description = "Timer a serie in corso"
            setShowBadge(false)
        }
        notifManager().createNotificationChannel(canale)
    }

    override fun onDestroy() {
        osservaJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val CANALE_ID = "timer"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.my.gymbro.action.TIMER_START"
        const val ACTION_SKIP = "com.my.gymbro.action.TIMER_SKIP"
        const val ACTION_AVANTI = "com.my.gymbro.action.TIMER_AVANTI"
        const val ACTION_STOP = "com.my.gymbro.action.TIMER_STOP"

        fun start(context: Context) {
            val i = Intent(context, TimerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
