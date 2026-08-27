package com.example.pion.family.tracker.demo.ui.core.motion

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Toán học thuần cho nội suy marker ở tầng hiển thị (phase-03, PRD delta D4/US-40). THUẦN JVM —
 * KHÔNG import Compose, KHÔNG import Android — test được bằng JUnit thường
 * (`ui/src/test/.../ui/core/motion/MarkerInterpolationTest.kt`), không cần Robolectric hay thiết bị.
 *
 * **Không ngoại suy — ở đây, và ở bất kỳ đâu gọi các hàm này** (ranh giới X1, PRD delta §7): hết
 * `progress = 1` thì [progressOf] dừng cứng ở đó, không có tổ hợp tham số nào khiến các hàm này trả
 * về một vị trí NGOÀI đoạn `[from, to]`. Bịa một vị trí thứ ba giữa hai sự thật đã ghi là bịa ra
 * chuyển động của một con người không có thật.
 */

private const val FULL_CIRCLE_DEGREES = 360f
private const val HALF_CIRCLE_DEGREES = 180f
private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Nội suy tuyến tính THẲNG — KHÔNG xử lý vòng qua mốc 0°/360°. Dùng cho toạ độ (vĩ độ/kinh độ): dù
 * đơn vị cũng là "độ", đây KHÔNG phải góc la bàn — FR-5/QA-SRM-22 đòi mọi điểm nội suy nằm ĐÚNG
 * trên đoạn thẳng nối hai mẫu, và đi đường vòng qua 0°/360° như [lerpBearing] sẽ vi phạm đúng điều
 * đó (một toạ độ 179° và -179° chỉ cách nhau 2° theo la bàn, nhưng KHÔNG được phép "đi tắt" qua
 * đường đổi ngày khi đó chỉ là hai vị trí gần nhau trên bản đồ phẳng, không phải một chuyển hướng).
 */
fun lerpDegrees(from: Double, to: Double, progress: Float): Double = from + (to - from) * progress

/**
 * Nội suy góc la bàn (bearing) đi ĐƯỜNG NGẮN NHẤT qua mốc 0°/360° (FR-3, QA-SRM-07) — ví dụ từ
 * 350° tới 10° đi 20° (350→360/0→10), không đi 340° theo chiều ngược lại. Ở đúng 180° (hai điểm đối
 * cực trên vòng tròn), hai chiều dài bằng nhau tuyệt đối; hàm này CHỌN CHIỀU DƯƠNG (0°→180° đi qua
 * 90°, không qua 270°) và khoá lựa chọn đó bằng test — nếu không, marker sẽ quay ngẫu nhiên hai
 * chiều khác nhau ở đúng những cặp góc đối cực, tuỳ thuộc sai số làm tròn của lần tính đó.
 */
fun lerpBearing(from: Float, to: Float, progress: Float): Float {
    var delta = (to - from) % FULL_CIRCLE_DEGREES
    if (delta > HALF_CIRCLE_DEGREES) delta -= FULL_CIRCLE_DEGREES
    if (delta < -HALF_CIRCLE_DEGREES) delta += FULL_CIRCLE_DEGREES
    val result = (from + delta * progress) % FULL_CIRCLE_DEGREES
    return if (result < 0f) result + FULL_CIRCLE_DEGREES else result
}

/**
 * Tiến độ nội suy, chặn CỨNG trong `[0, 1]` — đây là toàn bộ cơ chế chặn ngoại suy (US-44,
 * QA-SRM-22, xem KDoc đầu file). `elapsedMs <= 0` trả `0f` (chưa bắt đầu); `elapsedMs >= durationMs`
 * trả `1f` (đã xong, KHÔNG được vượt); `durationMs <= 0` trả `1f` NGAY LẬP TỨC thay vì chia cho 0.
 */
fun progressOf(elapsedMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 1f
    if (elapsedMs <= 0L) return 0f
    if (elapsedMs >= durationMs) return 1f
    return elapsedMs.toFloat() / durationMs.toFloat()
}

/**
 * Haversine — bản sao CÓ CHỦ Ý của
 * [com.example.pion.family.tracker.demo.domain.tracking.GeoDistance] (`:domain/tracking/`,
 * `internal` nên phạm vi CHỈ module `:domain` — Kotlin `internal` theo biên Gradle module, `:ui`
 * không thấy được dù có phụ thuộc `:domain`, LLM.md §2). Cùng lý do trùng lặp đã ghi ở LLM.md §13
 * Open #12 cho `ValhallaDirectionsMapper` (`:data`, cùng lý do): mở `GeoDistance` thành `public`
 * chỉ để một module khác dùng phá đúng ranh giới mà `internal` dựng lên cho lý do đó.
 *
 * Dùng ở `AnimatedMarkerPositions.kt` để đo khoảng cách từ mẫu mới tới vị trí ĐANG hiển thị, cho
 * ngưỡng snap của quyết định (A) — xem KDoc [isSpawnJump].
 */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2.0).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2.0).pow(2)
    return 2.0 * EARTH_RADIUS_M * asin(sqrt(a))
}

/**
 * Quyết định (A), F-6 (`plans/260825-0956-smooth-road-following-member-movement/`) — `true` khi
 * [distanceMeters] vượt [thresholdMeters]: mẫu mới KHÔNG còn là một bước đi liên tục, mà là cú
 * spawn một lần của `MemberRoamer.tick` (`:domain`) — ghi `bearingDegrees = 0f`/`speedMps = 0f`
 * cứng khi dời một thành viên tới gần một zone mới quá xa để đi bộ tới. Nội suy góc TỚI mẫu đó sẽ
 * quay marker về hướng bắc đúng MỘT LẦN mỗi thành viên mỗi lần chạy — chủ dự án chốt sửa ở `:ui`
 * (đây), không sửa `:domain`.
 *
 * Hàm được tách ra khỏi Compose (`AnimatedMarkerPositions.kt`, nơi [thresholdMeters] thật
 * — `SPAWN_SNAP_THRESHOLD_M` — được định nghĩa và giải thích đầy đủ) để phép so sánh này khoá được
 * bằng JUnit thuần, không chỉ nằm im trong code Compose không test trực tiếp được.
 */
fun isSpawnJump(distanceMeters: Double, thresholdMeters: Double): Boolean = distanceMeters > thresholdMeters
