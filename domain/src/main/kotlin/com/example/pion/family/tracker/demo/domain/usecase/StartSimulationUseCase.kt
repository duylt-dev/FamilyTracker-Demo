package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.GeoDistance
import com.example.pion.family.tracker.demo.domain.tracking.RouteBlueprint
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

/**
 * US-33 (F5), PRD §3.5 — orchestrates the "▶ Mô phỏng lộ trình" button on the History screen:
 * ensure a zone exists, build a [RouteBlueprint] through the nearest one, then hand it to
 * [TrackingRepository.runSimulation] to play it through the SAME pipeline real GPS uses
 * (LLM.md §8.4).
 *
 * **fix-zone-follows-members — lộ trình này KHÔNG còn sinh sự kiện vào/rời zone.** Nó phát vị trí
 * cho SELF, và self đã thôi là chủ thể của zone (LLM.md §8.1): mục đích còn lại của nút là vẽ một
 * chuyến đi thật vào `location_points` để màn History có polyline + thống kê quãng đường/thời
 * lượng để trình bày. Thông báo vào/rời zone giờ đến từ `MemberMovementSimulator`, chạy liên tục
 * chừng nào công tắc theo dõi còn bật — không cần bấm nút nào.
 *
 * **Current position fallback (2 layers, never `(0,0)`)** — same lesson as
 * `ZoneEditorViewModel`'s create-from-empty seeding (LLM.md §13 Fixed #11): self may have never
 * recorded a point (tracking never toggled on before hitting "Simulate" — a plausible real demo
 * sequence, `DemoDataSeeder` never seeds a location for self). Fall back to any OTHER member's
 * last known position before giving up; only fail if genuinely nobody has ever been located.
 */
class StartSimulationUseCase(
    private val zoneRepository: ZoneRepository,
    private val saveZoneUseCase: SaveZoneUseCase,
    private val observeMembersWithLastLocation: ObserveMembersWithLastLocationUseCase,
    private val trackingRepository: TrackingRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val locations = observeMembersWithLastLocation().first()
        val current = locations.firstOrNull { it.member.isSelf }?.lastLocation
            ?: locations.firstNotNullOfOrNull { it.lastLocation }
            ?: return AppResult.Failure(AppError.Validation(NO_POSITION_MESSAGE))

        val zones = zoneRepository.observeAll().first()
        val targetZone = if (zones.isEmpty()) {
            val sample = sampleZone(current.latitude, current.longitude)
            when (val saved = saveZoneUseCase(sample)) {
                is AppResult.Success -> saved.data
                is AppResult.Failure -> return AppResult.Failure(saved.error)
            }
        } else {
            zones.minBy { zone ->
                GeoDistance.haversineMeters(current.latitude, current.longitude, zone.latitude, zone.longitude)
            }
        }

        val blueprint = RouteBlueprint.build(current.latitude, current.longitude, targetZone)
        trackingRepository.runSimulation(blueprint)
        return AppResult.Success(Unit)
    }

    /** PRD §3.5 "nếu chưa có zone nào thì tạo trước một zone mẫu" — goes through
     * [SaveZoneUseCase] rather than `zoneRepository.save` directly, so the `MAX_ZONES` guard and
     * any future save-time rule apply to it exactly like a zone the user creates by hand. */
    private fun sampleZone(lat: Double, lng: Double) = Zone(
        id = UUID.randomUUID().toString(),
        name = SAMPLE_ZONE_NAME,
        latitude = lat,
        longitude = lng,
        radiusMeters = TrackingConstants.ZONE_RADIUS_DEFAULT_M.toFloat(),
        colorArgb = SAMPLE_ZONE_COLOR_ARGB,
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.now(),
    )

    private companion object {
        const val SAMPLE_ZONE_NAME = "Zone mẫu"

        /** `#1B6EF3` — `Theme.kt` `PrimaryBlue`. `:domain` cannot import Compose theme (LLM.md §2),
         * same literal-copy pattern as `HistoryContract.kt`'s `DEFAULT_MEMBER_COLOR_ARGB`. */
        const val SAMPLE_ZONE_COLOR_ARGB: Int = 0xFF1B6EF3.toInt()

        const val NO_POSITION_MESSAGE = "Chưa có vị trí nào được ghi nhận — bật theo dõi vị trí trước khi mô phỏng"
    }
}
