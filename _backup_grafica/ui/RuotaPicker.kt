package com.my.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Selettore a ruota in stile app Orologio iPhone: scorri verticalmente, l'elemento al centro
 * (evidenziato) è quello selezionato; aggancio (snap) automatico e dissolvenza ai lati.
 */
@Composable
fun <T> RuotaPicker(
    valori: List<T>,
    selezionato: T,
    onSelezione: (T) -> Unit,
    modifier: Modifier = Modifier,
    etichetta: (T) -> String = { it.toString() },
) {
    val altezzaItem = 42.dp
    val visibili = 3
    val altezzaItemPx = with(LocalDensity.current) { altezzaItem.toPx() }

    val indiceIniziale = valori.indexOf(selezionato).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = indiceIniziale)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    val indiceCentrale by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            listState.firstVisibleItemIndex + if (offset > altezzaItemPx / 2) 1 else 0
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            valori.getOrNull(indiceCentrale)?.let { if (it != selezionato) onSelezione(it) }
        }
    }

    Box(modifier.height(altezzaItem * visibili), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(altezzaItem)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = altezzaItem * (visibili / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(valori) { i, v ->
                val distanza = abs(i - indiceCentrale)
                val alpha = when (distanza) {
                    0 -> 1f
                    1 -> 0.45f
                    else -> 0.2f
                }
                Box(Modifier.height(altezzaItem).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        etichetta(v),
                        style = if (distanza == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Coppia di ruote minuti : secondi che insieme formano [secondi]. */
@Composable
fun RuotaTempo(secondi: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val minuti = secondi / 60
    val sec = secondi % 60
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RuotaPicker(
            valori = (0..30).toList(),
            selezionato = minuti,
            onSelezione = { onChange(it * 60 + sec) },
            modifier = Modifier.width(60.dp),
            etichetta = { it.toString() }
        )
        Text(":", style = MaterialTheme.typography.titleLarge)
        RuotaPicker(
            valori = (0..59).toList(),
            selezionato = sec,
            onSelezione = { onChange(minuti * 60 + it) },
            modifier = Modifier.width(60.dp),
            etichetta = { "%02d".format(it) }
        )
    }
}

private val PESI: List<Float> = (0..600).map { it * 0.5f } // 0..300 kg, passo 0.5

/** Ruota per il peso in kg (passo 0,5). */
@Composable
fun RuotaPeso(peso: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val corrente = ((peso / 0.5f).roundToInt() * 0.5f).coerceIn(0f, 300f)
    RuotaPicker(
        valori = PESI,
        selezionato = corrente,
        onSelezione = onChange,
        modifier = modifier.width(96.dp),
        etichetta = { formattaKgRuota(it) }
    )
}

fun formattaKgRuota(kg: Float): String = if (kg % 1f == 0f) kg.toInt().toString() else kg.toString()
