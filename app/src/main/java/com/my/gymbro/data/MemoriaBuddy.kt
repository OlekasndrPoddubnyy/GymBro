package com.my.gymbro.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREF_PROGRESSO = "p:"
private const val PREF_SOMIGLIANZA = "s:"
private const val PREF_IMPOSTAZIONE = "i:"

data class DatiBuddy(
    val progressi: Map<ChiaveBuddy, ProgressoBuddy> = emptyMap(),
    val impostazioni: ImpostazioniBuddy = ImpostazioniBuddy(),
    val manuali: Map<CoppiaEsercizi, Double> = emptyMap(),
)

/**
 * Memoria di Gym Buddy e dei suggerimenti, su SharedPreferences separate dal database: lo schema e
 * lo storico non si toccano, e salvare una scheda (che ricrea le sue righe) non azzera i contatori.
 */
class MemoriaBuddy(context: Context) {
    private val prefs = context.getSharedPreferences("gymbro_buddy", Context.MODE_PRIVATE)

    private val _dati = MutableStateFlow(leggi())
    val dati: StateFlow<DatiBuddy> = _dati.asStateFlow()

    fun progresso(chiave: ChiaveBuddy): ProgressoBuddy? = _dati.value.progressi[chiave]

    fun salva(chiave: ChiaveBuddy, progresso: ProgressoBuddy) = salva(mapOf(chiave to progresso))

    fun salva(nuovi: Map<ChiaveBuddy, ProgressoBuddy>) {
        if (nuovi.isEmpty()) return
        val editor = prefs.edit()
        nuovi.forEach { (k, p) ->
            editor.putString("$PREF_PROGRESSO${k.schedaId}:${k.esercizioId}", codificaProgresso(p))
        }
        editor.apply()
        _dati.value = _dati.value.copy(progressi = _dati.value.progressi + nuovi)
    }

    /** [peso] null = torna al valore calcolato. */
    fun correggiSomiglianza(coppia: CoppiaEsercizi, peso: Double?) {
        val chiave = "$PREF_SOMIGLIANZA${coppia.primo}:${coppia.secondo}"
        val editor = prefs.edit()
        val manuali = _dati.value.manuali
        val nuovi = if (peso == null) {
            editor.remove(chiave)
            manuali - coppia
        } else {
            editor.putString(chiave, peso.toString())
            manuali + (coppia to peso)
        }
        editor.apply()
        _dati.value = _dati.value.copy(manuali = nuovi)
    }

    fun impostazioni(i: ImpostazioniBuddy) {
        val editor = prefs.edit()
        fun metti(nome: String, valore: Any) {
            editor.putString(PREF_IMPOSTAZIONE + nome, valore.toString())
        }
        metti("settimane", i.sostituzione.settimaneMax)
        metti("stallo", i.sostituzione.sessioniStallo)
        metti("soglia", i.sostituzione.sogliaMinima)
        metti("rimanda", i.giorniRimanda)
        metti("min", i.ripMin)
        metti("max", i.ripMax)
        metti("passo", i.passoRip)
        metti("conferme", i.conferme)
        metti("rapida", i.progressioneRapida)
        metti("kgGambe", i.kgBilanciereGambe)
        metti("kgAlto", i.kgBilanciereAlto)
        metti("kgManubri", i.kgManubri)
        metti("kgMacchine", i.kgMacchine)
        metti("attrezzi", i.attrezziDisponibili.joinToString(",") { it.name })
        editor.apply()
        _dati.value = _dati.value.copy(impostazioni = i)
    }

    /** Gli stessi progressi su un'altra scheda: la copia di una scheda riparte da dove era l'originale. */
    fun copiaScheda(da: Long, a: Long) {
        salva(
            _dati.value.progressi
                .filterKeys { it.schedaId == da }
                .mapKeys { (k, _) -> ChiaveBuddy(a, k.esercizioId) }
        )
    }

