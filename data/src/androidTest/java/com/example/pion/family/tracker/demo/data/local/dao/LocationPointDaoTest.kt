package com.example.pion.family.tracker.demo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase
import com.example.pion.family.tracker.demo.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class LocationPointDaoTest {
    private lateinit var db: FamilyTrackerDatabase
    private lateinit var dao: LocationPointDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FamilyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.locationPointDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeBetween_returnsOnlyPointsInRangeForMember() = runBlocking {
        val dayStart = Instant.parse("2026-08-20T00:00:00Z")
        dao.insert(pointOf("p1", "m1", dayStart.plusSeconds(10)))
        dao.insert(pointOf("p2", "m1", dayStart.plusSeconds(20)))
        dao.insert(pointOf("p3", "m1", dayStart.minusSeconds(10))) // before range
        dao.insert(pointOf("p4", "m2", dayStart.plusSeconds(10))) // different member

        val from = dayStart.toEpochMilli()
        val to = dayStart.plusSeconds(3600).toEpochMilli()
        val result = dao.observeBetween("m1", from, to).first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.memberId == "m1" })
    }

    @Test
    fun latestPerMember_returnsOneRowPerMember_withMaxRecordedAt() = runBlocking {
        val base = Instant.now()
        dao.insert(pointOf("p1", "m1", base.minusSeconds(60)))
        dao.insert(pointOf("p2", "m1", base))
        dao.insert(pointOf("p3", "m2", base.minusSeconds(30)))

        val result = dao.latestPerMember().first()
        assertEquals(2, result.size)
        assertEquals("p2", result.first { it.memberId == "m1" }.id)
    }

    @Test
    fun deleteOlderThan_removesOnlyOldPoints() = runBlocking {
        val now = Instant.now()
        dao.insert(pointOf("old", "m1", now.minus(10, ChronoUnit.DAYS)))
        dao.insert(pointOf("new", "m1", now))

        val deleted = dao.deleteOlderThan(now.minus(7, ChronoUnit.DAYS).toEpochMilli())

        assertEquals(1, deleted)
        val remaining = dao.observeBetween("m1", 0L, now.plusSeconds(60).toEpochMilli()).first()
        assertEquals(1, remaining.size)
        assertEquals("new", remaining.first().id)
    }

    private fun pointOf(id: String, memberId: String, recordedAt: Instant) = LocationPointEntity(
        id = id,
        memberId = memberId,
        latitude = 10.77,
        longitude = 106.70,
        accuracyMeters = 10f,
        speedMps = 0f,
        bearingDegrees = 0f,
        recordedAt = recordedAt,
    )
}
