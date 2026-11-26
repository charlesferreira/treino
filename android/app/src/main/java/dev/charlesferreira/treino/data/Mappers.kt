package dev.charlesferreira.treino.data

import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem

fun ExerciseEntity.toDomain() = Exercise(
    id = id, legacyId = legacyId, name = name, group = group, kind = kind, equipment = equipment,
    countsTwoDumbbells = countsTwoDumbbells, stepKg = stepKg, plateKg = plateKg, aliases = aliases, notes = notes,
)

fun Exercise.toEntity() = ExerciseEntity(
    id = id, legacyId = legacyId, name = name, group = group, kind = kind, equipment = equipment,
    countsTwoDumbbells = countsTwoDumbbells, stepKg = stepKg, plateKg = plateKg, aliases = aliases, notes = notes,
)

fun WorkoutEntity.toDomain() = Workout(id = id, legacyId = legacyId, name = name, position = position)

fun Workout.toEntity() = WorkoutEntity(id = id, legacyId = legacyId, name = name, position = position)

fun WorkoutItemEntity.toDomain() = WorkoutItem(
    id = id, workoutId = workoutId, exerciseId = exerciseId, position = position, sets = sets,
    repsMin = repsMin, repsMax = repsMax, minutesMin = minutesMin, minutesMax = minutesMax,
    restSec = restSec, notes = notes,
)

fun WorkoutItem.toEntity() = WorkoutItemEntity(
    id = id, workoutId = workoutId, exerciseId = exerciseId, position = position, sets = sets,
    repsMin = repsMin, repsMax = repsMax, minutesMin = minutesMin, minutesMax = minutesMax,
    restSec = restSec, notes = notes,
)

fun SessionEntity.toDomain() = Session(
    id = id, date = date, workoutId = workoutId, note = note, startedAt = startedAt, endedAt = endedAt,
)

fun Session.toEntity() = SessionEntity(
    id = id, date = date, workoutId = workoutId, note = note, startedAt = startedAt, endedAt = endedAt,
)

fun SetEntryEntity.toDomain() = SetEntry(
    id = id, sessionId = sessionId, exerciseId = exerciseId, position = position,
    weightKg = weightKg, reps = reps, loggedAt = loggedAt,
)

fun SetEntry.toEntity() = SetEntryEntity(
    id = id, sessionId = sessionId, exerciseId = exerciseId, position = position,
    weightKg = weightKg, reps = reps, loggedAt = loggedAt,
)

fun TimeEntryEntity.toDomain() = TimeEntry(
    id = id, sessionId = sessionId, exerciseId = exerciseId, position = position,
    minutes = minutes, km = km, place = place, loggedAt = loggedAt,
)

fun TimeEntry.toEntity() = TimeEntryEntity(
    id = id, sessionId = sessionId, exerciseId = exerciseId, position = position,
    minutes = minutes, km = km, place = place, loggedAt = loggedAt,
)
