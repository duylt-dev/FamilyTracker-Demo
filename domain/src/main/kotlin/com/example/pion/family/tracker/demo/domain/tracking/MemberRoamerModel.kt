package com.example.pion.family.tracker.demo.domain.tracking

// Kiểu dữ liệu của MemberRoamer — tách khỏi `MemberRoamer.kt` để giữ file đó dưới 200 dòng
// (`.claude/rules/development-rules.md`), cùng lý do `ZoneCenterMap.kt`/`HistoryMap.kt` tách khỏi
// screen của chúng (LLM.md §3, §5).

/** Loại chặng — quyết định hướng đi có TẤT ĐỊNH hay không ([MemberRoamer.stableBearing]) và có
 * sinh dwell khi tới đích hay không (chỉ [ENTER_ZONE]). */
enum class LegKind { ENTER_ZONE, LEAVE_ZONE, WANDER }

/**
 * Đích của một chặng đi. [zoneId] là zone LIÊN QUAN tới chặng — đích cho [LegKind.ENTER_ZONE], zone
 * VỪA RỜI cho [LegKind.LEAVE_ZONE] (dùng để tra zone khi kiểm [RouteGeometryGuard]); `null` cho
 * [LegKind.WANDER]. [approachRadiusMeters] là khoảng cách từ đích tới điểm spawn khi thành viên ở
 * quá xa để đi bộ tới (xem [MemberRoamer.MAX_WALK_M]) — với đích trong zone, giá trị này được tính
 * sao cho điểm spawn LUÔN nằm ngoài ranh giới zone.
 */
data class RoamTarget(
    val latitude: Double,
    val longitude: Double,
    val zoneId: String?,
    val approachRadiusMeters: Double,
    val kind: LegKind,
)

/** Trạng thái đi lại của MỘT thành viên được theo dõi giữa hai lần [MemberRoamer.tick]. [path] khác
 * `null` nghĩa là đang bám một dãy điểm cụ thể tới [target] ([MemberRoamer.withPath]); `null` nghĩa
 * là đang chờ tầng gọi cấp một dãy điểm mới ([RoamStep.NeedPath]). [hasSpawned] gác nhánh dời vị
 * trí — xem KDoc [MemberRoamer.MAX_WALK_M]. */
data class RoamState(
    val latitude: Double,
    val longitude: Double,
    val target: RoamTarget? = null,
    val dwellTicksLeft: Int = 0,
    val bearingDegrees: Double = 0.0,
    val speedMps: Double = 0.0,
    val hasSpawned: Boolean = false,
    val path: ParametrizedPath? = null,
    val pathCursorMeters: Double = 0.0,
)

/** Kết quả một lần [MemberRoamer.tick] — hai pha vì lấy tuyến là việc `suspend`/có thể lỗi, mà
 * `:domain` không được biết điều đó (xem KDoc [MemberRoamer], "Vì sao hai pha"). */
sealed interface RoamStep {
    /** Chưa có [ParametrizedPath] cho [target] hiện tại của [from] — tầng gọi (`:data`) tự lấy một
     * dãy điểm rồi gọi [MemberRoamer.withPath] để tiếp tục. KHÔNG ghi điểm ở nhịp này. */
    data class NeedPath(val from: RoamState, val target: RoamTarget) : RoamStep

    /**
     * Đã có toạ độ mới (hoặc đang dwell, toạ độ không đổi) — tầng gọi ghi [RoamState.latitude]/
     * [RoamState.longitude]/[RoamState.bearingDegrees]/[RoamState.speedMps] thành một điểm.
     *
     * [spawnDistanceMeters] khác `null` ĐÚNG ở nhịp mà [RoamState.hasSpawned] chuyển `false -> true`,
     * và mang quãng đường của chính cú dời đó. **Đây là phép đo hình học duy nhất mà `:data` cần
     * cho log `sim_spawn`, và nó được tính ở đây chứ không ở `:data` vì NFR-4 của phase-02:**
     * `:data:test` không bật `returnDefaultValues` và không có Robolectric, nên một lời gọi
     * `android.location.Location.distanceBetween` trên nhánh này làm `MemberMovementSimulatorTest`
     * đỏ bằng `RuntimeException: ... not mocked` — đo thật, xem `reports/reviewer-phase-02-report.md`.
     * `:domain` đã có [GeoDistance]; `:data` chỉ đọc con số và ghi log.
     */
    data class Move(val state: RoamState, val spawnDistanceMeters: Double? = null) : RoamStep
}
