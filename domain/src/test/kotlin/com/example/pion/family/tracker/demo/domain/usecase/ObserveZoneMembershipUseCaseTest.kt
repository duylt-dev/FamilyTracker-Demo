package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * US-12 — "ai đang ở trong zone nào", tính cho các thành viên ĐƯỢC THEO DÕI, dùng lại `ZoneEvaluator`.
 *
 * Test thứ hai (`self standing inside a zone is never reported`) là hồi quy cho chính lỗi đã sinh
 * ra fix-zone-follows-members: bản đầu tiên tính cho self, nên khoanh một zone quanh chỗ mình đang
 * đứng là Zone List báo "Đang ở trong" ngay — sai chủ thể của cả màn hình.
 */
class ObserveZoneMembershipUseCaseTest {

    private val self = Member(id = "m-self", name = "Tôi", colorArgb = 0xFF1B6EF3.toInt(), isSelf = true)
    private val minh = Member(id = "m-minh", name = "Minh", colorArgb = 0xFFE5820C.toInt(), isSelf = false)
    private val lan = Member(id = "m-lan", name = "Lan", colorArgb = 0xFF7B3FF2.toInt(), isSelf = false)

    private val nha = zoneOf("z-nha", lat = 10.7769, lng = 106.7009, radius = 500f)
    private val truong = zoneOf("z-truong", lat = 10.7820, lng = 106.6950, radius = 300f)

    @Test
    fun `a followed member inside a zone is reported under that zone`() = runTest {
        val useCase = useCase(
            zones = listOf(nha, truong),
            members = listOf(self, minh),
            locations = mapOf(minh.id to pointAt(10.7769, 106.7009)), // đúng tâm "Nhà"
        )

        assertEquals(mapOf(nha.id to listOf(minh)), useCase().first())
    }

    @Test
    fun `self standing inside a zone is never reported`() = runTest {
        val centreOfNha = pointAt(10.7769, 106.7009)
        val useCase = useCase(
            zones = listOf(nha),
            members = listOf(self, minh),
            // Self đứng giữa zone; Minh ở Hà Nội, xa mọi zone.
            locations = mapOf(self.id to centreOfNha, minh.id to pointAt(21.0, 105.8)),
        )

        assertEquals(emptyMap<String, List<Member>>(), useCase().first())
    }

    @Test
    fun `two followed members in the same zone are both reported, in member order`() = runTest {
        val centreOfNha = pointAt(10.7769, 106.7009)
        val useCase = useCase(
            zones = listOf(nha),
            members = listOf(self, minh, lan),
            locations = mapOf(minh.id to centreOfNha, lan.id to centreOfNha),
        )

        assertEquals(mapOf(nha.id to listOf(minh, lan)), useCase().first())
    }

    @Test
    fun `a member inside two overlapping zones is reported under both`() = runTest {
        val wide = zoneOf("z-wide", lat = 10.7769, lng = 106.7009, radius = 2_000f)
        val useCase = useCase(
            zones = listOf(nha, wide),
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(10.7769, 106.7009)),
        )

        assertEquals(mapOf(nha.id to listOf(minh), wide.id to listOf(minh)), useCase().first())
    }

    @Test
    fun `a followed member outside every zone reports an empty map`() = runTest {
        val useCase = useCase(
            zones = listOf(nha, truong),
            members = listOf(minh),
            locations = mapOf(minh.id to pointAt(21.0, 105.8)), // Hà Nội — xa cả hai zone ở Sài Gòn
        )

        assertEquals(emptyMap<String, List<Member>>(), useCase().first())
    }

    @Test
    fun `a followed member with no recorded location yet is skipped, not a crash`() = runTest {
        val useCase = useCase(zones = listOf(nha), members = listOf(minh), locations = emptyMap())

        assertEquals(emptyMap<String, List<Member>>(), useCase().first())
    }

    private fun useCase(zones: List<Zone>, members: List<Member>, locations: Map<String, LocationPoint>) =
        ObserveZoneMembershipUseCase(
            MembershipFakeZoneRepository(zones),
            MembershipFakeMemberRepository(members, locations),
        )

    private fun zoneOf(id: String, lat: Double, lng: Double, radius: Float) = Zone(
        id = id,
        name = id,
        latitude = lat,
        longitude = lng,
        radiusMeters = radius,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.parse("2026-08-21T00:00:00Z"),
    )

    private fun pointAt(lat: Double, lng: Double) = LocationPoint(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = Instant.parse("2026-08-21T08:00:00Z"),
    )
}

private class MembershipFakeZoneRepository(zones: List<Zone>) : ZoneRepository {
    private val flow = MutableStateFlow(zones).asStateFlow()
    override fun observeAll(): Flow<List<Zone>> = flow
    override suspend fun save(zone: Zone) = AppResult.Success(zone)
    override suspend fun delete(zoneId: String) = AppResult.Success(Unit)
    override suspend fun count(): Int = flow.value.size
    override suspend fun exists(zoneId: String): Boolean = flow.value.any { it.id == zoneId }
}

private class MembershipFakeMemberRepository(
    members: List<Member>,
    locations: Map<String, LocationPoint>,
) : MemberRepository {
    private val membersFlow = MutableStateFlow(members).asStateFlow()
    private val locationsFlow = MutableStateFlow(locations).asStateFlow()
    override fun observeAll(): Flow<List<Member>> = membersFlow
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = locationsFlow
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
