package com.my.gymbro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.my.gymbro.data.ChiaveBuddy
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.EsercizioSchedaCompleto
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.SchedaConEsercizi
import com.my.gymbro.data.allenabile
import java.util.Locale
import kotlin.math.roundToInt

/** I tre sostituti più simili; in "Correggi i pesi" tutta la libreria, per cambiare una somiglianza. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EserciziSimiliSheet(
    scheda: SchedaConEsercizi,
    riga: EsercizioSchedaCompleto,
    vista: VistaBuddy,
    vm: GymBuddyViewModel,
    onChiudi: () -> Unit,
) {
    var correzione by remember { mutableStateOf(false) }
    var filtro by remember { mutableStateOf(StatoFiltro()) }
    var scelto by remember { mutableStateOf<Esercizio?>(null) }
    var daCorreggere by remember { mutableStateOf<Esercizio?>(null) }
    val attuale = riga.esercizio

    val voci: List<Pair<Esercizio, Double>> = remember(vista, scheda, correzione, filtro) {
        if (correzione) {
            vista.libreria.filtra(filtro)
                .filter { it.id != attuale.id && it.gruppoMuscolare.allenabile }
                .map { it to vista.somiglianzaTra(attuale.id, it.id) }
                .sortedByDescending { it.second }
        } else {
            val perId = vista.libreria.associateBy { it.id }
            vista.sostitutiPer(scheda, attuale.id).mapNotNull { s -> perId[s.esercizioId]?.let { it to s.peso } }
        }
    }

    ModalBottomSheet(onDismissRequest = onChiudi) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TitoloSezione(if (correzione) "Correggi i pesi" else "Al posto di ${attuale.nome}")
            TextButton(onClick = { correzione = !correzione }) {
                Text(if (correzione) "Torna alle alternative" else "Correggi i pesi di somiglianza")
            }
            if (correzione) FiltriEsercizi(filtro, { filtro = it }, Modifier.fillMaxWidth())
            if (voci.isEmpty()) {
                Text(
                    "Nessun esercizio abbastanza simile con gli attrezzi disponibili.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(voci, key = { it.first.id }) { (es, peso) ->
                ListItem(
                    headlineContent = { Text(es.nome, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text("${es.attrezzatura.label} · somiglianza ${formattaSomiglianza(peso)}")
                    },
                    leadingContent = { MiniaturaLibreria(es, Modifier.size(64.dp), cliccabile = false) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { if (correzione) daCorreggere = es else scelto = es },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    scelto?.let { nuovo ->
        DialogoSostituisci(
            scheda = scheda,
            riga = riga,
            nuovo = nuovo,
            vista = vista,
            onConferma = { carico ->
                vm.sostituisci(scheda.scheda.id, riga, nuovo, carico)
                scelto = null
                onChiudi()
            },
            onAnnulla = { scelto = null },
        )
    }
    daCorreggere?.let { altro ->
        var percento by remember(altro.id) {
            mutableStateOf((vista.somiglianzaTra(attuale.id, altro.id) * 100).roundToInt())
        }
        AlertDialog(
            onDismissRequest = { daCorreggere = null },
            title = { Text(altro.nome) },
            text = {
                Column {
                    RigaNumero("Somiglianza %", percento, 0..100, passo = 5) { percento = it }
                    TextButton(
                        onClick = {
                            vm.correggiSomiglianza(attuale.id, altro.id, null)
                            daCorreggere = null
                        },
                    ) { Text("Torna al valore calcolato") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.correggiSomiglianza(attuale.id, altro.id, percento / 100.0)
                        daCorreggere = null
                    },
                ) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { daCorreggere = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun DialogoSostituisci(
    scheda: SchedaConEsercizi,
    riga: EsercizioSchedaCompleto,
    nuovo: Esercizio,
    vista: VistaBuddy,
    onConferma: (Float?) -> Unit,
    onAnnulla: () -> Unit,
) {
    val conPeso = riga.esercizioScheda.modalita == Modalita.RIPETIZIONI
    val storico = vista.ultimoCarico[nuovo.id]
    val proposto = remember(nuovo.id) {
        val progresso = vista.progresso(ChiaveBuddy(scheda.scheda.id, riga.esercizio.id))
        val attuale = progresso.stato?.obiettivo?.caricoKg?.takeIf { progresso.attivo }
            ?: vista.ultimoCarico[riga.esercizio.id]
            ?: riga.serie.minByOrNull { it.numero }?.pesoKg
        vista.caricoPerSostituto(riga.esercizio, nuovo, attuale)
    }
    var carico by remember(nuovo.id) { mutableStateOf(proposto ?: 0f) }

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Sostituire con ${nuovo.nome}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Cambia la scheda, con le stesse serie. Puoi annullare, e lo storico resta salvato.")
                if (conPeso) {
                    Text(
                        when {
                            storico != null -> "Carico dell'ultima volta:"
                            proposto != null -> "Carico stimato, correggilo se serve:"
                            else -> "Che carico usi? Lascia 0 se è a corpo libero o non lo sai ancora."
                        },
                    )
                    RuotaPeso(carico, { carico = it }, Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConferma(carico.takeIf { conPeso && it > 0f }) }) { Text("Sostituisci") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
    )
}

private fun formattaSomiglianza(peso: Double): String = String.format(Locale.ITALY, "%.2f", peso)
