package com.my.gymbro.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.Figura
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.ui.Muscolo.*
import kotlinx.coroutines.flow.MutableStateFlow

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

// ---------------- componente (zone in FigureMuscoli.kt, resa in FiguraRealistica.kt) ----------------

/**
 * Mappa fronte/retro su una persona (uomo o donna, a scelta): muscoli principali in vermiglio pieno
 * con alone pulsante, secondari in vermiglio più leggero con bordo. Tocca un muscolo per vederne il nome e il ruolo nell'esercizio.
 */
@Composable
fun MappaMuscoli(coinvolti: MuscoliCoinvolti, modifier: Modifier = Modifier, allungamento: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val impostazioni = (LocalContext.current.applicationContext as? GymBroApplication)?.impostazioni
    // senza applicazione (anteprima dell'IDE) la scelta resta locale
    val figuraLocale = remember { MutableStateFlow(Figura.UOMO) }
    val figura by (impostazioni?.figura ?: figuraLocale).collectAsStateWithLifecycle()
    // Muscoli in vermiglio: il bianco carta del tema non si vedrebbe sulla figura realistica.
    val lime = cs.tertiaryContainer
    val bordoSecondario = cs.tertiary.copy(alpha = 0.8f)
    val bagliore = rememberInfiniteTransition(label = "mappa").animateFloat(
        initialValue = 0.1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bagliore"
    )
    var selezionato by remember(coinvolti) { mutableStateOf<Muscolo?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            visteDi(figura).forEach { vista ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val base = ImageBitmap.imageResource(vista.immagine)
                    // Ricordata: un tocco o un fotogramma dell'alone non devono ricolorare l'immagine
                    val colorata = remember(base, vista, coinvolti, lime) { coloraFigura(base, vista, coinvolti, lime) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(PROPORZIONE_FIGURA)
                            .semantics { contentDescription = "Figura ${figura.label}, ${vista.etichetta}" }
                            .pointerInput(vista) {
                                detectTapGestures { punto ->
                                    val m = vista.muscoloIn(punto.x / size.width, punto.y / size.height)
                                    selezionato = if (m == selezionato) null else m
                                }
                            }
                    ) {
                        Image(colorata, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize()) {
                            disegnaEvidenze(vista, coinvolti, lime, bordoSecondario, cs.onSurface, bagliore.value, selezionato)
                        }
                    }
                    Sopratitolo(vista.etichetta.uppercase(), colore = cs.onSurfaceVariant)
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

        SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.CenterHorizontally)) {
            Figura.entries.forEachIndexed { i, f ->
                SegmentedButton(
                    selected = f == figura,
                    onClick = { if (impostazioni != null) impostazioni.impostaFigura(f) else figuraLocale.value = f },
                    shape = SegmentedButtonDefaults.itemShape(i, Figura.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = cs.primaryContainer,
                        activeContentColor = cs.onPrimaryContainer,
                    ),
                ) { Text(f.label) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VoceLegenda(
                SolidColor(lime),
                if (allungamento) "Allunga" else "Principali",
                coinvolti.testoPrincipali
            )
            if (coinvolti.secondari.isNotEmpty()) {
                VoceLegenda(
                    SolidColor(lime.copy(alpha = FORZA_SECONDARIO)),
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
