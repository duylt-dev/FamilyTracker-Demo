package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

private const val METERS_PER_DEGREE_LAT = 111_320.0

/** Success Criteria S1 (phase-02): "chạy 200 nhịp trên polyline ≥ 3 đoạn". */
private const val TICKS_FOR_S1 = 200

/**
 * Phase-02 Implementation Step 2. Test trung tâm của cả phase là bảo toàn đỉnh
 * (`a step that would overshoot a vertex …`) — xem KDoc [PolylineFollower.advance]. Step 10 của
 * dev report mutation ngay trên chính hành vi này.
 */
class PolylineFollowerTest {

    /** Gấp khúc 90°: (0,0) -> (100m bắc, 0) -> (100m bắc, 100m đông). Đoạn dài/ngắn xen kẽ dùng ở
     * test tổng quãng đường bên dưới bằng cách thay đổi `stepMeters`. */
    private val bentPath = PolylineFollower.parametrize(
        listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(northOf(100.0), 0.0),
            GeoPoint(northOf(100.0), eastOf(100.0)),
        ),
    )

    /**
     * F-9 (reviewer phase-02): S1 đòi "200 nhịp, polyline ≥ 3 đoạn"; bản cũ chạy 20 nhịp trên
     * [bentPath] (2 đoạn) — đúng bản chất (bảo toàn đỉnh) nhưng lệch hình thức. Đường ở đây dài
     * ~6230m (hai góc rẽ 15m gần xích đạo + một đoạn thẳng 6200m) để 200 nhịp × 30m/nhịp = 6000m
     * luôn CHƯA hết đường — nếu không, vòng lặp dừng sớm và không thực sự chạy đủ 200 nhịp như S1
     * đòi (khoá lại bằng assertion `tick == TICKS_FOR_S1` bên dưới). Góc rẽ giữ NHỎ (15m, không
     * phải 100m như [bentPath]) và ở gần xích đạo để sai số xấp xỉ phẳng của
     * [distanceToNearestSegment] (không có hệ số `cos(lat)`, xem KDoc [eastOf]) không tích luỹ theo
     * chiều dài đoạn thẳng 6200m — góc rẽ lớn hơn ở cuối một đoạn dài cỡ này sẽ đẩy sai số lên gần
     * ngưỡng `1e-6 m` của chính assertion đang kiểm.
     *
     * F-7 (reviewer phase-02): bản cũ dùng `repeat(20) { ... if (progress.finished) return@repeat }`
     * — `return@repeat` là `continue` (bỏ qua PHẦN CÒN LẠI của một lần lặp), không phải `break`
     * (thoát hẳn vòng lặp), vì `repeat` là một `inline fun` và nhãn mặc định của lambda truyền vào
     * nó chính là `repeat`. Hậu quả: sau khi path `finished`, các lần lặp còn lại vẫn gọi lại
     * `advance()` trên một path đã hết — vô tình phủ thêm một ca khác (`advancing again after the
     * path already finished…`, tách riêng ở dưới) thay vì dừng như tên đọc lên tưởng vậy. Ở đây
     * dùng `while` + `break` thật để ý định khớp với đọc.
     */
    @Test
    fun `every sample lands exactly on the polyline, even across a corner`() {
        val path = PolylineFollower.parametrize(
            listOf(
                GeoPoint(0.0, 0.0),
                GeoPoint(northOf(15.0), 0.0),
                GeoPoint(northOf(15.0), eastOf(6_200.0)),
                GeoPoint(northOf(30.0), eastOf(6_200.0)),
            ),
        )
        var cursor = 0.0
        val stepMeters = 30.0
        val samples = mutableListOf<GeoPoint>()
        val cursors = mutableListOf(0.0)
        var tick = 0
        while (tick < TICKS_FOR_S1) {
            val progress = PolylineFollower.advance(path, cursor, stepMeters)
            samples += progress.point
            cursor = progress.cursorMeters
            cursors += cursor
            tick++
            if (progress.finished) break
        }
        assertEquals("đường test phải đủ dài để thật sự chạy hết 200 nhịp, không hết giữa chừng", TICKS_FOR_S1, tick)
        samples.forEach { point ->
            assertTrue(
                "điểm $point phải cách một trong các đoạn của polyline < 1e-6 m",
                distanceToNearestSegment(path, point) < 1e-6,
            )
        }
        // Bất biến THẬT sau khi hợp đồng đổi ở phase-06 (§13 Fixed #32): dây cung giữa hai mẫu
        // liên tiếp — thứ tầng hiển thị vẽ ra — không được lệch khỏi đường thật quá trần cắt góc.
        // Bản cũ khẳng định "không đỉnh nào bị nhảy qua", tức sai lệch BẰNG 0; luật đó làm tốc độ
        // phụ thuộc mật độ đỉnh nên đã bị thay. Ca này giữ nguyên fixture, chỉ đổi thứ được khẳng
        // định — và đó mới là thứ phase-03 thật sự cần.
        val budgetMeters = 2.0 // = SIM_ROAD_TOLERANCE_M / 5; viết rời có chủ ý để đổi hằng số là ĐỎ
        // `cursors` mở đầu bằng 0.0 nên cặp (cursors[i], cursors[i+1]) ứng với dây cung đi TỚI
        // samples[i]; điểm xuất phát của dây cung đầu tiên là đỉnh đầu của path.
        cursors.zipWithNext().forEachIndexed { i, (startCursor, endCursor) ->
            val from = if (i == 0) path.points.first() else samples[i - 1]
            val to = samples[i]
            val worst = path.points.indices
                .filter { v -> path.cumulativeMeters[v] > startCursor + 1e-9 && path.cumulativeMeters[v] < endCursor - 1e-9 }
                .maxOfOrNull { v ->
                    GeoDistance.pointToSegmentMeters(
                        path.points[v].latitude, path.points[v].longitude,
                        from.latitude, from.longitude,
                        to.latitude, to.longitude,
                    )
                } ?: 0.0
            assertTrue("dây cung tới mẫu #$i cắt góc $worst m, vượt trần $budgetMeters m", worst <= budgetMeters)
        }
    }

    /**
     * **Ca gây ra `LLM.md` §13 Fixed #32 (Open #17 cũ), chưa từng có test nào chạm tới.** Đường dày đỉnh nhưng gần
     * thẳng — đúng hình dạng của [SyntheticPath] (đỉnh cách `STEP_METERS / 2`) và của polyline
     * GraphHopper trong phố (~5 m). Luật cũ "luôn dừng ở đỉnh đầu tiên" làm mỗi nhịp chỉ đi được
     * khoảng cách tới đỉnh kế, tức tốc độ thật bằng một nửa (hoặc một phần tư) tốc độ khai báo, và
     * làm một vòng ENTER→EXIT không tái lập được vì nó phụ thuộc tầng nguồn đang chạy.
     *
     * Đo trên máy thật đã xác nhận: `speedMps` ghi xuống `location_points` là 4.08–4.21 trong khi
     * `SIM_MEMBER_SPEED_MPS = 8.3`.
     */
    @Test
    fun `a dense but nearly straight path no longer halves the step`() {
        val vertexSpacingM = 10.375 // = MemberRoamer.STEP_METERS / 2, đúng cách SyntheticPath đặt đỉnh
        val vertexCount = 40
        // Cong nhẹ: lệch ngang tối đa 1 m trên toàn tuyến ~415 m — dưới trần cắt góc rất xa.
        val points = (0..vertexCount).map { i ->
            val along = i * vertexSpacingM
            val bow = 1.0 * kotlin.math.sin(Math.PI * i / vertexCount)
            GeoPoint(northOf(bow), eastOf(along))
        }
        val path = PolylineFollower.parametrize(points)

        val stepMeters = 20.75 // MemberRoamer.STEP_METERS
        var cursor = 0.0
        val moved = mutableListOf<Double>()
        repeat(10) {
            val progress = PolylineFollower.advance(path, cursor, stepMeters)
            moved += progress.movedMeters
            cursor = progress.cursorMeters
        }

        moved.forEach { m ->
            assertEquals("đường gần thẳng phải đi ĐỦ bước, không bị đỉnh chặn lại", stepMeters, m, 1e-6)
        }
    }

    @Test
    fun `a step that would overshoot a vertex stops exactly at the vertex, shortened`() {
        // Đứng cách đỉnh đầu tiên đúng 10m (đỉnh thật đo bằng chính GeoDistance của parametrize,
        // không giả định 100m tròn — Haversine thật và xấp xỉ mét-trên-độ dùng để dựng điểm test
        // lệch nhau ~0.1%, đủ để 1e-9 sai nếu so với một hằng số đoán trước).
        val vertexCursor = bentPath.cumulativeMeters[1]
        val progress = PolylineFollower.advance(bentPath, cursorMeters = vertexCursor - 10.0, stepMeters = 30.0)

        assertEquals("bước phải bị cắt ngắn, không đi đủ 30m", 10.0, progress.movedMeters, 1e-6)
        assertEquals(northOf(100.0), progress.point.latitude, 1e-9)
        assertEquals(0.0, progress.point.longitude, 1e-9)
        assertEquals(vertexCursor, progress.cursorMeters, 1e-9)
        assertTrue("chưa hết đường, chỉ mới qua đỉnh đầu", !progress.finished)
    }

    @Test
    fun `a step that lands with no vertex in range travels the full step`() {
        val progress = PolylineFollower.advance(bentPath, cursorMeters = 0.0, stepMeters = 50.0)

        assertEquals(50.0, progress.movedMeters, 1e-9)
        assertEquals(50.0, progress.cursorMeters, 1e-6)
        assertTrue(!progress.finished)
    }

    @Test
    fun `a step past the total length finishes at the last point`() {
        val progress = PolylineFollower.advance(bentPath, cursorMeters = 180.0, stepMeters = 50.0)

        assertTrue(progress.finished)
        assertEquals(northOf(100.0), progress.point.latitude, 1e-9)
        assertEquals(eastOf(100.0), progress.point.longitude, 1e-9)
        assertEquals(bentPath.totalMeters, progress.cursorMeters, 1e-9)
    }

    /**
     * F-7 (reviewer phase-02): tách ca "gọi advance() trên path đã finished" thành một ca riêng có
     * tên — trước đây nó chỉ là tác dụng phụ tình cờ của lỗi `return@repeat`/`continue` ở ca S1
     * ("every sample lands…"), không phải một ca ai chủ đích viết ra để kiểm.
     */
    @Test
    fun `advancing again after the path already finished stays put and keeps reporting finished`() {
        val firstFinish = PolylineFollower.advance(bentPath, cursorMeters = 180.0, stepMeters = 50.0)
        assertTrue("chuẩn bị dữ liệu sai: bước đầu phải đã hết đường", firstFinish.finished)

        val advancedAgain = PolylineFollower.advance(bentPath, cursorMeters = firstFinish.cursorMeters, stepMeters = 30.0)

        assertTrue("gọi advance() trên path đã hết phải tiếp tục báo finished", advancedAgain.finished)
        assertEquals("không được di chuyển thêm khi đã hết đường", 0.0, advancedAgain.movedMeters, 1e-9)
        assertEquals(bentPath.totalMeters, advancedAgain.cursorMeters, 1e-9)
        assertEquals(bentPath.points.last().latitude, advancedAgain.point.latitude, 1e-9)
        assertEquals(bentPath.points.last().longitude, advancedAgain.point.longitude, 1e-9)
    }

    @Test
    fun `a single-point path never throws and finishes immediately`() {
        val single = PolylineFollower.parametrize(listOf(GeoPoint(10.0, 106.0)))
        val progress = PolylineFollower.advance(single, cursorMeters = 0.0, stepMeters = 20.0)
        assertTrue(progress.finished)
    }

    @Test
    fun `an empty path never throws and finishes immediately`() {
        val empty = PolylineFollower.parametrize(emptyList())
        val progress = PolylineFollower.advance(empty, cursorMeters = 0.0, stepMeters = 20.0)
        assertTrue(progress.finished)
    }

    // Fixture nằm ở vĩ độ 0, nơi mét/độ KINH bằng đúng mét/độ VĨ — nên hai hàm trùng công thức mà
    // vẫn đúng cả hai trục. Giữ hai tên để đọc ra trục nào đang bị dịch; ở vĩ độ khác 0 thì
    // `eastOf` phải chia thêm cho `cos(lat)`.
    private fun northOf(meters: Double) = meters / METERS_PER_DEGREE_LAT
    private fun eastOf(meters: Double) = meters / METERS_PER_DEGREE_LAT

    private fun distanceToNearestSegment(path: ParametrizedPath, point: GeoPoint): Double {
        val segments = path.points.zipWithNext()
        return segments.minOf { (a, b) -> distanceToSegment(point, a, b) }
    }

    /** Xấp xỉ phẳng (mét trên độ) — đủ chính xác ở quy mô test này (< 200m). */
    private fun distanceToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val ax = a.longitude * METERS_PER_DEGREE_LAT
        val ay = a.latitude * METERS_PER_DEGREE_LAT
        val bx = b.longitude * METERS_PER_DEGREE_LAT
        val by = b.latitude * METERS_PER_DEGREE_LAT
        val px = p.longitude * METERS_PER_DEGREE_LAT
        val py = p.latitude * METERS_PER_DEGREE_LAT

        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared <= 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val projX = ax + dx * t
        val projY = ay + dy * t
        val ddx = px - projX
        val ddy = py - projY
        return sqrt(ddx * ddx + ddy * ddy)
    }
}
