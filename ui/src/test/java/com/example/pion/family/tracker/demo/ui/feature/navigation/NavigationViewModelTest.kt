package com.example.pion.family.tracker.demo.ui.feature.navigation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.repository.LocationSource
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveNavigationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private const val TARGET_ID = "m-target"
private const val SELF_ID = "m-self"

/**
 * MVI doc §7. `ObserveNavigationUseCase` là class THẬT (không fake) — chỉ fake ba dependency của
 * nó ([LocationSource]/[MemberRepository]/[RoutingProvider]), cùng mẫu `MapViewModelTest`
 * ("real use case + fake repository", không fake use case).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val self = Member(id = SELF_ID, name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
    private val target = Member(id = TARGET_ID, name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)

    private fun viewModel(
        locationSource: FakeLocationSource,
        memberRepository: FakeMemberRepository,
        routingProvider: FakeRoutingProvider,
        trackingRepository: TrackingRepository = FakeTrackingRepository(initial = true),
        nowMs: () -> Long = { 0L },
    ) = NavigationViewModel(
        savedStateHandle = SavedStateHandle(mapOf("memberId" to TARGET_ID)),
        observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
        observeNavigation = ObserveNavigationUseCase(locationSource, memberRepository, routingProvider, nowMs),
        trackingRepository = trackingRepository,
    )

    @Test
    fun `a successful route populates directions, distance and non-empty OSM attribution`() = runTest(dispatcher) {
        val routingProvider = FakeRoutingProvider(AppResult.Success(directionsOf()))
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), mapOf(TARGET_ID to pointAt(10.05, 106.05))),
            routingProvider = routingProvider,
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.directions)
        assertFalse(state.isDistanceEstimated)
        assertFalse(state.isFallbackStraightLine)
        // Điều kiện pháp lý — docs/routing-and-map-attribution.md §3 mục 1: attributionLines KHÔNG
        // rỗng bất cứ khi nào có directions, và luôn chứa "OpenStreetMap contributors".
        assertTrue(state.attributionLines.isNotEmpty())
        assertTrue(state.attributionLines.contains("OpenStreetMap contributors"))
    }

    @Test
    fun `fallback state never shows OSM credit — attributionLines is empty`() = runTest(dispatcher) {
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), mapOf(TARGET_ID to pointAt(10.05, 106.05))),
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isFallbackStraightLine)
        assertTrue("no data OSM on screen while degraded — crediting it would misattribute", state.attributionLines.isEmpty())
    }

    @Test
    fun `a provider error sets error but never erases a route already drawn`() = runTest(dispatcher) {
        var now = 0L
        val routingProvider = FakeRoutingProvider(AppResult.Success(directionsOf()))
        val memberRepository = FakeMemberRepository(listOf(self, target), mapOf(TARGET_ID to pointAt(10.05, 106.05)))
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = memberRepository,
            routingProvider = routingProvider,
            nowMs = { now },
        )
        advanceUntilIdle()
        val routeBeforeFailure = vm.state.value.directions
        assertNotNull(routeBeforeFailure)

        // Ép một lần reroute MỚI: nhảy qua cửa sổ debounce 60s (RerouteEvaluator bước 4) VÀ dời
        // đích ra xa hơn `DESTINATION_MOVED_TOLERANCE_M` (200m) so với điểm cuối tuyến hiện có
        // (bước 5) — hai điều kiện thật của RerouteEvaluator, không phải giả lập tắt.
        now = 61_000L
        routingProvider.result = AppResult.Failure(AppError.Network("timeout"))
        memberRepository.updateLocation(TARGET_ID, pointAt(10.10, 106.10))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("provider lỗi không được xoá tuyến đang vẽ", routeBeforeFailure, state.directions)
        assertNotNull(state.error)
    }

    @Test
    fun `isFallbackStraightLine sticks through a later no-error emission while still unrouted`() = runTest(dispatcher) {
        val memberRepository = FakeMemberRepository(listOf(self, target), mapOf(TARGET_ID to pointAt(10.05, 106.05)))
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = memberRepository,
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.isFallbackStraightLine)
        assertNotNull(vm.state.value.error)

        // Emit KẾ TIẾP không mang lỗi (RerouteEvaluator bước 1 "arrived" trả về TRƯỚC khi chạm
        // logic reroute — provider không hề được gọi lại lần này, `lastError == null`), nhưng vẫn
        // CHƯA từng có tuyến thật (`directions` vẫn null). Đây là bằng chứng thật, qua use case
        // thật, cho đúng cạm bẫy nêu ở mục (b): suy `isFallbackStraightLine` thẳng từ
        // `lastError != null` sẽ tắt cờ ở emit này — SAI, vì chưa có tuyến thật nào.
        memberRepository.updateLocation(TARGET_ID, pointAt(10.0, 106.0))
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.hasArrived)
        assertNull(state.directions)
        assertTrue("isFallbackStraightLine phải DÍNH — chưa có tuyến thật nào", state.isFallbackStraightLine)
    }

    @Test
    fun `StopNavigation sends NavigateBack`() = runTest(dispatcher) {
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), emptyMap()),
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
        )
        vm.onIntent(NavigationIntent.StopNavigation)
        advanceUntilIdle()

        vm.effects.test {
            assertTrue(awaitItem() is NavigationEffect.NavigateBack)
            expectNoEvents()
        }
    }

    @Test
    fun `CameraCentered latches hasCenteredOnce`() = runTest(dispatcher) {
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), emptyMap()),
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
        )
        assertFalse(vm.state.value.hasCenteredOnce)

        vm.onIntent(NavigationIntent.CameraCentered)
        advanceUntilIdle()

        assertTrue(vm.state.value.hasCenteredOnce)
    }

    @Test
    fun `EnableTrackingRequested calls the repository, not a local flag, then confirms once`() = runTest(dispatcher) {
        val tracking = FakeTrackingRepository(initial = false)
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), emptyMap()),
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
            trackingRepository = tracking,
        )

        vm.onIntent(NavigationIntent.EnableTrackingRequested)
        advanceUntilIdle()

        assertEquals(listOf(true), tracking.setTrackingCalls)
        assertTrue(vm.state.value.isTracking) // re-emitted từ isTracking(), không gán tay
        vm.effects.test { assertTrue(awaitItem() is NavigationEffect.StartTracking) }
    }

    @Test
    fun `a failing enable-tracking reaches the repository again on retry, never strands the banner`() = runTest(dispatcher) {
        val tracking = FakeTrackingRepository(initial = false, throwOnSetTracking = IllegalStateException("boom"))
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), emptyMap()),
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
            trackingRepository = tracking,
        )

        vm.onIntent(NavigationIntent.EnableTrackingRequested)
        advanceUntilIdle()
        vm.effects.test { assertTrue(awaitItem() is NavigationEffect.ShowError) }

        tracking.throwOnSetTracking = null
        vm.onIntent(NavigationIntent.EnableTrackingRequested)
        advanceUntilIdle()
        assertEquals(listOf(true), tracking.setTrackingCalls)
    }

    // ---------------------------------------------------------------------------------------
    // feedback #3 (2026-08-26) — marker "tôi" phải đứng ở vị trí GPS THẬT, không ở điểm Room.
    // ---------------------------------------------------------------------------------------

    /**
     * Lỗi đã xảy ra thật: màn này chỉ đọc `observeLatestLocations()` (Room), tức điểm SEED ngẫu
     * nhiên của `DemoDataSeeder` khi self chưa từng bật theo dõi — trong khi `ObserveNavigationUseCase`
     * dựng tuyến từ `LocationSource` (GPS thật). Hệ quả trên máy: chấm xanh đứng ở Bến Bạch Đằng còn
     * tuyến lại xuất phát từ UBND TP.HCM, lệch hơn 1 km, trên CÙNG một màn hình.
     */
    @Test
    fun `the live GPS gate wins over the stored Room point for the self marker`() = runTest(dispatcher) {
        val livePoint = pointAt(10.7769, 106.7009)
        val roomPoint = pointAt(10.7870, 106.7060)
        val tracking = FakeTrackingRepository(initial = true, liveSelfLocation = livePoint)
        val vm = viewModel(
            locationSource = FakeLocationSource(livePoint),
            memberRepository = FakeMemberRepository(
                listOf(self, target),
                mapOf(SELF_ID to roomPoint, TARGET_ID to pointAt(10.80, 106.72)),
            ),
            routingProvider = FakeRoutingProvider(AppResult.Success(directionsOf())),
            trackingRepository = tracking,
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(livePoint, state.selfLocation)
        assertEquals(GeoPoint(livePoint.latitude, livePoint.longitude), state.selfPoint)
        // Điểm Room vẫn được giữ nguyên làm dự phòng — nó không bị vứt đi, chỉ bị xếp sau.
        assertEquals(roomPoint, state.storedSelfLocation)
    }

    /** Mặt còn lại của cùng một luật: chưa có fix GPS nào (chưa bật theo dõi) thì điểm Room là thứ
     * duy nhất còn lại — không thì chấm "tôi" biến mất mỗi lần mở lại app. */
    @Test
    fun `the stored Room point still draws the self marker until the live gate emits`() = runTest(dispatcher) {
        val roomPoint = pointAt(10.7870, 106.7060)
        val tracking = FakeTrackingRepository(initial = true, liveSelfLocation = null)
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), mapOf(SELF_ID to roomPoint)),
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
            trackingRepository = tracking,
        )
        advanceUntilIdle()
        assertEquals(roomPoint, vm.state.value.selfLocation)

        val livePoint = pointAt(10.7769, 106.7009)
        tracking.publishLiveSelf(livePoint)
        advanceUntilIdle()

        assertEquals("fix GPS đầu tiên phải chiếm chỗ ngay", livePoint, vm.state.value.selfLocation)
    }

    // ---------------------------------------------------------------------------------------
    // feedback #4 (2026-08-26) — hai đầu tuyến bám marker sống mà KHÔNG tốn thêm credit provider.
    // ---------------------------------------------------------------------------------------

    /**
     * BA đã chốt tính năng không phải realtime navigation, nên `RerouteEvaluator` giữ nguyên
     * debounce 60s + ngưỡng 200m: một thành viên đi vài chục mét KHÔNG được kéo theo một lần gọi
     * provider (quota GraphHopper free tier là 500 credit/NGÀY — LLM.md §13 Open #9). Nhưng
     * `targetPoint` PHẢI đổi theo, vì đó là mỏ neo mà đoạn nối nét đứt bám vào — không thì tuyến
     * đứng im ở chỗ cũ đúng như lỗi được báo.
     */
    @Test
    fun `a moving target moves the connector anchor without spending another provider call`() = runTest(dispatcher) {
        val memberRepository = FakeMemberRepository(
            listOf(self, target),
            mapOf(SELF_ID to pointAt(10.0, 106.0), TARGET_ID to pointAt(10.05, 106.05)),
        )
        val routingProvider = FakeRoutingProvider(AppResult.Success(directionsOf()))
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = memberRepository,
            routingProvider = routingProvider,
        )
        advanceUntilIdle()
        val callsAfterFirstRoute = routingProvider.callCount
        val routeEndBefore = vm.state.value.routeEnd

        // ~90m về phía đông — dưới DESTINATION_MOVED_TOLERANCE_M (200m), nên KHÔNG được reroute.
        val movedTo = pointAt(10.05, 106.0508)
        memberRepository.updateLocation(TARGET_ID, movedTo)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("không được tốn thêm credit cho một bước đi 90m", callsAfterFirstRoute, routingProvider.callCount)
        assertEquals(GeoPoint(movedTo.latitude, movedTo.longitude), state.targetPoint)
        assertEquals("tuyến thật giữ nguyên — mỏ neo cuối không được đi theo", routeEndBefore, state.routeEnd)
        assertFalse("có tuyến thật thì không còn ở nhánh đường thẳng", state.isStraightLineOnly)
    }

    /** Chưa có tuyến nào thì cả màn hình chỉ còn MỘT đường chim bay, và hai mỏ neo phải là `null`
     * để `NavigationMap` không vẽ chồng thêm hai đoạn nối lên chính nó. */
    @Test
    fun `with no route at all the screen falls back to a single straight line`() = runTest(dispatcher) {
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), mapOf(TARGET_ID to pointAt(10.05, 106.05))),
            routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null))),
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isStraightLineOnly)
        assertTrue(state.routePolyline.isEmpty())
        assertNull(state.routeStart)
        assertNull(state.routeEnd)
    }

    /**
     * **`isStraightLineOnly` không phải bí danh của `isFallbackStraightLine`, và đây là ca chứng
     * minh điều đó.** Khung hình ĐẦU TIÊN của một phiên: chưa có tuyến, mà cũng chưa có lỗi
     * provider nào — `isFallbackStraightLine` còn `false` theo đúng hợp đồng của nó (*lý do*, không
     * phải *hình dạng*). Nếu `NavigationMap` gác nhánh đường thẳng bằng cờ đó thì lúc này màn hình
     * không vẽ gì cả: hai marker rời rạc, không đường nào nối chúng, đúng lúc người dùng vừa bấm
     * "Chỉ đường" và đang chờ xem cái gì đó xuất hiện.
     */
    @Test
    fun `the first frame of a session already draws a straight line, before any provider failure`() {
        val state = NavigationState(
            storedSelfLocation = pointAt(10.0, 106.0),
            targetLocation = pointAt(10.05, 106.05),
        )

        assertFalse("chưa gọi provider lần nào thì chưa có *lý do* nào để dính", state.isFallbackStraightLine)
        assertTrue("nhưng *hình dạng* thì đã là đường thẳng ngay từ khung hình đầu", state.isStraightLineOnly)
        assertNull(state.routeStart)
        assertNull(state.routeEnd)
    }

    /** Mặt kia: tuyến thật về thì cả hai cùng tắt — `applyUpdate` cho `isFallbackStraightLine` về
     * `false` ngay khi `update.directions != null`, nên dải ghi công thôi nói "ước tính" đúng lúc
     * màn hình thôi vẽ đường chim bay. Hai thứ phải tắt CÙNG NHAU, và ca này khoá điều đó. */
    @Test
    fun `a route arriving after a failure clears both the straight-line branch and the sticky flag`() = runTest(dispatcher) {
        val routingProvider = FakeRoutingProvider(AppResult.Failure(AppError.Network(null)))
        val vm = viewModel(
            locationSource = FakeLocationSource(pointAt(10.0, 106.0)),
            memberRepository = FakeMemberRepository(listOf(self, target), mapOf(TARGET_ID to pointAt(10.05, 106.05))),
            routingProvider = routingProvider,
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.isFallbackStraightLine)
        assertTrue(vm.state.value.isStraightLineOnly)

        routingProvider.result = AppResult.Success(directionsOf())
        vm.onIntent(NavigationIntent.Retry)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isFallbackStraightLine)
        assertFalse(state.isStraightLineOnly)
        assertNotNull("tuyến thật về thì hai mỏ neo của đoạn nối phải có giá trị", state.routeStart)
        assertNotNull(state.routeEnd)
    }

    private fun directionsOf() = Directions(
        points = listOf(GeoPoint(10.0, 106.0), GeoPoint(10.05, 106.05)),
        distanceMeters = 1_200.0,
        durationSeconds = 300L,
        engineId = "fake-engine",
        attribution = listOf("FakeEngine", "OpenStreetMap contributors"),
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

private class FakeLocationSource(initial: LocationPoint) : LocationSource {
    private val flow = MutableStateFlow(initial)
    override fun stream(): Flow<LocationPoint> = flow
}

private class FakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members)
    private val locationsFlow = MutableStateFlow(locations)
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
    fun updateLocation(memberId: String, point: LocationPoint) {
        locationsFlow.value = locationsFlow.value + (memberId to point)
    }
}

