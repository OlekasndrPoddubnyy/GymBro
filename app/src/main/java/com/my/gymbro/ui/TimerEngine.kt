package com.my.gymbro.ui

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Fasi del timer a serie.
 * PREPARAZIONE = conto alla rovescia di 3 s prima della prima serie.
 * SERIE con durata 0 = lavoro libero: il tempo scorre in avanti finché l'utente non avvia il recupero.
 * ATTESA = recupero 0: si riparte a mano con "Avanti".
 */
enum class FaseTimer { CONFIG, PREPARAZIONE, SERIE, RECUPERO, ATTESA }

data class StatoTimer(
    val serieTotali: Int = 4,
    val durataSec: Int = 45,          // 0 = lavoro libero (fine serie manuale)
    val recuperoSec: Int = 30,        // 0 = avvio manuale della serie successiva
    val fase: FaseTimer = FaseTimer.CONFIG,
    val serieCorrente: Int = 0,
    val secondiRimanenti: Int = 0,
    val secondiTrascorsi: Int = 0,    // usato nel lavoro libero (conteggio in avanti)
) {
    val lavoroLibero: Boolean get() = durataSec == 0
    val ultimaSerie: Boolean get() = serieCorrente >= serieTotali - 1
}

/** Secondi del conto alla rovescia iniziale. */
const val SEC_PREPARAZIONE_TIMER = 3

/**
 * Motore del Timer a serie (spec 4.1) come singleton di processo: il conteggio gira in uno scope
 * applicativo, quindi continua anche se l'Activity viene distrutta. Il [com.my.gymbro.TimerService]
 * lo tiene vivo in background e ne mostra lo stato in notifica.
 */
object TimerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _stato = MutableStateFlow(StatoTimer())
    val stato: StateFlow<StatoTimer> = _stato.asStateFlow()

    private val _segnali = MutableSharedFlow<Segnale>(extraBufferCapacity = 8)
    val segnali: SharedFlow<Segnale> = _segnali.asSharedFlow()

    private var timerJob: Job? = null

    val attivo: Boolean get() = _stato.value.fase != FaseTimer.CONFIG

    fun setSerie(n: Int) = aggiornaConfig { it.copy(serieTotali = n.coerceIn(1, 99)) }
    fun setDurata(s: Int) = aggiornaConfig { it.copy(durataSec = s.coerceIn(0, 3600)) }
    fun setRecupero(s: Int) = aggiornaConfig { it.copy(recuperoSec = s.coerceIn(0, 3600)) }

    private fun aggiornaConfig(blocco: (StatoTimer) -> StatoTimer) {
        if (_stato.value.fase == FaseTimer.CONFIG) _stato.update(blocco)
    }

    /** Avvio: 3 s di preparazione, poi la prima serie. */
    fun avvia() {
        if (_stato.value.fase != FaseTimer.CONFIG) return
        _stato.update { it.copy(fase = FaseTimer.PREPARAZIONE, serieCorrente = 0, secondiTrascorsi = 0) }
        _segnali.tryEmit(Segnale.TICK)
        countdown(SEC_PREPARAZIONE_TIMER) { iniziaSerie() }
    }

    /** Fine della serie a lavoro libero: parte il recupero (o si finisce, se era l'ultima). */
    fun fineSerie() {
        if (_stato.value.fase != FaseTimer.SERIE) return
        timerJob?.cancel()
        _segnali.tryEmit(Segnale.FINE)
        serieFinita()
    }

    fun skip() {
        when (_stato.value.fase) {
            FaseTimer.PREPARAZIONE -> {
                timerJob?.cancel()
                iniziaSerie()
            }
            FaseTimer.SERIE, FaseTimer.RECUPERO, FaseTimer.ATTESA -> {
                timerJob?.cancel()
                if (_stato.value.ultimaSerie) finisci() else prossima()
            }
            else -> {}
        }
    }

    fun avanti() {
        if (_stato.value.fase == FaseTimer.ATTESA) prossima()
    }

    fun stop() {
        timerJob?.cancel()
        _stato.update { it.copy(fase = FaseTimer.CONFIG, serieCorrente = 0, secondiRimanenti = 0, secondiTrascorsi = 0) }
    }

    private fun iniziaSerie() {
        _stato.update { it.copy(fase = FaseTimer.SERIE, secondiTrascorsi = 0) }
        val s = _stato.value
        if (s.lavoroLibero) cronometro() else countdown(s.durataSec) { serieFinita() }
    }

    private fun serieFinita() {
        val s = _stato.value
        when {
            s.ultimaSerie -> finisci()
            s.recuperoSec > 0 -> {
                _stato.update { it.copy(fase = FaseTimer.RECUPERO) }
                countdown(s.recuperoSec) { prossima() }
            }
            else -> _stato.update { it.copy(fase = FaseTimer.ATTESA) }
        }
    }

    private fun prossima() {
        _stato.update { it.copy(serieCorrente = it.serieCorrente + 1) }
        iniziaSerie()
    }

    private fun finisci() {
        timerJob?.cancel()
        _stato.update { it.copy(fase = FaseTimer.CONFIG, serieCorrente = 0, secondiRimanenti = 0, secondiTrascorsi = 0) }
    }

    /**
     * Conteggio in avanti per il lavoro libero; si ferma con [fineSerie], [skip] o [stop].
     * Il tempo è calcolato sull'orologio di sistema, quindi resta esatto anche in background.
     */
    private fun cronometro() {
        timerJob?.cancel()
        _stato.update { it.copy(secondiRimanenti = 0, secondiTrascorsi = 0) }
        val inizio = SystemClock.elapsedRealtime()
        timerJob = scope.launch {
            var ultimo = 0
            while (true) {
                delay(250)
                val trascorsi = ((SystemClock.elapsedRealtime() - inizio) / 1000).toInt()
                if (trascorsi != ultimo) {
                    ultimo = trascorsi
                    _stato.update { it.copy(secondiTrascorsi = trascorsi) }
                }
            }
        }
    }

    /**
     * Conto alla rovescia basato sull'orologio di sistema (non sul numero di attese): se l'app viene
     * rallentata in background il tempo resta giusto e la fase finisce all'istante corretto.
     */
    private fun countdown(secondi: Int, alFine: () -> Unit) {
        timerJob?.cancel()
        _stato.update { it.copy(secondiRimanenti = secondi) }
        if (secondi <= 0) {
            alFine()
            return
        }
        val fine = SystemClock.elapsedRealtime() + secondi * 1000L
        timerJob = scope.launch {
            var ultimo = secondi
            while (true) {
                delay(200)
                val rim = ((fine - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                if (rim != ultimo) {
                    ultimo = rim
                    _stato.update { it.copy(secondiRimanenti = rim) }
                    if (rim in 1..3) _segnali.tryEmit(Segnale.TICK)
                }
                if (rim <= 0) break
            }
            _segnali.tryEmit(Segnale.FINE)
            alFine()
        }
    }
}
