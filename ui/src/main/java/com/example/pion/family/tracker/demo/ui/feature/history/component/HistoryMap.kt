package com.example.pion.family.tracker.demo.ui.feature.history.component

import com.example.pion.family.tracker.demo.ui.core.logging.FtdLog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val TAG = "FTD_EVENT"
private const val CAMERA_BOUNDS_PADDING_PX = 96
private const val FOCUS_ZOOM = 17f
private const val CAMERA_RETRY_DELAY_MS = 100L

/**
 * US-28 — `GoogleMap` + [RoutePolyline] + canh camera vào bounds của chuyến đang chọn. File THÊM
 * ngoài 5 file `component/` liệt kê trong phase-08 (`DayPickerBar`/`RouteStatsCard`/`SessionList`/
 * `RoutePolyline`/`EmptyRouteState`) — cùng lý do `ZoneCenterMap.kt` ở phase-06: `HistoryScreen.kt`
 * đã đủ dài với date picker + stats card + session list để vượt 200 dòng (LLM.md §5) nếu còn ôm cả
 * việc dựng `GoogleMap`/`cameraPositionState` ở đây.
 *
 * Canh camera (phase file Implementation Step 6): lần canh ĐẦU TIÊN trong vòng đời composable này
 * dùng `.move()` (không animate — cùng luật `FamilyTrackerMap` phase-05, tránh hiệu ứng bay từ toạ
 * độ (0,0) mỗi lần vào tab History), các lần chọn chuyến kế tiếp dùng `.animate()`.
 * `newLatLngBounds` ném `IllegalStateException` nếu map chưa layout xong (researcher-02 §3.3) —
 * bắt, `delay(100)` rồi thử lại bằng `move()`.
 *
 * [focusPoint] (US-35, phase-10 — Timeline mở History canh đúng vị trí sự kiện) THẮNG bounds của
 * chuyến ở đúng LẦN CANH ĐẦU TIÊN nếu route mang toạ độ focus; các lần chọn chuyến sau đó vẫn canh
 * theo bounds bình thường. Chưa có màn Timeline nào gửi hiệu ứng này ở phase-08 — đường ống chỉ để
 * sẵn sàng cho phase-10, không tự kiểm chứng được ở phase này.
 */
@Composable
internal fun HistoryMap(
    session: TrackSession?,
    memberColorArgb: Int,
    focusPoint: LatLng?,
    day: LocalDate,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()
    var hasCenteredOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(session?.id, focusPoint) {
        if (!hasCenteredOnce && focusPoint != null) {
            hasCenteredOnce = true
            moveOrRetry(cameraPositionState) { it.move(CameraUpdateFactory.newLatLngZoom(focusPoint, FOCUS_ZOOM)) }
            return@LaunchedEffect
        }
        val points = session?.points.orEmpty()
        if (points.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        val update = CameraUpdateFactory.newLatLngBounds(bounds, CAMERA_BOUNDS_PADDING_PX)
        if (!hasCenteredOnce) {
            hasCenteredOnce = true
            moveOrRetry(cameraPositionState) { it.move(update) }
        } else {
            try {
                cameraPositionState.animate(update)
            } catch (unused: IllegalStateException) {
                delay(CAMERA_RETRY_DELAY_MS)
                cameraPositionState.move(update)
            }
        }
    }

    // fix-phase-08 — field ĐỔI TÊN từ `renderMs` thành `frameMs`: tên cũ hứa nhiều hơn thứ nó đo.
    // `LaunchedEffect(session?.id)` chỉ BẮT ĐẦU chạy SAU KHI composition (bao gồm cả
    // `RoutePolyline`'s `PolyUtil.simplify`, xem `RoutePolyline.kt`) đã xong — `remember` chạy đồng
    // bộ trong pha composition, còn effect chỉ launch sau khi composition apply xong. Nên `frameMs`
    // ở đây CHỈ là thời gian chờ MỘT khung hình kế tiếp sau khi mọi thứ đã compose xong, KHÔNG bao
    // gồm Room query/mapping/split (`history_query_split`, `:data`) lẫn simplify
    // (`history_simplify`, `RoutePolyline.kt`). Bằng chứng đo thật (log `nanoTime` cả 2 phía) nằm ở
    // `reports/fix-phase-08-report.md`. Tổng pipeline thật = cộng cả 3 sự kiện lại, không phải đọc
    // một mình field này — PRD §10 đã cập nhật theo hướng này.
    LaunchedEffect(session?.id) {
        val startNanos = System.nanoTime()
        withFrameNanos { }
        if (session != null) {
            val frameMs = (System.nanoTime() - startNanos) / 1_000_000
            FtdLog.d(TAG, "history_rendered day=$day pointCount=${session.points.size} frameMs=$frameMs")
        }
    }

    GoogleMap(modifier = modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
        session?.let { RoutePolyline(points = it.points, colorArgb = memberColorArgb) }
    }
}

private suspend fun moveOrRetry(
    cameraPositionState: CameraPositionState,
    move: (CameraPositionState) -> Unit,
) {
    try {
        move(cameraPositionState)
    } catch (unused: IllegalStateException) {
        delay(CAMERA_RETRY_DELAY_MS)
        move(cameraPositionState)
    }
}
