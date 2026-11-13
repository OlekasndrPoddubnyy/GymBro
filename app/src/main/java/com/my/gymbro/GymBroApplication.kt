package com.my.gymbro

import android.app.Application
import com.my.gymbro.data.GymBroDatabase
import com.my.gymbro.data.GymBroRepository
import com.my.gymbro.data.Impostazioni
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Versione della libreria esercizi predefiniti. v12: catalogo Technogym (Selection/Element+), multipower, cavi, pesi liberi e corpo libero ampliati. */
private const val VERSIONE_ESERCIZI = 12

/** Versione delle schede predefinite. v4–v11: schede Total body A/B/C. Aumentarla SOLO quando cambiano i template in SchedePredefinite.kt (sovrascrive le schede omonime). */
private const val VERSIONE_SCHEDE = 11

class GymBroApplication : Application() {
    val repository: GymBroRepository by lazy {
        GymBroRepository(GymBroDatabase.getInstance(this).gymBroDao())
    }
    val impostazioni: Impostazioni by lazy { Impostazioni(this) }

    override fun onCreate() {
        super.onCreate()
        // Seeding incrementale di esercizi e schede predefinite (nuovi DB ed esistenti, senza duplicare).
        val aggiornaEsercizi = impostazioni.versioneSeed < VERSIONE_ESERCIZI
        val aggiornaSchede = impostazioni.versioneSchede < VERSIONE_SCHEDE
        if (aggiornaEsercizi || aggiornaSchede) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                if (aggiornaEsercizi) {
                    val slugs = assets.list("esercizi")?.toSet() ?: emptySet()
                    repository.assicuraEserciziPredefiniti(slugs)
                    impostazioni.versioneSeed = VERSIONE_ESERCIZI
                }
                // Dopo gli esercizi: le schede li cercano per nome nella libreria.
                if (aggiornaSchede) {
                    repository.assicuraSchedePredefinite()
                    impostazioni.versioneSchede = VERSIONE_SCHEDE
                }
            }
        }
    }
}
