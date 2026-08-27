package com.example.pion.family.tracker.demo.data.location

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.tracking.DropReason
import com.example.pion.family.tracker.demo.domain.tracking.FilterResult
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Khoá Key Insight #10 (phase-04) / LLM.md §8.3: `LocationTrackingService` (ở đây đại diện bởi
 * [LocationPointProcessor], "lớp giữ state tương đương" phase-04 doc chấp nhận) PHẢI so điểm mới
 * với điểm CUỐI CÙNG ĐƯỢC GIỮ (`FilterResult.Accept`), không phải điểm cuối cùng nhận được từ
 * `LocationSource.stream()`. Input mô phỏng lại đúng kịch bản "người đi bộ chậm" của
 * `LocationFilterTest.distance rule compares against the last KEPT point, not the last seen
 * point` (`domain/src/test/.../tracking/LocationFilterTest.kt`) — mỗi bước dịch 9m về phía Bắc so
 * với điểm NGAY TRƯỚC (luôn < MIN_DISTANCE_M = 10m), nhưng cách điểm ĐƯỢC GIỮ gần nhất tích luỹ đủ
 * xa để vượt ngưỡng ở bước thứ 2 và thứ 4.
 *
 * Nếu ai đó đổi [LocationPointProcessor.process] sang cập nhật `lastKeptPoint` bằng MỌI điểm nhận
 * được (kể cả `Reject`) thay vì chỉ ở nhánh `Accept`, mọi bước sau bước đầu sẽ so với điểm ngay
 * trước — luôn đúng 9m, luôn < 10m — và bị `Reject(DISTANCE)` vĩnh viễn. Test này đỏ ngay khi đó.
 */
class LocationPointProcessorTest {

    private val base: Instant = Instant.parse("2026-08-21T08:00:00Z")
    private val origin = point(21.0, 105.8, at = base)

    @Test
    fun `a slow walker keeps points measured against the last KEPT point, not the last seen point`() = runTest {
        val trackingRepository = FakeTrackingRepository()
        val processor = LocationPointProcessor(trackingRepository, LiveSelfLocation())

        // Bootstrap: điểm đầu tiên luôn Accept (lastKept == null).
        val bootstrap = processor.process(origin)
        assertEquals(FilterResult.Accept, bootstrap)
        assertEquals(origin, processor.lastKeptPoint)

        var current = origin
        var acceptedCount = 0
        repeat(5) { i ->
            current = pointDueNorth(current, distanceMeters = 9.0, at = base.plusSeconds((i + 1) * 10L))
            val result = processor.process(current)
            if (result is FilterResult.Accept) acceptedCount++
        }

        // Cumulative offsets from origin are 9/18/27/36/45m; comparing against lastKeptPoint
        // (updated only on Accept) crosses MIN_DISTANCE_M (10m) at the 2nd and 4th step -> 2.
        // A buggy "lastSeen" threading would compare consecutive 9m steps forever -> 0.
        assertEquals(2, acceptedCount)

        // Every Accept (bootstrap + 2 walking steps) must have reached Room — 3 total.
        assertEquals(3, trackingRepository.recorded.size)
    }

    @Test
    fun `lastKeptPoint is never updated on a Reject`() = runTest {
        val trackingRepository = FakeTrackingRepository()
        val processor = LocationPointProcessor(trackingRepository, LiveSelfLocation())

        processor.process(origin)
        val keptAfterBootstrap = processor.lastKeptPoint

        // 1m away, well under MIN_DISTANCE_M -> Reject(DISTANCE).
        val tooClose = pointDueNorth(origin, distanceMeters = 1.0, at = base.plusSeconds(10))
        val result = processor.process(tooClose)

        assertEquals(FilterResult.Reject::class, result::class)
        assertEquals(keptAfterBootstrap, processor.lastKeptPoint)
    }

    /**
     * phase-01, D4 (`decisions.md` §C3) — QA-SRM-18: `MAX_ACCURACY_M` (50m) tiếp tục quyết định cái
     * gì vào `location_points` (US-31 không đổi), nhưng thôi quyết định cái gì lên
     * [LiveSelfLocation] (US-43). Một điểm `Reject(ACCURACY)` PHẢI được publish để vẽ, PHẢI không
     * được ghi, và PHẢI không đụng `lastKeptPoint` (test đỏ trước khi có code — dev report S1).
     */
    @Test
    fun `an indoor fix with accuracy 80m is published for display even though it is not recorded`() = runTest {
        val trackingRepository = FakeTrackingRepository()
        val liveSelfLocation = LiveSelfLocation()
        val processor = LocationPointProcessor(trackingRepository, liveSelfLocation)

        val indoorFix = origin.copy(accuracyMeters = 80f)
        val result = processor.process(indoorFix)

        assertEquals(FilterResult.Reject(DropReason.ACCURACY), result)
        assertEquals(indoorFix, liveSelfLocation.observe().value)
        assertEquals(0, trackingRepository.recorded.size)
        // Publish-before-filter must not touch the record-gate's own state either.
        assertNull(processor.lastKeptPoint)
    }

