package com.my.gymbro.ui

import android.graphics.Matrix
import android.graphics.Region
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.ui.Muscolo.*

/** Zone muscolari disegnate nella mappa. */
enum class Muscolo(val etichetta: String) {
    PETTO("Petto"), DELTOIDI_ANT("Deltoidi anteriori"), DELTOIDI_LAT("Deltoidi laterali"), DELTOIDI_POST("Deltoidi posteriori"),
    CUFFIA("Cuffia dei rotatori"),
    BICIPITI("Bicipiti"), TRICIPITI("Tricipiti"), AVAMBRACCI("Avambracci"),
    ADDOME("Addome"), OBLIQUI("Obliqui"), TRAPEZIO("Trapezio"), DORSALI("Dorsali"), LOMBARI("Lombari"),
    GLUTEI("Glutei"), QUADRICIPITI("Quadricipiti"), ADDUTTORI("Adduttori"), FEMORALI("Femorali"), POLPACCI("Polpacci")
}

data class MuscoliCoinvolti(val principali: Set<Muscolo>, val secondari: Set<Muscolo> = emptySet()) {
    /** Es. "Femorali, Glutei" */
    val testoPrincipali: String get() = principali.joinToString(", ") { it.etichetta }
    val testoSecondari: String get() = secondari.joinToString(", ") { it.etichetta }
}

private fun p(vararg m: Muscolo): Set<Muscolo> = m.toSet()
private fun mc(principali: Set<Muscolo>, secondari: Set<Muscolo> = emptySet()) = MuscoliCoinvolti(principali, secondari)

