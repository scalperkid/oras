package com.jefflim.oras.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jefflim.oras.core.TimeRules
import com.jefflim.oras.data.AppState
import com.jefflim.oras.ui.components.Card
import com.jefflim.oras.ui.components.Hairline
import com.jefflim.oras.ui.components.KidBadge
import com.jefflim.oras.ui.components.Kicker
import com.jefflim.oras.ui.components.SectionHead
import com.jefflim.oras.ui.components.UsageBar
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.Paper
import com.jefflim.oras.ui.theme.Red
import com.jefflim.oras.ui.theme.T
import com.jefflim.oras.ui.theme.hueOf

/**
 * Seven real days. No projections, no averages dressed up as insight: what actually
 * happened, so a conversation about it starts from facts.
 */
@Composable
fun HistoryScreen(state: AppState, now: Long) {
    val days = TimeRules.recentDayKeys(now, state.rules.resetHour, 7)
    val labels = days.map { TimeRules.weekdayLabel(now, state.rules.resetHour, it) }
    val limit = state.rules.dailyTotalMinutes

    // The daily limit is the natural ceiling; a day that went over sets its own.
    val ceiling = maxOf(limit, days.maxOfOrNull { state.usage.totalUsed(it) / 60 } ?: 0)
        .coerceAtLeast(15)

    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            kicker = "History",
            title = "The last seven days",
            description = "Measured while the screen was actually on, not just while a turn " +
                "was open."
        )
        Spacer(Modifier.height(24.dp))

        if (state.kids.isEmpty()) {
            Card {
                Text("Nothing recorded yet", style = T.title, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Once a child takes their first turn, the week shows up here.",
                    style = T.body, color = Muted
                )
            }
            return@Column
        }

        Card {
            Kicker("Whole family", color = Muted)
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { day ->
                    val minutes = state.usage.totalUsed(day) / 60
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (minutes > 0) "$minutes" else "",
                            style = T.label,
                            color = Muted
                        )
                        Spacer(Modifier.height(6.dp))
                        UsageBar(
                            fraction = minutes.toFloat() / ceiling,
                            color = if (minutes > limit) Red else Ink,
                            track = Paper,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEach { label ->
                    Text(
                        label,
                        style = T.label,
                        color = Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Hairline(Line)
            Spacer(Modifier.height(14.dp))
            Text(
                "Daily total is ${TimeRules.formatMinutes(limit)}. A red bar is a day that " +
                    "went over, which only happens when you added time.",
                style = T.micro, color = Muted
            )
        }

        Spacer(Modifier.height(20.dp))
        Kicker("Each child")
        Spacer(Modifier.height(12.dp))

        state.kids.forEachIndexed { index, kid ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            val hue = hueOf(kid.hue)
            val week = days.map { state.usage.secondsUsed(it, kid.id) / 60 }
            val total = week.sum()
            val turns = days.sumOf { state.usage.turnsToday(it, kid.id) }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KidBadge(kid.name, kid.hue, size = 34)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(kid.name, style = T.bodyStrong, color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (turns == 0) "No turns this week"
                            else "${turns} ${if (turns == 1) "turn" else "turns"} this week",
                            style = T.micro, color = Muted
                        )
                    }
                    Text(
                        TimeRules.formatMinutes(total),
                        style = T.metric.copy(fontSize = T.title.fontSize),
                        color = Ink
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    week.forEach { minutes ->
                        UsageBar(
                            fraction = minutes.toFloat() / ceiling,
                            color = hue.tint,
                            track = Paper,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label ->
                        Text(
                            label,
                            style = T.label,
                            color = Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
