package com.example.pion.family.tracker.demo.ui.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.ui.core.logging.AppLogger
import com.example.pion.family.tracker.demo.ui.core.logging.NoopAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base class for every screen ViewModel — MVI doc §1. Non-negotiable contract:
 * `onIntent` is the only public method, effects go through a [Channel] (never a
 * [kotlinx.coroutines.flow.SharedFlow]), and every coroutine goes through [launchSafely].
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    private val logger: AppLogger = NoopAppLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    abstract fun onIntent(intent: I)

    protected fun setState(reducer: S.() -> S) = _state.update { it.reducer() }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }

    protected fun launchSafely(
        onError: (AppError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation // NEVER swallow this
        } catch (throwable: Throwable) {
            logger.e(throwable) { "Unhandled failure in ${this@MviViewModel::class.simpleName}" }
            onError(AppError.Unexpected(throwable.message))
        }
    }

    /**
     * The blessed way to observe a [Flow] from a ViewModel `init` block or `onIntent` branch —
     * `someFlow.onEach { ... }.launchIn(viewModelScope)` is `viewModelScope.launch { collect {} }`
     * spelled differently and does **not** go through [launchSafely], so an exception from the
     * source `Flow` (e.g. a Room-backed `Flow` hitting `SQLiteException`) escapes uncaught and
     * crashes the app on a real device. See
     * `plans/260821-1113-geofence-zone-and-history-tracking/reports/fix-phase-04-report.md`
     * (`MapViewModelLaunchSafetyTest` proves the crash on the un-fixed version).
     */
    protected fun <T> Flow<T>.collectSafely(
        onError: (AppError) -> Unit = {},
        onEach: suspend (T) -> Unit,
    ): Job = launchSafely(onError) { collect { onEach(it) } }
}
