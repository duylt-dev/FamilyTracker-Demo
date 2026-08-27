package com.example.pion.family.tracker.demo.domain.tracking

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bearing thật giữa hai điểm — phase-02 (PRD delta D5/D2, US-40). Hàm thuần, không import Android;
 * cùng luật `domain/tracking/` như [GeoDistance] (LLM.md §8.2). `internal`: chỉ
 * [PolylineFollower]/[MemberRoamer] trong cùng module cần nó.
 */
internal object GeoBearing {
    private const val FULL_CIRCLE_DEGREES: Double = 360.0
    private const val HALF_CIRCLE_DEGREES: Double = 180.0

    /**
     * Góc phương vị ban đầu (initial/forward bearing), độ, chuẩn hoá về `[0, 360)`. Công thức cầu
     * chuẩn (không phải xấp xỉ phẳng như [pointAtBearing] ở `MemberRoamerGeometry.kt`) vì đây là
     * giá trị người dùng NHÌN THẤY marker xoay theo (phase-03), không phải một phép tính nội bộ
     * chấp nhận sai số.
     */
    fun initialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearingDegrees = Math.toDegrees(atan2(y, x))
        return (bearingDegrees + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    }

    /**
     * Chênh lệch góc ngắn nhất để xoay từ [from] tới [to], độ, khoảng `(-180, 180]`. Đúng 180°
     * LUÔN trả `+180.0` — chọn một chiều cố định và khoá bằng test, chứ không để dấu rơi tự do
     * theo sai số dấu phẩy động: nếu không, marker sẽ quay ngẫu nhiên trái/phải mỗi lần gặp đúng
     * góc đối đỉnh, tuỳ may rủi của phép trừ.
     */
    fun shortestDelta(from: Double, to: Double): Double {
        val raw = (to - from) % FULL_CIRCLE_DEGREES
        val normalized = (raw + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES // [0, 360)
        return if (normalized > HALF_CIRCLE_DEGREES) normalized - FULL_CIRCLE_DEGREES else normalized
    }
}
