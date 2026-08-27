package com.example.pion.family.tracker.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pion.family.tracker.demo.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT COUNT(*) FROM members")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(members: List<MemberEntity>)

    /** Looked up by `TrackingRepositoryImpl.record()` — real GPS points always belong to self. */
    @Query("SELECT * FROM members WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): MemberEntity?
}
