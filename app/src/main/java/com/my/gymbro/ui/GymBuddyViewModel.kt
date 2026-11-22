package com.my.gymbro.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.ChiaveBuddy
import com.my.gymbro.data.CoppiaEsercizi
import com.my.gymbro.data.DatiBuddy
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.EsercizioSchedaCompleto
import com.my.gymbro.data.GymBroRepository
import com.my.gymbro.data.ImpostazioniBuddy
import com.my.gymbro.data.MemoriaBuddy
import com.my.gymbro.data.Modalita
import com.my.gymbro.data.Obiettivo
import com.my.gymbro.data.ProfiloEsercizio
import com.my.gymbro.data.ProgressoBuddy
import com.my.gymbro.data.RegoleBuddy
import com.my.gymbro.data.SchedaConEsercizi
import com.my.gymbro.data.Sostituto
import com.my.gymbro.data.StatoBuddy
import com.my.gymbro.data.allenabile
import com.my.gymbro.data.daSuggerire
import com.my.gymbro.data.regolePer
import com.my.gymbro.data.rispondi
import com.my.gymbro.data.schemaMovimento
import com.my.gymbro.data.somiglianza
import com.my.gymbro.data.sostituti
import com.my.gymbro.data.stimaCarico
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MS_GIORNO = 24 * 60 * 60 * 1000L

/** Correzioni di fabbrica dei pesi calcolati: i piegamenti restano un sostituto della panca piana. */
private val CORREZIONI_PREDEFINITE = listOf(Triple("Panca piana", "Piegamenti sulle braccia", 0.6))

/** Tutto ciò che serve alla UI di Gym Buddy e dei suggerimenti. */
data class VistaBuddy(
    val pronta: Boolean = false,
    val dati: DatiBuddy = DatiBuddy(),
    val libreria: List<Esercizio> = emptyList(),
    val profili: Map<Long, ProfiloEsercizio> = emptyMap(),
    val pesiCorretti: Map<CoppiaEsercizi, Double> = emptyMap(),
    val primaVolta: Map<ChiaveBuddy, Long> = emptyMap(),
    val ultimoCarico: Map<Long, Float> = emptyMap(),
) {
    fun progresso(chiave: ChiaveBuddy): ProgressoBuddy = dati.progressi[chiave] ?: ProgressoBuddy()

    fun somiglianzaTra(a: Long, b: Long): Double {
        pesiCorretti[CoppiaEsercizi(a, b)]?.let { return it }
        val primo = profili[a] ?: return 0.0
        val secondo = profili[b] ?: return 0.0
        return somiglianza(primo, secondo)
    }

    fun sostitutiPer(scheda: SchedaConEsercizi, esercizioId: Long): List<Sostituto> {
        val profilo = profili[esercizioId] ?: return emptyList()
        val i = dati.impostazioni
        return sostituti(
            attuale = profilo,
            libreria = libreria.mapNotNull { profili[it.id] },
            inScheda = scheda.esercizi.map { it.esercizio.id }.toSet(),
            regole = i.sostituzione,
            manuali = pesiCorretti,
            attrezziDisponibili = i.attrezziDisponibili,
        )
    }

    fun troppoALungo(chiave: ChiaveBuddy, ora: Long): Boolean {
        val p = progresso(chiave)
        val dal = p.inSchedaDal ?: primaVolta[chiave]
        // Con una proposta in attesa decide prima quella; lo stallo non scatta prima delle conferme.
        val sessioni = if (p.attivo && p.stato?.proposta == null) p.sessioniSenzaAumento else null
        val regole = dati.impostazioni.sostituzione
        return daSuggerire(
            ora = ora,
            inSchedaDal = dal ?: ora,
            sessioniSenzaAumento = sessioni,
            regole = regole.copy(sessioniStallo = maxOf(regole.sessioniStallo, p.regole.conferme + 1)),
            rimandatoFino = p.rimandatoFino,
            ignorato = p.ignorato,
        )
    }

    fun caricoPerSostituto(da: Esercizio, a: Esercizio, caricoAttuale: Float?): Float? =
        ultimoCarico[a.id] ?: stimaCarico(da.attrezzatura, a.attrezzatura, caricoAttuale, somiglianzaTra(da.id, a.id))
}

