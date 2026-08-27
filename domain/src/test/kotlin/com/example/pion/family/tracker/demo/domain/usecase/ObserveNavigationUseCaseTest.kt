package com.example.pion.family.tracker.demo.domain.usecase

import app.cash.turbine.test
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.repository.LocationSource
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val TARGET_MEMBER_ID = "m-target"

/**
 * `ObserveNavigationUseCase` is the only place that calls `RoutingProvider` — routing plan
 * phase-04 non-negotiable, verified here by counting `FakeRoutingProvider` calls rather than
 * trusting the source.
 *
 * Fixture distances (computed independently in Python, Haversine): S0–T0 ≈ 152m (> ARRIVAL_M, not
 * arrived), S0–S1 ≈ 5.6m (a small GPS update), T0–T1 ≈ 222m (> DESTINATION_MOVED_TOLERANCE_M).
 */
class ObserveNavigationUseCaseTest {

    private val s0 = locationOf(21.0285, 105.8542)
    private val s1 = locationOf(21.02855, 105.8542) // ~5.6m from s0 — a small GPS update
    private val t0 = locationOf(21.0295, 105.8552) // ~152m from s0
    private val t1 = locationOf(21.0315, 105.8552) // ~222m from t0

    private val fakeRoute = Directions(
        points = listOf(GeoPoint(s0.latitude, s0.longitude), GeoPoint(t0.latitude, t0.longitude)),
        distanceMeters = 500.0,
        durationSeconds = 120L,
        engineId = "FAKE",
        attribution = listOf("Fake"),
    )

