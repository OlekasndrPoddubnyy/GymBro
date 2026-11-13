package com.my.gymbro.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Restituisce una funzione che riproduce il feedback sonoro + aptico per i [Segnale]:
 * un beep breve nel countdown finale (TICK) e uno più marcato a fine fase (FINE).
 * Il [ToneGenerator] viene rilasciato all'uscita dalla composizione.
 */
@Composable
fun rememberFeedbackController(): (Segnale) -> Unit {
    val view = LocalView.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    return remember(view, tone) {
        { seg: Segnale ->
            when (seg) {
                Segnale.TICK -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                }
                Segnale.FINE -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
                }
            }
        }
    }
}

/** Tiene lo schermo acceso finché il composable è in composizione. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
