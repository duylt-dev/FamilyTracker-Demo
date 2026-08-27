package com.example.pion.family.tracker.demo.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of `POST <valhallaBaseUrl>/route` — routing plan phase-03 Step 3. Field types come
 * from the real fixture (`data/src/test/resources/valhalla-route-hanoi.json`), not from memory
 * (VERIFY-2026-08-24.md mục 2): `trip.summary.time` is `742.029` — a `Double`, not an `Int`/`Long`.
 * Declaring it as an integer type makes `kotlinx.serialization` throw `SerializationException`
 * **at parse time**, on 100% of real responses, not just an edge case.
 *
 * [trip] is nullable: a Valhalla error response is `{"error_code": 171, "error": "..."}`, with no
 * `trip` key at all — `RoutingErrorMapper.fromValhalla` is what reads that shape, not this DTO
 * (this DTO only ever gets decoded for a 2xx response, mirroring `GraphHopperDirectionsDto`).
 */
@Serializable
data class ValhallaDirectionsDto(val trip: TripDto? = null)

@Serializable
data class TripDto(
    val summary: SummaryDto,
    val legs: List<LegDto> = emptyList(),
    val status: Int = 0,
    @SerialName("status_message") val statusMessage: String? = null,
)

/**
 * [time] is seconds, real, with a fractional part (`742.029`) — never an integer type.
 * [length] is **kilometres** (`units=kilometers` in the request), not metres like GraphHopper's
 * `distance` — the mapper is the one place that multiplies by 1000, on purpose.
 */
@Serializable
data class SummaryDto(val time: Double, val length: Double)

/**
 * [shape] is an encoded polyline at **precision 6**, not the precision-5 GraphHopper/Google
 * default — `PolyUtil.decode(String)` has no precision parameter and must never touch this field.
 */
@Serializable
data class LegDto(val shape: String)
