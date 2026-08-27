package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.remote.RoutingHttpClient
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.RoutingConfig
import com.example.pion.family.tracker.demo.domain.model.RoutingEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `mockwebserver3.MockWebServer` only — routing plan phase-02 Requirement #4: a test that depends
 * on the real internet goes red on exactly the day it needs to be green. [baseUrl] is pointed at
 * the fake server via `GraphHopperRoutingProvider`'s test-only constructor parameter.
 */
class GraphHopperRoutingProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GraphHopperRoutingProvider

    private val from = GeoPoint(latitude = 21.0285, longitude = 105.8542)
    private val to = GeoPoint(latitude = 21.0378, longitude = 105.8342)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = GraphHopperRoutingProvider(
            httpClient = RoutingHttpClient(OkHttpClient()),
            json = Json { ignoreUnknownKeys = true },
            config = RoutingConfig(
                engine = RoutingEngine.GRAPHHOPPER,
                graphHopperApiKey = "test-key",
                stadiaApiKey = "",
                valhallaBaseUrl = "",
            ),
            baseUrl = server.url("/api/1/route").toString(),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `200 with real fixture body maps to Success`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(loadFixture()).build())

        val result = provider.directions(from, to)

        assertTrue(result is AppResult.Success)
        val directions = (result as AppResult.Success).data
        assertEquals(69, directions.points.size)
        assertEquals(586L, directions.durationSeconds)

        // Non-negotiable: GET, never POST — POST reverses coordinate order for GraphHopper.
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `401 maps to Network`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"message":"Wrong credentials"}""").build())

        val result = provider.directions(from, to)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }

    @Test
    fun `400 maps to Validation`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(400).body("""{"message":"Cannot find point 0: 21.0,105.0"}""").build(),
        )

        val result = provider.directions(from, to)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
    }

    @Test
    fun `200 with empty paths maps to NotFound`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"paths": []}""").build())

        val result = provider.directions(from, to)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    private fun loadFixture(): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("graphhopper-route-hanoi.json")) {
            "Fixture graphhopper-route-hanoi.json not found on test classpath"
        }.bufferedReader().use { it.readText() }
}
