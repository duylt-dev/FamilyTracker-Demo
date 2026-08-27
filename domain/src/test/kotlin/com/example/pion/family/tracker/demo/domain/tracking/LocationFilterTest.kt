package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Ba luật lọc nhiễu GPS — phase-03 Implementation Step 3, bảng test biên, LLM.md §8.3. */
class LocationFilterTest {

    private val base = Instant.parse("2026-08-21T08:00:00Z")
    private val origin = point(21.0, 105.8, accuracyMeters = 5f, at = base)

    @Test
    fun `first point with no lastKept is accepted regardless of distance or speed`() {
        val result = LocationFilter.accept(origin, lastKept = null)
        assertEquals(FilterResult.Accept, result)
    }

    @Test
    fun `accuracy worse than MAX_ACCURACY_M is rejected, indoor GPS case`() {
        val indoor = point(21.0, 105.8, accuracyMeters = 200f, at = base)
        val result = LocationFilter.accept(indoor, lastKept = null)
        assertEquals(FilterResult.Reject(DropReason.ACCURACY), result)
    }

    @Test
    fun `accuracy exactly at MAX_ACCURACY_M is still accepted, boundary is exclusive`() {
        val atThreshold = point(21.0, 105.8, accuracyMeters = TrackingConstants.MAX_ACCURACY_M.toFloat(), at = base)
        val result = LocationFilter.accept(atThreshold, lastKept = null)
        assertEquals(FilterResult.Accept, result)
    }

    @Test
    fun `60 identical points while standing still keep only the first, reject the other 59 as DISTANCE`() {
        var lastKept: LocationPoint? = null
        var keptCount = 0
        var rejectedAsDistance = 0
        repeat(60) { i ->
            val candidate = point(21.0, 105.8, accuracyMeters = 5f, at = base.plusSeconds(i.toLong() * 10))
            when (val result = LocationFilter.accept(candidate, lastKept)) {
                FilterResult.Accept -> {
                    keptCount++
                    lastKept = candidate
                }
                is FilterResult.Reject -> if (result.reason == DropReason.DISTANCE) rejectedAsDistance++
            }
        }
        assertEquals(1, keptCount)
        assertEquals(59, rejectedAsDistance)
    }

    @Test
    fun `distance rule compares against the last KEPT point, not the last seen point`() {
        // Each step moves ~9m north — below MIN_DISTANCE_M (10m) versus the immediately preceding
        // point, but a slow walker should still accumulate distance versus the last KEPT point.
        // This is the "bẫy" from phase-03 Key Insight #3: 5 steps of 9m = 45m versus lastKept,
        // which clears MIN_DISTANCE_M even though no single step would on its own.
        var lastKept: LocationPoint? = origin
        var keptCount = 0
        var current = origin
        repeat(5) { i ->
            current = pointDueNorth(current, distanceMeters = 9.0, at = base.plusSeconds((i + 1) * 10L))
            val result = LocationFilter.accept(current, lastKept)
            if (result == FilterResult.Accept) {
                keptCount++
                lastKept = current
            }
        }
        // Cumulative offsets from origin are 9/18/27/36/45m; comparing against lastKept (updated
        // only on Accept) crosses MIN_DISTANCE_M (10m) at the 2nd and 4th step -> exactly 2 kept.
        assertEquals(2, keptCount)
    }

    @Test
    fun `GPS jump of 5km within 1 second is rejected as SPEED`() {
        val jumped = pointDueNorth(origin, distanceMeters = 5_000.0, at = base.plusSeconds(1))
        val result = LocationFilter.accept(jumped, lastKept = origin)
        assertEquals(FilterResult.Reject(DropReason.SPEED), result)
    }

    @Test
    fun `walking at 5kmh with a 10s cadence keeps points, about 14m per tick`() {
        // 5 km/h = ~1.389 m/s -> ~13.9m every 10s. Must clear MIN_DISTANCE_M (10m) and stay
        // far under MAX_SPEED_KMH (200) -> every tick should be Accept.
        var lastKept = origin
        var keptCount = 0
        var current = origin
        repeat(10) { i ->
            current = pointDueNorth(current, distanceMeters = 13.9, at = base.plusSeconds((i + 1) * 10L))
            val result = LocationFilter.accept(current, lastKept)
            if (result == FilterResult.Accept) {
                keptCount++
                lastKept = current
            }
        }
        assertEquals(10, keptCount)
    }

    private fun point(lat: Double, lng: Double, accuracyMeters: Float, at: Instant) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = accuracyMeters,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = at,
    )

    /** Dịch một điểm [distanceMeters] về phía Bắc bằng công thức Haversine rút gọn (dLon=0) — khoảng cách chính xác tuyệt đối. */
    private fun pointDueNorth(from: LocationPoint, distanceMeters: Double, at: Instant): LocationPoint {
        val deltaLatDeg = Math.toDegrees(distanceMeters / EARTH_RADIUS_M)
        return from.copy(latitude = from.latitude + deltaLatDeg, recordedAt = at)
    }

    private companion object {
        // Phải khớp GeoDistance.EARTH_RADIUS_M để khoảng cách sinh ra chính xác tuyệt đối.
        const val EARTH_RADIUS_M = 6_371_008.8
    }
}
