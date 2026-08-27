package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Một tuyến đã cache — hình học + attribution + engine, đọc lại từ đĩa. Không phải
 * `Directions` (`:domain/model/`): đây là kiểu NỘI BỘ của `:data`, cache không cần
 * `distanceMeters`/`durationSeconds` mà `MemberRouteSource` không dùng tới.
 */
data class CachedRoute(
    val points: List<GeoPoint>,
    val attribution: List<String>,
    val engineId: String,
)

/**
 * Đọc/ghi `{routesDir}/{key}.json` — tầng 2 của D5 (`decisions.md` §C2), kiểm TRƯỚC provider mạng
 * (FR-2 "lần sau đọc cache, không gọi mạng"). Nhận thẳng một [routesDir]
 * (`java.io.File`), KHÔNG nhận `Context`: lớp này phải chạy được trên JVM thuần
 * (`OnDevicePolylineCacheTest`, LLM.md §11 — không Robolectric, `Context.filesDir` là stub ném
 * `not mocked` trên classpath unit test). `DataModule.kt` tính `File(androidContext().filesDir,
 * "routes")` một lần lúc đăng ký Koin và truyền vào đây — cùng kỹ thuật "tách tham số Android ra
 * khỏi constructor để giữ lớp nghiệp vụ JVM thuần" mà `LocationPointProcessor` dùng (LLM.md §8.3).
 *
 * Mọi thao tác file bọc `runCatching` + `withContext(Dispatchers.IO)` (phase-04 NFR-4, Security
 * Considerations) — cache hỏng KHÔNG BAO GIỜ được làm chết chuyển động; hỏng (JSON sai,
 * `schemaVersion` cũ, quyền đọc bị thu hồi, …) thì xoá file và trả `null`, coi như miss.
 *
 * **Không log gì cả** (gate G7, không toạ độ) — `MemberRouteSource` là nơi log
 * `sim_route_loaded`/`sim_route_failed`, không phải lớp này.
 */
class OnDevicePolylineCache(
    private val routesDir: File,
    private val json: Json,
) {

    suspend fun get(key: String): CachedRoute? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        runCatching {
            if (!file.exists()) return@runCatching null
            val dto = json.decodeFromString(CachedRouteDto.serializer(), file.readText())
            if (dto.schemaVersion != SCHEMA_VERSION) {
                file.delete()
                return@runCatching null
            }
            // review phase-05 L-2 — `attribution` rỗng là file HỎNG, không phải một tuyến hợp lệ:
            // `MemberRouteSource` gọi `put()` ở ĐÚNG MỘT chỗ, nhánh provider, luôn kèm
            // `directions.attribution`; tầng `SyntheticPath` (attribution rỗng) không bao giờ được
            // cache. Cái giá nếu tin file rỗng: `RoutingAttribution` rơi vào `else -> null` — không
            // credit OSM mà cũng không nhãn ước tính — nên màn hiển thị hình học OSM KHÔNG ghi công
            // gì cả. Đó là chiều THIẾU ghi công, vi phạm ODbL (`routing-and-map-attribution.md` §3
            // ràng buộc 1). Coi như miss thì tầng provider fetch lại và ghi đè bằng bản có credit.
            if (dto.attribution.isEmpty()) {
                file.delete()
                return@runCatching null
            }
            CachedRoute(
                points = dto.points.map { GeoPoint(latitude = it[0], longitude = it[1]) },
                attribution = dto.attribution,
                engineId = dto.engineId,
            )
        }.getOrElse {
            // Bất kỳ lỗi nào (JSON hỏng, quyền đọc, `it[0]`/`it[1]` vượt biên) đều coi như miss —
            // xoá file rác để lần sau không thử đọc lại đúng cái hỏng đó.
            runCatching { file.delete() }
            null
        }
    }

    suspend fun put(key: String, points: List<GeoPoint>, attribution: List<String>, engineId: String) {
        withContext(Dispatchers.IO) {
            // Ghi thất bại (đĩa đầy, quyền bị thu hồi) không được làm chết chuyển động — im lặng bỏ
            // qua, lần sau lại thử fetch tầng 1 (provider mạng).
            runCatching {
                val dto = CachedRouteDto(
                    schemaVersion = SCHEMA_VERSION,
                    engineId = engineId,
                    attribution = attribution,
                    points = points.map { listOf(it.latitude, it.longitude) },
                    createdAtMs = System.currentTimeMillis(),
                )
                routesDir.mkdirs()
                fileFor(key).writeText(json.encodeToString(CachedRouteDto.serializer(), dto))
            }
        }
    }

    private fun fileFor(key: String): File = File(routesDir, "$key.json")

    companion object {
        /** Đổi khi hình dạng [CachedRouteDto] đổi — khác giá trị này thì coi là miss, không migrate
         * (researcher-01 Q5, `decisions.md` §C2 "Khoá cache"). */
        const val SCHEMA_VERSION: Int = 1
    }
}
