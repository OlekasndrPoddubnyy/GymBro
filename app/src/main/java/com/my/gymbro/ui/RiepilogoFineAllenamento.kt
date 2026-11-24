package com.my.gymbro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.RigaReport
import com.my.gymbro.data.SerieEseguita
import com.my.gymbro.data.allenabile
import com.my.gymbro.data.caricoInverso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Un esercizio appena fatto confrontato con le sessioni precedenti. */
internal data class ConfrontoEsercizio(
    val primaVolta: Boolean,
    /**
     * Peso massimo mai sollevato prima; a corpo libero più ripetizioni di sempre, a tempo più secondi,
     * sulle macchine assistite meno aiuto.
     */
    val record: Boolean,
    /** Volume rispetto all'ultima volta che l'hai fatto; null se manca un termine di paragone. */
    val deltaVolume: Double?,
)

private fun volume(peso: Float?, rip: Int?): Double = (peso ?: 0f).toDouble() * (rip ?: 0)

/** Confronta le serie di [log] (tutte della stessa sessione) con lo [storico] delle altre sessioni. */
internal fun confrontaConStorico(log: List<SerieEseguita>, storico: List<RigaReport>): Map<Long, ConfrontoEsercizio> {
    val sessione = log.firstOrNull()?.sessioneId ?: return emptyMap()
    val prima = storico.filter { it.sessioneId != sessione }.groupBy { it.esercizioId }
    return log.groupBy { it.esercizioId }.mapValues { (id, serie) ->
        val prec = prima[id].orEmpty()
        if (prec.isEmpty()) return@mapValues ConfrontoEsercizio(primaVolta = true, record = false, deltaVolume = null)

        // Macchine assistite: il peso è aiuto, il record è averne usato meno di sempre e il volume non ha senso.
        val inverso = caricoInverso(prec.first().nomeEsercizio)
        val pesoOra = serie.mapNotNull { it.pesoKg }.filter { it > 0f }.maxOrNull()
        val pesoPrima = prec.mapNotNull { it.pesoKg }.filter { it > 0f }.maxOrNull()
        val aTempo = serie.all { it.durataSec != null }
        val record = when {
            // Stretching e cardio hanno durate fisse: un "record" non vorrebbe dire niente.
            !prec.first().gruppoMuscolare.allenabile -> false
            // Plank e simili: tenuto più a lungo di sempre.
            aTempo -> {
                val primaSec = prec.mapNotNull { it.durataSec }.maxOrNull()
                primaSec != null && serie.maxOf { it.durataSec ?: 0 } > primaSec
            }
            inverso -> serie.minOf { it.pesoKg ?: 0f } < prec.minOf { it.pesoKg ?: 0f }
            pesoOra != null -> pesoPrima != null && pesoOra > pesoPrima
            else -> {
                val ripOra = serie.mapNotNull { it.ripetizioni }.maxOrNull()
                val ripPrima = prec.filter { it.pesoKg == null }.mapNotNull { it.ripetizioni }.maxOrNull()
                ripOra != null && ripPrima != null && ripOra > ripPrima
            }
        }

        val ultima = prec.groupBy { it.sessioneId }.values.maxBy { it.first().dataInizio }
        val volOra = serie.sumOf { volume(it.pesoKg, it.ripetizioni) }
        val volPrima = ultima.sumOf { volume(it.pesoKg, it.ripetizioni) }
        ConfrontoEsercizio(
            primaVolta = false,
            record = record,
            deltaVolume = if (!inverso && volOra > 0 && volPrima > 0) volOra - volPrima else null
        )
    }
}

/** Il confronto con lo storico, letto una volta dal database (vuoto finché non è pronto). */
@Composable
internal fun confrontoConStorico(log: List<SerieEseguita>): Map<Long, ConfrontoEsercizio> {
    val app = LocalContext.current.applicationContext as? GymBroApplication
    val confronto by produceState(emptyMap<Long, ConfrontoEsercizio>(), log, app) {
        if (app == null || log.isEmpty()) return@produceState
        val storico = app.repository.righeReport().first()
        value = withContext(Dispatchers.Default) { confrontaConStorico(log, storico) }
    }
    return confronto
}

/** Volume (kg × ripetizioni) delle serie; quelle sulle macchine assistite non contano, il loro peso è aiuto. */
internal fun volumeSerie(stato: StatoEsecuzione, serie: List<SerieEseguita>): Double =
    serie.filterNot { caricoInverso(stato.nomiEseguiti[it.esercizioId].orEmpty()) }
        .sumOf { volume(it.pesoKg, it.ripetizioni) }

/** Tutti i muscoli lavorati nell'allenamento: principali di almeno un esercizio, poi gli altri. */
internal fun muscoliAllenamento(stato: StatoEsecuzione): MuscoliCoinvolti? {
    val gruppi = stato.esercizi.associate { it.esercizioId to it.gruppoMuscolare }
    val principali = LinkedHashSet<Muscolo>()
    val secondari = LinkedHashSet<Muscolo>()
    stato.log.map { it.esercizioId }.distinct().forEach { id ->
        val gruppo = gruppi[id]
        if (gruppo == GruppoMuscolare.STRETCHING) return@forEach
        val nome = stato.nomiEseguiti[id] ?: return@forEach
        val mc = muscoliCoinvolti(nome, gruppo) ?: return@forEach
        principali += mc.principali
        secondari += mc.secondari
    }
    secondari -= principali
    return if (principali.isEmpty()) null else MuscoliCoinvolti(principali, secondari)
}
