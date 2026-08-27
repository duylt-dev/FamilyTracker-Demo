package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.EventSource
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * US-25 — hàm thuần đứng sau `ZoneEventRepositoryImpl.record()` (Q-D, LLM.md §8.1, Phụ lục A.1).
 * Cùng ngưỡng 60s với `data/repository/ZoneEventDedupeTest` (androidTest) — hai tầng khoá cùng
 * một hành vi, một bằng JUnit thuần, một bằng Room thật.
 */
class ZoneEventDeduperTest {

    private val base = Instant.parse("2026-08-21T08:00:00Z")

    @Test
    fun `no previous event with the same key always records`() {
        val result = ZoneEventDeduper.shouldRecord(lastSameKey = null, incoming = eventAt(base))
        assertEquals(true, result)
    }

    @Test
    fun `same key 30s apart is deduped, gap under EVENT_DEDUPE_WINDOW_MS`() {
        val last = eventAt(base)
        val incoming = eventAt(base.plusSeconds(30))

        val result = ZoneEventDeduper.shouldRecord(last, incoming)

        assertEquals(false, result)
    }

    @Test
    fun `same key 90s apart is recorded, gap past EVENT_DEDUPE_WINDOW_MS`() {
        val last = eventAt(base)
        val incoming = eventAt(base.plusSeconds(90))

        val result = ZoneEventDeduper.shouldRecord(last, incoming)

        assertEquals(true, result)
    }

    @Test
    fun `gap exactly equal to the window is recorded, threshold is inclusive on this side`() {
        val last = eventAt(base)
        val incoming = eventAt(base.plusMillis(TrackingConstants.EVENT_DEDUPE_WINDOW_MS))

        val result = ZoneEventDeduper.shouldRecord(last, incoming)

        assertEquals(true, result)
    }

    private fun eventAt(occurredAt: Instant) = ZoneEvent(
        id = "e-${occurredAt.toEpochMilli()}",
        zoneId = "zone-1",
        zoneName = "Nha",
        memberId = "m1",
        type = ZoneEventType.ENTER,
        occurredAt = occurredAt,
        latitude = 21.0,
        longitude = 105.8,
        source = EventSource.FOREGROUND,
    )
}
