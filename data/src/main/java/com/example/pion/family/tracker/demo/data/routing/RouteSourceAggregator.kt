package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.domain.model.RouteSourceInfo
import com.example.pion.family.tracker.demo.domain.model.RouteSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Ghi công tổng hợp theo TỪNG THÀNH VIÊN (code review phase-04 "VIỆC B") — không phải
 * ghi-sau-thắng. `MemberRouteSource.observeSource()` trước bản sửa này là MỘT `RouteSourceInfo`
 * dùng chung cho mọi thành viên: Minh đang PROVIDER, Lan chuyển sang SYNTHETIC (hoặc ngược lại) thì
 * chỉ ai gọi `path()`/`wander()` SAU CÙNG mới thắng — sai với chính lập luận pháp lý cho phép tính
 * năng này tồn tại (`docs/routing-and-map-attribution.md` §3: "chỉ hiện credit OSM khi đang thật sự
 * hiển thị dữ liệu OSM"; PRD delta X5).
 *
 * [update] ghi trạng thái RIÊNG mỗi `memberId` rồi tính lại [current]: **còn ít nhất MỘT thành viên
 * đang ở tầng 1 (PROVIDER) hoặc tầng 2 (CACHE)** ⇒ phát attribution HỢP của những người đó (khử
 * trùng lặp, giữ thứ tự xuất hiện — `List<String>.distinct()`), `kind = PROVIDER` nếu có ít nhất
 * một người đang PROVIDER (mới hơn CACHE), ngược lại `CACHE`; **không ai còn ở tầng OSM** (mọi
 * người đều `SYNTHETIC` — lỗi, mất khoá, hoặc đang `WANDER`) ⇒ `SYNTHETIC`, attribution rỗng —
 * không hiện credit khi màn không thật sự có dữ liệu OSM nào để ghi công.
 *
 * Tách khỏi `MemberRouteSource` (giữ file đó dưới 200 dòng, `.claude/rules/development-rules.md`)
 * — một trách nhiệm độc lập ("gộp N trạng thái thành một"), không phụ thuộc HTTP/cache/guard.
 */
internal class RouteSourceAggregator {
    private val perMember = mutableMapOf<String, RouteSourceInfo>()

    /**
     * **P0 fix (chạy thật trên emulator, không phải test — FR-4/S4 phase-05):** `null` = CHƯA thành
     * viên nào từng gọi [update] (`path()`/`wander()` chưa xảy ra lần nào — màn vừa mở, chưa bật theo
     * dõi). Tách bạch với `SYNTHETIC` ("mọi người ĐANG chạy tầng 3"): khởi tạo
     * `MutableStateFlow(RouteSourceInfo(SYNTHETIC, emptyList()))` trộn hai trạng thái khác hẳn nhau,
     * làm `MemberRouteSource.observeSource()` phát nhãn ước tính NGAY LẬP TỨC dù chưa có tuyến nào
     * từng được yêu cầu — vỡ đúng luật "trạng thái thứ ba: ẩn hẳn, không vẽ `Text` nào".
     * `MapViewModelTest` (`:ui`) không bắt được vì fake repository ở đó không tự phát gì cho tới khi
     * test bơm vào — chỉ instance thật này mới có hành vi "có giá trị ngay từ đầu" của `MutableStateFlow`.
     */
    private val _current = MutableStateFlow<RouteSourceInfo?>(null)

    /** [filterNotNull] — không phát gì cho tới khi [update] được gọi lần đầu (FR-4). Từ đó trở đi,
     * hành vi gộp giữ NGUYÊN như trước: còn ai ở tầng OSM thì hợp attribution của người đó, không ai
     * còn thì `SYNTHETIC` rỗng — không đổi một luật pháp lý nào. */
    val current: Flow<RouteSourceInfo> = _current.filterNotNull()

    fun update(memberId: String, info: RouteSourceInfo) {
        perMember[memberId] = info
        val onOsmTier = perMember.values.filter { it.kind != RouteSourceKind.SYNTHETIC }
        _current.value = if (onOsmTier.isEmpty()) {
            RouteSourceInfo(RouteSourceKind.SYNTHETIC, emptyList())
        } else {
            val kind = if (onOsmTier.any { it.kind == RouteSourceKind.PROVIDER }) RouteSourceKind.PROVIDER else RouteSourceKind.CACHE
            RouteSourceInfo(kind, onOsmTier.flatMap { it.attribution }.distinct())
        }
    }
}
