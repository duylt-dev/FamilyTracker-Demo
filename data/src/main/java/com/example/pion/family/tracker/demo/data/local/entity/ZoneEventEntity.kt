package com.example.pion.family.tracker.demo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.pion.family.tracker.demo.domain.model.EventSource
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import java.time.Instant

/**
 * LLM.md §9 — table `zone_events`. `zoneName` is copied, not joined (phase-02 Key Insight #3):
 * deleting a zone must not erase its Timeline history.
 */
@Entity(
    tableName = "zone_events",
    indices = [Index(value = ["occurredAt"])],
)
data class ZoneEventEntity(
    @PrimaryKey val id: String,
    val zoneId: String,
    val zoneName: String,
    val memberId: String,
    val type: ZoneEventType,
    val occurredAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val source: EventSource,
)
