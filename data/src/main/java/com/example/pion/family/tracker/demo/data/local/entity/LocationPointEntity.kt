package com.example.pion.family.tracker.demo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * LLM.md §9 — table `location_points`. Index `(memberId, recordedAt)` serves both the
 * per-day route query and the `MAX(recordedAt)`-per-member query (US-08).
 */
@Entity(
    tableName = "location_points",
    indices = [Index(value = ["memberId", "recordedAt"])],
)
data class LocationPointEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDegrees: Float,
    val recordedAt: Instant,
)
