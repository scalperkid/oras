package com.jefflim.oras.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jefflim.oras.core.Engine
import com.jefflim.oras.core.TimeRules
import com.jefflim.oras.data.Kid
import com.jefflim.oras.ui.components.KidBadge
import com.jefflim.oras.ui.components.tap
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.InkDeep
import com.jefflim.oras.ui.theme.InkLine
import com.jefflim.oras.ui.theme.InkRaised
import com.jefflim.oras.ui.theme.OnInk
import com.jefflim.oras.ui.theme.OnInkFaint
import com.jefflim.oras.ui.theme.OnInkMuted
import com.jefflim.oras.ui.theme.T
import com.jefflim.oras.ui.theme.hueOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The lock. A child sees this more than any other screen, so it stays calm: dark ground,
 * their own name, and an honest reason whenever a code is refused.
 *
 * It names whose turn it is rather than asking. The queue has already decided, and saying
 * so out loud is what settles the argument before it starts.
 */
@Composable
fun LockContent(onEmergency: () -> Unit) {
    val state by Engine.state.collectAsStateWithLifecycle()
    val now by Engine.tick.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<LockMessage?>(null) }
    var parentPanel by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(4_500)
            message = null
        }
    }

    val frozenFor = ((state.lockout.frozenUntil - now) / 1000).toInt()
    val bedtime = TimeRules.inBedtime(now, state.rules)
    val whose = state.whoseTurn()
    val poolLeft = Engine.poolLeft(state, now)
    val dayDone = poolLeft <= 0 && state.kids.isNotEmpty()

    fun submit(code: String, countWrong: Boolean) {
        scope.launch {
            when (val r = Engine.attemptUnlock(code, countWrong)) {
                Engine.Unlock.NoMatch -> Unit

                is Engine.Unlock.Started -> {
                    entry = ""
                    message = null
                }

                Engine.Unlock.Parent -> {
                    entry = ""
                    message = null
                    parentPanel = true
                }

                is Engine.Unlock.Wrong -> {
                    entry = ""
                    message = LockMessage(
                        "That code did not match",
                        if (r.triesLeft <= 2) "${r.triesLeft} more tries before a short wait" else null
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    shake.animateTo(1f, tween(60))
                    shake.animateTo(-1f, tween(60))
                    shake.animateTo(1f, tween(60))
                    shake.animateTo(0f, tween(60))
                }

                is Engine.Unlock.Frozen -> {
                    entry = ""
                    message = LockMessage("Too many tries", "The keypad rests for a moment")
                }

                Engine.Unlock.DayDone -> {
                    entry = ""
                    message = LockMessage(
                        "That is all the phone time for today",
                        "It comes back at ${TimeRules.formatClock(state.rules.resetHour * 60)}"
                    )
                }

                is Engine.Unlock.NotYourTurn -> {
                    entry = ""
                    message = LockMessage(
                        "It is ${r.whose.name}'s turn",
                        "Yours comes after theirs"
                    )
                }

                Engine.Unlock.Bedtime -> {
                    entry = ""
                    message = LockMessage(
                        "The phone is asleep",
                        "It wakes at ${TimeRules.formatClock(state.rules.bedtimeEnd)}"
                    )
                }
            }
        }
    }

    fun press(digit: String) {
        if (frozenFor > 0) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (entry.length >= Engine.PARENT_CODE_LENGTH) return
        entry += digit

        when (entry.length) {
            Engine.KID_CODE_LENGTH -> {
                val probe = entry
                submit(probe, countWrong = false)
                // A child who mistypes would otherwise sit in front of a lock that did
                // nothing. Long enough that a parent pausing between digits five and six
                // is not caught by it, short enough that a child is not left staring.
                scope.launch {
                    delay(2_200)
                    if (entry == probe) submit(probe, countWrong = true)
                }
            }

            Engine.PARENT_CODE_LENGTH -> submit(entry, countWrong = true)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(InkDeep)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    TimeRules.formatClock(TimeRules.minutesIntoDay(now)),
                    style = T.label,
                    color = OnInkFaint
                )
                if (!bedtime && !dayDone && state.kids.isNotEmpty()) {
                    Text(
                        "${TimeRules.formatMinutes(poolLeft / 60)} left today",
                        style = T.label,
                        color = OnInkFaint
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        parentPanel -> Heading(
                            "Your phone",
                            "Give more time, or take it back."
                        )

                        bedtime -> Heading(
                            "Good night",
                            "The phone wakes up at ${TimeRules.formatClock(state.rules.bedtimeEnd)}"
                        )

                        dayDone -> Heading(
                            "That is all for today",
                            "Time comes back at ${TimeRules.formatClock(state.rules.resetHour * 60)}"
                        )

                        state.kids.isEmpty() -> Heading(
                            "No one is set up yet",
                            "Enter your parent code to get started"
                        )

                        whose != null -> WhoseTurn(whose)

                        else -> Heading("Locked", "Enter your code")
                    }

                    if (!parentPanel) {
                        Spacer(Modifier.height(26.dp))
                        Box(Modifier.graphicsLayer { translationX = shake.value * 14f }) {
                            Dots(entry.length)
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Box(Modifier.heightIn(min = 54.dp), contentAlignment = Alignment.TopCenter) {
                        RefusalSlot(visible = frozenFor > 0 || message != null) {
                            if (frozenFor > 0) {
                                Refusal(
                                    "Wait ${TimeRules.formatDuration(frozenFor)}",
                                    "Too many wrong codes"
                                )
                            } else {
                                message?.let { Refusal(it.title, it.detail) }
                            }
                        }
                    }
                }
            }

            if (parentPanel) {
                ParentPanel(
                    poolLeftMinutes = poolLeft / 60,
                    onBonus = { minutes ->
                        scope.launch {
                            Engine.grantBonus(minutes)
                            parentPanel = false
                            message = LockMessage(
                                "Added ${TimeRules.formatMinutes(minutes)}",
                                "Whoever is next can start now"
                            )
                        }
                    },
                    onTakePhone = { scope.launch { Engine.parentMode() } },
                    onBack = { parentPanel = false }
                )
            } else {
                Keypad(
                    enabled = frozenFor <= 0,
                    onDigit = ::press,
                    onBackspace = {
                        if (entry.isNotEmpty()) {
                            entry = entry.dropLast(1)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onConfirm = { if (entry.length >= Engine.KID_CODE_LENGTH) submit(entry, true) }
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!parentPanel && state.rules.emergencyNumber.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .border(BorderStroke(1.dp, InkLine), RoundedCornerShape(999.dp))
                        .tap { onEmergency() }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        state.rules.emergencyName.ifBlank { "Call for help" },
                        style = T.bodyStrong,
                        color = OnInkMuted
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private data class LockMessage(val title: String, val detail: String?)

/** Names the child whose turn it is. The queue decided; this just says so. */
@Composable
private fun WhoseTurn(kid: Kid) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KidBadge(kid.name, kid.hue, size = 64, onDark = true)
        Spacer(Modifier.height(16.dp))
        Text(
            "${kid.name}'s turn",
            style = T.h1,
            color = OnInk,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text("Enter your code", style = T.body, color = OnInkMuted)
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = T.h1, color = OnInk, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = T.body, color = OnInkMuted, textAlign = TextAlign.Center)
    }
}

/**
 * Declared at top level on purpose: inside the lock's Column, the ColumnScope overload of
 * AnimatedVisibility wins overload resolution and refuses a plain content lambda.
 */
@Composable
private fun RefusalSlot(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 3 },
        exit = fadeOut(tween(150))
    ) {
        content()
    }
}

/**
 * What a parent sees after entering their code. Adding time and taking the phone away are
 * both one tap, and neither is the default.
 */
@Composable
private fun ParentPanel(
    poolLeftMinutes: Int,
    onBonus: (Int) -> Unit,
    onTakePhone: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${TimeRules.formatMinutes(poolLeftMinutes)} LEFT TODAY",
            style = T.label,
            color = OnInkFaint
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(10, 20, 30).forEach { m ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Accent)
                        .tap { onBonus(m) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+$m min", style = T.bodyStrong, color = InkDeep)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelButton("Take the phone", Modifier.weight(1f), onTakePhone)
            PanelButton("Back", Modifier.weight(1f), onBack)
        }
    }
}

@Composable
private fun PanelButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, InkLine), RoundedCornerShape(12.dp))
            .tap(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = T.bodyStrong, color = OnInkMuted)
    }
}

