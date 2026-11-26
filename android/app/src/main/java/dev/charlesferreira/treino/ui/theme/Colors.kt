package dev.charlesferreira.treino.ui.theme

import androidx.compose.ui.graphics.Color

/** Direction B palette, named after its role in `the android/design screens`. */
object TreinoColors {
    /** `.ph` background. */
    val Background = Color(0xFF101113)

    /** `.card`. */
    val Card = Color(0xFF1A1C20)

    /** `.tag`, `.cell`, `.stp`, the avatar. */
    val Surface = Color(0xFF24272C)

    /** `.tabbar`. */
    val SurfaceBar = Color(0xFF1F2125)

    /** Segment not started yet. */
    val SurfaceLow = Color(0xFF2A2D33)

    /** Segment in progress (unfilled part), `.dot` ring, toggle off, sheet grab. */
    val SurfaceHigh = Color(0xFF3A3D44)

    val Accent = Color(0xFFFF7A45)

    /** Text on accent tinted surfaces (`.tag.acc`). */
    val AccentLight = Color(0xFFFF9A70)

    /** `.tag.acc` background: accent at 14%. */
    val AccentSoft = Color(0x24FF7A45)

    /** Current set row: accent at 7%. */
    val AccentFaint = Color(0x12FF7A45)

    /** Current exercise card border: accent at 35%. */
    val AccentBorder = Color(0x59FF7A45)

    /** Text and icons on accent. */
    val Ink = Color(0xFF1A0D06)

    val Text = Color(0xFFF2F0EC)

    /** Secondary text, tag text, stepper buttons. */
    val TextSoft = Color(0xFFD6D2CB)

    /** `.dim`, `.lbl`. */
    val TextDim = Color(0xFFA8A49D)

    /** `.faint`, inactive tabs, chevrons. */
    val TextFaint = Color(0xFF8A867F)

    /** `rgba(255,255,255,.06)`: card border, row dividers. */
    val Border = Color(0x0FFFFFFF)

    /** `rgba(255,255,255,.08)`: tab bar and sheet border. */
    val BorderStrong = Color(0x14FFFFFF)

    /** `.scrim`. */
    val Scrim = Color(0x8C000000)
}
