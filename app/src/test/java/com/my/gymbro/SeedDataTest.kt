package com.my.gymbro

import com.my.gymbro.data.descrizioneSenzaMacchina
import com.my.gymbro.data.eserciziPredefiniti
import com.my.gymbro.data.macchinaTechnogym
import com.my.gymbro.data.schedePredefinite
import com.my.gymbro.data.slugEsercizio
import com.my.gymbro.ui.fileAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeedDataTest {

    private val seed = eserciziPredefiniti()

    @Test
    fun nomiUnici() {
        // Il seeding deduplica per nome: due voci con lo stesso nome finirebbero entrambe nel DB.
        val doppi = seed.groupBy { it.nome.lowercase() }.filterValues { it.size > 1 }.keys
        assertTrue("Nomi duplicati: $doppi", doppi.isEmpty())
    }

    @Test
    fun slugUnici() {
        val doppi = seed.groupBy { slugEsercizio(it.nome) }.filterValues { it.size > 1 }.keys
        assertTrue("Slug duplicati: $doppi", doppi.isEmpty())
    }

    @Test
    fun immaginiRiusateEsistono() {
        val cartella = listOf("src/main/assets/esercizi", "app/src/main/assets/esercizi")
            .map(::File).firstOrNull { it.isDirectory } ?: return
        val mancanti = seed.mapNotNull { it.immaginePath }
            .filterNot { File(cartella, fileAsset("$it/0.jpg")).isFile }
        assertTrue("Cartelle immagini inesistenti: $mancanti", mancanti.isEmpty())
    }

    @Test
    fun immaginiPredefinite_dueFasiWebp() {
        val cartella = listOf("src/main/assets/esercizi", "app/src/main/assets/esercizi")
            .map(::File).firstOrNull { it.isDirectory } ?: return
        val incomplete = cartella.listFiles()!!.filter { it.isDirectory }
            .filterNot { File(it, "0.webp").isFile && File(it, "1.webp").isFile }
            .map { it.name }
        assertTrue("Cartelle senza 0.webp/1.webp: $incomplete", incomplete.isEmpty())
        assertEquals("esercizi/x/1.webp", fileAsset("esercizi/x/1.jpg"))
        assertEquals("esercizi/x/1.webp", fileAsset("esercizi/x/1.webp"))
    }

    @Test
    fun schedePredefinite_esistonoInCatalogoESenzaDoppioni() {
        val nomi = seed.map { it.nome }.toSet()
        for (scheda in schedePredefinite()) {
            val usati = scheda.esercizi.map { it.nomeEsercizio }
            assertEquals("${scheda.nome}: non in catalogo", emptyList<String>(), usati.filterNot { it in nomi })
            assertEquals("${scheda.nome}: esercizi ripetuti", usati.distinct(), usati)
        }
    }

    @Test
    fun descrizioniPresenti() {
        val senza = seed.filter { descrizioneSenzaMacchina(it.descrizione).isNullOrBlank() }.map { it.nome }
        assertTrue("Esercizi senza descrizione: $senza", senza.isEmpty())
    }

    @Test
    fun rigaTechnogym() {
        assertEquals("Chest Press", macchinaTechnogym("Technogym: Chest Press\nSpingi."))
        assertEquals("Spingi.", descrizioneSenzaMacchina("Technogym: Chest Press\nSpingi."))
        assertNull(macchinaTechnogym("Spingi."))
        assertEquals("Spingi.", descrizioneSenzaMacchina("Spingi."))
    }
}
