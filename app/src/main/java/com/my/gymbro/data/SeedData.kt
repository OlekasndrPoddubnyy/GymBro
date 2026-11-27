package com.my.gymbro.data

import com.my.gymbro.data.Attrezzatura.*
import com.my.gymbro.data.GruppoMuscolare.*

/**
 * Slug usato per la cartella delle immagini in assets/esercizi/<slug>/{0,1}.jpg.
 * Deve restare identico alla logica dello script che ha scaricato le immagini.
 */
fun slugEsercizio(nome: String): String = nome.lowercase()
    .replace('à', 'a').replace('è', 'e').replace('é', 'e')
    .replace('ì', 'i').replace('ò', 'o').replace('ù', 'u')
    .replace(Regex("[^a-z0-9]"), "_")
    .replace(Regex("_+"), "_")
    .trim('_')

private const val PREFISSO_TECHNOGYM = "Technogym: "

/** Nome della macchina Technogym (prima riga della descrizione "Technogym: …"), se presente. */
fun macchinaTechnogym(descrizione: String?): String? =
    descrizione?.lineSequence()?.firstOrNull()
        ?.takeIf { it.startsWith(PREFISSO_TECHNOGYM) }
        ?.removePrefix(PREFISSO_TECHNOGYM)
        ?.trim()

/** Descrizione senza la riga "Technogym: …". */
fun descrizioneSenzaMacchina(descrizione: String?): String? =
    if (macchinaTechnogym(descrizione) != null) descrizione!!.substringAfter('\n', "").trim().ifBlank { null }
    else descrizione

/**
 * Esercizio predefinito.
 * @param tg nome della macchina Technogym (linea Selection/Element+): finisce come prima riga
 *   della descrizione ("Technogym: …") ed è cercabile dalla libreria.
 * @param img slug di una cartella immagini esistente da riusare quando l'esercizio non ha
 *   immagini proprie (movimento equivalente). Viene trasportato temporaneamente in
 *   [Esercizio.immaginePath] e risolto da [GymBroRepository.assicuraEserciziPredefiniti].
 */
private fun e(
    nome: String,
    gruppo: GruppoMuscolare,
    attrezzo: Attrezzatura,
    descrizione: String,
    tg: String? = null,
    img: String? = null,
) = Esercizio(
    nome = nome,
    gruppoMuscolare = gruppo,
    attrezzatura = attrezzo,
    descrizione = if (tg != null) "$PREFISSO_TECHNOGYM$tg\n$descrizione" else descrizione,
    immaginePath = img,
    predefinito = true,
    chiaveSeed = nome,
)

/**
 * Testi di esecuzione di versioni precedenti del seed, poi corretti (senza la riga "Technogym: …").
 * Se un esercizio predefinito ha ancora esattamente uno di questi testi, l'utente non l'ha
 * modificato e viene allineato alla versione attuale.
 */
val descrizioniSuperate: Map<String, Set<String>> = mapOf(
    "Panca inclinata con bilanciere" to "Su panca inclinata 30-45°, spingi il bilanciere verso l'alto allenando la parte alta del petto.",
    "Panca declinata con bilanciere" to "Su panca declinata, spingi il bilanciere in alto enfatizzando la parte bassa del petto.",
    "Rack pull" to "Stacco parziale con il bilanciere appoggiato sui fermi del rack all'altezza delle ginocchia: estendi le anche e stringi le scapole.",
    "Iperestensioni" to "Sulla panca apposita, scendi col busto e risali estendendo la schiena, per lombari e glutei.",
    "Lento avanti" to "In piedi, spingi il bilanciere dalle spalle sopra la testa fino a braccia distese.",
    "Tirate al mento con bilanciere" to "Tira il bilanciere lungo il corpo fino al mento con i gomiti alti, per spalle e trapezi.",
    "Arnold press" to "Parti con i manubri davanti e ruota i polsi mentre spingi sopra la testa, per tutto il deltoide.",
    "Tirate al mento ai cavi" to "Tira la barra del cavo basso verso il mento con i gomiti alti.",
    "Pike push-up" to "In posizione a V rovesciata, piega le braccia portando la testa a terra e spingi.",
    "Distensioni presa stretta" to "Su panca, presa stretta sul bilanciere, spingi enfatizzando i tricipiti.",
    "French press" to "Sdraiato, porta il bilanciere dietro la fronte piegando i gomiti e ridistendi per i tricipiti.",
    "Affondi inversi con manubri" to "Manubri ai fianchi, passo indietro e ginocchio dietro verso terra: più facile per le ginocchia dell'affondo avanti.",
    "Adductor machine" to "Seduto, chiudi le gambe contro la resistenza per gli adduttori interno coscia.",
    "Calf alla pressa" to "Alla pressa, spingi con le punte dei piedi estendendo le caviglie.",
    "Hip thrust" to "Schiena su una panca e bilanciere sull'anca, spingi il bacino in alto contraendo i glutei.",
    "Ponte glutei con bilanciere" to "A terra, bilanciere sull'anca, solleva il bacino contraendo i glutei.",
    "Pull-through ai cavi" to "Tra le gambe, tira la fune del cavo basso estendendo l'anca in avanti, per i glutei.",
    "Abduzione ai cavi" to "Cavigliera al cavo basso, porta la gamba lateralmente verso l'esterno.",
    "Ponte glutei a corpo libero" to "A terra, solleva il bacino contraendo i glutei, senza pesi.",
    "Crunch ai cavi" to "In ginocchio sotto il cavo alto, fletti il busto verso il basso contraendo l'addome.",
).mapValues { setOf(it.value) }

