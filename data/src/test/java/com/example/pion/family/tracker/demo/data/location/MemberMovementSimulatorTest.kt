package com.example.pion.family.tracker.demo.data.location

import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteProvider
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteRequest
import com.example.pion.family.tracker.demo.domain.repository.ZoneEventRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.SyntheticPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

private const val METERS_PER_DEGREE_LAT = 111_320.0
private const val TICKS_FOR_A_FULL_CYCLE = 200

/** F-10 (reviewer phase-02): ngưỡng "không quá N mẫu LIÊN TIẾP = 0f" — xem KDoc ca test dùng nó. */
private const val MAX_CONSECUTIVE_ZERO_BEARING_SAMPLES = 3

/**
 * JVM thuần, không Robolectric — cùng lý do `LocationPointProcessorTest` chạy được như vậy: lớp
 * này không extends `android.app.Service` và không gọi API Android nào (xem KDoc `ROAM_SPEED_MPS`
 * cho lý do tốc độ được suy ra thay vì đo bằng `Location.distanceBetween`).
 *
 * Bơm từng nhịp qua `tickOnce()` thay vì chạy `run()` — không đồng hồ giả, không `delay`, không
 * test bất định.
 */
class MemberMovementSimulatorTest {

    private val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
    private val minh = Member(id = "m-minh", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
    private val zone = zoneOf("z-truong", lat = 10.0, lng = 106.0, radius = 150f)

    @Test
    fun `self is never moved and never raises a zone event`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(self, minh),
            // Self đứng ĐÚNG giữa zone — đúng kịch bản người dùng báo lỗi: tạo zone quanh chính mình.
            locations = mapOf(self.id to pointAt(10.0, 106.0), minh.id to pointAt(northOf(10.0, 600.0), 106.0)),
        )
        val events = FakeZoneEventRepository()
        val simulator = simulatorWith(members, events = events)

        repeat(TICKS_FOR_A_FULL_CYCLE) { simulator.tickOnce() }

