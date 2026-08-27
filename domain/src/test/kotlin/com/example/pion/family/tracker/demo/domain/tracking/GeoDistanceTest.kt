package com.example.pion.family.tracker.demo.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * Đối chiếu 3 cặp toạ độ đã biết khoảng cách (phase-03 Implementation Step 2, Risk Assessment).
 * Cặp A/B là hai trường hợp suy giảm đại số (dLon=0, dLat=0) của công thức Haversine đầy đủ nên
 * khoảng cách "đã biết" chính xác tuyệt đối theo hình học cầu (`R * góc`, radian); cặp C là
 * khoảng cách thật Hà Nội–TP.HCM, tính độc lập bằng Python cùng bán kính 6 371 008.8 m để bắt
 * lỗi sai dấu/sai đơn vị nếu có. `GeoDistance` là `internal`; test source set của module Kotlin
 * JVM mặc định "associate" với `main` nên truy cập được — không cần đổi visibility.
 */
class GeoDistanceTest {

    @Test
    fun `same point is zero distance`() {
        val d = GeoDistance.haversineMeters(21.0, 105.8, 21.0, 105.8)
        assertEquals(0.0, d, 0.001)
    }

    @Test
    fun `1 degree of latitude at the equator is ~111195 m`() {
        val d = GeoDistance.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertWithinPercent(expected = 111_195.08, actual = d, percent = 0.5)
    }

    @Test
    fun `90 degrees of longitude at the equator is ~10007557 m`() {
        val d = GeoDistance.haversineMeters(0.0, 0.0, 0.0, 90.0)
        assertWithinPercent(expected = 10_007_557.22, actual = d, percent = 0.5)
    }

    @Test
    fun `Hanoi to Ho Chi Minh City is ~1137806 m`() {
        val d = GeoDistance.haversineMeters(21.0285, 105.8542, 10.8231, 106.6297)
        assertWithinPercent(expected = 1_137_805.95, actual = d, percent = 0.5)
    }

    @Test
    fun `distance is symmetric regardless of argument order`() {
        val forward = GeoDistance.haversineMeters(21.0285, 105.8542, 10.8231, 106.6297)
        val backward = GeoDistance.haversineMeters(10.8231, 106.6297, 21.0285, 105.8542)
        assertEquals(forward, backward, 0.001)
    }

    private fun assertWithinPercent(expected: Double, actual: Double, percent: Double) {
        val allowed = expected * (percent / 100.0)
        assertEquals(expected, actual, allowed)
        // Also fail loudly if the sign/magnitude is wildly wrong (e.g. degrees vs radians bug).
        assertEquals(0.0, abs(expected - actual) / expected * 100.0, percent)
    }
}
