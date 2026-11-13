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

/** Preferenze locali dell'app (offline), salvate su SharedPreferences. */
class Impostazioni(context: Context) {
    private val prefs = context.getSharedPreferences("gymbro_prefs", Context.MODE_PRIVATE)

    private val _tema = MutableStateFlow(leggiTema())
    val tema: StateFlow<Tema> = _tema.asStateFlow()

    fun impostaTema(t: Tema) {
        prefs.edit().putString(CHIAVE_TEMA, t.name).apply()
        _tema.value = t
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

    private fun leggiTema(): Tema =
        prefs.getString(CHIAVE_TEMA, null)
            ?.let { runCatching { Tema.valueOf(it) }.getOrNull() }
            ?: Tema.SISTEMA

    private companion object {
        const val CHIAVE_TEMA = "tema"
        const val CHIAVE_SEED = "seed_version"
        const val CHIAVE_SCHEDE = "schede_version"
    }
}
