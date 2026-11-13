package com.my.gymbro

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.my.gymbro.data.Tema
import com.my.gymbro.ui.GymBroApp
import com.my.gymbro.ui.theme.GymBroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                GymBroApp(temaCorrente = tema, onTema = impostazioni::impostaTema)
            }
        }
    }
}
