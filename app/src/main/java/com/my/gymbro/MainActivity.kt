package com.my.gymbro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.my.gymbro.data.Tema
import com.my.gymbro.ui.GymBroApp
import com.my.gymbro.ui.theme.GymBroTheme

class MainActivity : ComponentActivity() {

    /** Dove portare l'utente dopo il tocco su una notifica ([APRI_ALLENAMENTO], [APRI_TIMER]); null = niente. */
    private val daAprire = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Activity ricreata (rotazione, …): l'intent è quello vecchio, già gestito.
        if (savedInstanceState == null) leggiDestinazione(intent)
        enableEdgeToEdge()
        val impostazioni = (application as GymBroApplication).impostazioni
        setContent {
            val tema by impostazioni.tema.collectAsStateWithLifecycle()
            val scuro = when (tema) {
                Tema.SISTEMA -> isSystemInDarkTheme()
                Tema.CHIARO -> false
                Tema.SCURO -> true
            }
            // Icone della barra di stato coerenti con il tema scelto nell'app (non solo con quello di sistema).
            LaunchedEffect(scuro) {
                val stile = if (scuro) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = stile, navigationBarStyle = stile)
            }
            GymBroTheme(darkTheme = scuro) {
                GymBroApp(
                    temaCorrente = tema,
                    onTema = impostazioni::impostaTema,
                    daAprire = daAprire.value,
                    onAperto = { daAprire.value = null },
                )
            }
        }
    }

    /** App già aperta (singleTop): il tocco sulla notifica arriva qui. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        leggiDestinazione(intent)
    }

    private fun leggiDestinazione(intent: Intent?) {
        daAprire.value = intent?.getStringExtra(EXTRA_APRI) ?: return
    }

    companion object {
        const val EXTRA_APRI = "com.my.gymbro.extra.APRI"
        const val APRI_ALLENAMENTO = "allenamento"
        const val APRI_TIMER = "timer"
    }
}
