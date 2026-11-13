package com.my.gymbro.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.my.gymbro.data.Tema

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymBroApp(
    temaCorrente: Tema,
    onTema: (Tema) -> Unit,
) {
    var sezione by rememberSaveable { mutableStateOf(Sezione.ESERCIZI) }
    var allenamentoSchedaId by rememberSaveable { mutableStateOf(-1L) }
    var allenamentoToken by rememberSaveable { mutableStateOf(0L) }

    val schedaInCorso = allenamentoSchedaId
    if (schedaInCorso >= 0) {
        AllenamentoScreen(
            schedaId = schedaInCorso,
            vmKey = "allenamento_$allenamentoToken",
            onFine = { allenamentoSchedaId = -1L }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sezione.label) },
                actions = { MenuTema(temaCorrente, onTema) }
            )
        },
        bottomBar = {
            NavigationBar {
                Sezione.entries.forEach { s ->
                    NavigationBarItem(
                        selected = sezione == s,
                        onClick = { sezione = s },
                        icon = { Icon(s.icon, contentDescription = null) },
                        label = { Text(s.label) }
                    )
                }
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { padding ->
        Box(Modifier.fillMaxSize().sfondoApp().padding(padding)) {
            Crossfade(targetState = sezione, animationSpec = tween(300), label = "sezione") { s ->
                when (s) {
                    Sezione.TIMER -> TimerScreen()
                    Sezione.ESERCIZI -> EserciziScreen()
                    Sezione.SCHEDE -> SchedeScreen(
                        onAvviaAllenamento = { id ->
                            allenamentoToken = System.nanoTime()
                            allenamentoSchedaId = id
                        }
                    )
                    Sezione.REPORT -> ReportScreen()
                }
            }
        }
    }
}

@Composable
private fun MenuTema(corrente: Tema, onTema: (Tema) -> Unit) {
    var aperto by remember { mutableStateOf(false) }
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
