package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

private const val METERS_PER_DEGREE_LAT = 111_320.0
private const val MEMBER_SEED = 4242

/**
 * Bộ đi lại mô phỏng của các thành viên được theo dõi. Test cuối cùng
 * (`a full roam cycle …`) là test quan trọng nhất file: nó chạy roamer qua `ZoneEvaluator` thật và
 * khoá lời hứa duy nhất mà cả tính năng dựa vào — mỗi vòng sinh ĐÚNG một ENTER rồi ĐÚNG một EXIT,
 * xen kẽ, không dội. Phase-02: viết lại theo API hai pha (`RoamStep.NeedPath`/`withPath`), nhưng
 * test đó GIỮ NGUYÊN tên, assertion và `TICKS_FOR_SEVERAL_CYCLES` — chỉ vòng lặp gọi qua helper
 * [advance] mới (`decisions.md` §C4, Risk Assessment "Cao").
 */
class MemberRoamerTest {

    private val zone = zoneOf("z-truong", lat = 10.0, lng = 106.0, radius = 150f)

    @Test
    fun `standing outside, the next target lands inside a zone`() {
        val target = MemberRoamer.nextTarget(northOf(10.0, 600.0), 106.0, listOf(zone), Random(1), MEMBER_SEED)

        assertEquals(zone.id, target.zoneId)
        assertEquals(LegKind.ENTER_ZONE, target.kind)
        assertTrue(
            "đích phải nằm trong ranh giới zone",
            distanceToCentre(target.latitude, target.longitude) < zone.radiusMeters,
        )
    }

    @Test
    fun `standing inside, the next target clears the exit hysteresis buffer`() {
        val target = MemberRoamer.nextTarget(10.0, 106.0, listOf(zone), Random(1), MEMBER_SEED)

        assertEquals("chặng đi RA phải nhớ mình đang rời zone nào, để RouteGeometryGuard tra được", zone.id, target.zoneId)
        assertEquals(LegKind.LEAVE_ZONE, target.kind)
        // `ZoneEvaluator.exitsAt` chỉ sinh EXIT khi d > radius + ZONE_EXIT_BUFFER_M. Đích nằm đúng
        // ở mép cộng buffer thì hysteresis nuốt mất EXIT và thành viên kẹt trong zone vĩnh viễn.
        assertTrue(
            "đích phải ra ngoài cả vùng đệm hysteresis",
            distanceToCentre(target.latitude, target.longitude) >
                zone.radiusMeters + TrackingConstants.ZONE_EXIT_BUFFER_M,
        )
    }

    @Test
    fun `with no zones at all, the member wanders instead of standing still`() {
        val target = MemberRoamer.nextTarget(10.0, 106.0, zones = emptyList(), random = Random(1), memberSeed = MEMBER_SEED)

        assertNull(target.zoneId)
        assertEquals(LegKind.WANDER, target.kind)
        assertTrue(distanceToCentre(target.latitude, target.longitude) > 0.0)
    }

    @Test
    fun `advancing toward a distant target moves without overshooting the step budget`() {
        val start = RoamState(latitude = northOf(10.0, 600.0), longitude = 106.0)

        val next = advance(start, listOf(zone), Random(1))

        val travelled = GeoDistance.haversineMeters(start.latitude, start.longitude, next.latitude, next.longitude)
        assertTrue("phải có di chuyển thật ở lần advance() đầu tiên", travelled > 0.0)
        assertTrue("không được vượt ngân sách một bước (bảo toàn đỉnh có thể cắt ngắn)", travelled <= MemberRoamer.STEP_METERS + 1e-6)
        assertNotNull("đích được giữ lại để nhịp sau đi tiếp cùng một hướng", next.target)
    }

