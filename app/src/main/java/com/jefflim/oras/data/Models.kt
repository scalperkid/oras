package com.jefflim.oras.data

import kotlinx.serialization.Serializable

/**
 * A child is a name, a colour, a code, and the apps they may open. Nothing about time
 * lives here: turn length and the daily total are the same for everyone, set once in
 * [Rules], because three numbers per child is more than any parent wants to maintain.
 */
@Serializable
data class Kid(
    val id: String,
    val name: String,
    /** Index into KidHues. */
    val hue: Int = 0,
    /** Salted PBKDF2 hash, never the code itself. */
    val pin: String = "",
    val enabled: Boolean = true,
    /**
     * Packages this child may open. Set by a parent only, from inside the code-gated part
     * of the app. Empty means every app, which is the default so nobody is locked out
     * by accident.
     */
    val allowedApps: Set<String> = emptySet(),
) {
    val hasAppLimit: Boolean get() = allowedApps.isNotEmpty()
}

@Serializable
data class Rules(
    val parentPin: String = "",

    /** How long one turn lasts, for every child. */
    val turnMinutes: Int = 20,

    /** How much phone time the children share in a day, across all of them. */
    val dailyTotalMinutes: Int = 120,

    /** Hour the daily total refills. 6 means "the day starts at 6am". */
    val resetHour: Int = 6,

    val bedtimeEnabled: Boolean = true,
    /** Minutes from midnight. Wraps across midnight when start > end. */
    val bedtimeStart: Int = 20 * 60,
    val bedtimeEnd: Int = 6 * 60,

    val emergencyName: String = "",
    val emergencyNumber: String = "",

    /** The launcher to fall back to when no child is using the phone. */
    val parentLauncher: String = "",

    val setupDone: Boolean = false,
) {
    val turnSeconds: Int get() = turnMinutes * 60
    val dailyTotalSeconds: Int get() = dailyTotalMinutes * 60
}

@Serializable
data class Usage(
    /** dayKey -> kidId -> seconds used. Kept per child for history; the cap is the sum. */
    val days: Map<String, Map<String, Int>> = emptyMap(),
    /** dayKey -> kidId -> turns taken */
    val turns: Map<String, Map<String, Int>> = emptyMap(),
    /** dayKey -> extra seconds a parent added to the shared pool */
    val bonus: Map<String, Int> = emptyMap(),
) {
    fun secondsUsed(day: String, kidId: String) = days[day]?.get(kidId) ?: 0
    fun turnsToday(day: String, kidId: String) = turns[day]?.get(kidId) ?: 0
    fun bonusToday(day: String) = bonus[day] ?: 0
    fun totalUsed(day: String) = days[day]?.values?.sum() ?: 0
}

@Serializable
data class Session(
    val kidId: String,
    val startedAt: Long,
    /** Seconds allowed for this turn, already trimmed to what the day has left. */
    val grantedSeconds: Int,
    /** Seconds burned in completed run stretches. */
    val bankedSeconds: Int = 0,
    /** Epoch millis the current run stretch began, or null while paused. */
    val runningSince: Long? = null,
    /** How much of the elapsed time is already written into Usage. */
    val committedSeconds: Int = 0,
) {
    fun consumedAt(now: Long): Int =
        bankedSeconds + (runningSince?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0)

    fun remainingAt(now: Long): Int = (grantedSeconds - consumedAt(now)).coerceAtLeast(0)
    val paused: Boolean get() = runningSince == null
}

enum class GuardMode {
    /** Not guarding. The phone behaves normally. */
    OFF,

    /** Guarding. Locked when no turn is running. */
    ARMED,

    /** Parent has the phone. No lock, no countdown. */
    PARENT,
}

enum class EndReason { TIME_UP, PARENT, IDLE, DAY_DONE }

@Serializable
data class Lockout(
    val wrongTries: Int = 0,
    /** Epoch millis until which the keypad refuses input. */
    val frozenUntil: Long = 0L,
)

@Serializable
data class AppState(
    val rules: Rules = Rules(),
    val kids: List<Kid> = emptyList(),
    val usage: Usage = Usage(),
    val session: Session? = null,
    val guard: GuardMode = GuardMode.OFF,
    /** Position in the rotation. Advances after every turn, so nobody goes twice. */
    val queueIndex: Int = 0,
    val lockout: Lockout = Lockout(),
    /** Lock is suspended until this time, used for an emergency call. */
    val overlaySuspendedUntil: Long = 0L,
    /** When the running turn was paused by the screen going off. 0 when not paused. */
    val lastPauseAt: Long = 0L,
) {
    val enabledKids: List<Kid> get() = kids.filter { it.enabled }

    fun kid(id: String?): Kid? = id?.let { k -> kids.firstOrNull { it.id == k } }

    /**
     * Whose turn it is. The rotation is the whole fairness mechanism: finish your turn
     * and you go to the back, so you cannot start again until everyone else has been.
     */
    fun whoseTurn(): Kid? {
        val list = enabledKids
        if (list.isEmpty()) return null
        return list[((queueIndex % list.size) + list.size) % list.size]
    }
}
