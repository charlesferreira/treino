package dev.charlesferreira.treino.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.charlesferreira.treino.BuildConfig
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.debug.TimerSpikeScreen
import dev.charlesferreira.treino.ui.evolution.EvolutionScreen
import dev.charlesferreira.treino.ui.evolution.ExerciseProgressScreen
import dev.charlesferreira.treino.ui.evolution.HistoryDayScreen
import dev.charlesferreira.treino.ui.profile.BackupScreen
import dev.charlesferreira.treino.ui.profile.LoadReviewScreen
import dev.charlesferreira.treino.ui.profile.OnboardingScreen
import dev.charlesferreira.treino.ui.profile.ProfileScreen
import dev.charlesferreira.treino.ui.screens.RootViewModel
import dev.charlesferreira.treino.ui.screens.SubScreenHeader
import dev.charlesferreira.treino.ui.workouts.ExercisePickerScreen
import dev.charlesferreira.treino.ui.workouts.WorkoutEditScreen
import dev.charlesferreira.treino.ui.workouts.WorkoutsScreen
import dev.charlesferreira.treino.ui.today.SummaryScreen
import dev.charlesferreira.treino.ui.today.TodayScreen
import androidx.compose.runtime.CompositionLocalProvider
import dev.charlesferreira.treino.ui.theme.TreinoColors

/** Waits for the first database read, then starts at Hoje, or at the onboarding when empty. */
@Composable
fun TreinoAppRoot() {
    val container = appContainer()
    val vm = viewModel { RootViewModel(container.repository) }
    val hasData by vm.hasData.collectAsStateWithLifecycle()
    // The start is decided once; later changes (seeding, import) navigate explicitly.
    var start by remember { mutableStateOf<Any?>(null) }
    if (start == null) hasData?.let { start = if (it) TodayRoute else OnboardingRoute }
    Box(Modifier.fillMaxSize().background(TreinoColors.Background)) {
        start?.let { TreinoNavHost(it) }
    }
}

@Composable
private fun TreinoNavHost(start: Any) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val tab = when {
        destination == null -> null
        destination.hasRoute<TodayRoute>() -> Tab.TODAY
        destination.hasRoute<EvolutionRoute>() -> Tab.EVOLUTION
        // `b-exercise` keeps the tab bar, as in the design.
        destination.hasRoute<ExerciseProgressRoute>() -> Tab.EVOLUTION
        destination.hasRoute<WorkoutsRoute>() -> Tab.WORKOUTS
        else -> null
    }
    val back: () -> Unit = { nav.popBackStack() }
    val overlay = remember { OverlayHost() }
    val profile: () -> Unit = { nav.navigate(ProfileRoute) { launchSingleTop = true } }

    CompositionLocalProvider(LocalOverlayHost provides overlay) {
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onCreateWorkout = {
                        nav.navigate(TodayRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                        nav.selectTab(Tab.WORKOUTS)
                    },
                    onBackup = { nav.navigate(BackupRoute(firstUse = true)) },
                )
            }
            composable<TodayRoute> {
                TodayScreen(
                    onProfile = profile,
                    onCreateWorkout = { nav.selectTab(Tab.WORKOUTS) },
                    onManageWorkouts = { nav.selectTab(Tab.WORKOUTS) },
                    onSummary = { date -> nav.navigate(SummaryRoute(date)) { launchSingleTop = true } },
                )
            }
            composable<EvolutionRoute> {
                EvolutionScreen(
                    onProfile = profile,
                    onDay = { date -> nav.navigate(HistoryDayRoute(date)) { launchSingleTop = true } },
                    onExercise = { id -> nav.navigate(ExerciseProgressRoute(id)) { launchSingleTop = true } },
                )
            }
            composable<WorkoutsRoute> {
                WorkoutsScreen(
                    onProfile = profile,
                    onWorkout = { id -> nav.navigate(WorkoutEditRoute(id)) { launchSingleTop = true } },
                    onExercises = { nav.navigate(ExercisePickerRoute(null)) { launchSingleTop = true } },
                )
            }
            composable<ProfileRoute> {
                ProfileScreen(
                    onBack = back,
                    onLoadReview = { nav.navigate(LoadReviewRoute) },
                    onBackup = { nav.navigate(BackupRoute()) },
                    onTimerSpike = { nav.navigate(TimerSpikeRoute) },
                )
            }

            composable<SummaryRoute> { SummaryScreen(it.toRoute<SummaryRoute>().date, onClose = back) }
            composable<HistoryDayRoute> { HistoryDayScreen(it.toRoute<HistoryDayRoute>().date, onBack = back) }
            composable<ExerciseProgressRoute> { ExerciseProgressScreen(it.toRoute<ExerciseProgressRoute>().exerciseId, onBack = back) }
            composable<WorkoutEditRoute> { entry ->
                val workoutId = entry.toRoute<WorkoutEditRoute>().workoutId
                // The picker leaves the new item here; its sheet opens on the way back.
                val openItem by entry.savedStateHandle.getStateFlow<String?>(OPEN_ITEM, null).collectAsStateWithLifecycle()
                WorkoutEditScreen(
                    workoutId = workoutId,
                    openItem = openItem,
                    onOpenItemShown = { entry.savedStateHandle[OPEN_ITEM] = null },
                    onBack = back,
                    onAddExercise = { nav.navigate(ExercisePickerRoute(workoutId)) { launchSingleTop = true } },
                    onOpenWorkout = { id ->
                        nav.navigate(WorkoutEditRoute(id)) { popUpTo<WorkoutEditRoute> { inclusive = true } }
                    },
                )
            }
            composable<ExercisePickerRoute> { entry ->
                ExercisePickerScreen(
                    workoutId = entry.toRoute<ExercisePickerRoute>().workoutId,
                    onBack = back,
                    onPicked = { itemId ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(OPEN_ITEM, itemId)
                        nav.popBackStack()
                    },
                )
            }
            composable<BackupRoute> {
                BackupScreen(
                    firstUse = it.toRoute<BackupRoute>().firstUse,
                    onBack = back,
                    // Everything was replaced: start over at Hoje (from the first use too), then
                    // show the days that came in.
                    onImported = {
                        nav.navigate(TodayRoute) { popUpTo(nav.graph.id) { inclusive = true } }
                        nav.selectTab(Tab.EVOLUTION)
                    },
                )
            }
            composable<LoadReviewRoute> { LoadReviewScreen(onBack = back) }
            if (BuildConfig.DEBUG) {
                composable<TimerSpikeRoute> {
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp)) {
                        SubScreenHeader("Timer (spike)", back)
                        TimerSpikeScreen()
                    }
                }
            }
        }
        if (tab != null) {
            TabBar(
                selected = tab,
                onSelect = { t ->
                    // Evolução again from the exercise's progression goes back to the tab's root.
                    if (t == Tab.EVOLUTION && destination?.hasRoute<ExerciseProgressRoute>() == true) {
                        nav.popBackStack<EvolutionRoute>(inclusive = false)
                    } else {
                        nav.selectTab(t)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        // The rest, then the sheets of the current screen, over the tab bar.
        overlay.floating?.invoke()
        overlay.content?.invoke()
    }
    }
}

private const val OPEN_ITEM = "openItem"

/** Tabs keep their own state: Hoje is the root, the others are saved and restored. */
private fun NavHostController.selectTab(tab: Tab) {
    val route: Any = when (tab) {
        Tab.TODAY -> TodayRoute
        Tab.EVOLUTION -> EvolutionRoute
        Tab.WORKOUTS -> WorkoutsRoute
    }
    navigate(route) {
        popUpTo<TodayRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
