package com.example.pion.family.tracker.demo.ui.feature.map

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.mvi.CollectEffects
import com.example.pion.family.tracker.demo.ui.designsystem.component.BottomBarDestination
import com.example.pion.family.tracker.demo.ui.designsystem.component.FamilyTrackerBottomBar
import com.example.pion.family.tracker.demo.ui.designsystem.component.RoutingAttribution
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.feature.map.component.FamilyTrackerMap
import com.example.pion.family.tracker.demo.ui.feature.map.component.NavigateToMemberButton
import com.example.pion.family.tracker.demo.ui.feature.map.component.NoInternetOverlay
import com.example.pion.family.tracker.demo.ui.feature.map.component.PermissionBannerStack
import com.example.pion.family.tracker.demo.ui.feature.map.component.TrackingToggle
import com.example.pion.family.tracker.demo.ui.permission.currentPermissionStatus
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Màn chính (F1, US-06→US-11). [MapRoute] đọc lại quyền mỗi lần ON_START (quyền có thể đổi qua
 * Settings bất cứ lúc nào — giữ nguyên từ phase-04), báo lên ViewModel bằng Intent — ViewModel
 * không tự kiểm tra quyền (không import android.*).
 *
 * Điều hướng nhận bằng callback (`onOpenZoneEditor`/...), không phải `NavHostController` thẳng
 * vào feature — cùng quy ước `PermissionRoute(onFinished)` đã lập ở phase-04, giữ `:ui/feature/map`
 * tách khỏi chi tiết `navigation-compose`.
 */
@Composable
fun MapRoute(
    onOpenZoneEditor: (lat: Double, lng: Double) -> Unit,
    onOpenZoneList: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenNavigation: (memberId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // MVI doc §8 "skippable is a promise the route can break" — onIntent = viewModel::onIntent
    // viết thẳng ở call site cấp một Function1 mới mỗi lần recompose (capture ViewModel instance,
    // luôn unstable). Giữ nó ổn định để cây bản đồ bên dưới còn cơ hội skip (Implementation Step 9).
    val onIntent = remember(viewModel) { viewModel::onIntent }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                val status = context.currentPermissionStatus()
                onIntent(
                    MapIntent.PermissionStateChanged(
                        notificationsGranted = status.notificationsGranted,
                        fineLocationGranted = status.fineLocationGranted,
                    ),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is MapEffect.OpenZoneEditor -> onOpenZoneEditor(effect.lat, effect.lng)
            MapEffect.OpenZoneList -> onOpenZoneList()
            MapEffect.OpenHistory -> onOpenHistory()
            MapEffect.OpenTimeline -> onOpenTimeline()
            is MapEffect.OpenNavigation -> onOpenNavigation(effect.memberId)
            is MapEffect.ShowError -> {
                val message = effect.error.toDisplayMessage(context)
                // MVI doc §4: suspend công khai (showSnackbar) phải chạy trong scope riêng của
                // Route, KHÔNG await bởi bộ thu effect — nếu không, effect kế tiếp bị giữ lại sau
                // snackbar hiện tại.
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    MapScreen(
        state = state,
        onIntent = onIntent,
        snackbarHostState = snackbarHostState,
        onBottomBarSelect = { destination ->
            when (destination) {
                BottomBarDestination.MAP -> Unit
                BottomBarDestination.ZONE -> onIntent(MapIntent.ZoneListRequested)
                BottomBarDestination.HISTORY -> onIntent(MapIntent.HistoryRequested)
                BottomBarDestination.TIMELINE -> onIntent(MapIntent.TimelineRequested)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun MapScreen(
    state: MapState,
    onIntent: (MapIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBottomBarSelect: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { FamilyTrackerBottomBar(current = BottomBarDestination.MAP, onSelect = onBottomBarSelect) },
    ) { padding ->
        // Smooth-road plan phase-05 (KHÁC "Routing plan phase-05" nhắc bên dưới) — `Column` thay `Box`
        // phủ toàn màn: dải ghi công OSM (US-46) nằm NGOÀI khung bản đồ, không phải overlay chồng lên.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                FamilyTrackerMap(
                    zones = state.zones,
                    otherMembers = state.otherMembers,
                    self = state.selfLocation,
                    initialCameraTarget = state.initialCameraTarget,
                    hasCenteredOnce = state.hasCenteredOnce,
                    onLongClick = { lat, lng -> onIntent(MapIntent.MapLongPressed(lat, lng)) },
                    onMemberTapped = { id -> onIntent(MapIntent.MemberTapped(id)) },
                    onCameraCentered = { onIntent(MapIntent.CameraCentered) },
                    modifier = Modifier.fillMaxSize(),
                )

                // Feedback #1 — nút "Chỉ đường" lên góc PHẢI TRÊN, banner quyền xếp dưới nó trong
                // CÙNG một Column (lý do vì sao không phải hai `align()` rời: KDoc của nút).
                Column(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    val navigableMemberId = state.selectedMemberId
                    if (state.canNavigateToSelected && navigableMemberId != null) {
                        NavigateToMemberButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = { onIntent(MapIntent.NavigateToMemberRequested(navigableMemberId)) },
                        )
                    }
                    PermissionBannerStack(
                        showLocationDegraded = state.showLocationDegradedBanner,
                        showNotificationsOff = state.showNotificationsBanner,
                    )
                }

                // phase-07 (US-47/D8) — lớp phủ đứng SAU bản đồ/banner/nút Chỉ đường (chặn cả ba)
                // nhưng TRƯỚC `TrackingToggle`, nên công tắc nổi lên trên scrim và vẫn bấm được.
                // **Đây là sửa đổi có chủ ý so với Step 8 của phase doc** ("lớp phủ là phần tử cuối"):
                // đo thật cho thấy phủ luôn công tắc tạo ra đúng cái lỗ mà D8 sinh ra để chặn — mở
                // app lúc ngoại tuyến với theo dõi đang TẮT thì không bật được, nên không một
                // `location_points` nào được ghi suốt thời gian mất mạng (§13 Fixed #33). Theo dõi
                // chạy hoàn toàn ngoại tuyến (GPS + Room); internet chỉ cần cho TUYẾN ĐƯỜNG. Nút
                // "Chỉ đường" thì CỐ Ý để dưới scrim — màn Dẫn đường thật sự cần mạng.
                if (state.showNoInternetOverlay) {
                    NoInternetOverlay()
                }

                TrackingToggle(
                    isTracking = state.isTracking,
                    onToggle = { onIntent(MapIntent.ToggleTracking) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.ScreenPadding),
                )
            }

            // phase-05, US-46 — dải ghi công OSM, cùng composable dùng ở màn Dẫn đường.
            RoutingAttribution(
                attributionLines = state.attributionLines,
                isFallbackStraightLine = state.isFallbackRoute,
            )
        }
    }
}

private fun AppError.toDisplayMessage(context: Context): String = when (this) {
    is AppError.Network -> context.getString(R.string.error_network)
    is AppError.NotFound -> context.getString(R.string.error_not_found)
    is AppError.Validation -> context.getString(R.string.error_validation)
    is AppError.Unexpected -> context.getString(R.string.error_generic)
}
