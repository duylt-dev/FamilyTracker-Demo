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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `mockwebserver3.MockWebServer` only — same rule as `GraphHopperRoutingProviderTest` (routing
 * plan phase-02 Requirement #4, still true for phase-03): a test that depends on the real internet
 * goes red on exactly the day it needs to be green.
 *
 * [baseUrl] in [setUp] is a bare host with no path (`server.url("/").toString()`), matching the
 * real, currently-configured FOSSGIS-shaped value (`https://valhalla1.openstreetmap.de`,
 * `local.properties`) — [ValhallaRoutingProvider.requestUrl] appends `/route`. Host-branching
 * behaviour (Stadia vs. FOSSGIS vs. self-host) is asserted directly against
 * [ValhallaRoutingProvider.attribution]/[ValhallaRoutingProvider.requestUrl]/
 * [ValhallaRoutingProvider.requestHeaders] below, not through a real request — `stadiamaps.com`
 * cannot be made to resolve to a local `MockWebServer`.
 */
class ValhallaRoutingProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: ValhallaRoutingProvider

    private val from = GeoPoint(latitude = 21.0285, longitude = 105.8542)
    private val to = GeoPoint(latitude = 21.0378, longitude = 105.8342)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = providerFor(baseUrl = server.url("/").toString())
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
        assertEquals(143, directions.points.size)
        assertEquals(742L, directions.durationSeconds)
        assertEquals("valhalla", directions.engineId)

        val request = server.takeRequest()
        // Non-negotiable: POST, never GET — Valhalla takes locations/costing/units as a JSON body.
        assertEquals("POST", request.method)
        assertEquals("/route", request.url.encodedPath)
        assertTrue(request.body?.utf8()?.contains("\"costing\":\"auto\"") == true)
    }

    @Test
    fun `error_code 171 maps to NotFound, not Network`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(400)
                .body("""{"error_code":171,"error":"No suitable edges near location"}""")
                .build(),
        )

        val result = provider.directions(from, to)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun `400 with a different error_code maps to Validation`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(400)
                .body("""{"error_code":110,"error":"Insufficiently specified required parameter"}""")
                .build(),
        )

        val result = provider.directions(from, to)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
    }

    @Test
    fun `500 maps to Network`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(500).body("""{"error_code":154,"error":"Internal server error"}""").build(),
        )

        val result = provider.directions(from, to)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }

    @Test
    fun `attribution is Valhalla plus OpenStreetMap contributors on a non-Stadia host`() {
        val fossgis = providerFor(baseUrl = "https://valhalla1.openstreetmap.de")
        val selfHost = providerFor(baseUrl = "http://192.168.1.10:8002")

        assertEquals(listOf("Valhalla", "OpenStreetMap contributors"), fossgis.attribution())
        assertEquals(listOf("Valhalla", "OpenStreetMap contributors"), selfHost.attribution())
    }

    @Test
    fun `attribution is Stadia Maps plus OpenStreetMap contributors on the Stadia host`() {
        val stadia = providerFor(baseUrl = "https://api.stadiamaps.com/route/v1")

        assertEquals(listOf("Stadia Maps", "OpenStreetMap contributors"), stadia.attribution())
    }

    @Test
    fun `X-Client-Id is sent only for FOSSGIS`() {
        val fossgis = providerFor(baseUrl = "https://valhalla1.openstreetmap.de")
        val stadia = providerFor(baseUrl = "https://api.stadiamaps.com/route/v1")
        val selfHost = providerFor(baseUrl = "http://192.168.1.10:8002")

        assertEquals(mapOf("X-Client-Id" to "com.example.pion.family.tracker.demo"), fossgis.requestHeaders())
        assertTrue(stadia.requestHeaders().isEmpty())
        assertTrue(selfHost.requestHeaders().isEmpty())
    }

    @Test
    fun `api_key is attached only when stadiaApiKey is non-empty`() {
        val withoutKey = providerFor(baseUrl = "https://api.stadiamaps.com/route/v1", stadiaApiKey = "")
        val withKey = providerFor(baseUrl = "https://api.stadiamaps.com/route/v1", stadiaApiKey = "secret")

        assertFalse(withoutKey.requestUrl().contains("api_key"))
        assertTrue(withKey.requestUrl().contains("api_key=secret"))
    }

    @Test
    fun `a bare host gets route appended, a Stadia base URL is used as-is`() {
        val fossgis = providerFor(baseUrl = "https://valhalla1.openstreetmap.de")
        val stadia = providerFor(baseUrl = "https://api.stadiamaps.com/route/v1")

        assertEquals("https://valhalla1.openstreetmap.de/route", fossgis.requestUrl())
        assertEquals("https://api.stadiamaps.com/route/v1", stadia.requestUrl())
    }

    private fun providerFor(baseUrl: String, stadiaApiKey: String = ""): ValhallaRoutingProvider =
        ValhallaRoutingProvider(
            httpClient = RoutingHttpClient(OkHttpClient()),
            json = Json { ignoreUnknownKeys = true },
            config = RoutingConfig(
                engine = RoutingEngine.VALHALLA,
                graphHopperApiKey = "",
                stadiaApiKey = stadiaApiKey,
                valhallaBaseUrl = "",
            ),
            baseUrl = baseUrl,
        )

    private fun loadFixture(): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("valhalla-route-hanoi.json")) {
            "Fixture valhalla-route-hanoi.json not found on test classpath"
        }.bufferedReader().use { it.readText() }
}
