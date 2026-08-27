package com.example.pion.family.tracker.demo.ui.core.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * The one effect collector in the codebase — MVI doc §4. `collect`, never `collectLatest`
 * (that would cancel an in-flight navigation the moment the next effect arrives), and
 * lifecycle-aware via `repeatOnLifecycle(STARTED)` so nothing is handled while the screen
 * is not visible — the channel buffers meanwhile, nothing is lost.
 */
@Composable
fun <E : UiEffect> CollectEffects(effects: Flow<E>, onEffect: suspend (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler(it) }
        }
    }
}
