package com.my.gymbro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.GruppoMuscolare
import com.my.gymbro.data.GymBroRepository
import com.my.gymbro.data.RigaReport
import com.my.gymbro.data.Sessione
import com.my.gymbro.data.caricoInverso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// ---------- modelli per la UI ----------

enum class PeriodoReport(val label: String, val giorni: Long?) {
    TUTTO("Tutto", null),
    M1("1 mese", 30),
    M3("3 mesi", 90),
    A1("1 anno", 365),
}

enum class MetricaTrend(val label: String) {
    PESO("Peso max"),
    VOLUME("Volume"),
}

data class BarraValore(val etichetta: String, val valore: Double)
data class RecordEsercizio(
    val id: Long,
    val nome: String,
    val pesoMax: Float?,
    val ripMax: Int?,
    val e1rm: Float?,
    val dataMs: Long,          // giorno in cui è stato fatto il record (miglior 1RM stimato, o peso max)
    val nuovo: Boolean = false, // fatto negli ultimi 7 giorni
)
data class PuntoAndamento(val dataMs: Long, val valore: Float)
data class EsercizioRef(val id: Long, val nome: String)
data class EsercizioStorico(val nome: String, val serie: List<String>)
data class VoceStorico(
    val id: Long,
    val dataMs: Long,
    val nome: String,
    val durataMs: Long,
    val volume: Double,
    val numSerie: Int,
    val dettaglio: List<EsercizioStorico>,
)

/** Una colonna del calendario-attività: 7 giorni (lun→dom) dal lunedì [inizio]; null = giorno futuro. */
data class SettimanaAttivita(val giorni: List<Int?>, val inizio: LocalDate = LocalDate.MIN)

/** Valori del periodo precedente di pari durata, per il confronto (null con "Tutto"). */
data class ConfrontoPeriodo(val volume: Double, val sessioni: Int)

data class ReportState(
    val pronto: Boolean = false,
    val periodo: PeriodoReport = PeriodoReport.TUTTO,
    val metrica: MetricaTrend = MetricaTrend.PESO,
    val sessioniTotali: Int = 0,
    // Riepilogo (rispetta il periodo selezionato)
    val numeroSessioni: Int = 0,
    val volumeTotale: Double = 0.0,
    val giorniAllenati: Int = 0,
    val serieTotali: Int = 0,
    val durataMediaMin: Long = 0,
    val durataMediaMs: Long = 0,
    val confronto: ConfrontoPeriodo? = null,
    // Costanza (sempre su tutti i dati: è "lo stato attuale")
    val settimaneConsecutive: Int = 0,
    val migliorStreak: Int = 0,
    val allenamentiSettimana: Int = 0,
    val heatmap: List<SettimanaAttivita> = emptyList(),
    // Grafici volume (finestre temporali fisse)
    val volumeSettimanale: List<BarraValore> = emptyList(),
    val volumeMensile: List<BarraValore> = emptyList(),
    // Sezioni filtrate per periodo
    val pr: List<RecordEsercizio> = emptyList(),
    val distribuzioneMuscolare: List<BarraValore> = emptyList(),
    val muscoliPeriodo: MuscoliCoinvolti? = null,
    val eserciziDisponibili: List<EsercizioRef> = emptyList(),
    val esercizioAndamento: Long? = null,
    val andamento: List<PuntoAndamento> = emptyList(),
    val storico: List<VoceStorico> = emptyList(),
)

class ReportViewModel(repo: GymBroRepository) : ViewModel() {

    private val esercizioSel = MutableStateFlow<Long?>(null)
    private val periodo = MutableStateFlow(PeriodoReport.TUTTO)
    private val metrica = MutableStateFlow(MetricaTrend.PESO)

    val stato: StateFlow<ReportState> =
        combine(repo.righeReport(), repo.sessioni, esercizioSel, periodo, metrica) { righe, sessioni, sel, per, met ->
            calcola(righe, sessioni, sel, per, met)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportState())

