package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import kotlin.random.Random

/**
 * Bộ đi lại mô phỏng cho các thành viên được theo dõi (Minh/Lan) — hàm thuần, không `Context`,
 * không coroutine, không Play Services (LLM.md §8.2). `MemberMovementSimulator` (`:data`) là nơi
 * duy nhất gọi nó, mỗi [TrackingConstants.MEMBER_ROAM_INTERVAL_MS] một lần.
 *
 * **Phase-02 (PRD delta D1/D2/D3/D5, `decisions.md` §C1/§C4):** cách đi chuyển từ nội suy đường
 * thẳng sang bám một [ParametrizedPath] qua [PolylineFollower]. Nhịp lấy mẫu KHÔNG đổi ([DWELL_TICKS]
 * neo vào [TrackingConstants.EVENT_DEDUPE_WINDOW_MS] như trước) — chỉ tốc độ đổi
 * ([TrackingConstants.SIM_MEMBER_SPEED_MPS]), và [STEP_METERS] suy ra từ đó.
 *
 * **Vì sao hai pha (`NeedPath` → [withPath]) chứ không truyền sẵn một `Map<zoneId, path>`:** lấy
 * tuyến là việc `suspend` và có thể thất bại (phase-04, `RoutingProvider`); `:domain` không được
 * biết điều đó. Trả về [RoamStep.NeedPath] để tầng gọi tự quyết định lấy tuyến ở đâu, giữ
 * `MemberRoamer` hoàn toàn đồng bộ và thuần, và giữ `MemberRoamerTest` là JUnit không coroutine.
 */
object MemberRoamer {

    /**
     * Suy ra từ [TrackingConstants.SIM_MEMBER_SPEED_MPS] × nhịp tick — KHÔNG còn là hằng số tự do.
     * = 8.3 × 2.5 = 20.75m. Đây là bước đi DỌC THEO đường ([PolylineFollower]), không phải đường
     * chim bay như bản cũ (từng ghi "~72 km/h ô tô" — số đó đã sai từ khi hạ tốc độ, xoá hẳn).
     */
    val STEP_METERS: Double = TrackingConstants.SIM_MEMBER_SPEED_MPS * (TrackingConstants.MEMBER_ROAM_INTERVAL_MS / MILLIS_PER_SECOND)

    /**
     * Số tick đứng yên sau khi tới đích trong zone. SUY RA từ cửa sổ khử trùng lặp, không phải một
     * con số chọn tay: riêng thời gian đứng yên đã phải dài hơn
     * [TrackingConstants.EVENT_DEDUPE_WINDOW_MS] thì chu kỳ vào→ra→vào cùng một zone mới CHẮC CHẮN
     * nằm ngoài cửa sổ đó, bất kể zone to hay nhỏ. Phase-02 hạ tốc độ 2.4× nhưng KHÔNG đụng hằng số
     * này (`decisions.md` §C4): quãng đường mỗi nhịp giảm làm một vòng dài RA, dwell một mình đã
     * vượt cửa sổ nên đi lại chỉ cộng thêm dự phòng.
     */
    val DWELL_TICKS: Int =
        (TrackingConstants.EVENT_DEDUPE_WINDOW_MS / TrackingConstants.MEMBER_ROAM_INTERVAL_MS).toInt() +
            DWELL_SAFETY_TICKS

    /** Ra ngoài mép zone bao xa khi rời. Phải lớn hơn [TrackingConstants.ZONE_EXIT_BUFFER_M] (30m),
     * nếu không hysteresis giữ nguyên trạng thái "đang ở trong" và EXIT không bao giờ sinh ra. */
    internal const val LEAVE_MARGIN_M: Double = 120.0

    /**
     * Xa hơn mức này thì thành viên KHÔNG đi bộ tới đích được — phase-02 (PRD delta D3/§4.1) hạ cấp
     * nhánh này thành spawn ĐÚNG MỘT LẦN mỗi thành viên mỗi lần bắt đầu mô phỏng
     * ([RoamState.hasSpawned]), thay vì đánh giá lại mỗi tick như bản cũ (bug "nhảy 13.000km" — một
     * zone mới tạo ở nửa kia địa cầu khiến thành viên dời vị trí lặp lại vô hạn). Sau khi đã spawn,
     * gặp lại ngưỡng này (zone mới quá xa) không dời thêm lần nào nữa — đích đó bị THAY bằng một
     * đích [LegKind.WANDER] quanh vị trí hiện tại.
     */
    internal const val MAX_WALK_M: Double = 5_000.0

