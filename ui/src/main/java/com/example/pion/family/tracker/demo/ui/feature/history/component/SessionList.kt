package com.example.pion.family.tracker.demo.ui.feature.history.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.format.DistanceFormat
import com.example.pion.family.tracker.demo.ui.core.format.DurationFormat
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

/**
 * US-30 — mỗi dòng "HH:mm → HH:mm  quãng đường", dòng đang được vẽ trên bản đồ có dấu ✓
 * (PRD §5.7). Danh sách ĐÃ được `RouteSplitter` gom theo `SESSION_GAP_MS` ở `:domain` trước khi
 * tới đây — component này chỉ hiển thị, không tách chuyến lần hai.
 */
@Composable
internal fun SessionList(
    sessions: List<TrackSession>,
    selectedSessionId: String?,
    onSessionTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = Dimens.SpaceSm)) {
        items(sessions, key = { it.id }) { session ->
            SessionRow(
                session = session,
                isSelected = session.id == selectedSessionId,
                onTapped = { onSessionTapped(session.id) },
            )
        }
    }
}

@Composable
private fun SessionRow(session: TrackSession, isSelected: Boolean, onTapped: () -> Unit) {
    Card(
        onClick = onTapped,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceXs),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${DurationFormat.formatClock(session.startedAt)} → ${DurationFormat.formatClock(session.endedAt)}")
            Text(DistanceFormat.format(session.distanceMeters))
            if (isSelected) Text(stringResource(R.string.history_session_selected_mark))
        }
    }
}
