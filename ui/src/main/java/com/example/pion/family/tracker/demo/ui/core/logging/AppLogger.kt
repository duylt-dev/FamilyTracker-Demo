package com.example.pion.family.tracker.demo.ui.core.logging

/**
 * Port for the one thing [com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel]
 * needs from a platform logger — MVI doc §9 checklist: "No Compose / Android / platform
 * import" in a ViewModel. `android.util.Log` lives only in [AndroidAppLogger], the adapter.
 *
 * Message is a lambda, not a `String`, so a call site that never fails never builds the
 * string either.
 */
fun interface AppLogger {
    fun e(throwable: Throwable, message: () -> String)
}

/**
 * JVM-safe default. Lets a `MviViewModel` subclass be unit-tested with no Koin container and
 * no Robolectric — see `MviViewModelLaunchSafelyTest`, which pins down the failure this
 * fixes: `android.util.Log` is a stub on the unit-test classpath and throws
 * `RuntimeException: ... not mocked` the first time `launchSafely` catches a real error.
 */
object NoopAppLogger : AppLogger {
    override fun e(throwable: Throwable, message: () -> String) = Unit
}
