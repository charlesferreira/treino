package dev.charlesferreira.treino.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.ExerciseKind

/*
 * Room tables, one per class of the domain model (`:domain` Model.kt). Ids are UUID strings.
 *
 * Foreign keys:
 * - workout_item → workout CASCADE, → exercise CASCADE (removing an exercise takes it out of the
 *   workouts; the repository refuses to remove one that has history);
 * - session → workout SET NULL (a deleted workout keeps its days);
 * - set_entry / time_entry → session CASCADE, → exercise NO ACTION (history never vanishes with
 *   an exercise: the delete fails instead).
 */

@Entity(
    tableName = "exercise",
    indices = [Index("legacy_id"), Index("name")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "legacy_id") val legacyId: String?,
    val name: String,
    @ColumnInfo(name = "group_name") val group: String,
    val kind: ExerciseKind,
    val equipment: Equipment,
    @ColumnInfo(name = "counts_two_dumbbells") val countsTwoDumbbells: Boolean,
    @ColumnInfo(name = "step_kg") val stepKg: Double?,
    @ColumnInfo(name = "plate_kg") val plateKg: Double?,
    val aliases: List<String>,
    val notes: String?,
)

@Entity(tableName = "workout", indices = [Index("position")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "legacy_id") val legacyId: String?,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "workout_item",
    foreignKeys = [
        ForeignKey(WorkoutEntity::class, ["id"], ["workout_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("workout_id", "position"), Index("exercise_id")],
)
data class WorkoutItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    val sets: Int,
    @ColumnInfo(name = "reps_min") val repsMin: Int?,
    @ColumnInfo(name = "reps_max") val repsMax: Int?,
    @ColumnInfo(name = "minutes_min") val minutesMin: Double?,
    @ColumnInfo(name = "minutes_max") val minutesMax: Double?,
    @ColumnInfo(name = "rest_sec") val restSec: Int,
    val notes: String?,
)

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(WorkoutEntity::class, ["id"], ["workout_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("date"), Index("workout_id")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    /** "YYYY-MM-DD", local time zone. */
    val date: String,
    @ColumnInfo(name = "workout_id") val workoutId: String?,
    val note: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long?,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
)

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("session_id", "exercise_id", "position"), Index("exercise_id")],
)
data class SetEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    @ColumnInfo(name = "logged_at") val loggedAt: Long?,
)

@Entity(
    tableName = "time_entry",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("session_id", "exercise_id", "position"), Index("exercise_id")],
)
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    val minutes: Double,
    val km: Double?,
    val place: String?,
    @ColumnInfo(name = "logged_at") val loggedAt: Long?,
)

/** A row of the days list: the session, its workout's name and how much was logged. */
data class DayRow(
    @Embedded val session: SessionEntity,
    @ColumnInfo(name = "workout_name") val workoutName: String?,
    @ColumnInfo(name = "set_count") val setCount: Int,
    @ColumnInfo(name = "time_count") val timeCount: Int,
)

data class SetWithDate(@Embedded val set: SetEntryEntity, val date: String)

data class TimeWithDate(@Embedded val time: TimeEntryEntity, val date: String)
