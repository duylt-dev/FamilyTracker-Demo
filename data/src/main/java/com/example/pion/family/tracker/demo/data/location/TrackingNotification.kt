package com.example.pion.family.tracker.demo.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pion.family.tracker.demo.data.R

/**
 * Kênh + nội dung thông báo thường trực của `LocationTrackingService` (phase-04 Implementation
 * Step 8). Phải nói rõ app đang theo dõi vị trí (PRD §7.3) — yêu cầu minh bạch, không trang trí.
 * `setOngoing(true)` + action "Dừng theo dõi" gửi [LocationTrackingService.ACTION_STOP] về chính
 * service.
 */
object TrackingNotification {
    const val CHANNEL_ID = "location_tracking"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tracking_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification {
        ensureChannel(context)

        // `:data` không thấy `MainActivity` (LLM.md §8.6) — lấy launch intent qua PackageManager,
        // không import :app.
        val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val stopIntent = Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.getString(R.string.tracking_notification_text))
            .setSmallIcon(R.drawable.ic_location_tracking)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.tracking_notification_stop_action), stopPendingIntent)
            .build()
    }
}
