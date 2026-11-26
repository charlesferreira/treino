package dev.charlesferreira.treino.domain.backup

import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * O bloco `native` do format 3: as tabelas do app, uma lista por tabela, com os campos das
 * classes do modelo (enums em minúsculas: `load`/`time`, `dumbbell`/`barbell`/`machine`/`cable`/
 * `bodyweight`/`other`). Campos nulos são omitidos.
 */
@Serializable
internal data class NativeBlock(
    val schema: Int = SCHEMA,
    val exercises: List<Exercise> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val workoutItems: List<WorkoutItem> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val setEntries: List<SetEntry> = emptyList(),
    val timeEntries: List<TimeEntry> = emptyList(),
) {
    companion object {
        const val SCHEMA = 1
    }
}

/**
 * Exporta o backup **format 3**.
 *
 * É um format 2 completo, que o PWA consegue importar como está, mais o bloco `native`:
 *
 * ```
 * {
 *   "app": "treino",
 *   "format": 3,
 *   "exportedAt": "YYYY-MM-DD",
 *   "logs": { "YYYY-MM-DD": { "template": "<id do treino>", "sets": { "<ex>": [{ "w": 16, "r": 10 }] },
 *             "times": { "<ex>": [{ "min": 35, "km": 5.2, "local": "parque" }] }, "note": "..." } },
 *   "settings": { "sound": true, "vibration": true, "keepScreenOn": true },
 *   "plan": { "version": 1, "workouts": [{ "id": "...", "name": "...", "items": [
 *             { "exercise": "<ex>", "kind": "carga", "sets": 3, "reps": [8, 12], "rest": 90, "notes": "..." },
 *             { "exercise": "<ex>", "kind": "tempo", "sets": 1, "minutes": [30, 40], "rest": 0 } ] }] },
 *   "exercises": { "<ex>": { "id": "<ex>", "name": "...", "group": "...", "kind": "carga", "aliases": [], "notes": "..." } },
 *   "profile": { "name": "..." } | null,
 *   "native": { "schema": 1, "exercises": [...], "workouts": [...], "workoutItems": [...],
 *               "sessions": [...], "setEntries": [...], "timeEntries": [...] }
 * }
 * ```
 *
 * - Nas chaves do format 2, exercícios e treinos usam o id antigo (`legacyId`) quando há, senão o
 *   UUID. Assim o histórico importado do PWA volta para ele com os mesmos ids.
 * - `exercises` leva só o que difere do catálogo embutido (como o `treino.exercises` do PWA).
 * - Sessões sem treino saem com `template: ""`. Sem RIR, sem horários e sem equipamento: isso
 *   tudo só existe no `native`.
 * - `native` (ver [NativeBlock]) espelha as tabelas Room, com UUIDs, equipamento, passo, placa,
 *   início e fim da sessão e o horário de cada série. Na importação, quando ele existe, é a
 *   fonte de tudo e as chaves do format 2 são ignoradas.
 */
object BackupExporter {

