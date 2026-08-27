package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.remote.dto.GraphHopperDirectionsDto
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.tracking.PolylineDecoder
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * `GraphHopperDirectionsDto` -> `Directions` — routing plan phase-02 Architecture. Pure: no HTTP,
 * no JSON string parsing (that is the provider's job, via the injected `Json`), which is exactly
 * what lets [toDirections] be tested against a DTO built from a plain string
 * (`GraphHopperDirectionsMapperTest`) without ever constructing a provider.
 */
object GraphHopperDirectionsMapper {

    /**
     * Legal condition #1 fallback (`docs/routing-and-map-attribution.md` §3) — used only when
     * `info.copyrights` is absent or empty. [Directions.attribution] must never be empty.
     */
    private val FALLBACK_ATTRIBUTION = listOf("GraphHopper", "OpenStreetMap contributors")

    fun toDirections(dto: GraphHopperDirectionsDto): AppResult<Directions> {
        val path = dto.paths.firstOrNull()
            ?: return AppResult.Failure(AppError.NotFound("GraphHopper trả về paths rỗng — không có tuyến đường"))

        // Key Insight #1: `points_encoded=false` returns raw GeoJSON coordinate arrays, not an
        // encoded string. Feeding that into PolylineDecoder crashes at runtime, not build time —
        // reject it here instead of ever attempting to decode.
        if (!path.pointsEncoded) {
            return AppResult.Failure(AppError.Validation("points_encoded=false không được hỗ trợ"))
        }

        val multiplier = path.pointsEncodedMultiplier
        val precision = log10(multiplier).roundToInt()
        if (10.0.pow(precision) != multiplier) {
            return AppResult.Failure(
                AppError.Validation("points_encoded_multiplier không phải luỹ thừa của 10: $multiplier"),
            )
        }

        val attribution = dto.info?.copyrights?.takeIf { it.isNotEmpty() } ?: FALLBACK_ATTRIBUTION

        return AppResult.Success(
            Directions(
                points = PolylineDecoder.decode(path.points, precision),
                distanceMeters = path.distance,
                // `time` is milliseconds (Key Insight #3). Rounded, not truncated: 585990ms is
                // 585.99s, and truncating division (`time / 1000`) lands on 585 — the fixture's
                // real contract is 586s, so this must round to the nearest second.
                durationSeconds = (path.time / 1000.0).roundToLong(),
                engineId = "graphhopper",
                attribution = attribution,
            ),
        )
    }
}
