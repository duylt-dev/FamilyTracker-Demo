package com.example.pion.family.tracker.demo.ui.feature.timeline

import com.example.pion.family.tracker.demo.domain.model.MemberLocation
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZoneTimelineUseCase
import com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Trần hiển thị thuần UI, KHÔNG phải hằng số nghiệp vụ PRD §6 (`TrackingConstants`) — đây chỉ là
 * ngân sách vẽ `LazyColumn` (Key Insight #5 phase file: "Danh sách có thể tăng không giới hạn...
 * Cắt ở ViewModel"), không có ngưỡng nào PRD quy định. Áp dụng trên danh sách sự kiện ĐÃ sắp mới
 * nhất trước (`ZoneEventDao.observeSince` `ORDER BY occurredAt DESC`) nên luôn giữ N sự kiện MỚI
 * NHẤT, không phải N sự kiện đầu tiên theo thời gian.
 */
private const val MAX_VISIBLE_EVENTS = 200

/**
 * US-34→US-36. `clock` tiêm vào (mặc định hệ thống) để nhãn "Hôm nay"/"Hôm qua" test được xác định
 * (phase file Risk Assessment: "truyền Clock vào ViewModel để test được") — không dùng
 * `LocalDate.now()` trần vì kết quả phụ thuộc đồng hồ thật lúc chạy test.
 *
 * Tái dùng [ObserveMembersWithLastLocationUseCase] (đã đăng ký Koin từ phase-05/08) để biết
 * `memberId` của self và tên các thành viên khác — cùng lý do `HistoryViewModel` không thêm
 * repository method "lấy id self" riêng (KDoc `HistoryViewModel`, brief "tái dùng, đừng phát minh
 * lại"). `combine` (không phải hai `collectSafely` độc lập) vì mỗi lần phát của state cuối cần CẢ
 * HAI nguồn tại cùng thời điểm để tô đúng `memberName` — cùng mẫu `ZoneListViewModel.init`.
 */
class TimelineViewModel(
    observeZoneTimeline: ObserveZoneTimelineUseCase,
    observeMembersWithLastLocation: ObserveMembersWithLastLocationUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) : MviViewModel<TimelineState, TimelineIntent, TimelineEffect>(TimelineState()) {

    init {
        combine(observeZoneTimeline(), observeMembersWithLastLocation()) { events, locations -> events to locations }
            .collectSafely(
                // MVI doc §1 "onError must lower every flag the call raised" — without this,
                // a Room-backed `Flow` throwing (e.g. `SQLiteException`) would strand `isLoading`
                // at its `true` default forever: the screen spins, never shows the empty state or
                // any data, with no crash and no visible signal anything went wrong.
                onError = { setState { copy(isLoading = false) } },
                onEach = { (events, locations) ->
                    setState { copy(days = buildDays(events, locations, clock), isLoading = false) }
                },
            )
    }

    override fun onIntent(intent: TimelineIntent) {
        when (intent) {
            is TimelineIntent.EventTapped -> onEventTapped(intent.eventId)
        }
    }

    /** US-35 — tra lại item đã dựng sẵn trong state (đã mang `epochDay`/`lat`/`lng`), không đọc
     * lại repository: state đã LÀ nguồn sự thật hiển thị (MVI doc §2 "Derive, don't duplicate"). */
    private fun onEventTapped(eventId: String) {
        val item = currentState.days.asSequence().flatMap { it.items }.firstOrNull { it.eventId == eventId }
            ?: return
        sendEffect(TimelineEffect.OpenHistory(item.epochDay, item.lat, item.lng))
    }
}

/** Nhóm theo ngày (múi giờ thiết bị, MỘT chỗ duy nhất qua [clock] — phase file Risk Assessment
 * "Nhóm ngày sai khi thiết bị đổi múi giờ"), cắt [MAX_VISIBLE_EVENTS] TRƯỚC khi nhóm để giữ đúng N
 * sự kiện MỚI NHẤT toàn cục, rồi mới chia nhóm — cắt sau khi nhóm sẽ giữ N ngày đầy đủ thay vì N
 * sự kiện. `groupBy` giữ thứ tự gặp đầu tiên của khoá (`LinkedHashMap`), và vì [events] đã sắp mới
 * nhất trước, nhóm ngày mới nhất luôn đứng trước — không cần sort lại. */
private fun buildDays(events: List<ZoneEvent>, locations: List<MemberLocation>, clock: Clock): List<TimelineDay> {
    val selfMemberId = locations.firstOrNull { it.member.isSelf }?.member?.id
    val memberNameById = locations.associate { it.member.id to it.member.name }
    val zoneId = clock.zone
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1)

    return events.take(MAX_VISIBLE_EVENTS)
        .groupBy { it.occurredAt.atZone(zoneId).toLocalDate() }
        .map { (date, dayEvents) ->
            TimelineDay(
                label = labelFor(date, today, yesterday),
                epochDay = date.toEpochDay(),
                items = dayEvents.map { it.toTimelineItem(date, selfMemberId, memberNameById) },
            )
        }
}

private fun labelFor(date: LocalDate, today: LocalDate, yesterday: LocalDate): TimelineDayLabel = when (date) {
    today -> TimelineDayLabel.Today
    yesterday -> TimelineDayLabel.Yesterday
    else -> TimelineDayLabel.Dated(DAY_FORMATTER.format(date))
}

private fun ZoneEvent.toTimelineItem(
    date: LocalDate,
    selfMemberId: String?,
    memberNameById: Map<String, String>,
) = TimelineItem(
    eventId = id,
    zoneName = zoneName,
    type = type,
    time = occurredAt,
    memberName = if (memberId == selfMemberId) null else memberNameById[memberId],
    epochDay = date.toEpochDay(),
    lat = latitude,
    lng = longitude,
)
