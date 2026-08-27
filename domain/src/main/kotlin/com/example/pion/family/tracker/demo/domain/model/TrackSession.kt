package com.example.pion.family.tracker.demo.domain.model

import java.time.Instant

/**
 * A "trip" — SUY RA lúc đọc, không có bảng Room tương ứng (LLM.md §9, PRD §9, phase-02 Key
 * Insight #2). A group of consecutive points less than `SESSION_GAP_MS` apart.
 */
data class TrackSession(
    val id: String,
    val memberId: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val points: List<LocationPoint>,
    val distanceMeters: Double,
)
