package com.jefflim.oras.ui.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jefflim.oras.core.TimeRules
import com.jefflim.oras.data.Rules
import com.jefflim.oras.ui.components.Callout
import com.jefflim.oras.ui.components.Card
import com.jefflim.oras.ui.components.GhostButton
import com.jefflim.oras.ui.components.Kicker
import com.jefflim.oras.ui.components.NoticeBlock
import com.jefflim.oras.ui.components.SectionHead
import com.jefflim.oras.ui.components.Stepper
import com.jefflim.oras.ui.components.ToggleRow
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.Amber
import com.jefflim.oras.ui.theme.AmberSoft
import com.jefflim.oras.ui.theme.Faint
import com.jefflim.oras.ui.theme.Green
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.Paper
import com.jefflim.oras.ui.theme.Red
import com.jefflim.oras.ui.theme.T

/**
 * Two numbers decide everything about time: how long one turn is, and how much the
 * children share in a day. Everything else here is a rule you set once and forget.
 */
@Composable
fun RulesScreen(
    rules: Rules,
    kidCount: Int,
    isHome: Boolean,
    overlayOk: Boolean,
    batteryOk: Boolean,
    strongLockOn: Boolean,
    needsOem: Boolean,
    onChange: (Rules) -> Unit,
    onChangeParentCode: () -> Unit,
    onFixHome: () -> Unit,
    onFixOverlay: () -> Unit,
    onFixBattery: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAutostart: () -> Unit,
    onResetToday: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            kicker = "Rules",
            title = "How the phone behaves",
            description = "These apply to every child. Set them once."
        )
        Spacer(Modifier.height(24.dp))

        Card {
            Kicker("Time", color = Muted)
            Spacer(Modifier.height(18.dp))
            Stepper(
                "One turn",
                rules.turnMinutes,
                { onChange(rules.copy(turnMinutes = it)) },
                step = 5, min = 5, max = 120,
                format = { TimeRules.formatMinutes(it) }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "How long each child gets when they enter their code. When it ends they go " +
                    "to the back of the queue, so the next one is up.",
                style = T.micro, color = Muted
            )

            Spacer(Modifier.height(22.dp))
            Stepper(
                "Whole day",
                rules.dailyTotalMinutes,
                { onChange(rules.copy(dailyTotalMinutes = it)) },
                step = 15, min = 15, max = 600,
                format = { TimeRules.formatMinutes(it) }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Total phone time the children share between them each day. When this runs " +
                    "out the phone locks until morning.",
                style = T.micro, color = Muted
            )

            if (kidCount > 0) {
                Spacer(Modifier.height(18.dp))
                Callout(
                    title = "That works out at about ${rules.dailyTotalMinutes / rules.turnMinutes} turns a day",
                    body = "Across $kidCount ${if (kidCount == 1) "child" else "children"}, " +
                        "roughly ${TimeRules.formatMinutes(rules.dailyTotalMinutes / kidCount)} each " +
                        "if they take it in turns evenly."
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card {
            Kicker("The day", color = Muted)
            Spacer(Modifier.height(16.dp))
            Stepper(
                "Starts at",
                rules.resetHour,
                { onChange(rules.copy(resetHour = it)) },
                step = 1, min = 0, max = 12,
                format = { TimeRules.formatClock(it * 60) }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Time refills at this hour. Anything before it still counts against the " +
                    "day before.",
                style = T.micro, color = Muted
            )
        }

        Spacer(Modifier.height(16.dp))

        Card {
            ToggleRow(
                "Bedtime",
                "No codes work inside this window, however much time is left.",
                rules.bedtimeEnabled
            ) { onChange(rules.copy(bedtimeEnabled = it)) }

            if (rules.bedtimeEnabled) {
                Spacer(Modifier.height(18.dp))
                Stepper(
                    "Sleeps at",
                    rules.bedtimeStart,
                    { onChange(rules.copy(bedtimeStart = it)) },
                    step = 30, min = 0, max = 1410,
                    format = { TimeRules.formatClock(it) }
                )
                Spacer(Modifier.height(14.dp))
                Stepper(
                    "Wakes at",
                    rules.bedtimeEnd,
                    { onChange(rules.copy(bedtimeEnd = it)) },
                    step = 30, min = 0, max = 1410,
                    format = { TimeRules.formatClock(it) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card {
            Kicker("Emergency", color = Muted)
            Spacer(Modifier.height(8.dp))
            Text(
                "Always reachable from the lock, whatever the timer says.",
                style = T.micro, color = Muted
            )
            Spacer(Modifier.height(16.dp))
            PlainField(
                value = rules.emergencyName,
                placeholder = "Who to call, for example Mama",
                onChange = { onChange(rules.copy(emergencyName = it)) }
            )
            Spacer(Modifier.height(10.dp))
            PlainField(
                value = rules.emergencyNumber,
                placeholder = "Phone number",
                numeric = true,
                onChange = { onChange(rules.copy(emergencyNumber = it)) }
            )
            if (rules.emergencyNumber.isBlank()) {
                Spacer(Modifier.height(12.dp))
                NoticeBlock(
                    "No emergency number set",
                    "Without one, a child holding a locked phone has no way to reach you.",
                    Amber, AmberSoft
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        SectionHead(
            kicker = "Setup",
            title = "What Oras needs",
            description = "Android keeps each of these behind its own screen."
        )
        Spacer(Modifier.height(20.dp))

        Column {
            SetupRow(
                title = "Home screen",
                buys = "Makes the kids screen the home screen, so Home stays inside their " +
                    "apps instead of escaping to yours.",
                ok = isHome,
                required = true,
                onAction = onFixHome
            )
            SetupRow(
                title = "Draw over other apps",
                buys = "The lock screen itself. Nothing is blocked without it.",
                ok = overlayOk,
                required = true,
                onAction = onFixOverlay
            )
            SetupRow(
                title = "Unrestricted battery",
                buys = "Stops the phone killing the timer in the background.",
                ok = batteryOk,
                required = true,
                onAction = onFixBattery
            )
            SetupRow(
                title = "App blocking",
                buys = "Catches apps opened from outside the kids home screen, like from a " +
                    "notification. Optional, and it reads nothing but the app name.",
                ok = strongLockOn,
                required = false,
                onAction = onOpenAccessibility,
                actionLabel = if (strongLockOn) "Manage" else "Turn on"
            )
        }

        if (needsOem) {
            Spacer(Modifier.height(16.dp))
            NoticeBlock(
                "This phone needs one more step",
                "Xiaomi, Realme, Oppo, Vivo and Infinix phones keep a second list outside " +
                    "the battery screen. If the timer stops overnight, Oras is missing from it.",
                Amber, AmberSoft
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Open autostart settings", onOpenAutostart)
        }

        Spacer(Modifier.height(36.dp))

        Card {
            Kicker("Your code", color = Muted)
            Spacer(Modifier.height(10.dp))
            Text("Six digits", style = T.bodyStrong, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Opens this app and clears any lock. Keep it longer than theirs, and not " +
                    "one they have watched you type.",
                style = T.micro, color = Muted
            )
            Spacer(Modifier.height(16.dp))
            GhostButton("Change my code", onChangeParentCode)
        }

        Spacer(Modifier.height(16.dp))

        Card {
            Kicker("Today", color = Muted)
            Spacer(Modifier.height(10.dp))
            Text("Give back today's time", style = T.bodyStrong, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Refills the whole day and clears the turns taken so far. Earlier days " +
                    "are untouched.",
                style = T.micro, color = Muted
            )
            Spacer(Modifier.height(16.dp))
            GhostButton("Start today over", onResetToday, color = Red)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SetupRow(
    title: String,
    buys: String,
    ok: Boolean,
    required: Boolean,
    onAction: () -> Unit,
    actionLabel: String = "Set up",
) {
    val dot: Color = when {
        ok -> Green
        required -> Red
        else -> Amber
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = Ink)
            Spacer(Modifier.height(3.dp))
            Text(buys, style = T.micro, color = Muted)
        }
        Spacer(Modifier.width(12.dp))
        if (!ok || actionLabel == "Manage") {
            GhostButton(actionLabel, onAction)
        } else {
            Text("On", style = T.label, color = Green, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    placeholder: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Paper)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, style = T.body, color = Faint)
        BasicTextField(
            value = value,
            onValueChange = { input ->
                val cleaned = if (numeric) input.filter { it.isDigit() || it == '+' } else input
                if (cleaned.length <= 24) onChange(cleaned)
            },
            singleLine = true,
            textStyle = (if (numeric) T.label.copy(fontSize = T.body.fontSize) else T.body)
                .copy(color = Ink),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Phone else KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
