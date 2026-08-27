package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.RouteSourceInfo
import com.example.pion.family.tracker.demo.domain.model.RouteSourceKind
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteRequest
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import com.example.pion.family.tracker.demo.domain.tracking.LegKind
import com.example.pion.family.tracker.demo.domain.tracking.MemberRoamer
import com.example.pion.family.tracker.demo.domain.tracking.RouteGeometryGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

private const val METERS_PER_DEGREE_LAT = 111_320.0

/** Khoá cache mà `MemberRouteSource.cacheKeyFor` PHẢI sinh ra cho [MemberRouteSourceTest.enterRequest] —
 * hợp đồng nguyên văn ở `decisions.md` §C2 "Khoá cache", cùng chuỗi với `OnDevicePolylineCacheTest.KEY`. */
private const val ENTER_CACHE_KEY = "m-minh_z-truong_ENTER_ZONE_10.00000_106.00000_150"

/**
 * JVM thuần — fake [RoutingProvider] viết tay (LLM.md §11, không thư viện mock), cache thật trên một
 * thư mục tạm (đúng như `OnDevicePolylineCacheTest`). Không kiểm bằng chế độ máy bay bằng tay (S2
 * KDoc, phase-04 Implementation Step 9) — một fake ném lỗi/treo quá 10s chứng minh đúng cùng một
 * điều nhưng tất định và chạy được trong CI.
 */
class MemberRouteSourceTest {

    private val zone = Zone(
        id = "z-truong",
        name = "z-truong",
        latitude = 10.0,
        longitude = 106.0,
        radiusMeters = 150f,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-22T00:00:00Z"),
    )

    /** Hai mốc của MỌI chặng trong file: tâm zone, và một điểm cách 300m về phía bắc — ngoài cả bán
     * kính 150m lẫn `ZONE_EXIT_BUFFER_M`. `ENTER_ZONE` đi từ [northOfZone] vào [zoneCenter],
     * `LEAVE_ZONE` đi ngược lại; đặt tên một lần ở đây thay vì rải phép cộng độ vĩ ra 5 chỗ. */
    private val zoneCenter = GeoPoint(zone.latitude, zone.longitude)
    private val northOfZone = GeoPoint(zone.latitude + 300.0 / METERS_PER_DEGREE_LAT, zone.longitude)

    private lateinit var routesDir: File

    @Before
    fun setUp() {
        routesDir = Files.createTempDirectory("routes").toFile()
    }

    @After
    fun tearDown() {
        routesDir.deleteRecursively()
    }

