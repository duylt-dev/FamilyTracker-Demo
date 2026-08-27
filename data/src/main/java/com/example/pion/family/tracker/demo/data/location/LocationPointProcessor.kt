package com.example.pion.family.tracker.demo.data.location

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.tracking.FilterResult
import com.example.pion.family.tracker.demo.domain.tracking.LocationFilter

/**
 * Một điểm GPS THẬT đi qua đúng 2 bước: lọc nhiễu -> ghi Room. Tách khỏi `LocationTrackingService`
 * (extends `android.app.Service`, không dựng được trên JVM test thuần không Robolectric) để khoá
 * được luật threading `lastKept` bằng JUnit — `LocationPointProcessorTest` (Key Insight #10,
 * LLM.md §8.3).
 *
 * **fix-zone-follows-members — bước "đánh giá zone" đã rời khỏi lớp này.** Điểm ở đây thuộc về
 * SELF (thiết bị đang chạy app), và self thôi là chủ thể của zone: tạo một zone quanh chính mình
 * không còn sinh sự kiện hay thông báo nào. Việc đánh giá zone giờ nằm ở
 * [MemberMovementSimulator], cho các thành viên ĐƯỢC THEO DÕI — xem LLM.md §8.1. Lớp này giữ lại
 * đúng phần còn có chủ: nuôi chấm xanh trên bản đồ (US-06) và polyline ở tab Lịch sử (F3).
 *
 * [lastKeptPoint] chỉ cập nhật ở nhánh [FilterResult.Accept] của [LocationFilter.accept] — KHÔNG
 * cập nhật bằng điểm thô nhận trực tiếp từ
 * [com.example.pion.family.tracker.demo.domain.repository.LocationSource.stream]. Đây là hàm
 * DUY NHẤT trong service gọi `LocationFilter.accept` — không để một nơi thứ hai tự threading biến
 * này theo cách khác.
 *
 * **phase-01, D4 — "publish trước khi lọc" KHÔNG phải chỗ để bỏ qua bộ lọc.** [liveSelfLocation]
 * nhận MỌI điểm, kể cả điểm sắp bị [FilterResult.Reject] — cổng nuôi chấm xanh (US-06/US-43).
 * [trackingRepository] chỉ nhận điểm [FilterResult.Accept] — cổng ghi `location_points`/Lịch sử
 * (US-31, KHÔNG đổi). Ca `an indoor fix with accuracy 80m …` của `LocationPointProcessorTest`
 * khoá cả hai vế cùng lúc.
 */
class LocationPointProcessor(
    private val trackingRepository: TrackingRepository,
    private val liveSelfLocation: LiveSelfLocation,
) {
    var lastKeptPoint: LocationPoint? = null
        private set

    suspend fun process(point: LocationPoint): FilterResult {
        // LUÔN publish, bất kể kết quả lọc — nuôi chấm xanh không phụ thuộc MAX_ACCURACY_M (D4).
        liveSelfLocation.publish(point)

        val result = LocationFilter.accept(point, lastKeptPoint)
        if (result is FilterResult.Accept) {
            lastKeptPoint = point
            trackingRepository.record(point)
        }
        return result
    }
}
