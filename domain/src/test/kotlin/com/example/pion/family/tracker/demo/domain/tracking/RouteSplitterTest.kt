package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * `RouteSplitter` — 5 test biên bắt buộc theo bảng phase-03 (0 điểm, 1 điểm, đúng ngưỡng gap,
 * gap 6 phút, 7 điểm/3 chuyến với id ổn định). Khoá hành vi thay cho helper tạm từng lặp cục bộ
 * logic này ở `:data`.
 */
class RouteSplitterTest {

    private val memberId = "m1"
    private val base = Instant.parse("2026-08-21T08:00:00Z")

    @Test
    fun `zero points returns an empty list without throwing`() {
        val sessions = RouteSplitter.split(memberId, emptyList())
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `one point is exactly one session with startedAt equal to endedAt and zero distance`() {
        val sessions = RouteSplitter.split(memberId, listOf(pointAt(21.0, 105.8, base)))

        assertEquals(1, sessions.size)
        val session = sessions.single()
        assertEquals(base, session.startedAt)
        assertEquals(base, session.endedAt)
        assertEquals(0.0, session.distanceMeters, 0.0)
    }

    @Test
    fun `two points exactly SESSION_GAP_MS apart stay in the same session, threshold is one-sided`() {
        val second = base.plusMillis(TrackingConstants.SESSION_GAP_MS)
        val sessions = RouteSplitter.split(
            memberId,
            listOf(pointAt(21.0, 105.8, base), pointAt(21.001, 105.8, second)),
        )

        assertEquals(1, sessions.size)
    }

    @Test
    fun `two points 6 minutes apart split into 2 sessions, US-30`() {
        val sixMinutesLater = base.plusSeconds(6 * 60)
        val sessions = RouteSplitter.split(
            memberId,
            listOf(pointAt(21.0, 105.8, base), pointAt(21.001, 105.8, sixMinutesLater)),
        )

        assertEquals(2, sessions.size)
    }

    @Test
    fun `7 points across gaps produce 3 sessions, ordered ascending, with stable ids across calls`() {
        // 3 điểm liền nhau, cách nhau > gap, 2 điểm nữa, cách nhau > gap, 2 điểm cuối.
        val points = listOf(
            pointAt(21.0, 105.8, base),
            pointAt(21.0001, 105.8, base.plusSeconds(60)),
            pointAt(21.0002, 105.8, base.plusSeconds(120)),
            pointAt(21.1, 105.8, base.plusSeconds(120).plusSeconds(7 * 60)),
            pointAt(21.1001, 105.8, base.plusSeconds(120).plusSeconds(7 * 60).plusSeconds(60)),
            pointAt(21.2, 105.8, base.plusSeconds(120).plusSeconds(7 * 60).plusSeconds(60).plusSeconds(7 * 60)),
            pointAt(21.2001, 105.8, base.plusSeconds(120).plusSeconds(7 * 60).plusSeconds(60).plusSeconds(7 * 60).plusSeconds(60)),
        )

        val firstCall = RouteSplitter.split(memberId, points)
        val secondCall = RouteSplitter.split(memberId, points.shuffled(java.util.Random(42)))

        assertEquals(3, firstCall.size)
        assertEquals(3, secondCall.size)
        // Thứ tự tăng dần theo thời gian.
        assertTrue(firstCall.zipWithNext().all { (a, b) -> a.startedAt < b.startedAt })
        // id ổn định giữa hai lần gọi trên cùng dữ liệu, kể cả khi input không sắp xếp sẵn.
        assertEquals(firstCall.map { it.id }, secondCall.map { it.id })
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
