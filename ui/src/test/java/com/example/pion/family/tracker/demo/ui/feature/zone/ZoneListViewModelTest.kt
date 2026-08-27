package com.example.pion.family.tracker.demo.ui.feature.zone

import app.cash.turbine.test
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.usecase.DeleteZoneUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZoneMembershipUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZonesUseCase
import com.example.pion.family.tracker.demo.domain.usecase.SaveZoneUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.time.Instant

/** MVI doc §7 — reducer test mỗi Intent, effect test mỗi Effect, crash-containment, và
 * "retry reaches the repository" cho `DeleteConfirmed`/`NotifyToggled` (phase-06 Implementation
 * Step 11). */
@OptIn(ExperimentalCoroutinesApi::class)
class ZoneListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
    private val minh = Member(id = "m-minh", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)

    private fun viewModel(
        zones: List<Zone> = emptyList(),
        locations: Map<String, LocationPoint> = emptyMap(),
        zoneRepository: ZoneRepository = FakeZoneRepository(zones),
    ): ZoneListViewModel {
        val memberRepository = FakeMemberRepository(listOf(self, minh), locations)
        return ZoneListViewModel(
            observeZones = ObserveZonesUseCase(zoneRepository),
            observeZoneMembership = ObserveZoneMembershipUseCase(zoneRepository, memberRepository),
            saveZoneUseCase = SaveZoneUseCase(zoneRepository),
            deleteZoneUseCase = DeleteZoneUseCase(zoneRepository),
        )
    }

    @Test
    fun `a followed member inside a zone shows up on that zone's row, with their marker colour`() = runTest(dispatcher) {
        val nha = zoneOf("z-nha", lat = 10.7769, lng = 106.7009, radius = 500f)
        val vm = viewModel(zones = listOf(nha), locations = mapOf(minh.id to pointAt(10.7769, 106.7009)))
        advanceUntilIdle()

        val item = vm.state.value.zones.single()
        assertEquals("z-nha", item.id)
        assertEquals(listOf(ZoneMemberChip(minh.id, "Minh", 0xFFE5820C.toInt())), item.membersInside)
        assertFalse(vm.state.value.isLoading)
    }

    /**
     * Hồi quy cho chính lỗi sinh ra fix-zone-follows-members: khoanh một zone quanh chỗ MÌNH đang
     * đứng thì dòng zone phải trống, không phải "Đang ở trong". Zone theo dõi người nhà, không theo
     * dõi mình.
     */
    @Test
    fun `self standing inside a zone leaves the row empty`() = runTest(dispatcher) {
        val nha = zoneOf("z-nha", lat = 10.7769, lng = 106.7009, radius = 500f)
        val vm = viewModel(zones = listOf(nha), locations = mapOf(self.id to pointAt(10.7769, 106.7009)))
        advanceUntilIdle()

        assertEquals(emptyList<ZoneMemberChip>(), vm.state.value.zones.single().membersInside)
    }

    @Test
    fun `an empty zone list after loading is the empty state, not the initial loading state`() = runTest(dispatcher) {
        val vm = viewModel(zones = emptyList())
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun `ZoneTapped sends OpenEditor with the zone's id`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(ZoneListIntent.ZoneTapped("z-1"))
        advanceUntilIdle()

        vm.effects.test {
            val effect = awaitItem()
            assertTrue(effect is ZoneListEffect.OpenEditor)
            assertEquals("z-1", (effect as ZoneListEffect.OpenEditor).zoneId)
        }
    }

    @Test
    fun `CreateTapped sends OpenEditor with a null id`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(ZoneListIntent.CreateTapped)
        advanceUntilIdle()

        vm.effects.test {
            val effect = awaitItem()
            assertTrue(effect is ZoneListEffect.OpenEditor)
            assertNull((effect as ZoneListEffect.OpenEditor).zoneId)
        }
    }

    @Test
    fun `DeleteRequested sets pendingDeleteId, DeleteCancelled clears it without calling the repository`() = runTest(dispatcher) {
        val repository = FakeZoneRepository(listOf(zoneOf("z-1")))
        val vm = viewModel(zones = listOf(zoneOf("z-1")), zoneRepository = repository)
        advanceUntilIdle()

        vm.onIntent(ZoneListIntent.DeleteRequested("z-1"))
        advanceUntilIdle()
        assertEquals("z-1", vm.state.value.pendingDeleteId)

        vm.onIntent(ZoneListIntent.DeleteCancelled)
        advanceUntilIdle()
        assertNull(vm.state.value.pendingDeleteId)
        assertEquals(0, repository.deleteCallCount)
    }

    @Test
    fun `DeleteConfirmed deletes through the repository and clears pendingDeleteId`() = runTest(dispatcher) {
        val repository = FakeZoneRepository(listOf(zoneOf("z-1")))
        val vm = viewModel(zones = listOf(zoneOf("z-1")), zoneRepository = repository)
        advanceUntilIdle()

        vm.onIntent(ZoneListIntent.DeleteRequested("z-1"))
        vm.onIntent(ZoneListIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertEquals(1, repository.deleteCallCount)
        assertNull(vm.state.value.pendingDeleteId)
    }

    @Test
    fun `a failing delete lowers pendingDeleteId and a retry still reaches the repository`() = runTest(dispatcher) {
        val repository = FakeZoneRepository(listOf(zoneOf("z-1")), throwOnDelete = IllegalStateException("boom"))
        val vm = viewModel(zones = listOf(zoneOf("z-1")), zoneRepository = repository)
        advanceUntilIdle()

        vm.onIntent(ZoneListIntent.DeleteRequested("z-1"))
        vm.onIntent(ZoneListIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertNull("onError must lower pendingDeleteId too — MVI doc §1", vm.state.value.pendingDeleteId)
        vm.effects.test { assertTrue(awaitItem() is ZoneListEffect.ShowMessage) }

        repository.throwOnDelete = null
        vm.onIntent(ZoneListIntent.DeleteRequested("z-1"))
        vm.onIntent(ZoneListIntent.DeleteConfirmed)
        advanceUntilIdle()
        assertEquals(1, repository.deleteCallCount)
    }

    @Test
    fun `NotifyToggled saves both flags together and does not assign state directly, Room re-emits`() = runTest(dispatcher) {
        val zone = zoneOf("z-1").copy(notifyOnEnter = false, notifyOnExit = false)
        val repository = FakeZoneRepository(listOf(zone))
        val vm = viewModel(zones = listOf(zone), zoneRepository = repository)
        advanceUntilIdle()
        assertFalse(vm.state.value.zones.single().notifyEnabled)

        vm.onIntent(ZoneListIntent.NotifyToggled("z-1", true))
        advanceUntilIdle()

        assertEquals(1, repository.saveCallCount)
        val saved = repository.lastSaved
        assertTrue(saved != null && saved.notifyOnEnter && saved.notifyOnExit)
        // observeZones() re-emit thay đổi thật (FakeZoneRepository.save cập nhật flow) -> state theo đúng.
        assertTrue(vm.state.value.zones.single().notifyEnabled)
    }

    private fun zoneOf(id: String, lat: Double = 10.0, lng: Double = 106.0, radius: Float = 150f) = Zone(
        id = id,
        name = "Zone $id",
        latitude = lat,
        longitude = lng,
        radiusMeters = radius,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-21T00:00:00Z"),
    )

    private fun pointAt(lat: Double, lng: Double) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-21T08:00:00Z"),
    )
}

/** Fake trong bộ nhớ — có `Flow` thật sự re-emit khi `save`/`delete` để test "Room là nguồn sự
 * thật duy nhất" (MVI doc §3), không chỉ trả về Success rỗng như test SaveZoneUseCase thuần. */
private class FakeZoneRepository(
    initialZones: List<Zone>,
    var throwOnDelete: Throwable? = null,
) : ZoneRepository {
    private val flow = MutableStateFlow(initialZones)
    var saveCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var lastSaved: Zone? = null
        private set

    override fun observeAll(): Flow<List<Zone>> = flow.asStateFlow()

    override suspend fun save(zone: Zone): AppResult<Zone> {
        saveCallCount++
        lastSaved = zone
        flow.value = flow.value.filterNot { it.id == zone.id } + zone
        return AppResult.Success(zone)
    }

    override suspend fun delete(zoneId: String): AppResult<Unit> {
        throwOnDelete?.let { throw it }
        deleteCallCount++
        flow.value = flow.value.filterNot { it.id == zoneId }
        return AppResult.Success(Unit)
    }

    override suspend fun count(): Int = flow.value.size
    override suspend fun exists(zoneId: String): Boolean = flow.value.any { it.id == zoneId }
}


private class FakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
