package dev.charlesferreira.treino.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A stroked 24×24 icon from the design (`.ic`: no fill, stroke 2, round caps and joins). [paths]
 * are the SVG path strings copied from `the android/design screens`; circles and rects were turned
 * into paths. [dots] are the `h.01` dot paths, which the design draws with stroke 3.
 */
class TreinoIcon internal constructor(
    val name: String,
    private val paths: List<String>,
    private val dots: List<String> = emptyList(),
) {
    /** The icon at the default stroke (2). Drawn white: tint it with [Icon]. */
    val vector: ImageVector by lazy { vector(2f) }

    /** Some places use a heavier stroke: 2.4 (app icon glyph), 2.6 (primary buttons), 3 (done dot). */
    fun vector(strokeWidth: Float): ImageVector = ImageVector.Builder(
        name = "$name-$strokeWidth",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { addStroke(it, strokeWidth) }
        dots.forEach { addStroke(it, 3f) }
    }.build()

    private fun ImageVector.Builder.addStroke(d: String, width: Float) {
        addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}

object TreinoIcons {
    /** Hoje tab, "Halteres 2×" tag, onboarding and app icon. */
    val Dumbbell = TreinoIcon("dumbbell", listOf("M6 7v10M18 7v10M3 10v4M21 10v4M6 12h12"))

    /** Evolução tab. */
    val Chart = TreinoIcon("chart", listOf("M3 20h18", "M6 16v-4M11 16V8M16 16v-6M21 16V4"))

    /** Treinos tab. */
    val List = TreinoIcon("list", listOf("M8 6h13M8 12h13M8 18h13"), dots = listOf("M3 6h.01M3 12h.01M3 18h.01"))

    val Check = TreinoIcon("check", listOf("M5 12.5l4.5 4.5L19 7.5"))
    val ChevronDown = TreinoIcon("chevron-down", listOf("M6 9l6 6 6-6"))
    val ChevronRight = TreinoIcon("chevron-right", listOf("M9 6l6 6-6 6"))
    val Back = TreinoIcon("back", listOf("M15 6l-6 6 6 6"))
    val Clock = TreinoIcon("clock", listOf("M4 13a8 8 0 1 0 16 0a8 8 0 1 0 -16 0", "M12 9v4l2 2M9 2h6"))
    val Plus = TreinoIcon("plus", listOf("M12 5v14M5 12h14"))
    val Close = TreinoIcon("close", listOf("M6 6l12 12M18 6L6 18"))
    val Play = TreinoIcon("play", listOf("M8 5l11 7-11 7z"))

    /** "Nota do dia". */
    val Note = TreinoIcon("note", listOf("M4 6h16M4 12h10M4 18h7"))

    /** Menu lines (workouts list). */
    val Lines = TreinoIcon("lines", listOf("M4 6h16M4 12h16M4 18h10"))

    /** Drag handle: two columns of three dots. */
    val Grip = TreinoIcon("grip", emptyList(), dots = listOf("M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01"))

    /** Vertical "more". */
    val More = TreinoIcon("more", emptyList(), dots = listOf("M12 6h.01M12 12h.01M12 18h.01"))

    val Trash = TreinoIcon("trash", listOf("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"))

    /** "Já tenho um backup" (import). */
    val Upload = TreinoIcon("upload", listOf("M12 20V9M7 14l5-5 5 5M5 4h14"))

    /** Export. */
    val Download = TreinoIcon("download", listOf("M12 4v11M7 10l5 5 5-5M5 20h14"))

    val Search = TreinoIcon("search", listOf("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M20 20l-4-4"))

    /** Machine (equipment picker). */
    val Machine = TreinoIcon(
        "machine",
        listOf("M10 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-4a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z", "M4 8v8M20 8v8"),
    )
}

/** Draws [icon] tinted with [tint]. Default size 20 like `.ic`. */
@Composable
fun Icon(
    icon: TreinoIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Float = 2f,
    contentDescription: String? = null,
) {
    val vector = remember(icon, strokeWidth) { if (strokeWidth == 2f) icon.vector else icon.vector(strokeWidth) }
    Image(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
