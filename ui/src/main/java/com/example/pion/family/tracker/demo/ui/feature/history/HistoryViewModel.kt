package com.example.pion.family.tracker.demo.ui.feature.history

import androidx.lifecycle.SavedStateHandle
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveRouteForDayUseCase
import com.example.pion.family.tracker.demo.domain.usecase.StartSimulationUseCase
import com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel
import kotlinx.coroutines.Job
import java.time.LocalDate

/** Literal key khớp tên field của `HistoryRoute` — xem KDoc lớp [HistoryViewModel] vì sao không
 * phải hằng số `ARG_*` trong `Routes.kt`. */
private const val ARG_EPOCH_DAY = "epochDay"
private const val ARG_FOCUS_LAT = "focusLat"
private const val ARG_FOCUS_LNG = "focusLng"

/**
 * US-27→US-32. `epochDay`/`focusLat`/`focusLng` đọc từ [SavedStateHandle] bằng `get<T>(KEY)` ngay
 * ở constructor (MVI doc §3 luật 5, cùng mẫu `ZoneEditorViewModel`) — literal key khớp TÊN FIELD
 * của `HistoryRoute` (`"epochDay"`, `"focusLat"`, `"focusLng"`), không hằng số `ARG_*`: `Routes.kt`
 * không nằm trong "Related Code Files" của phase-08, nên không thêm companion object vào đó.
 *
 * `observeRouteForDay` cần `memberId` — màn này chỉ vẽ lộ trình của SELF (phase file Architecture:
 * "observeRouteForDay(memberId=self, day)"), nhưng không có repository method "lấy id của self"
 * riêng lẻ. Tái dùng [ObserveMembersWithLastLocationUseCase] (đã có, đã đăng ký Koin) để tìm self —
 * đúng tinh thần "tái dùng, đừng phát minh lại" (brief), tránh thêm method mới vào
 * `MemberRepository` chỉ để lấy một id ổn định gần như không đổi trong suốt vòng đời màn hình.
 */
