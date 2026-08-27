package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.domain.model.AppError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `(code, body) -> AppError`, shared by both routing providers as "the one place that decides what
 * a given code means" (phase-02 Architecture) — but as **two functions, not one**, per phase-03's
 * decision (correcting an earlier draft of this KDoc that claimed the opposite): GraphHopper's
 * error body is `{"message": "..."}`, Valhalla's is `{"error_code": 171, "error": "..."}` — two
 * unrelated JSON shapes with unrelated field names. A single function that tries to read both
 * shapes is exactly the coupling this class exists to avoid ("gộp lại thành một hàm biết cả hai
 * format là đúng thứ mà cổng này sinh ra để tránh"). [fromGraphHopper] and [fromValhalla] each read
 * only their own provider's shape; neither calls the other.
 *
 * Pure: no `android.util.Log`/`FtdLog` call here. The caller (provider) decides what to log and
 * never logs the request URL, which carries `key=`.
 */
object RoutingErrorMapper {

    /**
     * GraphHopper (phase-02 Architecture):
     * - transport failure / 401 / 429 / 5xx -> [AppError.Network] (retry is meaningful)
     * - 400 / 501 -> [AppError.Validation] (a programming or coordinate bug, retry is not meaningful)
     * - 200 with empty `paths` -> [AppError.NotFound] — mapped in `GraphHopperDirectionsMapper`,
     *   not here, since that case never reaches this function (only non-2xx codes do).
     */
    fun fromGraphHopper(code: Int, body: String): AppError {
        val message = extractField(body, "message")
        return when (code) {
            400, 501 -> AppError.Validation(message)
            else -> AppError.Network(message)
        }
    }

    /**
     * Valhalla (phase-03 Implementation Step 6): `error_code` 171 ("No suitable edges near
     * location") -> [AppError.NotFound] — the requested coordinate itself has no routable road
     * nearby, so retrying identical input is pointless, unlike every other code here. Any other
     * `error_code` falls back to the same `code`-based split GraphHopper uses (400 -> Validation,
     * else Network) — Valhalla's HTTP status still carries the same broad meaning.
     */
    fun fromValhalla(code: Int, body: String): AppError {
        if (extractErrorCode(body) == VALHALLA_NO_SUITABLE_EDGES) {
            return AppError.NotFound(extractField(body, "error"))
        }
        val message = extractField(body, "error")
        return when (code) {
            400 -> AppError.Validation(message)
            else -> AppError.Network(message)
        }
    }

    /**
     * Reads a single top-level string field for logging/diagnostics only — never surfaced to UI
     * directly (English, technical; Security Considerations in the phase file). `null` on any
     * parse failure: an unreadable error body must not throw, it must just carry no message.
     */
    private fun extractField(body: String, field: String): String? =
        runCatching {
            (Json.parseToJsonElement(body) as? JsonObject)?.get(field)?.jsonPrimitive?.contentOrNull
        }.getOrNull()

    private fun extractErrorCode(body: String): Int? =
        runCatching {
            (Json.parseToJsonElement(body) as? JsonObject)?.get("error_code")?.jsonPrimitive?.intOrNull
        }.getOrNull()

    private const val VALHALLA_NO_SUITABLE_EDGES = 171
}
