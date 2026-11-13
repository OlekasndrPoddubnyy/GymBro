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
)
