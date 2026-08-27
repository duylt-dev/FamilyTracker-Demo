package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint

/**
 * Một dãy điểm đã được "đo" bằng khoảng cách cộng dồn dọc theo nó — phase-02, D5: cả ba tầng nguồn
 * đường (GraphHopper, cache, [SyntheticPath]) đều đi qua [parametrize] trước khi
 * [MemberRoamer] bám. `cumulativeMeters[i]` là khoảng cách từ `points[0]` tới `points[i]`;
 * `cumulativeMeters[0] == 0.0`.
 *
 * **Public, không `internal`** — bắt buộc, không phải lựa chọn: đây là kiểu của `RoamState.path`,
 * mà `RoamState` phải public vì `:data` giữ `Map<String, RoamState>`. Hạ xuống `internal` thì
 * compiler chặn ngay: *"'public' function exposes its 'internal' parameter type
 * 'ParametrizedPath'"* tại `MemberRoamerModel.kt` (đã thử thật, phase-02 simplifier). `internal`
 * của Kotlin là biên theo MODULE Gradle, không phải theo file — cùng bài học LLM.md §13 Fixed #12.
 */
class ParametrizedPath(
    val points: List<GeoPoint>,
    val cumulativeMeters: DoubleArray,
    val totalMeters: Double,
)

/**
 * Kết quả một lần [PolylineFollower.advance]. [cursorMeters] là vị trí MỚI dọc theo path — truyền
 * thẳng vào lần gọi `advance` kế tiếp. [movedMeters] nhỏ hơn `stepMeters` yêu cầu đúng khi bước bị
 * cắt ngắn ở một đỉnh (bảo toàn đỉnh, xem KDoc [PolylineFollower.advance]) hoặc khi đã hết đường.
 */
internal data class Progress(
    val point: GeoPoint,
    val cursorMeters: Double,
    val bearingDegrees: Double,
    val movedMeters: Double,
    val finished: Boolean,
)

/**
 * Bám một dãy điểm bằng khoảng cách cộng dồn — phase-02, thay cho nội suy đường thẳng cũ của
 * `MemberRoamer` (LLM.md §8.2, PRD delta D1/D2). Hàm thuần: không Android, không coroutine.
 */
internal object PolylineFollower {

