package com.my.gymbro.ui

import android.util.Log
import com.my.gymbro.GymBroApplication
import com.my.gymbro.TimerService
import com.my.gymbro.data.ChiaveBuddy
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.Obiettivo
import com.my.gymbro.data.ProgressoBuddy
import com.my.gymbro.data.SerieEseguita
import com.my.gymbro.data.SerieFatta
import com.my.gymbro.data.Sessione
import com.my.gymbro.data.caricoInverso
import com.my.gymbro.data.dopoAllenamento
import com.my.gymbro.data.nomeCatalogo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

private const val SEC_PREPARAZIONE = 10

/** Un passo di esecuzione: una serie di un esercizio, nell'ordine reale (interlacciato per i superset). */
internal data class Passo(
    val idxEsercizio: Int,
    val idxSerie: Int,
    val recuperoDopo: Int,
    val attesaPrima: Boolean,
)

/**
 * Motore dell'allenamento in corso, singleton di processo come [TimerEngine]: stato, conto alla
 * rovescia e salvataggi vivono finché vive il processo (tenuto in vita dal [TimerService]), quindi
 * l'allenamento continua anche se l'Activity viene distrutta (app tolta dalle recenti, cambio
 * configurazione, …). La UI lo osserva tramite [AllenamentoViewModel].
 *
 * Tutto gira sul main thread: i comandi dell'utente e lo scadere del tempo non si sovrappongono.
 */
object AllenamentoEngine : MotoreAllenamento(Dispatchers.Main.immediate) {
    fun avvia(app: GymBroApplication, idScheda: Long) = avvia(AmbienteApp(app), idScheda)

    fun riprendi(app: GymBroApplication, salvato: AllenamentoSalvato, terminaSubito: Boolean) =
        riprendi(AmbienteApp(app), salvato, terminaSubito)
}

/**
 * La logica dell'allenamento (passi, superset, recuperi, salti, salvataggi), separata da Android:
 * [contesto] dice dove girano le coroutine (il main thread nell'app, quello virtuale di runTest nei
 * test), [orologio] da dove si legge il tempo. Database e memorie arrivano con [avvia]/[riprendi].
 */
