package com.my.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.gymbro.data.ChiaveBuddy
import com.my.gymbro.data.EsercizioSchedaCompleto
import com.my.gymbro.data.Obiettivo
import com.my.gymbro.data.Proposta
import com.my.gymbro.data.SchedaConEsercizi
import com.my.gymbro.data.allenabile

/** In cima al dettaglio della scheda: interruttore per tutta la scheda e impostazioni generali. */
@Composable
internal fun GymBuddyScheda(scheda: SchedaConEsercizi) {
    val vm: GymBuddyViewModel = viewModel(factory = GymBuddyViewModel.Factory)
    val vista by vm.vista.collectAsStateWithLifecycle()
    var impostazioni by remember { mutableStateOf(false) }

    val righe = scheda.esercizi.filter { it.progredisce }
    val attivi = righe.count { vista.progresso(ChiaveBuddy(scheda.scheda.id, it.esercizio.id)).attivo }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = bordoCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Sopratitolo("Gym Buddy")
                    Text(
                        if (attivi == 0) "Progressione spenta"
                        else "Attivo su $attivi di ${righe.size} esercizi",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = righe.isNotEmpty() && attivi == righe.size,
                    onCheckedChange = { vm.attiva(scheda.scheda.id, righe, it) },
                    enabled = vista.pronta && righe.isNotEmpty(),
                )
            }
            TextButton(onClick = { impostazioni = true }, enabled = vista.pronta) { Text("Impostazioni") }
        }
    }

    if (impostazioni) {
        DialogoImpostazioniBuddy(
            iniziali = vista.dati.impostazioni,
            onSalva = {
                vm.salvaImpostazioni(it)
                impostazioni = false
            },
            onAnnulla = { impostazioni = false },
        )
    }
}

/** Fuori dalla lista della scheda: lì dentro, scorrendo, il dialogo non verrebbe composto. */
@Composable
internal fun ErroreGymBuddy() {
    val vm: GymBuddyViewModel = viewModel(factory = GymBuddyViewModel.Factory)
    vm.errore?.let { errore ->
        AlertDialog(
            onDismissRequest = vm::chiudiErrore,
            title = { Text("Operazione non riuscita") },
            text = { Text(errore) },
            confirmButton = { TextButton(onClick = vm::chiudiErrore) { Text("OK") } },
        )
    }
}

/** Dentro la card di un esercizio: obiettivo, proposta, suggerimento di sostituzione e annulla. */
@Composable
internal fun GymBuddyEsercizio(scheda: SchedaConEsercizi, riga: EsercizioSchedaCompleto) {
    if (!riga.esercizio.gruppoMuscolare.allenabile) return
    val vm: GymBuddyViewModel = viewModel(factory = GymBuddyViewModel.Factory)
    val vista by vm.vista.collectAsStateWithLifecycle()
    var regole by remember { mutableStateOf(false) }
    var simili by remember { mutableStateOf(false) }
    if (!vista.pronta) return

    val chiave = ChiaveBuddy(scheda.scheda.id, riga.esercizio.id)
    val progresso = vista.progresso(chiave)
    val stato = progresso.stato
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (riga.progredisce) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (progresso.attivo && stato != null) {
                        Text(
                            "Obiettivo ${descriviObiettivo(stato.obiettivo)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.primary,
                        )
                        Text(
                            if (stato.calibrazione) "Prima sessione: trova il carico giusto"
                            else "Sessioni complete: ${stato.completeDiFila} di ${progresso.regole.conferme}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        Text("Gym Buddy", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = progresso.attivo,
                    onCheckedChange = { vm.attiva(scheda.scheda.id, listOf(riga), it) },
                )
            }
            if (progresso.attivo && stato != null) {
                stato.proposta?.let { proposta ->
                    RiquadroProposta(proposta) { accettata -> vm.rispondi(chiave, accettata) }
                }
                TextButton(onClick = { regole = true }) { Text("Regola obiettivo") }
            }
        }

        val ora = remember(vista) { System.currentTimeMillis() }
        val suggerire = vista.troppoALungo(chiave, ora)
        val alternative = remember(vista, scheda, suggerire) {
            if (suggerire) vista.sostitutiPer(scheda, riga.esercizio.id) else emptyList()
        }
        if (alternative.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text(
                        "Il tuo corpo potrebbe essersi abituato a questo esercizio",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { simili = true }) { Text("Vedi alternative") }
                    Row {
                        TextButton(onClick = { vm.rimanda(chiave) }) { Text("Rimanda") }
                        TextButton(onClick = { vm.ignora(chiave) }) { Text("Ignora") }
                    }
                }
            }
        } else {
            TextButton(onClick = { simili = true }) { Text("Esercizi simili") }
        }

        progresso.precedente?.let { prima ->
            val nome = vista.libreria.firstOrNull { it.id == prima.esercizioId }?.nome
            if (nome != null) {
                TextButton(onClick = { vm.annullaSostituzione(chiave) }) { Text("Annulla: torna a $nome") }
            }
        }
    }

    if (regole && stato != null) {
        DialogoRegoleBuddy(
            regoleIniziali = progresso.regole,
            obiettivoIniziale = stato.obiettivo,
            onSalva = { nuove, obiettivo ->
                vm.salvaRegole(chiave, nuove, obiettivo)
                regole = false
            },
            onAnnulla = { regole = false },
        )
    }
    if (simili) {
        EserciziSimiliSheet(scheda, riga, vista, vm, onChiudi = { simili = false })
    }
}

