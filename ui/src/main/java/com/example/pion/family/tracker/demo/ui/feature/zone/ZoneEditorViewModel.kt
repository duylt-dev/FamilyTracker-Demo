package com.example.pion.family.tracker.demo.ui.feature.zone

import androidx.lifecycle.SavedStateHandle
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZonesUseCase
import com.example.pion.family.tracker.demo.domain.usecase.SaveZoneUseCase
import com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel
import com.example.pion.family.tracker.demo.ui.navigation.ZoneEditorRoute
import java.time.Instant
import java.util.UUID

/**
 * US-16→US-21. `zoneId`/`lat`/`lng` đọc từ [SavedStateHandle] bằng `get<T>(KEY)` NGAY TRONG
 * constructor, vào initial state (MVI doc §3 luật 5) — cố ý KHÔNG dùng
 * `savedStateHandle.toRoute<ZoneEditorRoute>()` dù nó "đúng kiểu" hơn: đã đo thật, `toRoute()`
 * chạm `android.os.Bundle`/`SavedState` typed-getter chưa mock trên JVM unit test và ném
 * `RuntimeException` (không Robolectric trong dự án, LLM.md §11) — xem KDoc ở `Routes.kt`. Koin
 * tự tổng hợp `SavedStateHandle` cho MỌI ViewModel constructor-injected khi resolve qua
 * `koinViewModel()` trong scope của một `NavBackStackEntry` (xác nhận bằng đọc source thật
 * `org.koin.viewmodel.factory.AndroidParametersHolder`, không đoán từ tài liệu).
 *
 * Ba nguồn TÂM khác nhau, cùng một cơ chế `hasCenteredOnce` để chỉ `.move()` camera đúng MỘT lần
 * (giống `FamilyTrackerMap`, phase-05 Key Insight #3):
 * 1. Route mang sẵn `lat`/`lng` (US-10, nhấn giữ bản đồ) → đã có ngay ở initial state, `hasCenteredOnce=true` từ đầu.
 * 2. Route KHÔNG mang toạ độ và `zoneId == null` (US-15 "Tạo zone" từ empty state) → chờ vị trí
 *    qua `observeMembersWithLastLocation()`, seed một lần. Cùng thứ tự ưu tiên với
 *    `MapState.initialCameraTarget` (phase-05): ưu tiên vị trí của mình, RƠI VỀ vị trí của một
 *    thành viên bất kỳ nếu self chưa từng bật theo dõi — nếu chỉ chờ self, self không tracking bao
 *    giờ = Flow không bao giờ seed = camera đứng nguyên ở (0,0) mặc định của
 *    `rememberCameraPositionState()` (Vịnh Guinea). `DemoDataSeeder` luôn chèn sẵn 2 thành viên
 *    khác kèm toạ độ demo HCMC nên trong thực tế nhánh self-null luôn có ít nhất một điểm dự phòng.
 * 3. `zoneId != null` (US-13, sửa) → chờ `observeZones()` phát lần đầu chứa đúng zone đó, nạp
 *    toàn bộ field (kể cả `createdAt` gốc, để Lưu không đổi ngày tạo).
 */
