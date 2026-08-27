package com.example.pion.family.tracker.demo.ui.feature.map.component

import androidx.compose.runtime.Composable
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.ui.designsystem.theme.PrimaryBlue
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMapComposable

/** Bốn hằng số của ĐÚNG một composable, không có call site thứ hai — khai tại file như
 * `SELF_Z_INDEX` (`FamilyTrackerMap.kt`) và `ZONE_LABEL_Z_INDEX` (`ZoneCircles.kt`). */
private const val FILL_ALPHA = 0.10f
private const val STROKE_ALPHA = 0.30f
private const val STROKE_WIDTH_PX = 1f
private const val Z_INDEX = 0f

/**
 * FR-4 (phase-01, D4 — `decisions.md` §C3) — vòng sai số quanh chấm xanh self, US-43/QA-SRM-23.
 * Bán kính = đúng [accuracyMeters] báo về, KHÔNG làm tròn/quy đổi. Chỉ vẽ khi vượt
 * `MAX_ACCURACY_M`: dưới ngưỡng đó vòng nhỏ tới mức chỉ gây nhiễu thị giác (r-03 Q2).
 *
 * **Không dialog, không toast, không chữ "lỗi"** — vòng này nói "đây là mức sai số hiện tại",
 * không nói "vị trí này không đáng tin". `zIndex` = 0f, mức thấp nhất đang dùng trên bản đồ (bằng
 * `Circle` của `ZoneCircles`, dưới `ZONE_LABEL_Z_INDEX` 0.5f và `SELF_Z_INDEX` 2f). Bán kính có
 * thể phình tới hàng trăm mét, nên thứ thật sự giữ cho zone còn nhìn thấy là [FILL_ALPHA] 0.10 +
 * [STROKE_WIDTH_PX] 1px, không phải riêng thứ tự lớp (Risk Assessment phase-01).
 *
 * Màu lấy từ `designsystem/theme/Color.kt` ([PrimaryBlue]) — cùng sắc với `SelfDot`, chỉ pha
 * alpha ở đây, không literal RGB nào trong file này (LLM.md §12).
 */
@Composable
@GoogleMapComposable
internal fun SelfAccuracyCircle(center: LatLng, accuracyMeters: Float) {
    if (accuracyMeters.toDouble() <= TrackingConstants.MAX_ACCURACY_M) return

    Circle(
        center = center,
        radius = accuracyMeters.toDouble(),
        fillColor = PrimaryBlue.copy(alpha = FILL_ALPHA),
        strokeColor = PrimaryBlue.copy(alpha = STROKE_ALPHA),
        strokeWidth = STROKE_WIDTH_PX,
        zIndex = Z_INDEX,
    )
}
