package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.repository.ZoneEventRepository
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import kotlinx.coroutines.flow.Flow

/** Phase-10 (Timeline) — nhật ký sự kiện vào/rời zone trong [sinceDays] ngày gần nhất. */
class ObserveZoneTimelineUseCase(
    private val zoneEventRepository: ZoneEventRepository,
) {
    operator fun invoke(sinceDays: Int = TrackingConstants.HISTORY_RETENTION_DAYS): Flow<List<ZoneEvent>> =
        zoneEventRepository.observeTimeline(sinceDays)
}