    /**
     * P0 fix — chạy thật trên `emulator-5554` bắt được, `MapViewModelTest` (`:ui`) thì không: mở
     * app, chưa bật theo dõi, chưa từng gọi [MemberRouteSource.path]/[MemberRouteSource.wander] một
     * lần nào, dải ghi công vẫn hiện nhãn "Đường thẳng ước tính" — vỡ FR-4/S4 phase-05 ("trạng thái
     * thứ ba: ẩn hẳn"). Nguyên nhân: `RouteSourceAggregator._current` từng khởi tạo bằng
     * `RouteSourceInfo(SYNTHETIC, emptyList())` thay vì `null` — một `StateFlow` luôn có giá trị,
     * nên `observeSource()` phát SYNTHETIC ngay lập tức, trước khi bất kỳ thành viên nào báo cáo.
     * Test double của `MapViewModelTest` không lộ lỗi vì fake ở đó không tự phát gì cho tới khi test
     * bơm vào — chỉ instance thật (`MutableStateFlow` khởi tạo có giá trị) mới có hành vi này, nên
     * ca này BẮT BUỘC sống ở `:data`, không phải `:ui`.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `observeSource emits nothing before any member has ever reported`() = runTest {
        val source = sourceWith(FakeRoutingProvider { AppResult.Success(directionsOf(goodEnterRoute())) })

        var emittedCount = 0
        val collector = launch { source.observeSource().collect { emittedCount++ } }
        advanceUntilIdle()
        collector.cancel()

        assertFalse(
            "chưa path()/wander() lần nào thì observeSource() không được phát gì (FR-4/S4)",
            emittedCount > 0,
        )
    }

    /** Ca đối chứng của ca trên — khi CÓ báo cáo, `observeSource()` phải hoạt động lại bình thường
     * ngay từ lần phát ĐẦU TIÊN, không phải chờ thêm một lần cập nhật nữa. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `after the first path() call observeSource emits exactly one value`() = runTest {
        val source = sourceWith(FakeRoutingProvider { AppResult.Success(directionsOf(goodEnterRoute())) })

        source.path(enterRequest())

        val received = mutableListOf<RouteSourceInfo>()
        val collector = launch { source.observeSource().collect { received += it } }
        advanceUntilIdle()
        collector.cancel()

        assertEquals(1, received.size)
        assertEquals(RouteSourceKind.PROVIDER, received.single().kind)
    }

    @Test
    fun `S1 a successful provider response is cached, published as PROVIDER, and returned as-is`() = runTest {
        val directions = directionsOf(goodEnterRoute())
        val source = sourceWith(FakeRoutingProvider { AppResult.Success(directions) })

        val points = source.path(enterRequest())

        assertEquals(directions.points, points)
        assertEquals(RouteSourceKind.PROVIDER, source.observeSource().first().kind)
        assertEquals(directions.attribution, source.observeSource().first().attribution)
    }

    /**
     * S2 — khi khoá đã có trong cache TỪ TRƯỚC và nhà cung cấp bắt đầu lỗi, lần gọi sau vẫn trả
     * ĐÚNG tuyến đã cache (không phải `SyntheticPath`, không phải một tuyến hỏng). **Sửa lại ở code
     * review phase-04 "VIỆC D": ca này KHÔNG khoá được THỨ TỰ tầng**, dù đặt tên ban đầu ngụ ý vậy —
     * dù kiểm cache trước hay provider trước, cả hai thứ tự đều hội tụ về cache khi lần gọi thứ hai
     * hỏng (provider-first cũng thử provider, thấy lỗi, rồi mới đọc cache và trúng). Thứ tự tầng
     * THẬT SỰ (cache trước, đúng Architecture phase-04) được khoá bởi ca `NFR-2` ngay dưới đây: nó
     * đếm `provider.calls`, phân biệt được "provider không hề bị hỏi lại" (cache-first, đúng) với
     * "provider bị hỏi rồi mới rơi về cache" (provider-first, sai) — điều S2 không phân biệt được.
     */
    @Test
    fun `S2 a second call with the same request is served from cache, never asking the provider again`() = runTest {
        val cachedDirections = directionsOf(goodEnterRoute())
        val source = sourceWith(
            FakeRoutingProvider { call ->
                // Nhà cung cấp fetch được ĐÚNG một lần rồi mới hỏng — S2 là ca "hỏng SAU khi cache
                // đã có", không phải ca "hỏng ngay từ đầu" (đó là S4).
                if (call == 1) AppResult.Success(cachedDirections) else AppResult.Failure(AppError.Network("401"))
            },
        )
        val request = enterRequest()

        val firstCallPoints = source.path(request)
        val secondCallPoints = source.path(request)

        assertEquals("lần đầu phải là tuyến của nhà cung cấp", cachedDirections.points, firstCallPoints)
        assertEquals(
            "lần hai phải là ĐÚNG tuyến đã cache — không phải SyntheticPath, không phải 401 giả lập",
            cachedDirections.points,
            secondCallPoints,
        )
        assertEquals(RouteSourceKind.CACHE, source.observeSource().first().kind)
    }

