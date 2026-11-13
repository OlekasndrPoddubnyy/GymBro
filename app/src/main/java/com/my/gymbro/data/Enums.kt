package com.my.gymbro.data

enum class Modalita(val label: String) {
    TEMPO("Tempo"),
    RIPETIZIONI("Ripetizioni")
}

enum class GruppoMuscolare(val label: String) {
    PETTO("Petto"),
    SCHIENA("Schiena"),
    SPALLE("Spalle"),
    BICIPITI("Bicipiti"),
    TRICIPITI("Tricipiti"),
    GAMBE("Gambe"),
    GLUTEI("Glutei"),
    POLPACCI("Polpacci"),
    CORE("Core"),
    CARDIO("Cardio"),
    STRETCHING("Stretching")
}

enum class Attrezzatura(val label: String) {
    CORPO_LIBERO("Corpo libero"),
    MANUBRI("Manubri"),
    BILANCIERE("Bilanciere"),
    MACCHINA("Macchina"),
    CAVI("Cavi")
}