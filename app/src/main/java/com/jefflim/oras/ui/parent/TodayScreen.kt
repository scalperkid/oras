package com.jefflim.oras.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jefflim.oras.core.Engine
import com.jefflim.oras.core.TimeRules
import com.jefflim.oras.data.AppState
import com.jefflim.oras.data.GuardMode
import com.jefflim.oras.data.Kid
import com.jefflim.oras.ui.components.Card
import com.jefflim.oras.ui.components.GhostButton
import com.jefflim.oras.ui.components.Hairline
import com.jefflim.oras.ui.components.KidBadge
import com.jefflim.oras.ui.components.Kicker
import com.jefflim.oras.ui.components.NoticeBlock
import com.jefflim.oras.ui.components.PrimaryButton
import com.jefflim.oras.ui.components.StatusTag
import com.jefflim.oras.ui.components.tap
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.AccentSoft
import com.jefflim.oras.ui.theme.Amber
import com.jefflim.oras.ui.theme.Faint
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.InkLine
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.OnInk
import com.jefflim.oras.ui.theme.OnInkMuted
import com.jefflim.oras.ui.theme.Paper
import com.jefflim.oras.ui.theme.Red
import com.jefflim.oras.ui.theme.RedSoft
import com.jefflim.oras.ui.theme.Surface
import com.jefflim.oras.ui.theme.T
import com.jefflim.oras.ui.theme.hueOf

/**
 * What is happening right now, and the one action a parent actually reaches for:
 * hand the phone over, or take it back.
 */
@Composable
fun TodayScreen(
    state: AppState,
    now: Long,
    setupProblem: String?,
    onArm: () -> Unit,
    onParentMode: () -> Unit,
    onEndTurn: () -> Unit,
    onBonus: (Int) -> Unit,
    onSkipTurn: () -> Unit,
    onFixSetup: () -> Unit,
    onAddKid: () -> Unit,
) {
    val session = state.session
    val activeKid = state.kid(session?.kidId)
    val day = TimeRules.dayKey(now, state.rules.resetHour)
    val poolLeft = Engine.poolLeft(state, now)
    val usedToday = Engine.usedToday(state, now)

    Column(Modifier.fillMaxWidth()) {

        if (setupProblem != null) {
            NoticeBlock(
                title = "Needs one more step",
                body = setupProblem,
                fg = Red,
                bg = RedSoft
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Fix it", onFixSetup, color = Red)
            Spacer(Modifier.height(28.dp))
        }

        when {
            session != null && activeKid != null -> RunningBand(
                kid = activeKid,
                remaining = session.remainingAt(now),
                paused = session.paused,
                granted = session.grantedSeconds,
                onBonus = onBonus
            )

            state.guard == GuardMode.ARMED -> LockedBand(
                whose = state.whoseTurn(),
                bedtime = TimeRules.inBedtime(now, state.rules),
                bedtimeEnd = state.rules.bedtimeEnd,
                poolLeftMinutes = poolLeft / 60,
                onSkipTurn = onSkipTurn
            )

            else -> OpenBand(hasKids = state.kids.isNotEmpty())
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state.guard) {
                GuardMode.ARMED -> {
                    GhostButton("Parent mode", onParentMode, Modifier.weight(1f))
                    if (session != null) {
                        GhostButton("End turn", onEndTurn, Modifier.weight(1f), color = Red)
                    }
                }

                else -> {
                    if (state.kids.isNotEmpty()) {
                        PrimaryButton("Hand the phone over", onArm, Modifier.weight(1f))
                    } else {
                        PrimaryButton("Add your first child", onAddKid, Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (state.kids.isNotEmpty()) {
            // The one number that matters: how much of today is left for everyone.
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker("Today")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${TimeRules.formatMinutes(poolLeft / 60)} left",
                            style = T.metric,
                            color = if (poolLeft <= 0) Red else Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "of ${TimeRules.formatMinutes(state.rules.dailyTotalMinutes)} shared, " +
                                "back at ${TimeRules.formatClock(state.rules.resetHour * 60)}",
                            style = T.micro,
                            color = Muted
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    GhostButton("+15 min", { onBonus(15) })
                }
                Spacer(Modifier.height(16.dp))
                PoolTrack(used = usedToday, total = state.rules.dailyTotalSeconds)
            }

            Spacer(Modifier.height(20.dp))
            Kicker("The queue")
            Spacer(Modifier.height(12.dp))

            Card(padding = PaddingValues(0.dp)) {
                val order = state.enabledKids
                val turnIdx = order.indexOfFirst { it.id == state.whoseTurn()?.id }
                order.forEachIndexed { i, kid ->
                    if (i > 0) Hairline()
                    QueueRow(
                        kid = kid,
                        state = state,
                        day = day,
                        position = if (turnIdx < 0) i else ((i - turnIdx) + order.size) % order.size,
                        isActive = session?.kidId == kid.id
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningBand(
    kid: Kid,
    remaining: Int,
    paused: Boolean,
    granted: Int,
    onBonus: (Int) -> Unit,
) {
    val h = hueOf(kid.hue)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KidBadge(kid.name, kid.hue, size = 38, onDark = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${kid.name}'s turn", style = T.title, color = OnInk)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (paused) "Paused, the screen is off" else "On the phone now",
                    style = T.micro,
                    color = OnInkMuted
                )
            }
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (paused) Amber else h.tint)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            TimeRules.formatDuration(remaining),
            style = T.clock,
            color = OnInk,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "of a ${TimeRules.formatMinutes(granted / 60)} turn",
            style = T.label,
            color = OnInkMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(InkLine)
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15).forEach { m ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentSoft.copy(alpha = 0.12f))
                        .tap { onBonus(m) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+$m min", style = T.bodyStrong, color = Accent)
                }
            }
        }
    }
}

@Composable
private fun LockedBand(
    whose: Kid?,
    bedtime: Boolean,
    bedtimeEnd: Int,
    poolLeftMinutes: Int,
    onSkipTurn: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink)
            .padding(24.dp)
    ) {
        StatusTag(
            if (bedtime) "Bedtime" else if (poolLeftMinutes <= 0) "Done for today" else "Locked",
            Paper,
            Accent
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                bedtime -> "Asleep until ${TimeRules.formatClock(bedtimeEnd)}"
                poolLeftMinutes <= 0 -> "No time left today"
                whose != null -> "${whose.name}'s turn next"
                else -> "Waiting for a code"
            },
            style = T.h2,
            color = OnInk
        )
        if (whose != null && !bedtime && poolLeftMinutes > 0) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KidBadge(whose.name, whose.hue, size = 32, onDark = true)
                Spacer(Modifier.width(10.dp))
                Text(
                    "They enter their own code to start",
                    style = T.micro,
                    color = OnInkMuted,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(OnInk.copy(alpha = 0.10f))
                        .tap { onSkipTurn() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Skip", style = T.bodyStrong, color = OnInkMuted)
                }
            }
        }
    }
}

