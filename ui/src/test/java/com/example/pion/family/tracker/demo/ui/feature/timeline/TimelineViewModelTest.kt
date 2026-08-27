package com.example.pion.family.tracker.demo.ui.feature.timeline

import app.cash.turbine.test
import com.example.pion.family.tracker.demo.domain.model.EventSource
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneEventRepository
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZoneTimelineUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Phase file Implementation Step 7: nhóm ngày, nhãn "Hôm nay"/"Hôm qua" với `Clock` cố định,
 * effect `OpenHistory` mang đúng `epochDay`, và giới hạn số dòng. `ZoneEvaluator`/`ZoneEventDeduper`
 * đã khoá ở `:domain` (phase-03/07) — file này chỉ xét việc RIÊNG của ViewModel: nhóm, nhãn, cắt,
 * memberName, và điều hướng.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Múi giờ cố định (không phụ thuộc máy chạy CI) — UTC+7, khớp demo (PRD toạ độ HCMC).
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    // "Bây giờ" cố định = 21/08/2026 17:00 giờ địa phương.
    private val fixedNow = Instant.parse("2026-08-21T10:00:00Z")
    private val fixedClock = Clock.fixed(fixedNow, zone)

    private val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
    private val other = Member(id = "m-other", name = "Minh", colorArgb = 0xFF2E7D32.toInt(), isSelf = false)

    private fun eventOf(
        id: String,
        occurredAt: Instant,
        memberId: String = self.id,
        type: ZoneEventType = ZoneEventType.ENTER,
        zoneName: String = "Nhà",
        lat: Double = 10.77,
        lng: Double = 106.70,
    ) = ZoneEvent(
        id = id,
        zoneId = "z-1",
        zoneName = zoneName,
        memberId = memberId,
        type = type,
        occurredAt = occurredAt,
        latitude = lat,
        longitude = lng,
        source = EventSource.FOREGROUND,
    )

    private fun viewModel(
        zoneEventRepository: ZoneEventRepository = TimelineFakeZoneEventRepository(),
        memberRepository: MemberRepository = TimelineFakeMemberRepository(listOf(self, other)),
        clock: Clock = fixedClock,
    ) = TimelineViewModel(
        observeZoneTimeline = ObserveZoneTimelineUseCase(zoneEventRepository),
        observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
        clock = clock,
    )

    // --- US-34 empty state ---

    @Test
    fun `no events yields an empty day list, no crash`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.days.isEmpty())
        assertTrue(vm.state.value.isEmpty)
        assertFalse(vm.state.value.isLoading)
    }

    // --- US-36: 3 sự kiện của 2 ngày -> 2 nhóm, mới nhất trước ---

    @Test
    fun `three events across two days group into two TimelineDay buckets, newest day first`() = runTest(dispatcher) {
        val yesterday = fixedNow.minus(1, ChronoUnit.DAYS)
        val repository = TimelineFakeZoneEventRepository(
            listOf(
                eventOf("e-today-1", fixedNow),
                eventOf("e-today-2", fixedNow.minusSeconds(60)),
                eventOf("e-yesterday-1", yesterday),
            ),
        )
        val vm = viewModel(zoneEventRepository = repository)
        advanceUntilIdle()

        val days = vm.state.value.days
        assertEquals(2, days.size)
        assertEquals(TimelineDayLabel.Today, days[0].label)
        assertEquals(2, days[0].items.size)
        assertEquals(TimelineDayLabel.Yesterday, days[1].label)
        assertEquals(1, days[1].items.size)
    }

    // --- US-36: nhãn "Hôm nay"/"Hôm qua"/dd/MM/yyyy đúng với Clock cố định ---

    @Test
    fun `day label is Today, Yesterday, or a formatted date, decided against the injected Clock`() = runTest(dispatcher) {
        val twoDaysAgo = fixedNow.minus(2, ChronoUnit.DAYS)
        val repository = TimelineFakeZoneEventRepository(
            listOf(eventOf("e-today", fixedNow), eventOf("e-2d", twoDaysAgo)),
        )
        val vm = viewModel(zoneEventRepository = repository)
        advanceUntilIdle()

        val days = vm.state.value.days
        assertEquals(TimelineDayLabel.Today, days[0].label)
        assertEquals(TimelineDayLabel.Dated("19/08/2026"), days[1].label)
    }

    // --- US-34: tên thành viên chỉ hiện khi KHÁC mình ---

    @Test
    fun `memberName is null for self events, populated for other members`() = runTest(dispatcher) {
        val repository = TimelineFakeZoneEventRepository(
            listOf(eventOf("e-self", fixedNow, memberId = self.id), eventOf("e-other", fixedNow, memberId = other.id)),
        )
        val vm = viewModel(zoneEventRepository = repository)
        advanceUntilIdle()

        val items = vm.state.value.days.single().items.associateBy { it.eventId }
        assertNull(items.getValue("e-self").memberName)
        assertEquals(other.name, items.getValue("e-other").memberName)
    }

    // --- US-35: bấm dòng -> Effect.OpenHistory mang đúng epochDay/lat/lng ---

    @Test
    fun `EventTapped sends OpenHistory carrying the tapped item's epochDay, lat, and lng`() = runTest(dispatcher) {
        val repository = TimelineFakeZoneEventRepository(listOf(eventOf("e-1", fixedNow, lat = 10.5, lng = 106.5)))
        val vm = viewModel(zoneEventRepository = repository)
        advanceUntilIdle()
        val expectedEpochDay = fixedNow.atZone(zone).toLocalDate().toEpochDay()

        vm.onIntent(TimelineIntent.EventTapped("e-1"))

        vm.effects.test {
            val effect = awaitItem() as TimelineEffect.OpenHistory
            assertEquals(expectedEpochDay, effect.epochDay)
            assertEquals(10.5, effect.lat, 0.0)
            assertEquals(106.5, effect.lng, 0.0)
        }
    }

    @Test
    fun `EventTapped with an unknown id sends nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(TimelineIntent.EventTapped("does-not-exist"))

        vm.effects.test { expectNoEvents() }
    }

    // --- Key Insight #5: danh sách không giới hạn -> cắt ở ViewModel, không phải LazyColumn ---

    @Test
    fun `500 events in the fake are capped to the ViewModel's display budget`() = runTest(dispatcher) {
        val many = (1..500).map { i -> eventOf("e-$i", fixedNow.minusSeconds(i.toLong())) }
        val repository = TimelineFakeZoneEventRepository(many)
        val vm = viewModel(zoneEventRepository = repository)
        advanceUntilIdle()

        val totalItems = vm.state.value.days.sumOf { it.items.size }
        // 200 == TimelineViewModel's private MAX_VISIBLE_EVENTS — asserted by value since the
        // constant is intentionally private (a UI render budget, not a PRD-numbered threshold).
        assertEquals(200, totalItems)
        // Newest events survive the cut — events are supplied newest-first (Room query order).
        assertTrue(vm.state.value.days.first().items.any { it.eventId == "e-1" })
        assertFalse(vm.state.value.days.first().items.any { it.eventId == "e-500" })
    }

    // --- crash containment (MVI doc §1 point 2) ---

    @Test
    fun `a failing timeline flow lowers isLoading instead of crashing`() = runTest(dispatcher) {
        val vm = viewModel(zoneEventRepository = TimelineThrowingZoneEventRepository())
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.days.isEmpty())
    }
}

private class TimelineFakeZoneEventRepository(initial: List<ZoneEvent> = emptyList()) : ZoneEventRepository {
    private val eventsFlow = MutableStateFlow(initial).asStateFlow()
    override fun observeTimeline(sinceDays: Int): Flow<List<ZoneEvent>> = eventsFlow
    override suspend fun record(event: ZoneEvent) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
}

private class TimelineThrowingZoneEventRepository : ZoneEventRepository {
    override fun observeTimeline(sinceDays: Int): Flow<List<ZoneEvent>> = flow { throw IllegalStateException("boom") }
    override suspend fun record(event: ZoneEvent) = Unit
    override suspend fun purgeOlderThan(days: Int): Int = 0
}

private class TimelineFakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint> = emptyMap(),
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
