package com.example.pion.family.tracker.demo.ui.designsystem.component

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R

/**
 * US-11 — 4 lối vào chính (PRD §5.5: "Bản đồ · Zone · Lịch sử · Nhật ký"). Sống ở
 * `designsystem/component/`, không `feature/map/component/`, dù chỉ Map dùng nó ở phase-05:
 * phase-06/08/10 thay các placeholder `Text(...)` ở `FamilyTrackerNavHost` bằng màn thật, và mỗi
 * màn đó render đúng thanh này (đã quyết định sẵn ở `phase-05-map-screen.md` Related Code Files —
 * không đợi "≥2 chỗ dùng thật" như quy tắc mặc định LLM.md §12 vì nơi dùng thứ hai đã có lịch cụ
 * thể, không phải suy đoán).
 *
 * Không dùng icon: `androidx.compose.material:material-icons-core` không nằm trong dependency
 * của `:ui` (xem `ui/build.gradle.kts`) — thêm nó chỉ để có 4 icon khi nhãn chữ đã đủ rõ (PRD §5.5
 * chỉ vẽ chữ trong ASCII layout, không có icon) là vi phạm YAGNI.
 */
@Composable
fun FamilyTrackerBottomBar(
    current: BottomBarDestination,
    onSelect: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        BottomBarDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = {},
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}

enum class BottomBarDestination(val labelRes: Int) {
    MAP(R.string.bottom_nav_map),
    ZONE(R.string.bottom_nav_zone),
    HISTORY(R.string.bottom_nav_history),
    TIMELINE(R.string.bottom_nav_timeline),
}
