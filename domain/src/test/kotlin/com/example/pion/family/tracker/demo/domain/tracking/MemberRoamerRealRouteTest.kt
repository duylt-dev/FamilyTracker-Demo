package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.random.Random

private const val MEMBER_SEED = 4242

/** Vài vòng vào-ra đầy đủ trên tuyến thật (~1044m một chiều + dwell) — cùng bậc với
 * `TICKS_FOR_SEVERAL_CYCLES` của `MemberRoamerTest`, chỉnh lên vì một chặng ở đây dài hơn zone
 * tổng hợp 150m của test đó. */
private const val TICKS_FOR_SEVERAL_CYCLES = 400

/** Mét/độ vĩ, dùng cho phép xấp xỉ phẳng trong `metersFromChord` — cùng hằng số các test khác của
 * repo dùng. */
private const val METERS_PER_DEGREE = 111_320.0

/** Fixture thật cong tối đa **335 m** so với dây cung; một đường thẳng cho 0 m. Ngưỡng đặt ở giữa,
 * không sát biên nào — nới nó lên gần 335 là biến ca này thành ca đo chính xác fixture, thu nó về
 * gần 0 là giết ca này. */
private const val MIN_OFF_CHORD_METERS = 100.0

/**
 * Phase-04 Implementation Step 11 (S9, QA-SRM-25/26, `decisions.md` §C4 "Lịch kiểm bắt buộc": bất
 * biến ENTER/EXIT phải chạy lại ở cuối phase 02, 04 và 06). `MemberRoamerTest`'s ca "a full roam
 * cycle …" khoá đúng bất biến này trên [SyntheticPath] (đường tổng hợp, tất định-do-thiết-kế); ca ở
 * đây khoá LẠI ĐÚNG bất biến đó trên [RealRouteFixture] — 43 điểm THẬT lấy từ GraphHopper qua
 * `MemberRouteSource` chạy thật trên `emulator-5554` (không phải fixture tự dựng).
 *
 * **Vì sao chặng LEAVE_ZONE dùng chính fixture đảo ngược, không phải một lần fetch thật thứ hai:**
 * đây vẫn là toạ độ THẬT (không nội suy, không làm tròn thêm) — chỉ đổi HƯỚNG đi qua CÙNG một con
 * đường đã ghi nhận, đúng cách một chuyến quay đầu thật sự đi lại. Mục tiêu của ca này là bất biến
 * ENTER/EXIT của `ZoneEvaluator` trên hình học đường THẬT (đã qua [RouteGeometryGuard] — xem
 * `RealRouteFixture` KDoc), không phải kiểm tra chính GraphHopper.
 *
 * `advance()` dưới đây LẶP LẠI đúng cấu trúc `MemberRoamerTest.advance()` (hai pha `NeedPath`/
 * `withPath`), chỉ thay nguồn điểm: [RealRouteFixture.ENTER_ZONE_POINTS] cho `ENTER_ZONE`, đảo
 * ngược cho `LEAVE_ZONE` — thay vì `SyntheticPath.between(...)`. `LegKind` (không phải toạ độ đích
 * do `nextTarget` tính) là thứ duy nhất `advance()` đọc để chọn nguồn điểm, đúng seam mà phase-02
 * dựng lên: `MemberRoamer` không biết và không cần biết tuyến tới từ đâu.
 */
class MemberRoamerRealRouteTest {

    private val zone = Zone(
        id = "z-real-fixture",
        name = "z-real-fixture",
        latitude = RealRouteFixture.ZONE_LATITUDE,
        longitude = RealRouteFixture.ZONE_LONGITUDE,
        radiusMeters = RealRouteFixture.ZONE_RADIUS_M,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-26T00:00:00Z"),
    )

    @Test
    fun `a full roam cycle on the real GraphHopper fixture produces alternating ENTER and EXIT, starting with ENTER`() {
        val zones = listOf(zone)
        val random = Random(42)
        val start = RealRouteFixture.ENTER_ZONE_POINTS.first()
        var state = RoamState(latitude = start.latitude, longitude = start.longitude)
        var inside = emptySet<String>()
        val events = mutableListOf<ZoneEventType>()

        repeat(TICKS_FOR_SEVERAL_CYCLES) {
            state = advance(state, zones, random)
            val evaluation = ZoneEvaluator.evaluate(pointAt(state.latitude, state.longitude), zones, inside)
            inside = evaluation.insideAfter
            events += evaluation.events.map { it.type }
        }

        assertTrue(
            "phải chạy hết được ít nhất một vòng vào-ra THẬT trong $TICKS_FOR_SEVERAL_CYCLES nhịp",
            events.size >= 2,
        )
        assertEquals(ZoneEventType.ENTER, events.first())
        assertTrue(
            "không được dội trên hình học đường THẬT: hai sự kiện liên tiếp cùng loại nghĩa là ranh " +
                "giới bị cắt hai lần cùng chiều",
            events.zipWithNext().none { (a, b) -> a == b },
        )
    }

