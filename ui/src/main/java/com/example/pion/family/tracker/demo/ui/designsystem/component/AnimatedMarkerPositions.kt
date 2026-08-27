package com.example.pion.family.tracker.demo.ui.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.ui.core.motion.haversineMeters
import com.example.pion.family.tracker.demo.ui.core.motion.isSpawnJump
import com.example.pion.family.tracker.demo.ui.core.motion.lerpBearing
import com.example.pion.family.tracker.demo.ui.core.motion.lerpDegrees
import com.example.pion.family.tracker.demo.ui.core.motion.progressOf
import kotlinx.coroutines.isActive

/**
 * Một mẫu vị trí THẬT của một marker (thành viên hoặc self) — đủ để nội suy, KHÔNG phải model đầy
 * đủ của tầng domain (phase-03). Toàn bộ field là primitive/`String` — `@Immutable` để Compose
 * không cần biết `LocationPoint`/`MemberLocation` (`:domain`) có ổn định hay không
 * (`compose-stability.conf` chưa dựng, LLM.md §13 Fixed #20 / phase-03 Key Insight #6).
 */
@Immutable
internal data class MarkerSample(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float,
    val recordedAtMs: Long,
)

/**
 * Chỗ DUY NHẤT quy đổi một [LocationPoint] của `:domain` thành [MarkerSample] — `MemberMarkers.kt`
 * và `FamilyTrackerMap.kt` cùng cần đúng phép chuyển này (nhất là `recordedAt`: `Instant` → epoch
 * millis), chép ra hai nơi là hai nơi để lệch nhau. `LocationPoint` chỉ ĐI QUA đây, KHÔNG nằm lại
 * trong state animation — xem KDoc [AnimatedMarkerPosition] cho lý do state chỉ chứa primitive.
 */
internal fun LocationPoint.toMarkerSample(id: String): MarkerSample =
    MarkerSample(id, latitude, longitude, bearingDegrees, recordedAt.toEpochMilli())

/**
 * Vị trí ĐANG HIỂN THỊ của một marker tại khung hình hiện tại — CHỈ primitive (`Double`/`Float`).
 * **KHÔNG được thêm `LatLng` (hay bất kỳ kiểu nào của Maps SDK) vào đây.** `LatLng` không nằm
 * trong `compose-stability.conf` (chưa dựng cho v1.0 — LLM.md §13 Fixed #20), Compose suy luận nó
 * "unstable", và một state unstable trong `SnapshotStateMap` làm mọi marker đọc nó mất khả năng
 * skip đúng đắn dù giá trị không đổi. Dựng `LatLng` NGAY TẠI nơi dùng — bên trong composable gọi
 * `MarkerComposable`/`rememberUpdatedMarkerState` (`MemberMarkers.kt`, `FamilyTrackerMap.kt`),
 * không phải ở đây.
 */
@Immutable
internal data class AnimatedMarkerPosition(
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float,
)

/**
 * Trạng thái nội suy nội bộ của MỘT marker giữa hai mẫu — bookkeeping thuần (KHÔNG phải Compose
 * state, không kích hoạt recompose khi đổi); chỉ vòng lặp trong [rememberAnimatedMarkerPositions]
 * đọc/ghi. Field là `var` vì được cập nhật TẠI CHỖ mỗi lần retarget, tránh cấp phát một object mới
 * cho mỗi mẫu × mỗi marker.
 */
private class MarkerMotion(
    var fromLatitude: Double,
    var fromLongitude: Double,
    var fromBearing: Float,
    var toLatitude: Double,
    var toLongitude: Double,
    var toBearing: Float,
    var startedAtNanos: Long,
    var durationMs: Long,
)

private const val MIN_DURATION_MS: Long = 1L
private const val MAX_DURATION_MS: Long = 5_000L
private const val NANOS_PER_MS: Long = 1_000_000L

