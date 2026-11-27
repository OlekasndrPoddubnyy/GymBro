package com.my.gymbro.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------- Libreria ----------
@Entity(
    tableName = "esercizi",
    indices = [Index(value = ["chiaveSeed"], unique = true)]
)
data class Esercizio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val gruppoMuscolare: GruppoMuscolare,
    val attrezzatura: Attrezzatura,
    val descrizione: String? = null,
    val immaginePath: String? = null,
    val predefinito: Boolean = false,
    /**
     * Per gli esercizi del catalogo: il loro nome nel catalogo, che non cambia se l'utente li
     * rinomina. È la chiave con cui si ritrovano negli aggiornamenti, nelle schede predefinite,
     * nella mappa dei muscoli. null per quelli creati dall'utente.
     */
    val chiaveSeed: String? = null,
)

/** Il nome con cui cercare l'esercizio nelle tabelle del catalogo (muscoli, macchine assistite…). */
val Esercizio.nomeCatalogo: String get() = chiaveSeed ?: nome

// ---------- Schede (template) ----------
@Entity(tableName = "schede")
data class Scheda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val note: String? = null,
    val dataCreazione: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "esercizi_scheda",
    foreignKeys = [
        ForeignKey(entity = Scheda::class, parentColumns = ["id"], childColumns = ["schedaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Esercizio::class, parentColumns = ["id"], childColumns = ["esercizioId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("schedaId"), Index("esercizioId")]
)
data class EsercizioScheda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schedaId: Long,
    val esercizioId: Long,
    val modalita: Modalita,
    val ordine: Int = 0,
    val note: String? = null,
    val gruppoSuperset: Int? = null   // esercizi con lo stesso gruppo (≠ null) sono un superset
)

@Entity(
    tableName = "serie_pianificate",
    foreignKeys = [
        ForeignKey(entity = EsercizioScheda::class, parentColumns = ["id"], childColumns = ["esercizioSchedaId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("esercizioSchedaId")]
)
data class SeriePianificata(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val esercizioSchedaId: Long,
    val numero: Int,
    val durataSec: Int? = null,        // modalità Tempo
    val ripetizioni: String? = null,   // modalità Ripetizioni (es. "8-12")
    val pesoKg: Float? = null,         // modalità Ripetizioni
    val recuperoSec: Int = 0           // recupero dopo la serie
)

// ---------- Esecuzione / log ----------
@Entity(
    tableName = "sessioni",
    foreignKeys = [
        ForeignKey(entity = Scheda::class, parentColumns = ["id"], childColumns = ["schedaId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("schedaId")]
)
data class Sessione(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schedaId: Long? = null,
    val nomeScheda: String,
    val dataInizio: Long = System.currentTimeMillis(),
    val dataFine: Long? = null
)

@Entity(
    tableName = "serie_eseguite",
    foreignKeys = [
        ForeignKey(entity = Sessione::class, parentColumns = ["id"], childColumns = ["sessioneId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Esercizio::class, parentColumns = ["id"], childColumns = ["esercizioId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("sessioneId"), Index("esercizioId")]
)
data class SerieEseguita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessioneId: Long,
    val esercizioId: Long,
    val numero: Int,
    val ripetizioni: Int? = null,
    val pesoKg: Float? = null,
    val durataSec: Int? = null,
    val completata: Boolean = false
)