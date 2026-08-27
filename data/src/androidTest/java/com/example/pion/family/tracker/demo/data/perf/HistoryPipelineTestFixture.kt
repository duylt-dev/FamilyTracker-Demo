package com.example.pion.family.tracker.demo.data.perf

import androidx.room.withTransaction
import com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase
import com.example.pion.family.tracker.demo.data.local.entity.LocationPointEntity
import java.time.Instant
import kotlin.math.sin

/** Split out of [HistoryPipelineScaleTest] to stay under LLM.md §5's 200-line file limit. */
internal const val PERF_TAG = "FTD_PERF_EVENT"
internal const val PERF_DB_NAME = "history-pipeline-scale-test.db"
internal const val PERF_MEMBER_ID = "perf-test-member"
internal const val PERF_ITERATIONS = 5
internal const val PERF_POINT_COUNT = 8_640
internal const val PERF_SESSION1_COUNT = 5_760 // 16h @ 10s cadence
internal const val PERF_SIMPLIFY_TOLERANCE_M = 10.0

private const val POINT_INTERVAL_SECONDS = 10L
private const val GAP_SECONDS = 20L * 60L // > SESSION_GAP_MS (5 min) -> forces a 2nd session
private const val BASE_LAT = 10.7899983
private const val BASE_LNG = 106.68
private const val NORTH_STEP_DEG = 1.0 / 111_320.0 // ~1 m/s north drift per 10s point
private const val ZIGZAG_AMP_DEG = 15.0 / 111_320.0 // ~15m east-west amplitude
private const val ZIGZAG_PHASE_STEP = 0.3

internal data class SeededRange(val fromMillis: Long, val toMillis: Long)

/**
 * PRD §7.1: "chu kỳ 10 giây = tối đa 8.640 điểm thô mỗi ngày". Two sessions (16h + 8h, split by a
 * 20-min gap > `SESSION_GAP_MS`) — same shape as real usage and `test-phase-08-report.md`'s scale
 * test, but totalling the PRD's exact ceiling instead of 8,460. Zigzag drift (matches
 * `dev-phase-08-report.md`'s method) so `PolyUtil.simplify` has real reduction work to do, not a
 * degenerate straight line.
 */
internal suspend fun seedOneDayAt8640Points(db: FamilyTrackerDatabase): SeededRange {
    val start = Instant.parse("2026-01-15T00:00:00Z")
    var timestamp = start
    val entities = ArrayList<LocationPointEntity>(PERF_POINT_COUNT)
    var lat = BASE_LAT
    for (i in 0 until PERF_POINT_COUNT) {
        if (i == PERF_SESSION1_COUNT) timestamp = timestamp.plusSeconds(GAP_SECONDS)
        lat += NORTH_STEP_DEG
        val zigzagLng = BASE_LNG + (ZIGZAG_AMP_DEG * sin(i * ZIGZAG_PHASE_STEP))
        entities += LocationPointEntity(
            id = "perf-$i",
            memberId = PERF_MEMBER_ID,
            latitude = lat,
            longitude = zigzagLng,
            accuracyMeters = 5f,
            speedMps = 1f,
            bearingDegrees = 0f,
            recordedAt = timestamp,
        )
        timestamp = timestamp.plusSeconds(POINT_INTERVAL_SECONDS)
    }
    db.withTransaction {
        entities.forEach { db.locationPointDao().insert(it) }
    }
    return SeededRange(fromMillis = start.toEpochMilli(), toMillis = timestamp.toEpochMilli() + 1)
}

internal fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

internal fun median(values: List<Long>): Long {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
}
