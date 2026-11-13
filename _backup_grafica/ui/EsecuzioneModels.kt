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
)

data class StatoEsecuzione(
    val pronta: Boolean = false,
    val nomeScheda: String = "",
    val esercizi: List<EsercizioEsec> = emptyList(),
    val indicePasso: Int = 0,
    val passiTotali: Int = 0,
    val indiceEsercizio: Int = 0,
    val indiceSerie: Int = 0,
    val eserciziFatti: Set<Int> = emptySet(),
    val fase: Fase = Fase.PREPARAZIONE,
    val secondiRimanenti: Int = 0,
    val secondiTotali: Int = 0,
    val ripInput: String = "",
    val pesoInput: String = "",
    val log: List<SerieEseguita> = emptyList(),
    val durataTotaleMs: Long = 0,
) {
    val esercizioCorrente: EsercizioEsec? get() = esercizi.getOrNull(indiceEsercizio)
    val serieCorrente: SerieEsec? get() = esercizioCorrente?.serie?.getOrNull(indiceSerie)
}
