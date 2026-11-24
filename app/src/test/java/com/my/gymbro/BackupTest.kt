package com.my.gymbro

import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.BackupNonValido
import com.my.gymbro.data.DatiBackup
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.EsercizioScheda
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.Scheda
import com.my.gymbro.data.SerieEseguita
import com.my.gymbro.data.SeriePianificata
import com.my.gymbro.data.Sessione
import com.my.gymbro.data.codificaBackup
import com.my.gymbro.data.decodificaBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {

    private val dati = DatiBackup(
        creatoIl = 1_764_000_000_000,
        esercizi = listOf(
            Esercizio(1, "Panca piana", GruppoMuscolare.PETTO, Attrezzatura.BILANCIERE, "Spingi.\n\"Lento\"", "asset://esercizi/panca/0.jpg", true),
            Esercizio(2, "Il mio esercizio", GruppoMuscolare.CORE, Attrezzatura.CORPO_LIBERO),
        ),
        schede = listOf(Scheda(5, "A · Total body — Martedì", "Note con è e 100%", 123L)),
        eserciziScheda = listOf(
            EsercizioScheda(10, 5, 1, Modalita.RIPETIZIONI, 0, "Scapole indietro", 1),
            EsercizioScheda(11, 5, 2, Modalita.TEMPO, 1),
        ),
        seriePianificate = listOf(
            SeriePianificata(20, 10, 1, ripetizioni = "8-12", pesoKg = 7.3f, recuperoSec = 90),
            SeriePianificata(21, 11, 1, durataSec = 35, recuperoSec = 45),
        ),
        sessioni = listOf(
            Sessione(30, 5, "A · Total body — Martedì", 1000L, 2000L),
            Sessione(31, null, "Scheda eliminata", 3000L, null),
        ),
        serieEseguite = listOf(
            SerieEseguita(40, 30, 1, 1, 10, 12.5f, null, true),
            SerieEseguita(41, 30, 2, 1, null, null, 35, true),
        ),
        buddy = mapOf("p:5:1" to "#commento\nattivo=true\nkg=12.5\n", "i:min" to "8"),
        predefinitiEliminati = setOf("Croci ai cavi", "Pullover"),
    )

    @Test
    fun andataERitorno_tuttoUguale() {
        assertEquals(dati, decodificaBackup(codificaBackup(dati)))
    }

    @Test
    fun allenamenti_soloSessioniConSerie() {
        assertEquals(1, dati.allenamenti)
    }

    @Test
    fun fileNonGymBro_rifiutato() {
        assertThrows(BackupNonValido::class.java) { decodificaBackup("non è json") }
        assertThrows(BackupNonValido::class.java) { decodificaBackup("""{"app":"Altro","formato":1}""") }
    }

    @Test
    fun versionePiuNuova_rifiutata() {
        assertThrows(BackupNonValido::class.java) { decodificaBackup("""{"app":"GymBro","formato":99}""") }
    }

    @Test
    fun rigaIncompleta_rifiutata() {
        assertThrows(BackupNonValido::class.java) {
            decodificaBackup("""{"app":"GymBro","formato":1,"schede":[{"id":1}]}""")
        }
    }
}
