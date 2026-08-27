package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

private const val KEY = "m-minh_z-truong_ENTER_ZONE_10.00000_106.00000_150"

/**
 * JVM thuần — [OnDevicePolylineCache] nhận thẳng một `java.io.File` (không `Context`), đúng để test
 * này chạy không cần Robolectric (LLM.md §11). Dùng một thư mục tạm thật trên đĩa, xoá lại ở
 * `@After` — không có gì để fake ở đây, `File`/JVM I/O không cần test double.
 */
class OnDevicePolylineCacheTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var routesDir: File
    private lateinit var cache: OnDevicePolylineCache

    @Before
    fun setUp() {
        routesDir = Files.createTempDirectory("routes").toFile()
        cache = OnDevicePolylineCache(routesDir, json)
    }

    @After
    fun tearDown() {
        routesDir.deleteRecursively()
    }

    @Test
    fun `a route written to the cache reads back identical, with attribution`() = runTest {
        val points = listOf(GeoPoint(10.0, 106.0), GeoPoint(10.001, 106.001))
        val attribution = listOf("GraphHopper", "OpenStreetMap contributors")

        cache.put(KEY, points, attribution, engineId = "graphhopper")
        val read = cache.get(KEY)

        assertEquals(points, read?.points)
        assertEquals(attribution, read?.attribution)
        assertEquals("graphhopper", read?.engineId)
    }

    @Test
    fun `a missing key is a miss, not an exception`() = runTest {
        assertNull(cache.get("khong-ton-tai"))
    }

    @Test
    fun `a schemaVersion mismatch is treated as a miss and the stale file is deleted`() = runTest {
        val file = File(routesDir, "$KEY.json")
        file.writeText(
            Json.encodeToString(
                CachedRouteDto.serializer(),
                CachedRouteDto(
                    schemaVersion = OnDevicePolylineCache.SCHEMA_VERSION + 1,
                    engineId = "graphhopper",
                    attribution = listOf("GraphHopper"),
                    points = listOf(listOf(10.0, 106.0)),
                    createdAtMs = 0L,
                ),
            ),
        )

        assertNull("schemaVersion khác phải coi là miss", cache.get(KEY))
        assertTrue("file cache cũ phải bị xoá, không phải đọc lại lần sau", !file.exists())
    }

    @Test
    fun `a corrupted cache file is a miss, and it never crashes the caller`() = runTest {
        val file = File(routesDir, "$KEY.json")
        file.writeText("{ not valid json at all")

        assertNull("JSON hỏng không được ném ra ngoài — coi như miss", cache.get(KEY))
        assertTrue("file rác phải bị xoá", !file.exists())
    }

    /**
     * Review phase-05 L-2. Đây là ca PHÁP LÝ, không phải ca độ bền dữ liệu: một entry cache có hình
     * học nhưng `attribution` rỗng làm `RoutingAttribution` rơi vào nhánh thứ ba (`else -> null`) —
     * dải ẩn hẳn — nên màn vẽ hình học OSM mà KHÔNG ghi công gì. Đó là chiều THIẾU ghi công, vi
     * phạm ODbL (`docs/routing-and-map-attribution.md` §3 ràng buộc 1), và nó im lặng: không log,
     * không crash, không test nào khác chạm tới.
     *
     * Entry như vậy không thể sinh ra từ code hiện tại — `MemberRouteSource` gọi `put()` ở đúng một
     * chỗ, nhánh provider, luôn kèm `directions.attribution` — nên đây là hàng rào chống file bị
     * cắt cụt và chống một `put()` thứ hai được thêm vào sau này mà quên attribution.
     */
    @Test
    fun `a cached route with no attribution is a miss, so OSM geometry is never shown uncredited`() = runTest {
        val file = File(routesDir, "$KEY.json")
        file.writeText(
            json.encodeToString(
                CachedRouteDto.serializer(),
                CachedRouteDto(
                    schemaVersion = OnDevicePolylineCache.SCHEMA_VERSION,
                    engineId = "graphhopper",
                    attribution = emptyList(),
                    points = listOf(listOf(10.0, 106.0), listOf(10.1, 106.1)),
                    createdAtMs = 0L,
                ),
            ),
        )

        assertNull("hình học không kèm ghi công phải coi là miss, không được dùng", cache.get(KEY))
        assertTrue("file phải bị xoá để tầng provider ghi đè bằng bản có attribution", !file.exists())
    }

    @Test
    fun `writing then re-reading with a fresh cache instance still round-trips`() = runTest {
        val points = listOf(GeoPoint(10.5, 106.5), GeoPoint(10.6, 106.6))
        cache.put(KEY, points, attribution = listOf("Valhalla", "OpenStreetMap contributors"), engineId = "valhalla")

        val secondInstance = OnDevicePolylineCache(routesDir, json)
        val read = secondInstance.get(KEY)

        assertEquals("cache phải sống sót qua việc dựng lại instance (khởi động lại app)", points, read?.points)
    }
}
