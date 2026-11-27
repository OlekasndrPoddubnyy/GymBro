package com.my.gymbro.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun ReportScreen() {
    val vm: ReportViewModel = viewModel(factory = ReportViewModel.Factory)
    val stato by vm.stato.collectAsStateWithLifecycle()
    // Per id, non la voce: dopo una correzione il dettaglio mostra i valori nuovi.
    var sessioneAperta by rememberSaveable { mutableStateOf<Long?>(null) }
    var storicoCompleto by rememberSaveable { mutableStateOf(false) }

    when {
        !stato.pronto -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        stato.sessioniTotali == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Completa un allenamento\nper vedere le statistiche.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { FiltroPeriodo(stato.periodo, vm::setPeriodo) }
            item { Riepilogo(stato) }
            item { SezioneCostanza(stato) }
            item { SezioneVolume(stato) }
            item { SezioneAndamento(stato, vm) }

            if (stato.distribuzioneMuscolare.isNotEmpty()) {
                item { SezioneMuscoli(stato) }
            }

            item { TitoloSezione("Record personali", Modifier.padding(top = 8.dp)) }
            if (stato.pr.isEmpty()) {
                item { Text("Nessun record nel periodo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // Chiavi con prefisso: id di esercizi e di sessioni si sovrappongono nella stessa lista.
            items(stato.pr, key = { "pr_${it.id}" }) { PrRow(it, Modifier.animateItem()) }

            item { TitoloSezione("Storico", Modifier.padding(top = 8.dp)) }
            if (stato.storico.isEmpty()) {
                item { Text("Nessuna sessione nel periodo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val storicoVisibile = if (storicoCompleto) stato.storico else stato.storico.take(STORICO_INIZIALE)
            items(storicoVisibile, key = { "sessione_${it.id}" }) { v ->
                StoricoRow(v, onClick = { sessioneAperta = v.id }, modifier = Modifier.animateItem())
            }
            if (stato.storico.size > STORICO_INIZIALE) {
                item(key = "storico_altro") {
                    TextButton(
                        onClick = { storicoCompleto = !storicoCompleto },
                        modifier = Modifier.fillMaxWidth().animateItem()
                    ) {
                        Text(if (storicoCompleto) "Mostra meno" else "Mostra tutte (${stato.storico.size})")
                    }
                }
            }
        }
    }

    // Sparita dallo storico (eliminata, o ultima serie tolta): il dettaglio si chiude.
    val voceAperta = sessioneAperta?.let { id -> stato.storico.firstOrNull { it.id == id } }
    if (voceAperta != null) {
        DettaglioSessioneSheet(
            v = voceAperta,
            modificabile = vm.modificabile(voceAperta.id),
            onCorreggi = { serie, correzione -> vm.correggiSerie(voceAperta.id, serie, correzione) },
            onEliminaSerie = { serie -> vm.eliminaSerie(voceAperta.id, serie) },
            onEliminaSessione = {
                vm.eliminaSessione(voceAperta.id)
                sessioneAperta = null
            },
            onDismiss = { sessioneAperta = null },
        )
    } else if (sessioneAperta != null && stato.pronto) {
        LaunchedEffect(Unit) { sessioneAperta = null }
    }

    vm.avviso?.let { a ->
        AlertDialog(
            onDismissRequest = vm::chiudiAvviso,
            title = { Text(a.titolo) },
            text = { Text(a.testo) },
            confirmButton = { TextButton(onClick = vm::chiudiAvviso) { Text("OK") } },
        )
    }
}

private const val STORICO_INIZIALE = 5

// ---------- filtro periodo ----------

@Composable
private fun FiltroPeriodo(corrente: PeriodoReport, onSelezione: (PeriodoReport) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PeriodoReport.entries) { p ->
            ChipForge(selezionato = corrente == p, onClick = { onSelezione(p) }, testo = p.label)
        }
    }
}

// ---------- riepilogo ----------

private fun descriviPeriodo(p: PeriodoReport): String = when (p) {
    PeriodoReport.TUTTO -> "da sempre"
    PeriodoReport.M1 -> "ultimi 30 giorni"
    PeriodoReport.M3 -> "ultimi 90 giorni"
    PeriodoReport.A1 -> "ultimo anno"
}

/** Come si chiama il periodo precedente di pari durata, es. "30 giorni prima". */
private fun descriviPeriodoPrima(p: PeriodoReport): String = when (p) {
    PeriodoReport.TUTTO -> ""
    PeriodoReport.M1 -> "30 giorni prima"
    PeriodoReport.M3 -> "90 giorni prima"
    PeriodoReport.A1 -> "anno prima"
}

@Composable
private fun Riepilogo(stato: ReportState) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, border = bordoCard(), modifier = Modifier.magnesite(RoundedCornerShape(24.dp))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Sopratitolo("Volume totale · ${descriviPeriodo(stato.periodo)}")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(compatto(stato.volumeTotale), style = MaterialTheme.typography.displayMedium, color = cs.primary)
                    Text(" kg", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 6.dp))
                }
                stato.confronto?.let {
                    ConfrontoVolume(stato.volumeTotale, it, descriviPeriodoPrima(stato.periodo))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TesseraStat("Allenamenti", stato.numeroSessioni.toString(), Modifier.weight(1f))
                TesseraStat("Giorni attivi", stato.giorniAllenati.toString(), Modifier.weight(1f))
                TesseraStat("Serie", stato.serieTotali.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TesseraStat(
                    "Durata media",
                    if (stato.durataMediaMs > 0) durataBreve(stato.durataMediaMs) else "—",
                    Modifier.weight(1f)
                )
                val medio = if (stato.numeroSessioni > 0) stato.volumeTotale / stato.numeroSessioni else 0.0
                TesseraStat(
                    "Volume a seduta",
                    if (medio > 0) "${compatto(medio)} kg" else "—",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

/** "▲ 12% rispetto ai 30 giorni prima" (in evidenza se si sale), o quanti allenamenti c'erano prima. */
@Composable
private fun ConfrontoVolume(attuale: Double, prima: ConfrontoPeriodo, nomePrima: String) {
    val cs = MaterialTheme.colorScheme
    val articolo = if (nomePrima.startsWith("anno")) "all'" else "ai "
    val (testo, colore) = when {
        prima.volume <= 0.0 && attuale <= 0.0 -> return
        prima.volume <= 0.0 ->
            "Nessun volume " + (if (nomePrima.startsWith("anno")) "nell'" else "nei ") + nomePrima to cs.onSurfaceVariant
        else -> {
            val delta = ((attuale - prima.volume) / prima.volume * 100).roundToInt()
            val rif = "rispetto $articolo$nomePrima"
            when {
                delta > 0 -> "▲ $delta% $rif" to cs.primary
                delta < 0 -> "▼ ${-delta}% $rif" to cs.onSurfaceVariant
                else -> "Stesso volume $rif" to cs.onSurfaceVariant
            }
        }
    }
    Text(testo, style = MaterialTheme.typography.labelLarge, color = colore)
}

// ---------- costanza ----------

@Composable
private fun SezioneCostanza(stato: ReportState) {
    val cs = MaterialTheme.colorScheme
    SezioneCard("Costanza") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TesseraStat("Di fila", "${stato.settimaneConsecutive} sett.", Modifier.weight(1f), coloreValore = cs.primary)
            TesseraStat("Record", "${stato.migliorStreak} sett.", Modifier.weight(1f))
            TesseraStat("Questa sett.", stato.allenamentiSettimana.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Settimane di fila con almeno un allenamento: la settimana in corso non interrompe la serie.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Heatmap(stato.heatmap)
    }
}

@Composable
private fun Heatmap(settimane: List<SettimanaAttivita>) {
    if (settimane.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val vuoto = cs.surfaceVariant
    val basso = cs.primaryContainer.copy(alpha = 0.45f)
    val alto = cs.primaryContainer
    val giorniLabel = listOf("L", "M", "M", "G", "V", "S", "D")
    // Oggi = ultimo giorno non futuro dell'ultima settimana
    val oggiIdx = settimane.last().giorni.indexOfLast { it != null }
    val gap = 3.dp
    val colonnaGiorni = 16.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cella = ((maxWidth - colonnaGiorni - gap * settimane.size) / settimane.size).coerceAtMost(26.dp)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            // Nome del mese sopra la prima settimana di ciascuno
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.width(colonnaGiorni))
                settimane.forEachIndexed { i, s ->
                    Box(Modifier.width(cella).height(18.dp)) {
                        if (i == 0 || s.inizio.month != settimane[i - 1].inizio.month) {
                            Text(
                                s.inizio.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                            )
                        }
                    }
                }
            }
            giorniLabel.forEachIndexed { g, etichetta ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        etichetta,
                        Modifier.width(colonnaGiorni),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    settimane.forEachIndexed { w, settimana ->
                        val conteggio = settimana.giorni[g]
                        val colore = when {
                            conteggio == null -> Color.Transparent
                            conteggio == 0 -> vuoto
                            conteggio == 1 -> basso
                            else -> alto
                        }
                        val forma = RoundedCornerShape(4.dp)
                        val oggi = w == settimane.lastIndex && g == oggiIdx
                        Box(
                            Modifier
                                .size(cella)
                                .clip(forma)
                                .background(colore)
                                .then(if (oggi) Modifier.border(2.dp, cs.onSurface, forma) else Modifier)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ultime ${settimane.size} settimane · bordo = oggi",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Meno", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(vuoto))
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(basso))
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(alto))
                    Text("Più", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- volume ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SezioneVolume(stato: ReportState) {
    var mensile by rememberSaveable { mutableStateOf(false) }
    SezioneCard("Volume (kg)") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !mensile,
                onClick = { mensile = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("Settimanale") }
            SegmentedButton(
                selected = mensile,
                onClick = { mensile = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("Mensile") }
        }
        val barre = if (mensile) stato.volumeMensile else stato.volumeSettimanale
        // Selezionata di default l'ultima barra (settimana o mese in corso)
        var selezionata by rememberSaveable(mensile) {
            mutableIntStateOf(barre.indexOfLast { it.valore > 0 }.takeIf { it >= 0 } ?: barre.lastIndex)
        }
        Spacer(Modifier.height(14.dp))
        barre.getOrNull(selezionata)?.let { b ->
            val inCorso = selezionata == barre.lastIndex
            val nome = when {
                mensile && inCorso -> "Questo mese"
                mensile -> "Mese ${b.etichetta}"
                inCorso -> "Questa settimana"
                else -> "Settimana del ${b.etichetta}"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(nome, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (b.valore > 0) "${compatto(b.valore)} kg" else "nessun volume",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (b.valore > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BarChart(barre, selezionata, onSeleziona = { selezionata = it })
        Spacer(Modifier.height(6.dp))
        Text(
            "Tocca una barra per vederne il valore.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- andamento carico ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SezioneAndamento(stato: ReportState, vm: ReportViewModel) {
    SezioneCard("Andamento") {
        if (stato.eserciziDisponibili.isEmpty()) {
            Text("Nessun dato nel periodo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SezioneCard
        }
        val selNome = stato.eserciziDisponibili.firstOrNull { it.id == stato.esercizioAndamento }?.nome
            ?: stato.eserciziDisponibili.first().nome
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selNome,
                onValueChange = {},
                readOnly = true,
                label = { Text("Esercizio") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                stato.eserciziDisponibili.forEach { e ->
                    DropdownMenuItem(text = { Text(e.nome) }, onClick = { vm.selezionaEsercizio(e.id); expanded = false })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MetricaTrend.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = stato.metrica == m,
                    onClick = { vm.setMetrica(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, MetricaTrend.entries.size)
                ) { Text(m.label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val fmt: (Float) -> String = when (stato.metrica) {
            MetricaTrend.PESO -> { v -> "${formattaPeso(v)} kg" }
            MetricaTrend.VOLUME -> { v -> "${compatto(v.toDouble())} kg" }
        }
        LineChart(stato.andamento, fmt)
    }
}

// ---------- muscoli ----------

@Composable
private fun SezioneMuscoli(stato: ReportState) {
    val cs = MaterialTheme.colorScheme
    SezioneCard("Muscoli allenati") {
        stato.muscoliPeriodo?.let { muscoli ->
            Text(
                "In pieno i muscoli che hai allenato di più nel periodo, più chiari gli altri (in base alle serie).",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            MappaMuscoli(muscoli, Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
        }
        Sopratitolo("Serie per gruppo")
        Spacer(Modifier.height(8.dp))
        BarreOrizzontali(stato.distribuzioneMuscolare)
    }
}

// ---------- righe PR / storico ----------

@Composable
private fun EtichettaNuovo() {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            "NUOVO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PrRow(r: RecordEsercizio, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().magnesite(MaterialTheme.shapes.medium),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = elevazioneCard()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.nome, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
                    if (r.nuovo) EtichettaNuovo()
                }
                val stima = r.e1rm?.let { "1RM stimato ~${formattaPeso(it)} kg · " } ?: ""
                Text(
                    "${stima}fatto il ${dataBreve(r.dataMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                r.pesoMax?.let { Text("${formattaPeso(it)} kg", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }
                r.ripMax?.let {
                    Text("$it rip max", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StoricoRow(v: VoceStorico, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().magnesite(MaterialTheme.shapes.medium),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = elevazioneCard()
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.nome, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        dataStorico(v.dataMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                val durata = if (v.durataMs > 0) "${durataBreve(v.durataMs)} · " else ""
                val volume = if (v.volume > 0) " · ${compatto(v.volume)} kg" else ""
                Text(
                    "$durata${v.numSerie} serie · ${v.dettaglio.size} ${if (v.dettaglio.size == 1) "esercizio" else "esercizi"}$volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Apri dettaglio",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DettaglioSessioneSheet(
    v: VoceStorico,
    modificabile: Boolean,
    onCorreggi: (SerieStorico, CorrezioneSerie) -> Unit,
    onEliminaSerie: (SerieStorico) -> Unit,
    onEliminaSessione: () -> Unit,
    onDismiss: () -> Unit,
) {
    var daCorreggere by remember { mutableStateOf<Pair<String, SerieStorico>?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TitoloSezione(v.nome)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(v.dataMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val durata = if (v.durataMs > 0) formattaDurata(v.durataMs) else "—"
            val volume = if (v.volume > 0) " · volume ${compatto(v.volume)} kg" else ""
            Text(
                "Durata $durata · ${v.numSerie} serie$volume",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            if (v.dettaglio.isEmpty()) {
                Text("Nessuna serie registrata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (modificabile) {
                Text(
                    "Tocca una serie per correggerla o eliminarla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Allenamento in corso: potrai correggerlo quando sarà finito.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            v.dettaglio.forEach { es ->
                Text(es.nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                es.serie.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = modificabile) { daCorreggere = es.nome to s }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• ${s.testo}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (modificabile) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Correggi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            if (modificabile) {
                TextButton(
                    onClick = { confermaElimina = true },
                    modifier = Modifier.padding(top = 12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Elimina allenamento")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    daCorreggere?.let { (nome, serie) ->
        DialogoCorreggiSerie(
            nomeEsercizio = nome,
            serie = serie,
            onSalva = { correzione ->
                onCorreggi(serie, correzione)
                daCorreggere = null
            },
            onElimina = {
                onEliminaSerie(serie)
                daCorreggere = null
            },
            onAnnulla = { daCorreggere = null },
        )
    }

    if (confermaElimina) {
        AlertDialog(
            onDismissRequest = { confermaElimina = false },
            title = { Text("Eliminare l'allenamento?") },
            text = {
                Text(
                    "\"${v.nome}\" del ${dataStorico(v.dataMs)} sparirà dallo storico con le sue ${v.numSerie} serie. " +
                        "Record, massimali e grafici verranno ricalcolati senza. Non si può annullare."
                )
            },
            confirmButton = {
                TextButton(onClick = { confermaElimina = false; onEliminaSessione() }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } },
        )
    }
}

/** Correzione di una serie registrata: ripetizioni e peso, o la durata per le serie a tempo. */
@Composable
private fun DialogoCorreggiSerie(
    nomeEsercizio: String,
    serie: SerieStorico,
    onSalva: (CorrezioneSerie) -> Unit,
    onElimina: () -> Unit,
    onAnnulla: () -> Unit,
) {
    val aTempo = serie.durataSec != null
    var rip by rememberSaveable { mutableStateOf(serie.ripetizioni?.toString().orEmpty()) }
    var kg by rememberSaveable { mutableStateOf(serie.pesoKg?.toBigDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var sec by rememberSaveable { mutableStateOf(serie.durataSec?.toString().orEmpty()) }
    var confermaElimina by remember { mutableStateOf(false) }
    val correzione = correzioneDa(aTempo, rip, kg, sec)

    if (confermaElimina) {
        AlertDialog(
            onDismissRequest = { confermaElimina = false },
            title = { Text("Eliminare la serie?") },
            text = { Text("\"${serie.testo}\" di $nomeEsercizio sparirà dallo storico. Non si può annullare.") },
            confirmButton = { TextButton(onClick = onElimina) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Correggi serie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(nomeEsercizio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (aTempo) {
                    OutlinedTextField(
                        value = sec,
                        onValueChange = { t -> sec = t.filter(Char::isDigit).take(5) },
                        label = { Text("Durata (secondi)") },
                        singleLine = true,
                        isError = correzione == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    OutlinedTextField(
                        value = rip,
                        onValueChange = { t -> rip = t.filter(Char::isDigit).take(3) },
                        label = { Text("Ripetizioni") },
                        singleLine = true,
                        isError = rip.toIntOrNull() == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = kg,
                        onValueChange = { t -> kg = t.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) },
                        label = { Text("Peso (kg)") },
                        placeholder = { Text("Corpo libero: lascia vuoto") },
                        singleLine = true,
                        isError = rip.toIntOrNull() != null && correzione == null,
                        supportingText = { Text("Massimo ${KG_MASSIMI_CORREZIONE} kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                TextButton(
                    onClick = { confermaElimina = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Elimina serie") }
            }
        },
        confirmButton = {
            TextButton(onClick = { correzione?.let(onSalva) }, enabled = correzione != null) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
    )
}

private const val KG_MASSIMI_CORREZIONE = 1000

@Composable
private fun SezioneCard(titolo: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().magnesite(MaterialTheme.shapes.medium),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = elevazioneCard()
    ) {
        Column(Modifier.padding(18.dp)) {
            TitoloSezione(titolo)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ---------- grafici ----------

@Composable
private fun BarChart(barre: List<BarraValore>, selezionata: Int, onSeleziona: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val max = barre.maxOfOrNull { it.valore } ?: 0.0
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        barre.forEachIndexed { i, b ->
            val frac = if (max > 0) (b.valore / max).toFloat() else 0f
            val sel = i == selezionata
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSeleziona(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Valore solo sulle barre piene: tanti "0" erano solo rumore
                if (b.valore > 0) {
                    Text(
                        compatto(b.valore),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) cs.onSurface else cs.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (b.valore > 0) (6f + frac * 104f).dp else 4.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(
                            when {
                                b.valore <= 0 -> cs.surfaceVariant
                                sel -> cs.primaryContainer
                                else -> cs.primaryContainer.copy(alpha = 0.4f)
                            }
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    b.etichetta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sel) cs.onSurface else cs.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LineChart(punti: List<PuntoAndamento>, fmt: (Float) -> String) {
    if (punti.size < 2) {
        Text(
            "Servono almeno 2 sessioni con questo esercizio per il grafico.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val valori = punti.map { it.valore }
    val maxV = valori.max()
    val minV = valori.min()
    val range = (maxV - minV).coerceAtLeast(1f)
    val colore = MaterialTheme.colorScheme.primary
    val riempimento = MaterialTheme.colorScheme.primaryContainer
    val griglia = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val internoPunto = MaterialTheme.colorScheme.surfaceContainerHighest

    Column {
        Text(
            "max ${fmt(maxV)} · min ${fmt(minV)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(griglia, Offset(0f, y), Offset(w, y), strokeWidth = 2f)
            }
            val dx = w / (punti.size - 1)
            val offs = valori.mapIndexed { i, v -> Offset(i * dx, h - ((v - minV) / range) * h) }

            val area = Path().apply {
                moveTo(offs.first().x, h)
                offs.forEach { lineTo(it.x, it.y) }
                lineTo(offs.last().x, h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(riempimento.copy(alpha = 0.35f), riempimento.copy(alpha = 0.02f))))

            for (i in 0 until offs.size - 1) {
                drawLine(colore, offs[i], offs[i + 1], strokeWidth = 5f)
            }
            offs.forEach { drawCircle(colore, radius = 6f, center = it) }
            // Ultimo punto evidenziato
            drawCircle(colore, radius = 11f, center = offs.last())
            drawCircle(internoPunto, radius = 5f, center = offs.last())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dataBreve(punti.first().dataMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${dataBreve(punti.last().dataMs)} · ${fmt(punti.last().valore)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BarreOrizzontali(barre: List<BarraValore>) {
    val max = barre.maxOfOrNull { it.valore }?.coerceAtLeast(1.0) ?: 1.0
    val totale = barre.sumOf { it.valore }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        barre.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.etichetta, Modifier.width(78.dp), style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((b.valore / max).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                Text(
                    "${b.valore.toInt()} · ${(b.valore / totale * 100).roundToInt()}%",
                    Modifier.padding(start = 8.dp).width(64.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ---------- helper ----------

private fun compatto(v: Double): String =
    if (v >= 1000) "%.1fk".format(v / 1000) else v.toInt().toString()

private fun formattaDurata(ms: Long): String {
    val min = TimeUnit.MILLISECONDS.toMinutes(ms)
    val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d min %02d s".format(min, sec)
}

private fun formattaPeso(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else "%.1f".format(kg)

private fun dataBreve(ms: Long): String =
    SimpleDateFormat("d MMM", Locale.ITALIAN).format(Date(ms))

/** Es. "dom 22 nov"; con l'anno se non è quello in corso. */
private fun dataStorico(ms: Long): String {
    val anno = SimpleDateFormat("yyyy", Locale.ITALIAN)
    val stessoAnno = anno.format(Date(ms)) == anno.format(Date())
    return SimpleDateFormat(if (stessoAnno) "EEE d MMM" else "d MMM yyyy", Locale.ITALIAN).format(Date(ms))
}

/** Es. "45 s", "52 min", "1 h 05". */
private fun durataBreve(ms: Long): String {
    val sec = TimeUnit.MILLISECONDS.toSeconds(ms)
    val min = sec / 60
    return when {
        min < 1 -> "$sec s"
        min < 60 -> "$min min"
        else -> "%d h %02d".format(min / 60, min % 60)
    }
}
