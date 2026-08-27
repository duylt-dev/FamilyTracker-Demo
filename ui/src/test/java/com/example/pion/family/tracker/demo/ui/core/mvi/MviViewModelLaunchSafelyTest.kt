package com.example.pion.family.tracker.demo.ui.core.mvi

import com.example.pion.family.tracker.demo.domain.model.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private data class FakeState(val error: AppError? = null) : UiState
private interface FakeIntent : UiIntent
private interface FakeEffect : UiEffect

/** Minimal concrete ViewModel — only exists to exercise [MviViewModel.launchSafely] on the JVM. */
private class FakeViewModel : MviViewModel<FakeState, FakeIntent, FakeEffect>(FakeState()) {
    override fun onIntent(intent: FakeIntent) = Unit

    fun triggerFailure() = launchSafely(
        onError = { setState { copy(error = it) } },
    ) {
        throw IllegalStateException("boom")
    }
}

/**
 * Regression test for LLM.md §13 — `launchSafely`'s catch branch must be exercisable on a
 * plain JVM unit test, with no Robolectric and no `android.jar` stub involved. See
 * `plans/260821-1113-geofence-zone-and-history-tracking/reports/fix-phase-01-report.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelLaunchSafelyTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `launchSafely catches a thrown exception, rethrows nothing, and reports it via onError`() = runTest(dispatcher) {
        val viewModel = FakeViewModel()

        viewModel.triggerFailure()
        advanceUntilIdle()

        val error = viewModel.state.value.error
        assertTrue(error is AppError.Unexpected)
        assertEquals("boom", (error as AppError.Unexpected).message)
    }
}
