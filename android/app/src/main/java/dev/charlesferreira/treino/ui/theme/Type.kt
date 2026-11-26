package dev.charlesferreira.treino.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.R

/**
 * DM Sans and JetBrains Mono, bundled as the variable TTFs from google/fonts (`res/font`). Each
 * weight is an instance of the same file through [FontVariation]. DM Sans also has an optical size
 * axis: the browser sets it to the font size (`font-optical-sizing: auto`), so text uses opsz 14
 * and the large titles use [DmSansDisplay] (opsz 30).
 */
@OptIn(ExperimentalTextApi::class)
object TreinoFonts {
    private fun dmSans(weight: Int, opsz: Float) = Font(
        R.font.dm_sans,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
    )

    private fun mono(weight: Int) = Font(
        R.font.jetbrains_mono,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val DmSans = FontFamily(listOf(400, 500, 600, 700).map { dmSans(it, 14f) })

    val DmSansDisplay = FontFamily(listOf(400, 500, 600, 700).map { dmSans(it, 30f) })

    val JetBrainsMono = FontFamily(mono(500), mono(700))
}

/** Sizes from the design (px become sp). Line height 1.4 like `.ph`, titles 1.15. */
object TreinoType {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    /** `.ph`: 15 px, line height 1.4. */
    val Body = TextStyle(
        fontFamily = TreinoFonts.DmSans,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = TreinoColors.Text,
        lineHeightStyle = trim,
    )

    /** `.lbl`: 12 px medium, dim. */
    val Label = Body.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.W500, color = TreinoColors.TextDim)

    /** `.sec`: 12 px semibold uppercase faint, .06em. Uppercase the text at the call site. */
    val Section = Body.copy(
        fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.W600,
        letterSpacing = 0.06.em, color = TreinoColors.TextFaint,
    )

    /** Rows: 14 px medium. */
    val Row = Body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W500)

    /** `.tag`: 12 px medium. */
    val Tag = Body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.W500, color = TreinoColors.TextSoft)

    /** `.tab`: 11 px semibold. */
    val Tab = Body.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.W600)

    /** `.pbtn`: 16 px bold ink. */
    val Button = Body.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.W700, color = TreinoColors.Ink)

    /** `.gbtn`: 15 px semibold. */
    val ButtonGhost = Body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600)

    /** Exercise name in the current card: 21 px bold, -.01em. */
    val Heading = Body.copy(
        fontFamily = TreinoFonts.DmSansDisplay, fontSize = 21.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.W700, letterSpacing = (-0.01).em,
    )

    /** Screen title in sub screens (Perfil): 18 px bold. */
    val TitleSmall = Body.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.W700)

    /** Workout name in the header: 30 px bold, -.02em, line height 1.15. */
    val Title = Body.copy(
        fontFamily = TreinoFonts.DmSansDisplay, fontSize = 30.sp, lineHeight = 34.5.sp,
        fontWeight = FontWeight.W700, letterSpacing = (-0.02).em,
    )

    /** Onboarding: 34 px bold, line height 1.1. */
    val Display = Title.copy(fontSize = 34.sp, lineHeight = 37.4.sp)

    /** `.m`: JetBrains Mono 500, tabular figures, -.02em. */
    fun mono(size: TextUnit, weight: FontWeight = FontWeight.W500, color: androidx.compose.ui.graphics.Color = TreinoColors.Text) =
        Body.copy(
            fontFamily = TreinoFonts.JetBrainsMono,
            fontWeight = weight,
            fontSize = size,
            lineHeight = size * 1.4f,
            letterSpacing = (-0.02).em,
            fontFeatureSettings = "tnum",
            color = color,
        )

    val Mono = mono(15.sp)
}
