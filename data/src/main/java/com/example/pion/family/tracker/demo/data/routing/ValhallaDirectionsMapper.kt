package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.remote.dto.ValhallaDirectionsDto
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.tracking.PolylineDecoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * `ValhallaDirectionsDto` -> `Directions` — routing plan phase-03 Architecture. Pure: no HTTP, no
 * JSON string parsing (that stays the provider's job, via its injected `Json`) — the same split as
 * `GraphHopperDirectionsMapper` (phase-02), which is what lets [toDirections] be tested against a
 * DTO built from a plain string (`ValhallaDirectionsMapperTest`) without ever constructing a
 * provider.
 *
 * [origin] is the coordinate the caller actually requested (`from` in `RoutingProvider.directions`)
 * — never anything read out of the response — and exists ONLY to feed the precision-drift assert
 * below. [attribution] is built by `ValhallaRoutingProvider` from the host in
 * `RoutingConfig.valhallaBaseUrl` (phase-03 spec Implementation Step 4: "provider phải tự dựng
 * theo host") and passed in rather than guessed here, so this object stays testable with a plain
 * `List<String>` instead of needing a base-URL string of its own.
 */
object ValhallaDirectionsMapper {

    /** Valhalla encodes `legs[].shape` at precision 6 — Key Insight #1. Never 5, `PolyUtil.decode`'s
     * hardcoded assumption; that mismatch is exactly the failure this object exists to prevent. */
    private const val PRECISION = 6

    /**
     * Decoding at the wrong precision does not throw — it returns type-valid coordinates roughly
     * 10x off. A route whose first point lands more than 5km from the coordinate that was actually
     * requested is that failure's signature, so it must fail here, in the mapper, rather than
     * silently drawing a wrong polyline on the map (phase-03 spec, Implementation Step 4).
     */
    private const val ORIGIN_DRIFT_THRESHOLD_METERS = 5_000.0

    // Same IUGG mean Earth radius `:domain/tracking/GeoDistance.kt` uses. Duplicated on purpose,
    // not reused: `GeoDistance` is `internal` to `:domain` (LLM.md §8.2), and Kotlin `internal` is
    // enforced per Gradle module — `:data` genuinely cannot see it. The phase-03 acceptance rule
    // forbids touching `:domain` to widen that visibility for one assert, so this ~10-line formula
    // is copied here instead.
    private const val EARTH_RADIUS_M = 6_371_008.8

    private const val TAG = "FTD_EVENT"

    fun toDirections(
        dto: ValhallaDirectionsDto,
        origin: GeoPoint,
        attribution: List<String>,
    ): AppResult<Directions> {
        val trip = dto.trip
            ?: return AppResult.Failure(AppError.NotFound("Valhalla trả về không có trip — không có tuyến đường"))

        // `legs` is a list — `flatMap`, never `first()` (Architecture note). Two points is one leg
        // today; the day a waypoint is added, `first()` would silently draw only half the route.
        val points = trip.legs.flatMap { PolylineDecoder.decode(it.shape, PRECISION) }
        if (points.isEmpty()) {
            return AppResult.Failure(AppError.NotFound("Valhalla trả về legs rỗng — không có tuyến đường"))
        }

        val driftMeters = haversineMeters(origin, points.first())
        if (driftMeters > ORIGIN_DRIFT_THRESHOLD_METERS) {
            FtdLog.w(TAG, "routing_precision_drift engine=valhalla driftMeters=${driftMeters.roundToLong()}")
            return AppResult.Failure(
                AppError.Validation(
                    "Điểm đầu tuyến đường cách toạ độ yêu cầu ${driftMeters.roundToLong()}m — nghi ngờ decode sai precision",
                ),
            )
        }

        return AppResult.Success(
            Directions(
                points = points,
                // km -> m (Key Insight #2): `summary.length` is kilometres, unlike GraphHopper's
                // `distance`, which is already metres.
                distanceMeters = trip.summary.length * 1000,
                // Seconds with a fraction (`742.029`) rounded once, here — the mapper's one job.
                durationSeconds = trip.summary.time.roundToLong(),
                engineId = "valhalla",
                attribution = attribution,
            ),
        )
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val phi1 = Math.toRadians(a.latitude)
        val phi2 = Math.toRadians(b.latitude)
        val deltaPhi = Math.toRadians(b.latitude - a.latitude)
        val deltaLambda = Math.toRadians(b.longitude - a.longitude)

        val sinDeltaPhi = sin(deltaPhi / 2)
        val sinDeltaLambda = sin(deltaLambda / 2)
        val h = sinDeltaPhi * sinDeltaPhi + cos(phi1) * cos(phi2) * sinDeltaLambda * sinDeltaLambda
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return EARTH_RADIUS_M * c
    }
}
