@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pion.family.tracker.demo.ui.feature.zone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.mvi.CollectEffects
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.feature.zone.component.ColorPicker
import com.example.pion.family.tracker.demo.ui.feature.zone.component.RadiusSlider
import com.example.pion.family.tracker.demo.ui.feature.zone.component.ZoneCenterMap
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private val MAP_HEIGHT = 260.dp

/**
 * US-16→US-21. Không phải tab đáy (khác `ZoneListRoute`) — bị đẩy CHỒNG lên từ long-press bản đồ
 * (US-10) hoặc từ Zone List (US-13/US-15), nên cần "back" thật (`NavigateBack` Effect), không
 * chuyển qua `FamilyTrackerBottomBar`.
 */
@Composable
fun ZoneEditorRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ZoneEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ZoneEditorEffect.NavigateBack -> onNavigateBack()
            is ZoneEditorEffect.ShowMessage -> {
                val message = effect.error.toEditorDisplayMessage(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    ZoneEditorScreen(
        state = state,
        onIntent = onIntent,
        snackbarHostState = snackbarHostState,
        onBackTapped = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun ZoneEditorScreen(
    state: ZoneEditorState,
    onIntent: (ZoneEditorIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBackTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEditing) R.string.zone_editor_title_edit else R.string.zone_editor_title_new)) },
                // Không dùng `androidx.compose.material.icons` — không nằm trong dependency của
                // `:ui` (đã ghi trong `FamilyTrackerBottomBar.kt`), PRD §5.6 ASCII layout vẽ "←"
                // dạng chữ nên literal text ở đây đúng đặc tả, không phải chỗ thiếu sót.
                navigationIcon = {
                    IconButton(onClick = onBackTapped) {
                        Text(text = "←", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    TextButton(onClick = { onIntent(ZoneEditorIntent.SaveTapped) }, enabled = state.canSave) {
                        Text(stringResource(R.string.zone_editor_save_action))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ZoneCenterMap(
                centerLat = state.centerLat,
                centerLng = state.centerLng,
                hasCenteredOnce = state.hasCenteredOnce,
                radiusMeters = state.radiusMeters,
                colorArgb = state.colorArgb,
                onCenterMoved = { lat, lng -> onIntent(ZoneEditorIntent.CenterMoved(lat, lng)) },
                modifier = Modifier.fillMaxWidth().height(MAP_HEIGHT),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onIntent(ZoneEditorIntent.NameChanged(it)) },
                    label = { Text(stringResource(R.string.zone_editor_name_label)) },
                    singleLine = true,
                    isError = state.name.isNotEmpty() && !state.isNameValid,
                    modifier = Modifier.fillMaxWidth(),
                )

                RadiusSlider(
                    radiusMeters = state.radiusMeters,
                    showLowRadiusWarning = state.showLowRadiusWarning,
                    onRadiusChanged = { onIntent(ZoneEditorIntent.RadiusChanged(it)) },
                )

                ColorPicker(
                    selectedColorArgb = state.colorArgb,
                    onColorSelected = { onIntent(ZoneEditorIntent.ColorSelected(it)) },
                )

                NotifySwitchRow(
                    label = stringResource(R.string.zone_editor_notify_enter_label),
                    checked = state.notifyOnEnter,
                    onCheckedChange = { onIntent(ZoneEditorIntent.NotifyOnEnterToggled(it)) },
                )
                NotifySwitchRow(
                    label = stringResource(R.string.zone_editor_notify_exit_label),
                    checked = state.notifyOnExit,
                    onCheckedChange = { onIntent(ZoneEditorIntent.NotifyOnExitToggled(it)) },
                )

                if (state.showZoneLimitWarning) {
                    Text(
                        text = stringResource(R.string.zone_editor_limit_warning, TrackingConstants.MAX_ZONES),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifySwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Cùng mẫu `MapScreen.AppError.toDisplayMessage` (phase-05), với một khác biệt US-21 đòi: giữ
 * NGUYÊN `message` của `AppError.Validation` thay vì đè bằng chuỗi chung — `SaveZoneUseCase` đã
 * trả sẵn đúng văn bản PRD §2.4 ("Android giới hạn 100 zone cho mỗi ứng dụng"). */
private fun AppError.toEditorDisplayMessage(context: Context): String = when (this) {
    is AppError.Network -> context.getString(R.string.error_network)
    is AppError.NotFound -> context.getString(R.string.error_not_found)
    is AppError.Validation -> message ?: context.getString(R.string.error_validation)
    is AppError.Unexpected -> context.getString(R.string.error_generic)
}
