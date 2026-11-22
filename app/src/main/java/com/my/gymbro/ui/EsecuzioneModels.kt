package com.my.gymbro.ui

import com.my.gymbro.data.Modalita
import com.my.gymbro.data.SerieEseguita

/** Fasi dell'allenamento in corso. */
enum class Fase { PREPARAZIONE, ESECUZIONE, RECUPERO, ATTESA, COMPLETATO }

/** Segnali per il feedback aptico/sonoro nella UI (la VM resta indipendente dalla View). */
enum class Segnale { TICK, FINE }

/** Una serie del piano, vista in esecuzione. */
data class SerieEsec(
    val numero: Int,
    val durataSec: Int?,
    val ripetizioniPianificate: String?,
    val pesoPianificato: Float?,
    val recuperoSec: Int,
)

/** Un esercizio del piano appiattito per l'esecuzione. [gruppo] ≠ null = parte di un superset. */
data class EsercizioEsec(
    val esercizioId: Long,
    val nome: String,
    val modalita: Modalita,
    val serie: List<SerieEsec>,
    val gruppo: Int? = null,
    val immaginePath: String? = null,   // per mostrare la foto durante l'esecuzione
    val descrizione: String? = null,
    val note: String? = null,           // note di tecnica della scheda
    val gruppoMuscolare: com.my.gymbro.data.GruppoMuscolare? = null,
)

data class StatoEsecuzione(
    val pronta: Boolean = false,
    val nomeScheda: String = "",
    val esercizi: List<EsercizioEsec> = emptyList(),
    val indicePasso: Int = 0,
    val passiTotali: Int = 0,
    val indiceEsercizio: Int = 0,
    val indiceSerie: Int = 0,
    val eserciziFatti: Set<Int> = emptySet(),        // esercizi con tutte le serie completate o saltate
    val serieFatte: Map<Int, Int> = emptyMap(),        // indice esercizio -> serie completate
    val passiFatti: Int = 0,                           // serie completate o saltate sul totale dei passi
    val fase: Fase = Fase.PREPARAZIONE,
    val secondiRimanenti: Int = 0,
    val secondiTotali: Int = 0,
    val secondiTrascorsi: Int = 0,                     // serie a tempo libero: conta in avanti
    val ripInput: String = "",
    val pesoInput: String = "",
    val log: List<SerieEseguita> = emptyList(),
    val nomiEseguiti: Map<Long, String> = emptyMap(),  // nomi del log: restano anche dopo una sostituzione
    val durataTotaleMs: Long = 0,
) {
    val esercizioCorrente: EsercizioEsec? get() = esercizi.getOrNull(indiceEsercizio)
    val serieCorrente: SerieEsec? get() = esercizioCorrente?.serie?.getOrNull(indiceSerie)

    /** Serie a tempo senza durata: si conta in avanti finché non si tocca Completa. */
    val lavoroLibero: Boolean
        get() = esercizioCorrente?.modalita == Modalita.TEMPO && (serieCorrente?.durataSec ?: 0) <= 0
}