/** Muscoli coinvolti per ogni esercizio della libreria (chiave: nome in minuscolo). */
private val MUSCOLI_PER_ESERCIZIO: Map<String, MuscoliCoinvolti> = mapOf(
    // ---------- Petto ----------
    "panca piana" to mc(p(PETTO), p(DELTOIDI_ANT, TRICIPITI)),
    "panca inclinata con bilanciere" to mc(p(PETTO, DELTOIDI_ANT), p(TRICIPITI)),
    "panca declinata con bilanciere" to mc(p(PETTO), p(TRICIPITI)),
    "panca inclinata manubri" to mc(p(PETTO, DELTOIDI_ANT), p(TRICIPITI)),
    "panca piana con manubri" to mc(p(PETTO), p(DELTOIDI_ANT, TRICIPITI)),
    "panca declinata con manubri" to mc(p(PETTO), p(TRICIPITI)),
    "croci su panca piana" to mc(p(PETTO), p(DELTOIDI_ANT)),
    "croci su panca inclinata" to mc(p(PETTO), p(DELTOIDI_ANT)),
    "pullover con manubrio" to mc(p(PETTO, DORSALI), p(TRICIPITI)),
    "chest press" to mc(p(PETTO), p(DELTOIDI_ANT, TRICIPITI)),
    "chest press inclinata" to mc(p(PETTO, DELTOIDI_ANT), p(TRICIPITI)),
    "pectoral machine" to mc(p(PETTO), p(DELTOIDI_ANT)),
    "croci ai cavi" to mc(p(PETTO), p(DELTOIDI_ANT)),
    "croci ai cavi bassi" to mc(p(PETTO, DELTOIDI_ANT)),
    "chest press ai cavi" to mc(p(PETTO), p(DELTOIDI_ANT, TRICIPITI, ADDOME)),
    "piegamenti sulle braccia" to mc(p(PETTO, TRICIPITI), p(DELTOIDI_ANT, ADDOME)),
    "piegamenti su rialzo" to mc(p(PETTO, TRICIPITI), p(DELTOIDI_ANT, ADDOME)),
    "dip alle parallele" to mc(p(PETTO, TRICIPITI), p(DELTOIDI_ANT)),
    // ---------- Schiena ----------
    "stacco da terra" to mc(p(GLUTEI, FEMORALI, LOMBARI), p(QUADRICIPITI, TRAPEZIO, DORSALI, AVAMBRACCI)),
    "rematore con bilanciere" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST, LOMBARI)),
    "rematore presa inversa" to mc(p(DORSALI), p(BICIPITI, TRAPEZIO, LOMBARI)),
    "rematore con manubrio" to mc(p(DORSALI), p(BICIPITI, DELTOIDI_POST, TRAPEZIO)),
    "lat machine" to mc(p(DORSALI), p(BICIPITI, DELTOIDI_POST, TRAPEZIO)),
    "lat machine presa inversa" to mc(p(DORSALI, BICIPITI), p(TRAPEZIO)),
    "lat machine presa stretta" to mc(p(DORSALI), p(BICIPITI, TRAPEZIO)),
    "pulley basso" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST)),
    "rematore alla macchina" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST)),
    "trazioni assistite alla macchina" to mc(p(DORSALI), p(BICIPITI, AVAMBRACCI)),
    "iperestensioni" to mc(p(LOMBARI), p(GLUTEI, FEMORALI)),
    "rematore ai cavi" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI)),
    "pulldown a un braccio ai cavi" to mc(p(DORSALI), p(BICIPITI)),
    "pullover ai cavi" to mc(p(DORSALI), p(TRICIPITI, PETTO)),
    "trazioni alla sbarra" to mc(p(DORSALI), p(BICIPITI, AVAMBRACCI, ADDOME)),
    "trazioni presa inversa" to mc(p(DORSALI, BICIPITI), p(AVAMBRACCI)),
    "trazioni presa larga" to mc(p(DORSALI), p(BICIPITI, DELTOIDI_POST)),
    "australian pull-up" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, ADDOME)),
    // ---------- Spalle ----------
    "lento avanti" to mc(p(DELTOIDI_ANT), p(DELTOIDI_LAT, TRICIPITI, TRAPEZIO, ADDOME)),
    "tirate al mento con bilanciere" to mc(p(DELTOIDI_LAT, TRAPEZIO), p(DELTOIDI_ANT, BICIPITI)),
    "scrollate con bilanciere" to mc(p(TRAPEZIO), p(AVAMBRACCI)),
    "lento avanti con manubri" to mc(p(DELTOIDI_ANT), p(DELTOIDI_LAT, TRICIPITI, TRAPEZIO)),
    "arnold press" to mc(p(DELTOIDI_ANT, DELTOIDI_LAT), p(TRICIPITI)),
    "alzate laterali" to mc(p(DELTOIDI_LAT), p(TRAPEZIO, DELTOIDI_ANT)),
    "alzate frontali" to mc(p(DELTOIDI_ANT), p(PETTO)),
    "alzate posteriori" to mc(p(DELTOIDI_POST), p(TRAPEZIO)),
    "scrollate con manubri" to mc(p(TRAPEZIO), p(AVAMBRACCI)),
    "shoulder press alla macchina" to mc(p(DELTOIDI_ANT), p(DELTOIDI_LAT, TRICIPITI)),
    "alzate laterali alla macchina" to mc(p(DELTOIDI_LAT), p(TRAPEZIO)),
    "reverse fly alla macchina" to mc(p(DELTOIDI_POST), p(TRAPEZIO)),
    "alzate laterali ai cavi" to mc(p(DELTOIDI_LAT), p(TRAPEZIO)),
    "alzate posteriori ai cavi" to mc(p(DELTOIDI_POST), p(TRAPEZIO)),
    "tirate al mento ai cavi" to mc(p(DELTOIDI_LAT, TRAPEZIO), p(DELTOIDI_ANT, BICIPITI)),
    "face pull ai cavi" to mc(p(DELTOIDI_POST, TRAPEZIO), p(CUFFIA)),
    "pike push-up" to mc(p(DELTOIDI_ANT), p(TRICIPITI, PETTO)),
    // ---------- Bicipiti ----------
    "curl con bilanciere" to mc(p(BICIPITI), p(AVAMBRACCI)),
    "curl con bilanciere ez" to mc(p(BICIPITI), p(AVAMBRACCI)),
    "curl presa inversa" to mc(p(AVAMBRACCI, BICIPITI)),
    "curl con manubri" to mc(p(BICIPITI), p(AVAMBRACCI)),
    "curl alternato" to mc(p(BICIPITI), p(AVAMBRACCI)),
    "curl a martello" to mc(p(BICIPITI, AVAMBRACCI)),
    "curl concentrato" to mc(p(BICIPITI)),
    "curl su panca inclinata" to mc(p(BICIPITI), p(AVAMBRACCI)),
    "curl alla panca scott" to mc(p(BICIPITI), p(AVAMBRACCI)),
    "curl alla macchina" to mc(p(BICIPITI)),
    "curl ai cavi" to mc(p(BICIPITI), p(AVAMBRACCI)),
    "curl a martello ai cavi" to mc(p(BICIPITI, AVAMBRACCI)),
    // ---------- Tricipiti ----------
    "distensioni presa stretta" to mc(p(TRICIPITI, PETTO), p(DELTOIDI_ANT)),
    "french press" to mc(p(TRICIPITI)),
    "estensioni sopra la testa con manubrio" to mc(p(TRICIPITI)),
    "kickback con manubri" to mc(p(TRICIPITI)),
    "pushdown ai cavi" to mc(p(TRICIPITI)),
    "pushdown ai cavi con barra" to mc(p(TRICIPITI)),
    "pushdown presa inversa" to mc(p(TRICIPITI), p(AVAMBRACCI)),
    "estensioni sopra la testa ai cavi" to mc(p(TRICIPITI)),
    "tricipiti alla macchina" to mc(p(TRICIPITI)),
    "dip alla macchina" to mc(p(TRICIPITI), p(PETTO, DELTOIDI_ANT)),
    "dip tra le panche" to mc(p(TRICIPITI), p(DELTOIDI_ANT, PETTO)),
    "piegamenti diamante" to mc(p(TRICIPITI, PETTO), p(DELTOIDI_ANT)),
    // ---------- Gambe ----------
    "squat" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI, LOMBARI)),
    "squat frontale" to mc(p(QUADRICIPITI), p(GLUTEI, ADDOME)),
    "affondi con bilanciere" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI)),
    "stacco gambe tese" to mc(p(FEMORALI), p(GLUTEI, LOMBARI)),
    "stacco rumeno" to mc(p(FEMORALI, GLUTEI), p(LOMBARI)),
    "good morning" to mc(p(FEMORALI, LOMBARI), p(GLUTEI)),
    "goblet squat con manubrio" to mc(p(QUADRICIPITI, GLUTEI), p(ADDUTTORI, ADDOME)),
    "affondi con manubri" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI)),
    "affondi in camminata" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI)),
    "bulgarian split squat" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI)),
    "step-up con manubri" to mc(p(QUADRICIPITI, GLUTEI), p(POLPACCI)),
    "leg press" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI)),
    "pressa 45°" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI)),
    "hack squat alla macchina" to mc(p(QUADRICIPITI), p(GLUTEI)),
    "leg extension" to mc(p(QUADRICIPITI)),
    "leg curl sdraiato" to mc(p(FEMORALI), p(POLPACCI)),
    "leg curl seduto" to mc(p(FEMORALI), p(POLPACCI)),
    "adductor machine" to mc(p(ADDUTTORI)),
    "squat al multipower" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI)),
    "squat a corpo libero" to mc(p(QUADRICIPITI, GLUTEI)),
    "affondi a corpo libero" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI)),
    "pistol squat" to mc(p(QUADRICIPITI, GLUTEI), p(ADDOME)),
    "wall sit" to mc(p(QUADRICIPITI), p(GLUTEI)),
    // ---------- Glutei ----------
    "hip thrust" to mc(p(GLUTEI), p(FEMORALI)),
    "stacco sumo" to mc(p(GLUTEI, ADDUTTORI, QUADRICIPITI), p(FEMORALI, LOMBARI)),
    "ponte glutei con bilanciere" to mc(p(GLUTEI), p(FEMORALI)),
    "hip thrust con manubrio" to mc(p(GLUTEI), p(FEMORALI)),
    "hip thrust alla macchina" to mc(p(GLUTEI), p(FEMORALI)),
    "abductor machine" to mc(p(GLUTEI)),
    "glute machine" to mc(p(GLUTEI), p(FEMORALI)),
    "kickback ai cavi" to mc(p(GLUTEI), p(FEMORALI)),
    "pull-through ai cavi" to mc(p(GLUTEI, FEMORALI), p(LOMBARI)),
    "abduzione ai cavi" to mc(p(GLUTEI)),
    "ponte glutei a corpo libero" to mc(p(GLUTEI), p(FEMORALI)),
    "slanci posteriori" to mc(p(GLUTEI), p(FEMORALI)),
    // ---------- Polpacci ----------
    "calf raise" to mc(p(POLPACCI)),
    "calf raise seduto" to mc(p(POLPACCI)),
    "calf alla pressa" to mc(p(POLPACCI)),
    "calf raise con manubri" to mc(p(POLPACCI)),
    "calf raise a corpo libero" to mc(p(POLPACCI)),
    // ---------- Core ----------
    "plank" to mc(p(ADDOME), p(OBLIQUI, DELTOIDI_ANT)),
    "plank laterale" to mc(p(OBLIQUI), p(ADDOME, GLUTEI)),
    "crunch" to mc(p(ADDOME)),
    "crunch inverso" to mc(p(ADDOME), p(OBLIQUI)),
    "sit-up" to mc(p(ADDOME), p(QUADRICIPITI)),
    "russian twist" to mc(p(OBLIQUI), p(ADDOME)),
    "mountain climber" to mc(p(ADDOME), p(QUADRICIPITI, DELTOIDI_ANT)),
    "sollevamento gambe" to mc(p(ADDOME), p(QUADRICIPITI)),
    "sollevamento gambe alla sbarra" to mc(p(ADDOME), p(AVAMBRACCI, DORSALI)),
    "ab wheel rollout" to mc(p(ADDOME), p(DORSALI, DELTOIDI_ANT)),
    "crunch alla macchina" to mc(p(ADDOME)),
    "rotazione del busto alla macchina" to mc(p(OBLIQUI)),
    "crunch ai cavi" to mc(p(ADDOME), p(OBLIQUI)),
    "pallof press ai cavi" to mc(p(OBLIQUI, ADDOME)),
    "woodchop ai cavi" to mc(p(OBLIQUI), p(ADDOME, DELTOIDI_ANT)),
    // ---------- Stretching (muscoli allungati) ----------
    "stretching flessori dell'anca" to mc(p(QUADRICIPITI), p(ADDOME)),
    "stretching pettorali al muro" to mc(p(PETTO), p(DELTOIDI_ANT)),
    "stretching femorali" to mc(p(FEMORALI), p(POLPACCI)),
    "stretching glutei a figura 4" to mc(p(GLUTEI), p(LOMBARI)),
    "stretching quadricipiti in piedi" to mc(p(QUADRICIPITI)),
    "stretching dorsali alla panca" to mc(p(DORSALI), p(TRICIPITI)),
    "stretching tricipiti e spalle" to mc(p(TRICIPITI), p(DORSALI)),
    "posizione del bambino" to mc(p(LOMBARI, DORSALI), p(GLUTEI)),
    "gatto-mucca" to mc(p(LOMBARI), p(ADDOME)),
    "rotazione toracica a terra" to mc(p(OBLIQUI, PETTO), p(DORSALI)),
    "cobra" to mc(p(ADDOME), p(LOMBARI)),
    // ---------- Cardio ----------
    "tapis roulant" to mc(p(QUADRICIPITI, POLPACCI), p(GLUTEI, FEMORALI)),
    "cyclette" to mc(p(QUADRICIPITI), p(GLUTEI, FEMORALI, POLPACCI)),
    "cyclette orizzontale" to mc(p(QUADRICIPITI), p(GLUTEI, FEMORALI, POLPACCI)),
    "ellittica" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, DELTOIDI_ANT)),
    "vogatore" to mc(p(DORSALI, QUADRICIPITI), p(GLUTEI, BICIPITI, FEMORALI)),
    "stairmaster" to mc(p(GLUTEI, QUADRICIPITI), p(POLPACCI)),
    "air bike" to mc(p(QUADRICIPITI), p(DELTOIDI_ANT, DORSALI, GLUTEI)),
    "skierg" to mc(p(DORSALI, TRICIPITI), p(ADDOME)),
    "corsa" to mc(p(QUADRICIPITI, POLPACCI), p(GLUTEI, FEMORALI)),
    "salto con la corda" to mc(p(POLPACCI), p(QUADRICIPITI, DELTOIDI_ANT)),
    "burpees" to mc(p(QUADRICIPITI, PETTO), p(DELTOIDI_ANT, TRICIPITI, ADDOME)),
    "jumping jacks" to mc(p(POLPACCI, DELTOIDI_LAT), p(QUADRICIPITI, GLUTEI)),

    // ========== Catalogo ampliato (seed v12) ==========
    // ---------- Petto ----------
    "floor press con bilanciere" to mc(p(PETTO, TRICIPITI), p(DELTOIDI_ANT)),
    "squeeze press con manubri" to mc(p(PETTO), p(TRICIPITI, DELTOIDI_ANT)),
    "chest press declinata alla macchina" to mc(p(PETTO), p(TRICIPITI)),
    "panca piana al multipower" to mc(p(PETTO), p(DELTOIDI_ANT, TRICIPITI)),
    "panca inclinata al multipower" to mc(p(PETTO, DELTOIDI_ANT), p(TRICIPITI)),
    "croci ai cavi a metà altezza" to mc(p(PETTO), p(DELTOIDI_ANT)),
    "croci ai cavi a un braccio" to mc(p(PETTO), p(DELTOIDI_ANT)),
    "piegamenti sulle ginocchia" to mc(p(PETTO, TRICIPITI), p(DELTOIDI_ANT)),
    "piegamenti presa larga" to mc(p(PETTO), p(DELTOIDI_ANT, TRICIPITI, ADDOME)),
    "piegamenti declinati" to mc(p(PETTO, DELTOIDI_ANT), p(TRICIPITI, ADDOME)),
    // ---------- Schiena ----------
    "rack pull" to mc(p(LOMBARI, TRAPEZIO, GLUTEI), p(DORSALI, FEMORALI, AVAMBRACCI)),
    "rematore pendlay" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST, LOMBARI)),
    "rematore t-bar" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST, LOMBARI)),
    "rematore con due manubri" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST, LOMBARI)),
    "rematore su panca inclinata con manubri" to mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST)),
    "vertical traction" to mc(p(DORSALI), p(BICIPITI, DELTOIDI_POST, TRAPEZIO)),
    "upper back alla macchina" to mc(p(TRAPEZIO, DELTOIDI_POST), p(DORSALI, BICIPITI)),
    "lower back alla macchina" to mc(p(LOMBARI), p(GLUTEI)),
    "rematore a un braccio ai cavi" to mc(p(DORSALI), p(BICIPITI, DELTOIDI_POST, OBLIQUI)),
    "trazioni presa neutra" to mc(p(DORSALI), p(BICIPITI, AVAMBRACCI)),
    "superman" to mc(p(LOMBARI), p(GLUTEI, DELTOIDI_POST)),
    // ---------- Spalle ----------
    "push press" to mc(p(DELTOIDI_ANT, TRICIPITI), p(DELTOIDI_LAT, QUADRICIPITI, GLUTEI, TRAPEZIO)),
    "landmine press" to mc(p(DELTOIDI_ANT), p(PETTO, TRICIPITI, ADDOME)),
    "tirate al mento con manubri" to mc(p(DELTOIDI_LAT, TRAPEZIO), p(DELTOIDI_ANT, BICIPITI)),
    "lento avanti al multipower" to mc(p(DELTOIDI_ANT), p(DELTOIDI_LAT, TRICIPITI, TRAPEZIO)),
    "alzate frontali ai cavi" to mc(p(DELTOIDI_ANT), p(PETTO)),
    "y-raise ai cavi" to mc(p(DELTOIDI_LAT, TRAPEZIO), p(DELTOIDI_POST)),
    "scrollate ai cavi" to mc(p(TRAPEZIO), p(AVAMBRACCI)),
    "rotazione esterna ai cavi" to mc(p(CUFFIA), p(DELTOIDI_POST)),
    "rotazione interna ai cavi" to mc(p(CUFFIA), p(PETTO, DELTOIDI_ANT)),
    "piegamenti in verticale al muro" to mc(p(DELTOIDI_ANT, TRICIPITI), p(TRAPEZIO, ADDOME)),
    // ---------- Bicipiti ----------
    "curl zottman" to mc(p(BICIPITI, AVAMBRACCI)),
    "curl a ragno" to mc(p(BICIPITI)),
    "curl ai cavi alti" to mc(p(BICIPITI)),
    "curl bayesian ai cavi" to mc(p(BICIPITI), p(AVAMBRACCI)),
    // ---------- Tricipiti ----------
    "french press con manubri" to mc(p(TRICIPITI)),
    "pushdown a un braccio ai cavi" to mc(p(TRICIPITI)),
    "kickback tricipiti ai cavi" to mc(p(TRICIPITI)),
    "dip assistiti alla macchina" to mc(p(TRICIPITI, PETTO), p(DELTOIDI_ANT)),
    "distensioni presa stretta al multipower" to mc(p(TRICIPITI, PETTO), p(DELTOIDI_ANT)),
    // ---------- Gambe ----------
    "box squat" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI, LOMBARI)),
    "affondi inversi con bilanciere" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI)),
    "bulgarian split squat con bilanciere" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI)),
    "step-up con bilanciere" to mc(p(QUADRICIPITI, GLUTEI), p(POLPACCI)),
    "girata al petto" to mc(p(GLUTEI, QUADRICIPITI, TRAPEZIO), p(FEMORALI, DELTOIDI_ANT, LOMBARI)),
    "squat con manubri" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, AVAMBRACCI)),
    "affondi inversi con manubri" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, ADDUTTORI)),
    "affondi laterali con manubri" to mc(p(QUADRICIPITI, ADDUTTORI, GLUTEI)),
    "stacco rumeno con manubri" to mc(p(FEMORALI, GLUTEI), p(LOMBARI)),
    "multi hip adduzione" to mc(p(ADDUTTORI)),
    "multi hip flessione dell'anca" to mc(p(QUADRICIPITI), p(ADDOME)),
    "affondi al multipower" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI)),
    "leg curl ai cavi" to mc(p(FEMORALI), p(POLPACCI)),
    "adduzione ai cavi" to mc(p(ADDUTTORI)),
    "stacco rumeno ai cavi" to mc(p(FEMORALI, GLUTEI), p(LOMBARI)),
    "squat jump" to mc(p(QUADRICIPITI, GLUTEI), p(POLPACCI)),
    "affondi inversi a corpo libero" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI)),
    "affondi laterali a corpo libero" to mc(p(QUADRICIPITI, ADDUTTORI, GLUTEI)),
    "nordic curl" to mc(p(FEMORALI), p(GLUTEI, POLPACCI)),
    "box jump" to mc(p(QUADRICIPITI, GLUTEI), p(POLPACCI, FEMORALI)),
    // ---------- Glutei ----------
    "sumo squat con manubrio" to mc(p(GLUTEI, ADDUTTORI, QUADRICIPITI)),
    "stacco a una gamba con manubri" to mc(p(GLUTEI, FEMORALI), p(LOMBARI)),
    "hip thrust al multipower" to mc(p(GLUTEI), p(FEMORALI)),
    "multi hip slancio posteriore" to mc(p(GLUTEI), p(FEMORALI)),
    "multi hip abduzione" to mc(p(GLUTEI)),
    "ponte glutei a una gamba" to mc(p(GLUTEI), p(FEMORALI)),
    "clamshell" to mc(p(GLUTEI)),
    "fire hydrant" to mc(p(GLUTEI)),
    // ---------- Polpacci ----------
    "rotary calf" to mc(p(POLPACCI)),
    "calf al multipower" to mc(p(POLPACCI)),
    "calf raise con bilanciere" to mc(p(POLPACCI)),
    "calf raise a una gamba con manubrio" to mc(p(POLPACCI)),
    // ---------- Core ----------
    "plank con tocco spalla" to mc(p(ADDOME, OBLIQUI), p(DELTOIDI_ANT, TRICIPITI)),
    "crunch obliquo" to mc(p(OBLIQUI), p(ADDOME)),
    "bicycle crunch" to mc(p(ADDOME, OBLIQUI)),
    "v-up" to mc(p(ADDOME), p(QUADRICIPITI)),
    "flutter kick" to mc(p(ADDOME), p(QUADRICIPITI)),
    "ginocchia al petto alla sbarra" to mc(p(ADDOME), p(AVAMBRACCI, DORSALI)),
    "dead bug" to mc(p(ADDOME), p(OBLIQUI)),
    "bird dog" to mc(p(LOMBARI, GLUTEI), p(ADDOME, DELTOIDI_ANT)),
    "hollow hold" to mc(p(ADDOME), p(QUADRICIPITI)),
    "total abdominal" to mc(p(ADDOME), p(OBLIQUI)),
    "woodchop dal basso ai cavi" to mc(p(OBLIQUI), p(ADDOME, DELTOIDI_ANT, GLUTEI)),
    "flessioni laterali ai cavi" to mc(p(OBLIQUI), p(ADDOME)),
    "rollout con bilanciere" to mc(p(ADDOME), p(DORSALI, DELTOIDI_ANT)),
    "rotazioni landmine" to mc(p(OBLIQUI), p(ADDOME, DELTOIDI_ANT, GLUTEI)),
    "flessioni laterali con manubrio" to mc(p(OBLIQUI), p(ADDOME)),
    "farmer walk" to mc(p(AVAMBRACCI, TRAPEZIO), p(ADDOME, OBLIQUI)),
    // ---------- Stretching ----------
    "stretching polpacci al muro" to mc(p(POLPACCI)),
    "stretching adduttori a farfalla" to mc(p(ADDUTTORI)),
    "stretching laterale del busto" to mc(p(OBLIQUI), p(DORSALI)),
    // ---------- Cardio ----------
    "skillmill" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, POLPACCI)),
    "spinning" to mc(p(QUADRICIPITI), p(GLUTEI, FEMORALI, POLPACCI)),
    "ellittica a passo variabile" to mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI, DELTOIDI_ANT)),
    "stepper" to mc(p(GLUTEI, QUADRICIPITI), p(POLPACCI)),
    "ergometro per braccia" to mc(p(DELTOIDI_ANT, DORSALI), p(BICIPITI, TRICIPITI)),
    "skip a ginocchia alte" to mc(p(QUADRICIPITI, POLPACCI), p(ADDOME, GLUTEI)),
)

