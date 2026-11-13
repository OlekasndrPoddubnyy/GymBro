package com.my.gymbro.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stato dell'allenamento in corso visibile al resto dell'app (barra "in corso", notifica del
 * [com.my.gymbro.TimerService]) e segnali per beep/vibrazione, che il servizio riproduce anche
 * con l'app in background.
 */
object AllenamentoAttivo {

    data class Info(val nomeScheda: String, val testo: String)

    private val _info = MutableStateFlow<Info?>(null)
    /** null = nessun allenamento in corso. */
    val info: StateFlow<Info?> = _info.asStateFlow()

    private val _segnali = MutableSharedFlow<Segnale>(extraBufferCapacity = 8)
    val segnali: SharedFlow<Segnale> = _segnali.asSharedFlow()

    fun aggiorna(info: Info?) {
        _info.value = info
    }

    fun segnala(s: Segnale) {
        _segnali.tryEmit(s)
    }
}

/** Testo breve dello stato di esecuzione, per barra e notifica (es. "Riposo · 0:42"). */
fun descriviEsecuzione(s: StatoEsecuzione): String {
    val tempo = "%d:%02d".format(s.secondiRimanenti / 60, s.secondiRimanenti % 60)
    val es = s.esercizioCorrente
    return when (s.fase) {
        Fase.PREPARAZIONE -> "Preparati · $tempo"
        Fase.ESECUZIONE -> {
            val nome = es?.nome ?: "Esercizio"
            val serie = es?.let { " · serie ${s.indiceSerie + 1}/${it.serie.size}" }.orEmpty()
            if (es?.modalita == com.my.gymbro.data.Modalita.TEMPO) "$nome · $tempo" else "$nome$serie"
        }
        Fase.RECUPERO -> "Riposo · $tempo"
        Fase.ATTESA -> "Prossimo: ${es?.nome ?: "esercizio"}"
        Fase.COMPLETATO -> "Completato"
    }
}

/** Testo breve dello stato del timer libero (es. "Lavoro · 0:32 · serie 2/5"). */
fun descriviTimer(s: StatoTimer): String {
    val tempo = if (s.fase == FaseTimer.SERIE && s.lavoroLibero)
        "+%d:%02d".format(s.secondiTrascorsi / 60, s.secondiTrascorsi % 60)
    else "%d:%02d".format(s.secondiRimanenti / 60, s.secondiRimanenti % 60)
    val fase = when (s.fase) {
        FaseTimer.PREPARAZIONE -> "Si parte"
        FaseTimer.SERIE -> "Lavoro"
        FaseTimer.RECUPERO -> "Riposo"
        FaseTimer.ATTESA -> return "In attesa · serie ${s.serieCorrente + 2}/${s.serieTotali}"
        FaseTimer.CONFIG -> return "Timer"
    }
    return "$fase · $tempo · serie ${s.serieCorrente + 1}/${s.serieTotali}"
}
