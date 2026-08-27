package com.example.pion.family.tracker.demo.domain.model

import java.time.Instant

/** ENTER/EXIT of a zone boundary — PRD §9. */
enum class ZoneEventType { ENTER, EXIT }

/**
 * Which detection path raised the event — PRD §3.2, used to verify dedupe (LLM.md §8.1).
 *
 * Chỉ còn MỘT giá trị sau fix-zone-follows-members: `GEOFENCE_API` biến mất cùng cả đường phát
 * hiện qua Play Services (API đó chỉ bắn transition cho THIẾT BỊ đang chạy app, tức là chỉ cho
 * self, mà chủ thể của zone giờ là các thành viên được theo dõi). `FOREGROUND` vẫn đúng nghĩa
 * đen: `MemberMovementSimulator` chạy bên trong `LocationTrackingService`, một foreground service.
 * Giữ lại cả enum lẫn cột thay vì xoá — PRD §9 khai cột này, và một đường phát hiện thứ hai xuất
 * hiện sau sẽ cần đúng chỗ đó để phân biệt.
 */
enum class EventSource { FOREGROUND }

/**
 * `zoneName` is copied, not joined from `zones`, so deleting a zone never erases its Timeline
 * history — PRD §9, phase-02 Key Insight #3.
 */
data class ZoneEvent(
    val id: String,
    val zoneId: String,
    val zoneName: String,
    val memberId: String,
    val type: ZoneEventType,
    val occurredAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val source: EventSource,
)
