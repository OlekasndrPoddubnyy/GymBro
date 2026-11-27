package com.my.gymbro.ui

import android.content.Context
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.Obiettivo
import org.json.JSONArray
import org.json.JSONObject

// Ripresa di un allenamento dopo che Android ha chiuso l'app (memoria, batteria, app tolta dalle
// recenti): il motore vive solo in memoria, quindi a ogni cambio di passo o di fase se ne salva
// quanto basta per ricostruirlo. Le serie fatte non stanno qui: sono già nel database.

/** Versione del formato salvato: uno sconosciuto (app tornata indietro) si scarta. */
private const val FORMATO_RIPRESA = 1

data class AllenamentoSalvato(
    val schedaId: Long,
    val sessioneId: Long,
    val dataInizio: Long,
    val nomeScheda: String,
    /** Gli esercizi come li stava facendo, sostituzioni comprese. */
    val esercizi: List<EsercizioEsec>,
    /** Serie previste e obiettivo di Gym Buddy all'avvio, per valutarlo a fine allenamento. */
    val pianoBuddy: Map<Long, Pair<Int, Obiettivo>>,
    val indicePasso: Int,
    val fase: Fase,
    val completati: Set<Int>,
    val saltati: Set<Int>,
    /** Fine del recupero in corso (ora di sistema, ms); null fuori dal recupero. */
    val fineRecupero: Long?,
    /** Ultimo cambiamento (ms): è più o meno quando l'allenamento si è interrotto. */
    val aggiornatoIl: Long = 0,
)

/** Da dove si riparte dopo l'interruzione. */
internal sealed interface Ripartenza {
    /** Il recupero non era finito: continua col tempo che resta. */
    data class Recupero(val secondi: Int) : Ripartenza
    /** Serie a ripetizioni: si torna alla registrazione della serie. */
    data object Serie : Ripartenza
    /** Tutto il resto: si aspetta "Inizia", così una serie a tempo non parte da sola. */
    data object Attesa : Ripartenza
}

internal fun ripartenza(fase: Fase, modalita: Modalita, fineRecupero: Long?, ora: Long): Ripartenza {
    val rimastiMs = if (fase == Fase.RECUPERO && fineRecupero != null) fineRecupero - ora else 0L
    return when {
        rimastiMs >= 1000 -> Ripartenza.Recupero(((rimastiMs + 999) / 1000).toInt())
        // Un recupero scaduto intanto porta alla serie, come se fosse finito con l'app aperta.
        modalita == Modalita.RIPETIZIONI && (fase == Fase.ESECUZIONE || fase == Fase.RECUPERO) -> Ripartenza.Serie
        else -> Ripartenza.Attesa
    }
}

/** L'allenamento da riprendere, salvato in SharedPreferences (un solo allenamento alla volta). */
class RipresaAllenamento(context: Context) {
    private val prefs = context.getSharedPreferences("gymbro_ripresa", Context.MODE_PRIVATE)

    /** null se non c'è niente da riprendere (un contenuto illeggibile viene scartato). */
    fun leggi(): AllenamentoSalvato? {
        val testo = prefs.getString(CHIAVE, null) ?: return null
        return decodificaRipresa(testo) ?: run { cancella(); null }
    }

    fun salva(a: AllenamentoSalvato) {
        prefs.edit().putString(CHIAVE, codificaRipresa(a)).apply()
    }

    fun cancella() {
        prefs.edit().remove(CHIAVE).apply()
    }

    private companion object {
        const val CHIAVE = "allenamento"
    }
}