@Composable
private fun Dots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxOf(4, filled)) { i ->
            val on = i < filled
            val scale by animateFloatAsState(if (on) 1f else 0.72f, tween(180), label = "dot")
            Box(
                Modifier
                    .size(14.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(if (on) Accent else InkLine)
            )
        }
    }
}

@Composable
private fun Refusal(title: String, detail: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = T.bodyStrong, color = OnInk, textAlign = TextAlign.Center)
        detail?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = T.micro, color = OnInkMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("del", "0", "ok"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    Key(key, enabled, Modifier.weight(1f)) {
                        when (key) {
                            "del" -> onBackspace()
                            "ok" -> onConfirm()
                            else -> onDigit(key)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(key: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = when (key) {
        "del" -> "⌫"
        "ok" -> "OK"
        else -> key
    }
    val accent = key == "ok"
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "key")

    Box(
        modifier
            .aspectRatio(1.45f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) Accent.copy(alpha = if (enabled) 1f else 0.3f) else InkRaised)
            .tap(enabled = enabled) {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (key == "ok") T.bodyStrong.copy(fontSize = 15.sp) else T.keypad,
            color = when {
                !enabled -> OnInkFaint
                accent -> InkDeep
                else -> OnInk
            }
        )
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(120)
            pressed = false
        }
    }
}

/** Top banner shown a few minutes before time runs out. Brief, non-blocking. */
@Composable
fun WarningBanner(name: String, hue: Int, minutesLeft: Int) {
    val h = hueOf(hue)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(InkRaised)
                .border(BorderStroke(1.dp, InkLine), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KidBadge(name, hue, size = 34, onDark = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (minutesLeft <= 1) "$name, one minute left"
                    else "$name, $minutesLeft minutes left",
                    style = T.bodyStrong,
                    color = OnInk
                )
                Spacer(Modifier.height(2.dp))
                Text("Good time to find a stopping point", style = T.micro, color = OnInkMuted)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(h.tint)
            )
        }
    }
}
