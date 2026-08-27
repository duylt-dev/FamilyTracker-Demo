package com.example.pion.family.tracker.demo.ui.feature.map.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberUpdatedMarkerState

private const val ZONE_LABEL_Z_INDEX = 0.5f

/**
 * Zone rings — US-07, PRD §5.5. `Circle.strokeWidth` là pixel màn hình chứ không phải mét
 * (researcher-02 §2.2) nên viền mảnh dần khi zoom ra — chấp nhận theo Key Insight #2.
 *
 * Tên zone dùng [MarkerComposable] (composable dựng thành bitmap, LUÔN hiện), không phải
 * `Marker` + `title` — `title` chỉ hiện trong info window khi CHẠM vào marker, không thoả yêu
 * cầu "tên zone hiển thị ở tâm" (PRD §5.5, hiện sẵn không cần chạm) — phase-05 Implementation
 * Step 5, lý do chọn ghi ở đây theo đúng yêu cầu "chọn cái nào build được, ghi lý do vào KDoc".
 */
@Composable
@GoogleMapComposable
internal fun ZoneCircles(zones: List<Zone>) {
    zones.forEach { zone ->
        val center = LatLng(zone.latitude, zone.longitude)
        val color = Color(zone.colorArgb)
        Circle(
            center = center,
            radius = zone.radiusMeters.toDouble(),
            fillColor = color.copy(alpha = Dimens.ZONE_FILL_ALPHA),
            strokeColor = color,
            strokeWidth = Dimens.ZONE_STROKE_WIDTH_PX,
        )
        MarkerComposable(
            zone.id,
            zone.name,
            state = rememberUpdatedMarkerState(position = center),
            zIndex = ZONE_LABEL_Z_INDEX,
        ) {
            Text(
                text = zone.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.SpaceXs))
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
            )
        }
    }
}
