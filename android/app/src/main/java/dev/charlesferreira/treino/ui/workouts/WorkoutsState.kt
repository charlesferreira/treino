package dev.charlesferreira.treino.ui.workouts

import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseGroups
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.ExerciseSearch
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Steps
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import dev.charlesferreira.treino.ui.evolution.equipmentTag
import dev.charlesferreira.treino.ui.today.restLabel
import kotlin.math.roundToInt

/* Pure state of the Treinos tab (`p-workouts`, `p-workout-edit`, `p-item`, `p-picker` and
 * `dumbbell-equipment`), tested in WorkoutsStateTest. */

/* ------------------------------------------------------------------ Sequence */

data class SequenceRowUi(
    val id: String,
    /** 1-based place in the rotation. */
    val number: Int,
    val name: String,
    /** "6 exercícios, 16 séries", "tempo, 30–40 min", "sem exercícios". */
    val summary: String,
    val upcoming: Boolean,
)

fun buildSequence(
    workouts: List<Workout>,
    items: List<WorkoutItem>,
    exercises: Map<String, Exercise>,
    upcomingId: String?,
): List<SequenceRowUi> {
    val byWorkout = items.groupBy { it.workoutId }
    return workouts.sortedBy { it.position }.mapIndexed { i, w ->
        SequenceRowUi(
            id = w.id,
            number = i + 1,
            name = w.name,
            summary = workoutSummary(byWorkout[w.id].orEmpty().sortedBy { it.position }, exercises),
            upcoming = w.id == upcomingId,
        )
    }
}

fun workoutSummary(items: List<WorkoutItem>, exercises: Map<String, Exercise>): String {
    if (items.isEmpty()) return "sem exercícios"
    val allTime = items.all { exercises[it.exerciseId]?.kind == ExerciseKind.TIME }
    if (allTime) {
        if (items.size > 1) return "tempo, ${items.size} exercícios"
        return minutesRange(items.single())?.let { "tempo, $it min" } ?: "tempo"
    }
    val sets = items.sumOf { it.sets }
    return "${plural(items.size, "exercício", "exercícios")}, ${plural(sets, "série", "séries")}"
}

fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

/* -------------------------------------------------------------- Workout edit */

data class ItemRowUi(val item: WorkoutItem, val exercise: Exercise?, val name: String, val line: String)

data class WorkoutEditUi(
    val workout: Workout,
    val rows: List<ItemRowUi>,
    /** "7 exercícios", "17 séries", "cerca de 55 min"; empty without items. */
    val exercisesTag: String?,
    val setsTag: String?,
    val durationTag: String?,
)

fun buildWorkoutEdit(workout: Workout, items: List<WorkoutItem>, exercises: Map<String, Exercise>): WorkoutEditUi {
    val sorted = items.sortedBy { it.position }
    val rows = sorted.map { item ->
        val ex = exercises[item.exerciseId]
        ItemRowUi(item, ex, ex?.name ?: "Exercício removido", itemLine(item, ex))
    }
    val minutes = estimatedMinutes(sorted, exercises)
    return WorkoutEditUi(
        workout = workout,
        rows = rows,
        exercisesTag = sorted.takeIf { it.isNotEmpty() }?.let { plural(it.size, "exercício", "exercícios") },
        setsTag = sorted.takeIf { it.isNotEmpty() }?.let { plural(it.sumOf { i -> i.sets }, "série", "séries") },
        durationTag = minutes?.let { "cerca de $it min" },
    )
}

/** "3 × 8–12, 2:15"; time: "30–40 min" (one set) or "2 × 30–40 min"; no rest, no ", 0:00". */
fun itemLine(item: WorkoutItem, exercise: Exercise?): String {
    val time = exercise?.kind == ExerciseKind.TIME
    val main = if (time) {
        val range = minutesRange(item)?.let { "$it min" }
        when {
            range == null -> plural(item.sets, "série", "séries")
            item.sets == 1 -> range
            else -> "${item.sets} × $range"
        }
    } else {
        repsRange(item)?.let { "${item.sets} × $it" } ?: plural(item.sets, "série", "séries")
    }
    return if (item.restSec > 0) "$main, ${restLabel(item.restSec)}" else main
}

