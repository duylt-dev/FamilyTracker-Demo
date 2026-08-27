package com.example.pion.family.tracker.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pion.family.tracker.demo.data.local.entity.ZoneEventEntity
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneEventDao {
    @Insert
    suspend fun insert(event: ZoneEventEntity)

    @Query("SELECT * FROM zone_events WHERE occurredAt >= :sinceMillis ORDER BY occurredAt DESC")
    fun observeSince(sinceMillis: Long): Flow<List<ZoneEventEntity>>

    /** Read by `ZoneEventRepositoryImpl.record()` for the 60s dedupe rule (LLM.md §8.1). */
    @Query(
        "SELECT * FROM zone_events WHERE zoneId = :zoneId AND memberId = :memberId AND type = :type " +
            "ORDER BY occurredAt DESC LIMIT 1",
    )
    suspend fun latestForKey(zoneId: String, memberId: String, type: ZoneEventType): ZoneEventEntity?

    /** PRD §3.3 7-day retention. Returns rows deleted for `FTD_EVENT purge_completed`. */
    @Query("DELETE FROM zone_events WHERE occurredAt < :millis")
    suspend fun deleteOlderThan(millis: Long): Int

    /**
     * Sự kiện gần nhất cho MỖI zone của một member — dùng bởi `LocationTrackingService` (phase-04
     * Implementation Step 9) để nạp lại `insideZoneIds` khi service khởi động, tránh bắn ENTER giả
     * cho zone người dùng đã đang ở trong từ trước khi service chạy lại.
     */
    @Query(
        "SELECT * FROM zone_events e1 WHERE memberId = :memberId AND occurredAt = (" +
            "SELECT MAX(e2.occurredAt) FROM zone_events e2 WHERE e2.zoneId = e1.zoneId AND e2.memberId = :memberId)",
    )
    suspend fun latestPerZone(memberId: String): List<ZoneEventEntity>
}
