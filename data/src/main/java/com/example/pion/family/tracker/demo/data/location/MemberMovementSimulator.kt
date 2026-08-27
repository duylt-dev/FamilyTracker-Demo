package com.example.pion.family.tracker.demo.data.location

import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.model.EventSource
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteProvider
import com.example.pion.family.tracker.demo.domain.repository.ZoneEventRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.MemberRoamer
import com.example.pion.family.tracker.demo.domain.tracking.RoamState
import com.example.pion.family.tracker.demo.domain.tracking.RoamStep
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.domain.tracking.ZoneEvaluator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Nguồn di chuyển của các thành viên ĐƯỢC THEO DÕI (Minh/Lan) — và do đó là nơi DUY NHẤT sinh
 * `ZoneEvent` trong app (LLM.md §8.1). Self không còn là chủ thể của zone: GPS thật chỉ nuôi chấm
 * xanh trên bản đồ và polyline ở tab Lịch sử.
 *
 * Vì sao phải mô phỏng: Minh/Lan không có thiết bị thật nào phát vị trí lên. `DemoDataSeeder` ghi
 * cho mỗi người đúng MỘT điểm lúc cài app, nên nếu chỉ đổi "ai được tính" mà không có chuyển động
 * thì `ZoneEvaluator` không bao giờ có hai vị trí khác nhau để so, và không thông báo vào/rời zone
 * nào nổ được.
 *
 * **Điểm ở đây KHÔNG đi qua [LocationPointProcessor]/`LocationFilter`.** Bộ lọc tồn tại để loại
 * nhiễu GPS thật (`accuracy > 50m`, `speed > 200 km/h`); điểm mô phỏng không có nhiễu. Ghi qua
 * [MemberRepository.recordLocation], đúng cổng dành cho việc này.
 *
 * **[insideZoneIds] tách riêng theo từng thành viên.** Dùng chung một tập cho hai người sẽ khiến
 * Lan "thừa hưởng" ENTER của Minh và không bao giờ nhận ENTER của chính mình.
 *
 * **Phase-02 (PRD delta D1/D2/D3/D5):** [MemberRoamer.tick] trả `RoamStep` hai pha —
 * [RoamStep.NeedPath] không ghi điểm (chưa di chuyển); [MemberRoutePathResolver] cấp một dãy điểm.
 * Bearing/tốc độ ghi vào [LocationPoint] đọc thẳng từ [RoamState] — `PolylineFollower`/`GeoBearing`
 * tính thật, không còn `bearingDegrees = 0f` cứng.
 *
 * **Phase-04 (D5, `decisions.md` §C2):** lấy dãy điểm cho một chặng là ĐÚNG một hàm phase-04 đổi
 * (nay ở [MemberRoutePathResolver], tách khỏi lớp này ở review "VIỆC 4" — không mang bất biến
 * ZoneEvent bên dưới), mọi chỗ khác đứng yên — đó là bài kiểm tra seam của phase-02. TẦNG nào cấp
 * dãy điểm và theo thứ tự nào là việc riêng của `MemberRouteSource`, lớp này không biết.
 *
 * **KHÔNG đo hình học bằng API Android** (`Location.distanceBetween`…): `data/build.gradle.kts`
 * không có Robolectric/`returnDefaultValues`, nên lời gọi đó làm test đỏ bằng `not mocked` (NFR-4
 * phase-02) — mọi phép đo đã có sẵn trong [RoamState]/[RoamStep] do `:domain` tính.
 */
