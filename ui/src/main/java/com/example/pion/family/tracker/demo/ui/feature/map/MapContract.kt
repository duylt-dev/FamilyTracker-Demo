package com.example.pion.family.tracker.demo.ui.feature.map

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.MemberLocation
import com.example.pion.family.tracker.demo.domain.model.RouteSourceInfo
import com.example.pion.family.tracker.demo.domain.model.RouteSourceKind
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.ui.core.mvi.UiEffect
import com.example.pion.family.tracker.demo.ui.core.mvi.UiIntent
import com.example.pion.family.tracker.demo.ui.core.mvi.UiState

/**
 * Màn chính (F1, US-06→US-11). `zones`/`memberLocations` là dữ liệu thô từ `:domain`;
 * [selfLocation]/[otherMembers] là `val` tính toán, không lưu trùng (MVI doc §2 "Derive, don't
 * duplicate") — hai field tách rời có thể lệch nhau, một field suy ra thì không bao giờ lệch.
 * **Không giữ đối tượng đã chọn, chỉ giữ id** (`selectedMemberId`) — phase-05 Implementation Step 2.
 */
data class MapState(
    val zones: List<Zone> = emptyList(),
    val memberLocations: List<MemberLocation> = emptyList(),
    val isTracking: Boolean = false,
    val notificationsGranted: Boolean = true,
    val fineLocationGranted: Boolean = true,
    val hasCenteredOnce: Boolean = false,
    val selectedMemberId: String? = null,
    /** phase-01, D4 (`decisions.md` §C3) — điểm thật mới nhất từ
     * `TrackingRepository.observeLiveSelfLocation()`, CHƯA qua `LocationFilter`. `null` cho tới
     * fix đầu tiên. Input thô cho [selfLocation] — composable không đọc thẳng field này. */
    val liveSelfLocation: LocationPoint? = null,
    /** Smooth-road plan phase-05 — nguồn tuyến hiện tại của các thành viên mô phỏng, đã GỘP ở
     * `RouteSourceAggregator` (`:data/routing/`, phase-04): còn ai ở tầng PROVIDER/CACHE thì phát
     * attribution của (các) người đó, không ai còn thì `SYNTHETIC` với `attribution` rỗng. `null`
     * cho tới khi `SimulatedRouteRepository.observeSource()` phát lần đầu — màn vừa mở, chưa có gì
     * để ghi công (FR-4). [attributionLines]/[isFallbackRoute] đọc THẲNG field này — KHÔNG tự gộp
     * lại logic ở `:ui`, `RouteSourceAggregator` đã là nơi DUY NHẤT quyết định chuyện đó. */
    val routeSource: RouteSourceInfo? = null,
    /** phase-07 (US-47, D8) — `true` mặc định: giữa lúc khởi tạo ViewModel và lần phát đầu tiên
     * của `NetworkMonitor.observeHasInternet()` có một khoảng vài mili-giây; mặc định `false` sẽ
     * làm lớp phủ KHÔNG ĐÓNG ĐƯỢC nháy lên ở MỌI lần mở app, kể cả khi mạng bình thường. Khoảng
     * trống đó an toàn CHỈ VÌ `AndroidNetworkMonitor` đọc trạng thái hiện tại làm giá trị đầu tiên
     * TRƯỚC khi đăng ký callback — hai quyết định này đi cùng nhau, không tách rời được. */
    val hasInternet: Boolean = true,
) : UiState {
    /** US-06/US-43: marker vị trí của mình. Ưu tiên [liveSelfLocation] để marker đi theo cả những fix
     * mà `LocationFilter` từ chối (sai số vượt `MAX_ACCURACY_M` — đứng trong nhà); rơi về điểm Room
     * đã ghi khi cổng live chưa phát gì, không thì chấm xanh biến mất mỗi lần mở lại app cho tới fix
     * đầu tiên. `null` khi self chưa từng có điểm nào ở CẢ HAI nguồn — `FamilyTrackerMap` không
     * vẽ gì trong lúc đó. */
    val selfLocation: MemberLocation?
        get() {
            val self = memberLocations.firstOrNull { it.member.isSelf } ?: return null
            return liveSelfLocation?.let { self.copy(lastLocation = it) } ?: self
        }

    /** US-08: "thành viên gia đình KHÁC" — loại trừ self, self đã có marker riêng ở US-06. */
    val otherMembers: List<MemberLocation> get() = memberLocations.filterNot { it.member.isSelf }

    /** US-06 đọc rộng: ưu tiên canh camera vào vị trí của mình; nếu chưa từng bật theo dõi (chưa
     * có điểm nào cho self) thì canh tạm vào một thành viên bất kỳ đã có vị trí — không thì màn
     * hình mở lên là bản đồ thế giới trống trơn cho tới khi người dùng tự bật công tắc, một trải
     * nghiệm mở màn tệ hơn PRD §7.1 định hình. Chỉ ảnh hưởng lần canh CAMERA đầu tiên, không ảnh
     * hưởng marker nào được vẽ (marker self vẫn chỉ vẽ khi [selfLocation] có toạ độ thật). */
    val initialCameraTarget get() = selfLocation?.lastLocation ?: memberLocations.firstNotNullOfOrNull { it.lastLocation }

    /** US-04: thông báo bị tắt — sự kiện vẫn ghi vào Timeline, chỉ không có thông báo. */
    val showNotificationsBanner: Boolean get() = !notificationsGranted

    /** US-02: vị trí bị từ chối — bản đồ ở chế độ giảm chức năng. */
    val showLocationDegradedBanner: Boolean get() = !fineLocationGranted

    /** Routing plan phase-05 — nút "Chỉ đường" chỉ hiện khi đã chọn MỘT thành viên và người đó
     * KHÔNG PHẢI self (chỉ đường tới chính mình vô nghĩa). Dò trong [otherMembers] (đã loại self
     * sẵn) thay vì so `selectedMemberId != selfLocation?.member?.id`, để đúng cả trường hợp self
     * chưa từng có vị trí ([selfLocation] `null`). */
    val canNavigateToSelected: Boolean get() = selectedMemberId != null && otherMembers.any { it.member.id == selectedMemberId }

    /** Smooth-road plan phase-05, US-46 — dòng ghi công đã dựng sẵn, đọc thẳng từ [routeSource].
     * Rỗng ở [RouteSourceKind.SYNTHETIC] (không có dữ liệu OSM để ghi công, X5) và khi chưa có nguồn. */
    val attributionLines: List<String> get() = routeSource?.attribution.orEmpty()

    /** Smooth-road plan phase-05, US-46 — `val` tính từ `kind`, không phải field lưu riêng (MVI doc
     * §2 "Derive, don't duplicate"): tách rời sẽ có ngày nói hai chuyện khác nhau, và ngày đó là
     * ngày app ghi sai nguồn. */
    val isFallbackRoute: Boolean get() = routeSource?.kind == RouteSourceKind.SYNTHETIC

    /** phase-07 (US-47, D8) — `val` tính từ [hasInternet], cùng mẫu `showNotificationsBanner`/
     * `showLocationDegradedBanner` (MVI doc §2 "Derive, don't duplicate"). Ranh giới sống còn:
     * đây là NGUỒN DUY NHẤT quyết định lớp phủ — mã lỗi HTTP của nhà cung cấp routing KHÔNG được
     * đọc vào đây (Key Insight #1, `InternetBlockerBoundaryTest`). */
    val showNoInternetOverlay: Boolean get() = !hasInternet
}

