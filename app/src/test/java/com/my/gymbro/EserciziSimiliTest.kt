package com.my.gymbro

import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.CoppiaEsercizi
import com.my.gymbro.data.ProfiloEsercizio
import com.my.gymbro.data.allenabile
import com.my.gymbro.data.daSuggerire
import com.my.gymbro.data.eserciziPredefiniti
import com.my.gymbro.data.schemaMovimento
import com.my.gymbro.data.somiglianza
import com.my.gymbro.data.sostituti
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EserciziSimiliTest {

    private val panca = ProfiloEsercizio(1, setOf("petto"), setOf("deltoidi", "tricipiti"), "spinta", Attrezzatura.BILANCIERE)
    private val manubri = panca.copy(id = 2, attrezzo = Attrezzatura.MANUBRI)
    private val inclinata = panca.copy(id = 3, principali = setOf("petto", "deltoidi"), secondari = setOf("tricipiti"))
    private val squat = ProfiloEsercizio(4, setOf("quadricipiti"), setOf("femorali"), "squat", Attrezzatura.BILANCIERE)
    private val libreria = listOf(squat, inclinata, panca, manubri)

    private val giorno = 24 * 60 * 60 * 1000L

    @Test
    fun esempiDelDocumento() {
        assertEquals(0.9, somiglianza(panca, manubri), 0.001)
        assertEquals(0.0, somiglianza(panca, squat), 0.001)
        assertTrue(somiglianza(panca, inclinata) in 0.6..0.89)
    }

    @Test
    fun catalogo_tuttiGliAllenabiliHannoUnoSchema() {
        val senza = eserciziPredefiniti()
            .filter { it.gruppoMuscolare.allenabile && schemaMovimento(it.nome, it.gruppoMuscolare) == null }
            .map { it.nome }
        assertTrue("Senza schema di movimento: $senza", senza.isEmpty())
    }

    @Test
    fun simmetrica() {
        assertEquals(somiglianza(panca, inclinata), somiglianza(inclinata, panca), 0.0)
    }

    @Test
    fun ordinatiESopraSoglia() {
        assertEquals(listOf(2L, 3L), sostituti(panca, libreria, inScheda = setOf(1L)).map { it.esercizioId })
    }

    @Test
    fun escludeQuelliInSchedaESenzaAttrezzo() {
        assertEquals(listOf(3L), sostituti(panca, libreria, inScheda = setOf(2L)).map { it.esercizioId })
        val soloManubri = sostituti(panca, libreria, emptySet(), attrezziDisponibili = setOf(Attrezzatura.MANUBRI))
        assertEquals(listOf(2L), soloManubri.map { it.esercizioId })
    }

    @Test
    fun pesoManualePrevale() {
        val manuali = mapOf(CoppiaEsercizi(4, 1) to 0.95)
        assertEquals(4L, sostituti(panca, libreria, emptySet(), manuali = manuali).first().esercizioId)
    }

    @Test
    fun scattaPerTempoOPerStallo() {
        assertFalse(daSuggerire(55 * giorno, 0, null))
        assertTrue(daSuggerire(56 * giorno, 0, null))
        assertTrue(daSuggerire(10 * giorno, 0, 4))
    }

    @Test
    fun rimandatoOIgnorato_nonCompare() {
        assertFalse(daSuggerire(60 * giorno, 0, null, rimandatoFino = 63 * giorno))
        assertTrue(daSuggerire(63 * giorno, 0, null, rimandatoFino = 63 * giorno))
        assertFalse(daSuggerire(60 * giorno, 0, null, ignorato = true))
    }
}
