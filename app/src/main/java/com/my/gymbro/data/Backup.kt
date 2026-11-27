package com.my.gymbro.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Copia di tutti i dati in un file JSON: libreria, schede, storico e Gym Buddy.

/** Versione del formato del file: un file più nuovo dell'app non si importa. */
private const val FORMATO_BACKUP = 1

/** Tutti i dati dell'app. Gli id restano quelli originali: lo storico e Gym Buddy vi fanno riferimento. */
data class DatiBackup(
    val creatoIl: Long,
    val esercizi: List<Esercizio>,
    val schede: List<Scheda>,
    val eserciziScheda: List<EsercizioScheda>,
    val seriePianificate: List<SeriePianificata>,
    val sessioni: List<Sessione>,
    val serieEseguite: List<SerieEseguita>,
    /** Il contenuto delle preferenze di Gym Buddy, così com'è salvato. */
    val buddy: Map<String, String>,
    /** Esercizi predefiniti eliminati: gli aggiornamenti del catalogo non devono rimetterli. */
    val predefinitiEliminati: Set<String>,
) {
    /** Le sessioni con almeno una serie, cioè gli allenamenti veri. */
    val allenamenti: Int get() = serieEseguite.mapTo(HashSet()) { it.sessioneId }.size
}

/** Il file non è un backup di GymBro leggibile da questa versione dell'app. */
class BackupNonValido(messaggio: String, causa: Throwable? = null) : Exception(messaggio, causa)

fun codificaBackup(d: DatiBackup): String {
    val radice = JSONObject()
        .put("app", "GymBro")
        .put("formato", FORMATO_BACKUP)
        .put("creatoIl", d.creatoIl)
        .put("esercizi", JSONArray(d.esercizi.map { e ->
            JSONObject()
                .put("id", e.id)
                .put("nome", e.nome)
                .put("gruppoMuscolare", e.gruppoMuscolare.name)
                .put("attrezzatura", e.attrezzatura.name)
                .metti("descrizione", e.descrizione)
                .metti("immaginePath", e.immaginePath)
                .put("predefinito", e.predefinito)
                .metti("chiaveSeed", e.chiaveSeed)
        }))
        .put("schede", JSONArray(d.schede.map { s ->
            JSONObject()
                .put("id", s.id)
                .put("nome", s.nome)
                .metti("note", s.note)
                .put("dataCreazione", s.dataCreazione)
        }))
        .put("eserciziScheda", JSONArray(d.eserciziScheda.map { r ->
            JSONObject()
                .put("id", r.id)
                .put("schedaId", r.schedaId)
                .put("esercizioId", r.esercizioId)
                .put("modalita", r.modalita.name)
                .put("ordine", r.ordine)
                .metti("note", r.note)
                .metti("gruppoSuperset", r.gruppoSuperset)
        }))
        .put("seriePianificate", JSONArray(d.seriePianificate.map { s ->
            JSONObject()
                .put("id", s.id)
                .put("esercizioSchedaId", s.esercizioSchedaId)
                .put("numero", s.numero)
                .metti("durataSec", s.durataSec)
                .metti("ripetizioni", s.ripetizioni)
                .metti("pesoKg", s.pesoKg?.toDouble())
                .put("recuperoSec", s.recuperoSec)
        }))
        .put("sessioni", JSONArray(d.sessioni.map { s ->
            JSONObject()
                .put("id", s.id)
                .metti("schedaId", s.schedaId)
                .put("nomeScheda", s.nomeScheda)
                .put("dataInizio", s.dataInizio)
                .metti("dataFine", s.dataFine)
        }))
        .put("serieEseguite", JSONArray(d.serieEseguite.map { s ->
            JSONObject()
                .put("id", s.id)
                .put("sessioneId", s.sessioneId)
                .put("esercizioId", s.esercizioId)
                .put("numero", s.numero)
                .metti("ripetizioni", s.ripetizioni)
                .metti("pesoKg", s.pesoKg?.toDouble())
                .metti("durataSec", s.durataSec)
                .put("completata", s.completata)
        }))
        .put("buddy", JSONObject().also { o -> d.buddy.forEach { (k, v) -> o.put(k, v) } })
        .put("predefinitiEliminati", JSONArray(d.predefinitiEliminati.sorted()))
    return radice.toString(2)
}