fun eserciziPredefiniti(): List<Esercizio> = listOf(
    // ---------- PETTO ----------
    e("Panca piana", PETTO, BILANCIERE, "Sdraiato sulla panca, spingi il bilanciere dal petto verso l'alto fino a braccia distese, poi controlla la discesa."),
    e("Panca inclinata con bilanciere", PETTO, BILANCIERE, "Su panca inclinata 30-45°, abbassa il bilanciere alla parte alta del petto e spingi fino a braccia distese."),
    e("Panca declinata con bilanciere", PETTO, BILANCIERE, "Su panca declinata con le gambe bloccate, abbassa il bilanciere alla parte bassa del petto e spingi in alto."),
    e("Floor press con bilanciere", PETTO, BILANCIERE, "Sdraiato a terra, spingi il bilanciere dal petto: i gomiti toccano il pavimento e limitano l'escursione, utile per tricipiti e lockout."),
    e("Panca inclinata manubri", PETTO, MANUBRI, "Su panca inclinata, spingi i manubri verso l'alto fino a unirli, poi scendi in controllo."),
    e("Panca piana con manubri", PETTO, MANUBRI, "Sdraiato, spingi i due manubri dal petto verso l'alto controllando l'apertura in basso."),
    e("Panca declinata con manubri", PETTO, MANUBRI, "Su panca declinata, distendi i manubri verso l'alto per la parte bassa del petto."),
    e("Squeeze press con manubri", PETTO, MANUBRI, "Sdraiato, premi i manubri uno contro l'altro sopra il petto e spingili in alto senza mai separarli: tensione costante sui pettorali.", img = "panca_piana_con_manubri"),
    e("Croci su panca piana", PETTO, MANUBRI, "Braccia semi-tese, apri ad arco i manubri e richiudili sopra il petto isolando i pettorali."),
    e("Croci su panca inclinata", PETTO, MANUBRI, "Su panca inclinata, apri e chiudi le braccia ad arco con i manubri per il petto alto."),
    e("Pullover con manubrio", PETTO, MANUBRI, "Sdraiato di traverso, porta un manubrio dietro la testa a braccia semi-tese e riportalo sul petto."),
    e("Chest press", PETTO, MACCHINA, "Seduto alla macchina, spingi le maniglie in avanti fino a distendere le braccia, poi torna lentamente.", tg = "Chest Press"),
    e("Chest press inclinata", PETTO, MACCHINA, "Alla macchina inclinata, spingi le maniglie verso l'alto per la parte alta del petto.", tg = "Chest Incline"),
    e("Chest press declinata alla macchina", PETTO, MACCHINA, "Spingi le maniglie in avanti e leggermente verso il basso: traiettoria guidata per la parte bassa del petto.", tg = "Decline Chest Press", img = "chest_press"),
    e("Pectoral machine", PETTO, MACCHINA, "Seduto, chiudi le braccia davanti al petto contro i cuscinetti e riapri lentamente.", tg = "Pectoral"),
    e("Panca piana al multipower", PETTO, MACCHINA, "Panca sotto il bilanciere guidato: abbassalo al petto e spingi in alto. Più stabile della panca libera, utile senza assistente.", tg = "Multipower", img = "panca_piana"),
    e("Panca inclinata al multipower", PETTO, MACCHINA, "Panca inclinata 30° sotto il bilanciere guidato, scendi alla parte alta del petto e spingi.", tg = "Multipower", img = "panca_inclinata_con_bilanciere"),
    e("Croci ai cavi", PETTO, CAVI, "Tra i cavi alti, porta le maniglie davanti al petto ad arco e controlla il ritorno."),
    e("Croci ai cavi bassi", PETTO, CAVI, "Dai cavi bassi, porta le maniglie verso l'alto e al centro per il petto alto."),
    e("Croci ai cavi a metà altezza", PETTO, CAVI, "Cavi all'altezza del petto: porta le maniglie avanti ad arco fino a unirle, stringi 1 secondo e torna lento.", img = "croci_ai_cavi"),
    e("Croci ai cavi a un braccio", PETTO, CAVI, "Di fianco al cavo, porta una maniglia ad arco davanti al petto oltre la linea centrale; un lato alla volta.", img = "croci_ai_cavi"),
    e("Chest press ai cavi", PETTO, CAVI, "In piedi, spingi le maniglie dei cavi in avanti distendendo le braccia all'altezza del petto."),
    e("Piegamenti sulle braccia", PETTO, CORPO_LIBERO, "In appoggio su mani e piedi, scendi col petto e spingi fino a braccia distese, corpo in linea."),
    e("Piegamenti su rialzo", PETTO, CORPO_LIBERO, "Piegamenti con le mani su un rialzo per ridurre il carico, ideali per iniziare."),
    e("Piegamenti sulle ginocchia", PETTO, CORPO_LIBERO, "Piegamenti in appoggio sulle ginocchia invece che sulle punte: busto in linea dalle ginocchia alla testa.", img = "piegamenti_su_rialzo"),
    e("Piegamenti presa larga", PETTO, CORPO_LIBERO, "Mani più larghe delle spalle: scendi col petto verso terra per enfatizzare i pettorali.", img = "piegamenti_sulle_braccia"),
    e("Piegamenti declinati", PETTO, CORPO_LIBERO, "Piedi su una panca e mani a terra: piegamenti più difficili che spostano il lavoro sul petto alto e le spalle.", img = "piegamenti_sulle_braccia"),
    e("Dip alle parallele", PETTO, CORPO_LIBERO, "Alle parallele, scendi piegando i gomiti col busto inclinato avanti e risali distendendo le braccia."),

    // ---------- SCHIENA ----------
    e("Stacco da terra", SCHIENA, BILANCIERE, "Con schiena dritta, solleva il bilanciere da terra estendendo gambe e anche fino in piedi, poi controlla la discesa."),
    e("Rack pull", SCHIENA, BILANCIERE, "Stacco parziale con il bilanciere appoggiato sui fermi del rack all'altezza delle ginocchia: schiena dritta, estendi le anche fino a stare in piedi con le spalle indietro.", img = "stacco_da_terra"),
    e("Rematore con bilanciere", SCHIENA, BILANCIERE, "Busto inclinato in avanti, tira il bilanciere verso l'addome stringendo le scapole."),
    e("Rematore presa inversa", SCHIENA, BILANCIERE, "Come il rematore ma con presa supina, per coinvolgere di più dorso basso e bicipiti."),
    e("Rematore Pendlay", SCHIENA, BILANCIERE, "Busto parallelo a terra, ogni ripetizione parte da fermo col bilanciere a terra: tira esplosivo verso lo sterno e riappoggia.", img = "rematore_con_bilanciere"),
    e("Rematore T-bar", SCHIENA, BILANCIERE, "Bilanciere ancorato a terra (landmine) con maniglia a V: busto inclinato, tira l'estremità verso il petto stringendo le scapole."),
    e("Rematore con manubrio", SCHIENA, MANUBRI, "Con un ginocchio sulla panca, tira il manubrio verso il fianco portando il gomito indietro."),
    e("Rematore con due manubri", SCHIENA, MANUBRI, "In piedi, busto inclinato e schiena dritta: tira i due manubri verso i fianchi insieme, stringi le scapole e scendi lento.", img = "rematore_con_manubrio"),
    e("Rematore su panca inclinata con manubri", SCHIENA, MANUBRI, "Petto appoggiato su una panca inclinata 30-45°: tira i manubri verso i fianchi senza usare la zona lombare."),
    e("Lat machine", SCHIENA, MACCHINA, "Seduto, tira la barra alta verso il petto stringendo le scapole, poi risali in controllo.", tg = "Lat Machine"),
    e("Lat machine presa inversa", SCHIENA, MACCHINA, "Tira la barra al petto con presa supina e stretta, enfatizzando la parte bassa del dorso.", tg = "Lat Machine"),
    e("Lat machine presa stretta", SCHIENA, MACCHINA, "Con maniglia a presa stretta, tira verso il petto per lavorare lo spessore del dorso.", tg = "Lat Machine"),
    e("Vertical traction", SCHIENA, MACCHINA, "Leve convergenti che scendono dall'alto: tira le maniglie verso le spalle portando i gomiti ai fianchi, petto alto, poi risali lento.", tg = "Vertical Traction", img = "lat_machine"),
    e("Pulley basso", SCHIENA, MACCHINA, "Seduto, tira l'impugnatura verso l'addome tenendo il busto fermo e stringendo le scapole.", tg = "Pulley"),
    e("Rematore alla macchina", SCHIENA, MACCHINA, "Petto contro il supporto, tira le maniglie verso di te portando i gomiti indietro.", tg = "Low Row"),
    e("Upper back alla macchina", SCHIENA, MACCHINA, "Petto contro il supporto, gomiti alti all'altezza delle spalle: tira indietro aprendo i gomiti per dorso alto, romboidi e deltoidi posteriori.", tg = "Upper Back", img = "rematore_alla_macchina"),
    e("Lower back alla macchina", SCHIENA, MACCHINA, "Seduto con lo schienale mobile sulla zona lombare: estendi il busto all'indietro in modo controllato e torna lento, senza strattoni.", tg = "Lower Back", img = "iperestensioni"),
    e("Trazioni assistite alla macchina", SCHIENA, MACCHINA, "Trazioni con assistenza al peso, per allenare il dorso fino a riuscire a farle libere.", tg = "Assisted Chin Dip"),
    e("Iperestensioni", SCHIENA, MACCHINA, "Sulla panca apposita col bacino sul cuscinetto, scendi col busto e risali fino ad allinearlo alle gambe, senza inarcare oltre. Per lombari, glutei e femorali."),
    e("Rematore ai cavi", SCHIENA, CAVI, "Seduto al cavo basso, tira l'impugnatura verso l'addome stringendo le scapole."),
    e("Rematore a un braccio ai cavi", SCHIENA, CAVI, "In piedi o in affondo davanti al cavo a metà altezza, tira la maniglia verso il fianco con un braccio, ruotando appena il busto.", img = "rematore_ai_cavi"),
    e("Pulldown a un braccio ai cavi", SCHIENA, CAVI, "Dal cavo alto, tira con un braccio alla volta per un lavoro mirato del dorso.", img = "lat_machine_presa_stretta"),
    e("Pullover ai cavi", SCHIENA, CAVI, "Davanti al cavo alto, a braccia tese porta la barra verso le cosce attivando il gran dorsale."),
    e("Trazioni alla sbarra", SCHIENA, CORPO_LIBERO, "Appeso alla sbarra, tirati su fino a portare il mento sopra di essa, poi scendi controllando."),
    e("Trazioni presa inversa", SCHIENA, CORPO_LIBERO, "Trazioni con palmi rivolti verso di te: più bicipiti e dorso basso."),
    e("Trazioni presa larga", SCHIENA, CORPO_LIBERO, "Trazioni con presa ampia per enfatizzare l'ampiezza del dorso."),
    e("Trazioni presa neutra", SCHIENA, CORPO_LIBERO, "Trazioni alle maniglie parallele (palmi affrontati): presa più comoda per spalle e gomiti.", img = "trazioni_alla_sbarra"),
    e("Australian pull-up", SCHIENA, CORPO_LIBERO, "Sotto una barra bassa e corpo teso, tirati verso la barra mantenendo il corpo in linea."),
    e("Superman", SCHIENA, CORPO_LIBERO, "A pancia in giù, braccia distese avanti: solleva insieme braccia, petto e gambe di pochi centimetri, tieni 2 secondi e scendi."),

    // ---------- SPALLE ----------
    e("Lento avanti", SPALLE, BILANCIERE, "In piedi, glutei e addome contratti: spingi il bilanciere dalle clavicole sopra la testa fino a braccia distese, senza inarcare la schiena."),
    e("Push press", SPALLE, BILANCIERE, "Bilanciere sulle spalle: piccolo piegamento di gambe e spinta esplosiva per portarlo sopra la testa, poi scendi controllando.", img = "lento_avanti"),
    e("Landmine press", SPALLE, BILANCIERE, "Bilanciere ancorato a terra: spingi l'estremità in alto e in avanti con una mano. Traiettoria ad arco, amica delle spalle."),
    e("Tirate al mento con bilanciere", SPALLE, BILANCIERE, "Presa poco più larga delle spalle: tira il bilanciere lungo il corpo fino al petto alto, gomiti che guidano senza superare l'altezza delle spalle. Per deltoidi e trapezi."),
    e("Scrollate con bilanciere", SPALLE, BILANCIERE, "Solleva le spalle verso le orecchie tenendo il bilanciere, per i trapezi."),
    e("Lento avanti con manubri", SPALLE, MANUBRI, "Seduto o in piedi, spingi i manubri sopra la testa e controlla la discesa."),
    e("Arnold press", SPALLE, MANUBRI, "Parti con i manubri davanti alle spalle e i palmi verso di te: spingi sopra la testa ruotando i polsi fino ad avere i palmi in avanti, poi torna invertendo il movimento."),
    e("Alzate laterali", SPALLE, MANUBRI, "Solleva i manubri lateralmente fino all'altezza delle spalle, gomiti leggermente piegati."),
    e("Alzate frontali", SPALLE, MANUBRI, "Solleva i manubri davanti a te fino all'altezza delle spalle, per il deltoide anteriore."),
    e("Alzate posteriori", SPALLE, MANUBRI, "Busto inclinato in avanti, apri le braccia all'indietro per il deltoide posteriore."),
    e("Tirate al mento con manubri", SPALLE, MANUBRI, "Manubri davanti alle cosce: tirali verso il petto alto con i gomiti che salgono sopra i polsi, senza superare l'altezza delle spalle.", img = "tirate_al_mento_con_bilanciere"),
    e("Scrollate con manubri", SPALLE, MANUBRI, "Solleva le spalle verso l'alto con i manubri ai fianchi, per i trapezi."),
    e("Shoulder press alla macchina", SPALLE, MACCHINA, "Seduto, spingi le maniglie sopra la testa fino a distendere le braccia.", tg = "Shoulder Press"),
    e("Lento avanti al multipower", SPALLE, MACCHINA, "Seduto su panca a schienale verticale sotto il bilanciere guidato: spingi dalle spalle sopra la testa.", tg = "Multipower", img = "lento_avanti"),
    e("Alzate laterali alla macchina", SPALLE, MACCHINA, "Apri le braccia lateralmente contro i cuscinetti per i deltoidi laterali.", tg = "Delts Machine", img = "alzate_laterali"),
    e("Reverse fly alla macchina", SPALLE, MACCHINA, "Rivolto alla macchina, apri le braccia all'indietro per i deltoidi posteriori.", tg = "Pectoral / Reverse Fly"),
    e("Alzate laterali ai cavi", SPALLE, CAVI, "Dal cavo basso, solleva il braccio lateralmente fino all'altezza della spalla."),
    e("Alzate frontali ai cavi", SPALLE, CAVI, "Di spalle al cavo basso, solleva la maniglia (o la corda) davanti a te fino all'altezza delle spalle.", img = "alzate_frontali"),
    e("Alzate posteriori ai cavi", SPALLE, CAVI, "Ai cavi incrociati, apri le braccia all'indietro per i deltoidi posteriori."),
    e("Y-raise ai cavi", SPALLE, CAVI, "Cavi bassi incrociati: solleva le braccia in diagonale sopra la testa formando una Y, per deltoidi e trapezio basso."),
    e("Tirate al mento ai cavi", SPALLE, CAVI, "Dal cavo basso, tira la barra lungo il corpo fino al petto alto con i gomiti che guidano, senza superare l'altezza delle spalle.", img = "tirate_al_mento_con_bilanciere"),
    e("Scrollate ai cavi", SPALLE, CAVI, "Barra o maniglie ai cavi bassi: solleva le spalle verso le orecchie, tieni 1 secondo e scendi.", img = "scrollate_con_bilanciere"),
    e("Face pull ai cavi", SPALLE, CAVI, "Dal cavo all'altezza del viso, tira la corda verso la fronte aprendo i gomiti."),
    e("Rotazione esterna ai cavi", SPALLE, CAVI, "Cavo all'altezza del gomito, gomito piegato a 90° attaccato al fianco: ruota l'avambraccio verso l'esterno. Per la cuffia dei rotatori, carico leggero."),
    e("Rotazione interna ai cavi", SPALLE, CAVI, "Cavo all'altezza del gomito, gomito a 90° attaccato al fianco: porta l'avambraccio verso l'addome. Per la cuffia dei rotatori, carico leggero."),
    e("Pike push-up", SPALLE, CORPO_LIBERO, "Bacino alto a V rovesciata, mani poco più larghe delle spalle: piega i gomiti portando la testa verso terra davanti alle mani, poi spingi fino a braccia distese."),
    e("Piegamenti in verticale al muro", SPALLE, CORPO_LIBERO, "In verticale con i piedi appoggiati al muro, scendi con la testa verso terra e spingi: esercizio avanzato per spalle e tricipiti."),

    // ---------- BICIPITI ----------
    e("Curl con bilanciere", BICIPITI, BILANCIERE, "In piedi, fletti i gomiti portando il bilanciere verso le spalle, poi scendi lentamente."),
    e("Curl con bilanciere EZ", BICIPITI, BILANCIERE, "Curl con bilanciere sagomato (EZ) per ridurre lo stress sui polsi."),
    e("Curl presa inversa", BICIPITI, BILANCIERE, "Curl con presa prona per coinvolgere avambracci e brachiale."),
    e("Curl con manubri", BICIPITI, MANUBRI, "Fletti i gomiti portando i manubri verso le spalle, controllando la discesa."),
    e("Curl alternato", BICIPITI, MANUBRI, "Curl con manubri sollevando un braccio alla volta."),
    e("Curl a martello", BICIPITI, MANUBRI, "Curl con manubri a presa neutra (palmi affrontati), per brachiale e avambraccio."),
    e("Curl concentrato", BICIPITI, MANUBRI, "Seduto, gomito appoggiato all'interno coscia, fletti il manubrio isolando il bicipite."),
    e("Curl su panca inclinata", BICIPITI, MANUBRI, "Seduto su panca inclinata, braccia in basso, fletti i manubri allungando il bicipite."),
    e("Curl Zottman", BICIPITI, MANUBRI, "Sali con i palmi in su, in alto ruota i polsi e scendi lento con i palmi in giù: bicipiti in salita, avambracci in discesa.", img = "curl_con_manubri"),
    e("Curl a ragno", BICIPITI, MANUBRI, "Petto appoggiato su una panca inclinata, braccia che pendono verticali: fletti i manubri senza muovere i gomiti."),
    e("Curl alla panca Scott", BICIPITI, MACCHINA, "Braccia appoggiate sul leggio Scott, fletti il carico isolando i bicipiti."),
    e("Curl alla macchina", BICIPITI, MACCHINA, "Seduto alla macchina, fletti le braccia contro la resistenza guidata.", tg = "Arm Curl"),
    e("Curl ai cavi", BICIPITI, CAVI, "Dal cavo basso, fletti i gomiti portando la barra verso le spalle con tensione costante."),
    e("Curl a martello ai cavi", BICIPITI, CAVI, "Con corda al cavo basso, curl a presa neutra per brachiale e avambraccio."),
    e("Curl ai cavi alti", BICIPITI, CAVI, "Tra due cavi alti, braccia aperte all'altezza delle spalle: fletti i gomiti portando le maniglie verso le tempie."),
    e("Curl Bayesian ai cavi", BICIPITI, CAVI, "Di spalle al cavo basso, braccio leggermente dietro al corpo: fletti il gomito. Il bicipite lavora in allungamento.", img = "curl_ai_cavi"),

    // ---------- TRICIPITI ----------
    e("Distensioni presa stretta", TRICIPITI, BILANCIERE, "Su panca, presa alla larghezza delle spalle: abbassa il bilanciere al petto basso con i gomiti vicini al corpo e spingi, enfatizzando i tricipiti."),
    e("French press", TRICIPITI, BILANCIERE, "Sdraiato su panca, braccia verticali: piega solo i gomiti portando il bilanciere verso la fronte, poi distendi senza muovere le braccia."),
    e("French press con manubri", TRICIPITI, MANUBRI, "Sdraiato con un manubrio per mano a presa neutra: piega i gomiti portando i manubri ai lati della testa e distendi.", img = "french_press"),
    e("Estensioni sopra la testa con manubrio", TRICIPITI, MANUBRI, "Porta un manubrio dietro la nuca e distendi le braccia in alto."),
    e("Kickback con manubri", TRICIPITI, MANUBRI, "Busto inclinato e gomito fermo, distendi l'avambraccio all'indietro per il tricipite."),
    e("Pushdown ai cavi", TRICIPITI, CAVI, "Dal cavo alto con corda, spingi le mani verso il basso distendendo i gomiti."),
    e("Pushdown ai cavi con barra", TRICIPITI, CAVI, "Pushdown con barra dritta, gomiti ai fianchi, distendi verso il basso."),
    e("Pushdown presa inversa", TRICIPITI, CAVI, "Pushdown con presa supina per enfatizzare il capo mediale del tricipite."),
    e("Pushdown a un braccio ai cavi", TRICIPITI, CAVI, "Maniglia singola al cavo alto: gomito incollato al fianco, distendi il braccio verso il basso. Un lato alla volta.", img = "pushdown_ai_cavi"),
    e("Kickback tricipiti ai cavi", TRICIPITI, CAVI, "Busto inclinato davanti al cavo basso, gomito fermo vicino al fianco: distendi l'avambraccio all'indietro.", img = "kickback_con_manubri"),
    e("Estensioni sopra la testa ai cavi", TRICIPITI, CAVI, "Rivolto lontano dal cavo, distendi le braccia sopra la testa per i tricipiti."),
    e("Tricipiti alla macchina", TRICIPITI, MACCHINA, "Seduto alla macchina, distendi le braccia contro la resistenza guidata.", tg = "Arm Extension"),
    e("Dip alla macchina", TRICIPITI, MACCHINA, "Alla macchina dip, spingi verso il basso distendendo le braccia."),
    e("Dip assistiti alla macchina", TRICIPITI, MACCHINA, "In ginocchio sulla pedana assistita, alle maniglie parallele: scendi piegando i gomiti e risali. Più peso = più aiuto.", tg = "Assisted Chin Dip", img = "dip_alle_parallele"),
    e("Distensioni presa stretta al multipower", TRICIPITI, MACCHINA, "Su panca sotto il bilanciere guidato, presa alla larghezza delle spalle: gomiti vicini al corpo, spingi in alto.", tg = "Multipower", img = "distensioni_presa_stretta"),
    e("Dip tra le panche", TRICIPITI, CORPO_LIBERO, "Mani su una panca dietro di te, scendi piegando i gomiti e risali per i tricipiti."),
    e("Piegamenti diamante", TRICIPITI, CORPO_LIBERO, "Piegamenti con le mani vicine a formare un rombo, per enfatizzare i tricipiti."),

    // ---------- GAMBE ----------
    e("Squat", GAMBE, BILANCIERE, "Bilanciere sulle spalle, scendi piegando anche e ginocchia fino a coscia parallela, poi risali."),
    e("Squat frontale", GAMBE, BILANCIERE, "Bilanciere sulle spalle anteriori, accosciati mantenendo il busto eretto."),
    e("Box squat", GAMBE, BILANCIERE, "Squat fino a sederti brevemente su una panca o box dietro di te, poi risali spingendo coi talloni: controlla la profondità.", img = "squat"),
    e("Affondi con bilanciere", GAMBE, BILANCIERE, "Bilanciere sulle spalle, fai un passo avanti e piega entrambe le ginocchia, poi torna."),
    e("Affondi inversi con bilanciere", GAMBE, BILANCIERE, "Bilanciere sulle spalle, fai un passo indietro e scendi col ginocchio dietro verso terra; torna spingendo con la gamba davanti.", img = "affondi_con_bilanciere"),
    e("Bulgarian split squat con bilanciere", GAMBE, BILANCIERE, "Bilanciere sulle spalle e piede dietro su una panca: scendi con la gamba davanti e risali.", img = "bulgarian_split_squat"),
    e("Step-up con bilanciere", GAMBE, BILANCIERE, "Bilanciere sulle spalle, sali su un box spingendo con la gamba sopra e scendi controllando.", img = "step_up_con_manubri"),
    e("Stacco gambe tese", GAMBE, BILANCIERE, "Gambe quasi tese, scendi col bilanciere lungo le gambe sentendo i femorali, poi risali."),
    e("Stacco rumeno", GAMBE, BILANCIERE, "Schiena dritta e gambe semi-flesse, scendi col bilanciere lungo le cosce e risali contraendo i glutei."),
    e("Good morning", GAMBE, BILANCIERE, "Bilanciere sulle spalle, fletti il busto avanti a gambe semi-tese e risali, per femorali e lombari."),
    e("Girata al petto", GAMBE, BILANCIERE, "Power clean: dal pavimento estendi esplosivamente anche e ginocchia e ricevi il bilanciere sulle spalle anteriori. Tecnica prima del carico."),
    e("Goblet squat con manubrio", GAMBE, MANUBRI, "Tieni un manubrio al petto e accosciati mantenendo il busto eretto."),
    e("Squat con manubri", GAMBE, MANUBRI, "Un manubrio per mano lungo i fianchi: accosciati con busto eretto e talloni a terra, poi risali.", img = "goblet_squat_con_manubrio"),
    e("Affondi con manubri", GAMBE, MANUBRI, "Con un manubrio per mano, fai un passo avanti e piega le ginocchia, poi torna in piedi."),
    e("Affondi inversi con manubri", GAMBE, MANUBRI, "Manubri ai fianchi, passo indietro e ginocchio dietro verso terra, poi torna spingendo con la gamba davanti: più delicato per le ginocchia dell'affondo in avanti.", img = "affondi_con_manubri"),
    e("Affondi laterali con manubri", GAMBE, MANUBRI, "Manubrio al petto o ai fianchi: passo laterale ampio, piega la gamba che si sposta tenendo l'altra tesa, poi torna."),
    e("Affondi in camminata", GAMBE, MANUBRI, "Affondi avanzando un passo dopo l'altro con i manubri ai fianchi."),
    e("Bulgarian split squat", GAMBE, MANUBRI, "Piede posteriore su una panca, piega la gamba avanti fino in basso e risali, con manubri."),
    e("Step-up con manubri", GAMBE, MANUBRI, "Sali su un rialzo spingendo con la gamba sopra, alternando, con manubri ai fianchi."),
    e("Stacco rumeno con manubri", GAMBE, MANUBRI, "Manubri davanti alle cosce, ginocchia morbide: scendi spingendo il bacino indietro con schiena dritta, poi risali stringendo i glutei.", img = "stacco_rumeno"),
    e("Leg press", GAMBE, MACCHINA, "Seduto alla pressa, spingi la pedana distendendo le gambe senza bloccare le ginocchia.", tg = "Leg Press"),
    e("Pressa 45°", GAMBE, MACCHINA, "Alla pressa inclinata 45°, spingi la pedana in alto e controlla la discesa."),
    e("Hack squat alla macchina", GAMBE, MACCHINA, "Spalle sotto i cuscinetti, accosciati lungo la guida e risali, per i quadricipiti."),
    e("Leg extension", GAMBE, MACCHINA, "Seduto, distendi le gambe sollevando il rullo per isolare i quadricipiti.", tg = "Leg Extension"),
    e("Leg curl sdraiato", GAMBE, MACCHINA, "Prono alla macchina, fletti le gambe portando i talloni ai glutei per i femorali.", tg = "Prone Leg Curl"),
    e("Leg curl seduto", GAMBE, MACCHINA, "Seduto, spingi il rullo verso il basso flettendo le ginocchia per i femorali.", tg = "Leg Curl"),
    e("Adductor machine", GAMBE, MACCHINA, "Seduto, chiudi le gambe contro la resistenza per gli adduttori (interno coscia), poi riapri lentamente.", tg = "Adductor"),
    e("Multi hip adduzione", GAMBE, MACCHINA, "In piedi alla Multi Hip, rullo all'interno del ginocchio: porta la gamba verso e oltre la linea centrale, poi torna lento.", tg = "Multi Hip"),
    e("Multi hip flessione dell'anca", GAMBE, MACCHINA, "In piedi alla Multi Hip, rullo sopra il ginocchio: solleva la coscia in avanti fino all'altezza dell'anca, busto fermo.", tg = "Multi Hip"),
    e("Squat al multipower", GAMBE, MACCHINA, "Squat guidato al multipower (Smith) per maggiore stabilità del movimento.", tg = "Multipower"),
    e("Affondi al multipower", GAMBE, MACCHINA, "Bilanciere guidato sulle spalle, piedi sfalsati: scendi in verticale col ginocchio dietro verso terra e risali.", tg = "Multipower", img = "affondi_con_bilanciere"),
    e("Leg curl ai cavi", GAMBE, CAVI, "Cavigliera al cavo basso, in piedi di fronte alla colonna: porta il tallone verso il gluteo tenendo la coscia ferma."),
    e("Adduzione ai cavi", GAMBE, CAVI, "Cavigliera al cavo basso dal lato interno: porta la gamba verso e oltre l'altra, poi torna controllando."),
    e("Stacco rumeno ai cavi", GAMBE, CAVI, "Barra al cavo basso davanti a te: piega le anche con schiena dritta e ginocchia morbide, risali stringendo i glutei."),
    e("Squat a corpo libero", GAMBE, CORPO_LIBERO, "Accosciati piegando anche e ginocchia mantenendo il busto eretto, senza pesi."),
    e("Squat jump", GAMBE, CORPO_LIBERO, "Squat seguito da un salto esplosivo verso l'alto; atterra morbido sugli avampiedi e riparti.", img = "squat_a_corpo_libero"),
    e("Affondi a corpo libero", GAMBE, CORPO_LIBERO, "Passo avanti e piega entrambe le ginocchia, poi torna, senza sovraccarico."),
    e("Affondi inversi a corpo libero", GAMBE, CORPO_LIBERO, "Passo indietro e ginocchio dietro verso terra, poi torna spingendo con la gamba davanti.", img = "affondi_a_corpo_libero"),
    e("Affondi laterali a corpo libero", GAMBE, CORPO_LIBERO, "Passo laterale ampio, piega la gamba che si sposta con l'altra tesa e il busto eretto, poi torna."),
    e("Pistol squat", GAMBE, CORPO_LIBERO, "Squat su una gamba sola con l'altra tesa in avanti: richiede forza ed equilibrio."),
    e("Nordic curl", GAMBE, CORPO_LIBERO, "In ginocchio con i talloni bloccati, scendi lentamente in avanti col corpo dritto frenando con i femorali; aiutati con le mani."),
    e("Box jump", GAMBE, CORPO_LIBERO, "Salta a piedi uniti su un box stabile atterrando morbido in squat, poi scendi camminando."),
    e("Wall sit", GAMBE, CORPO_LIBERO, "Schiena al muro e ginocchia a 90°, mantieni la posizione isometrica più a lungo possibile."),

    // ---------- GLUTEI ----------
    e("Hip thrust", GLUTEI, BILANCIERE, "Parte alta della schiena appoggiata alla panca e bilanciere sull'anca: spingi il bacino in alto fino ad allineare busto e cosce, stringi i glutei e scendi controllando."),
    e("Stacco sumo", GLUTEI, BILANCIERE, "Stacco a gambe larghe e punte aperte, per coinvolgere di più glutei e interno coscia."),
    e("Ponte glutei con bilanciere", GLUTEI, BILANCIERE, "Supino a terra con ginocchia piegate e bilanciere sull'anca: solleva il bacino contraendo i glutei, poi scendi lento."),
    e("Hip thrust con manubrio", GLUTEI, MANUBRI, "Spalle sulla panca e manubrio sull'anca, spingi il bacino in alto.", img = "hip_thrust"),
    e("Sumo squat con manubrio", GLUTEI, MANUBRI, "Gambe larghe e punte aperte, manubrio tenuto in verticale tra le gambe: scendi con le ginocchia in fuori e risali.", img = "goblet_squat_con_manubrio"),
    e("Stacco a una gamba con manubri", GLUTEI, MANUBRI, "In equilibrio su una gamba, piega il busto in avanti allungando l'altra gamba indietro, schiena dritta; risali stringendo il gluteo."),
    e("Hip thrust alla macchina", GLUTEI, MACCHINA, "Alla macchina, spingi il bacino contro il cuscinetto contraendo i glutei.", img = "hip_thrust"),
    e("Hip thrust al multipower", GLUTEI, MACCHINA, "Schiena sulla panca e bilanciere guidato sull'anca (con imbottitura): spingi il bacino in alto.", tg = "Multipower", img = "hip_thrust"),
    e("Abductor machine", GLUTEI, MACCHINA, "Seduto, apri le gambe contro la resistenza per glutei e abduttori.", tg = "Abductor"),
    e("Glute machine", GLUTEI, MACCHINA, "Alla macchina dedicata, slancia la gamba all'indietro per isolare il gluteo.", tg = "Glute", img = "slanci_posteriori"),
    e("Multi hip slancio posteriore", GLUTEI, MACCHINA, "In piedi alla Multi Hip, rullo dietro al ginocchio: spingi la gamba indietro estendendo l'anca, senza inarcare la schiena.", tg = "Multi Hip", img = "slanci_posteriori"),
    e("Multi hip abduzione", GLUTEI, MACCHINA, "In piedi alla Multi Hip, rullo all'esterno del ginocchio: porta la gamba di lato verso l'esterno e torna lento.", tg = "Multi Hip"),
    e("Kickback ai cavi", GLUTEI, CAVI, "Cavigliera al cavo basso, slancia la gamba all'indietro contraendo il gluteo."),
    e("Pull-through ai cavi", GLUTEI, CAVI, "Di spalle al cavo basso con la corda tra le gambe: piegati in avanti dalle anche con schiena dritta, poi estendi spingendo il bacino in avanti e stringi i glutei."),
    e("Abduzione ai cavi", GLUTEI, CAVI, "Cavigliera al cavo basso sulla gamba più lontana dalla colonna: porta la gamba lateralmente verso l'esterno e torna lento, busto fermo."),
    e("Ponte glutei a corpo libero", GLUTEI, CORPO_LIBERO, "Supino con ginocchia piegate e piedi a terra: solleva il bacino contraendo i glutei fino ad allineare cosce e busto, poi scendi lento."),
    e("Ponte glutei a una gamba", GLUTEI, CORPO_LIBERO, "Ponte con un piede a terra e l'altra gamba sollevata: spingi col tallone e tieni il bacino dritto.", img = "ponte_glutei_a_corpo_libero"),
    e("Slanci posteriori", GLUTEI, CORPO_LIBERO, "A quattro zampe, slancia una gamba all'indietro e in alto contraendo il gluteo."),
    e("Clamshell", GLUTEI, CORPO_LIBERO, "Sdraiato su un fianco a ginocchia piegate, piedi uniti: apri il ginocchio di sopra come una conchiglia senza ruotare il bacino."),
    e("Fire hydrant", GLUTEI, CORPO_LIBERO, "A quattro zampe, solleva lateralmente una gamba piegata fino all'altezza dell'anca, poi torna."),

    // ---------- POLPACCI ----------
    e("Calf raise", POLPACCI, MACCHINA, "In piedi alla macchina, sollevati sulle punte estendendo le caviglie, poi scendi lentamente."),
    e("Calf raise seduto", POLPACCI, MACCHINA, "Seduto coi pesi sulle ginocchia, sollevati sulle punte per il soleo."),
    e("Rotary calf", POLPACCI, MACCHINA, "Seduto con la schiena appoggiata, avampiedi sulla pedana: spingi estendendo le caviglie e torna allungando bene il polpaccio.", tg = "Rotary Calf", img = "calf_raise_seduto"),
    e("Calf alla pressa", POLPACCI, MACCHINA, "Alla pressa, avampiedi sul bordo basso della pedana e gambe distese ma non bloccate: spingi con le punte estendendo le caviglie e torna in allungamento."),
    e("Calf al multipower", POLPACCI, MACCHINA, "Bilanciere guidato sulle spalle e avampiedi su un rialzo: sollevati sulle punte e scendi in allungamento.", tg = "Multipower", img = "calf_raise"),
    e("Calf raise con bilanciere", POLPACCI, BILANCIERE, "Bilanciere sulle spalle, sollevati sulle punte e scendi lento; meglio con gli avampiedi su un rialzo.", img = "calf_raise"),
    e("Calf raise con manubri", POLPACCI, MANUBRI, "In piedi con manubri ai fianchi, sollevati sulle punte dei piedi."),
    e("Calf raise a una gamba con manubrio", POLPACCI, MANUBRI, "Avampiede su un rialzo, manubrio nella mano dello stesso lato: sollevati su una gamba sola, tieniti con l'altra mano.", img = "calf_raise_con_manubri"),
    e("Calf raise a corpo libero", POLPACCI, CORPO_LIBERO, "Sollevati sulle punte dei piedi, meglio su un rialzo per ampliare l'escursione.", img = "calf_raise"),

    // ---------- CORE ----------
    e("Plank", CORE, CORPO_LIBERO, "In appoggio su avambracci e punte, mantieni il corpo in linea contraendo l'addome."),
    e("Plank laterale", CORE, CORPO_LIBERO, "In appoggio su un avambraccio e sul fianco, mantieni il corpo allineato."),
    e("Plank con tocco spalla", CORE, CORPO_LIBERO, "In plank a braccia tese, tocca alternativamente la spalla opposta senza far ruotare il bacino.", img = "plank"),
    e("Crunch", CORE, CORPO_LIBERO, "Sdraiato, solleva le spalle da terra contraendo gli addominali, senza tirare il collo."),
    e("Crunch obliquo", CORE, CORPO_LIBERO, "Sdraiato con le ginocchia piegate, solleva le spalle ruotando il busto verso il ginocchio opposto.", img = "crunch"),
    e("Bicycle crunch", CORE, CORPO_LIBERO, "Sdraiato, porta gomito e ginocchio opposti uno verso l'altro alternando, come pedalare.", img = "crunch"),
    e("Crunch inverso", CORE, CORPO_LIBERO, "Sdraiato, porta le ginocchia al petto sollevando il bacino, per gli addominali bassi."),
    e("Sit-up", CORE, CORPO_LIBERO, "Sdraiato, solleva tutto il busto fino a sederti e torna giù controllando."),
    e("V-up", CORE, CORPO_LIBERO, "Sdraiato a corpo disteso, solleva insieme busto e gambe tese cercando di toccare le punte dei piedi."),
    e("Russian twist", CORE, CORPO_LIBERO, "Seduto con busto inclinato, ruota il tronco da un lato all'altro, anche con un peso."),
    e("Mountain climber", CORE, CORPO_LIBERO, "In appoggio sulle mani, porta alternativamente le ginocchia al petto rapidamente."),
    e("Sollevamento gambe", CORE, CORPO_LIBERO, "Sdraiato, solleva le gambe tese verso l'alto e scendi senza inarcare la schiena."),
    e("Flutter kick", CORE, CORPO_LIBERO, "Sdraiato, gambe tese sollevate di poco da terra: battile su e giù alternandole, zona lombare a terra.", img = "sollevamento_gambe"),
    e("Sollevamento gambe alla sbarra", CORE, CORPO_LIBERO, "Appeso alla sbarra, solleva le gambe verso l'alto contraendo gli addominali."),
    e("Ginocchia al petto alla sbarra", CORE, CORPO_LIBERO, "Appeso alla sbarra, porta le ginocchia verso il petto arrotolando il bacino, senza dondolare.", img = "sollevamento_gambe_alla_sbarra"),
    e("Ab wheel rollout", CORE, CORPO_LIBERO, "In ginocchio con la ruota, rotola in avanti e torna mantenendo l'addome contratto."),
    e("Dead bug", CORE, CORPO_LIBERO, "Sdraiato con braccia in alto e ginocchia a 90°: distendi braccio e gamba opposti verso terra tenendo la schiena appoggiata."),
    e("Bird dog", CORE, CORPO_LIBERO, "A quattro zampe, distendi braccio e gamba opposti in linea col busto, tieni 2 secondi e cambia lato."),
    e("Hollow hold", CORE, CORPO_LIBERO, "Sdraiato, solleva spalle e gambe tese tenendo la zona lombare schiacciata a terra; mantieni la posizione."),
    e("Crunch alla macchina", CORE, MACCHINA, "Seduto alla macchina, fletti il busto in avanti contro la resistenza.", tg = "Abdominal Crunch"),
    e("Total abdominal", CORE, MACCHINA, "Seduto con le braccia sui cuscinetti: fletti busto e bacino insieme avvicinando sterno e ginocchia, poi torna lento.", tg = "Total Abdominal", img = "crunch_alla_macchina"),
    e("Rotazione del busto alla macchina", CORE, MACCHINA, "Seduto, ruota il tronco contro la resistenza per gli obliqui.", tg = "Rotary Torso"),
    e("Crunch ai cavi", CORE, CAVI, "In ginocchio sotto il cavo alto, corda ai lati della testa: fletti il busto verso il basso arrotondando la schiena, con le anche ferme."),
    e("Pallof press ai cavi", CORE, CAVI, "Di lato al cavo, spingi l'impugnatura in avanti resistendo alla rotazione (anti-rotazione)."),
    e("Woodchop ai cavi", CORE, CAVI, "Tira il cavo in diagonale dall'alto al basso (o viceversa) ruotando il tronco."),
    e("Woodchop dal basso ai cavi", CORE, CAVI, "Dal cavo basso, porta la maniglia in diagonale verso l'alto oltre la spalla opposta ruotando busto e anche.", img = "woodchop_ai_cavi"),
    e("Flessioni laterali ai cavi", CORE, CAVI, "Di fianco al cavo basso, maniglia in mano: piegati lateralmente verso il cavo e risali con gli obliqui opposti."),
    e("Rollout con bilanciere", CORE, BILANCIERE, "In ginocchio, mani sul bilanciere con dischi: rotola in avanti mantenendo l'addome contratto e torna.", img = "ab_wheel_rollout"),
    e("Rotazioni landmine", CORE, BILANCIERE, "Bilanciere ancorato a terra tenuto con le braccia tese davanti: portalo ad arco da un fianco all'altro ruotando busto e anche."),
    e("Flessioni laterali con manubrio", CORE, MANUBRI, "In piedi con un manubrio in una mano: piegati lateralmente da quel lato e risali usando gli obliqui opposti."),
    e("Farmer walk", CORE, MANUBRI, "Cammina con un manubrio pesante per mano, busto eretto e spalle basse: core, presa e trapezi."),

    // ---------- STRETCHING (statico, a fine allenamento) ----------
    e("Stretching flessori dell'anca", STRETCHING, CORPO_LIBERO, "In affondo con il ginocchio dietro appoggiato a terra, porta il bacino in avanti stringendo il gluteo della gamba dietro. Allunga la parte davanti dell'anca, che si accorcia stando seduti."),
    e("Stretching pettorali al muro", STRETCHING, CORPO_LIBERO, "Avambraccio appoggiato al muro con il gomito all'altezza della spalla, ruota lentamente il busto dalla parte opposta finché senti allungare il petto."),
    e("Stretching femorali", STRETCHING, CORPO_LIBERO, "Tallone su un rialzo basso e gamba tesa, schiena dritta: piegati in avanti dalle anche finché senti tirare dietro la coscia."),
    e("Stretching glutei a figura 4", STRETCHING, CORPO_LIBERO, "Sdraiato sulla schiena, appoggia la caviglia sul ginocchio opposto e porta le gambe verso il petto tirando la coscia di sotto."),
    e("Stretching quadricipiti in piedi", STRETCHING, CORPO_LIBERO, "In piedi, afferra la caviglia e porta il tallone verso il gluteo con le ginocchia vicine e il bacino leggermente in avanti. Appoggiati se serve."),
    e("Stretching dorsali alla panca", STRETCHING, CORPO_LIBERO, "In ginocchio, mani o avambracci su una panca: lascia scendere il petto verso terra spingendo il bacino indietro, braccia distese."),
    e("Stretching tricipiti e spalle", STRETCHING, CORPO_LIBERO, "Porta un braccio piegato dietro la testa e con l'altra mano spingi delicatamente il gomito verso il basso."),
    e("Stretching polpacci al muro", STRETCHING, CORPO_LIBERO, "Mani al muro, una gamba dietro tesa col tallone a terra: porta il peso in avanti finché senti allungare il polpaccio."),
    e("Stretching adduttori a farfalla", STRETCHING, CORPO_LIBERO, "Seduto con le piante dei piedi unite e le ginocchia aperte: schiena dritta, lascia scendere le ginocchia verso terra."),
    e("Stretching laterale del busto", STRETCHING, CORPO_LIBERO, "In piedi, un braccio sopra la testa: piegati lateralmente dalla parte opposta finché senti allungare il fianco."),
    e("Posizione del bambino", STRETCHING, CORPO_LIBERO, "In ginocchio, siediti sui talloni e allunga le braccia avanti a terra, fronte in basso. Respira lentamente: rilassa schiena e spalle."),
    e("Gatto-mucca", STRETCHING, CORPO_LIBERO, "A quattro zampe, alterna lentamente schiena tonda (espirando) e schiena inarcata (inspirando). Mobilità per la colonna."),
    e("Rotazione toracica a terra", STRETCHING, CORPO_LIBERO, "Sdraiato su un fianco con le ginocchia piegate, apri il braccio di sopra a libro verso il lato opposto seguendolo con lo sguardo. Mobilità della parte alta della schiena."),
    e("Cobra", STRETCHING, CORPO_LIBERO, "A pancia in giù, mani sotto le spalle: solleva il petto distendendo le braccia quanto basta, bacino a terra. Allunga addome e lombari."),

    // ---------- CARDIO ----------
    e("Tapis roulant", CARDIO, MACCHINA, "Cammina o corri sul tappeto regolando velocità e pendenza.", tg = "Run / Skillrun"),
    e("Skillmill", CARDIO, MACCHINA, "Tappeto curvo senza motore: lo muovi tu spingendo. Camminata, corsa, sprint e spinta a slitta con resistenza regolabile.", tg = "Skillmill", img = "tapis_roulant"),
    e("Cyclette", CARDIO, MACCHINA, "Pedala da seduto regolando la resistenza, cardio a basso impatto.", tg = "Bike / Skillbike"),
    e("Cyclette orizzontale", CARDIO, MACCHINA, "Pedala in posizione reclinata, comoda per la schiena.", tg = "Recline"),
    e("Spinning", CARDIO, MACCHINA, "Bici da indoor cycling: pedalata in piedi e seduto con resistenza regolabile, a intervalli.", tg = "Group Cycle", img = "cyclette"),
    e("Ellittica", CARDIO, MACCHINA, "Movimento fluido di gambe e braccia senza impatto sulle articolazioni.", tg = "Synchro / Cross"),
    e("Ellittica a passo variabile", CARDIO, MACCHINA, "La lunghezza del passo segue il tuo movimento: da camminata corta a falcata lunga, senza impatto.", tg = "Vario", img = "ellittica"),
    e("Vogatore", CARDIO, MACCHINA, "Spingi con le gambe e tira l'impugnatura verso l'addome simulando la voga: total body.", tg = "Skillrow"),
    e("Stairmaster", CARDIO, MACCHINA, "Sali gradini in continuazione su scala mobile, intenso per gambe e fiato.", tg = "Climb"),
    e("Stepper", CARDIO, MACCHINA, "Pedane alternate su cui spingere come salendo le scale: busto eretto, non appoggiarti troppo alle maniglie.", img = "stairmaster"),
    e("Ergometro per braccia", CARDIO, MACCHINA, "Pedala con le braccia in avanti e indietro: cardio per la parte alta, utile anche con le gambe a riposo.", tg = "Top"),
    e("Air bike", CARDIO, MACCHINA, "Pedala spingendo e tirando le maniglie: cardio total body ad alta intensità."),
    e("Skierg", CARDIO, MACCHINA, "In piedi, tira le maniglie verso il basso simulando lo sci di fondo."),
    e("Corsa", CARDIO, CORPO_LIBERO, "Corsa all'aperto o sul posto per allenare la resistenza cardiovascolare."),
    e("Skip a ginocchia alte", CARDIO, CORPO_LIBERO, "Corsa sul posto portando le ginocchia all'altezza delle anche, braccia che accompagnano, ritmo veloce."),
    e("Salto con la corda", CARDIO, CORPO_LIBERO, "Salta la corda a piedi uniti mantenendo un ritmo costante."),
    e("Burpees", CARDIO, CORPO_LIBERO, "Da in piedi scendi a terra, fai un piegamento, risali e salta: total body intenso."),
    e("Jumping jacks", CARDIO, CORPO_LIBERO, "Salta aprendo e chiudendo gambe e braccia simultaneamente, ottimo per scaldarsi."),
)
