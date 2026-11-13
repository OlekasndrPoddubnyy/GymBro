package com.my.gymbro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * True quando il dispositivo è in Flex Mode: semi-aperto con cerniera orizzontale (lo schermo è
 * diviso in metà superiore e inferiore), tipico del Galaxy Z Flip appoggiato.
 */
@Composable
fun inFlexMode(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return false
    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
    val info by remember(activity) { tracker.windowLayoutInfo(activity) }.collectAsStateWithLifecycle(initial = null)

    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    return fold != null &&
        fold.state == FoldingFeature.State.HALF_OPENED &&
        fold.orientation == FoldingFeature.Orientation.HORIZONTAL
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
