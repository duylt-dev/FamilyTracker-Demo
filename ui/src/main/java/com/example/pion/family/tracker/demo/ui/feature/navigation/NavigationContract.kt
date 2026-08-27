package com.example.pion.family.tracker.demo.ui.feature.navigation

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.ui.core.mvi.UiEffect
import com.example.pion.family.tracker.demo.ui.core.mvi.UiIntent
import com.example.pion.family.tracker.demo.ui.core.mvi.UiState

/**
 * Routing plan phase-05, US "Chỉ đường tới thành viên". Hai chỗ tách khỏi Architecture snippet gốc
 * của phase file. Snippet đó viết trước khi phase-04 tồn tại; cả hai chỗ lệch là hệ quả trực tiếp
 * của kiểu dữ liệu phase-04 đã chốt, không phải lựa chọn thẩm mỹ:
 *
 * 1. [distanceMeters]/[isDistanceEstimated] — phase file không liệt kê hai field này, nhưng
 *    `NavigationUpdate` thật (phase-04, đã commit) luôn mang sẵn khoảng cách tính SẴN ở `:domain`
 *    (`GeoDistance` là `internal`, `:ui` không gọi được — LLM.md §8.2). Không lưu lại hai field này
 *    thì màn hình không có cách hợp lệ nào để vẽ khoảng cách ở nhánh giảm cấp (đường thẳng).
 * 2. [hasCenteredOnce] + `NavigationIntent.CameraCentered` — Implementation Step 6 của phase file
 *    đòi "cùng luật `hasCenteredOnce` mà `MapState` đang dùng", nhưng field đó không có trong
 *    snippet. Thêm vào đây cho đúng luật đã nêu bằng lời.
 *
 * [isFallbackStraightLine] là field LƯU, không suy ra — nó là *lý do* (đã từng gọi provider mà
 * không ra tuyến), không phải *hình dạng* ([directions] `== null`). `NavigationUpdate.lastError` chỉ
 * khác null ĐÚNG ở lần emit mà provider được gọi; các lần emit tiếp theo rơi trong cửa sổ debounce
 * 60s có `lastError == null` dù vẫn chưa có tuyến — suy ra thẳng từ `lastError != null` sẽ làm cờ
 * này bật rồi tắt ngay ở emit kế tiếp, dải credit nhấp nháy giữa "ước tính" và ẩn. Khoá bằng
 * `NavigationViewModelTest` (mục "sticky fallback flag").
 *
 * **Feedback #3 — ba nguồn toạ độ, không phải một.** [storedSelfLocation] (Room, đã qua
 * `LocationFilter`) và [liveSelfLocation] (GPS thật, CHƯA lọc) là hai field riêng vì chúng đến từ
 * hai flow độc lập; [selfLocation] chọn giữa chúng theo ĐÚNG luật `MapState.selfLocation`. Trước
 * đây màn này chỉ đọc Room nên marker "tôi" đứng ở điểm seed ngẫu nhiên của `DemoDataSeeder` trong
 * khi tuyến lại xuất phát từ vị trí GPS thật (`ObserveNavigationUseCase` đọc `LocationSource`) —
 * hai chỗ trên cùng một màn hình nói hai vị trí khác nhau, lệch tới hơn 1 km trong bản demo.
 */
