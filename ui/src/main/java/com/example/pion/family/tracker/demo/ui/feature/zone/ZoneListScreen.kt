@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pion.family.tracker.demo.ui.feature.zone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.mvi.CollectEffects
import com.example.pion.family.tracker.demo.ui.designsystem.component.BottomBarDestination
import com.example.pion.family.tracker.demo.ui.designsystem.component.FamilyTrackerBottomBar
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.feature.zone.component.ZoneRow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * US-12→US-15. Là một trong 4 tab đáy (`FamilyTrackerBottomBar`, US-11) như `MapRoute` — không
 * có "back", chuyển tab qua chính thanh đó (phase-05 tiền lệ). `ZoneEditorRoute` mới cần "back"
 * vì nó bị đẩy CHỒNG lên (từ dòng bấm hoặc long-press bản đồ), không phải một tab ngang hàng.
 */
@Composable
fun ZoneListRoute(
    onOpenEditor: (zoneId: String?) -> Unit,
    onOpenMap: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ZoneListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ZoneListEffect.OpenEditor -> onOpenEditor(effect.zoneId)
            is ZoneListEffect.ShowMessage -> {
                val message = effect.error.toDisplayMessage(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    ZoneListScreen(
        state = state,
        onIntent = onIntent,
        snackbarHostState = snackbarHostState,
        onBottomBarSelect = { destination ->
            when (destination) {
                BottomBarDestination.MAP -> onOpenMap()
                BottomBarDestination.ZONE -> Unit
                BottomBarDestination.HISTORY -> onOpenHistory()
                BottomBarDestination.TIMELINE -> onOpenTimeline()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ZoneListScreen(
    state: ZoneListState,
    onIntent: (ZoneListIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBottomBarSelect: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.zone_list_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { FamilyTrackerBottomBar(current = BottomBarDestination.ZONE, onSelect = onBottomBarSelect) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(ZoneListIntent.CreateTapped) }) {
                Text(stringResource(R.string.zone_list_create_action))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isEmpty) {
                ZoneListEmptyState(onCreateTapped = { onIntent(ZoneListIntent.CreateTapped) })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    items(state.zones, key = { it.id }) { item ->
                        ZoneRow(
                            item = item,
                            isPendingDelete = item.id == state.pendingDeleteId,
                            onTapped = { onIntent(ZoneListIntent.ZoneTapped(item.id)) },
                            onNotifyToggled = { enabled -> onIntent(ZoneListIntent.NotifyToggled(item.id, enabled)) },
                            onDeleteRequested = { onIntent(ZoneListIntent.DeleteRequested(item.id)) },
                        )
                    }
                }
            }
        }
    }

    if (state.pendingDeleteId != null) {
        DeleteConfirmDialog(
            zoneName = state.pendingDeleteName.orEmpty(),
            onConfirm = { onIntent(ZoneListIntent.DeleteConfirmed) },
            onDismiss = { onIntent(ZoneListIntent.DeleteCancelled) },
        )
    }
}

@Composable
private fun ZoneListEmptyState(onCreateTapped: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.zone_list_empty_message))
        Button(onClick = onCreateTapped, modifier = Modifier.padding(top = Dimens.SpaceMd)) {
            Text(stringResource(R.string.zone_list_create_action))
        }
    }
}

@Composable
private fun DeleteConfirmDialog(zoneName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zone_delete_dialog_title)) },
        text = { Text(stringResource(R.string.zone_delete_dialog_message, zoneName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.zone_delete_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.zone_delete_dialog_cancel)) } },
    )
}

/** Cùng mẫu `MapScreen.AppError.toDisplayMessage` (phase-05) — không nâng thành file dùng chung
 * để tránh sửa `MapScreen.kt`, file ngoài phạm vi sở hữu của phase-06. */
private fun AppError.toDisplayMessage(context: Context): String = when (this) {
    is AppError.Network -> context.getString(R.string.error_network)
    is AppError.NotFound -> context.getString(R.string.error_not_found)
    is AppError.Validation -> context.getString(R.string.error_validation)
    is AppError.Unexpected -> context.getString(R.string.error_generic)
}
