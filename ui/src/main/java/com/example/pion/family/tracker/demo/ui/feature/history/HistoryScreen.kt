@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pion.family.tracker.demo.ui.feature.history

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.mvi.CollectEffects
import com.example.pion.family.tracker.demo.ui.designsystem.component.BottomBarDestination
import com.example.pion.family.tracker.demo.ui.designsystem.component.FamilyTrackerBottomBar
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.feature.history.component.DayPickerBar
import com.example.pion.family.tracker.demo.ui.feature.history.component.EmptyRouteState
import com.example.pion.family.tracker.demo.ui.feature.history.component.HistoryMap
import com.example.pion.family.tracker.demo.ui.feature.history.component.RouteStatsCard
import com.example.pion.family.tracker.demo.ui.feature.history.component.SessionList
import com.example.pion.family.tracker.demo.ui.feature.history.component.SimulateRouteButton
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private val SESSION_LIST_HEIGHT = 180.dp

/**
 * US-27→US-32. Là một trong 4 tab đáy (`FamilyTrackerBottomBar`, US-11), cùng tiền lệ
 * `MapRoute`/`ZoneListRoute` — chuyển tab qua chính thanh đó, không có "back".
 *
 * [focusPoint] (`HistoryEffect.FocusCamera`, US-35/phase-10) giữ ở state cục bộ của Route — MỘT
 * hiệu ứng, không phải trạng thái màn hình (MVI doc §2), nên không có chỗ trong `HistoryState`;
 * `HistoryMap` chỉ dùng nó cho lần canh camera ĐẦU TIÊN.
 */
@Composable
fun HistoryRoute(
    onOpenMap: () -> Unit,
    onOpenZoneList: () -> Unit,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    var focusPoint by remember { mutableStateOf<LatLng?>(null) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HistoryEffect.FocusCamera -> focusPoint = LatLng(effect.lat, effect.lng)
            is HistoryEffect.ShowError -> {
                val message = effect.error.toDisplayMessage(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    HistoryScreen(
        state = state,
        onIntent = onIntent,
        snackbarHostState = snackbarHostState,
        focusPoint = focusPoint,
        onBottomBarSelect = { destination ->
            when (destination) {
                BottomBarDestination.MAP -> onOpenMap()
                BottomBarDestination.ZONE -> onOpenZoneList()
                BottomBarDestination.HISTORY -> Unit
                BottomBarDestination.TIMELINE -> onOpenTimeline()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryState,
    onIntent: (HistoryIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    focusPoint: LatLng?,
    onBottomBarSelect: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    DayPickerBar(
                        selectedDay = state.selectedDay,
                        onDaySelected = { onIntent(HistoryIntent.SelectDay(it)) },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { FamilyTrackerBottomBar(current = BottomBarDestination.HISTORY, onSelect = onBottomBarSelect) },
    ) { padding ->
        // US-33 — nút mô phỏng ở CUỐI màn History (PRD §5.7), luôn có mặt bất kể ngày đang chọn có
        // dữ liệu hay không (chính là lối thoát khỏi US-32's empty state), nên nằm NGOÀI nhánh
        // if/else bên dưới thay vì lặp lại trong cả hai.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isEmptyDay) {
                EmptyRouteState(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HistoryMap(
                        session = state.selectedSession,
                        memberColorArgb = state.memberColorArgb,
                        focusPoint = focusPoint,
                        day = state.selectedDay,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.stats?.let {
                        RouteStatsCard(stats = it, modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMd))
                    }
                    SessionList(
                        sessions = state.sessions,
                        selectedSessionId = state.selectedSessionId,
                        onSessionTapped = { onIntent(HistoryIntent.SelectSession(it)) },
                        modifier = Modifier.fillMaxWidth().height(SESSION_LIST_HEIGHT),
                    )
                }
            }
            SimulateRouteButton(
                isSimulating = state.isSimulating,
                onClick = { onIntent(HistoryIntent.StartSimulation) },
                modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMd),
            )
        }
    }
}

/** Cùng mẫu `MapScreen.AppError.toDisplayMessage`/`ZoneListScreen` — không nâng thành file dùng
 * chung để tránh sửa các file đó, ngoài phạm vi sở hữu của phase-08. */
private fun AppError.toDisplayMessage(context: Context): String = when (this) {
    is AppError.Network -> context.getString(R.string.error_network)
    is AppError.NotFound -> context.getString(R.string.error_not_found)
    is AppError.Validation -> context.getString(R.string.error_validation)
    is AppError.Unexpected -> context.getString(R.string.error_generic)
}