        assertTrue("self không được ghi thêm điểm nào", members.recorded.none { it.first == self.id })
        assertTrue("self không được là chủ thể của sự kiện zone nào", events.recorded.none { it.memberId == self.id })
    }

    @Test
    fun `a followed member walking into the zone raises ENTER then EXIT, attributed to them`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(self, minh),
            locations = mapOf(minh.id to pointAt(northOf(10.0, 600.0), 106.0)),
        )
        val events = FakeZoneEventRepository()
        val simulator = simulatorWith(members, events = events)

        repeat(TICKS_FOR_A_FULL_CYCLE) { simulator.tickOnce() }

        assertTrue("phải đi hết được một vòng vào-ra", events.recorded.size >= 2)
        assertTrue("mọi sự kiện phải thuộc về Minh", events.recorded.all { it.memberId == minh.id })
        assertEquals(zone.id, events.recorded.first().zoneId)
        assertEquals(ZoneEventType.ENTER, events.recorded[0].type)
        assertEquals(ZoneEventType.EXIT, events.recorded[1].type)
    }

    @Test
    fun `a member already standing inside a zone when tracking starts does not get a ghost ENTER`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(10.0, 106.0)), // đã ở giữa zone từ trước
        )
        val events = FakeZoneEventRepository()
        val simulator = simulatorWith(members, events = events)

        simulator.tickOnce()

        assertEquals(emptyList<ZoneEvent>(), events.recorded)
    }

    /**
     * F-10 (reviewer phase-02): `none { bearingDegrees == 0f }` báo SAI nếu một mẫu thật đi ĐÚNG
     * hướng bắc — bearing thật lúc đó CŨNG là `0.0f`, không phân biệt được với hằng số cứng cũ.
     * Sửa bằng "không quá [MAX_CONSECUTIVE_ZERO_BEARING_SAMPLES] mẫu LIÊN TIẾP = 0f": một mẫu đơn
     * lẻ trùng đúng hướng bắc là chuyện có thể xảy ra tự nhiên trên một đường cong; hàng loạt mẫu
     * liên tiếp giống hệt nhau ở `0f` thì không — đường đang bám liên tục đổi hướng mỗi nhịp
     * (`PolylineFollower`/`GeoBearing`), nên nhiều nhịp liên tiếp trùng CHÍNH XÁC cùng một bearing
     * gần như không thể xảy ra trừ khi bearing đang bị đóng băng lại — đúng bug NFR-3/QA-SRM-03 mà
     * ca này sinh ra để bắt. Ngưỡng chọn rộng rãi (không phải `0`, cũng không phải một nửa
     * [TICKS_FOR_A_FULL_CYCLE]) để không biến ca này thành một bản sao CHẶT HƠN bản cũ.
     */
    @Test
    fun `no long run of recorded bearingDegrees is stuck at 0f after 200 ticks on a curved path`() = runTest {
        // Lệch cả bắc lẫn đông so với zone — tránh trùng đúng 0/90/180/270 độ của cả đường thẳng
        // lẫn cung cong SyntheticPath (QA-SRM-03, phase-02).
        val members = FakeMemberRepository(
            members = listOf(self, minh),
            locations = mapOf(minh.id to pointAt(northOf(10.0, 500.0), eastOf(106.0, 300.0))),
        )
        val simulator = simulatorWith(members)

        repeat(TICKS_FOR_A_FULL_CYCLE) { simulator.tickOnce() }

        assertTrue("phải ghi được ít nhất vài điểm trong $TICKS_FOR_A_FULL_CYCLE nhịp", members.recorded.isNotEmpty())
        val longestZeroRun = longestConsecutiveZeroBearingRun(members.recorded.map { it.second.bearingDegrees })
        assertTrue(
            "bearing phải được TÍNH từ đường đang bám: $longestZeroRun mẫu LIÊN TIẾP = 0f nghĩa là nó đang " +
                "bị đóng băng ở một giá trị cố định, không còn được tính lại mỗi nhịp",
            longestZeroRun < MAX_CONSECUTIVE_ZERO_BEARING_SAMPLES,
        )
    }

    private fun longestConsecutiveZeroBearingRun(bearings: List<Float>): Int {
        var longest = 0
        var current = 0
        bearings.forEach { bearing ->
            current = if (bearing == 0f) current + 1 else 0
            longest = maxOf(longest, current)
        }
        return longest
    }

    @Test
    fun `dwelling inside a zone stops writing duplicate points`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(northOf(10.0, 600.0), 106.0)),
        )
        val simulator = simulatorWith(members)

        // Đủ nhịp để đi vào rồi đứng lại: nếu dwell vẫn ghi điểm, số điểm sẽ bằng đúng số nhịp.
        val ticks = 30
        repeat(ticks) { simulator.tickOnce() }

        assertTrue("dwell phải bỏ qua việc ghi điểm", members.recorded.size < ticks)
    }

    @Test
    fun `deleting the zone a member stands in leaves no stale state and raises nothing`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(northOf(10.0, 600.0), 106.0)),
        )
        val events = FakeZoneEventRepository()
        val zones = FakeZoneRepository(listOf(zone))
        val simulator = simulatorWith(members, zones = zones, events = events)

        repeat(TICKS_FOR_A_FULL_CYCLE / 2) { simulator.tickOnce() }
        val beforeDelete = events.recorded.size
        zones.replaceWith(emptyList())
        repeat(TICKS_FOR_A_FULL_CYCLE / 2) { simulator.tickOnce() }

        assertEquals("không zone nào tồn tại thì không sự kiện nào được sinh thêm", beforeDelete, events.recorded.size)
    }

    /**
     * **NFR-4 của phase-02, và nó là một ca THẬT.** Thành viên ở Mountain View, zone duy nhất ở
     * TP.HCM (~13 400 km > `MemberRoamer.MAX_WALK_M`), nên nhịp ĐẦU TIÊN chạy đúng nhánh spawn —
     * nhánh trước đây gọi `android.location.Location.distanceBetween` để dựng log `sim_spawn`.
     *
     * Bản đầu của ca này (`tester` phase-02) bọc `tickOnce()` trong `try/catch (RuntimeException)`
     * rồi `return@runTest` khi thấy chuỗi `"distanceBetween"`, và KHÔNG có assertion nào — nó đạt
     * cả khi mìn nổ lẫn khi không. Đo lại thì mìn ĐÃ nổ: bỏ lớp `catch` đi, ca này đỏ ngay bằng
     * `RuntimeException: Method distanceBetween in android.location.Location not mocked`
     * (`reports/reviewer-phase-02-report.md`). Quãng đường spawn nay do `:domain` tính và trả qua
     * `RoamStep.Move.spawnDistanceMeters`; ca này đỏ trở lại nếu ai đưa phép đo đó về `:data`.
     */
    @Test
    fun `NFR-4 the spawn branch runs in a pure JVM test and lands the member next to the zone`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(37.4220, -122.0841)), // Mountain View
        )
        val simulator = simulatorWith(members)

        // Không try/catch: một API Android chưa mock trên nhánh này làm ca đỏ, đúng như NFR-4 muốn.
        simulator.tickOnce()

        val spawned = members.recorded.single().second
        // `approachRadiusMeters` = 150×1.4 + 120 = 330m quanh tâm zone — dung sai 0.05° (~5.5km) là
        // thừa sức để phân biệt "đã dời sang TP.HCM" với "vẫn ở Mountain View", mà không khoá vào
        // hướng spawn ngẫu nhiên.
        assertEquals("spawn phải đưa thành viên tới cạnh zone", zone.latitude, spawned.latitude, 0.05)
        assertEquals("spawn phải đưa thành viên tới cạnh zone", zone.longitude, spawned.longitude, 0.05)
    }

    /** Nhánh spawn chỉ chạy MỘT lần (FR-4/QA-SRM-09) — nhịp sau phải là bước đi bám tuyến bình
     * thường, không phải một cú dời thứ hai. Khoá ở `:data` cạnh ca JVM thuần ở trên vì đây là nơi
     * `roamStates` thật sự sống qua các nhịp. */
    @Test
    fun `after the single spawn, no later tick jumps across the planet again`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(37.4220, -122.0841)),
        )
        val simulator = simulatorWith(members)

        repeat(20) { simulator.tickOnce() }

        val jumps = members.recorded.map { it.second }.zipWithNext().count { (a, b) ->
            kotlin.math.abs(b.latitude - a.latitude) > 0.05 || kotlin.math.abs(b.longitude - a.longitude) > 0.05
        }
        assertEquals("sau cú spawn duy nhất, không nhịp nào được nhảy tiếp", 0, jumps)
    }

    /**
     * Phase-04: `pathFor()` không còn tự vẽ `SyntheticPath` cho các chặng ENTER_ZONE/LEAVE_ZONE, nó
     * hỏi [MemberRouteProvider]. Ca này đứng ở vai "nguồn tuyến đã suy biến hết cỡ" (nhà cung cấp
     * lỗi, cache rỗng — `MemberRouteSource` thật đã tự rơi tới tầng 3) — CHỈ trả một đường thẳng hai
     * điểm (tệ hơn cả `SyntheticPath` thật), để chứng minh `MemberMovementSimulator` không quan tâm
     * hình học dãy điểm tới từ đâu: chuyển động vẫn đi hết một vòng vào-ra, không đứng yên, không
     * ném lỗi (FR-3, US-45).
     */
    @Test
    fun `a followed member still completes an ENTER-EXIT cycle when the route source degrades to a straight fallback`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(self, minh),
            locations = mapOf(minh.id to pointAt(northOf(10.0, 600.0), 106.0)),
        )
        val events = FakeZoneEventRepository()
        val degradedProvider = FakeMemberRouteProvider(onPath = { request -> listOf(request.from, request.to) })
        val simulator = simulatorWith(members, events = events, routes = degradedProvider)

        repeat(TICKS_FOR_A_FULL_CYCLE) { simulator.tickOnce() }

        assertTrue("phải đi hết được một vòng vào-ra dù nguồn tuyến suy biến", events.recorded.size >= 2)
        assertEquals(ZoneEventType.ENTER, events.recorded[0].type)
        assertEquals(ZoneEventType.EXIT, events.recorded[1].type)
    }

    /**
     * US-41 sống hay chết ở đúng dòng rẽ nhánh này: chặng có zone (`ENTER_ZONE`/`LEAVE_ZONE`) phải đi
     * qua [MemberRouteProvider.path] — cửa DUY NHẤT dẫn tới cache/nhà cung cấp — chứ không phải
     * [MemberRouteProvider.wander], cửa luôn trả `SyntheticPath`. **Đo bằng mutation ở lượt soát thứ
     * hai của `code-reviewer`: cho `pathFor` đẩy MỌI chặng qua `wander()` ⇒ 301/301 vẫn XANH.** Tức
     * là trước ca này, bám đường thật có thể bị gỡ khỏi bộ mô phỏng mà không một test nào đỏ — đúng
     * kiểu hỏng im lặng mà `LLM.md` §11 gọi là lỗ nghiệm thu.
     */
    @Test
    fun `a zone leg goes through path(), never through the wander door`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(northOf(10.0, 600.0), 106.0)),
        )
        val routes = FakeMemberRouteProvider()
        val simulator = simulatorWith(members, routes = routes)

        repeat(TICKS_FOR_A_FULL_CYCLE) { simulator.tickOnce() }

        assertTrue("chặng có zone phải hỏi cổng `path()`", routes.pathCalls > 0)
        assertEquals("có zone thì không chặng nào được đi cửa `wander()`", 0, routes.wanderCalls)
    }

    /** Mọi ca dựng simulator theo cùng một khuôn — chỉ truyền tham số khi ca đó CẦN giữ tham chiếu
     * tới fake để khẳng định, hoặc cần một fake cư xử khác. Mặc định: đúng một zone [zone], một kho
     * sự kiện không ai đọc tới, và nguồn tuyến trả về đúng `SyntheticPath` như trước phase-04. */
    private fun simulatorWith(
        members: FakeMemberRepository,
        zones: ZoneRepository = FakeZoneRepository(listOf(zone)),
        events: ZoneEventRepository = FakeZoneEventRepository(),
        routes: MemberRouteProvider = FakeMemberRouteProvider(),
    ) = MemberMovementSimulator(members, zones, events, routes)

    private fun northOf(latitude: Double, meters: Double) = latitude + meters / METERS_PER_DEGREE_LAT

    /** Xấp xỉ: dùng mét/độ VĨ cho kinh độ, nên ở vĩ độ 10° lệch ~1.5% so với mét thật. Test chỉ cần
     * "chệch sang đông một quãng", không cần con số chính xác — đừng chép công thức này đi nơi khác. */
    private fun eastOf(longitude: Double, meters: Double) = longitude + meters / METERS_PER_DEGREE_LAT

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
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-22T08:00:00Z"),
    )
}