    /**
     * NFR-2/QA-SRM-36 — phiên bản chạy được trong CI của lời hứa "từ vòng thứ hai trở đi là 0
     * request" (`decisions.md` §C2 Key Insight #2). S7 (Step 10, thủ công trên emulator) đếm log
     * trong 10 phút; ca này khẳng định ĐÚNG cơ chế sinh ra con số đó, tất định, không cần chờ.
     */
    @Test
    fun `NFR-2 calling path() twice with the same request only calls the provider once`() = runTest {
        val directions = directionsOf(goodEnterRoute())
        val provider = FakeRoutingProvider { AppResult.Success(directions) }
        val source = sourceWith(provider)
        val request = enterRequest()

        val firstCallPoints = source.path(request)
        val secondCallPoints = source.path(request)

        assertEquals("lần hai phải trả ĐÚNG dãy điểm đã cache", firstCallPoints, secondCallPoints)
        assertEquals(RouteSourceKind.CACHE, source.observeSource().first().kind)
        assertEquals("provider chỉ được hỏi ở lần cache-miss đầu tiên, không hỏi lại lần hai", 1, provider.calls)
    }

    @Test
    fun `S4 401, 429 and 400 with no cache all fall to synthetic, and movement does not stop`() = runTest {
        // Ba mã lỗi QA-SRM-15 đòi kiểm — cùng một hành vi ở tầng này, khác nhau chỉ ở message.
        listOf(AppError.Network("401"), AppError.Network("429"), AppError.Validation("400"))
            .forEach { assertDegradesToSynthetic(it) }
    }

    @Test
    fun `S5 a provider that never answers times out at 10s and falls to synthetic`() = runTest {
        val source = sourceWith(
            FakeRoutingProvider {
                delay(20_000)
                AppResult.Success(directionsOf(goodEnterRoute()))
            },
        )

        val points = source.path(enterRequest())

        assertTrue("timeout vẫn phải cho ra một dãy điểm để chuyển động không đứt", points.isNotEmpty())
        assertEquals(RouteSourceKind.SYNTHETIC, source.observeSource().first().kind)
    }

    @Test
    fun `S8 a route that hugs the zone edge is rejected by the geometry guard and falls to synthetic`() = runTest {
        val source = sourceWith(FakeRoutingProvider { AppResult.Success(directionsOf(bouncingRoute())) })

        val points = source.path(enterRequest())

        assertTrue("một tuyến bị từ chối vẫn phải cho ra một dãy điểm để chuyển động không đứt", points.isNotEmpty())
        assertEquals(RouteSourceKind.SYNTHETIC, source.observeSource().first().kind)
    }

    @Test
    fun `WANDER-shaped geometry is never requested from this port in production — LEAVE_ZONE still works end to end`() = runTest {
        val directions = directionsOf(goodLeaveRoute(), attribution = listOf("Valhalla", "OpenStreetMap contributors"))
        val source = sourceWith(FakeRoutingProvider { AppResult.Success(directions) })

        val points = source.path(leaveRequest())

        assertEquals(directions.points, points)
        assertEquals(RouteSourceKind.PROVIDER, source.observeSource().first().kind)
    }

    /**
     * Khoá cache là một HỢP ĐỒNG (`decisions.md` §C2 "Khoá cache"), không phải chi tiết nội bộ:
     * `"{memberId}_{zoneId}_{kind}_{lat5}_{lng5}_{r}"`. Sửa zone ⇒ khoá đổi ⇒ tự miss, và đó là cơ
     * chế vô hiệu hoá DUY NHẤT (researcher-01 Q3). Khẳng định thẳng TÊN FILE vì đó là thứ duy nhất
     * quan sát được từ ngoài lớp. **Thêm ở review phase-04:** mutation test cho thấy bỏ `kind`, bỏ
     * bán kính hoặc bỏ `memberId` khỏi khoá đều KHÔNG làm một ca nào đỏ trước khi có ca này.
     */
    @Test
    fun `the cache key carries memberId, zoneId, leg kind, zone centre and radius`() = runTest {
        val source = sourceWith(FakeRoutingProvider { AppResult.Success(directionsOf(goodEnterRoute())) })

        source.path(enterRequest())

        assertEquals(listOf("$ENTER_CACHE_KEY.json"), routesDir.list()?.sorted())
    }

