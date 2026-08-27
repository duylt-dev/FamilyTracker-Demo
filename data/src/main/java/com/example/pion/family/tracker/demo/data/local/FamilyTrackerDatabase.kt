package com.example.pion.family.tracker.demo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pion.family.tracker.demo.data.local.dao.LocationPointDao
import com.example.pion.family.tracker.demo.data.local.dao.MemberDao
import com.example.pion.family.tracker.demo.data.local.dao.ZoneDao
import com.example.pion.family.tracker.demo.data.local.dao.ZoneEventDao
import com.example.pion.family.tracker.demo.data.local.entity.LocationPointEntity
import com.example.pion.family.tracker.demo.data.local.entity.MemberEntity
import com.example.pion.family.tracker.demo.data.local.entity.ZoneEntity
import com.example.pion.family.tracker.demo.data.local.entity.ZoneEventEntity

/**
 * LLM.md §9 — exactly 4 tables, no `track_sessions`. `fallbackToDestructiveMigration()` for the
 * demo stage (PRD §7.4); `exportSchema = true` + `room.schemaLocation` (data/build.gradle.kts)
 * so the exported schema JSON under `data/schemas/` is the source of truth for what schema a
 * demo build is running.
 *
 * **version 2 (fix-zone-follows-members) — bump có chủ ý dù không cột nào đổi hình dạng.** Hai lý
 * do, cả hai đều là dữ liệu chứ không phải lược đồ:
 * 1. Mọi dòng `zone_events` cũ mang `memberId` của SELF. Sau thay đổi này, sự kiện zone thuộc về
 *    các thành viên được theo dõi — để nguyên thì Timeline trộn lẫn hai mô hình và người dùng đọc
 *    thấy chính lỗi vừa sửa vẫn còn đó.
 * 2. Cột `source` không còn nhận `GEOFENCE_API`. Dòng cũ mang chuỗi đó sẽ làm
 *    `Converters.nameToEventSource` ném `IllegalArgumentException` NGAY khi đọc Timeline lần đầu.
 *
 * `fallbackToDestructiveMigration(dropAllTables = true)` biến bump này thành một lần xoá sạch —
 * đúng thứ cần cho cả hai lý do trên, và đúng chính sách PRD §7.4 cho giai đoạn demo.
 */
@Database(
    entities = [ZoneEntity::class, LocationPointEntity::class, ZoneEventEntity::class, MemberEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FamilyTrackerDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun zoneEventDao(): ZoneEventDao
    abstract fun memberDao(): MemberDao

    companion object {
        private const val DATABASE_NAME = "family_tracker.db"

        fun build(context: Context): FamilyTrackerDatabase =
            Room.databaseBuilder(context.applicationContext, FamilyTrackerDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
