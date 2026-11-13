package com.my.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.macchinaTechnogym

/** Stato dei filtri della libreria esercizi: testo + gruppo muscolare + attrezzatura (null = tutti). */
data class StatoFiltro(
    val query: String = "",
    val gruppo: GruppoMuscolare? = null,
    val attrezzatura: Attrezzatura? = null,
) {
    val attivo: Boolean get() = query.isNotBlank() || gruppo != null || attrezzatura != null
}

fun List<Esercizio>.filtra(f: StatoFiltro): List<Esercizio> {
    val q = f.query.trim()
    return filter { e ->
        // Si cerca nel nome e nel nome della macchina Technogym (es. "low row", "multi hip").
        (q.isBlank() || e.nome.contains(q, ignoreCase = true) ||
            macchinaTechnogym(e.descrizione)?.contains(q, ignoreCase = true) == true) &&
            (f.gruppo == null || e.gruppoMuscolare == f.gruppo) &&
            (f.attrezzatura == null || e.attrezzatura == f.attrezzatura)
    }
}

@Composable
fun FiltriEsercizi(
    filtro: StatoFiltro,
    onChange: (StatoFiltro) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = filtro.query,
            onValueChange = { onChange(filtro.copy(query = it)) },
            placeholder = { Text("Cerca un esercizio") },
            singleLine = true,
            shape = FormaCampo,
            colors = coloriCampo(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filtro.query.isNotEmpty()) {
                    IconButton(onClick = { onChange(filtro.copy(query = "")) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Pulisci")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ChipForge(filtro.gruppo == null, { onChange(filtro.copy(gruppo = null)) }, "Tutti")
            }
            items(GruppoMuscolare.entries) { g ->
                ChipForge(
                    selezionato = filtro.gruppo == g,
                    onClick = { onChange(filtro.copy(gruppo = if (filtro.gruppo == g) null else g)) },
                    testo = g.label
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ChipForge(filtro.attrezzatura == null, { onChange(filtro.copy(attrezzatura = null)) }, "Ogni attrezzo")
            }
            items(Attrezzatura.entries) { a ->
                ChipForge(
                    selezionato = filtro.attrezzatura == a,
                    onClick = { onChange(filtro.copy(attrezzatura = if (filtro.attrezzatura == a) null else a)) },
                    testo = a.label
                )
            }
        }
    }
}
