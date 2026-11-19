package com.jefflim.oras.core

import com.jefflim.oras.data.Rules
import java.util.Calendar
import java.util.Locale

/**
 * All day boundaries and bedtime windows live here, in local wall-clock time.
 * Kept separate from the engine so the arithmetic can be reasoned about on its own.
 */
object TimeRules {

    /**
     * The key for "which day does this instant belong to", honouring the reset hour.
     * With resetHour = 6, anything before 6am still counts against yesterday's budget,
     * which is what a parent means by "the day".
     */
    fun dayKey(now: Long, resetHour: Int): String {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        c.add(Calendar.HOUR_OF_DAY, -resetHour)
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** The previous [count] day keys, oldest first, ending with today. */
    fun recentDayKeys(now: Long, resetHour: Int, count: Int): List<String> =
        (count - 1 downTo 0).map { dayKey(now - it * 86_400_000L, resetHour) }

    /** Short label for a day key, e.g. "Mon". */
    fun weekdayLabel(now: Long, resetHour: Int, dayKey: String): String {
        val c = Calendar.getInstance()
        for (i in 0 until 400) {
            val t = now - i * 86_400_000L
            if (dayKey(t, resetHour) == dayKey) {
                c.timeInMillis = t
                c.add(Calendar.HOUR_OF_DAY, -resetHour)
                return arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[
                    c.get(Calendar.DAY_OF_WEEK) - 1
                ]
            }
        }
        return ""
    }

    fun minutesIntoDay(now: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Bedtime windows normally wrap midnight, e.g. 20:00 to 06:00. */
    fun inBedtime(now: Long, rules: Rules): Boolean {
        if (!rules.bedtimeEnabled) return false
        if (rules.bedtimeStart == rules.bedtimeEnd) return false
        val m = minutesIntoDay(now)
        return if (rules.bedtimeStart < rules.bedtimeEnd) {
            m >= rules.bedtimeStart && m < rules.bedtimeEnd
        } else {
            m >= rules.bedtimeStart || m < rules.bedtimeEnd
        }
    }

    fun formatClock(minutesIntoDay: Int): String {
        val h24 = (minutesIntoDay / 60) % 24
        val m = minutesIntoDay % 60
        val suffix = if (h24 < 12) "AM" else "PM"
        val h12 = when {
            h24 % 12 == 0 -> 12
            else -> h24 % 12
        }
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
    }

    /** mm:ss under an hour, h:mm:ss above. Always fixed width within a band. */
    fun formatDuration(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    /** Human phrasing for budgets and summaries, e.g. "1h 25m", "45m". */
    fun formatMinutes(totalMinutes: Int): String {
        val m = totalMinutes.coerceAtLeast(0)
        val h = m / 60
        val rem = m % 60
        return when {
            h > 0 && rem > 0 -> "${h}h ${rem}m"
            h > 0 -> "${h}h"
            else -> "${rem}m"
        }
    }
}
