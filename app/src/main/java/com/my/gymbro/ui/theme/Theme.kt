package com.my.gymbro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Convenzione dei ruoli colore usata in tutta l'app (palette "Carta"):
 *  - primaryContainer / onPrimaryContainer  → riempimento del lavoro/azioni principali:
 *                                             carta su inchiostro in scuro, inchiostro su carta in chiaro
 *  - primary                                → accento leggibile come testo/icona (stessa logica)
 *  - tertiaryContainer / onTertiaryContainer → riempimento vermiglio (fase di recupero)
 *  - tertiary                               → vermiglio leggibile come testo
 *  - error                                  → ambra, per non confondersi col vermiglio del recupero
 */

private val DarkColorScheme = darkColorScheme(
    primary = Carta,
    onPrimary = Inchiostro,
    primaryContainer = Carta,
    onPrimaryContainer = Inchiostro,
    inversePrimary = Inchiostro,
    secondary = Color(0xFFC9CCBE),
    onSecondary = Inchiostro,
    secondaryContainer = ScuroCardMassima,
    onSecondaryContainer = Carta,
    tertiary = Vermiglio,
    onTertiary = Inchiostro,
    tertiaryContainer = Vermiglio,
    onTertiaryContainer = Inchiostro,
    error = Ambra,
    onError = Inchiostro,
    errorContainer = Color(0xFF3D2E0A),
    onErrorContainer = Color(0xFFFFE3A3),
    background = ScuroSfondo,
    onBackground = ScuroTesto,
    surface = ScuroSfondo,
    onSurface = ScuroTesto,
    surfaceVariant = ScuroCardAlta,
    onSurfaceVariant = ScuroTestoSecondario,
    surfaceTint = Color.Transparent,
    inverseSurface = ScuroTesto,
    inverseOnSurface = Inchiostro,
    outline = ScuroLinea,
    outlineVariant = ScuroBordo,
    scrim = Color.Black,
    surfaceBright = ScuroCardAlta,
    surfaceDim = ScuroSfondo,
    surfaceContainerLowest = Color(0xFF080907),
    surfaceContainerLow = ScuroBarra,
    surfaceContainer = ScuroCard,
    surfaceContainerHigh = ScuroCardAlta,
    surfaceContainerHighest = ScuroCardMassima,
)

private val LightColorScheme = lightColorScheme(
    primary = ChiaroTesto,
    onPrimary = Carta,
    primaryContainer = ChiaroTesto,
    onPrimaryContainer = Carta,
    inversePrimary = Carta,
    secondary = Color(0xFF4C4F44),
    onSecondary = Color.White,
    secondaryContainer = ChiaroCardMassima,
    onSecondaryContainer = ChiaroTesto,
    tertiary = VermiglioTesto,
    onTertiary = Color.White,
    tertiaryContainer = Vermiglio,
    onTertiaryContainer = Inchiostro,
    error = AmbraTesto,
    onError = Color.White,
    errorContainer = Color(0xFFFFE9B8),
    onErrorContainer = Color(0xFF2E1F00),
    background = ChiaroSfondo,
    onBackground = ChiaroTesto,
    surface = ChiaroSfondo,
    onSurface = ChiaroTesto,
    surfaceVariant = ChiaroCardAlta,
    onSurfaceVariant = ChiaroTestoSecondario,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF1A1B17),
    inverseOnSurface = ScuroTesto,
    outline = ChiaroLinea,
    outlineVariant = ChiaroBordo,
    scrim = Color.Black,
    surfaceBright = ChiaroCard,
    surfaceDim = ChiaroCardAlta,
    surfaceContainerLowest = ChiaroCard,
    surfaceContainerLow = ChiaroBarra,
    surfaceContainer = ChiaroCard,
    surfaceContainerHigh = ChiaroCardAlta,
    surfaceContainerHighest = ChiaroCardMassima,
)

private val ForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GymBroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Niente colori dinamici di sistema: l'app ha una sua identità (carta/vermiglio).
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = ForgeShapes,
        content = content
    )
}