fun repsRange(item: WorkoutItem): String? = rangeText(item.repsMin?.toString(), item.repsMax?.toString())

fun minutesRange(item: WorkoutItem): String? =
    rangeText(item.minutesMin?.let(PtBr::number), item.minutesMax?.let(PtBr::number))

private fun rangeText(min: String?, max: String?): String? = when {
    min != null && max != null && min != max -> "$min–$max"
    else -> min ?: max
}

/**
 * "cerca de N min": a load set counts 1 min of work plus its rest, each exercise 1 min to set up,
 * a time item the middle of its minutes range. Rounded to 5 min; null without items.
 */
fun estimatedMinutes(items: List<WorkoutItem>, exercises: Map<String, Exercise>): Int? {
    if (items.isEmpty()) return null
    var seconds = 0.0
    for (item in items) {
        seconds += 60
        if (exercises[item.exerciseId]?.kind == ExerciseKind.TIME) {
            val lo = item.minutesMin ?: item.minutesMax ?: 0.0
            val hi = item.minutesMax ?: lo
            seconds += item.sets * ((lo + hi) / 2 * 60 + item.restSec)
        } else {
            seconds += item.sets * (60 + item.restSec)
        }
    }
    return maxOf(5, ((seconds / 60) / 5).roundToInt() * 5)
}

/* ------------------------------------------------------------------ Item sheet */

/** What the range fields hold: nothing, a valid range, or something to fix. */
sealed interface RangeInput {
    data object Empty : RangeInput
    data class Ok(val min: Double, val max: Double) : RangeInput
    data object Invalid : RangeInput
}

/**
 * The two fields of "Repetições" or "Minutos". One filled = that value on both ends; reversed
 * ends are swapped. Reps are whole numbers from 1 to 999; minutes accept a comma, above 0 and up
 * to 600.
 */
fun parseRange(minText: String, maxText: String, decimals: Boolean): RangeInput {
    fun parse(t: String): Double? {
        val s = t.trim().replace(',', '.')
        if (s.isEmpty()) return null
        val v = if (decimals) s.toDoubleOrNull() else s.toIntOrNull()?.toDouble()
        return v ?: Double.NaN
    }
    val a = parse(minText)
    val b = parse(maxText)
    if (a == null && b == null) return RangeInput.Empty
    val lo = a ?: b!!
    val hi = b ?: a!!
    val limit = if (decimals) 600.0 else 999.0
    val ok = { v: Double -> !v.isNaN() && v > 0 && v <= limit }
    if (!ok(lo) || !ok(hi)) return RangeInput.Invalid
    return RangeInput.Ok(minOf(lo, hi), maxOf(lo, hi))
}

const val MAX_SETS = 20
const val REST_STEP = 15
const val MAX_REST = 600

