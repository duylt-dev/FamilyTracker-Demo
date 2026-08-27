package com.example.pion.family.tracker.demo.ui.feature.navigation

import androidx.lifecycle.SavedStateHandle
import com.example.pion.family.tracker.demo.domain.model.NavigationUpdate
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveNavigationUseCase
import com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel
import com.example.pion.family.tracker.demo.ui.navigation.NavigationRoute
import kotlinx.coroutines.Job

/**
 * `targetMemberId` đọc từ [SavedStateHandle] bằng `get<T>(KEY)` ngay ở constructor, KHÔNG
 * `toRoute<NavigationRoute>()` — cùng lý do `ZoneEditorViewModel`/`HistoryViewModel` (xem KDoc
 * `ZoneEditorRoute` trong `Routes.kt`): `toRoute()` chạm `android.os.Bundle`, ném trên JVM test.
 *
 * `init` quan sát BỐN nguồn độc lập, mỗi cái một `collectSafely` riêng (không `combine()` — cùng lý
 * do `MapViewModel`: chúng cập nhật field khác nhau của [NavigationState] và không phụ thuộc nhau):
 * 1. [observeMembersWithLastLocation] — nguồn của [NavigationState.targetMember]/
 *    `storedSelfLocation`/`targetLocation`. `ObserveNavigationUseCase` không phát toạ độ (chỉ
 *    khoảng cách + tuyến), nên marker/camera vẫn cần nguồn riêng này — không phải trùng lặp.
 * 2. [observeNavigation] — tuyến, khoảng cách, đã-tới-nơi, lỗi provider. Job này đi qua
 *    [navigationJob] (KHÔNG `private val` mặc định của `collectSafely`) vì [NavigationIntent.Retry]
 *    cần huỷ-và-thay nó (MVI doc §3 "cancel and replace", cùng mẫu `HistoryViewModel.routeObservationJob`)
 *    để ép một `RerouteState` MỚI — lần gọi provider đầu của một phiên luôn bỏ qua debounce 60s.
 * 3. `trackingRepository.isTracking()` — nguồn sự thật cho banner "bật theo dõi", cùng mẫu
 *    `MapViewModel`.
 * 4. **`trackingRepository.observeLiveSelfLocation()` — feedback #3.** Nguồn #1 chỉ đọc Room, tức
 *    những điểm đã qua `LocationFilter`; ở bản demo self chưa từng bật theo dõi thì đó là điểm SEED
 *    ngẫu nhiên của `DemoDataSeeder`, cách vị trí GPS thật hơn 1 km. Cùng lúc đó
 *    `ObserveNavigationUseCase` lại dựng tuyến từ `LocationSource` (GPS thật), nên marker "tôi" và
 *    điểm xuất phát của tuyến chỉ vào hai chỗ khác nhau trên CÙNG một màn hình. Cổng này là đúng
 *    cổng mà `MapViewModel` đọc — đọc nó ở đây là cách duy nhất để hai màn không thể lệch nhau nữa.
 */
class NavigationViewModel(
    savedStateHandle: SavedStateHandle,
    observeMembersWithLastLocation: ObserveMembersWithLastLocationUseCase,
    private val observeNavigation: ObserveNavigationUseCase,
    private val trackingRepository: TrackingRepository,
) : MviViewModel<NavigationState, NavigationIntent, NavigationEffect>(NavigationState()) {

    private val targetMemberId: String = savedStateHandle.get<String>(NavigationRoute.ARG_MEMBER_ID).orEmpty()
    private var navigationJob: Job? = null

    init {
        observeMembersWithLastLocation().collectSafely { locations ->
            val target = locations.firstOrNull { it.member.id == targetMemberId }
            val self = locations.firstOrNull { it.member.isSelf }
            setState {
                copy(
                    targetMember = target?.member,
                    targetLocation = target?.lastLocation,
                    storedSelfLocation = self?.lastLocation,
                )
            }
        }
        startObservingNavigation()
        trackingRepository.isTracking().collectSafely { enabled -> setState { copy(isTracking = enabled) } }
        trackingRepository.observeLiveSelfLocation().collectSafely { point ->
            setState { copy(liveSelfLocation = point) }
        }
    }

    override fun onIntent(intent: NavigationIntent) {
        when (intent) {
            NavigationIntent.Retry -> {
                setState { copy(error = null) }
                startObservingNavigation()
            }
            NavigationIntent.StopNavigation -> sendEffect(NavigationEffect.NavigateBack)
            NavigationIntent.EnableTrackingRequested -> onEnableTrackingRequested()
            NavigationIntent.CameraCentered -> setState { copy(hasCenteredOnce = true) }
        }
    }

    private fun startObservingNavigation() {
        navigationJob?.cancel()
        navigationJob = observeNavigation(targetMemberId).collectSafely(
            onError = { setState { copy(error = it) } },
            onEach = ::applyUpdate,
        )
    }

    /**
     * Requirement phi chức năng #10 — lỗi provider KHÔNG được xoá tuyến đang vẽ.
     * `update.directions` đã là nguồn sự thật cho việc đó (`ObserveNavigationUseCase` giữ nguyên
     * `Directions` cũ khi `RoutingProvider.directions()` thất bại — chỉ đổi khi thành công), nên gán
     * thẳng `directions = update.directions` ở đây KHÔNG bao giờ vô tình null hoá tuyến đã có, kể cả
     * khi `update.lastError != null` cùng lúc.
     *
     * `isFallbackStraightLine` PHẢI dính — xem KDoc [NavigationState]. `isFallbackStraightLine` bên
     * phải của biểu thức đọc field của STATE CŨ (receiver `S.() -> S` của `copy`, chưa bị ghi đè bởi
     * chính lệnh `copy` này), không phải giá trị mới.
     */
    private fun applyUpdate(update: NavigationUpdate) {
        setState {
            copy(
                directions = update.directions,
                distanceMeters = update.distanceMeters,
                isDistanceEstimated = update.isDistanceEstimated,
                isFallbackStraightLine = update.directions == null && (update.lastError != null || isFallbackStraightLine),
                hasArrived = update.hasArrived,
                error = update.lastError,
            )
        }
        update.lastError?.let { sendEffect(NavigationEffect.ShowError(it)) }
    }

    /**
     * "Bật" khi người dùng chạm nút — KHÔNG tự bật thay (Requirement #10). Gọi thẳng
     * `trackingRepository.setTracking(true)`, cùng mẫu `MapViewModel.onToggleTracking`: không cần
     * kiểm quyền trước, `TrackingRepositoryImpl` chỉ start service và `FusedLocationSource` tự đóng
     * flow nếu thiếu quyền (không crash). [NavigationEffect.StartTracking] là xác nhận một-lần cho
     * Route hiện snackbar — trạng thái banner tự ẩn theo `state.isTracking` (nguồn thật ở #3 trên).
     */
    private fun onEnableTrackingRequested() {
        launchSafely(onError = { sendEffect(NavigationEffect.ShowError(it)) }) {
            trackingRepository.setTracking(true)
            sendEffect(NavigationEffect.StartTracking)
        }
    }
}
