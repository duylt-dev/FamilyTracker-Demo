package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType

/**
 * Một lần vào/ra ranh giới zone, sinh bởi [ZoneEvaluator.evaluate] — chưa phải [ZoneEvent] đầy
 * đủ vì hàm thuần này không biết `memberId`/`id`/`source`; tầng gọi (`:data`) điền các trường đó.
 * [shouldNotify] tôn trọng `notifyOnEnter`/`notifyOnExit` CHỈ ở mức có bắn thông báo hay không —
 * sự kiện vẫn luôn có mặt trong [ZoneEvaluation.events] để Timeline ghi nhận đủ (PRD §3.2).
 */
data class ZoneCrossing(
    val zoneId: String,
    val zoneName: String,
    val type: ZoneEventType,
    val shouldNotify: Boolean,
)

/** Kết quả một lần đánh giá: sự kiện sinh ra + tập zoneId đang ở trong SAU khi tính (LLM.md §8.2). */
data class ZoneEvaluation(
    val events: List<ZoneCrossing>,
    val insideAfter: Set<String>,
)

/**
 * Hàm thuần quyết định "đã vào hay đã ra" — không `Context`, không Play Services, không
 * coroutine (LLM.md §8.2). Nhận trạng thái trước làm tham số, không giữ trạng thái bên trong,
 * nên gọi hai lần liên tiếp không cần dựng lại đối tượng (phase-03 Key Insight #2).
 *
 * Chống rung ranh giới: vào khi `d < radius`, chỉ ra khi `d > radius + ZONE_EXIT_BUFFER_M`. Không
 * có khoảng đệm này, đứng yên ngay mép zone sẽ nhận ENTER/EXIT liên tục theo sai số GPS.
 */
object ZoneEvaluator {
    /**
     * Luật vào zone, tách riêng khỏi [evaluate] để test nạp thẳng `Double` — điểm dựng bằng toạ
     * độ (qua Haversine round-trip) không bao giờ cho `distanceMeters == radiusMeters` tuyệt đối
     * (sai số ~1e-10m), nên chỉ hàm nhận `Double` trực tiếp mới khoá được biên `<` vs `<=` thật.
     */
    internal fun entersAt(distanceMeters: Double, radiusMeters: Double): Boolean =
        distanceMeters < radiusMeters

    /** Luật ra zone — biên cách xa `radiusMeters` một khoảng `ZONE_EXIT_BUFFER_M` (hysteresis). */
    internal fun exitsAt(distanceMeters: Double, radiusMeters: Double): Boolean =
        distanceMeters > radiusMeters + TrackingConstants.ZONE_EXIT_BUFFER_M

    fun evaluate(
        point: LocationPoint,
        zones: List<Zone>,
        previouslyInside: Set<String>,
    ): ZoneEvaluation {
        val insideAfter = previouslyInside.toMutableSet()
        val events = mutableListOf<ZoneCrossing>()

        for (zone in zones) {
            val distanceMeters = GeoDistance.haversineMeters(
                point.latitude,
                point.longitude,
                zone.latitude,
                zone.longitude,
            )
            val wasInside = zone.id in previouslyInside
            val radius = zone.radiusMeters.toDouble()

            when {
                !wasInside && entersAt(distanceMeters, radius) -> {
                    insideAfter += zone.id
                    events += ZoneCrossing(zone.id, zone.name, ZoneEventType.ENTER, zone.notifyOnEnter)
                }
                wasInside && exitsAt(distanceMeters, radius) -> {
                    insideAfter -= zone.id
                    events += ZoneCrossing(zone.id, zone.name, ZoneEventType.EXIT, zone.notifyOnExit)
                }
                // còn lại: giữ nguyên trạng thái, không sinh sự kiện — vùng đệm hysteresis.
            }
        }

        return ZoneEvaluation(events, insideAfter)
    }
}
