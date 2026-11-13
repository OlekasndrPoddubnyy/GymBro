package com.my.gymbro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.Modalita

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedaEditorScreen(
    iniziale: SchedaInModifica,
    libreria: List<Esercizio>,
    onSalva: (SchedaInModifica) -> Unit,
    onAnnulla: () -> Unit,
) {
    var stato by remember(iniziale) { mutableStateOf(iniziale) }
    var mostraPicker by remember { mutableStateOf(false) }

    fun aggiorna(blocco: (SchedaInModifica) -> SchedaInModifica) { stato = blocco(stato) }
    fun aggiornaEsercizio(indice: Int, blocco: (EsercizioInScheda) -> EsercizioInScheda) {
        aggiorna { s -> s.copy(esercizi = s.esercizi.toMutableList().also { it[indice] = blocco(it[indice]) }) }
    }

    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(listState) { fromKey, toKey ->
        val da = stato.esercizi.indexOfFirst { it.uid == fromKey }
        val a = stato.esercizi.indexOfFirst { it.uid == toKey }
        if (da != -1 && a != -1 && da != a) {
            aggiorna { s -> s.copy(esercizi = s.esercizi.toMutableList().also { it.add(a, it.removeAt(da)) }) }
            true
        } else false
    }

    BackHandler(onBack = onAnnulla)

    Column(Modifier.fillMaxSize()) {
        // Header con back, titolo e salva
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAnnulla) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                if (iniziale.id == 0L) "Nuova scheda" else "Modifica scheda",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSalva(stato) }, enabled = stato.nome.isNotBlank()) {
                Text("Salva")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "nome") {
                OutlinedTextField(
                    value = stato.nome,
                    onValueChange = { v -> aggiorna { it.copy(nome = v) } },
                    label = { Text("Nome scheda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "note") {
                OutlinedTextField(
                    value = stato.note,
                    onValueChange = { v -> aggiorna { it.copy(note = v) } },
                    label = { Text("Note (opzionale)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "titolo") {
                Text("Esercizi", style = MaterialTheme.typography.titleMedium)
            }

            if (stato.esercizi.isEmpty()) {
                item(key = "vuoto") {
                    Text(
                        "Nessun esercizio. Aggiungine uno dalla libreria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(stato.esercizi, key = { _, e -> e.uid }) { indice, e ->
                val dragging = dragState.draggingItemKey == e.uid
                val itemModifier =
                    if (dragging) Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragState.draggingItemOffset }
                    else Modifier.animateItem()

                val handleModifier = Modifier.pointerInput(e.uid) {
                    detectDragGestures(
                        onDragStart = { dragState.onDragStart(e.uid) },
                        onDrag = { change, amount -> change.consume(); dragState.onDrag(amount.y) },
                        onDragEnd = { dragState.onDragEnd() },
                        onDragCancel = { dragState.onDragEnd() }
                    )
                }

                EsercizioEditorCard(
                    modifier = itemModifier,
                    dragHandleModifier = handleModifier,
                    e = e,
                    mostraSuperset = indice < stato.esercizi.lastIndex,
                    onModalita = { m -> aggiornaEsercizio(indice) { it.copy(modalita = m) } },
                    onNumeroSerie = { n -> aggiornaEsercizio(indice) { it.copy(serie = ridimensionaSerie(it.serie, n)) } },
                    onSerie = { sIdx, nuova -> aggiornaEsercizio(indice) { it.copy(serie = it.serie.toMutableList().also { l -> l[sIdx] = nuova }) } },
                    onNote = { v -> aggiornaEsercizio(indice) { it.copy(note = v) } },
                    onSuperset = { v -> aggiornaEsercizio(indice) { it.copy(supersetConProssimo = v) } },
                    onRimuovi = { aggiorna { s -> s.copy(esercizi = s.esercizi.toMutableList().also { it.removeAt(indice) }) } }
                )
            }

            item(key = "aggiungi") {
                OutlinedButton(
                    onClick = { mostraPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aggiungi esercizio")
                }
            }
        }
    }

    if (mostraPicker) {
        LibreriaPickerSheet(
            libreria = libreria,
            onScelto = { es ->
                aggiorna { it.copy(esercizi = it.esercizi + EsercizioInScheda(esercizio = es)) }
                mostraPicker = false
            },
            onDismiss = { mostraPicker = false }
        )
    }
}

/** Porta la lista di serie a [numero] elementi, copiando l'ultima riga quando se ne aggiungono. */
private fun ridimensionaSerie(serie: List<SerieInput>, numero: Int): List<SerieInput> = when {
    numero < 1 -> serie
    numero > serie.size -> serie + List(numero - serie.size) { serie.lastOrNull() ?: SerieInput() }
    else -> serie.take(numero)
}

@Composable
private fun EsercizioEditorCard(
    dragHandleModifier: Modifier,
    e: EsercizioInScheda,
    mostraSuperset: Boolean,
    onModalita: (Modalita) -> Unit,
    onNumeroSerie: (Int) -> Unit,
    onSerie: (Int, SerieInput) -> Unit,
    onNote: (String) -> Unit,
    onSuperset: (Boolean) -> Unit,
    onRimuovi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Trascina per riordinare",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.size(40.dp).padding(8.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(e.esercizio.nome, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${e.esercizio.gruppoMuscolare.label} · ${e.esercizio.attrezzatura.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRimuovi) {
                    Icon(Icons.Filled.Delete, contentDescription = "Rimuovi", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Modalità
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Modalita.entries.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onModalita(m) }
                    ) {
                        RadioButton(selected = e.modalita == m, onClick = { onModalita(m) })
                        Text(m.label)
                    }
                }
            }

            // Numero di serie (stepper)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Serie", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onNumeroSerie(e.serie.size - 1) }, enabled = e.serie.size > 1) {
                    Icon(Icons.Filled.Remove, contentDescription = "Una serie in meno")
                }
                Text("${e.serie.size}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onNumeroSerie(e.serie.size + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Una serie in più")
                }
            }

            e.serie.forEachIndexed { i, s ->
                SerieRow(
                    numero = i + 1,
                    modalita = e.modalita,
                    serie = s,
                    onChange = { onSerie(i, it) }
                )
            }

            OutlinedTextField(
                value = e.note,
                onValueChange = onNote,
                label = { Text("Note esercizio (opzionale)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (mostraSuperset) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Superset con il successivo", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = e.supersetConProssimo, onCheckedChange = onSuperset)
                }
            }
        }
    }
}

