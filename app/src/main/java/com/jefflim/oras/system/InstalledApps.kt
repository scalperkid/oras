package com.jefflim.oras.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/**
 * The launchable apps on the phone, for the parent's picker and the child's grid.
 *
 * Only apps with a launcher entry are listed, declared through a <queries> filter rather
 * than QUERY_ALL_PACKAGES: Oras has no business enumerating everything installed.
 */
object InstalledApps {

    @Volatile
    private var cache: List<AppEntry>? = null

    /** Packages that must never be blocked, whatever a child's list says. */
    fun exempt(context: Context): Set<String> = buildSet {
        add(context.packageName)
        add("com.android.systemui")
        homePackages(context).forEach { add(it) }
        dialerPackage(context)?.let { add(it) }
        add("com.android.server.telecom")
        add("com.android.phone")
    }

    fun homePackages(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(home, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    /**
     * Home apps other than Oras, for the parent to choose their normal launcher from.
     * Once Oras is Home, the system will not tell us who held the role before.
     */
    fun otherHomeApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, 0)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrDefault(info.packageName),
                    icon = runCatching { info.loadIcon(pm).toImageBitmap() }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun dialerPackage(context: Context): String? {
        val dial = Intent(Intent.ACTION_DIAL)
        return context.packageManager.resolveActivity(dial, 0)?.activityInfo?.packageName
    }

    fun load(context: Context, refresh: Boolean = false): List<AppEntry> {
        cache?.let { if (!refresh) return it }
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hidden = exempt(context) - context.packageName

        val list = pm.queryIntentActivities(main, 0)
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .filter { it.packageName !in hidden }
            .distinctBy { it.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrDefault(info.packageName),
                    icon = runCatching { info.loadIcon(pm).toImageBitmap() }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

        cache = list
        return list
    }

    fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun labelOf(context: Context, packageName: String): String =
        load(context).firstOrNull { it.packageName == packageName }?.label ?: packageName
}

/** Compose cannot draw a Drawable directly, so adaptive icons are rasterised once. */
private fun Drawable.toImageBitmap(size: Int = 144): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()
    }
    val w = if (intrinsicWidth > 0) intrinsicWidth else size
    val h = if (intrinsicHeight > 0) intrinsicHeight else size
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}
