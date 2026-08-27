package com.example.pion.family.tracker.demo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pion.family.tracker.demo.ui.feature.history.HistoryRoute as HistoryScreenRoute
import com.example.pion.family.tracker.demo.ui.feature.map.MapRoute as MapScreenRoute
import com.example.pion.family.tracker.demo.ui.feature.navigation.NavigationRoute as NavigationScreenRoute
import com.example.pion.family.tracker.demo.ui.feature.permission.PermissionRoute
import com.example.pion.family.tracker.demo.ui.feature.timeline.TimelineRoute as TimelineScreenRoute
import com.example.pion.family.tracker.demo.ui.feature.zone.ZoneEditorRoute as ZoneEditorScreenRoute
import com.example.pion.family.tracker.demo.ui.feature.zone.ZoneListRoute as ZoneListScreenRoute
import com.example.pion.family.tracker.demo.ui.permission.currentPermissionStatus

/**
 * `PermissionOnboardingRoute` là start destination khi thiếu quyền; `MapRoute` khi đủ (US-05,
 * phase-04 Requirements + Implementation Step 6: kiểm tra quyền THẬT lúc khởi động, không lưu cờ
 * — một cờ đã lưu sẽ nói dối khi người dùng thu hồi quyền qua Settings).
 *
 * `TimelineRoute` là màn thật từ phase-10 (trước đó là placeholder `Text(...)`, phase-04
 * Implementation Step 2). phase-06 thay `ZoneListRoute`/`ZoneEditorRoute`, phase-08 thay
 * `HistoryRoute` — cùng bẫy trùng tên route-type/composable đã ghi ở LLM.md §7, giải bằng import
 * alias `ZoneListScreenRoute`/`ZoneEditorScreenRoute`/`HistoryScreenRoute`/`TimelineScreenRoute`.
 *
 * `pendingRoute` — phase-07 Implementation Step 9 (US-22): bấm thông báo vào/rời zone mở app tới
 * Timeline. `ZoneNotifier` (`:data`) không được import `MainActivity` (LLM.md §8.6), nên nó chỉ
 * đính `putExtra("ftd_route", "timeline")`; `MainActivity` đọc extra đó và truyền chuỗi thô xuống
 * đây — `:ui` là nơi DUY NHẤT biết `TimelineRoute` là gì, `:app` không được biết route type
 * (LLM.md §3: `:app` không chứa logic). So khớp bằng string literal thay vì hằng số dùng chung,
 * vì hai module không được phép thấy nhau ngoài đúng một ngoại lệ đã ghi ở LLM.md §6.
 *
 * `pendingRouteNonce` — khoá `LaunchedEffect` theo NÓ, không theo `pendingRoute` (đã sửa sau khi
 * bắt lỗi thật trên `emulator-5554`, xem `MainActivity.kt` KDoc): hai lần bấm thông báo liên tiếp
 * đều mang `pendingRoute = "timeline"` GIỐNG HỆT nhau, nên khoá theo chuỗi khiến `LaunchedEffect`
 * chỉ chạy đúng một lần (lần đầu) — `MainActivity` tăng nonce ở mỗi `onCreate`/`onNewIntent` để
 * đảm bảo mỗi lần bấm đều mở lại Timeline, không chỉ lần đầu tiên.
 */
@Composable
fun FamilyTrackerNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    pendingRoute: String? = null,
    pendingRouteNonce: Int = 0,
) {
    val context = LocalContext.current
    val startDestination = remember {
        if (context.currentPermissionStatus().hasUsableLocation) MapRoute else PermissionOnboardingRoute
    }

    LaunchedEffect(pendingRouteNonce) {
        if (pendingRoute == "timeline") {
            navController.navigate(TimelineRoute)
        }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<PermissionOnboardingRoute> {
            PermissionRoute(
                onFinished = {
                    navController.navigate(MapRoute) {
                        popUpTo(PermissionOnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<MapRoute> {
            MapScreenRoute(
                onOpenZoneEditor = { lat, lng -> navController.navigate(ZoneEditorRoute(lat = lat, lng = lng)) },
                onOpenZoneList = { navController.navigate(ZoneListRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute()) },
                onOpenTimeline = { navController.navigate(TimelineRoute) },
                onOpenNavigation = { memberId -> navController.navigate(NavigationRoute(memberId = memberId)) },
            )
        }
        composable<NavigationRoute> {
            NavigationScreenRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable<ZoneListRoute> {
            ZoneListScreenRoute(
                onOpenEditor = { zoneId -> navController.navigate(ZoneEditorRoute(zoneId = zoneId)) },
                onOpenMap = { navController.navigate(MapRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute()) },
                onOpenTimeline = { navController.navigate(TimelineRoute) },
            )
        }
        composable<ZoneEditorRoute> {
            ZoneEditorScreenRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable<HistoryRoute> {
            HistoryScreenRoute(
                onOpenMap = { navController.navigate(MapRoute) },
                onOpenZoneList = { navController.navigate(ZoneListRoute) },
                onOpenTimeline = { navController.navigate(TimelineRoute) },
            )
        }
        composable<TimelineRoute> {
            TimelineScreenRoute(
                onOpenMap = { navController.navigate(MapRoute) },
                onOpenZoneList = { navController.navigate(ZoneListRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute()) },
                onOpenHistoryAt = { epochDay, lat, lng ->
                    navController.navigate(HistoryRoute(epochDay = epochDay, focusLat = lat, focusLng = lng))
                },
            )
        }
    }
}
