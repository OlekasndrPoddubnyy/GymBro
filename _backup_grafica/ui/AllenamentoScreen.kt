package com.my.gymbro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.SerieEseguita
import java.util.concurrent.TimeUnit

@Composable
fun AllenamentoScreen(
    schedaId: Long,
    vmKey: String,
    onFine: () -> Unit,
) {
    val vm: AllenamentoViewModel = viewModel(key = vmKey, factory = AllenamentoViewModel.Factory)
    LaunchedEffect(schedaId) { vm.avvia(schedaId) }

    val stato by vm.stato.collectAsState()
    val feedback = rememberFeedbackController()

    KeepScreenOn()
    LaunchedEffect(Unit) { vm.segnali.collect { feedback(it) } }
    BackHandler(onBack = onFine)

    Box(Modifier.fillMaxSize().sfondoApp()) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            when {
                !stato.pronta -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                stato.fase == Fase.COMPLETATO -> Riepilogo(stato, onChiudi = onFine)
                else -> EsecuzioneAttiva(stato, vm, onEsci = onFine)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EsecuzioneAttiva(
    stato: StatoEsecuzione,
    vm: AllenamentoViewModel,
    onEsci: () -> Unit,
) {
    val libreria by vm.libreria.collectAsState()
    var mostraSostituzione by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onEsci) {
                Icon(Icons.Filled.Close, contentDescription = "Esci")
            }
            Text(
                stato.nomeScheda,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.terminaOra() }) { Text("Termina") }
        }

        BarraAvanzamentoScheda(stato)
        BarraProgressoFase(stato)

        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (stato.fase) {
                Fase.PREPARAZIONE -> {
                    Etichetta("Preparati")
                    Cronometro(stato.secondiRimanenti)
                    stato.esercizioCorrente?.let { Text("Inizia con: ${it.nome}", style = MaterialTheme.typography.bodyLarge) }
                    FilledTonalButton(onClick = { vm.saltaPreparazione() }) { Text("Salta") }
                }

                Fase.ESECUZIONE -> {
                    EsecuzioneSerie(stato, vm)
                    TextButton(onClick = { mostraSostituzione = true }) { Text("Sostituisci esercizio") }
                }

                Fase.RECUPERO -> {
                    Etichetta("Recupero")
                    Cronometro(stato.secondiRimanenti)
                    stato.esercizioCorrente?.let { es ->
                        Text(
                            "Prossima: ${es.nome} · serie ${stato.indiceSerie + 1} di ${es.serie.size}",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                    FilledTonalButton(onClick = { vm.saltaRecupero() }) { Text("Salta recupero") }
                }

                Fase.ATTESA -> {
                    Etichetta("Prossimo esercizio")
                    stato.esercizioCorrente?.let {
                        Text(it.nome, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { vm.saltaEsercizio() }) { Text("Salta esercizio") }
                        Button(onClick = { vm.iniziaProssimo() }) { Text("Inizia") }
                    }
                    TextButton(onClick = { mostraSostituzione = true }) { Text("Sostituisci esercizio") }
                }

                Fase.COMPLETATO -> Unit
            }
        }

        HorizontalDivider()
        Text(
            "Esercizi",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ListaStato(stato, onVai = { vm.vaiAEsercizio(it) }, modifier = Modifier.weight(1f))
    }

    if (mostraSostituzione) {
        SostituzioneSheet(
            libreria = libreria,
            onScelto = { es ->
                vm.sostituisci(stato.indiceEsercizio, es)
                mostraSostituzione = false
            },
            onDismiss = { mostraSostituzione = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SostituzioneSheet(
    libreria: List<Esercizio>,
    onScelto: (Esercizio) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var filtro by remember { mutableStateOf(StatoFiltro()) }
        Text(
            "Sostituisci con",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        FiltriEsercizi(filtro, { filtro = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(libreria.filtra(filtro), key = { it.id }) { es ->
                ListItem(
                    headlineContent = { Text(es.nome) },
                    supportingContent = { Text("${es.gruppoMuscolare.label} · ${es.attrezzatura.label}") },
                    modifier = Modifier.clickable { onScelto(es) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Avanzamento complessivo della scheda: serie completate/saltate sul totale dei passi. */
@Composable
private fun BarraAvanzamentoScheda(stato: StatoEsecuzione) {
    if (stato.passiTotali <= 0) return
    val fatti = stato.indicePasso.coerceIn(0, stato.passiTotali)
    val pa by animateFloatAsState(
        fatti.toFloat() / stato.passiTotali,
        tween(400),
        label = "progScheda"
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Avanzamento scheda",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$fatti/${stato.passiTotali} serie",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { pa }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BarraProgressoFase(stato: StatoEsecuzione) {
    val mostra = stato.fase == Fase.PREPARAZIONE || stato.fase == Fase.RECUPERO ||
        (stato.fase == Fase.ESECUZIONE && stato.esercizioCorrente?.modalita == Modalita.TEMPO)
    if (!mostra || stato.secondiTotali <= 0) return
    val p = (stato.secondiTotali - stato.secondiRimanenti).toFloat() / stato.secondiTotali
    val pa by animateFloatAsState(p.coerceIn(0f, 1f), tween(1000, easing = LinearEasing), label = "progFase")
    LinearProgressIndicator(
        progress = { pa },
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EsecuzioneSerie(stato: StatoEsecuzione, vm: AllenamentoViewModel) {
    val es = stato.esercizioCorrente ?: return
    val serie = stato.serieCorrente ?: return

    Text(es.nome, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        "Serie ${stato.indiceSerie + 1} di ${es.serie.size}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when (es.modalita) {
        Modalita.TEMPO -> {
            Cronometro(stato.secondiRimanenti)
        }
        Modalita.RIPETIZIONI -> {
            val pianificato = buildString {
                serie.ripetizioniPianificate?.takeIf { it.isNotBlank() }?.let { append("$it rip") }
                serie.pesoPianificato?.let { if (isNotEmpty()) append(" · "); append("${formattaPeso(it)} kg") }
            }
            if (pianificato.isNotBlank()) {
                Text("Pianificato: $pianificato", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ripetizioni", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RuotaPicker(
                        valori = (0..50).toList(),
                        selezionato = stato.ripInput.toIntOrNull() ?: 0,
                        onSelezione = { vm.aggiornaRip(it.toString()) },
                        modifier = Modifier.width(72.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Peso (kg)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RuotaPeso(
                        peso = stato.pesoInput.toFloatOrNull() ?: 0f,
                        onChange = { vm.aggiornaPeso(formattaKgRuota(it)) }
                    )
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = { vm.saltaSerie() }) { Text("Salta serie") }
        Button(onClick = { vm.completaSerie() }) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Completa serie")
        }
    }
}

@Composable
private fun Etichetta(testo: String) {
    Text(testo, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun Cronometro(secondi: Int) {
    Text(
        formattaTempo(secondi),
        style = MaterialTheme.typography.displayLarge,
        color = if (secondi in 1..3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ListaStato(
    stato: StatoEsecuzione,
    onVai: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(stato.esercizi) { indice, es ->
            val corrente = indice == stato.indiceEsercizio
            val fatto = stato.eserciziFatti.contains(indice)
            ListItem(
                headlineContent = { Text(es.nome) },
                supportingContent = {
                    val superset = if (es.gruppo != null) " · Superset" else ""
                    Text("${es.serie.size} serie · ${es.modalita.label}$superset")
                },
                leadingContent = {
                    when {
                        fatto -> Icon(Icons.Filled.Check, contentDescription = "Completato", tint = MaterialTheme.colorScheme.primary)
                        corrente -> Text("▶", style = MaterialTheme.typography.titleMedium)
                        else -> Text("${indice + 1}", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = if (corrente)
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                else ListItemDefaults.colors(),
                modifier = Modifier.clickable { onVai(indice) }
            )
        }
    }
}

@Composable
private fun Riepilogo(stato: StatoEsecuzione, onChiudi: () -> Unit) {
    val nomi = remember(stato.esercizi) { stato.esercizi.associate { it.esercizioId to it.nome } }
    val perEsercizio = remember(stato.log) { stato.log.groupBy { it.esercizioId } }
    val volume = remember(stato.log) {
        stato.log.sumOf { ((it.pesoKg ?: 0f) * (it.ripetizioni ?: 0)).toDouble() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Allenamento completato", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = coloriCard(),
                    border = bordoCard(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stato.nomeScheda, style = MaterialTheme.typography.titleMedium)
                        Text("Durata: ${formattaDurata(stato.durataTotaleMs)}")
                        Text("Serie completate: ${stato.log.size}")
                        if (volume > 0) Text("Volume totale: ${formattaPeso(volume.toFloat())} kg")
                    }
                }
            }

            if (perEsercizio.isEmpty()) {
                item { Text("Nessuna serie registrata.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(perEsercizio.entries.toList()) { (id, serie) ->
                Card(
                    Modifier.fillMaxWidth().animateItem(),
                    colors = coloriCard(),
                    border = bordoCard(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(nomi[id] ?: "Esercizio", style = MaterialTheme.typography.titleMedium)
                        serie.sortedBy { it.numero }.forEach { se ->
                            Text("• " + descriviSerieEseguita(se), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onChiudi,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("Fine")
        }
    }
}

// ---------- helper ----------

private fun descriviSerieEseguita(se: SerieEseguita): String = when {
    se.durataSec != null -> "${se.durataSec}s"
    else -> {
        val rip = se.ripetizioni?.toString() ?: "—"
        val peso = se.pesoKg?.let { " × ${formattaPeso(it)} kg" } ?: ""
        "$rip rip$peso"
    }
}

private fun formattaTempo(secondi: Int): String {
    val m = secondi / 60
    val s = secondi % 60
    return "%d:%02d".format(m, s)
}

private fun formattaDurata(ms: Long): String {
    val min = TimeUnit.MILLISECONDS.toMinutes(ms)
    val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d min %02d s".format(min, sec)
}

private fun formattaPeso(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else kg.toString()
