package com.example.pion.family.tracker.demo.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.pion.family.tracker.demo.data.local.dao.LocationPointDao
import com.example.pion.family.tracker.demo.data.local.dao.MemberDao
import com.example.pion.family.tracker.demo.data.local.mapper.toDomain
import com.example.pion.family.tracker.demo.data.local.mapper.toEntity
import com.example.pion.family.tracker.demo.data.location.LiveSelfLocation
import com.example.pion.family.tracker.demo.data.location.LocationTrackingService
import com.example.pion.family.tracker.demo.data.location.SimulatedLocationSource
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.repository.TrackingRepository
import com.example.pion.family.tracker.demo.domain.tracking.RouteSplitter
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * `isTracking()`/`setTracking()` không giữ cờ riêng — chúng lái/đọc thẳng
 * [LocationTrackingService] (start/stop + `isRunning`). Cờ tự lưu sẽ nói dối khi hệ thống kill
 * service (phase-04 Risk Assessment: "Service bị kill, công tắc vẫn hiện 'đang bật' — UI nói dối").
 * phase-09: [runSimulation] theo cùng mẫu này cho [LocationTrackingService.isSimulating].
 */
class TrackingRepositoryImpl(
    private val locationPointDao: LocationPointDao,
    private val memberDao: MemberDao,
    private val simulatedLocationSource: SimulatedLocationSource,
    private val liveSelfLocation: LiveSelfLocation,
    private val context: Context,
) : TrackingRepository {

    /**
     * fix-phase-08 — `history_query_split` logs the ONE part of the History pipeline
     * `history_rendered.frameMs` (`:ui/HistoryMap.kt`) never covered: the Room query + entity→
     * `:domain` mapping + `RouteSplitter.split`, all of which finish before `session` even reaches
     * the composable (see `reports/fix-phase-08-report.md`). Logged only for the FIRST emission of
     * each fresh collection (i.e. once per day/session selection) — `observeBetween`'s `Flow`
     * re-emits on every new location write while History stays open (~10s cadence while tracking),
     * and those re-emissions aren't part of "time from selecting a day", so they're not logged here.
     */
    override fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>> {
        val zoneId = ZoneId.systemDefault()
        val fromMillis = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val toMillis = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        val collectionStartNanos = System.nanoTime()
        var firstEmissionLogged = false
        return locationPointDao.observeBetween(memberId, fromMillis, toMillis).map { entities ->
            val sessions = RouteSplitter.split(memberId, entities.map { it.toDomain() })
            if (!firstEmissionLogged) {
                firstEmissionLogged = true
                val pipelineMs = (System.nanoTime() - collectionStartNanos) / 1_000_000
                FtdLog.d(
                    TAG,
                    "history_query_split day=$day pointCount=${entities.size} " +
                        "sessionCount=${sessions.size} pipelineMs=$pipelineMs",
                )
            }
            sessions
        }
    }

    // PRD §8 signature takes no memberId: this demo only ever records real GPS for "self".
    override suspend fun record(point: LocationPoint) {
        val selfId = memberDao.getSelf()?.id ?: return
        locationPointDao.insert(point.toEntity(id = UUID.randomUUID().toString(), memberId = selfId))
    }

    override suspend fun purgeOlderThan(days: Int): Int {
        val cutoff = Instant.now().minus(days.toLong(), ChronoUnit.DAYS).toEpochMilli()
        return locationPointDao.deleteOlderThan(cutoff)
    }

    override fun isTracking(): Flow<Boolean> = LocationTrackingService.isRunning

    // phase-01, D4 — chưa qua LocationFilter, xem KDoc ở TrackingRepository.observeLiveSelfLocation.
    override fun observeLiveSelfLocation(): Flow<LocationPoint?> = liveSelfLocation.observe()

    override suspend fun setTracking(enabled: Boolean) {
        FtdLog.d(TAG, "tracking_toggled enabled=$enabled")
        val intent = Intent(context, LocationTrackingService::class.java)
        if (enabled) {
            // Bằng chứng chạy thật (emulator-5554, API 37.1): bật công tắc khi CHƯA cấp
            // ACCESS_FINE_LOCATION crash cả app — `startForeground()` ném `SecurityException:
            // Starting FGS with type location ... requires ... any of [ACCESS_COARSE_LOCATION,
            // ACCESS_FINE_LOCATION]`. Build xanh, chỉ nổ lúc chạy — không tài liệu nào (LLM.md
            // §10, researcher-01) nói rõ điều này áp cho CẢ push permission, không riêng gì
            // foregroundServiceType. Chặn ở đây, không đợi Android ném lỗi (PRD §7.4: từ chối
            // quyền -> app vẫn mở được, không crash).
            if (hasFineLocationPermission()) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                FtdLog.w(TAG, "tracking_toggle_ignored reason=NO_LOCATION_PERMISSION")
            }
        } else {
            context.stopService(intent)
        }
    }

    /**
     * US-33, phase-09 — nạp [fixes] vào [simulatedLocationSource] (singleton chia sẻ với
     * [LocationTrackingService] qua `data/di/DataModule.kt`) rồi đánh thức service bằng
     * `ACTION_SIMULATE`, đúng cổng `Context`/`Intent` mà [setTracking] đã dùng. Chờ
     * [LocationTrackingService.isSimulating] lên rồi xuống thay vì `delay()` cố định — vẫn đúng dù
     * pipeline xử lý chậm hơn nhịp phát thô của lộ trình.
     */
    override suspend fun runSimulation(fixes: List<SimulatedFix>) {
        if (fixes.isEmpty()) return
        if (!hasFineLocationPermission()) {
            FtdLog.w(TAG, "simulation_skipped reason=NO_LOCATION_PERMISSION")
            return
        }
        simulatedLocationSource.load(fixes)
        val intent = Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_SIMULATE)
        ContextCompat.startForegroundService(context, intent)
        LocationTrackingService.isSimulating.first { it }
        LocationTrackingService.isSimulating.first { !it }
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "FTD_EVENT"
    }
}
