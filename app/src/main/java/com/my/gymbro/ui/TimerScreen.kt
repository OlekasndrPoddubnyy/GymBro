package com.my.gymbro.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.gymbro.TimerService
import com.my.gymbro.ui.theme.Inchiostro
import com.my.gymbro.ui.theme.StileTitoloSchermata
import com.my.gymbro.ui.theme.StileTitoloSezione

@Composable
fun TimerScreen() {
    val stato by TimerEngine.stato.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Beep e vibrazione li riproduce il TimerService (funzionano anche su altre schermate o in background)

    val chiediNotifiche = rememberRichiestaNotifiche()

    if (stato.fase == FaseTimer.CONFIG) {
        ConfigTimer(
            stato = stato,
            onAvvia = {
                chiediNotifiche()
                TimerEngine.avvia()
                TimerService.start(context)
            }
        )
    } else {
        TimerAttivo(stato)
    }
}

// ---------------- Configurazione ----------------

@Composable
private fun ConfigTimer(stato: StatoTimer, onAvvia: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val totale = stato.serieTotali * stato.durataSec + (stato.serieTotali - 1).coerceAtLeast(0) * stato.recuperoSec

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Totale + sequenza lavoro/recupero
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Sopratitolo(if (stato.lavoroLibero) "Recuperi totali" else "Durata totale", Modifier.weight(1f))
            Text(formattaTimer(totale), style = MaterialTheme.typography.headlineLarge, color = cs.primary)
        }
        SequenzaBlocchi(stato.serieTotali, stato.durataSec, stato.recuperoSec)

        BloccoPicker("Serie", cs.onSurfaceVariant) {
            RuotaPicker(
                valori = (1..50).toList(),
                selezionato = stato.serieTotali,
                onSelezione = { TimerEngine.setSerie(it) },
                modifier = Modifier.width(96.dp)
            )
        }
        BloccoPicker("Lavoro", cs.primary, nota = "0:00 = finisci tu la serie") {
            RuotaTempo(stato.durataSec, onChange = { TimerEngine.setDurata(it) })
        }
        BloccoPicker("Recupero", cs.tertiary, nota = "0:00 = avvio manuale") {
            RuotaTempo(stato.recuperoSec, onChange = { TimerEngine.setRecupero(it) })
        }

        Button(
            onClick = onAvvia,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text("AVVIA", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Card con etichetta colorata a sinistra e ruota a destra. */
@Composable
private fun BloccoPicker(etichetta: String, colore: Color, nota: String? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = bordoCard(),
        modifier = Modifier.fillMaxWidth().magnesite(RoundedCornerShape(22.dp))
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Sopratitolo(etichetta, colore = colore)
                if (nota != null) {
                    Text(nota, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** Barra che mostra in proporzione i blocchi di lavoro (carta) e recupero (vermiglio). */
@Composable
private fun SequenzaBlocchi(serie: Int, lavoro: Int, recupero: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(serie.coerceAtMost(30)) { i ->
            // Lavoro libero (0:00): blocco di larghezza indicativa, più tenue
            Box(
                Modifier.weight(if (lavoro > 0) lavoro.toFloat() else 30f).fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (lavoro > 0) cs.primaryContainer else cs.primaryContainer.copy(alpha = 0.45f))
            )
            if (i < serie - 1 && recupero > 0) {
                Box(
                    Modifier.weight(recupero.toFloat()).fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp)).background(cs.tertiaryContainer)
                )
            }
        }
    }
}

// ---------------- Timer in azione ----------------

@Composable
private fun TimerAttivo(stato: StatoTimer) {
    KeepScreenOn()
    // Niente BackHandler: "indietro" riduce l'app e il timer continua; si ferma solo con Stop.
    val flex = inFlexMode()
    val cs = MaterialTheme.colorScheme

    val recupero = stato.fase == FaseTimer.RECUPERO
    val totale = when (stato.fase) {
        FaseTimer.PREPARAZIONE -> SEC_PREPARAZIONE_TIMER
        FaseTimer.SERIE -> stato.durataSec          // 0 nel lavoro libero
        FaseTimer.RECUPERO -> stato.recuperoSec
        else -> 0
    }
    val progresso = when {
        stato.fase == FaseTimer.SERIE && stato.lavoroLibero -> 1f   // anello pieno: il tempo scorre in avanti
        totale > 0 -> (totale - stato.secondiRimanenti).toFloat() / totale
        else -> 0f
    }
    val progressoAnim by animateFloatAsState(
        targetValue = progresso.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "riempimento"
    )

    val riposo = coloriRiposo()
    val testo by animateColorAsState(if (recupero) riposo.testo else cs.onSurface, tween(700), label = "testo")

    CompositionLocalProvider(LocalContentColor provides testo) {
        Box(Modifier.fillMaxSize()) {   // trasparente: sotto c'è il fondo con la magnesite
            // Niente riempimento dello sfondo: l'avanzamento lo racconta già l'anello a pennello.
            if (flex) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { InfoTimer(stato, progressoAnim, recupero, compatto = true) }
                    HorizontalDivider(color = testo.copy(alpha = 0.15f))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { BottoniTimer(stato, recupero) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    InfoTimer(stato, progressoAnim, recupero, compatto = false)
                    Spacer(Modifier.weight(1f))
                    BottoniTimer(stato, recupero)
                }
            }
        }
    }
}

