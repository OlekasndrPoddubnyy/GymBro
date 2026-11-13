package com.my.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.gymbro.ui.theme.StileSopratitolo
import com.my.gymbro.ui.theme.StileTitoloSezione

/** Bottone principale lime a tutta larghezza (azione primaria della schermata). */
@Composable
fun BottonePrincipale(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    ) {
        if (icona != null) {
            Icon(icona, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(testo, style = MaterialTheme.typography.titleMedium)
    }
}

/** Bottone secondario pieno tonale (es. "Salta", "Stop"). */
@Composable
fun BottoneSecondario(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    contenitore: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contenuto: Color = MaterialTheme.colorScheme.onSurface,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = contenitore, contentColor = contenuto),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (icona != null) {
            Icon(icona, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(testo, style = MaterialTheme.typography.titleSmall)
    }
}

/** Testo sopratitolo maiuscolo (es. "TEMPO TOTALE STIMATO"). */
@Composable
fun Sopratitolo(testo: String, modifier: Modifier = Modifier, colore: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(testo.uppercase(), style = StileSopratitolo, color = colore, modifier = modifier)
}

/** Titolo di sezione condensato maiuscolo. */
@Composable
fun TitoloSezione(testo: String, modifier: Modifier = Modifier, colore: Color = MaterialTheme.colorScheme.onSurface) {
    Text(testo.uppercase(), style = StileTitoloSezione, color = colore, modifier = modifier)
}

/** Bottone icona quadrato arrotondato (indietro, chiudi…). */
@Composable
fun BottoneIcona(icona: ImageVector, descrizione: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.size(44.dp)
    ) { Icon(icona, contentDescription = descrizione) }
}

/**
 * Intestazione delle schermate di dettaglio: bottone indietro, sopratitolo + titolo condensato,
 * azioni a destra.
 */
@Composable
fun IntestazioneDettaglio(
    titolo: String,
    onIndietro: () -> Unit,
    modifier: Modifier = Modifier,
    sopratitolo: String? = null,
    iconaIndietro: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    descrizioneIndietro: String = "Indietro",
    azioni: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BottoneIcona(iconaIndietro, descrizioneIndietro, onIndietro)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = azioni)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (sopratitolo != null) Sopratitolo(sopratitolo)
            Text(
                titolo.uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Chip di filtro a pillola: lime pieno quando selezionato, contorno sottile altrimenti. */
@Composable
fun ChipForge(selezionato: Boolean, onClick: () -> Unit, testo: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selezionato,
        onClick = onClick,
        label = { Text(testo, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(50),
        modifier = modifier.height(40.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = cs.onSurface,
            selectedContainerColor = cs.primaryContainer,
            selectedLabelColor = cs.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selezionato,
            borderColor = cs.outline,
            selectedBorderColor = Color.Transparent,
        )
    )
}

/** Colori dei campi di testo: riempiti con il colore card, bordo sottile, accento lime/oliva a fuoco. */
@Composable
fun coloriCampo(): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = cs.surfaceContainer,
        unfocusedContainerColor = cs.surfaceContainer,
        disabledContainerColor = cs.surfaceContainer,
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.outlineVariant,
        focusedLabelColor = cs.primary,
        cursorColor = cs.primary,
    )
}

/** Forma standard dei campi di testo. */
val FormaCampo = RoundedCornerShape(16.dp)

/** Piccola tessera statistica: etichetta sopra, numero condensato sotto. */
@Composable
fun TesseraStat(etichetta: String, valore: String, modifier: Modifier = Modifier, coloreValore: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(etichetta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(valore, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 32.sp), color = coloreValore, maxLines = 1)
        }
    }
}

/**
 * Colori "miti" della fase di recupero: fondo scuro caldo (o pesca tenue in tema chiaro)
 * con il corallo solo come accento, invece dello schermo tutto corallo.
 */
data class ColoriRiposo(
    val sfondo: Color,        // sfondo a tutto schermo (timer)
    val pannello: Color,      // riquadro della fase (allenamento)
    val testo: Color,
    val accento: Color,       // anello, onde, bottone principale
    val accentoTesto: Color,  // scritte d'accento (es. "RIPOSO")
    val traccia: Color,
    val suAccento: Color,     // testo sopra il bottone principale
)

@Composable
fun coloriRiposo(): ColoriRiposo {
    val scuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (scuro) ColoriRiposo(
        sfondo = Color(0xFF1E1714),
        pannello = Color(0xFF2A1F1A),
        testo = Color(0xFFF2EDE9),
        accento = Color(0xFFFF8A5B),
        accentoTesto = Color(0xFFFF9F78),
        traccia = Color(0x2EFF8A5B),
        suAccento = Color(0xFF0D0E0B),
    ) else ColoriRiposo(
        sfondo = Color(0xFFFCEDE6),
        pannello = Color(0xFFFBE4D9),
        testo = Color(0xFF1F1612),
        accento = Color(0xFFE5784B),
        accentoTesto = Color(0xFFA8401A),
        traccia = Color(0x33E5784B),
        suAccento = Color(0xFF0D0E0B),
    )
}
