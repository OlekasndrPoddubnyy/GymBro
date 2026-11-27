package com.my.gymbro.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

private const val PREFISSO_ASSET = "asset://"

private const val CACHE_IMMAGINI_KB = 24 * 1024

/**
 * Immagini già decodificate, per dimensione e percorso. I percorsi non cambiano mai contenuto
 * (asset dell'APK o file con nome casuale), quindi non serve invalidarla.
 */
private val cacheImmagini = object : LruCache<String, ImageBitmap>(CACHE_IMMAGINI_KB) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
}

/**
 * Salvataggio delle immagini degli esercizi nello storage interno dell'app.
 * Nel DB si tiene solo il percorso: file assoluto per le foto utente, oppure
 * "asset://esercizi/<slug>/0.jpg" per le immagini predefinite incluse nell'APK (file .webp, vedi [fileAsset]).
 */
object ImageStorage {
    private const val DIR = "exercise_images"

    /** Cartella delle foto scelte dall'utente. */
    fun cartella(context: Context): File = File(context.filesDir, DIR)

    /** Lato massimo (px) delle foto salvate: l'app non le mostra mai più grandi di così. */
    private const val LATO_MAX = 1280
    private const val QUALITA_JPEG = 88

    /**
     * Salva l'immagine puntata da [uri] in un file interno e ne restituisce il percorso assoluto.
     * La foto viene ridotta a [LATO_MAX] px e ricompressa (una foto della fotocamera passa da
     * diversi MB a poche centinaia di KB); ImageDecoder applica anche la rotazione EXIF, così le
     * foto scattate in verticale non risultano girate. Se la decodifica fallisce si copia l'originale.
     */
    suspend fun salva(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            val ridotta = runCatching { decodificaRidotta(context, uri) }.getOrNull()
            if (ridotta != null) {
                try {
                    file.outputStream().use { ridotta.compress(Bitmap.CompressFormat.JPEG, QUALITA_JPEG, it) }
                } finally {
                    ridotta.recycle()
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun decodificaRidotta(context: Context, uri: Uri): Bitmap {
        val sorgente = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(sorgente) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scala = minOf(1f, LATO_MAX.toFloat() / maxOf(w, h))
            if (scala < 1f) decoder.setTargetSize((w * scala).roundToInt(), (h * scala).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // serve per poterla ricomprimere
        }
    }

    /** Elimina il file immagine se presente; no-op per percorsi nulli/vuoti o asset dell'APK. */
    fun elimina(path: String?) {
        if (path.isNullOrBlank() || path.startsWith(PREFISSO_ASSET)) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}

/**
 * Carica (in modo asincrono, fuori dal main thread) l'immagine al percorso [path] — file interno
 * oppure "asset://..." — ricampionandola fino a [maxDim] px di lato per non sprecare memoria.
 * Restituisce null finché non è pronta o se assente.
 */
@Composable
fun rememberImmagine(path: String?, maxDim: Int = 512): ImageBitmap? {
    val context = LocalContext.current
    val chiave = "$maxDim|$path"
    // Se è già in cache si mostra subito, senza un fotogramma vuoto.
    return produceState<ImageBitmap?>(initialValue = cacheImmagini.get(chiave), path, maxDim) {
        value = if (path.isNullOrBlank()) null
        else cacheImmagini.get(chiave) ?: withContext(Dispatchers.IO) {
            runCatching {
                val bmp = if (path.startsWith(PREFISSO_ASSET)) {
                    decodeAssetRicampionata(context, path.removePrefix(PREFISSO_ASSET), maxDim)
                } else {
                    decodeRicampionata(path, maxDim)
                }
                bmp?.asImageBitmap()?.also { cacheImmagini.put(chiave, it) }
            }.getOrNull()
        }
    }.value
}

private fun campione(w: Int, h: Int, maxDim: Int): Int {
    var sample = 1
    while (w / sample > maxDim || h / sample > maxDim) sample *= 2
    return sample
}

private fun decodeRicampionata(path: String, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = campione(bounds.outWidth, bounds.outHeight, maxDim) }
    return BitmapFactory.decodeFile(path, opts)
}

/**
 * File reale di un'immagine predefinita. Nel DB, nei backup e nei QR il percorso resta quello
 * storico "esercizi/<slug>/0.jpg" (così non servono migrazioni e i vecchi backup valgono ancora),
 * ma nell'APK le immagini sono WebP: pesano circa la metà.
 */
internal fun fileAsset(assetPath: String): String =
    if (assetPath.endsWith(".jpg")) assetPath.removeSuffix(".jpg") + ".webp" else assetPath

private fun decodeAssetRicampionata(context: Context, percorso: String, maxDim: Int): Bitmap? {
    val assetPath = fileAsset(percorso)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = campione(bounds.outWidth, bounds.outHeight, maxDim) }
    return context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
}
