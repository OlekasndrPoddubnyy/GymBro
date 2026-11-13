package com.my.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Sfondo a gradiente verticale dell'app: neutro e leggero, così le card (più chiare/elevate)
 * staccano nettamente per leggibilità, in chiaro e in scuro.
 */
@Composable
fun Modifier.sfondoApp(): Modifier {
    val cs = MaterialTheme.colorScheme
    return background(
        Brush.verticalGradient(listOf(cs.surface, cs.surfaceContainer, cs.surfaceContainerHigh))
    )
}

/** Colore card che sta nettamente sopra lo sfondo (massimo livello di contenitore). */
@Composable
fun coloriCard(): CardColors =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)

/** Bordo sottile per separare nettamente le card dallo sfondo (utile soprattutto in tema chiaro). */
@Composable
fun bordoCard(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
