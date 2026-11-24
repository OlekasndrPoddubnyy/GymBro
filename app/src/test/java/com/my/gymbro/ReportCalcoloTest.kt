package com.my.gymbro

import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.RigaReport
import com.my.gymbro.data.SerieEseguita
import com.my.gymbro.data.Sessione
import com.my.gymbro.ui.MetricaTrend
import com.my.gymbro.ui.PeriodoReport
import com.my.gymbro.ui.calcola
import com.my.gymbro.ui.confrontaConStorico
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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

    private val lunedi = LocalDate.of(2026, 11, 23)

    private fun mezzogiorno(giorno: LocalDate) =
        giorno.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun streakDiLunedi(vararg giorniFa: Long): Int {
        val date = giorniFa.map { mezzogiorno(lunedi.minusDays(it)) }
        val sessioni = date.mapIndexed { i, d -> Sessione(id = i + 1L, nomeScheda = "A", dataInizio = d) }
        val righe = date.mapIndexed { i, d -> riga(i + 1L, d) }
        return calcola(righe, sessioni, null, PeriodoReport.TUTTO, MetricaTrend.PESO, oggi = lunedi)
            .settimaneConsecutive
    }

    @Test
    fun settimanaInCorsoAncoraVuota_nonAzzeraLeSettimaneDiFila() {
        assertEquals(2, streakDiLunedi(3, 10))
    }

    @Test
    fun settimanaInCorsoGiaAllenata_conta() {
        assertEquals(3, streakDiLunedi(0, 3, 10))
    }

    @Test
    fun unaSettimanaInteraSaltata_azzera() {
        assertEquals(0, streakDiLunedi(10, 17))
    }

    @Test
    fun macchinaAssistita_recordConMenoAiuto_senzaVolume() {
        val assistita = riga(1, ieri).copy(nomeEsercizio = "Trazioni assistite alla macchina", pesoKg = 30f)
        val adesso = SerieEseguita(sessioneId = 2, esercizioId = 1, numero = 1, ripetizioni = 10, pesoKg = 25f)
        val confronto = confrontaConStorico(listOf(adesso), listOf(assistita)).getValue(1)
        assertEquals(true, confronto.record)
        assertEquals(null, confronto.deltaVolume)

        val stato = calcolaTutto(listOf(assistita), listOf(Sessione(id = 1, nomeScheda = "A", dataInizio = ieri)))
        assertEquals(0.0, stato.volumeTotale, 0.0)
        assertEquals(null, stato.pr.single().pesoMax)
    }

    @Test
    fun riepilogo_aTempo_recordSeTenutoPiuALungo_maiPerLoStretching() {
        val plank = riga(1, ieri).copy(nomeEsercizio = "Plank", ripetizioni = null, pesoKg = null, durataSec = 35)
        val adesso = SerieEseguita(sessioneId = 2, esercizioId = 1, numero = 1, durataSec = 40)
        assertEquals(true, confrontaConStorico(listOf(adesso), listOf(plank)).getValue(1).record)
        assertEquals(false, confrontaConStorico(listOf(adesso.copy(durataSec = 35)), listOf(plank)).getValue(1).record)

        val stretching = plank.copy(gruppoMuscolare = GruppoMuscolare.STRETCHING, durataSec = 30)
        assertEquals(false, confrontaConStorico(listOf(adesso), listOf(stretching)).getValue(1).record)
    }

    @Test
    fun eserciziOmonimi_hannoRecordConIdDiversi() {
        val righe = listOf(riga(1, ieri), riga(1, ieri).copy(esercizioId = 2))
        val stato = calcolaTutto(righe, listOf(Sessione(id = 1, nomeScheda = "A", dataInizio = ieri)))
        assertEquals(setOf(1L, 2L), stato.pr.map { it.id }.toSet())
    }
}
