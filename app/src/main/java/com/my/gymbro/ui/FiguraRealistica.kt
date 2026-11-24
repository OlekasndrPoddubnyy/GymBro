package com.my.gymbro.ui

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Region
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.my.gymbro.R
import com.my.gymbro.data.Figura

// Le immagini sono il riquadro (92, 36)-(804, 1566) del render 896 x 1600.
private const val RITAGLIO_X = 92f
private const val RITAGLIO_Y = 36f
private const val RITAGLIO_W = 712f
private const val RITAGLIO_H = 1530f
private const val ASSE = 448f

/** Raggio di tolleranza del tocco, per le zone sottili. */
private const val TOLLERANZA = 14f

internal const val PROPORZIONE_FIGURA = RITAGLIO_W / RITAGLIO_H

internal class Zona(val muscolo: Muscolo, val d: String)

private class ZonaPronta(val muscolo: Muscolo, val path: Path) {
    val bordi: Rect = path.getBounds()
    private val regione = Region().apply { setPath(path.asAndroidPath(), Region(0, 0, 896, 1600)) }
    fun contiene(x: Float, y: Float): Boolean = regione.contains(x.toInt(), y.toInt())
}

internal class Vista(val etichetta: String, @param:DrawableRes val immagine: Int, sorgente: List<Zona>) {
    private val specchio = Matrix().apply { setScale(-1f, 1f, ASSE, 0f) }

    private val zone: List<ZonaPronta> = sorgente.flatMap { z ->
        val destra = PathParser().parsePathString(z.d).toPath()
        val sinistra = android.graphics.Path(destra.asAndroidPath()).apply { transform(specchio) }.asComposePath()
        listOf(ZonaPronta(z.muscolo, destra), ZonaPronta(z.muscolo, sinistra))
    }

    fun tracciati(muscoli: Set<Muscolo>): List<Path> = zone.filter { it.muscolo in muscoli }.map { it.path }

    /** Muscolo nel punto toccato; [x] e [y] vanno da 0 a 1 sull'immagine. */
    fun muscoloIn(x: Float, y: Float): Muscolo? {
        val px = RITAGLIO_X + x * RITAGLIO_W
        val py = RITAGLIO_Y + y * RITAGLIO_H
        zone.lastOrNull { it.contiene(px, py) }?.let { return it.muscolo }
        // fuori da ogni zona: la più vicina entro la tolleranza
        val passi = listOf(-1f, 0f, 1f)
        for (raggio in listOf(TOLLERANZA / 2, TOLLERANZA)) {
            for (dx in passi) for (dy in passi) {
                zone.lastOrNull { it.contiene(px + dx * raggio, py + dy * raggio) }?.let { return it.muscolo }
            }
        }
        return null
    }
}

private val visteUomo by lazy {
    listOf(
        Vista("Fronte", R.drawable.figura_uomo_fronte, ZONE_UOMO_FRONTE),
        Vista("Retro", R.drawable.figura_uomo_retro, ZONE_UOMO_RETRO),
    )
}
private val visteDonna by lazy {
    listOf(
        Vista("Fronte", R.drawable.figura_donna_fronte, ZONE_DONNA_FRONTE),
        Vista("Retro", R.drawable.figura_donna_retro, ZONE_DONNA_RETRO),
    )
}

internal fun visteDi(figura: Figura): List<Vista> = if (figura == Figura.UOMO) visteUomo else visteDonna

internal const val FORZA_PRINCIPALE = 0.92f
internal const val FORZA_SECONDARIO = 0.40f

/**
 * Figura con i muscoli tinti: dentro ogni zona la pelle prende il colore mantenendo luci e ombre,
 * e la tinta non esce mai dal corpo (usa l'alfa dell'immagine).
 */
internal fun coloraFigura(base: ImageBitmap, vista: Vista, coinvolti: MuscoliCoinvolti, colore: Color): ImageBitmap {
    val originale = base.asAndroidBitmap()
    val risultato = originale.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val tela = android.graphics.Canvas(risultato)
    val scala = originale.width / RITAGLIO_W
    tela.scale(scala, scala)
    tela.translate(-RITAGLIO_X, -RITAGLIO_Y)

    val riquadro = RectF(RITAGLIO_X, RITAGLIO_Y, RITAGLIO_X + RITAGLIO_W, RITAGLIO_Y + RITAGLIO_H)
    val k = 1.0f
    val luminanza = floatArrayOf(0.30f * k, 0.59f * k, 0.11f * k)
    // il termine fisso schiarisce la stoffa scura: i glutei devono leggersi sopra i pantaloncini
    fun riga(c: Float) = floatArrayOf(luminanza[0] * c, luminanza[1] * c, luminanza[2] * c, 0f, 0.44f * 255f * c)
    val tinta = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(
            riga(colore.red) + riga(colore.green) + riga(colore.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    fun tingi(muscoli: Set<Muscolo>, forza: Float) {
        val tracciati = vista.tracciati(muscoli)
        if (tracciati.isEmpty()) return
        val maschera = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.argb((255 * forza).toInt(), 255, 255, 255)
            maskFilter = BlurMaskFilter(2.5f, BlurMaskFilter.Blur.NORMAL)
        }
        val livello = tela.saveLayer(riquadro, null)
        tracciati.forEach { tela.drawPath(it.asAndroidPath(), maschera) }
        tela.drawBitmap(originale, null, riquadro, tinta)
        tela.restoreToCount(livello)
    }
    tingi(coinvolti.secondari - coinvolti.principali, FORZA_SECONDARIO)
    tingi(coinvolti.principali, FORZA_PRINCIPALE)
    return risultato.asImageBitmap()
}

/** Sopra l'immagine: alone pulsante dei principali, bordo dei secondari, contorno del muscolo toccato. */
internal fun DrawScope.disegnaEvidenze(
    vista: Vista,
    coinvolti: MuscoliCoinvolti,
    principale: Color,
    secondario: Color,
    selezione: Color,
    bagliore: Float,
    selezionato: Muscolo?,
) {
    val s = size.width / RITAGLIO_W
    withTransform({
        scale(s, s, pivot = Offset.Zero)
        translate(-RITAGLIO_X, -RITAGLIO_Y)
    }) {
        val alone = Stroke(width = 9f, join = StrokeJoin.Round)
        vista.tracciati(coinvolti.principali).forEach { drawPath(it, principale.copy(alpha = bagliore), style = alone) }
        val bordo = Stroke(width = 3f, join = StrokeJoin.Round)
        vista.tracciati(coinvolti.secondari - coinvolti.principali).forEach { drawPath(it, secondario, style = bordo) }
        if (selezionato != null) {
            val contorno = Stroke(width = 5f, join = StrokeJoin.Round)
            vista.tracciati(setOf(selezionato)).forEach { drawPath(it, selezione, style = contorno) }
        }
    }
}
