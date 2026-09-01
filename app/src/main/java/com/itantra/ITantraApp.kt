package com.itantra

import android.app.Application
import android.util.Log

/**
 * Application class for iTantra.
 * Minimal — all initialization happens lazily in ITantraForegroundService.
 */
class ITantraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("iTantra", "Application started")
    }
}
