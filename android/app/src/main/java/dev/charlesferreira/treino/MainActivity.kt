package dev.charlesferreira.treino

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.charlesferreira.treino.timer.RestMath
import dev.charlesferreira.treino.timer.RestRequest
import dev.charlesferreira.treino.timer.RestTimer
import dev.charlesferreira.treino.timer.WorkoutProgress
import kotlinx.coroutines.delay

/** Phase 0 spike screen. Replaced in Phase 3; the timer itself lives in the timer package. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpikeScreen() }
    }

    override fun onResume() {
        super.onResume()
        RestTimer.resumeIfActive(this)
    }

    @Composable
    private fun SpikeScreen() {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var state by remember { mutableStateOf(RestTimer.state(this)) }
        // Only the screen refreshes on a loop; the timer itself is the store, the clock and an alarm.
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                state = RestTimer.state(this@MainActivity)
                delay(250)
            }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val start = { seconds: Long ->
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            RestTimer.start(this, sampleRequest(seconds * 1000))
        }
        val rest = state
        val status = when {
            rest == null -> "Sem descanso"
            rest.finished -> "Descanso acabou"
            else -> "Descanso ${RestMath.format(rest.remainingMs(now))}"
        }
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0C0D10)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            BasicText(status, style = TextStyle(color = Color(0xFFF2F0EC), fontSize = 40.sp))
            SpikeButton("Descanso 15s") { start(15) }
            SpikeButton("Descanso 90s") { start(90) }
        }
    }

    @Composable
    private fun SpikeButton(label: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .background(Color(0xFFFF7A45), RoundedCornerShape(28.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = TextStyle(color = Color(0xFF1A0D06), fontSize = 18.sp))
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
}
