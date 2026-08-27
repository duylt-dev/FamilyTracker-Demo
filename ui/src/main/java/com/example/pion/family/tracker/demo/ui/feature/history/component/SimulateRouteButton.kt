package com.example.pion.family.tracker.demo.ui.feature.history.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/**
 * US-33 (F5), phase-09. Only composes when `BuildConfig.SIMULATOR_ENABLED` — read through Koin
 * qualifier `named("simulatorEnabled")`, declared in `:app/FamilyTrackerApp.kt` (phase-09 Key
 * Insight #6/#7): `:ui` deliberately does NOT enable its own `buildConfig`, so it cannot read
 * `:app`'s `BuildConfig.SIMULATOR_ENABLED` directly — a second `BuildConfig` class would exist
 * with no such field, and importing the wrong one is a compile error rather than a silent bug
 * (Key Insight #7). Same flag is meant to gate `EmptyRouteState`/`EmptyTimelineState` (phase-10) —
 * one flag, three call sites reading the same Koin binding.
 */
@Composable
internal fun SimulateRouteButton(isSimulating: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val simulatorEnabled = koinInject<Boolean>(qualifier = named("simulatorEnabled"))
    if (!simulatorEnabled) return

    Button(
        onClick = onClick,
        enabled = !isSimulating,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(
                if (isSimulating) R.string.history_simulate_running else R.string.history_simulate_button,
            ),
        )
    }
}