/** The editable copy of an item. Reps and minutes are kept apart so switching the kind loses nothing. */
data class ItemDraft(
    val kind: ExerciseKind,
    val sets: Int,
    val repsMin: String,
    val repsMax: String,
    val minutesMin: String,
    val minutesMax: String,
    val restSec: Int,
    val notes: String,
) {
    val range: RangeInput
        get() = if (kind == ExerciseKind.TIME) parseRange(minutesMin, minutesMax, decimals = true) else parseRange(repsMin, repsMax, decimals = false)

    val valid: Boolean get() = range !is RangeInput.Invalid

    fun setsMinus() = copy(sets = (sets - 1).coerceAtLeast(1))
    fun setsPlus() = copy(sets = (sets + 1).coerceAtMost(MAX_SETS))
    fun restMinus() = copy(restSec = (restSec - REST_STEP).coerceAtLeast(0))
    fun restPlus() = copy(restSec = (restSec + REST_STEP).coerceAtMost(MAX_REST))

    /** The item with these values (null while a range is invalid). The other kind's range is cleared. */
    fun applyTo(item: WorkoutItem): WorkoutItem? {
        val r = range
        if (r is RangeInput.Invalid) return null
        val ok = r as? RangeInput.Ok
        val time = kind == ExerciseKind.TIME
        return item.copy(
            sets = sets,
            repsMin = if (time) null else ok?.min?.toInt(),
            repsMax = if (time) null else ok?.max?.toInt(),
            minutesMin = if (time) ok?.min else null,
            minutesMax = if (time) ok?.max else null,
            restSec = restSec,
            notes = notes.trim().takeIf { it.isNotEmpty() },
        )
    }

    /** Séries, Reps (or Minutos) and Descanso, as the equipment sheet shows them. */
    fun prescription(): List<Pair<String, String>> {
        val r = range as? RangeInput.Ok
        val rangeText = r?.let {
            val lo = PtBr.number(it.min)
            val hi = PtBr.number(it.max)
            if (lo == hi) lo else "$lo–$hi"
        } ?: "–"
        return listOf(
            "Séries" to sets.toString(),
            (if (kind == ExerciseKind.TIME) "Minutos" else "Reps") to rangeText,
            "Descanso" to restText(restSec),
        )
    }

    companion object {
        /**
         * The item's own kind keeps what is stored (blank stays blank); the other kind starts at
         * 8 to 12 reps or 20 to 30 minutes, like the PWA's `defaultItem`.
         */
        fun of(item: WorkoutItem, exercise: Exercise?): ItemDraft {
            val kind = exercise?.kind ?: ExerciseKind.LOAD
            val noReps = item.repsMin == null && item.repsMax == null
            val noMinutes = item.minutesMin == null && item.minutesMax == null
            val repsDefault = noReps && kind != ExerciseKind.LOAD
            val minutesDefault = noMinutes && kind != ExerciseKind.TIME
            return ItemDraft(
                kind = kind,
                sets = item.sets,
                repsMin = if (repsDefault) "8" else item.repsMin?.toString().orEmpty(),
                repsMax = if (repsDefault) "12" else item.repsMax?.toString().orEmpty(),
                minutesMin = if (minutesDefault) "20" else item.minutesMin?.let(PtBr::number).orEmpty(),
                minutesMax = if (minutesDefault) "30" else item.minutesMax?.let(PtBr::number).orEmpty(),
                restSec = item.restSec,
                notes = item.notes.orEmpty(),
            )
        }
    }
}

/** "1:30"; 0 is "sem". */
fun restText(seconds: Int): String = if (seconds <= 0) "sem" else restLabel(seconds)

/** Defaults of a new item, from the PWA's `defaultItem`: 3 × 8–12 with 2:00, or 1 × 20–30 min. */
fun newItemDefaults(exercise: Exercise): NewItem = if (exercise.kind == ExerciseKind.TIME) {
    NewItem(sets = 1, minutesMin = 20.0, minutesMax = 30.0, restSec = 0)
} else {
    NewItem(sets = 3, repsMin = 8, repsMax = 12, restSec = 120)
}

data class NewItem(
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val minutesMin: Double? = null,
    val minutesMax: Double? = null,
    val restSec: Int,
)

/** The item of [exerciseId] in the first workout of the rotation (then the first in that workout). */
fun firstItemOf(exerciseId: String, workouts: List<Workout>, items: List<WorkoutItem>): WorkoutItem? {
    val position = workouts.associate { it.id to it.position }
    return items.filter { it.exerciseId == exerciseId && it.workoutId in position }
        .minWithOrNull(compareBy({ position.getValue(it.workoutId) }, { it.position }))
}

/** The tag of the item sheet and of the progression; it opens the equipment sheet. */
fun equipmentChip(exercise: Exercise): String = equipmentTag(exercise) ?: "Equipamento"

/* -------------------------------------------------------------------- Picker */

data class PickerRowUi(val id: String, val name: String, val trailing: String?)

data class PickerSectionUi(val label: String, val rows: List<PickerRowUi>)

data class PickerUi(
    /** The trimmed query for "Criar “…”", null when empty or an exercise already has that name. */
    val create: String?,
    val sections: List<PickerSectionUi>,
)

