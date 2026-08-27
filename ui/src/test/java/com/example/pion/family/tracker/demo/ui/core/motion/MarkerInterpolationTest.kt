package com.example.pion.family.tracker.demo.ui.core.motion

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit thuần cho `MarkerInterpolation.kt` — không Compose, không Android, không Robolectric.
 * Ca bắt buộc theo phase-03 Implementation Step 1
 * (`plans/260825-0956-smooth-road-following-member-movement/phase-03-noi-suy-marker-o-tang-hien-thi.md`).
 */
class MarkerInterpolationTest {

    // --- lerpBearing: đi đường ngắn qua mốc 0°/360° (FR-3, QA-SRM-07, S1) ---

    @Test
    fun `lerpBearing from 350 to 10 goes forward through 0, not backward through 340`() {
        assertEquals(0f, lerpBearing(350f, 10f, 0.5f), 0f)
    }

    @Test
    fun `lerpBearing from 10 to 350 goes backward through 0, not forward through 340`() {
        assertEquals(0f, lerpBearing(10f, 350f, 0.5f), 0f)
    }

    @Test
    fun `lerpBearing at exact antipode 0 to 180 is locked to the positive direction`() {
        // Ở đúng 180°, hai chiều dài bằng nhau — khoá chiều dương (đi qua 90°) để marker không
        // quay ngẫu nhiên hai chiều khác nhau mỗi lần chạy (xem KDoc lerpBearing).
        assertEquals(90f, lerpBearing(0f, 180f, 0.5f), 0f)
    }

    @Test
    fun `lerpBearing at progress 0 returns from unchanged`() {
        assertEquals(350f, lerpBearing(350f, 10f, 0f), 0f)
    }

    @Test
    fun `lerpBearing at progress 1 returns to unchanged`() {
        assertEquals(10f, lerpBearing(350f, 10f, 1f), 0f)
    }

    // --- progressOf: chặn cứng [0,1], không chia cho 0 (US-44, QA-SRM-22) ---

    @Test
    fun `progressOf clamps to 1f when durationMs is zero`() {
        assertEquals(1f, progressOf(elapsedMs = 500L, durationMs = 0L), 0f)
    }

    @Test
    fun `progressOf clamps to 1f when durationMs is negative`() {
        assertEquals(1f, progressOf(elapsedMs = 500L, durationMs = -10L), 0f)
    }

    @Test
    fun `progressOf clamps to 0f when elapsed has not started`() {
        assertEquals(0f, progressOf(elapsedMs = 0L, durationMs = 2_500L), 0f)
        assertEquals(0f, progressOf(elapsedMs = -5L, durationMs = 2_500L), 0f)
    }

    @Test
    fun `progressOf clamps to 1f and never extrapolates past the last real sample`() {
        assertEquals(1f, progressOf(elapsedMs = 2_500L, durationMs = 2_500L), 0f)
        assertEquals(1f, progressOf(elapsedMs = 10_000L, durationMs = 2_500L), 0f)
    }

    @Test
    fun `progressOf is proportional mid-flight`() {
        assertEquals(0.4f, progressOf(elapsedMs = 1_000L, durationMs = 2_500L), 1e-6f)
    }

    // --- QA-SRM-22 — mọi điểm nội suy nằm trên đoạn thẳng nối hai mẫu, sai lệch < 1e-9 (S2) ---

    private data class Segment(val lat1: Double, val lng1: Double, val lat2: Double, val lng2: Double)

    @Test
    fun `lerpDegrees keeps every one of 100 interpolated points on the straight segment`() {
        val segments = listOf(
            Segment(10.7626, 106.6602, 10.7700, 106.6700), // quy mô một bước đi HCMC
            Segment(-33.8688, 151.2093, 40.7128, -74.0060), // cực đoan — hai bán cầu khác nhau
            Segment(0.0, 0.0, 0.0, 1.0), // đoạn NGANG thuần tuý — cạnh của công thức khoảng cách
            Segment(21.0285, 105.8542, 21.0285, 105.8542), // hai đầu trùng nhau — đoạn suy biến
        )
        for ((lat1, lng1, lat2, lng2) in segments) {
            for (i in 0 until 100) {
                val t = i / 99f
                val lat = lerpDegrees(lat1, lat2, t)
                val lng = lerpDegrees(lng1, lng2, t)
                val distance = distanceToSegment(lat, lng, lat1, lng1, lat2, lng2)
                assertTrue(
                    "t=$t lệch $distance khỏi đoạn ($lat1,$lng1)-($lat2,$lng2)",
                    distance < 1e-9,
                )
            }
        }
    }

