package com.my.gymbro.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GymBroRepository(private val db: GymBroDatabase) {

    private val dao: GymBroDao = db.gymBroDao()

    val esercizi: Flow<List<Esercizio>> = dao.getEsercizi()
    val schede: Flow<List<Scheda>> = dao.getSchede()

    fun schedaConEsercizi(id: Long): Flow<SchedaConEsercizi?> = dao.getSchedaConEsercizi(id)

    /**
     * Allinea gli esercizi predefiniti: inserisce quelli mancanti (per nome) e riempie descrizioni
     * e immagini (da assets, [assetSlugs]) mancanti di quelli già presenti. Idempotente e non
     * distruttivo: non duplica, non sovrascrive dati esistenti né esercizi creati dall'utente.
     * Una descrizione predefinita viene aggiornata solo se quella salvata è ancora contenuta nella
     * nuova (es. aggiunta della riga "Technogym: …"): le descrizioni modificate dall'utente restano.
     * Tutto in un'unica transazione: se qualcosa va storto non resta un catalogo a metà.
     * Gli esercizi predefiniti che l'utente ha eliminato ([eliminati], per nome) non vengono reinseriti.
     */
    suspend fun assicuraEserciziPredefiniti(
        assetSlugs: Set<String> = emptySet(),
        eliminati: Set<String> = emptySet(),
    ) = db.withTransaction {
        val perNome = dao.tuttiEsercizi().associateBy { it.nome }
        val daInserire = mutableListOf<Esercizio>()
        for (seed in eserciziPredefiniti()) {
            if (seed.nome in eliminati && seed.nome !in perNome) continue
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
     *  - quelle già presenti (stesso nome) passano alla versione attuale, con copia di quella precedente.
     * Gli esercizi vengono cercati per nome nella libreria; quelli mancanti vengono saltati.
     * Le schede create dall'utente non vengono mai toccate, e nemmeno quelle in cui ha sostituito
     * un esercizio ([personalizzate]).
     * Tutto in un'unica transazione: o si aggiornano tutte le schede o nessuna.
     */
    suspend fun assicuraSchedePredefinite(personalizzate: Set<Long> = emptySet()) = db.withTransaction {
        val schedePerNome = dao.tutteSchede().associateBy { it.nome }
        val perNome = dao.tuttiEsercizi().associateBy { it.nome }
        val adesso = System.currentTimeMillis()
        // La lista schede è ordinata per data di creazione decrescente: la A deve apparire per prima.
        schedePredefinite().forEachIndexed { indice, template ->
            val esistente = schedePerNome[template.nome]
            if (esistente != null) {
                if (esistente.id !in personalizzate) aggiornaSchedaEsistente(esistente.id, template, perNome)
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
     * Porta una scheda predefinita già salvata alla versione attuale del template (esercizi, serie
     * e note), senza perdere quello che l'utente ci aveva messo:
     *  - la versione di prima, con le eventuali modifiche, resta come copia "(versione precedente)";
     *  - gli esercizi che restano tengono i pesi salvati.
     * La riga della scheda resta la stessa, così lo storico continua a riferirsi a lei.
     */
    private suspend fun aggiornaSchedaEsistente(
        schedaId: Long,
        template: SchedaTemplate,
        perNome: Map<String, Esercizio>,
    ) {
        val completa = dao.schedaConEserciziOra(schedaId) ?: return
        val modello = righeDaTemplate(template.esercizi, perNome)
        if (modello.isEmpty()) return

        val attuali = completa.esercizi
            .sortedBy { it.esercizioScheda.ordine }
            .map { ec -> ec.esercizioScheda to ec.serie.sortedBy { it.numero } }
        val pesiSalvati = attuali.associate { (riga, serie) -> riga.esercizioId to serie.map { it.pesoKg } }
        val nuove = modello.map { (riga, serie) ->
            val pesi = pesiSalvati[riga.esercizioId].orEmpty()
            riga to serie.mapIndexed { i, s ->
                // Una serie in più prende il peso dell'ultima salvata.
                s.copy(pesoKg = pesi.getOrNull(i) ?: pesi.lastOrNull() ?: s.pesoKg)
            }
        }

        val scheda = completa.scheda
        if (scheda.note == template.note && senzaId(attuali) == senzaId(nuove)) return
        if (attuali.isNotEmpty()) {
            val copia = scheda.copy(
                id = 0,
                nome = "${scheda.nome} (versione precedente)",
                // La lista va dalla più recente: le copie restano sotto le schede attuali.
                dataCreazione = scheda.dataCreazione - 24 * 60 * 60 * 1000L,
            )
            dao.salvaSchedaConEsercizi(copia, attuali)
        }
        dao.salvaSchedaConEsercizi(scheda.copy(note = template.note), nuove)
    }

    /** Le righe senza id e posizione salvata, per confrontare solo il contenuto. */
    private fun senzaId(righe: List<Pair<EsercizioScheda, List<SeriePianificata>>>) =
        righe.map { (riga, serie) ->
            riga.copy(id = 0, schedaId = 0, ordine = 0) to serie.map { it.copy(id = 0, esercizioSchedaId = 0) }
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
    suspend fun schedeConEsercizio(id: Long): List<String> = dao.schedeConEsercizio(id)
    suspend fun esercizioNelloStorico(id: Long): Boolean = dao.esercizioNelloStorico(id)

    // Schede
    suspend fun eliminaScheda(s: Scheda) = dao.deleteScheda(s)

    // Scheda completa (template + esercizi + serie)
    suspend fun salvaScheda(
        scheda: Scheda,
        esercizi: List<Pair<EsercizioScheda, List<SeriePianificata>>>
    ): Long = dao.salvaSchedaConEsercizi(scheda, esercizi)

    suspend fun caricaSchedaCompleta(id: Long): SchedaConEsercizi? =
        dao.getSchedaConEsercizi(id).first()

    /**
     * Mette [nuovoId] al posto di [vecchioId] nella scheda, con le stesse serie: cambiano il carico
     * e le note, che valevano per l'esercizio sostituito. Lo storico non si tocca.
     *
     * @return ciò che serve per annullare; null se la scheda nel frattempo è cambiata.
     */
    suspend fun cambiaEsercizio(
        schedaId: Long,
        vecchioId: Long,
        nuovoId: Long,
        carico: Float?,
    ): Precedente? = db.withTransaction {
        val completa = dao.schedaConEserciziOra(schedaId) ?: return@withTransaction null
        val ordinati = completa.esercizi.sortedBy { it.esercizioScheda.ordine }
        val cambiati = ordinati.filter { it.esercizioScheda.esercizioId == vecchioId }
        if (cambiati.isEmpty() || ordinati.any { it.esercizioScheda.esercizioId == nuovoId }) {
            return@withTransaction null
        }
        if (dao.tuttiEsercizi().none { it.id == nuovoId }) return@withTransaction null

        val righe = ordinati.map { ec ->
            val serie = ec.serie.sortedBy { it.numero }
            if (ec.esercizioScheda.esercizioId != vecchioId) ec.esercizioScheda to serie
            else ec.esercizioScheda.copy(esercizioId = nuovoId, note = null) to serie.map { it.copy(pesoKg = carico) }
        }
        dao.salvaSchedaConEsercizi(completa.scheda, righe)
        Precedente(
            esercizioId = vecchioId,
            note = cambiati.map { it.esercizioScheda.note },
            pesi = cambiati.flatMap { ec -> ec.serie.sortedBy { it.numero }.map { it.pesoKg } },
        )
    }

    /** Annulla [cambiaEsercizio], rimettendo note e pesi di prima. false = non è più possibile. */
    suspend fun annullaCambio(schedaId: Long, nuovoId: Long, prima: Precedente): Boolean = db.withTransaction {
        val completa = dao.schedaConEserciziOra(schedaId) ?: return@withTransaction false
        val ordinati = completa.esercizi.sortedBy { it.esercizioScheda.ordine }
        val presenti = ordinati.map { it.esercizioScheda.esercizioId }
        if (nuovoId !in presenti || prima.esercizioId in presenti) return@withTransaction false
        if (dao.tuttiEsercizi().none { it.id == prima.esercizioId }) return@withTransaction false

        var indiceRiga = 0
        var indiceSerie = 0
        val righe = ordinati.map { ec ->
            val serie = ec.serie.sortedBy { it.numero }
            if (ec.esercizioScheda.esercizioId != nuovoId) {
                ec.esercizioScheda to serie
            } else {
                val nota = prima.note.getOrNull(indiceRiga++)
                val ripristinate = serie.map { it.copy(pesoKg = prima.pesi.getOrNull(indiceSerie++)) }
                ec.esercizioScheda.copy(esercizioId = prima.esercizioId, note = nota) to ripristinate
            }
        }
        dao.salvaSchedaConEsercizi(completa.scheda, righe)
        true
    }

    // Esecuzione / sessioni
    val sessioni: Flow<List<Sessione>> = dao.getSessioni()

    suspend fun avviaSessione(s: Sessione): Long = dao.insertSessione(s)
    suspend fun terminaSessione(id: Long, fine: Long) = dao.chiudiSessione(id, fine)
    suspend fun registraSerie(s: SerieEseguita): Long = dao.insertSerieEseguita(s)

    /** Elimina una sessione senza serie (allenamento chiuso prima di farne anche solo una). */
    suspend fun scartaSessioneVuota(id: Long) = dao.deleteSessioneVuota(id)

    suspend fun prefillEsercizio(esercizioId: Long, sessioneCorrente: Long): List<SerieEseguita> =
        dao.ultimeSerieEseguite(esercizioId, sessioneCorrente)

    // Report
    fun righeReport(): Flow<List<RigaReport>> = dao.getRigheReport()
}