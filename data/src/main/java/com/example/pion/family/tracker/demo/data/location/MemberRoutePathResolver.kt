package com.example.pion.family.tracker.demo.data.location

import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.Member
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteProvider
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteRequest
import com.example.pion.family.tracker.demo.domain.tracking.LegKind
import com.example.pion.family.tracker.demo.domain.tracking.RoamStep
import com.example.pion.family.tracker.demo.domain.tracking.SyntheticPath

/**
 * Tách khỏi `MemberMovementSimulator` (code review phase-04 "VIỆC 4" — giữ file đó dưới 200 dòng,
 * `.claude/rules/development-rules.md`; nó đo được ĐÚNG 200 dòng, sát trần). **Ưu tiên tách đúng
 * khối này vì nó KHÔNG mang bất biến LLM.md §8.1** ("`MemberMovementSimulator` là nơi DUY NHẤT sinh
 * `ZoneEvent`") — [resolve] không tạo `ZoneEvent` nào, chỉ quyết định gọi cổng tuyến nào, nên tách
 * nó không đòi sửa câu khẳng định đó ở §8.1. Tách `raiseZoneEvents` (khối THẬT SỰ mang bất biến đó)
 * thay vào sẽ rẻ hơn về dòng nhưng đắt hơn về tài liệu — mỗi lần đọc §8.1 sẽ phải nhảy sang file này
 * để xác nhận lời hứa còn đúng, đúng cái giá bị coi là "đắt cho một dòng" mà phase-04 muốn tránh.
 *
 * `WANDER` (không zone) — hoặc zone hiếm khi bị xoá đúng lúc chờ tuyến — gọi
 * [MemberRouteProvider.wander]: KHÔNG chạm cache lẫn mạng (một nửa luật hạn ngạch NFR-2, phase-04
 * Step 6), nhưng VẪN cập nhật nguồn hiện tại của thành viên thành SYNTHETIC qua đúng cổng đó (code
 * review "VIỆC B") — gọi thẳng [SyntheticPath] ở đây sẽ bỏ sót bước đó. `ENTER_ZONE`/`LEAVE_ZONE`
 * giao hẳn cho [MemberRouteProvider.path] quyết định tầng.
 */
internal class MemberRoutePathResolver(
    private val memberRouteProvider: MemberRouteProvider,
) {
    suspend fun resolve(member: Member, step: RoamStep.NeedPath, zones: List<Zone>): List<GeoPoint> {
        val from = GeoPoint(step.from.latitude, step.from.longitude)
        val to = GeoPoint(step.target.latitude, step.target.longitude)
        val zone = step.target.zoneId?.let { zoneId -> zones.firstOrNull { it.id == zoneId } }
        if (step.target.kind == LegKind.WANDER || zone == null) {
            return memberRouteProvider.wander(member.id, from, to)
        }
        return memberRouteProvider.path(MemberRouteRequest(member.id, from, to, zone, step.target.kind))
    }
}
