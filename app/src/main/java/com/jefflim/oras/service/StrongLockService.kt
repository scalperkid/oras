package com.jefflim.oras.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.jefflim.oras.core.Engine
import com.jefflim.oras.system.InstalledApps

/**
 * Watches only which app comes to the front, and does two jobs with that one fact.
 *
 * 1. While a child's turn is running, an app outside their list is sent Home, which now
 *    lands on their own home screen rather than a dead end.
 * 2. While a lock is up, someone reaching Settings to force stop Oras is sent Home too,
 *    so the guard cannot be dismantled from under itself.
 *
 * Configured with canRetrieveWindowContent="false", so it is structurally unable to read
 * the contents of any app. It sees a package name and nothing else.
 *
 * This is a backstop. The launcher does the day to day work, which is why a parent who
 * declines this still gets a working kids mode.
 */
class StrongLockService : AccessibilityService() {

    companion object {
        /** Screens from which the guard itself can be dismantled. */
        private val GUARDED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.securitycenter",
            "com.samsung.android.lool",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
        )

        fun isEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains("${context.packageName}/${StrongLockService::class.java.name}")
        }
    }

    private var exempt: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Engine.init(this)
        exempt = InstalledApps.exempt(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        val st = Engine.state.value

        // Enabling this service in Android's own accessibility settings is the consent.
        // A second switch inside the app would only be a way for the two to disagree.
        if (Engine.shouldLock(st, now)) {
            if (pkg in GUARDED_PACKAGES) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                GuardService.start(applicationContext)
            }
            return
        }

        val session = st.session ?: return
        val kid = st.kid(session.kidId) ?: return
        if (!kid.hasAppLimit) return
        if (pkg in kid.allowedApps) return

        if (exempt.isEmpty()) exempt = InstalledApps.exempt(this)
        if (pkg in exempt) return

        // Home is their own screen now, so this reads as "back to your apps", not a wall.
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() = Unit
}
