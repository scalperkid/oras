package com.jefflim.oras

import com.jefflim.oras.core.Engine
import com.jefflim.oras.core.TimeRules
import com.jefflim.oras.data.AppState
import com.jefflim.oras.data.GuardMode
import com.jefflim.oras.data.Kid
import com.jefflim.oras.data.Pin
import com.jefflim.oras.data.Rules
import com.jefflim.oras.data.Session
import com.jefflim.oras.data.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The arithmetic a parent has to be able to trust: which day a minute counts against,
 * whose turn it is, whether a code opens anything, and how much of the day is left.
 */
class RulesTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val ana = Kid(id = "a", name = "Ana")
    private val ben = Kid(id = "b", name = "Ben")
    private val cy = Kid(id = "c", name = "Cy")

    // ---- day boundaries ---------------------------------------------------

    @Test
    fun `late night counts against the day it started`() {
        val tuesdayNight = at(2026, 8, 18, 23, 30)
        val wednesdayEarly = at(2026, 8, 19, 1, 15)
        assertEquals(
            TimeRules.dayKey(tuesdayNight, 6),
            TimeRules.dayKey(wednesdayEarly, 6)
        )
    }

    @Test
    fun `the day flips at the reset hour, not at midnight`() {
        assertNotEquals(
            TimeRules.dayKey(at(2026, 8, 19, 5, 59), 6),
            TimeRules.dayKey(at(2026, 8, 19, 6, 1), 6)
        )
    }

    @Test
    fun `seven recent days are distinct and end with today`() {
        val now = at(2026, 8, 19, 12, 0)
        val keys = TimeRules.recentDayKeys(now, 6, 7)
        assertEquals(7, keys.size)
        assertEquals(7, keys.toSet().size)
        assertEquals(TimeRules.dayKey(now, 6), keys.last())
    }

    // ---- bedtime ----------------------------------------------------------

    @Test
    fun `bedtime window wraps past midnight`() {
        val rules = Rules(bedtimeEnabled = true, bedtimeStart = 20 * 60, bedtimeEnd = 6 * 60)
        assertTrue(TimeRules.inBedtime(at(2026, 8, 19, 21, 0), rules))
        assertTrue(TimeRules.inBedtime(at(2026, 8, 19, 2, 0), rules))
        assertFalse(TimeRules.inBedtime(at(2026, 8, 19, 6, 0), rules))
        assertFalse(TimeRules.inBedtime(at(2026, 8, 19, 15, 0), rules))
    }

    @Test
    fun `bedtime off means never in bedtime`() {
        val rules = Rules(bedtimeEnabled = false, bedtimeStart = 20 * 60, bedtimeEnd = 6 * 60)
        assertFalse(TimeRules.inBedtime(at(2026, 8, 19, 23, 0), rules))
    }

    // ---- turn arithmetic --------------------------------------------------

    @Test
    fun `a running turn burns real seconds`() {
        val start = at(2026, 8, 19, 10, 0)
        val s = Session("a", start, grantedSeconds = 1200, runningSince = start)
        assertEquals(1200, s.remainingAt(start))
        assertEquals(1140, s.remainingAt(start + 60_000))
        assertEquals(0, s.remainingAt(start + 1_300_000))
    }

    @Test
    fun `a paused turn does not burn time`() {
        val start = at(2026, 8, 19, 10, 0)
        val s = Session("a", start, grantedSeconds = 1200, bankedSeconds = 120, runningSince = null)
        assertEquals(1080, s.remainingAt(start + 3_600_000))
        assertTrue(s.paused)
    }

    // ---- the shared daily pool -------------------------------------------

    @Test
    fun `every child draws from the same daily pool`() {
        val now = at(2026, 8, 19, 14, 0)
        val day = TimeRules.dayKey(now, 6)
        val st = AppState(
            rules = Rules(dailyTotalMinutes = 120),
            kids = listOf(ana, ben),
            usage = Usage(days = mapOf(day to mapOf("a" to 40 * 60, "b" to 30 * 60)))
        )
        // 120 total, 70 spent between the two of them.
        assertEquals(50 * 60, Engine.poolLeft(st, now))
        assertEquals(70 * 60, Engine.usedToday(st, now))
    }

    @Test
    fun `a turn is trimmed to what the day has left`() {
        val now = at(2026, 8, 19, 14, 0)
        val day = TimeRules.dayKey(now, 6)
        val st = AppState(
            rules = Rules(turnMinutes = 20, dailyTotalMinutes = 120),
            kids = listOf(ana),
            usage = Usage(days = mapOf(day to mapOf("a" to 112 * 60)))
        )
        // Only 8 minutes left, so a 20 minute turn cannot be granted in full.
        val pool = Engine.poolLeft(st, now)
        assertEquals(8 * 60, pool)
        assertEquals(8 * 60, minOf(pool, st.rules.turnSeconds))
    }

    @Test
    fun `bonus time tops the shared pool back up`() {
        val now = at(2026, 8, 19, 14, 0)
        val day = TimeRules.dayKey(now, 6)
        val st = AppState(
            rules = Rules(dailyTotalMinutes = 60),
            kids = listOf(ana),
            usage = Usage(
                days = mapOf(day to mapOf("a" to 60 * 60)),
                bonus = mapOf(day to 15 * 60)
            )
        )
        assertEquals(15 * 60, Engine.poolLeft(st, now))
    }

    @Test
    fun `a spent day cannot go below zero`() {
        val now = at(2026, 8, 19, 14, 0)
        val day = TimeRules.dayKey(now, 6)
        val st = AppState(
            rules = Rules(dailyTotalMinutes = 60),
            kids = listOf(ana),
            usage = Usage(days = mapOf(day to mapOf("a" to 999 * 60)))
        )
        assertEquals(0, Engine.poolLeft(st, now))
    }

    @Test
    fun `yesterdays usage does not touch today`() {
        val now = at(2026, 8, 19, 14, 0)
        val yesterday = TimeRules.dayKey(now - 86_400_000L, 6)
        val st = AppState(
            rules = Rules(dailyTotalMinutes = 60),
            kids = listOf(ana),
            usage = Usage(days = mapOf(yesterday to mapOf("a" to 60 * 60)))
        )
        assertEquals(60 * 60, Engine.poolLeft(st, now))
    }

    // ---- the rotation -----------------------------------------------------

    @Test
    fun `the queue rotates through every child in order`() {
        val kids = listOf(ana, ben, cy)
        assertEquals("Ana", AppState(kids = kids, queueIndex = 0).whoseTurn()?.name)
        assertEquals("Ben", AppState(kids = kids, queueIndex = 1).whoseTurn()?.name)
        assertEquals("Cy", AppState(kids = kids, queueIndex = 2).whoseTurn()?.name)
        // Back to the start, so nobody goes twice before everyone has been.
        assertEquals("Ana", AppState(kids = kids, queueIndex = 3).whoseTurn()?.name)
    }

    @Test
    fun `a paused child is skipped by the rotation`() {
        val kids = listOf(ana, ben.copy(enabled = false), cy)
        assertEquals("Ana", AppState(kids = kids, queueIndex = 0).whoseTurn()?.name)
        assertEquals("Cy", AppState(kids = kids, queueIndex = 1).whoseTurn()?.name)
        assertEquals("Ana", AppState(kids = kids, queueIndex = 2).whoseTurn()?.name)
    }

    @Test
    fun `no children means nobody is up`() {
        assertEquals(null, AppState().whoseTurn())
    }

    // ---- recovery after the process dies -----------------------------------

    @Test
    fun `a reboot does not eat the day's time`() {
        val start = at(2026, 8, 19, 10, 0)
        val st = AppState(
            kids = listOf(ana),
            session = Session(
                kidId = "a", startedAt = start, grantedSeconds = 20 * 60,
                bankedSeconds = 0, runningSince = start, committedSeconds = 9 * 60
            )
        )
        val after = Engine.recoverAfterRestart(st, start + 3_600_000)
        val s = after.session!!
        assertTrue("the turn must be paused, not still running", s.paused)
        assertEquals(9 * 60, s.bankedSeconds)
        assertEquals(11 * 60, s.remainingAt(start + 3_600_000))
    }

    @Test
    fun `a turn already paused survives a restart untouched`() {
        val start = at(2026, 8, 19, 10, 0)
        val st = AppState(
            session = Session(
                kidId = "a", startedAt = start, grantedSeconds = 1200,
                bankedSeconds = 600, runningSince = null, committedSeconds = 600
            )
        )
        assertEquals(st, Engine.recoverAfterRestart(st, start + 99_000))
    }

    // ---- locking ----------------------------------------------------------

    @Test
    fun `the lock is up when armed with no turn running`() {
        val now = at(2026, 8, 19, 14, 0)
        val st = AppState(rules = Rules(setupDone = true), guard = GuardMode.ARMED)
        assertTrue(Engine.shouldLock(st, now))
    }

    @Test
    fun `the lock steps aside during an emergency call`() {
        val now = at(2026, 8, 19, 14, 0)
        val st = AppState(
            rules = Rules(setupDone = true),
            guard = GuardMode.ARMED,
            overlaySuspendedUntil = now + 60_000
        )
        assertFalse(Engine.shouldLock(st, now))
        assertTrue(Engine.shouldLock(st, now + 61_000))
    }

    @Test
    fun `nothing locks before setup is finished`() {
        assertFalse(Engine.shouldLock(AppState(guard = GuardMode.ARMED), at(2026, 8, 19, 14, 0)))
    }

    // ---- codes ------------------------------------------------------------

    @Test
    fun `a code verifies against its own hash and nothing else`() {
        val stored = Pin.hash("1234")
        assertTrue(Pin.verify("1234", stored))
        assertFalse(Pin.verify("1235", stored))
        assertFalse(Pin.verify("", stored))
        assertFalse(Pin.verify("12345", stored))
    }

    @Test
    fun `the same code hashes differently every time`() {
        assertNotEquals(Pin.hash("1234"), Pin.hash("1234"))
    }

    @Test
    fun `the stored value never contains the code`() {
        assertFalse(Pin.hash("4821").contains("4821"))
    }

    @Test
    fun `a malformed stored value is rejected rather than crashing`() {
        assertFalse(Pin.verify("1234", "not-a-real-hash"))
        assertFalse(Pin.verify("1234", ""))
        assertFalse(Pin.verify("1234", "a:b:c"))
    }

    // ---- app limits -------------------------------------------------------

    @Test
    fun `no chosen apps means no limit`() {
        assertFalse(ana.hasAppLimit)
        assertTrue(ana.copy(allowedApps = setOf("com.example")).hasAppLimit)
    }

    // ---- formatting -------------------------------------------------------

    @Test
    fun `durations read correctly at every scale`() {
        assertEquals("00:00", TimeRules.formatDuration(0))
        assertEquals("01:00", TimeRules.formatDuration(60))
        assertEquals("59:59", TimeRules.formatDuration(3599))
        assertEquals("1:00:00", TimeRules.formatDuration(3600))
        assertEquals("00:00", TimeRules.formatDuration(-5))
    }

    @Test
    fun `minutes read the way a parent would say them`() {
        assertEquals("45m", TimeRules.formatMinutes(45))
        assertEquals("1h", TimeRules.formatMinutes(60))
        assertEquals("2h", TimeRules.formatMinutes(120))
        assertEquals("1h 25m", TimeRules.formatMinutes(85))
        assertEquals("0m", TimeRules.formatMinutes(0))
    }

    @Test
    fun `clock labels use twelve hour time with a correct noon and midnight`() {
        assertEquals("12:00 AM", TimeRules.formatClock(0))
        assertEquals("6:30 AM", TimeRules.formatClock(6 * 60 + 30))
        assertEquals("12:00 PM", TimeRules.formatClock(12 * 60))
        assertEquals("8:00 PM", TimeRules.formatClock(20 * 60))
    }
}
