package com.example.pion.family.tracker.demo.domain.tracking

import com.example.pion.family.tracker.demo.domain.model.GeoPoint

/**
 * Tuyến THẬT lấy từ nhà cung cấp routing, phase-04 Implementation Step 11 (S9, QA-SRM-25/26) —
 * `MemberRoamer`'s bất biến ENTER/EXIT phải sống sót trên hình học đường THẬT, không chỉ trên
 * [SyntheticPath]. Literal Kotlin, KHÔNG đọc JSON lúc test chạy: `:domain:test` chỉ có
 * `junit`/`turbine`/`coroutines-test` trên classpath (không parser JSON), và `LLM.md` §11 đòi
 * `:domain:test` chạy dưới 5 giây cho toàn bộ package — thêm một dependency JSON + I/O đĩa vào một
 * module JVM thuần chỉ để đọc đúng một fixture là không cân xứng.
 *
 * **Xuất xứ (cùng khuôn `data/src/test/resources/README.md`, không tự bịa):**
 *
 * | | |
 * |---|---|
 * | Nguồn | GraphHopper Cloud, `profile=car`, key thật của dự án |
 * | Ngày lấy | 2026-08-26, qua `MemberRouteSource` chạy thật trên `emulator-5554` (kịch bản 9a) |
 * | Chặng | `ENTER_ZONE` — 43 điểm, `pointCount=43` khớp log `sim_route_loaded source=PROVIDER` |
 * | Attribution | `["GraphHopper", "OpenStreetMap contributors"]` (không hiển thị ở đây — test này
 * | | không vẽ UI, chỉ ghi lại vì đây là dữ liệu THẬT của nhà cung cấp) |
 *
 * Không chứa API key — cùng xác nhận với `data/src/test/resources/README.md`: GraphHopper không
 * echo key vào response, và các điểm dưới đây chỉ là toạ độ.
 *
 * Là **ghim hợp đồng** (đã qua [RouteGeometryGuard.isUsable] thật trong lần chạy đo P0, xem
 * `reports/dev-phase-04-report.md`), không phải giám sát API — không cần cập nhật khi hạ tầng
 * GraphHopper đổi, chỉ cập nhật nếu chính fixture cần thay (cùng luật với fixture routing của
 * `:data`).
 */
object RealRouteFixture {
    /** Zone THẬT tạo trên `emulator-5554` lúc đo — tâm và bán kính khớp đúng zone đã sinh ra chặng
     * ENTER_ZONE bên dưới. */
    const val ZONE_LATITUDE: Double = 10.77633
    const val ZONE_LONGITUDE: Double = 106.70296
    const val ZONE_RADIUS_M: Float = 150f

    /** 43 điểm THẬT, thứ tự ENTER_ZONE — điểm cuối nằm trong bán kính zone ở trên (~72m tới tâm). */
    val ENTER_ZONE_POINTS: List<GeoPoint> = listOf(
        GeoPoint(10.76812, 106.70611),
        GeoPoint(10.76807, 106.70599),
        GeoPoint(10.76715, 106.70643),
        GeoPoint(10.76711, 106.70633),
        GeoPoint(10.76765, 106.706),
        GeoPoint(10.76803, 106.70578),
        GeoPoint(10.76821, 106.70571),
        GeoPoint(10.76839, 106.70566),
        GeoPoint(10.76849, 106.70565),
        GeoPoint(10.76868, 106.70564),
        GeoPoint(10.76876, 106.70564),
        GeoPoint(10.76898, 106.70567),
        GeoPoint(10.76916, 106.70572),
        GeoPoint(10.76925, 106.70576),
        GeoPoint(10.76963, 106.70597),
        GeoPoint(10.7699, 106.7061),
        GeoPoint(10.77003, 106.70615),
        GeoPoint(10.77016, 106.70619),
        GeoPoint(10.7704, 106.70625),
        GeoPoint(10.77078, 106.70632),
        GeoPoint(10.7712, 106.70636),
        GeoPoint(10.77362, 106.70656),
        GeoPoint(10.77557, 106.7067),
        GeoPoint(10.77565, 106.70668),
        GeoPoint(10.77572, 106.70665),
        GeoPoint(10.77577, 106.7066),
        GeoPoint(10.7758, 106.70656),
        GeoPoint(10.77581, 106.70643),
        GeoPoint(10.77579, 106.70631),
        GeoPoint(10.77576, 106.70622),
        GeoPoint(10.77571, 106.70614),
        GeoPoint(10.77565, 106.70608),
        GeoPoint(10.77615, 106.70552),
        GeoPoint(10.77653, 106.7051),
        GeoPoint(10.77688, 106.7047),
        GeoPoint(10.77739, 106.70412),
        GeoPoint(10.77762, 106.70389),
        GeoPoint(10.77718, 106.70344),
        GeoPoint(10.77703, 106.7033),
        GeoPoint(10.77702, 106.7032),
        GeoPoint(10.77705, 106.70311),
        GeoPoint(10.77706, 106.70303),
        GeoPoint(10.77698, 106.70296),
    )
}
