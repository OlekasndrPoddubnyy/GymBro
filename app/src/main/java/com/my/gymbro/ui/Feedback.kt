package com.my.gymbro.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat

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

/**
 * Restituisce una funzione da chiamare all'avvio di timer o allenamento: se il permesso per le
 * notifiche non è ancora concesso lo chiede. Senza permesso tutto funziona lo stesso, ma la notifica
 * con lo stato e i comandi (salta, stop…) non compare.
 */
@Composable
fun rememberRichiestaNotifiche(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            val concesso = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!concesso) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