internal fun codificaRipresa(a: AllenamentoSalvato): String = JSONObject()
    .put("formato", FORMATO_RIPRESA)
    .put("scheda", a.schedaId)
    .put("sessione", a.sessioneId)
    .put("inizio", a.dataInizio)
    .put("nome", a.nomeScheda)
    .put("esercizi", JSONArray(a.esercizi.map { e ->
        JSONObject()
            .put("id", e.esercizioId)
            .put("nome", e.nome)
            .put("modalita", e.modalita.name)
            .metti("gruppo", e.gruppo)
            .metti("immagine", e.immaginePath)
            .metti("descrizione", e.descrizione)
            .metti("note", e.note)
            .metti("muscolo", e.gruppoMuscolare?.name)
            .metti("obiettivo", e.obiettivo?.let(::obiettivoJson))
            .put("calibrazione", e.calibrazione)
            .metti("chiave", e.chiaveSeed)
            .put("serie", JSONArray(e.serie.map { s ->
                JSONObject()
                    .put("numero", s.numero)
                    .metti("durata", s.durataSec)
                    .metti("rip", s.ripetizioniPianificate)
                    .metti("kg", s.pesoPianificato?.let(::kgJson))
                    .put("recupero", s.recuperoSec)
            }))
    }))
    .put("buddy", JSONArray(a.pianoBuddy.map { (id, piano) ->
        JSONObject().put("id", id).put("previste", piano.first).put("obiettivo", obiettivoJson(piano.second))
    }))
    .put("passo", a.indicePasso)
    .put("fase", a.fase.name)
    .put("completati", JSONArray(a.completati.sorted()))
    .put("saltati", JSONArray(a.saltati.sorted()))
    .metti("fineRecupero", a.fineRecupero)
    .put("aggiornato", a.aggiornatoIl)
    .toString()

/** null se il testo è rovinato o di un formato che questa versione non conosce. */
internal fun decodificaRipresa(testo: String): AllenamentoSalvato? = try {
    val o = JSONObject(testo)
    if (o.optInt("formato", 0) != FORMATO_RIPRESA) null
    else AllenamentoSalvato(
        schedaId = o.getLong("scheda"),
        sessioneId = o.getLong("sessione"),
        dataInizio = o.getLong("inizio"),
        nomeScheda = o.getString("nome"),
        esercizi = o.getJSONArray("esercizi").oggetti().map { e ->
            EsercizioEsec(
                esercizioId = e.getLong("id"),
                nome = e.getString("nome"),
                modalita = Modalita.valueOf(e.getString("modalita")),
                gruppo = if (e.isNull("gruppo")) null else e.getInt("gruppo"),
                immaginePath = e.testoONull("immagine"),
                descrizione = e.testoONull("descrizione"),
                note = e.testoONull("note"),
                gruppoMuscolare = e.testoONull("muscolo")?.let { n -> GruppoMuscolare.entries.firstOrNull { it.name == n } },
                obiettivo = e.optJSONObject("obiettivo")?.let(::obiettivoDa),
                calibrazione = e.optBoolean("calibrazione", false),
                chiaveSeed = e.testoONull("chiave"),
                serie = e.getJSONArray("serie").oggetti().map { s ->
                    SerieEsec(
                        numero = s.getInt("numero"),
                        durataSec = if (s.isNull("durata")) null else s.getInt("durata"),
                        ripetizioniPianificate = s.testoONull("rip"),
                        pesoPianificato = if (s.isNull("kg")) null else s.getDouble("kg").toFloat(),
                        recuperoSec = s.optInt("recupero", 0),
                    )
                },
            )
        },
        pianoBuddy = o.getJSONArray("buddy").oggetti().associate { b ->
            b.getLong("id") to (b.getInt("previste") to obiettivoDa(b.getJSONObject("obiettivo")))
        },
        indicePasso = o.getInt("passo"),
        fase = Fase.valueOf(o.getString("fase")),
        completati = o.getJSONArray("completati").interi(),
        saltati = o.getJSONArray("saltati").interi(),
        fineRecupero = if (o.isNull("fineRecupero")) null else o.getLong("fineRecupero"),
        aggiornatoIl = o.optLong("aggiornato", 0L),
    )
} catch (e: Exception) {
    // JSON rotto, campo mancante, enum sconosciuto: non si può riprendere.
    null
}

private fun obiettivoJson(ob: Obiettivo): JSONObject =
    JSONObject().put("rip", ob.ripetizioni).metti("kg", ob.caricoKg?.let(::kgJson))

private fun obiettivoDa(o: JSONObject) =
    Obiettivo(o.getInt("rip"), if (o.isNull("kg")) null else o.getDouble("kg").toFloat())

/** 72.5f resta 72.5 e non 72.5000000001. */
private fun kgJson(kg: Float): Double = kg.toString().toDouble()

private fun JSONObject.metti(chiave: String, valore: Any?): JSONObject =
    if (valore == null) this else put(chiave, valore)

private fun JSONObject.testoONull(k: String): String? = if (isNull(k)) null else getString(k)

private fun JSONArray.oggetti(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONArray.interi(): Set<Int> = (0 until length()).mapTo(HashSet()) { getInt(it) }
