package com.example.pion.family.tracker.demo.ui.core.logging

import android.util.Log

/**
 * Real [AppLogger] adapter, registered as a Koin `single` in `uiModule` (LLM.md §6). This is
 * the only file in `ui/core/logging` allowed to import `android.util.Log` — the port
 * ([AppLogger]) stays pure Kotlin so it can sit on a `MviViewModel` constructor without
 * pulling a platform import into the ViewModel itself.
 */
class AndroidAppLogger : AppLogger {
    override fun e(throwable: Throwable, message: () -> String) {
        Log.e(TAG, message(), throwable)
    }

    private companion object {
        const val TAG = "FamilyTracker"
    }
}
