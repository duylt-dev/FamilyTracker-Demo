package com.example.pion.family.tracker.demo.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Route type-safe (`navigation-compose` + `kotlinx.serialization`) — PRD §4.1, §9, LLM.md §7.
 * [PermissionOnboardingRoute] hoặc [MapRoute] là start destination tuỳ vào quyền hiện có
 * (US-05, phase-04 Requirements) — quyết định ở `FamilyTrackerNavHost`, không lưu cờ (phase-04
 * Implementation Step 6).
 */
@Serializable
data object PermissionOnboardingRoute

@Serializable
data object MapRoute

@Serializable
data object ZoneListRoute

/** US-10: nhấn giữ bản đồ mở editor với tâm là điểm vừa chọn -> route phải chở toạ độ.
 *
 * `ARG_*` — phase-06: `ZoneEditorViewModel` đọc các tham số này bằng
 * `savedStateHandle.get<T>(KEY)` (khớp đúng tên field, `navigation-compose` populate
 * `SavedStateHandle` của `NavBackStackEntry` bằng tên property), KHÔNG dùng
 * `savedStateHandle.toRoute<ZoneEditorRoute>()` — đã đo thật: `toRoute()` chạm vào
 * `android.os.Bundle`/`SavedState` typed-getter chưa mock trên JVM unit test (không Robolectric
 * trong dự án, LLM.md §11) và ném `RuntimeException` ngay khi gọi, trong khi `get<T>(key)` đơn
 * giản chạy được thẳng trên JVM — xác nhận bằng test thăm dò trước khi viết `ZoneEditorViewModel`. */
@Serializable
data class ZoneEditorRoute(
    val zoneId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    companion object {
        const val ARG_ZONE_ID = "zoneId"
        const val ARG_LAT = "lat"
        const val ARG_LNG = "lng"
    }
}

/** US-35: bấm một sự kiện ở Timeline mở History và canh camera vào đúng vị trí đó. */
@Serializable
data class HistoryRoute(
    val epochDay: Long? = null,
    val focusLat: Double? = null,
    val focusLng: Double? = null,
)

@Serializable
data object TimelineRoute

/**
 * Routing plan phase-05 — bấm "Chỉ đường" trên marker một thành viên mở màn dẫn đường tới người đó.
 * `ARG_MEMBER_ID` cùng cơ chế `get<T>(KEY)` đã lập ở `ZoneEditorRoute`/`HistoryRoute` (KHÔNG
 * `toRoute<T>()`, xem KDoc `ZoneEditorRoute` — `toRoute()` ném trên JVM unit test).
 */
@Serializable
data class NavigationRoute(val memberId: String) {
    companion object {
        const val ARG_MEMBER_ID = "memberId"
    }
}
