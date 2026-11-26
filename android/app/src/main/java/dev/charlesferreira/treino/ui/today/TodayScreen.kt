package dev.charlesferreira.treino.ui.today

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.domain.SetEntry
import kotlinx.coroutines.delay

/**
 * The Hoje tab: [StartScreen] until the workout starts, then [WorkoutScreen]. Sheets (switch the
 * workout, note, edit a logged set) are overlays of this screen, not routes.
 */
@Composable
fun TodayScreen(
    onProfile: () -> Unit,
    onCreateWorkout: () -> Unit,
    onManageWorkouts: () -> Unit,
    onSummary: (String) -> Unit,
) {
    val container = appContainer()
    val context = LocalContext.current
    val vm = viewModel { TodayViewModel(container.repository, container.workoutTimer, context.applicationContext) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val edits by vm.edits.collectAsStateWithLifecycle()
    val timeDrafts by vm.timeDrafts.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle()
    val restExpanded by vm.restExpanded.collectAsStateWithLifecycle()
    // Back from the background: the rest is read again from the store.
    LifecycleResumeEffect(vm) {
        vm.reloadRest()
        onPauseOrDispose { }
    }
    // The rest and the between-sets notification need the permission; asked when the workout starts.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ui = state ?: return

    if (ui.started && ui.workout != null) {
        val running = !ui.ended
        KeepScreenOn(running && ui.keepScreenOn)
        val restView = if (running) restView(rest, restExpanded, System.currentTimeMillis()) else RestView.NONE
        val restActions = remember(vm) {
            object : RestActions {
                override fun onAdjust(deltaMs: Long) = vm.adjustRest(deltaMs)
                override fun onSkip() = vm.skipRest()
                override fun onMinimize() = vm.expandRest(false)
                override fun onExpand() = vm.expandRest(true)
                override fun onDoneSeen() = vm.restDoneSeen()
            }
        }
        RestLayer(restView, rest, restActions)
        val clock = workoutClock(ui.session?.startedAt, ui.session?.endedAt)
        val actions = remember(vm) {
            object : WorkoutActions {
                override fun onSwitch() = vm.openSheet(TodaySheet.Switch)
                override fun onProfile() = onProfile()
                override fun onSelect(item: ItemUi) = vm.selectItem(item.item.id)
                override fun onStepWeight(item: ItemUi, up: Boolean) = vm.stepWeight(item, up)
                override fun onStepReps(item: ItemUi, up: Boolean) = vm.stepReps(item, up)
                override fun onTypeWeight(item: ItemUi, kg: Double) = vm.typeWeight(item, kg)
                override fun onTypeReps(item: ItemUi, reps: Int) = vm.typeReps(item, reps)
                override fun onLogSet(item: ItemUi, draft: SetDraft) = vm.onSetLogged(item, draft.weightKg, draft.reps)
                override fun onEditSet(set: SetEntry) = vm.openSheet(TodaySheet.EditSet(set.id))
                override fun onEditTime(item: ItemUi, draft: (TimeDraft) -> TimeDraft) = vm.editTime(item, draft)
                override fun onLogTime(item: ItemUi, draft: TimeDraft) = vm.onTimeLogged(item, draft)
                override fun onEditLoggedTime(id: String) = vm.openSheet(TodaySheet.EditTime(id))
                override fun onFinish() = vm.finish { onSummary(vm.date) }
            }
        }
        WorkoutScreen(
            ui = ui,
            clock = clock,
            setDraft = { item -> edits[item.draftKey()] ?: item.defaultSetDraft(ui.session?.id) },
            timeDraft = { item -> timeDrafts[item.draftKey()] ?: item.defaultTimeDraft() },
            actions = actions,
            bottomExtra = if (restView == RestView.PILL || restView == RestView.DONE) RestPillClearance else 0.dp,
        )
    } else {
        StartScreen(
            ui = ui,
            onSwitch = { vm.openSheet(TodaySheet.Switch) },
            onProfile = onProfile,
            onNote = { vm.openSheet(TodaySheet.Note) },
            onStart = {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.start()
            },
            onCreateWorkout = onCreateWorkout,
        )
    }

    when (val s = sheet) {
        TodaySheet.Switch -> SwitchWorkoutSheet(
            options = ui.switchOptions,
            onChoose = vm::chooseWorkout,
            onManage = {
                vm.closeSheet()
                onManageWorkouts()
            },
            onDismiss = vm::closeSheet,
        )
        TodaySheet.Note -> NoteSheet(ui.session?.note, onSave = vm::saveNote, onDismiss = vm::closeSheet)
        is TodaySheet.EditSet -> {
            val found = ui.items.firstNotNullOfOrNull { item -> item.sets.firstOrNull { it.id == s.setId }?.let { item to it } }
            if (found == null) {
                LaunchedEffect(s) { vm.closeSheet() }
            } else {
                EditSetSheet(
                    exercise = found.first.exercise,
                    set = found.second,
                    onSave = { kg, reps -> vm.updateSet(s.setId, kg, reps) },
                    onDelete = { vm.deleteSet(s.setId) },
                    onDismiss = vm::closeSheet,
                )
            }
        }
        is TodaySheet.EditTime -> {
            val found = ui.items.firstNotNullOfOrNull { item -> item.times.firstOrNull { it.id == s.timeId }?.let { item to it } }
            if (found == null) {
                LaunchedEffect(s) { vm.closeSheet() }
            } else {
                EditTimeSheet(
                    exercise = found.first.exercise,
                    time = found.second,
                    onSave = { minutes -> vm.updateTime(s.timeId, minutes, found.second.km, found.second.place) },
                    onDelete = { vm.deleteTime(s.timeId) },
                    onDismiss = vm::closeSheet,
                )
            }
        }
        null -> Unit
    }
}

/**
 * The workout clock: from the start to now (or to the end), ticking every second only to redraw.
 * Nothing is counted by the screen.
 */
@Composable
fun workoutClock(startedAt: Long?, endedAt: Long?): String {
    if (startedAt == null) return clockLabel(0)
    if (endedAt != null) return clockLabel(endedAt - startedAt)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    return clockLabel(now - startedAt)
}

/** "Tela sempre ligada no treino": FLAG_KEEP_SCREEN_ON while [on]. */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val context = LocalContext.current
    DisposableEffect(on) {
        val window = context.findActivity()?.window
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (on) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
