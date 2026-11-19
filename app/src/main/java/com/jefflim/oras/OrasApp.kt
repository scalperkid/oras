package com.jefflim.oras

import android.app.Application
import com.jefflim.oras.core.Engine

/**
 * The engine is initialised here rather than in the Activity, because the service and
 * the accessibility service can both start without any UI ever being created.
 */
class OrasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
    }
}
