package com.jefflim.oras.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jefflim.oras.MainActivity
import com.jefflim.oras.R
import com.jefflim.oras.core.Engine
import com.jefflim.oras.core.TimeRules
import com.jefflim.oras.data.AppState
import com.jefflim.oras.data.EndReason
import com.jefflim.oras.data.GuardMode
import com.jefflim.oras.overlay.OverlayHost
import com.jefflim.oras.ui.lock.LockContent
import com.jefflim.oras.ui.lock.WarningBanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the countdown alive and owns the lock window.
 *
 * Everything that must survive the app being swiped away lives here: the ticking, the
 * lock, and the ongoing notification that keeps Android from reclaiming us.
 */
class GuardService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.jefflim.oras.START"
        const val ACTION_LOCK_NOW = "com.jefflim.oras.LOCK_NOW"
        const val ACTION_STOP = "com.jefflim.oras.STOP"

        private const val CHANNEL_ONGOING = "guard"
        private const val NOTIF_ID = 1001

        fun start(context: Context, action: String = ACTION_START) {
            val i = Intent(context, GuardService::class.java).setAction(action)
            runCatching { context.startForegroundService(i) }
        }

        fun canDrawOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)
    }

    /**
     * Rebuilt on every show. A LifecycleRegistry cannot climb back up once driven to
     * DESTROYED, so reusing one host would leave the lock unable to reappear after the
     * very first unlock.
     */
    private var lock: OverlayHost? = null
    private var banner: OverlayHost? = null
    private var bannerJob: Job? = null
    private var lastNotificationText: String? = null

    /**
     * Android kills the process if a service started with startForegroundService() stops
     * before it ever calls startForeground(). The state collector starts in onCreate and
     * can decide to stand down before onStartCommand runs, so standing down waits on this.
     */
    private var startedForeground = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val on = when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> true
                Intent.ACTION_SCREEN_OFF -> false
                else -> return
            }
            lifecycleScope.launch { Engine.setScreenOn(on) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
        createChannels()

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })

        observeState()
        observeEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Always the first thing that happens, before any branch can return or stop.
        goForeground(buildNotification("Oras", "Checking", null))
        startedForeground = true
        // That placeholder just replaced whatever was on screen, and the state collector
        // may already have posted the real thing. Clearing the dedupe cache forces the
        // next tick to re-post, instead of leaving "Checking" showing forever.
        lastNotificationText = null

        when (intent?.action) {
            ACTION_LOCK_NOW -> lifecycleScope.launch {
                if (Engine.state.value.session != null) Engine.endSession(EndReason.PARENT)
                Engine.arm()
            }

            ACTION_STOP -> lifecycleScope.launch {
                Engine.disarm()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        lock?.hide()
        lock = null
        banner?.hide()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Engine.state.value.guard == GuardMode.ARMED) start(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    // ---- state --------------------------------------------------------------

    private fun observeState() {
        lifecycleScope.launch {
            combine(Engine.state, Engine.tick) { st, now -> st to now }.collect { (st, now) ->
                val wantLock = Engine.shouldLock(st, now)

                if (wantLock && lock == null && canDrawOverlay(this@GuardService)) {
                    val host = OverlayHost(
                        context = this@GuardService,
                        focusable = true,
                        onBackPressed = { true } // Back is swallowed while locked
                    )
                    lock = host
                    host.show { LockContent(onEmergency = ::onEmergency) }
                } else if (!wantLock && lock != null) {
                    lock?.hide()
                    lock = null
                }

                updateNotification(st, now)

                if (startedForeground &&
                    Engine.loaded.value &&
                    st.guard == GuardMode.OFF &&
                    st.session == null
                ) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            Engine.events.collect { e ->
                when (e) {
                    is Engine.Event.Warn -> {
                        buzz(longArrayOf(0, 40, 90, 40))
                        showBanner(e.kid.name, e.kid.hue, e.minutesLeft)
                    }

                    is Engine.Event.Ended -> {
                        if (e.reason == EndReason.TIME_UP) buzz(longArrayOf(0, 60, 120, 60, 120, 90))
                    }

                    else -> Unit
                }
            }
        }
    }

    // ---- warning banner ------------------------------------------------------

    private fun showBanner(name: String, hue: Int, minutes: Int) {
        if (!canDrawOverlay(this)) return
        bannerJob?.cancel()
        banner?.hide()
        val host = OverlayHost(this, focusable = false, fullHeight = false)
        banner = host
        host.show { WarningBanner(name = name, hue = hue, minutesLeft = minutes) }
        bannerJob = lifecycleScope.launch {
            delay(5_000)
            host.hide()
            if (banner === host) banner = null
        }
    }

    // ---- emergency -----------------------------------------------------------

    /**
     * Steps the lock aside so the dialer is reachable, then re-locks itself. A child must
     * always be able to reach a parent, whatever the timer says.
     */
    private fun onEmergency() {
        val number = Engine.state.value.rules.emergencyNumber
        if (number.isBlank()) return
        lifecycleScope.launch {
            Engine.suspendOverlay(2)
            val dial = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(dial) }
        }
    }

    private fun buzz(pattern: LongArray) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    // ---- notification --------------------------------------------------------

    private fun createChannels() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Guard", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows whose turn it is and how much time is left."
                setShowBadge(false)
            }
        )
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(st: AppState, now: Long) {
        val session = st.session
        val kid = st.kid(session?.kidId)
        val poolLeft = Engine.poolLeft(st, now) / 60
        val title: String
        val text: String

        when {
            session != null && kid != null -> {
                val left = session.remainingAt(now)
                title = "${kid.name}'s turn"
                text = when {
                    session.paused -> "Paused, ${TimeRules.formatMinutes((left + 59) / 60)} left"
                    left < 60 -> "$left seconds left"
                    else -> "${TimeRules.formatMinutes((left + 59) / 60)} left"
                }
            }

            st.guard == GuardMode.ARMED -> {
                title = "Locked"
                text = if (poolLeft <= 0) {
                    "No time left today"
                } else {
                    st.whoseTurn()?.let { "${it.name}'s turn next" } ?: "Waiting for a code"
                }
            }

            st.guard == GuardMode.PARENT -> {
                title = "Parent mode"
                text = "The lock is off. Tap to hand the phone over."
            }

            else -> {
                title = "Oras"
                text = "Not guarding"
            }
        }

        val signature = "$title|$text"
        if (signature == lastNotificationText) return
        lastNotificationText = signature

        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(title, text, session != null))
    }

    private fun buildNotification(title: String, text: String, running: Boolean?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val lockNow = PendingIntent.getService(
            this, 1,
            Intent(this, GuardService::class.java).setAction(ACTION_LOCK_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_oras)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (running == true) addAction(0, "End turn", lockNow) }
            .build()
    }
}
