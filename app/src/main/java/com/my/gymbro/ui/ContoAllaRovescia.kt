package com.my.gymbro.ui

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Conto alla rovescia (e cronometro in avanti) basato sull'orologio di sistema, non sul numero di
 * attese: se l'app viene rallentata in background il tempo resta giusto e la fase finisce
 * all'istante corretto. Condiviso da [TimerEngine] e [AllenamentoEngine].
 *
 * Va usato da un solo thread: [scope] deve girare sul main thread, così i comandi dell'utente
 * (salta, stop, …) e lo scadere del tempo non possono mai sovrapporsi.
 */
class ContoAllaRovescia(
    private val scope: CoroutineScope,
    private val segnala: (Segnale) -> Unit,
) {
    private var job: Job? = null

    /**
     * Conta [secondi] all'indietro: [onSecondo] a ogni cambio di secondo, beep negli ultimi 3,
     * segnale di FINE e poi [onFine]. Con [secondi] ≤ 0 chiama subito [onFine], senza segnale.
     * Il valore iniziale lo imposta il chiamante.
     */
    fun avvia(secondi: Int, onSecondo: (Int) -> Unit, onFine: () -> Unit) {
        ferma()
        if (secondi <= 0) {
            onFine()
            return
        }
        val fine = SystemClock.elapsedRealtime() + secondi * 1000L
        job = scope.launch {
            var ultimo = secondi
            while (true) {
                delay(200)
                val rim = ((fine - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                if (rim != ultimo) {
                    ultimo = rim
                    onSecondo(rim)
                    if (rim in 1..3) segnala(Segnale.TICK)
                }
                if (rim <= 0) break
            }
            // Prima di onFine: se questo avvia la fase successiva, non deve annullare se stesso.
            job = null
            segnala(Segnale.FINE)
            onFine()
        }
    }

    /** Cronometro in avanti (secondi trascorsi da ora); si ferma solo con [ferma] o un nuovo avvio. */
    fun avviaInAvanti(onSecondo: (Int) -> Unit) {
        ferma()
        val inizio = SystemClock.elapsedRealtime()
        job = scope.launch {
            var ultimo = 0
            while (true) {
                delay(250)
                val trascorsi = ((SystemClock.elapsedRealtime() - inizio) / 1000).toInt()
                if (trascorsi != ultimo) {
                    ultimo = trascorsi
                    onSecondo(trascorsi)
                }
            }
        }
    }

    fun ferma() {
        job?.cancel()
        job = null
    }
}