    /** PRD Q13 (`decisions.md` §C2): mỗi thành viên một tuyến. Cùng zone, cùng chặng, khác người ⇒
     * KHÔNG được đọc lại cache của nhau, nên nhà cung cấp phải bị hỏi đúng hai lần. */
    @Test
    fun `two members asking for the same zone leg do not share one cached route`() = runTest {
        val provider = FakeRoutingProvider { AppResult.Success(directionsOf(goodEnterRoute())) }
        val source = sourceWith(provider)

        source.path(enterRequest())
        source.path(enterRequest().copy(memberId = "m-lan"))

        assertEquals("khoá cache phải tách theo memberId", 2, provider.calls)
    }

    /** researcher-01 Q3: sửa zone ⇒ khoá đổi ⇒ tự miss. Bán kính rơi khỏi khoá thì một zone vừa
     * được người dùng thu nhỏ vẫn đi trên tuyến của bán kính cũ mãi mãi. */
    @Test
    fun `editing the zone radius invalidates the cached route`() = runTest {
        val provider = FakeRoutingProvider { AppResult.Success(directionsOf(goodEnterRoute())) }
        val source = sourceWith(provider)

        source.path(enterRequest())
        source.path(enterRequest().copy(zone = zone.copy(radiusMeters = 80f)))

        assertEquals("bán kính zone phải nằm trong khoá cache", 2, provider.calls)
    }

    /**
     * `RouteGeometryGuard` phải chạy trên tầng CACHE, không chỉ trên tầng provider (phase-04
     * Implementation Step 4; `LLM.md` §11 "guard hình học chạy trên tầng 1 VÀ 2"). Một tuyến đã nằm
     * sẵn trong cache — từ một lần chạy cũ, hoặc một file bị sửa tay — phá bất biến ENTER/EXIT y hệt
     * một tuyến mới tải về. **Thêm ở review phase-04:** bỏ guard khỏi nhánh cache KHÔNG làm ca nào
     * đỏ trước khi có ca này.
     */
    @Test
    fun `a cached route that fails the geometry guard is rejected and the provider is asked instead`() = runTest {
        OnDevicePolylineCache(routesDir, Json { ignoreUnknownKeys = true })
            .put(ENTER_CACHE_KEY, bouncingRoute(), listOf("GraphHopper"), engineId = "graphhopper")
        val good = directionsOf(goodEnterRoute())
        val provider = FakeRoutingProvider { AppResult.Success(good) }
        val source = sourceWith(provider)

        val points = source.path(enterRequest())

        assertEquals("tuyến trong cache trượt guard thì không được dùng", good.points, points)
        assertEquals(RouteSourceKind.PROVIDER, source.observeSource().first().kind)
        assertEquals("phải hỏi nhà cung cấp đúng một lần sau khi từ chối cache", 1, provider.calls)
    }

