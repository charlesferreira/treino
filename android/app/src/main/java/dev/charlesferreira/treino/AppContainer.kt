package dev.charlesferreira.treino

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.preferencesDataStore
import dev.charlesferreira.treino.data.SettingsStore
import dev.charlesferreira.treino.data.TimerSwitches
import dev.charlesferreira.treino.data.TreinoDatabase
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.timer.RestNotifications
import dev.charlesferreira.treino.timer.RestOverHandler
import dev.charlesferreira.treino.timer.RestTimer
import dev.charlesferreira.treino.timer.SetLogHandler
import dev.charlesferreira.treino.workout.WorkoutTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual dependency injection: one of each, built lazily, owned by [TreinoApp]. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    /** Work that outlives a screen (settings sync at start). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: TreinoDatabase by lazy { TreinoDatabase.open(app) }

    val settings: SettingsStore by lazy {
        SettingsStore(
            app.settingsDataStore,
            object : TimerSwitches {
                override fun setSound(enabled: Boolean) = RestTimer.setSoundEnabled(app, enabled)
                override fun setVibration(enabled: Boolean) = RestTimer.setVibrationEnabled(app, enabled)
            },
        )
    }

    val repository: TreinoRepository by lazy { TreinoRepository(database, settings) }

    val workoutTimer: WorkoutTimer by lazy { WorkoutTimer(app, repository) }
}

class TreinoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The timer keeps its own copy of the switches (receivers read it in a fresh process).
        container.appScope.launch { container.settings.syncTimer() }
        // Set here, so a notification button in a fresh process finds them.
        RestNotifications(this).createChannels()
        RestTimer.setLogHandler = SetLogHandler { _, _ -> container.workoutTimer.logNextFromNotification() }
        RestTimer.restOverHandler = RestOverHandler { container.workoutTimer.refreshNotification() }
        container.workoutTimer.watch(container.appScope)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as TreinoApp).container

@Composable
fun appContainer(): AppContainer = LocalContext.current.appContainer
