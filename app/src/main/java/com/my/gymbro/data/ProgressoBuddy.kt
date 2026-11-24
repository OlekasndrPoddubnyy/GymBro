package com.my.gymbro.data

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

// Stato salvato di Gym Buddy e dei suggerimenti, con la sua forma testuale.

/**
 * Esercizio dentro una scheda. Non si usa l'id di esercizi_scheda: cambia a ogni salvataggio della
 * scheda, mentre questa coppia resta la stessa.
 */
data class ChiaveBuddy(val schedaId: Long, val esercizioId: Long)

/** Ciò che serve per annullare una sostituzione: esercizio, note e pesi delle righe cambiate. */
data class Precedente(val esercizioId: Long, val note: List<String?>, val pesi: List<Float?>)

data class ProgressoBuddy(
    val attivo: Boolean = false,
    val regole: RegoleBuddy = RegoleBuddy(),
    val stato: StatoBuddy? = null,         // null = Gym Buddy mai attivato
    val sessioniSenzaAumento: Int = 0,
    val inSchedaDal: Long? = null,         // null = vale la prima volta nello storico
    val rimandatoFino: Long? = null,
    val ignorato: Boolean = false,
    val precedente: Precedente? = null,
    val ultimaProposta: String? = null,
    val ultimaAccettata: Boolean? = null,  // null = ancora senza risposta
)

fun ProgressoBuddy.dopoAllenamento(seriePreviste: Int, fatte: List<SerieFatta>): ProgressoBuddy {
    if (!attivo || stato == null || fatte.isEmpty()) return this
    val nuovo = stato.dopoSessione(regole, seriePreviste, fatte)
    val proposta = nuovo.proposta
    return copy(
        stato = nuovo,
        // La calibrazione non è una sessione di stallo.
        sessioniSenzaAumento = if (stato.calibrazione) 0 else sessioniSenzaAumento + 1,
        ultimaProposta = proposta?.let(::tipoProposta) ?: ultimaProposta,
        ultimaAccettata = if (proposta != null) null else ultimaAccettata,
    )
}

fun ProgressoBuddy.rispondi(accettata: Boolean): ProgressoBuddy {
    val proposta = stato?.proposta ?: return this
    return copy(
        stato = if (accettata) stato.accetta() else stato.rifiuta(),
        // Anche lo scarico azzera lo stallo: non si propone di cambiare esercizio subito dopo.
        sessioniSenzaAumento = if (accettata && proposta.obiettivo != null) 0 else sessioniSenzaAumento,
        ultimaProposta = tipoProposta(proposta),
        ultimaAccettata = accettata,
    )
}

private const val PIU_RIPETIZIONI = "piu_ripetizioni"
private const val PIU_PESO = "piu_peso"
private const val SCARICO = "scarico"
private const val CORREGGI_CARICO = "correggi_carico"
private const val VARIANTE = "variante"
private const val MENO_AIUTO = "meno_aiuto"

private fun tipoProposta(p: Proposta): String = when (p) {
    is Proposta.PiuRipetizioni -> PIU_RIPETIZIONI
    is Proposta.PiuPeso -> PIU_PESO
    is Proposta.Scarico -> SCARICO
    is Proposta.CorreggiCarico -> CORREGGI_CARICO
    is Proposta.MenoAiuto -> MENO_AIUTO
    Proposta.VariantePiuDifficile -> VARIANTE
}

