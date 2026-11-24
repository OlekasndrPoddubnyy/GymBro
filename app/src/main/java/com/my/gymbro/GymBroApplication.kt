package com.my.gymbro

import android.app.Application
import android.util.Log
import com.my.gymbro.data.GymBroDatabase
import com.my.gymbro.data.GymBroRepository
import com.my.gymbro.data.Impostazioni
import com.my.gymbro.data.MemoriaBuddy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Versione della libreria esercizi predefiniti. v12: catalogo Technogym (Selection/Element+), multipower, cavi, pesi liberi e corpo libero ampliati. */
private const val VERSIONE_ESERCIZI = 12

/** Versione delle schede predefinite. v4–v11: schede Total body A/B/C; v12: giovedì e venerdì senza doppioni, polpacci, B più corta. Aumentarla SOLO quando cambiano i template in SchedePredefinite.kt (aggiorna le schede omonime, tenendo i pesi). */
private const val VERSIONE_SCHEDE = 12

private const val TAG = "GymBro"

class GymBroApplication : Application() {
    val repository: GymBroRepository by lazy {
        GymBroRepository(GymBroDatabase.getInstance(this))
    }
    val impostazioni: Impostazioni by lazy { Impostazioni(this) }
    val memoriaBuddy: MemoriaBuddy by lazy { MemoriaBuddy(this) }

    /** Scope di processo per il lavoro di avvio (non legato a nessuna schermata). */
    private val scopeApp = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        allineaDatiPredefiniti()
    }

    /**
     * Seeding incrementale di esercizi e schede predefinite (nuovi DB ed esistenti, senza duplicare).
     * Un errore non fa crashare l'app: viene registrato nel log, la versione non avanza e si
     * riprova al prossimo avvio.
     */
    private fun allineaDatiPredefiniti() {
        val aggiornaEsercizi = impostazioni.versioneSeed < VERSIONE_ESERCIZI
        val aggiornaSchede = impostazioni.versioneSchede < VERSIONE_SCHEDE
        val togliCopie = !impostazioni.copieSchedeEliminate
        if (!aggiornaEsercizi && !aggiornaSchede && !togliCopie) return

        scopeApp.launch {
            if (togliCopie && tenta("copie delle schede") { repository.eliminaCopieSchedePredefinite() }) {
                impostazioni.copieSchedeEliminate = true
            }
            if (aggiornaEsercizi) {
                val ok = tenta("esercizi predefiniti") {
                    val slugs = assets.list("esercizi")?.toSet() ?: emptySet()
                    repository.assicuraEserciziPredefiniti(slugs, impostazioni.predefinitiEliminati)
                }
                // Le schede cercano gli esercizi per nome: senza catalogo aggiornato si rimanda tutto.
                if (!ok) return@launch
                impostazioni.versioneSeed = VERSIONE_ESERCIZI
            }
            val schedeAllineate = aggiornaSchede && tenta("schede predefinite") {
                repository.assicuraSchedePredefinite(memoriaBuddy.schedePersonalizzate())
            }
            if (schedeAllineate) {
                impostazioni.versioneSchede = VERSIONE_SCHEDE
            }
        }
    }

    private suspend fun tenta(cosa: String, blocco: suspend () -> Unit): Boolean =
        try {
            blocco()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Allineamento $cosa fallito: si riprova al prossimo avvio", e)
            false
        }
}
