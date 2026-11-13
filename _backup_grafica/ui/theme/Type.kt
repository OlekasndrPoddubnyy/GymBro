package com.my.gymbro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.my.gymbro.R

// Nunito: sans rotondo e amichevole, molto leggibile. Font variabile → il peso pilota l'asse "wght".
private val Nunito = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold),
)

private val base = Typography()

// Pesi più sostenuti per leggibilità: corpo a Medium, titoli/numeri/etichette a SemiBold.
private val Pieno = FontWeight.SemiBold
private val Corpo = FontWeight.Medium

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Nunito, fontWeight = Pieno),
    displayMedium = base.displayMedium.copy(fontFamily = Nunito, fontWeight = Pieno),
    displaySmall = base.displaySmall.copy(fontFamily = Nunito, fontWeight = Pieno),
    headlineLarge = base.headlineLarge.copy(fontFamily = Nunito, fontWeight = Pieno),
    headlineMedium = base.headlineMedium.copy(fontFamily = Nunito, fontWeight = Pieno),
    headlineSmall = base.headlineSmall.copy(fontFamily = Nunito, fontWeight = Pieno),
    titleLarge = base.titleLarge.copy(fontFamily = Nunito, fontWeight = Pieno),
    titleMedium = base.titleMedium.copy(fontFamily = Nunito, fontWeight = Pieno),
    titleSmall = base.titleSmall.copy(fontFamily = Nunito, fontWeight = Pieno),
    bodyLarge = base.bodyLarge.copy(fontFamily = Nunito, fontWeight = Corpo),
    bodyMedium = base.bodyMedium.copy(fontFamily = Nunito, fontWeight = Corpo),
    bodySmall = base.bodySmall.copy(fontFamily = Nunito, fontWeight = Corpo),
    labelLarge = base.labelLarge.copy(fontFamily = Nunito, fontWeight = Pieno),
    labelMedium = base.labelMedium.copy(fontFamily = Nunito, fontWeight = Pieno),
    labelSmall = base.labelSmall.copy(fontFamily = Nunito, fontWeight = Pieno),
)