class GymBuddyViewModel(
    private val repo: GymBroRepository,
    private val memoria: MemoriaBuddy,
) : ViewModel() {

    val vista: StateFlow<VistaBuddy> =
        combine(memoria.dati, repo.esercizi, repo.righeReport(), repo.sessioni) { dati, libreria, righe, sessioni ->
            val schedaDi = sessioni.associate { it.id to it.schedaId }
            val prima = mutableMapOf<ChiaveBuddy, Long>()
            val carichi = mutableMapOf<Long, Float>()
            // Le righe sono in ordine di data: la prima resta, l'ultimo carico vince.
            for (riga in righe) {
                schedaDi[riga.sessioneId]?.let { scheda ->
                    prima.getOrPut(ChiaveBuddy(scheda, riga.esercizioId)) { riga.dataInizio }
                }
                riga.pesoKg?.takeIf { it > 0f }?.let { carichi[riga.esercizioId] = it }
            }
            val perNome = libreria.associateBy { it.nome }
            val corretti = mutableMapOf<CoppiaEsercizi, Double>()
            for ((a, b, peso) in CORREZIONI_PREDEFINITE) {
                val primo = perNome[a] ?: continue
                val secondo = perNome[b] ?: continue
                corretti[CoppiaEsercizi(primo.id, secondo.id)] = peso
            }
            VistaBuddy(
                pronta = true,
                dati = dati,
                libreria = libreria,
                profili = libreria.filter { it.gruppoMuscolare.allenabile }.associate { it.id to profiloDi(it) },
                pesiCorretti = corretti + dati.manuali, // quelli dell'utente prevalgono
                primaVolta = prima,
                ultimoCarico = carichi,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VistaBuddy())

    var errore by mutableStateOf<String?>(null)
        private set

    private var modifica: Job? = null

    fun chiudiErrore() { errore = null }

    fun attiva(schedaId: Long, righe: List<EsercizioSchedaCompleto>, attivo: Boolean) {
        val v = vista.value
        if (!v.pronta) return
        val nuovi = righe
            .filter { it.progredisce }
            .distinctBy { it.esercizio.id }
            .associate { ec ->
                val chiave = ChiaveBuddy(schedaId, ec.esercizio.id)
                val prima = memoria.progresso(chiave) ?: ProgressoBuddy()
                val dopo = when {
                    !attivo -> prima.copy(attivo = false)
                    prima.stato != null -> prima.copy(attivo = true, sessioniSenzaAumento = 0)
                    else -> nuovoProgresso(prima, ec, v)
                }
                chiave to dopo
            }
        memoria.salva(nuovi)
    }

    private fun nuovoProgresso(prima: ProgressoBuddy, ec: EsercizioSchedaCompleto, v: VistaBuddy): ProgressoBuddy {
        val serie = ec.serie.sortedBy { it.numero }
        val regole = v.dati.impostazioni.regolePer(ec.esercizio, serie.firstOrNull()?.ripetizioni)
        val carico = v.ultimoCarico[ec.esercizio.id] ?: serie.firstOrNull()?.pesoKg?.takeIf { it > 0f }
        return prima.copy(
            attivo = true,
            regole = regole,
            stato = StatoBuddy(
                obiettivo = Obiettivo(regole.ripMin, carico),
                calibrazione = carico == null && regole.incrementoKg != null,
            ),
            sessioniSenzaAumento = 0,
        )
    }

    fun rispondi(chiave: ChiaveBuddy, accettata: Boolean) {
        val p = memoria.progresso(chiave) ?: return
        memoria.salva(chiave, p.rispondi(accettata))
    }

    fun salvaRegole(chiave: ChiaveBuddy, regole: RegoleBuddy, obiettivo: Obiettivo) {
        val p = memoria.progresso(chiave) ?: return
        val stato = p.stato ?: return
        val nuovoStato = when {
            obiettivo != stato.obiettivo -> StatoBuddy(obiettivo) // scelto dall'utente: si riparte da qui
            regole != p.regole -> stato.copy(proposta = null)     // la proposta era fatta con le regole vecchie
            else -> stato
        }
        memoria.salva(
            chiave,
            p.copy(
                regole = regole,
                stato = nuovoStato,
                sessioniSenzaAumento = if (obiettivo != stato.obiettivo) 0 else p.sessioniSenzaAumento,
            ),
        )
    }

    fun rimanda(chiave: ChiaveBuddy) {
        val p = memoria.progresso(chiave) ?: ProgressoBuddy()
        val giorni = memoria.dati.value.impostazioni.giorniRimanda
        memoria.salva(chiave, p.copy(rimandatoFino = System.currentTimeMillis() + giorni * MS_GIORNO))
    }

    fun ignora(chiave: ChiaveBuddy) {
        val p = memoria.progresso(chiave) ?: ProgressoBuddy()
        memoria.salva(chiave, p.copy(ignorato = true))
    }

    fun correggiSomiglianza(a: Long, b: Long, peso: Double?) =
        memoria.correggiSomiglianza(CoppiaEsercizi(a, b), peso)

    fun salvaImpostazioni(i: ImpostazioniBuddy) = memoria.impostazioni(i)

    fun sostituisci(schedaId: Long, vecchio: EsercizioSchedaCompleto, nuovo: Esercizio, carico: Float?) {
        if (modifica?.isActive == true) return
        modifica = viewModelScope.launch {
            val prima = repo.cambiaEsercizio(schedaId, vecchio.esercizio.id, nuovo.id, carico)
            if (prima == null) {
                errore = "La scheda è cambiata nel frattempo: sostituzione non fatta."
                return@launch
            }
            val origine = memoria.progresso(ChiaveBuddy(schedaId, vecchio.esercizio.id))
            val impostazioni = memoria.dati.value.impostazioni
            // Stesso range di prima, passo e incremento del nuovo attrezzo.
            val base = impostazioni.regolePer(nuovo, vecchio.serie.minByOrNull { it.numero }?.ripetizioni)
            val regole = if (origine?.stato == null) base
            else base.copy(ripMin = origine.regole.ripMin, ripMax = origine.regole.ripMax)
            val attivo = origine?.attivo == true
            memoria.salva(
                ChiaveBuddy(schedaId, nuovo.id),
                ProgressoBuddy(
                    attivo = attivo,
                    regole = regole,
                    stato = if (attivo) StatoBuddy(Obiettivo(regole.ripMin, carico), calibrazione = true) else null,
                    inSchedaDal = System.currentTimeMillis(), // il contatore riparte da zero
                    precedente = prima,
                ),
            )
        }
    }

    fun annullaSostituzione(chiave: ChiaveBuddy) {
        if (modifica?.isActive == true) return
        val attuale = memoria.progresso(chiave) ?: return
        val prima = attuale.precedente ?: return
        modifica = viewModelScope.launch {
            if (!repo.annullaCambio(chiave.schedaId, chiave.esercizioId, prima)) {
                errore = "Non si può più annullare: la scheda è cambiata."
                return@launch
            }
            val chiaveVecchia = ChiaveBuddy(chiave.schedaId, prima.esercizioId)
            val vecchio = memoria.progresso(chiaveVecchia) ?: ProgressoBuddy()
            val attesa = memoria.dati.value.impostazioni.giorniRimanda * MS_GIORNO
            memoria.salva(
                mapOf(
                    chiave to attuale.copy(attivo = false, precedente = null),
                    // Appena ripristinato, non si ripropone subito di cambiarlo.
                    chiaveVecchia to vecchio.copy(rimandatoFino = System.currentTimeMillis() + attesa),
                )
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                GymBuddyViewModel(app.repository, app.memoriaBuddy)
            }
        }
    }
}

/** Gym Buddy vale per gli esercizi a ripetizioni, esclusi cardio e stretching. */
internal val EsercizioSchedaCompleto.progredisce: Boolean
    get() = esercizioScheda.modalita == Modalita.RIPETIZIONI && esercizio.gruppoMuscolare.allenabile

internal fun profiloDi(e: Esercizio): ProfiloEsercizio {
    val muscoli = muscoliCoinvolti(e.nome, e.gruppoMuscolare)
    return ProfiloEsercizio(
        id = e.id,
        principali = muscoli?.principali.orEmpty().map { it.name }.toSet(),
        secondari = muscoli?.secondari.orEmpty().map { it.name }.toSet(),
        movimento = schemaMovimento(e.nome, e.gruppoMuscolare),
        attrezzo = e.attrezzatura,
    )
}
