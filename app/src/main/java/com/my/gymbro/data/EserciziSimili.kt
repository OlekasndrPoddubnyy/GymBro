package com.my.gymbro.data

import kotlin.math.roundToInt

// Regole dei sostituti, senza dipendenze da Android né dal database.

private const val PUNTI_PRINCIPALI = 0.4
private const val PUNTI_MOVIMENTO = 0.3
private const val PUNTI_SECONDARI = 0.2
private const val PUNTI_ATTREZZO = 0.1
private const val MS_SETTIMANA = 7 * 24 * 60 * 60 * 1000L

/** Le caratteristiche che contano per la somiglianza. [movimento] null = non classificato. */
data class ProfiloEsercizio(
    val id: Long,
    val principali: Set<String>,
    val secondari: Set<String>,
    val movimento: String?,
    val attrezzo: Attrezzatura,
)

/** Coppia senza verso: A-B e B-A sono la stessa chiave. */
class CoppiaEsercizi(a: Long, b: Long) {
    val primo: Long = minOf(a, b)
    val secondo: Long = maxOf(a, b)

    override fun equals(other: Any?): Boolean =
        other is CoppiaEsercizi && other.primo == primo && other.secondo == secondo

    override fun hashCode(): Int = 31 * primo.hashCode() + secondo.hashCode()
}

data class Sostituto(val esercizioId: Long, val peso: Double)

data class RegoleSostituzione(
    val settimaneMax: Int = 8,
    val sessioniStallo: Int = 4,
    val sogliaMinima: Double = 0.6,
    val massimoProposte: Int = 3,
)

/**
 * Peso da 0 a 1, simmetrico. I punti dei muscoli sono proporzionali a quanti sono in comune;
 * il solo attrezzo uguale non rende simili due esercizi (panca e squat valgono 0).
 */
fun somiglianza(a: ProfiloEsercizio, b: ProfiloEsercizio): Double {
    val principali = inComune(a.principali, b.principali)
    val stessoMovimento = a.movimento != null && a.movimento == b.movimento
    if (principali == 0.0 && !stessoMovimento) return 0.0

    var punti = PUNTI_PRINCIPALI * principali + PUNTI_SECONDARI * inComune(a.secondari, b.secondari)
    if (stessoMovimento) punti += PUNTI_MOVIMENTO
    if (a.attrezzo == b.attrezzo) punti += PUNTI_ATTREZZO
    return (punti * 100).roundToInt() / 100.0
}

/**
 * Alternative per [attuale], dalla più simile.
 *
 * @param inScheda esercizi già nella scheda, da non proporre.
 * @param manuali pesi corretti dall'utente: prevalgono su quelli calcolati.
 * @param attrezziDisponibili null = attrezzatura dell'utente non nota.
 */
fun sostituti(
    attuale: ProfiloEsercizio,
    libreria: List<ProfiloEsercizio>,
    inScheda: Set<Long>,
    regole: RegoleSostituzione = RegoleSostituzione(),
    manuali: Map<CoppiaEsercizi, Double> = emptyMap(),
    attrezziDisponibili: Set<Attrezzatura>? = null,
): List<Sostituto> = libreria.asSequence()
    .filter { it.id != attuale.id && it.id !in inScheda }
    .filter { attrezziDisponibili == null || it.attrezzo in attrezziDisponibili }
    .map { Sostituto(it.id, manuali[CoppiaEsercizi(attuale.id, it.id)] ?: somiglianza(attuale, it)) }
    .filter { it.peso >= regole.sogliaMinima }
    .sortedByDescending { it.peso }
    .take(regole.massimoProposte)
    .toList()

/**
 * "Troppo a lungo": stallo oppure tempo, il primo che arriva.
 *
 * @param sessioniSenzaAumento null = Gym Buddy spento, conta solo il tempo.
 * @param rimandatoFino fino a questo istante il suggerimento non ricompare.
 */
fun daSuggerire(
    ora: Long,
    inSchedaDal: Long,
    sessioniSenzaAumento: Int?,
    regole: RegoleSostituzione = RegoleSostituzione(),
    rimandatoFino: Long? = null,
    ignorato: Boolean = false,
): Boolean {
    if (ignorato || (rimandatoFino != null && ora < rimandatoFino)) return false
    val stallo = sessioniSenzaAumento != null && sessioniSenzaAumento >= regole.sessioniStallo
    return stallo || ora - inSchedaDal >= regole.settimaneMax * MS_SETTIMANA
}

private fun inComune(a: Set<String>, b: Set<String>): Double {
    val tutti = a union b
    return if (tutti.isEmpty()) 0.0 else (a intersect b).size.toDouble() / tutti.size
}
