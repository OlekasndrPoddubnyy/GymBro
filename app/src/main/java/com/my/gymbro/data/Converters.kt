package com.my.gymbro.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromModalita(v: Modalita): String = v.name
    @TypeConverter fun toModalita(v: String): Modalita = Modalita.valueOf(v)

    @TypeConverter fun fromGruppo(v: GruppoMuscolare): String = v.name
    @TypeConverter fun toGruppo(v: String): GruppoMuscolare = GruppoMuscolare.valueOf(v)

    @TypeConverter fun fromAttrezzatura(v: Attrezzatura): String = v.name
    @TypeConverter fun toAttrezzatura(v: String): Attrezzatura = Attrezzatura.valueOf(v)
}