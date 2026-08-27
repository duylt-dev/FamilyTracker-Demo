package com.example.pion.family.tracker.demo.ui.feature.history

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveRouteForDayUseCase
import com.example.pion.family.tracker.demo.domain.usecase.SaveZoneUseCase
import com.example.pion.family.tracker.demo.domain.usecase.StartSimulationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * MVI doc §7. `TrackSession`/`RouteStats` are already covered exhaustively as pure functions in
 * `:domain` (`RouteSplitterTest`/`RouteStatsTest`, phase-03) — this file only exercises the
 * ViewModel's OWN job: initial state from route args, self-memberId resolution, cancel-and-replace
 * on `SelectDay`, default-selection (longest trip), and crash containment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)

    private fun handle(epochDay: Long? = null, focusLat: Double? = null, focusLng: Double? = null) =
        SavedStateHandle(mapOf("epochDay" to epochDay, "focusLat" to focusLat, "focusLng" to focusLng))

    private fun viewModel(
        savedStateHandle: SavedStateHandle = handle(),
        trackingRepository: TrackingRepository = HistoryFakeTrackingRepository(),
        memberRepository: MemberRepository = HistoryFakeMemberRepository(listOf(self), emptyMap()),
        zoneRepository: ZoneRepository = HistoryFakeZoneRepository(),
    ) = HistoryViewModel(
        savedStateHandle = savedStateHandle,
        observeRouteForDay = ObserveRouteForDayUseCase(trackingRepository),
        observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
        startSimulation = StartSimulationUseCase(
            zoneRepository = zoneRepository,
            saveZoneUseCase = SaveZoneUseCase(zoneRepository),
            observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
            trackingRepository = trackingRepository,
        ),
    )

    /** US-33 tests need self to actually have a recorded position — `viewModel()`'s default
     * `memberRepository` intentionally leaves locations empty for the pre-existing route tests. */
    private fun selfLocatedMemberRepository() =
        HistoryFakeMemberRepository(listOf(self), mapOf(self.id to pointAt(10.77, 106.70, Instant.now())))

    private fun sessionOf(id: String, day: LocalDate, distanceMeters: Double) = TrackSession(
        id = id,
        memberId = self.id,
        startedAt = day.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        endedAt = day.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(600),
        points = listOf(pointAt(10.77, 106.70, day.atStartOfDay(ZoneId.systemDefault()).toInstant())),
        distanceMeters = distanceMeters,
    )

    private fun pointAt(lat: Double, lng: Double, at: Instant) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = at,
    )

    // --- initialStateFrom (route args -> initial state), MVI doc §3 luật 5 ---

    @Test
    fun `initialStateFrom uses today when epochDay is absent, otherwise the given day`() {
        assertEquals(LocalDate.now(), initialStateFrom(handle()).selectedDay)
        val explicitDay = LocalDate.now().minusDays(3)
        assertEquals(explicitDay, initialStateFrom(handle(epochDay = explicitDay.toEpochDay())).selectedDay)
    }

    // --- ngày rỗng — US-32 ---

    @Test
    fun `an empty day yields empty sessions, null stats, and no crash`() = runTest(dispatcher) {
        val vm = viewModel(trackingRepository = HistoryFakeTrackingRepository())
        advanceUntilIdle()

        assertTrue(vm.state.value.sessions.isEmpty())
        assertEquals(null, vm.state.value.stats)
        assertTrue(vm.state.value.isEmptyDay)
        assertFalse(vm.state.value.isLoading)
    }

    // --- US-30: default selection is the LONGEST trip, not the first in the list ---

    @Test
    fun `sessions arriving pick the longest trip as default, not the first`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val repository = HistoryFakeTrackingRepository()
        repository.emit(today, listOf(sessionOf("short", today, 100.0), sessionOf("long", today, 5_000.0)))
        val vm = viewModel(trackingRepository = repository)
        advanceUntilIdle()

        assertEquals("long", vm.state.value.selectedSessionId)
        assertEquals(5_000.0, vm.state.value.stats!!.distanceMeters, 0.0)
    }

    @Test
    fun `SelectSession changes selection and stats follows it (derived, not stored separately)`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val repository = HistoryFakeTrackingRepository()
        repository.emit(today, listOf(sessionOf("a", today, 100.0), sessionOf("b", today, 5_000.0)))
        val vm = viewModel(trackingRepository = repository)
        advanceUntilIdle()
        assertEquals("b", vm.state.value.selectedSessionId) // longest by default

        vm.onIntent(HistoryIntent.SelectSession("a"))
        advanceUntilIdle()

        assertEquals("a", vm.state.value.selectedSessionId)
        assertEquals(100.0, vm.state.value.stats!!.distanceMeters, 0.0)
    }

    // --- US-27: SelectDay cancels-and-replaces the observation job ---

    @Test
    fun `SelectDay cancels the previous day's flow and switches to the new day's data`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val repository = HistoryFakeTrackingRepository()
        repository.emit(today, listOf(sessionOf("s-today", today, 500.0)))
        repository.emit(yesterday, listOf(sessionOf("s-yesterday", yesterday, 900.0)))
        val vm = viewModel(trackingRepository = repository)
        advanceUntilIdle()
        assertEquals("s-today", vm.state.value.sessions.single().id)

        vm.onIntent(HistoryIntent.SelectDay(yesterday))
        advanceUntilIdle()
        assertEquals("s-yesterday", vm.state.value.sessions.single().id)

        // Today's flow updating AFTER we switched away must not leak back into state — proves the
        // old job was actually cancelled, not merely ignored.
        repository.emit(today, listOf(sessionOf("s-today-2", today, 100.0)))
        advanceUntilIdle()
        assertEquals("s-yesterday", vm.state.value.sessions.single().id)
    }

    @Test
    fun `bumping SelectDay three times rapidly ends on the last day requested`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val d1 = today.minusDays(1)
        val d2 = today.minusDays(2)
        val d3 = today.minusDays(3)
        val repository = HistoryFakeTrackingRepository()
        repository.emit(d1, listOf(sessionOf("s1", d1, 1.0)))
        repository.emit(d2, listOf(sessionOf("s2", d2, 2.0)))
        repository.emit(d3, listOf(sessionOf("s3", d3, 3.0)))
        val vm = viewModel(trackingRepository = repository)
        advanceUntilIdle()

        vm.onIntent(HistoryIntent.SelectDay(d1))
        vm.onIntent(HistoryIntent.SelectDay(d2))
        vm.onIntent(HistoryIntent.SelectDay(d3))
        advanceUntilIdle()

        assertEquals(d3, vm.state.value.selectedDay)
        assertEquals("s3", vm.state.value.sessions.single().id)
    }

    // --- self memberId/color resolution ---

    @Test
    fun `self member's color is loaded into state once self is known`() = runTest(dispatcher) {
        val vm = viewModel(memberRepository = HistoryFakeMemberRepository(listOf(self), emptyMap()))
        advanceUntilIdle()
        assertEquals(self.colorArgb, vm.state.value.memberColorArgb)
    }

    // --- US-35 plumbing (phase-10 consumes it) ---

    @Test
    fun `route with focus coordinates sends FocusCamera exactly once in init`() = runTest(dispatcher) {
        val vm = viewModel(savedStateHandle = handle(focusLat = 10.5, focusLng = 106.5))
        advanceUntilIdle()

        vm.effects.test {
            val effect = awaitItem() as HistoryEffect.FocusCamera
            assertEquals(10.5, effect.lat, 0.0)
            assertEquals(106.5, effect.lng, 0.0)
            expectNoEvents()
        }
    }

    @Test
    fun `route without focus coordinates never sends FocusCamera`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test { expectNoEvents() }
    }

    // --- crash containment (MVI doc §1 point 2) ---

    @Test
    fun `a failing route flow lowers isLoading and sends ShowError instead of crashing`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val repository = HistoryFakeTrackingRepository(throwOnDay = today)
        val vm = viewModel(trackingRepository = repository)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        vm.effects.test { assertTrue(awaitItem() is HistoryEffect.ShowError) }
    }

    // --- US-33 (phase-09): the real "▶ Mô phỏng lộ trình" button ---

    @Test
    fun `StartSimulation sets isSimulating immediately, clears it once the route finishes`() = runTest(dispatcher) {
        val trackingRepository = HistoryFakeTrackingRepository()
        val vm = viewModel(trackingRepository = trackingRepository, memberRepository = selfLocatedMemberRepository())
        advanceUntilIdle()
        assertFalse(vm.state.value.isSimulating)

        vm.onIntent(HistoryIntent.StartSimulation)
        assertTrue(vm.state.value.isSimulating) // true the instant onIntent returns — before any suspend point runs

        advanceUntilIdle()
        assertFalse(vm.state.value.isSimulating)
        assertEquals(1, trackingRepository.runSimulationCallCount)
    }

    @Test
    fun `StartSimulation pressed twice while running only reaches the use case once, re-entry guard`() = runTest(dispatcher) {
        val trackingRepository = HistoryFakeTrackingRepository(simulationGate = CompletableDeferred())
        val vm = viewModel(trackingRepository = trackingRepository, memberRepository = selfLocatedMemberRepository())
        advanceUntilIdle()

        vm.onIntent(HistoryIntent.StartSimulation)
        vm.onIntent(HistoryIntent.StartSimulation)
        advanceUntilIdle()
        assertEquals(1, trackingRepository.runSimulationCallCount)
        assertTrue(vm.state.value.isSimulating)

        trackingRepository.simulationGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.isSimulating)
    }

    @Test
    fun `StartSimulation with nobody ever located fails cleanly and still clears isSimulating`() = runTest(dispatcher) {
        val trackingRepository = HistoryFakeTrackingRepository()
        val vm = viewModel(
            trackingRepository = trackingRepository,
            memberRepository = HistoryFakeMemberRepository(listOf(self), emptyMap()),
        )
        advanceUntilIdle()

        vm.onIntent(HistoryIntent.StartSimulation)
        advanceUntilIdle()

        assertFalse(vm.state.value.isSimulating)
        assertEquals(0, trackingRepository.runSimulationCallCount)
        vm.effects.test { assertTrue(awaitItem() is HistoryEffect.ShowError) }
    }

    @Test
    fun `StartSimulation crash-containment - an unexpected exception still clears isSimulating`() = runTest(dispatcher) {
        val trackingRepository = HistoryFakeTrackingRepository(simulationThrows = IllegalStateException("boom"))
        val vm = viewModel(trackingRepository = trackingRepository, memberRepository = selfLocatedMemberRepository())
        advanceUntilIdle()

        vm.onIntent(HistoryIntent.StartSimulation)
        advanceUntilIdle()

        assertFalse(vm.state.value.isSimulating)
        vm.effects.test { assertTrue(awaitItem() is HistoryEffect.ShowError) }
    }
}

