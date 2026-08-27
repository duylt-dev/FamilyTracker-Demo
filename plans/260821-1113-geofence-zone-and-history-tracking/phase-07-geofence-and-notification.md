# Phase 07 — Geofence, thông báo, khử trùng lặp (F2)

## Context Links

- [`plan.md`](plan.md) · [`phase-06`](phase-06-zone-list-and-editor.md) · [`phase-03`](phase-03-domain-tracking-algorithms.md)
- [`LLM.md`](../../LLM.md) §8.1 (hai đường phát hiện), §8.6 (thông báo không kéo `:data`→`:app`), §10
- PRD §2.5 US-22→US-26 · §3.2 F2 · §7.4 · §10 telemetry · §11.1 **gate G5**
- [`research/researcher-01-geofencing-and-background-location.md`](research/researcher-01-geofencing-and-background-location.md) §1, §4, §5, §7

## Overview

| | |
|---|---|
| Priority | **P0** |
| Status | completed (G5 HOÃN — máy thật RF8Y60B9NCZ chưa cắm; mọi phần khác xác nhận trên `emulator-5554`) |
| Effort | 6h |
| Story ánh xạ | **US-22, US-23, US-24, US-25, US-26** — hoàn tất **F2** |
| Gate | **G5** — đóng app khỏi recents, bước qua ranh giới zone thật → có thông báo trong ≤ 3 phút |

Đấu nối đường phát hiện thứ hai: `GeofencingClient` chạy khi app đã đóng. Đường thứ nhất (foreground
service) đã có từ phase-04. Cả hai đổ vào **một** hàm ghi nhận, nơi luật khử trùng lặp 60 giây đã
được viết và test từ phase-02/03.

## Key Insights

1. **Geofence PendingIntent phải `FLAG_MUTABLE`; notification PendingIntent phải `FLAG_IMMUTABLE`**
   (researcher-01 §5). Play Services cần sửa intent để nhét dữ liệu transition vào. Dùng nhầm
   `FLAG_IMMUTABLE` cho geofence → crash lúc `addGeofences`.
2. **Geofence bị xoá sạch sau reboot, sau app upgrade, và sau force-stop** (researcher-01 §4.1).
   Không có cơ chế tự sống lại. Phải đăng ký lại từ Room ở `BOOT_COMPLETED` **và** ở mỗi lần app khởi
   động — cái sau rẻ và bắt luôn trường hợp upgrade.
3. **Không dùng WorkManager cho việc đăng ký lại.** researcher-01 §4.2 gợi ý WorkManager, nhưng đó là
   một dependency **không có** trong `LLM.md` §14 và không có trong `VERSIONS-VERIFIED.md`.
   `BroadcastReceiver.goAsync()` + một coroutine ngắn đủ cho ≤ 100 geofence và không thêm ẩn số
   version nào vào một toolchain vốn đã rất mới. Nếu sau này cần retry bền, mở lại lựa chọn đó.
4. **Độ trễ thật 30 giây – 3 phút, tới 6 phút nếu thiết bị nằm yên** (researcher-01 §1.3). Không gọi
   `setNotificationResponsiveness()` — mặc định là nhanh nhất. Đây cũng chính là lý do F5 (phase-09)
   là P0 chứ không phải tuỳ chọn.
5. **`ZoneNotifier` nằm ở `:data` và không được import `MainActivity`** — làm thế là phụ thuộc ngược
   `:data → :app` và Gradle từ chối. Lấy intent bằng
   `packageManager.getLaunchIntentForPackage(context.packageName)` (`LLM.md` §8.6). Để mở đúng màn
   Timeline (US-22), gắn `putExtra("ftd_route", "timeline")` và cho `MainActivity` đọc extra đó.
6. **Sự kiện luôn được ghi kể cả khi quyền thông báo bị từ chối** (PRD §3.2). `record()` chạy trước,
   `notify()` chạy sau và có thể im lặng. Cột `source` (`GEOFENCE_API` / `FOREGROUND`) tồn tại để QA
   kiểm chứng luật dedupe đang chạy đúng.
