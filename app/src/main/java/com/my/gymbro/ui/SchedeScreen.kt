package com.my.gymbro.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Edit
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
    val schede by vm.schede.collectAsStateWithLifecycle()
    val libreria by vm.libreria.collectAsStateWithLifecycle()

    when (val n = vm.nav) {
        SchedeNav.Lista -> ListaSchede(
            schede = schede,
            onNuova = { vm.apriEditor(null) },
            onApri = { vm.apri(it.id) },
            onDuplica = { vm.duplica(it) },
            onElimina = { vm.elimina(it) }
        )

        is SchedeNav.Dettaglio -> {
            val scheda by remember(n.schedaId) { vm.scheda(n.schedaId) }.collectAsStateWithLifecycle(initialValue = null)
            SchedaDettaglio(
                scheda = scheda,
                onIndietro = { vm.mostraLista() },
                onModifica = { vm.apriEditor(n.schedaId) },
                onInizia = { onAvviaAllenamento(n.schedaId) }
            )
        }

        is SchedeNav.Editor -> {
            val bozza = vm.bozza
            if (bozza == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SchedaEditorScreen(
                    stato = bozza,
                    modificata = vm.bozzaModificata,
                    libreria = libreria,
                    onModifica = vm::modificaBozza,
                    onSalva = vm::salvaBozza,
                    onAnnulla = vm::chiudiEditor
                )
            }
            vm.erroreSalvataggio?.let { errore ->
                AlertDialog(
                    onDismissRequest = vm::chiudiErroreSalvataggio,
                    title = { Text("Scheda non salvata") },
                    text = { Text(errore) },
                    confirmButton = { TextButton(onClick = vm::chiudiErroreSalvataggio) { Text("OK") } }
                )
            }
        }
    }
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
            ExtendedFloatingActionButton(
                onClick = onNuova,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuova scheda", style = MaterialTheme.typography.titleSmall) }
            )
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
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(schede, key = { _, it -> it.id }) { i, s ->
                        SchedaCard(
                            numero = i + 1,
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
    numero: Int,
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
        shape = RoundedCornerShape(20.dp),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = elevazioneCard()
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "%02d".format(numero),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
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
        IntestazioneDettaglio(
            titolo = scheda?.scheda?.nome ?: "Scheda",
            sopratitolo = "Dettaglio allenamento",
            onIndietro = onIndietro
        ) {
            BottoneSecondario(
                testo = "Modifica",
                onClick = onModifica,
                icona = Icons.Filled.Edit,
                contenitore = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.height(44.dp)
            )
        }

        if (scheda == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        val esercizi = scheda.esercizi.sortedBy { it.esercizioScheda.ordine }
        val stima = remember(esercizi) { stimaTempi(esercizi.map { it.toEsecuzione() }) }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (esercizi.isNotEmpty()) {
                item(key = "riassunto") { RiassuntoTempo(stima, esercizi.size) }
            }
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
            itemsIndexed(esercizi, key = { _, it -> it.esercizioScheda.id }) { i, ec ->
                Card(
                    Modifier.fillMaxWidth().animateItem(),
                    shape = RoundedCornerShape(18.dp),
                    colors = coloriCard(),
                    border = bordoCard(),
                    elevation = elevazioneCard()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        // Foto: tocca per vederla animata e ingrandita, con i muscoli coinvolti
                        MiniaturaEsercizio(
                            nome = ec.esercizio.nome,
                            path = ec.esercizio.immaginePath,
                            descrizione = ec.esercizio.descrizione,
                            nota = ec.esercizioScheda.note,
                            gruppo = ec.esercizio.gruppoMuscolare,
                            modifier = Modifier.size(72.dp),
                            segnaposto = {
                                Text(
                                    ec.esercizio.gruppoMuscolare.label.take(3).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Sopratitolo("Esercizio %02d".format(i + 1))
                            Text(ec.esercizio.nome, style = MaterialTheme.typography.titleMedium)
                            val superset = if (ec.esercizioScheda.gruppoSuperset != null) " · Superset" else ""
                            Text(
                                "${ec.esercizio.gruppoMuscolare.label} · ${ec.esercizioScheda.modalita.label}$superset",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ec.esercizioScheda.note?.takeIf { it.isNotBlank() }?.let { nota ->
                                Text(
                                    nota,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            ec.serie.sortedBy { it.numero }.forEach { sp ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${sp.numero}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(20.dp)
                                    )
                                    Text(
                                        riassuntoSerie(ec.esercizioScheda.modalita, sp.durataSec, sp.ripetizioni, sp.pesoKg, sp.recuperoSec),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            BottonePrincipale(
                testo = "Inizia allenamento",
                onClick = onInizia,
                enabled = esercizi.isNotEmpty(),
                icona = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RiassuntoTempo(stima: StimaTempi, numEsercizi: Int) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, border = bordoCard()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Sopratitolo("Tempo totale stimato")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${(stima.totaleSec + 30) / 60}", style = MaterialTheme.typography.displayMedium, color = cs.primary)
                        Text(" min", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$numEsercizi esercizi", style = MaterialTheme.typography.bodyMedium)
                    Text("${stima.serie} serie", style = MaterialTheme.typography.bodyMedium)
                }
            }
            val totale = stima.totaleSec.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (stima.lavoroSec > 0) {
                    Box(Modifier.weight(stima.lavoroSec.toFloat() / totale).fillMaxHeight().clip(RoundedCornerShape(50)).background(cs.primaryContainer))
                }
                if (stima.recuperoSec > 0) {
                    Box(Modifier.weight(stima.recuperoSec.toFloat() / totale).fillMaxHeight().clip(RoundedCornerShape(50)).background(cs.tertiaryContainer))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Legenda(cs.primaryContainer, "Lavoro ${(stima.lavoroSec + 30) / 60} min")
                Legenda(cs.tertiaryContainer, "Recupero ${(stima.recuperoSec + 30) / 60} min")
            }
        }
    }
}

@Composable
private fun Legenda(colore: Color, testo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colore))
        Text(testo, style = MaterialTheme.typography.bodySmall)
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
