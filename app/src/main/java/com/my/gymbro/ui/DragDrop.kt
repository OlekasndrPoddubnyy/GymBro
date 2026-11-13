package com.my.gymbro.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Stato per il riordino drag & drop di una LazyColumn, senza librerie esterne.
 *
 * [onMove] riceve le key (stabili) dell'elemento trascinato e di quello bersaglio e restituisce
 * true se lo spostamento è avvenuto. Gli elementi non trascinabili (header senza key, o key non
 * gestite) vengono ignorati restituendo false.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (fromKey: Any, toKey: Any) -> Boolean,
): DragDropState {
    val onMoveAggiornato = rememberUpdatedState(onMove)
    return remember(lazyListState) {
        DragDropState(lazyListState) { a, b -> onMoveAggiornato.value(a, b) }
    }
}

class DragDropState internal constructor(
    private val lazyListState: LazyListState,
    private val onMove: (Any, Any) -> Boolean,
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    private var initialOffset = 0
    private var delta by mutableFloatStateOf(0f)

    /** Spostamento verticale (px) da applicare alla card trascinata. */
    val draggingItemOffset: Float
        get() {
            val key = draggingItemKey ?: return 0f
            val corrente = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key } ?: return 0f
            return initialOffset + delta - corrente.offset
        }

    fun onDragStart(key: Any) {
        initialOffset = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }?.offset ?: 0
        draggingItemKey = key
        delta = 0f
    }

    fun onDrag(deltaY: Float) {
        delta += deltaY
        val key = draggingItemKey ?: return
        val info = lazyListState.layoutInfo
        val trascinato = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val centro = initialOffset + delta + trascinato.size / 2f
        val bersaglio = info.visibleItemsInfo.firstOrNull { item ->
            item.key != key && centro.toInt() in item.offset..(item.offset + item.size)
        } ?: return
        // Le key non gestite (header) non corrispondono a nessun uid: onMove le ignora.
        onMove(key, bersaglio.key)
    }

    fun onDragEnd() {
        draggingItemKey = null
        delta = 0f
        initialOffset = 0
    }
}
