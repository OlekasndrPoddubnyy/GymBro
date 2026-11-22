package com.my.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.ImpostazioniBuddy
import com.my.gymbro.data.Obiettivo
import com.my.gymbro.data.RegoleBuddy
import kotlin.math.roundToInt

/** Obiettivo e regole di un esercizio. Qui è l'utente a cambiare l'obiettivo, quindi vale subito. */
@Composable
internal fun DialogoRegoleBuddy(
    regoleIniziali: RegoleBuddy,
    obiettivoIniziale: Obiettivo,
    onSalva: (RegoleBuddy, Obiettivo) -> Unit,
    onAnnulla: () -> Unit,
) {
    var r by remember { mutableStateOf(regoleIniziali) }
    var ripetizioni by remember { mutableStateOf(obiettivoIniziale.ripetizioni) }
    var carico by remember { mutableStateOf(obiettivoIniziale.caricoKg ?: 0f) }

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Gym Buddy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RigaNumero("Ripetizioni obiettivo", ripetizioni, r.ripMin..r.ripMax) { ripetizioni = it }
                Text("Carico obiettivo (kg)", style = MaterialTheme.typography.bodyMedium)
                RuotaPeso(carico, { carico = it }, Modifier.align(Alignment.CenterHorizontally))
                RigaNumero("Minimo del range", r.ripMin, 1..r.ripMax) {
                    r = r.copy(ripMin = it)
                    ripetizioni = ripetizioni.coerceIn(r.ripMin, r.ripMax)
                }
                RigaNumero("Massimo del range", r.ripMax, r.ripMin..100) {
                    r = r.copy(ripMax = it)
                    ripetizioni = ripetizioni.coerceIn(r.ripMin, r.ripMax)
                }
                RigaNumero("Passo ripetizioni", r.passoRip, 1..10) { r = r.copy(passoRip = it) }
                RigaKg("Incremento peso", r.incrementoKg ?: 0f) {
                    r = r.copy(incrementoKg = it.takeIf { kg -> kg > 0f })
                }
                RigaNumero("Conferme richieste", r.conferme, 1..10) { r = r.copy(conferme = it) }
                RigaInterruttore("Progressione rapida", r.progressioneRapida) {
                    r = r.copy(progressioneRapida = it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSalva(r, Obiettivo(ripetizioni, carico.takeIf { it > 0f })) }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
    )
}

/** Impostazioni generali di Gym Buddy e dei suggerimenti. */
@Composable
internal fun DialogoImpostazioniBuddy(
    iniziali: ImpostazioniBuddy,
    onSalva: (ImpostazioniBuddy) -> Unit,
    onAnnulla: () -> Unit,
) {
    var i by remember { mutableStateOf(iniziali) }
    val s = i.sostituzione

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Impostazioni Gym Buddy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Sopratitolo("Esercizi simili")
                RigaNumero("Settimane in scheda", s.settimaneMax, 1..52) {
                    i = i.copy(sostituzione = s.copy(settimaneMax = it))
                }
                RigaNumero("Sessioni di stallo", s.sessioniStallo, 1..20) {
                    i = i.copy(sostituzione = s.copy(sessioniStallo = it))
                }
                RigaNumero("Somiglianza minima %", (s.sogliaMinima * 100).roundToInt(), 5..100, passo = 5) {
                    i = i.copy(sostituzione = s.copy(sogliaMinima = it / 100.0))
                }
                RigaNumero("Giorni di rimando", i.giorniRimanda, 1..60) { i = i.copy(giorniRimanda = it) }

                Sopratitolo("Attrezzi disponibili")
                Attrezzatura.entries.forEach { attrezzo ->
                    RigaInterruttore(attrezzo.label, attrezzo in i.attrezziDisponibili) { attivo ->
                        val nuovi = if (attivo) i.attrezziDisponibili + attrezzo else i.attrezziDisponibili - attrezzo
                        i = i.copy(attrezziDisponibili = nuovi)
                    }
                }

                Sopratitolo("Per i nuovi esercizi")
                RigaNumero("Minimo del range", i.ripMin, 1..i.ripMax) { i = i.copy(ripMin = it) }
                RigaNumero("Massimo del range", i.ripMax, i.ripMin..100) { i = i.copy(ripMax = it) }
                RigaNumero("Passo ripetizioni", i.passoRip, 1..10) { i = i.copy(passoRip = it) }
                RigaNumero("Conferme richieste", i.conferme, 1..10) { i = i.copy(conferme = it) }
                RigaInterruttore("Progressione rapida", i.progressioneRapida) { i = i.copy(progressioneRapida = it) }
                RigaKg("Bilanciere, gambe", i.kgBilanciereGambe) { i = i.copy(kgBilanciereGambe = it) }
                RigaKg("Bilanciere, sopra", i.kgBilanciereAlto) { i = i.copy(kgBilanciereAlto = it) }
                RigaKg("Manubri", i.kgManubri) { i = i.copy(kgManubri = it) }
                RigaKg("Macchine e cavi", i.kgMacchine) { i = i.copy(kgMacchine = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onSalva(i) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
    )
}

@Composable
internal fun RigaNumero(
    etichetta: String,
    valore: Int,
    intervallo: IntRange,
    passo: Int = 1,
    testo: String = valore.toString(),
    onCambia: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etichetta, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = { onCambia((valore - passo).coerceAtLeast(intervallo.first)) },
            enabled = valore > intervallo.first,
        ) { Text("−") }
        Text(testo, Modifier.width(40.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
        TextButton(
            onClick = { onCambia((valore + passo).coerceAtMost(intervallo.last)) },
            enabled = valore < intervallo.last,
        ) { Text("+") }
    }
}

/** Kg a passi di mezzo chilo. */
@Composable
private fun RigaKg(etichetta: String, kg: Float, onCambia: (Float) -> Unit) {
    RigaNumero(
        etichetta = "$etichetta (kg)",
        valore = (kg * 2).roundToInt(),
        intervallo = 0..50,
        testo = formattaKgRuota(kg),
        onCambia = { onCambia(it / 2f) },
    )
}

@Composable
private fun RigaInterruttore(etichetta: String, attivo: Boolean, onCambia: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(etichetta, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = attivo, onCheckedChange = onCambia)
    }
}
