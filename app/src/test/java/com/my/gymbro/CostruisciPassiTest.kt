package com.my.gymbro

import com.my.gymbro.data.Modalita
import com.my.gymbro.ui.EsercizioEsec
import com.my.gymbro.ui.SerieEsec
import com.my.gymbro.ui.costruisciPassi
import org.junit.Assert.assertEquals
import org.junit.Test

class CostruisciPassiTest {

    private fun es(nSerie: Int, recupero: Int = 60, gruppo: Int? = null) = EsercizioEsec(
        esercizioId = 0,
        nome = "x",
        modalita = Modalita.RIPETIZIONI,
        serie = (1..nSerie).map { SerieEsec(it, null, "10", null, recupero) },
        gruppo = gruppo,
    )

    @Test
    fun esercizioSingolo_unaSerieDopoLAltra_recuperoDopoOgniSerie() {
        val passi = costruisciPassi(listOf(es(3, recupero = 90)))
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), passi.map { it.idxEsercizio to it.idxSerie })
        assertEquals(listOf(90, 90, 90), passi.map { it.recuperoDopo })
        // Il primo blocco parte dopo la preparazione, senza attesa.
        assertEquals(listOf(false, false, false), passi.map { it.attesaPrima })
    }

    @Test
    fun esercizioSuccessivo_attendeInizia() {
        val passi = costruisciPassi(listOf(es(1), es(2)))
        assertEquals(listOf(false, true, false), passi.map { it.attesaPrima })
    }

    @Test
    fun superset_interlacciatoConRecuperoSoloAFineGiro() {
        val passi = costruisciPassi(listOf(es(2, recupero = 60, gruppo = 1), es(2, recupero = 45, gruppo = 1)))
        assertEquals(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), passi.map { it.idxEsercizio to it.idxSerie })
        assertEquals(listOf(0, 45, 0, 45), passi.map { it.recuperoDopo })
        assertEquals(listOf(false, false, false, false), passi.map { it.attesaPrima })
    }

    @Test
    fun superset_conSerieDiverse_ilGiroSiAccorcia() {
        val passi = costruisciPassi(listOf(es(3, gruppo = 1), es(1, recupero = 30, gruppo = 1)))
        assertEquals(listOf(0 to 0, 1 to 0, 0 to 1, 0 to 2), passi.map { it.idxEsercizio to it.idxSerie })
        // Nel giro 0 l'ultimo è il secondo esercizio; dopo, resta solo il primo.
        assertEquals(listOf(0, 30, 60, 60), passi.map { it.recuperoDopo })
    }

    @Test
    fun supersetDopoEsercizioSingolo_attendeSoloAllInizio() {
        val passi = costruisciPassi(listOf(es(1), es(1, gruppo = 2), es(1, gruppo = 2)))
        assertEquals(listOf(false, true, false), passi.map { it.attesaPrima })
    }
}