/** Nel riepilogo di fine allenamento: le proposte in attesa per gli esercizi appena fatti. */
@Composable
internal fun ProposteFineAllenamento(stato: StatoEsecuzione) {
    val schedaId by AllenamentoEngine.schedaInCorso.collectAsStateWithLifecycle()
    val id = schedaId ?: return
    val vm: GymBuddyViewModel = viewModel(factory = GymBuddyViewModel.Factory)
    val vista by vm.vista.collectAsStateWithLifecycle()

    val fatti = stato.log.map { it.esercizioId }.distinct()
    val proposte = fatti.mapNotNull { esercizioId ->
        val p = vista.progresso(ChiaveBuddy(id, esercizioId))
        val proposta = p.stato?.proposta
        if (p.attivo && proposta != null) esercizioId to proposta else null
    }
    if (proposte.isEmpty()) return

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitoloSezione("Gym Buddy propone")
        proposte.forEach { (esercizioId, proposta) ->
            Text(stato.nomiEseguiti[esercizioId] ?: "Esercizio", style = MaterialTheme.typography.titleMedium)
            RiquadroProposta(proposta) { accettata -> vm.rispondi(ChiaveBuddy(id, esercizioId), accettata) }
        }
    }
}

@Composable
private fun RiquadroProposta(proposta: Proposta, onRisposta: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.primaryContainer,
        contentColor = cs.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(descriviProposta(proposta), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onRisposta(true) }) {
                    Text(if (proposta.obiettivo == null) "Ho capito" else "Accetta", color = cs.onPrimaryContainer)
                }
                if (proposta.obiettivo != null) {
                    TextButton(onClick = { onRisposta(false) }) { Text("Rifiuta", color = cs.onPrimaryContainer) }
                }
            }
        }
    }
}

private fun descriviProposta(p: Proposta): String = when (p) {
    is Proposta.PiuRipetizioni -> "Passa a ${descriviObiettivo(p.obiettivo)}"
    is Proposta.PiuPeso -> "Aumenta il peso: ${descriviObiettivo(p.obiettivo)}"
    is Proposta.Scarico -> "Tre sessioni incomplete: scarica a ${descriviObiettivo(p.obiettivo)}"
    is Proposta.CorreggiCarico -> "Usa il carico provato: ${descriviObiettivo(p.obiettivo)}"
    is Proposta.MenoAiuto -> "Riduci l'aiuto della macchina: ${descriviObiettivo(p.obiettivo)}"
    Proposta.VariantePiuDifficile -> "Sei a fine range: aggiungi peso (segnalo quando fai la serie) o passa a una variante più difficile"
}

internal fun descriviObiettivo(o: Obiettivo): String =
    "${o.ripetizioni} rip" + o.caricoKg?.let { " × ${formattaKgRuota(it)} kg" }.orEmpty()
