package com.example.pion.family.tracker.demo.domain.model

import java.time.Instant

/**
 * One filtered GPS sample — PRD §9. Deliberately carries no `memberId`: this demo only ever
 * records real points for the "self" member (see [TrackingRepository]), so the association is
 * made at the `:data` layer, not here.
 */
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDegrees: Float,
    val recordedAt: Instant,
)
