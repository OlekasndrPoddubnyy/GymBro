package com.my.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.my.gymbro.ui.theme.Carta
import com.my.gymbro.ui.theme.Inchiostro
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sfondo pieno dell'app con un velo di magnesite: chiazze di polvere dalla forma organica,
 * trascinate, con una grana fittissima dentro e i bordi che si sfaldano.
 * In chiaro la polvere diventa inchiostro tenue.
 */
@Composable
fun Modifier.sfondoApp(): Modifier {
    val fondo = MaterialTheme.colorScheme.background
    val scuro = fondo.luminance() < 0.5f
    val polvere = if (scuro) Carta else Inchiostro
    val forza = if (scuro) 0.7f else 0.35f
    return drawWithCache {
        val texture = TextureMagnesite.per(size, density, seme = 11, soglia = 0.6f, polvere, forza)
        onDrawBehind {
            drawRect(fondo)
            drawImage(texture, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
}

/**
 * Polvere di magnesite stesa sopra un riquadro (card, pannello), ritagliata con [forma].
 * Ogni riquadro ha un disegno suo: [seme] di default dipende dalla posizione nella UI, quindi è
 * diverso tra un riquadro e l'altro ma resta lo stesso tra un ridisegno e l'altro (anche scorrendo).
 * Si fonde "schiarendo" in scuro e "scurendo" in chiaro: le scritte restano intatte.
 */
@Composable
fun Modifier.magnesite(forma: Shape, seme: Int = currentCompositeKeyHash): Modifier {
    val scuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val polvere = if (scuro) Carta else Inchiostro
    val forza = if (scuro) 0.45f else 0.22f   // tenue: le schede devono leggersi bene
    val fusione = if (scuro) BlendMode.Lighten else BlendMode.Darken
    return clip(forma).drawWithCache {
        val texture = TextureMagnesite.per(size, density, seme, soglia = 0.44f, polvere, forza)
        onDrawWithContent {
            drawContent()
            drawImage(texture, dstSize = IntSize(size.width.toInt(), size.height.toInt()), blendMode = fusione)
        }
    }
}

/** Calcola le texture di polvere e tiene in memoria le ultime usate (sfondo + riquadri visibili). */
private object TextureMagnesite {
    private val cache = object : LinkedHashMap<List<Any>, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Any>, ImageBitmap>?) = size > 24
    }

    fun per(size: Size, density: Float, seme: Int, soglia: Float, polvere: Color, forza: Float): ImageBitmap {
        val k = listOf(size.width.toInt(), size.height.toInt(), density, seme, soglia, polvere, forza)
        return cache.getOrPut(k) { crea(size, density, seme, soglia, polvere, forza) }
    }

    private fun crea(size: Size, density: Float, seme: Int, soglia: Float, polvere: Color, forza: Float): ImageBitmap {
        val scala = 0.33f                        // a bassa risoluzione: ingrandita viene già sfumata
        val w = (size.width * scala).toInt().coerceAtLeast(1)
        val h = (size.height * scala).toInt().coerceAtLeast(1)
        val pxPerDp = density * scala
        val r = Random(seme)
        val g = 64
        val griglia = FloatArray(g * g) { r.nextFloat() }
        // ogni seme trascina la polvere in una direzione diversa e parte da un punto diverso del rumore
        val ang = r.nextFloat() * 2f * PI.toFloat()
        val ca = cos(ang)
        val sa = sin(ang)
        val ox = r.nextFloat() * 50f
        val oy = r.nextFloat() * 50f
        fun liscia(t: Float) = t * t * (3 - 2 * t)
        fun nodo(a: Int, b: Int) = griglia[Math.floorMod(b, g) * g + Math.floorMod(a, g)]
        fun rumore(u: Float, v: Float): Float {
            val i = floor(u).toInt()
            val j = floor(v).toInt()
            val fu = liscia(u - i)
            val fv = liscia(v - j)
            return (nodo(i, j) * (1 - fu) + nodo(i + 1, j) * fu) * (1 - fv) +
                (nodo(i, j + 1) * (1 - fu) + nodo(i + 1, j + 1) * fu) * fv
        }
        fun fbm(u: Float, v: Float) =
            rumore(u, v) * 0.6f + rumore(u * 2.3f + 17f, v * 2.3f + 5f) * 0.3f + rumore(u * 5.1f + 3f, v * 5.1f + 29f) * 0.1f
        fun gradino(a: Float, b: Float, t: Float): Float {
            val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
            return x * x * (3 - 2 * x)
        }

        val rgb = polvere.toArgb() and 0x00FFFFFF
        val pixel = IntArray(w * h)
        for (y in 0 until h) {
            val yd = y / pxPerDp
            for (x in 0 until w) {
                val xd = x / pxPerDp
                // chiazze allungate lungo la direzione del seme, come polvere trascinata
                val u = (xd * ca + yd * sa) / 120f + ox
                val v = (yd * ca - xd * sa) / 70f + oy
                val densita = gradino(soglia, soglia + 0.24f, fbm(u, v)) * (0.55f + 0.45f * fbm(xd / 18f + 40f, yd / 18f + 40f))
                var a = densita * 0.13f                                                               // velo morbido
                if (r.nextFloat() < densita * 0.35f) a += 0.04f + r.nextFloat() * 0.12f * densita   // grana leggera
                if (r.nextFloat() < 0.002f) a += 0.03f                                                // rari granelli sparsi
                val alfa = (a.coerceAtMost(0.35f) * forza * 255f).toInt()
                pixel[y * w + x] = (alfa shl 24) or rgb
            }
        }
        return android.graphics.Bitmap.createBitmap(pixel, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Colore card: un gradino sopra lo sfondo. */
@Composable
fun coloriCard(): CardColors =
    CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

/** Bordo sottilissimo: quasi invisibile in scuro, separa le card bianche in chiaro. */
@Composable
fun bordoCard(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** Card piatte: la gerarchia la danno colore e spaziatura, non le ombre. */
@Composable
fun elevazioneCard(): CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