/** [browse] = "Todos os exercícios": each row shows its equipment. */
fun buildPicker(all: List<Exercise>, query: String, browse: Boolean): PickerUi {
    val found = ExerciseSearch.search(all, query)
    val q = query.trim()
    val create = q.takeIf { it.isNotEmpty() && ExerciseSearch.exactMatch(all, q) == null }
    val sections = ExerciseSearch.grouped(found).map { (group, list) ->
        PickerSectionUi(
            label = ExerciseGroups.label(group),
            rows = list.map { ex ->
                val trailing = when {
                    ex.kind == ExerciseKind.TIME -> "tempo"
                    browse -> equipmentTag(ex)
                    else -> null
                }
                PickerRowUi(ex.id, ex.name, trailing)
            },
        )
    }
    return PickerUi(create, sections)
}

/* ----------------------------------------------------------------- Equipment */

/** The buttons of "Equipamento", in the design's order. */
val EQUIPMENT_OPTIONS: List<Pair<Equipment, String>> = listOf(
    Equipment.DUMBBELL to "Halteres",
    Equipment.BARBELL to "Barra",
    Equipment.MACHINE to "Máquina",
    Equipment.CABLE to "Cabo",
    Equipment.BODYWEIGHT to "Peso corporal",
    Equipment.OTHER to "Outro",
)

/** A button of "Passo do + e −". [kg] null = "1 placa". */
data class StepOption(val label: String, val kg: Double?, val selected: Boolean)

const val PLATE_STEP = 0.5
const val MAX_PLATE = 50.0

/**
 * The equipment sheet's draft. [stepKg] null = the equipment's default (see [Steps]), which is
 * "1 placa" on a machine.
 */
data class EquipmentDraft(
    val equipment: Equipment,
    val countsTwoDumbbells: Boolean,
    val stepKg: Double?,
    val plateKg: Double,
) {
    /** Another equipment starts at its default step; dumbbells start counting 2. */
    fun select(eq: Equipment): EquipmentDraft = if (eq == equipment) {
        this
    } else {
        copy(equipment = eq, countsTwoDumbbells = eq == Equipment.DUMBBELL, stepKg = null)
    }

    fun stepOptions(): List<StepOption> {
        val machine = equipment == Equipment.MACHINE
        val effective = stepKg ?: if (machine) null else Steps.defaultFor(equipment).kg
        val kgs = (Steps.KG_OPTIONS + listOfNotNull(stepKg)).distinct().sorted()
        val plate = if (machine) listOf(StepOption("1 placa", null, effective == null)) else emptyList()
        return plate + kgs.map { StepOption(PtBr.kg(it), it, effective != null && kotlin.math.abs(effective - it) < 1e-9) }
    }

    /** Picking the equipment's own default stores null, so a later change of default follows. */
    fun chooseStep(kg: Double?): EquipmentDraft {
        val default = if (equipment == Equipment.MACHINE) null else Steps.defaultFor(equipment).kg
        return copy(stepKg = if (kg == null || (default != null && kotlin.math.abs(kg - default) < 1e-9)) null else kg)
    }

    fun plateMinus() = copy(plateKg = (plateKg - PLATE_STEP).coerceAtLeast(PLATE_STEP))
    fun platePlus() = copy(plateKg = (plateKg + PLATE_STEP).coerceAtMost(MAX_PLATE))

    fun applyTo(exercise: Exercise): Exercise = exercise.copy(
        equipment = equipment,
        countsTwoDumbbells = equipment == Equipment.DUMBBELL && countsTwoDumbbells,
        stepKg = stepKg,
        plateKg = if (equipment == Equipment.MACHINE) plateKg.takeIf { it != Steps.DEFAULT_PLATE_KG } else exercise.plateKg,
    )

    companion object {
        fun of(exercise: Exercise) = EquipmentDraft(
            equipment = exercise.equipment,
            countsTwoDumbbells = exercise.countsTwoDumbbells,
            stepKg = exercise.stepKg?.takeIf { it > 0 },
            plateKg = exercise.plateKg ?: Steps.DEFAULT_PLATE_KG,
        )
    }
}
