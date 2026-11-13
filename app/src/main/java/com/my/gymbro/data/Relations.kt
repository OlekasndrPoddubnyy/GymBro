package com.my.gymbro.data

import androidx.room.Embedded
import androidx.room.Relation

// Un esercizio dentro una scheda: dati di libreria + serie pianificate
data class EsercizioSchedaCompleto(
    @Embedded val esercizioScheda: EsercizioScheda,
    @Relation(parentColumn = "esercizioId", entityColumn = "id")
    val esercizio: Esercizio,
    @Relation(parentColumn = "id", entityColumn = "esercizioSchedaId")
    val serie: List<SeriePianificata>
)

// Una scheda con tutti i suoi esercizi
data class SchedaConEsercizi(
    @Embedded val scheda: Scheda,
    @Relation(
        entity = EsercizioScheda::class,
        parentColumn = "id",
        entityColumn = "schedaId"
    )
    val esercizi: List<EsercizioSchedaCompleto>
)