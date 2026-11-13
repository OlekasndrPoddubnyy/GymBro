package com.my.gymbro.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.my.gymbro.TimerService

@Composable
fun TimerScreen() {
    val stato by TimerEngine.stato.collectAsState()
    val context = LocalContext.current
    val feedback = rememberFeedbackController()
    LaunchedEffect(Unit) { TimerEngine.segnali.collect { feedback(it) } }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val concesso = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!concesso) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (stato.fase == FaseTimer.CONFIG) {
        ConfigTimer(
            stato = stato,
            onAvvia = {
                TimerEngine.avvia()
                TimerService.start(context)
            }
        )
    } else {
        TimerAttivo(stato)
    }
}

@Composable
private fun ConfigTimer(stato: StatoTimer, onAvvia: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Timer a serie", style = MaterialTheme.typography.headlineSmall)

        Card(
            Modifier.fillMaxWidth(),
            colors = coloriCard(),
            border = bordoCard(),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SezionePicker("Serie") {
                    RuotaPicker(
                        valori = (1..50).toList(),
                        selezionato = stato.serieTotali,
                        onSelezione = { TimerEngine.setSerie(it) },
                        modifier = Modifier.width(96.dp)
                    )
                }
                HorizontalDivider()
                SezionePicker("Durata serie") {
                    RuotaTempo(stato.durataSec, onChange = { TimerEngine.setDurata(it) })
                }
                HorizontalDivider()
                SezionePicker("Recupero (0:00 = manuale)") {
                    RuotaTempo(stato.recuperoSec, onChange = { TimerEngine.setRecupero(it) })
                }
            }
        }

        BottoneGradiente(onClick = onAvvia, modifier = Modifier.fillMaxWidth().height(60.dp))
        val totale = stato.serieTotali * stato.durataSec + (stato.serieTotali - 1).coerceAtLeast(0) * stato.recuperoSec
        Text(
            "Durata stimata: ${formattaTimer(totale)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SezionePicker(label: String, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        content()
    }
}


@Composable
private fun BottoneGradiente(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(cs.primary, cs.tertiary)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = cs.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text("START", color = cs.onPrimary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TimerAttivo(stato: StatoTimer) {
    KeepScreenOn()
    BackHandler { TimerEngine.stop() }
    val flex = inFlexMode()

    val totale = when (stato.fase) {
        FaseTimer.SERIE -> stato.durataSec
        FaseTimer.RECUPERO -> stato.recuperoSec
        else -> 0
    }
    val progresso = if (totale > 0) (totale - stato.secondiRimanenti).toFloat() / totale else 0f
    val progressoAnim by animateFloatAsState(
        targetValue = progresso.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "riempimento"
    )
    val coloreFill = if (stato.fase == FaseTimer.RECUPERO) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        // Riempimento animato dal basso in base alla progressione del timer
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(progressoAnim)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(coloreFill.copy(alpha = 0.28f), coloreFill.copy(alpha = 0.06f))
                    )
                )
        )

        if (flex) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { InfoTimer(stato, progressoAnim) }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { BottoniTimer(stato) }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                InfoTimer(stato, progressoAnim)
                Spacer(Modifier.weight(1f))
                BottoniTimer(stato)
            }
        }
    }
}

@Composable
private fun InfoTimer(stato: StatoTimer, progresso: Float) {
    Text(
        "Serie ${stato.serieCorrente + 1} di ${stato.serieTotali}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Crossfade(targetState = stato.fase, label = "etichetta") { fase ->
        Text(
            when (fase) {
                FaseTimer.SERIE -> "Lavoro"
                FaseTimer.RECUPERO -> "Recupero"
                FaseTimer.ATTESA -> "Pronto?"
                else -> ""
            },
            style = MaterialTheme.typography.titleLarge
        )
    }

    val ultimi3 = stato.fase != FaseTimer.ATTESA && stato.secondiRimanenti in 1..3
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "scala"
    )
    val scala = if (ultimi3) pulse else 1f

    if (stato.fase == FaseTimer.ATTESA) {
        Text("Avanti", style = MaterialTheme.typography.displayMedium)
    } else {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progresso },
                modifier = Modifier.size(220.dp),
                strokeWidth = 10.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = if (stato.fase == FaseTimer.RECUPERO) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
            Text(
                formattaTimer(stato.secondiRimanenti),
                style = MaterialTheme.typography.displayLarge,
                color = if (ultimi3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { scaleX = scala; scaleY = scala }
            )
        }
    }
}

@Composable
private fun BottoniTimer(stato: StatoTimer) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = { TimerEngine.stop() }, modifier = Modifier.weight(1f)) { Text("Stop") }
        if (stato.fase == FaseTimer.ATTESA) {
            Button(onClick = { TimerEngine.avanti() }, modifier = Modifier.weight(1f)) { Text("Avanti") }
        } else {
            Button(onClick = { TimerEngine.skip() }, modifier = Modifier.weight(1f)) { Text("Salta") }
        }
    }
}

private fun formattaTimer(secondi: Int): String = "%d:%02d".format(secondi / 60, secondi % 60)
