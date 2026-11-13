package com.my.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun ReportScreen() {
    val vm: ReportViewModel = viewModel(factory = ReportViewModel.Factory)
    val stato by vm.stato.collectAsState()
    var sessioneAperta by remember { mutableStateOf<VoceStorico?>(null) }

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
            item { SezioneCard("Costanza — ultime 14 settimane") { Heatmap(stato.heatmap) } }
            item { SezioneVolume(stato) }
            item { SezioneAndamento(stato, vm) }

            if (stato.distribuzioneMuscolare.isNotEmpty()) {
                item {
                    SezioneCard("Distribuzione muscolare (serie)") {
                        BarreOrizzontali(stato.distribuzioneMuscolare)
                    }
                }
            }

            item { Text("Record personali", style = MaterialTheme.typography.titleMedium) }
            if (stato.pr.isEmpty()) {
                item { Text("Nessun record nel periodo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(stato.pr, key = { it.nome }) { PrRow(it, Modifier.animateItem()) }

            item { Text("Storico", style = MaterialTheme.typography.titleMedium) }
            if (stato.storico.isEmpty()) {
                item { Text("Nessuna sessione nel periodo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(stato.storico, key = { it.id }) { v ->
                StoricoRow(v, onClick = { sessioneAperta = v }, modifier = Modifier.animateItem())
            }
        }
    }

    sessioneAperta?.let { v ->
        DettaglioSessioneSheet(v, onDismiss = { sessioneAperta = null })
    }
}

// ---------- filtro periodo ----------

@Composable
private fun FiltroPeriodo(corrente: PeriodoReport, onSelezione: (PeriodoReport) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PeriodoReport.entries) { p ->
            FilterChip(
                selected = corrente == p,
                onClick = { onSelezione(p) },
                label = { Text(p.label) }
            )
        }
    }
}

// ---------- riepilogo ----------

@Composable
private fun Riepilogo(stato: ReportState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Riepilogo", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Sessioni", stato.numeroSessioni.toString())
                Stat("Giorni", stato.giorniAllenati.toString())
                Stat("Serie", stato.serieTotali.toString())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Sett. di fila", stato.settimaneConsecutive.toString())
                Stat("Record sett.", stato.migliorStreak.toString())
                Stat("Questa sett.", stato.allenamentiSettimana.toString())
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Volume totale: ${compatto(stato.volumeTotale)} kg", style = MaterialTheme.typography.bodyLarge)
                if (stato.durataMediaMin > 0) {
                    Text(
                        "Durata media sessione: ${stato.durataMediaMin} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, valore: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valore, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- heatmap costanza ----------

@Composable
private fun Heatmap(settimane: List<SettimanaAttivita>) {
    if (settimane.isEmpty()) return
    val vuoto = MaterialTheme.colorScheme.surfaceVariant
    val basso = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val alto = MaterialTheme.colorScheme.primary
    val giorniLabel = listOf("L", "M", "M", "G", "V", "S", "D")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                giorniLabel.forEach { g ->
                    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        Text(g, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            settimane.forEach { settimana ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    settimana.giorni.forEach { conteggio ->
                        val colore = when {
                            conteggio == null -> Color.Transparent
                            conteggio == 0 -> vuoto
                            conteggio == 1 -> basso
                            else -> alto
                        }
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colore)
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Meno", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(vuoto))
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(basso))
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(alto))
            Text("Più", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Spacer(Modifier.height(12.dp))
        BarChart(if (mensile) stato.volumeMensile else stato.volumeSettimanale)
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

// ---------- righe PR / storico ----------

@Composable
private fun PrRow(r: RecordEsercizio, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.nome, style = MaterialTheme.typography.bodyLarge)
                r.e1rm?.let {
                    Text(
                        "1RM stimato ~${formattaPeso(it)} kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                r.pesoMax?.let { Text("${formattaPeso(it)} kg", style = MaterialTheme.typography.titleMedium) }
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
        modifier.fillMaxWidth(),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.nome, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(v.dataMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                val durata = if (v.durataMs > 0) formattaDurata(v.durataMs) else "—"
                val volume = if (v.volume > 0) " · ${compatto(v.volume)} kg" else ""
                Text(
                    "Durata $durata · ${v.numSerie} serie$volume",
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
private fun DettaglioSessioneSheet(v: VoceStorico, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(v.nome, style = MaterialTheme.typography.titleLarge)
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
            v.dettaglio.forEach { es ->
                Text(es.nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                es.serie.forEach { s ->
                    Text("• $s", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SezioneCard(titolo: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = coloriCard(),
        border = bordoCard(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titolo, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ---------- grafici ----------

@Composable
private fun BarChart(barre: List<BarraValore>) {
    val max = barre.maxOfOrNull { it.valore } ?: 0.0
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        barre.forEach { b ->
            val frac = if (max > 0) (b.valore / max).toFloat() else 0f
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(compatto(b.valore), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((4f + frac * 110f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.height(4.dp))
                Text(b.etichetta, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
            drawPath(area, Brush.verticalGradient(listOf(colore.copy(alpha = 0.30f), colore.copy(alpha = 0.02f))))

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
                            .background(MaterialTheme.colorScheme.primary)
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
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