    /** Scheda eliminata: i suoi progressi non servono più. */
    fun dimenticaScheda(id: Long) {
        val chiavi = _dati.value.progressi.keys.filter { it.schedaId == id }
        if (chiavi.isEmpty()) return
        val editor = prefs.edit()
        chiavi.forEach { editor.remove("$PREF_PROGRESSO${it.schedaId}:${it.esercizioId}") }
        editor.apply()
        _dati.value = _dati.value.copy(progressi = _dati.value.progressi - chiavi.toSet())
    }

    /** Il contenuto salvato, per il backup. */
    fun esporta(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    /** Sostituisce tutto con il contenuto di un backup. */
    fun ripristina(contenuto: Map<String, String>) {
        val editor = prefs.edit().clear()
        contenuto.forEach { (k, v) -> editor.putString(k, v) }
        // commit, non apply: subito dopo si rilegge e il ripristino deve essere già su disco.
        editor.commit()
        _dati.value = leggi()
    }

    /** Schede con un esercizio sostituito: l'aggiornamento delle schede predefinite non le sovrascrive. */
    fun schedePersonalizzate(): Set<Long> =
        _dati.value.progressi.filterValues { it.precedente != null }.keys.map { it.schedaId }.toSet()

    private fun leggi(): DatiBuddy {
        val progressi = mutableMapOf<ChiaveBuddy, ProgressoBuddy>()
        val manuali = mutableMapOf<CoppiaEsercizi, Double>()
        val generali = mutableMapOf<String, String>()
        prefs.all.forEach { (nome, valore) ->
            val testo = valore as? String ?: return@forEach
            if (nome.startsWith(PREF_IMPOSTAZIONE)) {
                generali[nome.removePrefix(PREF_IMPOSTAZIONE)] = testo
                return@forEach
            }
            val id = nome.substringAfter(':').split(':').mapNotNull { it.toLongOrNull() }
            if (id.size != 2) return@forEach
            if (nome.startsWith(PREF_SOMIGLIANZA)) {
                testo.toDoubleOrNull()?.let { manuali[CoppiaEsercizi(id[0], id[1])] = it }
            } else if (nome.startsWith(PREF_PROGRESSO)) {
                try {
                    progressi[ChiaveBuddy(id[0], id[1])] = decodificaProgresso(testo)
                } catch (e: IllegalArgumentException) {
                    Log.e("GymBro", "Stato Gym Buddy illeggibile: $nome", e)
                }
            }
        }
        return DatiBuddy(progressi, leggiImpostazioni(generali), manuali)
    }

    private fun leggiImpostazioni(v: Map<String, String>): ImpostazioniBuddy {
        val d = ImpostazioniBuddy()
        fun intero(nome: String, base: Int): Int = v[nome]?.toIntOrNull() ?: base
        fun kg(nome: String, base: Float): Float = v[nome]?.toFloatOrNull() ?: base
        return ImpostazioniBuddy(
            sostituzione = d.sostituzione.copy(
                settimaneMax = intero("settimane", d.sostituzione.settimaneMax),
                sessioniStallo = intero("stallo", d.sostituzione.sessioniStallo),
                sogliaMinima = v["soglia"]?.toDoubleOrNull() ?: d.sostituzione.sogliaMinima,
            ),
            giorniRimanda = intero("rimanda", d.giorniRimanda),
            ripMin = intero("min", d.ripMin),
            ripMax = intero("max", d.ripMax),
            passoRip = intero("passo", d.passoRip),
            conferme = intero("conferme", d.conferme),
            progressioneRapida = v["rapida"].toBoolean(),
            kgBilanciereGambe = kg("kgGambe", d.kgBilanciereGambe),
            kgBilanciereAlto = kg("kgAlto", d.kgBilanciereAlto),
            kgManubri = kg("kgManubri", d.kgManubri),
            kgMacchine = kg("kgMacchine", d.kgMacchine),
            attrezziDisponibili = v["attrezzi"]
                ?.split(',')
                ?.mapNotNull { nome -> Attrezzatura.entries.firstOrNull { it.name == nome } }
                ?.toSet()
                ?: d.attrezziDisponibili,
        )
    }
}
