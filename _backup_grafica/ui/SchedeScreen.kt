package com.my.gymbro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.Scheda
import com.my.gymbro.data.SchedaConEsercizi
import java.text.DateFormat
import java.util.Date

@Composable
fun SchedeScreen(onAvviaAllenamento: (Long) -> Unit) {
    val vm: SchedeViewModel = viewModel(factory = SchedeViewModel.Factory)
    val schede by vm.schede.collectAsState()
    val libreria by vm.libreria.collectAsState()
    var nav by remember { mutableStateOf<SchedeNav>(SchedeNav.Lista) }

    when (val n = nav) {
        SchedeNav.Lista -> ListaSchede(
            schede = schede,
            onNuova = { nav = SchedeNav.Editor(null) },
            onApri = { nav = SchedeNav.Dettaglio(it.id) },
            onDuplica = { vm.duplica(it) },
            onElimina = { vm.elimina(it) }
        )

        is SchedeNav.Dettaglio -> {
            val scheda by remember(n.schedaId) { vm.scheda(n.schedaId) }.collectAsState(initial = null)
            SchedaDettaglio(
                scheda = scheda,
                onIndietro = { nav = SchedeNav.Lista },
                onModifica = { nav = SchedeNav.Editor(n.schedaId) },
                onInizia = { onAvviaAllenamento(n.schedaId) }
            )
        }

        is SchedeNav.Editor -> {
            val schedaId = n.schedaId
            var iniziale by remember(schedaId) {
                mutableStateOf(if (schedaId == null) SchedaInModifica() else null)
            }
            LaunchedEffect(schedaId) {
                if (schedaId != null) iniziale = vm.carica(schedaId) ?: SchedaInModifica()
            }
            val init = iniziale
            if (init == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SchedaEditorScreen(
                    iniziale = init,
                    libreria = libreria,
                    onSalva = { vm.salva(it) { nav = SchedeNav.Lista } },
                    onAnnulla = {
                        nav = if (schedaId == null) SchedeNav.Lista else SchedeNav.Dettaglio(schedaId)
                    }
                )
            }
        }
    }
}

private sealed interface SchedeNav {
    data object Lista : SchedeNav
    data class Dettaglio(val schedaId: Long) : SchedeNav
    data class Editor(val schedaId: Long?) : SchedeNav
}

@Composable
private fun ListaSchede(
    schede: List<Scheda>,
    onNuova: () -> Unit,
    onApri: (Scheda) -> Unit,
    onDuplica: (Scheda) -> Unit,
    onElimina: (Scheda) -> Unit,
) {
    var daEliminare by remember { mutableStateOf<Scheda?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        floatingActionButton = {
            FloatingActionButton(onClick = onNuova) {
                Icon(Icons.Filled.Add, contentDescription = "Nuova scheda")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (schede.isEmpty()) {
                Text(
                    "Nessuna scheda.\nTocca + per crearne una.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(schede, key = { it.id }) { s ->
                        SchedaCard(
                            scheda = s,
                            onClick = { onApri(s) },
                            onDuplica = { onDuplica(s) },
                            onElimina = { daEliminare = s },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    daEliminare?.let { s ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            title = { Text("Eliminare scheda?") },
            text = { Text("Vuoi eliminare \"${s.nome}\"? L'azione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = { onElimina(s); daEliminare = null }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { daEliminare = null }) { Text("Annulla") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedaCard(
    scheda: Scheda,
    onClick: () -> Unit,
    onDuplica: () -> Unit,
    onElimina: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(scheda.nome, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Creata il ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(scheda.dataCreazione))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opzioni")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Duplica") }, onClick = { menu = false; onDuplica() })
                    DropdownMenuItem(text = { Text("Elimina") }, onClick = { menu = false; onElimina() })
                }
            }
        }
    }
}

@Composable
private fun SchedaDettaglio(
    scheda: SchedaConEsercizi?,
    onIndietro: () -> Unit,
    onModifica: () -> Unit,
    onInizia: () -> Unit,
) {
    BackHandler(onBack = onIndietro)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onIndietro) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                scheda?.scheda?.nome ?: "Scheda",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onModifica) { Text("Modifica") }
        }

        if (scheda == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        val esercizi = scheda.esercizi.sortedBy { it.esercizioScheda.ordine }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scheda.scheda.note?.takeIf { it.isNotBlank() }?.let { note ->
                item {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (esercizi.isEmpty()) {
                item {
                    Text("Questa scheda non ha esercizi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(esercizi, key = { it.esercizioScheda.id }) { ec ->
                Card(
                    Modifier.fillMaxWidth().animateItem(),
                    colors = coloriCard(),
                    border = bordoCard(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ec.esercizio.nome, style = MaterialTheme.typography.titleMedium)
                        val superset = if (ec.esercizioScheda.gruppoSuperset != null) " · Superset" else ""
                        Text(
                            "${ec.esercizio.gruppoMuscolare.label} · ${ec.esercizioScheda.modalita.label}$superset",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ec.serie.sortedBy { it.numero }.forEach { sp ->
                            Text(
                                "• " + riassuntoSerie(ec.esercizioScheda.modalita, sp.durataSec, sp.ripetizioni, sp.pesoKg, sp.recuperoSec),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp) {
            Button(
                onClick = onInizia,
                enabled = esercizi.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Inizia allenamento")
            }
        }
    }
}

private fun riassuntoSerie(
    modalita: Modalita,
    durataSec: Int?,
    ripetizioni: String?,
    pesoKg: Float?,
    recuperoSec: Int,
): String {
    val rec = "rec ${recuperoSec}s"
    return when (modalita) {
        Modalita.TEMPO -> listOfNotNull(durataSec?.let { "${it}s" } ?: "—", rec).joinToString(" · ")
        Modalita.RIPETIZIONI -> {
            val rip = ripetizioni?.takeIf { it.isNotBlank() }?.let { "$it rip" } ?: "—"
            val peso = pesoKg?.let { "${formattaPeso(it)} kg" }
            listOfNotNull(rip, peso, rec).joinToString(" · ")
        }
    }
}

private fun formattaPeso(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else kg.toString()
