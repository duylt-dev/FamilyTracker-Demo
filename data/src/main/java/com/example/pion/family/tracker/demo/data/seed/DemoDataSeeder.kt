package com.example.pion.family.tracker.demo.data.seed

import com.example.pion.family.tracker.demo.data.local.dao.LocationPointDao
import com.example.pion.family.tracker.demo.data.local.dao.MemberDao
import com.example.pion.family.tracker.demo.data.local.entity.LocationPointEntity
import com.example.pion.family.tracker.demo.data.local.entity.MemberEntity
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Seeds 1 self member + 2 fake members (PRD §5.2 colors) with a recent position each, so the
 * Map screen has something to show before any real tracking has happened. Runs once, when
 * `members` is empty — safe to call every app start given `fallbackToDestructiveMigration()`
 * wipes the table between schema changes (PRD §7.4).
 */
class DemoDataSeeder(
    private val memberDao: MemberDao,
    private val locationPointDao: LocationPointDao,
) {
    suspend fun seedIfEmpty() {
        if (memberDao.count() > 0) return

        val self = MemberEntity(id = UUID.randomUUID().toString(), name = "Tôi", colorArgb = SELF_COLOR, isSelf = true)
        val minh = MemberEntity(id = UUID.randomUUID().toString(), name = "Minh", colorArgb = MEMBER_2_COLOR, isSelf = false)
        val lan = MemberEntity(id = UUID.randomUUID().toString(), name = "Lan", colorArgb = MEMBER_3_COLOR, isSelf = false)
        memberDao.insertAll(listOf(self, minh, lan))

        val now = Instant.now()
        listOf(minh, lan).forEach { member ->
            locationPointDao.insert(
                LocationPointEntity(
                    id = UUID.randomUUID().toString(),
                    memberId = member.id,
                    latitude = DEMO_CENTER_LAT + Random.nextDouble(-0.01, 0.01),
                    longitude = DEMO_CENTER_LNG + Random.nextDouble(-0.01, 0.01),
                    accuracyMeters = 15f,
                    speedMps = 0f,
                    // Điểm gieo MỘT LẦN lúc cài app, trước khi MemberMovementSimulator từng chạy —
                    // không phải một mẫu "đang đi" nên không có hướng nào để tính. Nhịp mô phỏng
                    // đầu tiên ghi đè giá trị này bằng bearing thật ngay khi bắt đầu theo dõi
                    // (phase-02, decisions.md "Sai lệch phát hiện trong chính research" #5).
                    bearingDegrees = 0f,
                    recordedAt = now,
                ),
            )
        }
    }

    companion object {
        // PRD §5.2 member colors.
        private const val SELF_COLOR = 0xFF1B6EF3.toInt()
        private const val MEMBER_2_COLOR = 0xFFE5820C.toInt()
        private const val MEMBER_3_COLOR = 0xFF7B3FF2.toInt()

        // Ho Chi Minh City center — a reasonable demo default; replaced by real points once
        // `FusedLocationSource` starts recording (phase-04).
        private const val DEMO_CENTER_LAT = 10.7769
        private const val DEMO_CENTER_LNG = 106.7009
    }
}
