@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.pion.family.tracker.demo.ui.feature.navigation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.example.pion.family.tracker.demo.ui.designsystem.component.RoutingAttribution
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.feature.navigation.component.NavigationMap
import com.example.pion.family.tracker.demo.ui.feature.navigation.component.NavigationSummaryCard
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Bị đẩy CHỒNG lên từ marker một thành viên trên `MapScreen` (US "Chỉ đường") — cần "back" thật
 * (`NavigateBack` Effect qua nút "←"), không phải tab đáy, cùng lý do `ZoneEditorRoute`.
 */
@Composable
fun NavigationRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavigationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    // MVI doc §4 "Resolve strings inside the composition" — `stringResource` ở đây, KHÔNG
    // `context.getString(...)` trong nhánh `StartTracking` bên dưới (đó chạy trong composable
    // scope thật, khác `toNavigationDisplayMessage` là hàm KHÔNG `@Composable`).
    val trackingStartedMessage = stringResource(R.string.navigation_tracking_started_message)
    val retryActionLabel = stringResource(R.string.navigation_retry_action)

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            NavigationEffect.NavigateBack -> onNavigateBack()
            is NavigationEffect.ShowError -> {
                // Hành động "Thử lại" là đường DUY NHẤT gửi được `NavigationIntent.Retry`. Không có
                // nó, intent đó là code chết và người dùng gặp lỗi phải đứng chờ hết debounce 60s
                // mới có lần gọi provider tiếp theo — `Retry` huỷ-và-thay job quan sát, tạo một
                // `RerouteState` mới, và lần gọi đầu của một phiên luôn đi qua debounce ngay.
                val message = effect.error.toNavigationDisplayMessage(context)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(message, actionLabel = retryActionLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onIntent(NavigationIntent.Retry)
                }
            }
            NavigationEffect.StartTracking -> {
                scope.launch { snackbarHostState.showSnackbar(trackingStartedMessage) }
            }
        }
    }

    NavigationScreen(
        state = state,
        onIntent = onIntent,
        onBackTapped = { onIntent(NavigationIntent.StopNavigation) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
private fun NavigationScreen(
    state: NavigationState,
    onIntent: (NavigationIntent) -> Unit,
    onBackTapped: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.targetMember?.name ?: stringResource(R.string.navigation_title)) },
                // Không dùng `androidx.compose.material.icons` — cùng lý do `ZoneEditorScreen`.
                navigationIcon = {
                    IconButton(onClick = onBackTapped) {
                        Text(text = "←", style = MaterialTheme.typography.headlineSmall)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                NavigationMap(
                    self = state.selfLocation,
                    target = state.targetLocation,
                    targetColorArgb = state.targetMember?.colorArgb,
                    routePolyline = state.routePolyline,
                    routeStart = state.routeStart,
                    routeEnd = state.routeEnd,
                    isStraightLineOnly = state.isStraightLineOnly,
                    hasCenteredOnce = state.hasCenteredOnce,
                    onCameraCentered = { onIntent(NavigationIntent.CameraCentered) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!state.isTracking) {
                    TrackingOffBanner(
                        onEnableTapped = { onIntent(NavigationIntent.EnableTrackingRequested) },
                        modifier = Modifier.align(Alignment.TopStart).padding(Dimens.ScreenPadding),
                    )
                }
            }

            NavigationSummaryCard(
                targetMemberName = state.targetMember?.name,
                distanceMeters = state.distanceMeters,
                isDistanceEstimated = state.isDistanceEstimated,
                durationSeconds = state.directions?.durationSeconds,
                hasArrived = state.hasArrived,
                modifier = Modifier.fillMaxWidth().padding(Dimens.ScreenPadding),
            )

            RoutingAttribution(
                attributionLines = state.attributionLines,
                isFallbackStraightLine = state.isFallbackStraightLine,
            )
        }
    }
}

/**
 * Requirement phi chức năng #10 — chỉ giải thích + một nút bấm, KHÔNG tự bật thay người dùng và
 * KHÔNG chặn màn hình (không phải dialog, chỉ là một Card nổi ở góc trên). Lái hoàn toàn bằng
 * `state.isTracking` ở nơi gọi nên tự ẩn ngay khi công tắc bật, không cần nhánh riêng.
 */
@Composable
private fun TrackingOffBanner(onEnableTapped: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Text(
                text = stringResource(R.string.navigation_tracking_banner_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnableTapped) {
                Text(stringResource(R.string.navigation_enable_tracking_action))
            }
        }
    }
}

/** Cùng mẫu `MapScreen.AppError.toDisplayMessage` — mỗi feature giữ bản mapping của riêng mình
 * (LLM.md không có `ErrorMessages.kt` dùng chung, xem `ZoneEditorScreen`/`HistoryScreen`). */
private fun AppError.toNavigationDisplayMessage(context: Context): String = when (this) {
    is AppError.Network -> context.getString(R.string.error_network)
    is AppError.NotFound -> context.getString(R.string.error_not_found)
    is AppError.Validation -> context.getString(R.string.error_validation)
    is AppError.Unexpected -> context.getString(R.string.error_generic)
}
