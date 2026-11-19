package com.jefflim.oras.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A reboot is the most obvious way around a lock, so the guard comes back with the phone.
 * The engine's own reconciliation decides whether a sitting survived; this only restarts
 * the service that enforces it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> GuardService.start(context)
        }
    }
}
