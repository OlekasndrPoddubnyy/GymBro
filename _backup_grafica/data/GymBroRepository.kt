package com.my.gymbro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GymBroRepository(private val dao: GymBroDao) {

    val esercizi: Flow<List<Esercizio>> = dao.getEsercizi()
    val schede: Flow<List<Scheda>> = dao.getSchede()

    fun schedaConEsercizi(id: Long): Flow<SchedaConEsercizi?> = dao.getSchedaConEsercizi(id)

    /**
     * Allinea gli esercizi predefiniti: inserisce quelli mancanti (per nome) e riempie descrizioni
     * e immagini (da assets, [assetSlugs]) mancanti di quelli già presenti. Idempotente e non
     * distruttivo: non duplica, non sovrascrive dati esistenti né esercizi creati dall'utente.
     */
    suspend fun assicuraEserciziPredefiniti(assetSlugs: Set<String> = emptySet()) {
        val perNome = dao.tuttiEsercizi().associateBy { it.nome }
        val daInserire = mutableListOf<Esercizio>()
        for (seed in eserciziPredefiniti()) {
            val slug = slugEsercizio(seed.nome)
            val pathAsset = if (slug in assetSlugs) "asset://esercizi/$slug/0.jpg" else null
            val attuale = perNome[seed.nome]
            if (attuale == null) {
                daInserire += seed.copy(immaginePath = pathAsset)
            } else if (attuale.predefinito) {
                val descr = if (attuale.descrizione.isNullOrBlank()) seed.descrizione else attuale.descrizione
                val img = if (attuale.immaginePath.isNullOrBlank()) pathAsset else attuale.immaginePath
                if (descr != attuale.descrizione || img != attuale.immaginePath) {
                    dao.updateEsercizio(attuale.copy(descrizione = descr, immaginePath = img))
                }
            }
        }
        if (daInserire.isNotEmpty()) dao.insertEsercizi(daInserire)
    }

    // Esercizi
    suspend fun aggiungiEsercizio(e: Esercizio): Long = dao.insertEsercizio(e)
    suspend fun aggiornaEsercizio(e: Esercizio) = dao.updateEsercizio(e)
    suspend fun eliminaEsercizio(e: Esercizio) = dao.deleteEsercizio(e)

    // Schede
    suspend fun creaScheda(s: Scheda): Long = dao.insertScheda(s)
    suspend fun aggiornaScheda(s: Scheda) = dao.updateScheda(s)
    suspend fun eliminaScheda(s: Scheda) = dao.deleteScheda(s)

    // Esercizi in scheda + serie
    suspend fun aggiungiEsercizioAScheda(es: EsercizioScheda): Long = dao.insertEsercizioScheda(es)
    suspend fun aggiornaEsercizioScheda(es: EsercizioScheda) = dao.updateEsercizioScheda(es)
    suspend fun rimuoviEsercizioScheda(es: EsercizioScheda) = dao.deleteEsercizioScheda(es)

    suspend fun sostituisciSerie(esercizioSchedaId: Long, serie: List<SeriePianificata>) {
        dao.deleteSerieDi(esercizioSchedaId)
        dao.insertSerie(serie)
    }

    // Scheda completa (template + esercizi + serie)
    suspend fun salvaScheda(
        scheda: Scheda,
        esercizi: List<Pair<EsercizioScheda, List<SeriePianificata>>>
    ): Long = dao.salvaSchedaConEsercizi(scheda, esercizi)

    suspend fun caricaSchedaCompleta(id: Long): SchedaConEsercizi? =
        dao.getSchedaConEsercizi(id).first()

    // Esecuzione / sessioni
    val sessioni: Flow<List<Sessione>> = dao.getSessioni()

    suspend fun avviaSessione(s: Sessione): Long = dao.insertSessione(s)
    suspend fun terminaSessione(s: Sessione) = dao.updateSessione(s)
    suspend fun registraSerie(s: SerieEseguita): Long = dao.insertSerieEseguita(s)

    suspend fun prefillEsercizio(esercizioId: Long, sessioneCorrente: Long): List<SerieEseguita> =
        dao.ultimeSerieEseguite(esercizioId, sessioneCorrente)

    // Report
    fun righeReport(): Flow<List<RigaReport>> = dao.getRigheReport()
}