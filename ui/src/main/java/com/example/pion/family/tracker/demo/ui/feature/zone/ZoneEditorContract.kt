package com.example.pion.family.tracker.demo.ui.feature.zone

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.ui.core.mvi.UiEffect
import com.example.pion.family.tracker.demo.ui.core.mvi.UiIntent
import com.example.pion.family.tracker.demo.ui.core.mvi.UiState
import com.example.pion.family.tracker.demo.ui.designsystem.theme.ZoneColorPalette
import java.time.Instant

/**
 * US-16→US-21. [zoneId] `null` = chế độ tạo. `centerLat`/`centerLng`/`zoneId` là tham số route,
 * đọc vào initial state ở constructor `ZoneEditorViewModel` (MVI doc §3 luật 5) — mọi field KHÁC
 * (tên, bán kính, màu, 2 công tắc, `createdAt` gốc) chỉ có giá trị THẬT sau khi `observeZones()`
 * phát lần đầu ở chế độ sửa, nên [isLoaded] gác để composable không vẽ form rỗng đè lên dữ liệu
 * đang tải, và [hasCenteredOnce] gác để `ZoneCenterMap` chỉ `.move()` camera đúng MỘT lần dù tâm
 * đến từ 1 trong 3 nguồn khác nhau (route, self location, hay zone đã lưu — Implementation Step 5).
 */
data class ZoneEditorState(
    val zoneId: String? = null,
    val name: String = "",
    val radiusMeters: Float = TrackingConstants.ZONE_RADIUS_DEFAULT_M.toFloat(),
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val colorArgb: Int = ZoneColorPalette.COLORS.first(),
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val zoneCount: Int = 0,
    val isSaving: Boolean = false,
    val isLoaded: Boolean = false,
    val hasCenteredOnce: Boolean = false,
) : UiState {
    val isEditing: Boolean get() = zoneId != null
    val isNameValid: Boolean get() = name.trim().length in 1..MAX_NAME_LENGTH

    /** US-21 — chỉ zone MỚI cạnh tranh giới hạn 100; sửa zone có sẵn luôn được phép bấm Lưu (còn
     * đúng-sai cuối cùng vẫn do `SaveZoneUseCase.exists()` quyết — đây chỉ là UI, LLM.md §13). */
    val canSave: Boolean get() = isLoaded && isNameValid && !isSaving && (isEditing || zoneCount < TrackingConstants.MAX_ZONES)

    /** US-21 thông điệp hiển thị khi kho đầy VÀ đang tạo mới. */
    val showZoneLimitWarning: Boolean get() = !isEditing && zoneCount >= TrackingConstants.MAX_ZONES

    /** US-17 — cảnh báo ngay trên slider khi bán kính < 100m (PRD §3.1). */
    val showLowRadiusWarning: Boolean get() = radiusMeters < LOW_RADIUS_WARNING_THRESHOLD_M

    companion object {
        const val MAX_NAME_LENGTH = 40
        const val LOW_RADIUS_WARNING_THRESHOLD_M = 100f
    }
}

sealed interface ZoneEditorIntent : UiIntent {
    data class NameChanged(val name: String) : ZoneEditorIntent
    data class RadiusChanged(val radiusMeters: Float) : ZoneEditorIntent

    /** US-18 — camera dừng kéo, tâm mới báo lên (debounce ở `CenterCrosshair`, không mỗi khung hình). */
    data class CenterMoved(val lat: Double, val lng: Double) : ZoneEditorIntent
    data class ColorSelected(val colorArgb: Int) : ZoneEditorIntent
    data class NotifyOnEnterToggled(val enabled: Boolean) : ZoneEditorIntent
    data class NotifyOnExitToggled(val enabled: Boolean) : ZoneEditorIntent
    data object SaveTapped : ZoneEditorIntent
}

sealed interface ZoneEditorEffect : UiEffect {
    data object NavigateBack : ZoneEditorEffect

    /** ViewModel không build chuỗi hiển thị (MVI doc §5) — mang thẳng [AppError]. US-21 đặc biệt:
     * `SaveZoneUseCase` đã trả sẵn ĐÚNG văn bản PRD §2.4 trong `AppError.Validation.message`, nên
     * màn hình hiện thẳng nó thay vì ánh xạ qua bảng chuỗi chung (xem `ZoneEditorScreen`). */
    data class ShowMessage(val error: AppError) : ZoneEditorEffect
}
