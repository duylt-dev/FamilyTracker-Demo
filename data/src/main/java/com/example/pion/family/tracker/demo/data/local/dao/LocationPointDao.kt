package com.example.pion.family.tracker.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pion.family.tracker.demo.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {
    @Insert
    suspend fun insert(point: LocationPointEntity)

    /** Backs `TrackingRepository.observeRoute(memberId, day)` — uses the `(memberId, recordedAt)` index. */
    @Query(
        "SELECT * FROM location_points " +
            "WHERE memberId = :memberId AND recordedAt BETWEEN :fromMillis AND :toMillis " +
            "ORDER BY recordedAt ASC",
    )
    fun observeBetween(memberId: String, fromMillis: Long, toMillis: Long): Flow<List<LocationPointEntity>>

    /**
     * One row per member, its most recent point — US-08 member marker "last updated". Uses a
     * correlated subquery (not a window function): the platform SQLite on minSdk 28 predates
     * SQLite 3.25's window function support.
     */
    @Query(
        "SELECT * FROM location_points AS lp " +
            "WHERE lp.recordedAt = (" +
            "  SELECT MAX(recordedAt) FROM location_points WHERE memberId = lp.memberId" +
            ")",
    )
    fun latestPerMember(): Flow<List<LocationPointEntity>>

    /** PRD §3.3 7-day retention. Returns rows deleted for `FTD_EVENT purge_completed`. */
    @Query("DELETE FROM location_points WHERE recordedAt < :millis")
    suspend fun deleteOlderThan(millis: Long): Int
}
