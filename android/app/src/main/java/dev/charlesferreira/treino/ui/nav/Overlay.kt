package dev.charlesferreira.treino.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Sheets of a screen are drawn by the root above the tab bar (the design's scrim covers it).
 * A screen shows one with [Overlay]; the root draws [content] after the [TabBar].
 */
class OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        internal set

    /** The rest (pill or expanded sheet): above the tab bar, below the sheets. */
    var floating: (@Composable () -> Unit)? by mutableStateOf(null)
        internal set
}

val LocalOverlayHost = staticCompositionLocalOf { OverlayHost() }

/** Shows [content] over everything while this is in the composition. */
@Composable
fun Overlay(content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    val latest by rememberUpdatedState(content)
    DisposableEffect(host) {
        val shown: @Composable () -> Unit = { latest() }
        host.content = shown
        onDispose { if (host.content === shown) host.content = null }
    }
}

/** Shows [content] in the floating layer (the rest) while this is in the composition. */
@Composable
fun FloatingOverlay(content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    val latest by rememberUpdatedState(content)
    DisposableEffect(host) {
        val shown: @Composable () -> Unit = { latest() }
        host.floating = shown
        onDispose { if (host.floating === shown) host.floating = null }
    }
}

/**
 * The animation of the overlay being drawn, for its parts to enter and leave on their own: the
 * scrim fades ([scrimMotion]) and the sheet slides up from the bottom ([sheetMotion]). Null outside
 * an animated layer.
 */
val LocalOverlayMotion = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** True while the overlay is shown or entering; false while it leaves (a closing sheet). */
@Composable
fun overlayActive(): Boolean =
    LocalOverlayMotion.current?.transition?.targetState?.let { it == EnterExitState.Visible } ?: true

private const val IN_MS = 300
private const val OUT_MS = 220

/** The scrim of a sheet: fades in and out with it. */
@Composable
fun Modifier.scrimMotion(): Modifier {
    val scope = LocalOverlayMotion.current ?: return this
    return with(scope) {
        this@scrimMotion.animateEnterExit(enter = fadeIn(tween(IN_MS)), exit = fadeOut(tween(OUT_MS)))
    }
}

/** A sheet: slides up from below the screen and back down. */
@Composable
fun Modifier.sheetMotion(): Modifier {
    val scope = LocalOverlayMotion.current ?: return this
    return with(scope) {
        this@sheetMotion.animateEnterExit(
            enter = slideInVertically(tween(IN_MS, easing = LinearOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(OUT_MS, easing = FastOutLinearInEasing)) { it },
        )
    }
}

/**
 * Draws [content] and keeps the last one on screen while it leaves, so a closing sheet slides
 * down instead of vanishing. Switching straight from one sheet to another swaps them in place.
 */
@Composable
fun AnimatedOverlayLayer(content: (@Composable () -> Unit)?) {
    val last = remember { arrayOfNulls<@Composable () -> Unit>(1) }
    if (content != null) last[0] = content
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = content != null
    if (!visible.currentState && !visible.targetState) {
        last[0] = null
        return
    }
    val shown = last[0] ?: return
    AnimatedVisibility(visibleState = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        CompositionLocalProvider(LocalOverlayMotion provides this) { shown() }
    }
}
