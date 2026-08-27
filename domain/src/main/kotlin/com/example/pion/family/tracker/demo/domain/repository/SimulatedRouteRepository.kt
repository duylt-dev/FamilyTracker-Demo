package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.RouteSourceInfo
import kotlinx.coroutines.flow.Flow

/**
 * Cổng `:ui` (phase-05) đọc để hiện dải ghi công của tuyến các thành viên mô phỏng đang bám — KHÔNG
 * phải cổng lấy tuyến (đó là [MemberRouteProvider]). `MemberRouteSource` (`:data/routing/`)
 * implement CẢ HAI interface trên CÙNG một instance Koin (D5 Architecture, `decisions.md` §C2): nó
 * là nơi DUY NHẤT biết tuyến hiện tại tới từ tầng nào, nên tách ra hai lớp sẽ cần một kênh đồng bộ
 * giữa chúng (một `MutableStateFlow` đi vòng) — đúng loại phức tạp không mua được gì.
 */
interface SimulatedRouteRepository {
    fun observeSource(): Flow<RouteSourceInfo>
}
