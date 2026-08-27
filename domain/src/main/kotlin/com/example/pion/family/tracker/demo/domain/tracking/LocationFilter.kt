package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import java.time.Duration

/** Lý do một điểm bị loại — dùng để log `FTD_EVENT location_dropped reason=…` ở tầng gọi (PRD §10). */
enum class DropReason { ACCURACY, DISTANCE, SPEED }

sealed interface FilterResult {
    data object Accept : FilterResult
    data class Reject(val reason: DropReason) : FilterResult
}

/**
 * Ba luật lọc nhiễu GPS, chạy theo thứ tự cố định — LLM.md §8.3, PRD §6, phase-03 Key Insight #3:
 * ACCURACY (`> MAX_ACCURACY_M`) → DISTANCE (`< MIN_DISTANCE_M`) → SPEED (`> MAX_SPEED_KMH`).
 *
 * [lastKept] PHẢI là điểm cuối cùng ĐƯỢC GIỮ LẠI, không phải điểm cuối cùng nhận được — nếu so
 * với điểm vừa nhận, một người đi bộ chậm (dưới `MIN_DISTANCE_M` mỗi nhịp) sẽ bị loại vĩnh viễn
 * vì so lệch luôn với điểm ngay trước, chưa từng nhích đủ xa (phase-03 "bẫy").
 *
 * **phase-01, D4 (`decisions.md` §C3) — luật `ACCURACY` chi phối việc GHI, không chi phối việc
 * VẼ.** `MAX_ACCURACY_M` = 50 quyết định đúng một việc: cái gì được ghi vào `location_points`
 * (`LocationPointProcessor` ở `:data/location/` → `trackingRepository.record()`, US-31 không đổi).
 * Marker vị trí thật thì đọc
 * [com.example.pion.family.tracker.demo.domain.repository.TrackingRepository.observeLiveSelfLocation]
 * (US-06/US-43), cổng phát MỌI điểm kể cả điểm hàm này sắp `Reject`. Không dòng nào của file này
 * đổi vì thế — hai luật vẽ/ghi tách bạch ở TẦNG GỌI, không ở đây.
 */
object LocationFilter {
    fun accept(point: LocationPoint, lastKept: LocationPoint?): FilterResult {
        if (point.accuracyMeters.toDouble() > TrackingConstants.MAX_ACCURACY_M) {
            return FilterResult.Reject(DropReason.ACCURACY)
        }

        if (lastKept == null) return FilterResult.Accept

        val distanceMeters = GeoDistance.haversineMeters(
            lastKept.latitude,
            lastKept.longitude,
            point.latitude,
            point.longitude,
        )
        if (distanceMeters < TrackingConstants.MIN_DISTANCE_M) {
            return FilterResult.Reject(DropReason.DISTANCE)
        }

        val dtMs = Duration.between(lastKept.recordedAt, point.recordedAt).toMillis()
        if (dtMs > 0) {
            val speedKmh = (distanceMeters / 1000.0) / (dtMs / 3_600_000.0)
            if (speedKmh > TrackingConstants.MAX_SPEED_KMH) {
                return FilterResult.Reject(DropReason.SPEED)
            }
        }

        return FilterResult.Accept
    }
}
