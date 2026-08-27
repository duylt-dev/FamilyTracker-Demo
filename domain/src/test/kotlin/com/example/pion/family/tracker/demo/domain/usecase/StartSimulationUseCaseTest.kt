package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** US-33 — the orchestration around [StartSimulationUseCase], NOT the route geometry itself
 * ([com.example.pion.family.tracker.demo.domain.tracking.RouteBlueprintTest] owns that). */
class StartSimulationUseCaseTest {

    private val self = Member(id = "m-self", name = "Toi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
    private val other = Member(id = "m-other", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
    private val selfPoint = pointAt(21.0, 105.8)

    @Test
    fun `an existing zone is used as-is, no sample zone created`() = runTest {
        val zone = zoneOf("z-1", lat = 21.001, lng = 105.801)
        val zoneRepository = SimulationFakeZoneRepository(listOf(zone))
        val trackingRepository = SimulationFakeTrackingRepository()
        val useCase = useCase(zoneRepository = zoneRepository, memberRepository = memberRepository(self to selfPoint), trackingRepository = trackingRepository)

        val result = useCase()

        assertTrue(result is AppResult.Success)
        assertEquals(0, zoneRepository.saveCallCount)
        assertEquals(1, trackingRepository.runSimulationCallCount)
        assertTrue(trackingRepository.lastFixes!!.isNotEmpty())
    }

    @Test
    fun `no zones yet creates a sample zone around the current position first, US-33`() = runTest {
        val zoneRepository = SimulationFakeZoneRepository(emptyList())
        val useCase = useCase(zoneRepository = zoneRepository, memberRepository = memberRepository(self to selfPoint))

        val result = useCase()

        assertTrue(result is AppResult.Success)
        assertEquals(1, zoneRepository.saveCallCount)
        val created = zoneRepository.lastSaved!!
        assertEquals(selfPoint.latitude, created.latitude, 0.0)
        assertEquals(selfPoint.longitude, created.longitude, 0.0)
        assertEquals(TrackingConstants.ZONE_RADIUS_DEFAULT_M.toFloat(), created.radiusMeters, 0.0f)
    }

    @Test
    fun `the nearest zone is chosen when several exist`() = runTest {
        val near = zoneOf("z-near", lat = 21.001, lng = 105.8)
        val far = zoneOf("z-far", lat = 25.0, lng = 105.8)
        val trackingRepository = SimulationFakeTrackingRepository()
        val useCase = useCase(
            zoneRepository = SimulationFakeZoneRepository(listOf(far, near)),
            memberRepository = memberRepository(self to selfPoint),
            trackingRepository = trackingRepository,
        )

        useCase()

        // Blueprint's fixes hug `near`'s center (21.001, 105.8), not `far`'s (25.0, 105.8).
        val midFix = trackingRepository.lastFixes!![trackingRepository.lastFixes!!.size / 2]
        assertTrue(kotlin.math.abs(midFix.latitude - near.latitude) < 0.01)
    }

    @Test
    fun `self with no recorded position falls back to another member's position, never fails`() = runTest {
        val zoneRepository = SimulationFakeZoneRepository(emptyList())
        val useCase = useCase(zoneRepository = zoneRepository, memberRepository = memberRepository(other to selfPoint))

        val result = useCase()

        assertTrue(result is AppResult.Success)
        assertEquals(1, zoneRepository.saveCallCount)
    }

    @Test
    fun `nobody has ever been located fails with a clear validation error, never simulates`() = runTest {
        val trackingRepository = SimulationFakeTrackingRepository()
        val useCase = useCase(
            zoneRepository = SimulationFakeZoneRepository(emptyList()),
            memberRepository = SimulationFakeMemberRepository(listOf(self), emptyMap()),
            trackingRepository = trackingRepository,
        )

        val result = useCase()

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
        assertEquals(0, trackingRepository.runSimulationCallCount)
    }

    private fun useCase(
        zoneRepository: ZoneRepository,
        memberRepository: MemberRepository,
        trackingRepository: TrackingRepository = SimulationFakeTrackingRepository(),
    ) = StartSimulationUseCase(
        zoneRepository = zoneRepository,
        saveZoneUseCase = SaveZoneUseCase(zoneRepository),
        observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
        trackingRepository = trackingRepository,
    )

    private fun memberRepository(vararg located: Pair<Member, LocationPoint>) =
        SimulationFakeMemberRepository(located.map { it.first }, located.associate { it.first.id to it.second })

    private fun pointAt(lat: Double, lng: Double) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-21T08:00:00Z"),
    )

    private fun zoneOf(id: String, lat: Double, lng: Double) = Zone(
        id = id,
        name = "Zone $id",
        latitude = lat,
        longitude = lng,
        radiusMeters = 150f,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-21T00:00:00Z"),
    )
}

/** Fake per LLM.md §11 — no mocking library. Tracks the last saved zone (not just a call count)
 * so tests can assert WHERE the sample zone was centered. */
private class SimulationFakeZoneRepository(initial: List<Zone>) : ZoneRepository {
    private val zones = initial.toMutableList()
    var saveCallCount = 0
        private set
    var lastSaved: Zone? = null
        private set

    override fun observeAll(): Flow<List<Zone>> = MutableStateFlow(zones.toList()).asStateFlow()

    override suspend fun save(zone: Zone): AppResult<Zone> {
        saveCallCount++
        lastSaved = zone
        zones += zone
        return AppResult.Success(zone)
    }

    override suspend fun delete(zoneId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun count(): Int = zones.size
    override suspend fun exists(zoneId: String): Boolean = zones.any { it.id == zoneId }
}


private class SimulationFakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}

private class SimulationFakeTrackingRepository : TrackingRepository {
    var runSimulationCallCount = 0
        private set
    var lastFixes: List<SimulatedFix>? = null
        private set

    override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> =
        MutableStateFlow<List<TrackSession>>(emptyList()).asStateFlow()

    override suspend fun record(point: LocationPoint) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
    override fun isTracking(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setTracking(enabled: Boolean) = Unit
    /** phase-01 (D4): mô phỏng không đi qua cổng hiển thị self. */
    override fun observeLiveSelfLocation(): Flow<LocationPoint?> = MutableStateFlow(null)
    override suspend fun runSimulation(fixes: List<SimulatedFix>) {
        runSimulationCallCount++
        lastFixes = fixes
    }
}
