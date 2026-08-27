package com.example.pion.family.tracker.demo.ui.feature.timeline

import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import com.example.pion.family.tracker.demo.ui.core.mvi.UiEffect
import com.example.pion.family.tracker.demo.ui.core.mvi.UiIntent
import com.example.pion.family.tracker.demo.ui.core.mvi.UiState
import java.time.Instant

/**
 * US-34→US-36 (F4). Nhật ký sự kiện vào/rời zone, nhóm theo ngày, mới nhất trước.
 *
 * [days] đã được nhóm + cắt số lượng ở [TimelineViewModel] (phase file Key Insight #4/#5) —
 * `TimelineScreen` chỉ vẽ, không tự nhóm lại theo `occurredAt` (MVI doc §4 "composable không suy
 * ra sự thật nghiệp vụ").
 */
data class TimelineState(
    val days: List<TimelineDay> = emptyList(),
    val isLoading: Boolean = true,
) : UiState {
    /** Cùng luật `HistoryState.isEmptyDay` (phase-08) — chỉ "rỗng" sau khi lần tải đầu đã xong. */
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

/**
 * Một nhóm ngày dưới `stickyHeader` (US-36). [epochDay] tồn tại RIÊNG cho việc keying
 * `LazyListScope.stickyHeader(key = ...)` — Compose bọc key đó qua `SaveableStateHolder`, đòi
 * kiểu lưu được vào `Bundle` (String/Int/Long/Parcelable/…). [label] (sealed interface, `data
 * object`/`data class` thường) KHÔNG thoả điều kiện đó — dùng trực tiếp làm key ném
 * `IllegalArgumentException: Type of the key Today is not supported` ngay khi cuộn tới nhóm đầu
 * tiên (bắt được thật trên `emulator-5554`, không phải suy đoán — xem `TimelineScreen.kt`).
 * `Long` (epoch day, vốn đã duy nhất theo từng nhóm vì `buildDays` gom theo `LocalDate`) là kiểu
 * an toàn nhất cho `Bundle`, nên tách riêng thay vì cố "sửa" `label` cho vừa yêu cầu của Compose.
 */
data class TimelineDay(
    val label: TimelineDayLabel,
    val epochDay: Long,
    val items: List<TimelineItem>,
)

/**
 * Quyết định "hôm nay"/"hôm qua"/ngày cụ thể được tính Ở ĐÂY (ViewModel, so `LocalDate` với
 * [java.time.Clock] tiêm vào — testable), nhưng chuỗi hiển thị "Hôm nay"/"Hôm qua" vẫn phải đọc từ
 * `strings.xml` qua `stringResource` ở composable (ViewModel không được import Compose/Android —
 * MVI doc §9, cùng mẫu `AppError` → `toDisplayMessage(context)` đã dùng ở Map/ZoneList/History).
 * [Dated.formatted] đã là chuỗi cuối (`dd/MM/yyyy`, thuần `java.time`, không cần resource).
 */
sealed interface TimelineDayLabel {
    data object Today : TimelineDayLabel
    data object Yesterday : TimelineDayLabel
    data class Dated(val formatted: String) : TimelineDayLabel
}

/**
 * Trình bày sẵn cho một dòng — [time] giữ nguyên `Instant`, định dạng `HH:mm` ở composable qua
 * `DurationFormat.formatClock` (cùng mẫu `SessionList.kt`, phase-08), không format sẵn thành
 * `String` ở đây.
 *
 * [memberName] `null` nghĩa là sự kiện của chính mình (US-34: "tên thành viên nếu ≠ mình").
 * [epochDay]/[lat]/[lng] chở nguyên cho `TimelineEffect.OpenHistory` khi bấm dòng (US-35) — copy
 * thẳng từ `ZoneEvent.occurredAt`/`latitude`/`longitude`, không tính lại.
 */
data class TimelineItem(
    val eventId: String,
    val zoneName: String,
    val type: ZoneEventType,
    val time: Instant,
    val memberName: String?,
    val epochDay: Long,
    val lat: Double,
    val lng: Double,
)

sealed interface TimelineIntent : UiIntent {
    data class EventTapped(val eventId: String) : TimelineIntent
}

sealed interface TimelineEffect : UiEffect {
    /** US-35 — mở History đúng ngày, canh camera vào đúng vị trí sự kiện. `HistoryRoute` (phase-08)
     * đã có đường ống sẵn (`HistoryEffect.FocusCamera`) chờ đúng effect này. */
    data class OpenHistory(val epochDay: Long, val lat: Double, val lng: Double) : TimelineEffect
}
