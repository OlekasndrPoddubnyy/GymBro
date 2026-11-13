package com.my.gymbro.ui

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

enum class FaseTimer { CONFIG, SERIE, RECUPERO, ATTESA }

data class StatoTimer(
    val serieTotali: Int = 4,
    val durataSec: Int = 45,
    val recuperoSec: Int = 30,
    val fase: FaseTimer = FaseTimer.CONFIG,
    val serieCorrente: Int = 0,
    val secondiRimanenti: Int = 0,
)

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
    fun setDurata(s: Int) = aggiornaConfig { it.copy(durataSec = s.coerceIn(1, 3600)) }
    fun setRecupero(s: Int) = aggiornaConfig { it.copy(recuperoSec = s.coerceIn(0, 3600)) }

    private fun aggiornaConfig(blocco: (StatoTimer) -> StatoTimer) {
        if (_stato.value.fase == FaseTimer.CONFIG) _stato.update(blocco)
    }

    fun avvia() {
        if (_stato.value.fase != FaseTimer.CONFIG) return
        _stato.update { it.copy(fase = FaseTimer.SERIE, serieCorrente = 0) }
        countdown(_stato.value.durataSec) { serieFinita() }
    }

    fun skip() {
        when (_stato.value.fase) {
            FaseTimer.SERIE, FaseTimer.RECUPERO, FaseTimer.ATTESA -> {
                timerJob?.cancel()
                if (_stato.value.serieCorrente >= _stato.value.serieTotali - 1) finisci() else prossima()
            }
            else -> {}
        }
    }

    fun avanti() {
        if (_stato.value.fase == FaseTimer.ATTESA) prossima()
    }

    fun stop() {
        timerJob?.cancel()
        _stato.update { it.copy(fase = FaseTimer.CONFIG, serieCorrente = 0, secondiRimanenti = 0) }
    }

    private fun serieFinita() {
        val s = _stato.value
        when {
            s.serieCorrente >= s.serieTotali - 1 -> finisci()
            s.recuperoSec > 0 -> {
                _stato.update { it.copy(fase = FaseTimer.RECUPERO) }
                countdown(s.recuperoSec) { prossima() }
            }
            else -> _stato.update { it.copy(fase = FaseTimer.ATTESA) }
        }
    }

    private fun prossima() {
        _stato.update { it.copy(fase = FaseTimer.SERIE, serieCorrente = it.serieCorrente + 1) }
        countdown(_stato.value.durataSec) { serieFinita() }
    }

    private fun finisci() {
        timerJob?.cancel()
        _stato.update { it.copy(fase = FaseTimer.CONFIG, serieCorrente = 0, secondiRimanenti = 0) }
    }

    private fun countdown(secondi: Int, alFine: () -> Unit) {
        timerJob?.cancel()
        _stato.update { it.copy(secondiRimanenti = secondi) }
        if (secondi <= 0) {
            alFine()
            return
        }
        timerJob = scope.launch {
            var rim = secondi
            while (rim > 0) {
                delay(1000)
                rim--
                _stato.update { it.copy(secondiRimanenti = rim) }
                if (rim in 1..3) _segnali.tryEmit(Segnale.TICK)
            }
            _segnali.tryEmit(Segnale.FINE)
            alFine()
        }
    }
}
