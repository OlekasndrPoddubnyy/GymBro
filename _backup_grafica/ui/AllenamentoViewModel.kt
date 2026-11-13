package com.my.gymbro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
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

class AllenamentoViewModel(private val repo: GymBroRepository) : ViewModel() {

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

    fun avvia(idScheda: Long) {
        if (avviata) return
        avviata = true
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
                it[indice] = es.copy(esercizioId = nuovo.id, nome = nuovo.nome)
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
        _stato.update { it.copy(log = it.log + eseguita) }
        avanza(saltato = false)
    }

    /** Salta la serie/passo corrente senza registrarla. */
    fun saltaSerie() {
        timerJob?.cancel()
        avanza(saltato = true)
    }

    /** Salta l'intero blocco corrente (esercizio singolo o superset). */
    fun saltaEsercizio() {
        timerJob?.cancel()
        val cur = _stato.value.indicePasso
        val next = (cur + 1 until passi.size).firstOrNull { passi[it].attesaPrima }
        if (next == null) terminaAllenamento()
        else {
            posizionaSu(next)
            _stato.update { it.copy(fase = Fase.ATTESA) }
        }
    }

    /** Salta direttamente a un esercizio dalla lista. */
    fun vaiAEsercizio(indice: Int) {
        val p = passi.indexOfFirst { it.idxEsercizio == indice }
        if (p < 0) return
        timerJob?.cancel()
        posizionaSu(p)
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
        val nextIdx = curIdx + 1
        if (nextIdx >= passi.size) {
            terminaAllenamento()
            return
        }
        val next = passi[nextIdx]
        posizionaSu(nextIdx)
        val rec = if (saltato) 0 else cur.recuperoDopo
        when {
            next.attesaPrima -> _stato.update { it.copy(fase = Fase.ATTESA) }
            rec > 0 -> {
                _stato.update { it.copy(fase = Fase.RECUPERO) }
                avviaCountdown(rec) { iniziaPasso() }
            }
            else -> iniziaPasso()
        }
    }

    /** Aggiorna l'indice di passo e gli indici/derivati nello stato (esercizi completati inclusi). */
    private fun posizionaSu(idx: Int) {
        val p = passi.getOrNull(idx)
        val fatti = _stato.value.esercizi.indices.filter { e ->
            val ultimo = passi.indexOfLast { it.idxEsercizio == e }
            ultimo in 0 until idx
        }.toSet()
        _stato.update {
            it.copy(
                indicePasso = idx,
                indiceEsercizio = p?.idxEsercizio ?: it.indiceEsercizio,
                indiceSerie = p?.idxSerie ?: it.indiceSerie,
                eserciziFatti = fatti
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

    private fun avviaCountdown(secondi: Int, alFine: () -> Unit) {
        timerJob?.cancel()
        _stato.update { it.copy(secondiRimanenti = secondi, secondiTotali = secondi) }
        if (secondi <= 0) {
            alFine()
            return
        }
        timerJob = viewModelScope.launch {
            var rim = secondi
            while (rim > 0) {
                delay(1000)
                rim--
                _stato.update { it.copy(secondiRimanenti = rim) }
                if (rim in 1..3) _segnali.tryEmit(Segnale.TICK)
            }
            _segnali.tryEmit(Segnale.FINE)
            alFine()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                AllenamentoViewModel(app.repository)
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
