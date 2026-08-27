package com.example.pion.family.tracker.demo.domain.model

/**
 * Tầng nào cấp dãy điểm hiện tại cho một thành viên được mô phỏng — D5 (`decisions.md` §C2),
 * phase-04. `:data/routing/MemberRouteSource.kt` là nơi DUY NHẤT phát ra giá trị này, qua
 * [com.example.pion.family.tracker.demo.domain.repository.SimulatedRouteRepository].
 */
enum class RouteSourceKind { PROVIDER, CACHE, SYNTHETIC }

/**
 * [attribution] rỗng ở [RouteSourceKind.SYNTHETIC] — tầng 3 không chứa dữ liệu OSM, không có gì để
 * ghi công (`decisions.md` §C2, bảng 3 tầng). Ở [RouteSourceKind.PROVIDER]/[RouteSourceKind.CACHE],
 * đây LUÔN là [Directions.attribution] của lần fetch thành công gần nhất — không tự soạn lại
 * (`docs/routing-and-map-attribution.md` §3).
 */
data class RouteSourceInfo(
    val kind: RouteSourceKind,
    val attribution: List<String>,
)
