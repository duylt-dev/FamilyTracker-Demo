# Dev report — zone theo dõi thành viên được follow

**Ngày:** 2026-08-22 · **Kết quả:** ✅ Hoàn thành, đã xác minh trên `emulator-5554`

## Yêu cầu

> Khi tôi tạo zone, thì người mà tôi follow (Minh/Lan) ở trong hay đi qua zone đó thì mới có thông
> báo; ở tab Zone, item zone nào có member ở trong thì phải hiển thị lên, chứ không phải tracking
> bản thân mình. Hiện tại tôi tạo zone 'test zone 2' chứa vị trí của tôi và nó báo "(Đang ở trong)".

## Chẩn đoán

Ba nơi cùng một lỗi, đều lấy self làm chủ thể của zone:

| Nơi | Hành vi cũ |
|---|---|
| `ObserveZoneMembershipUseCase` | tính `isInside` cho member `isSelf = true` |
| `LocationPointProcessor` | GPS thật của máy → `ZoneEvent(memberId = self)` |
| `GeofenceBroadcastReceiver` | Play Services → `ZoneEvent(memberId = self)` |

Hai chặn kỹ thuật không hiển nhiên, phát hiện khi khảo sát:

1. **Đổi "ai được tính" một mình là chưa đủ.** `DemoDataSeeder` ghi cho Minh/Lan đúng MỘT điểm lúc
   cài app rồi thôi. Không có nguồn di chuyển thì `ZoneEvaluator` không bao giờ có hai vị trí khác
   nhau để so — Zone List sẽ đúng nhưng thông báo không bao giờ nổ.
2. **Geofencing API không thể bắn cho Minh/Lan.** Nó chỉ báo transition cho THIẾT BỊ đang chạy app.
   Sau khi self thôi là chủ thể, mọi sự kiện nó sinh ra đều là sự kiện phải vứt đi.

## Đã làm

**`:domain`**
- `tracking/MemberRoamer.kt` (mới) — hàm thuần: bước đi kế tiếp của một thành viên (vào zone →
  đứng lại → ra khỏi zone → zone khác). Dời vị trí khi đích xa hơn `MAX_WALK_M` (Minh ở TP.HCM,
  zone ở Mountain View) nhưng LUÔN thả ngoài ranh giới để lần cắt sinh ENTER là thật.
- `usecase/ObserveZoneMembershipUseCase.kt` — trả `Map<zoneId, List<Member>>`, lọc `!isSelf`.
- `repository/MemberRepository.kt` — thêm `recordLocation(memberId, point)`.
- Xoá `repository/GeofenceGateway.kt`; gỡ khỏi `SaveZoneUseCase`/`DeleteZoneUseCase`/`StartSimulationUseCase`.

**`:data`**
- `location/MemberMovementSimulator.kt` (mới) — nguồn di chuyển của Minh/Lan, nơi DUY NHẤT sinh
  `ZoneEvent`. `insideZoneIds` riêng theo từng thành viên. Ghi thẳng qua `recordLocation`, không
  qua `LocationFilter` (điểm mô phỏng không nhiễu; cú dời vị trí sẽ bị luật `SPEED` từ chối).
- `location/LocationPointProcessor.kt` — bỏ hẳn bước đánh giá zone, còn lọc → ghi Room.
- `location/LocationTrackingService.kt` — thêm `familyJob` ĐỘC LẬP với `trackingJob`
  (`ACTION_SIMULATE` huỷ `trackingJob`; gộp chung thì bấm "Mô phỏng lộ trình" sẽ giết luôn chuyển
  động của gia đình).
- `repository/ZoneEventRepositoryImpl.kt` — tra tên thành viên cho thông báo.
- `notification/ZoneNotifier.kt` + strings — "Minh đã đến {zone}" / "Minh đã rời {zone}".
- Xoá `geofence/` (3 file) và `GeofenceRegistrarTest`.
- DB `version = 2` (destructive) — dòng `zone_events` cũ mang `memberId` của self và
  `source='GEOFENCE_API'` đều không còn hợp lệ.

