package com.my.gymbro.data

/**
 * Riga di lettura per i Report: una serie eseguita arricchita con la data della sessione
 * e i dati dell'esercizio (nome + gruppo muscolare). Risultato di una JOIN.
 */
data class RigaReport(
    val sessioneId: Long,
    val dataInizio: Long,
    val esercizioId: Long,
    val nomeEsercizio: String,
    val gruppoMuscolare: GruppoMuscolare,
    val numero: Int,
    val ripetizioni: Int?,
    val pesoKg: Float?,
    val durataSec: Int?,
    /** [Esercizio.chiaveSeed] dell'esercizio. */
    val chiaveSeed: String? = null,
    /** Id della [SerieEseguita], per correggerla o eliminarla dallo storico. */
    val serieId: Long = 0,
) {
    /** Il nome per le tabelle del catalogo (muscoli, macchine assistite), anche se rinominato. */
    val nomeCatalogo: String get() = chiaveSeed ?: nomeEsercizio
}
