package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Tầng 3 của D5 (`decisions.md` §C2) — polyline TỰ SINH, tất định, KHÔNG chứa dữ liệu OSM. Dùng
 * khi không có khoá API, nhà cung cấp lỗi, hoặc [RouteGeometryGuard] từ chối một tuyến thật (chưa
 * nối dây tới phase-04). Một cung cong nhẹ giữa hai đầu chặng dựng bằng lượng giác thuần — không
 * phải GIS thật, chỉ đủ để marker không đi xuyên nhà theo đường thẳng tuyệt đối. US-41 KHÔNG đạt ở
 * tầng này, biết trước và chấp nhận (`decisions.md` §C2).
 *
 * `seed` phải đến từ `member.id.hashCode()`, KHÔNG từ `Random.Default` (Security Considerations,
 * phase-02): không có nhu cầu ngẫu nhiên chất lượng mật mã ở đây, và tất định là yêu cầu chức năng
 * — cùng seed phải luôn ra cùng một cung để test tái hiện được.
 *
 * **Public, không `internal`** — khác các file khác của phase này (`GeoBearing`, `PolylineFollower`,
 * `RouteGeometryGuard`): đây là hàm DUY NHẤT của phase-02 mà `:data/location/MemberMovementSimulator`
 * (module khác) gọi trực tiếp (`pathFor()`, kiến trúc phase file). `internal` là biên theo MODULE
 * Gradle (LLM.md §13 Fixed #12), không phải theo file, nên giữ `internal` ở đây sẽ vỡ build `:data`.
 */
object SyntheticPath {
    private const val MIN_AMPLITUDE_FACTOR: Double = 0.05
    private const val MAX_AMPLITUDE_FACTOR: Double = 0.15
    private const val MAX_AMPLITUDE_M: Double = 120.0
    private const val METERS_PER_DEGREE_LAT: Double = 111_320.0
    private const val MIN_DISTANCE_M: Double = 0.01

    /**
     * Biên độ cung `min(factor × d, [MAX_AMPLITUDE_M])`, `factor` trong
     * `[[MIN_AMPLITUDE_FACTOR], [MAX_AMPLITUDE_FACTOR]]`; dấu (trái/phải) — cả hai suy từ [seed]
     * qua [Random], không phải [Random.Default]. Khoảng cách hai đỉnh liên tiếp xấp xỉ
     * `[MemberRoamer.STEP_METERS] / 2` (mẫu dày hơn bước đi một nửa để [PolylineFollower] luôn có
     * đỉnh để bảo toàn giữa hai lần bám).
     */
    fun between(from: GeoPoint, to: GeoPoint, seed: Int): List<GeoPoint> {
        val distance = GeoDistance.haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude)
        if (distance < MIN_DISTANCE_M) return listOf(from, to)

        val random = Random(seed)
        val amplitudeFactor = MIN_AMPLITUDE_FACTOR + random.nextDouble() * (MAX_AMPLITUDE_FACTOR - MIN_AMPLITUDE_FACTOR)
        val amplitudeMeters = min(amplitudeFactor * distance, MAX_AMPLITUDE_M)
        val sign = if (random.nextBoolean()) 1.0 else -1.0

        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians((from.latitude + to.latitude) / 2.0))
        val (perpLatUnit, perpLngUnit) = perpendicularUnitVector(from, to, metersPerDegreeLng)
        val vertexCount = maxOf(2, ceil(distance / (MemberRoamer.STEP_METERS / 2.0)).toInt())

        return (0..vertexCount).map { index ->
            val t = index.toDouble() / vertexCount
            val offsetMeters = sign * amplitudeMeters * sin(PI * t)
            GeoPoint(
                latitude = from.latitude + (to.latitude - from.latitude) * t + offsetMeters * perpLatUnit / METERS_PER_DEGREE_LAT,
                longitude = from.longitude + (to.longitude - from.longitude) * t + offsetMeters * perpLngUnit / metersPerDegreeLng,
            )
        }
    }

    /** Vector đơn vị vuông góc với hướng `from -> to`, trong không gian mét (không phải độ) — cùng
     * kỹ thuật xấp xỉ phẳng với [RouteBlueprint.directionUnitVector], xoay 90°. */
    private fun perpendicularUnitVector(from: GeoPoint, to: GeoPoint, metersPerDegreeLng: Double): Pair<Double, Double> {
        val dyMeters = (to.latitude - from.latitude) * METERS_PER_DEGREE_LAT
        val dxMeters = (to.longitude - from.longitude) * metersPerDegreeLng
        val norm = sqrt(dyMeters * dyMeters + dxMeters * dxMeters).coerceAtLeast(MIN_DISTANCE_M)
        // Xoay (dy,dx) 90°: (-dx, dy) — vuông góc với hướng đi ban đầu.
        return (-dxMeters / norm) to (dyMeters / norm)
    }
}
