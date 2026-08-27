package com.example.pion.family.tracker.demo.ui.feature.zone.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState

private const val EDITOR_ZOOM = 16f

/**
 * US-17/US-18 — bản đồ + vòng tròn xem trước + crosshair của Zone Editor. File RIÊNG ngoài 4 file
 * `component/` liệt kê trong phase file (`ZoneRow`/`RadiusSlider`/`ColorPicker`/`CenterCrosshair`)
 * — quyết định lúc viết code, không phải sai lệch ngẫu nhiên: `ZoneEditorScreen.kt` đã đủ dài với
 * form (tên, slider, màu, 2 công tắc) để vượt 200 dòng (LLM.md §5) nếu còn ôm cả việc dựng
 * `GoogleMap`/`cameraPositionState` ở đây. Xem dev report phase-06 "Sai lệch so với file phase".
 *
 * `centerLat`/`centerLng`/`hasCenteredOnce` đọc từ `ZoneEditorState` — CÙNG cơ chế
 * `hasCenteredOnce` đã dùng ở `FamilyTrackerMap` (phase-05 Key Insight #3): `.move()` (không
 * animate) đúng MỘT lần khi `hasCenteredOnce` chuyển `false -> true`, bất kể tâm đến từ route,
 * self location hay zone đã lưu (`ZoneEditorViewModel` 3 nhánh). Sau lần đó, `centerLat`/`centerLng`
 * còn đổi tiếp (do người dùng kéo bản đồ, debounce qua `CenterCrosshair`) nhưng KHÔNG kéo camera
 * `.move()` lại — camera đã ở đúng chỗ người dùng vừa kéo tới, gọi lại sẽ giật ngược.
 *
 * `Circle.center` đọc thẳng `cameraPositionState.position.target` MỖI khung hình khi đang kéo —
 * đây CHÍNH là chỗ LLM.md §13 Open #1 chỉ đích danh cần đo lại (researcher-02 §3.4), khác Map
 * screen (phase-05, kéo/zoom không đụng recomposition Compose). Bằng chứng đo thật ở dev report.
 */
@Composable
internal fun ZoneCenterMap(
    centerLat: Double,
    centerLng: Double,
    hasCenteredOnce: Boolean,
    radiusMeters: Float,
    colorArgb: Int,
    onCenterMoved: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()
    val latestCenter = rememberUpdatedState(centerLat to centerLng)

    LaunchedEffect(hasCenteredOnce) {
        if (hasCenteredOnce) {
            val (lat, lng) = latestCenter.value
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), EDITOR_ZOOM))
        }
    }

    Box(modifier = modifier) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
            val color = Color(colorArgb)
            Circle(
                center = cameraPositionState.position.target,
                radius = radiusMeters.toDouble(),
                fillColor = color.copy(alpha = Dimens.ZONE_FILL_ALPHA),
                strokeColor = color,
                strokeWidth = Dimens.ZONE_STROKE_WIDTH_PX,
            )
        }
        CenterCrosshair(
            cameraPositionState = cameraPositionState,
            onCenterMoved = onCenterMoved,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
