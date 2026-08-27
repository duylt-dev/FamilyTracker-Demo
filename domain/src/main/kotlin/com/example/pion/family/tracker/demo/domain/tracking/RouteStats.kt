package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.TrackSession
import java.time.Duration

/** Tổng quãng đường, thời lượng, tốc độ trung bình của một [TrackSession] — cho màn History (phase-08). */
data class RouteStats(
    val distanceMeters: Double,
    val durationMs: Long,
    val averageSpeedKmh: Double,
) {
    companion object {
        /** Thời lượng 0 (chuyến 1 điểm) → tốc độ 0, KHÔNG chia cho 0. */
        fun of(session: TrackSession): RouteStats {
            val durationMs = Duration.between(session.startedAt, session.endedAt).toMillis()
            val averageSpeedKmh = if (durationMs <= 0L) {
                0.0
            } else {
                (session.distanceMeters / 1000.0) / (durationMs / 3_600_000.0)
            }
            return RouteStats(session.distanceMeters, durationMs, averageSpeedKmh)
        }
    }
}