    @Test
    fun `a member on the far side of the planet is repositioned OUTSIDE the zone, not into it`() {
        // Đúng kịch bản thật: DemoDataSeeder đặt Minh ở TP.HCM, emulator báo vị trí Mountain View.
        val start = RoamState(latitude = 37.4220, longitude = -122.0841)

        val step = MemberRoamer.tick(start, listOf(zone), Random(1), MEMBER_SEED)
        val next = (step as RoamStep.Move).state

        val distance = distanceToCentre(next.latitude, next.longitude)
        assertTrue("phải được thả NGOÀI ranh giới, nếu không sẽ không có lần cắt nào sinh ENTER", distance > zone.radiusMeters)
        assertTrue("và đủ gần để đi bộ nốt", distance < MemberRoamer.MAX_WALK_M)
        assertTrue("phải đánh dấu đã spawn", next.hasSpawned)

        // NFR-4 (phase-02 review): quãng đường cú dời do `:domain` tính và trả ra ở ĐÚNG nhịp này.
        // `:data` chỉ đọc để ghi log `sim_spawn` — nó không được tự đo bằng API Android, vì
        // `:data:test` không có Robolectric/`returnDefaultValues`. Bỏ dòng gán ở `MemberRoamer` thì
        // log im lặng biến mất và QA-SRM-09/11 mất cách đếm; ca này đỏ trước khi điều đó xảy ra.
        val spawnDistance = requireNotNull(step.spawnDistanceMeters) {
            "nhịp spawn phải mang theo quãng đường của chính nó"
        }
        assertEquals(
            "quãng đường spawn phải là khoảng cách thật từ chỗ cũ tới điểm được thả",
            GeoDistance.haversineMeters(start.latitude, start.longitude, next.latitude, next.longitude),
            spawnDistance,
            1e-6,
        )
        assertTrue("một cú spawn theo định nghĩa là xa hơn MAX_WALK_M", spawnDistance > MemberRoamer.MAX_WALK_M)
    }

    @Test
    fun `an ordinary walking tick carries no spawn distance`() {
        val start = RoamState(latitude = northOf(10.0, 600.0), longitude = 106.0)
        val step = MemberRoamer.tick(start, listOf(zone), Random(1), MEMBER_SEED)

        // `spawnDistanceMeters` khác null ĐÚNG ở nhịp spawn — nếu nó rò sang nhịp thường, `:data`
        // sẽ ghi một dòng `sim_spawn` cho mỗi bước đi và QA-SRM-09/11 đếm sai hoàn toàn.
        assertNull((step as? RoamStep.Move)?.spawnDistanceMeters)
    }

    @Test
    fun `arriving inside a zone starts the dwell, and dwell ticks do not move the member`() {
        val target = MemberRoamer.nextTarget(northOf(10.0, 600.0), 106.0, listOf(zone), Random(1), MEMBER_SEED)
        // Path ngắn hơn STEP_METERS -> một tick() duy nhất là hết đường, kích hoạt dwell.
        val approachStart = GeoPoint(target.latitude - metersToLatDegrees(5.0), target.longitude)
        val almostThere = RoamState(
            latitude = approachStart.latitude,
            longitude = approachStart.longitude,
            target = target,
            path = PolylineFollower.parametrize(listOf(approachStart, GeoPoint(target.latitude, target.longitude))),
        )

        val arrived = (MemberRoamer.tick(almostThere, listOf(zone), Random(1), MEMBER_SEED) as RoamStep.Move).state
        assertEquals(MemberRoamer.DWELL_TICKS, arrived.dwellTicksLeft)

        val dwelling = (MemberRoamer.tick(arrived, listOf(zone), Random(1), MEMBER_SEED) as RoamStep.Move).state
        assertEquals(arrived.latitude, dwelling.latitude, 0.0)
        assertEquals(arrived.longitude, dwelling.longitude, 0.0)
        assertEquals(MemberRoamer.DWELL_TICKS - 1, dwelling.dwellTicksLeft)
    }

    @Test
    fun `a full roam cycle produces alternating ENTER and EXIT, starting with ENTER`() {
        val zones = listOf(zone)
        val random = Random(42)
        var state = RoamState(latitude = northOf(10.0, 600.0), longitude = 106.0)
        var inside = emptySet<String>()
        val events = mutableListOf<ZoneEventType>()

        repeat(TICKS_FOR_SEVERAL_CYCLES) {
            state = advance(state, zones, random)
            val evaluation = ZoneEvaluator.evaluate(pointAt(state.latitude, state.longitude), zones, inside)
            inside = evaluation.insideAfter
            events += evaluation.events.map { it.type }
        }

        assertTrue("phải chạy hết được ít nhất một vòng vào-ra trong $TICKS_FOR_SEVERAL_CYCLES nhịp", events.size >= 2)
        assertEquals(ZoneEventType.ENTER, events.first())
        assertTrue(
            "không được dội: hai sự kiện liên tiếp cùng loại nghĩa là ranh giới bị cắt hai lần cùng chiều",
            events.zipWithNext().none { (a, b) -> a == b },
        )
    }

