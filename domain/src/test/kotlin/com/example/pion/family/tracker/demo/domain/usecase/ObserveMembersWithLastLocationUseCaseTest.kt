package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Phase-05 (Map, US-06/US-08) — joins `MemberRepository.observeAll()` with
 * `observeLatestLocations()`, both already exposed, no new repository method. */
class ObserveMembersWithLastLocationUseCaseTest {

    @Test
    fun `each member is paired with their last recorded point`() = runTest {
        val self = memberOf("m-self", isSelf = true)
        val minh = memberOf("m-minh")
        val point = LocationPoint(10.0, 106.0, 5f, 0f, 0f, Instant.parse("2026-08-21T08:00:00Z"))
        val repository = FakeMemberRepository(
            members = listOf(self, minh),
            locations = mapOf("m-self" to point),
        )

        val result = ObserveMembersWithLastLocationUseCase(repository)().first()

        assertEquals(2, result.size)
        assertEquals(point, result.first { it.member.id == "m-self" }.lastLocation)
        assertNull(result.first { it.member.id == "m-minh" }.lastLocation)
    }

    @Test
    fun `a member with no recorded point yet is still present, with a null location`() = runTest {
        val repository = FakeMemberRepository(members = listOf(memberOf("m-lan")), locations = emptyMap())

        val result = ObserveMembersWithLastLocationUseCase(repository)().first()

        assertEquals(1, result.size)
        assertNull(result.single().lastLocation)
    }

    private fun memberOf(id: String, isSelf: Boolean = false) =
        Member(id = id, name = id, colorArgb = 0xFF1B6EF3.toInt(), isSelf = isSelf)
}

private class FakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
