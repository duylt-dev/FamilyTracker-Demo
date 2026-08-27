package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

private const val MEMBER_SEED = 4_242
private const val TICKS = 600

/**
 * **Lớp đo TẤT ĐỊNH của smooth-road plan phase-06 (FR-1) — nguồn số cho luật C5 (`decisions.md`
 * §C5, chặn B4).**
 *
 * Vì sao phải có lớp này chứ không chỉ đo trên máy: một vòng ENTER→EXIT chạy trên máy đi qua NHIỀU
 * tầng nguồn tuyến (provider / cache / tổng hợp), và trước khi `LLM.md` §13 Fixed #32 được sửa thì
 * mỗi tầng cho một tốc độ khác nhau ⇒ phép đo **không tái lập được**, chạy hai lần ra hai số. Ở đây
 * nguồn tuyến là tham số của phép đo, không phải biến ngẫu nhiên của môi trường, nên bốn con số
 * dưới đây so sánh được với nhau và lặp lại được.
 *
 * Đo bằng đơn vị **nhịp**, đổi ra giây bằng `TrackingConstants.MEMBER_ROAM_INTERVAL_MS` — không đo
 * bằng đồng hồ thật, để kết quả không phụ thuộc máy chạy test.
 */
class MemberRoamerLapTimeTest {

    private enum class Source { SYNTHETIC, REAL }

    private fun zoneOf(radiusMeters: Float) = Zone(
        id = "lap-zone",
        name = "LapZone",
        latitude = RealRouteFixture.ZONE_LATITUDE,
        longitude = RealRouteFixture.ZONE_LONGITUDE,
        radiusMeters = radiusMeters,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-26T00:00:00Z"),
    )

    /** Danh sách (nhịp thứ mấy, loại sự kiện) của một lần chạy. */
    private fun run(radiusMeters: Float, source: Source): List<Pair<Int, ZoneEventType>> {
        val zones = listOf(zoneOf(radiusMeters))
        val random = Random(MEMBER_SEED)
        val start = RealRouteFixture.ENTER_ZONE_POINTS.first()
        var state = RoamState(latitude = start.latitude, longitude = start.longitude)
        var inside = emptySet<String>()
        val timeline = mutableListOf<Pair<Int, ZoneEventType>>()

        repeat(TICKS) { tick ->
            state = advance(state, zones, random, source)
            val evaluation = ZoneEvaluator.evaluate(
                LocationPoint(
                    latitude = state.latitude,
                    longitude = state.longitude,
                    accuracyMeters = 5f,
                    speedMps = state.speedMps.toFloat(),
                    bearingDegrees = state.bearingDegrees.toFloat(),
                    recordedAt = Instant.EPOCH.plusMillis(tick * TrackingConstants.MEMBER_ROAM_INTERVAL_MS),
                ),
                zones,
                inside,
            )
            inside = evaluation.insideAfter
            evaluation.events.forEach { timeline += tick to it.type }
        }
        return timeline
    }

    private fun advance(state: RoamState, zones: List<Zone>, random: Random, source: Source): RoamState {
        val step = MemberRoamer.tick(state, zones, random, MEMBER_SEED)
        if (step is RoamStep.Move) return step.state
        step as RoamStep.NeedPath
        val points = when (source) {
            Source.REAL -> when (step.target.kind) {
                LegKind.LEAVE_ZONE -> RealRouteFixture.ENTER_ZONE_POINTS.reversed()
                else -> RealRouteFixture.ENTER_ZONE_POINTS
            }
            Source.SYNTHETIC -> SyntheticPath.between(
                from = GeoPoint(step.from.latitude, step.from.longitude),
                to = GeoPoint(step.target.latitude, step.target.longitude),
                seed = MEMBER_SEED,
            )
        }
        val withPath = MemberRoamer.withPath(step.from, points)
        return (MemberRoamer.tick(withPath, zones, random, MEMBER_SEED) as RoamStep.Move).state
    }

    private fun secondsBetween(timeline: List<Pair<Int, ZoneEventType>>, from: ZoneEventType, to: ZoneEventType): Double? {
        val i = timeline.indexOfFirst { it.second == from }
        if (i < 0) return null
        val j = (i + 1 until timeline.size).firstOrNull { timeline[it].second == to } ?: return null
        return (timeline[j].first - timeline[i].first) * TrackingConstants.MEMBER_ROAM_INTERVAL_MS / 1_000.0
    }

