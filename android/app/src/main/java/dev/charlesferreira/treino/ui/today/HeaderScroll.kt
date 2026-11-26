package dev.charlesferreira.treino.ui.today

/**
 * The workout header tied to the scroll, a port of `renderVals()` in `b-header-scroll.dc.html`.
 * Lengths are in dp (the design's px), sizes in sp, alphas 0..1.
 *
 * `p` is scroll / [COLLAPSE_DP]: the design moves the content 180 px from 0% to 100%, so the
 * header is fully collapsed after 180 dp of scroll and follows the finger with no jump.
 * `early` = clamp(2p) runs in the first half (what leaves), `late` = clamp(2p − 1) in the second
 * (what arrives).
 *
 * The design's vertical positions count from the top of an iPhone. Like the other screens (54 px
 * of top padding = status bar + 10 dp), the screen adds the real status bar inset minus
 * [DESIGN_STATUS_DP] to every top value. Collapsed, the design's rows overflow 10 px above the
 * 46 px padding (flex-end), into the iPhone's empty status area; [WorkoutHeader] grows the header
 * by that much instead, so the rows never reach under the Android status bar.
 */
data class HeaderValues(
    val p: Float,
    val early: Float,
    val late: Float,
    val height: Float,
    val paddingTop: Float,
    val gap: Float,
    val backgroundAlpha: Float,
    val borderAlpha: Float,
    val eyebrowAlpha: Float,
    val eyebrowHeight: Float,
    val titleSize: Float,
    val titleGap: Float,
    val inlineCountAlpha: Float,
    val inlineCountMaxWidth: Float,
    val sideCountAlpha: Float,
    val sideCountMaxWidth: Float,
    val rowGap: Float,
    val timerHeight: Float,
    val timerPadding: Float,
    val timerBackgroundAlpha: Float,
    val timerFontSize: Float,
    /** The timer's text turns faint past 50%. */
    val timerFaint: Boolean,
    val avatarSize: Float,
    val avatarAlpha: Float,
    val barHeight: Float,
    val segmentGap: Float,
    /** Where the content starts at this scroll (it is what the scroll moves). */
    val contentTop: Float,
    /**
     * Not in the prototype: `b-workout` has a chevron after the title and a clock icon in the
     * timer. They leave with the eyebrow (alpha and width 1 − early) so nothing jumps.
     */
    val extrasAlpha: Float,
)

object HeaderScroll {
    const val COLLAPSE_DP = 180f
    const val PADDING_BOTTOM_DP = 12f
    const val PADDING_SIDE_DP = 20f
    const val DESIGN_STATUS_DP = 44f

    /** The design's content top at 0%: the expanded header plus the 12 px gap. */
    const val CONTENT_TOP_DP = 162f

    fun progress(scrollDp: Float): Float = (scrollDp / COLLAPSE_DP).coerceIn(0f, 1f)

    fun values(progress: Float): HeaderValues {
        val p = progress.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * p
        fun clamp(v: Float) = v.coerceIn(0f, 1f)
        val early = clamp(p * 2)
        val late = clamp(p * 2 - 1)
        return HeaderValues(
            p = p,
            early = early,
            late = late,
            height = lerp(150f, 84f),
            paddingTop = lerp(54f, 46f),
            gap = lerp(12f, 8f),
            // The prototype lerps 0 → 0.94 over the whole scroll, but its content is empty cards
            // of nearly the background's color; with real text, 47% at half scroll leaves the list
            // legible under the rows. Same 0 → 0.94, reached with the eyebrow (early), so at 50%
            // the header reads as the prototype looks.
            backgroundAlpha = 0.94f * early,
            borderAlpha = lerp(0f, 0.06f),
            eyebrowAlpha = 1 - early,
            eyebrowHeight = lerp(17f, 0f),
            titleSize = lerp(30f, 18f),
            titleGap = late * 10,
            inlineCountAlpha = late,
            inlineCountMaxWidth = late * 52,
            sideCountAlpha = 1 - early,
            sideCountMaxWidth = (1 - early) * 52,
            rowGap = (1 - early) * 12,
            timerHeight = lerp(40f, 24f),
            timerPadding = lerp(12f, 0f),
            timerBackgroundAlpha = 1 - p,
            timerFontSize = lerp(15f, 13f),
            timerFaint = p > 0.5f,
            avatarSize = lerp(40f, 0f),
            avatarAlpha = 1 - early,
            barHeight = lerp(8f, 4f),
            segmentGap = lerp(4f, 3f),
            contentTop = CONTENT_TOP_DP - p * COLLAPSE_DP,
            extrasAlpha = 1 - early,
        )
    }
}
