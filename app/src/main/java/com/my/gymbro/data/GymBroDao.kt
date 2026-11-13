package com.my.gymbro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GymBroDao {

    // ---------- Esercizi (libreria) ----------
    @Query("SELECT * FROM esercizi ORDER BY nome")
    fun getEsercizi(): Flow<List<Esercizio>>

    @Query("SELECT * FROM esercizi")
    suspend fun tuttiEsercizi(): List<Esercizio>

    @Insert suspend fun insertEsercizio(esercizio: Esercizio): Long
    @Insert suspend fun insertEsercizi(esercizi: List<Esercizio>)
    @Update suspend fun updateEsercizio(esercizio: Esercizio)
    @Delete suspend fun deleteEsercizio(esercizio: Esercizio)

    // ---------- Schede ----------
    @Query("SELECT * FROM schede ORDER BY dataCreazione DESC")
    fun getSchede(): Flow<List<Scheda>>

    @Transaction
    @Query("SELECT * FROM schede WHERE id = :schedaId")
    fun getSchedaConEsercizi(schedaId: Long): Flow<SchedaConEsercizi?>

    // Letture una-tantum (non Flow): usabili dentro una transazione.
    @Query("SELECT * FROM schede")
    suspend fun tutteSchede(): List<Scheda>

    @Transaction
    @Query("SELECT * FROM schede WHERE id = :schedaId")
    suspend fun schedaConEserciziOra(schedaId: Long): SchedaConEsercizi?

    @Insert suspend fun insertScheda(scheda: Scheda): Long
    @Update suspend fun updateScheda(scheda: Scheda)
    @Delete suspend fun deleteScheda(scheda: Scheda)

    // ---------- Esercizi in scheda + serie ----------
    @Insert suspend fun insertEsercizioScheda(es: EsercizioScheda): Long

    @Insert suspend fun insertSerie(serie: List<SeriePianificata>)

    @Query("DELETE FROM esercizi_scheda WHERE schedaId = :schedaId")
    suspend fun deleteEserciziDiScheda(schedaId: Long)

    /**
     * Salva una scheda con i suoi esercizi e le relative serie in un'unica transazione.
     * Per le schede esistenti rimpiazza l'intero contenuto (cancella gli esercizi-in-scheda —
     * CASCADE rimuove le serie — e reinserisce). La riga Scheda è conservata per non azzerare
     * gli eventuali riferimenti delle Sessioni (FK SET_NULL). [ordine] è derivato dalla posizione.
     */
    @Transaction
    suspend fun salvaSchedaConEsercizi(
        scheda: Scheda,
        esercizi: List<Pair<EsercizioScheda, List<SeriePianificata>>>
    ): Long {
        val schedaId = if (scheda.id == 0L) insertScheda(scheda)
        else { updateScheda(scheda); scheda.id }
        deleteEserciziDiScheda(schedaId)
        esercizi.forEachIndexed { idx, (es, serie) ->
            val esId = insertEsercizioScheda(es.copy(id = 0, schedaId = schedaId, ordine = idx))
            insertSerie(serie.map { it.copy(id = 0, esercizioSchedaId = esId) })
        }
        return schedaId
    }

    // ---------- Sessioni / serie eseguite ----------
    @Insert suspend fun insertSessione(sessione: Sessione): Long
    @Update suspend fun updateSessione(sessione: Sessione)
    @Insert suspend fun insertSerieEseguita(serie: SerieEseguita): Long

    /** Cancella la sessione [id] solo se non ha nessuna serie registrata. */
    @Query(
        """
        DELETE FROM sessioni WHERE id = :id
          AND NOT EXISTS (SELECT 1 FROM serie_eseguite WHERE sessioneId = :id)
        """
    )
    suspend fun deleteSessioneVuota(id: Long)

    @Query("SELECT * FROM sessioni ORDER BY dataInizio DESC")
    fun getSessioni(): Flow<List<Sessione>>

    /**
     * Serie realmente eseguite per [esercizioId] nell'ultima sessione che lo conteneva
     * (esclusa [sessioneCorrente]), ordinate per numero. Alimenta la pre-compilazione
     * (sovraccarico progressivo).
     */
    @Query(
        """
        SELECT se.* FROM serie_eseguite se
        WHERE se.esercizioId = :esercizioId
          AND se.sessioneId = (
            SELECT s2.id FROM sessioni s2
            JOIN serie_eseguite se2 ON se2.sessioneId = s2.id
            WHERE se2.esercizioId = :esercizioId AND s2.id != :sessioneCorrente
            ORDER BY s2.dataInizio DESC
            LIMIT 1
          )
        ORDER BY se.numero
        """
    )
    suspend fun ultimeSerieEseguite(esercizioId: Long, sessioneCorrente: Long): List<SerieEseguita>

    // ---------- Report ----------
    @Query(
        """
        SELECT s.id AS sessioneId, s.dataInizio AS dataInizio,
               se.esercizioId AS esercizioId, e.nome AS nomeEsercizio, e.gruppoMuscolare AS gruppoMuscolare,
               se.numero AS numero, se.ripetizioni AS ripetizioni, se.pesoKg AS pesoKg, se.durataSec AS durataSec
        FROM serie_eseguite se
        JOIN sessioni s ON s.id = se.sessioneId
        JOIN esercizi e ON e.id = se.esercizioId
        ORDER BY s.dataInizio
        """
    )
    fun getRigheReport(): Flow<List<RigaReport>>
}