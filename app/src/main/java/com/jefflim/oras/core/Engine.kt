package com.jefflim.oras.core

import android.content.Context
import com.jefflim.oras.data.AppState
import com.jefflim.oras.data.EndReason
import com.jefflim.oras.data.GuardMode
import com.jefflim.oras.data.Kid
import com.jefflim.oras.data.Lockout
import com.jefflim.oras.data.Pin
import com.jefflim.oras.data.Session
import com.jefflim.oras.data.Store
import com.jefflim.oras.data.Usage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The single source of truth for whose turn it is and how much of the day is left.
 *
 * The model is deliberately small: one turn length, one shared daily total, and a queue.
 * Finishing a turn puts a child at the back of the queue, which is what stops one of them
 * taking the phone straight back. There is no separate cooldown to explain.
 */
object Engine {

    private lateinit var store: Store
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Advances every second so the UI and notification can recompute the countdown. */
    private val _tick = MutableStateFlow(System.currentTimeMillis())
    val tick: StateFlow<Long> = _tick.asStateFlow()

    /**
     * False until stored state has actually been read back. Anything acting on
     * "not guarding" must wait for this, or it acts on the empty default that exists
     * for the first few milliseconds and shuts the guard down on every launch.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events

    /** Minutes remaining already announced in the current turn. */
    private val warned = mutableSetOf<Int>()

    /** Warn at these many minutes left. Fixed, so it is one less thing to set up. */
    private val WARN_AT = listOf(5, 1)

    /** Child codes are 4 digits, the parent code is 6. Nothing else is accepted. */
    const val KID_CODE_LENGTH = 4
    const val PARENT_CODE_LENGTH = 6

    /** A turn ends on its own after this long with the screen off. */
    private const val IDLE_END_MINUTES = 5

    @Volatile
    private var started = false

    @Volatile
    private var screenOn = true

    sealed interface Event {
        data class Warn(val kid: Kid, val minutesLeft: Int) : Event
        data class Ended(val kid: Kid, val reason: EndReason) : Event
        data class Started(val kid: Kid, val minutes: Int) : Event
    }

    fun init(context: Context) {
        if (started) return
        started = true
        store = Store(context.applicationContext)
        scope.launch {
            store.flow.collect { fromDisk ->
                // Recovery applies to the first read only. Every later emission is our own
                // write coming back, and reconciling those would roll the running countdown
                // back to its last flush, over and over.
                _state.value = if (_loaded.value) {
                    fromDisk
                } else {
                    recoverAfterRestart(fromDisk, System.currentTimeMillis())
                }
                _loaded.value = true
            }
        }
        scope.launch { tickLoop() }
    }

    /**
     * Repairs a turn that was still running when the process died, whether from a reboot,
     * a force stop, or the phone reclaiming memory.
     *
     * The gap is unknowable: the child may have kept using the phone, or it may have been
     * off all night. Charging the gap would mean a reboot silently eats the day's time, so
     * the turn is rolled back to its last flushed point and paused.
     */
    internal fun recoverAfterRestart(fromDisk: AppState, now: Long): AppState {
        val s = fromDisk.session ?: return fromDisk
        if (s.runningSince == null) return fromDisk
        return fromDisk.copy(
            session = s.copy(bankedSeconds = s.committedSeconds, runningSince = null),
            lastPauseAt = now
        )
    }

    private suspend fun tickLoop() {
        while (true) {
            val now = System.currentTimeMillis()
            _tick.value = now
            val st = _state.value
            val s = st.session
            if (s != null && !s.paused) {
                val kid = st.kid(s.kidId)
                val remaining = s.remainingAt(now)
                if (kid != null) {
                    val minutesLeft = (remaining + 59) / 60
                    WARN_AT.firstOrNull { it == minutesLeft && it !in warned }?.let {
                        warned += it
                        _events.tryEmit(Event.Warn(kid, it))
                    }
                }
                if (remaining <= 0) {
                    endSession(EndReason.TIME_UP, now)
                } else if (s.consumedAt(now) - s.committedSeconds >= 30) {
                    // Flush periodically so a battery pull loses seconds, not hours.
                    mutex.withLock { commit(_state.value, now)?.let { persist(it) } }
                }
            } else if (st.guard == GuardMode.ARMED && s != null && s.paused) {
                val idleMs = IDLE_END_MINUTES * 60_000L
                if (st.lastPauseAt > 0 && now - st.lastPauseAt > idleMs) {
                    endSession(EndReason.IDLE, now)
                }
            }
            delay(1000)
        }
    }

    // ---- mutation helpers -------------------------------------------------

    private suspend fun persist(next: AppState) {
        _state.value = next
        store.write(next)
    }

