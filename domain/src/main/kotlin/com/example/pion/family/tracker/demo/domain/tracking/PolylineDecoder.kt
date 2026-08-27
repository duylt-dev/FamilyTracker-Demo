package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import kotlin.math.pow

/**
 * Decodes the standard Google polyline algorithm format, `precision` taken as a **parameter** —
 * routing plan phase-01 Key Insight #5. `PolyUtil.decode(String)` from `android-maps-utils` has
 * no precision parameter and always assumes precision 5; GraphHopper returns precision 5 but
 * Valhalla returns precision 6 (verified against real responses, VERIFY-2026-08-24.md). Using
 * `PolyUtil` for a precision-6 string decodes coordinates off by exactly 10x — a polyline that
 * lands in the sea instead of on the street, and `RerouteEvaluator` reading it downstream is
 * wrong too. `PolylineDecoderTest` pins that exact failure mode.
 *
 * Lives at `:domain`, not `:ui` (Key Insight #5): this is a pure algorithm, testable without
 * Android — `:domain/tracking/` is exactly where a location algorithm belongs (LLM.md §12).
 */
object PolylineDecoder {

    /**
     * Empty input decodes to an empty list. A truncated/malformed string (one byte short from a
     * server) also decodes to an empty list — it never throws. One bad byte from a server must
     * not crash the app.
     */
    fun decode(encoded: String, precision: Int): List<GeoPoint> {
        if (encoded.isEmpty()) return emptyList()
        val factor = 10.0.pow(precision)
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            val dLat = readSignedValue(encoded, index) ?: return emptyList()
            lat += dLat.value
            index = dLat.nextIndex

            val dLng = readSignedValue(encoded, index) ?: return emptyList()
            lng += dLng.value
            index = dLng.nextIndex

            points.add(GeoPoint(latitude = lat / factor, longitude = lng / factor))
        }
        return points
    }

    /** One zigzag-encoded, base-64-ish varint chunk. `null` means the string ran out mid-chunk. */
    private fun readSignedValue(encoded: String, startIndex: Int): DecodedChunk? {
        var index = startIndex
        var shift = 0
        var result = 0
        var byte: Int
        do {
            if (index >= encoded.length) return null
            byte = encoded[index].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
            index++
        } while (byte >= 0x20)
        val value = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        return DecodedChunk(value, index)
    }

    private data class DecodedChunk(val value: Int, val nextIndex: Int)
}
