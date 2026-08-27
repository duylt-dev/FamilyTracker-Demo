package com.example.pion.family.tracker.demo.data.util

import android.util.Log

/**
 * G7 gate (PRD §11.1, `LLM.md` §13 phase-11 Key Insight #6): R8 is off (PRD v1.2 §7.2) so nothing
 * strips log calls at release — silence has to be a code flag, not a tool. This is the single log
 * gate for `:data` (and, via the one file allowed to see both worlds, `:app`'s
 * `FamilyTrackerApp.kt` — LLM.md §6). Every direct `Log.d`/`Log.w`/`Log.e` in `:data` production
 * code routes through here instead.
 *
 * fix-phase-11 (LLM.md §13 Fixed #22 — same lesson as Fixed #2 / fix-phase-01, `MviViewModel`'s
 * `android.util.Log`): this used to be a `KoinComponent` reading `debugBuild` via `by
 * inject(named("debugBuild"))`. A logging gate must not REQUIRE infrastructure to already be up —
 * the lazy Koin lookup threw `IllegalStateException: KoinApplication has not been started` the
 * first time any `FtdLog` call happened before `startKoin` ran (every `:data:connectedDebugAndroidTest`
 * — none of them start Koin), and it threw from inside a Play Services callback
 * (`GeofenceRegistrar.registerAll`'s `onComplete`, main thread), which killed the whole
 * instrumentation process and took the other 13 tests down with it before they could even start.
 * Now a plain `@Volatile var`, default `false` — "not yet initialized" reads as "stay silent",
 * which is exactly the safe behavior for both release and any test/tooling context. No DI, no
 * infrastructure — `FamilyTrackerApp.onCreate` sets it once, by direct assignment (not through
 * Koin), before `startKoin` runs.
 *
 * `:ui` cannot see this object (module graph, LLM.md §2: `:ui` does not depend on `:data`) — it
 * has its own near-identical twin at `ui/core/logging/FtdLog.kt`. Two ~20-line platform-glue
 * objects instead of one shared one is the cost of the module boundary being real (Gradle-enforced,
 * not just a convention) rather than a DRY violation worth breaking that boundary for.
 */
object FtdLog {

    @Volatile
    var debugBuild: Boolean = false

    fun d(tag: String, msg: String) {
        if (debugBuild) Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (debugBuild) Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (debugBuild) Log.e(tag, msg, throwable)
    }
}
