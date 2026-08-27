package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants

/**
 * Chặn ở [TrackingConstants.MAX_ZONES] TRƯỚC khi chạm repository (US-21, researcher-01 §1.2) —
 * giới hạn cứng của Play Services, không đợi API ném lỗi (LLM.md §9).
 *
 * **phase-06 fix cho LLM.md §13 (trước là Open #4):** chỉ chặn khi đây thật sự là zone MỚI —
 * `zoneRepository.exists(zone.id)` cho biết "tạo" hay "sửa" trước khi đếm. Thiếu bước này, một
 * người dùng có ĐÚNG 100 zone mở một zone có sẵn, đổi tên, bấm Lưu sẽ bị từ chối sai (kịch bản
 * hỏng thật đã ghi trong `phase-06-zone-list-and-editor.md`) — sửa tên/bán kính một zone đã tồn
 * tại không hề làm tổng số zone tăng lên, nên không có lý do để nó cạnh tranh giới hạn 100.
 *
 * **fix-zone-follows-members: bỏ `GeofenceGateway`.** Zone không còn được đăng ký với Play
 * Services Geofencing API — API đó chỉ bắn transition cho THIẾT BỊ đang chạy app, tức là chỉ cho
 * self, mà chủ thể của zone giờ là các thành viên được theo dõi (xem LLM.md §8.1). Giới hạn
 * [TrackingConstants.MAX_ZONES] ở trên vẫn giữ: nó là hằng số PRD §6 mà QA đọc, và không có lý do
 * để một demo cần hơn 100 zone.
 */
class SaveZoneUseCase(
    private val zoneRepository: ZoneRepository,
) {
    suspend operator fun invoke(zone: Zone): AppResult<Zone> {
        val isUpdate = zoneRepository.exists(zone.id)
        if (!isUpdate && zoneRepository.count() >= TrackingConstants.MAX_ZONES) {
            return AppResult.Failure(
                AppError.Validation("Android giới hạn ${TrackingConstants.MAX_ZONES} zone cho mỗi ứng dụng"),
            )
        }
        return zoneRepository.save(zone)
    }
}
