package com.my.gymbro

import android.app.Application
import com.my.gymbro.data.GymBroDatabase
import com.my.gymbro.data.GymBroRepository
import com.my.gymbro.data.Impostazioni
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val VERSIONE_SEED = 3

class GymBroApplication : Application() {
    val repository: GymBroRepository by lazy {
        GymBroRepository(GymBroDatabase.getInstance(this).gymBroDao())
    }
    val impostazioni: Impostazioni by lazy { Impostazioni(this) }

    override fun onCreate() {
        super.onCreate()
        // Seeding incrementale degli esercizi predefiniti (nuovi DB ed esistenti, senza duplicare).
        if (impostazioni.versioneSeed < VERSIONE_SEED) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val slugs = assets.list("esercizi")?.toSet() ?: emptySet()
                repository.assicuraEserciziPredefiniti(slugs)
                impostazioni.versioneSeed = VERSIONE_SEED
            }
        }
    }
}
