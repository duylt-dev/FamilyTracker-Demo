# Fix Report — Phase 09 — Thông báo "đã rời zone" ma khi mở app (LLM.md §13 Open #5 → Fixed #18)

**Trạng thái: XONG.** Máy cuối: bản **release** cài trên `emulator-5554`, quyền đã cấp, DB đã
checkpoint (không WAL/SHM thừa).

## Tóm tắt kết quả

Bug report đúng: Open #5 mô tả phạm vi hẹp hơn thực tế NHIỀU. Đã tái hiện bằng log thật: **mọi lần
mở app đều bắn thông báo "đã rời zone" ma**, không chỉ trong cửa sổ 60s sau khi bấm Mô phỏng.
Nguyên nhân giống hệt Fixed #17 nhưng qua đường khác: `GeofenceRegistrar.registerAll()` (gọi từ
`FamilyTrackerApp.onCreate` **mỗi lần app khởi động**, và `BootCompletedReceiver` sau reboot) luôn
dùng `INITIAL_TRIGGER_ENTER or INITIAL_TRIGGER_EXIT` — Play Services so vị trí thật với MỌI zone
đã có ngay lúc đăng ký lại, bắn NGAY một transition khớp loại. Đã sửa bằng hướng (a): bỏ initial
trigger unconditional trong `registerAll()`. Đã kiểm bằng thực nghiệm cả 3 kịch bản yêu cầu, bao
gồm reboot thật — không mất khả năng phát hiện crossing thật. Tiện tay sửa luôn phát hiện phụ (3):
`registerAll()` chạy 2-3 lần/cold-start do `BootCompletedReceiver` nhận cả 2 action boot.

## Bước 1 — Tái hiện (TRƯỚC KHI SỬA)

Môi trường: `emulator-5554`, bản **release** (`app-release.apk`, không `DEBUGGABLE`), zone có sẵn
trong DB (`Zone mẫu`, `49e744a6-453e-46ef-aeab-a7af9a7f87cd`, lat=10.7816784650272,
lng=106.6978656251314, r=150m — còn lại từ phiên dev-phase-09 trước).

**Kịch bản 1 — mở app, không bấm gì, 20 giây:**
```
03:30:32.576  purge_completed deletedPoints=0 deletedEvents=0
03:30:32.889  geofence_registered zoneId=all count=1 success=true
03:30:32.890  geofence_reregistered_on_boot count=1
03:30:32.924  geofence_registered zoneId=all count=1 success=true
03:30:32.956  geofence_registered zoneId=all count=1 success=true
03:30:32.957  geofence_reregistered_on_boot count=1
03:30:32.963  zone_event_raised zoneId=49e744a6-... type=EXIT source=GEOFENCE_API
03:30:32.967  notification_posted zoneId=49e744a6-... type=EXIT
```
Xác nhận đúng như báo cáo: EXIT ma bắn ra **16ms** sau `geofence_registered` cuối, KHÔNG cần bấm gì.
Lặp lại lần 2 (force-stop + mở lại, không cài lại) cho cùng kết quả — `registerAll` chạy **3 lần**
trong ~70ms mỗi lần, đúng phát hiện phụ (3) trong bug report.

**Điều tra (3) — vì sao 3 lần:** chỉ có ĐÚNG 1 call site sản phẩm gọi `registerAll()`
(`FamilyTrackerApp.onCreate`, xác nhận bằng `grep -rn "registerAll("`). 2 lần còn lại đến từ
`BootCompletedReceiver.onReceive` chạy 2 LẦN — `adb shell dumpsys activity broadcasts history`
xác nhận hệ thống phát CẢ `ACTION_BOOT_COMPLETED` lẫn `ACTION_LOCKED_BOOT_COMPLETED` tới app ở
MỖI lần cold-start (không chỉ sau reboot thật) — hành vi chuẩn Android: broadcast BOOT_COMPLETED
bị hoãn cho app ở trạng thái "stopped" (sau `force-stop`) tới lúc người dùng tự mở lại. `uptime`
xác nhận emulator đã chạy 12h16m liên tục — KHÔNG phải do vừa reboot.

