package com.example.pion.family.tracker.demo.domain.usecase

import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.repository.MemberRepository
import com.example.pion.family.tracker.demo.domain.repository.ZoneRepository
import com.example.pion.family.tracker.demo.domain.tracking.ZoneEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * US-12 — "ai đang ở trong zone nào", cho mỗi dòng ở Zone List. Trả về map `zoneId -> thành viên
 * ĐANG Ở TRONG`, giữ nguyên thứ tự của `members` để hai lần phát liên tiếp không đảo chỗ tên trên
 * màn hình.
 *
 * **Chỉ tính thành viên được theo dõi (`!isSelf`).** Bản đầu tiên tính cho self và trả về
 * `Set<zoneId>`, nên tạo một zone quanh chính mình là Zone List lập tức báo "Đang ở trong" — ngược
 * hẳn ý nghĩa của màn hình này: zone tồn tại để biết NGƯỜI NHÀ đang ở đâu, không phải để nhắc lại
 * cho mình biết mình đang đứng ở đâu. Cùng luật với đường sinh `ZoneEvent`
 * (`MemberMovementSimulator`, `:data`): self không bao giờ là chủ thể của một sự kiện zone.
 *
 * Tái dùng [ZoneEvaluator.evaluate] (đã khoá bằng test ở phase-03) thay vì viết lại luật
 * `distance < radius` ở đây — gọi với `previouslyInside = emptySet()` cho MỌI lần tính, không
 * phải state tích luỹ qua thời gian: với tập rỗng ban đầu, hàm thuần trả đúng `insideAfter` =
 * đúng những zone mà khoảng cách hiện tại < bán kính, tức là "đang ở trong ngay bây giờ" — chính
 * là câu hỏi US-12 hỏi. Không dùng cho việc SINH thông báo enter/exit (đó là việc của
 * `MemberMovementSimulator`, LLM.md §8.3) nên bỏ qua trường `events` của kết quả.
 */
class ObserveZoneMembershipUseCase(
    private val zoneRepository: ZoneRepository,
    private val memberRepository: MemberRepository,
) {
    operator fun invoke(): Flow<Map<String, List<Member>>> = combine(
        zoneRepository.observeAll(),
        memberRepository.observeAll(),
        memberRepository.observeLatestLocations(),
    ) { zones, members, locations ->
        val membershipByZone = mutableMapOf<String, MutableList<Member>>()
        members.asSequence()
            .filterNot { it.isSelf }
            .forEach { member ->
                val point = locations[member.id] ?: return@forEach
                ZoneEvaluator.evaluate(point = point, zones = zones, previouslyInside = emptySet())
                    .insideAfter
                    .forEach { zoneId -> membershipByZone.getOrPut(zoneId) { mutableListOf() } += member }
            }
        membershipByZone
    }
}
