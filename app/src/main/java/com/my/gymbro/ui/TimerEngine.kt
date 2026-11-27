package com.my.gymbro.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 *
 * Tutto gira sul main thread, come i comandi della UI e delle azioni in notifica: lo scadere del
 * tempo e un "Salta" premuto nello stesso istante non possono sovrapporsi e far saltare due serie.
 */
object TimerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + gestoreErrori("timer"))

    private val _stato = MutableStateFlow(StatoTimer())
    val stato: StateFlow<StatoTimer> = _stato.asStateFlow()

    private val _segnali = MutableSharedFlow<Segnale>(extraBufferCapacity = 8)
    val segnali: SharedFlow<Segnale> = _segnali.asSharedFlow()

    private val conto = ContoAllaRovescia(scope) { _segnali.tryEmit(it) }

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
        conto.ferma()
        _segnali.tryEmit(Segnale.FINE)
        serieFinita()
    }

    fun skip() {
        when (_stato.value.fase) {
            FaseTimer.PREPARAZIONE -> {
                conto.ferma()
                iniziaSerie()
            }
            FaseTimer.SERIE, FaseTimer.RECUPERO, FaseTimer.ATTESA -> {
                conto.ferma()
                if (_stato.value.ultimaSerie) finisci() else prossima()
            }
            else -> {}
        }
    }

    fun avanti() {
        if (_stato.value.fase == FaseTimer.ATTESA) prossima()
    }

    fun stop() = finisci()

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
        conto.ferma()
        _stato.update { it.copy(fase = FaseTimer.CONFIG, serieCorrente = 0, secondiRimanenti = 0, secondiTrascorsi = 0) }
    }

    /** Conteggio in avanti per il lavoro libero; si ferma con [fineSerie], [skip] o [stop]. */
    private fun cronometro() {
        _stato.update { it.copy(secondiRimanenti = 0, secondiTrascorsi = 0) }
        conto.avviaInAvanti { t -> _stato.update { it.copy(secondiTrascorsi = t) } }
    }

    private fun countdown(secondi: Int, alFine: () -> Unit) {
        _stato.update { it.copy(secondiRimanenti = secondi) }
        conto.avvia(secondi, onSecondo = { rim -> _stato.update { it.copy(secondiRimanenti = rim) } }, onFine = alFine)
    }
}
