package com.example.pion.family.tracker.demo.ui.feature.history.component

import com.example.pion.family.tracker.demo.ui.core.logging.FtdLog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FTD_EVENT"
private const val SIMPLIFY_TOLERANCE_METERS = 10.0

/**
 * US-28/US-31 — polyline của MỘT chuyến đang chọn + marker Start (xanh)/End (đỏ). Điểm nhiễu
 * KHÔNG bị lọc lại ở đây (phase file Key Insight #8): [points] đã qua `LocationFilter` từ lúc GHI
 * (`LocationPointProcessor`, `:data`); lọc lần hai ở tầng đọc sẽ che mất một khuyết điểm thật ở
 * phase-04 nếu polyline vẫn nhảy lung tung — sửa ở đó, không lọc lại ở đây.
 *
 * `PolyUtil.simplify` (Douglas-Peucker, `com.google.maps.android:maps-compose-utils` kéo theo
 * `android-maps-utils-core` — xác nhận có mặt thật trong classpath, xem dev-phase-08-report.md)
 * giảm mẫu CHỈ để vẽ (phase file Key Insight #2): [points] KHÔNG bị giảm mẫu trước khi gọi hàm
 * này — thống kê (`RouteStats.of`) luôn tính trên danh sách gốc ở `:domain`.
 *
 * Chuyến 1 điểm (`points.size == 1`, hợp lệ theo `RouteStats` — chuyến đứng yên) vẫn vẽ marker
 * Start/End chồng lên cùng một vị trí, chỉ bỏ qua `Polyline` (cần ≥ 2 điểm).
 *
 * fix-phase-08 — `PolyUtil.simplify` chạy TRONG `remember`, tức là trong pha composition, ĐỒNG BỘ
 * trên UI thread, và luôn xảy ra TRƯỚC khi `HistoryMap`'s `LaunchedEffect` bắt đầu tính giờ (bằng
 * chứng: `reports/fix-phase-08-report.md` — log `simplify_start`/`simplify_end` xảy ra hoàn toàn
 * trước log `effect_start` của lần recomposition kế tiếp). Vì vậy `history_rendered.renderMs` cũ
 * KHÔNG bao giờ tính phần này. Log riêng `history_simplify` ở đây để mốc này còn lại được, cộng
 * với `history_rendered.frameMs` (đổi tên từ `renderMs`, xem `HistoryMap.kt`) mới ra tổng thật.
 *
 * fix2-phase-08 — số đo thật ở `reports/fix2-phase-08-report.md` (đã cô lập khỏi chi phí cold-start,
 * xem mục 3 báo cáo đó): 576ms trên UI thread ở 8.460 điểm (cùng tid với pid ứng dụng — chứng minh
 * trực tiếp, không suy luận), `dumpsys gfxinfo` cho thấy đúng 1 khung hình rơi vào bucket 650ms
 * (99th percentile 650ms) đúng một lần chạm tab "Lịch sử". Sau khi sửa: 99th percentile còn 105ms,
 * không còn khung nào ≥150ms. Chuyển `PolyUtil.simplify` ra `LaunchedEffect` + `withContext(Dispatchers.Default)` — vẫn
 * ở `:ui` (không đưa xuống `:domain`: `PolyUtil`/`LatLng` cần Android runtime thật, `:domain` là
 * module `kotlin.jvm` thuần, `import` sẽ lỗi biên dịch — xem `LLM.md` §2), chỉ đổi CHỖ nó chạy
 * (dispatcher nền) chứ không đổi TẦNG nó thuộc về. `simplified` khởi tạo rỗng mỗi khi
 * [rawLatLngs] đổi (chọn ngày khác/chọn chuyến khác) — `Polyline` không vẽ gì trong khoảnh khắc
 * chờ, marker Start/End vẫn hiện ngay (không phụ thuộc `simplified`) nên người dùng luôn thấy phản
 * hồi tức thì, bản đồ vẫn tương tác được (kéo/zoom) suốt lúc chờ — khác với đứng hình cũ (không
 * làm gì được, kể cả kéo bản đồ, trong hơn nửa giây). `LaunchedEffect(rawLatLngs)` tự huỷ-và-thay
 * job cũ khi khoá đổi, cùng luật "cancel and replace" `HistoryViewModel.onSelectDay` đã dùng — bấm
 * nhanh nhiều chuyến/ngày liên tiếp không để lại kết quả simplify của lựa chọn cũ.
 */
@Composable
@GoogleMapComposable
internal fun RoutePolyline(points: List<LocationPoint>, colorArgb: Int) {
    if (points.isEmpty()) return

    val density = LocalDensity.current
    val rawLatLngs = remember(points) { points.map { LatLng(it.latitude, it.longitude) } }
    var simplified by remember(rawLatLngs) { mutableStateOf<List<LatLng>>(emptyList()) }
    LaunchedEffect(rawLatLngs) {
        simplified = withContext(Dispatchers.Default) {
            val startNanos = System.nanoTime()
            val result = if (rawLatLngs.size > 2) PolyUtil.simplify(rawLatLngs, SIMPLIFY_TOLERANCE_METERS) else rawLatLngs
            val simplifyMs = (System.nanoTime() - startNanos) / 1_000_000
            FtdLog.d(TAG, "history_simplify pointCount=${rawLatLngs.size} simplifiedCount=${result.size} simplifyMs=$simplifyMs")
            result
        }
    }
    val widthPx = remember(density) { with(density) { Dimens.RoutePolylineWidth.toPx() } }

    if (simplified.size >= 2) {
        Polyline(points = simplified, color = Color(colorArgb), width = widthPx)
    }
    Marker(
        state = rememberUpdatedMarkerState(position = rawLatLngs.first()),
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
    )
    Marker(
        state = rememberUpdatedMarkerState(position = rawLatLngs.last()),
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
    )
}
