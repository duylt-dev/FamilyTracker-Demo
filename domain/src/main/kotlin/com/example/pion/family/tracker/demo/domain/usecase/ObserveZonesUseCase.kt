package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow

/** Phase-05 (Map) và phase-06 (Zone List) đọc danh sách zone qua đây, không gọi thẳng repository. */
class ObserveZonesUseCase(
    private val zoneRepository: ZoneRepository,
) {
    operator fun invoke(): Flow<List<Zone>> = zoneRepository.observeAll()
}
