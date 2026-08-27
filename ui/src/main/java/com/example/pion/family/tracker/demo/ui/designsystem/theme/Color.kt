package com.example.pion.family.tracker.demo.ui.designsystem.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * PRD §5.2 — bảng màu chính thức, thay `Purple40`/`PurpleGrey40`/`Pink40` mẫu của template gốc.
 * phase-06 sửa `LLM.md` §13 (trước là Open #5): nút/`Switch` bật đang tím thay vì xanh dương vì
 * chưa ai đổi `LightColorScheme` — Zone Editor mới là màn đầu tiên chạm `Theme.kt`/`Color.kt` nên
 * đây là chỗ trả nợ, theo đúng ghi chú "pick up whenever a phase touches Theme.kt/Color.kt".
 */
val PrimaryBlue = Color(0xFF1B6EF3)
val ZoneEnterGreen = Color(0xFF2E7D32)
val ZoneExitRed = Color(0xFFC62828)
val SurfaceWhite = Color(0xFFFFFFFF)
val BackgroundGray = Color(0xFFF5F6F8)

/**
 * 6 màu định sẵn cho Zone Editor (US-20, PRD §5.2 "Sáu màu zone định sẵn") — ARGB `Int` vì đó là
 * kiểu lưu trong Room (`ZoneEntity.colorArgb`, `Zone.colorArgb`), không phải `androidx.compose.ui.graphics.Color`
 * — tránh Zone Editor phải convert qua lại giữa hai kiểu màu ở mọi nơi nó chạm vào `Zone`.
 */
object ZoneColorPalette {
    val COLORS: List<Int> = listOf(
        0xFF1B6EF3.toInt(),
        0xFF2E7D32.toInt(),
        0xFFE5820C.toInt(),
        0xFFC62828.toInt(),
        0xFF7B3FF2.toInt(),
        0xFF00838F.toInt(),
    )
}

/**
 * Routing plan phase-05, Key Insight #4 / `docs/routing-and-map-attribution.md` §3 mục 2 — polyline
 * dẫn đường phải "visually distinguish" được với **nội dung Google**, và đó mới là ràng buộc chính;
 * không trùng bảng màu của app chỉ là điều kiện phụ.
 *
 * Bản đầu chọn vàng hổ phách `0xFFFFD600` vì nó không trùng `PrimaryBlue`, `ZoneEnterGreen`/
 * `ZoneExitRed`, hay 6 màu `ZoneColorPalette.COLORS`. Chụp thật trên máy thì hỏng: Google tô đường
 * chính của basemap mặc định bằng đúng dải vàng đó, nên tuyến đường đọc như một con phố của Google
 * chứ không phải lớp phủ của ta — đúng cái mà "visually distinguish" cấm. Ảnh chứng ở
 * `plans/260824-1335-pluggable-routing-provider/reports/`.
 *
 * Chọn theo sắc độ (hue), không theo cảm tính. Sắc độ Google đã dùng trên basemap: vàng ~45°
 * (đường chính), xanh lá ~120° (công viên), xanh nước ~200°, và xanh dương ~215° (polyline chỉ
 * đường của chính Google). Sắc độ app đã dùng: 0° đỏ, 33° cam, 123° lục, 187° lam ngọc, 217° lam,
 * 262° tím. Magenta ~320° là khoảng trống lớn nhất còn lại trong cả hai danh sách, và không có
 * thành phần nào của basemap mang màu đó.
 *
 * Đổi màu này thì phải chụp lại ảnh chứng — điều kiện được kiểm bằng mắt trên máy, không bằng đọc code.
 */
val NavigationRouteColor = Color(0xFFE10098)
