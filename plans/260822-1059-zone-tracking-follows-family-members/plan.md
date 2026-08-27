# Zone tracking theo thành viên được follow, không theo self

**Ngày:** 2026-08-22 · **Trạng thái:** ✅ Hoàn thành

## Vấn đề

Toàn bộ đường zone của app đang lấy **self** (thiết bị này) làm chủ thể:

- `ObserveZoneMembershipUseCase` tính `isInside` cho self → tạo zone quanh chính mình thì Zone List
  hiện "Đang ở trong".
- `LocationPointProcessor` sinh `ZoneEvent(memberId = self)` từ GPS thật.
- `GeofenceBroadcastReceiver` sinh `ZoneEvent(memberId = self)` từ Play Services.

Yêu cầu của người dùng ngược lại: **zone phải theo dõi Minh/Lan (thành viên được follow), không theo
dõi mình.** Self chỉ còn dùng cho chấm xanh trên bản đồ và tab Lịch sử.

## Chặn kỹ thuật đã xử lý

1. **Minh/Lan không di chuyển** — `DemoDataSeeder` ghi đúng 1 điểm cho mỗi người rồi thôi. Không có
   nguồn di chuyển thì đổi "ai được tính" cũng không bao giờ có thông báo.
   → Thêm `MemberRoamer` (hàm thuần, `:domain`) + `MemberMovementSimulator` (`:data`).
2. **Geofencing API chỉ bắn cho thiết bị đang chạy app**, tức là chỉ cho self. Không có cách nào để
   nó bắn cho Minh/Lan.
   → Gỡ toàn bộ đường phát hiện GEOFENCE_API. `LLM.md` §8.1 viết lại từ "hai đường phát hiện" thành
   một đường.

## Phase

| # | Phase | Trạng thái |
|---|---|---|
| 1 | [Domain — roamer thuần + membership theo member](phase-01-domain-roamer-and-membership.md) | ✅ |
| 2 | [Data — bộ mô phỏng di chuyển, gỡ geofence, thông báo có tên](phase-02-data-simulator-and-notifications.md) | ✅ |
| 3 | [UI — Zone List hiện ai đang ở trong](phase-03-ui-zone-list-members.md) | ✅ |

## Phụ thuộc

Phase 1 → Phase 2 → Phase 3 (tuần tự: `:domain` đổi chữ ký thì `:data`/`:ui` mới sửa theo được).

## Tiêu chí nghiệm thu

- G1 — Tạo zone quanh vị trí của mình: Zone List hiện "Chưa có ai trong zone", KHÔNG hiện "Đang ở trong".
- G2 — Minh/Lan đi vào zone: Zone List hiện tên + chấm màu của họ; có thông báo "Minh đã đến {zone}".
- G3 — Minh/Lan rời zone: thông báo "Minh đã rời {zone}"; Timeline có 2 dòng mang tên Minh.
- G4 — Không còn `ZoneEvent` nào mang `memberId` của self.
- G5 — `./gradlew test` xanh toàn bộ; `detekt` + `lint` không thêm lỗi mới.