sealed interface MapIntent : UiIntent {
    data class PermissionStateChanged(val notificationsGranted: Boolean, val fineLocationGranted: Boolean) : MapIntent
    data object ToggleTracking : MapIntent

    /** US-10 — nhấn giữ ≥500ms (ngưỡng hệ thống, Key Insight #5) tại toạ độ đã chọn. */
    data class MapLongPressed(val lat: Double, val lng: Double) : MapIntent

    /** US-08 — bấm marker thành viên. Info window (tên + giờ) là hành vi mặc định của Marker SDK;
     * intent này chỉ ghi lại lựa chọn hiện tại bằng id (MVI doc §2). */
    data class MemberTapped(val memberId: String) : MapIntent

    /** Camera đã canh xong lần đầu (US-06) — composable báo lên để không canh lại lần sau
     * (Risk Assessment: "Camera kéo về giữa lúc người dùng đang xem chỗ khác"). */
    data object CameraCentered : MapIntent

    data object ZoneListRequested : MapIntent
    data object HistoryRequested : MapIntent
    data object TimelineRequested : MapIntent

    /** Routing plan phase-05 — bấm nút "Chỉ đường" trên marker thành viên đã chọn. */
    data class NavigateToMemberRequested(val memberId: String) : MapIntent
}

sealed interface MapEffect : UiEffect {
    /** US-10 — tâm zone mới = điểm vừa nhấn giữ. */
    data class OpenZoneEditor(val lat: Double, val lng: Double) : MapEffect
    data object OpenZoneList : MapEffect
    data object OpenHistory : MapEffect
    data object OpenTimeline : MapEffect

    /** MVI doc §2 "A failure can legitimately be both": bản đồ đã có marker/zone, không còn chỗ
     * trống để vẽ banner lỗi inline, nên lỗi bật/tắt theo dõi được nói ra thay vì vẽ ra. */
    data class ShowError(val error: AppError) : MapEffect

    /** Routing plan phase-05 — mở `NavigationRoute(memberId)`. */
    data class OpenNavigation(val memberId: String) : MapEffect
}
