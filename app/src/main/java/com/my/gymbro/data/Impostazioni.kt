package com.my.gymbro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Tema(val label: String) {
    SISTEMA("Sistema"),
    CHIARO("Chiaro"),
    SCURO("Scuro")
}

/** Persona mostrata nella mappa dei muscoli. */
enum class Figura(val label: String) {
    UOMO("Uomo"),
    DONNA("Donna")
}

/** Preferenze locali dell'app (offline), salvate su SharedPreferences. */
class Impostazioni(context: Context) {
    private val prefs = context.getSharedPreferences("gymbro_prefs", Context.MODE_PRIVATE)

    private val _tema = MutableStateFlow(leggiTema())
    val tema: StateFlow<Tema> = _tema.asStateFlow()

    fun impostaTema(t: Tema) {
        prefs.edit().putString(CHIAVE_TEMA, t.name).apply()
        _tema.value = t
    }

    private val _figura = MutableStateFlow(leggiFigura())
    val figura: StateFlow<Figura> = _figura.asStateFlow()

    fun impostaFigura(f: Figura) {
        prefs.edit().putString(CHIAVE_FIGURA, f.name).apply()
        _figura.value = f
    }

    /** Versione del set di esercizi predefiniti già inserito (per il seeding incrementale). */
    var versioneSeed: Int
        get() = prefs.getInt(CHIAVE_SEED, 0)
        set(v) = prefs.edit().putInt(CHIAVE_SEED, v).apply()

    /**
     * Versione delle schede predefinite già applicata. Separata da [versioneSeed] così che
     * aggiungere esercizi non riscriva le schede (e le eventuali modifiche dell'utente).
     * Se manca (installazioni precedenti) vale la vecchia versione unica del seed.
     */
    var versioneSchede: Int
        get() = prefs.getInt(CHIAVE_SCHEDE, prefs.getInt(CHIAVE_SEED, 0))
        set(v) = prefs.edit().putInt(CHIAVE_SCHEDE, v).apply()

    /** true quando le copie "(versione precedente)" delle schede predefinite sono già state tolte. */
    var copieSchedeEliminate: Boolean
        get() = prefs.getBoolean(CHIAVE_COPIE_ELIMINATE, false)
        set(v) = prefs.edit().putBoolean(CHIAVE_COPIE_ELIMINATE, v).apply()

    /** Nomi degli esercizi predefiniti eliminati dall'utente: gli aggiornamenti del catalogo non li reinseriscono. */
    val predefinitiEliminati: Set<String>
        get() = prefs.getStringSet(CHIAVE_ELIMINATI, null)?.toSet() ?: emptySet()

    fun segnaPredefinitoEliminato(nome: String) {
        prefs.edit().putStringSet(CHIAVE_ELIMINATI, predefinitiEliminati + nome).apply()
    }

    /** Dopo un ripristino valgono gli esercizi eliminati del backup. */
    fun ripristinaPredefinitiEliminati(nomi: Set<String>) {
        prefs.edit().putStringSet(CHIAVE_ELIMINATI, nomi).apply()
    }

    private fun leggiTema(): Tema =
        prefs.getString(CHIAVE_TEMA, null)
            ?.let { runCatching { Tema.valueOf(it) }.getOrNull() }
            ?: Tema.SISTEMA

    private fun leggiFigura(): Figura =
        prefs.getString(CHIAVE_FIGURA, null)
            ?.let { runCatching { Figura.valueOf(it) }.getOrNull() }
            ?: Figura.UOMO

    private companion object {
        const val CHIAVE_TEMA = "tema"
        const val CHIAVE_FIGURA = "figura_mappa"
        const val CHIAVE_SEED = "seed_version"
        const val CHIAVE_SCHEDE = "schede_version"
        const val CHIAVE_ELIMINATI = "predefiniti_eliminati"
        const val CHIAVE_COPIE_ELIMINATE = "copie_schede_eliminate"
    }
}
