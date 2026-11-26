package dev.charlesferreira.treino.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.charlesferreira.treino.timer.RestMath
import dev.charlesferreira.treino.timer.RestRequest
import dev.charlesferreira.treino.timer.RestTimer
import dev.charlesferreira.treino.timer.WorkoutProgress
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.theme.TreinoType
import kotlinx.coroutines.delay

/**
 * The Phase 0 spike screen, kept for testing the timer by hand. Reachable only in debug builds,
 * from a hidden row at the bottom of the Profile.
 */
@Composable
fun TimerSpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var state by remember { mutableStateOf(RestTimer.state(context)) }
    // Only the screen refreshes on a loop; the timer itself is the store, the clock and an alarm.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            state = RestTimer.state(context)
            delay(250)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val start = { seconds: Long ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        RestTimer.start(context, sampleRequest(seconds * 1000))
    }
    val rest = state
    val status = when {
        rest == null -> "Sem descanso"
        rest.finished -> "Descanso acabou"
        else -> "Descanso ${RestMath.format(rest.remainingMs(now))}"
    }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        TText(status, style = TreinoType.mono(40.sp))
        PrimaryButton("Descanso 15s", onClick = { start(15) })
        PrimaryButton("Descanso 90s", onClick = { start(90) })
    }
}

/** Sample data from the design: Superior B, 9 of 16 sets done, next is Rosca martelo 2 of 2. */
private fun sampleRequest(durationMs: Long) = RestRequest(
    durationMs = durationMs,
    workoutName = "Superior B",
    exerciseName = "Rosca martelo",
    setNumber = 2,
    setCount = 2,
    target = "16 kg × 10",
    progress = WorkoutProgress(setsPerExercise = listOf(3, 3, 2, 2, 2, 2, 2), setsDone = 9),
)
