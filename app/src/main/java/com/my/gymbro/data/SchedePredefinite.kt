package com.my.gymbro.data

/*
 * Schede predefinite "Total body A/B/C" (martedì, giovedì, venerdì).
 * Pensate per: principiante, obiettivo ricomposizione (tonico e asciutto), preferenza per le macchine,
 * 40-50 minuti + riscaldamento e camminata facoltativa.
 *
 * Giovedì e venerdì sono consecutivi, quindi il venerdì (C) non ripete il B:
 *  - gambe: niente quadricipiti né femorali, solo abduttori e polpacci;
 *  - petto: croci alla macchina, nessuna spinta;
 *  - schiena: tirata verticale, il B ha quella orizzontale.
 *
 * Serie dirette a settimana: quadricipiti 8, femorali 6, petto 8, schiena 9, deltoidi laterali 4,
 * bicipiti 3, tricipiti 5, polpacci 5.
 *
 * I pesi sono indicativi: ogni macchina pesa in modo diverso, vanno adattati alla prima seduta.
 * Se cambi qui, aumenta VERSIONE_SCHEDE: le schede già salvate si aggiornano tenendo pesi e copia.
 */

/** Una serie pianificata del template. [ripetizioni] null = esercizio a tempo ([durataSec]). */
data class SerieTemplate(
    val ripetizioni: String? = null,
    val pesoKg: Float? = null,
    val durataSec: Int? = null,
    val recuperoSec: Int,
)

data class EsercizioTemplate(
    val nomeEsercizio: String,          // deve coincidere con un esercizio della libreria
    val modalita: Modalita,
    val serie: List<SerieTemplate>,
    val note: String,
    val supersetConProssimo: Boolean = false,
)

data class SchedaTemplate(
    val nome: String,
    val note: String,
    val esercizi: List<EsercizioTemplate>,
)

private fun rip(n: Int, ripetizioni: String, kg: Float?, recupero: Int) =
    List(n) { SerieTemplate(ripetizioni = ripetizioni, pesoKg = kg, recuperoSec = recupero) }

private fun tempo(n: Int, secondi: Int, recupero: Int) =
    List(n) { SerieTemplate(durataSec = secondi, recuperoSec = recupero) }

private fun ex(nome: String, serie: List<SerieTemplate>, note: String, superset: Boolean = false) =
    EsercizioTemplate(
        nomeEsercizio = nome,
        modalita = if (serie.first().ripetizioni == null) Modalita.TEMPO else Modalita.RIPETIZIONI,
        serie = serie,
        note = note,
        supersetConProssimo = superset,
    )

private const val NOTA_GENERALE =
    "Riscaldamento 5–8 min (cyclette o camminata) + 1 serie leggera sul primo esercizio. " +
        "Peso giusto = finisci la serie sentendo che ne faresti ancora 2 pulite. " +
        "Quando completi il massimo delle ripetizioni in tutte le serie, aumenta di poco il peso " +
        "(con Gym Buddy attivo aspetta la sua proposta). " +
        "Prime 2 settimane: pesi leggeri, priorità alla tecnica."

private val CAMMINATA = ex(
    "Tapis roulant",
    tempo(1, 600, 0),
    "Facoltativo: 10 min di camminata in salita (pendenza 8–10%, 5–5,5 km/h). Aiuta ad asciugarsi senza togliere recupero."
)

private const val NOTA_LATI = "Serie 1 = lato destro, serie 2 = lato sinistro. "
private const val NOTA_STRETCH = "Allungamento lento e senza molleggiare: devi sentire tensione, non dolore. Respira profondamente."

/** Stretching su due lati: 2 serie da 30 s, 10 s per cambiare lato. */
private fun stretchLati(nome: String) = ex(nome, tempo(2, 30, 10), NOTA_LATI + NOTA_STRETCH)

/** Stretching singolo da 40 s. */
private fun stretch(nome: String, secondi: Int = 40) = ex(nome, tempo(1, secondi, 10), NOTA_STRETCH)

