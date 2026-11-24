package com.my.gymbro.data

import kotlin.math.roundToInt

// Regole di Gym Buddy (progressione doppia), senza dipendenze da Android né dal database.

private const val INCOMPLETE_PER_SCARICO = 3
private const val QUOTA_SCARICO = 0.10f
private const val MARGINE_PROGRESSIONE_RAPIDA = 2

/** Oltre questa quota del carico l'incremento si dimezza: 4 kg → 5 kg, non 6. */
private const val QUOTA_MASSIMA_AUMENTO = 0.25f

data class RegoleBuddy(
    val ripMin: Int = 8,
    val ripMax: Int = 12,
    val passoRip: Int = 2,
    val incrementoKg: Float? = 2.5f,   // null = corpo libero: il carico non sale
    val conferme: Int = 2,
    val progressioneRapida: Boolean = false,
    /** Macchine assistite: più carico = più aiuto. Non si salva, si ricava dal nome ([caricoInverso]). */
    val caricoInverso: Boolean = false,
)

/** Trazioni e dip assistiti: il peso impostato alleggerisce, quindi progredire vuol dire toglierne. */
fun caricoInverso(nomeEsercizio: String): Boolean = "assistit" in nomeEsercizio.lowercase()

data class Obiettivo(val ripetizioni: Int, val caricoKg: Float?)

data class SerieFatta(val ripetizioni: Int, val caricoKg: Float?)

/** Ciò che Gym Buddy propone: non vale finché l'utente non lo accetta. */
sealed interface Proposta {
    /** Obiettivo che varrebbe accettando; null = solo un consiglio, la scheda non cambia. */
    val obiettivo: Obiettivo?

    data class PiuRipetizioni(override val obiettivo: Obiettivo) : Proposta
    data class PiuPeso(override val obiettivo: Obiettivo) : Proposta
    data class Scarico(override val obiettivo: Obiettivo) : Proposta
    data class CorreggiCarico(override val obiettivo: Obiettivo) : Proposta

    /** Macchina assistita: meno aiuto, cioè meno peso impostato. */
    data class MenoAiuto(override val obiettivo: Obiettivo) : Proposta

    /** Corpo libero a fine range: zavorra o variante più difficile. */
    data object VariantePiuDifficile : Proposta {
        override val obiettivo: Obiettivo? = null
    }
}

data class StatoBuddy(
    val obiettivo: Obiettivo,
    val completeDiFila: Int = 0,
    val incompleteDiFila: Int = 0,
    val calibrazione: Boolean = false, // prima sessione dopo una sostituzione
    val proposta: Proposta? = null,
)

/**
 * Stato dopo una sessione. L'obiettivo non cambia mai qui, solo con [accetta].
 *
 * @param seriePreviste serie in scheda: una saltata rende la sessione incompleta.
 * @param fatte serie registrate; se nessuna, l'esercizio non è stato svolto e nulla cambia.
 */
fun StatoBuddy.dopoSessione(regole: RegoleBuddy, seriePreviste: Int, fatte: List<SerieFatta>): StatoBuddy {
    if (fatte.isEmpty()) return this
    // Senza carico in obiettivo (es. iperestensioni) si progredisce a ripetizioni finché non si usa un peso.
    if (calibrazione || obiettivo.caricoKg == null) {
        val usato = fatte.last().caricoKg
        if (usato != null && usato != obiettivo.caricoKg) {
            return copy(
                calibrazione = false,
                completeDiFila = 0,
                incompleteDiFila = 0,
                proposta = Proposta.CorreggiCarico(obiettivo.copy(caricoKg = usato)),
            )
        }
    }

    val completa = fatte.size >= seriePreviste && fatte.all { it.raggiunge(obiettivo, regole.caricoInverso) }
    if (!completa) {
        // La calibrazione non conta come sessione fallita.
        val incomplete = if (calibrazione) 0 else incompleteDiFila + 1
        return copy(
            calibrazione = false,
            completeDiFila = 0,
            incompleteDiFila = incomplete,
            proposta = if (incomplete >= INCOMPLETE_PER_SCARICO) scarico(obiettivo, regole) else null,
        )
    }

    val doppia = regole.progressioneRapida &&
        fatte.all { it.ripetizioni >= obiettivo.ripetizioni + MARGINE_PROGRESSIONE_RAPIDA }
    val complete = completeDiFila + if (doppia) 2 else 1
    return copy(
        calibrazione = false,
        completeDiFila = complete,
        incompleteDiFila = 0,
        proposta = if (complete >= regole.conferme) avanzamento(obiettivo, regole) else null,
    )
}

