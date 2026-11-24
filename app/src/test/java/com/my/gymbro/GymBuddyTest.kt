package com.my.gymbro

import com.my.gymbro.data.Obiettivo
import com.my.gymbro.data.Precedente
import com.my.gymbro.data.ProgressoBuddy
import com.my.gymbro.data.Proposta
import com.my.gymbro.data.RegoleBuddy
import com.my.gymbro.data.SerieFatta
import com.my.gymbro.data.StatoBuddy
import com.my.gymbro.data.accetta
import com.my.gymbro.data.codificaProgresso
import com.my.gymbro.data.decodificaProgresso
import com.my.gymbro.data.dopoAllenamento
import com.my.gymbro.data.dopoSessione
import com.my.gymbro.data.rifiuta
import com.my.gymbro.data.rispondi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GymBuddyTest {

    private val regole = RegoleBuddy()

    private fun StatoBuddy.fai(vararg rip: Int, r: RegoleBuddy = regole, kg: Float? = obiettivo.caricoKg) =
        dopoSessione(r, 3, rip.map { SerieFatta(it, kg) })

    @Test
    fun tabellaDelDocumento() {
        var s = StatoBuddy(Obiettivo(8, 10f)).fai(8, 8, 8)
        assertNull(s.proposta)
        s = s.fai(8, 8, 8)
        assertEquals(Proposta.PiuRipetizioni(Obiettivo(10, 10f)), s.proposta)

        s = s.accetta().fai(10, 10, 9)
        assertEquals(0, s.completeDiFila)
        assertNull(s.proposta)
        s = s.fai(10, 10, 10)
        assertNull(s.proposta)
        s = s.fai(10, 10, 10)
        assertEquals(Proposta.PiuRipetizioni(Obiettivo(12, 10f)), s.proposta)

        s = s.accetta().fai(12, 12, 12)
        assertNull(s.proposta)
        s = s.fai(12, 12, 12)
        assertEquals(Proposta.PiuPeso(Obiettivo(8, 12.5f)), s.proposta)
        assertEquals(Obiettivo(8, 12.5f), s.accetta().obiettivo)
    }

    @Test
    fun senzaConferma_obiettivoInvariato() {
        val proposta = StatoBuddy(Obiettivo(8, 10f)).fai(8, 8, 8).fai(8, 8, 8)
        assertEquals(Obiettivo(8, 10f), proposta.obiettivo)
        assertEquals(StatoBuddy(Obiettivo(8, 10f)), proposta.rifiuta())
    }

    @Test
    fun serieSaltata_sessioneIncompleta() {
        val s = StatoBuddy(Obiettivo(10, 10f), completeDiFila = 1).fai(10, 10)
        assertEquals(0, s.completeDiFila)
        assertEquals(1, s.incompleteDiFila)
    }

    @Test
    fun esercizioNonSvolto_nonCambiaNulla() {
        val s = StatoBuddy(Obiettivo(10, 10f), completeDiFila = 1)
        assertEquals(s, s.fai())
    }

    @Test
    fun treIncomplete_propongonoScarico() {
        val s = StatoBuddy(Obiettivo(10, 50f)).fai(10, 10, 9).fai(10, 9, 9).fai(9, 9, 9)
        assertEquals(Proposta.Scarico(Obiettivo(8, 45f)), s.proposta)
    }

    @Test
    fun oltreObiettivo_doppiaConfermaSoloConProgressioneRapida() {
        val inizio = StatoBuddy(Obiettivo(10, 10f))
        assertEquals(1, inizio.fai(12, 12, 12).completeDiFila)
        assertEquals(2, inizio.fai(12, 12, 12, r = regole.copy(progressioneRapida = true)).completeDiFila)
    }

    @Test
    fun calibrazione_correggeIlCaricoSenzaFallire() {
        val s = StatoBuddy(Obiettivo(8, 20f), calibrazione = true).fai(6, 8, 8, kg = 16f)
        assertEquals(0, s.incompleteDiFila)
        assertEquals(Proposta.CorreggiCarico(Obiettivo(8, 16f)), s.proposta)
    }

    @Test
    fun senzaCarico_progredisceARipetizioni_ePoiPrendeIlPesoUsato() {
        val s = StatoBuddy(Obiettivo(8, null), calibrazione = true).fai(8, 8, 8)
        assertEquals(1, s.completeDiFila)
        assertEquals(Proposta.PiuRipetizioni(Obiettivo(10, null)), s.fai(8, 8, 8).proposta)
        assertEquals(Proposta.CorreggiCarico(Obiettivo(8, 5f)), s.fai(8, 8, 8, kg = 5f).proposta)
    }

    @Test
    fun ultimoPasso_arrivaAlMassimo() {
        val r = regole.copy(ripMin = 12, ripMax = 15, conferme = 1)
        val a14 = StatoBuddy(Obiettivo(12, 20f)).fai(12, 12, 12, r = r).accetta()
        assertEquals(14, a14.obiettivo.ripetizioni)
        assertEquals(15, a14.fai(14, 14, 14, r = r).accetta().obiettivo.ripetizioni)
    }

    @Test
    fun menoPesoDellObiettivo_sessioneIncompleta() {
        val s = StatoBuddy(Obiettivo(10, 20f)).fai(10, 10, 10, kg = 17.5f)
        assertEquals(0, s.completeDiFila)
        assertEquals(1, s.incompleteDiFila)
    }

    @Test
    fun scaricoAccettato_azzeraLoStallo() {
        var p = ProgressoBuddy(attivo = true, stato = StatoBuddy(Obiettivo(10, 50f)))
        repeat(3) { p = p.dopoAllenamento(3, List(3) { SerieFatta(7, 50f) }) }
        assertEquals(3, p.sessioniSenzaAumento)
        assertEquals(0, p.rispondi(accettata = true).sessioniSenzaAumento)
    }

    @Test
    fun salvataggio_rileggeTuttoUguale() {
        val p = ProgressoBuddy(
            attivo = true,
            regole = regole.copy(incrementoKg = null, progressioneRapida = true),
            stato = StatoBuddy(Obiettivo(10, 12.5f), 1, 2, true, Proposta.Scarico(Obiettivo(8, 10f))),
            sessioniSenzaAumento = 3,
            inSchedaDal = 123L,
            rimandatoFino = 456L,
            ignorato = true,
            precedente = Precedente(7, listOf("riga 1\nriga 2 = 100% è", null), listOf(40f, null)),
            ultimaProposta = "scarico",
            ultimaAccettata = false,
        )
        assertEquals(p, decodificaProgresso(codificaProgresso(p)))
    }

    @Test
    fun corpoLiberoAFineRange_proponeVariante() {
        val r = regole.copy(incrementoKg = null)
        val s = StatoBuddy(Obiettivo(12, null)).fai(12, 12, 12, r = r).fai(12, 12, 12, r = r)
        assertEquals(Proposta.VariantePiuDifficile, s.proposta)
    }
}
