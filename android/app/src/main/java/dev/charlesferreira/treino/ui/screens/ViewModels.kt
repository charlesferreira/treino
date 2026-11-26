package dev.charlesferreira.treino.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.charlesferreira.treino.data.Settings
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Decides the start: onboarding while the database is empty. Null while loading. */
class RootViewModel(repository: TreinoRepository) : ViewModel() {
    val hasData: StateFlow<Boolean?> = repository.observeHasData()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

class OnboardingViewModel(private val repository: TreinoRepository) : ViewModel() {
    /** "Criar meu primeiro treino": saves the name and seeds the exercise catalog. */
    fun start(name: String, then: () -> Unit) {
        viewModelScope.launch {
            repository.settings.setProfileName(name)
            repository.seedCatalog()
            then()
        }
    }
}

data class TodayItemRow(val number: Int, val name: String, val target: String)

data class TodayState(
    val dateLabel: String,
    val profileName: String,
    val workout: Workout?,
    val items: List<TodayItemRow>,
    val setCount: Int,
    val started: Boolean,
)

/** Phase 1 placeholder of Hoje: today's workout from the rotation and its items. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repository: TreinoRepository) : ViewModel() {
    private val date = repository.today()

    private val workoutItems = repository.observeWorkoutFor(date).flatMapLatest { w ->
        if (w == null) flowOf(null to emptyList()) else repository.observeItems(w.id).map { w to it }
    }

    val state: StateFlow<TodayState?> = combine(
        workoutItems,
        repository.observeExercises(),
        repository.observeSession(date),
        repository.settings.settings,
    ) { (workout, items), exercises, session, settings ->
        val byId = exercises.associateBy { it.id }
        TodayState(
            dateLabel = weekdayLabel(date),
            profileName = settings.profileName,
            workout = workout,
            items = items.mapIndexed { i, item -> TodayItemRow(i + 1, byId[item.exerciseId]?.name ?: "?", target(item, byId[item.exerciseId])) },
            setCount = items.sumOf { it.sets },
            started = session?.startedAt != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun start() {
        viewModelScope.launch { repository.startSession(date) }
    }
}

class WorkoutsViewModel(repository: TreinoRepository) : ViewModel() {
    val workouts: StateFlow<List<Workout>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class ProfileViewModel(private val repository: TreinoRepository) : ViewModel() {
    val settings: StateFlow<Settings> = repository.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun setSound(on: Boolean) {
        viewModelScope.launch { repository.settings.setSound(on) }
    }

    fun setVibration(on: Boolean) {
        viewModelScope.launch { repository.settings.setVibration(on) }
    }

    fun setKeepScreenOn(on: Boolean) {
        viewModelScope.launch { repository.settings.setKeepScreenOn(on) }
    }

    /** Debug only. */
    fun createSampleWorkout() {
        viewModelScope.launch { dev.charlesferreira.treino.debug.SampleData.createSuperiorB(repository) }
    }
}

/** "3 × 8–12", "1 × 30–40 min". */
fun target(item: WorkoutItem, exercise: Exercise?): String {
    val time = exercise?.kind == ExerciseKind.TIME
    val range = if (time) {
        rangeText(item.minutesMin?.let(PtBr::number), item.minutesMax?.let(PtBr::number))?.let { "$it min" }
    } else {
        rangeText(item.repsMin?.toString(), item.repsMax?.toString())
    }
    return if (range == null) "${item.sets} séries" else "${item.sets} × $range"
}

private fun rangeText(min: String?, max: String?): String? = when {
    min != null && max != null && min != max -> "$min–$max"
    min != null -> min
    max != null -> max
    else -> null
}

private val WEEKDAYS = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

/** "Quinta, 26 nov", the header's eyebrow. */
fun weekdayLabel(date: String): String =
    "${WEEKDAYS[LocalDate.parse(date).dayOfWeek.value - 1]}, ${PtBr.shortDate(date)}"
