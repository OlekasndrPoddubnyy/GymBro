package com.my.gymbro.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GruppoMuscolare

@Composable
fun EserciziScreen() {
    val vm: EserciziViewModel = viewModel(factory = EserciziViewModel.Factory)
    val esercizi by vm.esercizi.collectAsState()

    var editing by remember { mutableStateOf<EditState>(EditState.None) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messaggi.collect { snackbar.showSnackbar(it) }
    }

    when (val stato = editing) {
        EditState.None -> ListaEsercizi(
            esercizi = esercizi,
            snackbar = snackbar,
            onAggiungi = { editing = EditState.Form(null) },
            onModifica = { editing = EditState.Form(it) },
            onElimina = { vm.elimina(it) }
        )

        is EditState.Form -> EsercizioFormScreen(
            iniziale = stato.esercizio,
            onSalva = {
                vm.salva(it)
                editing = EditState.None
            },
            onAnnulla = { editing = EditState.None }
        )
    }
}

private sealed interface EditState {
    data object None : EditState
    data class Form(val esercizio: Esercizio?) : EditState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListaEsercizi(
    esercizi: List<Esercizio>,
    snackbar: SnackbarHostState,
    onAggiungi: () -> Unit,
    onModifica: (Esercizio) -> Unit,
    onElimina: (Esercizio) -> Unit,
) {
    var daEliminare by remember { mutableStateOf<Esercizio?>(null) }
    var filtro by remember { mutableStateOf(StatoFiltro()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAggiungi) {
                Icon(Icons.Filled.Add, contentDescription = "Aggiungi esercizio")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (esercizi.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nessun esercizio.\nTocca + per aggiungerne uno.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                FiltriEsercizi(filtro, { filtro = it }, Modifier.padding(16.dp))
                val filtrati = esercizi.filtra(filtro)
                if (filtrati.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nessun esercizio trovato.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Lista sezionata per gruppo muscolare (nell'ordine dell'enum)
                        GruppoMuscolare.entries.forEach { gruppo ->
                            val delGruppo = filtrati.filter { it.gruppoMuscolare == gruppo }
                            if (delGruppo.isNotEmpty()) {
                                stickyHeader(key = "header_${gruppo.name}") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            gruppo.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "${delGruppo.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                items(delGruppo, key = { it.id }) { e ->
                                    EsercizioCard(
                                        e = e,
                                        onClick = { onModifica(e) },
                                        onElimina = { daEliminare = e },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    daEliminare?.let { e ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            title = { Text("Eliminare esercizio?") },
            text = { Text("Vuoi eliminare \"${e.nome}\"? L'azione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = {
                    onElimina(e)
                    daEliminare = null
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { daEliminare = null }) { Text("Annulla") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EsercizioCard(
    e: Esercizio,
    onClick: () -> Unit,
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
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(e.immaginePath)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.nome, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${e.gruppoMuscolare.label} · ${e.attrezzatura.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                e.descrizione?.takeIf { it.isNotBlank() }?.let { descr ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        descr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opzioni")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifica") },
                        onClick = {
                            menu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Elimina") },
                        onClick = {
                            menu = false
                            onElimina()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(path: String?) {
    val shape = RoundedCornerShape(8.dp)
    val bmp = rememberImmagine(path, maxDim = 256)
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(shape)
        )
    } else {
        Box(
            Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