    private val pretty = Json {
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun export(data: TreinoData, exportedAt: String): String {
        val exById = data.exercises.associateBy { it.id }
        val wById = data.workouts.associateBy { it.id }
        fun exKey(id: String) = exById[id]?.let { it.legacyId ?: it.id } ?: id
        fun wKey(id: String?) = id?.let { wById[it]?.let { w -> w.legacyId ?: w.id } } ?: ""

        val setsBySession = data.setEntries.groupBy { it.sessionId }
        val timesBySession = data.timeEntries.groupBy { it.sessionId }
        val logs = buildJsonObject {
            for (s in data.sessions.sortedBy { it.date }) {
                put(s.date, buildJsonObject {
                    put("template", wKey(s.workoutId))
                    put("sets", buildJsonObject {
                        setsBySession[s.id].orEmpty().groupBy { it.exerciseId }.forEach { (ex, list) ->
                            put(exKey(ex), JsonArray(list.sortedBy { it.position }.map { e ->
                                buildJsonObject {
                                    put("w", jsonNumber(e.weightKg))
                                    put("r", e.reps)
                                }
                            }))
                        }
                    })
                    val times = timesBySession[s.id].orEmpty()
                    if (times.isNotEmpty()) {
                        put("times", buildJsonObject {
                            times.groupBy { it.exerciseId }.forEach { (ex, list) ->
                                put(exKey(ex), JsonArray(list.sortedBy { it.position }.map { t ->
                                    buildJsonObject {
                                        put("min", jsonNumber(t.minutes))
                                        t.km?.let { put("km", jsonNumber(it)) }
                                        t.place?.takeIf { it.isNotEmpty() }?.let { put("local", it) }
                                    }
                                }))
                            }
                        })
                    }
                    s.note?.let { put("note", it) }
                })
            }
        }

        val itemsByWorkout = data.workoutItems.groupBy { it.workoutId }
        val plan = buildJsonObject {
            put("version", 1)
            put("workouts", buildJsonArray {
                for (w in data.workouts.sortedBy { it.position }) {
                    add(buildJsonObject {
                        put("id", w.legacyId ?: w.id)
                        put("name", w.name)
                        put("items", buildJsonArray {
                            for (item in itemsByWorkout[w.id].orEmpty().sortedBy { it.position }) {
                                val time = item.minutesMin != null || exById[item.exerciseId]?.kind == ExerciseKind.TIME
                                add(buildJsonObject {
                                    put("exercise", exKey(item.exerciseId))
                                    put("kind", if (time) "tempo" else "carga")
                                    put("sets", item.sets)
                                    if (time) {
                                        item.minutesMin?.let { min ->
                                            put("minutes", JsonArray(listOf(jsonNumber(min), jsonNumber(item.minutesMax ?: min))))
                                        }
                                    } else {
                                        item.repsMin?.let { min ->
                                            put("reps", JsonArray(listOf(JsonPrimitive(min), JsonPrimitive(item.repsMax ?: min))))
                                        }
                                    }
                                    put("rest", item.restSec)
                                    item.notes?.let { put("notes", it) }
                                })
                            }
                        })
                    })
                }
            })
        }

        val catalog = Catalog.legacyDefs.associateBy { it.id }
        val exercises = buildJsonObject {
            for (e in data.exercises) {
                val key = e.legacyId ?: e.id
                val def = LegacyExerciseDef(key, e.name, e.group, e.kind, e.aliases, e.notes)
                val base = catalog[key]
                if (base != null && base.copy(kind = base.kind ?: ExerciseKind.LOAD) == def) continue
                put(key, buildJsonObject {
                    put("id", key)
                    put("name", e.name)
                    put("group", e.group)
                    put("kind", legacyKindName(e.kind))
                    if (e.aliases.isNotEmpty()) put("aliases", JsonArray(e.aliases.map { JsonPrimitive(it) }))
                    e.notes?.let { put("notes", it) }
                })
            }
        }

        val settings = buildJsonObject {
            data.settings.sound?.let { put("sound", it) }
            data.settings.vibration?.let { put("vibration", it) }
            data.settings.keepScreenOn?.let { put("keepScreenOn", it) }
        }

        val native = NativeBlock(
            exercises = data.exercises,
            workouts = data.workouts,
            workoutItems = data.workoutItems,
            sessions = data.sessions,
            setEntries = data.setEntries,
            timeEntries = data.timeEntries,
        )

        val root = buildJsonObject {
            put("app", "treino")
            put("format", 3)
            put("exportedAt", exportedAt)
            put("logs", logs)
            put("settings", settings)
            put("plan", plan)
            put("exercises", exercises)
            put("profile", data.settings.profileName?.let { buildJsonObject { put("name", it) } } ?: JsonNull)
            put("native", pretty.encodeToJsonElement(NativeBlock.serializer(), native))
        }
        return pretty.encodeToString(JsonObject.serializer(), root)
    }
}
