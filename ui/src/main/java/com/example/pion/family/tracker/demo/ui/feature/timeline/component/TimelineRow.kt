package com.example.pion.family.tracker.demo.ui.feature.timeline.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.format.DurationFormat
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.designsystem.theme.ZoneEnterGreen
import com.example.pion.family.tracker.demo.ui.designsystem.theme.ZoneExitRed
import com.example.pion.family.tracker.demo.ui.feature.timeline.TimelineItem

/** PRD §5.8 layout dot ("🟢"/"🔴") — cùng cỡ chấm màu với `ColorPicker.kt` (phase-06), không dùng
 * `material-icons-core` (không nằm trong dependency `:ui`, xem KDoc `FamilyTrackerBottomBar`). */
private val EVENT_DOT_SIZE = 16.dp

/**
 * US-34 — một dòng: chấm màu vào/ra (`#2E7D32`/`#C62828`, PRD §5.2) · "Đã đến/Đã rời {zone}" ·
 * tên thành viên nếu khác mình (US-34) · giờ `HH:mm`. Bấm dòng → `TimelineIntent.EventTapped`.
 */
@Composable
internal fun TimelineRow(item: TimelineItem, onTapped: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onTapped,
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Box(modifier = Modifier.size(EVENT_DOT_SIZE).clip(CircleShape).background(dotColorFor(item.type)))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = eventTitle(item), style = MaterialTheme.typography.titleMedium)
                item.memberName?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
            Text(text = DurationFormat.formatClock(item.time), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun eventTitle(item: TimelineItem): String = when (item.type) {
    ZoneEventType.ENTER -> stringResource(R.string.timeline_event_enter_title, item.zoneName)
    ZoneEventType.EXIT -> stringResource(R.string.timeline_event_exit_title, item.zoneName)
}

private fun dotColorFor(type: ZoneEventType) = when (type) {
    ZoneEventType.ENTER -> ZoneEnterGreen
    ZoneEventType.EXIT -> ZoneExitRed
}
