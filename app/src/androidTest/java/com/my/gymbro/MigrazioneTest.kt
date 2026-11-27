package com.my.gymbro

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.my.gymbro.data.GymBroDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrazioni del database sugli schemi esportati (app/schemas): un database creato con la
 * versione vecchia, migrato e confrontato con quello che Room si aspetta.
 *
 * Usa un file di prova suo, non il database dell'app. ATTENZIONE però: connectedDebugAndroidTest
 * a fine corsa disinstalla l'app (dati compresi): eseguirlo solo su un emulatore.
 */
@RunWith(AndroidJUnit4::class)
class MigrazioneTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GymBroDatabase::class.java,
    )

    @Test
    fun da2a3_chiaviDaiNomiDelCatalogo_datiIntatti() {
        helper.createDatabase(NOME, 2).use { db ->
            fun esercizio(id: Int, nome: String, gruppo: String, attrezzo: String, predefinito: Boolean) = db.execSQL(
                "INSERT INTO esercizi (id, nome, gruppoMuscolare, attrezzatura, descrizione, immaginePath, predefinito) " +
                    "VALUES (?, ?, ?, ?, NULL, NULL, ?)",
                arrayOf<Any>(id, nome, gruppo, attrezzo, if (predefinito) 1 else 0),
            )
            esercizio(1, "Panca piana", "PETTO", "BILANCIERE", predefinito = true)
            // Rinominato prima delle chiavi: dal nome non si può sapere quale fosse.
            esercizio(2, "La mia panca", "PETTO", "BILANCIERE", predefinito = true)
            // Doppione nato dal vecchio problema: la chiave è unica, la prende il più vecchio.
            esercizio(3, "Panca piana", "PETTO", "BILANCIERE", predefinito = true)
            // Dell'utente, anche se ha il nome di uno del catalogo: niente chiave.
            esercizio(4, "Curl con manubri", "BICIPITI", "MANUBRI", predefinito = false)
            db.execSQL("INSERT INTO sessioni (id, schedaId, nomeScheda, dataInizio, dataFine) VALUES (10, NULL, 'Lunedì', 1000, 2000)")
            db.execSQL(
                "INSERT INTO serie_eseguite (id, sessioneId, esercizioId, numero, ripetizioni, pesoKg, durataSec, completata) " +
                    "VALUES (20, 10, 2, 1, 8, 80.0, NULL, 1)"
            )
        }

        // Controlla anche che lo schema risultante sia identico a 3.json (indice unico compreso).
        val db = helper.runMigrationsAndValidate(NOME, 3, true, GymBroDatabase.MIGRATION_2_3)

        val chiavi = db.query("SELECT id, chiaveSeed FROM esercizi ORDER BY id").use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), if (c.isNull(1)) null else c.getString(1)) }
        }
        assertEquals(mapOf(1L to "Panca piana", 2L to null, 3L to null, 4L to null), chiavi)

        // Storico intatto.
        db.query("SELECT esercizioId, ripetizioni, pesoKg FROM serie_eseguite WHERE id = 20").use { c ->
            c.moveToFirst()
            assertEquals(2L, c.getLong(0))
            assertEquals(8, c.getInt(1))
            assertEquals(80f, c.getFloat(2))
        }

        // La stessa chiave due volte non è ammessa.
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL("UPDATE esercizi SET chiaveSeed = 'Panca piana' WHERE id = 3")
        }
        db.close()
    }

    private companion object {
        const val NOME = "migrazione-test.db"
    }
}
