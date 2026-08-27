package com.example.pion.family.tracker.demo.data.location

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.repository.LocationSource
import com.example.pion.family.tracker.demo.domain.tracking.FilterResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Foreground service — LLM.md §8.5, phase-04 Architecture. Không vẽ gì, không biết ViewModel nào
 * tồn tại. `startForeground()` gọi NGAY trong `onStartCommand` (researcher-01 §7.6) — nếu không,
 * hệ thống kill service sau vài giây mà không báo gì.
 *
 * Service chạy HAI job độc lập, cả hai là con cấu trúc của [scope]:
 *
 * | Job | Việc | Bị `ACTION_SIMULATE` huỷ? |
 * |---|---|---|
 * | [trackingJob] | GPS thật -> [LocationPointProcessor] -> điểm của self (chấm xanh + Lịch sử) | Có — nguồn thật nhường chỗ cho nguồn giả lập |
 * | [familyJob] | [MemberMovementSimulator] -> vị trí + sự kiện zone của Minh/Lan | **Không** |
 *
 * **[familyJob] phải tách khỏi [trackingJob].** `runSimulation` (nút "Mô phỏng lộ trình" ở tab Lịch
 * sử) gọi `trackingJob?.cancelAndJoin()`; gộp chung một job thì bấm nút đó sẽ giết luôn chuyển động
 * của gia đình, và Zone List/thông báo đứng hình cho tới lần bật lại công tắc.
 */
class LocationTrackingService : Service(), KoinComponent {

    private val fusedLocationSource: LocationSource by inject(named("fused"))
    private val simulatedLocationSource: LocationSource by inject(named("simulated"))
    private val processor: LocationPointProcessor by inject()
    private val memberMovementSimulator: MemberMovementSimulator by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null
    private var familyJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(TrackingNotification.NOTIFICATION_ID, TrackingNotification.build(this))
        _isRunning.value = true
        if (familyJob == null) familyJob = scope.launch { memberMovementSimulator.run() }

        if (intent?.action == ACTION_SIMULATE) {
            val startedJustForSimulation = trackingJob == null
            scope.launch { runSimulation(stopWhenDone = startedJustForSimulation) }
        } else if (trackingJob == null) {
            trackingJob = scope.launch { collectFrom(fusedLocationSource) }
        }
        return START_STICKY
    }

    /**
     * phase-09 US-33 — huỷ job đang collect nguồn thật (con cấu trúc của [scope], không phải
     * field bị huỷ tay từ nơi khác — MVI doc §3), chạy hết nguồn giả lập, rồi khôi phục nguồn
     * thật. [stopWhenDone] tự dừng service nếu nó chỉ được khởi động riêng cho lượt mô phỏng này
     * (Risk Assessment: "Mô phỏng chạy khi công tắc theo dõi đang tắt").
     */
    private suspend fun runSimulation(stopWhenDone: Boolean) {
        trackingJob?.cancelAndJoin()
        trackingJob = null
        _isSimulating.value = true
        FtdLog.d(TAG, "simulation_started")
        val startedAtMs = System.currentTimeMillis()
        try {
            collectFrom(simulatedLocationSource)
        } finally {
            val durationMs = System.currentTimeMillis() - startedAtMs
            // Không log lat/lng ở đây (gate G7) — chỉ durationMs, đúng PRD §10. `eventsRaised` bỏ
            // đi cùng lúc với việc self thôi sinh sự kiện zone (LLM.md §8.1): lượt mô phỏng này
            // vẽ một chuyến đi cho màn Lịch sử, nó không còn sinh sự kiện nào để đếm.
            FtdLog.d(TAG, "simulation_finished durationMs=$durationMs")
            if (stopWhenDone) {
                stopSelf()
            } else {
                trackingJob = scope.launch { collectFrom(fusedLocationSource) }
            }
            // Hạ cờ SAU KHI đã quyết định dừng/khôi phục — TrackingRepositoryImpl đang `first { !it }`
            // chờ đúng cờ này để biết lượt mô phỏng đã thật sự xong hẳn.
            _isSimulating.value = false
        }
    }

    private suspend fun collectFrom(source: LocationSource) {
        source.stream()
            .catch { throwable -> FtdLog.e(TAG, "tracking_loop_error", throwable) }
            .collect { point ->
                when (val result = processor.process(point)) {
                    // Không log lat/lng — chỉ accuracy + cờ filtered (PRD §7.3, gate G7).
                    FilterResult.Accept ->
                        FtdLog.d(TAG, "location_recorded accuracy=${point.accuracyMeters} filtered=false")
                    is FilterResult.Reject ->
                        FtdLog.d(TAG, "location_dropped reason=${result.reason}")
                }
            }
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        familyJob?.cancel()
        scope.cancel()
        _isRunning.value = false
        _isSimulating.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FTD_EVENT"
        const val ACTION_STOP = "com.example.pion.family.tracker.demo.action.STOP_TRACKING"
        const val ACTION_SIMULATE = "com.example.pion.family.tracker.demo.action.SIMULATE_ROUTE"

        private val _isRunning = MutableStateFlow(false)

        /**
         * Nguồn sự thật cho `TrackingRepositoryImpl.isTracking()` (LLM.md §6, phase-04 Risk
         * Assessment: "isTracking() đọc trạng thái service thật, không đọc cờ đã lưu"). Cùng
         * process, một `companion` `StateFlow` là đủ — không cần IPC.
         */
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _isSimulating = MutableStateFlow(false)

        /** phase-09 US-33 — `TrackingRepositoryImpl.runSimulation()` chờ cờ này lên rồi xuống để
         * biết khi nào mô phỏng THẬT SỰ xong. Việc chờ này độc lập với `viewModelScope`: coroutine
         * đang `first { }` ở `:ui`/`:data` có thể bị huỷ nếu người dùng rời màn hình, nhưng vòng
         * lặp thật trong [scope] (con của Service, không phải của ViewModel) vẫn chạy tới hết. */
        val isSimulating: StateFlow<Boolean> = _isSimulating
    }
}