    suspend fun update(block: (AppState) -> AppState) = mutex.withLock {
        persist(block(_state.value))
    }

    /** Moves elapsed seconds out of the live turn and into the day's tally. */
    private fun commit(st: AppState, now: Long): AppState? {
        val s = st.session ?: return null
        val consumed = s.consumedAt(now)
        val delta = consumed - s.committedSeconds
        if (delta <= 0) return null
        val day = TimeRules.dayKey(now, st.rules.resetHour)
        val u = st.usage
        val dayMap = (u.days[day] ?: emptyMap()).toMutableMap()
        dayMap[s.kidId] = (dayMap[s.kidId] ?: 0) + delta
        return st.copy(
            usage = u.copy(days = u.days + (day to dayMap)),
            session = s.copy(committedSeconds = consumed)
        )
    }

    // ---- the shared daily pool -------------------------------------------

    /** Seconds of phone time the children still share today. */
    fun poolLeft(st: AppState, now: Long): Int {
        val day = TimeRules.dayKey(now, st.rules.resetHour)
        val live = st.session?.let { it.consumedAt(now) - it.committedSeconds } ?: 0
        val used = st.usage.totalUsed(day) + live
        return (st.rules.dailyTotalSeconds + st.usage.bonusToday(day) - used).coerceAtLeast(0)
    }

    fun usedToday(st: AppState, now: Long): Int {
        val day = TimeRules.dayKey(now, st.rules.resetHour)
        val live = st.session?.let { it.consumedAt(now) - it.committedSeconds } ?: 0
        return st.usage.totalUsed(day) + live
    }

    // ---- unlocking --------------------------------------------------------

    sealed interface Unlock {
        data class Started(val kid: Kid, val minutes: Int) : Unlock
        data object Parent : Unlock
        data class Wrong(val triesLeft: Int) : Unlock
        data class Frozen(val until: Long) : Unlock
        data class NotYourTurn(val kid: Kid, val whose: Kid) : Unlock
        data object DayDone : Unlock
        data object Bedtime : Unlock

        /** No code matched, but the attempt was only a probe, so nothing was spent. */
        data object NoMatch : Unlock
    }

    /**
     * [countWrong] is false while the keypad is only probing a partially typed code.
     * A parent typing six digits passes through length four on the way, and that must not
     * spend one of the five tries before the freeze.
     */
    suspend fun attemptUnlock(
        code: String,
        countWrong: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): Unlock {
        val st = _state.value

        if (now < st.lockout.frozenUntil) return Unlock.Frozen(st.lockout.frozenUntil)

        // Parent first: a parent must always be able to get in, bedtime or not.
        if (st.rules.parentPin.isNotEmpty() && Pin.verify(code, st.rules.parentPin)) {
            update { it.copy(lockout = Lockout()) }
            return Unlock.Parent
        }

        val kid = st.kids.firstOrNull { it.enabled && it.pin.isNotEmpty() && Pin.verify(code, it.pin) }
            ?: return if (countWrong) registerWrongTry(st, now) else Unlock.NoMatch

        update { it.copy(lockout = Lockout()) }

        if (TimeRules.inBedtime(now, st.rules)) return Unlock.Bedtime

        val whose = st.whoseTurn()
        if (whose != null && whose.id != kid.id) return Unlock.NotYourTurn(kid, whose)

        val pool = poolLeft(st, now)
        if (pool <= 0) return Unlock.DayDone

        val granted = minOf(pool, st.rules.turnSeconds)
        startSession(kid, granted, now)
        return Unlock.Started(kid, (granted + 59) / 60)
    }

    private suspend fun registerWrongTry(st: AppState, now: Long): Unlock {
        val tries = st.lockout.wrongTries + 1
        return if (tries % 5 == 0) {
            // 1 minute, then 2, then 4, capped at 15.
            val step = (tries / 5).coerceAtMost(4)
            val freeze = (60_000L * (1 shl (step - 1))).coerceAtMost(900_000L)
            update { it.copy(lockout = Lockout(tries, now + freeze)) }
            Unlock.Frozen(now + freeze)
        } else {
            update { it.copy(lockout = it.lockout.copy(wrongTries = tries)) }
            Unlock.Wrong(5 - (tries % 5))
        }
    }

    // ---- turns ------------------------------------------------------------

