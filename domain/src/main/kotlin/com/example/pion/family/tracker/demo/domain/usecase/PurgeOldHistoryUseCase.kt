package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneEventRepository
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants

/**
 * Deletes `location_points` and `zone_events` older than [TrackingConstants.HISTORY_RETENTION_DAYS]
 * — PRD §3.3, run once at app startup (`FamilyTrackerApp.onCreate`, not here: `:domain` cannot
 * import `android.util.Log` for the `FTD_EVENT purge_completed` line — LLM.md §2).
 */
class PurgeOldHistoryUseCase(
    private val trackingRepository: TrackingRepository,
    private val zoneEventRepository: ZoneEventRepository,
) {
    suspend operator fun invoke(days: Int = TrackingConstants.HISTORY_RETENTION_DAYS): Result {
        val deletedPoints = trackingRepository.purgeOlderThan(days)
        val deletedEvents = zoneEventRepository.purgeOlderThan(days)
        return Result(deletedPoints, deletedEvents)
    }

    data class Result(val deletedPoints: Int, val deletedEvents: Int)
}
