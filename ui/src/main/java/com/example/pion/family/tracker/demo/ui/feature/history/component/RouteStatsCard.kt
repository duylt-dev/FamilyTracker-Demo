package com.example.pion.family.tracker.demo.ui.feature.history.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.domain.tracking.RouteStats
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.format.DistanceFormat
import com.example.pion.family.tracker.demo.ui.core.format.DurationFormat
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import java.util.Locale

/**
 * US-29 — thẻ "tổng km · thời lượng · tốc độ trung bình" (PRD §5.7). Đọc thẳng [RouteStats]
 * (`HistoryState.stats`, tính sẵn từ `RouteStats.of` ở `:domain`) — KHÔNG tự cộng lại quãng đường
 * ở đây (phase file Implementation Step 7: "Composable không tự tính lại km").
 */
@Composable
internal fun RouteStatsCard(stats: RouteStats, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(DistanceFormat.format(stats.distanceMeters))
            Text(DurationFormat.format(stats.durationMs))
            Text(
                stringResource(
                    R.string.history_stats_speed,
                    String.format(Locale.US, "%.1f", stats.averageSpeedKmh),
                ),
            )
        }
    }
}
