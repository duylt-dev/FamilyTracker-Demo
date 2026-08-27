package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.Zone

/**
 * Giữ bất biến ENTER/EXIT (`decisions.md` §C4) khi nguồn đường không còn là đường thẳng: một tuyến
 * vòng vèo có thể cắt ranh giới zone nhiều hơn một lần mỗi chặng, và mỗi lần cắt thêm là một
 * ENTER/EXIT thêm — dội. `isUsable` đếm số lần dãy điểm đổi dấu quanh hai đường tròn mốc (`d =
 * radius` và `d = radius + ZONE_EXIT_BUFFER_M`); quá một lần mỗi đường thì từ chối tuyến.
 *
 * **Chưa có người gọi thật trong phase-02** — [MemberRoamer] chỉ dùng [SyntheticPath] (đã tất định,
 * một cung lồi duy nhất, không tự dội), nên guard này được bài test của nó và của
 * `MemberRoamerTest` gọi trực tiếp để khoá hành vi trước khi phase-04 nối dây thật (khi
 * `RoutingProvider` có thể trả về một tuyến hình học xấu). Xem KDoc `Next Steps` của phase-02.
 *
 * **Phase-04: `public`, không còn `internal`.** Người gọi thật đầu tiên —
 * `data/routing/MemberRouteSource.kt` — sống ở MODULE `:data`, và `internal` của Kotlin là biên
 * theo MODULE Gradle, không phải theo file (LLM.md §13 Fixed #12, cùng bài học đã trả giá cho
 * `SyntheticPath`/`ParametrizedPath`). Giữ `internal` ở đây là lỗi biên dịch hiển nhiên ngay khi
 * `MemberRouteSource` gọi `isUsable(...)` — không phải một lựa chọn thiết kế.
 */
object RouteGeometryGuard {
    private const val MAX_ALLOWED_CROSSINGS: Int = 1

    fun isUsable(points: List<GeoPoint>, zone: Zone, kind: LegKind): Boolean {
        // Trước cả kiểm số điểm: một chặng WANDER không có ngữ nghĩa zone nào để vi phạm, kể cả khi
        // dãy điểm rỗng. Bỏ dòng này thì ca `WANDER legs are always usable` đỏ ngay.
        if (kind == LegKind.WANDER) return true
        if (points.size < 2) return false

        val distances = points.map { point ->
            GeoDistance.haversineMeters(point.latitude, point.longitude, zone.latitude, zone.longitude)
        }
        val entryCrossings = countCrossings(distances, zone.radiusMeters.toDouble())
        val exitCrossings = countCrossings(distances, zone.radiusMeters + TrackingConstants.ZONE_EXIT_BUFFER_M)
        // KHÔNG thừa so với `when` bên dưới, dù trông vậy: `when` chỉ ràng buộc đường tròn CÙNG
        // CHIỀU với chặng (ENTER kiểm `entryCrossings`, LEAVE kiểm `exitCrossings`). Dòng này bắt
        // ca chéo — một chặng ENTER cắt đúng một lần vòng bán kính nhưng quăng qua quăng lại vòng
        // `radius + ZONE_EXIT_BUFFER_M` — tức là đúng kiểu dội mà `decisions.md` §C4 tồn tại để
        // chặn. Chưa test nào phủ ca chéo đó (xem `reports/simplifier-phase-02-report.md`), nên
        // đừng "gọn hoá" dòng này: suite vẫn xanh khi xoá, mà bất biến thì thủng.
        if (entryCrossings > MAX_ALLOWED_CROSSINGS || exitCrossings > MAX_ALLOWED_CROSSINGS) return false

        return when (kind) {
            LegKind.ENTER_ZONE -> entryCrossings == 1 && distances.last() < zone.radiusMeters
            LegKind.LEAVE_ZONE ->
                exitCrossings == 1 && distances.last() > zone.radiusMeters + TrackingConstants.ZONE_EXIT_BUFFER_M
            LegKind.WANDER -> true // không tới được: đã trả về ở dòng đầu hàm. Có mặt cho `when` đủ nhánh.
        }
    }

    /**
     * Gốc tuyến còn khớp với vị trí HIỆN TẠI không — chặn lỗi P0 đo thật trên `emulator-5554`
     * 2026-08-26: tầng cache (`MemberRouteSource`, `:data`) trả một tuyến tính cho một `from` CŨ (vì
     * khoá cache cố ý không chứa `from` — `decisions.md` §C2 "Khoá cache"), và `MemberRoamer.withPath`
     * đặt `pathCursorMeters = 0.0` một cách vô điều kiện ⇒ thành viên bị dời thẳng về đỉnh đầu của
     * polyline đã cache. Đo được hai cú nhảy **346.14 m** và **872.99 m** (`reports/dev-phase-04-report.md`
     * §0). `isUsable` không bắt được ca này: nó chỉ xét hình học tương đối với ZONE, không biết gì về
     * `from` hiện tại của thành viên — đây là lý do cần một hàm RIÊNG, không gộp vào `isUsable`.
     *
     * [toleranceMeters] KHÔNG có mặc định ở đây, cố ý — một hằng số ẩn thứ hai trùng ý nghĩa với bước
     * đi mô phỏng là đúng thứ §13 Open #7 đã cảnh báo. Người gọi (`MemberRouteSource`) truyền
     * `MemberRoamer.STEP_METERS` (suy từ `TrackingConstants.SIM_MEMBER_SPEED_MPS ×
     * MEMBER_ROAM_INTERVAL_MS` = 20.75 m): một gốc tuyến lệch không quá MỘT bước đi là lệch nhỏ hơn
     * chính bước đi tiếp theo sẽ che nó — mắt người không phân biệt được với sai số làm tròn bình
     * thường của một bước, trong khi 346 m/873 m đo được ở trên gấp 16–42 lần ngưỡng này.
     */
    fun startsNear(points: List<GeoPoint>, from: GeoPoint, toleranceMeters: Double): Boolean {
        val first = points.firstOrNull() ?: return false
        return GeoDistance.haversineMeters(first.latitude, first.longitude, from.latitude, from.longitude) <= toleranceMeters
    }

    /** Số lần dấu của `(khoảng cách - boundary)` đổi giữa hai điểm liên tiếp — mỗi lần đổi dấu là
     * một lần dãy điểm cắt đường tròn bán kính [boundary]. */
    private fun countCrossings(distances: List<Double>, boundary: Double): Int {
        val outside = distances.map { it >= boundary }
        return outside.zipWithNext().count { (a, b) -> a != b }
    }
}
