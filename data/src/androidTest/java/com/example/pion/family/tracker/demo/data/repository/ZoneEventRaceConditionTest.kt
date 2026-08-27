package com.example.pion.family.tracker.demo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase
import com.example.pion.family.tracker.demo.data.local.dao.ZoneEventDao
import com.example.pion.family.tracker.demo.data.local.entity.ZoneEventEntity
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.EventSource
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
 * Regression test for the TOCTOU race in `ZoneEventRepositoryImpl.record()` found from real
 * device evidence — `plans/260821-1113-geofence-zone-and-history-tracking/reports/
 * fix-phase-07-report.md`. On `emulator-5554` two `Home EXIT` rows landed in `zone_events` 7ms
 * apart, one `source=FOREGROUND` one `source=GEOFENCE_API`, with zero `zone_event_deduped` log
 * lines — proof the two detection paths (`LocationTrackingService` on `Dispatchers.Default`,
 * `GeofenceBroadcastReceiver` on `Dispatchers.IO`, LLM.md §8.1) both read "no recent event" from
 * `ZoneEventDao.latestForKey()` before either had inserted.
 *
 * The real race window (read -> pure decision -> insert) is normally sub-millisecond on an
 * in-memory/WAL database, so two concurrent calls rarely land inside it in a plain unit test even
 * though they clearly do on-device (7-82ms gaps observed, LLM.md §8.1). [DelayingZoneEventDao]
 * widens that exact window with a fixed delay right after the real `latestForKey()` query
 * returns — it does not fake the result (the query answer is the real one), it only makes the
 * existing non-atomic check-then-act deterministic to hit instead of relying on timing luck. The
 * class under test, [ZoneEventRepositoryImpl], is exercised unmodified.
 */
@RunWith(AndroidJUnit4::class)
class ZoneEventRaceConditionTest {
    private lateinit var db: FamilyTrackerDatabase
    private lateinit var repository: ZoneEventRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FamilyTrackerDatabase::class.java).build()
        val delayingDao = DelayingZoneEventDao(db.zoneEventDao())
        repository = ZoneEventRepositoryImpl(delayingDao, RaceTestEmptyZoneRepository(), RaceTestEmptyMemberRepository(), context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Mirrors the real collision: same (zoneId, memberId, type) key, ~7ms apart, two callers on
     * two different dispatchers, launched concurrently via `async` so both `record()` calls are in
     * flight at once.
     *
     * fix-zone-follows-members: hai đường phát hiện thật đã còn một (`GeofenceBroadcastReceiver` bị
     * gỡ), nên hai lời gọi ở đây không còn đại diện cho hai lớp cụ thể nào. Test GIỮ LẠI vì nó khoá
     * hợp đồng của `dedupeMutex`, không phải hình dạng của lớp gọi — xem KDoc
     * `ZoneEventRepositoryImpl`: bỏ mutex đi là đặt lại đúng cái bẫy 7ms cho người thêm nguồn sự
     * kiện thứ hai sau này.
     */
    @Test
    fun record_concurrentCallsSameKey_onlyOneRowIsKept() = runBlocking {
        val base = Instant.now()
        val foregroundEvent = eventOf(occurredAt = base, source = EventSource.FOREGROUND)
        val secondCaller = eventOf(occurredAt = base.plusMillis(7), source = EventSource.FOREGROUND)

        val first = async(Dispatchers.Default) { repository.record(foregroundEvent) }
        val second = async(Dispatchers.IO) { repository.record(secondCaller) }
        awaitAll(first, second)

        val all = db.zoneEventDao().observeSince(0L).first()
        assertEquals(
            "Expected exactly 1 zone_events row for the same (zoneId,memberId,type) key within " +
                "the 60s dedupe window, found ${all.size}: " +
                all.joinToString { "${it.type}/${it.source}@${it.occurredAt}" },
            1,
            all.size,
        )
    }

    private fun eventOf(occurredAt: Instant, source: EventSource) = ZoneEvent(
        id = UUID.randomUUID().toString(),
        zoneId = "z1",
        zoneName = "Home",
        memberId = "m1",
        type = ZoneEventType.EXIT,
        occurredAt = occurredAt,
        latitude = 10.782,
        longitude = 106.695,
        source = source,
    )
}

/**
 * Delegates every call to the real Room-generated DAO unmodified, except [latestForKey] — adds a
 * fixed delay AFTER the real query returns, to widen the check-then-act race window in
 * `ZoneEventRepositoryImpl.record()` from sub-millisecond to deterministic. See class KDoc above.
 */
private class DelayingZoneEventDao(private val delegate: ZoneEventDao) : ZoneEventDao by delegate {
    override suspend fun latestForKey(zoneId: String, memberId: String, type: ZoneEventType): ZoneEventEntity? {
        val result = delegate.latestForKey(zoneId, memberId, type)
        delay(50)
        return result
    }
}

/** No zones -> `ZoneEventRepositoryImpl.record()`'s notify lookup always misses, keeping this
 * test about the dedupe race only, same convention as `ZoneEventDedupeTest.EmptyZoneRepository`.
 * Named distinctly from that file's private `EmptyZoneRepository` to avoid a same-package,
 * same-simple-name JVM class clash between the two `androidTest` files. */
private class RaceTestEmptyZoneRepository : ZoneRepository {
    override fun observeAll(): Flow<List<Zone>> = MutableStateFlow<List<Zone>>(emptyList()).asStateFlow()
    override suspend fun save(zone: Zone): AppResult<Zone> = AppResult.Success(zone)
    override suspend fun delete(zoneId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun count(): Int = 0
    override suspend fun exists(zoneId: String): Boolean = false
}

private class RaceTestEmptyMemberRepository : MemberRepository {
    override fun observeAll(): Flow<List<Member>> = flowOf(emptyList())
    override fun observeLatestLocations(): Flow<Map<String, LocationPoint>> = flowOf(emptyMap())
    override suspend fun recordLocation(memberId: String, point: LocationPoint) = Unit
}
