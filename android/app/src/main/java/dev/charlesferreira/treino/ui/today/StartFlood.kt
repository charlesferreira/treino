package dev.charlesferreira.treino.ui.today

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.nav.Overlay
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** "Começar treino" was tapped at [origin] (window coordinates) for [workoutName]. */
data class StartLaunch(val origin: Offset, val workoutName: String)

/**
 * The start of a workout: the button's orange grows from where it was tapped until it covers the
 * screen, the workout's name shows on it, and it fades away over the workout, now without tabs.
 * [onCovered] starts the workout behind the orange; [onDone] removes this layer.
 */
@Composable
fun StartFlood(launch: StartLaunch, onCovered: () -> Unit, onDone: () -> Unit) {
    val covered by rememberUpdatedState(onCovered)
    val done by rememberUpdatedState(onDone)
    val grow = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val view = LocalView.current
    LaunchedEffect(launch) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        val name = launch { delay(GROW_MS / 2L); text.animateTo(1f, tween(TEXT_MS, easing = LinearOutSlowInEasing)) }
        grow.animateTo(1f, tween(GROW_MS, easing = FastOutSlowInEasing))
        covered()
        name.join()
        delay(HOLD_MS)
        fade.animateTo(0f, tween(FADE_MS, easing = FastOutSlowInEasing))
        done()
    }
    Overlay {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                // Window and overlay share the origin: the layer fills the window.
                val o = launch.origin.takeIf { it != Offset.Zero } ?: Offset(size.width / 2, size.height * 0.8f)
                val far = maxOf(
                    hypot(o.x, o.y), hypot(size.width - o.x, o.y),
                    hypot(o.x, size.height - o.y), hypot(size.width - o.x, size.height - o.y),
                )
                drawCircle(TreinoColors.Accent, radius = 28.dp.toPx() + (far - 28.dp.toPx()) * grow.value, center = o)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = text.value
                    val s = 0.92f + 0.08f * text.value + 0.06f * (1f - fade.value)
                    scaleX = s
                    scaleY = s
                    translationY = (1f - text.value) * 16.dp.toPx()
                },
            ) {
                TText("Bom treino", style = TreinoType.Label, color = TreinoColors.Ink.copy(alpha = 0.7f), maxLines = 1)
                TText(launch.workoutName, style = TreinoType.Display, color = TreinoColors.Ink, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val GROW_MS = 420
private const val TEXT_MS = 300
private const val HOLD_MS = 250L
private const val FADE_MS = 340