    /**
     * P0 — tái hiện đúng lỗi đo thật trên `emulator-5554` 2026-08-26 (`reports/dev-phase-04-report.md`
     * §0 "VIỆC 1"): tầng cache có thể giữ một tuyến tính cho một `from` CŨ vì khoá cache cố ý không
     * chứa `from`. Trước bản sửa này, tuyến cache được nhận thẳng ⇒ `MemberRoamer.withPath` đặt
     * `pathCursorMeters = 0.0` ⇒ thành viên bị dời về đỉnh đầu polyline — đo thật gốc cache của Lan
     * cách vị trí hiện tại **876.09 m**, cú nhảy quan sát được **872.99 m**. Test này dùng 900 m
     * (cùng bậc với số đo thật) để không phụ thuộc một biên giới hạn giả định nhỏ hơn thực tế.
     */
    @Test
    fun `a cached route whose origin is far from the current position is treated as a miss and re-fetched`() = runTest {
        val staleOrigin = GeoPoint(zone.latitude + 900.0 / METERS_PER_DEGREE_LAT, zone.longitude)
        OnDevicePolylineCache(routesDir, Json { ignoreUnknownKeys = true })
            .put(ENTER_CACHE_KEY, listOf(staleOrigin, zoneCenter), listOf("GraphHopper"), engineId = "graphhopper")
        val fresh = directionsOf(goodEnterRoute())
        val provider = FakeRoutingProvider { AppResult.Success(fresh) }
        val source = sourceWith(provider)

        val points = source.path(enterRequest())

        assertEquals("gốc cache cũ bị từ chối, phải là tuyến MỚI từ nhà cung cấp", fresh.points, points)
        assertTrue(
            "tuyến trả về phải bắt đầu gần vị trí hiện tại, không phải gốc cache cũ",
            RouteGeometryGuard.startsNear(points, enterRequest().from, MemberRoamer.STEP_METERS),
        )
        assertEquals("phải hỏi nhà cung cấp đúng một lần sau khi từ chối gốc cache cũ", 1, provider.calls)
        assertEquals(RouteSourceKind.PROVIDER, source.observeSource().first().kind)
    }

    /**
     * Đối chứng của ca trên: gốc cache lệch nhưng TRONG ngưỡng một bước (`MemberRoamer.STEP_METERS`
     * = 20.75 m, đo thật 14.24 m sau khi sửa) vẫn phải trúng cache. Không được nới cách sửa thành
     * "từ chối bất kỳ độ lệch nào khác 0" — làm vậy giết NFR-2 (không còn tận dụng cache) để đổi lấy
     * một sự an toàn không ai cần: sai số làm tròn một bước là bình thường, không phải teleport.
     */
    @Test
    fun `a cached route whose origin is within one step still hits cache, no provider call`() = runTest {
        val nearOrigin = GeoPoint(zone.latitude + (300.0 - 14.0) / METERS_PER_DEGREE_LAT, zone.longitude)
        val cached = listOf(nearOrigin, zoneCenter)
        OnDevicePolylineCache(routesDir, Json { ignoreUnknownKeys = true })
            .put(ENTER_CACHE_KEY, cached, listOf("GraphHopper"), engineId = "graphhopper")
        val provider = FakeRoutingProvider { AppResult.Success(directionsOf(goodEnterRoute())) }
        val source = sourceWith(provider)

        val points = source.path(enterRequest())

        assertEquals("gốc trong ngưỡng một bước vẫn phải trúng cache", cached, points)
        assertEquals(RouteSourceKind.CACHE, source.observeSource().first().kind)
        assertEquals("không được hỏi nhà cung cấp khi gốc còn trong ngưỡng", 0, provider.calls)
    }

    /**
     * Code review phase-04 "VIỆC B" — `observeSource()` phải tổng hợp theo TỪNG thành viên, không
     * phải ghi-sau-thắng. Minh đang PROVIDER, Lan rơi về SYNTHETIC (provider lỗi ở lần gọi thứ hai,
     * không cache) ⇒ vẫn phải hiện attribution của Minh (còn ai đó đang dùng dữ liệu OSM thật thì
     * vẫn phải ghi công, `docs/routing-and-map-attribution.md` §3). Rồi CHÍNH Minh cũng chuyển sang
     * `WANDER` (không zone nào) ⇒ không còn ai ở tầng OSM ⇒ attribution phải VỀ RỖNG.
     */
    @Test
    fun `observeSource reports PROVIDER attribution while one member is on it, even if another is synthetic`() = runTest {
        val providerDirections = directionsOf(goodEnterRoute())
        val provider = FakeRoutingProvider { call ->
            if (call == 1) AppResult.Success(providerDirections) else AppResult.Failure(AppError.Network("no route"))
        }
        val source = sourceWith(provider)

        source.path(enterRequest()) // "m-minh" -> PROVIDER (lần gọi 1 thành công)
        source.path(enterRequest().copy(memberId = "m-lan")) // "m-lan" -> lần gọi 2 lỗi, không cache -> SYNTHETIC

        assertEquals(RouteSourceKind.PROVIDER, source.observeSource().first().kind)
        assertEquals(providerDirections.attribution, source.observeSource().first().attribution)

        source.wander("m-minh", zoneCenter, northOfZone) // Minh cũng rời khỏi tầng OSM

        assertEquals(RouteSourceKind.SYNTHETIC, source.observeSource().first().kind)
        assertTrue(
            "không còn ai chạy tầng OSM thì attribution phải rỗng",
            source.observeSource().first().attribution.isEmpty(),
        )
    }

