package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.remote.RoutingHttpClient
import com.example.pion.family.tracker.demo.data.remote.dto.ValhallaDirectionsDto
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.RoutingConfig
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Valhalla implementation of [RoutingProvider] — routing plan phase-03, the port's second engine.
 * The point of this class is not "one more vendor": everything about Valhalla's wire shape differs
 * from `GraphHopperRoutingProvider` (phase-02) — POST instead of GET, a JSON body instead of query
 * params, `trip.legs[].shape` instead of `paths[].points`, precision 6 instead of 5, `length` in
 * kilometres instead of metres — and none of that difference is visible past [directions]'s
 * `AppResult<Directions>` return type. That is the proof the `RoutingProvider` port is real.
 *
 * Same split as GraphHopper's provider: this class only builds the request and joins pieces; it
 * never parses JSON itself (`json.decodeFromString`) beyond turning bytes into a DTO, and never
 * decodes a polyline itself — `ValhallaDirectionsMapper` does both of those, which is what lets it
 * stay testable from a plain DTO (`ValhallaDirectionsMapperTest`) while this class stays testable
 * from a fake HTTP server (`ValhallaRoutingProviderTest`, `mockwebserver3`).
 *
 * [baseUrl] defaults to [RoutingConfig.valhallaBaseUrl] so production wiring (`DataModule`) needs
 * no extra argument — same test-only constructor trick as `GraphHopperRoutingProvider`'s `baseUrl`,
 * which `ValhallaRoutingProviderTest` overrides with a `MockWebServer` URL.
 */
class ValhallaRoutingProvider(
    private val httpClient: RoutingHttpClient,
    private val json: Json,
    private val config: RoutingConfig,
    private val baseUrl: String = config.valhallaBaseUrl,
) : RoutingProvider {

    override suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions> {
        val requestBody = json.encodeToString(
            RouteRequestDto.serializer(),
            RouteRequestDto(
                locations = listOf(
                    LocationRequestDto(lat = from.latitude, lon = from.longitude),
                    LocationRequestDto(lat = to.latitude, lon = to.longitude),
                ),
                costing = COSTING,
            ),
        )
        return when (val response = httpClient.postJson(requestUrl(), requestBody, requestHeaders())) {
            is AppResult.Failure -> response
            is AppResult.Success -> handleResponse(response.data.code, response.data.body, from)
        }
    }

    private fun handleResponse(code: Int, body: String, origin: GeoPoint): AppResult<Directions> {
        if (code !in 200..299) {
            // Never the request URL itself — Valhalla carries no `key=` on FOSSGIS/self-host, and
            // Stadia's `api_key` is a query param, same "don't log the URL" rule as GraphHopper's
            // 401 branch even though this specific engine has less to hide by default.
            FtdLog.w(TAG, "routing_error engine=valhalla code=$code")
            return AppResult.Failure(RoutingErrorMapper.fromValhalla(code, body))
        }
        return parseSuccessBody(body, origin)
    }

    private fun parseSuccessBody(body: String, origin: GeoPoint): AppResult<Directions> {
        val dto = try {
            json.decodeFromString(ValhallaDirectionsDto.serializer(), body)
        } catch (e: SerializationException) {
            return AppResult.Failure(AppError.Validation("Valhalla response không đọc được: ${e.message}"))
        }
        return ValhallaDirectionsMapper.toDirections(dto, origin, attribution())
    }

    /**
     * Valhalla has no `info.copyrights`-equivalent field, unlike GraphHopper (phase-02 Key Insight
     * #5) — credit is built from the host instead (phase-03 spec Implementation Step 4).
     * `OpenStreetMap contributors` is present on every branch; dropping the hosting vendor's own
     * name on the Stadia branch would omit exactly what their terms require
     * (`docs/routing-and-map-attribution.md` §3).
     *
     * `internal`, not `private`: lets `ValhallaRoutingProviderTest` assert this pure string-building
     * decision directly, without needing a `MockWebServer` request to a host string it can't
     * actually control DNS for (`stadiamaps.com` won't resolve to a local test server).
     */
    internal fun attribution(): List<String> =
        if (baseUrl.contains(STADIA_HOST)) {
            listOf("Stadia Maps", "OpenStreetMap contributors")
        } else {
            listOf("Valhalla", "OpenStreetMap contributors")
        }

    /**
     * Stadia's base URL (`https://api.stadiamaps.com/route/v1`, per `local.properties.example`) is
     * already the full route endpoint. FOSSGIS's and a self-host's base URL is a bare host
     * (`https://valhalla1.openstreetmap.de`, `http://<host>:8002`) — Valhalla's own API exposes the
     * route endpoint at `/route` on top of that, verified against the real fixture's own `curl`
     * command (`data/src/test/resources/README.md`: `curl -X POST
     * 'https://valhalla1.openstreetmap.de/route' ...`), so those two need it appended.
     *
     * `internal` for the same testability reason as [attribution].
     */
    internal fun requestUrl(): String {
        val endpoint = if (baseUrl.contains(STADIA_HOST)) baseUrl else "${baseUrl.trimEnd('/')}/route"
        return endpoint.toHttpUrl().newBuilder()
            .apply { if (config.stadiaApiKey.isNotEmpty()) addQueryParameter("api_key", config.stadiaApiKey) }
            .build()
            .toString()
    }

    /**
     * FOSSGIS is volunteer infrastructure; its maintainers ask publishers to self-identify via this
     * header (`docs/routing-and-map-attribution.md` §5) — Stadia and a self-host get neither the
     * header nor the obligation. `internal` for the same testability reason as [attribution].
     */
    internal fun requestHeaders(): Map<String, String> =
        if (baseUrl.contains(FOSSGIS_HOST)) mapOf("X-Client-Id" to CLIENT_ID) else emptyMap()

    @Serializable
    private data class RouteRequestDto(
        val locations: List<LocationRequestDto>,
        val costing: String,
        val units: String = "kilometers",
    )

    @Serializable
    private data class LocationRequestDto(val lat: Double, val lon: Double)

    private companion object {
        const val TAG = "FTD_EVENT"
        const val STADIA_HOST = "stadiamaps.com"
        const val FOSSGIS_HOST = "openstreetmap.de"
        const val CLIENT_ID = "com.example.pion.family.tracker.demo"

        // Valhalla's `motorcycle` costing exists and is free on both FOSSGIS and Stadia — unlike
        // GraphHopper, whose free tier rejects it with a 400 (phase-02 Key Insight #4, VERIFY-
        // 2026-08-24.md mục 6). That is a real operational difference for a Vietnam-focused family
        // tracker. Still Beta on Valhalla's side though (phase-03 Key Insight #4), so this stays
        // the only costing ever sent until it is measured against real Vietnamese roads.
        const val COSTING = "auto"
    }
}
