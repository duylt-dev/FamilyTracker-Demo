@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.pion.family.tracker.demo.ui.feature.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.mvi.CollectEffects
import com.example.pion.family.tracker.demo.ui.designsystem.component.BottomBarDestination
import com.example.pion.family.tracker.demo.ui.designsystem.component.FamilyTrackerBottomBar
import com.example.pion.family.tracker.demo.ui.feature.timeline.component.DayHeader
import com.example.pion.family.tracker.demo.ui.feature.timeline.component.EmptyTimelineState
import com.example.pion.family.tracker.demo.ui.feature.timeline.component.TimelineRow
import org.koin.androidx.compose.koinViewModel

/**
 * US-34→US-36. Là một trong 4 tab đáy (`FamilyTrackerBottomBar`, US-11) — cùng tiền lệ
 * `MapRoute`/`ZoneListRoute`/`HistoryRoute`, chuyển tab qua chính thanh đó, không có "back". Cũng
 * là đích của `ftd_route=timeline` (phase-07, US-22 — bấm thông báo zone) — nối ở
 * `FamilyTrackerNavHost`, không phải ở đây.
 *
 * Hai đường điều hướng tới History KHÁC NHAU: [onOpenHistory] (tab đáy, không toạ độ) và
 * [onOpenHistoryAt] (US-35, bấm một dòng — mang `epochDay`/`lat`/`lng` từ `TimelineEffect.OpenHistory`
 * để `HistoryRoute` canh camera đúng vị trí, xem `HistoryViewModel`/`HistoryMap` KDoc phase-08).
 */
@Composable
fun TimelineRoute(
    onOpenMap: () -> Unit,
    onOpenZoneList: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenHistoryAt: (epochDay: Long, lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is TimelineEffect.OpenHistory -> onOpenHistoryAt(effect.epochDay, effect.lat, effect.lng)
        }
    }

    TimelineScreen(
        state = state,
        onIntent = onIntent,
        onBottomBarSelect = { destination ->
            when (destination) {
                BottomBarDestination.MAP -> onOpenMap()
                BottomBarDestination.ZONE -> onOpenZoneList()
                BottomBarDestination.HISTORY -> onOpenHistory()
                BottomBarDestination.TIMELINE -> Unit
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun TimelineScreen(
    state: TimelineState,
    onIntent: (TimelineIntent) -> Unit,
    onBottomBarSelect: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.timeline_title)) }) },
        bottomBar = { FamilyTrackerBottomBar(current = BottomBarDestination.TIMELINE, onSelect = onBottomBarSelect) },
    ) { padding ->
        if (state.isEmpty) {
            EmptyTimelineState(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                state.days.forEach { day ->
                    stickyHeader(key = day.epochDay) { DayHeader(label = day.label) }
                    items(day.items, key = { it.eventId }) { item ->
                        TimelineRow(item = item, onTapped = { onIntent(TimelineIntent.EventTapped(item.eventId)) })
                    }
                }
            }
        }
    }
}