/**
 * Muscoli coinvolti in un esercizio: tabella per nome per tutti gli esercizi della libreria;
 * per quelli creati dall'utente regole per parola chiave e, in ultimo, il gruppo muscolare.
 * Per lo stretching indica i muscoli allungati.
 */
fun muscoliCoinvolti(nome: String, gruppo: GruppoMuscolare?): MuscoliCoinvolti? {
    val n = nome.trim().lowercase()
    MUSCOLI_PER_ESERCIZIO[n]?.let { return it }
    if (gruppo == GruppoMuscolare.STRETCHING) return null
    when {
        "calf" in n -> return mc(p(POLPACCI))
        "stacco rumeno" in n || "gambe tese" in n -> return mc(p(FEMORALI, GLUTEI), p(LOMBARI))
        "stacco" in n -> return mc(p(GLUTEI, FEMORALI, LOMBARI), p(QUADRICIPITI, TRAPEZIO))
        "leg curl" in n -> return mc(p(FEMORALI))
        "leg extension" in n -> return mc(p(QUADRICIPITI))
        "hip thrust" in n || "ponte" in n -> return mc(p(GLUTEI), p(FEMORALI))
        "squat" in n || "affondi" in n || "pressa" in n || "leg press" in n -> return mc(p(QUADRICIPITI, GLUTEI), p(FEMORALI))
        "face pull" in n || "posteriori" in n || "reverse" in n -> return mc(p(DELTOIDI_POST), p(TRAPEZIO))
        "alzate" in n && "lateral" in n -> return mc(p(DELTOIDI_LAT), p(TRAPEZIO))
        "alzate" in n -> return mc(p(DELTOIDI_ANT), p(TRAPEZIO))
        "lat machine" in n || "trazioni" in n || "pulldown" in n -> return mc(p(DORSALI), p(BICIPITI))
        "rematore" in n || "pulley" in n -> return mc(p(DORSALI, TRAPEZIO), p(BICIPITI))
        "curl" in n -> return mc(p(BICIPITI), p(AVAMBRACCI))
        "pushdown" in n || "french" in n || "estension" in n -> return mc(p(TRICIPITI))
        "plank" in n -> return mc(p(ADDOME), p(OBLIQUI))
        "crunch" in n -> return mc(p(ADDOME))
    }
    return when (gruppo) {
        GruppoMuscolare.PETTO -> mc(p(PETTO), p(DELTOIDI_ANT, TRICIPITI))
        GruppoMuscolare.SCHIENA -> mc(p(DORSALI, TRAPEZIO), p(BICIPITI, DELTOIDI_POST))
        GruppoMuscolare.SPALLE -> mc(p(DELTOIDI_ANT, DELTOIDI_LAT), p(TRAPEZIO, TRICIPITI))
        GruppoMuscolare.BICIPITI -> mc(p(BICIPITI), p(AVAMBRACCI))
        GruppoMuscolare.TRICIPITI -> mc(p(TRICIPITI))
        GruppoMuscolare.GAMBE -> mc(p(QUADRICIPITI), p(GLUTEI, FEMORALI))
        GruppoMuscolare.GLUTEI -> mc(p(GLUTEI), p(FEMORALI))
        GruppoMuscolare.POLPACCI -> mc(p(POLPACCI))
        GruppoMuscolare.CORE -> mc(p(ADDOME), p(OBLIQUI, LOMBARI))
        GruppoMuscolare.CARDIO -> mc(p(QUADRICIPITI, POLPACCI), p(GLUTEI, FEMORALI))
        GruppoMuscolare.STRETCHING, null -> null
    }
}

