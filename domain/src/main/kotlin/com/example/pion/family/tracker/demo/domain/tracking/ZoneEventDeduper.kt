package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import java.time.Duration

/**
 * Quyết định khử trùng lặp tách ra thành hàm thuần ở `:domain`, lệch `LLM.md` Phụ lục A.1 (đặt
 * luật ở `ZoneEventRepositoryImpl`) — có chủ ý, để US-25 test được bằng JUnit thay vì phải chạy
 * emulator (plan.md câu hỏi mở Q-D, phase-03 Key Insight #6). *Nơi áp dụng* vẫn là
 * `ZoneEventRepositoryImpl.record()` — nơi DUY NHẤT gọi hàm này; xem `LLM.md` §8.1 và Phụ lục A.1.
 */
object ZoneEventDeduper {
    fun shouldRecord(
        lastSameKey: ZoneEvent?,
        incoming: ZoneEvent,
        windowMs: Long = TrackingConstants.EVENT_DEDUPE_WINDOW_MS,
    ): Boolean {
        if (lastSameKey == null) return true
        val gapMs = Duration.between(lastSameKey.occurredAt, incoming.occurredAt).toMillis()
        return gapMs >= windowMs
    }
}
