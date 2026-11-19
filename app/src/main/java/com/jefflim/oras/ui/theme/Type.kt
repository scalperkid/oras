package com.jefflim.oras.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jefflim.oras.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Display and body share one family, per the design system. */
val Manrope = FontFamily(
    manrope(400), manrope(500), manrope(600), manrope(700), manrope(800)
)

/** Mono is always a separate technical voice: kickers, metadata, timers, codes. */
val PlexMono = FontFamily(
    Font(R.font.plex_mono, FontWeight.W400),
    Font(R.font.plex_mono_md, FontWeight.W500),
)

/** Tabular figures so counting numbers never jitter. */
private const val TNUM = "tnum"

object T {
    /** Mono, uppercase, wide tracking. Section eyebrows. */
    val kicker = TextStyle(
        fontFamily = PlexMono, fontWeight = FontWeight.W500,
        fontSize = 11.sp, letterSpacing = 0.1.em
    )

    /** Mono micro labels: metadata, units, table headers. */
    val label = TextStyle(
        fontFamily = PlexMono, fontWeight = FontWeight.W500,
        fontSize = 11.sp, letterSpacing = 0.05.em
    )

    val micro = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W500,
        fontSize = 12.sp, lineHeight = 16.sp
    )

    val body = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W400,
        fontSize = 15.sp, lineHeight = 22.sp
    )

    val bodyStrong = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W600,
        fontSize = 15.sp, lineHeight = 21.sp
    )

    /** Lead paragraph under a heading. */
    val lead = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W400,
        fontSize = 16.sp, lineHeight = 24.sp
    )

    /** Row and card titles. Below 20sp so this is the body role, not display. */
    val title = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W700,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em
    )

    /** Display role starts here. */
    val h2 = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W700,
        fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.035).em
    )

    val h1 = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W800,
        fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.04).em
    )

    val metric = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W700,
        fontSize = 30.sp, lineHeight = 30.sp, letterSpacing = (-0.03).em,
        fontFeatureSettings = TNUM
    )

    /** The countdown itself. Monospaced digits, so it cannot shuffle while ticking. */
    val clock = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W700,
        fontSize = 68.sp, lineHeight = 70.sp, letterSpacing = (-0.04).em,
        fontFeatureSettings = TNUM, textAlign = TextAlign.Center
    )

    val clockSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W700,
        fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.03).em,
        fontFeatureSettings = TNUM
    )

    /** Digits on the keypad. */
    val keypad = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.W600,
        fontSize = 26.sp, fontFeatureSettings = TNUM, textAlign = TextAlign.Center
    )
}