@Composable
private fun SerieRow(
    numero: Int,
    modalita: Modalita,
    serie: SerieInput,
    onChange: (SerieInput) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$numero", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
        when (modalita) {
            Modalita.TEMPO -> {
                CampoRuotaTempo("Durata", serie.durata, Modifier.weight(1f)) { onChange(serie.copy(durata = it)) }
                CampoRuotaTempo("Recupero", serie.recupero, Modifier.weight(1f)) { onChange(serie.copy(recupero = it)) }
            }
            Modalita.RIPETIZIONI -> {
                CampoTesto("Rip", serie.ripetizioni, Modifier.weight(1f)) { onChange(serie.copy(ripetizioni = it)) }
                CampoRuotaPeso("Kg", serie.peso, Modifier.weight(1f)) { onChange(serie.copy(peso = it)) }
                CampoRuotaTempo("Recupero", serie.recupero, Modifier.weight(1f)) { onChange(serie.copy(recupero = it)) }
            }
        }
    }
}

@Composable
private fun ValoreTappabile(label: String, valore: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valore, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioRuota(titolo: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(titolo, style = MaterialTheme.typography.titleMedium)
            content()
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fatto") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Campo (toccabile) per un tempo in secondi, che apre una ruota minuti:secondi. */
@Composable
private fun CampoRuotaTempo(label: String, secondiStr: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var aperto by remember { mutableStateOf(false) }
    val secondi = secondiStr.toIntOrNull() ?: 0
    ValoreTappabile(label, formattaMinSec(secondi), { aperto = true }, modifier)
    if (aperto) {
        FoglioRuota(label, { aperto = false }) {
            RuotaTempo(secondi, onChange = { onChange(it.toString()) })
        }
    }
}

/** Campo (toccabile) per il peso in kg, che apre una ruota. */
@Composable
private fun CampoRuotaPeso(label: String, pesoStr: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var aperto by remember { mutableStateOf(false) }
    ValoreTappabile(label, pesoStr.ifBlank { "—" }, { aperto = true }, modifier)
    if (aperto) {
        FoglioRuota(label, { aperto = false }) {
            RuotaPeso(pesoStr.toFloatOrNull() ?: 0f, onChange = { onChange(formattaKgRuota(it)) })
        }
    }
}

private fun formattaMinSec(secondi: Int): String = "%d:%02d".format(secondi / 60, secondi % 60)

@Composable
private fun CampoTesto(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibreriaPickerSheet(
    libreria: List<Esercizio>,
    onScelto: (Esercizio) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var filtro by remember { mutableStateOf(StatoFiltro()) }
        Text(
            "Scegli un esercizio",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        FiltriEsercizi(filtro, { filtro = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(libreria.filtra(filtro), key = { it.id }) { es ->
                ListItem(
                    headlineContent = { Text(es.nome) },
                    supportingContent = {
                        Text("${es.gruppoMuscolare.label} · ${es.attrezzatura.label}")
                    },
                    modifier = Modifier.clickable { onScelto(es) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
