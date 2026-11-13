package com.my.gymbro.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val PREFISSO_ASSET = "asset://"

/**
 * Salvataggio delle immagini degli esercizi nello storage interno dell'app.
 * Nel DB si tiene solo il percorso: file assoluto per le foto utente, oppure
 * "asset://esercizi/<slug>/0.jpg" per le immagini predefinite incluse nell'APK.
 */
object ImageStorage {
    private const val DIR = "exercise_images"

    /** Copia l'immagine puntata da [uri] in un file interno e ne restituisce il percorso assoluto. */
    suspend fun salva(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
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
    return produceState<ImageBitmap?>(initialValue = null, path, maxDim) {
        value = if (path.isNullOrBlank()) null
        else withContext(Dispatchers.IO) {
            runCatching {
                val bmp = if (path.startsWith(PREFISSO_ASSET)) {
                    decodeAssetRicampionata(context, path.removePrefix(PREFISSO_ASSET), maxDim)
                } else {
                    decodeRicampionata(path, maxDim)
                }
                bmp?.asImageBitmap()
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

private fun decodeAssetRicampionata(context: Context, assetPath: String, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = campione(bounds.outWidth, bounds.outHeight, maxDim) }
    return context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
}