7. **Hai công tắc `notifyOnEnter`/`notifyOnExit` chỉ chặn *thông báo*, không chặn *ghi nhận*** (US-19).
   Nếu tắt cả hai mà không đăng ký geofence, Timeline sẽ trống và không ai hiểu vì sao.
   **Vẫn đăng ký cả ENTER và EXIT**, lọc ở bước hiển thị.

## Requirements

**Chức năng**
- Lưu zone → đăng ký geofence ngay; xoá zone → huỷ đăng ký ngay (PRD §3.1, US-14).
- Kênh `zone_events`, importance DEFAULT, có âm thanh, không rung liên tục (PRD §3.2).
- Nội dung: "Đã đến {tên zone}" / "Đã rời {tên zone}", subtitle giờ `HH:mm` (US-22, US-23).
- Bấm thông báo → mở app tới Timeline (US-22).
- App đóng khỏi recents vẫn nhận thông báo trong ≤ 3 phút (US-24, gate G5).
- Sự kiện trùng `(zoneId, memberId, type)` < 60 giây bị bỏ qua (US-25).
- Đứng yên ở mép zone 5 phút → tối đa 1 sự kiện (US-26 — đã bảo đảm bởi hysteresis ở phase-03).
- Đăng ký lại sau `BOOT_COMPLETED` và sau mỗi lần app khởi động (PRD §7.4).

**Phi chức năng**
- Không vượt `MAX_ZONES = 100` geofence — chặn ở `SaveZoneUseCase` (đã có).
- Đăng ký lại 100 geofence không được chặn luồng chính quá 1 giây.

## Architecture

```
 GeofencingClient (nền, 30s–3ph)          LocationTrackingService (tức thì, app mở)
        │ PendingIntent FLAG_MUTABLE                 │ ZoneEvaluator (thuần)
        ▼                                            ▼
 GeofenceBroadcastReceiver ──────┐          ┌────────┘
                                 ▼          ▼
                     ZoneEventRepository.record()      ← nơi DUY NHẤT áp luật 60s
                                 │                        (ZoneEventDeduper, phase-03)
                    ┌────────────┴────────────┐
                    ▼                         ▼
             zone_events (Room)        ZoneNotifier.notify()   ← có thể im lặng nếu thiếu quyền

 BootCompletedReceiver ──goAsync()──▶ GeofenceRegistrar.registerAll(zones từ Room)
 FamilyTrackerApp.onCreate ─────────▶ GeofenceRegistrar.registerAll(...)
```

## Related Code Files

**Tạo**
- `data/geofence/GeofenceRegistrar.kt`, `GeofenceBroadcastReceiver.kt`, `BootCompletedReceiver.kt`
- `data/notification/ZoneNotifier.kt`, `NotificationChannels.kt`
- `data/src/androidTest/.../GeofenceRegistrarTest.kt`

**Sửa**
- `app/src/main/AndroidManifest.xml` — 2 receiver, `exported="false"`, intent-filter `BOOT_COMPLETED` + `LOCKED_BOOT_COMPLETED`
- `domain/usecase/SaveZoneUseCase.kt`, `DeleteZoneUseCase.kt` — gọi đăng ký / huỷ (qua một interface
  `GeofenceGateway` khai báo ở `:domain/repository/` để `:domain` không biết Play Services)
- `data/repository/ZoneEventRepositoryImpl.kt` — gọi `ZoneNotifier` sau khi ghi
- `app/FamilyTrackerApp.kt` — đăng ký lại lúc khởi động
- `app/MainActivity.kt` — đọc extra `ftd_route` để mở Timeline
- `app/src/main/res/values/strings.xml`
- `LLM.md` §3 nếu thêm `GeofenceGateway`

## Implementation Steps

1. Khai báo `GeofenceGateway` ở `:domain/repository/`: `suspend fun register(zone: Zone): AppResult<Unit>`,
   `suspend fun unregister(zoneId: String)`, `suspend fun registerAll(zones: List<Zone>)`.
   `:domain` không được import `com.google.android.gms.*`.
