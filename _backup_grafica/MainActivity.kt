package com.my.gymbro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
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
            val tema by impostazioni.tema.collectAsState()
            val scuro = when (tema) {
                Tema.SISTEMA -> isSystemInDarkTheme()
                Tema.CHIARO -> false
                Tema.SCURO -> true
            }
            GymBroTheme(darkTheme = scuro) {
                GymBroApp(temaCorrente = tema, onTema = impostazioni::impostaTema)
            }
        }
    }
}
