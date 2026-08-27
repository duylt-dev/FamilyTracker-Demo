package com.example.pion.family.tracker.demo.ui.feature.map

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.RouteSourceInfo
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.NetworkMonitor
import com.example.pion.family.tracker.demo.domain.repository.SimulatedRouteRepository
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZonesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** A repository whose `isTracking()` flow throws instead of emitting — simulates a Room-backed
 * `Flow` hitting `SQLiteException`, which is the failure mode phase-10+ screens will actually
 * face once they copy this init-block pattern. */
private class ThrowingTrackingRepository : TrackingRepository {
    override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> = flowOf(emptyList())
    override suspend fun record(point: LocationPoint) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
    override fun isTracking(): Flow<Boolean> = flow { throw IllegalStateException("boom-from-isTracking") }
    /** phase-01 (D4): test này chỉ ném từ `isTracking()`; cổng hiển thị im lặng để lỗi được
     * quy về đúng một nguồn. */
    override fun observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)
    override suspend fun setTracking(enabled: Boolean) = Unit
    override suspend fun runSimulation(fixes: List<SimulatedFix>) = Unit
}

private class EmptyZoneRepository : ZoneRepository {
    override fun observeAll(): Flow<List<Zone>> = flowOf(emptyList())
    override suspend fun save(zone: Zone) = error("not used in this test")
    override suspend fun delete(zoneId: String) = error("not used in this test")
    override suspend fun count(): Int = 0
    override suspend fun exists(zoneId: String): Boolean = false
}

private class EmptyMemberRepository : MemberRepository {
    override fun observeAll(): Flow<List<Member>> = flowOf(emptyList())
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = flowOf(emptyMap())
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}

/** phase-05 — nguồn thứ năm của `MapViewModel.init`, không liên quan tới ca `isTracking` test này
 * nhắm tới. `emptyFlow()`: hoàn tất ngay không phát gì, không gây nhiễu tới bộ bắt ngoại lệ. */
private class NoOpSimulatedRouteRepository : SimulatedRouteRepository {
    override fun observeSource(): Flow<RouteSourceInfo> = emptyFlow()
}

/** phase-07 — nguồn thứ sáu, không liên quan tới ca `isTracking` ở trên: hoàn tất ngay, không phát gì. */
private class NoOpNetworkMonitor : NetworkMonitor {
    override fun observeHasInternet(): Flow<Boolean> = emptyFlow()
}

/** phase-07 — đối xứng với `ThrowingTrackingRepository`, nhưng cho nguồn mạng: chứng minh
 * `collectSafely` là một SÀN áp dụng cho MỌI nguồn độc lập trong `init`, không riêng `isTracking`. */
private class ThrowingNetworkMonitor : NetworkMonitor {
    override fun observeHasInternet(): Flow<Boolean> = flow { throw IllegalStateException("boom-from-network") }
}

/** Một `TrackingRepository` bình thường, không ném gì — dùng ở ca `NetworkMonitor` ném lỗi để
 * chứng minh các nguồn KHÁC (ở đây là `isTracking`) vẫn cập nhật bình thường trong cùng `init`. */
private class OkTrackingRepository : TrackingRepository {
    override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> = flowOf(emptyList())
    override suspend fun record(point: LocationPoint) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
    override fun isTracking(): Flow<Boolean> = flowOf(true)
    override fun observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)
    override suspend fun setTracking(enabled: Boolean) = Unit
    override suspend fun runSimulation(fixes: List<SimulatedFix>) = Unit
}

/**
 * Regression test for `plans/260821-1113-geofence-zone-and-history-tracking/reports/fix-phase-04-report.md`.
 *
 * `MapViewModel.init` used to observe `trackingRepository.isTracking()` via
 * `.onEach { ... }.launchIn(viewModelScope)` — a `viewModelScope.launch { collect { ... } }` in
 * disguise that does **not** go through `launchSafely` (MVI doc §1, non-negotiable rule). This
 * test proves that bypass by feeding a throwing `Flow` and capturing the thread's uncaught
 * exception handler, since nothing in `MviViewModel`/`MapViewModel` catches it — the exception
 * would otherwise crash the app's main thread on a real device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelLaunchSafetyTest {

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
    fun `isTracking failure is caught by launchSafely instead of escaping uncaught`() = runTest(dispatcher) {
        var uncaught: Throwable? = null
        val previousHandler = Thread.currentThread().uncaughtExceptionHandler
        Thread.currentThread().setUncaughtExceptionHandler { _, throwable -> uncaught = throwable }

        try {
            MapViewModel(
                observeZones = ObserveZonesUseCase(EmptyZoneRepository()),
                observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(EmptyMemberRepository()),
                trackingRepository = ThrowingTrackingRepository(),
                simulatedRouteRepository = NoOpSimulatedRouteRepository(),
                networkMonitor = NoOpNetworkMonitor(),
            )
            advanceUntilIdle()
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(previousHandler)
        }

        assertTrue(
            "Expected no uncaught exception to escape MapViewModel.init — got: $uncaught",
            uncaught == null,
        )
    }

    /**
     * phase-07 (US-47/D8, NFR-2) — same bug shape as above, this time for the sixth
     * `collectSafely`: a `NetworkMonitor` whose flow throws must not crash `MapViewModel.init`,
     * and — the part `isTracking failure...` above does not prove — the OTHER independent sources
     * wired up in the same `init` must keep working. `collectSafely` is a floor per source, not a
     * single shared try/catch around the whole `init` block.
     */
    @Test
    fun `a NetworkMonitor that throws is caught too, other sources keep updating`() = runTest(dispatcher) {
        var uncaught: Throwable? = null
        val previousHandler = Thread.currentThread().uncaughtExceptionHandler
        Thread.currentThread().setUncaughtExceptionHandler { _, throwable -> uncaught = throwable }

        lateinit var vm: MapViewModel
        try {
            vm = MapViewModel(
                observeZones = ObserveZonesUseCase(EmptyZoneRepository()),
                observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(EmptyMemberRepository()),
                trackingRepository = OkTrackingRepository(),
                simulatedRouteRepository = NoOpSimulatedRouteRepository(),
                networkMonitor = ThrowingNetworkMonitor(),
            )
            advanceUntilIdle()
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(previousHandler)
        }

        assertTrue(
            "Expected no uncaught exception to escape MapViewModel.init when NetworkMonitor throws — got: $uncaught",
            uncaught == null,
        )
        assertTrue(
            "isTracking (một nguồn collectSafely KHÁC) vẫn phải cập nhật dù networkMonitor ném lỗi",
            vm.state.value.isTracking,
        )
    }
}
