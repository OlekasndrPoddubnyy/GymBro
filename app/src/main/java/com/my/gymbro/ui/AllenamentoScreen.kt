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

private val RIPETIZIONI_RUOTA = (0..200).toList()

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
    // L'esercizio si fissa all'apertura: mentre si sceglie, una serie a tempo può finire e passare al successivo.
    var daSostituire by remember { mutableStateOf<Int?>(null) }
    // Dopo una sostituzione: l'esercizio di prima e quello nuovo, per chiedere se salvarla nella scheda.
    var anchePerScheda by remember { mutableStateOf<Pair<EsercizioEsec, Esercizio>?>(null) }
    var confermaTermina by remember { mutableStateOf(false) }

    if (confermaTermina) {
        val fatte = stato.log.size
        AlertDialog(
            onDismissRequest = { confermaTermina = false },
            title = { Text("Terminare l'allenamento?") },
            text = {
                Text(
                    if (fatte == 0) "Non hai ancora registrato nessuna serie: l'allenamento non verrà salvato."
                    else "Hai registrato $fatte serie. Le serie rimanenti non verranno fatte e l'allenamento non si potrà riprendere."
                )
            },
            confirmButton = {
                TextButton(onClick = { confermaTermina = false; vm.terminaOra() }) { Text("Termina") }
            },
            dismissButton = {
                TextButton(onClick = { confermaTermina = false }) { Text("Continua") }
            }
        )
    }

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
                onClick = { confermaTermina = true },
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
                        Text(es.nome, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        // Con i lati alternati è il numero della serie a dire da che parte mettersi.
                        Text("Serie ${stato.indiceSerie + 1} di ${es.serie.size}", style = MaterialTheme.typography.bodyMedium)
                        FotoEsercizio(es)
                    }
                    BottoneSecondario("Salta", { vm.saltaPreparazione() }, icona = Icons.Filled.SkipNext)
                }

                Fase.ESECUZIONE -> {
                    EsecuzioneSerie(stato, vm)
                    TextButton(onClick = { daSostituire = stato.indiceEsercizio }) { Text("Sostituisci esercizio") }
                }

                Fase.RECUPERO -> {
                    Etichetta("Riposo", riposo.accentoTesto)
                    Cronometro(stato.secondiRimanenti, avvisaFine = false)
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
                    TextButton(onClick = { daSostituire = stato.indiceEsercizio }) { Text("Sostituisci esercizio") }
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

    daSostituire?.let { indice ->
        SostituzioneSheet(
            libreria = libreria,
            onScelto = { es ->
                val vecchio = stato.esercizi.getOrNull(indice)
                vm.sostituisci(indice, es)
                if (vecchio != null && vecchio.esercizioId != es.id) anchePerScheda = vecchio to es
                daSostituire = null
            },
            onDismiss = { daSostituire = null }
        )
    }

    anchePerScheda?.let { (vecchio, nuovo) ->
        val buddy: GymBuddyViewModel = viewModel(factory = GymBuddyViewModel.Factory)
        // Raccolta qui, così l'ultimo carico usato è già caricato quando si conferma.
        val vista by buddy.vista.collectAsStateWithLifecycle()
        val schedaId by AllenamentoEngine.schedaInCorso.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { anchePerScheda = null },
            title = { Text("Cambiarlo anche nella scheda?") },
            text = {
                Text(
                    "Per oggi fai \"${nuovo.nome}\" al posto di \"${vecchio.nome}\". " +
                        "Vuoi che resti così anche nei prossimi allenamenti di \"${stato.nomeScheda}\"? " +
                        "Potrai tornare indietro dal dettaglio della scheda."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    schedaId?.let { id ->
                        buddy.sostituisci(
                            schedaId = id,
                            vecchioId = vecchio.esercizioId,
                            ripetizioni = vecchio.serie.firstOrNull()?.ripetizioniPianificate,
                            nuovo = nuovo,
                            carico = vista.ultimoCarico[nuovo.id],
                        )
                    }
                    anchePerScheda = null
                }) { Text("Anche nella scheda") }
            },
            dismissButton = { TextButton(onClick = { anchePerScheda = null }) { Text("Solo oggi") } },
        )
    }
    ErroreGymBuddy()
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
            if (stato.lavoroLibero) {
                Cronometro(stato.secondiTrascorsi, avvisaFine = false)
                Text(
                    "Tocca Completa quando hai finito",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Cronometro(stato.secondiRimanenti)
            }
        }
        Modalita.RIPETIZIONI -> {
            val pianificato = buildString {
                serie.ripetizioniPianificate?.takeIf { it.isNotBlank() }?.let { append("$it rip") }
                serie.pesoPianificato?.let { if (isNotEmpty()) append(" · "); append("${formattaPeso(it)} kg") }
            }
            val obiettivo = es.obiettivo
            if (obiettivo != null) {
                Text(
                    "Obiettivo: ${descriviObiettivo(obiettivo)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (pianificato.isNotBlank()) {
                Text("Pianificato: $pianificato", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val passo = stato.indicePasso
            // Ruote nuove a ogni serie: con recupero 0 la schermata resta la stessa e terrebbero i numeri di prima.
            key(passo, es.esercizioId) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ripetizioni", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val rip = stato.ripInput.toIntOrNull() ?: 0
                        RuotaPicker(
                            valori = remember { RIPETIZIONI_RUOTA.conValore(rip) },
                            selezionato = rip,
                            onSelezione = { vm.aggiornaRip(passo, it.toString()) },
                            modifier = Modifier.width(72.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Peso (kg)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RuotaPeso(
                            peso = stato.pesoInput.toFloatOrNull() ?: 0f,
                            onChange = { vm.aggiornaPeso(passo, formattaKgRuota(it)) }
                        )
                    }
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
private fun Cronometro(secondi: Int, avvisaFine: Boolean = true) {
    NumeriInchiostro(
        formattaTempo(secondi),
        altezza = 68.dp,
        colore = if (secondi in 1..3 && avvisaFine) MaterialTheme.colorScheme.error else LocalContentColor.current
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
    val perEsercizio = remember(stato.log) { stato.log.groupBy { it.esercizioId } }
    val volume = remember(stato.log, stato.nomiEseguiti) { volumeSerie(stato, stato.log) }
    val confronto = confrontoConStorico(stato.log)
    val record = confronto.values.count { it.record }
    val muscoli = remember(stato.log, stato.esercizi) { muscoliAllenamento(stato) }

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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TesseraStat("Durata", formattaDurataBreve(stato.durataTotaleMs), Modifier.weight(1f))
                        TesseraStat("Esercizi", "${perEsercizio.size}", Modifier.weight(1f))
                        TesseraStat("Serie", "${stato.log.size}", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TesseraStat("Volume", if (volume > 0) "${formattaPeso(volume.toFloat())} kg" else "—", Modifier.weight(1.3f))
                        TesseraStat(
                            "Record",
                            if (record > 0) "$record" else "—",
                            Modifier.weight(1f),
                            coloreValore = if (record > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item { ProposteFineAllenamento(stato) }

            muscoli?.let { m ->
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = coloriCard(),
                        border = bordoCard(),
                        elevation = elevazioneCard()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TitoloSezione("Muscoli lavorati")
                            MappaMuscoli(m, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (perEsercizio.isEmpty()) {
                item { Text("Nessuna serie registrata.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { TitoloSezione("Dettaglio", Modifier.padding(top = 8.dp)) }
            }

            items(perEsercizio.entries.toList()) { (id, serie) ->
                val conf = confronto[id]
                Card(
                    Modifier.fillMaxWidth().animateItem(),
                    shape = RoundedCornerShape(18.dp),
                    colors = coloriCard(),
                    border = bordoCard(),
                    elevation = elevazioneCard()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stato.nomiEseguiti[id] ?: "Esercizio",
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleMedium
                            )
                            when {
                                conf?.record == true -> EtichettaRiepilogo("RECORD", evidenziata = true)
                                conf?.primaVolta == true -> EtichettaRiepilogo("PRIMA VOLTA", evidenziata = false)
                            }
                        }
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
                        // Volume dell'esercizio e confronto con l'ultima volta
                        val volEs = volumeSerie(stato, serie)
                        if (volEs > 0) {
                            val delta = conf?.deltaVolume
                            val confrontoTesto = when {
                                delta == null -> ""
                                delta > 0 -> " · ▲ ${formattaPeso(delta.toFloat())} kg rispetto all'ultima volta"
                                delta < 0 -> " · ▼ ${formattaPeso(-delta.toFloat())} kg rispetto all'ultima volta"
                                else -> " · come l'ultima volta"
                            }
                            Text(
                                "Volume ${formattaPeso(volEs.toFloat())} kg$confrontoTesto",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (delta != null && delta > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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

@Composable
private fun EtichettaRiepilogo(testo: String, evidenziata: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (evidenziata) cs.primaryContainer else cs.surfaceContainerHigh,
        contentColor = if (evidenziata) cs.onPrimaryContainer else cs.onSurfaceVariant
    ) {
        Text(testo, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
