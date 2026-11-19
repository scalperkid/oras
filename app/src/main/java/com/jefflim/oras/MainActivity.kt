package com.jefflim.oras

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jefflim.oras.core.Engine
import com.jefflim.oras.data.EndReason
import com.jefflim.oras.data.GuardMode
import com.jefflim.oras.data.Kid
import com.jefflim.oras.data.Pin
import com.jefflim.oras.service.GuardService
import com.jefflim.oras.system.Permissions
import com.jefflim.oras.ui.components.Pill
import com.jefflim.oras.ui.parent.AppPicker
import com.jefflim.oras.ui.parent.ChangeParentCode
import com.jefflim.oras.ui.parent.CodeSetConfirmation
import com.jefflim.oras.ui.parent.HistoryScreen
import com.jefflim.oras.ui.parent.KidEditor
import com.jefflim.oras.ui.parent.KidsScreen
import com.jefflim.oras.ui.parent.ParentGate
import com.jefflim.oras.ui.parent.RulesScreen
import com.jefflim.oras.ui.parent.TodayScreen
import com.jefflim.oras.ui.setup.SetupScreen
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Paper
import kotlinx.coroutines.launch

private sealed interface Route {
    data object Tabs : Route
    data class EditKid(val kid: Kid, val isNew: Boolean) : Route
    data class ShowCode(val name: String, val code: String) : Route
    data class PickApps(val kid: Kid) : Route
    data object ParentCode : Route
}

/**
 * One step Oras needs from Android. Kept as data so setup can walk them in order and a
 * parent only ever taps one button at a time.
 */
