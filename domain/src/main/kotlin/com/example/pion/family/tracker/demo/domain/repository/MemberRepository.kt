package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import kotlinx.coroutines.flow.Flow

/**
 * Not declared in PRD §8 (which only lists 4 interfaces) but required by LLM.md §3's 5-interface
 * domain layout and phase-02 Key Insight #1: US-08 needs each member's last-known position,
 * derived via `MAX(recordedAt)` per member rather than a coordinate column on `members`.
 */
interface MemberRepository {
    fun observeAll(): Flow<List<Member>>

    /** Keyed by memberId — one entry per member with at least one recorded point. */
    fun observeLatestLocations(): Flow<Map<String, LocationPoint>>

    /**
     * Ghi một điểm cho MỘT thành viên bất kỳ. Đối xứng với [observeLatestLocations] và tách hẳn
     * khỏi `TrackingRepository.record(point)` — hàm kia cố định gán điểm cho self (chữ ký PRD §8
     * không có `memberId`, vì GPS thật chỉ bao giờ thuộc về thiết bị này). Nơi gọi duy nhất là
     * `MemberMovementSimulator` (`:data`), nguồn di chuyển của các thành viên được theo dõi.
     */
    suspend fun recordLocation(memberId: String, point: LocationPoint)
}