    /**
     * Khoảng cách từ điểm tới đoạn thẳng trong CHÍNH mặt phẳng (lat,lng) — cùng không gian mà
     * [lerpDegrees] nội suy trong đó (QA-SRM-22 nói "đoạn thẳng nối hai mẫu", không phải đường trắc
     * địa cong trên mặt cầu). Suy biến an toàn khi A == B (trả khoảng cách Euclid tới điểm đó, thay
     * vì chia cho 0). Test-only — không phải mã sản phẩm, không thuộc `MarkerInterpolation.kt`.
     */
    private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val abx = bx - ax
        val aby = by - ay
        val lengthSquared = abx * abx + aby * aby
        if (lengthSquared == 0.0) return hypot(px - ax, py - ay)
        val cross = abx * (py - ay) - aby * (px - ax)
        return abs(cross) / hypot(abx, aby)
    }

    // --- isSpawnJump — quyết định (A), F-6. Các ca dưới đây khoá HÀM so sánh, với ngưỡng truyền
    // vào như một tham số. Bản thân GIÁ TRỊ 207.5 được khoá riêng ở
    // `ui/feature/map/component/AnimatedMarkerPositionsThresholdTest.kt` — tách ra vì nếu chỉ có
    // các ca ở đây, đổi `SPAWN_SNAP_THRESHOLD_M` trong mã sản phẩm thành 2.0 vẫn XANH hết (mỗi ca
    // tự mang hằng số của riêng nó), và bản sửa F-6 sẽ chết âm thầm.

    @Test
    fun `isSpawnJump is false for a normal continuous tick step`() {
        // 20.75 m = SIM_MEMBER_SPEED_MPS (8.3) x MEMBER_ROAM_INTERVAL_MS (2 500) / 1000 — cận trên
        // của một bước liên tục (xem KDoc SPAWN_SNAP_THRESHOLD_M ở AnimatedMarkerPositions.kt).
        assertFalse(isSpawnJump(distanceMeters = 20.75, thresholdMeters = 207.5))
    }

    @Test
    fun `isSpawnJump is true for the smallest possible real spawn distance`() {
        // 2 080 m = MAX_WALK_M (5 000) - approachRadiusMeters lớn nhất có thể (~2 920, zone bán
        // kính tối đa) — cận dưới của một cú spawn THẬT (cùng KDoc).
        assertTrue(isSpawnJump(distanceMeters = 2_080.0, thresholdMeters = 207.5))
    }

    @Test
    fun `isSpawnJump is exclusive at the threshold boundary`() {
        assertFalse(isSpawnJump(distanceMeters = 207.5, thresholdMeters = 207.5))
        assertTrue(isSpawnJump(distanceMeters = 207.500_001, thresholdMeters = 207.5))
    }

    // --- haversineMeters — sanity cho phép đo dùng bởi ngưỡng snap (A) ---

    /**
     * Reviewer phase-03: hai ca "zero cho cùng một điểm" và "đối xứng" phía dưới **cùng ĐẠT nếu
     * [haversineMeters] trả thẳng `0.0`** — đã kiểm bằng mutation: thay cả thân hàm bằng `= 0.0`
     * thì 101/101 ca của `:ui` vẫn XANH. Đó là đúng lỗ nghiệm thu mà
     * `AnimatedMarkerPositionsThresholdTest` được viết ra để bịt, chỉ thấp hơn một tầng: ngưỡng
     * snap `SPAWN_SNAP_THRESHOLD_M` được khoá GIÁ TRỊ, nhưng phép ĐO nuôi nó thì không — haversine
     * hỏng ⇒ `isSpawnJump` không bao giờ đúng ⇒ bản sửa F-6 chết âm thầm và marker "trượt" 2 km
     * qua thành phố ở mỗi cú spawn.
     *
     * Ca này khoá bằng khoảng cách BIẾT TRƯỚC, chọn để giết ba dạng hỏng cùng lúc:
     * - trả hằng số (0 hoặc bất kỳ) → cả ba assertion đỏ;
     * - đảo lat/lng → assertion thứ ba đỏ (ở vĩ độ 60°, 1° kinh độ chỉ bằng nửa 1° vĩ độ);
     * - quên `cos(lat)` trong công thức → cũng assertion thứ ba đỏ (ra 111 195 thay vì 55 597).
     *
     * Giá trị kỳ vọng tính từ CÙNG bán kính `EARTH_RADIUS_M = 6 371 000` mà mã sản phẩm dùng, nên
     * dung sai chặt (0.5 m) — đây không phải phép so với một nguồn trắc địa khác.
     */
    @Test
    fun `haversineMeters matches known distances at the scale the snap threshold works on`() {
        // 1° vĩ độ = πR/180 ở mọi vĩ độ.
        assertEquals(111_194.93, haversineMeters(0.0, 0.0, 1.0, 0.0), 0.5)
        assertEquals(111_194.93, haversineMeters(60.0, 0.0, 61.0, 0.0), 0.5)
        // 1° kinh độ co lại theo cos(vĩ độ): ở 60° chỉ còn một nửa.
        assertEquals(55_596.93, haversineMeters(60.0, 0.0, 60.0, 1.0), 0.5)
        // Quy mô THẬT mà ngưỡng snap làm việc: một bước tick 20.75 m ở TP.HCM.
        val oneDegreeLatMeters = 111_194.926_644_558_73
        assertEquals(
            "sai ở quy mô ~20 m thì `isSpawnJump` sai ở đúng dải nó phải phân biệt",
            NORMAL_TICK_STEP_METERS,
            haversineMeters(10.7626, 106.6602, 10.7626 + NORMAL_TICK_STEP_METERS / oneDegreeLatMeters, 106.6602),
            1e-3,
        )
    }

    @Test
    fun `haversineMeters is zero for the same point`() {
        assertEquals(0.0, haversineMeters(10.0, 106.0, 10.0, 106.0), 1e-9)
    }

    @Test
    fun `haversineMeters is symmetric`() {
        val ab = haversineMeters(10.762, 106.660, 10.770, 106.670)
        val ba = haversineMeters(10.770, 106.670, 10.762, 106.660)
        assertEquals(ab, ba, 1e-9)
    }

    // --- lerpDegrees KHÔNG đi đường ngắn — và đó là chủ ý, không phải sót ---

    /**
     * Khoá chủ ý "[lerpDegrees] nội suy THẲNG, KHÔNG vòng qua 0°/360° như [lerpBearing]".
     *
     * Không có ca này thì cái bẫy là hiển nhiên: hai hàm nằm cạnh nhau trong cùng file, cùng nhận
     * "độ", và một người đọc lướt sẽ thấy [lerpDegrees] "thiếu" phần xử lý vòng của [lerpBearing]
     * rồi "sửa" cho giống — sau đó mọi test hiện có vẫn XANH, vì không ca nào chạy qua kinh tuyến
     * 180°. Hỏng ra ở FR-5/QA-SRM-22: hai toạ độ 179.9° và −179.9° chỉ cách nhau 0.2° theo la bàn,
     * nhưng chúng là hai VỊ TRÍ, không phải một chuyển hướng — nội suy "đường ngắn" giữa chúng sẽ
     * ném marker qua đường đổi ngày thay vì đi trên đoạn thẳng nối hai mẫu.
     */
    @Test
    fun `lerpDegrees interpolates straight through, deliberately NOT the short way around 0-360`() {
        // Đường THẲNG từ 179.9 tới -179.9 đi qua 0 (giữa đoạn = 0.0), KHÔNG đi qua 180.
        assertEquals(0.0, lerpDegrees(179.9, -179.9, 0.5f), 1e-9)
        // Và tại 1/4 đoạn vẫn nằm trên chính đoạn thẳng đó, không nhảy sang nhánh kia.
        assertEquals(89.95, lerpDegrees(179.9, -179.9, 0.25f), 1e-9)
    }

    private companion object {
        /**
         * Cận trên một bước đi liên tục, chép CÓ CHỦ Ý thay vì import
         * `AnimatedMarkerPositions.NORMAL_TICK_STEP_M`: ca dùng nó khoá `haversineMeters` bằng một
         * khoảng cách BIẾT TRƯỚC ở đúng quy mô làm việc — nếu nó đọc chính hằng số sản phẩm thì
         * hằng số đổi (phase-06/B4) sẽ kéo theo cả kỳ vọng, và ca test hết đỏ được.
         * Việc canh `NORMAL_TICK_STEP_M` theo `TrackingConstants` là việc của
         * `AnimatedMarkerPositionsThresholdTest`, không phải của ca này.
         */
        const val NORMAL_TICK_STEP_METERS = 20.75
    }
}
