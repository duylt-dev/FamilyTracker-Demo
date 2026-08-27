package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val METERS_PER_DEGREE_LAT = 111_320.0

/** Phase-02 Implementation Step 4. Khoá `decisions.md` §C4 hàng "Bám polyline thay vì đường thẳng". */
class RouteGeometryGuardTest {

    private val zone = Zone(
        id = "z-truong",
        name = "z-truong",
        latitude = 10.0,
        longitude = 106.0,
        radiusMeters = 150f,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-22T00:00:00Z"),
    )

    @Test
    fun `a straight line into the zone is usable for an ENTER_ZONE leg`() {
        val points = northOffsets(400.0, 300.0, 200.0, 100.0, 50.0) // ra ngoài dần vào bên trong

        assertTrue(RouteGeometryGuard.isUsable(points, zone, LegKind.ENTER_ZONE))
    }

    @Test
    fun `a route that hugs the boundary and crosses it 4 times is rejected`() {
        val points = northOffsets(300.0, 100.0, 300.0, 100.0, 300.0)

        assertFalse(RouteGeometryGuard.isUsable(points, zone, LegKind.ENTER_ZONE))
    }

    @Test
    fun `a route that never enters the zone is rejected for ENTER_ZONE`() {
        val points = northOffsets(300.0, 250.0, 200.0, 180.0) // luôn ngoài bán kính 150m

        assertFalse(RouteGeometryGuard.isUsable(points, zone, LegKind.ENTER_ZONE))
    }

    @Test
    fun `a route that leaves the radius but not the exit hysteresis buffer is rejected for LEAVE_ZONE`() {
        // Radius 150, buffer 30 -> ngưỡng exit 180. Điểm cuối 170 đã ra khỏi radius nhưng CHƯA qua buffer.
        val points = northOffsets(50.0, 90.0, 130.0, 170.0)

        assertFalse(RouteGeometryGuard.isUsable(points, zone, LegKind.LEAVE_ZONE))
    }

    @Test
    fun `a route that clears the exit buffer is usable for LEAVE_ZONE`() {
        val points = northOffsets(50.0, 90.0, 130.0, 190.0)

        assertTrue(RouteGeometryGuard.isUsable(points, zone, LegKind.LEAVE_ZONE))
    }

    @Test
    fun `WANDER legs are always usable, no zone semantics apply`() {
        val points = northOffsets(300.0, 100.0, 300.0, 100.0, 300.0)

        assertTrue(RouteGeometryGuard.isUsable(points, zone, LegKind.WANDER))
    }

    @Test
    fun `fewer than 2 points is never usable`() {
        assertFalse(RouteGeometryGuard.isUsable(listOf(GeoPoint(10.0, 106.0)), zone, LegKind.ENTER_ZONE))
    }

    @Test
    fun `an ENTER leg that crosses entry once but bounces at exit buffer is rejected (cross-case guard)`() {
        // Radius 150, buffer 30 -> exit boundary 180.
        // Path: 200(out) -> 160(in: 1st cross 150) -> 185(out 180: 1st cross 180) -> 160(in 180: 2nd cross) -> 90(in)
        // entryCrossings = 1 (crosses 150 once, clean), exitCrossings = 2 (bounces 180) -> rejected
        val points = northOffsets(200.0, 160.0, 185.0, 160.0, 90.0)

        assertFalse(
            "ENTER leg must reject if it bounces at exit buffer, even though entry is clean",
            RouteGeometryGuard.isUsable(points, zone, LegKind.ENTER_ZONE),
        )
    }

    /**
     * P0 (`reports/dev-phase-04-report.md` §0) — tái hiện đúng con số đo thật trên `emulator-5554`:
     * gốc tuyến cache của Lan cách vị trí hiện tại **876.09 m** (`isUsable` một mình không chặn
     * được ca này vì nó không biết `from`).
     */
    @Test
    fun `a cached route whose origin is 876m from the current position does not start near`() {
        val cachedOrigin = GeoPoint(10.76812, 106.70611)
        val currentFrom = GeoPoint(10.77449, 106.70139)

        assertFalse(RouteGeometryGuard.startsNear(listOf(cachedOrigin), currentFrom, toleranceMeters = MemberRoamer.STEP_METERS))
    }

    /** Cùng cặp toạ độ trên, sau cú nhảy — 14.24 m, trong ngưỡng một bước (20.75 m). */
    @Test
    fun `an origin 14m from the current position starts near, within one step`() {
        val cachedOrigin = GeoPoint(10.76812, 106.70611)
        val currentFrom = GeoPoint(10.76807, 106.70599)

        assertTrue(RouteGeometryGuard.startsNear(listOf(cachedOrigin), currentFrom, toleranceMeters = MemberRoamer.STEP_METERS))
    }

    @Test
    fun `an origin exactly at the tolerance boundary starts near`() {
        val from = GeoPoint(10.0, 106.0)
        val origin = GeoPoint(10.0 + 20.0 / METERS_PER_DEGREE_LAT, 106.0) // 20m < STEP_METERS (20.75m)

        assertTrue(RouteGeometryGuard.startsNear(listOf(origin), from, toleranceMeters = MemberRoamer.STEP_METERS))
    }

    @Test
    fun `an empty points list never starts near anything`() {
        assertFalse(RouteGeometryGuard.startsNear(emptyList(), GeoPoint(10.0, 106.0), toleranceMeters = 1_000.0))
    }

    /** Điểm cách tâm zone đúng [offsets] mét về phía bắc, cùng kinh độ với tâm — để khoảng cách tới
     * tâm đúng bằng giá trị mong muốn mà không cần lượng giác. */
    private fun northOffsets(vararg offsets: Double): List<GeoPoint> =
        offsets.map { GeoPoint(zone.latitude + it / METERS_PER_DEGREE_LAT, zone.longitude) }
}
