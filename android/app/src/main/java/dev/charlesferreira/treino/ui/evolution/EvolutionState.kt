package dev.charlesferreira.treino.ui.evolution

import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.LoadHistory
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SessionDuration
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Volume
import dev.charlesferreira.treino.domain.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Pure state of Evolução > Dias and Evolução > Exercícios (`b-history`, `b-progress`), built from
 * the whole log. Facts only: counts, durations, volume (labelled as volume), top loads and when
 * they went up. No advice.
 */

/** The whole log, as Evolução reads it. */
data class EvolutionData(
    val sessions: List<Session>,
    val workouts: List<Workout>,
    val exercises: List<Exercise>,
    val sets: List<SetEntry>,
    val times: List<TimeEntry>,
) {
    val exercisesById: Map<String, Exercise> by lazy { exercises.associateBy { it.id } }
    val setsBySession: Map<String, List<SetEntry>> by lazy { sets.groupBy { it.sessionId } }
    val timesBySession: Map<String, List<TimeEntry>> by lazy { times.groupBy { it.sessionId } }

    /** Load history of every exercise with sets. */
    val histories: Map<String, LoadHistory> by lazy {
        sets.map { it.exerciseId }.distinct().associateWith { LoadHistory.of(it, sessions, sets) }
    }

    /** (sessionId, exerciseId) pairs where the exercise's top load went up. */
    val increases: Set<Pair<String, String>> by lazy {
        histories.flatMap { (id, h) -> h.increases().map { it.sessionId to id } }.toSet()
    }
}

/* ------------------------------------------------------------------ Calendar */

/** A cell of the month card: `.g` (load day), `.c` (time only), plain; `.t` rings today. */
enum class DayMark { NONE, LOAD, TIME }

data class CalendarCell(val date: String, val mark: DayMark, val today: Boolean)

/**
 * The month card: today's month and how many days of it had a workout, over the last [WEEKS]
 * weeks (Monday first, the current week last), as the design draws three rows ending in today's.
 */
data class MonthUi(val title: String, val count: Int, val weeks: List<List<CalendarCell>>, val key: String) {
    companion object {
        const val WEEKS = 3
    }
}

fun dayMarks(data: EvolutionData): Map<String, DayMark> {
    val marks = mutableMapOf<String, DayMark>()
    data.sessions.forEach { s ->
        val mark = when {
            data.setsBySession[s.id].orEmpty().isNotEmpty() -> DayMark.LOAD
            data.timesBySession[s.id].orEmpty().isNotEmpty() -> DayMark.TIME
            else -> DayMark.NONE
        }
        val current = marks[s.date] ?: DayMark.NONE
        // A day with a load session wins over a time-only one.
        if (mark == DayMark.LOAD || (mark == DayMark.TIME && current == DayMark.NONE)) marks[s.date] = mark
    }
    return marks
}

fun buildMonth(today: String, marks: Map<String, DayMark>, weeks: Int = MonthUi.WEEKS): MonthUi {
    val t = LocalDate.parse(today)
    val monday = t.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
    val rows = (0 until weeks).map { w ->
        (0 until 7).map { d ->
            val date = monday.plusWeeks(w.toLong()).plusDays(d.toLong())
            val key = date.toString()
            CalendarCell(key, if (date.isAfter(t)) DayMark.NONE else marks[key] ?: DayMark.NONE, date == t)
        }
    }
    val count = marks.count { (date, mark) ->
        mark != DayMark.NONE && LocalDate.parse(date).let { it.year == t.year && it.month == t.month && !it.isAfter(t) }
    }
    return MonthUi(PtBr.monthName(today), count, rows, today.take(7))
}

/* ---------------------------------------------------------------------- Days */

/** `.fact`: dim text with the number in mono ("**16** séries", "volume **6.840 kg**", "esteira"). */
data class Fact(val before: String = "", val value: String? = null, val after: String = "")

/** A line under the latest day: "Rosca martelo ... ↑ máx 16 kg". */
data class DayLine(val name: String, val maxKg: Double, val up: Boolean)

