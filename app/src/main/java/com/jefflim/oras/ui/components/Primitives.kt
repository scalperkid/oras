package com.jefflim.oras.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.AccentDeep
import com.jefflim.oras.ui.theme.AccentSoft
import com.jefflim.oras.ui.theme.Faint
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.Paper
import com.jefflim.oras.ui.theme.Surface
import com.jefflim.oras.ui.theme.T
import com.jefflim.oras.ui.theme.hueOf

/** Taps without Material's ink ripple, which does not belong in this system. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/** Section eyebrow: mono, uppercase, accent. */
@Composable
fun Kicker(text: String, color: Color = AccentDeep, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.kicker, color = color, modifier = modifier)
}

/**
 * Section head: heading plus its supporting line. The description carries meaning,
 * so it is never decorative filler.
 */
@Composable
fun SectionHead(
    title: String,
    kicker: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        kicker?.let {
            Kicker(it)
            Spacer(Modifier.height(10.dp))
        }
        Text(title, style = T.h2, color = Ink)
        description?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = T.body, color = Muted)
        }
    }
}

/** Surface card. Containers never lift on press, per the design system. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    padding: PaddingValues = PaddingValues(20.dp),
    background: Color = Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(
                BorderStroke(1.dp, if (highlight) Accent else Line),
                RoundedCornerShape(10.dp)
            )
            .padding(padding),
        content = content
    )
}

/** Soft background, saturated text. The only status treatment in the system. */
@Composable
fun StatusTag(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = T.label,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** The child's mark: initial on their own soft ground, uniform across all eight hues. */
@Composable
fun KidBadge(name: String, hue: Int, size: Int = 40, onDark: Boolean = false) {
    val h = hueOf(hue)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (onDark) h.tint.copy(alpha = 0.20f) else h.soft),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = T.title.copy(fontSize = (size * 0.42f).sp),
            color = if (onDark) h.tint else h.deep
        )
    }
}

/** Primary action. Buttons settle on press; cards never move. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Accent,
    foreground: Color = Paper,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f, tween(150), label = "press")

    Box(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) background else Line)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = T.bodyStrong, color = if (enabled) foreground else Faint)
    }
}

/** Secondary action: outline, so it never competes with the primary. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f, tween(150), label = "press")

    Box(
        modifier
            .scale(scale)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = T.bodyStrong, color = if (enabled) color else Faint)
    }
}

/**
 * Pill used for tabs and filters. 44dp minimum target throughout.
 *
 * A tab label must never wrap: equal-width pills make a long word break mid-syllable,
 * which is exactly what happened to "Children" before this was pinned to one line.
 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Ink else Surface)
            .border(BorderStroke(1.dp, if (selected) Ink else Line), RoundedCornerShape(999.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = T.bodyStrong,
            color = if (selected) Paper else Muted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center
        )
    }
}

/** Callout: accent-soft ground, used once per screen at most. */
@Composable
fun Callout(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AccentSoft)
            .border(BorderStroke(1.dp, Accent.copy(alpha = 0.28f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(title, style = T.bodyStrong, color = AccentDeep)
        Spacer(Modifier.height(6.dp))
        Text(body, style = T.body, color = Muted)
    }
}

/** Coloured issue block, replacing side-stripe callouts, which are banned. */
@Composable
fun NoticeBlock(title: String, body: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(16.dp)
    ) {
        Text(title, style = T.bodyStrong, color = fg)
        Spacer(Modifier.height(4.dp))
        Text(body, style = T.micro, color = Muted)
    }
}

/** Row of label and value, the workhorse list item. */
@Composable
fun ItemRow(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    background: Color = Paper,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.let { it(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = Ink)
            detail?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = T.micro, color = Muted)
            }
        }
        trailing?.let { Spacer(Modifier.width(12.dp)); it() }
    }
}

@Composable
fun Hairline(color: Color = Line) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** History bar. Height is the only encoded dimension, so it cannot mislead. */
@Composable
fun UsageBar(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(track),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction.coerceIn(0.015f, 1f))
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/** Numeric stepper. Big targets, monospaced value, no free-text keyboard to fight. */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 5,
    min: Int = 0,
    max: Int = 600,
    format: (Int) -> String = { "$it" },
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = T.body, color = Ink, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperKey("-", enabled = value > min) {
                onChange((value - step).coerceAtLeast(min))
            }
            Text(
                format(value),
                style = T.label.copy(fontSize = 14.sp),
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(82.dp)
            )
            StepperKey("+", enabled = value < max) {
                onChange((value + step).coerceAtMost(max))
            }
        }
    }
}

@Composable
private fun StepperKey(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(8.dp))
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, style = T.title, color = if (enabled) Ink else Line)
    }
}

/** Switch row. The label states the rule; the hint states the consequence. */
@Composable
fun ToggleRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(title, style = T.bodyStrong, color = Ink)
            hint?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = T.micro, color = Muted)
            }
        }
        TrackSwitch(checked) { onChange(!checked) }
    }
}

@Composable
private fun TrackSwitch(checked: Boolean, onClick: () -> Unit) {
    val knob by animateDpAsState(if (checked) 23.dp else 3.dp, tween(200), label = "switch")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) Accent else Line)
            .tap(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(26.dp)
                .clip(CircleShape)
                .background(Surface)
        )
    }
}