    @Test
    fun `first call fires immediately`() = runTest {
        val locationSource = FakeLocationSource(s0)
        val memberRepository = FakeNavigationMemberRepository(mapOf(TARGET_MEMBER_ID to t0))
        val provider = FakeRoutingProvider(AppResult.Success(fakeRoute))
        val useCase = ObserveNavigationUseCase(locationSource, memberRepository, provider, nowMs = { 1_000_000L })

        useCase(TARGET_MEMBER_ID).test {
            val update = awaitItem()

            assertEquals(fakeRoute, update.directions)
            assertEquals(500.0, update.distanceMeters, 0.0)
            assertFalse(update.isDistanceEstimated)
            assertNull(update.lastError)
            assertEquals(1, provider.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no second call within the debounce window`() = runTest {
        val locationSource = FakeLocationSource(s0)
        val memberRepository = FakeNavigationMemberRepository(mapOf(TARGET_MEMBER_ID to t0))
        val provider = FakeRoutingProvider(AppResult.Success(fakeRoute)) // exactly one canned response
        var currentTime = 1_000_000L
        val useCase = ObserveNavigationUseCase(locationSource, memberRepository, provider, nowMs = { currentTime })

        useCase(TARGET_MEMBER_ID).test {
            val first = awaitItem()
            assertEquals(1, provider.callCount)
            assertEquals(fakeRoute, first.directions)

            currentTime = 1_005_000L // +5s — inside the 60s debounce window
            locationSource.emit(s1) // a small GPS update triggers a new combine emission

            val second = awaitItem()
            assertEquals(1, provider.callCount) // provider was NOT called again
            assertEquals(fakeRoute, second.directions) // route carried over unchanged
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a provider error leaves the previous route intact`() = runTest {
        val locationSource = FakeLocationSource(s0)
        val memberRepository = FakeNavigationMemberRepository(mapOf(TARGET_MEMBER_ID to t0))
        val provider = FakeRoutingProvider(
            AppResult.Success(fakeRoute),
            AppResult.Failure(AppError.Network("no connection")),
        )
        var currentTime = 1_000_000L
        val useCase = ObserveNavigationUseCase(locationSource, memberRepository, provider, nowMs = { currentTime })

        useCase(TARGET_MEMBER_ID).test {
            val first = awaitItem()
            assertEquals(1, provider.callCount)

            currentTime = 1_061_000L // past the 60s debounce window
            memberRepository.emitLocations(mapOf(TARGET_MEMBER_ID to t1)) // target moved > 200m away

            val second = awaitItem()
            assertEquals(2, provider.callCount)
            assertEquals(fakeRoute, second.directions) // old route still there, not nulled out
            assertNotNull(second.lastError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The degraded branch: no route was ever obtained, so `distanceMeters` falls back to
     * straight-line. This is the whole reason `isDistanceEstimated` exists — `GeoDistance` is
     * `internal` to `:domain`, so if this use case emitted no distance here, `:ui` would have no
     * legal way to compute one (phase-04 Implementation Step 6). 152.107m is the S0-T0 Haversine
     * distance computed independently, not read back out of `GeoDistance`.
     */
    @Test
    fun `no route at all still emits a straight-line distance, flagged as estimated`() = runTest {
        val locationSource = FakeLocationSource(s0)
        val memberRepository = FakeNavigationMemberRepository(mapOf(TARGET_MEMBER_ID to t0))
        val provider = FakeRoutingProvider(AppResult.Failure(AppError.Network("no connection")))
        val useCase = ObserveNavigationUseCase(locationSource, memberRepository, provider, nowMs = { 1_000_000L })

        useCase(TARGET_MEMBER_ID).test {
            val update = awaitItem()

            assertNull(update.directions)
            assertTrue(update.isDistanceEstimated)
            assertEquals(152.107, update.distanceMeters, 0.5)
            assertNotNull(update.lastError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The failure loop this app must never ship: a provider that keeps failing leaves `directions`
     * null, and if the "no route yet" branch ignored the debounce, every location sample (~2.5s)
     * would buy another provider call forever. `FakeRoutingProvider` throws when called more times
     * than responses were queued, so a second call here fails the test loudly rather than
     * silently returning a default.
     */
    @Test
    fun `a failed fetch does not re-call the provider on the next sample`() = runTest {
        val locationSource = FakeLocationSource(s0)
        val memberRepository = FakeNavigationMemberRepository(mapOf(TARGET_MEMBER_ID to t0))
        val provider = FakeRoutingProvider(AppResult.Failure(AppError.Network("no connection")))
        var currentTime = 1_000_000L
        val useCase = ObserveNavigationUseCase(locationSource, memberRepository, provider, nowMs = { currentTime })

        useCase(TARGET_MEMBER_ID).test {
            awaitItem()
            assertEquals(1, provider.callCount)

            currentTime = 1_002_500L // one location tick later — well inside the 60s debounce
            locationSource.emit(s1)

            val second = awaitItem()
            assertEquals(1, provider.callCount) // still one — the debounce held
            assertNull(second.directions)
            assertTrue(second.isDistanceEstimated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun locationOf(latitude: Double, longitude: Double) = LocationPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-24T08:00:00Z"),
    )
}

private class FakeLocationSource(initial: LocationPoint) : LocationSource {
    private val flow = MutableStateFlow(initial)
    fun emit(point: LocationPoint) {
        flow.value = point
    }
    override fun stream(): Flow<LocationPoint> = flow
}

private class FakeNavigationMemberRepository(initialLocations: Map<String, LocationPoint>) : MemberRepository {
    private val locationsFlow = MutableStateFlow(initialLocations)
    fun emitLocations(locations: Map<String, LocationPoint>) {
        locationsFlow.value = locations
    }
    override fun observeAll(): Flow<List<Member>> = MutableStateFlow(emptyList())
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}

/** Consumes canned [AppResult]s in order; throws if called more times than responses were given —
 * an unexpected extra call must fail loudly, not silently return a default. */
private class FakeRoutingProvider(vararg responses: AppResult<Directions>) : RoutingProvider {
    private val queue = ArrayDeque(responses.toList())
    var callCount = 0
        private set

    override suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions> {
        callCount++
        check(queue.isNotEmpty()) { "FakeRoutingProvider called more times than responses were queued" }
        return queue.removeFirst()
    }
}