// ---------------- disegno ----------------
// Figura atletica in un riquadro 100 × 200. Ogni tracciato descrive la metà sinistra:
// l'altra metà si ottiene specchiando rispetto all'asse centrale (x = 50).

private const val SAGOMA =
    "M50,2 C44.5,2 41,6.5 41,12.5 C41,18 43,22 45,24.5 L45.5,28 C41,29.5 35,30 30,31.5 C24,33 20,38 19.5,45 C19,51 18,57 18.5,62 C18.5,64 18,66 17.5,67 C15.5,73 14,80 14,86 C14,89 14,91 14.5,93 C13,96 13.5,100 16,101 C18.5,101.5 19.5,99 19,96 C20,90 22,83 24,76 C25,72 26,69 26.5,66 C27.5,60 28.5,53 29.5,47 C31,56 33,66 35,76 C35.5,80 35.5,84 35,87 C34,91 33,94 33,98 C31,108 30.5,120 32,132 C33,138 34,142 35,145 C33,152 32.5,160 34,168 C35,175 36,182 36.5,188 C35,191 34,194 35,196 L43.5,196.5 C44,193 43.5,190 43.5,187 C44.5,180 45.5,172 46,165 C46.5,158 46,152 45,148 C45.5,144 46.5,141 47,138 C48,128 48.5,118 49,112 L50,109 Z"

