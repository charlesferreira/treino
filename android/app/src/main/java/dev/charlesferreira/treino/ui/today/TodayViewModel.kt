package dev.charlesferreira.treino.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.charlesferreira.treino.data.DaySummary
import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.data.Settings
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.Steps
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import dev.charlesferreira.treino.timer.RestState
import dev.charlesferreira.treino.timer.RestTimer
import dev.charlesferreira.treino.workout.WorkoutTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

/** An open sheet of Hoje. */
sealed interface TodaySheet {
    data object Switch : TodaySheet
    data object Note : TodaySheet
    data class EditSet(val setId: String) : TodaySheet
    data class EditTime(val timeId: String) : TodaySheet
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: TreinoRepository,
    private val workoutTimer: WorkoutTimer,
    private val app: Context,
) : ViewModel() {
    val date: String = repository.today()

    /* ------------------------------------------------------------------- Rest */

    /** Bumped on resume: the store is read again (a rest may have changed while away). */
    private val restReload = MutableStateFlow(0)

    /** The running (or just finished) rest, from the timer's store. */
    val rest: StateFlow<RestState?> = restReload
        .flatMapLatest { RestTimer.observe(app) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RestTimer.state(app))

    private val _restExpanded = MutableStateFlow(false)

    /** A rest that starts while Hoje is alive opens expanded; one already running when it opened shows as the pill. */
    val restExpanded: StateFlow<Boolean> = _restExpanded

    init {
        viewModelScope.launch {
            var seen = RestTimer.state(app)?.startedAtMs
            rest.collect { r ->
                when {
                    r == null || r.finished -> _restExpanded.value = false
                    r.startedAtMs != seen -> _restExpanded.value = true
                }
                seen = r?.startedAtMs
            }
        }
    }

    fun reloadRest() {
        restReload.update { it + 1 }
    }

    fun expandRest(expanded: Boolean) {
        _restExpanded.value = expanded
    }

    fun adjustRest(deltaMs: Long) = RestTimer.adjust(app, deltaMs)

    fun skipRest() = RestTimer.skip(app)

    fun restDoneSeen() = RestTimer.dismissFinished(app)

    private val selectedItemId = MutableStateFlow<String?>(null)

    /** Stepper and keyboard edits of the next set, by row; the row's default fills the rest. */
    private val setEdits = MutableStateFlow<Map<DraftKey, SetDraft>>(emptyMap())
    private val timeEdits = MutableStateFlow<Map<DraftKey, TimeDraft>>(emptyMap())

    private val _sheet = MutableStateFlow<TodaySheet?>(null)
    val sheet: StateFlow<TodaySheet?> = _sheet

    private val session = repository.observeSession(date)
    private val workout = repository.observeWorkoutFor(date)

    private val items = workout.flatMapLatest { w ->
        if (w == null) flowOf(emptyList()) else repository.observeItems(w.id)
    }

    private val sets = session.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSets(id)
    }

    private val times = session.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeTimes(id)
    }

    private val histories: Flow<Map<String, List<ExerciseDay>>> = items
        .map { list -> list.map { it.exerciseId }.distinct() }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(ids.map { id -> repository.observeExerciseHistory(id).map { id to it } }) { it.toMap() }
            }
        }

    private data class Base(
        val workouts: List<Workout>,
        val workout: Workout?,
        val next: Workout?,
        val session: Session?,
        val items: List<WorkoutItem>,
    )

    private val base = combine(
        repository.observeWorkouts(),
        workout,
        repository.observeNextInSequence(date),
        session,
        items,
    ) { ws, w, next, s, it -> Base(ws, w, next, s, it) }

    private val log = combine(sets, times, histories) { s, t, h -> Triple(s, t, h) }

    private data class Catalog(
        val exercises: List<Exercise>,
        val allItems: List<WorkoutItem>,
        val days: List<DaySummary>,
        val settings: Settings,
    )

    private val catalog = combine(
        repository.observeExercises(),
        repository.observeAllItems(),
        repository.observeDays(),
        repository.settings.settings,
        ::Catalog,
    )

    val state: StateFlow<TodayUi?> = combine(base, log, catalog, selectedItemId) { b, (s, t, h), c, selected ->
        buildToday(
            TodayInput(
                date = date,
                profileName = c.settings.profileName,
                keepScreenOn = c.settings.keepScreenOn,
                workouts = b.workouts,
                workout = b.workout,
                nextInSequence = b.next,
                session = b.session,
                items = b.items,
                allItems = c.allItems,
                exercises = c.exercises.associateBy { it.id },
                sets = s,
                times = t,
                histories = h,
                days = c.days,
                selectedItemId = selected,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val edits: StateFlow<Map<DraftKey, SetDraft>> = setEdits
    val timeDrafts: StateFlow<Map<DraftKey, TimeDraft>> = timeEdits

    /* ---------------------------------------------------------------- Actions */

    fun start() {
        viewModelScope.launch { repository.startSession(date) }
    }

    fun openSheet(sheet: TodaySheet) {
        _sheet.value = sheet
    }

    fun closeSheet() {
        _sheet.value = null
    }

    fun chooseWorkout(workoutId: String) {
        _sheet.value = null
        selectedItemId.value = null
        viewModelScope.launch { repository.chooseWorkout(date, workoutId) }
    }

    fun saveNote(note: String) {
        _sheet.value = null
        viewModelScope.launch { repository.setNote(date, note) }
    }

    fun selectItem(itemId: String) {
        selectedItemId.value = itemId
    }

    fun setDraft(ui: ItemUi): SetDraft = setEdits.value[ui.draftKey()] ?: ui.defaultSetDraft(state.value?.session?.id)

    fun stepWeight(ui: ItemUi, up: Boolean) = editSet(ui) { d ->
        d.copy(weightKg = if (up) Steps.up(d.weightKg, ui.exercise) else Steps.down(d.weightKg, ui.exercise))
    }

    fun stepReps(ui: ItemUi, up: Boolean) = editSet(ui) { d -> d.copy(reps = max(0, d.reps + if (up) 1 else -1)) }

    fun typeWeight(ui: ItemUi, kg: Double) = editSet(ui) { it.copy(weightKg = kg) }

    fun typeReps(ui: ItemUi, reps: Int) = editSet(ui) { it.copy(reps = reps) }

    private fun editSet(ui: ItemUi, change: (SetDraft) -> SetDraft) {
        val key = ui.draftKey()
        setEdits.update { it + (key to change(it[key] ?: ui.defaultSetDraft(state.value?.session?.id))) }
    }

    fun timeDraft(ui: ItemUi): TimeDraft = timeEdits.value[ui.draftKey()] ?: ui.defaultTimeDraft()

    fun editTime(ui: ItemUi, change: (TimeDraft) -> TimeDraft) {
        val key = ui.draftKey()
        timeEdits.update { it + (key to change(it[key] ?: ui.defaultTimeDraft())) }
    }

    /**
     * "Registrar série N": the single place a set of the workout screen is logged. The rest starts
     * here (none after the last set of the workout); see [WorkoutTimer.afterSetLogged].
     */
    fun onSetLogged(ui: ItemUi, weightKg: Double, reps: Int) {
        val key = ui.draftKey()
        viewModelScope.launch {
            val set = repository.logSet(date, ui.exercise.id, weightKg, reps)
            setEdits.update { it - key }
            // The exercise is complete: the card moves on to the next one not done.
            if (ui.sets.size + 1 >= ui.total) selectedItemId.update { if (it == ui.item.id) null else it }
            workoutTimer.afterSetLogged(date, ui.item.id, set)
        }
    }

    /** "Registrar" of a time exercise. No rest after it. */
    fun onTimeLogged(ui: ItemUi, draft: TimeDraft) {
        val key = ui.draftKey()
        viewModelScope.launch {
            repository.logTime(date, ui.exercise.id, draft.minutes, parseDecimal(draft.km), draft.place)
            timeEdits.update { it - key }
            if (ui.times.size + 1 >= ui.total) selectedItemId.update { if (it == ui.item.id) null else it }
        }
    }

    fun updateSet(id: String, weightKg: Double, reps: Int) {
        _sheet.value = null
        viewModelScope.launch { repository.updateSet(id, weightKg, reps) }
    }

    fun deleteSet(id: String) {
        _sheet.value = null
        viewModelScope.launch { repository.deleteSet(id) }
    }

    fun updateTime(id: String, minutes: Double, km: Double?, place: String?) {
        _sheet.value = null
        viewModelScope.launch { repository.updateTime(id, minutes, km, place) }
    }

    fun deleteTime(id: String) {
        _sheet.value = null
        viewModelScope.launch { repository.deleteTime(id) }
    }

    /** "Terminar treino": sets the end, then [then] opens the summary. */
    fun finish(then: () -> Unit) {
        viewModelScope.launch {
            if (RestTimer.state(app) != null) RestTimer.skip(app)
            repository.finishSession(date)
            then()
        }
    }
}
