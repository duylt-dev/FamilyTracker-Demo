package com.example.pion.family.tracker.demo.data.repository

import android.content.Context
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.data.local.dao.ZoneEventDao
import com.example.pion.family.tracker.demo.data.local.mapper.toDomain
import com.example.pion.family.tracker.demo.data.local.mapper.toEntity
import com.example.pion.family.tracker.demo.data.notification.ZoneNotifier
import com.example.pion.family.tracker.demo.domain.model.ZoneEvent
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneEventRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.ZoneEventDeduper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The ONE place the 60s zone-event dedupe rule is APPLIED (LLM.md §8.1, PRD §3.2). The DECISION
 * itself lives in the pure [ZoneEventDeduper] (`:domain/tracking/`, phase-03, LLM.md Phụ lục
 * A.1) — this class only reads the latest same-key event and asks it.
 *
 * phase-07: ghi TRƯỚC, thông báo SAU (Implementation Step 6) — sự kiện luôn vào Room kể cả khi
 * quyền thông báo bị từ chối (PRD §3.2). `zoneRepository` chỉ để tra `notifyOnEnter`/
 * `notifyOnExit`/tên zone tại thời điểm thông báo (`ZoneEvent` không mang hai cờ đó — LLM.md §8.2:
 * hàm thuần `ZoneEvaluator` không biết `notifyOnEnter`/`notifyOnExit`, chỉ `ZoneCrossing.shouldNotify`
 * ở tầng gọi thật hiểu ý nghĩa của cờ).
 *
 * fix-phase-07: [dedupeMutex] serializes the "read latest -> decide -> insert" segment of
 * [record] against concurrent callers — see `ZoneEventRaceConditionTest` and LLM.md §8.1 for the
 * real 7ms-apart on-device duplicate this closes. Chosen over a Room `@Transaction` DAO method to
 * avoid pulling the pure `:domain` decision (`ZoneEventDeduper`) down into `:data`'s DAO layer,
 * which stays exactly as documented in Phụ lục A.1.
 *
 * fix-zone-follows-members: đường phát hiện thứ hai (`GeofenceBroadcastReceiver`) đã bị gỡ, nên
 * hôm nay chỉ còn `MemberMovementSimulator` gọi [record]. [dedupeMutex] GIỮ NGUYÊN: nó bảo vệ một
 * đoạn kiểm-rồi-ghi không nguyên tử, và một `Mutex` không tranh chấp gần như không tốn gì — bỏ nó
 * đi chỉ để "dọn dẹp" là đặt lại đúng cái bẫy 7ms cho người thêm nguồn sự kiện thứ hai sau này.
 *
 * Tên thành viên được tra thêm ở [record] để thông báo đọc lên là "Minh đã đến Trường học", không
 * phải "Đã đến Trường học" — sau fix-zone-follows-members mọi sự kiện đều thuộc về một thành viên
 * ĐƯỢC THEO DÕI, nên không nói rõ là ai thì thông báo mất hẳn nghĩa.
 */
class ZoneEventRepositoryImpl(
    private val zoneEventDao: ZoneEventDao,
    private val zoneRepository: ZoneRepository,
    private val memberRepository: MemberRepository,
    private val context: Context,
) : ZoneEventRepository {

    private val dedupeMutex = Mutex()

    override fun observeTimeline(sinceDays: Int): Flow<List<ZoneEvent>> {
        val cutoff = Instant.now().minus(sinceDays.toLong(), ChronoUnit.DAYS).toEpochMilli()
        return zoneEventDao.observeSince(cutoff).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun record(event: ZoneEvent) {
        val wasInserted = dedupeMutex.withLock {
            val latest = zoneEventDao.latestForKey(event.zoneId, event.memberId, event.type)?.toDomain()
            if (!ZoneEventDeduper.shouldRecord(latest, event)) {
                val gapMs = latest?.let { Duration.between(it.occurredAt, event.occurredAt).toMillis() }
                FtdLog.d(TAG, "zone_event_deduped zoneId=${event.zoneId} type=${event.type} gapMs=$gapMs")
                false
            } else {
                zoneEventDao.insert(event.toEntity())
                FtdLog.d(TAG, "zone_event_raised zoneId=${event.zoneId} type=${event.type} source=${event.source}")
                true
            }
        }
        if (!wasInserted) return

        val zone = zoneRepository.observeAll().first().firstOrNull { it.id == event.zoneId } ?: return
        // Tên thành viên tra ở ĐÂY chứ không mang sẵn trong `ZoneEvent`: `zone_events` cố tình chỉ
        // lưu `memberId` (khoá), còn `zoneName` thì lưu bản sao (PRD §9) vì zone xoá được — thành
        // viên thì không, nên không có nguy cơ mất tên khi đọc lại Timeline sau này.
        val memberName = memberRepository.observeAll().first().firstOrNull { it.id == event.memberId }?.name
        ZoneNotifier.notify(context, event, zone, memberName)
    }

    override suspend fun purgeOlderThan(days: Int): Int {
        val cutoff = Instant.now().minus(days.toLong(), ChronoUnit.DAYS).toEpochMilli()
        return zoneEventDao.deleteOlderThan(cutoff)
    }

    companion object {
        private const val TAG = "FTD_EVENT"
    }
}