@Composable
private fun OpenBand(hasKids: Boolean) {
    Card(padding = PaddingValues(24.dp)) {
        Kicker("Not guarding")
        Spacer(Modifier.height(12.dp))
        Text(if (hasKids) "The phone is yours" else "Nobody is set up yet", style = T.h2, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasKids) {
                "Nothing is counting and nothing is locked. Hand the phone over when you " +
                    "are ready and the lock takes over from there."
            } else {
                "Add each child with their own code. Everything about time is set once, " +
                    "for all of them together."
            },
            style = T.body,
            color = Muted
        )
    }
}

@Composable
private fun QueueRow(
    kid: Kid,
    state: AppState,
    day: String,
    position: Int,
    isActive: Boolean,
) {
    val used = state.usage.secondsUsed(day, kid.id) / 60
    val turns = state.usage.turnsToday(day, kid.id)

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isActive) AccentSoft.copy(alpha = 0.35f) else Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (position == 0) Ink else Line),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${position + 1}",
                style = T.label,
                color = if (position == 0) Paper else Muted
            )
        }
        Spacer(Modifier.width(12.dp))
        KidBadge(kid.name, kid.hue, size = 34)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kid.name, style = T.bodyStrong, color = if (kid.enabled) Ink else Faint)
                if (position == 0 && !isActive) {
                    Spacer(Modifier.width(8.dp))
                    StatusTag("Up next", Paper, Ink)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (turns == 0) "No turns yet today"
                else "${TimeRules.formatMinutes(used)} over ${if (turns == 1) "1 turn" else "$turns turns"}",
                style = T.micro,
                color = Muted
            )
        }
    }
}

/** One honest bar: how much of the shared day is spent. */
@Composable
private fun PoolTrack(used: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Line)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (fraction >= 1f) Red else Accent)
        )
    }
}
