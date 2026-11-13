package com.my.gymbro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.my.gymbro.R

/** Big Shoulders Display: condensato e massiccio, per titoli, numeri e cronometri. */
val BigShoulders = FontFamily(
    Font(R.font.big_shoulders_700, FontWeight.Bold),
    Font(R.font.big_shoulders_800, FontWeight.ExtraBold),
    Font(R.font.big_shoulders_900, FontWeight.Black),
)

/** DM Sans: sans pulito e leggibile per testi, etichette e controlli. */
val DmSans = FontFamily(
    Font(R.font.dm_sans_400, FontWeight.Normal),
    Font(R.font.dm_sans_500, FontWeight.Medium),
    Font(R.font.dm_sans_600, FontWeight.SemiBold),
    Font(R.font.dm_sans_700, FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.display(size: Int, line: Int, weight: FontWeight = FontWeight.Black) = copy(
    fontFamily = BigShoulders,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.01.em
)

val Typography = Typography(
    // Display/headline → Big Shoulders (cronometri, numeri grandi, titoli di sezione)
    displayLarge = base.displayLarge.display(96, 92),
    displayMedium = base.displayMedium.display(64, 62),
    displaySmall = base.displaySmall.display(44, 44),
    headlineLarge = base.headlineLarge.display(40, 40),
    headlineMedium = base.headlineMedium.display(32, 34, FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.display(28, 30, FontWeight.ExtraBold),
    // Title/body/label → DM Sans
    titleLarge = base.titleLarge.copy(fontFamily = DmSans, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = DmSans, fontWeight = FontWeight.Normal),
    bodyMedium = base.bodyMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.Normal),
    bodySmall = base.bodySmall.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontFamily = DmSans, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
)

/** Sopratitolo maiuscolo spaziato (es. "LIBRERIA", "TEMPO TOTALE"). */
val StileSopratitolo = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.12.em
)

/** Titolo di sezione condensato maiuscolo (es. "RECORD PERSONALI"). */
val StileTitoloSezione = TextStyle(
    fontFamily = BigShoulders,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 24.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.02.em
)
