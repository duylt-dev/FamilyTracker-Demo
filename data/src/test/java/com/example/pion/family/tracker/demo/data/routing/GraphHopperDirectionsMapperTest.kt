package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.remote.dto.GraphHopperDirectionsDto
import com.example.pion.family.tracker.demo.data.remote.dto.InfoDto
import com.example.pion.family.tracker.demo.data.remote.dto.PathDto
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts against the REAL fixture's real numbers (routing plan phase-02 Step 8), not vague
 * ranges — `data/src/test/resources/graphhopper-route-hanoi.json`, a real GraphHopper Cloud
 * response (2026-08-24, `profile=car`, `locale=vi`, Hoàn Kiếm -> Văn Miếu, README.md).
 */
class GraphHopperDirectionsMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `real fixture maps to Directions with the fixture's real numbers`() {
        val dto = json.decodeFromString(GraphHopperDirectionsDto.serializer(), loadFixture())

        val result = GraphHopperDirectionsMapper.toDirections(dto)

        assertTrue(result is AppResult.Success)
        val directions = (result as AppResult.Success).data
        assertEquals(69, directions.points.size)
        assertEquals(21.02850, directions.points.first().latitude, 0.0001)
        assertEquals(105.85387, directions.points.first().longitude, 0.0001)
        assertEquals(3166.054, directions.distanceMeters, 0.001)
        // 585990ms rounds to 586s — NOT 585990, and not 585 either (truncating division would
        // land there). This is the only assert standing between a working ETA and one wrong by
        // roughly 1000x (routing plan phase-02, "Fixture" section).
        assertEquals(586L, directions.durationSeconds)
        assertEquals("graphhopper", directions.engineId)
        assertEquals(listOf("GraphHopper", "OpenStreetMap contributors"), directions.attribution)
    }

    @Test
    fun `odd points_encoded_multiplier that is not a power of 10 is Validation`() {
        val dto = fixturePath().copy(pointsEncodedMultiplier = 123.0).asDto()

        val result = GraphHopperDirectionsMapper.toDirections(dto)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
    }

    @Test
    fun `points_encoded false is Validation, never attempted to decode`() {
        val dto = fixturePath().copy(pointsEncoded = false).asDto()

        val result = GraphHopperDirectionsMapper.toDirections(dto)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
    }

    @Test
    fun `empty paths is NotFound`() {
        val dto = GraphHopperDirectionsDto(paths = emptyList(), info = InfoDto(listOf("GraphHopper")))

        val result = GraphHopperDirectionsMapper.toDirections(dto)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun `missing copyrights falls back to the required legal attribution, never empty`() {
        val dto = GraphHopperDirectionsDto(paths = listOf(fixturePath()), info = null)

        val result = GraphHopperDirectionsMapper.toDirections(dto)

        assertTrue(result is AppResult.Success)
        val attribution = (result as AppResult.Success).data.attribution
        assertTrue(attribution.isNotEmpty())
        assertEquals(listOf("GraphHopper", "OpenStreetMap contributors"), attribution)
    }

    private fun fixturePath(): PathDto =
        json.decodeFromString(GraphHopperDirectionsDto.serializer(), loadFixture()).paths.first()

    private fun PathDto.asDto(): GraphHopperDirectionsDto =
        GraphHopperDirectionsDto(paths = listOf(this), info = InfoDto(listOf("GraphHopper", "OpenStreetMap contributors")))

    private fun loadFixture(): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("graphhopper-route-hanoi.json")) {
            "Fixture graphhopper-route-hanoi.json not found on test classpath"
        }.bufferedReader().use { it.readText() }
}
