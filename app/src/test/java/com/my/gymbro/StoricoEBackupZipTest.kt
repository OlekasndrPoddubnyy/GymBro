package com.my.gymbro

import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.BackupNonValido
import com.my.gymbro.data.DatiBackup
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.conImmaginiIn
import com.my.gymbro.data.eZip
import com.my.gymbro.data.immaginiDaSalvare
import com.my.gymbro.data.leggiBackupZip
import com.my.gymbro.data.scriviBackupZip
import com.my.gymbro.ui.CorrezioneSerie
import com.my.gymbro.ui.correzioneDa
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StoricoEBackupZipTest {

    @get:Rule
    val cartelle = TemporaryFolder()

    // ---------- correzione di una serie dello storico ----------

    @Test
    fun correzione_pesoConVirgolaOPunto_eVuotoPerCorpoLibero() {
        assertEquals(CorrezioneSerie(8, 80f, null), correzioneDa(false, "8", "80", ""))
        assertEquals(CorrezioneSerie(8, 82.5f, null), correzioneDa(false, " 8 ", "82,5", ""))
        assertEquals(CorrezioneSerie(12, null, null), correzioneDa(false, "12", "", ""))
    }

    @Test
    fun correzione_valoriImpossibili_null() {
        assertNull(correzioneDa(false, "", "80", ""))      // ripetizioni mancanti
        assertNull(correzioneDa(false, "8", "1200", ""))   // oltre i 1000 kg
        assertNull(correzioneDa(false, "8", "8,0,0", ""))  // non è un numero
        assertNull(correzioneDa(false, "1000", "80", ""))  // oltre le 999 ripetizioni
    }

    @Test
    fun correzione_serieATempo_soloLaDurata() {
        assertEquals(CorrezioneSerie(null, null, 45), correzioneDa(true, "10", "80", "45"))
        assertNull(correzioneDa(true, "", "", "0"))
        assertNull(correzioneDa(true, "", "", ""))
    }

    // ---------- backup ZIP con le foto ----------

    private fun esercizio(id: Long, immagine: String?) = Esercizio(
        id = id, nome = "E$id", gruppoMuscolare = GruppoMuscolare.PETTO,
        attrezzatura = Attrezzatura.MANUBRI, immaginePath = immagine,
    )

    @Test
    fun zip_andataERitorno_jsonEFoto() {
        val foto = cartelle.newFolder("foto")
        val a = File(foto, "a1b2.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val esercizi = listOf(
            esercizio(1, a.absolutePath),
            esercizio(2, "asset://esercizi/panca_piana/0.jpg"), // inclusa nell'app: non serve
            esercizio(3, File(foto, "sparita.jpg").absolutePath), // file non più presente
            esercizio(4, null),
        )
        val immagini = immaginiDaSalvare(esercizi)
        assertEquals(listOf(a), immagini)

        val uscita = ByteArrayOutputStream()
        scriviBackupZip(uscita, """{"app":"GymBro"}""", immagini)
        assertTrue(eZip(uscita.toByteArray().copyOf(4)))

        val letto = leggiBackupZip(ByteArrayInputStream(uscita.toByteArray()), cartelle.newFolder("tmp"))
        assertEquals("""{"app":"GymBro"}""", letto.json)
        assertEquals(setOf("a1b2.jpg"), letto.immagini.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), letto.immagini.getValue("a1b2.jpg").readBytes())
    }

    @Test
    fun zip_percorsiDelVecchioTelefono_riscrittiVersoQuesto() {
        val dati = DatiBackup(
            creatoIl = 0,
            esercizi = listOf(
                esercizio(1, "/data/user/0/com.my.gymbro/files/exercise_images/a1b2.jpg"),
                esercizio(2, "/data/user/0/com.my.gymbro/files/exercise_images/mancante.jpg"),
                esercizio(3, "asset://esercizi/x/0.jpg"),
            ),
            schede = emptyList(), eserciziScheda = emptyList(), seriePianificate = emptyList(),
            sessioni = emptyList(), serieEseguite = emptyList(), buddy = emptyMap(), predefinitiEliminati = emptySet(),
        )
        val qui = File("/qui/exercise_images")
        val percorsi = dati.conImmaginiIn(qui, setOf("a1b2.jpg")).esercizi.map { it.immaginePath }
        assertEquals(File(qui, "a1b2.jpg").absolutePath, percorsi[0])
        assertEquals(dati.esercizi[1].immaginePath, percorsi[1]) // senza file: lo toglierà il ripristino
        assertEquals("asset://esercizi/x/0.jpg", percorsi[2])
    }

    @Test
    fun zip_nomiPericolosi_ignorati() {
        val uscita = ByteArrayOutputStream()
        ZipOutputStream(uscita).use { zip ->
            zip.putNextEntry(ZipEntry("gymbro-backup.json")); zip.write("{}".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("exercise_images/../../evil.jpg")); zip.write(byteArrayOf(9)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("exercise_images/.nascosto")); zip.write(byteArrayOf(9)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("altro/x.jpg")); zip.write(byteArrayOf(9)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("exercise_images/ok.jpg")); zip.write(byteArrayOf(7)); zip.closeEntry()
        }
        val tmp = cartelle.newFolder("tmp")
        val letto = leggiBackupZip(ByteArrayInputStream(uscita.toByteArray()), tmp)
        assertEquals(setOf("ok.jpg"), letto.immagini.keys)
        assertEquals(listOf("ok.jpg"), tmp.list()!!.toList())
        assertFalse(File(cartelle.root, "evil.jpg").exists())
    }

    @Test
    fun zip_senzaJson_rifiutato() {
        val uscita = ByteArrayOutputStream()
        ZipOutputStream(uscita).use { zip ->
            zip.putNextEntry(ZipEntry("foto.jpg")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        assertThrows(BackupNonValido::class.java) {
            leggiBackupZip(ByteArrayInputStream(uscita.toByteArray()), cartelle.newFolder("tmp"))
        }
        assertFalse(eZip("""{"app":"GymBro"}""".toByteArray()))
    }
}
