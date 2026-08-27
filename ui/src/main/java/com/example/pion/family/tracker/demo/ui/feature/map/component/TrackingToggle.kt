package com.example.pion.family.tracker.demo.ui.feature.map.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

/**
 * US-09 — công tắc theo dõi nổi ở góc phải dưới bản đồ (PRD §5.5 layout). Tách khỏi
 * [FamilyTrackerMap] để `isTracking` đổi không kéo cây bản đồ (zone/marker) recompose theo
 * (Key Insight #3) — chỉ chính card này vẽ lại.
 */
@Composable
internal fun TrackingToggle(isTracking: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.map_tracking_toggle_label))
            Switch(checked = isTracking, onCheckedChange = { onToggle() })
        }
    }
}
