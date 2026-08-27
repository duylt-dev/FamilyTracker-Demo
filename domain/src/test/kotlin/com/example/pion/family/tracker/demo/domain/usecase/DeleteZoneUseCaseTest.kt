package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US-14. Bản phase-07 của file này khoá thứ tự "unregister geofence TRƯỚC khi delete bản ghi";
 * fix-zone-follows-members đã gỡ cả đường Geofencing API (LLM.md §8.1), nên thứ tự đó không còn
 * tồn tại để mà khoá. Còn lại đúng hai điều use case này thật sự hứa: gọi đúng repository với đúng
 * id, và trả nguyên kết quả — kể cả kết quả lỗi — chứ không nuốt nó.
 */
class DeleteZoneUseCaseTest {

    @Test
    fun `deletes through the repository with the given id`() = runTest {
        val repository = RecordingZoneRepository()
        val useCase = DeleteZoneUseCase(repository)

        val result = useCase("z-1")

        assertEquals(listOf("z-1"), repository.deletedIds)
        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `a repository failure reaches the caller unchanged`() = runTest {
        val error = AppError.Unexpected("room down")
        val useCase = DeleteZoneUseCase(RecordingZoneRepository(deleteResult = AppResult.Failure(error)))

        val result = useCase("z-1")

        assertEquals(AppResult.Failure(error), result)
    }
}

private class RecordingZoneRepository(
    private val deleteResult: AppResult<Unit> = AppResult.Success(Unit),
) : ZoneRepository {
    val deletedIds = mutableListOf<String>()

    override fun observeAll(): Flow<List<Zone>> = MutableStateFlow<List<Zone>>(emptyList()).asStateFlow()
    override suspend fun save(zone: Zone): AppResult<Zone> = AppResult.Success(zone)
    override suspend fun delete(zoneId: String): AppResult<Unit> {
        deletedIds += zoneId
        return deleteResult
    }
    override suspend fun count(): Int = 0
    override suspend fun exists(zoneId: String): Boolean = false
}