private class HistoryFakeTrackingRepository(
    private val throwOnDay: LocalDate? = null,
    /** Pre-completed by default so `runSimulation` returns immediately — tests that need to
     * observe `isSimulating` mid-flight pass a fresh, still-pending [CompletableDeferred]. */
    val simulationGate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    private val simulationThrows: Throwable? = null,
) : TrackingRepository {
    private val flowsByDay = mutableMapOf<LocalDate, MutableStateFlow<List<TrackSession>>>()

    var runSimulationCallCount = 0
        private set

    fun emit(day: LocalDate, sessions: List<TrackSession>) {
        flowFor(day).value = sessions
    }

    private fun flowFor(day: LocalDate) = flowsByDay.getOrPut(day) { MutableStateFlow(emptyList()) }

    override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> =
        if (day == throwOnDay) flow { throw IllegalStateException("boom") } else flowFor(day).asStateFlow()

    override suspend fun record(point: LocationPoint) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
    override fun isTracking(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setTracking(enabled: Boolean) = Unit
    /** phase-01 (D4): Lịch sử đọc `observeRoute` (đã lọc), không đọc cổng hiển thị. */
    override fun observeLiveSelfLocation(): Flow<LocationPoint?> = MutableStateFlow(null)

    override suspend fun runSimulation(fixes: List<SimulatedFix>) {
        runSimulationCallCount++
        simulationGate.await()
        simulationThrows?.let { throw it }
    }
}

private class HistoryFakeZoneRepository : ZoneRepository {
    private val zones = mutableListOf<Zone>()
    override fun observeAll(): Flow<List<Zone>> = MutableStateFlow(zones.toList()).asStateFlow()
    override suspend fun save(zone: Zone): AppResult<Zone> {
        zones += zone
        return AppResult.Success(zone)
    }
    override suspend fun delete(zoneId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun count(): Int = zones.size
    override suspend fun exists(zoneId: String): Boolean = zones.any { it.id == zoneId }
}


private class HistoryFakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
