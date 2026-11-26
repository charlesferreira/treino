package dev.charlesferreira.treino.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.charlesferreira.treino.ui.theme.TreinoColors

/*
 * Screen transitions:
 * - between tabs, the screens slide sideways in the tab bar's order (Hoje → Evolução comes from
 *   the right, Treinos → Hoje from the left);
 * - a pushed screen (Perfil, a day, a workout...) comes in from the right over the current one,
 *   which drifts a little to the left; going back is the reverse, and follows the finger in the
 *   predictive back gesture.
 */

private const val DURATION = 320
private val easing = FastOutSlowInEasing

/** How far the screen underneath drifts while another is pushed over it. */
private const val UNDER_SHIFT = 0.25f

/** Position in the tab bar of a tab's root screen, or null for any other screen. */
private fun NavDestination.tabIndex(): Int? = when {
    hasRoute<TodayRoute>() -> Tab.TODAY.ordinal
    hasRoute<EvolutionRoute>() -> Tab.EVOLUTION.ordinal
    hasRoute<WorkoutsRoute>() -> Tab.WORKOUTS.ordinal
    else -> null
}

/** Exercise progress keeps the Evolução tab: leaving it by the tab bar is also a tab switch. */
private fun NavDestination.tabSwitchIndex(): Int? =
    tabIndex() ?: if (hasRoute<ExerciseProgressRoute>()) Tab.EVOLUTION.ordinal else null

/** +1 when moving right in the tab bar, −1 left, null when this is not a switch between tabs. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int? {
    val to = targetState.destination.tabIndex() ?: return null
    val from = initialState.destination.tabSwitchIndex() ?: return null
    return when {
        to > from -> 1
        to < from -> -1
        else -> null
    }
}

private fun <T> spec() = tween<T>(DURATION, easing = easing)

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(): EnterTransition =
    when (val dir = tabDirection()) {
        null -> slideInHorizontally(spec()) { it }
        else -> slideInHorizontally(spec()) { it * dir }
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(): ExitTransition =
    when (val dir = tabDirection()) {
        null -> slideOutHorizontally(spec()) { -(it * UNDER_SHIFT).toInt() } + fadeOut(spec(), targetAlpha = 0.6f)
        else -> slideOutHorizontally(spec()) { -it * dir }
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopEnter(): EnterTransition =
    when (val dir = tabDirection()) {
        null -> slideInHorizontally(spec()) { -(it * UNDER_SHIFT).toInt() } + fadeIn(spec(), initialAlpha = 0.6f)
        else -> slideInHorizontally(spec()) { it * dir }
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopExit(): ExitTransition =
    when (val dir = tabDirection()) {
        null -> slideOutHorizontally(spec()) { it }
        else -> slideOutHorizontally(spec()) { -it * dir }
    }

/**
 * A destination drawn on the app background: while sliding, one screen passes over another, so
 * each must be opaque.
 */
inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        Box(Modifier.fillMaxSize().background(TreinoColors.Background)) { content(entry) }
    }
}
