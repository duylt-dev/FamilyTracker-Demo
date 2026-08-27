package com.example.pion.family.tracker.demo.ui.feature.navigation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.ui.designsystem.component.rememberAnimatedMarkerPositions
import com.example.pion.family.tracker.demo.ui.designsystem.component.toMarkerSample
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.designsystem.theme.PrimaryBlue
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.delay

private const val CAMERA_BOUNDS_PADDING_PX = 120
private const val CAMERA_RETRY_DELAY_MS = 100L
private const val SELF_Z_INDEX = 2f
private const val TARGET_Z_INDEX = 1f
private const val SELF_MARKER_ID = "navigation-self"
private const val TARGET_MARKER_ID = "navigation-target"

/**
 * Bản đồ Google DUY NHẤT của màn dẫn đường — không có bản đồ nền thứ hai, không `TileOverlay` bên
 * thứ ba (Key Insight #5, ranh giới pháp lý). Camera bao cả hai điểm (`LatLngBounds`) đúng MỘT lần,
 * cùng luật `hasCenteredOnce` mà `MapState`/`FamilyTrackerMap` đang dùng (Implementation Step 6) —
 * gác bằng field STATE của ViewModel (không phải `rememberSaveable` cục bộ như `HistoryMap`, vì đây
 * là "cùng luật `MapState`", không phải "cùng luật `HistoryMap`"). **Canh một lần là quyết định đã
 * chốt lại ở feedback #2/#3 (2026-08-26), không phải thiếu sót:** camera KHÔNG bám theo marker, ai
 * muốn nhìn tiếp thì tự kéo.
 *
 * **feedback #2 — marker trượt, không nhảy.** [rememberAnimatedMarkerPositions]
 * (`designsystem/component/`) là ĐÚNG bộ nội suy mà `MemberMarkers`/`FamilyTrackerMap` dùng, không
 * phải một bản sao: hai màn hiển thị cùng những con người đó, và trước thay đổi này màn Dẫn đường
 * gắn thẳng `rememberUpdatedMarkerState` vào mẫu thô nên marker giật một nhịp mỗi 2.5s (nhịp ghi
 * của `MemberMovementSimulator`) trong khi màn Bản đồ trượt mượt. Vì thế [self]/[target] nhận
 * [LocationPoint] chứ không `GeoPoint` — nội suy cần `recordedAt` để biết một bước dài bao lâu.
 *
 * `.move()` không animate cho lần canh đầu (MVI doc §8 "Apply the first camera move without an
 * animation") — tránh hiệu ứng bay từ toạ độ mặc định mỗi lần mở màn.
 */
@Composable
internal fun NavigationMap(
    self: LocationPoint?,
    target: LocationPoint?,
    targetColorArgb: Int?,
    routePolyline: List<GeoPoint>,
    routeStart: GeoPoint?,
    routeEnd: GeoPoint?,
    isStraightLineOnly: Boolean,
    hasCenteredOnce: Boolean,
    onCameraCentered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()
    val samples = listOfNotNull(
        self?.toMarkerSample(id = SELF_MARKER_ID),
        target?.toMarkerSample(id = TARGET_MARKER_ID),
    )
    val animated = rememberAnimatedMarkerPositions(samples)
    val selfLatLng = animated[SELF_MARKER_ID]?.let { LatLng(it.latitude, it.longitude) }
    val targetLatLng = animated[TARGET_MARKER_ID]?.let { LatLng(it.latitude, it.longitude) }

    LaunchedEffect(self, target, hasCenteredOnce) {
        if (hasCenteredOnce || self == null || target == null) return@LaunchedEffect
        val bounds = LatLngBounds.Builder()
            .include(LatLng(self.latitude, self.longitude))
            .include(LatLng(target.latitude, target.longitude))
            .build()
        moveOrRetry(cameraPositionState) {
            it.move(CameraUpdateFactory.newLatLngBounds(bounds, CAMERA_BOUNDS_PADDING_PX))
        }
        onCameraCentered()
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
    ) {
        NavigationPolyline(points = routePolyline)
        // feedback #4 — đầu chạm marker lấy toạ độ ĐANG HIỂN THỊ (đã nội suy), đầu neo vào tuyến
        // lấy mỏ neo thật từ state. Nhánh giảm cấp: chưa có tuyến nào thì chỉ còn MỘT đoạn nối
        // self→đích, không vẽ kèm hai đoạn kia (chúng sẽ trùng lên nó).
        if (isStraightLineOnly) {
            NavigationConnector(points = segment(selfLatLng, targetLatLng))
        } else {
            NavigationConnector(points = segment(selfLatLng, routeStart?.toLatLng()))
            NavigationConnector(points = segment(routeEnd?.toLatLng(), targetLatLng))
        }

        if (selfLatLng != null) {
            MarkerComposable(
                keys = arrayOf(SELF_MARKER_ID),
                state = rememberUpdatedMarkerState(position = selfLatLng),
                anchor = SELF_DOT_ANCHOR,
                zIndex = SELF_Z_INDEX,
            ) { NavigationDot(color = PrimaryBlue, size = Dimens.SelfDotSize, borderWidth = Dimens.SelfDotBorderWidth) }
        }
        if (targetLatLng != null) {
            // Màu giải quyết TRƯỚC khi vào `keys`: `MarkerComposable` chụp lại bitmap khi keys đổi,
            // và màu là thứ DUY NHẤT của pin có thể đổi giữa hai lần vẽ. Vị trí thì KHÔNG được vào
            // keys — nó đổi mỗi khung hình lúc đang nội suy (cùng luật `MemberMarkers`).
            val pinColorArgb = targetColorArgb ?: PrimaryBlue.toArgb()
            MarkerComposable(
                keys = arrayOf(TARGET_MARKER_ID, pinColorArgb),
                state = rememberUpdatedMarkerState(position = targetLatLng),
                anchor = DESTINATION_PIN_ANCHOR,
                zIndex = TARGET_Z_INDEX,
            ) { DestinationPin(color = Color(pinColorArgb)) }
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/** Hai đầu rời nhau -> một đoạn; trùng nhau hoặc thiếu một đầu -> rỗng, [NavigationConnector] bỏ qua. */
private fun segment(from: LatLng?, to: LatLng?): List<LatLng> =
    if (from != null && to != null && from != to) listOf(from, to) else emptyList()

private suspend fun moveOrRetry(cameraPositionState: CameraPositionState, move: (CameraPositionState) -> Unit) {
    try {
        move(cameraPositionState)
    } catch (unused: IllegalStateException) {
        delay(CAMERA_RETRY_DELAY_MS)
        move(cameraPositionState)
    }
}
