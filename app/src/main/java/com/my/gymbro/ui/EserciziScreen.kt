package com.my.gymbro.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.descrizioneSenzaMacchina
import com.my.gymbro.data.macchinaTechnogym

@Composable
fun EserciziScreen() {
    val vm: EserciziViewModel = viewModel(factory = EserciziViewModel.Factory)
    val esercizi by vm.esercizi.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messaggi.collect { snackbar.showSnackbar(it) }
    }

    when (val stato = vm.editing) {
        EditState.None -> ListaEsercizi(
            esercizi = esercizi,
            snackbar = snackbar,
            onAggiungi = { vm.apriForm(null) },
            onModifica = { vm.apriForm(it) },
            onElimina = { vm.elimina(it) }
        )

        is EditState.Form -> EsercizioFormScreen(
            iniziale = stato.esercizio,
            onSalva = { vm.salva(it) },
            onAnnulla = { vm.chiudiForm() }
        )
    }
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
            FloatingActionButton(
                onClick = onAggiungi,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
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
                FiltriEsercizi(filtro, { filtro = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp))
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
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(top = 10.dp, bottom = 6.dp, start = 2.dp, end = 4.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        TitoloSezione(gruppo.label, Modifier.weight(1f))
                                        Text(
                                            "%02d".format(delGruppo.size),
                                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                                            color = MaterialTheme.colorScheme.primary
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
        modifier = modifier.fillMaxWidth().magnesite(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = elevazioneCard()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Foto ferma: tocca la miniatura per vederla animata e ingrandita, con i muscoli coinvolti
            MiniaturaLibreria(e, Modifier.size(60.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.nome, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${e.gruppoMuscolare.label} · ${e.attrezzatura.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                macchinaTechnogym(e.descrizione)?.let { macchina ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Technogym · $macchina",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                descrizioneSenzaMacchina(e.descrizione)?.takeIf { it.isNotBlank() }?.let { descr ->
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
