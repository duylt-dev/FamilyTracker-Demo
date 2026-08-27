package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * G4's cheapest safety net (phase-09 Implementation Step 2): if this file is green, a G4 failure
 * on-device can only be a permission or notification problem, not a routing/filtering one. Runs
 * the blueprint through the SAME two-stage pipeline `LocationPointProcessor` (`:data`) does —
 * [LocationFilter] THEN [ZoneEvaluator] — not [ZoneEvaluator] alone, per the Risk Assessment
 * table's explicit warning: `MIN_DISTANCE_M` can silently drop a simulated point before it ever
 * reaches the evaluator, and a test that only calls [ZoneEvaluator] would never catch that.
 */
class RouteBlueprintTest {

    private val base: Instant = Instant.parse("2026-08-21T08:00:00Z")

    private val zone = Zone(
        id = "zone-1",
        name = "Nha",
        latitude = 21.0,
        longitude = 105.8,
        radiusMeters = TrackingConstants.ZONE_RADIUS_DEFAULT_M.toFloat(),
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = base,
    )

    @Test
    fun `a blueprint from a device north of the zone produces exactly one ENTER then one EXIT`() {
        // ~1.1km north of the zone — a plausible "device somewhere nearby" starting point.
        val events = runThroughFilterAndEvaluator(
            RouteBlueprint.build(currentLat = zone.latitude + 0.01, currentLng = zone.longitude, zone = zone),
        )

        assertEquals(listOf(ZoneEventType.ENTER, ZoneEventType.EXIT), events)
    }

    @Test
    fun `a blueprint from a device east of the zone also produces exactly one ENTER then one EXIT`() {
        val events = runThroughFilterAndEvaluator(
            RouteBlueprint.build(currentLat = zone.latitude, currentLng = zone.longitude + 0.01, zone = zone),
        )

        assertEquals(listOf(ZoneEventType.ENTER, ZoneEventType.EXIT), events)
    }

    @Test
    fun `a device already AT the zone center still fires ENTER then EXIT, north fallback direction`() {
        val events = runThroughFilterAndEvaluator(
            RouteBlueprint.build(currentLat = zone.latitude, currentLng = zone.longitude, zone = zone),
        )

        assertEquals(listOf(ZoneEventType.ENTER, ZoneEventType.EXIT), events)
    }

    @Test
    fun `every consecutive fix clears MIN_DISTANCE_M at the demo default radius, none dropped`() {
        val fixes = RouteBlueprint.build(currentLat = zone.latitude + 0.01, currentLng = zone.longitude, zone = zone)

        var lastKept: LocationPoint? = null
        var acceptedCount = 0
        fixes.forEach { fix ->
            val point = fix.toLocationPoint()
            if (LocationFilter.accept(point, lastKept) == FilterResult.Accept) {
                acceptedCount++
                lastKept = point
            }
        }

        assertEquals(fixes.size, acceptedCount)
    }

    @Test
    fun `points are ordered by offsetMs and span exactly totalMillis`() {
        val fixes = RouteBlueprint.build(currentLat = zone.latitude + 0.01, currentLng = zone.longitude, zone = zone)

        assertEquals(RouteBlueprint.DEFAULT_POINT_COUNT, fixes.size)
        assertEquals(0L, fixes.first().offsetMs)
        assertEquals(RouteBlueprint.DEFAULT_TOTAL_MILLIS, fixes.last().offsetMs)
        assertTrue(fixes.zipWithNext().all { (a, b) -> b.offsetMs > a.offsetMs })
    }

    /**
     * **Known limitation, documented not fixed (see [RouteBlueprint] KDoc).** A zone anywhere
     * near [TrackingConstants.ZONE_RADIUS_MAX_M] (2000m) pushes the implied speed between fixes
     * (fixed `pointCount`/`totalMillis`, ~30s total) past [TrackingConstants.MAX_SPEED_KMH] (200),
     * so [LocationFilter] legitimately rejects some fixes as `SPEED`. This test PINS that behavior
     * down instead of silently assuming it away — it exists so a future change to
     * `RouteBlueprint`'s timing that accidentally "fixes" this without updating the KDoc gets
     * caught, and so the limitation stays visible. Demo zones default to 150m
     * ([TrackingConstants.ZONE_RADIUS_DEFAULT_M]), far under the ~683m breakeven, so this does not
     * affect the shipped demo.
     */
    @Test
    fun `known limitation - a near-MAX_M radius zone gets some fixes rejected as SPEED, not a routing bug`() {
        val hugeZone = zone.copy(radiusMeters = TrackingConstants.ZONE_RADIUS_MAX_M.toFloat())
        val fixes = RouteBlueprint.build(currentLat = hugeZone.latitude + 0.05, currentLng = hugeZone.longitude, zone = hugeZone)

        var lastKept: LocationPoint? = null
        var speedRejections = 0
        fixes.forEach { fix ->
            val point = fix.toLocationPoint()
            when (val result = LocationFilter.accept(point, lastKept)) {
                FilterResult.Accept -> lastKept = point
                is FilterResult.Reject -> if (result.reason == DropReason.SPEED) speedRejections++
            }
        }

        assertTrue("expected at least one SPEED rejection at MAX_M radius, math changed?", speedRejections > 0)
    }

    private fun runThroughFilterAndEvaluator(fixes: List<SimulatedFix>): List<ZoneEventType> {
        var lastKept: LocationPoint? = null
        var inside = emptySet<String>()
        val events = mutableListOf<ZoneEventType>()
        fixes.forEach { fix ->
            val point = fix.toLocationPoint()
            if (LocationFilter.accept(point, lastKept) == FilterResult.Accept) {
                lastKept = point
                val evaluation = ZoneEvaluator.evaluate(point, listOf(zone), inside)
                inside = evaluation.insideAfter
                events += evaluation.events.map { it.type }
            }
        }
        return events
    }

    private fun SimulatedFix.toLocationPoint() = LocationPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 8f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = base.plusMillis(offsetMs),
    )
}