2. `GeofenceRegistrar` (impl ở `:data`): `Geofence.Builder().setRequestId(zone.id)`
   `.setCircularRegion(lat, lng, radius)` `.setExpirationDuration(NEVER_EXPIRE)`
   `.setTransitionTypes(ENTER or EXIT)`. `GeofencingRequest` với
   `setInitialTrigger(INITIAL_TRIGGER_ENTER or INITIAL_TRIGGER_EXIT)`.
   **Không gọi `setNotificationResponsiveness()`** (Key Insight #4).
   PendingIntent: `FLAG_UPDATE_CURRENT or FLAG_MUTABLE` (API 31+).
   Log `FTD_EVENT geofence_registered zoneId success`.
3. `GeofenceBroadcastReceiver`: `GeofencingEvent.fromIntent(intent)`, kiểm `hasError()`,
   đọc `triggeringGeofences` + `geofenceTransition`, dựng `ZoneEvent(source = GEOFENCE_API)`,
   gọi `ZoneEventRepository.record()` trong `goAsync()`. Toạ độ lấy từ `triggeringLocation`.
   Zone đã bị xoá khỏi Room mà geofence còn sót → bỏ qua im lặng và huỷ đăng ký id đó.
4. `NotificationChannels`: kênh `zone_events` (DEFAULT, có âm thanh) và kênh `location_tracking`
   (LOW, đã dùng ở phase-04). Tạo kênh trong `FamilyTrackerApp.onCreate`.
5. `ZoneNotifier.notify(event, zoneName)`: tiêu đề theo US-22/US-23, `HH:mm` theo `Locale` VN,
   PendingIntent `FLAG_IMMUTABLE` từ `getLaunchIntentForPackage` + `putExtra("ftd_route","timeline")`.
   Không bắn nếu zone tắt công tắc tương ứng. Log `FTD_EVENT notification_posted zoneId type`.
6. `ZoneEventRepositoryImpl.record()`: hỏi `ZoneEventDeduper` → nếu bỏ qua thì log
   `FTD_EVENT zone_event_deduped zoneId type gapMs` và **dừng**; nếu ghi thì log
   `zone_event_raised zoneId type source` rồi gọi `ZoneNotifier`.
7. `SaveZoneUseCase` gọi `register` sau khi ghi Room thành công; `DeleteZoneUseCase` gọi `unregister`
   **trước** khi xoá bản ghi (xoá trước rồi crash giữa chừng sẽ để lại geofence mồ côi).
   Gỡ `TODO(phase-07)` ở phase-06.
8. `BootCompletedReceiver` + `FamilyTrackerApp.onCreate` cùng gọi `registerAll`. Dùng `goAsync()`,
   không WorkManager (Key Insight #3).
9. `MainActivity` đọc `intent.getStringExtra("ftd_route")`, nếu `"timeline"` thì điều hướng tới
   `TimelineRoute` sau khi NavHost dựng xong. Màn Timeline hoàn thiện ở phase-10 — tới đó chỉ cần
   route tồn tại.
10. Viết androidTest `GeofenceRegistrarTest`: đăng ký 1 zone → `addGeofences` trả success;
    huỷ → `removeGeofences` trả success. Test này chỉ chứng minh việc đấu nối, không chứng minh
    hành vi vào/ra — cái đó đã có ở `:domain:test`.

## Todo List

- [x] `GeofenceGateway` ở `:domain`, impl `GeofenceRegistrar` ở `:data`
- [x] PendingIntent geofence `FLAG_MUTABLE`, notification `FLAG_IMMUTABLE`
- [x] `GeofenceBroadcastReceiver` + `goAsync()`, bỏ qua geofence mồ côi
- [x] Kênh `zone_events` DEFAULT + `location_tracking` LOW
- [x] `ZoneNotifier` nội dung US-22/US-23 + `HH:mm`, mở app tới Timeline
- [x] `ZoneNotifier` không import `MainActivity`
- [x] `record()` ghi trước, thông báo sau; tôn trọng `notifyOnEnter`/`notifyOnExit`
- [x] `SaveZoneUseCase` đăng ký, `DeleteZoneUseCase` huỷ trước khi xoá — **gỡ `TODO(phase-07)`**
- [x] `BootCompletedReceiver` + đăng ký lại lúc app khởi động
- [x] `MainActivity` đọc extra `ftd_route` (cả `onCreate` lẫn `onNewIntent` — xem LLM.md §13 Fixed #13)
- [x] `FTD_EVENT`: `geofence_registered`, `zone_event_raised`, `zone_event_deduped`, `notification_posted`
- [x] androidTest `GeofenceRegistrarTest`

## Success Criteria

```bash
# connected*AndroidTest giữ variant debug — lý do ở phase-02 Success Criteria
./gradlew :app:assembleRelease :data:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -s FTD_EVENT
# Sau khi tạo zone: phải thấy geofence_registered success=true

# Kiểm chứng gate G5 trên thiết bị thật:
# 1. tạo zone bán kính 150m quanh vị trí hiện tại
# 2. adb shell am force-stop KHÔNG được dùng — phải vuốt app khỏi recents
# 3. đi bộ ra ngoài bán kính → bấm giờ tới lúc thông báo hiện, phải ≤ 3 phút
```
- `zone_event_deduped` xuất hiện trong logcat ít nhất một lần khi cả hai đường cùng bắt được một lần
  bước qua ranh giới → chứng minh luật 60 giây đang chạy (PRD §10).
- Reboot thiết bị → mở logcat → thấy `geofence_registered` cho từng zone mà không cần mở app.
- Xoá zone → `dumpsys` không còn geofence đó; bước qua chỗ cũ không sinh thông báo.

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| **G5 đòi đi bộ thật ngoài trời** — emulator và F5 đều không thay thế được | Gate P0 không đóng được đúng hạn | Xếp lịch đo G5 ngay khi phase này xong, tách khỏi việc code. Emulator dùng để gỡ lỗi luồng geofence, **không** dùng để tick G5 (phase-11 tầng b/c) |
| Thiếu `ACCESS_BACKGROUND_LOCATION` do người dùng chọn "Chỉ lần này" | Geofence im lặng, giống hệt lỗi code | Kiểm bằng `adb shell dumpsys package … \| grep BACKGROUND_LOCATION` trước khi kết luận |
| Xiaomi/Oppo/Vivo chặn `BOOT_COMPLETED` nếu chưa bật Autostart | Sau reboot geofence chết | Chọn Pixel/Samsung làm máy demo; nếu không, bật Autostart trước và ghi vào kịch bản demo |
| Zone bị xoá nhưng geofence còn sống | Thông báo cho zone không tồn tại | Receiver tự huỷ id không tìm thấy trong Room |
| Đăng ký lại 100 geofence lúc khởi động làm chậm mở app | Vi phạm §7.1 < 2.5s | Chạy trên `Dispatchers.IO`, không chặn `onCreate` |
| `record()` được gọi từ receiver không có coroutine scope sống lâu | Sự kiện mất | `goAsync()` + `PendingResult.finish()` trong `finally` |

## Security Considerations

- Nội dung thông báo chứa **tên zone** do người dùng đặt (đã giới hạn 40 ký tự ở US-16) và giờ —
  không chứa toạ độ.
- `zone_event_raised` log `zoneId`/`type`/`source`, **không** log lat/lng (gate G7).
- Cả hai receiver `exported="false"`; `GeofenceBroadcastReceiver` chỉ nhận PendingIntent do chính app tạo.
- `getLaunchIntentForPackage` thay vì tham chiếu `MainActivity` — vừa là luật kiến trúc vừa tránh
  hardcode tên lớp vào một module khác.

## Next Steps

→ [phase-08](phase-08-history-and-route-playback.md) (song song được) và [phase-09](phase-09-route-simulator.md).
Chặn: 09 (mô phỏng phải sinh thông báo thật), 10 (Timeline cần nguồn sự kiện).