/**
 * Zona disegnata: [muscolo] null = dettaglio anatomico non allenato (collo, tibiale, dentato…),
 * sempre neutro e non toccabile. [fibre] = linee interne (fasci muscolari) per dare dettaglio.
 * L'ordine conta: le zone successive si disegnano sopra e vincono nel tocco.
 */
private class Zona(val muscolo: Muscolo?, val d: String, val fibre: List<String> = emptyList())

private fun z(m: Muscolo?, d: String, vararg fibre: String) = Zona(m, d, fibre.toList())

// Forme condivise tra fronte e retro (la sagoma è simmetrica)
private const val DELTOIDE_LATERALE =
    "M26.4,32.6 C22.4,33.8 19.9,37.8 19.6,43.8 C19.6,47.2 20.4,50.2 22,52.4 C22,47 22.8,41.8 24.6,38 C25.4,36.2 26.2,34.4 26.4,32.6 Z"
private const val AVAMBRACCIO_INTERNO =
    "M21,65.8 C23,64.8 25.3,65.2 26.3,67 C25,72.2 23.4,77.6 21.6,83 C20.6,84.2 19.4,84 19,82.8 C19.4,76.8 20,71.2 21,65.8 Z"
private const val AVAMBRACCIO_ESTERNO =
    "M17.8,68.2 C18.6,66.8 19.4,66 20.2,66 C19.3,71.6 18.6,77.4 18.3,83.6 C17.2,85.2 15.8,85 15.6,83.4 C16,78 16.6,72.8 17.8,68.2 Z"

