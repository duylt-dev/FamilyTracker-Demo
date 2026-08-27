package com.example.pion.family.tracker.demo.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.pion.family.tracker.demo.data.R
import com.example.pion.family.tracker.demo.data.location.TrackingNotification

/**
 * phase-07 Implementation Step 4 — hai kênh của app: `zone_events` (DEFAULT, có âm thanh — vào/rời
 * zone, mới ở phase này) và `location_tracking` (LOW — thông báo thường trực FGS, phase-04). Gọi
 * từ `FamilyTrackerApp.onCreate` để cả hai kênh tồn tại TRƯỚC khi có thông báo đầu tiên, không đợi
 * lazy-create như trước.
 *
 * `location_tracking` đã tự tạo kênh của nó bên trong [TrackingNotification.ensureChannel] từ
 * phase-04 (gọi mỗi lần `TrackingNotification.build()` chạy, khi service khởi động) — gọi lại ở
 * đây là NO-OP an toàn (`createNotificationChannel` với cùng id không tạo bản sao, theo tài liệu
 * chính thức Android), không phải logic trùng lặp thứ hai.
 */
object NotificationChannels {
    const val ZONE_EVENTS_CHANNEL_ID = "zone_events"

    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        TrackingNotification.ensureChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val zoneEventsChannel = NotificationChannel(
            ZONE_EVENTS_CHANNEL_ID,
            context.getString(R.string.zone_events_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(zoneEventsChannel)
    }
}
