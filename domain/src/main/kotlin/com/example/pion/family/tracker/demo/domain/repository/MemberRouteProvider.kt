package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.tracking.LegKind

/**
 * Cổng `:data`-nội-bộ mà `MemberMovementSimulator` (`:data/location/`) thấy — tồn tại CHỈ để
 * `MemberMovementSimulatorTest` là JUnit thuần với một fake viết tay, không cần khởi động
 * `MemberRouteSource` thật (HTTP + file I/O). Implement thật duy nhất là `MemberRouteSource`
 * (`:data/routing/`), 3 tầng đánh số theo `decisions.md` §C2 (tầng 1 = `RoutingProvider` mạng, tầng
 * 2 = cache trên máy, tầng 3 = `SyntheticPath`, `:domain/tracking/`) — KIỂM theo thứ tự ngược lại,
 * cache trước provider sau (FR-2 "lần sau đọc cache, không gọi mạng"; NFR-2 hạn ngạch).
 * Implementation KHÔNG BAO GIỜ ném lỗi mạng ra ngoài: mọi thất bại được hấp thụ nội bộ và rơi xuống
 * tầng dưới (FR-3).
 */
interface MemberRouteProvider {
    /** Chỉ gọi cho chặng `ENTER_ZONE`/`LEAVE_ZONE` — có zone, có thể tận dụng cache/provider. */
    suspend fun path(request: MemberRouteRequest): List<GeoPoint>

    /**
     * Chặng `WANDER` — không có zone, KHÔNG BAO GIỜ chạm cache lẫn provider (nửa còn lại luật hạn
     * ngạch NFR-2, Step 6): implementation luôn trả `SyntheticPath`. Tách khỏi [path] (không nhận
     * [MemberRouteRequest], không có zone/kind để đưa vào) nhưng VẪN cập nhật nguồn hiện tại của
     * [memberId] thành SYNTHETIC — thiếu bước này, `observeSource()` (`SimulatedRouteRepository`)
     * sẽ tiếp tục báo PROVIDER/CACHE cho một thành viên đang thật ra đi lung tung không theo zone
     * nào (code review phase-04 "VIỆC B").
     */
    suspend fun wander(memberId: String, from: GeoPoint, to: GeoPoint): List<GeoPoint>
}

/**
 * [zone] là zone LIÊN QUAN của chặng — đích cho `ENTER_ZONE`, zone VỪA RỜI cho `LEAVE_ZONE` (cùng
 * ngữ nghĩa với `RoamTarget.zoneId`). Bắt buộc phải có (không nullable): chặng `WANDER` — trường
 * hợp duy nhất không có zone — không bao giờ tạo ra một [MemberRouteRequest] (xem KDoc
 * [MemberRouteProvider]). Dùng cả để tra khoá cache (`decisions.md` §C2 "Khoá cache") lẫn để gọi
 * `RouteGeometryGuard.isUsable(points, zone, kind)`.
 */
data class MemberRouteRequest(
    val memberId: String,
    val from: GeoPoint,
    val to: GeoPoint,
    val zone: Zone,
    val kind: LegKind,
)