private val ZONE_FRONTE: List<Zona> = listOf(
    // collo (sternocleidomastoideo)
    z(null, "M45.6,25 C46.6,27.4 47.8,29.6 49.2,31.6 L48.6,33 C47,31 45.9,28.6 45.1,26 Z"),
    z(TRAPEZIO, "M44.8,27.4 C43,29.6 38.6,30.9 33.2,31.8 C38.2,32.5 42.4,32.3 45.6,31.2 C45.4,29.8 45.1,28.6 44.8,27.4 Z"),
    z(DELTOIDI_LAT, DELTOIDE_LATERALE, "M23.6,36.4 C21.8,40.2 21.3,44.6 21.6,48.6"),
    z(DELTOIDI_ANT, "M28,32.1 C30.6,31.6 33,32.3 34,34 C33.5,36.5 31.5,38.5 29.5,41 C27.5,43.8 25.5,47.5 23.6,52 C23.4,46 24.5,40.5 26.8,36.5 C27.6,35 28,33.5 28,32.1 Z",
        "M30.4,34 C28.4,37.4 26.6,41.6 25.2,46.4"),
    // gran pettorale: capo clavicolare + capo sternale
    z(PETTO, "M49.3,34.2 C44.5,33.4 38.8,33.6 35,35 C33.6,35.8 32.6,37.4 32.4,39.4 C37,38.9 43.4,39.5 49.3,41 Z",
        "M48.6,36.6 C43,36.4 38,37 34.2,38.2"),
    z(PETTO, "M49.3,42.2 C43,40.8 36.4,40.3 31.6,40.6 C31,42.6 31.6,44.6 33.2,46.2 C37.2,49.4 44,49.8 49.3,48.2 Z",
        "M48.6,44.6 C43,44.4 37.4,43.6 33,42.4", "M48.6,46.8 C43.4,47 38.4,46.4 35,45"),
    // gran dorsale visibile sotto l'ascella
    z(DORSALI, "M30.8,47.6 C31.8,50 32.5,53.2 33,56.6 C33.4,59.6 34,62.8 34.8,66 C33.6,64.8 32.6,62 32,59 C31.3,55.3 30.9,51.4 30.8,47.6 Z"),
    // dentato anteriore
    z(null, "M33.6,49 L37.8,50.8 L36.8,52.6 L34,51.6 Z"),
    z(null, "M34,53.2 L37.8,54.4 L37,56.2 L34.4,55.4 Z"),
    z(null, "M34.4,57 L37.6,57.8 L37,59.6 L34.8,59.2 Z"),
    // braccio: bicipite (capo lungo + breve), tricipite di lato, avambraccio
    z(TRICIPITI, "M19.6,53.4 C20.2,52.6 21,52.4 21.6,52.8 C21.2,55.6 21.1,58.2 21.5,61 C20.6,60.8 19.9,60 19.6,58.6 C19.3,56.8 19.3,55 19.6,53.4 Z"),
    z(BICIPITI, "M22.8,54.4 C23.4,51.6 24.6,49.6 26.2,48.6 C25.8,53 25.4,58 25,63 C23.8,63.2 23,62.4 22.6,61 C22.1,58.8 22.2,56.6 22.8,54.4 Z"),
    z(BICIPITI, "M26.9,48.2 C27.6,47.6 28.3,47.3 29,47.4 C29.4,51.6 29,56.4 27.5,61.2 C27,62.3 26.3,62.9 25.7,63 C26,57.6 26.3,52.6 26.9,48.2 Z"),
    z(AVAMBRACCI, AVAMBRACCIO_INTERNO, "M23.4,68 C22.6,72.6 21.6,77 20.4,81"),
    z(AVAMBRACCI, AVAMBRACCIO_ESTERNO),
    // retto dell'addome (tre quadretti + parte bassa) e obliqui
    z(ADDOME, "M44.2,50.6 C46,50.2 47.8,50.2 49.3,50.4 L49.3,57.2 L43.6,57.6 C43.4,55 43.6,52.6 44.2,50.6 Z"),
    z(ADDOME, "M43.5,59 L49.3,58.8 L49.3,65.6 L43.3,66 C43.2,63.6 43.2,61.2 43.5,59 Z"),
    z(ADDOME, "M43.3,67.4 L49.3,67.2 L49.3,74.4 L43.6,74.8 C43.3,72.4 43.2,69.8 43.3,67.4 Z"),
    z(ADDOME, "M43.8,76.4 L49.3,76.2 L49.3,92 C47,91.2 45.2,89 44.2,86 C43.6,82.8 43.5,79.6 43.8,76.4 Z"),
    z(OBLIQUI, "M37.6,60.4 C39.4,59.6 41.2,60.2 42.4,61.6 C42,68 42,74.5 42.5,81 C42.7,84 42.3,86.6 41.2,88.2 C38.8,87.2 37,85 36.2,82.2 C35.4,74.6 35.8,67 37.6,60.4 Z",
        "M37.4,66 C38.8,67.4 40.2,69.4 41.4,71.8", "M36.8,73.4 C38.4,75 39.8,77.2 41.4,79.8"),
    // tensore della fascia lata
    z(null, "M34.4,92.6 C36.2,93.4 37.6,95.4 38,98 L34.6,99.2 C34,97 34,94.8 34.4,92.6 Z"),
    // coscia: adduttori + quadricipite (vasto laterale, retto femorale, vasto mediale)
    z(ADDUTTORI, "M45,104.8 C47,106.6 48.4,108.8 49,111.6 C48.8,117 48,123 46.8,128 C45.6,124 44.8,118 44.6,112 C44.5,109.4 44.6,107 45,104.8 Z"),
    z(QUADRICIPITI, "M33.8,101.2 C35.6,102.2 36.8,104.6 37.2,108 C37.6,118 37.6,128 37.1,138 C36.7,141 35.8,142.4 34.8,142 C33,133 32.2,121 32.4,111 C32.6,107 33.1,103.8 33.8,101.2 Z",
        "M35,108 C35.4,118 35.4,128 35.2,137"),
    z(QUADRICIPITI, "M38.6,102.4 C40.8,104 42.6,106.6 43.4,110.2 C43.8,119 43,128 41.2,136 C40.4,138.8 39.2,139.6 38.6,138.6 C38.6,128 38.4,117 38.2,108 C38.1,105.8 38.2,104 38.6,102.4 Z",
        "M40.6,106 C40.9,116 40.5,126 39.7,134"),
    z(QUADRICIPITI, "M44,124.6 C45.4,127.6 45.8,131.6 45.4,136 C44.8,140 43.2,143 41,143.8 C40.8,142 41.2,140.2 42,137.8 C43,133.4 43.6,129 44,124.6 Z"),
    // gamba: tibiale anteriore + polpaccio visibile ai lati
    z(null, "M38,150.8 C39.6,150.6 41,151.2 41.6,152.6 C41.2,160 40.6,168 39.8,176 C39,176.6 38.4,176 38.2,175 C37.8,167 37.6,158.8 38,150.8 Z"),
    z(POLPACCI, "M35,152.4 C35.8,151.4 36.6,151.2 37.2,152 C37,158.4 37,166 37.6,174 C36.8,174.4 36,173.6 35.6,172 C34.6,165.6 34.3,158.8 35,152.4 Z"),
    z(POLPACCI, "M42.8,151.2 C44.6,152.8 45.6,156.2 45.6,160.2 C45.4,165.2 44.6,169.8 43.4,173.2 C42.4,168.2 42,161.2 42.2,155.2 C42.2,153.8 42.4,152.4 42.8,151.2 Z"),
)