/** @throws BackupNonValido se il testo non è un backup di GymBro o è di una versione più nuova. */
fun decodificaBackup(testo: String): DatiBackup {
    val radice = try {
        JSONObject(testo)
    } catch (e: JSONException) {
        throw BackupNonValido("Il file non è un backup di GymBro.", e)
    }
    if (radice.optString("app") != "GymBro") throw BackupNonValido("Il file non è un backup di GymBro.")
    val formato = radice.optInt("formato", 0)
    if (formato > FORMATO_BACKUP) {
        throw BackupNonValido("Il backup viene da una versione più nuova di GymBro: aggiorna l'app e riprova.")
    }
    return try {
        DatiBackup(
            creatoIl = radice.optLong("creatoIl", 0L),
            esercizi = conChiavi(radice.lista("esercizi") { o ->
                Esercizio(
                    id = o.getLong("id"),
                    nome = o.getString("nome"),
                    gruppoMuscolare = enumDa(o.getString("gruppoMuscolare"), GruppoMuscolare.CORE),
                    attrezzatura = enumDa(o.getString("attrezzatura"), Attrezzatura.CORPO_LIBERO),
                    descrizione = o.testoONull("descrizione"),
                    immaginePath = o.testoONull("immaginePath"),
                    predefinito = o.optBoolean("predefinito", false),
                    chiaveSeed = o.testoONull("chiaveSeed"),
                )
            }),
            schede = radice.lista("schede") { o ->
                Scheda(
                    id = o.getLong("id"),
                    nome = o.getString("nome"),
                    note = o.testoONull("note"),
                    dataCreazione = o.getLong("dataCreazione"),
                )
            },
            eserciziScheda = radice.lista("eserciziScheda") { o ->
                EsercizioScheda(
                    id = o.getLong("id"),
                    schedaId = o.getLong("schedaId"),
                    esercizioId = o.getLong("esercizioId"),
                    modalita = enumDa(o.getString("modalita"), Modalita.RIPETIZIONI),
                    ordine = o.optInt("ordine", 0),
                    note = o.testoONull("note"),
                    gruppoSuperset = o.interoONull("gruppoSuperset"),
                )
            },
            seriePianificate = radice.lista("seriePianificate") { o ->
                SeriePianificata(
                    id = o.getLong("id"),
                    esercizioSchedaId = o.getLong("esercizioSchedaId"),
                    numero = o.getInt("numero"),
                    durataSec = o.interoONull("durataSec"),
                    ripetizioni = o.testoONull("ripetizioni"),
                    pesoKg = o.kgONull("pesoKg"),
                    recuperoSec = o.optInt("recuperoSec", 0),
                )
            },
            sessioni = radice.lista("sessioni") { o ->
                Sessione(
                    id = o.getLong("id"),
                    schedaId = o.lungoONull("schedaId"),
                    nomeScheda = o.getString("nomeScheda"),
                    dataInizio = o.getLong("dataInizio"),
                    dataFine = o.lungoONull("dataFine"),
                )
            },
            serieEseguite = radice.lista("serieEseguite") { o ->
                SerieEseguita(
                    id = o.getLong("id"),
                    sessioneId = o.getLong("sessioneId"),
                    esercizioId = o.getLong("esercizioId"),
                    numero = o.getInt("numero"),
                    ripetizioni = o.interoONull("ripetizioni"),
                    pesoKg = o.kgONull("pesoKg"),
                    durataSec = o.interoONull("durataSec"),
                    completata = o.optBoolean("completata", false),
                )
            },
            buddy = radice.optJSONObject("buddy")?.let { o ->
                o.keys().asSequence().associateWith { o.getString(it) }
            }.orEmpty(),
            predefinitiEliminati = radice.optJSONArray("predefinitiEliminati")?.let { a ->
                (0 until a.length()).mapTo(HashSet()) { a.getString(it) }
            }.orEmpty(),
        )
    } catch (e: JSONException) {
        throw BackupNonValido("Il backup è danneggiato o incompleto.", e)
    }
}

/**
 * I backup fatti prima delle chiavi di catalogo non le hanno: si assegnano come nella migrazione
 * del database (predefiniti col nome ancora nel catalogo). Se il backup ne ha anche una sola, è
 * recente e le chiavi sono già quelle giuste.
 */
private fun conChiavi(esercizi: List<Esercizio>): List<Esercizio> {
    if (esercizi.any { it.chiaveSeed != null }) return esercizi
    val chiavi = chiaviIniziali(
        esercizi.filter { it.predefinito }.map { it.id to it.nome },
        eserciziPredefiniti().mapTo(HashSet()) { it.nome },
    )
    return esercizi.map { e -> chiavi[e.id]?.let { e.copy(chiaveSeed = it) } ?: e }
}

/** Con null la chiave non si scrive: in lettura manca, cioè null. */
private fun JSONObject.metti(chiave: String, valore: Any?): JSONObject =
    if (valore == null) this else put(chiave, valore)

private fun JSONObject.testoONull(k: String): String? = if (isNull(k)) null else getString(k)
private fun JSONObject.interoONull(k: String): Int? = if (isNull(k)) null else getInt(k)
private fun JSONObject.lungoONull(k: String): Long? = if (isNull(k)) null else getLong(k)
private fun JSONObject.kgONull(k: String): Float? = if (isNull(k)) null else getDouble(k).toFloat()

private fun <T> JSONObject.lista(chiave: String, leggi: (JSONObject) -> T): List<T> {
    val a = optJSONArray(chiave) ?: return emptyList()
    return (0 until a.length()).map { leggi(a.getJSONObject(it)) }
}

/** Un valore sconosciuto (rinominato in una versione futura) non blocca l'importazione. */
private inline fun <reified E : Enum<E>> enumDa(nome: String, riserva: E): E =
    enumValues<E>().firstOrNull { it.name == nome } ?: riserva
