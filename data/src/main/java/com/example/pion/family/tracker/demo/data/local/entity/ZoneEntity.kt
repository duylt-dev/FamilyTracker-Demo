package com.example.pion.family.tracker.demo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** LLM.md §9 — table `zones`. */
@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val colorArgb: Int,
    val notifyOnEnter: Boolean,
    val notifyOnExit: Boolean,
    val createdAt: Instant,
)
