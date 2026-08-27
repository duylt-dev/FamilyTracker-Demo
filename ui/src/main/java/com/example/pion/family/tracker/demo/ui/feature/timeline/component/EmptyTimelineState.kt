package com.example.pion.family.tracker.demo.ui.feature.timeline.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/**
 * PRD §3.4 — "Chưa có sự kiện nào. Tạo zone rồi thử nút mô phỏng lộ trình." Kịch bản người dùng
 * MỚI (chưa từng có `zone_events`), rất dễ quên kiểm khi phát triển chỉ toàn thấy màn có dữ liệu.
 *
 * Đọc CHUNG cờ `simulatorEnabled` với `SimulateRouteButton` (`feature/history/component/`,
 * phase-09) qua cùng Koin qualifier — phase file Implementation Step 6: "vẫn đọc chung cờ ... để
 * hai màn không lệch nhau". Gợi ý nút mô phỏng chỉ hiện khi nút đó thật sự tồn tại ở màn History.
 */
@Composable
internal fun EmptyTimelineState(modifier: Modifier = Modifier) {
    val simulatorEnabled = koinInject<Boolean>(qualifier = named("simulatorEnabled"))

    Column(
        modifier = modifier.fillMaxSize().padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.timeline_empty_title), textAlign = TextAlign.Center)
        if (simulatorEnabled) {
            Text(
                text = stringResource(R.string.timeline_empty_hint),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.SpaceSm),
            )
        }
    }
}
