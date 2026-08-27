package com.example.pion.family.tracker.demo.ui.feature.map

import app.cash.turbine.test
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.RouteSourceInfo
import com.example.pion.family.tracker.demo.domain.model.RouteSourceKind
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.NetworkMonitor
import com.example.pion.family.tracker.demo.domain.repository.SimulatedRouteRepository
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZonesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * MVI doc §7 — reducer test mỗi Intent, effect test mỗi Effect, crash-containment cho
 * `onToggleTracking`, và "unbounded growth" cho `zones` (phase-05 Implementation Step 10).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        zones: List<Zone> = emptyList(),
        members: List<Member> = emptyList(),
        locations: Map<String, LocationPoint> = emptyMap(),
        tracking: TrackingRepository = FakeTrackingRepository(),
        routeSource: SimulatedRouteRepository = FakeSimulatedRouteRepository(),
        networkMonitor: NetworkMonitor = FakeNetworkMonitor(),
    ) = MapViewModel(
        observeZones = ObserveZonesUseCase(FakeZoneRepository(zones)),
        observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(FakeMemberRepository(members, locations)),
        trackingRepository = tracking,
        simulatedRouteRepository = routeSource,
        networkMonitor = networkMonitor,
    )

    @Test
    fun `PermissionStateChanged updates both granted flags`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(MapIntent.PermissionStateChanged(notificationsGranted = false, fineLocationGranted = false))
        advanceUntilIdle()

        assertTrue(vm.state.value.showNotificationsBanner)
        assertTrue(vm.state.value.showLocationDegradedBanner)
    }

    @Test
    fun `ToggleTracking flips tracking through the repository, not a local flag`() = runTest(dispatcher) {
        val tracking = FakeTrackingRepository(initial = false)
        val vm = viewModel(tracking = tracking)

        vm.onIntent(MapIntent.ToggleTracking)
        advanceUntilIdle()

        assertEquals(listOf(true), tracking.setTrackingCalls)
        assertTrue(vm.state.value.isTracking) // re-emitted from isTracking(), not assigned directly
    }

    @Test
    fun `MapLongPressed sends OpenZoneEditor with the pressed coordinates`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(MapIntent.MapLongPressed(lat = 10.77, lng = 106.70))
        advanceUntilIdle()

        vm.effects.test {
            val effect = awaitItem()
            assertTrue(effect is MapEffect.OpenZoneEditor)
            assertEquals(10.77, (effect as MapEffect.OpenZoneEditor).lat, 0.0)
            assertEquals(106.70, effect.lng, 0.0)
            expectNoEvents()
        }
    }

    @Test
    fun `MemberTapped records the id, not the object`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(MapIntent.MemberTapped("member-1"))
        advanceUntilIdle()

        assertEquals("member-1", vm.state.value.selectedMemberId)
    }

    @Test
    fun `CameraCentered latches hasCenteredOnce`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(!vm.state.value.hasCenteredOnce)

        vm.onIntent(MapIntent.CameraCentered)
        advanceUntilIdle()

        assertTrue(vm.state.value.hasCenteredOnce)
    }

    @Test
    fun `ZoneListRequested, HistoryRequested and TimelineRequested each send their own effect`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(MapIntent.ZoneListRequested)
        advanceUntilIdle()
        vm.effects.test {
            assertTrue(awaitItem() is MapEffect.OpenZoneList)
        }

        vm.onIntent(MapIntent.HistoryRequested)
        advanceUntilIdle()
        vm.effects.test {
            assertTrue(awaitItem() is MapEffect.OpenHistory)
        }

        vm.onIntent(MapIntent.TimelineRequested)
        advanceUntilIdle()
        vm.effects.test {
            assertTrue(awaitItem() is MapEffect.OpenTimeline)
        }
    }

    @Test
    fun `a failing setTracking lowers no flag it never raised and reaches the repository again on retry`() = runTest(dispatcher) {
        val tracking = FakeTrackingRepository(throwOnSetTracking = IllegalStateException("boom"))
        val vm = viewModel(tracking = tracking)

        vm.onIntent(MapIntent.ToggleTracking)
        advanceUntilIdle()

        vm.effects.test {
            val effect = awaitItem()
            assertTrue(effect is MapEffect.ShowError)
            assertTrue((effect as MapEffect.ShowError).error is AppError.Unexpected)
        }

        // Retry proves the ViewModel is not stuck — matches MVI doc §7 "Test it by retrying".
        tracking.throwOnSetTracking = null
        vm.onIntent(MapIntent.ToggleTracking)
        advanceUntilIdle()
        assertEquals(listOf(true), tracking.setTrackingCalls)
    }

    @Test
    fun `500 zones in the fake are capped at MAX_ZONES, the real invariant SaveZoneUseCase enforces`() = runTest(dispatcher) {
        val manyZones = (1..500).map { zoneOf("z-$it") }
        val vm = viewModel(zones = manyZones)
        advanceUntilIdle()

        assertEquals(TrackingConstants.MAX_ZONES, vm.state.value.zones.size)
    }

    @Test
    fun `selfLocation and otherMembers are derived, self is excluded from otherMembers`() = runTest(dispatcher) {
        val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
        val minh = Member(id = "m-minh", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
        val point = pointAt(10.0, 106.0)
        val vm = viewModel(members = listOf(self, minh), locations = mapOf("m-self" to point, "m-minh" to point))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("m-self", state.selfLocation?.member?.id)
        assertEquals(listOf("m-minh"), state.otherMembers.map { it.member.id })
    }

    @Test
    fun `initialCameraTarget falls back to any member's point when self has none yet`() = runTest(dispatcher) {
        val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
        val minh = Member(id = "m-minh", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
        val minhPoint = pointAt(10.78, 106.70)
        // self chưa từng bật theo dõi -> không có location cho self, chỉ Minh có.
        val vm = viewModel(members = listOf(self, minh), locations = mapOf("m-minh" to minhPoint))
        advanceUntilIdle()

        assertNull(vm.state.value.selfLocation?.lastLocation)
        assertEquals(minhPoint, vm.state.value.initialCameraTarget)
    }

    @Test
    fun `initialCameraTarget prefers self's own point once it exists`() = runTest(dispatcher) {
        val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
        val minh = Member(id = "m-minh", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
        val selfPoint = pointAt(10.70, 106.60)
        val minhPoint = pointAt(10.78, 106.70)
        val vm = viewModel(members = listOf(self, minh), locations = mapOf("m-self" to selfPoint, "m-minh" to minhPoint))
        advanceUntilIdle()

        assertEquals(selfPoint, vm.state.value.initialCameraTarget)
    }

    @Test
    fun `a member with no recorded point yet still appears, with a null location`() = runTest(dispatcher) {
        val minh = Member(id = "m-minh", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
        val vm = viewModel(members = listOf(minh), locations = emptyMap())
        advanceUntilIdle()

        assertNull(vm.state.value.otherMembers.single().lastLocation)
    }

    /**
     * phase-01, D4 (`decisions.md` §C3), S2/QA-SRM-20 — toạ độ marker trùng khít toạ độ phát ra qua
     * `observeLiveSelfLocation()`, delta CHÍNH XÁC 0.0, kể cả khi Room đã có một điểm khác hẳn:
     * live PHẢI thắng, không trộn/nội suy với Room.
     */
    @Test
    fun `a live self fix overrides whatever Room last recorded, coordinates match exactly`() = runTest(dispatcher) {
        val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
        val roomPoint = pointAt(10.70, 106.60)
        val tracking = FakeTrackingRepository()
        val vm = viewModel(members = listOf(self), locations = mapOf("m-self" to roomPoint), tracking = tracking)
        advanceUntilIdle()

        val livePoint = pointAt(21.0, 105.8).copy(accuracyMeters = 80f)
        tracking.publishLiveSelfLocation(livePoint)
        advanceUntilIdle()

        val drawn = vm.state.value.selfLocation!!.lastLocation!!
        assertEquals(livePoint.latitude, drawn.latitude, 0.0)
        assertEquals(livePoint.longitude, drawn.longitude, 0.0)
    }

    /**
     * S4/QA-SRM-18 — mở app lần đầu trong nhà: Room rỗng (self chưa từng có điểm ghi), fix đầu tiên
     * `accuracy = 90f` (> `MAX_ACCURACY_M`). Chấm xanh vẫn phải được vẽ — chính là ca hồi quy D6.
     */
    @Test
    fun `first indoor fix draws the self marker even with an empty Room history`() = runTest(dispatcher) {
        val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
        val tracking = FakeTrackingRepository()
        val vm = viewModel(members = listOf(self), locations = emptyMap(), tracking = tracking)
        advanceUntilIdle()
        assertNull(vm.state.value.selfLocation?.lastLocation)

        val indoorFix = pointAt(21.0, 105.8).copy(accuracyMeters = 90f)
        tracking.publishLiveSelfLocation(indoorFix)
        advanceUntilIdle()

        assertEquals(indoorFix, vm.state.value.selfLocation?.lastLocation)
    }

    /**
     * phase-05, US-46, QA-SRM-30 — tầng PROVIDER/CACHE: [MapState.attributionLines] đọc thẳng
     * `RouteSourceInfo.attribution` (đã GỘP ở `RouteSourceAggregator`, `:data` — `:ui` không tự
     * ghép), không có ngày ghi sai nguồn (`isFallbackRoute` false).
     */
    @Test
    fun `a PROVIDER route source shows attribution and is not a fallback`() = runTest(dispatcher) {
        val routeSource = FakeSimulatedRouteRepository()
        val vm = viewModel(routeSource = routeSource)
        advanceUntilIdle()

        routeSource.publish(RouteSourceInfo(kind = RouteSourceKind.PROVIDER, attribution = listOf("GraphHopper", "OpenStreetMap contributors")))
        advanceUntilIdle()

        assertEquals(listOf("GraphHopper", "OpenStreetMap contributors"), vm.state.value.attributionLines)
        assertFalse(vm.state.value.isFallbackRoute)
    }

    /**
     * phase-05, US-46, QA-SRM-32/16, X5 — tầng SYNTHETIC không mang dữ liệu OSM nào: ghi công lúc
     * đó là ghi sai nguồn, nên [MapState.attributionLines] PHẢI rỗng dù `RouteSourceInfo` tồn tại.
     */
    @Test
    fun `a SYNTHETIC route source has no attribution and is flagged as a fallback`() = runTest(dispatcher) {
        val routeSource = FakeSimulatedRouteRepository()
        val vm = viewModel(routeSource = routeSource)
        advanceUntilIdle()

        routeSource.publish(RouteSourceInfo(kind = RouteSourceKind.SYNTHETIC, attribution = emptyList()))
        advanceUntilIdle()

        assertTrue(vm.state.value.attributionLines.isEmpty())
        assertTrue(vm.state.value.isFallbackRoute)
    }

    /**
     * FR-4 — màn vừa mở, `SimulatedRouteRepository.observeSource()` chưa phát gì:
     * `MapState.routeSource` giữ default `null`, cả hai `val` tính toán đều rỗng/false, không có
     * gì để `RoutingAttribution` vẽ.
     */
    @Test
    fun `no route source yet leaves both attribution and fallback empty`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.state.value.routeSource)
        assertTrue(vm.state.value.attributionLines.isEmpty())
        assertFalse(vm.state.value.isFallbackRoute)
    }

    /** phase-07, US-47/D8, QA-SRM-13 — mất internet đã kiểm chứng phải bật lớp phủ ngay. */
    @Test
    fun `no verified internet shows the blocking overlay`() = runTest(dispatcher) {
        val network = FakeNetworkMonitor(initial = false)
        val vm = viewModel(networkMonitor = network)
        advanceUntilIdle()

        assertFalse(vm.state.value.hasInternet)
        assertTrue(vm.state.value.showNoInternetOverlay)
    }

    /**
     * FR-4/QA-SRM-17 — có mạng trở lại thì lớp phủ TỰ đóng, không một Intent nào được bơm. Đây là
     * bằng chứng "state, không phải Effect" (Key Insight #4): nếu lớp phủ từng là một Effect, phiên
     * bản này sẽ không có gì để bơm lại và ca này sẽ không mô tả đúng hành vi thật.
     */
    @Test
    fun `internet back closes the overlay without any intent`() = runTest(dispatcher) {
        val network = FakeNetworkMonitor(initial = false)
        val vm = viewModel(networkMonitor = network)
        advanceUntilIdle()
        assertTrue(vm.state.value.showNoInternetOverlay)

        network.flow.value = true
        advanceUntilIdle()

        assertTrue(vm.state.value.hasInternet)
        assertFalse(vm.state.value.showNoInternetOverlay)
    }

    /**
     * **Ca âm sống còn của QA-SRM-40 (Key Insight #1 phase-07).** Vẫn có internet, nhưng nhà cung
     * cấp routing vừa hạ cấp xuống tầng SYNTHETIC (401/429/400/timeout, phase 04) — lớp phủ KHÔNG
     * ĐƯỢC bật. Hỏng ca này ⇒ lớp phủ kẹt vĩnh viễn: điều kiện tắt là "có internet", mà internet
     * vẫn đang có, nên nó sẽ không bao giờ tự đóng được. `MapState.hasInternet`/
     * `showNoInternetOverlay` phải hoàn toàn không quan tâm `routeSource` là gì.
     */
    @Test
    fun `provider failure while online never blocks the map`() = runTest(dispatcher) {
        val routeSource = FakeSimulatedRouteRepository()
        val vm = viewModel(networkMonitor = FakeNetworkMonitor(initial = true), routeSource = routeSource)
        advanceUntilIdle()

        routeSource.publish(RouteSourceInfo(kind = RouteSourceKind.SYNTHETIC, attribution = emptyList()))
        advanceUntilIdle()

        assertTrue(vm.state.value.hasInternet)
        assertFalse(vm.state.value.showNoInternetOverlay)
    }

    /**
     * phase-07 — **giá trị MẶC ĐỊNH của `hasInternet` là một quyết định, không phải chi tiết.**
     * `MapState()` được dựng trước khi `NetworkMonitor` phát lần đầu (vài mili-giây). Mặc định
     * `false` ⇒ một lớp phủ KHÔNG ĐÓNG ĐƯỢC nháy lên ở MỌI lần mở app, kể cả khi mạng hoàn toàn
     * bình thường — hồi quy nhìn thấy được bằng mắt trên mọi lần chạy. Khoảng trống này an toàn CHỈ
     * VÌ `AndroidNetworkMonitor` đọc trạng thái hiện tại làm giá trị đầu tiên (Key Insight #3,
     * `AndroidNetworkMonitorContractTest` ghim phía bên kia). **Thêm ở review phase-07:** đổi mặc
     * định thành `false` KHÔNG làm ca nào đỏ trước khi có ca này.
     */
    @Test
    fun `a freshly built MapState never shows the blocking overlay`() {
        val fresh = MapState()

        assertTrue("mặc định phải là `true` — xem KDoc", fresh.hasInternet)
        assertFalse("lớp phủ không đóng được KHÔNG được nháy lên lúc mở app", fresh.showNoInternetOverlay)
    }

    private fun zoneOf(id: String) = Zone(
        id = id,
        name = "Zone $id",
        latitude = 10.77,
        longitude = 106.70,
        radiusMeters = 150f,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-21T00:00:00Z"),
    )

    private fun pointAt(lat: Double, lng: Double) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-21T08:00:00Z"),
    )
}

