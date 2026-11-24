package com.my.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Tratti "a pennello" disegnati nel Canvas, nello stesso stile delle cifre a inchiostro:
 * ogni tratto è fatto di tante setole sottili, con bordi irregolari e coda secca.
 * Tutto è deterministico (seme fisso): il tratto non "trema" tra un frame e l'altro.
 */

/** Rumore liscio e ripetibile in [-1, 1]. */
private fun rumore(x: Float, seme: Int): Float =
    (sin(x * 3f + seme * 1.7f) * 0.5f + sin(x * 7.3f + seme * 4.1f) * 0.3f + sin(x * 13f + seme * 2.3f) * 0.2f)

/**
 * Anello a pennello: arco da [inizioGradi] lungo [ampiezzaGradi] (0 = ore 3, senso orario),
 * centrato nell'area di disegno, spesso [spessore] px.
 */
fun DrawScope.anelloPennello(
    colore: Color,
    inizioGradi: Float,
    ampiezzaGradi: Float,
    spessore: Float,
    seme: Int,
    setole: Int = 11,
) {
    if (ampiezzaGradi <= 0.5f) return
    val c = center
    val raggio = size.minDimension / 2f - spessore
    for (k in 0 until setole) {
        val sk = seme * 31 + k
        val rnd = Random(sk)
        val off = (k - (setole - 1) / 2f) / setole * spessore
        val bordo = abs(off) / (spessore / 2f)                     // 0 al centro, 1 sul bordo
        val larghezza = spessore / setole * (1.3f + rnd.nextFloat() * 1.1f)
        val alfa = (0.95f - bordo * 0.35f) * (0.75f + rnd.nextFloat() * 0.25f)
        // le setole esterne partono un po' dopo e finiscono prima: punta e coda sfrangiate
        val a0 = inizioGradi + rnd.nextFloat() * 5f * bordo
        val a1 = inizioGradi + ampiezzaGradi - rnd.nextFloat() * bordo * minOf(14f, ampiezzaGradi * 0.25f)
        if (a1 <= a0) continue
        val path = Path()
        var disegnando = false
        var a = a0
        while (true) {
            val coda = (a - a0) / (a1 - a0)
            // pennello secco: buchi sulle setole esterne verso la fine del tratto
            val secco = bordo > 0.45f && coda > 0.6f && rumore(a * 0.25f, sk) > 0.3f
            val rad = Math.toRadians(a.toDouble())
            val r = raggio + off +
                rumore(a / 60f, seme) * spessore * 0.22f +           // l'anello intero non è un cerchio perfetto
                rumore(a / 18f, sk) * spessore * 0.08f                // ogni setola ondeggia un poco
            val x = c.x + r * cos(rad).toFloat()
            val y = c.y + r * sin(rad).toFloat()
            when {
                secco -> disegnando = false
                !disegnando -> { path.moveTo(x, y); disegnando = true }
                else -> path.lineTo(x, y)
            }
            if (a >= a1) break
            a = minOf(a + 2f, a1)
        }
        drawPath(path, colore.copy(alpha = colore.alpha * alfa), style = Stroke(larghezza, cap = StrokeCap.Round))
    }
}

/** Tratto orizzontale a pennello che attraversa tutta l'area (es. un segno per ogni serie). */
fun DrawScope.trattoPennello(colore: Color, seme: Int, setole: Int = 7) {
    val spessore = size.height * 0.7f
    val cy = size.height / 2f
    val w = size.width
    for (k in 0 until setole) {
        val sk = seme * 17 + k
        val rnd = Random(sk)
        val off = (k - (setole - 1) / 2f) / setole * spessore
        val bordo = abs(off) / (spessore / 2f)
        val x0 = rnd.nextFloat() * w * 0.08f * bordo
        val x1 = w - rnd.nextFloat() * w * 0.18f * bordo
        val path = Path()
        var disegnando = false
        val passi = 16
        for (i in 0..passi) {
            val x = x0 + (x1 - x0) * i / passi
            val t = i / passi.toFloat()
            val secco = bordo > 0.45f && t > 0.65f && rumore(t * 9f, sk) > 0.25f
            val y = cy + off + rumore(x / w * 2f + seme, seme) * spessore * 0.15f - t * spessore * 0.12f   // leggera salita
            when {
                secco -> disegnando = false
                !disegnando -> { path.moveTo(x, y); disegnando = true }
                else -> path.lineTo(x, y)
            }
        }
        drawPath(
            path,
            colore.copy(alpha = colore.alpha * (0.95f - bordo * 0.3f) * (0.8f + rnd.nextFloat() * 0.2f)),
            style = Stroke(spessore / setole * (1.3f + rnd.nextFloat()), cap = StrokeCap.Round)
        )
    }
}

/** Larghezza di [testo] in [NumeriInchiostro], in multipli dell'altezza delle cifre. */
fun larghezzaInchiostro(testo: String): Float {
    var u = 0f
    for (c in testo) u += if (c == ':') 18f / 69.14f else 56.28f / 69.14f
    return u - (testo.length - 1).coerceAtLeast(0) * 0.06f
}

/**
 * Barra di avanzamento a pennello: la traccia è una pennellata tenue, la parte fatta è la stessa
 * pennellata in [colore], "scoperta" da sinistra fino a [progresso] (0..1).
 */
@Composable
fun BarraPennello(
    progresso: Float,
    colore: Color,
    traccia: Color,
    modifier: Modifier = Modifier,
    seme: Int = 5,
) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        trattoPennello(traccia, seme)
        val p = progresso.coerceIn(0f, 1f)
        if (p > 0f) clipRect(right = size.width * p) { trattoPennello(colore, seme) }
    }
}
