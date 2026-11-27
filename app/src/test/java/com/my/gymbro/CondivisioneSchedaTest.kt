package com.my.gymbro

import com.google.zxing.PlanarYUVLuminanceSource
import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.EsercizioCondiviso
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.QrNonValido
import com.my.gymbro.data.QrPronto
import com.my.gymbro.data.SchedaCondivisa
import com.my.gymbro.data.SerieCondivisa
import com.my.gymbro.data.base45
import com.my.gymbro.data.codificaSchedaCondivisa
import com.my.gymbro.data.daBase45
import com.my.gymbro.data.decodificaSchedaCondivisa
import com.my.gymbro.data.eserciziNuovi
import com.my.gymbro.data.eserciziPredefiniti
import com.my.gymbro.data.leggiQr
import com.my.gymbro.data.nomeLibero
import com.my.gymbro.data.qrPerScheda
import com.my.gymbro.data.schedePredefinite
import com.my.gymbro.data.senzaNote
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.Deflater
import kotlin.random.Random

class CondivisioneSchedaTest {

    private val scheda = SchedaCondivisa(
        nome = "Spinta — Lunedì",
        note = "Riscaldamento 5 min. \"Lento\" in discesa, 100% è.",
        esercizi = listOf(
            EsercizioCondiviso(
                "Panca piana", GruppoMuscolare.PETTO, Attrezzatura.BILANCIERE, Modalita.RIPETIZIONI,
                "Scapole indietro", null,
                listOf(SerieCondivisa("8-12", 7.3f, null, 90), SerieCondivisa("8-12", 40f, null, 120)),
            ),
            EsercizioCondiviso(
                "Curl con manubri", GruppoMuscolare.BICIPITI, Attrezzatura.MANUBRI, Modalita.RIPETIZIONI,
                null, 1, listOf(SerieCondivisa("10", 6f, null, 0)),
            ),
            EsercizioCondiviso(
                "Il mio esercizio", GruppoMuscolare.CORE, Attrezzatura.CORPO_LIBERO, Modalita.TEMPO,
                null, 1, listOf(SerieCondivisa(null, null, 35, 45)),
            ),
        ),
    )

    @Test
    fun andataERitorno_tuttoUguale() {
        assertEquals(scheda, decodificaSchedaCondivisa(codificaSchedaCondivisa(scheda)))
    }

    @Test
    fun testoDelQr_soloCaratteriAlfanumericiDelQr() {
        // Così il QR usa la modalità alfanumerica, più densa di quella a byte.
        val ammessi = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:".toSet()
        assertTrue(codificaSchedaCondivisa(scheda).all { it in ammessi })
    }

    @Test
    fun base45_esempiDellaRfc() {
        assertEquals("BB8", base45("AB".toByteArray()))
        assertEquals("%69 VD92EX0", base45("Hello!!".toByteArray()))
        assertEquals("QED8WEX0", base45("ietf!".toByteArray()))
        val casuali = Random(7).nextBytes(301)
        assertArrayEquals(casuali, daBase45(base45(casuali)))
    }

    @Test
    fun qrDiAltreApp_rifiutato() {
        for (testo in listOf("https://esempio.it", "", "gymbro1:abc", "GYMBRO1:", "GYMBRO1:%%%", "GYMBRO1:ab")) {
            assertThrows(testo, QrNonValido::class.java) { decodificaSchedaCondivisa(testo) }
        }
    }

    @Test
    fun versionePiuNuova_chiedeDiAggiornare() {
        val e = assertThrows(QrNonValido::class.java) { decodificaSchedaCondivisa("GYMBRO2:ABC") }
        assertTrue(e.message!!.contains("aggiorna"))
    }

    @Test
    fun pochiByteCheDiventanoEnormi_rifiutati() {
        val enorme = "{\"n\":\"" + " ".repeat(200_000) + "\"}"
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(enorme.toByteArray())
        d.finish()
        val buf = ByteArray(4096)
        val compressi = buf.copyOf(d.deflate(buf))
        d.end()
        assertThrows(QrNonValido::class.java) { decodificaSchedaCondivisa("GYMBRO1:" + base45(compressi)) }
    }

    @Test
    fun valoriAssurdi_ripuliti() {
        val strana = scheda.copy(
            nome = "  " + "x".repeat(300),
            esercizi = listOf(scheda.esercizi[0].copy(serie = listOf(SerieCondivisa("  ", 5000f, -3, 99_999)))),
        )
        val letta = decodificaSchedaCondivisa(codificaSchedaCondivisa(strana))
        assertEquals(80, letta.nome.length)
        assertEquals(SerieCondivisa(null, null, null, 3600), letta.esercizi.single().serie.single())
    }

    @Test
    fun schedaSenzaEsercizi_rifiutata() {
        assertThrows(QrNonValido::class.java) {
            decodificaSchedaCondivisa(codificaSchedaCondivisa(scheda.copy(esercizi = emptyList())))
        }
    }

    /** Disegna il QR come farebbe uno schermo e lo rilegge come farebbe la fotocamera. */
    private fun rileggi(qr: QrPronto, pxPerModulo: Int = 3): String? {
        val lato = qr.lato * pxPerModulo
        val luma = ByteArray(lato * lato) { i ->
            val x = i % lato / pxPerModulo
            val y = i / lato / pxPerModulo
            (if (qr.scuro[y * qr.lato + x]) 0 else 255).toByte()
        }
        return leggiQr(PlanarYUVLuminanceSource(luma, lato, lato, 0, 0, lato, lato, false))
    }

