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

private fun e(nome: String, gruppo: GruppoMuscolare, attrezzo: Attrezzatura, descrizione: String) =
    Esercizio(
        nome = nome,
        gruppoMuscolare = gruppo,
        attrezzatura = attrezzo,
        descrizione = descrizione,
        predefinito = true
    )

fun eserciziPredefiniti(): List<Esercizio> = listOf(
    // ---------- PETTO ----------
    e("Panca piana", PETTO, BILANCIERE, "Sdraiato sulla panca, spingi il bilanciere dal petto verso l'alto fino a braccia distese, poi controlla la discesa."),
    e("Panca inclinata con bilanciere", PETTO, BILANCIERE, "Su panca inclinata 30-45°, spingi il bilanciere verso l'alto allenando la parte alta del petto."),
    e("Panca declinata con bilanciere", PETTO, BILANCIERE, "Su panca declinata, spingi il bilanciere in alto enfatizzando la parte bassa del petto."),
    e("Panca inclinata manubri", PETTO, MANUBRI, "Su panca inclinata, spingi i manubri verso l'alto fino a unirli, poi scendi in controllo."),
    e("Panca piana con manubri", PETTO, MANUBRI, "Sdraiato, spingi i due manubri dal petto verso l'alto controllando l'apertura in basso."),
    e("Panca declinata con manubri", PETTO, MANUBRI, "Su panca declinata, distendi i manubri verso l'alto per la parte bassa del petto."),
    e("Croci su panca piana", PETTO, MANUBRI, "Braccia semi-tese, apri ad arco i manubri e richiudili sopra il petto isolando i pettorali."),
    e("Croci su panca inclinata", PETTO, MANUBRI, "Su panca inclinata, apri e chiudi le braccia ad arco con i manubri per il petto alto."),
    e("Pullover con manubrio", PETTO, MANUBRI, "Sdraiato di traverso, porta un manubrio dietro la testa a braccia semi-tese e riportalo sul petto."),
    e("Chest press", PETTO, MACCHINA, "Seduto alla macchina, spingi le maniglie in avanti fino a distendere le braccia, poi torna lentamente."),
    e("Chest press inclinata", PETTO, MACCHINA, "Alla macchina inclinata, spingi le maniglie verso l'alto per la parte alta del petto."),
    e("Pectoral machine", PETTO, MACCHINA, "Seduto, chiudi le braccia davanti al petto contro i cuscinetti e riapri lentamente."),
    e("Croci ai cavi", PETTO, CAVI, "Tra i cavi alti, porta le maniglie davanti al petto ad arco e controlla il ritorno."),
    e("Croci ai cavi bassi", PETTO, CAVI, "Dai cavi bassi, porta le maniglie verso l'alto e al centro per il petto alto."),
    e("Chest press ai cavi", PETTO, CAVI, "In piedi, spingi le maniglie dei cavi in avanti distendendo le braccia all'altezza del petto."),
    e("Piegamenti sulle braccia", PETTO, CORPO_LIBERO, "In appoggio su mani e piedi, scendi col petto e spingi fino a braccia distese, corpo in linea."),
    e("Piegamenti su rialzo", PETTO, CORPO_LIBERO, "Piegamenti con le mani su un rialzo per ridurre il carico, ideali per iniziare."),
    e("Dip alle parallele", PETTO, CORPO_LIBERO, "Alle parallele, scendi piegando i gomiti col busto inclinato avanti e risali distendendo le braccia."),

    // ---------- SCHIENA ----------
    e("Stacco da terra", SCHIENA, BILANCIERE, "Con schiena dritta, solleva il bilanciere da terra estendendo gambe e anche fino in piedi, poi controlla la discesa."),
    e("Rematore con bilanciere", SCHIENA, BILANCIERE, "Busto inclinato in avanti, tira il bilanciere verso l'addome stringendo le scapole."),
    e("Rematore presa inversa", SCHIENA, BILANCIERE, "Come il rematore ma con presa supina, per coinvolgere di più dorso basso e bicipiti."),
    e("Rematore con manubrio", SCHIENA, MANUBRI, "Con un ginocchio sulla panca, tira il manubrio verso il fianco portando il gomito indietro."),
    e("Lat machine", SCHIENA, MACCHINA, "Seduto, tira la barra alta verso il petto stringendo le scapole, poi risali in controllo."),
    e("Lat machine presa inversa", SCHIENA, MACCHINA, "Tira la barra al petto con presa supina e stretta, enfatizzando la parte bassa del dorso."),
    e("Lat machine presa stretta", SCHIENA, MACCHINA, "Con maniglia a presa stretta, tira verso il petto per lavorare lo spessore del dorso."),
    e("Pulley basso", SCHIENA, MACCHINA, "Seduto, tira l'impugnatura verso l'addome tenendo il busto fermo e stringendo le scapole."),
    e("Rematore alla macchina", SCHIENA, MACCHINA, "Petto contro il supporto, tira le maniglie verso di te portando i gomiti indietro."),
    e("Trazioni assistite alla macchina", SCHIENA, MACCHINA, "Trazioni con assistenza al peso, per allenare il dorso fino a riuscire a farle libere."),
    e("Iperestensioni", SCHIENA, MACCHINA, "Sulla panca apposita, scendi col busto e risali estendendo la schiena, per lombari e glutei."),
    e("Rematore ai cavi", SCHIENA, CAVI, "Seduto al cavo basso, tira l'impugnatura verso l'addome stringendo le scapole."),
    e("Pulldown a un braccio ai cavi", SCHIENA, CAVI, "Dal cavo alto, tira con un braccio alla volta per un lavoro mirato del dorso."),
    e("Pullover ai cavi", SCHIENA, CAVI, "Davanti al cavo alto, a braccia tese porta la barra verso le cosce attivando il gran dorsale."),
    e("Trazioni alla sbarra", SCHIENA, CORPO_LIBERO, "Appeso alla sbarra, tirati su fino a portare il mento sopra di essa, poi scendi controllando."),
    e("Trazioni presa inversa", SCHIENA, CORPO_LIBERO, "Trazioni con palmi rivolti verso di te: più bicipiti e dorso basso."),
    e("Trazioni presa larga", SCHIENA, CORPO_LIBERO, "Trazioni con presa ampia per enfatizzare l'ampiezza del dorso."),
    e("Australian pull-up", SCHIENA, CORPO_LIBERO, "Sotto una barra bassa e corpo teso, tirati verso la barra mantenendo il corpo in linea."),

    // ---------- SPALLE ----------
    e("Lento avanti", SPALLE, BILANCIERE, "In piedi, spingi il bilanciere dalle spalle sopra la testa fino a braccia distese."),
    e("Tirate al mento con bilanciere", SPALLE, BILANCIERE, "Tira il bilanciere lungo il corpo fino al mento con i gomiti alti, per spalle e trapezi."),
    e("Scrollate con bilanciere", SPALLE, BILANCIERE, "Solleva le spalle verso le orecchie tenendo il bilanciere, per i trapezi."),
    e("Lento avanti con manubri", SPALLE, MANUBRI, "Seduto o in piedi, spingi i manubri sopra la testa e controlla la discesa."),
    e("Arnold press", SPALLE, MANUBRI, "Parti con i manubri davanti e ruota i polsi mentre spingi sopra la testa, per tutto il deltoide."),
    e("Alzate laterali", SPALLE, MANUBRI, "Solleva i manubri lateralmente fino all'altezza delle spalle, gomiti leggermente piegati."),
    e("Alzate frontali", SPALLE, MANUBRI, "Solleva i manubri davanti a te fino all'altezza delle spalle, per il deltoide anteriore."),
    e("Alzate posteriori", SPALLE, MANUBRI, "Busto inclinato in avanti, apri le braccia all'indietro per il deltoide posteriore."),
    e("Scrollate con manubri", SPALLE, MANUBRI, "Solleva le spalle verso l'alto con i manubri ai fianchi, per i trapezi."),
    e("Shoulder press alla macchina", SPALLE, MACCHINA, "Seduto, spingi le maniglie sopra la testa fino a distendere le braccia."),
    e("Alzate laterali alla macchina", SPALLE, MACCHINA, "Apri le braccia lateralmente contro i cuscinetti per i deltoidi laterali."),
    e("Reverse fly alla macchina", SPALLE, MACCHINA, "Rivolto alla macchina, apri le braccia all'indietro per i deltoidi posteriori."),
    e("Alzate laterali ai cavi", SPALLE, CAVI, "Dal cavo basso, solleva il braccio lateralmente fino all'altezza della spalla."),
    e("Alzate posteriori ai cavi", SPALLE, CAVI, "Ai cavi incrociati, apri le braccia all'indietro per i deltoidi posteriori."),
    e("Tirate al mento ai cavi", SPALLE, CAVI, "Tira la barra del cavo basso verso il mento con i gomiti alti."),
    e("Face pull ai cavi", SPALLE, CAVI, "Dal cavo all'altezza del viso, tira la corda verso la fronte aprendo i gomiti."),
    e("Pike push-up", SPALLE, CORPO_LIBERO, "In posizione a V rovesciata, piega le braccia portando la testa a terra e spingi."),

    // ---------- BICIPITI ----------
    e("Curl con bilanciere", BICIPITI, BILANCIERE, "In piedi, fletti i gomiti portando il bilanciere verso le spalle, poi scendi lentamente."),
    e("Curl con bilanciere EZ", BICIPITI, BILANCIERE, "Curl con bilanciere sagomato (EZ) per ridurre lo stress sui polsi."),
    e("Curl presa inversa", BICIPITI, BILANCIERE, "Curl con presa prona per coinvolgere avambracci e brachiale."),
    e("Curl con manubri", BICIPITI, MANUBRI, "Fletti i gomiti portando i manubri verso le spalle, controllando la discesa."),
    e("Curl alternato", BICIPITI, MANUBRI, "Curl con manubri sollevando un braccio alla volta."),
    e("Curl a martello", BICIPITI, MANUBRI, "Curl con manubri a presa neutra (palmi affrontati), per brachiale e avambraccio."),
    e("Curl concentrato", BICIPITI, MANUBRI, "Seduto, gomito appoggiato all'interno coscia, fletti il manubrio isolando il bicipite."),
    e("Curl su panca inclinata", BICIPITI, MANUBRI, "Seduto su panca inclinata, braccia in basso, fletti i manubri allungando il bicipite."),
    e("Curl alla panca Scott", BICIPITI, MACCHINA, "Braccia appoggiate sul leggio Scott, fletti il carico isolando i bicipiti."),
    e("Curl alla macchina", BICIPITI, MACCHINA, "Seduto alla macchina, fletti le braccia contro la resistenza guidata."),
    e("Curl ai cavi", BICIPITI, CAVI, "Dal cavo basso, fletti i gomiti portando la barra verso le spalle con tensione costante."),
    e("Curl a martello ai cavi", BICIPITI, CAVI, "Con corda al cavo basso, curl a presa neutra per brachiale e avambraccio."),

    // ---------- TRICIPITI ----------
    e("Distensioni presa stretta", TRICIPITI, BILANCIERE, "Su panca, presa stretta sul bilanciere, spingi enfatizzando i tricipiti."),
    e("French press", TRICIPITI, BILANCIERE, "Sdraiato, porta il bilanciere dietro la fronte piegando i gomiti e ridistendi per i tricipiti."),
    e("Estensioni sopra la testa con manubrio", TRICIPITI, MANUBRI, "Porta un manubrio dietro la nuca e distendi le braccia in alto."),
    e("Kickback con manubri", TRICIPITI, MANUBRI, "Busto inclinato e gomito fermo, distendi l'avambraccio all'indietro per il tricipite."),
    e("Pushdown ai cavi", TRICIPITI, CAVI, "Dal cavo alto con corda, spingi le mani verso il basso distendendo i gomiti."),
    e("Pushdown ai cavi con barra", TRICIPITI, CAVI, "Pushdown con barra dritta, gomiti ai fianchi, distendi verso il basso."),
    e("Pushdown presa inversa", TRICIPITI, CAVI, "Pushdown con presa supina per enfatizzare il capo mediale del tricipite."),
    e("Estensioni sopra la testa ai cavi", TRICIPITI, CAVI, "Rivolto lontano dal cavo, distendi le braccia sopra la testa per i tricipiti."),
    e("Tricipiti alla macchina", TRICIPITI, MACCHINA, "Seduto alla macchina, distendi le braccia contro la resistenza guidata."),
    e("Dip alla macchina", TRICIPITI, MACCHINA, "Alla macchina dip, spingi verso il basso distendendo le braccia."),
    e("Dip tra le panche", TRICIPITI, CORPO_LIBERO, "Mani su una panca dietro di te, scendi piegando i gomiti e risali per i tricipiti."),
    e("Piegamenti diamante", TRICIPITI, CORPO_LIBERO, "Piegamenti con le mani vicine a formare un rombo, per enfatizzare i tricipiti."),

    // ---------- GAMBE ----------
    e("Squat", GAMBE, BILANCIERE, "Bilanciere sulle spalle, scendi piegando anche e ginocchia fino a coscia parallela, poi risali."),
    e("Squat frontale", GAMBE, BILANCIERE, "Bilanciere sulle spalle anteriori, accosciati mantenendo il busto eretto."),
    e("Affondi con bilanciere", GAMBE, BILANCIERE, "Bilanciere sulle spalle, fai un passo avanti e piega entrambe le ginocchia, poi torna."),
    e("Stacco gambe tese", GAMBE, BILANCIERE, "Gambe quasi tese, scendi col bilanciere lungo le gambe sentendo i femorali, poi risali."),
    e("Stacco rumeno", GAMBE, BILANCIERE, "Schiena dritta e gambe semi-flesse, scendi col bilanciere lungo le cosce e risali contraendo i glutei."),
    e("Good morning", GAMBE, BILANCIERE, "Bilanciere sulle spalle, fletti il busto avanti a gambe semi-tese e risali, per femorali e lombari."),
    e("Goblet squat con manubrio", GAMBE, MANUBRI, "Tieni un manubrio al petto e accosciati mantenendo il busto eretto."),
    e("Affondi con manubri", GAMBE, MANUBRI, "Con un manubrio per mano, fai un passo avanti e piega le ginocchia, poi torna in piedi."),
    e("Affondi in camminata", GAMBE, MANUBRI, "Affondi avanzando un passo dopo l'altro con i manubri ai fianchi."),
    e("Bulgarian split squat", GAMBE, MANUBRI, "Piede posteriore su una panca, piega la gamba avanti fino in basso e risali, con manubri."),
    e("Step-up con manubri", GAMBE, MANUBRI, "Sali su un rialzo spingendo con la gamba sopra, alternando, con manubri ai fianchi."),
    e("Leg press", GAMBE, MACCHINA, "Seduto alla pressa, spingi la pedana distendendo le gambe senza bloccare le ginocchia."),
    e("Pressa 45°", GAMBE, MACCHINA, "Alla pressa inclinata 45°, spingi la pedana in alto e controlla la discesa."),
    e("Hack squat alla macchina", GAMBE, MACCHINA, "Spalle sotto i cuscinetti, accosciati lungo la guida e risali, per i quadricipiti."),
    e("Leg extension", GAMBE, MACCHINA, "Seduto, distendi le gambe sollevando il rullo per isolare i quadricipiti."),
    e("Leg curl sdraiato", GAMBE, MACCHINA, "Prono alla macchina, fletti le gambe portando i talloni ai glutei per i femorali."),
    e("Leg curl seduto", GAMBE, MACCHINA, "Seduto, spingi il rullo verso il basso flettendo le ginocchia per i femorali."),
    e("Adductor machine", GAMBE, MACCHINA, "Seduto, chiudi le gambe contro la resistenza per gli adduttori interno coscia."),
    e("Squat al multipower", GAMBE, MACCHINA, "Squat guidato al multipower (Smith) per maggiore stabilità del movimento."),
    e("Squat a corpo libero", GAMBE, CORPO_LIBERO, "Accosciati piegando anche e ginocchia mantenendo il busto eretto, senza pesi."),
    e("Affondi a corpo libero", GAMBE, CORPO_LIBERO, "Passo avanti e piega entrambe le ginocchia, poi torna, senza sovraccarico."),
    e("Pistol squat", GAMBE, CORPO_LIBERO, "Squat su una gamba sola con l'altra tesa in avanti: richiede forza ed equilibrio."),
    e("Wall sit", GAMBE, CORPO_LIBERO, "Schiena al muro e ginocchia a 90°, mantieni la posizione isometrica più a lungo possibile."),

    // ---------- GLUTEI ----------
    e("Hip thrust", GLUTEI, BILANCIERE, "Schiena su una panca e bilanciere sull'anca, spingi il bacino in alto contraendo i glutei."),
    e("Stacco sumo", GLUTEI, BILANCIERE, "Stacco a gambe larghe e punte aperte, per coinvolgere di più glutei e interno coscia."),
    e("Ponte glutei con bilanciere", GLUTEI, BILANCIERE, "A terra, bilanciere sull'anca, solleva il bacino contraendo i glutei."),
    e("Hip thrust con manubrio", GLUTEI, MANUBRI, "Spalle sulla panca e manubrio sull'anca, spingi il bacino in alto."),
    e("Hip thrust alla macchina", GLUTEI, MACCHINA, "Alla macchina, spingi il bacino contro il cuscinetto contraendo i glutei."),
    e("Abductor machine", GLUTEI, MACCHINA, "Seduto, apri le gambe contro la resistenza per glutei e abduttori."),
    e("Glute machine", GLUTEI, MACCHINA, "Alla macchina dedicata, slancia la gamba all'indietro per isolare il gluteo."),
    e("Kickback ai cavi", GLUTEI, CAVI, "Cavigliera al cavo basso, slancia la gamba all'indietro contraendo il gluteo."),
    e("Pull-through ai cavi", GLUTEI, CAVI, "Tra le gambe, tira la fune del cavo basso estendendo l'anca in avanti, per i glutei."),
    e("Abduzione ai cavi", GLUTEI, CAVI, "Cavigliera al cavo basso, porta la gamba lateralmente verso l'esterno."),
    e("Ponte glutei a corpo libero", GLUTEI, CORPO_LIBERO, "A terra, solleva il bacino contraendo i glutei, senza pesi."),
    e("Slanci posteriori", GLUTEI, CORPO_LIBERO, "A quattro zampe, slancia una gamba all'indietro e in alto contraendo il gluteo."),

    // ---------- POLPACCI ----------
    e("Calf raise", POLPACCI, MACCHINA, "In piedi alla macchina, sollevati sulle punte estendendo le caviglie, poi scendi lentamente."),
    e("Calf raise seduto", POLPACCI, MACCHINA, "Seduto coi pesi sulle ginocchia, sollevati sulle punte per il soleo."),
    e("Calf alla pressa", POLPACCI, MACCHINA, "Alla pressa, spingi con le punte dei piedi estendendo le caviglie."),
    e("Calf raise con manubri", POLPACCI, MANUBRI, "In piedi con manubri ai fianchi, sollevati sulle punte dei piedi."),
    e("Calf raise a corpo libero", POLPACCI, CORPO_LIBERO, "Sollevati sulle punte dei piedi, meglio su un rialzo per ampliare l'escursione."),

    // ---------- CORE ----------
    e("Plank", CORE, CORPO_LIBERO, "In appoggio su avambracci e punte, mantieni il corpo in linea contraendo l'addome."),
    e("Plank laterale", CORE, CORPO_LIBERO, "In appoggio su un avambraccio e sul fianco, mantieni il corpo allineato."),
    e("Crunch", CORE, CORPO_LIBERO, "Sdraiato, solleva le spalle da terra contraendo gli addominali, senza tirare il collo."),
    e("Crunch inverso", CORE, CORPO_LIBERO, "Sdraiato, porta le ginocchia al petto sollevando il bacino, per gli addominali bassi."),
    e("Sit-up", CORE, CORPO_LIBERO, "Sdraiato, solleva tutto il busto fino a sederti e torna giù controllando."),
    e("Russian twist", CORE, CORPO_LIBERO, "Seduto con busto inclinato, ruota il tronco da un lato all'altro, anche con un peso."),
    e("Mountain climber", CORE, CORPO_LIBERO, "In appoggio sulle mani, porta alternativamente le ginocchia al petto rapidamente."),
    e("Sollevamento gambe", CORE, CORPO_LIBERO, "Sdraiato, solleva le gambe tese verso l'alto e scendi senza inarcare la schiena."),
    e("Sollevamento gambe alla sbarra", CORE, CORPO_LIBERO, "Appeso alla sbarra, solleva le gambe verso l'alto contraendo gli addominali."),
    e("Ab wheel rollout", CORE, CORPO_LIBERO, "In ginocchio con la ruota, rotola in avanti e torna mantenendo l'addome contratto."),
    e("Crunch alla macchina", CORE, MACCHINA, "Seduto alla macchina, fletti il busto in avanti contro la resistenza."),
    e("Rotazione del busto alla macchina", CORE, MACCHINA, "Seduto, ruota il tronco contro la resistenza per gli obliqui."),
    e("Crunch ai cavi", CORE, CAVI, "In ginocchio sotto il cavo alto, fletti il busto verso il basso contraendo l'addome."),
    e("Pallof press ai cavi", CORE, CAVI, "Di lato al cavo, spingi l'impugnatura in avanti resistendo alla rotazione (anti-rotazione)."),
    e("Woodchop ai cavi", CORE, CAVI, "Tira il cavo in diagonale dall'alto al basso (o viceversa) ruotando il tronco."),

    // ---------- CARDIO ----------
    e("Tapis roulant", CARDIO, MACCHINA, "Cammina o corri sul tappeto regolando velocità e pendenza."),
    e("Cyclette", CARDIO, MACCHINA, "Pedala da seduto regolando la resistenza, cardio a basso impatto."),
    e("Cyclette orizzontale", CARDIO, MACCHINA, "Pedala in posizione reclinata, comoda per la schiena."),
    e("Ellittica", CARDIO, MACCHINA, "Movimento fluido di gambe e braccia senza impatto sulle articolazioni."),
    e("Vogatore", CARDIO, MACCHINA, "Spingi con le gambe e tira l'impugnatura verso l'addome simulando la voga: total body."),
    e("Stairmaster", CARDIO, MACCHINA, "Sali gradini in continuazione su scala mobile, intenso per gambe e fiato."),
    e("Air bike", CARDIO, MACCHINA, "Pedala spingendo e tirando le maniglie: cardio total body ad alta intensità."),
    e("Skierg", CARDIO, MACCHINA, "In piedi, tira le maniglie verso il basso simulando lo sci di fondo."),
    e("Corsa", CARDIO, CORPO_LIBERO, "Corsa all'aperto o sul posto per allenare la resistenza cardiovascolare."),
    e("Salto con la corda", CARDIO, CORPO_LIBERO, "Salta la corda a piedi uniti mantenendo un ritmo costante."),
    e("Burpees", CARDIO, CORPO_LIBERO, "Da in piedi scendi a terra, fai un piegamento, risali e salta: total body intenso."),
    e("Jumping jacks", CARDIO, CORPO_LIBERO, "Salta aprendo e chiudendo gambe e braccia simultaneamente, ottimo per scaldarsi."),
)