    private suspend fun startSession(kid: Kid, grantedSeconds: Int, now: Long) {
        warned.clear()
        mutex.withLock {
            val st = _state.value
            val day = TimeRules.dayKey(now, st.rules.resetHour)
            val t = (st.usage.turns[day] ?: emptyMap()).toMutableMap()
            t[kid.id] = (t[kid.id] ?: 0) + 1
            persist(
                st.copy(
                    session = Session(
                        kidId = kid.id,
                        startedAt = now,
                        grantedSeconds = grantedSeconds,
                        runningSince = if (screenOn) now else null
                    ),
                    guard = GuardMode.ARMED,
                    usage = st.usage.copy(turns = st.usage.turns + (day to t)),
                    lastPauseAt = if (screenOn) 0L else now,
                )
            )
        }
        _events.tryEmit(Event.Started(kid, (grantedSeconds + 59) / 60))
    }

    suspend fun endSession(reason: EndReason, now: Long = System.currentTimeMillis()) {
        val kid: Kid?
        mutex.withLock {
            val st = _state.value
            val s = st.session ?: return
            kid = st.kid(s.kidId)
            val committed = commit(st, now) ?: st
            // Whoever just finished goes to the back of the queue.
            val enabled = committed.enabledKids
            val idx = enabled.indexOfFirst { it.id == s.kidId }
            persist(
                committed.copy(
                    session = null,
                    queueIndex = if (idx >= 0) idx + 1 else committed.queueIndex,
                    lastPauseAt = 0L,
                )
            )
        }
        warned.clear()
        kid?.let { _events.tryEmit(Event.Ended(it, reason)) }
    }

    /** Adds minutes to the shared pool for today, and to the turn in progress. */
    suspend fun grantBonus(minutes: Int, now: Long = System.currentTimeMillis()) =
        mutex.withLock {
            val st = _state.value
            val day = TimeRules.dayKey(now, st.rules.resetHour)
            val s = st.session
            persist(
                st.copy(
                    usage = st.usage.copy(
                        bonus = st.usage.bonus + (day to (st.usage.bonusToday(day) + minutes * 60))
                    ),
                    session = s?.copy(grantedSeconds = s.grantedSeconds + minutes * 60)
                )
            )
        }

    // ---- screen state -----------------------------------------------------

    /** The countdown only runs while the screen is on, so a pocket costs nobody time. */
    suspend fun setScreenOn(on: Boolean, now: Long = System.currentTimeMillis()) {
        screenOn = on
        mutex.withLock {
            val st = _state.value
            val s = st.session ?: return@withLock
            if (on && s.paused) {
                persist(st.copy(session = s.copy(runningSince = now), lastPauseAt = 0L))
            } else if (!on && !s.paused) {
                persist(
                    st.copy(
                        session = s.copy(bankedSeconds = s.consumedAt(now), runningSince = null),
                        lastPauseAt = now
                    )
                )
            }
        }
    }

    // ---- guard ------------------------------------------------------------

    suspend fun arm() = update { it.copy(guard = GuardMode.ARMED, overlaySuspendedUntil = 0L) }

    suspend fun parentMode() {
        if (_state.value.session != null) endSession(EndReason.PARENT)
        update { it.copy(guard = GuardMode.PARENT) }
    }

    suspend fun disarm() {
        if (_state.value.session != null) endSession(EndReason.PARENT)
        update { it.copy(guard = GuardMode.OFF) }
    }

    /** Steps aside briefly so an emergency call can actually be placed. */
    suspend fun suspendOverlay(minutes: Int, now: Long = System.currentTimeMillis()) =
        update { it.copy(overlaySuspendedUntil = now + minutes * 60_000L) }

    /** True when the lock should be covering the screen right now. */
    fun shouldLock(st: AppState, now: Long): Boolean =
        st.rules.setupDone &&
            st.guard == GuardMode.ARMED &&
            st.session == null &&
            now >= st.overlaySuspendedUntil

    // ---- kids -------------------------------------------------------------

    suspend fun upsertKid(kid: Kid) = update { st ->
        val i = st.kids.indexOfFirst { it.id == kid.id }
        st.copy(kids = if (i >= 0) st.kids.toMutableList().also { it[i] = kid } else st.kids + kid)
    }

    suspend fun removeKid(id: String) = update { st ->
        st.copy(kids = st.kids.filterNot { it.id == id })
    }

    suspend fun moveKid(id: String, delta: Int) = update { st ->
        val list = st.kids.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@update st
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        st.copy(kids = list)
    }

    /** Passes the turn on without spending anyone's time. */
    suspend fun skipTurn() = update { it.copy(queueIndex = it.queueIndex + 1) }

    suspend fun resetToday(now: Long = System.currentTimeMillis()) = update { st ->
        val day = TimeRules.dayKey(now, st.rules.resetHour)
        st.copy(
            usage = Usage(
                days = st.usage.days - day,
                turns = st.usage.turns - day,
                bonus = st.usage.bonus - day,
            )
        )
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)
}
