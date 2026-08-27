package com.example.pion.family.tracker.demo.ui.feature.history.component

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

/**
 * US-32 — "Chưa có lộ trình nào trong ngày này" + gợi ý dùng nút mô phỏng. CHỈ text, KHÔNG vẽ nút
 * bấm thật ở đây: nút "Mô phỏng lộ trình" (US-33) thuộc phase-09 — `phase-08-history-and-route-
 * playback.md` Overview ghi rõ "Nút 'Mô phỏng lộ trình' ở phase-09", và cờ
 * `BuildConfig.SIMULATOR_ENABLED` chưa tồn tại tới lúc đó (`LLM.md` §14). Một nút không làm gì tệ
 * hơn không có nút — xem mục "Sai lệch" trong dev-phase-08-report.md.
 */
@Composable
internal fun EmptyRouteState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.history_empty_title), textAlign = TextAlign.Center)
        Text(
            text = stringResource(R.string.history_empty_hint),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.SpaceSm),
        )
    }
}