@Composable
private fun InfoTimer(stato: StatoTimer, progresso: Float, recupero: Boolean, compatto: Boolean) {
    val cs = MaterialTheme.colorScheme
    val testo = LocalContentColor.current
    val riposo = coloriRiposo()
    val accento = if (recupero) riposo.accentoTesto else cs.primary
    val traccia = if (recupero) riposo.traccia else cs.surfaceContainerHigh
    val libero = stato.fase == FaseTimer.SERIE && stato.lavoroLibero
    val preparazione = stato.fase == FaseTimer.PREPARAZIONE
    val anello = when {
        recupero -> riposo.accento
        libero -> cs.primaryContainer.copy(alpha = 0.35f)
        else -> cs.primaryContainer
    }

    val ultimi3 = stato.fase != FaseTimer.ATTESA && !libero && stato.secondiRimanenti in 1..3
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "scala"
    )
    val scala = if (ultimi3) pulse else 1f
    val diametro = if (compatto) 230.dp else 320.dp
    val etichetta = StileTitoloSezione.copy(fontSize = if (compatto) 16.sp else 20.sp, lineHeight = if (compatto) 26.sp else 32.sp)

    if (stato.fase == FaseTimer.ATTESA) {
        Sopratitolo("Serie ${stato.serieCorrente + 1} di ${stato.serieTotali}", colore = testo.copy(alpha = 0.75f))
        Text("pronto?", style = StileTitoloSchermata.copy(fontSize = 40.sp, lineHeight = 60.sp), color = cs.primary)
        Text("Tocca Avanti quando vuoi ripartire", style = MaterialTheme.typography.bodyLarge, color = testo.copy(alpha = 0.8f))
    } else {
        Box(Modifier.size(diametro), contentAlignment = Alignment.Center) {
            // Anello a pennello: traccia appena accennata, avanzamento a inchiostro pieno
            Canvas(Modifier.fillMaxSize()) {
                val spessore = (if (compatto) 14.dp else 20.dp).toPx()
                anelloPennello(traccia, -90f, 352f, spessore, seme = 3)
                anelloPennello(anello, -92f, 360f * progresso, spessore, seme = 7)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Crossfade(targetState = stato.fase, label = "etichetta") { fase ->
                    Text(
                        when (fase) {
                            FaseTimer.RECUPERO -> "riposo"
                            FaseTimer.PREPARAZIONE -> "pronti"
                            else -> "lavoro"
                        },
                        style = etichetta,
                        color = accento
                    )
                }
                val tempo = when {
                    preparazione -> "${stato.secondiRimanenti.coerceAtLeast(1)}"   // 3, 2, 1
                    libero -> formattaTimer(stato.secondiTrascorsi)              // conta in avanti
                    else -> formattaTimer(stato.secondiRimanenti)
                }
                // Le cifre stanno sempre dentro l'anello: si rimpiccioliscono per "10:00" e oltre.
                val massima = if (compatto) 84.dp else 118.dp
                val altezza = minOf(massima, diametro * 0.74f / larghezzaInchiostro(tempo).coerceAtLeast(2.4f))
                NumeriInchiostro(
                    tempo,
                    altezza = altezza,
                    // Negli ultimi 3 secondi le cifre restano crema e pulsano: il rosso si confondeva col vermiglio del riposo.
                    colore = testo,
                    modifier = Modifier.graphicsLayer { scaleX = scala; scaleY = scala }
                )
                Text(
                    "serie ${stato.serieCorrente + 1} di ${stato.serieTotali}",
                    style = etichetta.copy(fontSize = if (compatto) 11.sp else 13.sp, lineHeight = 22.sp),
                    color = testo.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Avanzamento serie: una pennellata per serie
    Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(stato.serieTotali.coerceAtMost(30)) { i ->
            val colore = when {
                i < stato.serieCorrente -> accento
                i == stato.serieCorrente -> accento.copy(alpha = 0.55f)
                else -> traccia
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) { trattoPennello(colore, seme = i + 1) }
        }
    }

    // Cosa viene dopo
    val lavoroTesto = if (stato.lavoroLibero) "lavoro libero" else formattaTimer(stato.durataSec)
    val dopo = when {
        preparazione -> "Serie 1 · $lavoroTesto"
        libero && stato.ultimaSerie -> "Premi Fine quando hai finito"
        libero -> "Premi Recupero quando hai finito"
        stato.fase == FaseTimer.RECUPERO -> "Serie ${stato.serieCorrente + 2} · $lavoroTesto"
        stato.serieCorrente >= stato.serieTotali - 1 -> "Fine"
        stato.recuperoSec > 0 -> "Riposo · ${formattaTimer(stato.recuperoSec)}"
        else -> "Pausa manuale"
    }
    if (stato.fase != FaseTimer.ATTESA) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (recupero) riposo.accento.copy(alpha = 0.12f) else cs.surfaceContainer,
            contentColor = testo,
            modifier = Modifier.fillMaxWidth().magnesite(RoundedCornerShape(18.dp))
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("poi", style = StileTitoloSezione.copy(fontSize = 15.sp, lineHeight = 24.sp), color = testo.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                Text(dopo, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BottoniTimer(stato: StatoTimer, recupero: Boolean) {
    val cs = MaterialTheme.colorScheme
    val riposo = coloriRiposo()
    val secondario = if (recupero) riposo.accento.copy(alpha = 0.16f) else cs.surfaceContainerHigh
    val principale = if (recupero) riposo.accento else cs.primaryContainer
    val suPrincipale = if (recupero) riposo.suAccento else cs.onPrimaryContainer
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BottoneSecondario(
            testo = "Stop",
            onClick = { TimerEngine.stop() },
            icona = Icons.Filled.Stop,
            contenitore = secondario,
            contenuto = LocalContentColor.current,
            modifier = Modifier.weight(1f).height(60.dp)
        )
        val libero = stato.fase == FaseTimer.SERIE && stato.lavoroLibero
        BottoneSecondario(
            testo = when {
                stato.fase == FaseTimer.ATTESA -> "Avanti"
                stato.fase == FaseTimer.PREPARAZIONE -> "Via!"
                libero && stato.ultimaSerie -> "Fine"
                libero -> "Recupero"
                else -> "Salta"
            },
            onClick = {
                when {
                    stato.fase == FaseTimer.ATTESA -> TimerEngine.avanti()
                    libero -> TimerEngine.fineSerie()
                    else -> TimerEngine.skip()
                }
            },
            icona = when {
                stato.fase == FaseTimer.ATTESA || stato.fase == FaseTimer.PREPARAZIONE -> Icons.Filled.PlayArrow
                libero -> Icons.Filled.Check
                else -> Icons.Filled.SkipNext
            },
            contenitore = principale,
            contenuto = suPrincipale,
            modifier = Modifier.weight(1f).height(60.dp)
        )
    }
}

private fun formattaTimer(secondi: Int): String = "%d:%02d".format(secondi / 60, secondi % 60)