/** L'utente accetta: il nuovo obiettivo vale dalla sessione successiva e i contatori ripartono. */
fun StatoBuddy.accetta(): StatoBuddy {
    val p = proposta ?: return this
    return StatoBuddy(obiettivo = p.obiettivo ?: obiettivo)
}

/** L'utente rifiuta: obiettivo invariato; i contatori ripartono per non riproporlo ogni volta. */
fun StatoBuddy.rifiuta(): StatoBuddy = if (proposta == null) this else StatoBuddy(obiettivo = obiettivo)

/**
 * Con meno peso dell'obiettivo la serie non è completa, anche con tutte le ripetizioni.
 * Sulle macchine assistite vale il contrario: è incompleta con più aiuto dell'obiettivo.
 */
private fun SerieFatta.raggiunge(obiettivo: Obiettivo, inverso: Boolean): Boolean {
    if (ripetizioni < obiettivo.ripetizioni) return false
    val richiesto = obiettivo.caricoKg ?: return true
    val usato = caricoKg ?: 0f
    return if (inverso) usato <= richiesto else usato >= richiesto
}

/**
 * Di quanto cambia il carico. Sui pesi piccoli l'incremento pieno sarebbe un salto troppo grande
 * (manubri da 4 kg + 2 = +50%): oltre un quarto del carico si usa metà incremento.
 * Sulle macchine assistite il carico è aiuto, non fatica: si toglie sempre l'incremento pieno.
 */
private fun passoKg(carico: Float, incremento: Float, inverso: Boolean): Float =
    if (!inverso && incremento > carico * QUOTA_MASSIMA_AUMENTO) incremento / 2 else incremento

private fun avanzamento(attuale: Obiettivo, regole: RegoleBuddy): Proposta {
    if (regole.passoRip > 0 && attuale.ripetizioni < regole.ripMax) {
        // L'ultimo passo si accorcia per arrivare al massimo: 12 → 14 → 15.
        val prossime = minOf(attuale.ripetizioni + regole.passoRip, regole.ripMax)
        return Proposta.PiuRipetizioni(attuale.copy(ripetizioni = prossime))
    }
    val carico = attuale.caricoKg
    val incremento = regole.incrementoKg
    if (carico == null || incremento == null || incremento <= 0f) return Proposta.VariantePiuDifficile
    val passo = passoKg(carico, incremento, regole.caricoInverso)
    if (!regole.caricoInverso) return Proposta.PiuPeso(Obiettivo(regole.ripMin, carico + passo))
    // Senza più aiuto da togliere si passa alla versione libera.
    val aiuto = carico - passo
    return if (aiuto > 0f) Proposta.MenoAiuto(Obiettivo(regole.ripMin, aiuto)) else Proposta.VariantePiuDifficile
}

/**
 * Circa il 10% in meno a multipli del passo (almeno uno), tornando al minimo del range.
 * Sulle macchine assistite si aggiunge un passo di aiuto.
 */
private fun scarico(attuale: Obiettivo, regole: RegoleBuddy): Proposta? {
    val carico = attuale.caricoKg
    val incremento = regole.incrementoKg
    val ridotto = if (carico != null && incremento != null && incremento > 0f) {
        val passo = passoKg(carico, incremento, regole.caricoInverso)
        if (regole.caricoInverso) {
            carico + passo
        } else {
            val passi = (carico * QUOTA_SCARICO / passo).roundToInt().coerceAtLeast(1)
            (carico - passi * passo).takeIf { it > 0f } ?: carico
        }
    } else {
        carico
    }
    val nuovo = Obiettivo(regole.ripMin, ridotto)
    return if (nuovo == attuale) null else Proposta.Scarico(nuovo)
}
