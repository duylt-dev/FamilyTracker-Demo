package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Point-to-segment / point-to-polyline distance via an **equirectangular projection**, not
 * Haversine — routing plan phase-04 Key Insight #2. Exact spherical point-to-segment distance is
 * a genuinely hard problem; at city scale (< 50km) this flat projection is under 0.1% error,
 * which is why point-to-point distance everywhere else in `:domain/tracking/` still goes through
 * [GeoDistance.haversineMeters] and only this file (segment geometry) uses the approximation.
 *
 * **Latitude limitation**: `cos(lat0)` distorts more the farther the reference latitude is from
 * the equator. This app operates in Vietnam (8–23°N), safely inside the range the 0.1% figure
 * above was measured at. Do not reuse this object for a deployment at high latitude without
 * re-checking that error bound.
 *
 * `internal` — routing plan phase-04 Architecture: only [RerouteEvaluator] (same module) needs it.
 */
internal object RoutingGeometry {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * Perpendicular distance from [point] to the segment [start]-[end], clamped to the segment —
     * never the infinite line through it. A point beyond either endpoint gets the distance to
     * that endpoint, not to a projection past it (`t` clamped to `[0, 1]`).
     */
    fun distanceToSegmentMeters(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        // Project both endpoints into a local metres-flat frame centred on `point` itself, so
        // `point` sits at the origin (0, 0) and only `start`/`end` need projecting.
        val (ax, ay) = project(point, start)
        val (bx, by) = project(point, end)

        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) {
            // Degenerate segment (start == end): only one point to measure against.
            return sqrt(ax * ax + ay * ay)
        }

        val t = (((-ax) * dx + (-ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val closestX = ax + t * dx
        val closestY = ay + t * dy
        return sqrt(closestX * closestX + closestY * closestY)
    }

    /**
     * Minimum distance from [point] to any segment of [polyline].
     *
     * Empty polyline returns [Double.MAX_VALUE] — `min()` on an empty list throws, and an empty
     * route is a real response shape from a provider, not a hypothetical. A single-point polyline
     * falls back to point-to-point [GeoDistance.haversineMeters] — there is no segment to project
     * onto. Both cases happen for real; neither may crash the caller.
     */
    fun distanceToPolylineMeters(point: GeoPoint, polyline: List<GeoPoint>): Double = when {
        polyline.isEmpty() -> Double.MAX_VALUE
        polyline.size == 1 -> GeoDistance.haversineMeters(
            point.latitude,
            point.longitude,
            polyline[0].latitude,
            polyline[0].longitude,
        )
        else -> polyline.zipWithNext { a, b -> distanceToSegmentMeters(point, a, b) }.min()
    }

    /** [other] projected into a local metres-flat (x, y) frame centred on [reference]. */
    private fun project(reference: GeoPoint, other: GeoPoint): Pair<Double, Double> {
        val lat0Rad = Math.toRadians(reference.latitude)
        val x = Math.toRadians(other.longitude - reference.longitude) * cos(lat0Rad) * EARTH_RADIUS_M
        val y = Math.toRadians(other.latitude - reference.latitude) * EARTH_RADIUS_M
        return x to y
    }
}
