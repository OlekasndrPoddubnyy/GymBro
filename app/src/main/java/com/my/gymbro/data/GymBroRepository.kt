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
     * Una descrizione predefinita viene aggiornata solo se quella salvata è ancora contenuta nella
     * nuova (es. aggiunta della riga "Technogym: …"): le descrizioni modificate dall'utente restano.
     */
    suspend fun assicuraEserciziPredefiniti(assetSlugs: Set<String> = emptySet()) {
        val perNome = dao.tuttiEsercizi().associateBy { it.nome }
        val daInserire = mutableListOf<Esercizio>()
        for (seed in eserciziPredefiniti()) {
            // Le cartelle proprie hanno la precedenza; altrimenti si riusa l'immagine indicata dal seed.
            val slug = slugEsercizio(seed.nome).takeIf { it in assetSlugs } ?: seed.immaginePath
            val pathAsset = if (slug != null && slug in assetSlugs) "asset://esercizi/$slug/0.jpg" else null
            val attuale = perNome[seed.nome]
            if (attuale == null) {
                daInserire += seed.copy(immaginePath = pathAsset)
            } else if (attuale.predefinito) {
                val nuova = seed.descrizione
                val descr = when {
                    attuale.descrizione.isNullOrBlank() -> nuova
                    nuova != null && nuova != attuale.descrizione && nuova.contains(attuale.descrizione) -> nuova
                    else -> attuale.descrizione
                }
                val img = if (attuale.immaginePath.isNullOrBlank()) pathAsset else attuale.immaginePath
                if (descr != attuale.descrizione || img != attuale.immaginePath) {
                    dao.updateEsercizio(attuale.copy(descrizione = descr, immaginePath = img))
                }
            }
        }
        if (daInserire.isNotEmpty()) dao.insertEsercizi(daInserire)
    }

    /**
     * Allinea le schede predefinite ([schedePredefinite]):
     *  - inserisce quelle che non esistono ancora (per nome);
     *  - quelle già presenti (stesso nome) vengono sostituite con la versione attuale.
     * Gli esercizi vengono cercati per nome nella libreria; quelli mancanti vengono saltati.
     * Le schede create dall'utente non vengono mai toccate.
     */
    suspend fun assicuraSchedePredefinite() {
        val schedePerNome = dao.getSchede().first().associateBy { it.nome }
        val perNome = dao.tuttiEsercizi().associateBy { it.nome }
        val adesso = System.currentTimeMillis()
        // La lista schede è ordinata per data di creazione decrescente: la A deve apparire per prima.
        schedePredefinite().forEachIndexed { indice, template ->
            val esistente = schedePerNome[template.nome]
            if (esistente != null) {
                aggiornaSchedaEsistente(esistente.id, template, perNome)
                return@forEachIndexed
            }
            val righe = righeDaTemplate(template.esercizi, perNome)
            if (righe.isEmpty()) return@forEachIndexed
            dao.salvaSchedaConEsercizi(
                Scheda(nome = template.nome, note = template.note, dataCreazione = adesso - indice * 1000L),
                righe
            )
        }
    }

    /**
     * Sostituisce per intero il contenuto di una scheda predefinita già salvata con la versione
     * attuale del template (esercizi, serie e note). La riga della scheda resta la stessa, così lo
     * storico degli allenamenti già fatti continua a riferirsi a lei.
     */
    private suspend fun aggiornaSchedaEsistente(
        schedaId: Long,
        template: SchedaTemplate,
        perNome: Map<String, Esercizio>,
    ) {
        val completa = dao.getSchedaConEsercizi(schedaId).first() ?: return
        val righe = righeDaTemplate(template.esercizi, perNome)
        if (righe.isEmpty()) return
        dao.salvaSchedaConEsercizi(completa.scheda.copy(note = template.note), righe)
    }

    /** Converte gli esercizi di un template nelle righe da salvare (superset inclusi). */
    private fun righeDaTemplate(
        esercizi: List<EsercizioTemplate>,
        perNome: Map<String, Esercizio>,
    ): List<Pair<EsercizioScheda, List<SeriePianificata>>> {
        val disponibili = esercizi.filter { perNome.containsKey(it.nomeEsercizio) }
        var gruppo = 0
        var gruppoAperto: Int? = null
        return disponibili.map { et ->
            // Superset: l'esercizio con supersetConProssimo apre un gruppo che include il successivo.
            val gruppoRiga = gruppoAperto ?: if (et.supersetConProssimo) ++gruppo else null
            gruppoAperto = if (et.supersetConProssimo) gruppoRiga else null
            val es = EsercizioScheda(
                schedaId = 0,
                esercizioId = perNome.getValue(et.nomeEsercizio).id,
                modalita = et.modalita,
                note = et.note,
                gruppoSuperset = gruppoRiga
            )
            val serie = et.serie.mapIndexed { i, st ->
                SeriePianificata(
                    esercizioSchedaId = 0,
                    numero = i + 1,
                    durataSec = st.durataSec,
                    ripetizioni = st.ripetizioni,
                    pesoKg = st.pesoKg,
                    recuperoSec = st.recuperoSec
                )
            }
            es to serie
        }
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