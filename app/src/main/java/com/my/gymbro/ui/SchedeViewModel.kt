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
import com.my.gymbro.data.Scheda
import com.my.gymbro.data.SchedaConEsercizi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Navigazione interna della sezione Schede. */
sealed interface SchedeNav {
    data object Lista : SchedeNav
    data class Dettaglio(val schedaId: Long) : SchedeNav
    data class Editor(val schedaId: Long?) : SchedeNav
}

class SchedeViewModel(private val repo: GymBroRepository) : ViewModel() {

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

    fun elimina(scheda: Scheda) = viewModelScope.launch { repo.eliminaScheda(scheda) }

    fun duplica(scheda: Scheda) = viewModelScope.launch {
        val completa = repo.caricaSchedaCompleta(scheda.id) ?: return@launch
        val copia = completa.toInModifica().copy(
            id = 0,
            nome = completa.scheda.nome + " (copia)",
            dataCreazione = System.currentTimeMillis()
        )
        val (s, righe) = copia.toEntities()
        repo.salvaScheda(s, righe)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                SchedeViewModel(app.repository)
            }
        }
    }
}
