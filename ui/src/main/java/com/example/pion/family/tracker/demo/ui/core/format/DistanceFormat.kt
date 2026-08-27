package com.example.pion.family.tracker.demo.ui.core.format

import java.util.Locale

/**
 * Đơn vị mét/km cho toàn màn hình (PRD §9, phase-08 Requirements phi chức năng) — chỉ định dạng
 * hiển thị, KHÔNG tính lại quãng đường: số liệu gốc luôn đến từ `RouteStats` ở `:domain`
 * (phase-08 Implementation Step 7 "Composable không tự tính lại km"). Sống ở `ui/core/format/`,
 * không phải `:domain`, vì đây là trình bày (chuỗi hiển thị), không phải nghiệp vụ — LLM.md §12
 * "Một helper thuần → :domain nếu là nghiệp vụ, :ui/core/ nếu là trình bày".
 */
object DistanceFormat {
    /** < 1km hiện theo mét nguyên (`"850 m"`), từ 1km trở lên hiện 1 chữ số thập phân (`"3.2 km"`). */
    fun format(meters: Double): String = if (meters < 1_000.0) {
        "${meters.toInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", meters / 1_000.0)
    }
}
