package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository

/**
 * US-14 — xoá zone khỏi Room. Không còn bước huỷ đăng ký geofence (fix-zone-follows-members: cả
 * đường phát hiện qua Play Services đã bị gỡ, LLM.md §8.1), nên cũng không còn khả năng để lại
 * một geofence mồ côi vẫn bắn cho zone đã xoá.
 *
 * `MemberMovementSimulator` (`:data`) đọc lại danh sách zone ở MỖI nhịp, nên zone vừa xoá biến khỏi
 * phép đánh giá ngay nhịp kế tiếp — không cần dọn dẹp gì thêm ở đây.
 */
class DeleteZoneUseCase(
    private val zoneRepository: ZoneRepository,
) {
    suspend operator fun invoke(zoneId: String): AppResult<Unit> = zoneRepository.delete(zoneId)
}
