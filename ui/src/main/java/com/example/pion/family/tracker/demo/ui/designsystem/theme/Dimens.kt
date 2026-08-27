package com.example.pion.family.tracker.demo.ui.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every spacing/size/alpha constant a screen may need, per PRD §5.3 and §7.5 ("mọi màu/khoảng
 * cách ở designsystem/theme/"). `feature/map/` is the first screen dense enough to need it —
 * `Dimens.kt` was listed in LLM.md §3's expected `designsystem/theme/` layout since phase-01 but
 * never created until this phase actually needed it.
 */
object Dimens {
    // PRD §5.3 — thang 4dp.
    val SpaceXs: Dp = 4.dp
    val SpaceSm: Dp = 8.dp
    val SpaceMd: Dp = 16.dp
    val SpaceLg: Dp = 24.dp
    val SpaceXl: Dp = 32.dp

    /** PRD §5.3 — lề màn hình. */
    val ScreenPadding: Dp = SpaceMd

    /** PRD §5.3 — vùng chạm tối thiểu. */
    val MinTouchTarget: Dp = 48.dp

    /** PRD §5.2 — nền zone bán trong suốt. */
    const val ZONE_FILL_ALPHA: Float = 0.2f

    /** phase-07, US-47/D8 — scrim của lớp phủ chặn màn Bản đồ khi mất internet. Đủ tối để rõ
     * ràng đang bị chặn, đủ trong để vẫn thấy khung bản đồ phía sau. */
    const val OVERLAY_SCRIM_ALPHA: Float = 0.6f

    /** `Circle.strokeWidth` là pixel màn hình, không phải mét (researcher-02 §2.2, §9) — viền
     * mảnh dần khi zoom ra, chấp nhận theo Key Insight #2 của phase-05. */
    const val ZONE_STROKE_WIDTH_PX: Float = 2f

    val MemberDotSize: Dp = 20.dp
    val MemberDotBorderWidth: Dp = 2.dp
    val SelfDotSize: Dp = 24.dp
    val SelfDotBorderWidth: Dp = 3.dp

    /** PRD §2.6 US-28 — bề dày polyline lịch sử. `Polyline.width` cũng là pixel màn hình (như
     * `ZONE_STROKE_WIDTH_PX`), chuyển từ Dp bằng `LocalDensity` tại nơi dùng (phase-08). */
    val RoutePolylineWidth: Dp = 12.dp

    /** Routing plan phase-05 — cùng bề dày `RoutePolylineWidth`, hằng số riêng vì hai polyline
     * khác vòng đời (tuyến dẫn đường sống ngắn theo phiên `NavigationScreen`, khác chuyến lịch sử đã
     * lưu) và có thể cần chỉnh độc lập sau này. */
    val NavigationPolylineWidth: Dp = 12.dp

    /**
     * feedback #4 — đoạn nối chim bay giữa hai đầu tuyến thật và hai marker đang sống
     * (`NavigationState.startConnector`/`endConnector`). MẢNH HƠN và NÉT ĐỨT so với
     * [NavigationPolylineWidth]: đây là đường app tự kẻ, không phải hình học OSM, và
     * `docs/routing-and-map-attribution.md` §3 mục 1 cấm trình bày hai thứ đó như nhau.
     */
    val NavigationConnectorWidth: Dp = 6.dp
    val NavigationConnectorDash: Dp = 10.dp
    val NavigationConnectorGap: Dp = 8.dp

    /**
     * feedback #4 — marker ĐÍCH của màn Dẫn đường. Lớn hơn [MemberDotSize] để phân biệt ngay với
     * chính người đó khi họ xuất hiện dưới dạng chấm thường ở màn Bản đồ; [DestinationPinTailHeight]
     * là phần đuôi nhọn chỉ xuống đúng toạ độ, nên `anchor` của marker phải là đáy (0.5f, 1f).
     */
    val DestinationPinSize: Dp = 28.dp
    val DestinationPinBorderWidth: Dp = 3.dp
    val DestinationPinTailHeight: Dp = 10.dp

    /** phase-03 — mũi chỉ hướng của `MemberDot` (FR-3, US-40 AC), vẽ TRONG cùng bound với
     * [MemberDotSize] (không mở rộng ra ngoài — đổi kích thước bitmap của marker sẽ đổi luôn vị trí
     * hình học mà `anchor`/`rotation` của `MarkerComposable` tính toán trên đó). */
    val MemberDotHeadingWidth: Dp = 8.dp
    val MemberDotHeadingHeight: Dp = 6.dp
}