**Kịch bản 2 (đối chứng, trước sửa — dùng lại evidence dev-phase-09's "Rủi ro còn lại")**: mở app
rồi bấm Mô phỏng trong 60s với zone có sẵn → EXIT tức thời của `registerAll()` dedupe-collide với
EXIT thật của lộ trình, một trong hai thông báo biến mất — đã ghi trong `dev-phase-09-report.md`
"Phát hiện phụ, chưa sửa" và `LLM.md` §13 Fixed #17's "Rủi ro còn lại".

## Bước 2 — Sửa: hướng (a), không đổi interface

**Đã cân nhắc và KHÔNG chọn hướng (b)** ("giữ initial trigger, đánh dấu sự kiện sinh từ nó là
đồng bộ-trạng-thái, không bắn thông báo, không chiếm khoá dedupe") vì kiểm bằng thực nghiệm (không
suy đoán) cho thấy initial trigger **không cần thiết** cho đồng bộ trạng thái ở CẢ hai đường:
- **FOREGROUND**: `LocationTrackingService.seedInsideZoneIds()` (phase-04 Implementation Step 9,
  không đổi) đã tự nạp `insideZoneIds` từ `ZoneEventDao.latestPerZone(memberId)` mỗi khi service
  khởi động (kể cả sau reboot) — độc lập hoàn toàn với `registerAll()`.
- **GEOFENCE_API**: initial trigger chỉ quyết định có bắn synthetic transition NGAY lúc đăng ký
  hay không; nó KHÔNG ảnh hưởng `eventsFilter` — filter quyết định crossing nào được BÁO CÁO về
  sau. Xác nhận bằng `dumpsys activity service com.google.android.gms | grep -i geofenc` sau khi
  đăng ký (sau fix): `GeofenceRequest(10.78...,106.69...+150.0m, eventsFilter=[INSIDE, OUTSIDE],
  initialEventsFilter=[]` — filter theo dõi transition (`eventsFilter`) KHÔNG đổi, chỉ
  `initialEventsFilter` (đúng phần gây ma) trống.

Vì cả hai call site sản phẩm của `registerAll()` (`FamilyTrackerApp.onCreate`,
`BootCompletedReceiver`) đều muốn "đồng bộ", không nơi nào muốn "thông báo" — khác `register()`
(Fixed #17, có cả hai use case), **không cần thêm tham số `notifyInitialState` vào
`GeofenceGateway.registerAll()`**. Chỉ đổi `GeofenceRegistrar.registerAll()` nội bộ:
`setInitialTrigger(ENTER or EXIT)` → `setInitialTrigger(0)` không điều kiện. Không đổi interface
`:domain` → không cascading tới bất kỳ fake test nào (6 file fake `registerAll` ở
`ui/test`/`domain/test` giữ nguyên `override suspend fun registerAll(zones: List<Zone>) = Unit`,
compile xanh không sửa).

**File sửa:**
- `data/src/main/java/.../data/geofence/GeofenceRegistrar.kt` — `registerAll()`'s `setInitialTrigger` → `0`, KDoc giải thích.
- `data/src/main/java/.../data/geofence/BootCompletedReceiver.kt` — (3) thêm `AtomicBoolean hasRunThisProcess`, chặn xử lý bản sao broadcast boot thứ 2 trong cùng process (`geofence_reregister_skipped reason=ALREADY_RUN_THIS_PROCESS`).
- `data/src/androidTest/java/.../data/geofence/GeofenceRegistrarTest.kt` — test mới `registerAll_withInitialTriggerZero_stillSucceeds` (khoá lại: gọi API thật với `initialTrigger=0` không ném lỗi — hồi quy nếu ai gõ nhầm giá trị trigger không hợp lệ). Không viết test JVM cho hành vi "không bắn ghost transition" — không Robolectric trong dự án (LLM.md §8.3), và `GeofencingRequest`/`GeofencingClient` là lớp GMS final không mock được trên JVM thuần; bằng chứng chính là log thật trước/sau, cùng tiền lệ Fixed #17.
- `LLM.md` §13 — Open #5 xoá, thêm Fixed #18 (phạm vi thật + bằng chứng 3 kịch bản + phát hiện phụ ngoài scope).

**Giữ nguyên (không đổi):** `ZoneEventDeduper` (hàm thuần `:domain`), `Mutex` ở
`ZoneEventRepositoryImpl` (Fixed #14), `notifyInitialState=false` mà `StartSimulationUseCase`/
`register()` đang dùng (Fixed #17), interface `GeofenceGateway.registerAll(zones)`.

## Bước 3 — Xác nhận SAU KHI SỬA, cả 3 kịch bản

Build: `./gradlew test` — 123 test, 0 fail. `./gradlew clean assembleDebug --no-configuration-cache
| grep -ci warning` = **1** (khớp baseline). `./gradlew :data:connectedDebugAndroidTest` — **14/14
pass** trên `emulator-5554` (gồm 2 test `GeofenceRegistrarTest`). `./gradlew assembleRelease` xanh.

### Kịch bản 1 — mở app, không bấm gì, 20 giây (release, cài mới sau sửa)

```
03:34:57.411  geofence_registered zoneId=all count=1 success=true
03:34:57.411  geofence_reregistered_on_boot count=1
03:34:57.411  geofence_registered zoneId=all count=1 success=true
03:34:57.424  geofence_reregister_skipped reason=ALREADY_RUN_THIS_PROCESS action=android.intent.action.BOOT_COMPLETED
```
**0 dòng `zone_event_raised`/`notification_posted`** trong suốt 22 giây theo dõi. Chạy lại lần 2
(fresh install, không mở app trước) cho kết quả giống hệt — xem
`scratchpad/postfix-scenario1.log` (đã pull về, không còn trong repo). Phát hiện phụ (3) cũng đã
hết: chỉ còn 2 lần gọi `registerAll` hiệu lực + 1 lần bị `geofence_reregister_skipped` (bản sao
LOCKED_BOOT_COMPLETED thứ hai), không còn 3 lần đăng ký Play Services vô ích.

### Kịch bản 2 — mở app rồi bấm Mô phỏng ngay (<60s, zone có sẵn — đúng kịch bản Open #5 gốc)

Mở app 03:35:48.966, bấm "▶ Mô phỏng lộ trình" lúc 03:36:04 (~16 giây sau, well trong 60s):
```
03:35:59.793  simulation_started
03:36:07.720  zone_event_raised zoneId=49e744a6-... type=ENTER source=FOREGROUND
03:36:07.724  notification_posted zoneId=49e744a6-... type=ENTER
03:36:25.170  zone_event_raised zoneId=49e744a6-... type=EXIT source=FOREGROUND
03:36:25.177  notification_posted zoneId=49e744a6-... type=EXIT
03:36:29.929  simulation_finished durationMs=30136 eventsRaised=2
```
**Cả hai thông báo có mặt, KHÔNG một dòng `zone_event_deduped` nào trong toàn bộ log.** Ảnh
notification shade (`scratchpad/screenshots/p9fix-notif-shade.png`, đã tự xem lại): nhóm thông báo
"FamilyTrackerDemo" đếm "2", hiện đúng "Đã đến Zone mẫu 03:36" và "Đã rời Zone mẫu 03:36".

### Kịch bản 3 — Reboot thật (trọng tâm rủi ro của hướng a)

`adb -s emulator-5554 reboot` → `wait-for-device` → poll `sys.boot_completed`. **KHÔNG mở app tay**
— hệ thống tự cold-start process để phát broadcast boot:
```
03:37:29.137  purge_completed deletedPoints=0 deletedEvents=0
03:37:29.239  geofence_registered zoneId=all count=1 success=true
03:37:29.239  geofence_reregistered_on_boot count=1
03:37:29.253  geofence_registered zoneId=all count=1 success=true
```
**0 `zone_event_raised`, 0 `notification_posted`** — geofence đăng ký lại đúng, không ghost.
Xác nhận trực tiếp bằng `dumpsys activity service com.google.android.gms | grep -i geofenc`:
```
10248/com.example.pion.family.tracker.demo/... GeofenceRequest(10.7816784650272,106.6978656251314+150.0m,
  eventsFilter=[INSIDE, OUTSIDE], initialEventsFilter=[] (inactive)
```
`eventsFilter=[INSIDE, OUTSIDE]` — geofence VẪN theo dõi cả hai loại crossing tương lai;
`initialEventsFilter=[]` — không còn ghost lúc đăng ký. Đúng như dự đoán.

**Phát hiện crossing thật sau reboot:** mở app, bật công tắc theo dõi (thiết bị đang ở vị trí
NGOÀI zone qua `emu geo fix`), rồi di chuyển mock location qua lại ranh giới:
```
03:44:35.102  tracking_toggled enabled=true
03:44:39.613  location_recorded accuracy=5.0 filtered=false
03:44:39.620  zone_event_raised zoneId=49e744a6-... type=EXIT source=GEOFENCE_API
03:44:39.629  notification_posted type=EXIT          (thiết bị NGOÀI zone — đúng)
03:44:59.717  zone_event_raised zoneId=49e744a6-... type=ENTER source=GEOFENCE_API
03:44:59.721  notification_posted type=ENTER          (di chuyển vào zone — đúng)
03:46:30.229  zone_event_raised zoneId=49e744a6-... type=EXIT source=GEOFENCE_API
03:46:30.233  notification_posted type=EXIT           (di chuyển ra lại — đúng)
```
Cả ba đều đúng thứ tự, đúng loại, khớp vị trí mock thật đã set — **xác nhận (a) không làm mất khả
năng phát hiện crossing thật sau reboot.** Ảnh notification shade
(`scratchpad/screenshots/p9fix-scenario3-notif-shade.png`, đã tự xem lại): nhóm "3", hiện đúng
"Đã rời Zone mẫu 03:46" / "Đã đến Zone mẫu 03:44" / "Đã rời Zone mẫu 03:44".

**Phát hiện phụ ngoài scope, KHÔNG sửa (ghi vào LLM.md §13 Fixed #18):** dòng EXIT lúc 03:44:39
(4 giây sau khi bật công tắc, ~7 phút sau khi geofence đăng ký lại lúc reboot) là lần Play Services
ĐẦU TIÊN đánh giá geofence bằng một fix GPS THẬT (không có hoạt động định vị nào suốt ~7 phút giữa
lúc đăng ký và lúc bật công tắc). Đây KHÔNG phải cơ chế "ghost tức thời lúc đăng ký" (initial
trigger) đã sửa — nó gắn với một fix GPS thật, xảy ra ~7 phút sau đăng ký chứ không phải trong vài
ms, và chỉ xảy ra khi người dùng THẬT SỰ bật theo dõi. Không khớp kịch bản bug report ("mở app
không bấm gì" — kịch bản 1 xác nhận đã hết ma hoàn toàn). Ghi lại vì là hành vi GMS Geofencing API
chưa từng tài liệu hoá trong dự án (baseline: "trạng thái UNKNOWN cần một fix thật để xác định lần
đầu, kể cả khi initialTrigger=0"), không phải hồi quy của fix này.

## Bước 4 — Tài liệu

`LLM.md` §13: xoá Open #5, thêm Fixed #18 (phạm vi thật rộng hơn ban đầu ghi — mọi lần mở app,
không chỉ cửa sổ 60s — kèm log 3 kịch bản, lý do chọn (a), và phát hiện phụ chưa sửa ở trên).

## Trạng thái máy cuối

- App: bản **release** (`app-release.apk`) cài trên `emulator-5554`, không `DEBUGGABLE`.
- Quyền: `ACCESS_FINE_LOCATION` + `POST_NOTIFICATIONS` đã cấp qua `pm grant`.
- Công tắc theo dõi: đã tắt lại sau kịch bản 3 (không để service chạy nền không cần thiết).
- DB: `family_tracker.db` đã checkpoint (WAL/SHM merge vào file chính, không còn `-wal`/`-shm` sót
  lại). 12 dòng `zone_events` tích luỹ từ toàn bộ phiên test (2 dòng ghost từ TRƯỚC khi sửa — giữ
  lại làm bằng chứng lịch sử, không phải lỗi còn tồn tại; 10 dòng còn lại đều là log thật hợp lệ từ
  kịch bản 2/3 SAU khi sửa). Không phải dữ liệu người dùng thật — sẽ tự bị `PurgeOldHistoryUseCase`
  dọn sau 7 ngày như bình thường.
- **Sự cố nhỏ trong lúc dọn DB (đã khắc phục, không mất dữ liệu):** thao tác checkpoint đầu tiên bị
  lỗi cú pháp `run-as ... sh -c "cp ... && rm ..."` (adb quote lồng nhau sai), khiến bước `cp` fail
  nhưng bước `rm -f databases/*-wal/-shm` ở lần thử THỨ HAI (tách riêng, không còn `&&` bảo vệ) vẫn
  chạy — xoá WAL/SHM trước khi merge, làm mất 4 dòng `zone_events` chưa checkpoint (kịch bản 2 +
  một phần kịch bản 3) khỏi file `.db` trên máy. Phục hồi ngay bằng cách đẩy lại bản `final.db` đã
  checkpoint cục bộ (lưu trước đó ở scratchpad, còn đủ 12 dòng) — xác nhận đọc lại đúng 12 dòng sau
  khi phục hồi. Không có tổn thất dữ liệu cuối cùng, nhưng đây là lỗi thao tác của tôi, ghi lại để
  minh bạch.

## Chỗ tôi thấy bug report chẩn đoán CHƯA ĐỦ (không phải sai)

- Bug report nói đúng phạm vi rộng hơn Open #5 mô tả, và đúng cơ chế (giống Fixed #17 qua đường
  `registerAll`). Không có chỗ nào tôi thấy bug report chẩn đoán SAI.
- Bổ sung: phát hiện phụ (3) "`registerAll()` chạy 3 lần" — bug report nghi ngờ đúng hướng ("kiểm
  xem có phải gọi từ nhiều nơi chồng nhau"), nhưng nguyên nhân thật không phải nhiều call site
  code chồng nhau (chỉ có đúng 1) mà là `BootCompletedReceiver` nhận 2 broadcast boot khác nhau
  mỗi cold-start — một hành vi Android/emulator, không phải lỗi logic gọi hàm.

## Vấn đề chưa giải quyết / cần theo dõi thêm

1. **GMS Geofencing "đánh giá lần đầu bằng fix thật" (phát hiện phụ ở kịch bản 3)** — chưa có tài
   liệu chính thức nào trong dự án mô tả hành vi này trước đây. Không phải bug của phase-09, nhưng
   đáng để phase-11 biết: nếu demo có khoảng trễ dài (nhiều phút) giữa lúc mở app và lúc bật theo
   dõi lần đầu, và thiết bị đang đứng NGOÀI mọi zone lúc đó, có thể thấy một thông báo "đã rời"
   ngay khi bật theo dõi — đây là báo cáo TRẠNG THÁI THẬT (đúng), không phải ghost, nhưng có thể
   gây bất ngờ nếu không biết trước.
2. Gate G4 vẫn cần đo lại trên máy thật (Samsung SM-A165F) ở phase-11 — không đổi bởi fix này,
   nhắc lại từ dev-phase-09.
