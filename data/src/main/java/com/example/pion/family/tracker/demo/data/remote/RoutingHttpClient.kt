package com.example.pion.family.tracker.demo.data.remote

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/**
 * A completed HTTP exchange — the server answered, whatever the status code. Non-2xx is still a
 * completed exchange, not a [RoutingHttpClient] failure: see the class doc below and phase-02's
 * `RoutingErrorMapper(code, body)`, which is the one place that decides what a given code means
 * for a given provider (GraphHopper 400 is a validation message; Valhalla's shape differs).
 */
data class HttpResponse(val code: Int, val body: String)

/**
 * Thin OkHttp wrapper shared by every routing provider (phase-02 GraphHopper, phase-03
 * Valhalla) — routing plan phase-01 Step 9. Cancellation must be real (MVI doc §3, Key Insight
 * #7): `OkHttpClient.newCall(req).execute()` is a **blocking** call that knows nothing about
 * coroutines — cancelling the job would leave a thread parked until the socket times out.
 * `enqueue` + `suspendCancellableCoroutine` + `invokeOnCancellation { call.cancel() }` releases
 * the connection immediately instead.
 *
 * `AppResult.Failure(AppError.Network(...))` is reserved for **transport** failure only — no
 * network, DNS, timeout, cancellation via [IOException]. Any response that actually came back
 * from the server, 2xx or not, is `AppResult.Success(HttpResponse(code, body))`: the raw code
 * rides along on the [HttpResponse] type itself, not string-embedded in an error message, and
 * the *provider* (phase-02's `RoutingErrorMapper`) is the one place that decides what a 400/401/
 * 429/5xx means for its own error JSON shape. Collapsing non-2xx into a `Failure` here would
 * force phase-02 to string-parse the code back out of a message — the exact fragility a
 * dedicated mapper exists to avoid. This is not a new [AppError] type, so the "no new error
 * type" constraint (VERIFY-2026-08-24.md) still holds.
 */
class RoutingHttpClient(
    private val client: OkHttpClient,
) {

    suspend fun get(url: String): AppResult<HttpResponse> =
        execute(Request.Builder().url(url).get().build())

    /**
     * [headers] defaults to none — GraphHopper (phase-02) never calls this at all (GET only), and
     * most Valhalla hosts (phase-03) need none either. FOSSGIS is the one host that asks callers to
     * self-identify via `X-Client-Id`; a `Map` keeps that a per-call detail instead of a client-wide
     * default, since Stadia/self-host must never send it.
     */
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): AppResult<HttpResponse> =
        execute(
            Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA_TYPE)).apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.build(),
        )

    private suspend fun execute(request: Request): AppResult<HttpResponse> =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(AppResult.Failure(AppError.Network(e.message)))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    // `response.body` is non-null in OkHttp 5.x (`@NotNull ResponseBody body()`,
                    // verified against the real `okhttp-android-5.5.0.aar` classes on this
                    // machine) — no `?.` needed, unlike the 4.x-era nullable signature.
                    val result = response.use { AppResult.Success(HttpResponse(it.code, it.body.string())) }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
