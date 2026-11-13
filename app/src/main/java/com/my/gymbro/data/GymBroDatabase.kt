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
    version = 2,
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

        fun getInstance(context: Context): GymBroDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GymBroDatabase::class.java,
                    "gymbro.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}