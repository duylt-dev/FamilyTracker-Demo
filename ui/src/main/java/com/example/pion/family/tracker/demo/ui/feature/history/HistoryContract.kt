package com.example.pion.family.tracker.demo.ui.feature.history

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.tracking.RouteStats
import com.example.pion.family.tracker.demo.ui.core.mvi.UiEffect
import com.example.pion.family.tracker.demo.ui.core.mvi.UiIntent
import com.example.pion.family.tracker.demo.ui.core.mvi.UiState
import java.time.LocalDate

/**
 * US-27→US-32 (F3). Khớp ví dụ PRD §9 với 2 sai lệch có lý do, cả hai theo đúng luật đã lập ở
 * phase-05/06 chứ không phải phát minh mới:
 *
 * 1. **`stats` là `val` TÍNH TOÁN, không phải field lưu trữ như PRD §9 phác thảo.** PRD viết
 *    `val stats: RouteStats? = null` (field cần một `setState` riêng để giữ đồng bộ với
 *    `selectedSessionId`/`sessions`). MVI doc §2 "Derive, don't duplicate": hai field có thể lệch
 *    nhau (`selectedSessionId` đổi mà quên `setState` lại `stats`) là lỗi có thật, một `val get()`
 *    thì không bao giờ lệch — cùng lý do phase-05 giữ `selectedMemberId` bằng id chứ không giữ bản
 *    sao đối tượng.
 * 2. **Thêm [memberColorArgb]** — Polyline (US-28) cần màu của thành viên để vẽ; PRD §9 không liệt
 *    field này vì ví dụ đó chỉ phác thảo phần lõi. Nạp một lần khi biết self member (`HistoryViewModel`),
 *    mặc định `PrimaryBlue` (`Theme.kt`) cho tới khi đó.
 *
 * [selectedSession] tính bằng id (Key Insight #5 của phase file) — giữ đối tượng thì một lần Room
 * re-emit (điểm mới ghi vào khi đang bật theo dõi) để lại bản sao cũ.
 */
data class HistoryState(
    val selectedDay: LocalDate = LocalDate.now(),
    val sessions: List<TrackSession> = emptyList(),
    val selectedSessionId: String? = null,
    val isLoading: Boolean = false,
    val isSimulating: Boolean = false,
    val memberColorArgb: Int = DEFAULT_MEMBER_COLOR_ARGB,
) : UiState {
    val selectedSession: TrackSession? get() = sessions.firstOrNull { it.id == selectedSessionId }
    val stats: RouteStats? get() = selectedSession?.let(RouteStats::of)

    /** US-32 — chỉ đúng nghĩa "trống" sau khi lần tải đầu đã xong, không phải trong lúc đang tải. */
    val isEmptyDay: Boolean get() = !isLoading && sessions.isEmpty()

    private companion object {
        /** `#1B6EF3` — `Theme.kt` `PrimaryBlue`, xem LLM.md §13 Fixed #9. Không import `Theme.kt`
         * trực tiếp (ViewModel/State không phụ thuộc Compose) nên chép lại giá trị ARGB. */
        const val DEFAULT_MEMBER_COLOR_ARGB: Int = 0xFF1B6EF3.toInt()
    }
}

sealed interface HistoryIntent : UiIntent {
    data class SelectDay(val day: LocalDate) : HistoryIntent
    data class SelectSession(val id: String) : HistoryIntent

    /** US-33 (F5), phase-09 — bấm nút "▶ Mô phỏng lộ trình" (`SimulateRouteButton`, chỉ compose
     * khi `BuildConfig.SIMULATOR_ENABLED`). `isSimulating` (đã có trong state từ phase-08) là
     * re-entry guard trong reducer (`HistoryViewModel.onStartSimulation`), không phải ở UI —
     * Risk Assessment "Người dùng bấm nút hai lần → Hai luồng cùng bơm vị trí". */
    data object StartSimulation : HistoryIntent
}

sealed interface HistoryEffect : UiEffect {
    /** Cùng mẫu `MapEffect.ShowError`/`ZoneEditorEffect.ShowMessage` — mang `AppError`, không phải
     * `String` như PRD §9 phác (`ShowError(val message: String)`): ViewModel không build chuỗi
     * hiển thị (MVI doc §5, đã áp dụng nhất quán từ phase-05/06). Giữ tên Intent `ShowError` để
     * không lệch PRD nhiều hơn cần thiết, chỉ đổi kiểu payload. */
    data class ShowError(val error: AppError) : HistoryEffect

    /** US-35 (phase-10) — bấm sự kiện Timeline mở History đúng ngày, canh camera vào đúng vị trí.
     * Bắn đúng MỘT lần trong `init` nếu route mang `focusLat`/`focusLng` (phase-08 Implementation
     * Step 2) — chưa có màn Timeline nào gửi nó ở phase-08, nhưng đường ống đã sẵn sàng. */
    data class FocusCamera(val lat: Double, val lng: Double) : HistoryEffect
}
