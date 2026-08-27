package com.example.pion.family.tracker.demo.ui.feature.zone.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.google.maps.android.compose.CameraPositionState

private val CROSSHAIR_SIZE = 28.dp
private val CROSSHAIR_STROKE_WIDTH = 2.dp

/**
 * US-18 — tâm zone LUÔN là điểm giữa màn hình bản đồ (researcher-02 §3.4, Key Insight #3):
 * crosshair là một `Canvas` cố định ở [Alignment.Center] của khung bản đồ, KHÔNG bao giờ đọc
 * `cameraPositionState.position` để tự định vị — nó đã ở đúng chỗ theo layout, không theo dữ liệu.
 *
 * [cameraPositionState] chỉ được đọc bên trong `LaunchedEffect`, khoá theo `isMoving` — nghĩa là
 * đọc `.position.target` tối đa 2 lần mỗi cử chỉ kéo (lúc bắt đầu và lúc dừng), KHÔNG mỗi khung
 * hình. [onCenterMoved] chỉ bắn ở cạnh kéo XONG (`true -> false`), đúng debounce Implementation
 * Step 8 đòi — tránh `setState` dội liên tục làm cả cây recompose (Risk Assessment).
 */
@Composable
internal fun CenterCrosshair(
    cameraPositionState: CameraPositionState,
    onCenterMoved: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wasMoving by remember { mutableStateOf(false) }

    LaunchedEffect(cameraPositionState.isMoving) {
        val isMoving = cameraPositionState.isMoving
        if (wasMoving && !isMoving) {
            val target = cameraPositionState.position.target
            onCenterMoved(target.latitude, target.longitude)
        }
        wasMoving = isMoving
    }

    val lineColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(CROSSHAIR_SIZE)) {
        val strokePx = CROSSHAIR_STROKE_WIDTH.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawLine(lineColor, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = strokePx)
        drawLine(lineColor, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = strokePx)
    }
}
