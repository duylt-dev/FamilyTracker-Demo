package com.example.pion.family.tracker.demo.ui.feature.permission

import com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel
import com.example.pion.family.tracker.demo.ui.permission.PermissionStep

/**
 * Không import `android.*` — ra lệnh bằng Effect, kết quả quyền vào bằng Intent
 * (phase-04 Implementation Step 4, MVI doc §4 "Quyền là việc của composable").
 */
class PermissionViewModel : MviViewModel<PermissionState, PermissionIntent, PermissionEffect>(PermissionState()) {

    override fun onIntent(intent: PermissionIntent) {
        when (intent) {
            PermissionIntent.Continue -> onContinue()
            PermissionIntent.Skip -> onSkip()
            is PermissionIntent.PermissionResolved -> onPermissionResolved(intent.type, intent.granted)
            PermissionIntent.ScreenResumed -> Unit // composable đã tự re-check và gửi PermissionResolved kèm theo
        }
    }

    private fun onContinue() {
        when (currentState.step) {
            PermissionStep.NOTIFICATIONS -> sendEffect(PermissionEffect.RequestNotifications)
            PermissionStep.FINE_LOCATION -> sendEffect(PermissionEffect.RequestFineLocation)
            PermissionStep.BACKGROUND_LOCATION -> sendEffect(PermissionEffect.OpenAppSettings)
        }
    }

    private fun onSkip() {
        when (currentState.step) {
            PermissionStep.NOTIFICATIONS -> advanceTo(PermissionStep.FINE_LOCATION)
            // Bỏ qua ở bước vị trí (bắt buộc hoặc tuỳ chọn) đều dẫn thẳng về Map — US-02.
            PermissionStep.FINE_LOCATION, PermissionStep.BACKGROUND_LOCATION -> sendEffect(PermissionEffect.GoToMap)
        }
    }

    private fun onPermissionResolved(type: PermissionStep, granted: Boolean) {
        when (type) {
            PermissionStep.NOTIFICATIONS -> {
                setState { copy(notificationsGranted = granted) }
                advanceTo(PermissionStep.FINE_LOCATION)
            }
            PermissionStep.FINE_LOCATION -> {
                setState { copy(fineLocationGranted = granted) }
                // Từ chối vị trí -> Map ở chế độ giảm chức năng (US-02); cấp -> bước 3.
                if (granted) advanceTo(PermissionStep.BACKGROUND_LOCATION) else sendEffect(PermissionEffect.GoToMap)
            }
            PermissionStep.BACKGROUND_LOCATION -> {
                setState { copy(backgroundLocationGranted = granted) }
                sendEffect(PermissionEffect.GoToMap)
            }
        }
    }

    private fun advanceTo(step: PermissionStep) = setState { copy(step = step) }
}