class ZoneEditorViewModel(
    savedStateHandle: SavedStateHandle,
    observeZones: ObserveZonesUseCase,
    observeMembersWithLastLocation: ObserveMembersWithLastLocationUseCase,
    private val saveZoneUseCase: SaveZoneUseCase,
) : MviViewModel<ZoneEditorState, ZoneEditorIntent, ZoneEditorEffect>(
    initialStateFrom(savedStateHandle),
) {

    private var hasSeededCenter = false

    init {
        observeZones().collectSafely { zones ->
            setState { copy(zoneCount = zones.size) }
            val zoneId = currentState.zoneId
            if (zoneId != null && !currentState.isLoaded) {
                zones.firstOrNull { it.id == zoneId }?.let { zone -> applyLoadedZone(zone) }
            }
        }

        if (currentState.zoneId == null && !currentState.hasCenteredOnce) {
            observeMembersWithLastLocation().collectSafely { locations ->
                if (!hasSeededCenter) {
                    // Same fallback order as MapState.initialCameraTarget (phase-05): self first,
                    // then any member with a recorded point — a fresh self that never tracked must
                    // not leave this Flow permanently silent.
                    val seedPoint = locations.firstOrNull { it.member.isSelf }?.lastLocation
                        ?: locations.firstNotNullOfOrNull { it.lastLocation }
                    if (seedPoint != null) {
                        hasSeededCenter = true
                        setState {
                            copy(centerLat = seedPoint.latitude, centerLng = seedPoint.longitude, hasCenteredOnce = true)
                        }
                    }
                }
            }
        }
    }

    private fun applyLoadedZone(zone: Zone) {
        setState {
            copy(
                name = zone.name,
                radiusMeters = zone.radiusMeters,
                centerLat = zone.latitude,
                centerLng = zone.longitude,
                colorArgb = zone.colorArgb,
                notifyOnEnter = zone.notifyOnEnter,
                notifyOnExit = zone.notifyOnExit,
                createdAt = zone.createdAt,
                isLoaded = true,
                hasCenteredOnce = true,
            )
        }
    }

    override fun onIntent(intent: ZoneEditorIntent) {
        when (intent) {
            is ZoneEditorIntent.NameChanged -> setState { copy(name = intent.name) }
            is ZoneEditorIntent.RadiusChanged -> setState { copy(radiusMeters = intent.radiusMeters) }
            is ZoneEditorIntent.CenterMoved -> setState { copy(centerLat = intent.lat, centerLng = intent.lng) }
            is ZoneEditorIntent.ColorSelected -> setState { copy(colorArgb = intent.colorArgb) }
            is ZoneEditorIntent.NotifyOnEnterToggled -> setState { copy(notifyOnEnter = intent.enabled) }
            is ZoneEditorIntent.NotifyOnExitToggled -> setState { copy(notifyOnExit = intent.enabled) }
            ZoneEditorIntent.SaveTapped -> onSaveTapped()
        }
    }

    private fun onSaveTapped() {
        if (!currentState.canSave) return
        val state = currentState
        val zone = Zone(
            id = state.zoneId ?: UUID.randomUUID().toString(),
            name = state.name.trim(),
            latitude = state.centerLat,
            longitude = state.centerLng,
            radiusMeters = state.radiusMeters,
            colorArgb = state.colorArgb,
            notifyOnEnter = state.notifyOnEnter,
            notifyOnExit = state.notifyOnExit,
            createdAt = state.createdAt,
        )
        setState { copy(isSaving = true) }
        launchSafely(
            onError = {
                setState { copy(isSaving = false) }
                sendEffect(ZoneEditorEffect.ShowMessage(it))
            },
        ) {
            // `FTD_EVENT zone_saved` logged by ZoneRepositoryImpl.save() (:data) — a ViewModel may
            // not import android.util.Log (MVI doc §9), and :data is the module every other
            // FTD_EVENT already lives in (ZoneEventRepositoryImpl, TrackingRepositoryImpl).
            when (val result = saveZoneUseCase(zone)) {
                is AppResult.Success -> sendEffect(ZoneEditorEffect.NavigateBack)
                is AppResult.Failure -> {
                    setState { copy(isSaving = false) }
                    sendEffect(ZoneEditorEffect.ShowMessage(result.error))
                }
            }
        }
    }
}

internal fun initialStateFrom(savedStateHandle: SavedStateHandle): ZoneEditorState {
    val zoneId = savedStateHandle.get<String?>(ZoneEditorRoute.ARG_ZONE_ID)
    val lat = savedStateHandle.get<Double?>(ZoneEditorRoute.ARG_LAT)
    val lng = savedStateHandle.get<Double?>(ZoneEditorRoute.ARG_LNG)
    val hasRouteCoordinates = lat != null && lng != null
    return ZoneEditorState(
        zoneId = zoneId,
        centerLat = lat ?: 0.0,
        centerLng = lng ?: 0.0,
        isLoaded = zoneId == null,
        hasCenteredOnce = hasRouteCoordinates,
    )
}
