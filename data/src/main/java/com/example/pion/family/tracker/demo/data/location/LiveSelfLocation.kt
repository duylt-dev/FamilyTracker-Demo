package com.example.pion.family.tracker.demo.data.location

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cổng hiển thị vị trí thật — phase-01, D4 (`decisions.md` §C3). Một lớp riêng chứ không phải
 * field của `TrackingRepositoryImpl`, vì bên ghi ([LocationPointProcessor]) và bên đọc
 * (`TrackingRepositoryImpl`) là hai `single` Koin khác nhau: một holder tiêm vào cả hai tránh việc
 * [com.example.pion.family.tracker.demo.domain.repository.TrackingRepository] mọc thêm một
 * method GHI chỉ tồn tại cho đúng một người gọi (`LLM.md` §3).
 *
 * **[publish] không đi qua [com.example.pion.family.tracker.demo.domain.tracking.LocationFilter]**
 * — nhận MỌI điểm từ nguồn vị trí thật, kể cả điểm bộ lọc sẽ từ chối ngay sau đó: `MAX_ACCURACY_M`
 * tiếp tục quyết định cái gì vào `location_points` (US-31), không quyết định cái gì lên bản đồ
 * (US-43). Không coroutine, không import Android — hàm thuần trên một `MutableStateFlow`.
 *
 * **Không log gì cả.** Đứng ngay trên đường đi của dữ liệu vị trí thô — log ở đây là log
 * `lat`/`lng` trần, vi phạm PRD §7.3 (gate G7).
 */
class LiveSelfLocation {
    private val _point = MutableStateFlow<LocationPoint?>(null)

    fun publish(point: LocationPoint) {
        _point.value = point
    }

    /** Chỉ đọc — `:ui` không có đường nào ghi vào cổng này (`asStateFlow` như `MviViewModel.state`). */
    fun observe(): StateFlow<LocationPoint?> = _point.asStateFlow()
}
