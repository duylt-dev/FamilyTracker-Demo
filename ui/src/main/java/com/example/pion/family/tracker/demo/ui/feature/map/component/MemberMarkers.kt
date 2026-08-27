package com.example.pion.family.tracker.demo.ui.feature.map.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.domain.model.MemberLocation
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.component.rememberAnimatedMarkerPositions
import com.example.pion.family.tracker.demo.ui.designsystem.component.toMarkerSample
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MEMBER_Z_INDEX = 1f

/** `Marker.rotation` xoay quanh CHÍNH `anchor` của icon — mặc định `MarkerComposable` là
 * `Offset(0.5f, 1.0f)` (bottom-center), thứ vốn vô hại khi marker chưa từng xoay. Từ phase-03 marker
 * này xoay theo `bearingDegrees` mỗi khung, nên anchor phải là TÂM hình tròn, không thì cả chấm sẽ
 * "swing" quanh mép dưới của nó mỗi khi hướng đổi thay vì xoay tại chỗ. */
private val MEMBER_MARKER_ANCHOR = Offset(0.5f, 0.5f)

/** `withZone` cho formatter khả năng format thẳng một [java.time.Instant] (không có field
 * giờ/phút riêng) bằng cách quy đổi qua múi giờ hệ thống trước khi trích field. */
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * US-08 — "thành viên gia đình KHÁC" ([MapState.otherMembers] đã loại self). Chấm màu tự vẽ qua
 * [MarkerComposable], không phải `BitmapDescriptorFactory.defaultMarker(hue)` — `hue` là góc HSV
 * rời rạc của Android Maps SDK và không tái tạo được đúng 2 trong 3 màu PRD §5.2 (`#E5820C`,
 * `#7B3FF2`). Bấm marker vẫn hiện info window mặc định (tên + snippet) — `onClick` trả `false`
 * để KHÔNG chặn hành vi đó, chỉ ghi thêm lựa chọn hiện tại lên state (US-08, MVI doc §2).
 *
 * phase-03 — vị trí VÀ góc nội suy tuyến tính giữa hai mẫu THẬT liên tiếp qua
 * [rememberAnimatedMarkerPositions] (`AnimatedMarkerPositions.kt`), thay cho jump thẳng mỗi lần có
 * mẫu mới. `rotation` xoay marker theo `bearingDegrees` đã nội suy; `flat = true` dán phẳng lên mặt
 * bản đồ nên xoay/nghiêng theo camera (Key Insight #7 của phase file — `flat = false` là billboard,
 * KHÔNG xoay theo camera, ngược lại thứ một mũi chỉ hướng cần).
 *
 * `keys = arrayOf(member.id)` — KHÔNG đưa vị trí vào key: `MarkerComposable` chụp lại bitmap của
 * [MemberDot] mỗi khi `keys` đổi, và vị trí đổi MỖI KHUNG HÌNH lúc đang animate (NFR-1, FR-1).
 */
@Composable
@GoogleMapComposable
internal fun MemberMarkers(members: List<MemberLocation>, onMemberTapped: (memberId: String) -> Unit) {
    val samples = members.mapNotNull { it.lastLocation?.toMarkerSample(id = it.member.id) }
    val animatedPositions = rememberAnimatedMarkerPositions(samples)

    members.forEach { memberLocation ->
        val location = memberLocation.lastLocation ?: return@forEach
        val member = memberLocation.member
        val position = animatedPositions[member.id] ?: return@forEach
        val updatedAt = TIME_FORMATTER.format(location.recordedAt)
        MarkerComposable(
            keys = arrayOf(member.id),
            state = rememberUpdatedMarkerState(position = LatLng(position.latitude, position.longitude)),
            title = member.name,
            snippet = stringResource(R.string.map_member_updated_at, updatedAt),
            zIndex = MEMBER_Z_INDEX,
            rotation = position.bearingDegrees,
            flat = true,
            anchor = MEMBER_MARKER_ANCHOR,
            onClick = { onMemberTapped(member.id); false },
        ) {
            MemberDot(color = Color(member.colorArgb))
        }
    }
}

@Composable
private fun MemberDot(color: Color) {
    Box(
        modifier = Modifier
            .size(Dimens.MemberDotSize)
            .background(color, CircleShape)
            .border(Dimens.MemberDotBorderWidth, MaterialTheme.colorScheme.surface, CircleShape),
    ) {
        HeadingIndicator(modifier = Modifier.fillMaxSize())
    }
}

/**
 * Mũi chỉ hướng — tam giác nhỏ ở MÉP TRÊN hình tròn (US-40 AC, FR-3). Gọi với `fillMaxSize()` nên
 * bound LUÔN đúng bằng bound của [MemberDot], không phải một `size()` thứ hai phải nhớ chỉnh cho
 * khớp — xem KDoc [MEMBER_MARKER_ANCHOR] cho lý do kích thước bitmap không được đổi khi marker đang
 * xoay. Màu trùng viền chấm (`MaterialTheme.colorScheme.surface`) để nổi rõ trên mọi màu thành
 * viên — không literal (§12).
 */
@Composable
private fun HeadingIndicator(modifier: Modifier = Modifier) {
    val indicatorColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val halfWidth = Dimens.MemberDotHeadingWidth.toPx() / 2f
        val height = Dimens.MemberDotHeadingHeight.toPx()
        val centerX = size.width / 2f
        val topInset = Dimens.MemberDotBorderWidth.toPx()
        val path = Path().apply {
            moveTo(centerX, topInset)
            lineTo(centerX - halfWidth, topInset + height)
            lineTo(centerX + halfWidth, topInset + height)
            close()
        }
        drawPath(path, color = indicatorColor)
    }
}
