package com.example.pion.family.tracker.demo.ui.feature.zone

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.ui.core.mvi.UiEffect
import com.example.pion.family.tracker.demo.ui.core.mvi.UiIntent
import com.example.pion.family.tracker.demo.ui.core.mvi.UiState

/**
 * Một thành viên ĐƯỢC THEO DÕI đang ở trong zone. [colorArgb] copy thẳng từ
 * [com.example.pion.family.tracker.demo.domain.model.Member.colorArgb] — cùng nguồn màu với
 * `MemberMarkers` trên bản đồ, nên chấm cam ở dòng zone và marker cam trên Map hiển nhiên là cùng
 * một người, không cần chú giải nào.
 */
data class ZoneMemberChip(
    val id: String,
    val name: String,
    val colorArgb: Int,
)

/**
 * US-12 — trình bày sẵn cho một dòng (tên, bán kính, ai đang ở trong, công tắc thông báo). Dựng ở
 * [ZoneListViewModel], KHÔNG để [ZoneListScreen] tự suy ra từ [com.example.pion.family.tracker.demo.domain.model.Zone]
 * (MVI doc §4 "Never derives business truth", phase-06 Implementation Step 1).
 */
data class ZoneListItem(
    val id: String,
    val name: String,
    val radiusMeters: Float,
    /**
     * Những thành viên được theo dõi đang ở trong zone này — thay cho `isInside: Boolean` của bản
     * đầu tiên. Câu hỏi đã đổi: "tôi có đang ở trong không" (nhị phân, và sai chủ thể — tạo zone
     * quanh chính mình thì dòng nào cũng báo "Đang ở trong") thành "những AI đang ở trong" (danh
     * sách). Rỗng nghĩa là chưa có ai — self không bao giờ xuất hiện ở đây, xem
     * `ObserveZoneMembershipUseCase`.
     */
    val membersInside: List<ZoneMemberChip>,
    /** Một công tắc gộp cho cả `notifyOnEnter`/`notifyOnExit` (US-12 chỉ vẽ MỘT công tắc trên mỗi
     * dòng) — bật/tắt ở đây set CẢ HAI cờ cùng lúc; chỉnh riêng từng cờ là việc của Zone Editor
     * (US-19, hai công tắc độc lập). `true` nếu ít nhất một trong hai cờ đang bật. */
    val notifyEnabled: Boolean,
)

data class ZoneListState(
    val zones: List<ZoneListItem> = emptyList(),
    val isLoading: Boolean = true,
    /** US-14 — sống trong State chứ không phải Effect: hộp thoại xác nhận phải còn đó qua một
     * lần xoay màn hình (phase-06 Implementation Step 3). */
    val pendingDeleteId: String? = null,
) : UiState {
    val isEmpty: Boolean get() = !isLoading && zones.isEmpty()
    val pendingDeleteName: String? get() = zones.firstOrNull { it.id == pendingDeleteId }?.name
}

sealed interface ZoneListIntent : UiIntent {
    data class ZoneTapped(val zoneId: String) : ZoneListIntent
    data class NotifyToggled(val zoneId: String, val enabled: Boolean) : ZoneListIntent
    data class DeleteRequested(val zoneId: String) : ZoneListIntent
    data object DeleteConfirmed : ZoneListIntent
    data object DeleteCancelled : ZoneListIntent
    data object CreateTapped : ZoneListIntent
}

sealed interface ZoneListEffect : UiEffect {
    /** `zoneId == null` — US-15 "nút tạo" ở empty state, mở Editor ở chế độ tạo mới. */
    data class OpenEditor(val zoneId: String?) : ZoneListEffect

    /** ViewModel không build chuỗi hiển thị (MVI doc §5) — mang thẳng [AppError], màn hình tự
     * ánh xạ sang tiếng Việt qua `AppError.toDisplayMessage(context)`, cùng mẫu `MapScreen`. */
    data class ShowMessage(val error: AppError) : ZoneListEffect
}
