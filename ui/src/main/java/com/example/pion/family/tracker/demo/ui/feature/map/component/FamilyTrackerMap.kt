package com.example.pion.family.tracker.demo.ui.feature.map.component

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.MemberLocation
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.ui.designsystem.component.rememberAnimatedMarkerPositions
import com.example.pion.family.tracker.demo.ui.designsystem.component.toMarkerSample
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_ZOOM = 15f
private const val SELF_Z_INDEX = 2f
private const val SELF_MARKER_ID = "self"

/**
 * Bản đồ thật — tách khỏi chrome (banner/toggle/bottom bar) đúng theo Architecture của phase-05,
 * để những state đó đổi (vd. `isTracking`) không kéo cả cây bản đồ recompose theo (Key Insight #3).
 *
 * Camera canh lần đầu bằng `.move()` (không animate — MVI doc §8 "Apply the first camera move
 * without an animation", tránh hiệu ứng bay từ toạ độ (0,0) mỗi lần mở màn), gác bằng
 * [hasCenteredOnce] để lần cập nhật vị trí thứ hai không kéo camera về giữa lúc người dùng đang
 * kéo xem chỗ khác (phase-05 Risk Assessment). [initialCameraTarget] (`MapState.initialCameraTarget`)
 * ưu tiên vị trí của mình nhưng rơi về một thành viên bất kỳ nếu self chưa có điểm nào — không thì
 * lần mở app ĐẦU TIÊN (trước khi ai bật công tắc) là một bản đồ thế giới trống trơn, xác nhận thật
 * bằng chạy trên `emulator-5554` lúc soạn phase này.
 *
 * phase-03 — chấm xanh self dùng CÙNG bộ nội suy [rememberAnimatedMarkerPositions] với
 * `MemberMarkers`, chỉ nội suy VỊ TRÍ, KHÔNG truyền `rotation` (chấm tròn nên xoay vô nghĩa — Key
 * Insight #8 của phase file). `SelfAccuracyCircle` dùng CHUNG toạ độ đã nội suy với chấm xanh
 * (`selfLatLng`) để vòng sai số không tách rời khỏi chấm trong lúc đang animate.
 *
 * zIndex khai theo thứ tự vẽ: zone trước, member sau, self cuối — self marker luôn nổi trên cùng.
 */
@Composable
fun FamilyTrackerMap(
    zones: List<Zone>,
    otherMembers: List<MemberLocation>,
    self: MemberLocation?,
    initialCameraTarget: LocationPoint?,
    hasCenteredOnce: Boolean,
    onLongClick: (lat: Double, lng: Double) -> Unit,
    onMemberTapped: (memberId: String) -> Unit,
    onCameraCentered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()
    val selfPoint = self?.lastLocation
    val selfSamples = listOfNotNull(selfPoint?.toMarkerSample(id = SELF_MARKER_ID))
    val animatedSelfPositions = rememberAnimatedMarkerPositions(selfSamples)

    // MVI doc §8 "Warm a heavy platform engine before its composable needs it" — MapsInitializer
    // fetches/links the Maps SDK renderer synchronously on whichever thread calls it; run it off
    // the main thread so the bill does not land on the first frame the user watches (PRD §7.1,
    // phase-05 Key Insight #4). Guarded by a process-wide flag — MapsInitializer.initialize() is
    // itself `synchronized` and idempotent, but re-hopping to Dispatchers.IO on every recomposition
    // of this composable would be pointless work.
    LaunchedEffect(Unit) { warmUpMapsEngineOnce(context) }

    LaunchedEffect(initialCameraTarget, hasCenteredOnce) {
        if (!hasCenteredOnce && initialCameraTarget != null) {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(initialCameraTarget.latitude, initialCameraTarget.longitude),
                    DEFAULT_ZOOM,
                ),
            )
            onCameraCentered()
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false), // custom marker, không dùng lớp vị trí của SDK — Key Insight #1
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
        onMapLongClick = { latLng -> onLongClick(latLng.latitude, latLng.longitude) },
    ) {
        ZoneCircles(zones = zones)
        MemberMarkers(members = otherMembers, onMemberTapped = onMemberTapped)
        val selfPosition = animatedSelfPositions[SELF_MARKER_ID]
        if (self != null && selfPoint != null && selfPosition != null) {
            val selfLatLng = LatLng(selfPosition.latitude, selfPosition.longitude)
            // phase-01, D4 — vòng sai số vẽ TRƯỚC chấm xanh vì thứ tự vẽ là thứ tự chồng lớp;
            // ngưỡng hiện vòng nằm trong chính SelfAccuracyCircle. Bán kính giữ nguyên
            // `selfPoint.accuracyMeters` (mẫu THẬT, không nội suy — chỉ VỊ TRÍ được nội suy, FR-2).
            SelfAccuracyCircle(center = selfLatLng, accuracyMeters = selfPoint.accuracyMeters)
            MarkerComposable(
                keys = arrayOf(SELF_MARKER_ID),
                state = rememberUpdatedMarkerState(position = selfLatLng),
                zIndex = SELF_Z_INDEX,
            ) {
                SelfDot(color = Color(self.member.colorArgb))
            }
        }
    }
}

/** US-06 — chấm xanh dương tại vị trí thiết bị. Viền trắng để nổi trên nền bản đồ bất kể zone/tile
 * bên dưới màu gì; lớn hơn [MemberDot] một chút để phân biệt "tôi" với "thành viên khác". */
@Composable
private fun SelfDot(color: Color) {
    Box(
        modifier = Modifier
            .size(Dimens.SelfDotSize)
            .background(color, CircleShape)
            .border(Dimens.SelfDotBorderWidth, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

@Volatile
private var mapsEngineWarmedUp = false

private suspend fun warmUpMapsEngineOnce(context: Context) {
    if (mapsEngineWarmedUp) return
    mapsEngineWarmedUp = true
    withContext(Dispatchers.IO) {
        runCatching { MapsInitializer.initialize(context.applicationContext) }
    }
}
