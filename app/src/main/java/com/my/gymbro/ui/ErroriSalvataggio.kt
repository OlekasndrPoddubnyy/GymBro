package com.my.gymbro.ui

import android.database.sqlite.SQLiteFullException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "GymBro"

/** Messaggio da mostrare all'utente in un dialogo (titolo + spiegazione). */
data class Avviso(val titolo: String, val testo: String)

/** Il perché di un errore, in breve e comprensibile. */
internal fun motivoErrore(e: Throwable): String = when (e) {
    is SQLiteFullException -> "la memoria del telefono è piena"
    else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
}

/**
 * Come [launch], ma un errore (non l'annullamento) non chiude l'app: si registra nel log e
 * passa a [seErrore], che decide cosa dire all'utente.
 */
internal fun CoroutineScope.lanciaProtetto(
    cosa: String,
    seErrore: (Exception) -> Unit,
    blocco: suspend CoroutineScope.() -> Unit,
): Job = launch {
    try {
        blocco()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "$cosa: non riuscito", e)
        seErrore(e)
    }
}

/**
 * Ultima rete per gli scope di processo (motori, servizio): un errore sfuggito alla gestione
 * puntuale si registra e, se serve, si segnala invece di chiudere l'app.
 */
internal fun gestoreErrori(dove: String, seErrore: (Throwable) -> Unit = {}) =
    CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Errore imprevisto in $dove", e)
        seErrore(e)
    }