/** Fake theo LLM.md §11 — không thư viện mock. */
private class FakeMemberRepository(
    members: List<Member>,
    private val locations: Map<String, LocationPoint>,
) : MemberRepository {
    val recorded = mutableListOf<Pair<String, LocationPoint>>()
    private val membersFlow = MutableStateFlow(members).asStateFlow()

    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = flowOf(locations)
    override suspend fun recordLocation(memberId: String, point: LocationPoint) {
        recorded += memberId to point
    }
}

private class FakeZoneRepository(zones: List<Zone>) : ZoneRepository {
    private val flow = MutableStateFlow(zones)

    fun replaceWith(zones: List<Zone>) {
        flow.value = zones
    }

    override fun observeAll(): Flow<List<Zone>> = flow.asStateFlow()
    override suspend fun save(zone: Zone) = AppResult.Success(zone)
    override suspend fun delete(zoneId: String) = AppResult.Success(Unit)
    override suspend fun count(): Int = flow.value.size
    override suspend fun exists(zoneId: String): Boolean = flow.value.any { it.id == zoneId }
}

/** Fake theo LLM.md §11 — mặc định trả về đúng [SyntheticPath] mà `MemberMovementSimulator` tự vẽ
 * trước phase-04, để mọi test có sẵn không đổi hành vi chỉ vì thêm một tham số constructor mới. */
