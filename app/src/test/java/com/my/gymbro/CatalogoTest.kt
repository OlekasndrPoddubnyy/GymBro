package com.my.gymbro

import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.allineaCatalogo
import com.my.gymbro.data.chiaviIniziali
import com.my.gymbro.data.eserciziPredefiniti
import com.my.gymbro.data.nomeCatalogo
import com.my.gymbro.data.nomeGiaUsato
import com.my.gymbro.data.perNomeCatalogo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoTest {

    private val panca = seed("Panca piana", "Spingi il bilanciere.")
    private val curl = seed("Curl con manubri", "Fletti i gomiti.", GruppoMuscolare.BICIPITI, Attrezzatura.MANUBRI)
    private val catalogo = listOf(panca, curl)

    private fun seed(
        nome: String,
        descrizione: String,
        gruppo: GruppoMuscolare = GruppoMuscolare.PETTO,
        attrezzo: Attrezzatura = Attrezzatura.BILANCIERE,
    ) = Esercizio(
        nome = nome, gruppoMuscolare = gruppo, attrezzatura = attrezzo,
        descrizione = descrizione, predefinito = true, chiaveSeed = nome,
    )

    @Test
    fun predefinitoRinominato_nonSiReinserisce() {
        val libreria = listOf(
            panca.copy(id = 1, nome = "Panca bilanciere"),
            curl.copy(id = 2),
        )
        val piano = allineaCatalogo(libreria, catalogo, emptySet(), emptySet())
        assertTrue(piano.daInserire.isEmpty())
        assertTrue(piano.daAggiornare.isEmpty())
    }

    @Test
    fun rinominato_tieneIlNomeDellUtente_maPrendeLaNuovaDescrizione() {
        val libreria = listOf(panca.copy(id = 1, nome = "Panca", descrizione = null), curl.copy(id = 2))
        val aggiornato = allineaCatalogo(libreria, catalogo, emptySet(), emptySet()).daAggiornare.single()
        assertEquals("Panca", aggiornato.nome)
        assertEquals("Spingi il bilanciere.", aggiornato.descrizione)
    }

    @Test
    fun mancante_siInserisceConLaChiave() {
        val piano = allineaCatalogo(listOf(curl.copy(id = 2)), catalogo, emptySet(), emptySet())
        val nuovo = piano.daInserire.single()
        assertEquals("Panca piana", nuovo.nome)
        assertEquals("Panca piana", nuovo.chiaveSeed)
    }

    @Test
    fun eliminatoDallUtente_nonTorna() {
        val piano = allineaCatalogo(listOf(curl.copy(id = 2)), catalogo, emptySet(), setOf("Panca piana"))
        assertTrue(piano.daInserire.isEmpty())
    }

    @Test
    fun esercizioDellUtenteConLoStessoNome_nonSiDuplica() {
        val mio = Esercizio(id = 7, nome = "panca PIANA ", gruppoMuscolare = GruppoMuscolare.PETTO, attrezzatura = Attrezzatura.MANUBRI)
        val piano = allineaCatalogo(listOf(mio, curl.copy(id = 2)), catalogo, emptySet(), emptySet())
        assertTrue(piano.daInserire.isEmpty())
        assertTrue(piano.daAggiornare.isEmpty()) // quello dell'utente non si tocca
    }

    @Test
    fun rinominatoPrimaDelleChiavi_siRiconosceDallaDescrizione() {
        // Predefinito senza chiave (la migrazione non l'ha riconosciuto dal nome).
        val orfano = panca.copy(id = 1, nome = "La mia panca", chiaveSeed = null)
        val piano = allineaCatalogo(listOf(orfano, curl.copy(id = 2)), catalogo, emptySet(), emptySet())
        assertTrue(piano.daInserire.isEmpty())
        val adottato = piano.daAggiornare.single()
        assertEquals(1L, adottato.id)
        assertEquals("La mia panca", adottato.nome)
        assertEquals("Panca piana", adottato.chiaveSeed)
    }

    @Test
    fun orfanoConDescrizioneCambiataDallUtente_nonSiAdotta() {
        val orfano = panca.copy(id = 1, nome = "La mia panca", chiaveSeed = null, descrizione = "Scritta da me")
        val piano = allineaCatalogo(listOf(orfano, curl.copy(id = 2)), catalogo, emptySet(), emptySet())
        assertEquals(listOf("Panca piana"), piano.daInserire.map { it.nome })
        assertTrue(piano.daAggiornare.isEmpty())
    }

    @Test
    fun dueOrfaniUguali_nonSiInserisceNiente() {
        val a = panca.copy(id = 1, nome = "Panca A", chiaveSeed = null)
        val b = panca.copy(id = 3, nome = "Panca B", chiaveSeed = null)
        val piano = allineaCatalogo(listOf(a, b, curl.copy(id = 2)), catalogo, emptySet(), emptySet())
        assertTrue(piano.daInserire.isEmpty())
        assertTrue(piano.daAggiornare.isEmpty())
    }

    @Test
    fun immagineDaAssets_soloSeLaCartellaEsiste() {
        val piano = allineaCatalogo(emptyList(), catalogo, setOf("panca_piana"), emptySet())
        val perNome = piano.daInserire.associateBy { it.nome }
        assertEquals("asset://esercizi/panca_piana/0.jpg", perNome.getValue("Panca piana").immaginePath)
        assertEquals(null, perNome.getValue("Curl con manubri").immaginePath)
    }

    @Test
    fun catalogoVero_suLibreriaVuota_tuttoInseritoConChiaviUniche() {
        val vero = eserciziPredefiniti()
        val piano = allineaCatalogo(emptyList(), vero, emptySet(), emptySet())
        assertEquals(vero.size, piano.daInserire.size)
        assertEquals(vero.size, piano.daInserire.mapNotNull { it.chiaveSeed }.toSet().size)
        // Secondo giro: già tutto allineato.
        val libreria = piano.daInserire.mapIndexed { i, e -> e.copy(id = i + 1L) }
        val dopo = allineaCatalogo(libreria, vero, emptySet(), emptySet())
        assertTrue(dopo.daInserire.isEmpty())
        assertTrue(dopo.daAggiornare.isEmpty())
    }

    @Test
    fun chiaviIniziali_soloNomiDelCatalogo_eUnaVoltaSola() {
        val chiavi = chiaviIniziali(
            listOf(5L to "Panca piana", 2L to "Panca piana", 3L to "Rinominata", 4L to "Curl con manubri"),
            setOf("Panca piana", "Curl con manubri"),
        )
        // Il doppione più vecchio (id più basso) prende la chiave.
        assertEquals(mapOf(2L to "Panca piana", 4L to "Curl con manubri"), chiavi)
    }

    @Test
    fun perNomeCatalogo_trovaIRinominati_eIlPredefinitoVince() {
        val rinominata = panca.copy(id = 1, nome = "Panca")
        val omonimo = Esercizio(id = 9, nome = "Panca piana", gruppoMuscolare = GruppoMuscolare.PETTO, attrezzatura = Attrezzatura.MANUBRI)
        val mio = Esercizio(id = 8, nome = "Il mio", gruppoMuscolare = GruppoMuscolare.CORE, attrezzatura = Attrezzatura.CORPO_LIBERO)
        val mappa = listOf(omonimo, rinominata, mio).perNomeCatalogo()
        assertEquals(1L, mappa.getValue("Panca piana").id)
        assertEquals(8L, mappa.getValue("Il mio").id)
        assertEquals("Panca piana", rinominata.nomeCatalogo)
        assertEquals("Il mio", mio.nomeCatalogo)
    }

    @Test
    fun nomeGiaUsato_senzaMaiuscoleNeSpazi_escludendoSeStesso() {
        val libreria = listOf(panca.copy(id = 1), curl.copy(id = 2))
        assertTrue(nomeGiaUsato("  panca PIANA", libreria))
        assertFalse(nomeGiaUsato("Panca piana", libreria, escludiId = 1)) // si sta modificando proprio lei
        assertTrue(nomeGiaUsato("curl con manubri", libreria, escludiId = 1))
        assertFalse(nomeGiaUsato("Panca inclinata", libreria))
    }
}
