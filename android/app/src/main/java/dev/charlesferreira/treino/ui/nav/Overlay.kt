package dev.charlesferreira.treino.ui.nav

import androidx.compose.runtime.Composable
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
