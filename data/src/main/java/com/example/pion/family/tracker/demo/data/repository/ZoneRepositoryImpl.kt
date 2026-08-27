package com.example.pion.family.tracker.demo.data.repository

import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.data.local.dao.ZoneDao
import com.example.pion.family.tracker.demo.data.local.mapper.toDomain
import com.example.pion.family.tracker.demo.data.local.mapper.toEntity
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ZoneRepositoryImpl(
    private val zoneDao: ZoneDao,
) : ZoneRepository {

    override fun observeAll(): Flow<List<Zone>> =
        zoneDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * phase-06 Implementation Step 10: `FTD_EVENT zone_saved` — cùng chỗ/cùng mẫu với
     * `ZoneEventRepositoryImpl.record()` (`zone_event_deduped`), không đặt ở `ZoneEditorViewModel`
     * vì ViewModel không được import `android.util.Log` (MVI doc §9, LLM.md §4 điểm 4). Không log
     * toạ độ — chỉ `zoneId`/`radius`/`totalZones` (PRD §10, gate G7, phase-06 Security Considerations).
     */
    override suspend fun save(zone: Zone): AppResult<Zone> = try {
        zoneDao.upsert(zone.toEntity())
        val totalZones = zoneDao.count()
        FtdLog.d(TAG, "zone_saved zoneId=${zone.id} radius=${zone.radiusMeters} totalZones=$totalZones")
        AppResult.Success(zone)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unexpected(e.message))
    }

    override suspend fun delete(zoneId: String): AppResult<Unit> = try {
        zoneDao.delete(zoneId)
        AppResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unexpected(e.message))
    }

    override suspend fun count(): Int = zoneDao.count()

    override suspend fun exists(zoneId: String): Boolean = zoneDao.exists(zoneId)

    private companion object {
        const val TAG = "FTD_EVENT"
    }
}