data class DayRowUi(
    val date: String,
    /** "2026-11": the list starts a new card, under the month's name, when it changes. */
    val monthKey: String,
    /** "Outubro", or "Outubro de 2025" outside today's year. */
    val monthLabel: String,
    /** "26". */
    val day: String,
    /** "qui". */
    val weekday: String,
    val title: String,
    val facts: List<Fact>,
    val lines: List<DayLine>,
)

/** How many exercise lines the latest day shows. */
const val DAY_LINES = 3

fun seriesLabel(n: Int) = if (n == 1) " série" else " séries"

/**
 * Days with sets, times or a note, newest first. A load day: series, duration (when the day has
 * times; imported days have none) and volume. A time-only day: the minutes, km and place logged.
 * The latest load day also lists up to [DAY_LINES] exercises with their top load, the ones that went
 * up first, then in the order they were done.
 */
fun buildDays(data: EvolutionData, today: String): List<DayRowUi> {
    val workoutNames = data.workouts.associate { it.id to it.name }
    val days = data.sessions.filter { s ->
        data.setsBySession[s.id].orEmpty().isNotEmpty() || data.timesBySession[s.id].orEmpty().isNotEmpty() || s.note != null
    }.sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.startedAt ?: 0L })
    // The exercise lines go on the latest day with load sets (a run after it has none to show).
    val linesOn = days.firstOrNull { data.setsBySession[it.id].orEmpty().isNotEmpty() }?.id
    return days.map { s ->
        val sets = data.setsBySession[s.id].orEmpty()
        val times = data.timesBySession[s.id].orEmpty()
        val facts = mutableListOf<Fact>()
        if (sets.isNotEmpty()) {
            facts += Fact(value = "${sets.size + times.size}", after = seriesLabel(sets.size + times.size))
            SessionDuration.minutes(s, sets, times)?.let { facts += Fact(value = "$it", after = " min") }
            facts += Fact(before = "volume ", value = volumeText(Volume.ofSession(s.id, sets, data.exercisesById)))
        } else if (times.isNotEmpty()) {
            facts += Fact(value = PtBr.number(times.sumOf { it.minutes }), after = " min")
            times.mapNotNull { it.km }.takeIf { it.isNotEmpty() }?.let { facts += Fact(value = PtBr.number(it.sum()), after = " km") }
            times.firstNotNullOfOrNull { it.place }?.let { facts += Fact(before = it) }
        }
        // Without a workout (deleted, or a free day): the first exercise's name.
        val title = s.workoutId?.let(workoutNames::get)
            ?: (exerciseOrder(sets).firstOrNull() ?: times.firstOrNull()?.exerciseId)?.let { data.exercisesById[it]?.name }
            ?: "Treino"
        DayRowUi(
            date = s.date,
            monthKey = s.date.take(7),
            monthLabel = PtBr.monthName(s.date) + if (s.date.take(4) != today.take(4)) " de ${s.date.take(4)}" else "",
            day = LocalDate.parse(s.date).dayOfMonth.toString(),
            weekday = PtBr.weekdayShort(s.date),
            title = title,
            facts = facts,
            lines = if (s.id == linesOn) dayLines(s, sets, data) else emptyList(),
        )
    }
}

private fun dayLines(session: Session, sets: List<SetEntry>, data: EvolutionData): List<DayLine> {
    val order = exerciseOrder(sets)
    val lines = order.mapNotNull { id ->
        val exercise = data.exercisesById[id] ?: return@mapNotNull null
        DayLine(exercise.name, sets.filter { it.exerciseId == id }.maxOf { it.weightKg }, (session.id to id) in data.increases)
    }
    val chosen = (lines.filter { it.up } + lines.filterNot { it.up }).take(DAY_LINES).toSet()
    return lines.filter { it in chosen }
}

/** Exercises in the order they were done (first set's time, then position, then id). */
fun exerciseOrder(sets: List<SetEntry>): List<String> =
    sets.groupBy { it.exerciseId }
        .entries
        .sortedWith(compareBy({ e -> e.value.mapNotNull { it.loggedAt }.minOrNull() ?: Long.MAX_VALUE }, { it.key }))
        .map { it.key }

/* ----------------------------------------------------------------- Exercises */

/** The three tiles over the exercises: over the last [OverviewUi.DAYS] days. */
data class OverviewUi(val risen: Int, val workouts: Int, val volumeChangePercent: Int?) {
    companion object {
        const val DAYS = 30L
    }
}

