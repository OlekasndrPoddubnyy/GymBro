package com.my.gymbro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/** Sezioni della barra in basso, nell'ordine in cui compaiono. */
enum class Sezione(val label: String, val sopratitolo: String, val icon: ImageVector) {
    ESERCIZI("Esercizi", "Libreria", Icons.Filled.FitnessCenter),
    TIMER("Timer", "Intervalli", Icons.Filled.Timer),
    SCHEDE("Schede", "My schedule", Icons.Filled.CalendarMonth),
    REPORT("Riepilogo", "Statistiche", Icons.Filled.BarChart)
}