    /** Ca riêng cho đúng câu hỏi "một thành viên đang PROVIDER chuyển sang WANDER" (tách khỏi ca
     * hai-thành-viên ở trên để lỗi báo rõ đúng nguyên nhân nếu một trong hai ca đỏ). */
    @Test
    fun `a member currently on PROVIDER clears its OSM attribution when it moves onto a WANDER leg`() = runTest {
        val providerDirections = directionsOf(goodEnterRoute())
        val source = sourceWith(FakeRoutingProvider { AppResult.Success(providerDirections) })

        source.path(enterRequest())
        assertEquals(RouteSourceKind.PROVIDER, source.observeSource().first().kind)

        source.wander("m-minh", zoneCenter, northOfZone)

        assertEquals(RouteSourceKind.SYNTHETIC, source.observeSource().first().kind)
        assertTrue(
            "chặng WANDER phải xoá attribution cũ của chính thành viên đó",
            source.observeSource().first().attribution.isEmpty(),
        )
    }

    /**
     * Ghi công gộp phải KHỬ TRÙNG LẶP và GIỮ THỨ TỰ xuất hiện: hai thành viên chạy hai engine khác
     * nhau đều kèm `"OpenStreetMap contributors"`, và dải ghi công của phase-05 hiện thẳng danh sách
     * này — in "OpenStreetMap contributors" hai lần là một lỗi hiển thị, không phải chi tiết nội bộ
     * (`docs/routing-and-map-attribution.md` §3 đòi credit "không che, tách bạch rõ").
     * **Thêm ở lượt soát thứ hai:** bỏ `.distinct()` khỏi `RouteSourceAggregator` KHÔNG làm ca nào
     * đỏ trước khi có ca này.
     */
    @Test
    fun `aggregated attribution de-duplicates shared credits and keeps first-seen order`() = runTest {
        val graphHopper = directionsOf(goodEnterRoute())
        val valhalla = directionsOf(goodEnterRoute(), attribution = listOf("Valhalla", "OpenStreetMap contributors"))
        val source = sourceWith(FakeRoutingProvider { call -> AppResult.Success(if (call == 1) graphHopper else valhalla) })

        source.path(enterRequest())
        source.path(enterRequest().copy(memberId = "m-lan"))

        assertEquals(
            listOf("GraphHopper", "OpenStreetMap contributors", "Valhalla"),
            source.observeSource().first().attribution,
        )
    }

