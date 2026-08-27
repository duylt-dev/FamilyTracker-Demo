package com.example.pion.family.tracker.demo.ui.core.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Định dạng thời lượng một chuyến (`RouteStats.durationMs`) — xem KDoc [DistanceFormat] cho lý do
 * sống ở `ui/core/format/` thay vì `:domain`.
 */
object DurationFormat {
    private val CLOCK_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /** < 1 phút hiện `"0 phút"` (chuyến 1 điểm, `RouteStats` đã chặn chia-cho-0 ở tầng domain —
     * đây chỉ là hiển thị). ≥ 60 phút hiện dạng `"1h 05p"`. */
    fun format(durationMs: Long): String {
        val totalMinutes = durationMs / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) String.format(Locale.US, "%dh %02dp", hours, minutes) else "$minutes phút"
    }

    /** `HH:mm` ngắn cho danh sách chuyến (US-30: "07:12 → 07:40"). */
    fun formatClock(instant: Instant): String = CLOCK_FORMATTER.format(instant)
}
