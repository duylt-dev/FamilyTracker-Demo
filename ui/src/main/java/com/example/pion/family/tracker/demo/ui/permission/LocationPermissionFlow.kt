package com.example.pion.family.tracker.demo.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.pion.family.tracker.demo.ui.core.logging.FtdLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "FTD_EVENT"

/**
 * Quyền là việc của composable, không của ViewModel (MVI doc §4, phase-04 Key Insight #7):
 * `rememberLauncherForActivityResult` sống ở đây; kết quả báo lên ViewModel bằng Intent
 * (`onPermissionResolved`), không bằng method public. Cũng log `FTD_EVENT permission_result`
 * (PRD §10) ở đây — `PermissionViewModel` không được import `android.util.Log` (MVI doc §9).
 */
@Stable
class LocationPermissionFlowState(
    val requestNotifications: () -> Unit,
    val requestFineLocation: () -> Unit,
    val openAppSettings: () -> Unit,
)

/**
 * @param currentStep bước đang hiển thị — đọc lại quyền background CHỈ khi đúng bước này (xem
 *   ghi chú "BẪY" bên dưới); không dùng lambda cung cấp giá trị vì `PermissionRoute` đã có sẵn
 *   `State<PermissionState>` từ `collectAsStateWithLifecycle()`, đọc trực tiếp là đủ.
 */
@Composable
fun rememberLocationPermissionFlow(
    currentStep: PermissionStep,
    onPermissionResolved: (PermissionStep, Boolean) -> Unit,
    onScreenResumed: () -> Unit,
): LocationPermissionFlowState {
    val context = LocalContext.current
    val latestStep by rememberUpdatedState(currentStep)
    val latestOnPermissionResolved by rememberUpdatedState(onPermissionResolved)
    val latestOnScreenResumed by rememberUpdatedState(onScreenResumed)

    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        FtdLog.d(TAG, "permission_result type=${PermissionStep.NOTIFICATIONS} granted=$granted")
        onPermissionResolved(PermissionStep.NOTIFICATIONS, granted)
    }
    val fineLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        FtdLog.d(TAG, "permission_result type=${PermissionStep.FINE_LOCATION} granted=$granted")
        onPermissionResolved(PermissionStep.FINE_LOCATION, granted)
    }

    // US-03: từ Android 11+ không có dialog cho background location — người dùng tự vào Settings,
    // không có callback báo đã chọn gì. Kiểm tra lại quyền khi app quay về foreground.
    //
    // HAI BẪY đã bắt được bằng chạy thật trên emulator (không đoán, không có trong tài liệu):
    // 1. `Lifecycle.addObserver()` phát lại NGAY LẬP TỨC các sự kiện "bắt kịp"
    //    (ON_CREATE/ON_START/ON_RESUME) nếu lifecycle đã ở RESUMED tại thời điểm đăng ký — đúng
    //    trường hợp này, vì composable chỉ mount SAU khi Activity đã resume. Logcat thật:
    //    `permission_result type=BACKGROUND_LOCATION granted=false` xuất hiện trong vòng 100ms
    //    sau launch, không có tương tác nào — nhảy cóc thẳng `GoToMap` trước khi người dùng làm
    //    gì. Sửa: bỏ qua đúng MỘT lần ON_RESUME "bắt kịp" đầu tiên (`isFirstResume`).
    // 2. Dialog hệ thống của bước 1 (POST_NOTIFICATIONS) và bước 2 (FINE_LOCATION) cũng che App ra
    //    rồi trả lại foreground — MỖI dialog đóng lại cũng bắn một ON_RESUME thật (không phải
    //    "bắt kịp"). Nếu không gác theo [currentStep], các ON_RESUME này bị hiểu nhầm thành "quay
    //    lại từ Settings" và bắn `PermissionResolved(BACKGROUND_LOCATION, false)` khi người dùng
    //    còn đang ở bước 1/2 — cùng hậu quả nhảy cóc như bẫy #1. Sửa: chỉ đọc lại quyền background
    //    khi [currentStep] THẬT SỰ là [PermissionStep.BACKGROUND_LOCATION].
    var isFirstResume by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                when {
                    isFirstResume -> isFirstResume = false
                    latestStep == PermissionStep.BACKGROUND_LOCATION -> {
                        val granted = context.currentPermissionStatus().backgroundLocationGranted
                        FtdLog.d(TAG, "permission_result type=${PermissionStep.BACKGROUND_LOCATION} granted=$granted")
                        latestOnPermissionResolved(PermissionStep.BACKGROUND_LOCATION, granted)
                        latestOnScreenResumed()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(context) {
        LocationPermissionFlowState(
            requestNotifications = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            requestFineLocation = { fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            openAppSettings = { context.openAppSettings() },
        )
    }
}

/**
 * Bước 3 luôn mở màn Settings của app, kể cả trên API 28–29 (nơi một dialog thường về lý thuyết
 * vẫn xin được — researcher-01 §2.1). Chọn một đường duy nhất cho mọi API level: đơn giản hơn,
 * đúng cho phạm vi demo (thiết bị test ở ENV-BRIEFING.md đều API 30+), và khớp đúng 4 Effect
 * phase-04 khai (không có Effect thứ 5 riêng cho dialog thường).
 */
private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}
