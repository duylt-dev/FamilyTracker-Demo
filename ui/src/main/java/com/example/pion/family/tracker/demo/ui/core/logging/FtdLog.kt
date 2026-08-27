package com.example.pion.family.tracker.demo.ui.core.logging

import android.util.Log

/**
 * G7 gate (PRD §11.1, `LLM.md` §13 phase-11 Key Insight #6) — `:ui`'s half of the single log gate.
 * `:data` has its own near-identical twin at `data/util/FtdLog.kt`; the two cannot share one
 * object because `:ui` does not depend on `:data` (module graph, LLM.md §2). Not [AppLogger] (§6,
 * `MviViewModel`'s constructor-injected error port) — that one is Compose/platform-free by
 * contract and always logs (crash-adjacent), this one gates `FTD_EVENT` telemetry lines emitted
 * directly from composables (`HistoryMap`, `RoutePolyline`, `LocationPermissionFlow`).
 *
 * fix-phase-11 (LLM.md §13 Fixed #22 — same lesson as Fixed #2 / fix-phase-01, `MviViewModel`'s
 * `android.util.Log`): used to be a `KoinComponent` reading `debugBuild` via `by
 * inject(named("debugBuild"))`. A logging gate must not REQUIRE infrastructure to already be up —
 * see `:data/util/FtdLog.kt`'s KDoc for the full incident (this object's twin, same bug, same
 * fix). Now a plain `@Volatile var`, default `false` — "not yet initialized" reads as "stay
 * silent". No DI — `FamilyTrackerApp.onCreate` sets it once, by direct assignment, before
 * `startKoin` runs.
 */
object FtdLog {

    @Volatile
    var debugBuild: Boolean = false

    fun d(tag: String, msg: String) {
        if (debugBuild) Log.d(tag, msg)
    }
}
