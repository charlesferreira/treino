package dev.charlesferreira.treino.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observe(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM exercise)")
    fun observeAny(): Flow<Boolean>

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query(
        "SELECT (SELECT COUNT(*) FROM set_entry WHERE exercise_id = :id) + " +
            "(SELECT COUNT(*) FROM time_entry WHERE exercise_id = :id)",
    )
    suspend fun historyCount(id: String): Int

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM exercise")
    suspend fun deleteAll()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout ORDER BY position, name")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout ORDER BY position, name")
    suspend fun getWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workout WHERE id = :id")
    fun observeWorkout(id: String): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkout(id: String): WorkoutEntity?

    @Upsert
    suspend fun upsertWorkout(workout: WorkoutEntity)

    @Insert
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>)

    @Query("UPDATE workout SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE workout SET position = :position WHERE id = :id")
    suspend fun setWorkoutPosition(id: String, position: Int)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("DELETE FROM workout")
    suspend fun deleteAllWorkouts()

    @Query("SELECT * FROM workout_item WHERE workout_id = :workoutId ORDER BY position")
    fun observeItems(workoutId: String): Flow<List<WorkoutItemEntity>>

    @Query("SELECT * FROM workout_item WHERE workout_id = :workoutId ORDER BY position")
    suspend fun getItems(workoutId: String): List<WorkoutItemEntity>

    @Query("SELECT * FROM workout_item ORDER BY workout_id, position")
    suspend fun getAllItems(): List<WorkoutItemEntity>

    @Query("SELECT * FROM workout_item ORDER BY workout_id, position")
    fun observeAllItems(): Flow<List<WorkoutItemEntity>>

    @Query("SELECT * FROM workout_item WHERE id = :id")
    suspend fun getItem(id: String): WorkoutItemEntity?

    @Upsert
    suspend fun upsertItem(item: WorkoutItemEntity)

    @Insert
    suspend fun insertItems(items: List<WorkoutItemEntity>)

    @Query("UPDATE workout_item SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: String, position: Int)

    @Query("DELETE FROM workout_item WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM workout_item")
    suspend fun deleteAllItems()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM session ORDER BY date, started_at")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session ORDER BY date, started_at")
    suspend fun getAll(): List<SessionEntity>

    /** One session per day in practice; if there were two, the latest started wins. */
    @Query("SELECT * FROM session WHERE date = :date ORDER BY started_at DESC, id LIMIT 1")
    fun observeByDate(date: String): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE date = :date ORDER BY started_at DESC, id LIMIT 1")
    suspend fun getByDate(date: String): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)

    /** Days with something in them (sets, times or a note), newest first. */
    @Query(
        """
        SELECT s.*, w.name AS workout_name,
            (SELECT COUNT(*) FROM set_entry e WHERE e.session_id = s.id) AS set_count,
            (SELECT COUNT(*) FROM time_entry t WHERE t.session_id = s.id) AS time_count
        FROM session s LEFT JOIN workout w ON w.id = s.workout_id
        WHERE set_count > 0 OR time_count > 0 OR s.note IS NOT NULL
        ORDER BY s.date DESC, s.started_at DESC
        """,
    )
    fun observeDays(): Flow<List<DayRow>>

    @Query("SELECT id FROM session WHERE date = :date")
    suspend fun idsByDate(date: String): List<String>

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM session")
    suspend fun deleteAll()
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM set_entry WHERE session_id = :sessionId ORDER BY exercise_id, position")
    fun observeSets(sessionId: String): Flow<List<SetEntryEntity>>

    @Query("SELECT * FROM set_entry WHERE session_id = :sessionId AND exercise_id = :exerciseId ORDER BY position")
    suspend fun getSets(sessionId: String, exerciseId: String): List<SetEntryEntity>

    @Query("SELECT * FROM set_entry WHERE exercise_id = :exerciseId")
    suspend fun getSetsOfExercise(exerciseId: String): List<SetEntryEntity>

    @Query("SELECT * FROM set_entry ORDER BY session_id, exercise_id, position")
    suspend fun getAllSets(): List<SetEntryEntity>

    @Query("SELECT * FROM set_entry ORDER BY session_id, exercise_id, position")
    fun observeAllSets(): Flow<List<SetEntryEntity>>

    @Query("SELECT * FROM set_entry WHERE id = :id")
    suspend fun getSet(id: String): SetEntryEntity?

    @Upsert
    suspend fun upsertSet(set: SetEntryEntity)

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>)

    @Query("UPDATE set_entry SET position = :position WHERE id = :id")
    suspend fun setSetPosition(id: String, position: Int)

    @Query("DELETE FROM set_entry WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("DELETE FROM set_entry WHERE session_id = :sessionId")
    suspend fun deleteSetsOfSession(sessionId: String)

    @Query("DELETE FROM set_entry")
    suspend fun deleteAllSets()

    @Query(
        """
        SELECT e.*, s.date AS date FROM set_entry e JOIN session s ON s.id = e.session_id
        WHERE e.exercise_id = :exerciseId ORDER BY s.date, s.started_at, e.position
        """,
    )
    fun observeSetHistory(exerciseId: String): Flow<List<SetWithDate>>

    @Query("SELECT * FROM time_entry WHERE session_id = :sessionId ORDER BY exercise_id, position")
    fun observeTimes(sessionId: String): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entry WHERE session_id = :sessionId AND exercise_id = :exerciseId ORDER BY position")
    suspend fun getTimes(sessionId: String, exerciseId: String): List<TimeEntryEntity>

    @Query("SELECT * FROM time_entry ORDER BY session_id, exercise_id, position")
    suspend fun getAllTimes(): List<TimeEntryEntity>

    @Query("SELECT * FROM time_entry ORDER BY session_id, exercise_id, position")
    fun observeAllTimes(): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entry WHERE id = :id")
    suspend fun getTime(id: String): TimeEntryEntity?

    @Upsert
    suspend fun upsertTime(time: TimeEntryEntity)

    @Insert
    suspend fun insertTimes(times: List<TimeEntryEntity>)

    @Query("UPDATE time_entry SET position = :position WHERE id = :id")
    suspend fun setTimePosition(id: String, position: Int)

    @Query("DELETE FROM time_entry WHERE id = :id")
    suspend fun deleteTime(id: String)

    @Query("DELETE FROM time_entry WHERE session_id = :sessionId")
    suspend fun deleteTimesOfSession(sessionId: String)

    @Query("DELETE FROM time_entry")
    suspend fun deleteAllTimes()

    @Query(
        """
        SELECT t.*, s.date AS date FROM time_entry t JOIN session s ON s.id = t.session_id
        WHERE t.exercise_id = :exerciseId ORDER BY s.date, s.started_at, t.position
        """,
    )
    fun observeTimeHistory(exerciseId: String): Flow<List<TimeWithDate>>

    /** Sessions that count as executed for the rotation: at least one set or time. */
    @Query("SELECT session_id FROM set_entry UNION SELECT session_id FROM time_entry")
    fun observeExecutedSessionIds(): Flow<List<String>>
}