class MemberMovementSimulator(
    private val memberRepository: MemberRepository,
    private val zoneRepository: ZoneRepository,
    private val zoneEventRepository: ZoneEventRepository,
    private val memberRouteProvider: MemberRouteProvider,
) {
    private val roamStates = mutableMapOf<String, RoamState>()
    private val insideZoneIds = mutableMapOf<String, Set<String>>()
    private val randoms = mutableMapOf<String, Random>()
    private val pathResolver = MemberRoutePathResolver(memberRouteProvider)

    /**
     * Vòng lặp vô hạn — dừng bằng cách huỷ coroutine cha (`LocationTrackingService.familyJob`),
     * không có cờ dừng riêng. Một nhịp lỗi (Room ném lỗi, zone bị xoá giữa chừng) chỉ bỏ qua nhịp
     * đó và đi tiếp: mất một bước đi mô phỏng không đáng để giết cả chuyển động của gia đình.
     * [CancellationException] ném lại nguyên vẹn, nếu không huỷ job sẽ không bao giờ dừng được.
     */
    suspend fun run() {
        FtdLog.d(TAG, "member_roam_started intervalMs=${TrackingConstants.MEMBER_ROAM_INTERVAL_MS}")
        while (currentCoroutineContext().isActive) {
            try {
                tickOnce()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                FtdLog.e(TAG, "member_roam_error", throwable)
            }
            delay(TrackingConstants.MEMBER_ROAM_INTERVAL_MS)
        }
    }

    /** `internal` để test JVM thuần bơm từng nhịp một mà không cần chạy [run] (không đồng hồ giả,
     * không `delay` — cùng lý do `LocationPointProcessor` tách khỏi `LocationTrackingService`). */
    internal suspend fun tickOnce() {
        val members = memberRepository.observeAll().first().filterNot { it.isSelf }
        if (members.isEmpty()) return
        // Đọc lại zone MỖI nhịp: zone vừa tạo/xoá có hiệu lực ngay nhịp kế tiếp, không cần ai đi
        // báo cho bộ mô phỏng biết.
        val zones = zoneRepository.observeAll().first()
        val locations = memberRepository.observeLatestLocations().first()
        members.forEach { member -> moveOne(member, zones, locations) }
    }

    private suspend fun moveOne(member: Member, zones: List<Zone>, locations: Map<String, LocationPoint>) {
        val previous = roamStates[member.id] ?: seedState(member, zones, locations) ?: return
        val random = randomFor(member)

        when (val step = MemberRoamer.tick(previous, zones, random, memberSeed = member.id.hashCode())) {
            is RoamStep.NeedPath -> {
                // Chưa di chuyển ở nhịp này — cùng luật với dwell, không ghi điểm.
                roamStates[member.id] = MemberRoamer.withPath(step.from, pathResolver.resolve(member, step, zones))
            }
            is RoamStep.Move -> {
                val next = step.state
                roamStates[member.id] = next
                if (next.latitude == previous.latitude && next.longitude == previous.longitude) return

                // Quãng đường cú dời do `:domain` tính và trả ra (NFR-4) — `:data` chỉ ghi log.
                step.spawnDistanceMeters?.let { distanceM ->
                    FtdLog.d(TAG, "sim_spawn memberId=${member.id} distanceM=$distanceM")
                }

                val point = LocationPoint(
                    latitude = next.latitude,
                    longitude = next.longitude,
                    accuracyMeters = SIMULATED_ACCURACY_M,
                    speedMps = next.speedMps.toFloat(),
                    bearingDegrees = next.bearingDegrees.toFloat(),
                    recordedAt = Instant.now(),
                )
                memberRepository.recordLocation(member.id, point)
                raiseZoneEvents(member, point, zones)
            }
        }
    }

    /**
     * Trạng thái ban đầu lấy từ vị trí cuối cùng đã biết của thành viên. Tập "đang ở trong" được
     * seed bằng HÌNH HỌC (`previouslyInside = emptySet()` → `insideAfter` = đúng những zone chứa
     * điểm hiện tại), không phải bằng cách đọc lại `ZoneEventDao.latestPerZone` như bản cũ làm cho
     * self: hỏi thẳng vị trí vừa rẻ hơn vừa đúng hơn — không có ENTER ma cho zone mà thành viên
     * vốn đã đứng sẵn bên trong từ trước khi service này chạy.
     */
    private fun seedState(member: Member, zones: List<Zone>, locations: Map<String, LocationPoint>): RoamState? {
        val point = locations[member.id] ?: return null
        insideZoneIds[member.id] = ZoneEvaluator.evaluate(point, zones, emptySet()).insideAfter
        return RoamState(latitude = point.latitude, longitude = point.longitude)
    }

    private suspend fun raiseZoneEvents(member: Member, point: LocationPoint, zones: List<Zone>) {
        val evaluation = ZoneEvaluator.evaluate(point, zones, insideZoneIds[member.id].orEmpty())
        // Giao với danh sách zone hiện tại: `ZoneEvaluator` khởi tạo `insideAfter` từ tập truyền
        // vào và chỉ bỏ id ra khi thấy zone đó trong `zones`, nên một zone bị xoá lúc thành viên
        // đang đứng trong nó sẽ nằm lại trong tập này mãi mãi.
        val liveZoneIds = zones.mapTo(mutableSetOf()) { it.id }
        insideZoneIds[member.id] = evaluation.insideAfter intersect liveZoneIds

        evaluation.events.forEach { crossing ->
            zoneEventRepository.record(
                ZoneEvent(
                    id = UUID.randomUUID().toString(),
                    zoneId = crossing.zoneId,
                    zoneName = crossing.zoneName,
                    memberId = member.id,
                    type = crossing.type,
                    occurredAt = point.recordedAt,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    source = EventSource.FOREGROUND,
                ),
            )
        }
    }

    /** Một [Random] RIÊNG cho mỗi thành viên, gieo từ id: Minh và Lan chọn zone và hướng đi khác
     * nhau thay vì bước cùng một đường, và mỗi lần chạy lại cho cùng một chuỗi (tái hiện được khi
     * cần dựng lại một lỗi từ demo). */
    private fun randomFor(member: Member): Random = randoms.getOrPut(member.id) { Random(member.id.hashCode()) }

    private companion object {
        const val TAG = "FTD_EVENT"

        /** Dưới xa `MAX_ACCURACY_M` (50m) — cùng lựa chọn với `SimulatedLocationSource`. */
        const val SIMULATED_ACCURACY_M = 8f
    }
}
