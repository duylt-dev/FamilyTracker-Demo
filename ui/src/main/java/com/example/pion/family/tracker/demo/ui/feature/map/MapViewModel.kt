package com.example.pion.family.tracker.demo.ui.feature.map

import com.example.pion.family.tracker.demo.domain.repository.NetworkMonitor
import com.example.pion.family.tracker.demo.domain.repository.SimulatedRouteRepository
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZonesUseCase
import com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel

/**
 * `init` quan sát 6 nguồn độc lập, mỗi cái một `collectSafely` riêng — không gộp bằng
 * `combine()` vì chúng cập nhật `MapState` ở field khác nhau và không phụ thuộc lẫn nhau
 * (geofence plan phase-05 Step 3, phase-01 Implementation Step 6). `isTracking` quan sát
 * `TrackingRepository.isTracking()` — nguồn sự thật là service thật, không phải cờ VM tự giữ
 * (LLM.md §8.5). `observeLiveSelfLocation()` (phase-01, D4) là nguồn thứ tư: vị trí thật CHƯA
 * lọc, nuôi `MapState.liveSelfLocation` cho US-06/US-43 (`decisions.md` §C3).
 * `SimulatedRouteRepository.observeSource()` (smooth-road plan phase-05 Step 4, US-46) là nguồn thứ
 * năm: đã GỘP theo `RouteSourceAggregator` ở `:data`, nuôi `MapState.routeSource` cho dải ghi công
 * OSM — `:ui` KHÔNG tự gộp lại logic đó (`docs/routing-and-map-attribution.md` §3).
 * `NetworkMonitor.observeHasInternet()` (phase-07, US-47/D8) là nguồn thứ sáu, nuôi
 * `MapState.hasInternet` cho lớp phủ chặn màn Bản đồ khi mất mạng. **Ranh giới sống còn (Key
 * Insight #1 phase-07):** nguồn này KHÔNG được đọc mã lỗi HTTP của [SimulatedRouteRepository]/
 * routing, và ngược lại — `InternetBlockerBoundaryTest` (`:data`) khoá luật đó bằng cách quét mã
 * nguồn cả hai phía.
 *
 * `observeZones`/`observeMembersWithLastLocation` không phải `private val` — chỉ dùng trong
 * `init` (MVI doc §3 luật 4).
 */
class MapViewModel(
    observeZones: ObserveZonesUseCase,
    observeMembersWithLastLocation: ObserveMembersWithLastLocationUseCase,
    private val trackingRepository: TrackingRepository,
    simulatedRouteRepository: SimulatedRouteRepository,
    networkMonitor: NetworkMonitor,
) : MviViewModel<MapState, MapIntent, MapEffect>(MapState()) {

    init {
        // "state chỉ giữ những gì cần" (MVI doc §7 Unbounded growth) — 100 là giới hạn CỨNG của
        // Play Services (TrackingConstants.MAX_ZONES), đã chặn ở SaveZoneUseCase lúc TẠO; take()
        // ở đây là lưới an toàn thứ hai cho một kho dữ liệu cũ/hỏng có nhiều hơn thế.
        observeZones().collectSafely { zones ->
            setState { copy(zones = zones.take(TrackingConstants.MAX_ZONES)) }
        }
        observeMembersWithLastLocation().collectSafely { locations ->
            setState { copy(memberLocations = locations) }
        }
        trackingRepository.isTracking().collectSafely { enabled -> setState { copy(isTracking = enabled) } }
        trackingRepository.observeLiveSelfLocation().collectSafely { point -> setState { copy(liveSelfLocation = point) } }
        simulatedRouteRepository.observeSource().collectSafely { source -> setState { copy(routeSource = source) } }
        networkMonitor.observeHasInternet().collectSafely { has -> setState { copy(hasInternet = has) } }
    }

    override fun onIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.PermissionStateChanged -> setState {
                copy(
                    notificationsGranted = intent.notificationsGranted,
                    fineLocationGranted = intent.fineLocationGranted,
                )
            }
            MapIntent.ToggleTracking -> onToggleTracking()
            is MapIntent.MapLongPressed -> sendEffect(MapEffect.OpenZoneEditor(intent.lat, intent.lng))
            is MapIntent.MemberTapped -> setState { copy(selectedMemberId = intent.memberId) }
            MapIntent.CameraCentered -> setState { copy(hasCenteredOnce = true) }
            MapIntent.ZoneListRequested -> sendEffect(MapEffect.OpenZoneList)
            MapIntent.HistoryRequested -> sendEffect(MapEffect.OpenHistory)
            MapIntent.TimelineRequested -> sendEffect(MapEffect.OpenTimeline)
            is MapIntent.NavigateToMemberRequested -> sendEffect(MapEffect.OpenNavigation(intent.memberId))
        }
    }

    private fun onToggleTracking() {
        // "Bật" khi thiếu quyền vị trí vẫn được phép chạm — TrackingRepositoryImpl chỉ start
        // service; FusedLocationSource tự đóng flow nếu thiếu quyền (không crash, PRD §7.4).
        launchSafely(onError = { sendEffect(MapEffect.ShowError(it)) }) {
            trackingRepository.setTracking(!currentState.isTracking)
        }
    }
}
