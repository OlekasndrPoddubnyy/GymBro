package com.my.gymbro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Esercizio::class,
        Scheda::class,
        EsercizioScheda::class,
        SeriePianificata::class,
        Sessione::class,
        SerieEseguita::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class GymBroDatabase : RoomDatabase() {

    abstract fun gymBroDao(): GymBroDao

    companion object {
        @Volatile private var INSTANCE: GymBroDatabase? = null

        // v2: aggiunge esercizi_scheda.gruppoSuperset (superset/circuiti)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE esercizi_scheda ADD COLUMN gruppoSuperset INTEGER")
            }
        }

        /**
         * v3: esercizi.chiaveSeed, il nome di catalogo degli esercizi predefiniti, che resta anche se
         * l'utente li rinomina. Si assegna a quelli che hanno ancora il nome del catalogo.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE esercizi ADD COLUMN chiaveSeed TEXT")
                val predefiniti = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, nome FROM esercizi WHERE predefinito = 1").use { c ->
                    while (c.moveToNext()) predefiniti += c.getLong(0) to c.getString(1)
                }
                val catalogo = eserciziPredefiniti().mapTo(HashSet()) { it.nome }
                chiaviIniziali(predefiniti, catalogo).forEach { (id, chiave) ->
                    db.execSQL("UPDATE esercizi SET chiaveSeed = ? WHERE id = ?", arrayOf<Any>(chiave, id))
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_esercizi_chiaveSeed` ON `esercizi` (`chiaveSeed`)")
            }
        }

        fun getInstance(context: Context): GymBroDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GymBroDatabase::class.java,
                    "gymbro.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}