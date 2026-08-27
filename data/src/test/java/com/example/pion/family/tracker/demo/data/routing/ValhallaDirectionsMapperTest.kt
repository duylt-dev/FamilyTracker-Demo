package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.remote.dto.SummaryDto
import com.example.pion.family.tracker.demo.data.remote.dto.TripDto
import com.example.pion.family.tracker.demo.data.remote.dto.ValhallaDirectionsDto
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.tracking.PolylineDecoder
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts against the REAL fixture's real numbers (routing plan phase-03 Step 8), not vague
 * ranges — `data/src/test/resources/valhalla-route-hanoi.json`, a real FOSSGIS response
 * (2026-08-24, `costing=auto`, `units=kilometers`), same Hồ Gươm -> Văn Miếu pair as the
 * GraphHopper fixture.
 */
class ValhallaDirectionsMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val origin = GeoPoint(latitude = 21.0285, longitude = 105.8542)
    private val attribution = listOf("Valhalla", "OpenStreetMap contributors")

    @Test
    fun `real fixture maps to Directions with the fixture's real numbers`() {
        val dto = json.decodeFromString(ValhallaDirectionsDto.serializer(), loadFixture())

        val result = ValhallaDirectionsMapper.toDirections(dto, origin, attribution)

        assertTrue(result is AppResult.Success)
        val directions = (result as AppResult.Success).data
        assertEquals(143, directions.points.size)
        // summary.length = 3.768 KILOMETRES -> 3768.0 metres, not 3.768 — a km/m mixup is a 1000x
        // error in every distance shown on screen.
        assertEquals(3768.0, directions.distanceMeters, 0.001)
        // summary.time = 742.029 (Double, seconds with a fraction) rounds to 742 — not truncated,
        // and not the raw fractional value a wrongly-`Long`-typed field would have crashed on
        // parsing already (VERIFY-2026-08-24.md mục 2).
        assertEquals(742L, directions.durationSeconds)
        assertEquals("valhalla", directions.engineId)
        assertEquals(attribution, directions.attribution)
    }

    /**
     * The one test that matters most (phase-03 spec, Implementation Step 8): decoding the SAME
     * real shape at the wrong precision must land somewhere that is obviously not Vietnam. Pins
     * "wrong precision is a visible failure" so nobody can quietly reintroduce `PolyUtil.decode()`
     * (hardcoded precision 5) against Valhalla data.
     */
    @Test
    fun `precision 5 on the same real shape decodes outside Vietnam`() {
        val dto = json.decodeFromString(ValhallaDirectionsDto.serializer(), loadFixture())
        val shape = requireNotNull(dto.trip?.legs?.firstOrNull()?.shape)

        val wrongPrecisionPoints = PolylineDecoder.decode(shape, precision = 5)

        val first = wrongPrecisionPoints.first()
        // Vietnam's mainland sits within roughly 8°..24°N, 102°..110°E. Decoding a precision-6
        // string at precision 5 multiplies every coordinate by 10 (measured against this exact
        // fixture: 21.028833 -> 210.28833, 105.854165 -> 1058.54165) — not just "outside Vietnam"
        // but outside any valid latitude/longitude on Earth.
        assertTrue(
            "expected the wrong-precision point to fall outside Vietnam, was $first",
            first.latitude !in 8.0..24.0 || first.longitude !in 102.0..110.0,
        )
    }

    @Test
    fun `missing trip is NotFound`() {
        val dto = ValhallaDirectionsDto(trip = null)

        val result = ValhallaDirectionsMapper.toDirections(dto, origin, attribution)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun `empty legs is NotFound, not a crash`() {
        val dto = ValhallaDirectionsDto(
            trip = TripDto(summary = SummaryDto(time = 1.0, length = 1.0), legs = emptyList()),
        )

        val result = ValhallaDirectionsMapper.toDirections(dto, origin, attribution)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun `first point more than 5km from the requested origin is Validation`() {
        // A real Hanoi shape decoded correctly at precision 6, but requested from Ho Chi Minh City
        // — well over 1000km away. This is the mapper's own defensive assert (phase-03
        // Implementation Step 4), independent of whatever precision bug might trigger it in
        // production — the "wrong precision" test above proves the trigger case separately.
        val farOrigin = GeoPoint(latitude = 10.7769, longitude = 106.7009)
        val dto = json.decodeFromString(ValhallaDirectionsDto.serializer(), loadFixture())

        val result = ValhallaDirectionsMapper.toDirections(dto, farOrigin, attribution)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
    }

    @Test
    fun `attribution passed in is carried through unchanged`() {
        val dto = json.decodeFromString(ValhallaDirectionsDto.serializer(), loadFixture())
        val stadiaAttribution = listOf("Stadia Maps", "OpenStreetMap contributors")

        val result = ValhallaDirectionsMapper.toDirections(dto, origin, stadiaAttribution)

        assertTrue(result is AppResult.Success)
        assertEquals(stadiaAttribution, (result as AppResult.Success).data.attribution)
    }

    private fun loadFixture(): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("valhalla-route-hanoi.json")) {
            "Fixture valhalla-route-hanoi.json not found on test classpath"
        }.bufferedReader().use { it.readText() }
}
