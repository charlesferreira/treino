package dev.charlesferreira.treino.data

import androidx.room.TypeConverter
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.ExerciseKind
import kotlinx.serialization.json.Json

/** Enums are stored by their serial names ("load", "dumbbell"), the same strings as the backup. */
class Converters {
    @TypeConverter
    fun kindToText(kind: ExerciseKind): String = when (kind) {
        ExerciseKind.LOAD -> "load"
        ExerciseKind.TIME -> "time"
    }

    @TypeConverter
    fun textToKind(text: String): ExerciseKind = if (text == "time") ExerciseKind.TIME else ExerciseKind.LOAD

    @TypeConverter
    fun equipmentToText(equipment: Equipment): String = equipment.name.lowercase()

    @TypeConverter
    fun textToEquipment(text: String): Equipment =
        Equipment.entries.firstOrNull { it.name.lowercase() == text } ?: Equipment.OTHER

    @TypeConverter
    fun listToText(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun textToList(text: String): List<String> =
        if (text.isBlank()) emptyList() else Json.decodeFromString(text)
}
