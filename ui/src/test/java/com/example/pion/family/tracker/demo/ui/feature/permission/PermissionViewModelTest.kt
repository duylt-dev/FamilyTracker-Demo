package com.example.pion.family.tracker.demo.ui.feature.permission

import app.cash.turbine.test
import com.example.pion.family.tracker.demo.ui.permission.PermissionStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MVI doc §7 — reducer test mỗi Intent, effect test mỗi Effect, `when` exhaustive không `else`
 * (phase-04 Implementation Step 12). `PermissionViewModel` không gọi use case nào -> không có
 * nhánh crash-containment (`launchSafely`) để test ở đây, khác `MviViewModelLaunchSafelyTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `Continue at NOTIFICATIONS sends RequestNotifications`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.Continue)
        advanceUntilIdle()

        vm.effects.test {
            assertTrue(awaitItem() is PermissionEffect.RequestNotifications)
            expectNoEvents()
        }
    }

    @Test
    fun `Continue at FINE_LOCATION sends RequestFineLocation`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.PermissionResolved(PermissionStep.NOTIFICATIONS, granted = true))
        vm.onIntent(PermissionIntent.Continue)
        advanceUntilIdle()

        vm.effects.test {
            assertTrue(awaitItem() is PermissionEffect.RequestFineLocation)
            expectNoEvents()
        }
    }

    @Test
    fun `Continue at BACKGROUND_LOCATION sends OpenAppSettings`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.PermissionResolved(PermissionStep.NOTIFICATIONS, granted = true))
        vm.onIntent(PermissionIntent.PermissionResolved(PermissionStep.FINE_LOCATION, granted = true))
        vm.onIntent(PermissionIntent.Continue)
        advanceUntilIdle()

        vm.effects.test {
            assertTrue(awaitItem() is PermissionEffect.OpenAppSettings)
            expectNoEvents()
        }
    }

    @Test
    fun `Skip at NOTIFICATIONS advances to FINE_LOCATION without an effect`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.Skip)
        advanceUntilIdle()

        assertEquals(PermissionStep.FINE_LOCATION, vm.state.value.step)
        vm.effects.test { expectNoEvents() }
    }

    @Test
    fun `Skip at FINE_LOCATION sends GoToMap`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.Skip) // -> FINE_LOCATION
        vm.onIntent(PermissionIntent.Skip) // skip location -> Map
        advanceUntilIdle()

        vm.effects.test {
            assertTrue(awaitItem() is PermissionEffect.GoToMap)
            expectNoEvents()
        }
    }

    @Test
    fun `PermissionResolved NOTIFICATIONS denied still advances to FINE_LOCATION`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.PermissionResolved(PermissionStep.NOTIFICATIONS, granted = false))
        advanceUntilIdle()

        assertFalse(vm.state.value.notificationsGranted)
        assertEquals(PermissionStep.FINE_LOCATION, vm.state.value.step)
    }

    @Test
    fun `PermissionResolved FINE_LOCATION granted advances to BACKGROUND_LOCATION`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.PermissionResolved(PermissionStep.FINE_LOCATION, granted = true))
        advanceUntilIdle()

        assertTrue(vm.state.value.fineLocationGranted)
        assertEquals(PermissionStep.BACKGROUND_LOCATION, vm.state.value.step)
    }

    @Test
    fun `PermissionResolved FINE_LOCATION denied sends GoToMap, degraded mode`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.PermissionResolved(PermissionStep.FINE_LOCATION, granted = false))
        advanceUntilIdle()

        assertFalse(vm.state.value.fineLocationGranted)
        vm.effects.test {
            assertTrue(awaitItem() is PermissionEffect.GoToMap)
            expectNoEvents()
        }
    }

    @Test
    fun `PermissionResolved BACKGROUND_LOCATION always sends GoToMap regardless of grant`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        vm.onIntent(PermissionIntent.PermissionResolved(PermissionStep.BACKGROUND_LOCATION, granted = false))
        advanceUntilIdle()

        assertFalse(vm.state.value.backgroundLocationGranted)
        vm.effects.test {
            assertTrue(awaitItem() is PermissionEffect.GoToMap)
            expectNoEvents()
        }
    }

    @Test
    fun `ScreenResumed is a documented no-op — no state change, no effect`() = runTest(dispatcher) {
        val vm = PermissionViewModel()
        val stateBefore = vm.state.value

        vm.onIntent(PermissionIntent.ScreenResumed)
        advanceUntilIdle()

        assertEquals(stateBefore, vm.state.value)
        vm.effects.test { expectNoEvents() }
    }
}