    fun parametrize(points: List<GeoPoint>): ParametrizedPath {
        if (points.size < 2) return ParametrizedPath(points, DoubleArray(points.size), 0.0)
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + GeoDistance.haversineMeters(
                points[i - 1].latitude,
                points[i - 1].longitude,
                points[i].latitude,
                points[i].longitude,
            )
        }
        return ParametrizedPath(points, cumulative, cumulative.last())
    }

    /**
     * **Bảo toàn đỉnh CÓ ĐIỀU KIỆN — hợp đồng viết lại ở smooth-road plan phase-06
     * (`LLM.md` §13 Fixed #32, Open #17 cũ).** Đi trọn `stepMeters`; chỉ khi việc đó cắt góc quá
     * [MAX_CORNER_CUT_M] mới lùi về dừng ĐÚNG tại đỉnh sắp tới (`movedMeters < stepMeters`).
     *
     * **Vì sao luật cũ ("luôn dừng ở đỉnh đầu tiên") phải đổi.** Nó làm quãng đi mỗi tick bằng
     * `min(step, khoảng-cách-tới-đỉnh-kế)`, tức **tốc độ thật phụ thuộc mật độ đỉnh của polyline,
     * không phụ thuộc `SIM_MEMBER_SPEED_MPS`**. Đo thật trên máy (phase-03 `SM-A165F`, phase-04
     * `emulator-5554`, 2 463 mẫu): phân bố bước lưỡng-ba đỉnh — `~20.75 m` trên polyline
     * GraphHopper thưa đỉnh (đủ tốc độ), `~10.375 m` trên [SyntheticPath] (đỉnh đặt cách
     * `STEP_METERS / 2` ⇒ đúng NỬA tốc độ), `~5 m` trên polyline GraphHopper dày đỉnh. Hệ quả nặng
     * nhất không phải "đi chậm": một vòng ENTER→EXIT chạy qua nhiều tầng nguồn nên **không tái lập
     * được**, và luật chốt `SIM_MEMBER_SPEED_MPS` ở `decisions.md` §C5 không chốt được.
     *
     * **Vì sao KHÔNG đơn giản là "gộp mọi đỉnh trong một bước".** Với một mẫu mỗi tick, tốc độ đúng
     * và sai-lệch-bằng-0 **loại trừ nhau**: bước nuốt nhiều đỉnh thì hai mẫu liên tiếp không còn
     * nằm trên cùng một đoạn, nên nội suy tuyến tính ở phase-03 cắt góc. Bỏ hẳn bảo toàn đỉnh cho
     * sai lệch tới ~9.6 m ở khúc cua 135°, sát trần `TrackingConstants.SIM_ROAD_TOLERANCE_M`
     * (10 m) — `decisions.md` §C1, phase-02 Key Insight #1.
     *
     * Nên luật mới giữ từng thứ ở đúng chỗ nó quan trọng: đoạn thẳng dày đỉnh (chính là ca gây ra
     * lỗi nửa tốc độ) có sai lệch gần 0 nên đi đủ bước; khúc cua gắt vẫn dừng ở đỉnh và giữ sai
     * lệch bằng 0 cho bước đó. Cái giá phải nói thẳng: sai lệch không còn **bằng 0 tuyệt đối** như
     * phase-02 hứa, mà là **≤ [MAX_CORNER_CUT_M]** — vẫn dưới `SIM_ROAD_TOLERANCE_M` 5 lần.
     *
     * Không ném với path 0/1 điểm — trả `finished = true` ngay, không có gì để bám.
     */
    fun advance(path: ParametrizedPath, cursorMeters: Double, stepMeters: Double): Progress {
        if (path.points.isEmpty()) return Progress(GeoPoint(0.0, 0.0), 0.0, 0.0, 0.0, finished = true)
        if (path.points.size == 1) return Progress(path.points[0], 0.0, 0.0, 0.0, finished = true)

        val fromPoint = pointAt(path, cursorMeters)
        val targetCursor = cursorMeters + stepMeters

        if (targetCursor >= path.totalMeters) {
            val lastPoint = path.points.last()
            return Progress(
                point = lastPoint,
                cursorMeters = path.totalMeters,
                bearingDegrees = bearingBetween(fromPoint, lastPoint),
                movedMeters = (path.totalMeters - cursorMeters).coerceAtLeast(0.0),
                finished = true,
            )
        }

        // Đi TRỌN bước trước, chỉ lùi về "dừng ở đỉnh" khi việc đó thật sự cắt góc quá nhiều.
        val toPoint = pointAt(path, targetCursor)
        if (cornerCutMeters(path, cursorMeters, targetCursor, fromPoint, toPoint) <= MAX_CORNER_CUT_M) {
            return Progress(
                point = toPoint,
                cursorMeters = targetCursor,
                bearingDegrees = bearingBetween(fromPoint, toPoint),
                movedMeters = stepMeters,
                finished = false,
            )
        }

        // Khúc cua gắt: dừng ĐÚNG tại đỉnh sắp tới, sai lệch bằng 0 cho bước này.
        val nextVertexIndex = path.cumulativeMeters.indexOfFirst { it > cursorMeters }
        if (nextVertexIndex != -1 && path.cumulativeMeters[nextVertexIndex] <= targetCursor) {
            val vertex = path.points[nextVertexIndex]
            val vertexCursor = path.cumulativeMeters[nextVertexIndex]
            return Progress(
                point = vertex,
                cursorMeters = vertexCursor,
                bearingDegrees = bearingBetween(fromPoint, vertex),
                movedMeters = vertexCursor - cursorMeters,
                finished = false,
            )
        }

        return Progress(
            point = toPoint,
            cursorMeters = targetCursor,
            bearingDegrees = bearingBetween(fromPoint, toPoint),
            movedMeters = stepMeters,
            finished = false,
        )
    }

    /**
     * Sai lệch lớn nhất giữa ĐƯỜNG THẬT và dây cung `from→to` mà tầng hiển thị sẽ vẽ, nếu bước này
     * nuốt trọn các đỉnh nằm trong `(cursorMeters, targetCursor)`. `0.0` khi không có đỉnh nào chắn
     * giữa đường — tức bước nằm gọn trong một đoạn, không có gì để cắt.
     */
    private fun cornerCutMeters(
        path: ParametrizedPath,
        cursorMeters: Double,
        targetCursor: Double,
        from: GeoPoint,
        to: GeoPoint,
    ): Double {
        var worst = 0.0
        for (i in path.points.indices) {
            val vertexCursor = path.cumulativeMeters[i]
            if (vertexCursor <= cursorMeters) continue
            if (vertexCursor >= targetCursor) break
            val vertex = path.points[i]
            val deviation = GeoDistance.pointToSegmentMeters(
                vertex.latitude, vertex.longitude,
                from.latitude, from.longitude,
                to.latitude, to.longitude,
            )
            if (deviation > worst) worst = deviation
        }
        return worst
    }

    /** `0.0` khi hai điểm trùng nhau (đứng đúng tại điểm đầu path) — không có hướng nào để tính,
     * và [GeoBearing.initialBearing] trả một giá trị tuỳ ý (không NaN) trong ca đó nên phải chặn
     * tay để không phát ra một con số vô nghĩa cho marker xoay theo (phase-03). */
    private fun bearingBetween(from: GeoPoint, to: GeoPoint): Double {
        if (from.latitude == to.latitude && from.longitude == to.longitude) return 0.0
        return GeoBearing.initialBearing(from.latitude, from.longitude, to.latitude, to.longitude)
    }

    /**
     * Trần cắt góc: một phần năm `SIM_ROAD_TOLERANCE_M`. Suy ra chứ không viết số rời — nới dung
     * sai bám đường thì trần này nới theo, và không ai phải nhớ hai con số phải đi cùng nhau.
     * Một phần năm chứ không phải một nửa: dung sai 10 m là trần *tuyệt đối* của "còn bám đường",
     * còn đây là sai lệch xảy ra ở MỌI khúc cua vừa phải, nên phải còn xa trần thật.
     */
    private const val MAX_CORNER_CUT_M: Double = TrackingConstants.SIM_ROAD_TOLERANCE_M / 5.0

    /** Điểm nội suy tại đúng [meters] dọc theo path — dùng cho cả điểm xuất phát của một bước lẫn
     * điểm đích khi không có đỉnh nào chắn giữa đường. */
    private fun pointAt(path: ParametrizedPath, meters: Double): GeoPoint {
        val clamped = meters.coerceIn(0.0, path.totalMeters)
        val rawIndex = path.cumulativeMeters.indexOfFirst { it >= clamped }
        val segmentEndIndex = if (rawIndex <= 0) 1 else rawIndex
        val segmentStartIndex = segmentEndIndex - 1
        val segmentStart = path.cumulativeMeters[segmentStartIndex]
        val segmentEnd = path.cumulativeMeters[segmentEndIndex]
        val segmentLength = segmentEnd - segmentStart
        val ratio = if (segmentLength <= 0.0) 0.0 else (clamped - segmentStart) / segmentLength
        val a = path.points[segmentStartIndex]
        val b = path.points[segmentEndIndex]
        return GeoPoint(
            latitude = a.latitude + (b.latitude - a.latitude) * ratio,
            longitude = a.longitude + (b.longitude - a.longitude) * ratio,
        )
    }
}
