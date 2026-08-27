package com.example.pion.family.tracker.demo.data.remote

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the post-review fix (coordinator defect 1, 2026-08-24): a non-2xx response is a completed
 * HTTP exchange, not a [RoutingHttpClient] failure. The raw HTTP code must survive on
 * [HttpResponse] so phase-02's `RoutingErrorMapper(code, body)` can read it directly, instead of
 * being embedded (and lost to string-parsing fragility) inside an [AppError.Network] message.
 * `mockwebserver3` (`mockwebserver3-junit4` artifact, LLM.md/phase-01 Step 1), not the legacy
 * `mockwebserver` — `MockResponse.Builder()` is the maintained, immutable API.
 */
class RoutingHttpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: RoutingHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RoutingHttpClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        // Already closed by the transport-failure test below on purpose — closing twice must not
        // fail the other two tests that never touch it.
        runCatching { server.close() }
    }

    @Test
    fun `200 response is Success carrying the body`() = runTest {
        val body = """{"ok":true}"""
        server.enqueue(MockResponse.Builder().code(200).body(body).build())

        val result = client.get(server.url("/route").toString())

        assertEquals(AppResult.Success(HttpResponse(code = 200, body = body)), result)
    }

    @Test
    fun `401 response is Success carrying the code, not a Failure`() = runTest {
        val errorBody = """{"message":"invalid key"}"""
        server.enqueue(MockResponse.Builder().code(401).body(errorBody).build())

        val result = client.get(server.url("/route").toString())

        // The whole point of the fix: a 401 is still a completed exchange. Collapsing it into
        // AppResult.Failure would force the caller to string-parse the code back out of a
        // message — exactly the fragility RoutingErrorMapper (phase-02) exists to avoid.
        assertEquals(AppResult.Success(HttpResponse(code = 401, body = errorBody)), result)
    }

    @Test
    fun `transport failure is a Failure with AppError Network`() = runTest {
        val url = server.url("/route").toString()
        server.close() // unreachable now -> connection refused, a real transport failure

        val result = client.get(url)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }
}
