package com.my.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
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
        (q.isBlank() || e.nome.contains(q, ignoreCase = true)) &&
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filtro.query,
            onValueChange = { onChange(filtro.copy(query = it)) },
            label = { Text("Cerca esercizio") },
            singleLine = true,
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
                FilterChip(
                    selected = filtro.gruppo == null,
                    onClick = { onChange(filtro.copy(gruppo = null)) },
                    label = { Text("Tutti i gruppi") }
                )
            }
            items(GruppoMuscolare.entries) { g ->
                FilterChip(
                    selected = filtro.gruppo == g,
                    onClick = { onChange(filtro.copy(gruppo = if (filtro.gruppo == g) null else g)) },
                    label = { Text(g.label) }
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = filtro.attrezzatura == null,
                    onClick = { onChange(filtro.copy(attrezzatura = null)) },
                    label = { Text("Tutte") }
                )
            }
            items(Attrezzatura.entries) { a ->
                FilterChip(
                    selected = filtro.attrezzatura == a,
                    onClick = { onChange(filtro.copy(attrezzatura = if (filtro.attrezzatura == a) null else a)) },
                    label = { Text(a.label) }
                )
            }
        }
    }
}
