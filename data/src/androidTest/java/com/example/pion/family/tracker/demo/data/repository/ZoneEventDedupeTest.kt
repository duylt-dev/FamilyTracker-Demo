package com.example.pion.family.tracker.demo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.EventSource
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * US-25 — LLM.md §8.1: events for the same (zoneId, memberId, type) within 60s are deduped.
 *
 * phase-07 cascading fix: `ZoneEventRepositoryImpl` now needs a [ZoneRepository] + [Context] to
 * reach `ZoneNotifier` after a successful record — this test passes an empty-zones fake so
 * `record()`'s notify lookup finds nothing and stays a no-op, keeping this test about dedupe only
 * (notification behaviour itself belongs to `GeofenceRegistrarTest`/manual US-22→US-23 checks).
 */
@RunWith(AndroidJUnit4::class)
class ZoneEventDedupeTest {
    private lateinit var db: FamilyTrackerDatabase
    private lateinit var repository: ZoneEventRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FamilyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ZoneEventRepositoryImpl(db.zoneEventDao(), EmptyZoneRepository(), EmptyMemberRepository(), context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun record_sameKeyWithin60s_isDeduped() = runBlocking {
        val base = Instant.now()
        repository.record(eventOf(base))
        repository.record(eventOf(base.plusSeconds(30)))

        val all = db.zoneEventDao().observeSince(0L).first()
        assertEquals(1, all.size)
    }

    @Test
    fun record_sameKeyAfter60s_isKept() = runBlocking {
        val base = Instant.now()
        repository.record(eventOf(base))
        repository.record(eventOf(base.plusSeconds(90)))

        val all = db.zoneEventDao().observeSince(0L).first()
        assertEquals(2, all.size)
    }

    private fun eventOf(occurredAt: Instant) = ZoneEvent(
        id = UUID.randomUUID().toString(),
        zoneId = "z1",
        zoneName = "Nha",
        memberId = "m1",
        type = ZoneEventType.ENTER,
        occurredAt = occurredAt,
        latitude = 10.77,
        longitude = 106.70,
        source = EventSource.FOREGROUND,
    )
}

/** No zones -> `ZoneEventRepositoryImpl.record()`'s notify lookup always misses, so this dedupe
 * test never actually posts a system notification. */
private class EmptyZoneRepository : ZoneRepository {
    override fun observeAll(): Flow<List<Zone>> = MutableStateFlow<List<Zone>>(emptyList()).asStateFlow()
    override suspend fun save(zone: Zone): AppResult<Zone> = AppResult.Success(zone)
    override suspend fun delete(zoneId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun count(): Int = 0
    override suspend fun exists(zoneId: String): Boolean = false
}

/** Không zone nào -> `record()` không gọi `ZoneNotifier`; test này chỉ quan tâm hàng trong Room. */
private class EmptyMemberRepository : MemberRepository {
    override fun observeAll(): Flow<List<Member>> = flowOf(emptyList())
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = flowOf(emptyMap())
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
