package com.my.gymbro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

enum class Sezione(val label: String, val icon: ImageVector) {
    TIMER("Timer", Icons.Filled.Timer),
    ESERCIZI("Esercizi", Icons.Filled.FitnessCenter),
    SCHEDE("Mie Schede", Icons.AutoMirrored.Filled.FormatListBulleted),
    REPORT("Report", Icons.Filled.BarChart)
}