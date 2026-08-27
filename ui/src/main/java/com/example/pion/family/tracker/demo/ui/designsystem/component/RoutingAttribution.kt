package com.example.pion.family.tracker.demo.ui.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

/**
 * Dải credit BẮT BUỘC — `docs/routing-and-map-attribution.md` §3 mục 1, Routing plan phase-05 Key
 * Insight #1/#2 (mọi số Key Insight trong KDoc này đều của Routing plan). Nằm ở
 * `designsystem/component/` vì SMOOTH-ROAD plan phase-05 cho nó chỗ dùng thứ hai (màn Bản đồ), đúng
 * ngưỡng LLM.md §12 "một composable ≥2 feature dùng" — trước đó chỉ một chỗ dùng (Dẫn đường) nên nó
 * sống ở `feature/navigation/component/`. Gọi từ NGOÀI khung bản đồ (một dải riêng phía dưới, không phải
 * overlay `align(BottomStart)` chồng lên bản đồ) — góc dưới-trái BÊN TRONG khung bản đồ là
 * logo/attribution của Google, không được che và không được di chuyển.
 *
 * Chỉ nhận `List<String>` đã dựng sẵn ([attributionLines], đọc thẳng từ `Directions.attribution`)
 * — KHÔNG nhận `engineId`. `engineId` chỉ để log/chẩn đoán; nếu composable này tự ghép chuỗi từ nó,
 * một ngày ai đó đổi tên engine trong log sẽ âm thầm đổi luôn nội dung pháp lý hiện trên màn hình
 * (Implementation Step 5).
 *
 * Ba trạng thái, không có trạng thái thứ tư:
 * - [attributionLines] không rỗng → "Tuyến đường: {các dòng nối bằng " · "}".
 * - rỗng nhưng [isFallbackStraightLine] → nhãn ước tính, KHÔNG credit OSM (Key Insight #3: đường
 *   thẳng tự vẽ không mang dữ liệu OSM nào trên màn hình, ghi credit lúc đó là ghi sai nguồn).
 * - cả hai đều không (chưa có gì để vẽ, vd. màn vừa mở) → ẩn hẳn, không vẽ Text rỗng.
 */
@Composable
internal fun RoutingAttribution(
    attributionLines: List<String>,
    isFallbackStraightLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = when {
        attributionLines.isNotEmpty() -> stringResource(R.string.route_attribution_route, attributionLines.joinToString(" · "))
        isFallbackStraightLine -> stringResource(R.string.route_attribution_fallback)
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