private val ZONE_RETRO: List<Zona> = listOf(
    // gran dorsale (sotto il trapezio) e lombari
    z(DORSALI, "M33.4,49.4 C37,50.2 40.4,49.8 42.8,48 C45.2,52.4 47.2,56.8 48.8,61.6 C47.6,67.6 45.6,73.4 42.8,78.6 C40.8,75 38.8,70.4 37,65 C35.2,59.4 34,54.2 33.4,49.4 Z",
        "M35.6,56 C39.4,57.2 43.4,57.4 47.2,57", "M37.6,64 C40.6,64.6 43.6,64.4 46.4,63.4", "M40,71.4 C41.8,71.4 43.6,70.8 45.2,69.8"),
    z(LOMBARI, "M46.4,64 C47.2,62.8 48.2,62.2 49.2,62.2 L49.2,90 C47.6,90 46,88.8 45,86.6 C44.6,79 45.2,71 46.4,64 Z",
        "M47.4,66 L47.4,86"),
    z(TRAPEZIO, "M49.3,22.6 C47.6,26.2 44,28.9 38,30.7 C35.4,31.5 33,32.1 31.4,32.7 C35.6,34.5 39.8,37.9 43.1,42.7 C45.7,46.5 47.7,51.2 49.3,58 Z",
        "M48.8,30 C44,31.6 38.4,32.6 33.6,33.2", "M48.6,44 C46,40.6 42,37.2 37.4,34.8"),
    // spalla: deltoide laterale + posteriore, cuffia dei rotatori sulla scapola
    z(DELTOIDI_LAT, DELTOIDE_LATERALE, "M23.6,36.4 C21.8,40.2 21.3,44.6 21.6,48.6"),
    z(DELTOIDI_POST, "M30.6,32.8 C33.2,33.6 34.6,35.6 34.2,37.8 C31.8,39.6 29.2,42.6 27,46.6 C25.8,48.8 24.6,50.8 23.6,52.6 C23,47 24,40.8 26.6,36.6 C27.8,34.8 29,33.4 30.6,32.8 Z",
        "M31.4,35 C29,38 27,42 25.4,46.6"),
    z(CUFFIA, "M34.8,38.8 C37.6,40.4 40.2,42.8 41.8,45.8 C39.8,47.8 37,48.6 34.2,48.2 C32.8,46.6 32.2,44.2 32.6,41.8 C33,40.4 33.8,39.4 34.8,38.8 Z",
        "M33.6,43.4 C36.2,43.6 38.8,44.4 40.6,45.6"),
    // braccio: tricipite (capo laterale + lungo), avambraccio
    z(TRICIPITI, "M21.4,55 C22.2,53.8 23.2,53 24.4,52.8 C24,56 23.8,59.2 23.8,62.2 C22.4,62.6 21.2,61.6 20.8,60 C20.5,58.2 20.7,56.4 21.4,55 Z"),
    z(TRICIPITI, "M25.6,53 C26.6,50.6 28.2,49.4 29.6,50 C29.6,54.6 29,58.8 27.6,62.6 C26.6,63.6 25.4,63.4 24.8,62.2 C24.8,58.8 25,55.8 25.6,53 Z"),
    z(AVAMBRACCI, AVAMBRACCIO_INTERNO, "M23.4,68 C22.6,72.6 21.6,77 20.4,81"),
    z(AVAMBRACCI, AVAMBRACCIO_ESTERNO),
    z(OBLIQUI, "M36.2,72.4 C38.2,75 40.2,78.8 41.4,83 C40.8,86.4 39.2,88.6 37,89.2 C35.8,85.8 35.2,81.2 35.4,77 C35.5,75.2 35.8,73.6 36.2,72.4 Z"),
    // glutei: medio + grande
    z(GLUTEI, "M34,91.6 C36.6,88.6 41.4,87.6 46,89 C46.6,90 46.4,91 45.6,91.8 C41.8,92.6 37.8,94 34.6,96.4 C34,94.8 33.8,93.2 34,91.6 Z"),
    z(GLUTEI, "M34.8,98.4 C38.6,95.2 43.8,93.6 49.2,94.6 L49.2,108.8 C46.6,112 42.4,113.4 38.6,112.8 C36,112.2 34.6,110.6 34,108.2 C33.4,104.8 33.6,101.2 34.8,98.4 Z",
        "M36.6,101.4 C40.6,99.2 45,98.6 48.6,99.4", "M35.6,106.2 C39.6,104.6 44,104.4 48.6,105.2"),
    // coscia: adduttore + femorali (bicipite femorale + semitendinoso)
    z(ADDUTTORI, "M47.4,113.6 C48.2,113.4 48.8,113.8 49,114.6 L48.2,127 C47.8,122.6 47.5,118 47.4,113.6 Z"),
    z(FEMORALI, "M34.4,116.4 C36.8,115.8 39.2,116.2 40.8,117.4 C40.8,126.4 40.4,135.4 39.6,144 C38.2,145.8 36.4,145.6 35.4,144 C33.4,135 33,125.4 34.4,116.4 Z",
        "M37.4,119 C37.6,127 37.4,135 37,142"),
    z(FEMORALI, "M42,117.4 C44,116.4 46,116 47,116.4 C47,126.4 46.2,136.4 44.4,145 C43,146.2 41.4,145.8 40.9,144.4 C41.6,135.4 42,126.4 42,117.4 Z",
        "M44.6,119 C44.6,127 44.2,135 43.4,142"),
    // polpaccio: gastrocnemio (capo laterale + mediale) + soleo
    z(POLPACCI, "M35.6,151 C37.6,149 40,148.8 41,150 C41,156.4 40.8,163 40.2,168.4 C38.8,170 37,169.6 36.2,168 C35,162.4 34.8,156.4 35.6,151 Z"),
    z(POLPACCI, "M41.8,149.8 C43.4,149.2 45,150.4 45.6,153 C45.8,158.8 45.3,165 43.8,169.8 C42.8,171 41.8,170.6 41.4,169.4 C41.6,162.8 41.6,156.4 41.8,149.8 Z"),
    z(POLPACCI, "M37.2,171.6 C39,172.6 41.6,172.6 43.6,171.8 C43.2,176.6 42.4,181.4 41.6,185.4 L39.4,185.4 C38.6,180.8 37.8,176 37.2,171.6 Z"),
)

/** Scala per la regione di hit-test (Region lavora in interi). */
private const val SCALA_TOCCO = 10f

private class ZonaDisegno(val muscolo: Muscolo?, val path: Path, val fibre: List<Path>) {
    val bordi: Rect = path.getBounds()

    private val regione: Region by lazy {
        val p = android.graphics.Path(path.asAndroidPath())
        p.transform(Matrix().apply { setScale(SCALA_TOCCO, SCALA_TOCCO) })
        Region().apply { setPath(p, Region(0, 0, (100 * SCALA_TOCCO).toInt(), (200 * SCALA_TOCCO).toInt())) }
    }

    fun contiene(x: Float, y: Float): Boolean = regione.contains((x * SCALA_TOCCO).toInt(), (y * SCALA_TOCCO).toInt())
}

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

private fun List<Zona>.prepara(): List<ZonaDisegno> = map { ZonaDisegno(it.muscolo, parse(it.d), it.fibre.map(::parse)) }

/** Sagoma intera (metà + metà specchiata unite): un solo contorno, senza linea sull'asse centrale. */
private val sagomaPath: Path by lazy {
    val meta = parse(SAGOMA).asAndroidPath()
    val specchiata = android.graphics.Path(meta).apply { transform(Matrix().apply { setScale(-1f, 1f, 50f, 0f) }) }
    android.graphics.Path().apply { op(meta, specchiata, android.graphics.Path.Op.UNION) }.asComposePath()
}
private val zoneFronte: List<ZonaDisegno> by lazy { ZONE_FRONTE.prepara() }
private val zoneRetro: List<ZonaDisegno> by lazy { ZONE_RETRO.prepara() }

