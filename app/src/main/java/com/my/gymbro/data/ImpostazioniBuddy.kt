package com.my.gymbro.data

import kotlin.math.roundToInt

/** Valori generali di Gym Buddy e dei suggerimenti, modificabili dall'utente. */
data class ImpostazioniBuddy(
    val sostituzione: RegoleSostituzione = RegoleSostituzione(),
    val giorniRimanda: Int = 7,
    val ripMin: Int = 8,
    val ripMax: Int = 12,
    val passoRip: Int = 2,
    val conferme: Int = 2,
    val progressioneRapida: Boolean = false,
    val kgBilanciereGambe: Float = 5f,
    val kgBilanciereAlto: Float = 2.5f,
    val kgManubri: Float = 2f,
    val kgMacchine: Float = 5f,
    val attrezziDisponibili: Set<Attrezzatura> = Attrezzatura.entries.toSet(),
)

private val GRUPPI_GAMBE = setOf(GruppoMuscolare.GAMBE, GruppoMuscolare.GLUTEI, GruppoMuscolare.POLPACCI)

/** Cardio e stretching non progrediscono e non hanno bisogno di sostituti. */
val GruppoMuscolare.allenabile: Boolean
    get() = this != GruppoMuscolare.CARDIO && this != GruppoMuscolare.STRETCHING

/**
 * Regole di partenza per [esercizio].
 *
 * @param ripetizioni quelle scritte in scheda: "10-12" dà il range, "10" solo il minimo.
 */
fun ImpostazioniBuddy.regolePer(esercizio: Esercizio, ripetizioni: String?): RegoleBuddy {
    val numeri = Regex("\\d+").findAll(ripetizioni.orEmpty())
        .mapNotNull { it.value.toIntOrNull() }
        .filter { it > 0 }
        .toList()
    val minimo = numeri.minOrNull() ?: ripMin
    val massimo = if (numeri.size >= 2) numeri.max() else minimo + (ripMax - ripMin).coerceAtLeast(0)
    val incremento = when (esercizio.attrezzatura) {
        Attrezzatura.CORPO_LIBERO -> null
        Attrezzatura.MANUBRI -> kgManubri
        Attrezzatura.MACCHINA, Attrezzatura.CAVI -> kgMacchine
        Attrezzatura.BILANCIERE ->
            if (esercizio.gruppoMuscolare in GRUPPI_GAMBE) kgBilanciereGambe else kgBilanciereAlto
    }
    return RegoleBuddy(
        minimo, massimo, passoRip, incremento, conferme, progressioneRapida,
        caricoInverso = caricoInverso(esercizio.nomeCatalogo),
    )
}

private const val QUOTA_MANUBRIO = 0.4f
private const val SOGLIA_STIMA = 0.7

/**
 * Carico stimato per il sostituto quando non è nello storico; null = va chiesto all'utente.
 * Si stima solo tra bilanciere e manubri (circa 40% per mano) e tra esercizi molto simili.
 */
fun stimaCarico(da: Attrezzatura, a: Attrezzatura, carico: Float?, somiglianza: Double): Float? {
    if (carico == null || somiglianza < SOGLIA_STIMA) return null
    val stima = when {
        da == Attrezzatura.BILANCIERE && a == Attrezzatura.MANUBRI -> carico * QUOTA_MANUBRIO
        da == Attrezzatura.MANUBRI && a == Attrezzatura.BILANCIERE -> carico / QUOTA_MANUBRIO
        else -> return null
    }
    return ((stima * 2).roundToInt() / 2f).takeIf { it > 0f }
}
