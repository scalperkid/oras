package com.jefflim.oras.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jefflim.oras.MainActivity
import com.jefflim.oras.core.Engine
import com.jefflim.oras.system.InstalledApps
import com.jefflim.oras.ui.kid.KidHome
import kotlinx.coroutines.launch

/**
 * Oras as the phone's Home app. This is what makes kids mode real: pressing Home during
 * a child's turn lands here, on their own small home screen, rather than escaping to the
 * full launcher. Nothing is being blocked, we simply are Home.
 *
 * When no child is using the phone, this steps out of the way immediately and opens the
 * parent's normal launcher, so their own phone still feels like their phone.
 */
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)
        enableEdgeToEdge()

        if (handoffIfNotKidTime()) return

        setContent {
            val state by Engine.state.collectAsStateWithLifecycle()
            val now by Engine.tick.collectAsStateWithLifecycle()
            KidHome(
                state = state,
                now = now,
                onOpen = { pkg ->
                    InstalledApps.launchIntent(this, pkg)?.let { runCatching { startActivity(it) } }
                }
            )
        }
    }

    /** Home was pressed again while already here; re-evaluate rather than sit on a stale screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handoffIfNotKidTime()
    }

    override fun onResume() {
        super.onResume()
        handoffIfNotKidTime()
    }

    /**
     * True when this activity has handed control elsewhere and should not draw.
     *
     * A child's turn keeps them here. Anything else, a parent pressing Home or the phone
     * simply idling, goes straight to the launcher they normally use. The transition is
     * suppressed so the handover is not a visible flash.
     */
    private fun handoffIfNotKidTime(): Boolean {
        val st = Engine.state.value

        // Before setup, Oras has nothing to show as a home screen.
        if (!st.rules.setupDone) {
            startActivity(Intent(this, MainActivity::class.java))
            finishAndNoAnimation()
            return true
        }

        if (st.session != null) return false

        val target = ParentLauncher.resolve(this, st.rules.parentLauncher)
        if (target != null) {
            runCatching { startActivity(target) }
            finishAndNoAnimation()
            return true
        }

        // No usable fallback launcher: keep the child's home rather than a blank screen,
        // and let the parent pick one from inside Oras.
        return false
    }

    @Suppress("DEPRECATION")
    private fun finishAndNoAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }
}

/**
 * Which launcher to fall back to. The parent picks one in Oras, because once Oras is the
 * Home app the system can no longer be asked "who was home before".
 */
object ParentLauncher {

    fun resolve(context: android.content.Context, preferred: String): Intent? {
        val options = InstalledApps.otherHomeApps(context)
        val pick = options.firstOrNull { it.packageName == preferred }
            ?: options.firstOrNull()
            ?: return null
        return context.packageManager.getLaunchIntentForPackage(pick.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
