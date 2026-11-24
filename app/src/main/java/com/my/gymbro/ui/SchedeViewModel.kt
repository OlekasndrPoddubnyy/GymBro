package com.my.gymbro.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GymBroRepository
import com.my.gymbro.data.MemoriaBuddy
import com.my.gymbro.data.Scheda
import com.my.gymbro.data.SchedaConEsercizi
import com.my.gymbro.data.QrNonValido
import com.my.gymbro.data.SchedaCondivisa
import com.my.gymbro.data.decodificaSchedaCondivisa
import com.my.gymbro.data.perCondividere
import com.my.gymbro.data.qrPerScheda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Navigazione interna della sezione Schede. */
sealed interface SchedeNav {
    data object Lista : SchedeNav
    data class Dettaglio(val schedaId: Long) : SchedeNav
    data class Editor(val schedaId: Long?) : SchedeNav
    /** Fotocamera (o foto) per leggere il QR di una scheda condivisa. */
    data object Scansione : SchedeNav
    /** Scheda letta da un QR, non ancora salvata. */
    data class Anteprima(val scheda: SchedaCondivisa) : SchedeNav
}

class SchedeViewModel(
    private val repo: GymBroRepository,
    private val memoria: MemoriaBuddy,
) : ViewModel() {

    val schede: StateFlow<List<Scheda>> =
        repo.schede.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Libreria esercizi, usata dal picker nell'editor. */
    val libreria: StateFlow<List<Esercizio>> =
        repo.esercizi.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Navigazione e bozza vivono qui (non nella composizione): cambiare sezione dalla barra in
    // basso o ruotare lo schermo non fa perdere la scheda che si sta modificando.

    var nav by mutableStateOf<SchedeNav>(SchedeNav.Lista)
        private set

    /** Scheda in modifica (null = in caricamento o editor chiuso). */
    var bozza by mutableStateOf<SchedaInModifica?>(null)
        private set

    /** Versione di partenza della bozza, per sapere se ci sono modifiche da non perdere. */
    private var originale: SchedaInModifica? = null
    private var caricamento: Job? = null
    private var salvataggio: Job? = null

    val bozzaModificata: Boolean get() = bozza != null && bozza != originale

    /** Perché l'ultimo salvataggio non è riuscito (null = nessun problema). */
    var erroreSalvataggio by mutableStateOf<String?>(null)
        private set

    fun chiudiErroreSalvataggio() { erroreSalvataggio = null }

    fun mostraLista() { nav = SchedeNav.Lista }
    fun apri(id: Long) { nav = SchedeNav.Dettaglio(id) }

    fun apriEditor(id: Long?) {
        nav = SchedeNav.Editor(id)
        bozza = null
        originale = null
        caricamento?.cancel()
        caricamento = viewModelScope.launch {
            val iniziale = id?.let { repo.caricaSchedaCompleta(it)?.toInModifica() } ?: SchedaInModifica()
            originale = iniziale
            bozza = iniziale
        }
    }

    fun modificaBozza(blocco: (SchedaInModifica) -> SchedaInModifica) {
        bozza = bozza?.let(blocco)
    }

    /** Esce dall'editor senza salvare: torna al dettaglio (scheda esistente) o alla lista. */
    fun chiudiEditor() {
        val id = (nav as? SchedeNav.Editor)?.schedaId
        caricamento?.cancel()
        bozza = null
        originale = null
        nav = if (id == null) SchedeNav.Lista else SchedeNav.Dettaglio(id)
    }

    fun salvaBozza() {
        val s = bozza ?: return
        if (s.nome.isBlank() || salvataggio?.isActive == true) return
        salvataggio = viewModelScope.launch {
            // La bozza resta aperta cambiando sezione: intanto un suo esercizio può essere stato eliminato.
            val mancanti = s.eserciziMancanti(repo.esercizi.first().mapTo(HashSet()) { it.id })
            if (mancanti.isNotEmpty()) {
                erroreSalvataggio = "Non sono più in libreria: ${mancanti.joinToString(", ") { "\"$it\"" }}. " +
                    "Rimuovili dalla scheda e salva di nuovo."
                return@launch
            }
            val (scheda, righe) = s.toEntities()
            repo.salvaScheda(scheda, righe)
            bozza = null
            originale = null
            nav = SchedeNav.Lista
        }
    }

    fun scheda(id: Long): Flow<SchedaConEsercizi?> = repo.schedaConEsercizi(id)

    fun elimina(scheda: Scheda) = viewModelScope.launch {
        repo.eliminaScheda(scheda)
        memoria.dimenticaScheda(scheda.id)
    }

    /** La copia ha anche gli obiettivi e i contatori di Gym Buddy dell'originale. */
    fun duplica(scheda: Scheda) = viewModelScope.launch {
        val completa = repo.caricaSchedaCompleta(scheda.id) ?: return@launch
        val copia = completa.toInModifica().copy(
            id = 0,
            nome = completa.scheda.nome + " (copia)",
            dataCreazione = System.currentTimeMillis()
        )
        val (s, righe) = copia.toEntities()
        val nuovaId = repo.salvaScheda(s, righe)
        memoria.copiaScheda(da = scheda.id, a = nuovaId)
    }

    // ---------- Condivisione con QR ----------

    /** QR da mostrare (null = nessuno). */
    var qr by mutableStateOf<QrDaMostrare?>(null)
        private set

    /** Perché un QR non è stato letto o la scheda ricevuta non è stata salvata (null = nessun problema). */
    var erroreQr by mutableStateOf<String?>(null)
        private set

    var salvandoCondivisa by mutableStateOf(false)
        private set

    /** Ultimo QR non valido inquadrato: la fotocamera lo rivede a ogni fotogramma, l'errore si mostra una volta. */
    private var scartato: String? = null

    fun condividi(schedaId: Long) = viewModelScope.launch {
        val completa = repo.caricaSchedaCompleta(schedaId) ?: return@launch
        val pronto = withContext(Dispatchers.Default) { qrPerScheda(completa.perCondividere()) }
        qr = QrDaMostrare(completa.scheda.nome, pronto)
    }

    fun chiudiQr() { qr = null }
    fun chiudiErroreQr() { erroreQr = null }

    fun apriScansione() {
        scartato = null
        nav = SchedeNav.Scansione
    }

    fun qrLetto(testo: String, daFoto: Boolean) {
        if (nav != SchedeNav.Scansione || erroreQr != null) return
        if (!daFoto && testo == scartato) return
        nav = try {
            SchedeNav.Anteprima(decodificaSchedaCondivisa(testo))
        } catch (e: QrNonValido) {
            scartato = testo
            erroreQr = e.message
            return
        }
    }

    fun fotoSenzaQr() {
        erroreQr = "Nella foto non c'è un QR leggibile. Prova con un'immagine più nitida, o ritagliata attorno al codice."
    }

    /** Salva la scheda in anteprima e la apre. */
    fun salvaCondivisa() {
        val anteprima = nav as? SchedeNav.Anteprima ?: return
        if (salvandoCondivisa) return
        salvandoCondivisa = true
        viewModelScope.launch {
            try {
                val id = repo.importaSchedaCondivisa(anteprima.scheda)
                if (nav == anteprima) nav = SchedeNav.Dettaglio(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erroreQr = "La scheda non è stata salvata. (${e.message ?: e.javaClass.simpleName})"
            } finally {
                salvandoCondivisa = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                SchedeViewModel(app.repository, app.memoriaBuddy)
            }
        }
    }
}
