package dev.charlesferreira.treino.domain.backup

import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Ids
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import dev.charlesferreira.treino.domain.humanizeId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Lê um backup e devolve a prévia com os dados prontos para gravar. Nada é gravado aqui.
 *
 * Aceita, como o `importBackup` do PWA:
 * - format 2: `{app:'treino', format:2, exportedAt, logs, settings, plan, exercises, profile}`;
 * - format 1: `{app:'treino', format:1, exportedAt, logs, settings}`;
 * - o objeto de logs cru (`{'YYYY-MM-DD': DayLog}`);
 * - format 3, o nosso (ver [BackupExporter]): com o bloco `native`, ele é a fonte; sem ele, é lido
 *   como format 2.
 *
 * Regras da conversão dos dados do PWA:
 * - `rir` é ignorado; `cardio` antigo vira tempo do exercício `corrida` (como `migrateLogs`, só
 *   quando o dia ainda não tem `times.corrida`);
 * - "halter" no nome (sem diferenciar maiúsculas e acentos) = halteres, conta 2 halteres; a carga
 *   fica como está (a revisão das cargas antigas é outra etapa);
 * - exercícios = catálogo embutido + `exercises` do backup (sobrepõem pelo id) + qualquer id citado
 *   nos logs ou no programa sem definição (nome = id humanizado, grupo `meus`);
 * - sem programa e com logs, os treinos e exercícios vêm do programa de exemplo, como o
 *   `seedFromProgram` do PWA;
 * - UUIDs determinísticos a partir dos ids antigos ([Ids]): reimportar não duplica.
 */
object BackupImporter {

    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parse(text: String): ImportPreview {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw BackupFormatException("Arquivo inválido: não é um JSON.", e)
        }

