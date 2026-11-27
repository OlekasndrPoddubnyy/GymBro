package com.my.gymbro.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * Backup completo in uno ZIP: il JSON di sempre più le foto degli esercizi scelte dall'utente.
 *
 *   gymbro-backup.json
 *   exercise_images/<nome>.jpg
 *
 * Nel JSON il percorso di una foto è quello del telefono che l'ha esportata; in importazione si
 * riconosce dal nome del file e si riscrive verso la cartella delle foto di questo telefono.
 */

const val NOME_JSON_IN_ZIP = "gymbro-backup.json"
const val CARTELLA_IMMAGINI_IN_ZIP = "exercise_images/"

/** Limiti per non riempire il telefono con un file che non è un backup di GymBro. */
private const val BYTE_MASSIMI_JSON = 50L * 1024 * 1024
private const val BYTE_MASSIMI_IMMAGINE = 20L * 1024 * 1024
private const val BYTE_MASSIMI_TOTALI = 500L * 1024 * 1024
private const val FILE_MASSIMI = 5000

private val NOME_SICURO = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

/** Un nome di file accettabile dentro la cartella delle foto: niente percorsi, niente file nascosti. */
internal fun nomeImmagineSicuro(nome: String): Boolean = NOME_SICURO.matches(nome) && ".." !in nome

/** I primi byte di uno ZIP ("PK\u0003\u0004"). */
fun eZip(inizio: ByteArray): Boolean =
    inizio.size >= 4 && inizio[0] == 0x50.toByte() && inizio[1] == 0x4B.toByte() &&
        inizio[2] == 0x03.toByte() && inizio[3] == 0x04.toByte()

/** Le foto dell'utente usate dagli esercizi (quelle incluse nell'app non servono: ci sono già). */
fun immaginiDaSalvare(esercizi: List<Esercizio>): List<File> =
    esercizi.mapNotNull { it.immaginePath }
        .filter { !it.startsWith("asset://") }
        .map(::File)
        .filter { it.isFile && nomeImmagineSicuro(it.name) }
        .distinctBy { it.name }

fun scriviBackupZip(uscita: OutputStream, json: String, immagini: List<File>) {
    ZipOutputStream(uscita.buffered()).use { zip ->
        zip.putNextEntry(ZipEntry(NOME_JSON_IN_ZIP))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        immagini.forEach { file ->
            // Le foto sono già JPEG compressi: ricomprimerle costa tempo senza guadagnare spazio.
            zip.putNextEntry(ZipEntry(CARTELLA_IMMAGINI_IN_ZIP + file.name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/** Contenuto di uno ZIP letto: il JSON e le foto estratte in una cartella provvisoria (nome → file). */
class ContenutoZip(val json: String, val immagini: Map<String, File>)

/**
 * Legge uno ZIP di backup, estraendo le foto in [cartella] (svuotata prima). Le voci con nomi
 * strani (percorsi, file nascosti, altre cartelle) si ignorano: un file non può finire fuori da lì.
 */
fun leggiBackupZip(ingresso: InputStream, cartella: File): ContenutoZip {
    cartella.deleteRecursively()
    if (!cartella.mkdirs()) throw IOException("Cartella provvisoria non creata")
    var json: String? = null
    val immagini = LinkedHashMap<String, File>()
    var totale = 0L
    var voci = 0
    ZipInputStream(ingresso.buffered()).use { zip ->
        while (true) {
            val voce = zip.nextEntry ?: break
            if (++voci > FILE_MASSIMI) throw BackupNonValido("Il file contiene troppi elementi per essere un backup di GymBro.")
            val nome = voce.name
            when {
                voce.isDirectory -> Unit
                nome == NOME_JSON_IN_ZIP -> {
                    val byte = zip.leggiAlPiu(BYTE_MASSIMI_JSON)
                    totale += byte.size
                    json = byte.decodeToString()
                }
                nome.startsWith(CARTELLA_IMMAGINI_IN_ZIP) -> {
                    val file = nome.removePrefix(CARTELLA_IMMAGINI_IN_ZIP)
                    if (!nomeImmagineSicuro(file)) continue
                    val destinazione = File(cartella, file)
                    val scritti = destinazione.outputStream().use { zip.copiaAlPiu(it, BYTE_MASSIMI_IMMAGINE) }
                    totale += scritti
                    immagini[file] = destinazione
                }
            }
            if (totale > BYTE_MASSIMI_TOTALI) throw BackupNonValido("Il file è troppo grande per essere un backup di GymBro.")
        }
    }
    return ContenutoZip(
        json ?: throw BackupNonValido("Nello ZIP non c'è $NOME_JSON_IN_ZIP: non è un backup di GymBro."),
        immagini,
    )
}

/**
 * Riscrive i percorsi delle foto verso [cartella]: quelle con un file in [disponibili] (per nome)
 * puntano lì, le altre restano come sono (il ripristino toglie poi quelle che non esistono).
 */
fun DatiBackup.conImmaginiIn(cartella: File, disponibili: Set<String>): DatiBackup = copy(
    esercizi = esercizi.map { e ->
        val path = e.immaginePath
        if (path.isNullOrBlank() || path.startsWith("asset://")) return@map e
        val nome = File(path).name
        if (nome in disponibili) e.copy(immaginePath = File(cartella, nome).absolutePath) else e
    }
)

private fun InputStream.leggiAlPiu(limite: Long): ByteArray {
    val byte = readNBytes((limite + 1).toInt())
    if (byte.size > limite) throw BackupNonValido("Il file è troppo grande per essere un backup di GymBro.")
    return byte
}

private fun InputStream.copiaAlPiu(uscita: OutputStream, limite: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var totale = 0L
    while (true) {
        val letti = read(buffer)
        if (letti < 0) return totale
        totale += letti
        if (totale > limite) throw BackupNonValido("Una foto nel file è troppo grande.")
        uscita.write(buffer, 0, letti)
    }
}
