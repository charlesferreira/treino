package dev.charlesferreira.treino.ui.nav

import kotlinx.serialization.Serializable

/* Type-safe Navigation Compose routes. Rest, set editing sheets and "Trocar treino" are overlays
 * of Hoje, not routes. */

@Serializable data object OnboardingRoute

// Tabs
@Serializable data object TodayRoute
@Serializable data object EvolutionRoute
@Serializable data object WorkoutsRoute

@Serializable data object ProfileRoute

// Hoje (switching the workout, the note and editing a set are sheets of Hoje)
@Serializable data class SummaryRoute(val date: String)

// Evolução
@Serializable data class HistoryDayRoute(val date: String)
@Serializable data class ExerciseProgressRoute(val exerciseId: String)

// Treinos (the item and the equipment are sheets, not routes)
@Serializable data class WorkoutEditRoute(val workoutId: String)

/** "Adicionar exercício" to [workoutId]; null is "Todos os exercícios". */
@Serializable data class ExercisePickerRoute(val workoutId: String? = null)

// Perfil
@Serializable data object BackupRoute
@Serializable data object LoadReviewRoute

/** Debug builds only: the Phase 0 timer screen. */
@Serializable data object TimerSpikeRoute
