package com.jefflim.oras.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warm orange palette, designed in OKLCH and converted to sRGB.
 * The comment on each line is the source token so the two stay reconcilable.
 * No stark white, no pure black: every neutral is tinted toward the accent hue.
 */

// Light surfaces (parent app)
val Paper = Color(0xFFF9F7F1)       // oklch(97.5% 0.008 88)
val Surface = Color(0xFFFEFDFA)     // oklch(99.3% 0.004 88)
val Ink = Color(0xFF180F0A)         // oklch(18% 0.018 52)
val Muted = Color(0xFF5E534C)       // oklch(45% 0.018 52)
val Faint = Color(0xFF8D847F)       // oklch(62% 0.014 52)
val Line = Color(0xFFDCD7CA)        // oklch(88% 0.018 88)

val Accent = Color(0xFFEB681D)      // oklch(67% 0.18 45)
val AccentSoft = Color(0xFFFFE4D6)  // oklch(94% 0.04 45)
val AccentDeep = Color(0xFFAF4000)  // oklch(52% 0.16 45) - accent text on soft bg, AA safe

// Semantic
val Green = Color(0xFF00793D)
val GreenSoft = Color(0xFFDFF0E4)
val Red = Color(0xFFB63B37)
val RedSoft = Color(0xFFFDE9E7)
val Amber = Color(0xFFA26F00)
val AmberSoft = Color(0xFFF9EDD8)
val Blue = Color(0xFF0069B9)
val BlueSoft = Color(0xFFDEEDFB)

// Dark band, used for the lock surface. This is the design system's "summary band"
// treatment, an explicit design choice, not system dark mode following.
val InkDeep = Color(0xFF0E0704)     // oklch(14% 0.016 52)
val InkRaised = Color(0xFF211914)   // oklch(22% 0.016 52)
val InkLine = Color(0xFF342C27)     // oklch(30% 0.014 52)
val OnInk = Color(0xFFF3F2ED)       // oklch(96% 0.006 88)
val OnInkMuted = Color(0xFFAAA39D)  // oklch(72% 0.012 60)
val OnInkFaint = Color(0xFF6E6762)  // oklch(52% 0.012 60)

/**
 * Kid identity colours. Uniform lightness and chroma across hues so eight children
 * read as one system rather than eight unrelated stickers.
 * [tint] sits on paper, [soft] is the soft background, [deep] is text on [soft].
 */
data class KidHue(val name: String, val tint: Color, val soft: Color, val deep: Color)

val KidHues = listOf(
    KidHue("Ember", Color(0xFFD15C56), Color(0xFFFFE3DF), Color(0xFF862726)),
    KidHue("Marigold", Color(0xFFC9690C), Color(0xFFFFE6D6), Color(0xFF803300)),
    KidHue("Sun", Color(0xFFA98000), Color(0xFFF4EBD1), Color(0xFF684700)),
    KidHue("Fern", Color(0xFF409D48), Color(0xFFDDF2DD), Color(0xFF045E17)),
    KidHue("Lagoon", Color(0xFF00A19A), Color(0xFFD2F3F0), Color(0xFF00615D)),
    KidHue("Sky", Color(0xFF008FD6), Color(0xFFD7EFFF), Color(0xFF00538C)),
    KidHue("Iris", Color(0xFF7F76DC), Color(0xFFE8E8FF), Color(0xFF483E90)),
    KidHue("Bloom", Color(0xFFB761B1), Color(0xFFF9E3F7), Color(0xFF722C6E)),
)

fun hueOf(index: Int): KidHue = KidHues[((index % KidHues.size) + KidHues.size) % KidHues.size]
