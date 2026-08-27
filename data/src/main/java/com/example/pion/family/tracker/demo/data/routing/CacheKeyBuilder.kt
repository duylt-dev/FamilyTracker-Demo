package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.domain.repository.MemberRouteRequest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * `"{memberId}_{zoneId}_{kind}_{lat5}_{lng5}_{r}"` (`decisions.md` §C2 "Khoá cache") — `lat5`/
 * `lng5` là tâm zone làm tròn 5 chữ số thập phân (~1.1m), `r` là bán kính làm tròn về mét. Sửa
 * zone ⇒ khoá đổi ⇒ tự miss, không cần cơ chế vô hiệu hoá riêng (researcher-01 Q3). `Locale.ROOT`
 * bắt buộc: `String.format` mặc định đọc locale của máy, và một số locale dùng dấu phẩy làm dấu
 * thập phân — khoá cache phải ổn định bất kể locale thiết bị.
 *
 * Tách khỏi `MemberRouteSource` (code review phase-04 "VIỆC 4" — giữ file đó dưới 200 dòng,
 * `.claude/rules/development-rules.md`) — hàm THUẦN, không đọc/ghi trạng thái hay I/O của lớp đó,
 * cùng mẫu `RoutingErrorSanitizer.kt`/`RouteSourceAggregator.kt`.
 */
internal fun cacheKeyFor(request: MemberRouteRequest): String {
    val zone = request.zone
    val lat5 = String.format(Locale.ROOT, "%.5f", zone.latitude)
    val lng5 = String.format(Locale.ROOT, "%.5f", zone.longitude)
    return "${request.memberId}_${zone.id}_${request.kind}_${lat5}_${lng5}_${zone.radiusMeters.roundToInt()}"
}
