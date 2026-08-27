package com.example.pion.family.tracker.demo.ui.feature.map.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

/**
 * Banner thường trực giải thích quyền còn thiếu — US-02 (vị trí bị từ chối), US-04 (thông báo bị
 * tắt). Không phải toast/snackbar: phải nằm im trên màn hình để người dùng luôn thấy trạng thái
 * thật (phase-04 Risk Assessment).
 *
 * `internal` trong `feature/map/component/`, KHÔNG `designsystem/component/` — LLM.md §12: một
 * composable chỉ chuyển lên `designsystem/component/` khi THẬT SỰ có ≥2 feature dùng. Ở phase-04
 * chỉ Map dùng nó; chuyển lên sớm khi phase-06/07 có màn thứ hai cần banner tương tự.
 */
/**
 * Chồng banner quyền của màn Bản đồ — 0, 1 hay 2 tấm tuỳ trạng thái quyền. Phát thẳng vào `Column`
 * của người gọi (không tự dựng layout), nên khoảng cách giữa hai tấm do `verticalArrangement` ở
 * `MapScreen` quyết định — một chỗ duy nhất định khoảng cách cho cả nút "Chỉ đường" lẫn banner.
 *
 * Tồn tại để `MapScreen.kt` không vượt trần 200 dòng (`LLM.md` §5) sau khi khu vực trên cùng phải
 * gánh thêm nút "Chỉ đường" (feedback #1) — cùng lý do `NavigateToMemberButton.kt` được tách ra ở
 * phase-06. Ở lại file này thay vì mở file thứ ba: nó không làm gì khác ngoài xếp chồng chính
 * [PermissionBanner] ngay bên trên.
 */
@Composable
internal fun PermissionBannerStack(showLocationDegraded: Boolean, showNotificationsOff: Boolean) {
    if (showLocationDegraded) {
        PermissionBanner(message = stringResource(R.string.map_banner_location_degraded))
    }
    if (showNotificationsOff) {
        PermissionBanner(message = stringResource(R.string.map_banner_notifications_off))
    }
}

@Composable
internal fun PermissionBanner(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(Dimens.SpaceMd),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
