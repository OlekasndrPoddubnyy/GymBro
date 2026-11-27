package com.my.gymbro.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

// Una scheda passata da un telefono all'altro con un QR code, senza internet.
//
// Testo del QR: "GYMBRO1:" + Base45(deflate(JSON)). Base45 usa solo i caratteri che il QR
// codifica in modalità alfanumerica, più densa di quella a byte: a parità di scheda il codice
// ha meno quadratini ed è più facile da inquadrare.

/** Una scheda così come viaggia nel QR: gli esercizi sono per nome, gli id cambiano da telefono a telefono. */
data class SchedaCondivisa(
    val nome: String,
    val note: String?,
    val esercizi: List<EsercizioCondiviso>,
)

data class EsercizioCondiviso(
    val nome: String,
    /** Gruppo e attrezzatura servono a chi riceve per creare l'esercizio, se non ce l'ha. */
    val gruppo: GruppoMuscolare,
    val attrezzatura: Attrezzatura,
    val modalita: Modalita,
    val note: String?,
    val superset: Int?,
    val serie: List<SerieCondivisa>,
    /**
     * Nome di catalogo, solo se diverso da [nome] (esercizio predefinito rinominato): chi riceve lo
     * ritrova anche se da lui si chiama in un altro modo. Le versioni precedenti lo ignorano.
     */
    val chiave: String? = null,
)

data class SerieCondivisa(
    val ripetizioni: String? = null,
    val pesoKg: Float? = null,
    val durataSec: Int? = null,
    val recuperoSec: Int = 0,
)

/** Il QR non contiene una scheda di GymBro leggibile da questa versione dell'app. */
class QrNonValido(messaggio: String, causa: Throwable? = null) : Exception(messaggio, causa)

private const val PREFISSO = "GYMBRO"
private const val FORMATO_QR = 1
private val INTESTAZIONE = Regex("""^$PREFISSO(\d{1,3}):""")

// Limiti per un QR che arriva da fuori: niente schede assurde né testi enormi.
private const val BYTE_MASSIMI_JSON = 64 * 1024
private const val MAX_ESERCIZI = 60
private const val MAX_SERIE = 30
private const val MAX_NOME = 80
private const val MAX_NOTE = 2000

/** Oltre questa versione (137×137 quadratini) un QR mostrato su uno schermo si legge male. */
internal const val VERSIONE_QR_MASSIMA = 30

fun SchedaConEsercizi.perCondividere(): SchedaCondivisa = SchedaCondivisa(
    nome = scheda.nome,
    note = scheda.note?.takeIf { it.isNotBlank() },
    esercizi = esercizi.sortedBy { it.esercizioScheda.ordine }.map { ec ->
        EsercizioCondiviso(
            nome = ec.esercizio.nome,
            gruppo = ec.esercizio.gruppoMuscolare,
            attrezzatura = ec.esercizio.attrezzatura,
            modalita = ec.esercizioScheda.modalita,
            note = ec.esercizioScheda.note?.takeIf { it.isNotBlank() },
            superset = ec.esercizioScheda.gruppoSuperset,
            serie = ec.serie.sortedBy { it.numero }.map { s ->
                SerieCondivisa(s.ripetizioni, s.pesoKg, s.durataSec, s.recuperoSec)
            },
            chiave = ec.esercizio.chiaveSeed?.takeIf { it != ec.esercizio.nome },
        )
    },
)

/** La stessa scheda senza note: se con le note non sta in un QR leggibile. */
fun SchedaCondivisa.senzaNote(): SchedaCondivisa =
    copy(note = null, esercizi = esercizi.map { it.copy(note = null) })

fun codificaSchedaCondivisa(s: SchedaCondivisa): String {
    val json = JSONObject()
        .put("n", s.nome)
        .metti("o", s.note)
        .put("e", JSONArray(s.esercizi.map { e ->
            JSONObject()
                .put("n", e.nome)
                .put("g", e.gruppo.name)
                .put("a", e.attrezzatura.name)
                .put("m", e.modalita.name)
                .metti("o", e.note)
                .metti("s", e.superset)
                .metti("k", e.chiave)
                .put("r", JSONArray(e.serie.map { r ->
                    // [ripetizioni, kg, secondi, recupero]: le posizioni al posto dei nomi tengono piccolo il QR.
                    JSONArray()
                        .put(r.ripetizioni ?: JSONObject.NULL)
                        // toString().toDouble(): 7.3f diventa 7.3 e non 7.300000190734863.
                        .put(r.pesoKg?.toString()?.toDouble() ?: JSONObject.NULL)
                        .put(r.durataSec ?: JSONObject.NULL)
                        .put(r.recuperoSec)
                }))
        }))
    return "$PREFISSO$FORMATO_QR:" + base45(comprimi(json.toString().encodeToByteArray()))
}

