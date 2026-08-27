package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.TrackSession

/**
 * Tách một danh sách điểm thành các "chuyến đi" — hàm thuần, không phải bảng Room (PRD v1.2 §9,
 * LLM.md §9, phase-02 Key Insight #2). Đây là bản thật thay cho helper tạm từng lặp cục bộ logic
 * này ở `:data` (LLM.md §13, xoá ở phase-03).
 */
object RouteSplitter {
    /**
     * Sắp theo `recordedAt`, cắt chuyến mới khi khoảng cách thời gian giữa 2 điểm liên tiếp
     * **lớn hơn** [gapMs] (ngưỡng một chiều: bằng đúng [gapMs] vẫn là cùng một chuyến — phase-03
     * bảng test biên). Chuyến chỉ có 1 điểm vẫn là một chuyến hợp lệ, quãng đường 0.
     *
     * `TrackSession.id` = `"$memberId-<epochMilli điểm đầu chuyến>"`: ổn định giữa hai lần gọi
     * trên cùng dữ liệu, vì màn History giữ chuyến đang chọn bằng `selectedSessionId` (phase-03
     * Key Insight #5) — id đổi mỗi lần Room re-emit sẽ tự bỏ chọn chuyến đang vẽ.
     */
    fun split(
        memberId: String,
        points: List<LocationPoint>,
        gapMs: Long = TrackingConstants.SESSION_GAP_MS,
    ): List<TrackSession> {
        if (points.isEmpty()) return emptyList()

        val sorted = points.sortedBy { it.recordedAt }
        val groups = mutableListOf(mutableListOf(sorted.first()))
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val current = sorted[i]
            val gapBetween = current.recordedAt.toEpochMilli() - prev.recordedAt.toEpochMilli()
            if (gapBetween > gapMs) {
                groups += mutableListOf(current)
            } else {
                groups.last() += current
            }
        }

        return groups.map { group ->
            TrackSession(
                id = "$memberId-${group.first().recordedAt.toEpochMilli()}",
                memberId = memberId,
                startedAt = group.first().recordedAt,
                endedAt = group.last().recordedAt,
                points = group,
                distanceMeters = totalDistanceMeters(group),
            )
        }
    }

    private fun totalDistanceMeters(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += GeoDistance.haversineMeters(
                points[i - 1].latitude,
                points[i - 1].longitude,
                points[i].latitude,
                points[i].longitude,
            )
        }
        return total
    }
}
