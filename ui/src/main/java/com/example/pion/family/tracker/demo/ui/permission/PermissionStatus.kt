package com.example.pion.family.tracker.demo.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Ba bước riêng biệt, đúng thứ tự PRD §4.3 Flow 1 — LLM.md §10, phase-04 Key Insight #1. */
enum class PermissionStep { NOTIFICATIONS, FINE_LOCATION, BACKGROUND_LOCATION }

/** Trạng thái thật của cả 3 quyền, đọc trực tiếp từ hệ thống — không suy luận, không lưu cờ. */
data class PermissionStatus(
    val notificationsGranted: Boolean,
    val fineLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
) {
    /** "Đủ quyền" để dùng app ở chế độ đầy đủ — vị trí là bắt buộc, background/thông báo là tuỳ chọn (US-05). */
    val hasUsableLocation: Boolean get() = fineLocationGranted
}

/**
 * Đọc trạng thái 3 quyền thật từ hệ thống — LLM.md §10:
 * - `POST_NOTIFICATIONS` chỉ tồn tại từ API 33 (researcher-01 §2.1); dưới đó coi như "đã cấp" vì
 *   không có gì để xin và thông báo hoạt động bình thường.
 * - `ACCESS_BACKGROUND_LOCATION` trên API 28–29 không tách riêng — cấp `ACCESS_FINE_LOCATION` là
 *   đủ (researcher-01 §2.4 `hasBackgroundLocationPermission`).
 */
fun Context.currentPermissionStatus(): PermissionStatus {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }
    val fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        fineLocation
    }
    return PermissionStatus(notifications, fineLocation, backgroundLocation)
}