/**
 * MỘT vòng `withFrameNanos` cho TẤT CẢ marker trong [samples] — không phải N `Animatable` riêng
 * (researcher-02 §E): N `Animatable` = N coroutine, N lần cập nhật state mỗi khung, N lần recompose
 * cha. Ở đây mọi mục còn `progress < 1` được cập nhật vào CÙNG một [SnapshotStateMap] trong CÙNG
 * một callback, một lần "ghi state" mỗi khung bất kể bao nhiêu marker đang animate.
 * `MemberMarkers.kt` gọi hàm này MỘT lần cho cả danh sách thành viên khác; `FamilyTrackerMap.kt`
 * gọi lại đúng hàm này (CÙNG bộ nội suy, vòng lặp riêng) cho self.
 *
 * `LaunchedEffect(samples)` chỉ khởi động lại khi NỘI DUNG `samples` thật sự đổi (`data class` so
 * `equals` cấu trúc, không phải reference) nên một recomposition không liên quan (vd. đổi `zones`)
 * không huỷ animation đang chạy. Mỗi mẫu thay đổi rơi vào đúng một nhánh:
 * - **id MỚI** → vẽ thẳng ngay, KHÔNG animate — chưa có "vị trí đang hiển thị" để nội suy từ.
 * - **id biến mất** khỏi [samples] → xoá khỏi mọi map nội bộ (chống rò rỉ, FR-6).
 * - **khoảng cách mẫu mới tới vị trí ĐANG HIỂN THỊ vượt [SPAWN_SNAP_THRESHOLD_M]** → snap vị trí,
 *   GIỮ NGUYÊN góc đang hiển thị (quyết định A, F-6).
 * - **còn lại** → retarget: `from` = vị trí ĐANG HIỂN THỊ (KHÔNG PHẢI mẫu cũ — chống giật lùi khi
 *   mẫu mới tới sớm/muộn hơn dự kiến), `to` = mẫu mới, `durationMs` = hiệu `recordedAtMs` của hai
 *   mẫu, `coerceIn(1, 5_000)`.
 *
 * Vòng lặp thoát ngay khi mọi mục đạt `progress >= 1` — không tốn pin chạy `withFrameNanos` vô ích
 * khi không còn gì animate; chỉ chạy lại khi `LaunchedEffect(samples)` khởi động lại.
 *
 * Trả về `Map`, KHÔNG phải `SnapshotStateMap` — code review phase-03: kiểu trả về ghi được cho
 * người gọi mở lại đúng cánh cửa mà nhánh `previousDisplayed == null` (snap không animate) được
 * viết ra để đóng. `MemberMarkers.kt`/`FamilyTrackerMap.kt` chỉ đọc bằng `[]`, `Map` đã đủ.
 *
 * **`git mv` từ `feature/map/component/` (feedback #2).** Lúc chỉ màn Bản đồ nội suy marker thì nó
 * đứng đúng chỗ; màn Dẫn đường (`NavigationMap.kt`) là chỗ dùng THỨ HAI nên đủ ngưỡng `LLM.md` §12
 * ("một composable ≥2 feature dùng → `designsystem/component/`") — cùng con đường `RoutingAttribution.kt`
 * đã đi. Di chuyển bằng `git mv`, không xoá-tạo-lại, để giữ `git blame`. Lý do bắt buộc phải dùng
 * chung chứ không chép: hai màn hiển thị CÙNG những thành viên đó, một bản sao thứ hai là một chỗ
 * để hai màn trôi lệch nhau về ngưỡng snap và luật `from` = vị trí ĐANG hiển thị.
 */
@Composable
internal fun rememberAnimatedMarkerPositions(samples: List<MarkerSample>): Map<String, AnimatedMarkerPosition> {
    val displayed = remember { mutableStateMapOf<String, AnimatedMarkerPosition>() }
    val motions = remember { mutableMapOf<String, MarkerMotion>() }
    val lastApplied = remember { mutableMapOf<String, MarkerSample>() }

    LaunchedEffect(samples) {
        val currentIds = samples.mapTo(hashSetOf()) { it.id }
        for (id in motions.keys + displayed.keys + lastApplied.keys) {
            if (id in currentIds) continue
            motions.remove(id)
            displayed.remove(id)
            lastApplied.remove(id)
        }

        // Một mốc thời gian DUY NHẤT cho cả lô retarget này — cùng nguồn đồng hồ với `frameNanos`
        // đọc bên trong vòng lặp bên dưới (cả hai đều là "frame time" của Choreographer).
        val nowNanos = withFrameNanos { it }

        for (sample in samples) {
            val previousSample = lastApplied[sample.id]
            if (previousSample == sample) continue // nội dung mẫu không đổi — không đụng animation đang chạy

            lastApplied[sample.id] = sample
            val previousDisplayed = displayed[sample.id]

            if (previousSample == null || previousDisplayed == null) {
                displayed[sample.id] = AnimatedMarkerPosition(sample.latitude, sample.longitude, sample.bearingDegrees)
                motions.remove(sample.id)
                continue
            }

            val jumpMeters = haversineMeters(
                previousDisplayed.latitude, previousDisplayed.longitude, sample.latitude, sample.longitude,
            )
            if (isSpawnJump(jumpMeters, SPAWN_SNAP_THRESHOLD_M)) {
                displayed[sample.id] = AnimatedMarkerPosition(sample.latitude, sample.longitude, previousDisplayed.bearingDegrees)
                motions.remove(sample.id)
                continue
            }

            val durationMs = (sample.recordedAtMs - previousSample.recordedAtMs).coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
            motions[sample.id] = MarkerMotion(
                fromLatitude = previousDisplayed.latitude,
                fromLongitude = previousDisplayed.longitude,
                fromBearing = previousDisplayed.bearingDegrees,
                toLatitude = sample.latitude,
                toLongitude = sample.longitude,
                toBearing = sample.bearingDegrees,
                startedAtNanos = nowNanos,
                durationMs = durationMs,
            )
        }

        while (isActive && motions.isNotEmpty()) {
            withFrameNanos { frameNanos ->
                val finishedIds = mutableListOf<String>()
                for ((id, motion) in motions) {
                    val elapsedMs = (frameNanos - motion.startedAtNanos) / NANOS_PER_MS
                    val progress = progressOf(elapsedMs, motion.durationMs)
                    displayed[id] = AnimatedMarkerPosition(
                        latitude = lerpDegrees(motion.fromLatitude, motion.toLatitude, progress),
                        longitude = lerpDegrees(motion.fromLongitude, motion.toLongitude, progress),
                        bearingDegrees = lerpBearing(motion.fromBearing, motion.toBearing, progress),
                    )
                    if (progress >= 1f) finishedIds += id
                }
                finishedIds.forEach { motions.remove(it) }
            }
        }
    }

    return displayed
}
