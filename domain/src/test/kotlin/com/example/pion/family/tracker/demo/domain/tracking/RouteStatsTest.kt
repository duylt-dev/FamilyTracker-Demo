package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Bảng test biên phase-03: chuyến 1 điểm không chia cho 0. */
class RouteStatsTest {

    @Test
    fun `single-point session has zero distance, zero duration, zero speed, no division by zero`() {
        val at = Instant.parse("2026-08-21T08:00:00Z")
        val onlyPoint = pointAt(21.0, 105.8, at)
        val session = TrackSession(
            id = "m1-${at.toEpochMilli()}",
            memberId = "m1",
            startedAt = at,
            endedAt = at,
            points = listOf(onlyPoint),
            distanceMeters = 0.0,
        )

        val stats = RouteStats.of(session)

        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(0L, stats.durationMs)
        assertEquals(0.0, stats.averageSpeedKmh, 0.0)
    }

    @Test
    fun `a session covering 1 hour and 36km averages 36kmh`() {
        val start = Instant.parse("2026-08-21T08:00:00Z")
        val end = start.plusSeconds(3_600)
        val session = TrackSession(
            id = "m1-${start.toEpochMilli()}",
            memberId = "m1",
            startedAt = start,
            endedAt = end,
            points = listOf(pointAt(21.0, 105.8, start), pointAt(21.1, 105.8, end)),
            distanceMeters = 36_000.0,
        )

        val stats = RouteStats.of(session)

        assertEquals(36_000.0, stats.distanceMeters, 0.0)
        assertEquals(3_600_000L, stats.durationMs)
        assertEquals(36.0, stats.averageSpeedKmh, 0.001)
    }

    private fun pointAt(lat: Double, lng: Double, at: Instant) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = at,
    )
}