    fun selezionaEsercizio(id: Long) { esercizioSel.value = id }
    fun setPeriodo(p: PeriodoReport) { periodo.value = p }
    fun setMetrica(m: MetricaTrend) { metrica.value = m }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication
                ReportViewModel(app.repository)
            }
        }
    }
}

// ---------- calcolo statistiche ----------

private val zona: ZoneId get() = ZoneId.systemDefault()

private fun giornoDi(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()

private fun inizioSettimana(d: LocalDate): LocalDate =
    d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Sulle macchine assistite il peso è aiuto: non è volume sollevato. */
private fun volumeRiga(r: RigaReport): Double =
    if (caricoInverso(r.nomeCatalogo)) 0.0
    else (r.pesoKg?.toDouble() ?: 0.0) * (r.ripetizioni?.toDouble() ?: 0.0)

private fun descriviRiga(r: RigaReport): String = when {
    r.durataSec != null -> "${r.durataSec}s"
    else -> {
        val rip = r.ripetizioni?.toString() ?: "—"
        val peso = r.pesoKg?.let { p ->
            " × " + (if (p % 1f == 0f) p.toInt().toString() else p.toString()) + " kg"
        } ?: ""
        "$rip rip$peso"
    }
}

internal fun calcola(
    righe: List<RigaReport>,
    tutteLeSessioni: List<Sessione>,
    sel: Long?,
    periodo: PeriodoReport,
    metrica: MetricaTrend,
    oggi: LocalDate = LocalDate.now(),
): ReportState {
    // Contano solo le sessioni con almeno una serie registrata: un allenamento aperto e chiuso
    // subito (o abbandonato prima della prima serie) non è un allenamento fatto e non deve gonfiare
    // conteggi, settimane consecutive, calendario e storico.
    val conSerie = righe.mapTo(HashSet()) { it.sessioneId }
    val sessioni = tutteLeSessioni.filter { it.id in conSerie }

    val settimanaCorrente = inizioSettimana(oggi)

    // Filtro periodo
    val cutoffMs = periodo.giorni?.let { g ->
        oggi.minusDays(g).atStartOfDay(zona).toInstant().toEpochMilli()
    }
    val righeF = if (cutoffMs == null) righe else righe.filter { it.dataInizio >= cutoffMs }
    val sessioniF = if (cutoffMs == null) sessioni else sessioni.filter { it.dataInizio >= cutoffMs }

    // ---- Costanza (tutti i dati) ----
    val giorniTutti = sessioni.groupingBy { giornoDi(it.dataInizio) }.eachCount()
    val settimaneAllenate = giorniTutti.keys.map { inizioSettimana(it) }.toSet()
    var streak = 0
    // La settimana in corso non interrompe la serie finché non è finita: se è ancora vuota si parte dalla scorsa.
    var wk = if (settimanaCorrente in settimaneAllenate) settimanaCorrente else settimanaCorrente.minusWeeks(1)
    while (wk in settimaneAllenate) { streak++; wk = wk.minusWeeks(1) }

    var migliorStreak = 0
    var run = 0
    var prec: LocalDate? = null
    for (w in settimaneAllenate.sorted()) {
        run = if (prec != null && w == prec.plusWeeks(1)) run + 1 else 1
        if (run > migliorStreak) migliorStreak = run
        prec = w
    }

    val allenamentiSettimana = sessioni.count { inizioSettimana(giornoDi(it.dataInizio)) == settimanaCorrente }

    // Heatmap: ultime 14 settimane (lun→dom), null per i giorni futuri
    val inizioHeatmap = settimanaCorrente.minusWeeks(13)
    val heatmap = (0..13).map { w ->
        val inizio = inizioHeatmap.plusWeeks(w.toLong())
        SettimanaAttivita(
            (0..6).map { d ->
                val data = inizio.plusDays(d.toLong())
                if (data.isAfter(oggi)) null else giorniTutti[data] ?: 0
            },
            inizio
        )
    }

    // ---- Volume su finestre fisse (tutti i dati) ----
    val volPerSettimana = righe.groupBy { inizioSettimana(giornoDi(it.dataInizio)) }
        .mapValues { (_, rs) -> rs.sumOf(::volumeRiga) }
    val volumeSettimanale = (7 downTo 0).map { off ->
        val w = settimanaCorrente.minusWeeks(off.toLong())
        BarraValore("${w.dayOfMonth}/${w.monthValue}", volPerSettimana[w] ?: 0.0)
    }

    val meseCorrente = YearMonth.from(oggi)
    val volPerMese = righe.groupBy { YearMonth.from(giornoDi(it.dataInizio)) }
        .mapValues { (_, rs) -> rs.sumOf(::volumeRiga) }
    val volumeMensile = (5 downTo 0).map { off ->
        val m = meseCorrente.minusMonths(off.toLong())
        BarraValore("%02d/%02d".format(m.monthValue, m.year % 100), volPerMese[m] ?: 0.0)
    }

    // ---- Riepilogo (periodo) ----
    val durate = sessioniF.mapNotNull { s -> s.dataFine?.let { it - s.dataInizio } }.filter { it > 0 }
    val durataMediaMin = if (durate.isEmpty()) 0L else durate.sum() / durate.size / 60000L

    // Periodo precedente di pari durata: [cutoff - giorni, cutoff)
    val confronto = periodo.giorni?.let { g ->
        val inizioPrec = oggi.minusDays(2 * g).atStartOfDay(zona).toInstant().toEpochMilli()
        val fine = cutoffMs!!
        ConfrontoPeriodo(
            volume = righe.filter { it.dataInizio in inizioPrec until fine }.sumOf(::volumeRiga),
            sessioni = sessioni.count { it.dataInizio in inizioPrec until fine }
        )
    }

    // ---- PR con 1RM stimato (Epley) (periodo) ----
    val pr = righeF.groupBy { it.esercizioId }
        .map { (id, rs) ->
            // Macchine assistite: più peso = più aiuto, quindi niente massimale né peso record.
            val inverso = caricoInverso(rs.first().nomeCatalogo)
            val stime = if (inverso) emptyList() else rs.mapNotNull { r ->
                val p = r.pesoKg ?: return@mapNotNull null
                val rip = r.ripetizioni ?: return@mapNotNull null
                if (rip <= 0) null else r to p * (1f + rip / 30f)
            }
            // La riga del record: miglior 1RM stimato; senza pesi, il maggior numero di ripetizioni.
            val migliore = stime.maxByOrNull { it.second }?.first
                ?: rs.maxByOrNull { it.pesoKg ?: 0f }?.takeIf { !inverso && it.pesoKg != null }
                ?: rs.maxBy { it.ripetizioni ?: it.durataSec ?: 0 }
            RecordEsercizio(
                id = id,
                nome = rs.first().nomeEsercizio,
                pesoMax = if (inverso) null else rs.mapNotNull { it.pesoKg }.maxOrNull(),
                ripMax = rs.mapNotNull { it.ripetizioni }.maxOrNull(),
                e1rm = stime.maxOfOrNull { it.second },
                dataMs = migliore.dataInizio,
                nuovo = !giornoDi(migliore.dataInizio).isBefore(oggi.minusDays(6))
            )
        }
        .sortedWith(compareByDescending<RecordEsercizio> { it.e1rm ?: it.pesoMax ?: 0f }.thenBy { it.nome })

    // ---- Distribuzione muscolare (periodo) ----
    val distribuzione = righeF.filter { it.gruppoMuscolare != GruppoMuscolare.STRETCHING }
        .groupingBy { it.gruppoMuscolare }.eachCount()
        .entries.sortedByDescending { it.value }
        .map { BarraValore(it.key.label, it.value.toDouble()) }

    // ---- Muscoli allenati sulla figura (periodo) ----
    // Punteggio per muscolo: 1 per serie dove è principale, 0,5 dove aiuta. In pieno quelli
    // vicini al più allenato (≥ 60%), più leggeri gli altri.
    val punteggi = HashMap<Muscolo, Double>()
    righeF.groupBy { it.esercizioId }.values.forEach { rs ->
        val mc = muscoliCoinvolti(rs.first().nomeCatalogo, rs.first().gruppoMuscolare) ?: return@forEach
        if (rs.first().gruppoMuscolare == GruppoMuscolare.STRETCHING) return@forEach
        mc.principali.forEach { punteggi.merge(it, rs.size.toDouble(), Double::plus) }
        mc.secondari.forEach { punteggi.merge(it, rs.size * 0.5, Double::plus) }
    }
    val muscoliPeriodo = punteggi.values.maxOrNull()?.let { max ->
        val (forti, deboli) = punteggi.entries.partition { it.value >= max * 0.6 }
        MuscoliCoinvolti(
            forti.sortedByDescending { it.value }.mapTo(LinkedHashSet()) { it.key },
            deboli.sortedByDescending { it.value }.mapTo(LinkedHashSet()) { it.key }
        )
    }

    // ---- Andamento per esercizio (periodo + metrica) ----
    val eserciziDisponibili = righeF.distinctBy { it.esercizioId }
        .map { EsercizioRef(it.esercizioId, it.nomeEsercizio) }
        .sortedBy { it.nome }
    val selId = sel.takeIf { s -> eserciziDisponibili.any { it.id == s } }
        ?: eserciziDisponibili.firstOrNull()?.id
    val andamento = if (selId == null) emptyList() else {
        righeF.filter { it.esercizioId == selId }
            .groupBy { it.sessioneId }
            .map { (_, rs) ->
                val valore = when (metrica) {
                    MetricaTrend.PESO -> rs.mapNotNull { it.pesoKg }.maxOrNull() ?: 0f
                    MetricaTrend.VOLUME -> rs.sumOf(::volumeRiga).toFloat()
                }
                PuntoAndamento(rs.first().dataInizio, valore)
            }
            .sortedBy { it.dataMs }
    }

    // ---- Storico con dettaglio (periodo) ----
    val righePerSessione = righeF.groupBy { it.sessioneId }
    val storico = sessioniF.sortedByDescending { it.dataInizio }.map { s ->
        val rs = righePerSessione[s.id].orEmpty()
        val dettaglio = rs.groupBy { it.esercizioId }.map { (_, righeEs) ->
            EsercizioStorico(
                nome = righeEs.first().nomeEsercizio,
                serie = righeEs.sortedBy { it.numero }.map(::descriviRiga)
            )
        }
        VoceStorico(
            id = s.id,
            dataMs = s.dataInizio,
            nome = s.nomeScheda,
            durataMs = (s.dataFine ?: s.dataInizio) - s.dataInizio,
            volume = rs.sumOf(::volumeRiga),
            numSerie = rs.size,
            dettaglio = dettaglio
        )
    }

    return ReportState(
        pronto = true,
        periodo = periodo,
        metrica = metrica,
        sessioniTotali = sessioni.size,
        numeroSessioni = sessioniF.size,
        volumeTotale = righeF.sumOf(::volumeRiga),
        giorniAllenati = sessioniF.map { giornoDi(it.dataInizio) }.toSet().size,
        serieTotali = righeF.size,
        durataMediaMin = durataMediaMin,
        durataMediaMs = if (durate.isEmpty()) 0L else durate.sum() / durate.size,
        confronto = confronto,
        settimaneConsecutive = streak,
        migliorStreak = migliorStreak,
        allenamentiSettimana = allenamentiSettimana,
        heatmap = heatmap,
        volumeSettimanale = volumeSettimanale,
        volumeMensile = volumeMensile,
        pr = pr,
        distribuzioneMuscolare = distribuzione,
        muscoliPeriodo = muscoliPeriodo,
        eserciziDisponibili = eserciziDisponibili,
        esercizioAndamento = selId,
        andamento = andamento,
        storico = storico
    )
}
