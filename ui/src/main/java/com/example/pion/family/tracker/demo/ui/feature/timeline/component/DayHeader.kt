package com.example.pion.family.tracker.demo.ui.feature.timeline.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.feature.timeline.TimelineDayLabel

/**
 * US-36 — header dính "Hôm nay" / "Hôm qua" / `dd/MM/yyyy`, vẽ bằng `LazyListScope.stickyHeader`
 * ở `TimelineScreen`. QUYẾT ĐỊNH nhãn nào đã tính xong ở `TimelineViewModel` (múi giờ, hôm nay so
 * với hôm qua) — component này chỉ ánh xạ [TimelineDayLabel] sang chuỗi hiển thị, [TimelineDayLabel.Dated]
 * đã là chuỗi cuối (`dd/MM/yyyy`, không cần `strings.xml`).
 *
 * `Surface` (không phải `Text` trần) để nền không xuyên qua nội dung cuộn phía sau khi header dính
 * — phần bị "dính" trên cùng phải che được các dòng đang cuộn lên dưới nó.
 */
@Composable
internal fun DayHeader(label: TimelineDayLabel, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = when (label) {
                TimelineDayLabel.Today -> stringResource(R.string.timeline_day_today)
                TimelineDayLabel.Yesterday -> stringResource(R.string.timeline_day_yesterday)
                is TimelineDayLabel.Dated -> label.formatted
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        )
    }
}