    /**
     * **FR-1 — bốn con số.** In ra để đọc, và ghim lại bằng khẳng định "tái lập được": chạy hai lần
     * cùng tham số phải ra ĐÚNG cùng một dòng thời gian. Đó chính là tính chất mà §13 Fixed #32 mua
     * về — trước khi sửa, tốc độ phụ thuộc mật độ đỉnh của tuyến nên con số này vô nghĩa.
     */
    @Test
    fun `lap times are measurable and reproducible for both radii and both route sources`() {
        val combos = listOf(150f to Source.SYNTHETIC, 150f to Source.REAL, 50f to Source.SYNTHETIC, 50f to Source.REAL)

        combos.forEach { (radius, source) ->
            val first = run(radius, source)
            val second = run(radius, source)

            assertEquals(
                "phép đo phải TÁI LẬP ĐƯỢC: cùng bán kính + cùng nguồn tuyến phải cho cùng dòng thời gian",
                first,
                second,
            )
            val enterExit = secondsBetween(first, ZoneEventType.ENTER, ZoneEventType.EXIT)
            val enterEnter = secondsBetween(first, ZoneEventType.ENTER, ZoneEventType.ENTER)
            println("LAP r=${radius.toInt()}m source=$source  ENTER→EXIT=${enterExit}s  ENTER→ENTER=${enterEnter}s  events=${first.size}")

            if (radius == 50f && source == Source.REAL) return@forEach // giới hạn đã biết, ghim ở ca dưới
            assertTrue(
                "r=${radius}m/$source: phải chạy được ít nhất một vòng vào-ra trong $TICKS nhịp, " +
                    "nếu không thì không có gì để áp luật C5",
                first.count { it.second == ZoneEventType.ENTER } >= 1 && first.count { it.second == ZoneEventType.EXIT } >= 1,
            )
        }

        // Hai nguồn tuyến phải cho CÙNG thời gian ENTER→EXIT ở cùng bán kính. Đây là thứ mà
        // §13 Fixed #32 mua về: trước bản sửa, tầng tổng hợp đi nửa tốc độ nên hai con số này lệch
        // nhau ~2×, và luật C5 không chốt được vì không biết chốt theo số nào.
        assertEquals(
            "cùng bán kính, hai nguồn tuyến phải cho cùng thời gian ENTER→EXIT — nếu lệch thì tốc độ " +
                "lại đang phụ thuộc mật độ đỉnh của tuyến (§13 Fixed #32 tái diễn)",
            secondsBetween(run(150f, Source.SYNTHETIC), ZoneEventType.ENTER, ZoneEventType.EXIT),
            secondsBetween(run(150f, Source.REAL), ZoneEventType.ENTER, ZoneEventType.EXIT),
        )
    }

    /**
     * **Giới hạn ĐÃ BIẾT của fixture, ghim lại thay vì giấu** (cùng tinh thần ca "known limitation"
     * của `RouteBlueprintTest`). Con số thứ tư mà phase-06 FR-1 yêu cầu — zone 50 m trên tuyến THẬT
     * — không lấy được, và không phải vì sản phẩm sai: `RealRouteFixture` được thu cho một zone bán
     * kính 150 m, điểm cuối của nó cách tâm ~72 m. Với bán kính 50 m thì tuyến kết thúc NGOÀI zone,
     * nên không bao giờ có ENTER, nên không có vòng nào để đo.
     *
     * Muốn con số đó thì phải thu một fixture MỚI cho zone 50 m (một request GraphHopper thật), chứ
     * không phải sửa test. Ghi ở đây để phase sau không đi tìm lại.
     */
    @Test
    fun `the real fixture cannot reach a 50m zone, which is why the fourth lap number is missing`() {
        val end = RealRouteFixture.ENTER_ZONE_POINTS.last()
        val distanceToCentre = GeoDistance.haversineMeters(
            end.latitude, end.longitude,
            RealRouteFixture.ZONE_LATITUDE, RealRouteFixture.ZONE_LONGITUDE,
        )

        assertTrue("điểm cuối fixture phải nằm TRONG zone 150m: $distanceToCentre m", distanceToCentre < 150.0)
        assertTrue("và NGOÀI zone 50m — đó là lý do con số thứ tư không tồn tại: $distanceToCentre m", distanceToCentre > 50.0)

        val timeline = run(50f, Source.REAL)
        assertEquals("zone 50m trên tuyến thật: không sự kiện nào, đúng như hình học trên báo", 0, timeline.size)
    }

    /**
     * **§13 Open #21 — vì sao `sim_spawn` chưa từng xuất hiện dòng nào trong mọi log đã thu.**
     * Không phải log thu sai lúc: nhánh spawn chỉ chạy khi khoảng cách tới đích **vượt
     * `MemberRoamer.MAX_WALK_M` (5 km)**, mà `DemoDataSeeder` seed thành viên quanh `DEMO_CENTER` và
     * zone tạo tay trên màn thì luôn nằm trong vài km. Ca này chứng minh cả hai chiều tất định, nên
     * phase sau không phải đi tìm lại trong log.
     */
    @Test
    fun `spawn only fires beyond MAX_WALK_M, which the demo scenario never reaches`() {
        val zones = listOf(zoneOf(150f))
        val random = Random(MEMBER_SEED)
        val near = RealRouteFixture.ENTER_ZONE_POINTS.first()

        var spawns = 0
        var state = RoamState(latitude = near.latitude, longitude = near.longitude)
        repeat(TICKS) {
            val step = MemberRoamer.tick(state, zones, random, MEMBER_SEED)
            state = when (step) {
                is RoamStep.Move -> {
                    if (step.spawnDistanceMeters != null) spawns++
                    step.state
                }
                is RoamStep.NeedPath -> advance(state, zones, random, Source.SYNTHETIC)
            }
        }
        assertEquals("kịch bản demo (zone cách vài km) KHÔNG được sinh cú spawn nào", 0, spawns)

        // Chiều ngược lại: đặt thành viên ở nửa vòng Trái Đất thì nhánh spawn PHẢI chạy — nếu không,
        // khẳng định ở trên chỉ chứng minh nhánh đó đã chết, không chứng minh nó có điều kiện.
        val far = RoamState(latitude = -33.86, longitude = 151.21) // Sydney
        val step = MemberRoamer.tick(far, zones, Random(MEMBER_SEED), MEMBER_SEED)
        assertTrue("cách hơn MAX_WALK_M thì phải spawn", step is RoamStep.Move && step.spawnDistanceMeters != null)
    }
}