        if (root is JsonObject && "logs" in root) {
            val format = root["format"].asInt() ?: 1
            if (format > 3) {
                throw BackupFormatException("Este backup é de uma versão mais nova do app (format $format).")
            }
            val native = root["native"].asObject()
            if (format == 3 && native != null) return parseNative(root, native)
            val source = when (format) {
                1 -> BackupSource.FORMAT_1
                2 -> BackupSource.FORMAT_2
                else -> BackupSource.FORMAT_3
            }
            return parseLegacy(source, root["logs"], root)
        }
        return parseLegacy(BackupSource.RAW_LOGS, root, null)
    }

    /* ------------------------------ PWA (format 1, 2, cru) ------------------------------ */

    private class Day(
        val date: String,
        val template: String?,
        val sets: Map<String, List<Pair<Double, Int>>>,
        val times: Map<String, List<LegacyTime>>,
        val note: String?,
    )

    private data class LegacyTime(val min: Double, val km: Double?, val local: String?)

    private fun parseLegacy(source: BackupSource, logsEl: JsonElement?, backup: JsonObject?): ImportPreview {
        val days = parseLogs(logsEl)

        // Exercícios: catálogo, depois os do aparelho (ganham no empate de id).
        val defs = LinkedHashMap<String, LegacyExerciseDef>()
        Catalog.legacyDefs.forEach { defs[it.id] = it }
        val userIds = LinkedHashSet<String>()
        when (val ex = backup?.get("exercises")) {
            is JsonObject -> ex.forEach { (key, el) -> parseLegacyDef(key, el)?.let { defs[it.id] = it; userIds += it.id } }
            else -> ex.asArray()?.forEach { el -> parseLegacyDef(null, el)?.let { defs[it.id] = it; userIds += it.id } }
        }

        // Programa.
        val planObj = backup?.get("plan").asObject()
        val hasPlan = planObj != null
        var plan = planObj?.let { parsePlan(it) } ?: emptyList()
        val seeded = !hasPlan && days.isNotEmpty()
        if (seeded) {
            val seed = Catalog.seedProgram
            seed.exercises.forEach { defs[it.id] = it; userIds += it.id }
            plan = seed.days.map { PlanWorkout(it.key, it.title, it.items) }
        }

        // O tipo de um exercício sem definição (ou sem `kind`) vem de onde ele aparece.
        val kindHint = HashMap<String, ExerciseKind>()
        days.forEach { d ->
            d.sets.keys.forEach { kindHint.putIfAbsent(it, ExerciseKind.LOAD) }
            d.times.keys.forEach { kindHint[it] = ExerciseKind.TIME }
        }
        plan.forEach { w -> w.items.forEach { kindHint.putIfAbsent(it.exercise, it.kind) } }
        val referenced = LinkedHashSet<String>()
        days.forEach { referenced += it.sets.keys; referenced += it.times.keys }
        plan.forEach { w -> w.items.forEach { referenced += it.exercise } }
        referenced.filter { it !in defs }.forEach { id ->
            defs[id] = LegacyExerciseDef(id, humanizeId(id), "meus", kindHint[id] ?: ExerciseKind.LOAD)
        }

        val exercises = defs.values.map { it.toExercise(kindHint[it.id]) }
        val exByLegacy = exercises.associateBy { it.legacyId!! }

        val workouts = plan.mapIndexed { i, w -> Workout(Ids.workout(w.id), w.id, w.name, i) }
        val items = plan.flatMap { w ->
            w.items.mapIndexed { i, it ->
                val time = it.kind == ExerciseKind.TIME
                WorkoutItem(
                    id = Ids.workoutItem(w.id, i),
                    workoutId = Ids.workout(w.id),
                    exerciseId = exByLegacy.getValue(it.exercise).id,
                    position = i,
                    sets = it.sets,
                    repsMin = if (time) null else it.reps?.first?.toInt(),
                    repsMax = if (time) null else it.reps?.second?.toInt(),
                    minutesMin = if (time) it.minutes?.first else null,
                    minutesMax = if (time) it.minutes?.second else null,
                    restSec = it.rest,
                    notes = it.notes,
                )
            }
        }
        val workoutByLegacy = workouts.associateBy { it.legacyId }

        val sessions = ArrayList<Session>()
        val sets = ArrayList<SetEntry>()
        val times = ArrayList<TimeEntry>()
        for (d in days) {
            val sid = Ids.session(d.date)
            sessions += Session(sid, d.date, d.template?.let { workoutByLegacy[it]?.id }, d.note)
            d.sets.forEach { (ex, list) ->
                list.forEachIndexed { i, (w, r) ->
                    sets += SetEntry(Ids.setEntry(d.date, ex, i), sid, exByLegacy.getValue(ex).id, i, w, r)
                }
            }
            d.times.forEach { (ex, list) ->
                list.forEachIndexed { i, t ->
                    times += TimeEntry(Ids.timeEntry(d.date, ex, i), sid, exByLegacy.getValue(ex).id, i, t.min, t.km, t.local)
                }
            }
        }

        val data = TreinoData(exercises, workouts, items, sessions, sets, times, parseSettings(backup))
        val fileExercises = (referenced + userIds).toSet()
        return preview(source, backup, data, fileExercises.size, hasPlan, seeded)
    }

    private fun parseLogs(logsEl: JsonElement?): List<Day> {
        val logs = logsEl.asObject()
            ?: throw BackupFormatException("Formato inválido: o arquivo não traz os registros de treino.")
        return logs.map { (date, el) ->
            if (!isDate(date)) throw BackupFormatException("Registro inválido: $date (a data deve ser AAAA-MM-DD).")
            val log = el.asObject()
                ?: throw BackupFormatException("Registro inválido: $date (o dia deve ser um objeto).")

            val sets = LinkedHashMap<String, List<Pair<Double, Int>>>()
            log["sets"].asObject()?.forEach { (ex, arr) ->
                // `rir` é ignorado de propósito.
                val list = arr.asArray().orEmpty().mapNotNull { s ->
                    val o = s.asObject() ?: return@mapNotNull null
                    val w = o["w"].asDouble() ?: return@mapNotNull null
                    val r = o["r"].asInt() ?: return@mapNotNull null
                    w to r
                }
                if (list.isNotEmpty()) sets[ex] = list
            }

            val times = LinkedHashMap<String, List<LegacyTime>>()
            log["times"].asObject()?.forEach { (ex, arr) ->
                val list = arr.asArray().orEmpty().mapNotNull { t ->
                    val o = t.asObject() ?: return@mapNotNull null
                    val min = o["min"].asDouble() ?: return@mapNotNull null
                    LegacyTime(min, o["km"].asDouble(), o["local"].asString()?.takeIf { it.isNotEmpty() })
                }
                if (list.isNotEmpty()) times[ex] = list
            }
            // migrateLogs: o cardio do formato antigo vira tempo de `corrida`, se ainda não houver.
            log["cardio"].asObject()?.let { c ->
                val minutes = c["minutes"].asDouble() ?: return@let
                if ("corrida" !in times) {
                    times["corrida"] = listOf(LegacyTime(minutes, c["km"].asDouble(), c["local"].asString()?.takeIf { it.isNotEmpty() }))
                }
            }

            Day(
                date = date,
                template = log["template"].asString()?.takeIf { it.isNotEmpty() },
                sets = sets,
                times = times,
                note = log["note"].asString()?.trim()?.takeIf { it.isNotEmpty() },
            )
        }.sortedBy { it.date }
    }

    private class PlanWorkout(val id: String, val name: String, val items: List<SeedItem>)

    private fun parsePlan(plan: JsonObject): List<PlanWorkout> {
        val seen = HashSet<String>()
        return plan["workouts"].asArray().orEmpty().mapIndexedNotNull { i, el ->
            val w = el.asObject() ?: return@mapIndexedNotNull null
            val id = w["id"].asString()?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            if (!seen.add(id)) return@mapIndexedNotNull null
            val items = w["items"].asArray().orEmpty().mapNotNull { itEl ->
                val it = itEl.asObject() ?: return@mapNotNull null
                val exercise = it["exercise"].asString()?.takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
                val kind = legacyKind(it["kind"].asString())
                    ?: if (it["minutes"] != null && it["reps"] == null) ExerciseKind.TIME else ExerciseKind.LOAD
                SeedItem(
                    exercise = exercise,
                    kind = kind,
                    sets = it["sets"].asInt()?.coerceAtLeast(1) ?: 1,
                    reps = it["reps"].asRange(),
                    minutes = it["minutes"].asRange(),
                    rest = it["rest"].asInt()?.coerceAtLeast(0) ?: 0,
                    notes = it["notes"].asString()?.takeIf { s -> s.isNotBlank() },
                )
            }
            PlanWorkout(id, w["name"].asString()?.takeIf { it.isNotBlank() } ?: "Treino ${i + 1}", items)
        }
    }

    /* ---------------------------------- format 3 ---------------------------------- */

    private fun parseNative(root: JsonObject, native: JsonObject): ImportPreview {
        val block = try {
            json.decodeFromJsonElement(NativeBlock.serializer(), native)
        } catch (e: SerializationException) {
            throw BackupFormatException("Backup inválido: o bloco do app nativo está corrompido.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Backup inválido: o bloco do app nativo está corrompido.", e)
        }
        if (block.schema > NativeBlock.SCHEMA) {
            throw BackupFormatException("Este backup é de uma versão mais nova do app (bloco nativo ${block.schema}).")
        }
        block.sessions.forEach {
            if (!isDate(it.date)) throw BackupFormatException("Registro inválido: ${it.date} (a data deve ser AAAA-MM-DD).")
        }
        val data = TreinoData(
            exercises = block.exercises,
            workouts = block.workouts,
            workoutItems = block.workoutItems,
            sessions = block.sessions,
            setEntries = block.setEntries,
            timeEntries = block.timeEntries,
            settings = parseSettings(root),
        )
        val catalogIds = Catalog.legacyDefs.mapTo(HashSet()) { it.id }
        val fileExercises = buildSet {
            data.setEntries.forEach { add(it.exerciseId) }
            data.timeEntries.forEach { add(it.exerciseId) }
            data.workoutItems.forEach { add(it.exerciseId) }
            data.exercises.filter { it.legacyId == null || it.legacyId !in catalogIds }.forEach { add(it.id) }
        }
        return preview(BackupSource.FORMAT_3, root, data, fileExercises.size, root["plan"] is JsonObject, false)
    }

    /* ------------------------------------ comum ------------------------------------ */

    private fun parseSettings(backup: JsonObject?): BackupSettings {
        val s = backup?.get("settings").asObject()
        return BackupSettings(
            sound = s?.get("sound").asBoolean(),
            vibration = s?.get("vibration").asBoolean(),
            keepScreenOn = s?.get("keepScreenOn").asBoolean(),
            profileName = backup?.get("profile").asObject()?.get("name").asString()?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun preview(
        source: BackupSource,
        backup: JsonObject?,
        data: TreinoData,
        fileExercises: Int,
        hasPlan: Boolean,
        seeded: Boolean,
    ): ImportPreview {
        // Conta como Evolução > Dias: o PWA deixa no log o dia em que só se escolheu o treino (ou
        // em que a última série foi apagada). A sessão entra, com o treino escolhido, mas não é um
        // dia de treino.
        val withEntries = data.setEntries.mapTo(HashSet()) { it.sessionId } + data.timeEntries.map { it.sessionId }
        val dates = data.sessions.filter { it.id in withEntries || it.note != null }.map { it.date }
        return ImportPreview(
            source = source,
            exportedAt = backup?.get("exportedAt").asString(),
            days = dates.size,
            sets = data.setEntries.size,
            times = data.timeEntries.size,
            workouts = data.workouts.size,
            exercises = fileExercises,
            firstDate = dates.minOrNull(),
            lastDate = dates.maxOrNull(),
            hasPlan = hasPlan,
            seededFromExample = seeded,
            data = data,
        )
    }

    private fun isDate(s: String): Boolean {
        if (!DATE.matches(s)) return false
        return try {
            LocalDate.parse(s); true
        } catch (_: DateTimeParseException) {
            false
        }
    }
}
