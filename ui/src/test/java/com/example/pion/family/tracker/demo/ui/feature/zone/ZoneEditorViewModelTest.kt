package com.example.pion.family.tracker.demo.ui.feature.zone

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * MVI doc §7. `initialStateFrom(SavedStateHandle)` — [ZoneEditorViewModel.kt] cố ý dùng
 * `get<T>(KEY)` thay vì `toRoute<ZoneEditorRoute>()` (xem KDoc ở `Routes.kt`), nên
 * `SavedStateHandle(mapOf(...))` dựng tay ở đây chạy được thẳng trên JVM — đã xác nhận bằng test
 * thăm dò trước khi viết ViewModel này (không phải giả định).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZoneEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)

    private fun handle(zoneId: String? = null, lat: Double? = null, lng: Double? = null) =
        SavedStateHandle(mapOf("zoneId" to zoneId, "lat" to lat, "lng" to lng))

    private fun viewModel(
        savedStateHandle: SavedStateHandle = handle(),
        zones: List<Zone> = emptyList(),
        selfPoint: LocationPoint? = null,
        zoneRepository: ZoneRepository = EditorFakeZoneRepository(zones),
    ): ZoneEditorViewModel {
        val memberRepository = EditorFakeMemberRepository(listOf(self), selfPoint?.let { mapOf(self.id to it) } ?: emptyMap())
        return ZoneEditorViewModel(
            savedStateHandle = savedStateHandle,
            observeZones = ObserveZonesUseCase(zoneRepository),
            observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
            saveZoneUseCase = SaveZoneUseCase(zoneRepository),
        )
    }

    // --- initialStateFrom (route args -> initial state), MVI doc §3 luật 5 ---

    @Test
    fun `initialStateFrom with route coordinates centers immediately, create mode`() {
        val state = initialStateFrom(handle(lat = 10.77, lng = 106.70))
        assertEquals(10.77, state.centerLat, 0.0)
        assertEquals(106.70, state.centerLng, 0.0)
        assertTrue(state.hasCenteredOnce)
        assertTrue(state.isLoaded) // tạo mới -> không cần chờ tải gì
        assertFalse(state.isEditing)
    }

    @Test
    fun `initialStateFrom with a zoneId is edit mode, not centered or loaded yet`() {
        val state = initialStateFrom(handle(zoneId = "z-1"))
        assertTrue(state.isEditing)
        assertFalse(state.isLoaded)
        assertFalse(state.hasCenteredOnce)
    }

    @Test
    fun `initialStateFrom with neither zoneId nor coordinates waits to be seeded from self`() {
        val state = initialStateFrom(handle())
        assertFalse(state.isEditing)
        assertTrue(state.isLoaded)
        assertFalse(state.hasCenteredOnce)
    }

    // --- create from empty state (US-15), self-location fallback ---

    @Test
    fun `create mode with no route coordinates seeds the center from self's last location once`() = runTest(dispatcher) {
        val vm = viewModel(selfPoint = pointAt(10.78, 106.69))
        advanceUntilIdle()

        assertTrue(vm.state.value.hasCenteredOnce)
        assertEquals(10.78, vm.state.value.centerLat, 0.0)
        assertEquals(106.69, vm.state.value.centerLng, 0.0)
    }

    /** Bug xác nhận trên máy: mở Editor từ nút "Tạo zone" khi self CHƯA từng bật theo dõi →
     * `observeMembersWithLastLocation()` vẫn phát (do `DemoDataSeeder` luôn có ≥1 member khác kèm
     * toạ độ), nhưng nhánh cũ chỉ đọc `self.lastLocation` nên bỏ qua điểm đó → camera đứng ở (0,0)
     * mãi mãi. Cùng thứ tự ưu tiên `MapState.initialCameraTarget` đã dùng ở phase-05: self trước,
     * rồi RƠI VỀ vị trí của một thành viên bất kỳ. */
    @Test
    fun `create mode with no self location falls back to another member's location, never (0,0)`() = runTest(dispatcher) {
        val otherMember = Member(id = "m-2", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
        val memberRepository = EditorFakeMemberRepository(
            members = listOf(self, otherMember),
            locations = mapOf(otherMember.id to pointAt(10.78, 106.69)), // self has no entry -> no self location
        )
        val vm = ZoneEditorViewModel(
            savedStateHandle = handle(),
            observeZones = ObserveZonesUseCase(EditorFakeZoneRepository(emptyList())),
            observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
            saveZoneUseCase = SaveZoneUseCase(EditorFakeZoneRepository(emptyList())),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.hasCenteredOnce)
        assertEquals(10.78, vm.state.value.centerLat, 0.0)
        assertEquals(106.69, vm.state.value.centerLng, 0.0)
    }

    @Test
    fun `create mode with no location anywhere never fakes a center, stays uncentered`() = runTest(dispatcher) {
        val vm = viewModel(selfPoint = null)
        advanceUntilIdle()

        // No member has ever recorded a point (edge case beyond this fix's scope, same as
        // MapState.initialCameraTarget) -- must stay honestly uncentered, not silently claim (0,0).
        assertFalse(vm.state.value.hasCenteredOnce)
    }

    // --- edit mode load (US-13) ---

    @Test
    fun `edit mode hydrates all fields, including the original createdAt, from the matching zone`() = runTest(dispatcher) {
        val createdAt = Instant.parse("2020-01-01T00:00:00Z")
        val existing = zoneOf("z-1").copy(name = "Nhà", radiusMeters = 300f, notifyOnEnter = false, createdAt = createdAt)
        val vm = viewModel(savedStateHandle = handle(zoneId = "z-1"), zones = listOf(existing))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Nhà", state.name)
        assertEquals(300f, state.radiusMeters, 0f)
        assertFalse(state.notifyOnEnter)
        assertEquals(createdAt, state.createdAt)
        assertTrue(state.isLoaded)
        assertTrue(state.hasCenteredOnce)
    }

    // --- reducers ---

    @Test
    fun `NameChanged, RadiusChanged, ColorSelected, CenterMoved and notify toggles update their own field only`() = runTest(dispatcher) {
        val vm = viewModel(savedStateHandle = handle(lat = 10.0, lng = 106.0))
        advanceUntilIdle()

        vm.onIntent(ZoneEditorIntent.NameChanged("Nhà"))
        vm.onIntent(ZoneEditorIntent.RadiusChanged(400f))
        vm.onIntent(ZoneEditorIntent.CenterMoved(11.0, 107.0))
        vm.onIntent(ZoneEditorIntent.ColorSelected(0xFF00838F.toInt()))
        vm.onIntent(ZoneEditorIntent.NotifyOnEnterToggled(false))
        vm.onIntent(ZoneEditorIntent.NotifyOnExitToggled(false))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Nhà", state.name)
        assertEquals(400f, state.radiusMeters, 0f)
        assertEquals(11.0, state.centerLat, 0.0)
        assertEquals(107.0, state.centerLng, 0.0)
        assertEquals(0xFF00838F.toInt(), state.colorArgb)
        assertFalse(state.notifyOnEnter)
        assertFalse(state.notifyOnExit)
    }

    @Test
    fun `an empty or blank name makes canSave false`() = runTest(dispatcher) {
        val vm = viewModel(savedStateHandle = handle(lat = 10.0, lng = 106.0))
        advanceUntilIdle()
        assertFalse(vm.state.value.canSave)

        vm.onIntent(ZoneEditorIntent.NameChanged("   "))
        advanceUntilIdle()
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `low radius shows the warning, at or above 100m does not`() = runTest(dispatcher) {
        val vm = viewModel(savedStateHandle = handle(lat = 10.0, lng = 106.0))
        advanceUntilIdle()

        vm.onIntent(ZoneEditorIntent.RadiusChanged(80f))
        advanceUntilIdle()
        assertTrue(vm.state.value.showLowRadiusWarning)

        vm.onIntent(ZoneEditorIntent.RadiusChanged(100f))
        advanceUntilIdle()
        assertFalse(vm.state.value.showLowRadiusWarning)
    }

    // --- US-21: the phase-06 fix, locked both directions at the ViewModel/canSave level too ---

    @Test
    fun `creating at MAX_ZONES shows the limit warning and disables Save`() = runTest(dispatcher) {
        val existing = (1..TrackingConstants.MAX_ZONES).map { zoneOf("z-$it") }
        val vm = viewModel(savedStateHandle = handle(lat = 10.0, lng = 106.0), zones = existing)
        vm.onIntent(ZoneEditorIntent.NameChanged("Zone mới"))
        advanceUntilIdle()

        assertTrue(vm.state.value.showZoneLimitWarning)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `editing an existing zone at MAX_ZONES is NOT blocked by the limit warning`() = runTest(dispatcher) {
        val existing = (1..TrackingConstants.MAX_ZONES).map { zoneOf("z-$it") }
        val vm = viewModel(savedStateHandle = handle(zoneId = "z-1"), zones = existing)
        advanceUntilIdle()

        assertFalse(vm.state.value.showZoneLimitWarning)
        assertTrue(vm.state.value.canSave) // đã có tên hợp lệ "Zone z-1" nạp từ zone có sẵn
    }

    // --- save flow ---

    @Test
    fun `SaveTapped on success sends NavigateBack and does not copy the zone into state (Room is truth)`() = runTest(dispatcher) {
        val repository = EditorFakeZoneRepository(emptyList())
        val vm = viewModel(savedStateHandle = handle(lat = 10.0, lng = 106.0), zoneRepository = repository)
        vm.onIntent(ZoneEditorIntent.NameChanged("Nhà"))
        advanceUntilIdle()

        vm.onIntent(ZoneEditorIntent.SaveTapped)
        advanceUntilIdle()

        assertEquals(1, repository.saveCallCount)
        vm.effects.test { assertTrue(awaitItem() is ZoneEditorEffect.NavigateBack) }
    }

    @Test
    fun `SaveTapped while canSave is false never reaches the repository`() = runTest(dispatcher) {
        val repository = EditorFakeZoneRepository(emptyList())
        val vm = viewModel(savedStateHandle = handle(lat = 10.0, lng = 106.0), zoneRepository = repository)
        // Tên vẫn trống -> canSave == false.
        vm.onIntent(ZoneEditorIntent.SaveTapped)
        advanceUntilIdle()

        assertEquals(0, repository.saveCallCount)
    }

    @Test
    fun `a failing save lowers isSaving and a retry still reaches the repository`() = runTest(dispatcher) {
        val repository = EditorFakeZoneRepository(emptyList(), throwOnSave = IllegalStateException("boom"))
        val vm = viewModel(savedStateHandle = handle(lat = 10.0, lng = 106.0), zoneRepository = repository)
        vm.onIntent(ZoneEditorIntent.NameChanged("Nhà"))
        advanceUntilIdle()

        vm.onIntent(ZoneEditorIntent.SaveTapped)
        advanceUntilIdle()
        assertFalse("onError must lower isSaving too — MVI doc §1", vm.state.value.isSaving)
        vm.effects.test { assertTrue(awaitItem() is ZoneEditorEffect.ShowMessage) }

        repository.throwOnSave = null
        vm.onIntent(ZoneEditorIntent.SaveTapped)
        advanceUntilIdle()
        assertEquals(1, repository.saveCallCount)
    }

    private fun zoneOf(id: String) = Zone(
        id = id,
        name = "Zone $id",
        latitude = 10.0,
        longitude = 106.0,
        radiusMeters = 150f,
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

private class EditorFakeZoneRepository(
    initialZones: List<Zone>,
    var throwOnSave: Throwable? = null,
) : ZoneRepository {
    private val flow = MutableStateFlow(initialZones)
    var saveCallCount = 0
        private set

    override fun observeAll(): Flow<List<Zone>> = flow.asStateFlow()

    override suspend fun save(zone: Zone): AppResult<Zone> {
        throwOnSave?.let { throw it }
        saveCallCount++
        flow.value = flow.value.filterNot { it.id == zone.id } + zone
        return AppResult.Success(zone)
    }

    override suspend fun delete(zoneId: String): AppResult<Unit> {
        flow.value = flow.value.filterNot { it.id == zoneId }
        return AppResult.Success(Unit)
    }

    override suspend fun count(): Int = flow.value.size
    override suspend fun exists(zoneId: String): Boolean = flow.value.any { it.id == zoneId }
}


private class EditorFakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
