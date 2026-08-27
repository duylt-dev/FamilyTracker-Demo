package com.example.pion.family.tracker.demo.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.pion.family.tracker.demo.data.util.FtdLog
import androidx.core.app.NotificationCompat
import com.example.pion.family.tracker.demo.data.R
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VI_VN: Locale = Locale.forLanguageTag("vi-VN")

/**
 * Kênh + nội dung thông báo vào/rời zone (US-22/US-23) — khác `TrackingNotification` (thông báo
 * thường trực của foreground service, phase-04). Gọi bởi `ZoneEventRepositoryImpl.record()` SAU
 * khi ghi Room thành công (LLM.md §8.1, PRD §3.2 "sự kiện luôn được ghi kể cả khi thông báo bị
 * tắt quyền" — record() không phụ thuộc kết quả notify()).
 *
 * fix-zone-follows-members: tiêu đề mang TÊN THÀNH VIÊN ("Minh đã đến Trường học"). Mọi sự kiện
 * zone giờ thuộc về một thành viên được theo dõi, không bao giờ thuộc về self (LLM.md §8.1) — bản
 * cũ chỉ ghi tên zone vì chủ thể luôn ngầm hiểu là "tôi", giờ thì không còn ngầm hiểu được nữa.
 *
 * `:data` không thấy `MainActivity` (LLM.md §8.6, `:data -> :app` là phụ thuộc ngược Gradle từ
 * chối) — mở app qua `packageManager.getLaunchIntentForPackage`, không import `:app`. Đính
 * `putExtra(EXTRA_ROUTE, ROUTE_TIMELINE)` bằng LITERAL string (không phải hằng số dùng chung xuyên
 * module) vì `:app/MainActivity.kt` không được phép import bất cứ gì từ `:data` ngoại trừ đúng một
 * ngoại lệ đã ghi ở LLM.md §6 (`FamilyTrackerApp.kt` nạp Koin module) — hai literal phải khớp tay,
 * xem `MainActivity.kt`.
 */
object ZoneNotifier {

    const val EXTRA_ROUTE = "ftd_route"
    const val ROUTE_TIMELINE = "timeline"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", VI_VN)

    /**
     * Không bắn nếu công tắc tương ứng (`notifyOnEnter`/`notifyOnExit`, US-19) đang tắt — sự kiện
     * đã được ghi vào Room bởi caller TRƯỚC khi gọi hàm này, hai công tắc chỉ chặn thông báo.
     *
     * [memberName] `null` chỉ xảy ra nếu thành viên bị xoá khỏi `members` giữa lúc ghi sự kiện và
     * lúc bắn thông báo — không có đường nào trong app làm việc đó hôm nay, nhưng thông báo vẫn
     * phải đọc được nếu nó xảy ra, nên rơi về một danh từ chung thay vì để trống một nửa câu.
     */
    fun notify(context: Context, event: ZoneEvent, zone: Zone, memberName: String?) {
        val shouldNotify = when (event.type) {
            ZoneEventType.ENTER -> zone.notifyOnEnter
            ZoneEventType.EXIT -> zone.notifyOnExit
        }
        if (!shouldNotify) return

        NotificationChannels.ensureAll(context)

        val who = memberName ?: context.getString(R.string.zone_notification_unknown_member)
        val title = when (event.type) {
            ZoneEventType.ENTER -> context.getString(R.string.zone_notification_enter_title, who, zone.name)
            ZoneEventType.EXIT -> context.getString(R.string.zone_notification_exit_title, who, zone.name)
        }
        val time = event.occurredAt.atZone(ZoneId.systemDefault()).format(timeFormatter)

        // FLAG_ACTIVITY_SINGLE_TOP — bằng chứng thật (không suy đoán): `getLaunchIntentForPackage()`
        // KHÔNG tự có cờ này. Thiếu nó, tap thông báo lúc `MainActivity` đã sống sẵn trong task (app
        // ở background, chưa bị kill) chỉ đưa task đó lên trước — hệ thống không gọi lại `onCreate`
        // LẪN `onNewIntent`, nên extra "ftd_route" bị lờ đi và app đứng nguyên ở màn cũ (bắt được
        // bằng thao tác thật trên `emulator-5554`: tap thông báo 3 lần liên tiếp, cả 3 đều dừng ở
        // Map, chỉ `am start` với cùng extra mới mở đúng Timeline). Với cờ này, hệ thống gọi
        // `MainActivity.onNewIntent()` khi activity đã ở đỉnh task — xem `MainActivity.kt`.
        val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_ROUTE, ROUTE_TIMELINE)
        }?.let {
            PendingIntent.getActivity(
                context,
                event.id.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.ZONE_EVENTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(time)
            .setSmallIcon(R.drawable.ic_location_tracking)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(event.id.hashCode(), notification)
        FtdLog.d(TAG, "notification_posted zoneId=${zone.id} type=${event.type}")
    }

    private const val TAG = "FTD_EVENT"
}
