package com.my.gymbro

import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.RigaReport
import com.my.gymbro.data.Sessione
import com.my.gymbro.ui.MetricaTrend
import com.my.gymbro.ui.PeriodoReport
import com.my.gymbro.ui.calcola
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCalcoloTest {

    private val ora = System.currentTimeMillis()
    private val ieri = ora - 24 * 60 * 60 * 1000L

    private fun riga(sessioneId: Long, data: Long) = RigaReport(
        sessioneId = sessioneId,
        dataInizio = data,
        esercizioId = 1,
        nomeEsercizio = "Panca piana",
        gruppoMuscolare = GruppoMuscolare.entries.first(),
        numero = 1,
        ripetizioni = 10,
        pesoKg = 50f,
        durataSec = null,
    )

    private fun calcolaTutto(righe: List<RigaReport>, sessioni: List<Sessione>) =
        calcola(righe, sessioni, sel = null, periodo = PeriodoReport.TUTTO, metrica = MetricaTrend.PESO)

    @Test
    fun sessioniSenzaSerie_nonContano() {
        val sessioni = listOf(
            Sessione(id = 1, nomeScheda = "A", dataInizio = ieri, dataFine = ieri + 3_600_000),
            Sessione(id = 2, nomeScheda = "A", dataInizio = ora, dataFine = ora + 1000), // aperta e chiusa subito
            Sessione(id = 3, nomeScheda = "B", dataInizio = ora, dataFine = null),       // abbandonata
        )
        val stato = calcolaTutto(listOf(riga(1, ieri)), sessioni)

        assertEquals(1, stato.sessioniTotali)
        assertEquals(1, stato.numeroSessioni)
        assertEquals(1, stato.giorniAllenati)
        assertEquals(listOf(1L), stato.storico.map { it.id })
        assertEquals(60L, stato.durataMediaMin)
    }

    @Test
    fun soloSessioniVuote_reportVuoto() {
        val stato = calcolaTutto(emptyList(), listOf(Sessione(id = 1, nomeScheda = "A", dataInizio = ora)))
        assertEquals(0, stato.sessioniTotali)
        assertEquals(0, stato.allenamentiSettimana)
        assertEquals(0, stato.settimaneConsecutive)
    }
}
