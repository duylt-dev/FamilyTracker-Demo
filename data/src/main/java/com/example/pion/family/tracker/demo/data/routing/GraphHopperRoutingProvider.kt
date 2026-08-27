package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.remote.RoutingHttpClient
import com.example.pion.family.tracker.demo.data.remote.dto.GraphHopperDirectionsDto
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.RoutingConfig
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * GraphHopper Cloud implementation of [RoutingProvider] — routing plan phase-02.
 *
 * Only builds a URL and hands the result to [RoutingHttpClient] / [GraphHopperDirectionsMapper];
 * it never parses JSON itself and never decodes a polyline itself (Architecture, phase-02 plan) —
 * the mapper stays testable from a plain string, this class stays testable from a fake HTTP
 * server (`GraphHopperRoutingProviderTest`, `mockwebserver3`).
 *
 * GET with `point=lat,lon`, never POST (Key Insight #2): POST reverses to `[lon, lat]`, and a
 * swapped Hanoi coordinate lands in the Indian Ocean with a 400 that names no cause.
 *
 * [baseUrl] defaults to the real endpoint and exists only so
 * `GraphHopperRoutingProviderTest` can point it at a `MockWebServer` instead — `String` is one of
 * `org.koin.test.verify.Verify.whiteList`'s default primitive types, so this extra constructor
 * parameter needs no `extraTypes` addition to `KoinModulesTest` (verified against
 * `koin-test-jvm:4.2.2` sources: `Verify.primitiveTypes` already contains `String::class`).
 */
class GraphHopperRoutingProvider(
    private val httpClient: RoutingHttpClient,
    private val json: Json,
    private val config: RoutingConfig,
    private val baseUrl: String = BASE_URL,
) : RoutingProvider {

    override suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions> =
        when (val response = httpClient.get(buildUrl(from, to))) {
            is AppResult.Failure -> response
            is AppResult.Success -> handleResponse(response.data.code, response.data.body)
        }

    private fun handleResponse(code: Int, body: String): AppResult<Directions> {
        if (code !in 200..299) {
            // 401 = key sai/thiếu, người dùng không tự sửa được — log để dev thấy (Architecture
            // table). Never log the request URL itself: it carries `key=`.
            if (code == 401) FtdLog.w(TAG, "routing_auth_failed engine=graphhopper code=$code")
            return AppResult.Failure(RoutingErrorMapper.fromGraphHopper(code, body))
        }
        return parseSuccessBody(body)
    }

    private fun parseSuccessBody(body: String): AppResult<Directions> {
        val dto = try {
            json.decodeFromString(GraphHopperDirectionsDto.serializer(), body)
        } catch (e: SerializationException) {
            return AppResult.Failure(AppError.Validation("GraphHopper response không đọc được: ${e.message}"))
        }
        return GraphHopperDirectionsMapper.toDirections(dto)
    }

    private fun buildUrl(from: GeoPoint, to: GeoPoint): String =
        baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("point", "${from.latitude},${from.longitude}")
            .addQueryParameter("point", "${to.latitude},${to.longitude}")
            .addQueryParameter("profile", PROFILE)
            .addQueryParameter("locale", "vi")
            .addQueryParameter("key", config.graphHopperApiKey)
            .build()
            .toString()

    private companion object {
        const val TAG = "FTD_EVENT"
        const val BASE_URL = "https://graphhopper.com/api/1/route"

        // Free tier chỉ cho đúng [car, bike, foot] — kiểm thật bằng key của dự án, 2026-08-24
        // (VERIFY-2026-08-24.md mục 6, phase-02 Key Insight #4). `motorcycle`/`scooter` trả 400.
        // Đổi sang `motorcycle` đòi NÂNG GÓI TRẢ PHÍ GraphHopper — không phải một dòng code.
        const val PROFILE = "car"
    }
}
