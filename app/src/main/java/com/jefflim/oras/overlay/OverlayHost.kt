package com.jefflim.oras.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts Compose content in a window owned by the service rather than by an Activity.
 *
 * This is what makes the lock hard to escape: an Activity can be dismissed with Home,
 * but an overlay window draws above the launcher too, so pressing Home just changes
 * what is behind the lock.
 *
 * Compose expects the three view-tree owners that an Activity normally supplies, so
 * this class plays that role itself.
 */
class OverlayHost(
    private val context: Context,
    private val focusable: Boolean,
    private val gravity: Int = Gravity.TOP or Gravity.START,
    private val fullHeight: Boolean = true,
    private val onBackPressed: (() -> Boolean)? = null,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null

    val isShowing: Boolean get() = root != null

    @SuppressLint("ViewConstructor")
    private inner class Root(ctx: Context) : FrameLayout(ctx) {
        // Back is consumable on a focusable window; Home is not, by OS design.
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBackPressed?.invoke()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    fun show(content: @Composable () -> Unit) {
        if (root != null) return
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED

        val container = Root(context)
        val compose = ComposeView(context).apply { setContent(content) }
        container.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.setViewTreeLifecycleOwner(this@OverlayHost)
        container.setViewTreeViewModelStoreOwner(this@OverlayHost)
        container.setViewTreeSavedStateRegistryOwner(this@OverlayHost)

        try {
            wm.addView(container, params())
        } catch (_: Exception) {
            // Overlay permission revoked while running. Fail quiet; the service re-checks.
            return
        }
        root = container
        registry.currentState = Lifecycle.State.RESUMED
        if (fullHeight) goImmersive(container)
    }

    fun hide() {
        val v = root ?: return
        root = null
        registry.currentState = Lifecycle.State.DESTROYED
        runCatching { wm.removeViewImmediate(v) }
        viewModelStore.clear()
    }

    private fun params(): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        if (!focusable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            // Wake and show over the keyguard so "time is up" is not hidden behind it.
            flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (fullHeight) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = this@OverlayHost.gravity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun goImmersive(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { c ->
                c.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                c.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            view.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
}
