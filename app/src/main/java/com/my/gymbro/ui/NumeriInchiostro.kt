package com.my.gymbro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.my.gymbro.R

/*
 * Numeri del timer a pennello e inchiostro.
 * Cifre: "Ink number collection", designed by Freepik (freepik.com), convertite dal file .ai.
 * I due punti sono disegnati a parte nello stesso stile.
 */

private val cifre = intArrayOf(
    R.drawable.cifra_inchiostro_0, R.drawable.cifra_inchiostro_1, R.drawable.cifra_inchiostro_2,
    R.drawable.cifra_inchiostro_3, R.drawable.cifra_inchiostro_4, R.drawable.cifra_inchiostro_5,
    R.drawable.cifra_inchiostro_6, R.drawable.cifra_inchiostro_7, R.drawable.cifra_inchiostro_8,
    R.drawable.cifra_inchiostro_9,
)

/** Proporzioni della cella comune di ogni cifra (viewport dei drawable). */
private const val PROPORZIONE_CIFRA = 56.28f / 69.14f
private const val PROPORZIONE_DUE_PUNTI = 18f / 69.14f

/**
 * Mostra [testo] (es. "1:30") con le cifre a inchiostro, colorate con [colore].
 * Ogni cifra occupa una casella di larghezza fissa, così il numero non "balla" mentre scorre.
 */
@Composable
fun NumeriInchiostro(testo: String, altezza: Dp, colore: Color, modifier: Modifier = Modifier) {
    val filtro = ColorFilter.tint(colore)
    Row(
        modifier.clearAndSetSemantics { contentDescription = testo },
        horizontalArrangement = Arrangement.spacedBy(-altezza * 0.06f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (c in testo) {
            when {
                c.isDigit() -> Image(
                    painterResource(cifre[c - '0']), null, colorFilter = filtro,
                    modifier = Modifier.height(altezza).aspectRatio(PROPORZIONE_CIFRA)
                )
                c == ':' -> Image(
                    painterResource(R.drawable.cifra_inchiostro_due_punti), null, colorFilter = filtro,
                    modifier = Modifier.height(altezza).aspectRatio(PROPORZIONE_DUE_PUNTI)
                )
                else -> Text(c.toString(), color = colore)
            }
        }
    }
}
