package com.example.pion.family.tracker.demo.ui.feature.zone.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

private const val RADIUS_STEP_M = 10
private val RADIUS_RANGE = TrackingConstants.ZONE_RADIUS_MIN_M.toFloat()..TrackingConstants.ZONE_RADIUS_MAX_M.toFloat()

/** Số bước NGOÀI 2 đầu mút cho `Slider.steps` — 50..2000 bước 10 có (2000-50)/10 - 1 = 194 điểm giữa. */
private val RADIUS_STEPS = ((RADIUS_RANGE.endInclusive - RADIUS_RANGE.start) / RADIUS_STEP_M).toInt() - 1

/**
 * US-17 — 50m–2000m bước 10m, hình tròn cập nhật realtime (giá trị đọc thẳng từ
 * `ZoneEditorState.radiusMeters`, không có state cục bộ nào che nó, nên `ZoneCenterMap` vẽ lại
 * ngay khung hình kéo tiếp theo). Cảnh báo dưới 100m hiện NGAY trên slider (PRD §3.1, Key
 * Insight #2) — không giấu trong tài liệu.
 */
@Composable
internal fun RadiusSlider(
    radiusMeters: Float,
    showLowRadiusWarning: Boolean,
    onRadiusChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.zone_editor_radius_label), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.zone_radius_value, radiusMeters.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = true),
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = radiusMeters,
            onValueChange = onRadiusChanged,
            valueRange = RADIUS_RANGE,
            steps = RADIUS_STEPS,
        )
        if (showLowRadiusWarning) {
            Text(
                text = stringResource(R.string.zone_editor_low_radius_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Dimens.SpaceXs),
            )
        }
    }
}
