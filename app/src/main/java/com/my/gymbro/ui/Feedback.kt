package com.my.gymbro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

// Beep e vibrazione di timer e allenamento li riproduce il TimerService (anche in background).

/** Tiene lo schermo acceso finché il composable è in composizione. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
