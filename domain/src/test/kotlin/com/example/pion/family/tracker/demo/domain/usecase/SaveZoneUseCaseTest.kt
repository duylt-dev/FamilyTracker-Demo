package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** US-21 — chặn ở [TrackingConstants.MAX_ZONES] TRƯỚC khi chạm repository (phase-03 bảng test
 * biên). phase-06 thêm cặp test "khoá cả hai chiều" cho LLM.md §13 (trước là Open #4): tạo mới ở
 * mốc 100 phải bị chặn, NHƯNG sửa một zone đã tồn tại ở đúng mốc 100 phải được cho qua — hai test
 * cuối file này khoá đúng hành vi đó bằng [FakeZoneRepository] biết phân biệt id đã tồn tại hay
 * chưa (không chỉ đếm một con số trơn như bản trước phase-06).
 *
 * fix-zone-follows-members: ba test về đăng ký geofence đã xoá cùng `GeofenceGateway` — zone không
 * còn được đăng ký với Play Services (LLM.md §8.1). `MAX_ZONES` thì giữ nguyên: nó là hằng số PRD
 * §6 mà QA đọc, không phải hệ quả của Play Services. */
class SaveZoneUseCaseTest {

    @Test
    fun `saving a new zone when under the limit delegates to the repository and returns its result`() = runTest {
        val repository = FakeZoneRepository(existingIds = idsUpTo(TrackingConstants.MAX_ZONES - 1))
        val useCase = SaveZoneUseCase(repository)
        val zone = zoneOf("z-new")

        val result = useCase(zone)

        assertEquals(AppResult.Success(zone), result)
        assertEquals(1, repository.saveCallCount)
    }

    @Test
    fun `creating a NEW zone at MAX_ZONES fails validation and never calls the repository, US-21`() = runTest {
        val repository = FakeZoneRepository(existingIds = idsUpTo(TrackingConstants.MAX_ZONES))
        val useCase = SaveZoneUseCase(repository)

        // "z-101" đã tồn tại không nằm trong existingIds -> đây chắc chắn là tạo MỚI.
        val result = useCase(zoneOf("z-101"))

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
        assertEquals(0, repository.saveCallCount)
    }

    @Test
    fun `creating a NEW zone when already over MAX_ZONES also fails, corrupt-store safety net`() = runTest {
        val repository = FakeZoneRepository(existingIds = idsUpTo(TrackingConstants.MAX_ZONES + 1))
        val useCase = SaveZoneUseCase(repository)

        val result = useCase(zoneOf("z-102"))

        assertTrue(result is AppResult.Failure)
        assertEquals(0, repository.saveCallCount)
    }

    @Test
    fun `editing an EXISTING zone at exactly MAX_ZONES still reaches the repository — the phase-06 fix`() = runTest {
        val existingIds = idsUpTo(TrackingConstants.MAX_ZONES)
        val repository = FakeZoneRepository(existingIds = existingIds)
        val useCase = SaveZoneUseCase(repository)
        val editedZone = zoneOf(existingIds.first()) // sửa MỘT zone đã có sẵn, kho vẫn đang đầy 100.

        val result = useCase(editedZone)

        assertEquals(AppResult.Success(editedZone), result)
        assertEquals(1, repository.saveCallCount)
    }

    @Test
    fun `editing an EXISTING zone when the store is even over MAX_ZONES still succeeds`() = runTest {
        val existingIds = idsUpTo(TrackingConstants.MAX_ZONES + 5)
        val repository = FakeZoneRepository(existingIds = existingIds)
        val useCase = SaveZoneUseCase(repository)
        val editedZone = zoneOf(existingIds.last())

        val result = useCase(editedZone)

        assertTrue(result is AppResult.Success)
        assertEquals(1, repository.saveCallCount)
    }

    private fun idsUpTo(count: Int): List<String> = (1..count).map { "existing-$it" }

    private fun zoneOf(id: String) = Zone(
        id = id,
        name = "Zone $id",
        latitude = 21.0,
        longitude = 105.8,
        radiusMeters = 150f,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-21T00:00:00Z"),
    )
}

/** Fake in-memory [ZoneRepository] — theo dõi ID đã lưu (không chỉ một con số đếm trơn) để test
 * phân biệt được "tạo mới" với "sửa", đúng thứ [SaveZoneUseCase] cần kiểm sau phase-06. */
private class FakeZoneRepository(existingIds: List<String>) : ZoneRepository {
    private val storedIds = existingIds.toMutableSet()
    var saveCallCount = 0
        private set

    override fun observeAll(): Flow<List<Zone>> = MutableStateFlow<List<Zone>>(emptyList()).asStateFlow()

    override suspend fun save(zone: Zone): AppResult<Zone> {
        saveCallCount++
        storedIds += zone.id
        return AppResult.Success(zone)
    }

    override suspend fun delete(zoneId: String): AppResult<Unit> {
        storedIds -= zoneId
        return AppResult.Success(Unit)
    }

    override suspend fun count(): Int = storedIds.size

    override suspend fun exists(zoneId: String): Boolean = zoneId in storedIds
}

