package com.my.gymbro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GymBroRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Collegamento tra la schermata dell'allenamento e [AllenamentoEngine]. Lo stato vive nel motore
 * (di processo), non qui: se il ViewModel viene distrutto l'allenamento prosegue.
 */
class AllenamentoViewModel(repo: GymBroRepository) : ViewModel() {

    val stato: StateFlow<StatoEsecuzione> = AllenamentoEngine.stato

    /** Libreria esercizi per la sostituzione al volo. */
    val libreria: StateFlow<List<Esercizio>> =
        repo.esercizi.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun aggiornaRip(passo: Int, v: String) = AllenamentoEngine.aggiornaRip(passo, v)
    fun aggiornaPeso(passo: Int, v: String) = AllenamentoEngine.aggiornaPeso(passo, v)
    fun sostituisci(indice: Int, nuovo: Esercizio) = AllenamentoEngine.sostituisci(indice, nuovo)
    fun saltaPreparazione() = AllenamentoEngine.saltaPreparazione()
    fun iniziaProssimo() = AllenamentoEngine.iniziaProssimo()
    fun saltaRecupero() = AllenamentoEngine.saltaRecupero()
    fun completaSerie() = AllenamentoEngine.completaSerie()
    fun saltaSerie() = AllenamentoEngine.saltaSerie()
    fun saltaEsercizio() = AllenamentoEngine.saltaEsercizio()
    fun vaiAEsercizio(indice: Int) = AllenamentoEngine.vaiAEsercizio(indice)
    fun terminaOra() = AllenamentoEngine.terminaOra()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                AllenamentoViewModel(app.repository)
            }
        }
    }
}
