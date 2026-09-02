package com.itantra

import android.app.Application
import android.util.Log

/**
 * Application class for iTantra v2.
 * Minimal — all initialization happens lazily in services and repositories.
 */
class ITantraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("iTantra", "Application v2.0 started")
    }
}