    @Test
    fun `the ENTER-EXIT invariant also holds at the minimum zone radius (50m)`() {
        val smallZone = zoneOf("z-nho", lat = 10.0, lng = 106.0, radius = TrackingConstants.ZONE_RADIUS_MIN_M.toFloat())
        val zones = listOf(smallZone)
        val random = Random(42)
        var state = RoamState(latitude = northOf(10.0, 300.0), longitude = 106.0)
        var inside = emptySet<String>()
        val events = mutableListOf<ZoneEventType>()

        repeat(TICKS_FOR_SEVERAL_CYCLES) {
            state = advance(state, zones, random)
            val evaluation = ZoneEvaluator.evaluate(pointAt(state.latitude, state.longitude), zones, inside)
            inside = evaluation.insideAfter
            events += evaluation.events.map { it.type }
        }

        assertTrue("phải chạy hết được ít nhất một vòng vào-ra ở bán kính 50m", events.size >= 2)
        assertEquals(ZoneEventType.ENTER, events.first())
        assertTrue("không được dội, kể cả ở zone nhỏ nhất cho phép", events.zipWithNext().none { (a, b) -> a == b })
    }

    @Test
    fun `each ENTER of the same zone is separated by more than the dedupe window, in milliseconds`() {
        val zones = listOf(zone)
        val random = Random(42)
        var state = RoamState(latitude = northOf(10.0, 600.0), longitude = 106.0)
        var inside = emptySet<String>()
        val enterTickIndices = mutableListOf<Int>()

        repeat(TICKS_FOR_SEVERAL_CYCLES) { tickIndex ->
            state = advance(state, zones, random)
            val evaluation = ZoneEvaluator.evaluate(pointAt(state.latitude, state.longitude), zones, inside)
            inside = evaluation.insideAfter
            if (evaluation.events.any { it.type == ZoneEventType.ENTER }) enterTickIndices += tickIndex
        }

        assertTrue("cần ít nhất 2 lần ENTER để so khoảng cách", enterTickIndices.size >= 2)
        enterTickIndices.zipWithNext().forEach { (first, second) ->
            val gapMs = (second - first) * TrackingConstants.MEMBER_ROAM_INTERVAL_MS
            assertTrue(
                "khoảng cách $gapMs ms giữa hai ENTER phải lớn hơn cửa sổ khử trùng lặp",
                gapMs > TrackingConstants.EVENT_DEDUPE_WINDOW_MS,
            )
        }
    }

    @Test
    fun `across many ticks, exactly one step covers more than 2x STEP_METERS - the spawn`() {
        var state = RoamState(latitude = 37.4220, longitude = -122.0841) // Mountain View, xa TP.HCM
        val random = Random(11)
        var bigJumps = 0

        repeat(TICKS_FOR_SEVERAL_CYCLES) {
            val previous = state
            state = advance(state, listOf(zone), random)
            val moved = GeoDistance.haversineMeters(previous.latitude, previous.longitude, state.latitude, state.longitude)
            if (moved > 2 * MemberRoamer.STEP_METERS) bigJumps++
        }

        assertEquals("đúng một bước là cú spawn, mọi bước khác đi dọc đường", 1, bigJumps)
    }

    @Test
    fun `restarting the simulation 3 times each spawns exactly once, no leak between runs`() {
        repeat(3) { runIndex ->
            var state = RoamState(latitude = 37.4220, longitude = -122.0841)
            val random = Random(runIndex)
            var spawns = 0

            repeat(50) {
                val previous = state
                state = advance(state, listOf(zone), random)
                val moved = GeoDistance.haversineMeters(previous.latitude, previous.longitude, state.latitude, state.longitude)
                if (moved > 2 * MemberRoamer.STEP_METERS) spawns++
            }

            assertEquals("lần khởi động lại #$runIndex phải spawn đúng 1 lần, không cộng dồn từ lần trước", 1, spawns)
        }
    }

