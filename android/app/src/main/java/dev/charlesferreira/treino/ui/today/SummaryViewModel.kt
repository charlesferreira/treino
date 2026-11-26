package dev.charlesferreira.treino.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.charlesferreira.treino.data.DaySummary
import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.LoadHistory
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SessionDuration
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Volume
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import dev.charlesferreira.treino.domain.WorkoutProgress
import dev.charlesferreira.treino.ui.screens.weekdayLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A row of "Subiu de carga": the session's top load against the previous session's. */
data class LiftUi(val name: String, val fromKg: Double, val toKg: Double, val note: String)

data class SummaryUi(
    val workoutName: String,
    /** "Quinta, 26 nov, 19:02 às 20:00". */
    val dateLine: String,
    val minutes: Int?,
    val setsDone: Int,
    val setsTotal: Int,
    val volumeKg: Double,
    val reps: Int,
    /** The workout's previous session, for the comparisons under each number. */
    val previousDate: String?,
    val previousMinutes: Int?,
    val previousSets: Int?,
    val previousVolumeKg: Double?,
    val previousReps: Int?,
    val lifts: List<LiftUi>,
    val note: String?,
) {
    /** "+5% vs 19 nov": rounded; null without a previous volume. */
    val volumeChangePercent: Int?
        get() = previousVolumeKg?.takeIf { it > 0 }?.let { ((volumeKg - it) / it * 100).roundToInt() }
}

data class SummaryInput(
    val session: Session,
    val workout: Workout?,
    val items: List<WorkoutItem>,
    val exercises: Map<String, Exercise>,
    val sets: List<SetEntry>,
    val times: List<TimeEntry>,
    val previous: Session?,
    val previousSets: List<SetEntry>,
    val previousTimes: List<TimeEntry>,
    /** Every day of each exercise done in the session, oldest first. */
    val histories: Map<String, List<ExerciseDay>>,
)

fun buildSummary(input: SummaryInput): SummaryUi {
    val session = input.session
    val progress = WorkoutProgress.of(input.items, session.id, input.sets, input.times)
    val start = session.startedAt ?: (input.sets.mapNotNull { it.loggedAt } + input.times.mapNotNull { it.loggedAt }).minOrNull()
    val end = session.endedAt ?: (input.sets.mapNotNull { it.loggedAt } + input.times.mapNotNull { it.loggedAt }).maxOrNull()
    val dateLine = weekdayLabel(session.date) +
        if (start != null && end != null) ", ${hourMinute(start)} às ${hourMinute(end)}" else ""
    val order = input.items.sortedBy { it.position }.map { it.exerciseId }.distinct()
    val doneIds = input.sets.map { it.exerciseId }.distinct().sortedBy { id -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
    val lifts = doneIds.mapNotNull { id ->
        val exercise = input.exercises[id] ?: return@mapNotNull null
        val days = input.histories[id].orEmpty()
        val history = LoadHistory.of(
            id,
            days.map { Session(id = it.sessionId, date = it.date) },
            days.flatMap { it.sets },
        )
        val up = history.increaseIn(session.id) ?: return@mapNotNull null
        LiftUi(
            name = exercise.name,
            fromKg = up.fromKg,
            toKg = up.toKg,
            note = if (exercise.countsTwoDumbbells) "por halter" else PtBr.loadDelta(up.deltaKg, exercise),
        )
    }
    val previous = input.previous
    return SummaryUi(
        workoutName = input.workout?.name ?: "Treino",
        dateLine = dateLine,
        minutes = SessionDuration.minutes(session, input.sets, input.times),
        setsDone = progress.done,
        setsTotal = progress.total,
        volumeKg = Volume.ofSession(session.id, input.sets, input.exercises),
        reps = input.sets.sumOf { it.reps },
        previousDate = previous?.date,
        previousMinutes = previous?.let { SessionDuration.minutes(it, input.previousSets, input.previousTimes) },
        previousSets = previous?.let { input.previousSets.size + input.previousTimes.size },
        previousVolumeKg = previous?.let { Volume.ofSession(it.id, input.previousSets, input.exercises) },
        previousReps = previous?.let { input.previousSets.sumOf { s -> s.reps } },
        lifts = lifts,
        note = session.note,
    )
}

/** The workout's last executed session before [date], from Evolução's day list. */
fun previousSession(days: List<DaySummary>, workoutId: String?, date: String): Session? =
    if (workoutId == null) {
        null
    } else {
        days.filter { it.session.workoutId == workoutId && it.session.date < date && it.setCount + it.timeCount > 0 }
            .maxByOrNull { it.session.date }?.session
    }

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(private val repository: TreinoRepository, val date: String) : ViewModel() {
    private val session = repository.observeSession(date)

    private val sessionId = session.map { it?.id }.distinctUntilChanged()
    private val sets = sessionId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSets(id) }
    private val times = sessionId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeTimes(id) }

    private val workout = session.map { it?.workoutId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeWorkout(id)
    }
    private val items = workout.flatMapLatest { w -> if (w == null) flowOf(emptyList()) else repository.observeItems(w.id) }

    private val previous = combine(session, repository.observeDays()) { s, days -> previousSession(days, s?.workoutId, date) }
        .distinctUntilChanged()
    private val previousSets = previous.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeSets(p.id) }
    private val previousTimes = previous.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeTimes(p.id) }

    private val histories: Flow<Map<String, List<ExerciseDay>>> = sets
        .map { list -> list.map { it.exerciseId }.distinct().sorted() }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(ids.map { id -> repository.observeExerciseHistory(id).map { id to it } }) { it.toMap() }
            }
        }

    private data class Today(val session: Session?, val workout: Workout?, val items: List<WorkoutItem>, val sets: List<SetEntry>, val times: List<TimeEntry>)
    private data class Before(val previous: Session?, val sets: List<SetEntry>, val times: List<TimeEntry>)

    private val today = combine(session, workout, items, sets, times, ::Today)
    private val before = combine(previous, previousSets, previousTimes, ::Before)

    val state: StateFlow<SummaryUi?> = combine(today, before, repository.observeExercises(), histories) { t, b, ex, h ->
        val s = t.session ?: return@combine null
        buildSummary(
            SummaryInput(
                session = s,
                workout = t.workout,
                items = t.items,
                exercises = ex.associateBy { it.id },
                sets = t.sets,
                times = t.times,
                previous = b.previous,
                previousSets = b.sets,
                previousTimes = b.times,
                histories = h,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveNote(note: String) {
        viewModelScope.launch { repository.setNote(date, note) }
    }
}
