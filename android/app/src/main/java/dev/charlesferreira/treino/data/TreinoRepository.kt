package dev.charlesferreira.treino.data

import androidx.room.withTransaction
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.EquipmentGuess
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Ids
import dev.charlesferreira.treino.domain.NextSet
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.SetPrefill
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import dev.charlesferreira.treino.domain.WorkoutSequence
import dev.charlesferreira.treino.domain.backup.Catalog
import dev.charlesferreira.treino.domain.backup.TreinoData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A row of Evolução > Dias. */
data class DaySummary(val session: Session, val workoutName: String?, val setCount: Int, val timeCount: Int)

/** One day of an exercise's history, oldest first in [TreinoRepository.observeExerciseHistory]. */
data class ExerciseDay(val sessionId: String, val date: String, val sets: List<SetEntry>, val times: List<TimeEntry>)

/**
 * Everything the screens read and write. Dates are "YYYY-MM-DD" in the local zone; [today] and
 * [now] are injectable for tests. There is one session per day: every write for a date goes to
 * that day's session, created on the first write.
 */
class TreinoRepository(
    private val db: TreinoDatabase,
    val settings: SettingsStore,
    private val today: () -> String = { LocalDate.now().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val exercises = db.exerciseDao()
    private val workouts = db.workoutDao()
    private val sessions = db.sessionDao()
    private val entries = db.entryDao()

    fun today(): String = today.invoke()

    /* ------------------------------------------------------------------ Reads */

    /** False until the first use seeds the catalog or a backup is imported. */
    fun observeHasData(): Flow<Boolean> = exercises.observeAny().distinctUntilChanged()

    fun observeExercises(): Flow<List<Exercise>> = exercises.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeExercise(id: String): Flow<Exercise?> = exercises.observe(id).map { it?.toDomain() }

    suspend fun exercise(id: String): Exercise? = exercises.get(id)?.toDomain()

    fun observeWorkouts(): Flow<List<Workout>> = workouts.observeWorkouts().map { list -> list.map { it.toDomain() } }

    fun observeWorkout(id: String): Flow<Workout?> = workouts.observeWorkout(id).map { it?.toDomain() }

    fun observeItems(workoutId: String): Flow<List<WorkoutItem>> =
        workouts.observeItems(workoutId).map { list -> list.map { it.toDomain() } }

    /** Every item of every workout ("Trocar treino" tells a time workout by its items). */
    fun observeAllItems(): Flow<List<WorkoutItem>> =
        workouts.observeAllItems().map { list -> list.map { it.toDomain() } }

    /** The workout of [date]: the one chosen or logged that day, or the next in the rotation. */
    fun observeWorkoutFor(date: String = today()): Flow<Workout?> = combine(
        observeWorkouts(),
        sessions.observeAll(),
        entries.observeExecutedSessionIds(),
    ) { ws, ss, executed ->
        WorkoutSequence.suggestWorkout(ws, ss.map { it.toDomain() }, executed.toSet(), date)
    }.distinctUntilChanged()

    /** The rotation's next workout, ignoring a manual choice for [date] ("Trocar treino" shows it). */
    fun observeNextInSequence(date: String = today()): Flow<Workout?> = combine(
        observeWorkouts(),
        sessions.observeAll(),
        entries.observeExecutedSessionIds(),
    ) { ws, ss, executed ->
        WorkoutSequence.nextInSequence(ws, ss.map { it.toDomain() }, executed.toSet(), date)
    }.distinctUntilChanged()

    fun observeSession(date: String = today()): Flow<Session?> = sessions.observeByDate(date).map { it?.toDomain() }

    fun observeSets(sessionId: String): Flow<List<SetEntry>> =
        entries.observeSets(sessionId).map { list -> list.map { it.toDomain() } }

    fun observeTimes(sessionId: String): Flow<List<TimeEntry>> =
        entries.observeTimes(sessionId).map { list -> list.map { it.toDomain() } }

    /** Every day [exerciseId] was done, oldest first. */
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseDay>> = combine(
        entries.observeSetHistory(exerciseId),
        entries.observeTimeHistory(exerciseId),
    ) { sets, times ->
        val setsBySession = sets.groupBy { it.set.sessionId }
        val timesBySession = times.groupBy { it.time.sessionId }
        val dates = sets.associate { it.set.sessionId to it.date } + times.associate { it.time.sessionId to it.date }
        dates.entries.sortedBy { it.value }.map { (sid, date) ->
            ExerciseDay(
                sessionId = sid,
                date = date,
                sets = setsBySession[sid].orEmpty().map { it.set.toDomain() }.sortedBy { it.position },
                times = timesBySession[sid].orEmpty().map { it.time.toDomain() }.sortedBy { it.position },
            )
        }
    }

    /** Evolução > Dias: days with sets, times or a note, newest first. */
    fun observeDays(): Flow<List<DaySummary>> = sessions.observeDays().map { rows ->
        rows.map { DaySummary(it.session.toDomain(), it.workoutName, it.setCount, it.timeCount) }
    }

    /** Load and reps for the next set: the same set of the last session the exercise was done. */
    suspend fun prefill(exerciseId: String, position: Int, currentSessionId: String? = null): SetPrefill? =
        NextSet.prefill(
            exerciseId = exerciseId,
            position = position,
            sessions = sessions.getAll().map { it.toDomain() },
            sets = entries.getSetsOfExercise(exerciseId).map { it.toDomain() },
            currentSessionId = currentSessionId,
        )

    /* --------------------------------------------------------------- Day log */

    /** The session of [date], created (with the day's suggested workout) if missing. */
    suspend fun ensureSession(date: String = today()): Session = db.withTransaction {
        sessions.getByDate(date)?.toDomain() ?: run {
            val ws = workouts.getWorkouts().map { it.toDomain() }
            val all = sessions.getAll().map { it.toDomain() }
            val executed = WorkoutSequence.executedSessionIds(
                entries.getAllSets().map { it.toDomain() },
                entries.getAllTimes().map { it.toDomain() },
            )
            val workout = WorkoutSequence.suggestWorkout(ws, all, executed, date)
            Session(id = Ids.random(), date = date, workoutId = workout?.id).also { sessions.upsert(it.toEntity()) }
        }
    }

    /** "Começar treino": marks the start once. */
    suspend fun startSession(date: String = today(), at: Long = now()): Session = db.withTransaction {
        val s = ensureSession(date)
        if (s.startedAt != null) s else s.copy(startedAt = at).also { sessions.upsert(it.toEntity()) }
    }

    /** "Terminar": sets (or moves) the end. */
    suspend fun finishSession(date: String = today(), at: Long = now()): Session = db.withTransaction {
        ensureSession(date).copy(endedAt = at).also { sessions.upsert(it.toEntity()) }
    }

    /** "Trocar treino" for the day. */
    suspend fun chooseWorkout(date: String, workoutId: String): Session = db.withTransaction {
        ensureSession(date).copy(workoutId = workoutId).also { sessions.upsert(it.toEntity()) }
    }

    /** "Nota do dia". Blank clears it. */
    suspend fun setNote(date: String, note: String?): Session = db.withTransaction {
        ensureSession(date).copy(note = note?.trim()?.takeIf { it.isNotEmpty() }).also { sessions.upsert(it.toEntity()) }
    }

    suspend fun logSet(
        date: String,
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        loggedAt: Long = now(),
    ): SetEntry = db.withTransaction {
        val session = ensureSession(date)
        val position = entries.getSets(session.id, exerciseId).size
        SetEntry(
            id = Ids.random(), sessionId = session.id, exerciseId = exerciseId, position = position,
            weightKg = weightKg, reps = reps, loggedAt = loggedAt,
        ).also { entries.upsertSet(it.toEntity()) }
    }

    suspend fun updateSet(id: String, weightKg: Double, reps: Int) {
        val current = entries.getSet(id) ?: return
        entries.upsertSet(current.copy(weightKg = weightKg, reps = reps))
    }

    /** Removes the set and renumbers the ones after it. */
    suspend fun deleteSet(id: String) = db.withTransaction {
        val set = entries.getSet(id) ?: return@withTransaction
        entries.deleteSet(id)
        entries.getSets(set.sessionId, set.exerciseId).forEachIndexed { i, s ->
            if (s.position != i) entries.setSetPosition(s.id, i)
        }
    }

    suspend fun logTime(
        date: String,
        exerciseId: String,
        minutes: Double,
        km: Double? = null,
        place: String? = null,
        loggedAt: Long = now(),
    ): TimeEntry = db.withTransaction {
        val session = ensureSession(date)
        val position = entries.getTimes(session.id, exerciseId).size
        TimeEntry(
            id = Ids.random(), sessionId = session.id, exerciseId = exerciseId, position = position,
            minutes = minutes, km = km, place = place?.trim()?.takeIf { it.isNotEmpty() }, loggedAt = loggedAt,
        ).also { entries.upsertTime(it.toEntity()) }
    }

    suspend fun updateTime(id: String, minutes: Double, km: Double?, place: String?) {
        val current = entries.getTime(id) ?: return
        entries.upsertTime(current.copy(minutes = minutes, km = km, place = place?.trim()?.takeIf { it.isNotEmpty() }))
    }

    suspend fun deleteTime(id: String) = db.withTransaction {
        val time = entries.getTime(id) ?: return@withTransaction
        entries.deleteTime(id)
        entries.getTimes(time.sessionId, time.exerciseId).forEachIndexed { i, t ->
            if (t.position != i) entries.setTimePosition(t.id, i)
        }
    }

    /* --------------------------------------------------------------- Workouts */

    /** A new workout at the end of the rotation. */
    suspend fun createWorkout(name: String): Workout = db.withTransaction {
        Workout(id = Ids.random(), name = name.trim(), position = workouts.getWorkouts().size)
            .also { workouts.upsertWorkout(it.toEntity()) }
    }

    suspend fun renameWorkout(id: String, name: String) = workouts.rename(id, name.trim())

    /** Its items go with it; its days stay, without a workout. */
    suspend fun deleteWorkout(id: String) = db.withTransaction {
        workouts.deleteWorkout(id)
        workouts.getWorkouts().forEachIndexed { i, w -> if (w.position != i) workouts.setWorkoutPosition(w.id, i) }
    }

    /** The rotation in the new order. Ids not listed keep their relative order after the listed ones. */
    suspend fun reorderWorkouts(orderedIds: List<String>) = db.withTransaction {
        val current = workouts.getWorkouts()
        reorder(current.map { it.id }, orderedIds).forEachIndexed { i, id ->
            if (current.first { it.id == id }.position != i) workouts.setWorkoutPosition(id, i)
        }
    }

    suspend fun addItem(
        workoutId: String,
        exerciseId: String,
        sets: Int = 3,
        repsMin: Int? = null,
        repsMax: Int? = null,
        minutesMin: Double? = null,
        minutesMax: Double? = null,
        restSec: Int = 90,
        notes: String? = null,
    ): WorkoutItem = db.withTransaction {
        WorkoutItem(
            id = Ids.random(), workoutId = workoutId, exerciseId = exerciseId,
            position = workouts.getItems(workoutId).size, sets = sets, repsMin = repsMin, repsMax = repsMax,
            minutesMin = minutesMin, minutesMax = minutesMax, restSec = restSec, notes = notes,
        ).also { workouts.upsertItem(it.toEntity()) }
    }

    /** Saves the item's fields. Position and workout are kept as stored; use [reorderItems] to move. */
    suspend fun updateItem(item: WorkoutItem) = db.withTransaction {
        val stored = workouts.getItem(item.id) ?: return@withTransaction
        workouts.upsertItem(item.toEntity().copy(workoutId = stored.workoutId, position = stored.position))
    }

    suspend fun deleteItem(id: String) = db.withTransaction {
        val item = workouts.getItem(id) ?: return@withTransaction
        workouts.deleteItem(id)
        workouts.getItems(item.workoutId).forEachIndexed { i, it -> if (it.position != i) workouts.setItemPosition(it.id, i) }
    }

    suspend fun reorderItems(workoutId: String, orderedIds: List<String>) = db.withTransaction {
        val current = workouts.getItems(workoutId)
        reorder(current.map { it.id }, orderedIds).forEachIndexed { i, id ->
            if (current.first { it.id == id }.position != i) workouts.setItemPosition(id, i)
        }
    }

    /* -------------------------------------------------------------- Exercises */

    suspend fun createExercise(
        name: String,
        kind: ExerciseKind = ExerciseKind.LOAD,
        equipment: Equipment = EquipmentGuess.fromName(name, kind),
        group: String = "meus",
    ): Exercise = Exercise(
        id = Ids.random(), name = name.trim(), group = group, kind = kind, equipment = equipment,
        countsTwoDumbbells = equipment == Equipment.DUMBBELL,
    ).also { exercises.upsert(it.toEntity()) }

    suspend fun updateExercise(exercise: Exercise) = exercises.upsert(exercise.toEntity())

    /** False (and nothing removed) when the exercise has sets or times. Removes it from the workouts. */
    suspend fun deleteExercise(id: String): Boolean = db.withTransaction {
        if (exercises.historyCount(id) > 0) return@withTransaction false
        val affected = workouts.getAllItems().filter { it.exerciseId == id }.map { it.workoutId }.toSet()
        exercises.delete(id)
        affected.forEach { wid ->
            workouts.getItems(wid).forEachIndexed { i, it -> if (it.position != i) workouts.setItemPosition(it.id, i) }
        }
        true
    }

    /** First use: writes the built-in catalog. Exercises already there (same UUID) are left alone. */
    suspend fun seedCatalog() = exercises.insertMissing(Catalog.exercises.map { it.toEntity() })

    /* ----------------------------------------------------------------- Backup */

    /** Import: everything is replaced in one transaction, then the backup's settings are applied. */
    suspend fun replaceAll(data: TreinoData) {
        db.withTransaction {
            entries.deleteAllSets()
            entries.deleteAllTimes()
            sessions.deleteAll()
            workouts.deleteAllItems()
            workouts.deleteAllWorkouts()
            exercises.deleteAll()
            exercises.insertAll(data.exercises.map { it.toEntity() })
            workouts.insertWorkouts(data.workouts.map { it.toEntity() })
            workouts.insertItems(data.workoutItems.map { it.toEntity() })
            sessions.insertAll(data.sessions.map { it.toEntity() })
            entries.insertSets(data.setEntries.map { it.toEntity() })
            entries.insertTimes(data.timeEntries.map { it.toEntity() })
        }
        settings.apply(data.settings)
    }

    /** Export: a consistent read of every table plus the settings. */
    suspend fun snapshot(): TreinoData {
        val data = db.withTransaction {
            TreinoData(
                exercises = exercises.getAll().map { it.toDomain() },
                workouts = workouts.getWorkouts().map { it.toDomain() },
                workoutItems = workouts.getAllItems().map { it.toDomain() },
                sessions = sessions.getAll().map { it.toDomain() },
                setEntries = entries.getAllSets().map { it.toDomain() },
                timeEntries = entries.getAllTimes().map { it.toDomain() },
            )
        }
        return data.copy(settings = settings.toBackup())
    }

    private fun reorder(current: List<String>, ordered: List<String>): List<String> {
        val known = ordered.filter { it in current }.distinct()
        return known + current.filterNot { it in known }
    }
}
