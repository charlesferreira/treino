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

// Hoje
@Serializable data class EditSetRoute(val setId: String)
@Serializable data class SummaryRoute(val date: String)

// Evolução
@Serializable data class HistoryDayRoute(val date: String)
@Serializable data class ExerciseProgressRoute(val exerciseId: String)

// Treinos
@Serializable data class WorkoutEditRoute(val workoutId: String)
@Serializable data class WorkoutItemEditRoute(val itemId: String)
@Serializable data class ExercisePickerRoute(val workoutId: String)
@Serializable data class ExerciseEditRoute(val exerciseId: String)

// Perfil
@Serializable data object BackupRoute
@Serializable data object LoadReviewRoute

/** Debug builds only: the Phase 0 timer screen. */
@Serializable data object TimerSpikeRoute
