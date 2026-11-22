package com.my.gymbro

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.my.gymbro.data.GymBroDatabase
import com.my.gymbro.data.Scheda
import com.my.gymbro.data.Sessione
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Usa un database in memoria. ATTENZIONE: connectedDebugAndroidTest a fine corsa disinstalla l'app
 * (dati compresi): eseguirlo solo su un emulatore, non sul telefono con lo storico vero.
 */
@RunWith(AndroidJUnit4::class)
class SessioneDaoTest {

    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        GymBroDatabase::class.java,
    ).build()

    @After
    fun chiudi() {
        db.close()
    }

    @Test
    fun schedaEliminataDuranteAllenamento_laSessioneSiChiudeLoStesso() = runBlocking<Unit> {
        val dao = db.gymBroDao()
        val schedaId = dao.insertScheda(Scheda(nome = "A"))
        val sessioneId = dao.insertSessione(Sessione(schedaId = schedaId, nomeScheda = "A", dataInizio = 100))

        dao.deleteScheda(Scheda(id = schedaId, nome = "A"))
        dao.chiudiSessione(sessioneId, 200)

        val sessione = dao.getSessioni().first().single()
        assertNull(sessione.schedaId)
        assertEquals(200L, sessione.dataFine)
    }
}