    /** Bán kính đi loanh quanh khi chưa có zone nào để nhắm tới, hoặc khi đích quá xa sau khi đã spawn. */
    internal const val WANDER_RADIUS_M: Double = 400.0

    /** Đích trong zone nằm ở `bán kính × hệ số` tính từ tâm, không phải đúng tâm — hai thành viên
     * cùng nhắm vào một zone sẽ không chồng marker lên nhau. */
    internal const val ZONE_TARGET_INSET: Double = 0.4

    private const val DWELL_SAFETY_TICKS: Int = 6
    private const val MILLIS_PER_SECOND: Double = 1_000.0
    private const val SEED_MULTIPLIER: Int = 31

    /**
     * Một nhịp đi. [memberSeed] (`member.id.hashCode()`, ổn định giữa các lần chạy) nuôi
     * [stableBearing] — KHÔNG phải [random], để hình học chặng ENTER_ZONE/LEAVE_ZONE tất định (xem
     * KDoc [stableBearing]). Dwell trả [RoamStep.Move] với toạ độ không đổi; tầng gọi bỏ qua việc
     * ghi điểm khi thấy vậy (không có gì mới để ghi).
     */
    fun tick(
        state: RoamState,
        zones: List<Zone>,
        random: Random,
        memberSeed: Int,
        stepMeters: Double = STEP_METERS,
    ): RoamStep {
        if (state.dwellTicksLeft > 0) {
            return RoamStep.Move(state.copy(dwellTicksLeft = state.dwellTicksLeft - 1, speedMps = 0.0))
        }

        val currentTarget = state.target
        val targetZoneGone = currentTarget?.zoneId != null && zones.none { it.id == currentTarget.zoneId }
        if (targetZoneGone) {
            // Zone bị xoá giữa chừng khi đang bám tuyến tới nó — bỏ target/path cũ, chọn lại từ đầu.
            return tick(state.copy(target = null, path = null, pathCursorMeters = 0.0), zones, random, memberSeed, stepMeters)
        }

        val path = state.path
        if (path != null) return advanceAlongPath(state, path, stepMeters)

        val target = currentTarget ?: nextTarget(state.latitude, state.longitude, zones, random, memberSeed)
        val distance = GeoDistance.haversineMeters(state.latitude, state.longitude, target.latitude, target.longitude)

        if (distance > MAX_WALK_M) {
            if (!state.hasSpawned) {
                val (lat, lng) = pointAtBearing(
                    target.latitude,
                    target.longitude,
                    target.approachRadiusMeters,
                    random.nextDouble(FULL_CIRCLE_DEGREES),
                )
                return RoamStep.Move(
                    RoamState(latitude = lat, longitude = lng, target = target, hasSpawned = true),
                    // Quãng đường của chính cú dời — tính ở ĐÂY, không ở `:data` (NFR-4, xem KDoc
                    // [RoamStep.Move.spawnDistanceMeters]).
                    spawnDistanceMeters = GeoDistance.haversineMeters(state.latitude, state.longitude, lat, lng),
                )
            }
            val wander = wanderTarget(state.latitude, state.longitude, random)
            val redirected = state.copy(target = wander)
            return RoamStep.NeedPath(redirected, wander)
        }

        val withTarget = state.copy(target = target)
        return RoamStep.NeedPath(withTarget, target)
    }

    /** Gắn một tuyến vừa lấy được (bất kể nguồn — [SyntheticPath] phase-02, `MemberRouteSource`
     * phase-04) vào trạng thái đang chờ ([RoamStep.NeedPath]). Tầng gọi (`:data`) là nơi DUY NHẤT
     * biết lấy tuyến ở đâu — xem KDoc lớp, "Vì sao hai pha". */
    fun withPath(state: RoamState, points: List<GeoPoint>): RoamState =
        state.copy(path = PolylineFollower.parametrize(points), pathCursorMeters = 0.0)

