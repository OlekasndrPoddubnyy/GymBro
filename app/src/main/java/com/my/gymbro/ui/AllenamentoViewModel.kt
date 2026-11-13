package com.my.gymbro.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
import com.my.gymbro.TimerService
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GymBroRepository
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.SerieEseguita
import com.my.gymbro.data.Sessione
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SEC_PREPARAZIONE = 10

/** Un passo di esecuzione: una serie di un esercizio, nell'ordine reale (interlacciato per i superset). */
private data class Passo(
    val idxEsercizio: Int,
    val idxSerie: Int,
    val recuperoDopo: Int,
    val attesaPrima: Boolean,
)

class AllenamentoViewModel(
    private val repo: GymBroRepository,
    private val app: Application,
) : ViewModel() {

    private val _stato = MutableStateFlow(StatoEsecuzione())
    val stato: StateFlow<StatoEsecuzione> = _stato.asStateFlow()

    private val _segnali = MutableSharedFlow<Segnale>(extraBufferCapacity = 8)
    val segnali: SharedFlow<Segnale> = _segnali.asSharedFlow()

    /** Libreria esercizi per la sostituzione al volo. */
    val libreria: StateFlow<List<Esercizio>> =
        repo.esercizi.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var sessioneId: Long = 0
    private var schedaId: Long? = null
    private var dataInizio: Long = 0
    private var prefill: Map<Long, List<SerieEseguita>> = emptyMap()
    private var passi: List<Passo> = emptyList()
    private var timerJob: Job? = null
    private var avviata = false

    /** Passi (serie) realmente completati e quelli saltati: è da qui che si capisce cosa resta da fare. */
    private val completati = mutableSetOf<Int>()
    private val saltati = mutableSetOf<Int>()
    private fun risolto(i: Int) = i in completati || i in saltati

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

    fun avvia(idScheda: Long) {
        if (avviata) return
        avviata = true
        // Lo stato dell'allenamento è visibile al resto dell'app (barra "in corso") e al servizio
        // in primo piano, che tiene vivo il conteggio e suona/vibra anche con l'app ridotta.
        viewModelScope.launch {
            stato.collect { s ->
                AllenamentoAttivo.aggiorna(
                    if (!s.pronta || s.fase == Fase.COMPLETATO) null
                    else AllenamentoAttivo.Info(s.nomeScheda, descriviEsecuzione(s))
                )
            }
        }
        viewModelScope.launch {
            segnali.collect { AllenamentoAttivo.segnala(it) }
        }
        viewModelScope.launch {
            val completa = repo.caricaSchedaCompleta(idScheda)
            val nome = completa?.scheda?.nome ?: "Allenamento"
            val esercizi = completa?.esercizi
                ?.sortedBy { it.esercizioScheda.ordine }
                ?.map { ec ->
                    EsercizioEsec(
                        esercizioId = ec.esercizio.id,
                        nome = ec.esercizio.nome,
                        modalita = ec.esercizioScheda.modalita,
                        gruppo = ec.esercizioScheda.gruppoSuperset,
                        immaginePath = ec.esercizio.immaginePath,
                        descrizione = ec.esercizio.descrizione,
                        note = ec.esercizioScheda.note,
                        gruppoMuscolare = ec.esercizio.gruppoMuscolare,
                        serie = ec.serie.sortedBy { it.numero }.map { sp ->
                            SerieEsec(sp.numero, sp.durataSec, sp.ripetizioni, sp.pesoKg, sp.recuperoSec)
                        }.ifEmpty { listOf(SerieEsec(1, null, null, null, 60)) }
                    )
                }
                .orEmpty()

            dataInizio = System.currentTimeMillis()
            schedaId = idScheda
            sessioneId = repo.avviaSessione(
                Sessione(schedaId = idScheda, nomeScheda = nome, dataInizio = dataInizio)
            )
            prefill = esercizi.associate { it.esercizioId to repo.prefillEsercizio(it.esercizioId, sessioneId) }
            passi = costruisciPassi(esercizi)

            if (passi.isEmpty()) {
                _stato.update { it.copy(pronta = true, nomeScheda = nome, esercizi = esercizi, fase = Fase.COMPLETATO) }
                terminaSessioneDb()
                return@launch
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
            // Pubblica subito lo stato e avvia il servizio in primo piano (conteggio in background)
            AllenamentoAttivo.aggiorna(AllenamentoAttivo.Info(nome, descriviEsecuzione(_stato.value)))
            TimerService.start(app)
            avviaCountdown(SEC_PREPARAZIONE) { iniziaPasso() }
        }
    }

    // ---------- azioni utente ----------

    fun aggiornaRip(v: String) = _stato.update { it.copy(ripInput = v.filter(Char::isDigit)) }
    fun aggiornaPeso(v: String) = _stato.update { it.copy(pesoInput = v.filter { c -> c.isDigit() || c == '.' }) }

    /** Sostituisce l'esercizio [indice] con [nuovo] dalla libreria, mantenendo serie e modalità.
     *  Vale solo per questa sessione: la scheda salvata non viene toccata. */
    fun sostituisci(indice: Int, nuovo: Esercizio) {
        _stato.update { s ->
            val es = s.esercizi.getOrNull(indice) ?: return@update s
            s.copy(esercizi = s.esercizi.toMutableList().also {
                it[indice] = es.copy(
                    esercizioId = nuovo.id,
                    nome = nuovo.nome,
                    immaginePath = nuovo.immaginePath,
                    descrizione = nuovo.descrizione,
                    note = null, // le note di tecnica erano dell'esercizio sostituito
                    gruppoMuscolare = nuovo.gruppoMuscolare
                )
            })
        }
    }

    fun saltaPreparazione() {
        if (_stato.value.fase == Fase.PREPARAZIONE) iniziaPasso()
    }

    fun iniziaProssimo() {
        if (_stato.value.fase == Fase.ATTESA) iniziaPasso()
    }

    fun saltaRecupero() {
        if (_stato.value.fase == Fase.RECUPERO) iniziaPasso()
    }

    /** Conferma la serie corrente registrando i valori effettivi. */
    fun completaSerie() {
        val s = _stato.value
        val passo = passi.getOrNull(s.indicePasso) ?: return
        val es = s.esercizi.getOrNull(passo.idxEsercizio) ?: return
        val serie = es.serie.getOrNull(passo.idxSerie) ?: return
        timerJob?.cancel()
        val eseguita = SerieEseguita(
            sessioneId = sessioneId,
            esercizioId = es.esercizioId,
            numero = serie.numero,
            ripetizioni = if (es.modalita == Modalita.RIPETIZIONI) s.ripInput.toIntOrNull() else null,
            pesoKg = if (es.modalita == Modalita.RIPETIZIONI) s.pesoInput.toFloatOrNull() else null,
            durataSec = if (es.modalita == Modalita.TEMPO) serie.durataSec else null,
            completata = true
        )
        viewModelScope.launch { repo.registraSerie(eseguita) }
        completati += s.indicePasso
        saltati -= s.indicePasso
        _stato.update { it.copy(log = it.log + eseguita) }
        avanza(saltato = false)
    }

    /** Salta la serie/passo corrente senza registrarla. */
    fun saltaSerie() {
        timerJob?.cancel()
        val cur = _stato.value.indicePasso
        if (cur !in completati) saltati += cur
        avanza(saltato = true)
    }

    /** Salta il resto del blocco corrente (esercizio singolo o superset): le serie già fatte restano. */
    fun saltaEsercizio() {
        timerJob?.cancel()
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
        val target = passi.indices.firstOrNull { passi[it].idxEsercizio == indice && !risolto(it) } ?: return
        val s = _stato.value
        if (target == s.indicePasso) return
        posizionaSu(target)
        if (s.fase == Fase.RECUPERO) return   // il conto alla rovescia prosegue; alla fine iniziaPasso() usa il nuovo passo
        timerJob?.cancel()
        _stato.update { it.copy(fase = Fase.ATTESA) }
    }

    fun terminaOra() = terminaAllenamento()

    // ---------- transizioni interne ----------

    private fun iniziaPasso() {
        val s = _stato.value
        val passo = passi.getOrNull(s.indicePasso) ?: return terminaAllenamento()
        val es = s.esercizi.getOrNull(passo.idxEsercizio) ?: return terminaAllenamento()
        val serie = es.serie.getOrNull(passo.idxSerie) ?: return terminaAllenamento()
        val (rip, peso) = prefillPer(es, passo.idxSerie, serie)
        _stato.update { it.copy(fase = Fase.ESECUZIONE, ripInput = rip, pesoInput = peso, secondiRimanenti = 0) }
        if (es.modalita == Modalita.TEMPO) {
            avviaCountdown(serie.durataSec ?: 0) { completaSerie() }
        } else {
            timerJob?.cancel()
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
                avviaCountdown(rec) { iniziaPasso() }
            }
            else -> iniziaPasso()
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

    private fun prefillPer(es: EsercizioEsec, idxSerie: Int, serie: SerieEsec): Pair<String, String> {
        val ultima = prefill[es.esercizioId]?.getOrNull(idxSerie)
        val rip = ultima?.ripetizioni?.toString()
            ?: serie.ripetizioniPianificate?.let(::ultimoNumero).orEmpty()
        val peso = ultima?.pesoKg?.let(::formattaPesoVm)
            ?: serie.pesoPianificato?.let(::formattaPesoVm).orEmpty()
        return rip to peso
    }

    private fun terminaAllenamento() {
        timerJob?.cancel()
        AllenamentoAttivo.aggiorna(null)
        _stato.update { it.copy(fase = Fase.COMPLETATO, durataTotaleMs = System.currentTimeMillis() - dataInizio) }
        terminaSessioneDb()
    }

    private fun terminaSessioneDb() {
        val fine = System.currentTimeMillis()
        viewModelScope.launch {
            repo.terminaSessione(
                Sessione(
                    id = sessioneId,
                    schedaId = schedaId,
                    nomeScheda = _stato.value.nomeScheda,
                    dataInizio = dataInizio,
                    dataFine = fine
                )
            )
        }
    }

    /** Conto alla rovescia sull'orologio di sistema: resta esatto anche con l'app in background. */
    private fun avviaCountdown(secondi: Int, alFine: () -> Unit) {
        timerJob?.cancel()
        _stato.update { it.copy(secondiRimanenti = secondi, secondiTotali = secondi) }
        if (secondi <= 0) {
            alFine()
            return
        }
        val fine = SystemClock.elapsedRealtime() + secondi * 1000L
        timerJob = viewModelScope.launch {
            var ultimo = secondi
            while (true) {
                delay(200)
                val rim = ((fine - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                if (rim != ultimo) {
                    ultimo = rim
                    _stato.update { it.copy(secondiRimanenti = rim) }
                    if (rim in 1..3) _segnali.tryEmit(Segnale.TICK)
                }
                if (rim <= 0) break
            }
            _segnali.tryEmit(Segnale.FINE)
            alFine()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        AllenamentoAttivo.aggiorna(null)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                AllenamentoViewModel(app.repository, app)
            }
        }
    }
}

/**
 * Costruisce la sequenza reale dei passi. Esercizi con lo stesso [EsercizioEsec.gruppo] consecutivi
 * formano un superset: un set per esercizio a giro, recupero solo a fine giro, nessuna attesa interna.
 */
private fun costruisciPassi(esercizi: List<EsercizioEsec>): List<Passo> {
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

/** Ultimo gruppo di cifre in una stringa tipo "8-12" → "12". */
private fun ultimoNumero(s: String): String =
    Regex("\\d+").findAll(s).lastOrNull()?.value ?: ""

private fun formattaPesoVm(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else kg.toString()