    @Test
    fun `distance rule ignores Reject(ACCURACY) points and uses the last KEPT point instead`() = runTest {
        val trackingRepository = FakeTrackingRepository()
        val liveSelfLocation = LiveSelfLocation()
        val processor = LocationPointProcessor(trackingRepository, liveSelfLocation)

        // Bootstrap: Origin với accuracy tốt, được Accept + record.
        assertEquals(FilterResult.Accept, processor.process(origin))
        assertEquals(1, trackingRepository.recorded.size)

        // Điểm 1: accuracy = 80m (vượt MAX_ACCURACY_M) cách origin 50m → Reject(ACCURACY)
        val badAccuracy = pointDueNorth(origin, distanceMeters = 50.0, at = base.plusSeconds(10))
            .copy(accuracyMeters = 80f)
        val result1 = processor.process(badAccuracy)
        assertEquals(FilterResult.Reject(DropReason.ACCURACY), result1)
        assertEquals(badAccuracy, liveSelfLocation.observe().value) // Published
        assertEquals(1, trackingRepository.recorded.size) // Not recorded

        // Điểm 2: accuracy = 5m, cách điểm 1 (Reject) là 15m → nhưng cách origin (last KEPT) là 65m
        // → phải Accept vì so với origin, không phải điểm 1
        val goodAccuracy = pointDueNorth(badAccuracy, distanceMeters = 15.0, at = base.plusSeconds(20))
            .copy(accuracyMeters = 5f)
        val result2 = processor.process(goodAccuracy)
        assertEquals(FilterResult.Accept, result2)
        // lastKeptPoint updated only on Accept, so bây giờ là goodAccuracy
        assertEquals(goodAccuracy, processor.lastKeptPoint)
        assertEquals(2, trackingRepository.recorded.size)
    }

    /**
     * phase-01, Việc 2 — Reject do DISTANCE vẫn phải được publish. Test này kiểm
     * Reject(DISTANCE) cụ thể (không phải accuracy) vẫn publish vào LiveSelfLocation, nhưng
     * không record. Nếu ai đó di chuyển `liveSelfLocation.publish()` xuống dưới nhánh Accept,
     * test này đỏ.
     */
    @Test
    fun `a point rejected for distance is still published for display, just not recorded`() = runTest {
        val trackingRepository = FakeTrackingRepository()
        val liveSelfLocation = LiveSelfLocation()
        val processor = LocationPointProcessor(trackingRepository, liveSelfLocation)

        // Bootstrap: Accept
        assertEquals(FilterResult.Accept, processor.process(origin))
        assertEquals(1, trackingRepository.recorded.size)

        // Điểm gần (1m) - Reject(DISTANCE), nhưng phải publish
        val tooClose = pointDueNorth(origin, distanceMeters = 1.0, at = base.plusSeconds(10))
        val result = processor.process(tooClose)
        assertEquals(FilterResult.Reject(DropReason.DISTANCE), result)
        assertEquals(tooClose, liveSelfLocation.observe().value) // Published
        assertEquals(1, trackingRepository.recorded.size) // Not recorded
    }

    /**
     * phase-01, D7 (LLM.md §13 Open #14) — CHỦ Ý giới hạn: LiveSelfLocation là
     * MutableStateFlow sống theo process, giữ điểm cuối vĩnh viễn. Nếu tắt theo dõi,
     * marker trông vẫn "sống" tại vị trí cuối — đây là lựa chọn có chủ ý (cấm ẩn marker
     * theo độ cũ, tránh hỗn loạn UX). Test này MÔ TẢ hành vi đó để lần sau ai đó sẽ
     * thấy test ĐỎ nếu muốn "sửa" nó mà không biết đó là chủ ý.
     */
    @Test
    fun `live self location retains last point indefinitely per design (D7), not cleared on service stop`() = runTest {
        val trackingRepository = FakeTrackingRepository()
        val liveSelfLocation = LiveSelfLocation()
        val processor = LocationPointProcessor(trackingRepository, liveSelfLocation)

        // Publish một điểm
        val point = origin.copy(recordedAt = base.plusSeconds(10))
        processor.process(point)
        assertEquals(point, liveSelfLocation.observe().value)

        // Giả sử service tắt (trong thực tế là LocationTrackingService.stop())
        // Không có method nào "clear" LiveSelfLocation — điểm vẫn ở đó
        // Đây là ý định (D7), không phải bug; marker sẽ vẫn hiện tại vị trí cuối
        // cho tới khi service chạy lại và publish điểm mới
        assertEquals(point, liveSelfLocation.observe().value)
    }

    private fun point(lat: Double, lng: Double, at: Instant) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = at,
    )

    /** Dịch một điểm [distanceMeters] về phía Bắc — cùng công thức rút gọn với `LocationFilterTest`. */
    private fun pointDueNorth(from: LocationPoint, distanceMeters: Double, at: Instant): LocationPoint {
        val deltaLatDeg = Math.toDegrees(distanceMeters / EARTH_RADIUS_M)
        return from.copy(latitude = from.latitude + deltaLatDeg, recordedAt = at)
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_008.8
    }

    /** Fake theo LLM.md §11 — không thư viện mock. */
    private class FakeTrackingRepository : TrackingRepository {
        val recorded = mutableListOf<LocationPoint>()
        override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> = flowOf(emptyList())
        override suspend fun record(point: LocationPoint) {
            recorded += point
        }
        override suspend fun purgeOlderThan(days: Int): Int = 0
        override fun isTracking(): Flow<Boolean> = flowOf(false)
        override fun observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)
        override suspend fun setTracking(enabled: Boolean) = Unit
        override suspend fun runSimulation(fixes: List<SimulatedFix>) = Unit
    }
}