/** Fake in-memory [ZoneRepository] — chỉ đủ hành vi cho test ViewModel, không phải mock giả build xanh. */
private class FakeZoneRepository(zones: List<Zone>) : ZoneRepository {
    private val flow = MutableStateFlow(zones).asStateFlow()
    override fun observeAll(): Flow<List<Zone>> = flow
    override suspend fun save(zone: Zone) = error("not used by MapViewModelTest")
    override suspend fun delete(zoneId: String) = error("not used by MapViewModelTest")
    override suspend fun count(): Int = flow.value.size
    override suspend fun exists(zoneId: String): Boolean = flow.value.any { it.id == zoneId }
}

private class FakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}

private class FakeTrackingRepository(
    initial: Boolean = false,
    var throwOnSetTracking: Throwable? = null,
) : TrackingRepository {
    private val trackingFlow = MutableStateFlow(initial)
    // phase-01, D4 — nguồn thứ tư MapViewModel collect độc lập (§C3 "decisions.md").
    private val liveSelfLocationFlow = MutableStateFlow<LocationPoint?>(null)
    val setTrackingCalls = mutableListOf<Boolean>()

    override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> = flowOf(emptyList())
    override suspend fun record(point: LocationPoint) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
    override fun isTracking(): Flow<Boolean> = trackingFlow
    override fun observeLiveSelfLocation(): Flow<LocationPoint?> = liveSelfLocationFlow

    override suspend fun setTracking(enabled: Boolean) {
        throwOnSetTracking?.let { throw it }
        setTrackingCalls.add(enabled)
        trackingFlow.value = enabled
    }

    override suspend fun runSimulation(fixes: List<SimulatedFix>) = Unit

    fun publishLiveSelfLocation(point: LocationPoint) {
        liveSelfLocationFlow.value = point
    }
}

