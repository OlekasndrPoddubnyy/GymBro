package com.my.gymbro

import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.ChiaveBuddy
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.EsercizioScheda
import com.my.gymbro.data.EsercizioSchedaCompleto
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.ProgressoBuddy
import com.my.gymbro.data.Scheda
import com.my.gymbro.data.SchedaConEsercizi
import com.my.gymbro.data.SerieEseguita
import com.my.gymbro.data.SeriePianificata
import com.my.gymbro.data.Sessione
import com.my.gymbro.ui.AllenamentoSalvato
import com.my.gymbro.ui.AmbienteAllenamento
import com.my.gymbro.ui.Fase
import com.my.gymbro.ui.MotoreAllenamento
import com.my.gymbro.ui.Orologio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il motore dell'allenamento con tempo virtuale: un recupero di un minuto dura un istante, e i
 * passaggi (serie, recuperi, superset, salti) si controllano uno per uno.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotoreAllenamentoTest {

    // ---------- finti ----------

    private class OrologioFinto(private val tempo: TestCoroutineScheduler) : Orologio {
        override fun monotono() = tempo.currentTime
        override fun adesso() = INIZIO + tempo.currentTime
    }

    private class AmbienteFinto(private val scheda: SchedaConEsercizi) : AmbienteAllenamento {
        val salvate = mutableListOf<SerieEseguita>()
        val terminate = mutableListOf<Pair<Long, Long>>()
        val scartate = mutableListOf<Long>()
        var sessioni = mutableListOf<Sessione>()
        var ripresa: AllenamentoSalvato? = null
        var servizioAvviato = false
        /** Quanti salvataggi di serie devono fallire prima di riuscire. */
        var guasti = 0

        override suspend fun caricaSchedaCompleta(id: Long) = scheda
        override suspend fun avviaSessione(sessione: Sessione): Long {
            val id = sessioni.size + 1L
            sessioni += sessione.copy(id = id)
            return id
        }
        override suspend fun prefillEsercizio(esercizioId: Long, sessioneCorrente: Long) = emptyList<SerieEseguita>()
        override suspend fun registraSerie(serie: SerieEseguita) {
            if (guasti > 0) {
                guasti--
                throw IllegalStateException("database or disk is full")
            }
            salvate += serie
        }
        override suspend fun terminaSessione(id: Long, fine: Long) { terminate += id to fine }
        override suspend fun scartaSessioneVuota(id: Long) { scartate += id }
        override suspend fun sessione(id: Long) = sessioni.firstOrNull { it.id == id }
        override suspend fun serieDiSessione(id: Long) = salvate.filter { it.sessioneId == id }
        override suspend fun eserciziOra() = scheda.esercizi.map { it.esercizio }
        override fun progressoBuddy(chiave: ChiaveBuddy): ProgressoBuddy? = null
        override fun salvaBuddy(nuovi: Map<ChiaveBuddy, ProgressoBuddy>) = Unit
        override fun salvaRipresa(allenamento: AllenamentoSalvato) { ripresa = allenamento }
        override fun cancellaRipresa() { ripresa = null }
        override fun avviaServizio() { servizioAvviato = true }
        override fun togliAvvisoInterrotto() = Unit
    }

    // ---------- scheda di prova ----------

    private var ordine = 0

    private fun es(
        id: Long,
        serie: Int,
        recupero: Int,
        modalita: Modalita = Modalita.RIPETIZIONI,
        durata: Int? = null,
        superset: Int? = null,
    ): EsercizioSchedaCompleto {
        val esId = 100L + ordine
        return EsercizioSchedaCompleto(
            EsercizioScheda(id = esId, schedaId = 1, esercizioId = id, modalita = modalita, ordine = ordine++, gruppoSuperset = superset),
            Esercizio(id = id, nome = "Esercizio $id", gruppoMuscolare = GruppoMuscolare.PETTO, attrezzatura = Attrezzatura.MACCHINA),
            (1..serie).map { n ->
                SeriePianificata(
                    esercizioSchedaId = esId, numero = n, recuperoSec = recupero,
                    ripetizioni = if (modalita == Modalita.RIPETIZIONI) "10" else null,
                    pesoKg = if (modalita == Modalita.RIPETIZIONI) 50f else null,
                    durataSec = durata,
                )
            },
        )
    }

    private fun scheda(vararg esercizi: EsercizioSchedaCompleto) =
        SchedaConEsercizi(Scheda(id = 1, nome = "Prova", dataCreazione = 0), esercizi.toList())

    private fun TestScope.motore() = MotoreAllenamento(
        backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
        OrologioFinto(testScheduler),
    )

    /** Fa passare [secondi] di tempo virtuale (e quello che scade nel frattempo). */
    private fun TestScope.passa(secondi: Double) {
        advanceTimeBy((secondi * 1000).toLong())
        runCurrent()
    }

    /** Avvia e supera i 10 s di preparazione: si è nella prima serie. */
    private fun TestScope.avviato(amb: AmbienteFinto): MotoreAllenamento {
        val m = motore()
        m.avvia(amb, 1)
        passa(10.3)
        passa(1.0) // oltre la protezione dal doppio tocco
        return m
    }

    // ---------- prove ----------

    @Test
    fun avvio_preparazioneDi10SecondiPoiLaPrimaSerie() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 60)))
        val m = motore()
        m.avvia(amb, 1)
        assertEquals(Fase.PREPARAZIONE, m.stato.value.fase)
        assertEquals(10, m.stato.value.secondiRimanenti)
        assertTrue(amb.servizioAvviato)
        assertEquals(1, amb.sessioni.size)

        passa(10.3)
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals("10", m.stato.value.ripInput)
        assertEquals("50", m.stato.value.pesoInput)
    }

    @Test
    fun completa_registraLaSerie_recuperoPoiSerieSuccessiva() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 60)))
        val m = avviato(amb)

        m.completaSerie()
        val salvata = amb.salvate.single()
        assertEquals(1, salvata.numero)
        assertEquals(10, salvata.ripetizioni)
        assertEquals(50f, salvata.pesoKg)
        assertEquals(Fase.RECUPERO, m.stato.value.fase)
        assertEquals(60, m.stato.value.secondiRimanenti)

        passa(59.0)
        assertEquals(Fase.RECUPERO, m.stato.value.fase)
        assertEquals(1, m.stato.value.secondiRimanenti)
        passa(1.3)
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals(1, m.stato.value.indiceSerie)
    }

    @Test
    fun doppioTocco_ilSecondoNonConta() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 0)))
        val m = motore()
        m.avvia(amb, 1)
        passa(10.3)
        // Il tocco arriva subito dopo il passaggio automatico alla serie: era per la preparazione.
        m.completaSerie()
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertTrue(amb.salvate.isEmpty())
    }

    @Test
    fun saltaRecupero_laSeriePartesubito() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 90)))
        val m = avviato(amb)
        m.completaSerie()
        m.saltaRecupero()
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals(1, m.stato.value.indiceSerie)
    }

    @Test
    fun saltaSerie_nonRegistra_eNienteRecupero() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 60)))
        val m = avviato(amb)
        m.saltaSerie()
        assertTrue(amb.salvate.isEmpty())
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals(1, m.stato.value.indiceSerie)
        assertEquals(1, m.stato.value.passiFatti)
    }

    @Test
    fun esercizioNuovo_siAspettaInizia() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 1, recupero = 30), es(2, serie = 1, recupero = 30)))
        val m = avviato(amb)
        m.completaSerie()
        assertEquals(Fase.ATTESA, m.stato.value.fase)
        assertEquals(1, m.stato.value.indiceEsercizio)

        m.iniziaProssimo()
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
    }

    @Test
    fun superset_alternaGliEsercizi_recuperoSoloAFineGiro() = runTest {
        val amb = AmbienteFinto(
            scheda(es(1, serie = 2, recupero = 45, superset = 1), es(2, serie = 2, recupero = 45, superset = 1))
        )
        val m = avviato(amb)

        m.completaSerie() // A1: dentro al giro niente recupero, si passa a B
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals(1, m.stato.value.indiceEsercizio)
        assertEquals(0, m.stato.value.indiceSerie)

        passa(1.0)
        m.completaSerie() // B1: fine giro, recupero e poi di nuovo A
        assertEquals(Fase.RECUPERO, m.stato.value.fase)
        assertEquals(45, m.stato.value.secondiRimanenti)
        assertEquals(0, m.stato.value.indiceEsercizio)
        assertEquals(1, m.stato.value.indiceSerie)

        passa(45.3)
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals(listOf(1L, 2L), amb.salvate.map { it.esercizioId })
    }

    @Test
    fun saltaEsercizio_restaAlBloccoSuccessivo() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 3, recupero = 60), es(2, serie = 1, recupero = 60)))
        val m = avviato(amb)
        m.saltaEsercizio()
        assertEquals(Fase.ATTESA, m.stato.value.fase)
        assertEquals(1, m.stato.value.indiceEsercizio)
        assertEquals(3, m.stato.value.passiFatti)
        assertEquals(setOf(0), m.stato.value.eserciziFatti)
    }

    @Test
    fun vaiAEsercizio_eQuelliLasciatiIndietroTornanoAllaFine() = runTest {
        val amb = AmbienteFinto(
            scheda(es(1, serie = 1, recupero = 0), es(2, serie = 1, recupero = 0), es(3, serie = 1, recupero = 0))
        )
        val m = avviato(amb)
        m.vaiAEsercizio(2)
        assertEquals(Fase.ATTESA, m.stato.value.fase)
        assertEquals(2, m.stato.value.indiceEsercizio)

        m.iniziaProssimo()
        passa(1.0)
        m.completaSerie()
        // Finito l'ultimo, si torna al primo rimasto da fare.
        assertEquals(Fase.ATTESA, m.stato.value.fase)
        assertEquals(0, m.stato.value.indiceEsercizio)
    }

    @Test
    fun vaiAEsercizioDuranteIlRecupero_ilRecuperoContinua() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 60), es(2, serie = 1, recupero = 0)))
        val m = avviato(amb)
        m.completaSerie()
        passa(20.0)
        m.vaiAEsercizio(1)
        assertEquals(Fase.RECUPERO, m.stato.value.fase)
        assertEquals(40, m.stato.value.secondiRimanenti)
        assertEquals(1, m.stato.value.indiceEsercizio)

        passa(40.3)
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals(1, m.stato.value.indiceEsercizio)
    }

    @Test
    fun serieATempo_contaESiRegistraDaSola() = runTest {
        val amb = AmbienteFinto(
            scheda(es(1, serie = 1, recupero = 0, modalita = Modalita.TEMPO, durata = 30), es(2, serie = 1, recupero = 0))
        )
        val m = motore()
        m.avvia(amb, 1)
        passa(10.3)
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase)
        assertEquals(30, m.stato.value.secondiRimanenti)

        passa(30.3)
        assertEquals(30, amb.salvate.single().durataSec)
        assertNull(amb.salvate.single().ripetizioni)
        assertEquals(Fase.ATTESA, m.stato.value.fase)
    }

    @Test
    fun ultimaSerie_terminaESalvaLaFine() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 1, recupero = 60)))
        val m = avviato(amb)
        assertNotNull(amb.ripresa) // in corso: c'è da riprendere se Android chiude l'app
        m.completaSerie()
        assertEquals(Fase.COMPLETATO, m.stato.value.fase)
        assertEquals(listOf(1L to INIZIO + testScheduler.currentTime), amb.terminate)
        assertNull(amb.ripresa)
        assertTrue(amb.scartate.isEmpty())
    }

    @Test
    fun terminaSenzaSerie_laSessioneVuotaSiScarta() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 60)))
        val m = avviato(amb)
        m.terminaOra()
        assertEquals(Fase.COMPLETATO, m.stato.value.fase)
        assertEquals(listOf(1L), amb.scartate)
        assertTrue(amb.terminate.isEmpty())
    }

    @Test
    fun salvataggioFallito_siRiprovaConLaSerieDopo() = runTest {
        val amb = AmbienteFinto(scheda(es(1, serie = 2, recupero = 0)))
        amb.guasti = 1
        val m = avviato(amb)

        m.completaSerie()
        assertTrue(amb.salvate.isEmpty())
        assertEquals("Serie non salvata", m.avviso.value?.titolo)
        assertEquals(1, m.stato.value.log.size) // nel riepilogo c'è
        assertEquals(Fase.ESECUZIONE, m.stato.value.fase) // e si continua

        passa(1.0)
        m.completaSerie()
        assertEquals(listOf(1, 2), amb.salvate.map { it.numero })
    }

    @Test
    fun ripresaNelRecupero_continuaColTempoRimasto() = runTest {
        val scheda = scheda(es(1, serie = 2, recupero = 60))
        val amb = AmbienteFinto(scheda)
        val m = avviato(amb)
        m.completaSerie()
        passa(15.0)
        val salvato = assertNotNull(amb.ripresa)

        // Android chiude l'app: un motore nuovo riparte dal salvataggio, 5 s dopo.
        passa(5.0)
        val nuovo = motore()
        nuovo.riprendi(amb, salvato.copy(aggiornatoIl = INIZIO + testScheduler.currentTime), terminaSubito = false)
        assertEquals(Fase.RECUPERO, nuovo.stato.value.fase)
        assertEquals(40, nuovo.stato.value.secondiRimanenti)
        assertEquals(1, nuovo.stato.value.log.size)
        assertEquals(1, nuovo.stato.value.indiceSerie)
    }

    private fun <T> assertNotNull(valore: T?): T {
        org.junit.Assert.assertNotNull(valore)
        return valore!!
    }

    private companion object {
        const val INIZIO = 1_764_000_000_000L
    }
}
