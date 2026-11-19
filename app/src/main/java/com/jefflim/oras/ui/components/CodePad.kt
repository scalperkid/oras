package com.jefflim.oras.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Surface
import com.jefflim.oras.ui.theme.T

/** Progress dots for a code of known length. */
@Composable
fun CodeDots(filled: Int, length: Int, color: Color = Accent, track: Color = Line) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(length) { i ->
            val on = i < filled
            val scale by animateFloatAsState(if (on) 1f else 0.7f, tween(180), label = "dot")
            Box(
                Modifier
                    .size(13.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(if (on) color else track)
            )
        }
    }
}

/**
 * Light-theme keypad for the parent side: the gate on the app itself, and setting codes.
 * Deliberately the same shape as the lock's keypad so the gesture transfers.
 */
@Composable
fun CodePad(
    value: String,
    length: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "del"),
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1.6f)
                        )
                    } else {
                        PadKey(
                            label = if (key == "del") "⌫" else key,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (key == "del") {
                                if (value.isNotEmpty()) onChange(value.dropLast(1))
                            } else if (value.length < length) {
                                onChange(value + key)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(12.dp))
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = T.keypad, color = Ink)
    }
}