    /**
     * Nửa còn lại của luật hạn ngạch NFR-2/QA-SRM-36 (phase-04 Step 6): chặng `WANDER` KHÔNG BAO GIỜ
     * chạm nhà cung cấp. Trước đây điều này chỉ đúng "theo cấu trúc" — **đo bằng mutation ở lượt soát
     * thứ hai: thêm một lời gọi `routingProvider.directions(...)` vào `wander()` không làm ca nào
     * đỏ**. Cache cũng không được đụng tới, nên ca này đồng thời khoá "wander không đọc/ghi file".
     */
    @Test
    fun `wander never touches the provider — the other half of the quota rule`() = runTest {
        val provider = FakeRoutingProvider { AppResult.Success(directionsOf(goodEnterRoute())) }
        val source = sourceWith(provider)

        val points = source.wander("m-minh", northOfZone, zoneCenter)

        assertTrue("chặng WANDER vẫn phải cho ra một dãy điểm", points.isNotEmpty())
        assertEquals("chặng WANDER không được gọi nhà cung cấp", 0, provider.calls)
        assertEquals("và không được ghi gì vào cache", emptyList<String>(), routesDir.list()?.sorted())
        assertEquals(RouteSourceKind.SYNTHETIC, source.observeSource().first().kind)
    }

    private suspend fun assertDegradesToSynthetic(error: AppError) {
        val source = sourceWith(FakeRoutingProvider { AppResult.Failure(error) })

        val points = source.path(enterRequest())

        assertTrue("nhà cung cấp lỗi mà không có cache vẫn phải cho ra một dãy điểm", points.isNotEmpty())
        assertEquals(RouteSourceKind.SYNTHETIC, source.observeSource().first().kind)
        assertTrue("tầng 3 không mang attribution OSM", source.observeSource().first().attribution.isEmpty())
    }

    private fun sourceWith(provider: RoutingProvider) =
        MemberRouteSource(routingProvider = provider, cache = OnDevicePolylineCache(routesDir, Json { ignoreUnknownKeys = true }))

    private fun enterRequest() = MemberRouteRequest(
        memberId = "m-minh",
        from = northOfZone,
        to = zoneCenter,
        zone = zone,
        kind = LegKind.ENTER_ZONE,
    )

    private fun leaveRequest() = MemberRouteRequest(
        memberId = "m-minh",
        from = zoneCenter,
        to = northOfZone,
        zone = zone,
        kind = LegKind.LEAVE_ZONE,
    )

    /** Cắt ranh giới zone đúng một lần mỗi vòng (radius + exit-buffer) — chấp nhận được bởi
     * [com.example.pion.family.tracker.demo.domain.tracking.RouteGeometryGuard] cho `ENTER_ZONE`. */
    private fun goodEnterRoute() = listOf(northOfZone, zoneCenter)

    /** Men mép zone: cắt `radius` và `radius + ZONE_EXIT_BUFFER_M` bốn lần — đúng kiểu dội mà
     * `decisions.md` §C4 tồn tại để chặn, nên `RouteGeometryGuard` phải từ chối nó ở MỌI tầng. */
    private fun bouncingRoute() = listOf(300.0, 100.0, 300.0, 100.0, 300.0).map { offsetMeters ->
        GeoPoint(zone.latitude + offsetMeters / METERS_PER_DEGREE_LAT, zone.longitude)
    }

    private fun goodLeaveRoute() = listOf(zoneCenter, northOfZone)

    private fun directionsOf(points: List<GeoPoint>, attribution: List<String> = listOf("GraphHopper", "OpenStreetMap contributors")) =
        Directions(
            points = points,
            distanceMeters = 300.0,
            durationSeconds = 60L,
            engineId = "graphhopper",
            attribution = attribution,
        )
}

/** Fake theo LLM.md §11 — `onDirections` quyết định kết quả mỗi lần gọi và nhận SỐ THỨ TỰ của lời
 * gọi đó (1, 2, …), nên ca "fetch được một lần rồi mới hỏng" (S2) tả được ngay tại chỗ thay vì cần
 * một fake thứ hai. [calls] đếm số lần thật sự bị hỏi, dùng để khoá lời hứa hạn ngạch NFR-2 (cache
 * trúng thì con số này không tăng thêm). */
private class FakeRoutingProvider(
    private val onDirections: suspend (call: Int) -> AppResult<Directions>,
) : RoutingProvider {
    var calls: Int = 0
        private set

    override suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions> {
        calls++
        return onDirections(calls)
    }
}
