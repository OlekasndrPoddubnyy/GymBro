package com.my.gymbro.data

// Schema di movimento, ricavato da gruppo e nome: vale anche per gli esercizi creati dall'utente.
// In ogni gruppo conta la prima regola che corrisponde.

private val REGOLE_MOVIMENTO: Map<GruppoMuscolare, List<Pair<String, String>>> = mapOf(
    GruppoMuscolare.PETTO to listOf(
        "apertura" to "croci|pectoral",
        "pullover" to "pullover",
        "spinta orizzontale" to "panca|press|piegamenti|dip",
    ),
    GruppoMuscolare.SCHIENA to listOf(
        "stacco" to "stacco|rack pull",
        "estensione schiena" to "lower back|iperestensioni|superman",
        "pullover" to "pullover",
        "tirata orizzontale" to "rematore|pulley|upper back|australian",
        "tirata verticale" to "lat machine|trazioni|traction|pulldown",
    ),
    GruppoMuscolare.SPALLE to listOf(
        "rotazione spalla" to "rotazione",
        "scrollata" to "scrollate",
        "tirata al mento" to "tirate al mento",
        "apertura posteriore" to "posteriori|reverse|face pull",
        "alzata laterale" to "laterali|y-raise",
        "alzata frontale" to "frontali",
        "spinta verticale" to "lento|press|pike|verticale",
    ),
    GruppoMuscolare.BICIPITI to listOf(
        "flessione gomito" to "curl",
    ),
    GruppoMuscolare.TRICIPITI to listOf(
        "spinta orizzontale" to "dip|distensioni|piegamenti",
        "estensione gomito" to "french|estensioni|kickback|pushdown|tricipiti",
    ),
    GruppoMuscolare.GAMBE to listOf(
        "flessione ginocchio" to "leg curl|nordic",
        "stacco" to "stacco|good morning|girata",
        "estensione ginocchio" to "leg extension",
        "adduzione" to "adductor|adduzione",
        "flessione anca" to "flessione dell",
        "affondo" to "affondi|split|step-up",
        "salto" to "jump",
        "squat" to "squat|press|wall sit",
    ),
    GruppoMuscolare.GLUTEI to listOf(
        "abduzione" to "abductor|abduzione|clamshell|hydrant",
        "ponte" to "hip thrust|ponte",
        "stacco" to "stacco|pull-through",
        "squat" to "squat",
        "slancio" to "kickback|slanci|glute machine",
    ),
    GruppoMuscolare.POLPACCI to listOf(
        "estensione caviglia" to "calf",
    ),
    GruppoMuscolare.CORE to listOf(
        "trasporto" to "farmer",
        "stabilità" to "rollout|plank|hollow|dead bug|bird dog|pallof",
        "flessione laterale" to "flessioni laterali",
        "rotazione busto" to "rotazion|twist|woodchop|obliquo|bicycle",
        "sollevamento gambe" to "gambe|ginocchia|inverso|flutter|mountain",
        "flessione busto" to "crunch|sit-up|v-up|abdominal",
    ),
)

private val REGEX_MOVIMENTO: Map<GruppoMuscolare, List<Pair<String, Regex>>> =
    REGOLE_MOVIMENTO.mapValues { (_, regole) -> regole.map { (schema, parole) -> schema to Regex(parole) } }

/** null = non classificato (cardio, stretching o nome non riconosciuto). */
fun schemaMovimento(nome: String, gruppo: GruppoMuscolare): String? {
    val n = nome.lowercase()
    return REGEX_MOVIMENTO[gruppo]?.firstOrNull { (_, regex) -> regex.containsMatchIn(n) }?.first
}