fun codificaProgresso(p: ProgressoBuddy): String {
    val c = Properties()
    fun metti(chiave: String, valore: Any?) {
        if (valore != null) c.setProperty(chiave, valore.toString())
    }
    metti("attivo", p.attivo)
    metti("min", p.regole.ripMin)
    metti("max", p.regole.ripMax)
    metti("passo", p.regole.passoRip)
    metti("incremento", p.regole.incrementoKg)
    metti("conferme", p.regole.conferme)
    metti("rapida", p.regole.progressioneRapida)
    p.stato?.let { s ->
        metti("rip", s.obiettivo.ripetizioni)
        metti("kg", s.obiettivo.caricoKg)
        metti("complete", s.completeDiFila)
        metti("incomplete", s.incompleteDiFila)
        metti("calibrazione", s.calibrazione)
        s.proposta?.let { proposta ->
            metti("proposta", tipoProposta(proposta))
            metti("proposta.rip", proposta.obiettivo?.ripetizioni)
            metti("proposta.kg", proposta.obiettivo?.caricoKg)
        }
    }
    metti("senzaAumento", p.sessioniSenzaAumento)
    metti("dal", p.inSchedaDal)
    metti("rimandato", p.rimandatoFino)
    metti("ignorato", p.ignorato)
    metti("ultima", p.ultimaProposta)
    metti("accettata", p.ultimaAccettata)
    p.precedente?.let { prima ->
        metti("prima.id", prima.esercizioId)
        metti("prima.note", prima.note.size)
        prima.note.forEachIndexed { i, nota -> metti("prima.nota.$i", nota) }
        metti("prima.pesi", prima.pesi.size)
        prima.pesi.forEachIndexed { i, peso -> metti("prima.peso.$i", peso) }
    }
    return StringWriter().also { c.store(it, null) }.toString()
}

fun decodificaProgresso(testo: String): ProgressoBuddy {
    val c = Properties().apply { load(StringReader(testo)) }
    fun testoDi(chiave: String): String? = c.getProperty(chiave)
    fun intero(chiave: String): Int? = testoDi(chiave)?.toIntOrNull()
    fun decimale(chiave: String): Float? = testoDi(chiave)?.toFloatOrNull()

    val base = RegoleBuddy()
    val regole = RegoleBuddy(
        ripMin = intero("min") ?: base.ripMin,
        ripMax = intero("max") ?: base.ripMax,
        passoRip = intero("passo") ?: base.passoRip,
        incrementoKg = decimale("incremento"),
        conferme = intero("conferme") ?: base.conferme,
        progressioneRapida = testoDi("rapida").toBoolean(),
    )
    val proposto = intero("proposta.rip")?.let { Obiettivo(it, decimale("proposta.kg")) }
    val tipo = testoDi("proposta")
    val proposta: Proposta? = when {
        tipo == VARIANTE -> Proposta.VariantePiuDifficile
        proposto == null -> null
        tipo == PIU_RIPETIZIONI -> Proposta.PiuRipetizioni(proposto)
        tipo == PIU_PESO -> Proposta.PiuPeso(proposto)
        tipo == SCARICO -> Proposta.Scarico(proposto)
        tipo == CORREGGI_CARICO -> Proposta.CorreggiCarico(proposto)
        tipo == MENO_AIUTO -> Proposta.MenoAiuto(proposto)
        else -> null
    }
    val stato = intero("rip")?.let { rip ->
        StatoBuddy(
            obiettivo = Obiettivo(rip, decimale("kg")),
            completeDiFila = intero("complete") ?: 0,
            incompleteDiFila = intero("incomplete") ?: 0,
            calibrazione = testoDi("calibrazione").toBoolean(),
            proposta = proposta,
        )
    }
    val precedente = testoDi("prima.id")?.toLongOrNull()?.let { id ->
        Precedente(
            esercizioId = id,
            note = List(intero("prima.note") ?: 0) { testoDi("prima.nota.$it") },
            pesi = List(intero("prima.pesi") ?: 0) { decimale("prima.peso.$it") },
        )
    }
    return ProgressoBuddy(
        attivo = testoDi("attivo").toBoolean() && stato != null,
        regole = regole,
        stato = stato,
        sessioniSenzaAumento = intero("senzaAumento") ?: 0,
        inSchedaDal = testoDi("dal")?.toLongOrNull(),
        rimandatoFino = testoDi("rimandato")?.toLongOrNull(),
        ignorato = testoDi("ignorato").toBoolean(),
        precedente = precedente,
        ultimaProposta = testoDi("ultima"),
        ultimaAccettata = testoDi("accettata")?.toBoolean(),
    )
}
