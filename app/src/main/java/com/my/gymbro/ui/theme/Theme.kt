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
 * Convenzione dei ruoli colore usata in tutta l'app:
 *  - primaryContainer / onPrimaryContainer  → riempimento lime + testo inchiostro (bottoni principali, selezioni)
 *  - primary                                → accento leggibile come testo/icona (lime in scuro, oliva in chiaro)
 *  - tertiaryContainer / onTertiaryContainer → riempimento corallo (fase di recupero)
 *  - tertiary                               → corallo leggibile come testo
 */

private val DarkColorScheme = darkColorScheme(
    primary = Lime,
    onPrimary = Inchiostro,
    primaryContainer = Lime,
    onPrimaryContainer = Inchiostro,
    inversePrimary = OlivaTesto,
    secondary = Color(0xFFC9CCBE),
    onSecondary = Inchiostro,
    secondaryContainer = Color(0xFF2F3324),
    onSecondaryContainer = Lime,
    tertiary = Corallo,
    onTertiary = Inchiostro,
    tertiaryContainer = Corallo,
    onTertiaryContainer = Inchiostro,
    error = Color(0xFFFF6B5E),
    onError = Inchiostro,
    errorContainer = Color(0xFF4A1A14),
    onErrorContainer = Color(0xFFFFDAD4),
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
    primary = OlivaTesto,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Inchiostro,
    inversePrimary = Lime,
    secondary = Color(0xFF4C4F44),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F6B0),
    onSecondaryContainer = Color(0xFF1C2400),
    tertiary = CoralloTesto,
    onTertiary = Color.White,
    tertiaryContainer = Corallo,
    onTertiaryContainer = Inchiostro,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),
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
    // Niente colori dinamici di sistema: l'app ha una sua identità (lime/corallo).
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = ForgeShapes,
        content = content
    )
}
