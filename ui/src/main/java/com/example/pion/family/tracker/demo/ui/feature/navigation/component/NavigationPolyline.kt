package com.example.pion.family.tracker.demo.ui.feature.navigation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.designsystem.theme.NavigationRouteColor
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Polyline

/**
 * Routing plan phase-05, Key Insight #6 — `:ui` nhận `List<GeoPoint>`, map sang `LatLng` NGAY TẠI
 * composable này, không đưa `LatLng` xuống ViewModel. `PolyUtil.simplify` KHÔNG cần ở đây
 * (Implementation Step 4): tuyến từ provider (GraphHopper/Valhalla) đã tối giản sẵn, khác polyline
 * lịch sử với hàng nghìn điểm GPS thô ở `RoutePolyline.kt`.
 *
 * Nét LIỀN — dành riêng cho hình học có thật từ provider. Đường app tự kẻ đi qua
 * [NavigationConnector], nét đứt.
 */
@Composable
@GoogleMapComposable
internal fun NavigationPolyline(points: List<GeoPoint>) {
    if (points.size < 2) return

    val density = LocalDensity.current
    val latLngs = remember(points) { points.map { LatLng(it.latitude, it.longitude) } }
    val widthPx = remember(density) { with(density) { Dimens.NavigationPolylineWidth.toPx() } }

    Polyline(points = latLngs, color = NavigationRouteColor, width = widthPx)
}

/**
 * **feedback #4** — đoạn chim bay nối một đầu tuyến thật với marker đang sống ở đầu đó, hoặc cả
 * tuyến ở nhánh giảm cấp (chưa lấy được tuyến nào).
 *
 * **Nét đứt là điều kiện, không phải trang trí.** BA đã chốt tính năng không phải realtime
 * navigation nên tuyến thật chỉ được tính lại theo `RerouteEvaluator` (debounce 60s, ngưỡng 200m);
 * giữa hai lần đó, đoạn nối là thứ giữ cho tuyến luôn chạm được hai marker. Nó KHÔNG bám đường và
 * KHÔNG đến từ OSM, nên vẽ liền cùng màu cùng bề dày với tuyến thật là trình bày dữ liệu tự chế
 * như dữ liệu OSM — đúng thứ `docs/routing-and-map-attribution.md` §3 mục 1 cấm, và cũng là lý do
 * `RoutingAttribution` phải biết `isFallbackStraightLine`.
 *
 * Nhận [LatLng] chứ không [GeoPoint] như [NavigationPolyline] ở trên: người gọi truyền vào toạ độ
 * ĐÃ NỘI SUY của marker (`rememberAnimatedMarkerPositions`), không phải mẫu thô — đoạn nối phải
 * dính vào chỗ marker ĐANG hiển thị ở khung hình này, không thì nó giật một nhịp mỗi 2.5s trong khi
 * marker trượt mượt, tức là tái tạo đúng cảm giác giật mà feedback #2 sinh ra để bỏ đi.
 */
@Composable
@GoogleMapComposable
internal fun NavigationConnector(points: List<LatLng>) {
    if (points.size < 2) return

    val density = LocalDensity.current
    val widthPx = remember(density) { with(density) { Dimens.NavigationConnectorWidth.toPx() } }
    val pattern = remember(density) {
        with(density) {
            listOf(Dash(Dimens.NavigationConnectorDash.toPx()), Gap(Dimens.NavigationConnectorGap.toPx()))
        }
    }

    Polyline(points = points, color = NavigationRouteColor, width = widthPx, pattern = pattern)
}
