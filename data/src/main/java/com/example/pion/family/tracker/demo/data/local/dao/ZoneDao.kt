package com.example.pion.family.tracker.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.pion.family.tracker.demo.data.local.entity.ZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Upsert
    suspend fun upsert(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :zoneId")
    suspend fun delete(zoneId: String)

    /** Gates creation at `MAX_ZONES` = 100 (Play Services hard limit) before use cases call it. */
    @Query("SELECT COUNT(*) FROM zones")
    suspend fun count(): Int

    /** phase-06 — lets [SaveZoneUseCase] tell "create" apart from "update" (LLM.md §13). */
    @Query("SELECT EXISTS(SELECT 1 FROM zones WHERE id = :zoneId)")
    suspend fun exists(zoneId: String): Boolean
}
