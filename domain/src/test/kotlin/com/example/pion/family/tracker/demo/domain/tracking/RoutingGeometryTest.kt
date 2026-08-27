package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `RoutingGeometry` is `internal`; test source set of a Kotlin JVM module "associate"s with
 * `main` by default so it is visible here, same as `GeoDistanceTest` (LLM.md §8.2, §11).
 *
 * The segment `HANOI_A`-`HANOI_B` is built from a real decoded Valhalla point
 * (`21.028833, 105.854165` — first point of `data/src/test/resources/valhalla-route-hanoi.json`'s
 * shape, already pinned by `PolylineDecoderTest`) extended 222.39m due north, so every case below
 * measures against a real Hà Nội location, not an arbitrary pair of numbers.
 *
 * Expected values for the "perpendicular" and "real coordinate" cases were computed independently
 * in Python via the spherical cross-track-distance formula (bearing + Haversine, not this file's
 * equirectangular projection) — an independent method, same physical quantity — and agree with
 * this object's output to within centimetres at this scale, matching the documented < 0.1% error
 * bound (routing plan phase-04 Key Insight #2).
 */
class RoutingGeometryTest {

    private val hanoiA = GeoPoint(21.028833, 105.854165)
    private val hanoiB = GeoPoint(21.030833, 105.854165) // hanoiA + 0.002 deg lat, ~222.39m north

    @Test
    fun `point exactly on the route is approximately 0m`() {
        // Linear interpolation at 40% along the segment — lands on the projected line by
        // construction, not by chance.
        val onRoute = GeoPoint(
            latitude = hanoiA.latitude + 0.4 * (hanoiB.latitude - hanoiA.latitude),
            longitude = hanoiA.longitude,
        )

        val distance = RoutingGeometry.distanceToSegmentMeters(onRoute, hanoiA, hanoiB)

        assertEquals(0.0, distance, 0.01)
    }

    @Test
    fun `point perpendicular to the middle of a segment`() {
        val midpoint = GeoPoint(
            latitude = (hanoiA.latitude + hanoiB.latitude) / 2,
            longitude = hanoiA.longitude,
        )
        // Offset east by 0.0003 deg longitude at this latitude — independently verified cross-track
        // distance is 31.136636 m.
        val perpendicular = GeoPoint(midpoint.latitude, midpoint.longitude + 0.0003)

        val distance = RoutingGeometry.distanceToSegmentMeters(perpendicular, hanoiA, hanoiB)

        assertEquals(31.136636, distance, 0.01)
    }

    @Test
    fun `point beyond a segment's start clamps to the endpoint, not the infinite line`() {
        // East of hanoiA by 0.001 deg longitude (~104m) — the opposite direction from hanoiB
        // (which is due north), so the closest point on the segment is hanoiA itself, not a
        // projection onto the line extended backward past it.
        val beyondStart = GeoPoint(hanoiA.latitude, hanoiA.longitude + 0.001)
        val distanceToStartPointOnly = GeoDistance.haversineMeters(
            beyondStart.latitude,
            beyondStart.longitude,
            hanoiA.latitude,
            hanoiA.longitude,
        )

        val distance = RoutingGeometry.distanceToSegmentMeters(beyondStart, hanoiA, hanoiB)

        assertEquals(distanceToStartPointOnly, distance, 0.01)
    }

    @Test
    fun `empty polyline returns MAX_VALUE instead of throwing`() {
        val distance = RoutingGeometry.distanceToPolylineMeters(hanoiA, emptyList())

        assertEquals(Double.MAX_VALUE, distance, 0.0)
    }

    @Test
    fun `single-point polyline falls back to point-to-point distance`() {
        val queryPoint = GeoPoint(hanoiA.latitude + 0.001, hanoiA.longitude)
        val expected = GeoDistance.haversineMeters(
            queryPoint.latitude,
            queryPoint.longitude,
            hanoiA.latitude,
            hanoiA.longitude,
        )

        val distance = RoutingGeometry.distanceToPolylineMeters(queryPoint, listOf(hanoiA))

        assertEquals(expected, distance, 0.001)
    }

    @Test
    fun `real Hanoi coordinate case checked against an independently computed distance`() {
        // Same perpendicular case as above, restated to make explicit this is the "real
        // coordinate, hand-computed" acceptance case from phase-04's Implementation Steps §4.
        val midpoint = GeoPoint(
            latitude = (hanoiA.latitude + hanoiB.latitude) / 2,
            longitude = hanoiA.longitude,
        )
        val point = GeoPoint(midpoint.latitude, midpoint.longitude + 0.0003)

        val distance = RoutingGeometry.distanceToPolylineMeters(point, listOf(hanoiA, hanoiB))

        // Independently computed (Python, spherical cross-track-distance formula): 31.13663632 m.
        assertEquals(31.13663632, distance, 0.01)
    }
}
