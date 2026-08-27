package com.example.pion.family.tracker.demo.data.routing

import kotlinx.serialization.Serializable

/**
 * Hình dạng trên đĩa của [OnDevicePolylineCache] — `{routesDir}/{key}.json`. KHÔNG đặt ở
 * `:data/remote/dto/`: đó là DTO của MỘT lời gọi HTTP (GraphHopper/Valhalla response, LLM.md §12);
 * đây là định dạng LƯU TRỮ do chính lớp cache sở hữu, không tương ứng với response nào (phase-04
 * Related Code Files).
 *
 * [schemaVersion] khác [OnDevicePolylineCache.SCHEMA_VERSION] hiện tại → coi là miss, xoá file
 * (`decisions.md` §C2 "Khoá cache", researcher-01 Q5 — không cần migration).
 *
 * [points] là `List<List<Double>>` — mỗi phần tử `[lat, lng]` — chứ không phải `List<GeoPoint>`:
 * `GeoPoint` (`:domain/model/`) không đánh dấu `@Serializable`, và thêm annotation đó vào một model
 * dùng chung chỉ để phục vụ một định dạng lưu trữ riêng của `:data` là kéo một chi tiết lưu trữ
 * ngược lên `:domain`. [OnDevicePolylineCache] là nơi map hai chiều với `GeoPoint`.
 *
 * **Không có trường khoá API nào ở đây** (Security Considerations, phase-04) — chỉ hình học +
 * attribution + số phiên bản + engine.
 */
@Serializable
data class CachedRouteDto(
    val schemaVersion: Int,
    val engineId: String,
    val attribution: List<String>,
    val points: List<List<Double>>,
    val createdAtMs: Long,
)