data class NavigationState(
    val targetMember: Member? = null,
    /** Điểm self ĐÃ GHI vào Room (`ObserveMembersWithLastLocationUseCase`) — dự phòng cho
     * [liveSelfLocation] khi cổng live chưa phát fix nào (app vừa mở, theo dõi chưa bật). */
    val storedSelfLocation: LocationPoint? = null,
    /** Điểm self THẬT chưa lọc (`TrackingRepository.observeLiveSelfLocation()`) — cùng cổng mà
     * `MapState.liveSelfLocation` đọc, nên hai màn không thể nói hai vị trí khác nhau nữa. */
    val liveSelfLocation: LocationPoint? = null,
    val targetLocation: LocationPoint? = null,
    val directions: Directions? = null,
    val distanceMeters: Double? = null,
    val isDistanceEstimated: Boolean = false,
    val isFallbackStraightLine: Boolean = false,
    val hasArrived: Boolean = false,
    val isTracking: Boolean = false,
    val hasCenteredOnce: Boolean = false,
    val error: AppError? = null,
) : UiState {

    /** Ưu tiên GPS thật, rơi về điểm Room khi cổng live chưa phát — luật SAO CHÉP nguyên văn từ
     * `MapState.selfLocation`, và đó là cả điểm của nó (feedback #3). */
    val selfLocation: LocationPoint? get() = liveSelfLocation ?: storedSelfLocation

    val selfPoint: GeoPoint? get() = selfLocation?.toGeoPoint()
    val targetPoint: GeoPoint? get() = targetLocation?.toGeoPoint()

    /**
     * Tuyến THẬT từ provider — rỗng khi chưa/không lấy được. Giữ riêng khỏi các đoạn nối mà
     * `NavigationMap` vẽ thêm (xem [routeStart]) vì hai thứ được VẼ KHÁC NHAU: đây là hình học OSM
     * có thật (nét liền, được ghi công qua [attributionLines]); đoạn nối là đường chim bay do app
     * tự kẻ, phải nét đứt để không nhận vơ là dữ liệu OSM
     * (`docs/routing-and-map-attribution.md` §3 mục 1 — cùng lý do `RoutingAttribution` phải biết
     * [isFallbackStraightLine]).
     */
    val routePolyline: List<GeoPoint> get() = directions?.points.orEmpty()

    /**
     * **Feedback #4 — hai mỏ neo để tuyến luôn dính vào hai marker đang sống.**
     *
     * BA đã chốt tính năng KHÔNG phải realtime navigation, nên tuyến không được gọi lại provider
     * mỗi lần Minh/Lan nhúc nhích (`RerouteEvaluator` giữ nguyên debounce 60s + ngưỡng 200m ở
     * `TrackingConstants`; quota GraphHopper free tier là 500 credit/NGÀY — `LLM.md` §13 Open #9).
     * Hệ quả nhìn thấy được của quyết định đó: tuyến đứng im trong khi marker đích đi tiếp, và
     * trông như tuyến vẽ SAI chứ không như "tuyến chưa cần tính lại".
     *
     * `NavigationMap` khép khoảng hở đó với chi phí 0 credit bằng hai đoạn nối nét đứt
     * ([NavigationConnector]): self→[routeStart] và [routeEnd]→đích. Hai mỏ neo nằm ở đây (state,
     * test JVM với tới được); đầu kia của mỗi đoạn là toạ độ marker ĐANG HIỂN THỊ ở khung hình này
     * (`rememberAnimatedMarkerPositions`), nên nó chỉ dựng được bên trong composable — dùng mẫu
     * thô ở đây thay vào sẽ làm đầu đoạn nối giật một nhịp mỗi 2.5s trong khi marker trượt mượt,
     * tức tái tạo đúng cảm giác giật mà feedback #2 sinh ra để bỏ đi.
     */
    val routeStart: GeoPoint? get() = routePolyline.firstOrNull()

    /** Xem [routeStart]. */
    val routeEnd: GeoPoint? get() = routePolyline.lastOrNull()

    /**
     * Nhánh GIẢM CẤP: chưa có tuyến thật nào thì cả màn hình chỉ còn MỘT đường chim bay self→đích,
     * cũng vẽ nét đứt. Suy từ [directions], không phải từ [isFallbackStraightLine] — cái sau là
     * *lý do* (đã gọi provider mà trượt) và cố ý DÍNH qua các lần emit sau, nên nó vẫn `true` ở
     * đúng lúc tuyến thật đã về và màn hình không còn đường chim bay nào để vẽ.
     */
    val isStraightLineOnly: Boolean get() = directions == null

    /** Rỗng ở nhánh fallback — Key Insight #3: không có dữ liệu OSM nào trên màn hình lúc đó, ghi
     * credit OSM khi đó là ghi sai nguồn. `RoutingAttribution` đọc field này, không tự chế chuỗi. */
    val attributionLines: List<String> get() = directions?.attribution.orEmpty()
}

private fun LocationPoint.toGeoPoint() = GeoPoint(latitude, longitude)

sealed interface NavigationIntent : UiIntent {
    /** Ép `ObserveNavigationUseCase` khởi động lại phiên quan sát (RerouteState mới) — lần gọi ĐẦU
     * TIÊN của một phiên luôn bỏ qua debounce 60s (RerouteEvaluator bước 3), nên đây là cách hợp lệ
     * duy nhất để thử lại ngay thay vì chờ hết debounce. */
    data object Retry : NavigationIntent
    data object StopNavigation : NavigationIntent
    data object EnableTrackingRequested : NavigationIntent

    /** Camera đã canh xong lần đầu — cùng vai trò `MapIntent.CameraCentered` (phase-05 map). */
    data object CameraCentered : NavigationIntent
}

sealed interface NavigationEffect : UiEffect {
    data object NavigateBack : NavigationEffect
    data class ShowError(val error: AppError) : NavigationEffect

    /** Xác nhận một lần rằng theo dõi vừa được BẬT theo đúng yêu cầu của người dùng (nút bấm), không
     * phải để báo trạng thái liên tục — `state.isTracking` (từ `TrackingRepository.isTracking()`)
     * mới là nguồn sự thật cho việc ẩn/hiện banner. */
    data object StartTracking : NavigationEffect
}
