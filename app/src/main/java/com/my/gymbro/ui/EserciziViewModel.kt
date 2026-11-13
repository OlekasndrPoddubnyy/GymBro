package com.my.gymbro.ui

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GymBroRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EserciziViewModel(private val repo: GymBroRepository) : ViewModel() {

    val esercizi: StateFlow<List<Esercizio>> =
        repo.esercizi.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _messaggi = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Messaggi una-tantum per la UI (es. snackbar di errore). */
    val messaggi: SharedFlow<String> = _messaggi.asSharedFlow()

    /** Inserisce un nuovo esercizio (id == 0) o aggiorna quello esistente. */
    fun salva(esercizio: Esercizio) = viewModelScope.launch {
        if (esercizio.id == 0L) repo.aggiungiEsercizio(esercizio)
        else repo.aggiornaEsercizio(esercizio)
    }

    fun elimina(esercizio: Esercizio) = viewModelScope.launch {
        try {
            repo.eliminaEsercizio(esercizio)
            ImageStorage.elimina(esercizio.immaginePath)
        } catch (_: SQLiteConstraintException) {
            // FK RESTRICT: l'esercizio è referenziato da una scheda
            _messaggi.tryEmit("Impossibile eliminare \"${esercizio.nome}\": è usato in una scheda")
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                EserciziViewModel(app.repository)
            }
        }
    }
}
