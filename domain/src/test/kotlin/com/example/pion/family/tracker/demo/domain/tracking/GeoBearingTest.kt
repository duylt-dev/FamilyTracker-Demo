package com.example.pion.family.tracker.demo.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase-02 Implementation Step 1. `GeoBearing` là `internal` — cùng cơ chế truy cập test source set như [GeoDistanceTest]. */
class GeoBearingTest {

    @Test
    fun `due north is 0 degrees`() {
        val bearing = GeoBearing.initialBearing(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0, bearing, 0.01)
    }

    @Test
    fun `due east is 90 degrees`() {
        val bearing = GeoBearing.initialBearing(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0, bearing, 0.01)
    }

    @Test
    fun `due south is 180 degrees`() {
        val bearing = GeoBearing.initialBearing(1.0, 0.0, 0.0, 0.0)
        assertEquals(180.0, bearing, 0.01)
    }

    @Test
    fun `due west is 270 degrees`() {
        val bearing = GeoBearing.initialBearing(0.0, 1.0, 0.0, 0.0)
        assertEquals(270.0, bearing, 0.01)
    }

    @Test
    fun `shortestDelta crossing 0 wraps forward`() {
        assertEquals(20.0, GeoBearing.shortestDelta(350.0, 10.0), 1e-9)
    }

    @Test
    fun `shortestDelta crossing 0 the other way wraps backward`() {
        assertEquals(-20.0, GeoBearing.shortestDelta(10.0, 350.0), 1e-9)
    }

    @Test
    fun `shortestDelta at exactly 180 degrees picks the positive direction, always`() {
        // Khoá chiều cố định — xem KDoc GeoBearing.shortestDelta. Không có cách nào "đúng hơn" để
        // chọn hướng ở đúng góc đối đỉnh; điều quan trọng là nó KHÔNG đổi giữa hai lần gọi.
        assertEquals(180.0, GeoBearing.shortestDelta(0.0, 180.0), 1e-9)
        assertEquals(180.0, GeoBearing.shortestDelta(90.0, 270.0), 1e-9)
    }

    @Test
    fun `shortestDelta of a point to itself is zero`() {
        assertEquals(0.0, GeoBearing.shortestDelta(123.0, 123.0), 1e-9)
    }
}
