package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

private const val METERS_PER_DEGREE_LAT = 111_320.0

/** Phase-02 Implementation Step 3. */
class SyntheticPathTest {

    private val from = GeoPoint(10.0, 106.0)
    private val to = GeoPoint(10.0, oneKilometreEastOfFrom())

    @Test
    fun `same seed always produces the same curve`() {
        val a = SyntheticPath.between(from, to, seed = 7)
        val b = SyntheticPath.between(from, to, seed = 7)
        assertEquals(a, b)
    }

    @Test
    fun `different seeds produce different curves`() {
        val a = SyntheticPath.between(from, to, seed = 7)
        val b = SyntheticPath.between(from, to, seed = 8)
        assertTrue(a != b)
    }

    @Test
    fun `the path starts at 'from' and ends at 'to'`() {
        val points = SyntheticPath.between(from, to, seed = 1)
        assertEquals(from.latitude, points.first().latitude, 1e-9)
        assertEquals(from.longitude, points.first().longitude, 1e-9)
        assertEquals(to.latitude, points.last().latitude, 1e-9)
        assertEquals(to.longitude, points.last().longitude, 1e-9)
    }

    @Test
    fun `max deviation from the straight line is within 5 to 15 percent of the distance, capped at 120m`() {
        val distance = GeoDistance.haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude)
        val points = SyntheticPath.between(from, to, seed = 3)

        val maxDeviation = points.maxOf { perpendicularDistanceMeters(it) }

        val lowerBound = 0.05 * distance
        val upperBound = minOf(0.15 * distance, 120.0)
        assertTrue("$maxDeviation phải >= $lowerBound", maxDeviation >= lowerBound - 1e-6)
        assertTrue("$maxDeviation phải <= $upperBound", maxDeviation <= upperBound + 1e-6)
    }

    @Test
    fun `consecutive vertices are never farther apart than STEP_METERS`() {
        val points = SyntheticPath.between(from, to, seed = 5)
        points.zipWithNext().forEach { (a, b) ->
            val gap = GeoDistance.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            assertTrue("khoảng cách $gap giữa hai đỉnh vượt STEP_METERS", gap <= MemberRoamer.STEP_METERS)
        }
    }

    @Test
    fun `coincident endpoints do not throw and return the two identical points`() {
        val points = SyntheticPath.between(from, from, seed = 1)
        assertEquals(listOf(from, from), points)
    }

    /** Kinh độ cách [from] đúng 1km về phía ĐÔNG — quy đổi qua mét/độ kinh tại vĩ độ của `from`. */
    private fun oneKilometreEastOfFrom(): Double {
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians(from.latitude))
        return from.longitude + 1_000.0 / metersPerDegreeLng
    }

    /** Khoảng cách vuông góc (mét, xấp xỉ phẳng) từ [point] tới đường thẳng vô hạn qua `from`/`to`
     * — cùng kỹ thuật với `PolylineFollowerTest.distanceToSegment`, nhưng KHÔNG kẹp vào đoạn vì mọi
     * điểm test đều nằm trong khoảng tham số `[0,1]`. */
    private fun perpendicularDistanceMeters(point: GeoPoint): Double {
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians(from.latitude))
        val ax = from.longitude * metersPerDegreeLng
        val ay = from.latitude * METERS_PER_DEGREE_LAT
        val bx = to.longitude * metersPerDegreeLng
        val by = to.latitude * METERS_PER_DEGREE_LAT
        val px = point.longitude * metersPerDegreeLng
        val py = point.latitude * METERS_PER_DEGREE_LAT

        val dx = bx - ax
        val dy = by - ay
        val lineLength = sqrt(dx * dx + dy * dy)
        if (lineLength <= 0.0) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        // |cross product| / |direction| = khoảng cách vuông góc.
        return abs((px - ax) * dy - (py - ay) * dx) / lineLength
    }
}