class HistoryViewModel(
    savedStateHandle: SavedStateHandle,
    private val observeRouteForDay: ObserveRouteForDayUseCase,
    observeMembersWithLastLocation: ObserveMembersWithLastLocationUseCase,
    private val startSimulation: StartSimulationUseCase,
) : MviViewModel<HistoryState, HistoryIntent, HistoryEffect>(initialStateFrom(savedStateHandle)) {

    private var routeObservationJob: Job? = null
    private var selfMemberId: String? = null

    init {
        val focusLat = savedStateHandle.get<Double?>(ARG_FOCUS_LAT)
        val focusLng = savedStateHandle.get<Double?>(ARG_FOCUS_LNG)
        if (focusLat != null && focusLng != null) {
            sendEffect(HistoryEffect.FocusCamera(focusLat, focusLng))
        }

        // Self's memberId (và màu) không đổi trong thực tế, nhưng flow này re-emit mỗi khi CÓ
        // điểm mới ghi cho bất kỳ thành viên nào (đang bật theo dõi = mỗi ~10s, TrackingConstants
        // .LOCATION_INTERVAL_MS) — so `id != selfMemberId` để chỉ (re)khởi động quan sát lộ trình
        // đúng MỘT lần khi self LẦN ĐẦU được biết, không phải mỗi lần vị trí cập nhật (nếu không,
        // `SelectDay` bấm nhanh 3 ngày liên tiếp — Todo phase-08 — sẽ bị một job route KHÁC hủy
        // ngang do vị trí vừa cập nhật, không liên quan gì tới việc chọn ngày).
        observeMembersWithLastLocation().collectSafely { locations ->
            val self = locations.firstOrNull { it.member.isSelf } ?: return@collectSafely
            if (self.member.id != selfMemberId) {
                selfMemberId = self.member.id
                setState { copy(memberColorArgb = self.member.colorArgb) }
                observeRoute(currentState.selectedDay)
            }
        }
    }

    override fun onIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.SelectDay -> onSelectDay(intent.day)
            is HistoryIntent.SelectSession -> setState { copy(selectedSessionId = intent.id) }
            HistoryIntent.StartSimulation -> onStartSimulation()
        }
    }

    /** US-33, phase-09 Implementation Step 6. `isSimulating` is set to `true` synchronously
     * before `launchSafely` even schedules its coroutine body — this is the re-entry guard
     * (Risk Assessment), and it is why the flag is already visible the instant `onIntent`
     * returns, not one dispatcher hop later. Lowered on EVERY exit path: the `AppResult.Failure`
     * branch below, AND `onError` (MVI doc §1 "onError must lower every flag the call raised") —
     * missing either one strands the button disabled forever. New polyline/session data arrives
     * on its own through the `observeRoute` flow already running in `init`; nothing is copied
     * here (MVI doc §2 "Derive, don't duplicate"). */
    private fun onStartSimulation() {
        if (currentState.isSimulating) return
        setState { copy(isSimulating = true) }
        launchSafely(onError = {
            setState { copy(isSimulating = false) }
            sendEffect(HistoryEffect.ShowError(it))
        }) {
            when (val result = startSimulation()) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> sendEffect(HistoryEffect.ShowError(result.error))
            }
            setState { copy(isSimulating = false) }
        }
    }

    /** US-27: chọn ngày huỷ-và-thay job quan sát cũ (MVI doc §3 "cancel and replace") — bấm nhanh
     * 3 ngày liên tiếp thì `routeObservationJob?.cancel()` trong [observeRoute] luôn chạy TRƯỚC khi
     * mở job mới, nên kết quả cuối cùng luôn phản ánh ĐÚNG ngày được bấm sau cùng, bất kể tốc độ
     * Room trả dữ liệu về cho từng ngày. */
    private fun onSelectDay(day: LocalDate) {
        if (day == currentState.selectedDay) return
        setState { copy(selectedDay = day, sessions = emptyList(), selectedSessionId = null, isLoading = true) }
        observeRoute(day)
    }

    private fun observeRoute(day: LocalDate) {
        val memberId = selfMemberId ?: return // self chưa biết -> quan sát lại ngay khi biết (init)
        routeObservationJob?.cancel()
        setState { copy(isLoading = true) }
        routeObservationJob = observeRouteForDay(memberId, day).collectSafely(
            onError = {
                setState { copy(isLoading = false) }
                sendEffect(HistoryEffect.ShowError(it))
            },
            onEach = ::applySessions,
        )
    }

    /** US-30: chuyến DÀI NHẤT được chọn mặc định, không phải chuyến đầu tiên (phase file
     * Implementation Step 4) — mở màn lên thấy ngay thứ đáng nhìn. Giữ nguyên lựa chọn hiện tại
     * nếu nó vẫn còn tồn tại trong danh sách mới (vd. có điểm mới ghi thêm vào chuyến đang xem khi
     * đang bật theo dõi), chỉ chọn lại khi lựa chọn cũ không còn (đổi ngày, hoặc chuyến đó biến mất). */
    private fun applySessions(sessions: List<TrackSession>) {
        setState {
            val keepsCurrentSelection = sessions.any { it.id == selectedSessionId }
            val defaultId = sessions.maxByOrNull { it.distanceMeters }?.id
            copy(
                sessions = sessions,
                selectedSessionId = if (keepsCurrentSelection) selectedSessionId else defaultId,
                isLoading = false,
            )
        }
    }

}

/** `epochDay == null` (US-27 "mặc định là hôm nay") -> `LocalDate.now()`. Route args là initial
 * state, không `setState` một khung sau (MVI doc §3 luật 5). */
internal fun initialStateFrom(savedStateHandle: SavedStateHandle): HistoryState {
    val epochDay = savedStateHandle.get<Long?>(ARG_EPOCH_DAY)
    val day = epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    return HistoryState(selectedDay = day)
}