private data class Grant(
    val title: String,
    val why: String,
    val done: Boolean,
    val run: () -> Unit,
)

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.init(this)
        val activity = this

        setContent {
            val state by Engine.state.collectAsStateWithLifecycle()
            val now by Engine.tick.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            // Permissions change outside the app, in Settings, so they are re-read on a
            // slow cadence rather than cached once at launch.
            val permKey = now / 2000
            val isHome = remember(permKey) { Permissions.isDefaultHome(activity) }
            val overlayOk = remember(permKey) { Permissions.canDrawOverlay(activity) }
            val batteryOk = remember(permKey) { Permissions.batteryUnrestricted(activity) }
            val notifOk = remember(permKey) { Permissions.notificationsGranted(activity) }
            val strongOk = remember(permKey) { Permissions.strongLockOn(activity) }
            val needsOem = remember { Permissions.needsOemAutostart() }

            // The order matters: the lock is useless without the overlay, and the timer
            // is useless without the battery exemption. Notifications come last.
            val grants = remember(isHome, overlayOk, batteryOk, notifOk) {
                listOf(
                    Grant(
                        "Home screen",
                        "So the kids screen becomes the home screen and Home does not escape.",
                        isHome
                    ) { Permissions.requestHomeRole(activity) },
                    Grant(
                        "Draw over other apps",
                        "This is the lock itself.",
                        overlayOk
                    ) { Permissions.requestOverlay(activity) },
                    Grant(
                        "Unrestricted battery",
                        "So the phone does not kill the timer in the background.",
                        batteryOk
                    ) { Permissions.requestBattery(activity) },
                    Grant(
                        "Notifications",
                        "So you can see the countdown without opening Oras.",
                        notifOk
                    ) { askNotifications() },
                )
            }
            val nextGrant = grants.firstOrNull { !it.done }

            var unlocked by remember { mutableStateOf(false) }
            var route by remember { mutableStateOf<Route>(Route.Tabs) }
            var tab by remember { mutableIntStateOf(0) }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Paper)
                    .systemBarsPadding()
            ) {
                when {
                    !state.rules.setupDone -> Scroll {
                        SetupScreen(
                            grantsDone = grants.count { it.done },
                            grantsTotal = grants.size,
                            allGrantsDone = nextGrant == null,
                            currentGrantTitle = nextGrant?.title,
                            currentGrantWhy = nextGrant?.why,
                            onRunGrants = { nextGrant?.run?.invoke() },
                            onFinish = { parentCode ->
                                scope.launch {
                                    Engine.update {
                                        it.copy(
                                            rules = it.rules.copy(
                                                parentPin = Pin.hash(parentCode),
                                                setupDone = true
                                            )
                                        )
                                    }
                                    unlocked = true
                                    tab = 1
                                }
                            }
                        )
                    }

                    !unlocked -> ParentGate(state.rules.parentPin) { unlocked = true }

                    else -> AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(tween(320)) { it / 10 })
                                .togetherWith(fadeOut(tween(140)))
                        },
                        label = "route"
                    ) { r ->
                        when (r) {
                            is Route.Tabs -> Column(Modifier.fillMaxSize()) {
                                TabBar(tab) { tab = it }
                                Scroll {
                                    when (tab) {
                                        0 -> TodayScreen(
                                            state = state,
                                            now = now,
                                            setupProblem = when {
                                                !overlayOk -> "Oras cannot show the lock until " +
                                                    "it is allowed to draw over other apps."

                                                !isHome -> "Oras is not the home screen yet, so " +
                                                    "pressing Home leaves the kids screen."

                                                !batteryOk -> "Battery is still restricted, so " +
                                                    "the timer will stop when the phone sleeps."

                                                else -> null
                                            },
                                            onArm = {
                                                scope.launch {
                                                    Engine.arm()
                                                    GuardService.start(activity)
                                                }
                                                moveTaskToBack(true)
                                            },
                                            onParentMode = { scope.launch { Engine.parentMode() } },
                                            onEndTurn = {
                                                scope.launch { Engine.endSession(EndReason.PARENT) }
                                            },
                                            onBonus = { m -> scope.launch { Engine.grantBonus(m) } },
                                            onSkipTurn = { scope.launch { Engine.skipTurn() } },
                                            onFixSetup = { nextGrant?.run?.invoke() },
                                            onAddKid = {
                                                route = Route.EditKid(newKid(state.kids.size), true)
                                            }
                                        )

                                        1 -> KidsScreen(
                                            kids = state.kids,
                                            onEdit = { route = Route.EditKid(it, false) },
                                            onAdd = {
                                                route = Route.EditKid(newKid(state.kids.size), true)
                                            },
                                            onMove = { kid, d ->
                                                scope.launch { Engine.moveKid(kid.id, d) }
                                            }
                                        )

                                        2 -> RulesScreen(
                                            rules = state.rules,
                                            kidCount = state.enabledKids.size,
                                            isHome = isHome,
                                            overlayOk = overlayOk,
                                            batteryOk = batteryOk,
                                            strongLockOn = strongOk,
                                            needsOem = needsOem,
                                            onChange = { next ->
                                                scope.launch { Engine.update { it.copy(rules = next) } }
                                            },
                                            onChangeParentCode = { route = Route.ParentCode },
                                            onFixHome = { Permissions.requestHomeRole(activity) },
                                            onFixOverlay = { Permissions.requestOverlay(activity) },
                                            onFixBattery = { Permissions.requestBattery(activity) },
                                            onOpenAccessibility = {
                                                Permissions.openAccessibilitySettings(activity)
                                            },
                                            onOpenAutostart = {
                                                if (!Permissions.openOemAutostart(activity)) {
                                                    Permissions.openAppSettings(activity)
                                                }
                                            },
                                            onResetToday = { scope.launch { Engine.resetToday() } }
                                        )

                                        else -> HistoryScreen(state, now)
                                    }
                                }
                            }

                            is Route.EditKid -> Scroll {
                                KidEditor(
                                    original = r.kid,
                                    isNew = r.isNew,
                                    existingNames = state.kids
                                        .filter { it.id != r.kid.id }
                                        .map { it.name },
                                    enforcementOn = strongOk,
                                    onSave = { kid, newCode ->
                                        scope.launch {
                                            Engine.upsertKid(
                                                if (newCode != null) kid.copy(pin = Pin.hash(newCode))
                                                else kid
                                            )
                                            route = if (newCode != null) {
                                                Route.ShowCode(kid.name, newCode)
                                            } else {
                                                Route.Tabs
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            Engine.removeKid(r.kid.id)
                                            route = Route.Tabs
                                        }
                                    },
                                    onCancel = { route = Route.Tabs },
                                    onPickApps = { draft -> route = Route.PickApps(draft) }
                                )
                            }

                            is Route.PickApps -> Scroll {
                                AppPicker(
                                    kidName = r.kid.name,
                                    selected = r.kid.allowedApps,
                                    enforcementOn = strongOk,
                                    onEnableEnforcement = {
                                        Permissions.openAccessibilitySettings(activity)
                                    },
                                    onDone = { picked ->
                                        scope.launch {
                                            Engine.upsertKid(r.kid.copy(allowedApps = picked))
                                            route = Route.EditKid(
                                                r.kid.copy(allowedApps = picked),
                                                false
                                            )
                                        }
                                    },
                                    onCancel = { route = Route.EditKid(r.kid, false) }
                                )
                            }

                            is Route.ShowCode -> Scroll {
                                CodeSetConfirmation(r.name, r.code) {
                                    route = Route.Tabs
                                    tab = 1
                                }
                            }

                            Route.ParentCode -> Scroll {
                                ChangeParentCode(
                                    currentHash = state.rules.parentPin,
                                    onSave = { fresh ->
                                        scope.launch {
                                            Engine.update {
                                                it.copy(
                                                    rules = it.rules.copy(parentPin = Pin.hash(fresh))
                                                )
                                            }
                                            route = Route.Tabs
                                        }
                                    },
                                    onCancel = { route = Route.Tabs }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only wake the service when there is genuinely something to guard. Starting it
        // just to have it stand down again is churn, and churn around the foreground
        // service contract is what gets a process killed.
        val st = Engine.state.value
        if (st.rules.setupDone && st.guard != GuardMode.OFF) GuardService.start(this)
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun newKid(index: Int) = Kid(id = Engine.newId(), name = "", hue = index % 8)
}

@Composable
private fun Scroll(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 48.dp)
    ) {
        content()
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Paper)
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Four equal pills on a 375dp screen leave roughly 80dp each, so the labels
            // have to be short words. "Kids" is also what a parent actually says.
            listOf("Today", "Kids", "Rules", "History").forEachIndexed { i, label ->
                Pill(label, i == selected, { onSelect(i) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Line)
        )
    }
}
