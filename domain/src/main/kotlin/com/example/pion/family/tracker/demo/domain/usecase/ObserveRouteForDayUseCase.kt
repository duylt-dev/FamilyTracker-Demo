package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Phase-08 (History) — chuyến đi của một member trong một ngày, đã gom bởi `RouteSplitter`. */
class ObserveRouteForDayUseCase(
    private val trackingRepository: TrackingRepository,
) {
    operator fun invoke(memberId: String, day: LocalDate): Flow<List<TrackSession>> =
        trackingRepository.observeRoute(memberId, day)
}
