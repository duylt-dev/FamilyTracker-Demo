package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Hysteresis + trạng thái đi vào/ra zone — G2, phase-03 bảng test biên, LLM.md §8.2. Zone đặt ở
 * toạ độ tròn số (21.0, 105.8) — Security Considerations, không dùng toạ độ thật.
 */
class ZoneEvaluatorTest {

    private val zone = Zone(
        id = "zone-1",
        name = "Nha",
        latitude = 21.0,
        longitude = 105.8,
        radiusMeters = 100f,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    // Hai test dưới đây dựng điểm qua toạ độ (round-trip Haversine của pointDueNorth), nên
    // distanceMeters đo lại KHÔNG bao giờ đúng bằng radius tuyệt đối (~1e-10m sai số làm tròn) —
    // chúng khoá hành vi ở gần mép trong giới hạn độ chính xác toạ độ, KHÔNG khoá được biên
    // `<` vs `<=` thật (xem `entersAt exactly at the radius is NOT an entry, boundary is exclusive`
    // bên dưới, test đó mới nạp thẳng Double và phân biệt được tất định).
    @Test
    fun `standing at radius within coordinate round-trip precision produces no event, from outside`() {
        val point = pointDueNorth(zone, distanceMeters = zone.radiusMeters.toDouble())

        val result = ZoneEvaluator.evaluate(point, listOf(zone), previouslyInside = emptySet())

        assertTrue(result.events.isEmpty())
        assertEquals(emptySet<String>(), result.insideAfter)
    }

    @Test
    fun `standing at radius within coordinate round-trip precision produces no event, from inside`() {
        val point = pointDueNorth(zone, distanceMeters = zone.radiusMeters.toDouble())

        val result = ZoneEvaluator.evaluate(point, listOf(zone), previouslyInside = setOf(zone.id))

        assertTrue(result.events.isEmpty())
        assertEquals(setOf(zone.id), result.insideAfter)
    }

    @Test
    fun `entersAt exactly at the radius is NOT an entry, boundary is exclusive`() {
        assertEquals(false, ZoneEvaluator.entersAt(distanceMeters = 100.0, radiusMeters = 100.0))
        assertEquals(true, ZoneEvaluator.entersAt(distanceMeters = 99.999, radiusMeters = 100.0))
    }

    @Test
    fun `exitsAt exactly at radius plus buffer is NOT an exit, boundary is exclusive`() {
        val boundary = 100.0 + TrackingConstants.ZONE_EXIT_BUFFER_M
        assertEquals(false, ZoneEvaluator.exitsAt(distanceMeters = boundary, radiusMeters = 100.0))
        assertEquals(true, ZoneEvaluator.exitsAt(distanceMeters = boundary + 0.001, radiusMeters = 100.0))
    }

    @Test
    fun `standing at the edge oscillating within radius plus-minus 5m for 30 points fires exactly one ENTER and zero EXIT`() {
        var inside = emptySet<String>()
        var enterCount = 0
        var exitCount = 0

        repeat(30) { i ->
            // Dao động 95..105m quanh radius=100m — không bao giờ vượt quá radius+30 (hysteresis).
            val offset = if (i % 2 == 0) -5.0 else 5.0
            val point = pointDueNorth(zone, distanceMeters = zone.radiusMeters + offset)
            val result = ZoneEvaluator.evaluate(point, listOf(zone), inside)
            enterCount += result.events.count { it.type == ZoneEventType.ENTER }
            exitCount += result.events.count { it.type == ZoneEventType.EXIT }
            inside = result.insideAfter
        }

        assertEquals(1, enterCount)
        assertEquals(0, exitCount)
        assertEquals(setOf(zone.id), inside)
    }

    @Test
    fun `entering then genuinely leaving at radius plus 40m fires one ENTER and one EXIT`() {
        val enterPoint = pointDueNorth(zone, distanceMeters = zone.radiusMeters - 50.0)
        val afterEnter = ZoneEvaluator.evaluate(enterPoint, listOf(zone), previouslyInside = emptySet())

        val exitPoint = pointDueNorth(zone, distanceMeters = zone.radiusMeters + 40.0)
        val afterExit = ZoneEvaluator.evaluate(exitPoint, listOf(zone), previouslyInside = afterEnter.insideAfter)

        assertEquals(listOf(ZoneEventType.ENTER), afterEnter.events.map { it.type })
        assertEquals(setOf(zone.id), afterEnter.insideAfter)
        assertEquals(listOf(ZoneEventType.EXIT), afterExit.events.map { it.type })
        assertTrue(afterExit.insideAfter.isEmpty())
    }

    @Test
    fun `notifyOnEnter false still records the ENTER event but marks shouldNotify false, PRD 3-2`() {
        val silentZone = zone.copy(notifyOnEnter = false, notifyOnExit = false)
        val point = pointDueNorth(silentZone, distanceMeters = silentZone.radiusMeters - 10.0)

        val result = ZoneEvaluator.evaluate(point, listOf(silentZone), previouslyInside = emptySet())

        assertEquals(1, result.events.size)
        val event = result.events.single()
        assertEquals(ZoneEventType.ENTER, event.type)
        assertEquals(false, event.shouldNotify)
        // Sự kiện vẫn được ghi (không bị bỏ qua) dù không bắn thông báo — timeline vẫn có dòng này.
        assertEquals(setOf(silentZone.id), result.insideAfter)
    }

    @Test
    fun `two zones are evaluated independently in the same tick`() {
        val nearZone = zone.copy(id = "zone-near", radiusMeters = 100f)
        val farZone = zone.copy(id = "zone-far", latitude = 22.0, radiusMeters = 100f)
        val point = pointDueNorth(nearZone, distanceMeters = 10.0) // inside near, still far from farZone

        val result = ZoneEvaluator.evaluate(point, listOf(nearZone, farZone), previouslyInside = emptySet())

        assertEquals(listOf("zone-near"), result.events.map { it.zoneId })
        assertEquals(setOf("zone-near"), result.insideAfter)
    }

    /** Dịch [zone]'s center [distanceMeters] về phía Bắc bằng công thức Haversine rút gọn (dLon=0) — khoảng cách chính xác tuyệt đối, khớp [GeoDistance]'s Earth radius. */
    private fun pointDueNorth(zone: Zone, distanceMeters: Double, at: Instant = Instant.parse("2026-08-21T08:00:00Z")): LocationPoint {
        val deltaLatDeg = Math.toDegrees(distanceMeters / EARTH_RADIUS_M)
        return LocationPoint(
            latitude = zone.latitude + deltaLatDeg,
            longitude = zone.longitude,
            accuracyMeters = 5f,
            speedMps = 0f,
            bearingDegrees = 0f,
            recordedAt = at,
        )
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_008.8
    }
}
