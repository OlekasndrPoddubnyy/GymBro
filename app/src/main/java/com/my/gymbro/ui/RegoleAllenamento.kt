package com.my.gymbro.ui

import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.EsercizioSchedaCompleto
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.SerieEseguita

// Regole dell'allenamento senza dipendenze da Android: si provano con i test JVM.

/** Secondi stimati per una serie senza durata pianificata (ripetizioni o tempo libero). */
private const val SEC_SERIE_STIMATA = 40

/** Secondi stimati per il cambio tra un esercizio (o superset) e il successivo. */
private const val SEC_CAMBIO_ESERCIZIO = 45

/** Entro questo intervallo un secondo tocco su Completa/Salta è considerato involontario. */
private const val MS_DOPPIO_TOCCO = 700L

internal fun EsercizioSchedaCompleto.toEsecuzione() = EsercizioEsec(
    esercizioId = esercizio.id,
    nome = esercizio.nome,
    modalita = esercizioScheda.modalita,
    gruppo = esercizioScheda.gruppoSuperset,
    immaginePath = esercizio.immaginePath,
    descrizione = esercizio.descrizione,
    note = esercizioScheda.note,
    gruppoMuscolare = esercizio.gruppoMuscolare,
    serie = serie.sortedBy { it.numero }.map { sp ->
        SerieEsec(sp.numero, sp.durataSec, sp.ripetizioni, sp.pesoKg, sp.recuperoSec)
    }.ifEmpty { listOf(SerieEsec(1, null, null, null, 60)) }
)

/**
 * Esercizio che prende il posto di questo durante l'allenamento: restano serie, modalità e superset,
 * ma non peso e note, che valevano per quello sostituito.
 */
internal fun EsercizioEsec.sostituitoCon(nuovo: Esercizio) = copy(
    esercizioId = nuovo.id,
    nome = nuovo.nome,
    immaginePath = nuovo.immaginePath,
    descrizione = nuovo.descrizione,
    note = null,
    gruppoMuscolare = nuovo.gruppoMuscolare,
    // L'obiettivo di Gym Buddy era dell'esercizio sostituito.
    obiettivo = null,
    calibrazione = false,
    serie = serie.map { it.copy(pesoPianificato = null) },
)

/**
 * Ripetizioni e peso proposti per [serie]: quelli della serie con lo stesso numero nell'ultima
 * sessione, altrimenti quelli pianificati.
 *
 * @param ultime serie dell'ultima sessione, in ordine di numero e poi di esecuzione.
 * @param occorrenza quante volte lo stesso esercizio compare prima di questo nella scheda.
 */
internal fun valoriIniziali(serie: SerieEsec, ultime: List<SerieEseguita>, occorrenza: Int): Pair<String, String> {
    val ultima = ultime.filter { it.numero == serie.numero }.getOrNull(occorrenza)
    val rip = ultima?.ripetizioni?.toString()
        ?: serie.ripetizioniPianificate?.let(::ultimoNumero).orEmpty()
    val peso = (ultima?.pesoKg ?: serie.pesoPianificato)?.let(::formattaKgRuota).orEmpty()
    return rip to peso
}

/** Secondi davvero svolti: parte del conto alla rovescia già passata, o cronometro se non c'è durata. */
internal fun durataEseguita(pianificata: Int?, rimanenti: Int, trascorsi: Int): Int =
    if (pianificata != null && pianificata > 0) (pianificata - rimanenti).coerceIn(0, pianificata)
    else trascorsi

/** Lista ordinata che contiene di sicuro [valore]: una ruota deve poter mostrare il valore che salva. */
internal fun <T : Comparable<T>> List<T>.conValore(valore: T): List<T> =
    if (valore in this) this else (this + valore).sorted()

/** Ultimo gruppo di cifre in una stringa tipo "8-12" → "12". */
private fun ultimoNumero(s: String): String =
    Regex("\\d+").findAll(s).lastOrNull()?.value ?: ""

/**
 * Scarta il secondo tocco ravvicinato. Serve quando la serie successiva parte subito (superset,
 * recupero 0): lo stesso bottone resta sotto il dito e registrerebbe una serie non fatta.
 */
internal class AntiDoppioTocco(private val ora: () -> Long) {
    private var ultimo: Long? = null

    fun accetta(): Boolean {
        val u = ultimo
        if (u != null && ora() - u < MS_DOPPIO_TOCCO) return false
        segna()
        return true
    }

    /** Anche un passaggio automatico di serie apre la finestra di protezione. */
    fun segna() {
        ultimo = ora()
    }

    fun azzera() {
        ultimo = null
    }
}

/** Stima dei tempi di una scheda, in secondi. */
internal data class StimaTempi(val lavoroSec: Int, val recuperoSec: Int, val serie: Int) {
    val totaleSec: Int get() = lavoroSec + recuperoSec
}

/**
 * Segue gli stessi passi del motore: recupero tra le serie (nei superset solo a fine giro) e, al
 * cambio di esercizio, l'attesa di "Inizia" al posto del recupero.
 */
internal fun stimaTempi(esercizi: List<EsercizioEsec>): StimaTempi {
    val passi = costruisciPassi(esercizi)
    val lavoro = passi.sumOf { p ->
        val es = esercizi[p.idxEsercizio]
        val durata = es.serie[p.idxSerie].durataSec ?: 0
        if (es.modalita == Modalita.TEMPO && durata > 0) durata else SEC_SERIE_STIMATA
    }
    val recupero = passi.zipWithNext().sumOf { (corrente, prossimo) ->
        if (prossimo.attesaPrima) SEC_CAMBIO_ESERCIZIO else corrente.recuperoDopo
    }
    return StimaTempi(lavoro, recupero, passi.size)
}
