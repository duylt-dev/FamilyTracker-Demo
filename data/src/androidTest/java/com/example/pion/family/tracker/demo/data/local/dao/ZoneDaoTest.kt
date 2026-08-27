package com.example.pion.family.tracker.demo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase
import com.example.pion.family.tracker.demo.data.local.entity.ZoneEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ZoneDaoTest {
    private lateinit var db: FamilyTrackerDatabase
    private lateinit var dao: ZoneDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FamilyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.zoneDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertAndObserveAll_returnsInsertedZone() = runBlocking {
        dao.upsert(zoneOf("z1", "Nha"))
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Nha", all.first().name)
    }

    @Test
    fun upsert_sameId_updatesInPlace() = runBlocking {
        dao.upsert(zoneOf("z1", "Nha"))
        dao.upsert(zoneOf("z1", "Nha moi"))
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Nha moi", all.first().name)
    }

    @Test
    fun delete_removesZone() = runBlocking {
        dao.upsert(zoneOf("z1", "Nha"))
        dao.delete("z1")
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun count_reflectsRowCount() = runBlocking {
        dao.upsert(zoneOf("z1", "Nha"))
        dao.upsert(zoneOf("z2", "Truong"))
        assertEquals(2, dao.count())
    }

    /** phase-06 — [ZoneDao.exists] backs [SaveZoneUseCase]'s create-vs-update check (LLM.md §13). */
    @Test
    fun exists_trueForStoredId_falseOtherwise() = runBlocking {
        dao.upsert(zoneOf("z1", "Nha"))
        assertTrue(dao.exists("z1"))
        assertTrue(!dao.exists("z-does-not-exist"))
    }

    private fun zoneOf(id: String, name: String) = ZoneEntity(
        id = id,
        name = name,
        latitude = 10.77,
        longitude = 106.70,
        radiusMeters = 150f,
        colorArgb = 0xFF1B6EF3.toInt(),
        notifyOnEnter = true,
        notifyOnExit = true,
        createdAt = Instant.now(),
    )
}