/** Muscolo nel punto (x, y) del riquadro 100 × 200; un po' di tolleranza per le zone piccole. */
private fun muscoloIn(zone: List<ZonaDisegno>, x: Float, y: Float): Muscolo? {
    val ux = if (x > 50f) 100f - x else x
    val toccabili = zone.filter { it.muscolo != null }
    toccabili.lastOrNull { it.contiene(ux, y) }?.let { return it.muscolo }
    return toccabili
        .filter { it.bordi.inflate(1.5f).contains(Offset(ux, y)) }
        .minByOrNull { (it.bordi.center - Offset(ux, y)).getDistanceSquared() }
        ?.muscolo
}

private class ColoriMappa(
    val sagomaAlto: Color,
    val sagomaBasso: Color,
    val sagomaBordo: Color,
    val neutro: Color,
    val dettaglio: Color,
    val solco: Color,
    val principale: Color,
    val principaleChiaro: Color,
    val principaleScuro: Color,
    val secondario: Color,
    val fibra: Color,
    val fibraAttiva: Color,
    val selezione: Color,
)

@Composable
private fun coloriMappa(): ColoriMappa {
    val cs = MaterialTheme.colorScheme
    val lime = cs.primaryContainer
    return ColoriMappa(
        sagomaAlto = cs.onSurface.copy(alpha = 0.15f),
        sagomaBasso = cs.onSurface.copy(alpha = 0.07f),
        sagomaBordo = cs.onSurface.copy(alpha = 0.22f),
        neutro = cs.onSurface.copy(alpha = 0.11f),
        dettaglio = cs.onSurface.copy(alpha = 0.05f),
        // Solchi tra i muscoli nel colore del fondo: separano i capi come in una tavola anatomica
        solco = cs.surface.copy(alpha = 0.75f),
        principale = lime,
        principaleChiaro = lerp(lime, Color.White, 0.35f),
        principaleScuro = lerp(lime, Color.Black, 0.2f),
        secondario = lime.copy(alpha = 0.42f),
        fibra = cs.onSurface.copy(alpha = 0.09f),
        fibraAttiva = Color.Black.copy(alpha = 0.18f),
        selezione = cs.onSurface,
    )
}

private fun DrawScope.disegnaFigura(
    zone: List<ZonaDisegno>,
    coinvolti: MuscoliCoinvolti,
    c: ColoriMappa,
    bagliore: Float,
    selezionato: Muscolo?,
) {
    val s = size.width / 100f // il riquadro ha proporzioni 1:2, la scala è uniforme
    val solco = Stroke(width = 0.55f, join = StrokeJoin.Round)
    val fibra = Stroke(width = 0.3f, cap = StrokeCap.Round)
    withTransform({ scale(s, s, pivot = Offset.Zero) }) {
        drawPath(sagomaPath, Brush.verticalGradient(listOf(c.sagomaAlto, c.sagomaBasso), 0f, 200f))
        drawPath(sagomaPath, c.sagomaBordo, style = Stroke(width = 0.5f, join = StrokeJoin.Round))
    }
    for (lato in 0..1) {
        withTransform({
            if (lato == 1) scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f))
            scale(s, s, pivot = Offset.Zero)
        }) {
            // Alone pulsante intorno ai muscoli principali
            zone.forEach { z ->
                if (z.muscolo != null && z.muscolo in coinvolti.principali) {
                    drawPath(z.path, c.principale.copy(alpha = bagliore), style = Stroke(width = 2.6f, join = StrokeJoin.Round))
                }
            }

            zone.forEach { z ->
                val m = z.muscolo
                val principale = m != null && m in coinvolti.principali
                when {
                    m == null -> drawPath(z.path, c.dettaglio)
                    principale -> drawPath(
                        z.path,
                        Brush.verticalGradient(
                            listOf(c.principaleChiaro, c.principale, c.principaleScuro),
                            startY = z.bordi.top,
                            endY = z.bordi.bottom
                        )
                    )
                    m in coinvolti.secondari -> drawPath(z.path, c.secondario)
                    else -> drawPath(z.path, c.neutro)
                }
                z.fibre.forEach { f -> drawPath(f, if (principale) c.fibraAttiva else c.fibra, style = fibra) }
                drawPath(z.path, c.solco, style = solco)
            }

            if (selezionato != null) {
                zone.forEach { z ->
                    if (z.muscolo == selezionato) drawPath(z.path, c.selezione, style = Stroke(width = 0.9f, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/**
 * Mappa anatomica fronte/retro: muscoli principali in lime pieno con alone pulsante, secondari in
 * lime tenue. Tocca un muscolo per vederne il nome e il ruolo nell'esercizio.
 */
@Composable
fun MappaMuscoli(coinvolti: MuscoliCoinvolti, modifier: Modifier = Modifier, allungamento: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val colori = coloriMappa()
    val bagliore by rememberInfiniteTransition(label = "mappa").animateFloat(
        initialValue = 0.12f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bagliore"
    )
    var selezionato by remember(coinvolti) { mutableStateOf<Muscolo?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Fronte" to zoneFronte, "Retro" to zoneRetro).forEach { (etichetta, zone) ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Canvas(
                        Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(0.5f)
                            .pointerInput(zone) {
                                detectTapGestures { punto ->
                                    val u = 100f / size.width
                                    val m = muscoloIn(zone, punto.x * u, punto.y * u)
                                    selezionato = if (m == selezionato) null else m
                                }
                            }
                    ) {
                        disegnaFigura(zone, coinvolti, colori, bagliore, selezionato)
                    }
                    Sopratitolo(etichetta.uppercase(), colore = cs.onSurfaceVariant)
                }
            }
        }

        val sel = selezionato
        Text(
            text = if (sel == null) "Tocca un muscolo per vederne il nome" else {
                val ruolo = when (sel) {
                    in coinvolti.principali -> if (allungamento) "allungato" else "lavora di più"
                    in coinvolti.secondari -> if (allungamento) "allungato in parte" else "aiuta"
                    else -> "non coinvolto"
                }
                "${sel.etichetta} · $ruolo"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (sel == null) cs.onSurfaceVariant else cs.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VoceLegenda(
                Brush.verticalGradient(listOf(colori.principaleChiaro, colori.principale, colori.principaleScuro)),
                if (allungamento) "Allunga" else "Principali",
                coinvolti.testoPrincipali
            )
            if (coinvolti.secondari.isNotEmpty()) {
                VoceLegenda(
                    SolidColor(colori.secondario),
                    if (allungamento) "Anche" else "Secondari",
                    coinvolti.testoSecondari
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF101110, widthDp = 340)
@Composable
private fun AnteprimaMappaMuscoli() {
    com.my.gymbro.ui.theme.GymBroTheme(darkTheme = true) {
        androidx.compose.material3.Surface {
            MappaMuscoli(
                MuscoliCoinvolti(setOf(PETTO, QUADRICIPITI, DORSALI), setOf(TRICIPITI, GLUTEI, ADDOME)),
                Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VoceLegenda(colore: Brush, titolo: String, muscoli: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(colore))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(titolo) }
                append("  ")
                append(muscoli)
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}
