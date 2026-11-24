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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.gymbro.TimerService
import com.my.gymbro.ui.theme.Inchiostro
import kotlin.math.PI
import kotlin.math.sin

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
        modifier = Modifier.fillMaxWidth()
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

/** Barra che mostra in proporzione i blocchi di lavoro (lime) e recupero (corallo). */
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

    // In recupero tutto lo schermo diventa corallo, con testo inchiostro.
    val riposo = coloriRiposo()
    val sfondo by animateColorAsState(if (recupero) riposo.sfondo else cs.background, tween(700), label = "sfondo")
    val testo by animateColorAsState(if (recupero) riposo.testo else cs.onSurface, tween(700), label = "testo")

    CompositionLocalProvider(LocalContentColor provides testo) {
        Box(Modifier.fillMaxSize().background(sfondo)) {
            // Riempimento dello sfondo dal basso, in sincronia col timer (pieno = fase finita)
            val libero = stato.fase == FaseTimer.SERIE && stato.lavoroLibero
            RiempimentoSfondo(
                livello = if (libero || stato.fase == FaseTimer.ATTESA) 0f else progressoAnim,
                colore = if (recupero) riposo.accento else cs.primaryContainer,
                modifier = Modifier.fillMaxSize()
            )
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
    val diametro = if (compatto) 200.dp else 290.dp

    if (stato.fase == FaseTimer.ATTESA) {
        Sopratitolo("Serie ${stato.serieCorrente + 1} di ${stato.serieTotali}", colore = testo.copy(alpha = 0.75f))
        Text("PRONTO?", style = MaterialTheme.typography.displayMedium, color = cs.primary)
        Text("Tocca Avanti quando vuoi ripartire", style = MaterialTheme.typography.bodyLarge, color = testo.copy(alpha = 0.8f))
    } else {
        Box(Modifier.size(diametro), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val spessore = 16.dp.toPx()
                val inset = spessore / 2
                val area = Size(size.width - spessore, size.height - spessore)
                drawArc(traccia, 0f, 360f, false, Offset(inset, inset), area, style = Stroke(spessore))
                drawArc(anello, -90f, 360f * progresso, false, Offset(inset, inset), area, style = Stroke(spessore, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Crossfade(targetState = stato.fase, label = "etichetta") { fase ->
                    Text(
                        when (fase) {
                            FaseTimer.RECUPERO -> "RIPOSO"
                            FaseTimer.PREPARAZIONE -> "PRONTI"
                            else -> "LAVORO"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
                        color = accento
                    )
                }
                NumeriInchiostro(
                    when {
                        preparazione -> "${stato.secondiRimanenti.coerceAtLeast(1)}"   // 3, 2, 1
                        libero -> formattaTimer(stato.secondiTrascorsi)              // conta in avanti
                        else -> formattaTimer(stato.secondiRimanenti)
                    },
                    altezza = if (compatto) 80.dp else 116.dp,
                    colore = if (ultimi3 && !recupero && !preparazione) cs.error else testo,
                    modifier = Modifier.padding(vertical = 4.dp).graphicsLayer { scaleX = scala; scaleY = scala }
                )
                Text(
                    "Serie ${stato.serieCorrente + 1} di ${stato.serieTotali}",
                    style = MaterialTheme.typography.titleSmall,
                    color = testo.copy(alpha = 0.8f)
                )
            }
        }
    }

    // Avanzamento serie: un trattino per serie
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(stato.serieTotali.coerceAtMost(30)) { i ->
            val colore = when {
                i < stato.serieCorrente -> accento
                i == stato.serieCorrente -> accento.copy(alpha = 0.5f)
                else -> traccia
            }
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(colore))
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Sopratitolo("Poi", Modifier.weight(1f), colore = testo.copy(alpha = 0.7f))
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

// ---------------- Riempimento di sottofondo ----------------

/**
 * Riempie lo schermo dal basso fino a [livello] (0..1). Il bordo superiore è un'onda bassa che
 * scorre lentamente, quasi impercettibile: dà vita senza distrarre.
 */
@Composable
private fun RiempimentoSfondo(livello: Float, colore: Color, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "onda")
    val fase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "faseOnda"
    )
    val coloreAnim by animateColorAsState(colore, tween(600), label = "coloreRiempimento")
    Canvas(modifier) {
        if (livello <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        val ampiezza = 5.dp.toPx() * (1f - livello).coerceIn(0.3f, 1f)   // l'onda si calma verso la fine
        val y0 = h * (1f - livello.coerceIn(0f, 1f))
        val passi = 48
        val yOnda = { x: Float -> y0 + ampiezza * sin(fase + x / w * 2f * PI.toFloat()) }
        val riempimento = Path().apply {
            moveTo(0f, h)
            for (i in 0..passi) {
                val x = w * i / passi
                lineTo(x, yOnda(x))
            }
            lineTo(w, h)
            close()
        }
        drawPath(
            riempimento,
            brush = Brush.verticalGradient(
                listOf(coloreAnim.copy(alpha = 0.22f), coloreAnim.copy(alpha = 0.08f)),
                startY = y0,
                endY = h
            )
        )
        // filo luminoso sul bordo superiore
        val bordo = Path().apply {
            for (i in 0..passi) {
                val x = w * i / passi
                if (i == 0) moveTo(x, yOnda(x)) else lineTo(x, yOnda(x))
            }
        }
        drawPath(bordo, coloreAnim.copy(alpha = 0.45f), style = Stroke(width = 2.dp.toPx()))
    }
}

private fun formattaTimer(secondi: Int): String = "%d:%02d".format(secondi / 60, secondi % 60)
