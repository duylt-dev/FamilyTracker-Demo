package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import kotlinx.coroutines.flow.Flow

/** PRD §8 — exact signature, implemented by `:data/repository/ZoneRepositoryImpl`. */
interface ZoneRepository {
    fun observeAll(): Flow<List<Zone>>
    suspend fun save(zone: Zone): AppResult<Zone>
    suspend fun delete(zoneId: String): AppResult<Unit>

    /** Used to block creation at 100 zones — Play Services' hard geofence limit (LLM.md §9). */
    suspend fun count(): Int

    /**
     * phase-06 fix for LLM.md §13 (formerly Open #4): lets [SaveZoneUseCase] tell "create" apart
     * from "update" so editing an existing zone at [com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants.MAX_ZONES]
     * is not wrongly rejected — only a genuinely NEW row competes for the 100-zone cap.
     */
    suspend fun exists(zoneId: String): Boolean
}
