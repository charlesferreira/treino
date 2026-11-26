package dev.charlesferreira.treino.domain.backup

import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.EquipmentGuess
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Ids
import dev.charlesferreira.treino.domain.humanizeId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Definição de exercício no formato do PWA (`ExerciseDef`). */
internal data class LegacyExerciseDef(
    val id: String,
    val name: String,
    val group: String,
    val kind: ExerciseKind?,
    val aliases: List<String> = emptyList(),
    val notes: String? = null,
)

internal fun legacyKind(s: String?): ExerciseKind? = when (s) {
    "tempo" -> ExerciseKind.TIME
    "carga" -> ExerciseKind.LOAD
    else -> null
}

internal fun legacyKindName(k: ExerciseKind): String = if (k == ExerciseKind.TIME) "tempo" else "carga"

internal fun parseLegacyDef(fallbackId: String?, el: JsonElement?): LegacyExerciseDef? {
    val o = el.asObject() ?: return null
    val id = o["id"].asString()?.takeIf { it.isNotBlank() } ?: fallbackId ?: return null
    return LegacyExerciseDef(
        id = id,
        name = o["name"].asString()?.trim()?.takeIf { it.isNotEmpty() } ?: humanizeId(id),
        group = o["group"].asString()?.takeIf { it.isNotBlank() } ?: "meus",
        kind = legacyKind(o["kind"].asString()),
        aliases = o["aliases"].asArray()?.mapNotNull { it.asString() } ?: emptyList(),
        notes = o["notes"].asString()?.takeIf { it.isNotBlank() },
    )
}

/** Converte a definição do PWA, deduzindo o equipamento ("halter" no nome = halteres). */
internal fun LegacyExerciseDef.toExercise(kindHint: ExerciseKind? = null): Exercise {
    val k = kind ?: kindHint ?: ExerciseKind.LOAD
    val equipment = EquipmentGuess.fromName(name, k)
    return Exercise(
        id = Ids.exercise(id),
        legacyId = id,
        name = name,
        group = group,
        kind = k,
        equipment = equipment,
        countsTwoDumbbells = equipment == Equipment.DUMBBELL,
        aliases = aliases,
        notes = notes,
    )
}

/** Programa de exemplo (`src/program.json`), só usado como semente. */
internal data class SeedProgram(
    val exercises: List<LegacyExerciseDef>,
    /** Na ordem segunda → domingo; o id é a chave do dia. */
    val days: List<SeedDay>,
)

internal data class SeedDay(val key: String, val title: String, val items: List<SeedItem>)

internal data class SeedItem(
    val exercise: String,
    val kind: ExerciseKind,
    val sets: Int,
    val reps: Pair<Double, Double>?,
    val minutes: Pair<Double, Double>?,
    val rest: Int,
    val notes: String?,
)

/** Catálogo e programa de exemplo do PWA, copiados para `resources/catalog/`. */
object Catalog {
    private val DAY_ORDER = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    internal val legacyDefs: List<LegacyExerciseDef> by lazy {
        Json.parseToJsonElement(read("catalog/exercises.json")).asArray()!!.mapNotNull { parseLegacyDef(null, it) }
    }

    /** O catálogo embutido como entidades, com os mesmos UUIDs que a importação usa. */
    val exercises: List<Exercise> by lazy { legacyDefs.map { it.toExercise() } }

    internal val seedProgram: SeedProgram by lazy {
        val root = Json.parseToJsonElement(read("catalog/program.json")) as JsonObject
        val exercises = root["exercises"].asObject()!!.map { (id, el) ->
            val o = el.asObject()
            LegacyExerciseDef(
                id = id,
                name = o?.get("name").asString() ?: id,
                group = "meus",
                kind = ExerciseKind.LOAD,
                notes = o?.get("notes").asString()?.takeIf { it.isNotBlank() },
            )
        }
        val week = root["week"].asObject()!!
        val days = DAY_ORDER.map { key ->
            val day = week[key].asObject()!!
            val title = day["title"].asString() ?: key
            val items = if (day["type"].asString() == "cardio") {
                val cardio = day["cardio"].asObject()
                listOf(
                    SeedItem(
                        exercise = "corrida",
                        kind = ExerciseKind.TIME,
                        sets = 1,
                        reps = null,
                        minutes = cardio?.get("minutes").asRange(),
                        rest = 0,
                        notes = cardio?.get("guidance").asString(),
                    ),
                )
            } else {
                day["items"].asArray().orEmpty().mapNotNull { it.asObject() }.map { it ->
                    SeedItem(
                        exercise = it["exercise"].asString()!!,
                        kind = ExerciseKind.LOAD,
                        sets = it["sets"].asInt() ?: 1,
                        reps = it["reps"].asRange(),
                        minutes = null,
                        rest = it["rest"].asInt() ?: 0,
                        notes = null,
                    )
                }
            }
            SeedDay(key, title, items)
        }
        SeedProgram(exercises, days)
    }

    private fun read(path: String): String =
        (Catalog::class.java.classLoader.getResourceAsStream(path) ?: error("Recurso ausente: $path"))
            .use { it.readBytes().toString(Charsets.UTF_8) }
}
