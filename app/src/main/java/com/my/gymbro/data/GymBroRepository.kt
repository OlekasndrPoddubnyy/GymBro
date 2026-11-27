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
     * Allinea gli esercizi predefiniti al catalogo ([allineaCatalogo]): inserisce quelli mancanti e
     * completa quelli presenti, riconoscendoli dalla chiave e non dal nome, così un esercizio
     * rinominato dall'utente non si duplica. Non tocca gli esercizi creati dall'utente e non
     * reinserisce i predefiniti eliminati ([eliminati], per chiave).
     * Tutto in un'unica transazione: se qualcosa va storto non resta un catalogo a metà.
     */
    suspend fun assicuraEserciziPredefiniti(
        assetSlugs: Set<String> = emptySet(),
        eliminati: Set<String> = emptySet(),
    ) = db.withTransaction {
        val piano = allineaCatalogo(dao.tuttiEsercizi(), eserciziPredefiniti(), assetSlugs, eliminati)
        piano.daAggiornare.forEach { dao.updateEsercizio(it) }
        if (piano.daInserire.isNotEmpty()) dao.insertEsercizi(piano.daInserire)
    }

    /**
     * Allinea le schede predefinite ([schedePredefinite]):
     *  - inserisce quelle che non esistono ancora (per nome);
     *  - quelle già presenti (stesso nome) passano alla versione attuale, tenendo i pesi.
     * Gli esercizi vengono cercati per nome nella libreria; quelli mancanti vengono saltati.
     * Le schede create dall'utente non vengono mai toccate, e nemmeno quelle in cui ha sostituito
     * un esercizio ([personalizzate]).
     * Tutto in un'unica transazione: o si aggiornano tutte le schede o nessuna.
     */
    suspend fun assicuraSchedePredefinite(personalizzate: Set<Long> = emptySet()) = db.withTransaction {
        val schedePerNome = dao.tutteSchede().associateBy { it.nome }
        // I template usano i nomi del catalogo: un esercizio rinominato si ritrova lo stesso.
        val perNome = dao.tuttiEsercizi().perNomeCatalogo()
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
     * e note). Gli esercizi che restano tengono i pesi salvati; la versione di prima non si conserva.
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
        dao.salvaSchedaConEsercizi(scheda.copy(note = template.note), nuove)
    }

    /**
     * Elimina le copie "(versione precedente)" che l'aggiornamento v12 delle schede predefinite
     * lasciava accanto alle nuove. Lo storico resta: le sessioni tengono il nome della scheda.
     */
    suspend fun eliminaCopieSchedePredefinite() = db.withTransaction {
        val copie = schedePredefinite().map { "${it.nome} (versione precedente)" }.toSet()
        dao.tutteSchede().filter { it.nome in copie }.forEach { dao.deleteScheda(it) }
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
     * Salva tra le proprie una scheda arrivata da un QR. Gli esercizi si cercano per chiave di
     * catalogo (così si ritrovano anche se uno dei due telefoni li ha rinominati), poi per nome
     * (prima esatto, poi senza maiuscole/minuscole). Quelli che mancano si aggiungono alla libreria:
     * un esercizio del catalogo che era stato eliminato torna predefinito, con la sua descrizione.
     * Se esiste già una scheda con lo stesso nome, la nuova diventa "Nome (condivisa)".
     * Tutto in un'unica transazione. @return l'id della scheda salvata.
     */
    suspend fun importaSchedaCondivisa(s: SchedaCondivisa): Long = db.withTransaction {
        val libreria = dao.tuttiEsercizi()
        val perChiave = libreria.filter { it.chiaveSeed != null }.associateBy { it.chiaveSeed!! }
        val esatti = libreria.associateBy { it.nome }
        val senzaMaiuscole = libreria.groupBy { normalizzaNome(it.nome) }.mapValues { it.value.first() }
        val catalogo by lazy { eserciziPredefiniti().associateBy { it.nome } }
        val idPerNome = HashMap<String, Long>()

        val righe = s.esercizi.map { e ->
            val chiave = e.chiave ?: e.nome
            val id = idPerNome.getOrPut(normalizzaNome(chiave)) {
                (perChiave[chiave] ?: esatti[e.nome] ?: senzaMaiuscole[normalizzaNome(e.nome)])?.id
                    ?: run {
                        val delCatalogo = catalogo[chiave]
                        dao.insertEsercizio(
                            Esercizio(
                                nome = e.nome,
                                gruppoMuscolare = e.gruppo,
                                attrezzatura = e.attrezzatura,
                                descrizione = delCatalogo?.descrizione,
                                predefinito = delCatalogo != null,
                                chiaveSeed = delCatalogo?.nome,
                            )
                        )
                    }
            }
            EsercizioScheda(
                schedaId = 0,
                esercizioId = id,
                modalita = e.modalita,
                note = e.note,
                gruppoSuperset = e.superset,
            ) to e.serie.mapIndexed { i, r ->
                SeriePianificata(
                    esercizioSchedaId = 0,
                    numero = i + 1,
                    durataSec = r.durataSec,
                    ripetizioni = r.ripetizioni,
                    pesoKg = r.pesoKg,
                    recuperoSec = r.recuperoSec,
                )
            }
        }
        val nome = nomeLibero(s.nome, dao.tutteSchede().mapTo(HashSet()) { it.nome })
        dao.salvaSchedaConEsercizi(Scheda(nome = nome, note = s.note), righe)
    }

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
    /** Salva la serie e porta la fine della sessione a [ora]: se l'allenamento si interrompe, la durata resta. */
    suspend fun registraSerie(s: SerieEseguita, ora: Long = System.currentTimeMillis()): Long =
        db.withTransaction {
            val id = dao.insertSerieEseguita(s)
            dao.estendiSessione(s.sessioneId, ora)
            id
        }

    /** Elimina una sessione senza serie (allenamento chiuso prima di farne anche solo una). */
    suspend fun scartaSessioneVuota(id: Long) = dao.deleteSessioneVuota(id)

    // Ripresa di un allenamento interrotto
    suspend fun sessione(id: Long): Sessione? = dao.sessione(id)
    suspend fun serieDiSessione(id: Long): List<SerieEseguita> = dao.serieDiSessione(id)
    suspend fun eserciziOra(): List<Esercizio> = dao.tuttiEsercizi()

    suspend fun prefillEsercizio(esercizioId: Long, sessioneCorrente: Long): List<SerieEseguita> =
        dao.ultimeSerieEseguite(esercizioId, sessioneCorrente)

    // Report
    fun righeReport(): Flow<List<RigaReport>> = dao.getRigheReport()

    // Backup

    /** Tutte le righe del database, lette insieme: il backup non mescola stati diversi. */
    suspend fun leggiPerBackup(
        buddy: Map<String, String>,
        predefinitiEliminati: Set<String>,
    ): DatiBackup = db.withTransaction {
        DatiBackup(
            creatoIl = System.currentTimeMillis(),
            esercizi = dao.tuttiEsercizi(),
            schede = dao.tutteSchede(),
            eserciziScheda = dao.tuttiEserciziScheda(),
            seriePianificate = dao.tutteSeriePianificate(),
            sessioni = dao.tutteSessioni(),
            serieEseguite = dao.tutteSerieEseguite(),
            buddy = buddy,
            predefinitiEliminati = predefinitiEliminati,
        )
    }

    /**
     * Sostituisce tutto il database con [d], con gli stessi id. In un'unica transazione: se una riga
     * non va (es. riferimento a un esercizio che non c'è) non cambia niente.
     * [immagineEsiste] dice se il file di una foto è su questo telefono: se manca, si toglie il percorso.
     */
    suspend fun ripristina(d: DatiBackup, immagineEsiste: (String) -> Boolean) = db.withTransaction {
        dao.svuotaSerieEseguite()
        dao.svuotaSessioni()
        dao.svuotaSeriePianificate()
        dao.svuotaEserciziScheda()
        dao.svuotaSchede()
        dao.svuotaEsercizi()
        dao.insertEsercizi(d.esercizi.map { e ->
            val path = e.immaginePath
            if (path.isNullOrBlank() || immagineEsiste(path)) e else e.copy(immaginePath = null)
        })
        dao.insertSchede(d.schede)
        dao.insertEserciziScheda(d.eserciziScheda)
        dao.insertSerie(d.seriePianificate)
        dao.insertSessioni(d.sessioni)
        dao.insertSerieEseguite(d.serieEseguite)
    }
}