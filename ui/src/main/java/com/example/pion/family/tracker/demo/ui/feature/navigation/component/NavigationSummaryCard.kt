package com.example.pion.family.tracker.demo.ui.feature.navigation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.format.DistanceFormat
import com.example.pion.family.tracker.demo.ui.core.format.DurationFormat
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

/**
 * Khoảng cách/ETA/trạng thái. Đọc `NavigationState.distanceMeters`/`isDistanceEstimated` — SỐ ĐÃ
 * TÍNH SẴN ở `:domain` (`ObserveNavigationUseCase`, phase-04), không tự cộng/trừ toạ độ ở đây (cùng
 * luật `RouteStatsCard` "Composable không tự tính lại km", LLM.md §12).
 */
@Composable
internal fun NavigationSummaryCard(
    targetMemberName: String?,
    distanceMeters: Double?,
    isDistanceEstimated: Boolean,
    durationSeconds: Long?,
    hasArrived: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.SpaceMd)) {
            Text(
                text = if (hasArrived) {
                    stringResource(R.string.navigation_summary_heading_arrived)
                } else {
                    stringResource(R.string.navigation_summary_heading_active, targetMemberName.orEmpty())
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (distanceMeters != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    Text(text = DistanceFormat.format(distanceMeters), style = MaterialTheme.typography.bodyMedium)
                    if (isDistanceEstimated) {
                        Text(
                            text = stringResource(R.string.navigation_distance_estimated_suffix),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (durationSeconds != null) {
                        Text(text = DurationFormat.format(durationSeconds * 1_000L), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
