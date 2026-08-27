package com.example.pion.family.tracker.demo.domain.model

import java.time.Instant

/** A circular geofence area — PRD §9, §3.1. */
data class Zone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val colorArgb: Int,
    val notifyOnEnter: Boolean,
    val notifyOnExit: Boolean,
    val createdAt: Instant,
)