open class MotoreAllenamento(
    contesto: CoroutineContext,
    private val orologio: Orologio = OrologioDiSistema,
) {

    // SupervisorJob figlio del Job del contesto (se c'è): nei test si chiude con il test.
    private val scope = CoroutineScope(
        contesto + SupervisorJob(contesto[Job]) + gestoreErrori("allenamento") { e ->
            mostra(Avviso("Qualcosa è andato storto", "Errore durante l'allenamento: ${motivoErrore(e)}."))
        }
    )
    private val conto = ContoAllaRovescia(scope, ora = orologio::monotono, segnala = AllenamentoAttivo::segnala)
    private val doppioTocco = AntiDoppioTocco(orologio::monotono)

    private val _stato = MutableStateFlow(StatoEsecuzione())
    val stato: StateFlow<StatoEsecuzione> = _stato.asStateFlow()

    /** Scheda dell'allenamento aperto (in corso o nel riepilogo finale); null = nessuno. */
    private val _schedaInCorso = MutableStateFlow<Long?>(null)
    val schedaInCorso: StateFlow<Long?> = _schedaInCorso.asStateFlow()

    /** true se c'è un allenamento avviato e non ancora completato. */
    val inEsecuzione: Boolean
        get() = _schedaInCorso.value != null && _stato.value.fase != Fase.COMPLETATO

    /** Sessione dell'allenamento in corso: finché non è finito non la si corregge dallo storico. */
    val sessioneInCorso: Long?
        get() = sessioneId.takeIf { inEsecuzione && it != 0L }

    /** true se l'allenamento in corso usa l'esercizio [id]: finché è così non si può eliminare. */
    fun usaEsercizio(id: Long): Boolean = inEsecuzione &&
        _stato.value.let { s -> s.esercizi.any { it.esercizioId == id } || s.log.any { it.esercizioId == id } }

    private lateinit var amb: AmbienteAllenamento

    /** Ultimo stato salvato per la ripresa (senza l'ora), per non riscriverlo a ogni secondo. */
    private var ultimoSalvato: AllenamentoSalvato? = null
    /** Fine del conto alla rovescia in corso, in ora di sistema: sopravvive alla chiusura dell'app. */
    private var fineConto: Long? = null

    private val _avviso = MutableStateFlow<Avviso?>(null)
    /** Problema da far sapere all'utente (salvataggio non riuscito, ripresa impossibile, …); null = nessuno. */
    val avviso: StateFlow<Avviso?> = _avviso.asStateFlow()
    fun chiudiAvviso() { _avviso.value = null }

    /** Un avviso già aperto non viene sostituito: l'utente lo legge prima di vederne un altro. */
    private fun mostra(avviso: Avviso) {
        if (_avviso.value == null) _avviso.value = avviso
    }

    /**
     * Serie fatte che il database non ha ancora accettato. Restano nel riepilogo e si riprova a
     * salvarle con la serie successiva e a fine allenamento: un errore passeggero non le fa perdere.
     */
    private val daSalvare = mutableListOf<SerieEseguita>()
    private val salvataggio = Mutex()
    /** Già avvisato che le serie non si salvano: non lo si ripete a ogni serie. */
    private var avvisatoNonSalvate = false

    /** Serie previste e obiettivo all'avvio, per gli esercizi con Gym Buddy. */
    private var pianoBuddy: Map<Long, Pair<Int, Obiettivo>> = emptyMap()

    private var sessioneId: Long = 0
    private var dataInizio: Long = 0
    private var prefill: Map<Long, List<SerieEseguita>> = emptyMap()
    private var passi: List<Passo> = emptyList()
    private var caricamento: Job? = null

    /** Passi (serie) realmente completati e quelli saltati: è da qui che si capisce cosa resta da fare. */
    private val completati = mutableSetOf<Int>()
    private val saltati = mutableSetOf<Int>()
    private fun risolto(i: Int) = i in completati || i in saltati

    init {
        // Lo stato è visibile al resto dell'app (barra "in corso") e al servizio in primo piano.
        scope.launch {
            _stato.collect { s ->
                AllenamentoAttivo.aggiorna(
                    if (!s.pronta || s.fase == Fase.COMPLETATO) null
                    else AllenamentoAttivo.Info(s.nomeScheda, descriviEsecuzione(s))
                )
                salvaPerRipresa(s)
            }
        }
    }

    /**
     * Salva quanto serve a riprendere l'allenamento se Android chiude l'app. Si scrive solo quando
     * cambia qualcosa che conta (passo, fase, esercizi, serie fatte), non a ogni secondo del conto.
     */
    private fun salvaPerRipresa(s: StatoEsecuzione) {
        if (!s.pronta || s.fase == Fase.COMPLETATO || sessioneId == 0L || !::amb.isInitialized) return
        val schedaId = _schedaInCorso.value ?: return
        val istantanea = AllenamentoSalvato(
            schedaId = schedaId,
            sessioneId = sessioneId,
            dataInizio = dataInizio,
            nomeScheda = s.nomeScheda,
            esercizi = s.esercizi,
            pianoBuddy = pianoBuddy,
            indicePasso = s.indicePasso,
            fase = s.fase,
            completati = completati.toSet(),
            saltati = saltati.toSet(),
            fineRecupero = if (s.fase == Fase.RECUPERO) fineConto else null,
        )
        if (istantanea == ultimoSalvato) return
        ultimoSalvato = istantanea
        amb.salvaRipresa(istantanea.copy(aggiornatoIl = orologio.adesso()))
    }

    private fun dimenticaRipresa() {
        ultimoSalvato = null
        if (::amb.isInitialized) amb.cancellaRipresa()
    }

    /** Azzera il motore per un nuovo allenamento (o per uno da riprendere). */
    private fun prepara(ambiente: AmbienteAllenamento, idScheda: Long) {
        amb = ambiente
        ultimoSalvato = null
        fineConto = null
        pianoBuddy = emptyMap()
        conto.ferma()
        caricamento?.cancel()
        sessioneId = 0
        prefill = emptyMap()
        passi = emptyList()
        completati.clear()
        saltati.clear()
        doppioTocco.azzera()
        _stato.value = StatoEsecuzione()
        _schedaInCorso.value = idScheda
    }

    /** Prima serie ancora da fare dopo [da]; se non ce ne sono, la prima rimasta indietro dall'inizio. */
    private fun prossimoPasso(da: Int): Int? =
        (da + 1 until passi.size).firstOrNull { !risolto(it) }
            ?: (0 until passi.size).firstOrNull { !risolto(it) }

    /** Indici degli esercizi del blocco di [idxEsercizio] (esercizio singolo o intero superset). */
    private fun bloccoDi(idxEsercizio: Int): Set<Int> {
        val esercizi = _stato.value.esercizi
        val gruppo = esercizi.getOrNull(idxEsercizio)?.gruppo ?: return setOf(idxEsercizio)
        return esercizi.indices.filter { esercizi[it].gruppo == gruppo }.toSet()
    }

    /**
     * Avvia un nuovo allenamento della scheda [idScheda]. Ignorato se ce n'è già uno in corso
     * (non completato); un eventuale riepilogo ancora aperto viene chiuso.
     */
    fun avvia(ambiente: AmbienteAllenamento, idScheda: Long) {
        if (inEsecuzione) return
        prepara(ambiente, idScheda)
        // Iniziarne uno nuovo vuol dire lasciar perdere quello interrotto (le sue serie restano nello storico).
        dimenticaRipresa()
        amb.togliAvvisoInterrotto()

        caricamento = scope.lanciaProtetto("Avvio dell'allenamento", seErrore = { e ->
            // Sessione forse già creata ma vuota: non deve restare nello storico.
            val creata = sessioneId
            abbandona(Avviso("Allenamento non avviato", "Non è stato possibile avviare l'allenamento: ${motivoErrore(e)}."))
            if (creata != 0L) scope.lanciaProtetto("Pulizia della sessione vuota", {}) { amb.scartaSessioneVuota(creata) }
        }) {
            val completa = amb.caricaSchedaCompleta(idScheda)
            val nome = completa?.scheda?.nome ?: "Allenamento"
            val esercizi = completa?.esercizi
                ?.sortedBy { it.esercizioScheda.ordine }
                ?.map { it.toEsecuzione().conGymBuddy(idScheda) }
                .orEmpty()
            pianoBuddy = esercizi
                .mapNotNull { es -> es.obiettivo?.let { es.esercizioId to it } }
                .toMap()
                .mapValues { (id, obiettivo) -> seriePerEsercizio(esercizi, id) to obiettivo }

            dataInizio = orologio.adesso()
            sessioneId = amb.avviaSessione(
                Sessione(schedaId = idScheda, nomeScheda = nome, dataInizio = dataInizio)
            )
            prefill = esercizi.associate { it.esercizioId to amb.prefillEsercizio(it.esercizioId, sessioneId) }
            passi = costruisciPassi(esercizi)

            if (passi.isEmpty()) {
                _stato.update { it.copy(pronta = true, nomeScheda = nome, esercizi = esercizi, fase = Fase.COMPLETATO) }
                terminaSessioneDb()
                return@lanciaProtetto
            }

            val primo = passi.first()
            _stato.update {
                it.copy(
                    pronta = true,
                    nomeScheda = nome,
                    esercizi = esercizi,
                    indicePasso = 0,
                    passiTotali = passi.size,
                    indiceEsercizio = primo.idxEsercizio,
                    indiceSerie = primo.idxSerie,
                    fase = Fase.PREPARAZIONE
                )
            }
            // Pubblica subito lo stato (il servizio controlla che ci sia qualcosa di attivo) e avvia
            // il servizio in primo piano: conteggio, beep e vibrazione proseguono in background.
            AllenamentoAttivo.aggiorna(AllenamentoAttivo.Info(nome, descriviEsecuzione(_stato.value)))
            amb.avviaServizio()
            avviaCountdown(SEC_PREPARAZIONE) { finePreparazione() }
        }
    }

    /** Chiude l'allenamento (dopo il riepilogo): se era ancora in corso viene prima terminato. */
    fun chiudi() {
        if (inEsecuzione) terminaAllenamento()
        caricamento?.cancel()
        conto.ferma()
        _schedaInCorso.value = null
        _stato.value = StatoEsecuzione()
    }

    /**
     * Ricostruisce un allenamento interrotto da Android a partire da [salvato] e dalle serie già nel
     * database. Con [terminaSubito] lo chiude come se fosse finito all'ultima attività (riepilogo e
     * Gym Buddy compresi); senza serie fatte la sessione vuota si elimina e basta.
     */
    fun riprendi(ambiente: AmbienteAllenamento, salvato: AllenamentoSalvato, terminaSubito: Boolean) {
        if (inEsecuzione) return
        prepara(ambiente, salvato.schedaId)
        amb.togliAvvisoInterrotto()

        caricamento = scope.launch {
            try {
                val sessione = amb.sessione(salvato.sessioneId)
                // Sessione sparita o diversa (es. dati importati da un backup nel frattempo).
                if (sessione == null || sessione.dataInizio != salvato.dataInizio) {
                    return@launch abbandona(nonRipreso("L'allenamento interrotto non è più nello storico: non si può riprendere."))
                }
                val esercizi = salvato.esercizi
                val nuoviPassi = costruisciPassi(esercizi)
                if (nuoviPassi.isEmpty()) return@launch abbandona(null)

                val libreria = amb.eserciziOra().associateBy { it.id }
                val log = amb.serieDiSessione(salvato.sessioneId)
                sessioneId = salvato.sessioneId
                dataInizio = salvato.dataInizio
                pianoBuddy = salvato.pianoBuddy
                passi = nuoviPassi
                completati += salvato.completati.filter { it in passi.indices }
                saltati += salvato.saltati.filter { it in passi.indices && it !in completati }
                // Esercizi tolti dalla libreria nel frattempo: le loro serie non si possono più registrare.
                passi.indices
                    .filter { esercizi[passi[it].idxEsercizio].esercizioId !in libreria && !risolto(it) }
                    .forEach { saltati += it }
                prefill = esercizi.map { it.esercizioId }.distinct().filter { it in libreria }
                    .associateWith { amb.prefillEsercizio(it, sessioneId) }

                val idx = salvato.indicePasso.takeIf { it in passi.indices && !risolto(it) }
                    ?: prossimoPasso(salvato.indicePasso.coerceIn(0, passi.lastIndex))
                _stato.value = StatoEsecuzione(
                    pronta = true,
                    nomeScheda = salvato.nomeScheda,
                    esercizi = esercizi,
                    indicePasso = idx ?: salvato.indicePasso.coerceIn(0, passi.lastIndex),
                    passiTotali = passi.size,
                    log = log,
                    nomiEseguiti = log.associate { r ->
                        r.esercizioId to (libreria[r.esercizioId]?.nome
                            ?: esercizi.firstOrNull { it.esercizioId == r.esercizioId }?.nome ?: "Esercizio")
                    },
                    catalogoEseguiti = log.mapNotNull { r ->
                        (libreria[r.esercizioId]?.nomeCatalogo
                            ?: esercizi.firstOrNull { it.esercizioId == r.esercizioId }?.nomeCatalogo)
                            ?.let { r.esercizioId to it }
                    }.toMap(),
                    fase = Fase.ATTESA,
                )

                if (terminaSubito || idx == null) {
                    if (log.isEmpty()) {
                        // Niente di fatto: non è un allenamento, non resta nello storico.
                        amb.scartaSessioneVuota(sessioneId)
                        return@launch abbandona(null)
                    }
                    posizionaSu(idx ?: passi.lastIndex)
                    terminaAllenamento(fine = maxOf(salvato.aggiornatoIl, dataInizio))
                    return@launch
                }

                posizionaSu(idx)
                // Il servizio controlla che ci sia qualcosa di attivo: lo stato va pubblicato prima.
                AllenamentoAttivo.aggiorna(AllenamentoAttivo.Info(salvato.nomeScheda, descriviEsecuzione(_stato.value)))
                amb.avviaServizio()
                val modalita = esercizi[passi[idx].idxEsercizio].modalita
                val come = if (idx != salvato.indicePasso) Ripartenza.Attesa
                else ripartenza(salvato.fase, modalita, salvato.fineRecupero, orologio.adesso())
                when (come) {
                    is Ripartenza.Recupero -> {
                        _stato.update { it.copy(fase = Fase.RECUPERO) }
                        avviaCountdown(come.secondi) { finePreparazione() }
                    }
                    Ripartenza.Serie -> avviaSerie()
                    Ripartenza.Attesa -> _stato.update { it.copy(fase = Fase.ATTESA) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GymBro", "Ripresa dell'allenamento non riuscita", e)
                abbandona(nonRipreso("Non è stato possibile riprendere l'allenamento (${motivoErrore(e)}). Le serie già fatte restano nello storico."))
            }
        }
    }

    private fun nonRipreso(testo: String) = Avviso("Allenamento non ripreso", testo)

    /** Avvio o ripresa non vanno: si dimentica l'allenamento e si torna a nessun allenamento. */
    private fun abbandona(avviso: Avviso?) {
        conto.ferma()
        dimenticaRipresa()
        AllenamentoAttivo.aggiorna(null)
        sessioneId = 0
        _schedaInCorso.value = null
        _stato.value = StatoEsecuzione()
        avviso?.let(::mostra)
    }

    // ---------- azioni utente ----------

    // [passo] è la serie per cui la ruota è stata mostrata: un valore in ritardo non finisce sulla successiva.
    fun aggiornaRip(passo: Int, v: String) = _stato.update {
        if (it.indicePasso == passo) it.copy(ripInput = v.filter(Char::isDigit)) else it
    }

    fun aggiornaPeso(passo: Int, v: String) = _stato.update {
        if (it.indicePasso == passo) it.copy(pesoInput = v.filter { c -> c.isDigit() || c == '.' }) else it
    }

    /** Sostituisce l'esercizio [indice] con [nuovo] dalla libreria, mantenendo serie e modalità e
     *  proponendo i valori dell'ultima volta che si è fatto [nuovo].
     *  Vale solo per questa sessione: la scheda salvata non viene toccata. */
    fun sostituisci(indice: Int, nuovo: Esercizio) {
        if (!inEsecuzione) return
        val sessione = sessioneId
        scope.lanciaProtetto("Sostituzione dell'esercizio", seErrore = { e ->
            mostra(Avviso("Esercizio non sostituito", "Non è stato possibile passare a \"${nuovo.nome}\": ${motivoErrore(e)}."))
        }) {
            val ultime = amb.prefillEsercizio(nuovo.id, sessione)
            if (!inEsecuzione || sessione != sessioneId) return@lanciaProtetto
            val prima = _stato.value.esercizi.getOrNull(indice) ?: return@lanciaProtetto
            if (prima.esercizioId == nuovo.id) return@lanciaProtetto
            prefill = prefill + (nuovo.id to ultime)
            _stato.update { s ->
                val cambiato = s.copy(
                    esercizi = s.esercizi.toMutableList().also { it[indice] = it[indice].sostituitoCon(nuovo) }
                )
                if (indice == s.indiceEsercizio) cambiato.conValoriIniziali() else cambiato
            }
        }
    }

    fun saltaPreparazione() {
        if (_stato.value.fase == Fase.PREPARAZIONE && doppioTocco.accetta()) avviaSerie()
    }

    fun iniziaProssimo() {
        if (_stato.value.fase == Fase.ATTESA) iniziaEsercizio()
    }

    fun saltaRecupero() {
        if (_stato.value.fase == Fase.RECUPERO) avviaSerie()
    }

    /** Tocco su "Completa". */
    fun completaSerie() {
        if (_stato.value.fase != Fase.ESECUZIONE || !doppioTocco.accetta()) return
        registraPasso()
    }

    /** Registra la serie corrente con i valori effettivi (tocco dell'utente o tempo scaduto). */
    private fun registraPasso() {
        val s = _stato.value
        if (!s.pronta || s.fase != Fase.ESECUZIONE) return
        val passo = passi.getOrNull(s.indicePasso) ?: return
        val es = s.esercizi.getOrNull(passo.idxEsercizio) ?: return
        val serie = es.serie.getOrNull(passo.idxSerie) ?: return
        conto.ferma()
        val eseguita = SerieEseguita(
            sessioneId = sessioneId,
            esercizioId = es.esercizioId,
            numero = serie.numero,
            ripetizioni = if (es.modalita == Modalita.RIPETIZIONI) s.ripInput.toIntOrNull() else null,
            pesoKg = if (es.modalita == Modalita.RIPETIZIONI) s.pesoInput.toFloatOrNull() else null,
            durataSec = if (es.modalita == Modalita.TEMPO) {
                durataEseguita(serie.durataSec, s.secondiRimanenti, s.secondiTrascorsi)
            } else null,
            completata = true
        )
        // Scope di processo: il salvataggio non si perde se la UI se ne va subito dopo.
        daSalvare += eseguita
        scope.launch { salvaSerieInSospeso() }
        completati += s.indicePasso
        saltati -= s.indicePasso
        _stato.update {
            it.copy(
                log = it.log + eseguita,
                nomiEseguiti = it.nomiEseguiti + (es.esercizioId to es.nome),
                catalogoEseguiti = it.catalogoEseguiti + (es.esercizioId to es.nomeCatalogo),
            )
        }
        avanza(saltato = false)
    }

    /** Salta la serie/passo corrente senza registrarla. */
    fun saltaSerie() {
        if (!inEsecuzione || _stato.value.fase != Fase.ESECUZIONE || !doppioTocco.accetta()) return
        conto.ferma()
        val cur = _stato.value.indicePasso
        if (cur !in completati) saltati += cur
        avanza(saltato = true)
    }

    /** Salta il resto del blocco corrente (esercizio singolo o superset): le serie già fatte restano. */
    fun saltaEsercizio() {
        if (!inEsecuzione) return
        conto.ferma()
        val cur = _stato.value.indicePasso
        val blocco = passi.getOrNull(cur)?.let { bloccoDi(it.idxEsercizio) } ?: emptySet()
        passi.indices.filter { passi[it].idxEsercizio in blocco && !risolto(it) }.forEach { saltati += it }
        val next = prossimoPasso(cur)
        if (next == null) terminaAllenamento()
        else {
            posizionaSu(next)
            _stato.update { it.copy(fase = Fase.ATTESA) }
        }
    }

    /**
     * Va a un esercizio dalla lista, riprendendo dalla sua prima serie non ancora fatta.
     * Se si è in recupero, il recupero continua col tempo rimasto e poi parte l'esercizio scelto.
     * Le serie già completate (di qualsiasi esercizio) restano registrate.
     */
    fun vaiAEsercizio(indice: Int) {
        if (!inEsecuzione) return
        val target = passi.indices.firstOrNull { passi[it].idxEsercizio == indice && !risolto(it) } ?: return
        val s = _stato.value
        if (target == s.indicePasso) return
        posizionaSu(target)
        if (s.fase == Fase.RECUPERO) return   // il conto alla rovescia prosegue; alla fine avviaSerie() usa il nuovo passo
        conto.ferma()
        _stato.update { it.copy(fase = Fase.ATTESA) }
    }

    fun terminaOra() {
        if (inEsecuzione && _stato.value.pronta) terminaAllenamento()
    }

    // ---------- transizioni interne ----------

    /**
     * "Inizia" su un nuovo esercizio: se è a tempo prima c'è il conto alla rovescia per mettersi in
     * posizione, altrimenti parte subito. Tra le serie dello stesso esercizio il recupero basta.
     */
    private fun iniziaEsercizio() {
        if (_stato.value.esercizioCorrente?.modalita != Modalita.TEMPO) return avviaSerie()
        // Il "Salta" della preparazione compare sotto il dito che ha appena toccato "Inizia".
        doppioTocco.segna()
        _stato.update { it.copy(fase = Fase.PREPARAZIONE) }
        avviaCountdown(SEC_VIA_SERIE_A_TEMPO) { finePreparazione() }
    }

    private fun finePreparazione() {
        // Un tocco su "Salta" arrivato allo scadere non deve saltare o completare la serie appena partita.
        doppioTocco.segna()
        avviaSerie()
    }

    /** La serie comincia davvero: da qui corre il suo tempo. */
    private fun avviaSerie() {
        val s = _stato.value
        val passo = passi.getOrNull(s.indicePasso) ?: return terminaAllenamento()
        val es = s.esercizi.getOrNull(passo.idxEsercizio) ?: return terminaAllenamento()
        val serie = es.serie.getOrNull(passo.idxSerie) ?: return terminaAllenamento()
        _stato.update {
            it.copy(fase = Fase.ESECUZIONE, secondiRimanenti = 0, secondiTotali = 0, secondiTrascorsi = 0)
                .conValoriIniziali()
        }
        val durata = serie.durataSec ?: 0
        when {
            es.modalita != Modalita.TEMPO -> conto.ferma()
            durata > 0 -> avviaCountdown(durata) {
                doppioTocco.segna()
                registraPasso()
            }
            // Senza durata la serie la chiude l'utente, come il lavoro libero del Timer.
            else -> conto.avviaInAvanti { t -> _stato.update { it.copy(secondiTrascorsi = t) } }
        }
    }

    private fun avanza(saltato: Boolean) {
        val curIdx = _stato.value.indicePasso
        val cur = passi.getOrNull(curIdx) ?: return terminaAllenamento()
        val nextIdx = prossimoPasso(curIdx)
        if (nextIdx == null) {
            terminaAllenamento()
            return
        }
        val next = passi[nextIdx]
        posizionaSu(nextIdx)
        // Si cambia blocco (nuovo esercizio o ritorno a uno lasciato indietro): si aspetta "Inizia".
        val stessoBlocco = nextIdx == curIdx + 1 && !next.attesaPrima
        val cambioBlocco = !stessoBlocco && next.idxEsercizio !in bloccoDi(cur.idxEsercizio)
        val rec = if (saltato) 0 else cur.recuperoDopo
        when {
            cambioBlocco || next.attesaPrima -> _stato.update { it.copy(fase = Fase.ATTESA) }
            rec > 0 -> {
                _stato.update { it.copy(fase = Fase.RECUPERO) }
                avviaCountdown(rec) { finePreparazione() }
            }
            else -> avviaSerie()
        }
    }

    /** Aggiorna l'indice di passo e i derivati: esercizi completati, serie fatte, avanzamento. */
    private fun posizionaSu(idx: Int) {
        val p = passi.getOrNull(idx)
        val esercizi = _stato.value.esercizi
        val fatti = esercizi.indices.filter { e ->
            val suoi = passi.indices.filter { passi[it].idxEsercizio == e }
            suoi.isNotEmpty() && suoi.all { risolto(it) }
        }.toSet()
        val serieFatte = esercizi.indices.associateWith { e -> completati.count { passi[it].idxEsercizio == e } }
        _stato.update {
            it.copy(
                indicePasso = idx,
                indiceEsercizio = p?.idxEsercizio ?: it.indiceEsercizio,
                indiceSerie = p?.idxSerie ?: it.indiceSerie,
                eserciziFatti = fatti,
                serieFatte = serieFatte,
                passiFatti = completati.size + saltati.count { it !in completati }
            )
        }
    }

    /** Lo stato con ripetizioni e peso proposti per la sua serie corrente. */
    private fun StatoEsecuzione.conValoriIniziali(): StatoEsecuzione {
        val es = esercizioCorrente ?: return this
        val serie = serieCorrente ?: return this
        val occorrenza = esercizi.take(indiceEsercizio).count { it.esercizioId == es.esercizioId }
        val (rip, peso) = valoriIniziali(serie, prefill[es.esercizioId].orEmpty(), occorrenza)
        val obiettivo = es.obiettivo ?: return copy(ripInput = rip, pesoInput = peso)
        // Con Gym Buddy vale l'obiettivo. In calibrazione resta il carico appena corretto.
        val corretto = if (es.calibrazione) log.lastOrNull { it.esercizioId == es.esercizioId }?.pesoKg else null
        val carico = corretto ?: obiettivo.caricoKg
        return copy(
            ripInput = obiettivo.ripetizioni.toString(),
            pesoInput = carico?.let(::formattaKgRuota) ?: peso,
        )
    }

    private fun EsercizioEsec.conGymBuddy(schedaId: Long): EsercizioEsec {
        if (modalita != Modalita.RIPETIZIONI) return this
        val progresso = amb.progressoBuddy(ChiaveBuddy(schedaId, esercizioId))?.takeIf { it.attivo } ?: return this
        val buddy = progresso.stato ?: return this
        return copy(obiettivo = buddy.obiettivo, calibrazione = buddy.calibrazione)
    }

    private fun seriePerEsercizio(esercizi: List<EsercizioEsec>, id: Long): Int =
        esercizi.filter { it.esercizioId == id && it.modalita == Modalita.RIPETIZIONI }.sumOf { it.serie.size }

    /** A fine allenamento aggiorna contatori e proposte. Gli obiettivi cambiano solo se l'utente accetta. */
    private fun valutaGymBuddy() {
        val schedaId = _schedaInCorso.value ?: return
        val s = _stato.value
        val nuovi = mutableMapOf<ChiaveBuddy, ProgressoBuddy>()
        for ((id, piano) in pianoBuddy) {
            val (previste, obiettivo) = piano
            // Sostituito durante l'allenamento: non si può giudicare.
            if (seriePerEsercizio(s.esercizi, id) != previste) continue
            val chiave = ChiaveBuddy(schedaId, id)
            val salvato = amb.progressoBuddy(chiave) ?: continue
            // Obiettivo cambiato nel frattempo: le serie erano fatte su quello vecchio.
            if (salvato.stato?.obiettivo != obiettivo) continue
            // Il verso del carico non è salvato: vale quello dell'esercizio fatto.
            val nome = s.esercizi.firstOrNull { it.esercizioId == id }?.nomeCatalogo
                ?: s.catalogoEseguiti[id] ?: s.nomiEseguiti[id]
            val progresso = salvato.copy(
                regole = salvato.regole.copy(caricoInverso = nome != null && caricoInverso(nome))
            )
            val fatte = s.log
                .filter { it.esercizioId == id && it.durataSec == null }
                .map { SerieFatta(it.ripetizioni ?: 0, it.pesoKg?.takeIf { kg -> kg > 0f }) }
            val aggiornato = progresso.dopoAllenamento(previste, fatte)
            if (aggiornato != progresso) nuovi[chiave] = aggiornato
        }
        amb.salvaBuddy(nuovi)
        pianoBuddy = emptyMap() // una sola valutazione per allenamento
    }

    /** [fine]: quando è finito; per un allenamento interrotto è l'ultima attività, non adesso. */
    private fun terminaAllenamento(fine: Long = orologio.adesso()) {
        conto.ferma()
        AllenamentoAttivo.aggiorna(null)
        valutaGymBuddy()
        _stato.update { it.copy(fase = Fase.COMPLETATO, durataTotaleMs = fine - dataInizio) }
        terminaSessioneDb(fine)
        dimenticaRipresa()
    }

    /**
     * Salva nell'ordine le serie in attesa. Al primo errore si ferma (le altre restano in coda per il
     * prossimo tentativo) e lo dice all'utente una volta sola. true = coda vuota.
     */
    private suspend fun salvaSerieInSospeso(): Boolean = salvataggio.withLock {
        while (daSalvare.isNotEmpty()) {
            try {
                amb.registraSerie(daSalvare.first())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GymBro", "Serie non salvata (${daSalvare.size} in attesa)", e)
                if (!avvisatoNonSalvate) {
                    avvisatoNonSalvate = true
                    mostra(
                        Avviso(
                            "Serie non salvata",
                            "Il salvataggio non è riuscito (${motivoErrore(e)}). Puoi continuare: la serie " +
                                "resta nel riepilogo e riprovo a salvarla con la prossima e a fine allenamento."
                        )
                    )
                }
                return@withLock false
            }
            daSalvare.removeAt(0)
        }
        avvisatoNonSalvate = false
        true
    }

    private fun terminaSessioneDb(fine: Long = orologio.adesso()) {
        val id = sessioneId
        if (id == 0L) return
        val vuota = _stato.value.log.isEmpty()
        scope.lanciaProtetto("Chiusura della sessione", seErrore = { e ->
            mostra(
                Avviso(
                    "Allenamento non chiuso",
                    "Non è stato possibile chiudere l'allenamento nello storico (${motivoErrore(e)}). " +
                        "Le serie salvate restano."
                )
            )
        }) {
            if (vuota) {
                // Nessuna serie fatta: non è un allenamento, non deve comparire nei Report.
                amb.scartaSessioneVuota(id)
                return@lanciaProtetto
            }
            // Ultimo tentativo per le serie rimaste indietro, prima di chiudere.
            if (!salvaSerieInSospeso()) {
                val perse = daSalvare.count { it.sessioneId == id }
                daSalvare.removeAll { it.sessioneId == id }
                avvisatoNonSalvate = false
                _avviso.value = Avviso(
                    "Serie non salvate",
                    "$perse ${if (perse == 1) "serie non è stata salvata" else "serie non sono state salvate"} " +
                        "nello storico: il database continua a dare errore. Le vedi nel riepilogo, ma non " +
                        "conteranno nei report."
                )
            }
            amb.terminaSessione(id, fine)
        }
    }

    /** Conto alla rovescia della fase corrente, con i secondi pubblicati nello stato. */
    private fun avviaCountdown(secondi: Int, alFine: () -> Unit) {
        fineConto = orologio.adesso() + secondi * 1000L
        _stato.update { it.copy(secondiRimanenti = secondi, secondiTotali = secondi) }
        conto.avvia(
            secondi,
            onSecondo = { rim -> _stato.update { it.copy(secondiRimanenti = rim) } },
            onFine = alFine
        )
    }
}

/**
 * Costruisce la sequenza reale dei passi. Esercizi con lo stesso [EsercizioEsec.gruppo] consecutivi
 * formano un superset: un set per esercizio a giro, recupero solo a fine giro, nessuna attesa interna.
 */
internal fun costruisciPassi(esercizi: List<EsercizioEsec>): List<Passo> {
    val passi = mutableListOf<Passo>()
    var i = 0
    var primoBlocco = true
    while (i < esercizi.size) {
        val gruppo = esercizi[i].gruppo
        if (gruppo == null) {
            val e = esercizi[i]
            e.serie.indices.forEach { s ->
                passi += Passo(
                    idxEsercizio = i,
                    idxSerie = s,
                    recuperoDopo = e.serie[s].recuperoSec,
                    attesaPrima = s == 0 && !primoBlocco
                )
            }
            i++
        } else {
            val indici = mutableListOf(i)
            var j = i + 1
            while (j < esercizi.size && esercizi[j].gruppo == gruppo) { indici += j; j++ }
            val maxSerie = indici.maxOf { esercizi[it].serie.size }
            for (r in 0 until maxSerie) {
                val attivi = indici.filter { r < esercizi[it].serie.size }
                attivi.forEachIndexed { k, idx ->
                    val ultimoDelGiro = k == attivi.lastIndex
                    passi += Passo(
                        idxEsercizio = idx,
                        idxSerie = r,
                        recuperoDopo = if (ultimoDelGiro) esercizi[idx].serie[r].recuperoSec else 0,
                        attesaPrima = r == 0 && k == 0 && !primoBlocco
                    )
                }
            }
            i = j
        }
        primoBlocco = false
    }
    return passi
}
