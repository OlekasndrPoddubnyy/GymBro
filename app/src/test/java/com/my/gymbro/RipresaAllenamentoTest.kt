package com.my.gymbro

import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.Obiettivo
import com.my.gymbro.ui.AllenamentoSalvato
import com.my.gymbro.ui.EsercizioEsec
import com.my.gymbro.ui.Fase
import com.my.gymbro.ui.Ripartenza
import com.my.gymbro.ui.SerieEsec
import com.my.gymbro.ui.codificaRipresa
import com.my.gymbro.ui.decodificaRipresa
import com.my.gymbro.ui.ripartenza
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RipresaAllenamentoTest {

    private val completo = AllenamentoSalvato(
        schedaId = 3,
        sessioneId = 41,
        dataInizio = 1_764_000_000_000,
        nomeScheda = "Total body A \"martedì\"",
        esercizi = listOf(
            EsercizioEsec(
                esercizioId = 7,
                nome = "Panca bilanciere",
                chiaveSeed = "Panca piana",
                modalita = Modalita.RIPETIZIONI,
                serie = listOf(
                    SerieEsec(1, null, "8-10", 72.5f, 90),
                    SerieEsec(2, null, "8-10", 72.5f, 90),
                ),
                gruppo = 1,
                immaginePath = "asset://esercizi/panca_piana/0.jpg",
                descrizione = "Riga uno\nRiga due",
                note = "Scapole addotte",
                gruppoMuscolare = GruppoMuscolare.PETTO,
                obiettivo = Obiettivo(10, 72.5f),
                calibrazione = true,
            ),
            // Tutti i campi facoltativi vuoti: devono tornare null, non "null" o 0.
            EsercizioEsec(
                esercizioId = 9,
                nome = "Plank",
                modalita = Modalita.TEMPO,
                serie = listOf(SerieEsec(1, 45, null, null, 0)),
            ),
        ),
        pianoBuddy = mapOf(7L to (2 to Obiettivo(10, null))),
        indicePasso = 1,
        fase = Fase.RECUPERO,
        completati = setOf(0),
        saltati = setOf(2, 3),
        fineRecupero = 1_764_000_090_000,
        aggiornatoIl = 1_764_000_060_000,
    )

    @Test
    fun codificaEDecodifica_restituisconoLoStessoAllenamento() {
        assertEquals(completo, decodificaRipresa(codificaRipresa(completo)))
    }

    @Test
    fun senzaRecuperoNePianoBuddy() {
        val semplice = completo.copy(fase = Fase.ATTESA, fineRecupero = null, pianoBuddy = emptyMap(), saltati = emptySet())
        assertEquals(semplice, decodificaRipresa(codificaRipresa(semplice)))
    }

    @Test
    fun testoRovinato_null() {
        assertNull(decodificaRipresa(""))
        assertNull(decodificaRipresa("{non è json"))
        assertNull(decodificaRipresa(codificaRipresa(completo).replace("\"RECUPERO\"", "\"BOH\"")))
    }

    @Test
    fun formatoDiUnaVersioneDiversa_null() {
        assertNull(decodificaRipresa(codificaRipresa(completo).replace("\"formato\":1", "\"formato\":2")))
    }

    @Test
    fun recuperoNonFinito_continuaColTempoRimasto() {
        assertEquals(Ripartenza.Recupero(30), ripartenza(Fase.RECUPERO, Modalita.RIPETIZIONI, fineRecupero = 40_000, ora = 10_000))
        // Arrotonda per eccesso, come il conto alla rovescia.
        assertEquals(Ripartenza.Recupero(2), ripartenza(Fase.RECUPERO, Modalita.TEMPO, fineRecupero = 11_500, ora = 10_000))
    }

    @Test
    fun recuperoScaduto_serieARipetizioniSubito_serieATempoAspetta() {
        assertEquals(Ripartenza.Serie, ripartenza(Fase.RECUPERO, Modalita.RIPETIZIONI, fineRecupero = 9_000, ora = 10_000))
        assertEquals(Ripartenza.Attesa, ripartenza(Fase.RECUPERO, Modalita.TEMPO, fineRecupero = 9_000, ora = 10_000))
        // Meno di un secondo: non vale la pena far ripartire il conto.
        assertEquals(Ripartenza.Serie, ripartenza(Fase.RECUPERO, Modalita.RIPETIZIONI, fineRecupero = 10_500, ora = 10_000))
    }

    @Test
    fun serieInCorso_aRipetizioniSiRiprende_aTempoSiRifa() {
        assertEquals(Ripartenza.Serie, ripartenza(Fase.ESECUZIONE, Modalita.RIPETIZIONI, null, 0))
        assertEquals(Ripartenza.Attesa, ripartenza(Fase.ESECUZIONE, Modalita.TEMPO, null, 0))
    }

    @Test
    fun preparazioneEAttesa_aspettanoInizia() {
        assertEquals(Ripartenza.Attesa, ripartenza(Fase.PREPARAZIONE, Modalita.RIPETIZIONI, null, 0))
        assertEquals(Ripartenza.Attesa, ripartenza(Fase.ATTESA, Modalita.TEMPO, null, 0))
    }
}
