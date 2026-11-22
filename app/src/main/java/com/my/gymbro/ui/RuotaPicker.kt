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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

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

    val indiceIniziale = remember { valori.indexOf(selezionato).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = indiceIniziale)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    val indiceCentrale by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            listState.firstVisibleItemIndex + if (offset > altezzaItemPx / 2) 1 else 0
        }
    }

    val valoriAttuali by rememberUpdatedState(valori)
    val selezionatoAttuale by rememberUpdatedState(selezionato)
    val onSelezioneAttuale by rememberUpdatedState(onSelezione)
    val ultimoScelto = remember { mutableStateOf<T?>(null) }

    // Ruota → valore: solo quando il centro cambia (mai alla comparsa) e già durante lo scorrimento,
    // così toccare Completa mentre la ruota gira salva il numero che si vede.
    LaunchedEffect(listState) {
        snapshotFlow { indiceCentrale }.drop(1).collect { i ->
            val valore = valoriAttuali.getOrNull(i) ?: return@collect
            ultimoScelto.value = valore
            if (valore != selezionatoAttuale) onSelezioneAttuale(valore)
        }
    }

    // Valore → ruota: se il valore cambia da fuori ci si riallinea, senza interrompere chi sta scorrendo.
    LaunchedEffect(selezionato) {
        if (selezionato == ultimoScelto.value || listState.isScrollInProgress) return@LaunchedEffect
        val indice = valori.indexOf(selezionato)
        if (indice >= 0) listState.scrollToItem(indice)
    }

    Box(modifier.height(altezzaItem * visibili), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(altezzaItem)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                        style = if (distanza == 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
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
            valori = remember { (0..30).toList().conValore(minuti) },
            selezionato = minuti,
            onSelezione = { onChange(it * 60 + sec) },
            modifier = Modifier.width(60.dp),
            etichetta = { it.toString() }
        )
        Text(":", style = MaterialTheme.typography.headlineMedium)
        RuotaPicker(
            valori = (0..59).toList(),
            selezionato = sec,
            onSelezione = { onChange(minuti * 60 + it) },
            modifier = Modifier.width(60.dp),
            etichetta = { "%02d".format(it) }
        )
    }
}

private val PESI: List<Float> = (0..1000).map { it * 0.5f } // 0..500 kg, passo 0.5

/** Ruota per il peso in kg (passo 0,5). Un peso fuori passo o fuori scala viene mostrato com'è. */
@Composable
fun RuotaPeso(peso: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    RuotaPicker(
        valori = remember { PESI.conValore(peso) },
        selezionato = peso,
        onSelezione = onChange,
        modifier = modifier.width(96.dp),
        etichetta = { formattaKgRuota(it) }
    )
}

fun formattaKgRuota(kg: Float): String = if (kg % 1f == 0f) kg.toInt().toString() else kg.toString()
