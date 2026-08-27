package com.example.pion.family.tracker.demo.data.repository

import com.example.pion.family.tracker.demo.data.local.dao.LocationPointDao
import com.example.pion.family.tracker.demo.data.local.dao.MemberDao
import com.example.pion.family.tracker.demo.data.local.mapper.toDomain
import com.example.pion.family.tracker.demo.data.local.mapper.toEntity
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MemberRepositoryImpl(
    private val memberDao: MemberDao,
    private val locationPointDao: LocationPointDao,
) : MemberRepository {

    override fun observeAll(): Flow<List<Member>> =
        memberDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> =
        locationPointDao.latestPerMember().map { entities ->
            entities.associate { it.memberId to it.toDomain() }
        }

    /** Cùng bảng `location_points` với `TrackingRepositoryImpl.record()` — khác đúng một chỗ:
     * `memberId` đến từ tham số thay vì luôn là self. */
    override suspend fun recordLocation(memberId: String, point: LocationPoint) {
        locationPointDao.insert(point.toEntity(id = UUID.randomUUID().toString(), memberId = memberId))
    }
}
