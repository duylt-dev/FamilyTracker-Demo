package com.example.pion.family.tracker.demo.data.location

import android.location.Location
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.repository.LocationSource
import com.example.pion.family.tracker.demo.domain.tracking.SimulatedFix
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 * `LocationSource` mô phỏng cho nút "Mô phỏng lộ trình" (F5, US-33) — đăng ký Koin ngay ở
 * phase-04 (qualifier `named("simulated")`) dù thân rỗng tới phase-09, để cửa vào hệ thống chỉ có
 * đúng một cái ngay từ đầu (LLM.md §8.4, phase-04 Key Insight #9). Thân thật ở đây phát
 * `LocationPoint` vào cùng `Flow` đi qua đúng `LocationFilter` -> `ZoneEvaluator` ->
 * `ZoneEventRepository` như [FusedLocationSource] — không có đường riêng cho demo.
 *
 * Koin singleton, chia sẻ với [LocationTrackingService] qua binding cùng instance
 * (`data/di/DataModule.kt`) — [load] nạp lộ trình từ [TrackingRepositoryImpl] (nơi có `Context`/
 * `Intent` để đánh thức service), rồi service tự gọi [stream] qua cổng [LocationSource] như bình
 * thường.
 */
class SimulatedLocationSource : LocationSource {

    @Volatile
    private var fixes: List<SimulatedFix> = emptyList()

    /** Nạp một lộ trình mới TRƯỚC khi service nhận `ACTION_SIMULATE` — xem [TrackingRepositoryImpl.runSimulation]. */
    fun load(fixes: List<SimulatedFix>) {
        this.fixes = fixes
    }

    /**
     * Phát theo `offsetMs` bằng `delay` (Implementation Step 3) — `recordedAt` bám đúng
     * `offsetMs` thật kể từ lúc phát, không phải một giá trị giả "trông chậm hơn" (Key Insight
     * #5): nếu giả thời gian, `RouteSplitter`/`RouteStats` sẽ tính ra một chuyến đi có thời lượng
     * sai lệch với thực tế đã chạy. `accuracyMeters = 8f` — dưới xa `MAX_ACCURACY_M` (50m), không
     * bao giờ bị `LocationFilter` loại vì lý do accuracy. `speedMps` suy từ khoảng cách + thời gian
     * giữa hai điểm liên tiếp (`Location.distanceBetween`, an toàn ở `:data` vì module này CÓ
     * Android — khác `:domain`'s `GeoDistance`, `internal`, không thấy được từ đây).
     */
    override fun stream(): Flow<LocationPoint> = flow {
        val currentFixes = fixes
        if (currentFixes.isEmpty()) return@flow

        val startedAt = Instant.now()
        var elapsedMs = 0L
        var previous: SimulatedFix? = null

        for (fix in currentFixes) {
            val waitMs = fix.offsetMs - elapsedMs
            if (waitMs > 0) delay(waitMs)
            elapsedMs = fix.offsetMs

            emit(
                LocationPoint(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyMeters = SIMULATED_ACCURACY_M,
                    speedMps = previous?.let { speedBetween(it, fix) } ?: 0f,
                    // Nguồn của SELF — chấm xanh trên bản đồ là hình tròn (SelfAccuracyCircle),
                    // không phải marker mũi tên, nên bearing không có gì để xoay. Phase-02 chỉ sửa
                    // `MemberMovementSimulator.kt` (marker mũi tên của Minh/Lan) — decisions.md
                    // "Sai lệch phát hiện trong chính research" #5. Giữ `0f` là cố ý, không phải sót.
                    bearingDegrees = 0f,
                    recordedAt = startedAt.plusMillis(fix.offsetMs),
                ),
            )
            previous = fix
        }
    }

    private fun speedBetween(from: SimulatedFix, to: SimulatedFix): Float {
        val dtSeconds = (to.offsetMs - from.offsetMs) / 1000f
        if (dtSeconds <= 0f) return 0f
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0] / dtSeconds
    }

    private companion object {
        const val SIMULATED_ACCURACY_M = 8f
    }
}
