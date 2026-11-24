package com.my.gymbro.ui.theme

import androidx.compose.ui.graphics.Color

// Palette "Carta": inchiostro e carta come le cifre a pennello. Il lavoro è bianco carta
// (in chiaro diventa inchiostro), il recupero è vermiglio, come il timbro rosso sulle stampe.

val Inchiostro = Color(0xFF0D0E0B)
val Carta = Color(0xFFEDEBE3)
val Vermiglio = Color(0xFFEE5A3C)
// Avvisi/errori in ambra: il rosso è già del recupero e i due non devono confondersi.
val Ambra = Color(0xFFF0B429)

// ---- Tema scuro ----
val ScuroSfondo = Color(0xFF0D0E0B)
val ScuroBarra = Color(0xFF121310)
val ScuroCard = Color(0xFF1A1B17)
val ScuroCardAlta = Color(0xFF24261F)
val ScuroCardMassima = Color(0xFF2B2E25)
val ScuroBordo = Color(0xFF2E3028)
val ScuroLinea = Color(0xFF3A3D33)
val ScuroTesto = Carta
val ScuroTestoSecondario = Color(0xFFA3A69A)

// ---- Tema chiaro ----
val ChiaroSfondo = Color(0xFFF2F3ED)
val ChiaroBarra = Color(0xFFFAFBF6)
val ChiaroCard = Color(0xFFFFFFFF)
val ChiaroCardAlta = Color(0xFFE8EAE1)
val ChiaroCardMassima = Color(0xFFDFE2D6)
val ChiaroBordo = Color(0xFFDDE0D4)
val ChiaroLinea = Color(0xFFC3C7B8)
val ChiaroTesto = Color(0xFF14150F)
val ChiaroTestoSecondario = Color(0xFF55584D)

// In chiaro il vermiglio pieno è poco leggibile come testo: per le scritte si usa una versione più scura.
val VermiglioTesto = Color(0xFFB8361C)
val AmbraTesto = Color(0xFF8A5A00)
