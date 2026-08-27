package com.example.pion.family.tracker.demo.ui.feature.zone.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.designsystem.theme.ZoneExitRed
import com.example.pion.family.tracker.demo.ui.feature.zone.ZoneListItem
import com.example.pion.family.tracker.demo.ui.feature.zone.ZoneMemberChip

/** Nhỏ hơn `Dimens.MemberDotSize` (20dp, marker trên bản đồ) — ở đây chấm chỉ là dấu nhận màu
 * cạnh tên, không phải thứ người dùng phải bấm trúng. */
private val MEMBER_DOT_SIZE = 10.dp

/**
 * US-12/US-14 — một dòng zone. Vuốt để xoá bằng `SwipeToDismissBox` thật (chữ ký xác nhận từ
 * `material3-android:1.5.0-alpha17` sources, không đoán): `onDismiss` chỉ BÁO lên
 * [onDeleteRequested] (mở hộp thoại xác nhận, US-14) — KHÔNG tự xoá gì. Nếu người dùng huỷ, dòng
 * phải tự trả về vị trí cũ, nên [isPendingDelete] gác một `LaunchedEffect` gọi `state.reset()`.
 */
@Composable
internal fun ZoneRow(
    item: ZoneListItem,
    isPendingDelete: Boolean,
    onTapped: () -> Unit,
    onNotifyToggled: (Boolean) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(isPendingDelete) {
        if (!isPendingDelete && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { DeleteBackground() },
        onDismiss = { onDeleteRequested() },
    ) {
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onTapped)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.zone_radius_value, item.radiusMeters.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MembersInside(members = item.membersInside)
                }
                Switch(checked = item.notifyEnabled, onCheckedChange = onNotifyToggled)
            }
        }
    }
}

/**
 * "Ai đang ở trong zone này" — thay cho nhãn "Đang ở trong / Ở ngoài" của bản đầu tiên, vốn nói về
 * SELF và vì thế báo "Đang ở trong" ngay khi người dùng khoanh một zone quanh chỗ mình đang đứng.
 *
 * Chấm màu lấy từ `Member.colorArgb`, cùng nguồn với marker trên bản đồ (`MemberMarkers`) — nhìn
 * chấm cam ở đây là biết ngay đó là marker cam ngoài Map, không cần chú giải. Tên gộp thành MỘT
 * `Text` cắt bằng ellipsis thay vì mỗi người một chip: dòng zone phải giữ chiều cao cố định trong
 * `LazyColumn`, một hàng chip tự xuống dòng sẽ làm dòng nhảy chiều cao mỗi khi có người vào/ra.
 */
@Composable
private fun MembersInside(members: List<ZoneMemberChip>, modifier: Modifier = Modifier) {
    if (members.isEmpty()) {
        Text(
            text = stringResource(R.string.zone_members_inside_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        members.forEach { member ->
            Box(
                modifier = Modifier
                    .size(MEMBER_DOT_SIZE)
                    .clip(CircleShape)
                    .background(Color(member.colorArgb)),
            )
        }
        Text(
            text = members.joinToString(separator = ", ") { it.name },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `Card` below has no explicit `shape` so it draws with the M3 default `MaterialTheme.shapes.medium`
 * (12dp rounded corners). This background is composed and laid out for the FULL row size even at
 * rest (`SwipeToDismissBoxValue.Settled`) — `SwipeToDismissBox` always renders `backgroundContent`,
 * it does not gate it on swipe progress. Without clipping to the same shape, this rectangular red
 * fill's square corners peek out a few dp past the Card's rounded corners on EVERY row, not just
 * while swiping — confirmed on-device (`scratchpad/zonelist100.png`, at rest, no swipe). Clipping to
 * the same shape the Card already uses is the one-line fix; gating on swipe state was the other
 * option but would still need this same clip to look right mid-swipe (M3 default Card shape), so
 * clipping alone covers both states. */
@Composable
private fun DeleteBackground() {
    Box(modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(ZoneExitRed))
}
