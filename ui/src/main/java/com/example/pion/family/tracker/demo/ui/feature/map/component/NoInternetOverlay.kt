package com.example.pion.family.tracker.demo.ui.feature.map.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

/**
 * phase-07 (US-47, D8) — lớp phủ TRONG NỘI DUNG, chồng lên khung bản đồ, **KHÔNG PHẢI**
 * `Dialog`/`AlertDialog`. Quyết định chốt 2026-08-25 (phase doc Key Insight #7): `Dialog` của
 * Compose dựng một **window** riêng nuốt mọi chạm, nên thanh tab bên dưới không bấm được — vỡ
 * thẳng AC "Zone, Lịch sử, Cài đặt vẫn dùng bình thường" của US-47 và trúng tiêu chí KHÔNG ĐẠT
 * "cả app bị khoá cứng" của UAT-04. **Không đổi lại thành `Dialog`/`AlertDialog` dù trông "đúng
 * hơn"** — `MapBlockerIsNotADialogTest` khoá lệnh cấm này bằng cách quét mã nguồn.
 *
 * Ba thứ bắt buộc, mỗi thứ khoá một bước của QA-SRM-13:
 * - [BackHandler] `enabled = true` với thân rỗng → Back không đóng, cũng không pop nav.
 * - `.clickable(indication = null) {}` trên scrim → chạm vào bản đồ bên dưới không xuyên qua được.
 * - Không nút nào trong [Surface] → không có đường đóng bằng thao tác; đóng CHỈ do `hasInternet`
 *   đổi (FR-4), tức composable này biến mất khi `MapState.showNoInternetOverlay` thành `false`.
 *
 * **Scrim này KHÔNG chặn tất cả — thứ tự trong `Box` của `MapScreen` mới là thứ quyết định**
 * (§13 Fixed #33). Phần tử viết SAU lớp phủ nổi lên trên và vẫn bấm được; viết TRƯỚC thì bị chặn.
 * Luật: **thứ gì chạy được ngoại tuyến thì không được chặn.** `TrackingToggle` đứng sau (theo dõi
 * = GPS + Room, không cần mạng — chặn nó là tạo lại đúng lỗ mà D8 sinh ra để chặn); nút "Chỉ
 * đường" đứng trước (màn Dẫn đường thật sự cần internet để lấy tuyến). `MapBlockerIsNotADialogTest`
 * khoá cả hai chiều — đừng "dọn cho gọn" bằng cách đưa lớp phủ về làm phần tử cuối.
 */
@Composable
internal fun NoInternetOverlay(modifier: Modifier = Modifier) {
    // Back không rời màn Bản đồ trong lúc đang bị chặn — thay cho
    // DialogProperties(dismissOnBackPress = false) của phương án AlertDialog đã bị bác.
    BackHandler(enabled = true) {}
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = Dimens.OVERLAY_SCRIM_ALPHA))
            // nuốt MỌI chạm rơi vào khung bản đồ; null/null = không ripple, không nảy.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Text(stringResource(R.string.map_no_internet_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.map_no_internet_message), style = MaterialTheme.typography.bodyMedium)
            } // KHÔNG nút nào — không có đường đóng bằng thao tác
        }
    }
}