/** @throws QrNonValido se [testo] non è una scheda di GymBro o è di una versione più nuova. */
fun decodificaSchedaCondivisa(testo: String): SchedaCondivisa {
    val intestazione = INTESTAZIONE.find(testo) ?: throw QrNonValido("Questo QR non è una scheda di GymBro.")
    if (intestazione.groupValues[1].toInt() > FORMATO_QR) {
        throw QrNonValido("La scheda viene da una versione più nuova di GymBro: aggiorna l'app e riprova.")
    }
    return try {
        val json = JSONObject(decomprimi(daBase45(testo.substring(intestazione.range.last + 1))).decodeToString())
        val esercizi = json.getJSONArray("e")
        if (esercizi.length() !in 1..MAX_ESERCIZI) throw QrNonValido("La scheda nel QR non ha esercizi validi.")
        SchedaCondivisa(
            nome = json.getString("n").trim().take(MAX_NOME).ifEmpty { "Scheda condivisa" },
            note = json.testoONull("o")?.take(MAX_NOTE),
            esercizi = (0 until esercizi.length()).map { i ->
                val e = esercizi.getJSONObject(i)
                val serie = e.getJSONArray("r")
                if (serie.length() !in 1..MAX_SERIE) throw QrNonValido("Un esercizio nel QR non ha serie valide.")
                val nome = e.getString("n").trim().take(MAX_NOME)
                if (nome.isEmpty()) throw QrNonValido("Un esercizio nel QR non ha nome.")
                EsercizioCondiviso(
                    nome = nome,
                    gruppo = enumDa(e.optString("g"), GruppoMuscolare.CORE),
                    attrezzatura = enumDa(e.optString("a"), Attrezzatura.CORPO_LIBERO),
                    modalita = enumDa(e.optString("m"), Modalita.RIPETIZIONI),
                    note = e.testoONull("o")?.take(MAX_NOTE),
                    superset = if (e.isNull("s")) null else e.getInt("s"),
                    chiave = e.testoONull("k")?.take(MAX_NOME),
                    serie = (0 until serie.length()).map { j ->
                        val r = serie.getJSONArray(j)
                        SerieCondivisa(
                            ripetizioni = if (r.isNull(0)) null else r.getString(0).trim().take(20).ifEmpty { null },
                            pesoKg = if (r.isNull(1)) null else r.getDouble(1).takeIf { it.isFinite() && it in 0.0..1000.0 }?.toFloat(),
                            durataSec = if (r.isNull(2)) null else r.getInt(2).takeIf { it in 1..7200 },
                            recuperoSec = r.optInt(3, 0).coerceIn(0, 3600),
                        )
                    },
                )
            },
        )
    } catch (e: QrNonValido) {
        throw e
    } catch (e: Exception) {
        // JSON rotto, Base45 o deflate non validi, numeri fuori formato: il QR è danneggiato.
        throw QrNonValido("Il QR non si legge bene o non è una scheda di GymBro.", e)
    }
}

/** Il QR pronto da disegnare: [scuro] ha [lato]×[lato] celle, margine bianco compreso. */
class QrPronto(val lato: Int, val scuro: BooleanArray, val noteTolte: Boolean, val testo: String)

/**
 * Il QR più piccolo che contiene [s]: correzione d'errore media, altrimenti bassa, altrimenti senza
 * note. null se nemmeno così sta in un QR leggibile.
 */
fun qrPerScheda(s: SchedaCondivisa): QrPronto? {
    for ((scheda, noteTolte) in listOf(s to false, s.senzaNote() to true)) {
        val testo = codificaSchedaCondivisa(scheda)
        for (livello in listOf(ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)) {
            val qr = try {
                Encoder.encode(testo, livello)
            } catch (e: WriterException) {
                continue // non ci sta proprio
            }
            if (qr.version.versionNumber > VERSIONE_QR_MASSIMA) continue
            val m = qr.matrix
            val margine = 4
            val lato = m.width + 2 * margine
            val scuro = BooleanArray(lato * lato)
            for (y in 0 until m.height) for (x in 0 until m.width) {
                if (m.get(x, y).toInt() == 1) scuro[(y + margine) * lato + x + margine] = true
            }
            return QrPronto(lato, scuro, noteTolte, testo)
        }
    }
    return null
}

