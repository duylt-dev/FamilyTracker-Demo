package com.example.pion.family.tracker.demo.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of `GET https://graphhopper.com/api/1/route` — routing plan phase-02 Step 3.
 *
 * `paths`/`info` both default to empty/null: a 400/401 error response carries neither field, and
 * this DTO must not be the reason a genuine error response fails to parse — the provider decides
 * what a missing `paths` means (empty list -> `AppError.NotFound` in `GraphHopperDirectionsMapper`,
 * never here).
 */
@Serializable
data class GraphHopperDirectionsDto(
    val paths: List<PathDto> = emptyList(),
    val info: InfoDto? = null,
)

/**
 * Field types come from the real fixture (`data/src/test/resources/graphhopper-route-hanoi.json`),
 * not from memory — VERIFY-2026-08-24.md. `distance: Int` or `time: Int` throws
 * `SerializationException` on 100% of real responses, not just an edge case.
 */
@Serializable
data class PathDto(
    /** Metres. Real value: `3166.054` — a `Double`, never `Int`. */
    val distance: Double,
    /** MILLISECONDS, not seconds. Real value: `585990`. Dividing (with rounding) is the mapper's job. */
    val time: Long,
    /** Encoded polyline string. Only meaningful when [pointsEncoded] is `true`. */
    val points: String,
    /**
     * Must stay `true` — `points_encoded=false` on the request returns raw GeoJSON `[lon, lat]`
     * arrays instead, which would crash [points] decoding at runtime (Key Insight #1). Defaults to
     * `true` to match the request's own default; the mapper still asserts this explicitly rather
     * than trusting the default silently.
     */
    @SerialName("points_encoded") val pointsEncoded: Boolean = true,
    /**
     * GraphHopper states its own encoding factor instead of the client guessing "precision 5" —
     * Key Insight #1. Real value: `100000.0`. Field absent -> this default, which is precision 5.
     */
    @SerialName("points_encoded_multiplier") val pointsEncodedMultiplier: Double = 100_000.0,
)

/** [copyrights] is legal condition #1 (`docs/routing-and-map-attribution.md` §3) verbatim. */
@Serializable
data class InfoDto(
    val copyrights: List<String> = emptyList(),
)
