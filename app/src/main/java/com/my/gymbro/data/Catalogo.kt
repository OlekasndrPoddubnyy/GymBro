package com.my.gymbro.data

// Come la libreria dell'utente si allinea al catalogo degli esercizi predefiniti. Gli esercizi del
// catalogo si riconoscono dalla chiave ([Esercizio.chiaveSeed]), non dal nome: l'utente li può
// rinominare senza che un aggiornamento li reinserisca. Niente Android né database: si prova sulla JVM.

/** Nome confrontabile: due esercizi che differiscono solo per maiuscole o spazi sono lo stesso nome. */
fun normalizzaNome(nome: String): String = nome.trim().lowercase()

/** true se un altro esercizio (id diverso da [escludiId]) ha già [nome]. */
fun nomeGiaUsato(nome: String, libreria: Collection<Esercizio>, escludiId: Long = 0): Boolean {
    val n = normalizzaNome(nome)
    return libreria.any { it.id != escludiId && normalizzaNome(it.nome) == n }
}

/**
 * Esercizi per nome di catalogo: quelli predefiniti con la loro chiave (anche se rinominati), gli
 * altri con il nome. A parità di nome vince il predefinito.
 */
fun Collection<Esercizio>.perNomeCatalogo(): Map<String, Esercizio> {
    val m = HashMap<String, Esercizio>()
    for (e in this) if (e.chiaveSeed == null) m.putIfAbsent(e.nome, e)
    for (e in this) e.chiaveSeed?.let { m[it] = e }
    return m
}

/**
 * Chiavi da assegnare agli esercizi predefiniti già salvati, quando la colonna nasce (migrazione v3):
 * quelli col nome ancora nel catalogo prendono il nome come chiave (se ce ne fossero due uguali, il
 * più vecchio). Gli altri restano senza: sono stati rinominati, e [allineaCatalogo] prova a riconoscerli.
 *
 * @param predefiniti coppie (id, nome).
 * @return id → chiave.
 */
fun chiaviIniziali(predefiniti: List<Pair<Long, String>>, catalogo: Set<String>): Map<Long, String> {
    val usate = HashSet<String>()
    return predefiniti.sortedBy { it.first }
        .filter { (_, nome) -> nome in catalogo && usate.add(nome) }
        .toMap()
}

/** Cosa cambiare nella libreria: righe nuove da inserire e righe esistenti da aggiornare. */
class AllineamentoCatalogo(val daInserire: List<Esercizio>, val daAggiornare: List<Esercizio>)

/**
 * Confronta la [libreria] con il [catalogo]:
 *  - un esercizio del catalogo che manca si inserisce, salvo che l'utente l'abbia eliminato
 *    ([eliminati], per chiave) o che abbia già un esercizio con lo stesso nome;
 *  - uno presente tiene il nome scelto dall'utente; si completano descrizione e immagine
 *    (da assets, [assetSlugs]) e si aggiorna una descrizione mai modificata dall'utente;
 *  - un predefinito senza chiave (rinominato prima che le chiavi esistessero) che corrisponde a un
 *    solo esercizio mancante del catalogo, per gruppo, attrezzo e descrizione o immagine, lo
 *    adotta. Se ne corrispondono di più non si inserisce niente: meglio che un doppione.
 */
fun allineaCatalogo(
    libreria: List<Esercizio>,
    catalogo: List<Esercizio>,
    assetSlugs: Set<String>,
    eliminati: Set<String>,
): AllineamentoCatalogo {
    val perChiave = HashMap<String, Esercizio>()
    libreria.forEach { e -> e.chiaveSeed?.let { perChiave[it] = e } }
    val nomiOccupati = libreria.mapTo(HashSet()) { normalizzaNome(it.nome) }
    val orfani = libreria.filter { it.predefinito && it.chiaveSeed == null }.toMutableList()
    val nuovi = mutableListOf<Esercizio>()
    val aggiornati = mutableListOf<Esercizio>()

    for (seed in catalogo) {
        val chiave = seed.chiaveSeed ?: seed.nome
        // Le cartelle proprie hanno la precedenza; altrimenti si riusa l'immagine indicata dal seed.
        val slug = slugEsercizio(chiave).takeIf { it in assetSlugs } ?: seed.immaginePath
        val pathAsset = if (slug != null && slug in assetSlugs) "asset://esercizi/$slug/0.jpg" else null

        var attuale = perChiave[chiave]
        var adottato = false
        if (attuale == null) {
            val candidati = orfani.filter { stessoEsercizio(it, seed, chiave, pathAsset) }
            when {
                candidati.size == 1 -> {
                    orfani -= candidati[0]
                    attuale = candidati[0].copy(chiaveSeed = chiave)
                    adottato = true
                }
                candidati.size > 1 -> continue
            }
        }

        if (attuale == null) {
            if (chiave in eliminati || normalizzaNome(seed.nome) in nomiOccupati) continue
            nuovi += seed.copy(immaginePath = pathAsset, chiaveSeed = chiave)
            nomiOccupati += normalizzaNome(seed.nome)
            continue
        }

        val nuova = seed.descrizione
        val descr = when {
            attuale.descrizione.isNullOrBlank() -> nuova
            nuova != null && nuova != attuale.descrizione && nuova.contains(attuale.descrizione) -> nuova
            // Testo di una versione precedente del seed, mai modificato dall'utente: si aggiorna.
            nuova != null && descrizioneSenzaMacchina(attuale.descrizione) in
                descrizioniSuperate[chiave].orEmpty() -> nuova
            else -> attuale.descrizione
        }
        val img = if (attuale.immaginePath.isNullOrBlank()) pathAsset else attuale.immaginePath
        if (adottato || descr != attuale.descrizione || img != attuale.immaginePath) {
            aggiornati += attuale.copy(descrizione = descr, immaginePath = img)
        }
    }
    return AllineamentoCatalogo(nuovi, aggiornati)
}

/**
 * Un predefinito rinominato che è [seed]: stesso gruppo e attrezzo, e la descrizione del catalogo.
 * L'immagine conta solo se la descrizione manca: alcune immagini sono condivise da più esercizi.
 */
private fun stessoEsercizio(orfano: Esercizio, seed: Esercizio, chiave: String, pathAsset: String?): Boolean {
    if (orfano.gruppoMuscolare != seed.gruppoMuscolare || orfano.attrezzatura != seed.attrezzatura) return false
    val d = orfano.descrizione
    if (d.isNullOrBlank()) return pathAsset != null && orfano.immaginePath == pathAsset
    return d == seed.descrizione ||
        seed.descrizione?.contains(d) == true ||
        descrizioneSenzaMacchina(d) in descrizioniSuperate[chiave].orEmpty()
}
