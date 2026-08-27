package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * PRD §8, with two deliberate signature deviations. (1) `purgeOlderThan` returns [Int] (rows
 * deleted), not `Unit` as PRD §8 literally shows — phase-02's own Success Criteria requires
 * logging `FTD_EVENT purge_completed deletedPoints=…`, unrepresentable with `Unit` (LLM.md §13).
 * (2) [runSimulation], added phase-09 and NOT in phase-09's own "Related Code Files" list — a gap
 * in that file, not a silent addition: `StartSimulationUseCase` (`:domain`) has no `Context`/
 * `Intent` to drive `LocationTrackingService` directly (§2), and `:ui` cannot see `:data` either
 * (§2). `TrackingRepository`, already the one interface crossing that boundary for
 * `setTracking`, is the only viable conduit — same reasoning as `ZoneRepository.exists` (§13
 * Fixed #8) and this file's own `purgeOlderThan`. See phase-09 dev report.
 */
interface TrackingRepository {
    fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>>
    suspend fun record(point: LocationPoint)
    suspend fun purgeOlderThan(days: Int): Int
    fun isTracking(): Flow<Boolean>
    suspend fun setTracking(enabled: Boolean)

    /**
     * US-33 — plays [fixes] through the SAME real-tracking pipeline (LLM.md §8.4), suspending
     * until the simulated route has actually finished (not a fixed `delay()` — stays correct
     * even if the pipeline runs slower than the route's raw timing). No-ops (logs, does not
     * throw) if location permission is missing, mirroring [setTracking]'s existing
     * `NO_LOCATION_PERMISSION` guard (LLM.md §13 Fixed #6).
     */
    suspend fun runSimulation(fixes: List<SimulatedFix>)

    /**
     * phase-01, D4 (`decisions.md` §C3) — vị trí thật CHƯA lọc, để VẼ (US-06/US-43), khác hẳn
     * [record]/[observeRoute] (đã qua `LocationFilter`, để GHI — US-31 không đổi). Phát MỌI điểm
     * nhận được từ nguồn vị trí thật, kể cả điểm mà `LocationFilter` sẽ từ chối ngay sau đó (sai số
     * kém). `null` cho tới khi có fix đầu tiên (chưa bật theo dõi).
     *
     * **KHÔNG có thân mặc định, và đó là cả điểm của nó (reviewer phase-01).** Bản đầu khai
     * `= flowOf(null)` để 5 test double khỏi phải sửa. Cái giá đo được: gỡ `override` khỏi
     * `TrackingRepositoryImpl` vẫn `BUILD SUCCESSFUL` và 212/212 test vẫn xanh — chấm xanh im lặng
     * ngừng bám fix trong nhà, đúng khuyết tật P0 mà phase-01 sinh ra để sửa, và không một test nào
     * đỏ. Abstract thì trình biên dịch chỉ mặt đúng chỗ; test double trả `flowOf(null)` tường minh.
     * **Đừng thêm lại default.**
     */
    fun observeLiveSelfLocation(): Flow<LocationPoint?>
}