    @Test
    fun `a route hugging the zone edge is rejected by the geometry guard`() {
        // Tuyến hỏng cố ý: cắt ranh giới zone 4 lần (giống RouteGeometryGuardTest) — mô phỏng thứ
        // phase-04 phải từ chối khi RoutingProvider trả một hình học xấu.
        val badRoute = listOf(300.0, 100.0, 300.0, 100.0, 300.0).map { offset ->
            GeoPoint(zone.latitude + offset / METERS_PER_DEGREE_LAT, zone.longitude)
        }

        assertFalse(RouteGeometryGuard.isUsable(badRoute, zone, LegKind.ENTER_ZONE))
    }

    // F-5 (reviewer phase-02): ca "the roamer with a synthetic path does not dither when entering
    // a zone" đã bị XOÁ ở đây, không sửa. Nó dùng TRÙNG KHÍT input của
    // `a full roam cycle produces alternating ENTER and EXIT, starting with ENTER` phía trên nhưng
    // với assertion YẾU HƠN (thiếu `assertEquals(ENTER, events.first())`) — một bản sao không thể
    // đỏ một mình: nếu ca bất biến kia không đỏ trước thì ca này không bao giờ bắt được lỗi, nó chỉ
    // tạo cảm giác an toàn giả. Test thật cho F-5 ("cho một badRoute qua withPath rồi khẳng định
    // roamer TỪ CHỐI và rơi về SyntheticPath") cần `MemberRoamer.withPath` gọi `RouteGeometryGuard`
    // — hôm nay `withPath` KHÔNG gọi guard (KDoc của chính `RouteGeometryGuard` ghi rõ "Chưa có
    // người gọi thật trong phase-02"), nên ca đó sẽ đạt vô điều kiện: một ca giả nữa, đúng bệnh mà
    // F-2 vừa sửa. Việc nối dây thuộc phase-04 (phase-02's Next Steps + LLM.md §13 Open).

    /** Một vòng đầy đủ ~40 nhịp (đi vào ~12, dwell 20, đi ra ~6) — đủ cho vài vòng. */
    private val TICKS_FOR_SEVERAL_CYCLES = 200

    /**
     * Giải quyết [RoamStep.NeedPath] bằng [SyntheticPath] rồi gọi lại `tick()` — mô phỏng đúng thứ
     * `MemberMovementSimulator.pathFor()` sẽ làm ở `:data` (phase-02: luôn SyntheticPath; phase-04:
     * routing thật). `advance()` không bao giờ trả về một state còn "đang chờ path" — hợp đồng của
     * helper là LUÔN có một bước di chuyển thật sau mỗi lần gọi.
     */
    private fun advance(state: RoamState, zones: List<Zone>, random: Random, memberSeed: Int = MEMBER_SEED): RoamState {
        val step = MemberRoamer.tick(state, zones, random, memberSeed)
        if (step is RoamStep.Move) return step.state
        step as RoamStep.NeedPath
        val points = SyntheticPath.between(
            GeoPoint(step.from.latitude, step.from.longitude),
            GeoPoint(step.target.latitude, step.target.longitude),
            memberSeed,
        )
        val withPath = MemberRoamer.withPath(step.from, points)
        return (MemberRoamer.tick(withPath, zones, random, memberSeed) as RoamStep.Move).state
    }

    private fun northOf(latitude: Double, meters: Double) = latitude + meters / METERS_PER_DEGREE_LAT

    private fun metersToLatDegrees(meters: Double) = meters / METERS_PER_DEGREE_LAT

    private fun distanceToCentre(lat: Double, lng: Double) =
        GeoDistance.haversineMeters(lat, lng, zone.latitude, zone.longitude)

    private fun zoneOf(id: String, lat: Double, lng: Double, radius: Float) = Zone(
        id = id,
        name = id,
        latitude = lat,
        longitude = lng,
        radiusMeters = radius,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-22T00:00:00Z"),
    )

    private fun pointAt(lat: Double, lng: Double) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 8f,
        speedMps = 20f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-22T08:00:00Z"),
    )
}
