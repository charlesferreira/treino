package dev.charlesferreira.treino.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.WorkoutItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutsUi(val profileName: String, val rows: List<SequenceRowUi>, val exerciseCount: Int)

/** Treinos: the rotation, "próximo", and the count of "Todos os exercícios". */
class WorkoutsViewModel(private val repository: TreinoRepository) : ViewModel() {
    val state: StateFlow<WorkoutsUi?> = combine(
        repository.observeWorkouts(),
        repository.observeAllItems(),
        repository.observeExercises(),
        repository.observeUpcomingWorkout(),
        repository.settings.settings,
    ) { ws, items, exercises, upcoming, settings ->
        WorkoutsUi(
            profileName = settings.profileName,
            rows = buildSequence(ws, items, exercises.associateBy { it.id }, upcoming?.id),
            exerciseCount = exercises.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, then: (String) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { then(repository.createWorkout(clean).id) }
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch { repository.reorderWorkouts(ids) }
    }
}

/** Editar treino, its item sheet and the equipment sheet opened from it. */
class WorkoutEditViewModel(private val repository: TreinoRepository, val workoutId: String) : ViewModel() {
    /** False once the workout is gone (deleted here): the screen goes back. */
    val exists = MutableStateFlow(true)

    val state: StateFlow<WorkoutEditUi?> = combine(
        repository.observeWorkout(workoutId),
        repository.observeItems(workoutId),
        repository.observeExercises(),
    ) { w, items, exercises ->
        if (w == null) exists.value = false
        w?.let { buildWorkoutEdit(it, items, exercises.associateBy { e -> e.id }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repository.renameWorkout(workoutId, clean) }
    }

    fun duplicate(then: (String) -> Unit) {
        viewModelScope.launch { repository.duplicateWorkout(workoutId)?.let { then(it.id) } }
    }

    fun delete(then: () -> Unit) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
            then()
        }
    }

    fun reorderItems(ids: List<String>) {
        viewModelScope.launch { repository.reorderItems(workoutId, ids) }
    }

    /** The item sheet's "Salvar": the item, and the exercise when its kind changed. */
    fun saveItem(item: WorkoutItem, draft: ItemDraft) {
        val updated = draft.applyTo(item) ?: return
        viewModelScope.launch {
            repository.updateItem(updated)
            val ex = repository.exercise(item.exerciseId)
            if (ex != null && ex.kind != draft.kind) repository.updateExercise(ex.copy(kind = draft.kind))
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch { repository.deleteItem(id) }
    }

    /** The equipment sheet's "Salvar" from an item: the exercise and the item's draft. */
    fun saveEquipment(exercise: Exercise, item: WorkoutItem?, draft: ItemDraft?) {
        viewModelScope.launch {
            val current = repository.exercise(exercise.id) ?: return@launch
            repository.updateExercise(exercise.copy(kind = draft?.kind ?: current.kind))
            if (item != null && draft != null) draft.applyTo(item)?.let { repository.updateItem(it) }
        }
    }
}

/**
 * Adicionar exercício ([workoutId] set) or Todos os exercícios (null): the search, "Criar", and
 * adding the pick to the workout with the defaults of [newItemDefaults].
 */
class ExercisePickerViewModel(private val repository: TreinoRepository, val workoutId: String?) : ViewModel() {
    val query = MutableStateFlow("")

    val exercises: StateFlow<List<Exercise>> = repository.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<PickerUi?> = combine(repository.observeExercises(), query) { all, q ->
        buildPicker(all, q, browse = workoutId == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds [exerciseId] to the workout; [then] gets the new item's id. */
    fun pick(exerciseId: String, then: (String) -> Unit) {
        val wid = workoutId ?: return
        viewModelScope.launch {
            val ex = repository.exercise(exerciseId) ?: return@launch
            val d = newItemDefaults(ex)
            val item = repository.addItem(
                workoutId = wid, exerciseId = ex.id, sets = d.sets, repsMin = d.repsMin, repsMax = d.repsMax,
                minutesMin = d.minutesMin, minutesMax = d.minutesMax, restSec = d.restSec,
            )
            then(item.id)
        }
    }

    /** "Criar “…”": a new load exercise in "Meus exercícios", its equipment guessed from the name. */
    fun create(name: String, then: (String) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val existing = repository.observeExercises().first()
                .firstOrNull { dev.charlesferreira.treino.domain.normalizeText(it.name) == dev.charlesferreira.treino.domain.normalizeText(clean) }
            then((existing ?: repository.createExercise(clean)).id)
        }
    }

    fun saveExercise(exercise: Exercise) {
        viewModelScope.launch { repository.updateExercise(exercise) }
    }
}
