package com.my.gymbro.ui

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SchedeViewModel(private val repo: GymBroRepository) : ViewModel() {

    val schede: StateFlow<List<Scheda>> =
        repo.schede.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Libreria esercizi, usata dal picker nell'editor. */
    val libreria: StateFlow<List<Esercizio>> =
        repo.esercizi.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun scheda(id: Long): Flow<SchedaConEsercizi?> = repo.schedaConEsercizi(id)

    suspend fun carica(id: Long): SchedaInModifica? = repo.caricaSchedaCompleta(id)?.toInModifica()

    fun salva(s: SchedaInModifica, onFatto: () -> Unit) = viewModelScope.launch {
        val (scheda, righe) = s.toEntities()
        repo.salvaScheda(scheda, righe)
        onFatto()
    }

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
