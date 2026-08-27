package com.example.pion.family.tracker.demo.domain.tracking

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Haversine great-circle distance, viết tay vì `android.location.Location.distanceBetween` nằm
 * ở `android.*` — `:domain` không có Android plugin nên gọi nó là lỗi biên dịch, đúng thiết kế
 * (LLM.md §8.2 điểm 4). `internal`: chỉ các thuật toán khác trong `domain/tracking/` cần nó.
 */
internal object GeoDistance {
    // Bán kính Trái Đất trung bình (IUGG) — phase-03 Implementation Step 2.
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val sinDeltaPhi = sin(deltaPhi / 2)
        val sinDeltaLambda = sin(deltaLambda / 2)
        val a = sinDeltaPhi * sinDeltaPhi + cos(phi1) * cos(phi2) * sinDeltaLambda * sinDeltaLambda
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_M * c
    }

    /**
     * Khoảng cách vuông góc từ một điểm tới ĐOẠN THẲNG `a→b` (không phải tới đường thẳng vô hạn:
     * điểm chiếu bị kẹp về hai đầu mút). Dùng để đo **cắt góc** — sai lệch giữa đường thật và dây
     * cung mà tầng hiển thị vẽ giữa hai mẫu liên tiếp ([PolylineFollower.advance], smooth-road plan
     * phase-06, `LLM.md` §13 Fixed #32).
     *
     * Chiếu phẳng cục bộ, KHÔNG haversine: ở quy mô một bước mô phỏng (~20 m) sai số của phép chiếu
     * là dưới một milimét, trong khi công thức điểm-tới-đoạn trên mặt cầu thì dài và không mua thêm
     * gì. `cos` lấy ở vĩ độ của [a] — chênh lệch vĩ độ trong một bước quá nhỏ để cần lấy trung bình.
     */
    fun pointToSegmentMeters(
        pointLat: Double,
        pointLng: Double,
        aLat: Double,
        aLng: Double,
        bLat: Double,
        bLng: Double,
    ): Double {
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians(aLat))
        val px = (pointLng - aLng) * metersPerDegreeLng
        val py = (pointLat - aLat) * METERS_PER_DEGREE_LAT
        val bx = (bLng - aLng) * metersPerDegreeLng
        val by = (bLat - aLat) * METERS_PER_DEGREE_LAT

        val segmentLengthSquared = bx * bx + by * by
        // a và b trùng nhau ⇒ "đoạn" suy biến thành một điểm, trả khoảng cách tới chính nó.
        if (segmentLengthSquared <= 0.0) return sqrt(px * px + py * py)

        val t = ((px * bx + py * by) / segmentLengthSquared).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return sqrt(dx * dx + dy * dy)
    }

    /** 1 độ vĩ ≈ 111 320 m. Hằng số này cũng có ở `SyntheticPath`; giữ bản riêng ở đây thay vì mở
     * `SyntheticPath` ra là có chủ ý — hai nơi dùng nó cho hai việc khác nhau và không phụ thuộc nhau. */
    private const val METERS_PER_DEGREE_LAT = 111_320.0
}
