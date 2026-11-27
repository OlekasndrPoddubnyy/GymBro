package com.my.gymbro.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.ui.theme.Inchiostro
import kotlinx.coroutines.delay

/** Lato massimo (px) delle miniature: una foto 850×567 scende a 425×283, nitida fino a 72 dp. */
private const val DIM_MINIATURA = 450

/** Le immagini predefinite hanno due fasi: posizione iniziale (0.jpg) e finale (1.jpg). */
private fun fasiImmagine(path: String?): List<String> = when {
    path.isNullOrBlank() -> emptyList()
    path.startsWith("asset://") && path.endsWith("/0.jpg") -> listOf(path, path.removeSuffix("0.jpg") + "1.jpg")
    else -> listOf(path)
}

/**
 * Immagine dell'esercizio. Per gli esercizi predefiniti alterna in automatico posizione iniziale e
 * finale (effetto "animazione" del movimento), con un'etichetta INIZIO/FINE.
 * Con [animata] = false mostra solo la posizione iniziale: è il caso delle miniature, che in un
 * elenco terrebbero occupata la CPU anche a schermo fermo.
 * Se non c'è immagine mostra [segnaposto] (o niente).
 */
@Composable
fun ImmagineEsercizioAnimata(
    path: String?,
    modifier: Modifier = Modifier,
    rapportoAspetto: Float = 16f / 9f,
    mostraEtichetta: Boolean = true,
    intervalloMs: Long = 1200,
    animata: Boolean = true,
    maxDim: Int = 900,
    segnaposto: (@Composable () -> Unit)? = null,
) {
    val fasi = remember(path, animata) { fasiImmagine(path).let { if (animata) it else it.take(1) } }
    val forma = RoundedCornerShape(20.dp)
    if (fasi.isEmpty()) {
        if (segnaposto != null) {
            Box(
                modifier.fillMaxWidth().aspectRatio(rapportoAspetto).clip(forma)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) { segnaposto() }
        }
        return
    }

    var fase by remember(path) { mutableIntStateOf(0) }
    if (fasi.size > 1) {
        LaunchedEffect(path) {
            while (true) {
                delay(intervalloMs)
                fase = (fase + 1) % fasi.size
            }
        }
    }

    Box(modifier.fillMaxWidth().aspectRatio(rapportoAspetto).clip(forma).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Crossfade(targetState = fase, animationSpec = tween(350), label = "faseImmagine") { i ->
            val bmp = rememberImmagine(fasi.getOrNull(i), maxDim = maxDim)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (mostraEtichetta && fasi.size > 1) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Inchiostro.copy(alpha = 0.72f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
            ) {
                Text(
                    if (fase == 0) "INIZIO" else "FINE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Miniatura quadrata ferma: al tocco apre la foto grande animata tra inizio e fine. */
@Composable
fun MiniaturaEsercizio(
    nome: String,
    path: String?,
    modifier: Modifier = Modifier,
    descrizione: String? = null,
    nota: String? = null,
    gruppo: GruppoMuscolare? = null,
    segnaposto: (@Composable () -> Unit)? = null,
    chiave: String? = null,
) {
    var aperta by remember { mutableStateOf(false) }
    ImmagineEsercizioAnimata(
        path = path,
        rapportoAspetto = 1f,
        mostraEtichetta = false,
        animata = false,
        maxDim = DIM_MINIATURA,
        segnaposto = segnaposto,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable { aperta = true }
    )
    if (aperta) {
        DialogoEsercizio(nome, path, descrizione, nota, gruppo, onChiudi = { aperta = false }, chiave = chiave)
    }
}

/** Finestra con l'immagine grande animata, la descrizione e le note di tecnica. */
@Composable
fun DialogoEsercizio(
    nome: String,
    path: String?,
    descrizione: String?,
    nota: String?,
    gruppo: GruppoMuscolare?,
    onChiudi: () -> Unit,
    testoConferma: String? = null,
    onConferma: (() -> Unit)? = null,
    /** Nome di catalogo (esercizi predefiniti): i muscoli si trovano anche se è stato rinominato. */
    chiave: String? = null,
) {
    val muscoli = remember(nome, chiave, gruppo) { muscoliCoinvolti(chiave ?: nome, gruppo) }
    AlertDialog(
        onDismissRequest = onChiudi,
        confirmButton = {
            if (onConferma != null) {
                BottoneSecondario(
                    testo = testoConferma ?: "Conferma",
                    onClick = onConferma,
                    contenitore = MaterialTheme.colorScheme.primaryContainer,
                    contenuto = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                TextButton(onClick = onChiudi) { Text("Chiudi") }
            }
        },
        dismissButton = {
            if (onConferma != null) TextButton(onClick = onChiudi) { Text("Annulla") }
        },
        title = { TitoloSezione(nome) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ImmagineEsercizioAnimata(path, rapportoAspetto = 3f / 2f)
                if (muscoli != null) {
                    val stretching = gruppo == GruppoMuscolare.STRETCHING
                    Sopratitolo(if (stretching) "Muscoli allungati" else "Muscoli coinvolti", colore = MaterialTheme.colorScheme.primary)
                    MappaMuscoli(muscoli, allungamento = stretching)
                }
                descrizione?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                nota?.takeIf { it.isNotBlank() }?.let {
                    Row {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Sopratitolo("Tecnica", colore = MaterialTheme.colorScheme.primary)
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    )
}

/**
 * Miniatura per gli elenchi: foto ferma (posizione iniziale) o sigla del gruppo.
 * Con [cliccabile] = true il tocco apre l'anteprima grande; false quando è la riga a gestire il tocco.
 */
@Composable
fun MiniaturaLibreria(es: Esercizio, modifier: Modifier = Modifier.size(56.dp), cliccabile: Boolean = true) {
    val segnaposto: @Composable () -> Unit = {
        Text(
            es.gruppoMuscolare.label.take(3).uppercase(),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (cliccabile) {
        MiniaturaEsercizio(
            nome = es.nome,
            path = es.immaginePath,
            descrizione = es.descrizione,
            gruppo = es.gruppoMuscolare,
            modifier = modifier,
            segnaposto = segnaposto,
            chiave = es.chiaveSeed,
        )
    } else {
        ImmagineEsercizioAnimata(
            path = es.immaginePath,
            rapportoAspetto = 1f,
            mostraEtichetta = false,
            animata = false,
            maxDim = DIM_MINIATURA,
            segnaposto = segnaposto,
            modifier = modifier.clip(RoundedCornerShape(14.dp))
        )
    }
}

/** Anteprima di un esercizio scelto da una ricerca, con il bottone per confermare la scelta. */
@Composable
fun AnteprimaScelta(es: Esercizio, testoConferma: String, onConferma: () -> Unit, onAnnulla: () -> Unit) {
    DialogoEsercizio(
        nome = es.nome,
        path = es.immaginePath,
        descrizione = es.descrizione,
        nota = null,
        gruppo = es.gruppoMuscolare,
        onChiudi = onAnnulla,
        testoConferma = testoConferma,
        onConferma = onConferma,
        chiave = es.chiaveSeed,
    )
}
