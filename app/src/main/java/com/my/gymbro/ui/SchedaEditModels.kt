package com.my.gymbro.ui

import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.EsercizioScheda
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.Scheda
import com.my.gymbro.data.SchedaConEsercizi
import com.my.gymbro.data.SeriePianificata
import java.util.concurrent.atomic.AtomicLong

/** Id stabile per le righe in editing (key univoca della lista, indipendente da esercizio.id). */
private val generaUid = AtomicLong(0)

/**
 * Modelli di editing in memoria per la creazione/modifica di una scheda.
 * I campi delle serie sono stringhe per legare i TextField senza riformattazioni a ogni tasto
 * (la conversione tipata avviene solo verso/da le entità Room).
 */
data class SchedaInModifica(
    val id: Long = 0,
    val nome: String = "",
    val note: String = "",
    val dataCreazione: Long = System.currentTimeMillis(),
    val esercizi: List<EsercizioInScheda> = emptyList()
)

data class EsercizioInScheda(
    val uid: Long = generaUid.incrementAndGet(),
    val esercizio: Esercizio,
    val modalita: Modalita = Modalita.RIPETIZIONI,
    val note: String = "",
    val serie: List<SerieInput> = listOf(SerieInput()),
    val supersetConProssimo: Boolean = false,   // collegato in superset con l'esercizio seguente
)

data class SerieInput(
    val durata: String = "",        // secondi (modalità Tempo)
    val ripetizioni: String = "",   // es. "8-12" (modalità Ripetizioni)
    val peso: String = "",          // kg (modalità Ripetizioni)
    val recupero: String = "60"     // secondi, vale per entrambe le modalità
)

// ---------- conversioni ----------

fun SchedaConEsercizi.toInModifica(): SchedaInModifica {
    val ordinati = esercizi.sortedBy { it.esercizioScheda.ordine }
    val righe = ordinati.mapIndexed { i, ec ->
        val gruppo = ec.esercizioScheda.gruppoSuperset
        val gruppoSucc = ordinati.getOrNull(i + 1)?.esercizioScheda?.gruppoSuperset
        EsercizioInScheda(
            esercizio = ec.esercizio,
            modalita = ec.esercizioScheda.modalita,
            note = ec.esercizioScheda.note.orEmpty(),
            supersetConProssimo = gruppo != null && gruppo == gruppoSucc,
            serie = ec.serie
                .sortedBy { it.numero }
                .map { sp ->
                    SerieInput(
                        durata = sp.durataSec?.toString().orEmpty(),
                        ripetizioni = sp.ripetizioni.orEmpty(),
                        peso = sp.pesoKg?.toString().orEmpty(),
                        recupero = sp.recuperoSec.toString()
                    )
                }
                .ifEmpty { listOf(SerieInput()) }
        )
    }
    return SchedaInModifica(
        id = scheda.id,
        nome = scheda.nome,
        note = scheda.note.orEmpty(),
        dataCreazione = scheda.dataCreazione,
        esercizi = righe
    )
}

fun SchedaInModifica.toEntities(): Pair<Scheda, List<Pair<EsercizioScheda, List<SeriePianificata>>>> {
    val scheda = Scheda(
        id = id,
        nome = nome.trim(),
        note = note.trim().ifBlank { null },
        dataCreazione = dataCreazione
    )
    val gruppi = calcolaGruppiSuperset(esercizi)
    val righe = esercizi.mapIndexed { idx, e ->
        val es = EsercizioScheda(
            schedaId = id,
            esercizioId = e.esercizio.id,
            modalita = e.modalita,
            note = e.note.trim().ifBlank { null },
            gruppoSuperset = gruppi[idx].takeIf { it >= 0 }
        )
        val serie = e.serie.mapIndexed { i, s ->
            when (e.modalita) {
                Modalita.TEMPO -> SeriePianificata(
                    esercizioSchedaId = 0,
                    numero = i + 1,
                    durataSec = s.durata.toIntOrNull(),
                    recuperoSec = s.recupero.toIntOrNull() ?: 0
                )
                Modalita.RIPETIZIONI -> SeriePianificata(
                    esercizioSchedaId = 0,
                    numero = i + 1,
                    ripetizioni = s.ripetizioni.trim().ifBlank { null },
                    pesoKg = s.peso.toFloatOrNull(),
                    recuperoSec = s.recupero.toIntOrNull() ?: 0
                )
            }
        }
        es to serie
    }
    return scheda to righe
}

/** Assegna un id di gruppo (>= 1) alle sequenze di esercizi collegati in superset; -1 = nessun gruppo. */
private fun calcolaGruppiSuperset(esercizi: List<EsercizioInScheda>): IntArray {
    val n = esercizi.size
    val gruppi = IntArray(n) { -1 }
    var gid = 0
    var i = 0
    while (i < n) {
        if (i < n - 1 && esercizi[i].supersetConProssimo) {
            gid++
            gruppi[i] = gid
            var j = i
            while (j < n - 1 && esercizi[j].supersetConProssimo) {
                j++
                gruppi[j] = gid
            }
            i = j + 1
        } else {
            i++
        }
    }
    return gruppi
}