    @Test
    fun qr_siRilegge() {
        val qr = qrPerScheda(scheda)
        assertNotNull(qr)
        assertEquals(false, qr!!.noteTolte)
        assertEquals(scheda, decodificaSchedaCondivisa(rileggi(qr)!!))
    }

    @Test
    fun schedePredefinite_stannoInUnQrESiRileggono() {
        val catalogo = eserciziPredefiniti().associateBy { it.nome }
        for (t in schedePredefinite()) {
            var gruppo = 0
            var aperto: Int? = null
            val s = SchedaCondivisa(
                nome = t.nome,
                note = t.note.ifBlank { null },
                esercizi = t.esercizi.map { et ->
                    val g = aperto ?: if (et.supersetConProssimo) ++gruppo else null
                    aperto = if (et.supersetConProssimo) g else null
                    val e = catalogo.getValue(et.nomeEsercizio)
                    EsercizioCondiviso(
                        et.nomeEsercizio, e.gruppoMuscolare, e.attrezzatura, et.modalita, et.note.ifBlank { null }, g,
                        et.serie.map { SerieCondivisa(it.ripetizioni, it.pesoKg, it.durataSec, it.recuperoSec) },
                    )
                },
            )
            val qr = qrPerScheda(s)
            assertNotNull(t.nome, qr)
            val atteso = if (qr!!.noteTolte) s.senzaNote() else s
            assertEquals(t.nome, atteso, decodificaSchedaCondivisa(rileggi(qr)!!))
        }
    }

    @Test
    fun noteTroppoLunghe_siTolgono_schedaEnorme_nessunQr() {
        val r = Random(3)
        fun testo(n: Int) = String(CharArray(n) { "abcdefghijklmnopqrstuvwxyz ".random(r) })
        val lunghe = scheda.copy(note = testo(2000), esercizi = List(20) { i ->
            scheda.esercizi[0].copy(nome = "Esercizio $i", note = testo(400))
        })
        val qr = qrPerScheda(lunghe)
        assertNotNull(qr)
        assertEquals(true, qr!!.noteTolte)

        val enorme = scheda.copy(note = null, esercizi = List(60) { i ->
            scheda.esercizi[0].copy(
                nome = "Esercizio ${testo(30)} $i",
                note = null,
                serie = List(30) { SerieCondivisa("${r.nextInt(100)}", r.nextInt(1000) / 4f, null, r.nextInt(300)) },
            )
        })
        assertNull(qrPerScheda(enorme))
    }

    @Test
    fun nomeGiaUsato_diventaCondivisa() {
        assertEquals("A", nomeLibero("A", setOf("B")))
        assertEquals("A (condivisa)", nomeLibero("A", setOf("A")))
        assertEquals("A (condivisa 2)", nomeLibero("A", setOf("A", "A (condivisa)")))
    }

    @Test
    fun eserciziNuovi_confrontoSenzaMaiuscole() {
        val libreria = listOf(es("panca piana"), es("Curl con manubri"))
        assertEquals(setOf("Il mio esercizio"), eserciziNuovi(scheda, libreria))
    }

    @Test
    fun eserciziNuovi_predefinitoRinominatoDaChiRiceve_siRitrovaPerChiave() {
        // Chi riceve ha rinominato "Panca piana": non va aggiunta di nuovo.
        val libreria = listOf(es("Panca", chiave = "Panca piana"), es("Curl con manubri"))
        assertEquals(setOf("Il mio esercizio"), eserciziNuovi(scheda, libreria))
    }

    @Test
    fun eserciziNuovi_rinominatoDaChiInvia_siRitrovaPerChiave() {
        val rinominata = scheda.copy(esercizi = scheda.esercizi.map {
            if (it.nome == "Panca piana") it.copy(nome = "Panca bilanciere", chiave = "Panca piana") else it
        })
        val libreria = listOf(es("Panca piana", chiave = "Panca piana"), es("Curl con manubri"), es("Il mio esercizio"))
        assertEquals(emptySet<String>(), eserciziNuovi(rinominata, libreria))
    }

    @Test
    fun chiave_viaggiaNelQr_eManca_SeNonServe() {
        val rinominata = scheda.copy(esercizi = scheda.esercizi.mapIndexed { i, e ->
            if (i == 0) e.copy(nome = "Panca bilanciere", chiave = "Panca piana") else e
        })
        assertEquals(rinominata, decodificaSchedaCondivisa(codificaSchedaCondivisa(rinominata)))
        // Senza esercizi rinominati il QR resta quello di prima: nessuna chiave in più.
        assertTrue(decodificaSchedaCondivisa(codificaSchedaCondivisa(scheda)).esercizi.all { it.chiave == null })
    }

    private fun es(nome: String, chiave: String? = null) = Esercizio(
        nome = nome,
        gruppoMuscolare = GruppoMuscolare.PETTO,
        attrezzatura = Attrezzatura.BILANCIERE,
        predefinito = chiave != null,
        chiaveSeed = chiave,
    )
}
