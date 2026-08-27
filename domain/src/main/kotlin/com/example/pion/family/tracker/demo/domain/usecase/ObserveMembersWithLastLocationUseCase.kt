package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.MemberLocation
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Map screen (phase-05, US-06/US-08) reads members joined with their last known position through
 * here, never `MemberRepository.observeAll()`/`observeLatestLocations()` directly. Joins the two
 * flows [MemberRepository] already exposes — no new repository method, no `:data` change: both
 * `Flow`s already carry everything this needs, and `combine` is plain `kotlinx-coroutines-core`,
 * available in `:domain` with no Android/Compose dependency (LLM.md §2).
 */
class ObserveMembersWithLastLocationUseCase(
    private val memberRepository: MemberRepository,
) {
    operator fun invoke(): Flow<List<MemberLocation>> =
        combine(memberRepository.observeAll(), memberRepository.observeLatestLocations()) { members, locations ->
            members.map { member -> MemberLocation(member = member, lastLocation = locations[member.id]) }
        }
}
