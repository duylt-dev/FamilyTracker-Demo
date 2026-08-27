package com.example.pion.family.tracker.demo.domain.tracking

/**
 * Every tracking threshold in one file, per PRD §6, so QA can read it without opening code.
 * If a number appears anywhere else, QA reads one value and the code runs another — phase-03
 * Key Insight #7.
 */
object TrackingConstants {
    /** PRD §6. Ít hơn → lộ trình mượt hơn, tốn pin hơn. Dùng bởi foreground service (phase-04). */
    const val LOCATION_INTERVAL_MS: Long = 10_000L

    /**
     * Nhịp một bước của [MemberRoamer] — bộ đi lại mô phỏng của các thành viên được theo dõi.
     * KHÔNG phải một ngưỡng của PRD §6: đây là núm vặn của phần demo (Minh/Lan không có thiết bị
     * thật để phát vị trí), đặt cạnh các hằng số kia vì nó cũng là "một con số điều khiển hành vi
     * theo dõi" mà QA cần đọc được ở một chỗ. Nhỏ hơn → marker mượt hơn, ghi Room dày hơn.
     */
    const val MEMBER_ROAM_INTERVAL_MS: Long = 2_500L

    /** PRD §6. [LocationFilter] — nhỏ hơn → polyline rối khi đứng yên. So với điểm ĐƯỢC GIỮ, không phải điểm vừa nhận. */
    const val MIN_DISTANCE_M: Double = 10.0

    /** PRD §6. [LocationFilter] — lớn hơn → nhận điểm rác khi ở trong nhà. */
    const val MAX_ACCURACY_M: Double = 50.0

    /** PRD §6. [LocationFilter] — lớn hơn → một cú nhảy GPS kéo polyline đi xa. */
    const val MAX_SPEED_KMH: Double = 200.0

    /** PRD §6. [ZoneEvaluator] hysteresis khi ra khỏi zone (`d > radius + buffer`) — nhỏ hơn → dội thông báo khi đứng ở mép zone. */
    const val ZONE_EXIT_BUFFER_M: Double = 30.0

    /** PRD §6. [ZoneEventDeduper] cửa sổ khử trùng lặp cùng khoá (zoneId, memberId, type) — nhỏ hơn → 2 thông báo cho 1 lần vào zone. */
    const val EVENT_DEDUPE_WINDOW_MS: Long = 60_000L

    /** PRD §6. [RouteSplitter] ngưỡng cắt chuyến mới (5 phút) — quyết định cách tách chuyến đi. */
    const val SESSION_GAP_MS: Long = 300_000L

    /** PRD §6. [com.example.pion.family.tracker.demo.domain.usecase.PurgeOldHistoryUseCase] default — lớn hơn → màn History vẽ chậm dần. */
    const val HISTORY_RETENTION_DAYS: Int = 7

    /** PRD §6. Giới hạn CỨNG của Play Services cho mỗi app — KHÔNG được tăng, dù muốn. Chặn ở [com.example.pion.family.tracker.demo.domain.usecase.SaveZoneUseCase]. */
    const val MAX_ZONES: Int = 100

    /** PRD §6. Bán kính zone nhỏ nhất cho phép khi tạo/sửa (phase-06 editor). */
    const val ZONE_RADIUS_MIN_M: Double = 50.0

    /** PRD §6. Bán kính zone lớn nhất cho phép khi tạo/sửa (phase-06 editor). */
    const val ZONE_RADIUS_MAX_M: Double = 2_000.0

    /** PRD §6. Bán kính mặc định gợi ý khi tạo zone mới (phase-06 editor). */
    const val ZONE_RADIUS_DEFAULT_M: Double = 150.0

    /**
     * PRD delta §4.2 (phase-02, D5) — tốc độ mô phỏng của [MemberRoamer], thay cho 20 m/s (~72 km/h,
     * "xe chạy trên phố như trên cao tốc") hôm nay. [MemberRoamer.STEP_METERS] suy ra trực tiếp từ
     * hằng số này. Lớn hơn → tới zone nhanh hơn nhưng trông sai với tốc độ đường phố. Nhỏ hơn → một
     * vòng ENTER→EXIT kéo dài hơn — `decisions.md` §C5 định luật chốt số dựa trên đo thật, KHÔNG
     * được vượt trần cứng 13.9 m/s (50 km/h).
     *
     * **ĐÃ CHỐT ở smooth-road plan phase-06 — B4 đóng, giữ nguyên 8.3.** Đo tất định
     * ([MemberRoamerLapTimeTest]), zone 150 m: **ENTER→EXIT = 120.0 s**, dưới trần 180 s của luật
     * C5 ⇒ nhánh "giữ 8.3". Zone 50 m cho 92.5 s. Con số đáng tin **chỉ vì** đường tổng hợp và
     * polyline GraphHopper THẬT nay cho ĐÚNG cùng 120.0 s; trước `LLM.md` §13 Fixed #32 chúng lệch
     * nhau ~2× (tốc độ phụ thuộc mật độ đỉnh của tuyến) nên không có số nào để chốt theo.
     *
     * Đổi hằng số này thì `AnimatedMarkerPositionsThresholdTest` (`:ui`) sẽ ĐỎ — đó là cố ý: ngưỡng
     * snap 207.5 m suy ra từ đây, tính lại ngưỡng chứ đừng sửa con số trong test.
     */
    const val SIM_MEMBER_SPEED_MPS: Double = 8.3

