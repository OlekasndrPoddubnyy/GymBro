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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.gymbro.ui.theme.StileTitoloSchermata
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.Tema
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GymBroApp(
    temaCorrente: Tema,
    onTema: (Tema) -> Unit,
) {
    var sezione by rememberSaveable { mutableStateOf(Sezione.ESERCIZI) }
    // false = allenamento ridotto: continua in sottofondo e si naviga nel resto dell'app
    var allenamentoAperto by rememberSaveable { mutableStateOf(false) }
    // Stato salvabile di ogni sezione: creato prima del ramo "allenamento a schermo intero",
    // così aprire e ridurre l'allenamento non fa perdere i form lasciati aperti.
    val statoSezioni = rememberSaveableStateHolder()
    // L'allenamento vive in AllenamentoEngine (di processo): sopravvive anche alla ricreazione dell'Activity.
    val schedaInCorso by AllenamentoEngine.schedaInCorso.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as GymBroApplication

    // Problemi del motore dell'allenamento (salvataggi, avvio, ripresa): visibili sia durante
    // l'allenamento a schermo intero sia nel resto dell'app, per questo stanno prima del ramo.
    val avviso by AllenamentoEngine.avviso.collectAsStateWithLifecycle()
    avviso?.let { a ->
        AlertDialog(
            onDismissRequest = AllenamentoEngine::chiudiAvviso,
            title = { Text(a.titolo) },
            text = { Text(a.testo) },
            confirmButton = { TextButton(onClick = AllenamentoEngine::chiudiAvviso) { Text("OK") } },
        )
    }

    if (schedaInCorso != null && allenamentoAperto) {
        AllenamentoScreen(
            onRiduci = { allenamentoAperto = false },
            onFine = {
                AllenamentoEngine.chiudi()
                allenamentoAperto = false
            }
        )
        return
    }

    val allenamentoInCorso by AllenamentoAttivo.info.collectAsStateWithLifecycle()
    var avvisoInCorso by remember { mutableStateOf(false) }
    val chiediNotifiche = rememberRichiestaNotifiche()
    val timer by TimerEngine.stato.collectAsStateWithLifecycle()

    val cs = MaterialTheme.colorScheme
    Scaffold(
        topBar = { IntestazioneSezione(sezione, temaCorrente, onTema) },
        bottomBar = {
          Column {
            // Barra "in corso": allenamento ridotto o timer attivo mentre sei su un'altra schermata
            val info = allenamentoInCorso
            if (schedaInCorso != null) {
                // Senza info l'allenamento è finito ma il riepilogo non è ancora stato chiuso.
                BarraInCorso(
                    etichetta = "Allenamento" + (info?.let { " · ${it.nomeScheda}" } ?: ""),
                    testo = info?.testo ?: "Completato · vedi il riepilogo",
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
        // Fondo con la magnesite sparsa dietro a tutte le sezioni (le sezioni sono trasparenti).
        modifier = Modifier.fillMaxSize().sfondoApp(),
        containerColor = Color.Transparent,
        contentColor = cs.onSurface
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(targetState = sezione, animationSpec = tween(300), label = "sezione") { s ->
              // Conserva lo stato "salvabile" di ogni sezione (campi dei form, scroll delle liste)
              // quando si passa a un'altra sezione e poi si torna indietro.
              statoSezioni.SaveableStateProvider(s.name) {
                when (s) {
                    Sezione.TIMER -> TimerScreen()
                    Sezione.ESERCIZI -> EserciziScreen()
                    Sezione.SCHEDE -> SchedeScreen(
                        onAvviaAllenamento = { id ->
                            if (AllenamentoEngine.inEsecuzione && schedaInCorso != id) {
                                // Un altro allenamento è già in corso: non lo si sostituisce in silenzio.
                                avvisoInCorso = true
                            } else {
                                // Stessa scheda già in corso: il motore ignora l'avvio e si torna a quella.
                                chiediNotifiche()
                                AllenamentoEngine.avvia(app, id)
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

    // Allenamento interrotto da Android (app chiusa per memoria o batteria): si chiede cosa farne.
    val interrotto = remember(schedaInCorso) { if (schedaInCorso == null) app.ripresa.leggi() else null }
    var ripresaRimandata by remember { mutableStateOf(false) }
    if (interrotto != null && !ripresaRimandata) {
        DialogoRipresa(
            salvato = interrotto,
            onRiprendi = {
                chiediNotifiche()
                AllenamentoEngine.riprendi(app, interrotto, terminaSubito = false)
                allenamentoAperto = true
            },
            onTermina = {
                AllenamentoEngine.riprendi(app, interrotto, terminaSubito = true)
                allenamentoAperto = true
            },
            onRimanda = { ripresaRimandata = true },
        )
    }
    if (avvisoInCorso) {
        val nome = allenamentoInCorso?.nomeScheda
        AlertDialog(
            onDismissRequest = { avvisoInCorso = false },
            title = { Text("Allenamento già in corso") },
            text = {
                Text(
                    "Stai già facendo " + (nome?.let { "\"$it\"" } ?: "un allenamento") +
                        ". Riprendilo, oppure terminalo prima di iniziarne un altro."
                )
            },
            confirmButton = {
                TextButton(onClick = { avvisoInCorso = false; allenamentoAperto = true }) { Text("Riprendi") }
            },
            dismissButton = {
                TextButton(onClick = { avvisoInCorso = false }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun DialogoRipresa(
    salvato: AllenamentoSalvato,
    onRiprendi: () -> Unit,
    onTermina: () -> Unit,
    onRimanda: () -> Unit,
) {
    val fatte = salvato.completati.size
    val quando = salvato.aggiornatoIl.takeIf { it > 0 }?.let { ms ->
        val momento = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val ora = DateTimeFormatter.ofPattern("HH:mm").format(momento)
        if (momento.toLocalDate() == LocalDate.now()) " alle $ora"
        else " il ${DateTimeFormatter.ofPattern("d/M").format(momento)} alle $ora"
    }.orEmpty()
    AlertDialog(
        onDismissRequest = onRimanda,
        title = { Text("Allenamento interrotto") },
        text = {
            Text(
                "\"${salvato.nomeScheda}\" si è fermato$quando: Android ha chiuso l'app mentre era in corso. " +
                    (if (fatte == 0) "Non avevi ancora registrato nessuna serie."
                    else if (fatte == 1) "Avevi registrato 1 serie, che resta salvata."
                    else "Avevi registrato $fatte serie, che restano salvate.") +
                    "\n\nRiprendi da dove eri rimasto, oppure terminalo qui" +
                    (if (fatte == 0) " (non verrà salvato)." else " e guarda il riepilogo.")
            )
        },
        confirmButton = { TextButton(onClick = onRiprendi) { Text("Riprendi") } },
        dismissButton = { TextButton(onClick = onTermina) { Text("Termina") } },
    )
}

/** Intestazione grande: sopratitolo maiuscolo + titolo condensato, menu (tema e dati) a destra. */
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
            Text(sezione.label, style = StileTitoloSchermata.copy(fontSize = 32.sp, lineHeight = 48.sp))
        }
        MenuApp(temaCorrente, onTema)
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