/** Filter chips. Groups outside these show only under "Todos". */
enum class BodyFilter(val label: String, val groups: Set<String>?) {
    ALL("Todos", null),
    UPPER("Superior", setOf("peito", "costas", "ombros")),
    LEGS("Pernas", setOf("quadriceps", "posteriores", "gluteos", "panturrilha")),
    ARMS("Braços", setOf("biceps", "triceps", "antebraco")),
    ;

    fun matches(group: String): Boolean = groups == null || group in groups
}

data class ExerciseRowUi(
    val exerciseId: String,
    val name: String,
    val group: String,
    /** "+2 kg em 26 nov", "igual desde 3 nov". */
    val fact: String,
    /** Went up within the last 30 days and has stayed there: accent text and sparkline. */
    val up: Boolean,
    /** Top load of the last [SPARK_SESSIONS] sessions, oldest first. */
    val spark: List<Double>,
    /** The latest session's top load. */
    val currentKg: Double,
    val perDumbbell: Boolean,
    val lastDate: String,
)

const val SPARK_SESSIONS = 7

data class ExercisesUi(val overview: OverviewUi, val rows: List<ExerciseRowUi>, val filters: List<BodyFilter>)

fun buildExercises(data: EvolutionData, today: String): ExercisesUi {
    val cutoff = LocalDate.parse(today).minusDays(OverviewUi.DAYS - 1).toString()
    val rows = data.histories.mapNotNull { (id, h) ->
        val exercise = data.exercisesById[id] ?: return@mapNotNull null
        if (exercise.kind == ExerciseKind.TIME || h.loads.isEmpty()) return@mapNotNull null
        val last = h.lastIncrease()
        val since = h.stableSince()!!
        val up = last != null && last.date >= cutoff && last.date == since && last.toKg == h.latestKg
        val fact = when {
            up -> PtBr.increase(last, exercise)
            h.loads.size == 1 -> "1ª vez em ${PtBr.shortDate(since)}"
            else -> "igual desde ${PtBr.shortDate(since)}"
        }
        ExerciseRowUi(
            exerciseId = id,
            name = exercise.name,
            group = exercise.group,
            fact = fact,
            up = up,
            spark = h.loads.takeLast(SPARK_SESSIONS).map { it.maxKg },
            currentKg = h.latestKg!!,
            perDumbbell = exercise.countsTwoDumbbells,
            lastDate = h.loads.last().date,
        ) to (if (up) last.date else "")
    }.sortedWith(
        compareByDescending<Pair<ExerciseRowUi, String>> { it.first.up }
            .thenByDescending { it.second }
            .thenByDescending { it.first.lastDate }
            .thenBy { it.first.name },
    ).map { it.first }

    val previousCutoff = LocalDate.parse(today).minusDays(2 * OverviewUi.DAYS - 1).toString()
    val workoutDays = dayMarks(data).count { (date, mark) -> mark != DayMark.NONE && date in cutoff..today }
    fun volume(from: String, to: String) = data.sessions.filter { it.date in from..to }
        .sumOf { Volume.ofSession(it.id, data.setsBySession[it.id].orEmpty(), data.exercisesById) }
    val recent = volume(cutoff, today)
    val before = volume(previousCutoff, LocalDate.parse(cutoff).minusDays(1).toString())
    val overview = OverviewUi(
        risen = rows.count { it.up },
        workouts = workoutDays,
        volumeChangePercent = if (before > 0) ((recent - before) / before * 100).roundToInt() else null,
    )
    val filters = BodyFilter.entries.filter { f -> f == BodyFilter.ALL || rows.any { f.matches(it.group) } }
    return ExercisesUi(overview, rows, if (filters.size >= 2) filters else listOf(BodyFilter.ALL))
}

/** Volume in whole kg ("6.334 kg"): halves from 2,5 kg steps are noise at this size. */
fun volumeText(kg: Double): String = PtBr.kg(kotlin.math.round(kg))

/** "+6%", "−4%", "0%". */
fun percentText(p: Int): String = when {
    p > 0 -> "+$p%"
    p < 0 -> "−${-p}%"
    else -> "0%"
}