/**
 * `MutableSharedFlow` (replay 1), KHÔNG `MutableStateFlow` — `observeSource()` không có giá trị
 * ban đầu hợp lệ để cấp (FR-4, "chưa có nguồn" là "chưa phát gì", không phải `null` trôi qua kiểu
 * không-null của `RouteSourceInfo`). Không gọi [publish] tái tạo đúng ca "màn vừa mở".
 */
private class FakeSimulatedRouteRepository : SimulatedRouteRepository {
    private val sourceFlow = MutableSharedFlow<RouteSourceInfo>(replay = 1)
    override fun observeSource(): Flow<RouteSourceInfo> = sourceFlow
    fun publish(source: RouteSourceInfo) {
        sourceFlow.tryEmit(source)
    }
}

/** phase-07 (US-47/D8) — `MutableStateFlow`, KHÔNG `MutableSharedFlow`: `AndroidNetworkMonitor`
 * thật LUÔN có một giá trị ngay từ lần collect đầu tiên (đọc trạng thái hiện tại trước khi đăng ký
 * callback, Key Insight #3), khác hẳn `SimulatedRouteRepository` — cùng fake mang hai kiểu flow
 * khác nhau là để phản ánh đúng hai hợp đồng khác nhau, không phải ngẫu nhiên. */
private class FakeNetworkMonitor(initial: Boolean = true) : NetworkMonitor {
    val flow = MutableStateFlow(initial)
    override fun observeHasInternet(): Flow<Boolean> = flow
}
