package com.my.gymbro.ui

import android.database.sqlite.SQLiteConstraintException
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
import com.my.gymbro.data.Impostazioni
import com.my.gymbro.data.nomeCatalogo
import com.my.gymbro.data.nomeGiaUsato
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn

/** Cosa mostra la sezione Esercizi: la lista o il form (nuovo esercizio = null). */
sealed interface EditState {
    data object None : EditState
    data class Form(val esercizio: Esercizio?) : EditState
}

class EserciziViewModel(
    private val repo: GymBroRepository,
    private val impostazioni: Impostazioni,
) : ViewModel() {

    /** Nel ViewModel, così il form aperto resta tale anche cambiando sezione o ruotando lo schermo. */
    var editing by mutableStateOf<EditState>(EditState.None)
        private set

    fun apriForm(esercizio: Esercizio?) { editing = EditState.Form(esercizio) }
    fun chiudiForm() { editing = EditState.None }

    val esercizi: StateFlow<List<Esercizio>> =
        repo.esercizi.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _messaggi = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Messaggi una-tantum per la UI (es. snackbar di errore). */
    val messaggi: SharedFlow<String> = _messaggi.asSharedFlow()

    /** Perché il form non è stato salvato (null = nessun problema): il form resta aperto con i dati. */
    var erroreSalvataggio by mutableStateOf<String?>(null)
        private set

    fun chiudiErroreSalvataggio() { erroreSalvataggio = null }

    private var salvataggio: Job? = null

    /**
     * Inserisce un nuovo esercizio (id == 0) o aggiorna quello esistente. Due esercizi con lo stesso
     * nome non sono ammessi (il form lo impedisce già; qui per sicurezza, con la libreria aggiornata).
     * Il form si chiude solo a salvataggio riuscito; l'immagine sostituita si elimina solo allora.
     */
    fun salva(esercizio: Esercizio) {
        if (salvataggio?.isActive == true) return // doppio tocco su "Salva"
        val immaginePrima = (editing as? EditState.Form)?.esercizio?.immaginePath
        salvataggio = viewModelScope.lanciaProtetto("Salvataggio dell'esercizio", seErrore = { e ->
            erroreSalvataggio = "Non è stato possibile salvare \"${esercizio.nome}\": ${motivoErrore(e)}. " +
                "Le modifiche sono ancora nel modulo: puoi riprovare."
        }) {
            if (nomeGiaUsato(esercizio.nome, repo.eserciziOra(), esercizio.id)) {
                erroreSalvataggio = "Esiste già un esercizio chiamato \"${esercizio.nome}\"."
                return@lanciaProtetto
            }
            if (esercizio.id == 0L) repo.aggiungiEsercizio(esercizio)
            else repo.aggiornaEsercizio(esercizio)
            if (immaginePrima != null && immaginePrima != esercizio.immaginePath) ImageStorage.elimina(immaginePrima)
            editing = EditState.None
        }
    }

    /**
     * Elimina l'esercizio. Se è in una scheda o nello storico degli allenamenti (FK RESTRICT) non si
     * può: il messaggio spiega quale dei due casi, così si sa cosa fare.
     */
    fun elimina(esercizio: Esercizio) {
        val nome = "\"${esercizio.nome}\""
        viewModelScope.lanciaProtetto("Eliminazione dell'esercizio", seErrore = { e ->
            _messaggi.tryEmit(
                if (e is SQLiteConstraintException) "Impossibile eliminare $nome: è in uso." // comparso nel frattempo
                else "Impossibile eliminare $nome: ${motivoErrore(e)}."
            )
        }) {
            val schede = repo.schedeConEsercizio(esercizio.id)
            val motivo = when {
                schede.size == 1 -> "è nella scheda \"${schede[0]}\". Toglilo prima da lì."
                schede.size > 1 -> "è nelle schede ${schede.joinToString(", ") { "\"$it\"" }}. Toglilo prima da lì."
                AllenamentoEngine.usaEsercizio(esercizio.id) -> "fa parte dell'allenamento in corso. Terminalo prima."
                repo.esercizioNelloStorico(esercizio.id) -> "compare nello storico dei tuoi allenamenti."
                else -> null
            }
            if (motivo != null) {
                _messaggi.tryEmit("Impossibile eliminare $nome: $motivo")
                return@lanciaProtetto
            }
            repo.eliminaEsercizio(esercizio)
            ImageStorage.elimina(esercizio.immaginePath)
            // Un predefinito eliminato non deve ricomparire al prossimo aggiornamento del catalogo.
            // Per chiave: se era stato rinominato, il catalogo lo conosce col nome originale.
            if (esercizio.predefinito) impostazioni.segnaPredefinitoEliminato(esercizio.nomeCatalogo)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                EserciziViewModel(app.repository, app.impostazioni)
            }
        }
    }
}