fun schedePredefinite(): List<SchedaTemplate> = listOf(
    SchedaTemplate(
        nome = "A · Total body — Martedì",
        note = "Giorno più impegnativo per le gambe (arrivi da 3 giorni di riposo). $NOTA_GENERALE",
        esercizi = listOf(
            ex("Leg press", rip(3, "10-12", 40f, 90),
                "Piedi a larghezza spalle a metà pedana. Scendi finché la zona lombare resta appoggiata allo schienale; spingi coi talloni senza bloccare le ginocchia in alto."),
            ex("Leg curl sdraiato", rip(3, "10-12", 20f, 60),
                "Sdraiato a pancia in giù, ginocchia appena oltre il bordo della panca e rullo sopra i talloni. Porta i talloni verso i glutei senza alzare il bacino, tieni 1 secondo e scendi lento."),
            ex("Leg extension", rip(2, "12-15", 20f, 60),
                "Schiena appoggiata, rullo sopra le caviglie. Estendi fino in fondo, tieni 1 secondo e scendi lento."),
            ex("Chest press", rip(3, "8-12", 20f, 90),
                "Regola il sedile così che le maniglie siano all'altezza del petto. Scapole indietro e in basso, spingi senza bloccare i gomiti e torna lento (2 secondi)."),
            ex("Lat machine", rip(3, "10-12", 30f, 90),
                "Presa poco più larga delle spalle. Tira la barra al petto alto portando i gomiti verso i fianchi; busto fermo, niente slanci."),
            ex("Alzate laterali", rip(2, "12-15", 4f, 60),
                "Il peso è per mano. Gomiti leggermente piegati, sali fino all'altezza delle spalle e scendi lento. Meglio leggero e pulito che pesante con slancio."),
            ex("Estensioni sopra la testa ai cavi", rip(2, "10-12", 10f, 60),
                "Di spalle al cavo, corda dietro la testa e gomiti alti vicino alle orecchie. Distendi le braccia in avanti e in alto, poi torna lento sentendo allungare i tricipiti. I gomiti restano fermi."),
            ex("Rotary calf", rip(2, "12-15", 20f, 60),
                "Avampiedi sulla pedana e schiena appoggiata. Spingi fino in fondo, tieni 1 secondo e torna lento allungando bene il polpaccio."),
            ex("Plank", tempo(3, 35, 45),
                "Gomiti sotto le spalle, glutei e addome contratti, bacino in linea (non deve cadere né salire). Respira normalmente."),
            CAMMINATA,
            // Stretching finale (~5 min): anche, femorali, petto e dorso
            stretchLati("Stretching flessori dell'anca"),
            stretchLati("Stretching femorali"),
            stretchLati("Stretching pettorali al muro"),
            stretch("Stretching dorsali alla panca"),
            stretch("Posizione del bambino"),
        )
    ),
    SchedaTemplate(
        nome = "B · Total body — Giovedì",
        note = "Intensità media. Gambe: quadricipiti (hack squat) e femorali (macchina); lombari con le iperestensioni. $NOTA_GENERALE",
        esercizi = listOf(
            ex("Hack squat alla macchina", rip(3, "10-12", 20f, 90),
                "Inizia con la slitta scarica o pochi kg. Schiena e testa appoggiate, piedi a larghezza spalle a metà pedana. Scendi finché le cosce sono parallele alla pedana con le ginocchia in linea con le punte, poi spingi coi talloni."),
            ex("Leg curl seduto", rip(3, "10-12", 25f, 60),
                "Ginocchia allineate al perno della macchina. Fletti fino in fondo, tieni 1 secondo e risali lento."),
            ex("Chest press inclinata", rip(3, "10-12", 15f, 90),
                "Sedile regolato con le maniglie all'altezza della parte alta del petto. Scapole indietro, spingi in alto e in avanti senza bloccare i gomiti, torna lento."),
            ex("Shoulder press alla macchina", rip(3, "10-12", 15f, 90),
                "Maniglie all'altezza delle spalle in partenza. Spingi in alto senza inarcare la schiena (glutei e addome contratti)."),
            ex("Pulley basso", rip(3, "10-12", 30f, 90),
                "Busto dritto e fermo, tira l'impugnatura verso l'ombelico stringendo le scapole; non dondolare avanti e indietro."),
            ex("Reverse fly alla macchina", rip(2, "12-15", 15f, 60),
                "Petto contro lo schienale della pectoral machine (in posizione inversa), maniglie all'altezza delle spalle. Apri le braccia all'indietro stringendo le scapole, poi torna lento. Ottimo contro la postura da scrivania."),
            ex("Iperestensioni", rip(2, "12-15", null, 60),
                "Per la zona lombare. Bordo del cuscinetto all'altezza delle anche, braccia incrociate al petto. Scendi lento con la schiena dritta e risali fino ad avere il corpo in linea, senza inarcarti oltre. Quando 15 diventano facili, tieni un disco al petto."),
            ex("Plank laterale", tempo(4, 25, 15),
                "Serie 1 e 3 sul lato destro, serie 2 e 4 sul sinistro. Gomito sotto la spalla, corpo in linea dalla testa ai piedi, fianchi alti: non lasciarli cadere."),
            CAMMINATA,
            // Stretching finale (~5 min): quadricipiti, glutei, colonna e spalle
            stretchLati("Stretching quadricipiti in piedi"),
            stretchLati("Stretching glutei a figura 4"),
            stretchLati("Rotazione toracica a terra"),
            stretch("Gatto-mucca"),
            stretchLati("Stretching tricipiti e spalle"),
        )
    ),
    SchedaTemplate(
        nome = "C · Total body — Venerdì",
        note = "Giorno dopo il B, quindi più leggero: gambe solo abduttori e polpacci, niente spinte, schiena con tirata verticale. $NOTA_GENERALE",
        esercizi = listOf(
            ex("Lat machine presa stretta", rip(3, "10-12", 25f, 90),
                "Maniglia stretta. Tira al petto alto portando i gomiti giù lungo i fianchi; busto fermo, risali lento fino a braccia distese."),
            ex("Pectoral machine", rip(2, "12-15", 20f, 60),
                "Schiena appoggiata, gomiti leggermente piegati e all'altezza delle spalle. Chiudi le braccia davanti al petto stringendo 1 secondo, poi riapri lento senza far toccare i pesi."),
            ex("Alzate laterali alla macchina", rip(2, "12-15", 10f, 60),
                "Braccia contro i cuscinetti. Sali fino all'altezza delle spalle senza alzarle verso le orecchie e scendi lento."),
            ex("Curl con manubri", rip(3, "10-12", 6f, 0),
                "Superset con il pushdown: fai le due serie una dopo l'altra, poi recupera. Il peso è per mano. Gomiti fermi ai fianchi, niente slancio.",
                superset = true),
            ex("Pushdown ai cavi", rip(3, "10-12", 15f, 60),
                "Gomiti incollati ai fianchi, spingi verso il basso fino a braccia distese e risali controllando."),
            ex("Abductor machine", rip(3, "12-15", 20f, 60),
                "Schiena appoggiata. Apri le gambe senza slancio, tieni 1 secondo e richiudi lento senza far toccare i pesi."),
            ex("Rotary calf", rip(3, "12-15", 20f, 60),
                "Avampiedi sulla pedana e schiena appoggiata. Spingi fino in fondo, tieni 1 secondo e torna lento allungando bene il polpaccio."),
            ex("Crunch inverso", rip(3, "10-12", null, 60),
                "Sdraiato, mani lungo i fianchi. Porta le ginocchia verso il petto staccando il bacino da terra, senza slancio; scendi lento."),
            CAMMINATA,
            // Stretching finale (~5 min): anche, glutei, petto e addome
            stretchLati("Stretching flessori dell'anca"),
            stretchLati("Stretching glutei a figura 4"),
            stretchLati("Stretching pettorali al muro"),
            stretch("Cobra", 30),
            stretch("Posizione del bambino"),
        )
    ),
)
