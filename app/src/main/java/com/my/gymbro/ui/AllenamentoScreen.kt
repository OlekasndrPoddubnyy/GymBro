package com.my.gymbro.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.sp
import com.my.gymbro.ui.theme.Inchiostro
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
    onRiduci: () -> Unit,
    onFine: () -> Unit,
) {
    // L'allenamento lo avvia GymBroApp tramite AllenamentoEngine: qui lo si mostra soltanto.
    val vm: AllenamentoViewModel = viewModel(factory = AllenamentoViewModel.Factory)

    val stato by vm.stato.collectAsStateWithLifecycle()
    KeepScreenOn()
    // Indietro riduce l'allenamento (continua in sottofondo); beep e vibrazione li fa il TimerService.
    BackHandler(onBack = if (stato.fase == Fase.COMPLETATO) onFine else onRiduci)

    Box(Modifier.fillMaxSize().sfondoApp()) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            when {
                !stato.pronta -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                stato.fase == Fase.COMPLETATO -> Riepilogo(stato, onChiudi = onFine)
                else -> EsecuzioneAttiva(stato, vm, onEsci = onRiduci)
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
    val libreria by vm.libreria.collectAsStateWithLifecycle()
    var mostraSostituzione by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BottoneIcona(Icons.Filled.KeyboardArrowDown, "Riduci (l'allenamento continua)", onEsci)
            Column(Modifier.weight(1f)) {
                Sopratitolo("Allenamento in corso")
                Text(stato.nomeScheda, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            BottoneSecondario(
                testo = "Termina",
                onClick = { vm.terminaOra() },
                contenitore = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.height(44.dp)
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        BarraAvanzamentoScheda(stato)

        // Pannello della fase: corallo pieno durante il recupero, card normale altrimenti.
        val recupero = stato.fase == Fase.RECUPERO
        val cs = MaterialTheme.colorScheme
        val riposo = coloriRiposo()
        val fondoPannello by animateColorAsState(if (recupero) riposo.pannello else cs.surfaceContainer, tween(500), label = "pannello")
        val testoPannello by animateColorAsState(if (recupero) riposo.testo else cs.onSurface, tween(500), label = "testoPannello")
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = fondoPannello,
            contentColor = testoPannello,
            border = if (recupero) null else bordoCard(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BarraProgressoFase(stato, recupero)
            when (stato.fase) {
                Fase.PREPARAZIONE -> {
                    Etichetta("Preparati")
                    Cronometro(stato.secondiRimanenti)
                    stato.esercizioCorrente?.let { es ->
                        Text("Inizia con: ${es.nome}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        FotoEsercizio(es)
                    }
                    BottoneSecondario("Salta", { vm.saltaPreparazione() }, icona = Icons.Filled.SkipNext)
                }

                Fase.ESECUZIONE -> {
                    EsecuzioneSerie(stato, vm)
                    TextButton(onClick = { mostraSostituzione = true }) { Text("Sostituisci esercizio") }
                }

                Fase.RECUPERO -> {
                    Etichetta("Riposo", riposo.accentoTesto)
                    Cronometro(stato.secondiRimanenti, inRecupero = true)
                    stato.esercizioCorrente?.let { es ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiniaturaEsercizio(
                                nome = es.nome,
                                path = es.immaginePath,
                                descrizione = es.descrizione,
                                nota = es.note,
                                gruppo = es.gruppoMuscolare,
                                modifier = Modifier.size(72.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Sopratitolo("Poi", colore = riposo.accentoTesto)
                                Text(es.nome, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Serie ${stato.indiceSerie + 1} di ${es.serie.size}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    BottoneSecondario(
                        "Salta recupero",
                        { vm.saltaRecupero() },
                        icona = Icons.Filled.SkipNext,
                        contenitore = riposo.accento,
                        contenuto = riposo.suAccento
                    )
                }

                Fase.ATTESA -> {
                    Etichetta("Prossimo esercizio")
                    stato.esercizioCorrente?.let {
                        Text(it.nome.uppercase(), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                        FotoEsercizio(it)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BottoneSecondario("Salta", { vm.saltaEsercizio() }, Modifier.weight(1f))
                        BottonePrincipale("Inizia", { vm.iniziaProssimo() }, Modifier.weight(1f), icona = Icons.Filled.PlayArrow)
                    }
                    TextButton(onClick = { mostraSostituzione = true }) { Text("Sostituisci esercizio") }
                }

                Fase.COMPLETATO -> Unit
            }
        }
        }

        TitoloSezione(
            "Esercizi",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        ListaStato(stato, onVai = { vm.vaiAEsercizio(it) })
        Spacer(Modifier.height(16.dp))
        }
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
        // Esercizio toccato: prima di sostituire se ne mostra l'anteprima con foto e muscoli
        var anteprima by remember { mutableStateOf<Esercizio?>(null) }
        anteprima?.let { es ->
            AnteprimaScelta(
                es = es,
                testoConferma = "Sostituisci",
                onConferma = { anteprima = null; onScelto(es) },
                onAnnulla = { anteprima = null }
            )
        }
        TitoloSezione(
            "Sostituisci con",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        FiltriEsercizi(filtro, { filtro = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(libreria.filtra(filtro), key = { it.id }) { es ->
                ListItem(
                    headlineContent = { Text(es.nome, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text("${es.gruppoMuscolare.label} · ${es.attrezzatura.label}") },
                    leadingContent = { MiniaturaLibreria(es, Modifier.size(72.dp), cliccabile = false) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { anteprima = es }
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
    val fatti = stato.passiFatti.coerceIn(0, stato.passiTotali)
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
        LinearProgressIndicator(
            progress = { pa },
            color = MaterialTheme.colorScheme.primaryContainer,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
private fun BarraProgressoFase(stato: StatoEsecuzione, recupero: Boolean) {
    val mostra = stato.fase == Fase.PREPARAZIONE || stato.fase == Fase.RECUPERO ||
        (stato.fase == Fase.ESECUZIONE && stato.esercizioCorrente?.modalita == Modalita.TEMPO)
    if (!mostra || stato.secondiTotali <= 0) return
    val p = (stato.secondiTotali - stato.secondiRimanenti).toFloat() / stato.secondiTotali
    val pa by animateFloatAsState(p.coerceIn(0f, 1f), tween(1000, easing = LinearEasing), label = "progFase")
    LinearProgressIndicator(
        progress = { pa },
        color = if (recupero) coloriRiposo().accento else MaterialTheme.colorScheme.primaryContainer,
        trackColor = if (recupero) coloriRiposo().traccia else MaterialTheme.colorScheme.surfaceContainerHigh,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().height(10.dp)
    )
}

@Composable
private fun EsecuzioneSerie(stato: StatoEsecuzione, vm: AllenamentoViewModel) {
    val es = stato.esercizioCorrente ?: return
    val serie = stato.serieCorrente ?: return

    Sopratitolo("Serie ${stato.indiceSerie + 1} di ${es.serie.size}", colore = MaterialTheme.colorScheme.primary)
    Text(es.nome.uppercase(), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    FotoEsercizio(es)

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

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BottoneSecondario("Salta", { vm.saltaSerie() }, Modifier.weight(1f).height(56.dp), icona = Icons.Filled.SkipNext)
        BottonePrincipale("Completa", { vm.completaSerie() }, Modifier.weight(1.4f), icona = Icons.Filled.Check)
    }
}

@Composable
private fun Etichetta(testo: String, colore: Color = MaterialTheme.colorScheme.primary) {
    Text(
        testo.uppercase(),
        style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
        color = colore
    )
}

@Composable
private fun Cronometro(secondi: Int, inRecupero: Boolean = false) {
    Text(
        formattaTempo(secondi),
        style = MaterialTheme.typography.displayLarge,
        color = if (secondi in 1..3 && !inRecupero) MaterialTheme.colorScheme.error else LocalContentColor.current
    )
}

@Composable
private fun ListaStato(
    stato: StatoEsecuzione,
    onVai: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        stato.esercizi.forEachIndexed { indice, es ->
            val corrente = indice == stato.indiceEsercizio
            val fatto = stato.eserciziFatti.contains(indice)
            ListItem(
                headlineContent = { Text(es.nome, style = MaterialTheme.typography.titleSmall) },
                supportingContent = {
                    val superset = if (es.gruppo != null) " · Superset" else ""
                    val fatte = stato.serieFatte[indice] ?: 0
                    Text("$fatte/${es.serie.size} serie fatte · ${es.modalita.label}$superset")
                },
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            when {
                                fatto -> Icon(Icons.Filled.Check, contentDescription = "Completato", tint = MaterialTheme.colorScheme.primary)
                                corrente -> Icon(Icons.Filled.PlayArrow, contentDescription = "In corso", tint = MaterialTheme.colorScheme.primary)
                                else -> Text(
                                    "%02d".format(indice + 1),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        MiniaturaEsercizio(
                            nome = es.nome,
                            path = es.immaginePath,
                            descrizione = es.descrizione,
                            nota = es.note,
                            gruppo = es.gruppoMuscolare,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = if (corrente) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
                ),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onVai(indice) }
            )
        }
    }
}

/** Foto grande dell'esercizio (animata inizio/fine) + note di tecnica; tocca per ingrandire. */
@Composable
private fun FotoEsercizio(es: EsercizioEsec) {
    if (es.immaginePath.isNullOrBlank() && es.note.isNullOrBlank() && es.gruppoMuscolare == null) return
    var dettaglio by remember(es.esercizioId) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { dettaglio = true },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImmagineEsercizioAnimata(es.immaginePath)
        muscoliCoinvolti(es.nome, es.gruppoMuscolare)?.let { mc ->
            Text(
                buildString {
                    if (es.gruppoMuscolare == com.my.gymbro.data.GruppoMuscolare.STRETCHING) {
                        append("Allunga: ").append(mc.testoPrincipali)
                    } else {
                        append("Muscoli: ").append(mc.testoPrincipali)
                        if (mc.secondari.isNotEmpty()) append(" · aiutano ").append(mc.testoSecondari.lowercase())
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        es.note?.takeIf { it.isNotBlank() }?.let { nota ->
            Text(
                nota,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.8f)
            )
        }
    }
    if (dettaglio) {
        DialogoEsercizio(es.nome, es.immaginePath, es.descrizione, es.note, es.gruppoMuscolare, onChiudi = { dettaglio = false })
    }
}

@Composable
private fun Riepilogo(stato: StatoEsecuzione, onChiudi: () -> Unit) {
    val nomi = remember(stato.esercizi) { stato.esercizi.associate { it.esercizioId to it.nome } }
    val perEsercizio = remember(stato.log) { stato.log.groupBy { it.esercizioId } }
    val volume = remember(stato.log) {
        stato.log.sumOf { ((it.pesoKg ?: 0f) * (it.ripetizioni ?: 0)).toDouble() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Sopratitolo("Allenamento completato")
                    Text("GRANDE\nLAVORO", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    Text(stato.nomeScheda, style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TesseraStat("Durata", formattaDurataBreve(stato.durataTotaleMs), Modifier.weight(1f))
                    TesseraStat("Serie", "${stato.log.size}", Modifier.weight(1f))
                    TesseraStat("Volume", if (volume > 0) "${formattaPeso(volume.toFloat())} kg" else "—", Modifier.weight(1.3f))
                }
            }

            if (perEsercizio.isEmpty()) {
                item { Text("Nessuna serie registrata.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { TitoloSezione("Dettaglio", Modifier.padding(top = 8.dp)) }
            }

            items(perEsercizio.entries.toList()) { (id, serie) ->
                Card(
                    Modifier.fillMaxWidth().animateItem(),
                    shape = RoundedCornerShape(18.dp),
                    colors = coloriCard(),
                    border = bordoCard(),
                    elevation = elevazioneCard()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(nomi[id] ?: "Esercizio", style = MaterialTheme.typography.titleMedium)
                        serie.sortedBy { it.numero }.forEach { se ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${se.numero}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(descriviSerieEseguita(se), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        BottonePrincipale(
            testo = "Fine",
            onClick = onChiudi,
            icona = Icons.Filled.Check,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
        )
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

private fun formattaDurataBreve(ms: Long): String {
    val min = TimeUnit.MILLISECONDS.toMinutes(ms)
    return if (min >= 60) "%dh%02d".format(min / 60, min % 60) else "$min′"
}

private fun formattaDurata(ms: Long): String {
    val min = TimeUnit.MILLISECONDS.toMinutes(ms)
    val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d min %02d s".format(min, sec)
}

private fun formattaPeso(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else kg.toString()