    private fun advanceAlongPath(state: RoamState, path: ParametrizedPath, stepMeters: Double): RoamStep.Move {
        val target = requireNotNull(state.target) { "path đang bám phải luôn đi kèm một target" }
        val progress = PolylineFollower.advance(path, state.pathCursorMeters, stepMeters)
        val speedMps = progress.movedMeters / (TrackingConstants.MEMBER_ROAM_INTERVAL_MS / MILLIS_PER_SECOND)

        if (!progress.finished) {
            return RoamStep.Move(
                state.copy(
                    latitude = progress.point.latitude,
                    longitude = progress.point.longitude,
                    bearingDegrees = progress.bearingDegrees,
                    speedMps = speedMps,
                    pathCursorMeters = progress.cursorMeters,
                ),
            )
        }
        return RoamStep.Move(
            RoamState(
                latitude = progress.point.latitude,
                longitude = progress.point.longitude,
                target = null,
                dwellTicksLeft = if (target.kind == LegKind.ENTER_ZONE) DWELL_TICKS else 0,
                bearingDegrees = progress.bearingDegrees,
                speedMps = speedMps,
                hasSpawned = state.hasSpawned,
            ),
        )
    }

    /**
     * Đang ở trong một zone thì chặng kế tiếp là đi RA (đảm bảo EXIT); ngoài mọi zone thì nhắm vào
     * một zone ngẫu nhiên (đảm bảo ENTER); chưa có zone nào thì đi loanh quanh. **Chọn ZONE vẫn
     * ngẫu nhiên** (`random.nextInt`); hình học của chặng đã chọn (hướng tiếp cận/rời) thì tất định
     * qua [stableBearing] — xem KDoc hàm đó cho lý do.
     */
    internal fun nextTarget(lat: Double, lng: Double, zones: List<Zone>, random: Random, memberSeed: Int): RoamTarget {
        val inside = zones.firstOrNull { zone ->
            GeoDistance.haversineMeters(lat, lng, zone.latitude, zone.longitude) < zone.radiusMeters
        }
        if (inside != null) {
            val bearing = stableBearing(memberSeed, inside.id, LegKind.LEAVE_ZONE)
            val (exitLat, exitLng) = pointAtBearing(inside.latitude, inside.longitude, inside.radiusMeters + LEAVE_MARGIN_M, bearing)
            return RoamTarget(exitLat, exitLng, zoneId = inside.id, approachRadiusMeters = WANDER_RADIUS_M, kind = LegKind.LEAVE_ZONE)
        }
        if (zones.isNotEmpty()) {
            val zone = zones[random.nextInt(zones.size)]
            val radius = zone.radiusMeters.toDouble()
            val bearing = stableBearing(memberSeed, zone.id, LegKind.ENTER_ZONE)
            val (targetLat, targetLng) = pointAtBearing(zone.latitude, zone.longitude, radius * ZONE_TARGET_INSET, bearing)
            // (1 + inset) × R + margin: đích lệch tâm tối đa `inset × R`, nên điểm khởi hành cách
            // TÂM ít nhất `R + margin` kể cả ở hướng bất lợi nhất — luôn nằm ngoài ranh giới.
            return RoamTarget(
                latitude = targetLat,
                longitude = targetLng,
                zoneId = zone.id,
                approachRadiusMeters = radius * (1 + ZONE_TARGET_INSET) + LEAVE_MARGIN_M,
                kind = LegKind.ENTER_ZONE,
            )
        }
        return wanderTarget(lat, lng, random)
    }

    /**
     * Bearing TẤT ĐỊNH của một chặng ENTER_ZONE/LEAVE_ZONE — hàm thuần của
     * ([memberSeed], [zoneId], [kind]), KHÔNG đọc [Random] dùng chung của roamer. **Vì sao:** nếu
     * hướng chặng đổi ngẫu nhiên mỗi vòng, cặp (điểm đi, điểm đến) mà phase-04 gửi cho nhà cung cấp
     * tuyến đường cũng đổi mỗi vòng, và bộ nhớ đệm theo zone (`decisions.md` §C2, D5 tầng 2) không
     * bao giờ trúng — hạn ngạch 500 credit/ngày cháy trong vài giờ demo. Dùng `Random(seed)` MỚI
     * mỗi lần gọi (không phải instance dùng chung) để giá trị chỉ phụ thuộc ba tham số, ổn định
     * giữa các lần chạy ứng dụng khác nhau — [String.hashCode] có thuật toán cố định theo đặc tả
     * JDK nên ổn định giữa các lần chạy cùng phiên bản JVM.
     */
    internal fun stableBearing(memberSeed: Int, zoneId: String, kind: LegKind): Double {
        val combined = memberSeed * SEED_MULTIPLIER + zoneId.hashCode() * SEED_MULTIPLIER + kind.ordinal
        return Random(combined).nextDouble(FULL_CIRCLE_DEGREES)
    }
}