/** Il testo del QR nell'immagine, o null se non se ne trova uno leggibile. */
fun leggiQr(sorgente: LuminanceSource, accurato: Boolean = false): String? {
    val suggerimenti = if (accurato) mapOf(DecodeHintType.TRY_HARDER to true) else emptyMap()
    val binarizzatori = if (accurato) listOf(HybridBinarizer(sorgente), GlobalHistogramBinarizer(sorgente))
    else listOf(HybridBinarizer(sorgente))
    for (b in binarizzatori) {
        try {
            return QRCodeReader().decode(BinaryBitmap(b), suggerimenti).text
        } catch (e: ReaderException) {
            // niente qui: si prova il prossimo
        }
    }
    return null
}

/** Un nome che non c'è ancora tra [esistenti]: "Nome", poi "Nome (condivisa)", "Nome (condivisa 2)"… */
fun nomeLibero(nome: String, esistenti: Set<String>): String {
    if (nome !in esistenti) return nome
    var n = 1
    while (true) {
        val candidato = if (n == 1) "$nome (condivisa)" else "$nome (condivisa $n)"
        if (candidato !in esistenti) return candidato
        n++
    }
}

/**
 * Gli esercizi della scheda che [libreria] non ha: né per chiave di catalogo, né per nome (senza
 * maiuscole/minuscole). Sono quelli che l'importazione aggiungerà.
 */
fun eserciziNuovi(s: SchedaCondivisa, libreria: Collection<Esercizio>): Set<String> {
    val chiavi = libreria.mapNotNullTo(HashSet()) { it.chiaveSeed }
    val nomi = libreria.mapTo(HashSet()) { normalizzaNome(it.nome) }
    return s.esercizi
        .filter { (it.chiave ?: it.nome) !in chiavi && normalizzaNome(it.nome) !in nomi }
        .mapTo(LinkedHashSet()) { it.nome }
}

// ---------- Compressione e Base45 ----------

private fun comprimi(dati: ByteArray): ByteArray {
    val d = Deflater(Deflater.BEST_COMPRESSION, true)
    try {
        d.setInput(dati)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        return out.toByteArray()
    } finally {
        d.end()
    }
}

/** Con un tetto: pochi byte compressi possono diventare moltissimi una volta aperti. */
private fun decomprimi(dati: ByteArray): ByteArray {
    val inf = Inflater(true)
    try {
        inf.setInput(dati)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw DataFormatException("dati troncati")
            out.write(buf, 0, n)
            if (out.size() > BYTE_MASSIMI_JSON) throw DataFormatException("troppo grande")
        }
        return out.toByteArray()
    } finally {
        inf.end()
    }
}

private const val ALFABETO_45 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

/** Base45 (RFC 9285): 2 byte → 3 caratteri alfanumerici del QR. */
internal fun base45(dati: ByteArray): String {
    val sb = StringBuilder((dati.size + 1) / 2 * 3)
    var i = 0
    while (i + 1 < dati.size) {
        val n = (dati[i].toInt() and 0xFF) * 256 + (dati[i + 1].toInt() and 0xFF)
        sb.append(ALFABETO_45[n % 45]).append(ALFABETO_45[n / 45 % 45]).append(ALFABETO_45[n / 2025])
        i += 2
    }
    if (i < dati.size) {
        val n = dati[i].toInt() and 0xFF
        sb.append(ALFABETO_45[n % 45]).append(ALFABETO_45[n / 45])
    }
    return sb.toString()
}

internal fun daBase45(testo: String): ByteArray {
    require(testo.length % 3 != 1) { "lunghezza Base45 non valida" }
    fun valore(c: Char) = ALFABETO_45.indexOf(c).also { require(it >= 0) { "carattere non Base45" } }
    val out = ByteArrayOutputStream(testo.length / 3 * 2 + 1)
    var i = 0
    while (i < testo.length) {
        if (i + 2 < testo.length) {
            val n = valore(testo[i]) + valore(testo[i + 1]) * 45 + valore(testo[i + 2]) * 2025
            require(n <= 0xFFFF) { "gruppo Base45 fuori intervallo" }
            out.write(n shr 8)
            out.write(n and 0xFF)
            i += 3
        } else {
            val n = valore(testo[i]) + valore(testo[i + 1]) * 45
            require(n <= 0xFF) { "gruppo Base45 fuori intervallo" }
            out.write(n)
            i += 2
        }
    }
    return out.toByteArray()
}

// ---------- JSON ----------

private fun JSONObject.metti(chiave: String, valore: Any?): JSONObject =
    if (valore == null) this else put(chiave, valore)

private fun JSONObject.testoONull(k: String): String? =
    if (isNull(k)) null else getString(k).trim().ifEmpty { null }

private inline fun <reified E : Enum<E>> enumDa(nome: String, riserva: E): E =
    enumValues<E>().firstOrNull { it.name == nome } ?: riserva