private class FakeRoutingProvider(var result: AppResult<Directions>) : RoutingProvider {
    var callCount = 0
        private set

    override suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions> {
        callCount++
        return result
    }
}

private class FakeTrackingRepository(
    initial: Boolean = false,
    var throwOnSetTracking: Throwable? = null,
    liveSelfLocation: LocationPoint? = null,
) : TrackingRepository {
    private val trackingFlow = MutableStateFlow(initial)
    private val liveSelfFlow = MutableStateFlow(liveSelfLocation)
    val setTrackingCalls = mutableListOf<Boolean>()

    override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> = flowOf(emptyList())
    override suspend fun record(point: LocationPoint) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
    override fun isTracking(): Flow<Boolean> = trackingFlow

    /** feedback #3 — cổng vị trí GPS THẬT mà màn Chỉ đường nay đọc, đúng cổng `MapViewModel` đọc.
     * `flowOf(null)` cứng như bản cũ sẽ làm mọi ca ở đây xanh vĩnh viễn dù fix có còn hay không. */
    override fun observeLiveSelfLocation(): Flow<LocationPoint?> = liveSelfFlow

    fun publishLiveSelf(point: LocationPoint) {
        liveSelfFlow.value = point
    }

    override suspend fun setTracking(enabled: Boolean) {
        throwOnSetTracking?.let { throw it }
        setTrackingCalls.add(enabled)
        trackingFlow.value = enabled
    }

    override suspend fun runSimulation(fixes: List<SimulatedFix>) = Unit
}
