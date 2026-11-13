package com.my.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Sfondo pieno dell'app (carbone in scuro, carta calda in chiaro): le card staccano per tono. */
@Composable
fun Modifier.sfondoApp(): Modifier = background(MaterialTheme.colorScheme.background)

/** Colore card: un gradino sopra lo sfondo. */
@Composable
fun coloriCard(): CardColors =
    CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

/** Bordo sottilissimo: quasi invisibile in scuro, separa le card bianche in chiaro. */
@Composable
fun bordoCard(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** Card piatte: la gerarchia la danno colore e spaziatura, non le ombre. */
@Composable
fun elevazioneCard(): CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