    /**
     * **S9 chỉ có nghĩa nếu thành viên THẬT SỰ đi theo hình học thật.** Ca ENTER/EXIT ở trên khoá
     * bất biến sự kiện, nhưng nó xanh y hệt khi tuyến bị thay bằng một đường thẳng — đo bằng mutation
     * ở lượt soát thứ hai của `code-reviewer`: cho `MemberRoamer.withPath` vứt hết đỉnh giữa
     * (`parametrize(listOf(first, last))`) ⇒ **301/301 vẫn XANH**. Nghĩa là trước ca này, KHÔNG một
     * test nào trong repo đỏ khi lời hứa "bám đường" (US-41 — lý do tồn tại của cả plan) bị gỡ bỏ.
     *
     * Đại lượng phân biệt phải là HÌNH DẠNG, không phải quãng đường đi được: mỗi nhịp đi một bước cố
     * định nên tổng quãng đường sau N nhịp gần như nhau ở cả hai trường hợp. Fixture thật cong tới
     * **335 m** so với dây cung nối hai đầu (dài 1 661 m so với dây cung 1 043 m); một đường thẳng
     * cho đúng 0 m. Ngưỡng 100 m nằm giữa hai thế giới đó, không sát biên nào.
     */
    @Test
    fun `the member walks the real polyline, not the straight line between its ends`() {
        val zones = listOf(zone)
        val random = Random(42)
        val start = RealRouteFixture.ENTER_ZONE_POINTS.first()
        var state = RoamState(latitude = start.latitude, longitude = start.longitude)
        var maxOffChordMeters = 0.0

        repeat(TICKS_FOR_SEVERAL_CYCLES) {
            state = advance(state, zones, random)
            val offChord = metersFromChord(
                latitude = state.latitude,
                longitude = state.longitude,
                from = RealRouteFixture.ENTER_ZONE_POINTS.first(),
                to = RealRouteFixture.ENTER_ZONE_POINTS.last(),
            )
            maxOffChordMeters = maxOf(maxOffChordMeters, offChord)
        }

        assertTrue(
            "thành viên phải bám hình học THẬT: điểm xa dây cung nhất mới chỉ ${maxOffChordMeters}m — " +
                "một tuyến bị làm phẳng thành đường thẳng cho ~0m",
            maxOffChordMeters > MIN_OFF_CHORD_METERS,
        )
    }

    /**
     * Khoảng cách từ một điểm tới ĐƯỜNG THẲNG qua [from]–[to], xấp xỉ phẳng: quy đổi độ ra mét
     * (`METERS_PER_DEGREE` cho vĩ độ, nhân `cos(lat)` cho kinh độ) rồi lấy |tích có hướng| / |AB|.
     * Sai số của phép xấp xỉ ở quy mô 1–2 km và vĩ độ 10.8° là dưới 0.1 %, không đáng kể so với
     * khoảng cách giữa hai thế giới mà ngưỡng [MIN_OFF_CHORD_METERS] tách ra. Không dùng
     * `GeoDistance` vì cần khoảng cách tới một ĐƯỜNG THẲNG, không phải tới một điểm.
     */
    private fun metersFromChord(latitude: Double, longitude: Double, from: GeoPoint, to: GeoPoint): Double {
        val metersPerDegreeLng = METERS_PER_DEGREE * cos(from.latitude * PI / 180.0)
        val ax = 0.0
        val ay = 0.0
        val bx = (to.longitude - from.longitude) * metersPerDegreeLng
        val by = (to.latitude - from.latitude) * METERS_PER_DEGREE
        val px = (longitude - from.longitude) * metersPerDegreeLng
        val py = (latitude - from.latitude) * METERS_PER_DEGREE
        val chordLength = hypot(bx - ax, by - ay)
        return abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / chordLength
    }

    /** Đúng cấu trúc `MemberRoamerTest.advance()` — chỉ khác nguồn điểm khi gặp `RoamStep.NeedPath`:
     * [RealRouteFixture] thay vì `SyntheticPath.between(...)`. `WANDER` không xảy ra trong ca này
     * (luôn có đúng một zone), nhánh đó chỉ có mặt để `when` đủ nhánh. */
    private fun advance(state: RoamState, zones: List<Zone>, random: Random, memberSeed: Int = MEMBER_SEED): RoamState {
        val step = MemberRoamer.tick(state, zones, random, memberSeed)
        if (step is RoamStep.Move) return step.state
        step as RoamStep.NeedPath
        val points = when (step.target.kind) {
            LegKind.ENTER_ZONE -> RealRouteFixture.ENTER_ZONE_POINTS
            LegKind.LEAVE_ZONE -> RealRouteFixture.ENTER_ZONE_POINTS.reversed()
            LegKind.WANDER -> RealRouteFixture.ENTER_ZONE_POINTS
        }
        val withPath = MemberRoamer.withPath(step.from, points)
        return (MemberRoamer.tick(withPath, zones, random, memberSeed) as RoamStep.Move).state
    }

    private fun pointAt(lat: Double, lng: Double) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 8f,
        speedMps = 8.3f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-26T08:00:00Z"),
    )
}
