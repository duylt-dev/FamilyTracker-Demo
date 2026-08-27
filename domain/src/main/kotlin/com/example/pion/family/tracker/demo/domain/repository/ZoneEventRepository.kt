package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import kotlinx.coroutines.flow.Flow

/**
 * PRD §8, with one deliberate addition: `purgeOlderThan`. PRD §8 only declares
 * `observeTimeline`/`record`, but phase-02's Requirements explicitly need `zone_events` purged
 * after `HISTORY_RETENTION_DAYS` — impossible without a delete capability on this interface.
 * See phase-02 dev report and LLM.md §13.
 */
interface ZoneEventRepository {
    fun observeTimeline(sinceDays: Int): Flow<List<ZoneEvent>>

    /** The ONE place the 60s dedupe rule (LLM.md §8.1) is applied. */
    suspend fun record(event: ZoneEvent)

    suspend fun purgeOlderThan(days: Int): Int
}