    /**
     * PRD delta §4.2 (phase-02, D5, US-41) — ngưỡng QA dùng để phán một vị trí mô phỏng có "nằm
     * trên đường" hay không, đo từ dãy điểm [MemberRoamer] đang bám ([PolylineFollower]). Lớn hơn →
     * test QA-SRM-01/02 không bắt được lỗi cắt góc thật. Nhỏ hơn → test đỏ vì sai số hình học của
     * phép giải mã polyline (phase-04) chứ không phải lỗi thật.
     *
     * **Cập nhật phase-06 (`LLM.md` §13 Fixed #32):** bảo toàn đỉnh nay CÓ ĐIỀU KIỆN, nên sai lệch
     * thật không còn bằng 0 tuyệt đối mà **≤ `MAX_CORNER_CUT_M`**, và hằng số đó được suy ra bằng
     * `SIM_ROAD_TOLERANCE_M / 5`. Hai con số này nay đi cùng nhau: nới dung sai ở đây là nới luôn
     * mức cắt góc mà `PolylineFollower` cho phép.
     */
    const val SIM_ROAD_TOLERANCE_M: Double = 10.0

    // --- Routing plan phase-04 research — NOT PRD §6. This file's header above says every
    // threshold comes from PRD §6; these six do not, they come from the routing plan's own
    // research pass (`plans/260824-1335-pluggable-routing-provider/phase-04-domain-reroute-and-arrival.md`).
    // Kept here anyway because QA still needs one place to read every tracking/navigation number.

    /**
     * [com.example.pion.family.tracker.demo.domain.tracking.RerouteEvaluator] step 6 — how far
     * off the route (metres) before a sample counts as "off route" at all.
     * Smaller (<30m): reroutes on every GPS wobble inside a dense alley.
     * Larger (>60m): the user has switched to a different street and the app still points at the old route.
     */
    const val OFF_ROUTE_TOLERANCE_M: Double = 45.0

    /**
     * [com.example.pion.family.tracker.demo.domain.tracking.RerouteEvaluator] step 6 — consecutive
     * off-route samples required before rerouting.
     * Smaller (1 sample): a single noisy fix triggers a reroute.
     * Larger (6 samples / 60s at the 10s tick): a full minute lost before the app rescues the user.
     */
    const val OFF_ROUTE_CONSECUTIVE_SAMPLES: Int = 3

    /**
     * [com.example.pion.family.tracker.demo.domain.tracking.RerouteEvaluator] step 5 — how far
     * (metres) the target may drift from the current route's endpoint before that counts as "moved".
     * Smaller (<100m): the followed member wandering in place still triggers a reroute.
     * Larger (>300m): the target has to run far before the route ever catches up.
     */
    const val DESTINATION_MOVED_TOLERANCE_M: Double = 200.0

    /**
     * [com.example.pion.family.tracker.demo.domain.tracking.RerouteEvaluator] step 4 — minimum
     * time (ms) between two provider calls, gates before every other reroute reason.
     * Smaller (<30s): burns provider quota fast, real money.
     * Larger (>120s): visibly slow to react to a real off-route event.
     */
    const val REROUTE_DEBOUNCE_MS: Long = 60_000L

    /**
     * [com.example.pion.family.tracker.demo.domain.tracking.RerouteEvaluator] step 1 — distance
     * (metres) to the target below which the app declares "arrived" and stops rerouting.
     * Smaller (<30m): keeps navigating all the way up to the target's front door, more than needed.
     * Larger (>100m): announces "arrived" while still a whole block away.
     */
    const val ARRIVAL_M: Double = 50.0

    /**
     * [com.example.pion.family.tracker.demo.domain.tracking.RerouteEvaluator] step 2 — distance
     * (metres) above which an already-arrived state clears (hysteresis paired with [ARRIVAL_M]).
     * Smaller buffer (<10m over ARRIVAL_M): flickers between arrived/not-arrived on GPS noise.
     * Larger buffer (>50m over ARRIVAL_M): still says "arrived" after the user has walked away.
     */
    const val ARRIVAL_EXIT_M: Double = 70.0
}
