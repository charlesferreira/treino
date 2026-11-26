package dev.charlesferreira.treino.ui.today

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderScrollTest {
    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, 1e-4f)

    @Test
    fun progressFollowsTheScrollAndClamps() {
        close(0f, HeaderScroll.progress(0f))
        close(0.5f, HeaderScroll.progress(90f))
        close(1f, HeaderScroll.progress(180f))
        close(1f, HeaderScroll.progress(400f))
        close(0f, HeaderScroll.progress(-10f))
    }

    @Test
    fun expanded() {
        val v = HeaderScroll.values(0f)
        close(150f, v.height); close(54f, v.paddingTop); close(12f, v.gap)
        close(0f, v.backgroundAlpha); close(0f, v.borderAlpha)
        close(1f, v.eyebrowAlpha); close(17f, v.eyebrowHeight)
        close(30f, v.titleSize); close(0f, v.titleGap)
        close(0f, v.inlineCountAlpha); close(0f, v.inlineCountMaxWidth)
        close(1f, v.sideCountAlpha); close(52f, v.sideCountMaxWidth); close(12f, v.rowGap)
        close(40f, v.timerHeight); close(12f, v.timerPadding); close(1f, v.timerBackgroundAlpha); close(15f, v.timerFontSize)
        assertFalse(v.timerFaint)
        close(40f, v.avatarSize); close(1f, v.avatarAlpha)
        close(8f, v.barHeight); close(4f, v.segmentGap)
        close(162f, v.contentTop)
    }

    @Test
    fun halfway() {
        val v = HeaderScroll.values(0.5f)
        close(1f, v.early); close(0f, v.late)
        close(117f, v.height); close(50f, v.paddingTop); close(10f, v.gap)
        close(0.94f, v.backgroundAlpha); close(0.03f, v.borderAlpha)
        // What leaves is gone, what arrives has not started.
        close(0f, v.eyebrowAlpha); close(8.5f, v.eyebrowHeight)
        close(24f, v.titleSize); close(0f, v.titleGap)
        close(0f, v.inlineCountAlpha); close(0f, v.inlineCountMaxWidth)
        close(0f, v.sideCountAlpha); close(0f, v.sideCountMaxWidth); close(0f, v.rowGap)
        close(32f, v.timerHeight); close(6f, v.timerPadding); close(0.5f, v.timerBackgroundAlpha); close(14f, v.timerFontSize)
        assertFalse(v.timerFaint, "the color changes only after 50%")
        close(20f, v.avatarSize); close(0f, v.avatarAlpha)
        close(6f, v.barHeight); close(3.5f, v.segmentGap)
        close(72f, v.contentTop)
        close(0f, v.extrasAlpha)
    }

    @Test
    fun collapsed() {
        val v = HeaderScroll.values(1f)
        close(84f, v.height); close(46f, v.paddingTop); close(8f, v.gap)
        close(0.94f, v.backgroundAlpha); close(0.06f, v.borderAlpha)
        close(0f, v.eyebrowAlpha); close(0f, v.eyebrowHeight)
        close(18f, v.titleSize); close(10f, v.titleGap)
        close(1f, v.inlineCountAlpha); close(52f, v.inlineCountMaxWidth)
        close(0f, v.sideCountAlpha); close(0f, v.sideCountMaxWidth); close(0f, v.rowGap)
        close(24f, v.timerHeight); close(0f, v.timerPadding); close(0f, v.timerBackgroundAlpha); close(13f, v.timerFontSize)
        assertTrue(v.timerFaint)
        close(0f, v.avatarSize); close(0f, v.avatarAlpha)
        close(4f, v.barHeight); close(3f, v.segmentGap)
        close(-18f, v.contentTop)
    }

    @Test
    fun inlineCountArrivesInTheSecondHalf() {
        close(0.5f, HeaderScroll.values(0.75f).late)
        close(26f, HeaderScroll.values(0.75f).inlineCountMaxWidth)
        close(0.5f, HeaderScroll.values(0.25f).sideCountAlpha)
    }
}
