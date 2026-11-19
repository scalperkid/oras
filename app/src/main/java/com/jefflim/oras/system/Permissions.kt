package com.jefflim.oras.system

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.jefflim.oras.service.StrongLockService

/**
 * Every capability Oras needs is a manual grant, and several are buried. Each entry says
 * plainly what it buys, because a parent should be able to refuse any one of them and
 * still understand what they gave up.
 */
object Permissions {

    fun canDrawOverlay(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun requestOverlay(a: Activity) {
        a.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${a.packageName}")
            )
        )
    }

    fun notificationsGranted(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

    fun batteryUnrestricted(c: Context): Boolean {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(c.packageName)
    }

    /**
     * Without this, aggressive power managers, which is most phones sold here, quietly
     * kill the service and the timer stops.
     */
    @android.annotation.SuppressLint("BatteryLife")
    fun requestBattery(a: Activity) {
        runCatching {
            a.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${a.packageName}")
                )
            )
        }.onFailure {
            runCatching { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    // ---- home app ---------------------------------------------------------

    fun isDefaultHome(c: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = c.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
        return resolved == c.packageName
    }

    /**
     * On Android 10 and up this is a single system dialog. Older versions have no such
     * API, so the parent is sent to the Home settings screen instead.
     */
    fun requestHomeRole(a: Activity, requestCode: Int = 7001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = a.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !rm.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                runCatching {
                    a.startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        requestCode
                    )
                    return
                }
            }
        }
        runCatching { a.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    // ---- app blocking -----------------------------------------------------

    fun strongLockOn(c: Context): Boolean = StrongLockService.isEnabled(c)

    fun openAccessibilitySettings(a: Activity) {
        runCatching { a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    // ---- OEM survival -----------------------------------------------------

    /**
     * The autostart whitelist Xiaomi, Oppo, Vivo, Realme and Infinix keep outside the
     * standard battery screen. There is no public API, so this opens the known activity
     * when it exists and reports failure honestly when it does not.
     */
    fun openOemAutostart(a: Activity): Boolean {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.transsion.phoenix" to "com.transsion.phoenix.MainActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
        )
        for ((pkg, cls) in candidates) {
            val i = Intent().setClassName(pkg, cls)
            if (a.packageManager.resolveActivity(i, 0) != null) {
                return runCatching { a.startActivity(i); true }.getOrDefault(false)
            }
        }
        return false
    }

    /** True when this phone is one of the brands with a separate autostart list. */
    fun needsOemAutostart(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return listOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo",
            "huawei", "honor", "infinix", "tecno", "itel", "transsion")
            .any { m.contains(it) }
    }

    fun openAppSettings(a: Activity) {
        runCatching {
            a.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${a.packageName}")
                )
            )
        }
    }
}
