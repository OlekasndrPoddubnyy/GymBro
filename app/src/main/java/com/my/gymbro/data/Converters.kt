package com.my.gymbro.data

import androidx.room.TypeConverter

/**
 * Gli enum sono salvati per nome. In lettura un nome sconosciuto (valore rinominato o rimosso in
 * una versione futura) non fa crashare l'app: si usa un valore di riserva.
 */
class Converters {
    @TypeConverter fun fromModalita(v: Modalita): String = v.name
    @TypeConverter fun toModalita(v: String): Modalita = enumONull<Modalita>(v) ?: Modalita.RIPETIZIONI

    @TypeConverter fun fromGruppo(v: GruppoMuscolare): String = v.name
    @TypeConverter fun toGruppo(v: String): GruppoMuscolare = enumONull<GruppoMuscolare>(v) ?: GruppoMuscolare.CORE

    @TypeConverter fun fromAttrezzatura(v: Attrezzatura): String = v.name
    @TypeConverter fun toAttrezzatura(v: String): Attrezzatura = enumONull<Attrezzatura>(v) ?: Attrezzatura.CORPO_LIBERO
}

private inline fun <reified E : Enum<E>> enumONull(nome: String): E? =
    enumValues<E>().firstOrNull { it.name == nome }
