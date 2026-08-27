package com.example.pion.family.tracker.demo.ui.feature.permission

import com.example.pion.family.tracker.demo.ui.core.mvi.UiEffect
import com.example.pion.family.tracker.demo.ui.core.mvi.UiIntent
import com.example.pion.family.tracker.demo.ui.core.mvi.UiState
import com.example.pion.family.tracker.demo.ui.permission.PermissionStep

/**
 * Luồng 3 bước riêng biệt (LLM.md §10, PRD §4.3 Flow 1): NOTIFICATIONS -> FINE_LOCATION ->
 * BACKGROUND_LOCATION. [step] là bước đang hiển thị; các cờ `xGranted` phản ánh kết quả ĐÃ BIẾT,
 * không suy ra ngược từ [step] — một bước có thể bị bỏ qua bằng "Để sau" mà không có kết quả.
 */
data class PermissionState(
    val step: PermissionStep = PermissionStep.NOTIFICATIONS,
    val notificationsGranted: Boolean = false,
    val fineLocationGranted: Boolean = false,
    val backgroundLocationGranted: Boolean = false,
) : UiState

sealed interface PermissionIntent : UiIntent {
    /** "Tiếp tục" ở trang giải thích của bước hiện tại — US-01. */
    data object Continue : PermissionIntent

    /** "Để sau" — bỏ qua bước hiện tại (US-01). */
    data object Skip : PermissionIntent

    data class PermissionResolved(val type: PermissionStep, val granted: Boolean) : PermissionIntent

    /** App quay lại foreground (thường là từ màn Settings hệ thống) — US-03. */
    data object ScreenResumed : PermissionIntent
}

sealed interface PermissionEffect : UiEffect {
    data object RequestNotifications : PermissionEffect
    data object RequestFineLocation : PermissionEffect
    data object OpenAppSettings : PermissionEffect
    data object GoToMap : PermissionEffect
}
