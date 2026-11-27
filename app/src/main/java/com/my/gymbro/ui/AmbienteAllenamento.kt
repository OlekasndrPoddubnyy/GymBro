package com.my.gymbro.ui

import android.os.SystemClock
import com.my.gymbro.GymBroApplication
import com.my.gymbro.TimerService
import com.my.gymbro.data.ChiaveBuddy
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.ProgressoBuddy
import com.my.gymbro.data.SchedaConEsercizi
import com.my.gymbro.data.SerieEseguita
import com.my.gymbro.data.Sessione

/** Gli orologi del motore dell'allenamento: nei test si usano quelli virtuali di runTest. */
interface Orologio {
    /** Tempo che scorre sempre in avanti (ms), per conti alla rovescia e doppi tocchi. */
    fun monotono(): Long

    /** Ora del calendario (ms), per le date salvate. */
    fun adesso(): Long
}

object OrologioDiSistema : Orologio {
    override fun monotono(): Long = SystemClock.elapsedRealtime()
    override fun adesso(): Long = System.currentTimeMillis()
}

/**
 * Quello che il motore dell'allenamento usa del resto dell'app: database, memoria di Gym Buddy,
 * salvataggio per la ripresa e servizio in primo piano. Nei test è un finto in memoria.
 */
interface AmbienteAllenamento {
    suspend fun caricaSchedaCompleta(id: Long): SchedaConEsercizi?
    suspend fun avviaSessione(sessione: Sessione): Long
    suspend fun prefillEsercizio(esercizioId: Long, sessioneCorrente: Long): List<SerieEseguita>
    suspend fun registraSerie(serie: SerieEseguita)
    suspend fun terminaSessione(id: Long, fine: Long)
    suspend fun scartaSessioneVuota(id: Long)
    suspend fun sessione(id: Long): Sessione?
    suspend fun serieDiSessione(id: Long): List<SerieEseguita>
    suspend fun eserciziOra(): List<Esercizio>

    fun progressoBuddy(chiave: ChiaveBuddy): ProgressoBuddy?
    fun salvaBuddy(nuovi: Map<ChiaveBuddy, ProgressoBuddy>)

    fun salvaRipresa(allenamento: AllenamentoSalvato)
    fun cancellaRipresa()

    /** Servizio in primo piano: tiene vivo l'allenamento con lo schermo spento. */
    fun avviaServizio()
    fun togliAvvisoInterrotto()
}

/** L'ambiente vero, sopra l'applicazione. */
internal class AmbienteApp(private val app: GymBroApplication) : AmbienteAllenamento {
    private val repo get() = app.repository

    override suspend fun caricaSchedaCompleta(id: Long) = repo.caricaSchedaCompleta(id)
    override suspend fun avviaSessione(sessione: Sessione) = repo.avviaSessione(sessione)
    override suspend fun prefillEsercizio(esercizioId: Long, sessioneCorrente: Long) =
        repo.prefillEsercizio(esercizioId, sessioneCorrente)
    override suspend fun registraSerie(serie: SerieEseguita) { repo.registraSerie(serie) }
    override suspend fun terminaSessione(id: Long, fine: Long) = repo.terminaSessione(id, fine)
    override suspend fun scartaSessioneVuota(id: Long) = repo.scartaSessioneVuota(id)
    override suspend fun sessione(id: Long) = repo.sessione(id)
    override suspend fun serieDiSessione(id: Long) = repo.serieDiSessione(id)
    override suspend fun eserciziOra() = repo.eserciziOra()

    override fun progressoBuddy(chiave: ChiaveBuddy) = app.memoriaBuddy.progresso(chiave)
    override fun salvaBuddy(nuovi: Map<ChiaveBuddy, ProgressoBuddy>) = app.memoriaBuddy.salva(nuovi)

    override fun salvaRipresa(allenamento: AllenamentoSalvato) = app.ripresa.salva(allenamento)
    override fun cancellaRipresa() = app.ripresa.cancella()

    override fun avviaServizio() = TimerService.start(app)
    override fun togliAvvisoInterrotto() = TimerService.togliAvvisoInterrotto(app)
}