private class FakeMemberRouteProvider(
    private val onPath: (MemberRouteRequest) -> List<GeoPoint> = { request ->
        SyntheticPath.between(request.from, request.to, seed = request.memberId.hashCode())
    },
) : MemberRouteProvider {
    /** Hai bộ đếm TÁCH RIÊNG: hai hàm của cổng có chi phí hạn ngạch khác nhau (`path` có thể gọi
     * mạng, `wander` thì không bao giờ), nên "chặng nào đi cửa nào" là một luật phải khoá được —
     * xem ca `a zone leg goes through path()…`. */
    var pathCalls: Int = 0
        private set
    var wanderCalls: Int = 0
        private set

    override suspend fun path(request: MemberRouteRequest): List<GeoPoint> {
        pathCalls++
        return onPath(request)
    }

    /** `WANDER` không cần cấu hình riêng cho các test ở trên (chỉ ENTER_ZONE/LEAVE_ZONE được kiểm) —
     * mặc định cùng công thức `SyntheticPath` mà `MemberMovementSimulator` từng gọi trực tiếp. */
    override suspend fun wander(memberId: String, from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        wanderCalls++
        return SyntheticPath.between(from, to, seed = memberId.hashCode())
    }
}

private class FakeZoneEventRepository : ZoneEventRepository {
    val recorded = mutableListOf<ZoneEvent>()
    override fun observeTimeline(sinceDays: Int): Flow<List<ZoneEvent>> = flowOf(emptyList())
    override suspend fun record(event: ZoneEvent) {
        recorded += event
    }
    override suspend fun purgeOlderThan(days: Int): Int = 0
}
