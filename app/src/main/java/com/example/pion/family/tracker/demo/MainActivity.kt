package com.example.pion.family.tracker.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pion.family.tracker.demo.ui.designsystem.theme.FamilyTrackerDemoTheme
import com.example.pion.family.tracker.demo.ui.navigation.FamilyTrackerNavHost

/**
 * Thin Android host — see LLM.md §2, §3. No screen composables here besides the NavHost call.
 *
 * phase-07 (US-22): bấm thông báo vào/rời zone mở app tới Timeline. `ZoneNotifier` (`:data`)
 * đính extra `"ftd_route" = "timeline"` — đọc bằng LITERAL string, KHÔNG import bất cứ gì từ
 * `:data` (LLM.md §6: `:app -> :data` chỉ tồn tại đúng một ngoại lệ, ở `FamilyTrackerApp.kt`, để
 * nạp Koin module — `MainActivity.kt` không nằm trong ngoại lệ đó).
 *
 * **`onNewIntent` bắt buộc, không chỉ `onCreate` — bằng chứng thật, không suy đoán.** Khi app đã
 * sống trong task (background, chưa bị kill), tap thông báo KHÔNG gọi lại `onCreate`: hệ thống chỉ
 * đưa task hiện có lên trước. Thiếu `onNewIntent` + `FLAG_ACTIVITY_SINGLE_TOP` (đặt ở
 * `ZoneNotifier`, xem KDoc bên đó), 3 lần tap thông báo liên tiếp trên `emulator-5554` đều dừng ở
 * màn cũ (Map), không mở Timeline — trong khi `am start` với CÙNG extra thì mở đúng.
 *
 * `pendingRouteNonce` tăng mỗi lần `onCreate`/`onNewIntent` chạy — `pendingRoute` một mình không
 * đủ: hai thông báo zone liên tiếp cùng mang `"timeline"` (giá trị GIỐNG HỆT lần trước) không làm
 * `mutableStateOf` đổi giá trị, nên `FamilyTrackerNavHost`'s `LaunchedEffect(pendingRoute)` sẽ
 * không chạy lại lần hai — US-22 chỉ mở Timeline đúng ở LẦN ĐẦU bấm thông báo, im lặng các lần sau.
 * Nonce ép `LaunchedEffect` khoá theo NÓ (một `Int` luôn khác lần trước), không theo chuỗi.
 */
class MainActivity : ComponentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)
    private var pendingRouteNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        pendingRouteNonce++
        setContent {
            FamilyTrackerDemoTheme {
                FamilyTrackerNavHost(pendingRoute = pendingRoute, pendingRouteNonce = pendingRouteNonce)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
        pendingRouteNonce++
    }

    private companion object {
        const val EXTRA_ROUTE = "ftd_route"
    }
}