**`:ui`**
- `ZoneListItem.isInside: Boolean` → `membersInside: List<ZoneMemberChip>`; `ZoneRow` vẽ chấm màu
  (cùng `Member.colorArgb` với marker trên bản đồ) + tên, hoặc "Chưa có ai trong zone".
- Nhãn công tắc "Theo dõi vị trí" → "Theo dõi gia đình" (công tắc bật CẢ hai job).

**`:app`** — gỡ 2 receiver + `RECEIVE_BOOT_COMPLETED` khỏi manifest, gỡ `registerAll` khỏi
`FamilyTrackerApp`.

## Một sửa thêm sau khi đo thật

`MemberRoamer.DWELL_TICKS` ban đầu chọn tay là 20 (50s). Đo trên máy thật với zone 150m:
ENTER→ENTER cách nhau ~90s — an toàn so với cửa sổ khử trùng lặp 60s, nhưng tính lại cho
`ZONE_RADIUS_MIN_M` (50m) thì hai chặng đi ngắn lại và dự phòng chỉ còn ~7s. Đổi thành suy ra từ
chính hằng số quy định giới hạn:

```kotlin
val DWELL_TICKS: Int =
    (EVENT_DEDUPE_WINDOW_MS / MEMBER_ROAM_INTERVAL_MS).toInt() + DWELL_SAFETY_TICKS  // = 30
```

Riêng thời gian đứng yên (75s) đã dài hơn cửa sổ 60s, nên bất biến đúng theo cấu trúc ở MỌI bán
kính, không phụ thuộc độ dài hai chặng đi. Đo lại sau khi sửa: ENTER→ENTER = **103s**, 0 dòng
`zone_event_deduped`.

## Xác minh

**Gate tự động**
- `./gradlew test` — **175/175 xanh** (thêm `MemberRoamerTest` 7, `MemberMovementSimulatorTest` 5,
  `ObserveZoneMembershipUseCaseTest` viết lại 6). Bao gồm `KoinModulesTest` (đồ thị DI resolve).
- `./gradlew lintDebug` — **0 error** (app 21 warning, data 2, ui 2 — không đổi so với baseline).
- `./gradlew compileDebugAndroidTestKotlin` — xanh.

**Chạy thật trên `emulator-5554`** (`pm clear` trước, vị trí giả 10.7769/106.7009)

| Bước | Kết quả |
|---|---|
| Tạo "test zone 2" bán kính 150m ĐÚNG quanh vị trí thiết bị | `zone_saved totalZones=1`, **KHÔNG** `zone_event_raised` nào |
| Tab Zone ngay sau đó | **"Chưa có ai trong zone"** — không còn "Đang ở trong" |
| +8s: Minh và Lan đi tới | 2 × `zone_event_raised type=ENTER` + 2 × `notification_posted` |
| Notification shade | "**Minh đã đến test zone 2**", "**Lan đã đến test zone 2**" |
| Tab Zone | chấm cam + chấm tím + "**Minh, Lan**" |
| Sau 2 vòng đầy đủ | 6 sự kiện xen kẽ ENTER/EXIT, `zone_event_deduped` = **0** |
| Tab Nhật ký | 9 dòng, **mọi dòng mang tên Minh hoặc Lan**, không dòng nào mang tên "Tôi" |

Ảnh chụp màn hình trong scratchpad của phiên làm việc (map, zone editor, zone list trước/sau,
notification shade, timeline).

## Nợ kỹ thuật đã ghi vào `LLM.md` §13 Open

| # | Nội dung |
|---|---|
| 4 | Mất khả năng phát hiện vào/rời zone khi tiến trình đã chết (US-24). Không sửa được trong phạm vi demo không có server — chuyển động của Minh/Lan chỉ tồn tại trong tiến trình này. |
| 5 | `ACCESS_BACKGROUND_LOCATION` vẫn được xin ở bước 3 onboarding dù không ai còn đọc vị trí ở nền. Gỡ cả bước là quyết định sản phẩm về luồng onboarding (PRD §4.3 Flow 1), không tự làm. |
