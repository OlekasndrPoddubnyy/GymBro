package com.my.gymbro.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.my.gymbro.data.Tema

@Composable
fun GymBroApp(
    temaCorrente: Tema,
    onTema: (Tema) -> Unit,
) {
    var sezione by rememberSaveable { mutableStateOf(Sezione.ESERCIZI) }
    var allenamentoSchedaId by rememberSaveable { mutableStateOf(-1L) }
    var allenamentoToken by rememberSaveable { mutableStateOf(0L) }
    // false = allenamento ridotto: continua in sottofondo e si naviga nel resto dell'app
    var allenamentoAperto by rememberSaveable { mutableStateOf(false) }

    val schedaInCorso = allenamentoSchedaId
    if (schedaInCorso >= 0 && allenamentoAperto) {
        AllenamentoScreen(
            schedaId = schedaInCorso,
            vmKey = "allenamento_$allenamentoToken",
            onRiduci = { allenamentoAperto = false },
            onFine = {
                allenamentoSchedaId = -1L
                allenamentoAperto = false
            }
        )
        return
    }

    val allenamentoInCorso by AllenamentoAttivo.info.collectAsStateWithLifecycle()
    val timer by TimerEngine.stato.collectAsStateWithLifecycle()

    val cs = MaterialTheme.colorScheme
    Scaffold(
        topBar = { IntestazioneSezione(sezione, temaCorrente, onTema) },
        bottomBar = {
          Column {
            // Barra "in corso": allenamento ridotto o timer attivo mentre sei su un'altra schermata
            val info = allenamentoInCorso
            if (schedaInCorso >= 0 && info != null) {
                BarraInCorso(
                    etichetta = "Allenamento · ${info.nomeScheda}",
                    testo = info.testo,
                    azione = "Torna",
                    onClick = { allenamentoAperto = true }
                )
            } else if (timer.fase != FaseTimer.CONFIG && sezione != Sezione.TIMER) {
                BarraInCorso(
                    etichetta = "Timer",
                    testo = descriviTimer(timer),
                    azione = "Apri",
                    onClick = { sezione = Sezione.TIMER }
                )
            }
            NavigationBar(
                containerColor = cs.surfaceContainerLow,
                tonalElevation = 0.dp
            ) {
                Sezione.entries.forEach { s ->
                    NavigationBarItem(
                        selected = sezione == s,
                        onClick = { sezione = s },
                        icon = { Icon(s.icon, contentDescription = null) },
                        label = { Text(s.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = cs.onPrimaryContainer,
                            selectedTextColor = cs.onSurface,
                            indicatorColor = cs.primaryContainer,
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant
                        )
                    )
                }
            }
          }
        },
        containerColor = cs.background,
        contentColor = cs.onSurface
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(targetState = sezione, animationSpec = tween(300), label = "sezione") { s ->
                when (s) {
                    Sezione.TIMER -> TimerScreen()
                    Sezione.ESERCIZI -> EserciziScreen()
                    Sezione.SCHEDE -> SchedeScreen(
                        onAvviaAllenamento = { id ->
                            if (allenamentoSchedaId >= 0 && allenamentoInCorso != null) {
                                // c'è già un allenamento in corso: si torna a quello
                                allenamentoAperto = true
                            } else {
                                allenamentoToken = System.nanoTime()
                                allenamentoSchedaId = id
                                allenamentoAperto = true
                            }
                        }
                    )
                    Sezione.REPORT -> ReportScreen()
                }
            }
        }
    }
}

/** Intestazione grande: sopratitolo maiuscolo + titolo condensato, menu tema a destra. */
@Composable
private fun IntestazioneSezione(sezione: Sezione, temaCorrente: Tema, onTema: (Tema) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Sopratitolo(sezione.sopratitolo)
            Text(sezione.label.uppercase(), style = MaterialTheme.typography.displaySmall)
        }
        MenuTema(temaCorrente, onTema)
    }
}

@Composable
private fun MenuTema(corrente: Tema, onTema: (Tema) -> Unit) {
    var aperto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aperto = true }) {
            Icon(Icons.Filled.BrightnessMedium, contentDescription = "Tema")
        }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            Tema.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.label) },
                    onClick = { onTema(t); aperto = false },
                    leadingIcon = {
                        if (t == corrente) Icon(Icons.Filled.Check, contentDescription = null)
                    }
                )
            }
        }
    }
}

/** Barra compatta sopra il menu: mostra cosa sta andando avanti in sottofondo e ci riporta. */
@Composable
private fun BarraInCorso(etichetta: String, testo: String, azione: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cs.primaryContainer))
            Column(Modifier.weight(1f)) {
                Sopratitolo(etichetta, colore = cs.primary)
                Text(testo, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Text(azione, style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
    }
}
