package dev.charlesferreira.treino.ui.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.ui.workouts.ItemDraft
import dev.charlesferreira.treino.ui.workouts.firstItemOf
import dev.charlesferreira.treino.domain.TimeEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EvolutionUi(
    val profileName: String,
    val month: MonthUi,
    val days: List<DayRowUi>,
    val exercises: ExercisesUi,
)

class EvolutionViewModel(private val repository: TreinoRepository) : ViewModel() {
    private val data = combine(
        repository.observeSessions(),
        repository.observeWorkouts(),
        repository.observeExercises(),
        repository.observeAllSets(),
        repository.observeAllTimes(),
        ::EvolutionData,
    )

    val state: StateFlow<EvolutionUi?> = combine(data, repository.settings.settings) { d, settings ->
        val today = repository.today()
        EvolutionUi(
            profileName = settings.profileName,
            month = buildMonth(today, dayMarks(d)),
            days = buildDays(d, today),
            exercises = buildExercises(d, today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** "Dia do histórico": the day's log, and the edits of "Editar" (sets, times and the note). */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryDayViewModel(private val repository: TreinoRepository, val date: String) : ViewModel() {
    private val session = repository.observeSession(date)
    private val sessionId = session.map { it?.id }.distinctUntilChanged()
    private val sets = sessionId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSets(id) }
    private val times = sessionId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeTimes(id) }
    private val workout = session.map { it?.workoutId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeWorkout(id)
    }
    private val items = workout.flatMapLatest { w -> if (w == null) flowOf(emptyList()) else repository.observeItems(w.id) }

    private data class Log(val sets: List<SetEntry>, val times: List<TimeEntry>)

    val state: StateFlow<DayDetailUi?> = combine(
        combine(session, workout, items) { s, w, i -> Triple(s, w, i) },
        combine(sets, times, ::Log),
        repository.observeExercises(),
    ) { (s, w, i), log, ex ->
        buildDayDetail(date, s, w, i, ex.associateBy { it.id }, log.sets, log.times)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSet(id: String, weightKg: Double, reps: Int) {
        viewModelScope.launch { repository.updateSet(id, weightKg, reps) }
    }

    fun deleteSet(id: String) {
        viewModelScope.launch { repository.deleteSet(id) }
    }

    fun updateTime(time: TimeEntry, minutes: Double) {
        viewModelScope.launch { repository.updateTime(time.id, minutes, time.km, time.place) }
    }

    fun deleteTime(id: String) {
        viewModelScope.launch { repository.deleteTime(id) }
    }

    fun saveNote(note: String) {
        viewModelScope.launch { repository.setNote(date, note) }
    }
}

class ExerciseProgressViewModel(private val repository: TreinoRepository, exerciseId: String) : ViewModel() {
    val state: StateFlow<ProgressUi?> = combine(
        repository.observeExercise(exerciseId),
        repository.observeExerciseHistory(exerciseId),
    ) { exercise, days -> exercise?.let { buildProgress(it, days) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The equipment sheet's "Prescrição": the exercise in the first workout of the rotation that has it. */
    val prescription: StateFlow<List<Pair<String, String>>?> = combine(
        repository.observeExercise(exerciseId),
        repository.observeWorkouts(),
        repository.observeAllItems(),
    ) { exercise, workouts, items ->
        firstItemOf(exerciseId, workouts, items)?.let { ItemDraft.of(it, exercise).prescription() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveExercise(exercise: Exercise) {
        viewModelScope.launch { repository.updateExercise(exercise) }
    }
}
