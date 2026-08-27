package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test vectors are known values, not self-generated (routing plan phase-01 Risk Assessment).
 *
 * Precision-5 vector is the public reference example from Google's own polyline algorithm
 * documentation (developers.google.com/maps/documentation/utilities/polylinealgorithm).
 *
 * Precision-6 vector is the first two REAL points decoded from
 * `data/src/test/resources/valhalla-route-hanoi.json`'s `trip.legs[0].shape` — a real Valhalla
 * response for a Hà Nội route (Hồ Gươm area), which the plan's VERIFY-2026-08-24.md confirmed
 * decodes at precision 6, not 5. Verified independently against the Google reference vector
 * above before slicing this substring out (both decode with the exact same algorithm).
 */
class PolylineDecoderTest {

    @Test
    fun `decodes known precision-5 vector`() {
        val points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)

        assertPointsEqual(
            expected = listOf(
                GeoPoint(38.5, -120.2),
                GeoPoint(40.7, -120.95),
                GeoPoint(43.252, -126.453),
            ),
            actual = points,
        )
    }

    @Test
    fun `decodes known precision-6 vector from a real Valhalla response`() {
        val points = PolylineDecoder.decode("a}nbg@ily{hEn@~N", precision = 6)

        assertPointsEqual(
            expected = listOf(
                GeoPoint(21.028833, 105.854165),
                GeoPoint(21.028809, 105.853909),
            ),
            actual = points,
        )
    }

    @Test
    fun `empty string decodes to an empty list`() {
        assertEquals(emptyList<GeoPoint>(), PolylineDecoder.decode("", precision = 5))
    }

    @Test
    fun `truncated string with no terminating byte decodes to an empty list, not a crash`() {
        // First 4 bytes of the precision-5 vector above, all 4 with the continuation bit set
        // (>= 0x20) and no terminating byte — the shape of a response cut one byte short.
        val points = PolylineDecoder.decode("_p~i", precision = 5)

        assertEquals(emptyList<GeoPoint>(), points)
    }

    @Test
    fun `same encoded string decoded at precision 5 vs precision 6 is off by exactly 10x`() {
        // Key Insight #5 — this is the exact trap PolyUtil.decode() falls into: it always assumes
        // precision 5, so feeding it a Valhalla (precision-6) string lands every coordinate 10x
        // off. Pinned here before phase-03 has a chance to hit it.
        val encoded = "a}nbg@ily{hEn@~N"
        val atPrecision6 = PolylineDecoder.decode(encoded, precision = 6)
        val atPrecision5 = PolylineDecoder.decode(encoded, precision = 5)

        assertEquals(atPrecision6.size, atPrecision5.size)
        atPrecision6.zip(atPrecision5).forEach { (p6, p5) ->
            assertEquals(10.0, p5.latitude / p6.latitude, 0.0001)
            assertEquals(10.0, p5.longitude / p6.longitude, 0.0001)
        }
    }

    private fun assertPointsEqual(expected: List<GeoPoint>, actual: List<GeoPoint>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.latitude, a.latitude, 1e-6)
            assertEquals(e.longitude, a.longitude, 1e-6)
        }
    }
}
