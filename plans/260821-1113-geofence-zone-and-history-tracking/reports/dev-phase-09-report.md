# Dev Report — Phase 09 — Route Simulator (F5, US-33, gate G4)

## Trạng thái tổng quan

- [x] Code viết xong, biên dịch xanh cả 4 module (`:domain`, `:data`, `:ui`, `:app`)
- [x] `./gradlew test` — **123 test, 0 fail, 0 error** (toàn bộ 4 module)
- [x] Gate G6 — `--no-configuration-cache`, **1 warning** (khớp baseline `ENV-BRIEFING.md` §8)
- [x] `assembleRelease` + cài lên `emulator-5554`
- [x] Nút mô phỏng CÓ MẶT trên bản **release** — ảnh `p9-EVIDENCE-01-button-on-release.png`
- [x] Chạy mô phỏng thật, xác minh cả 3 hệ quả (thông báo, `zone_events`, polyline History)
- [x] Gate G4 đo trên `emulator-5554` — **~25.4 giây** (chi tiết dưới) — **KHÔNG PHẢI thiết bị demo thật**, xem "Sai lệch" #4
- [x] Gate G7 — log của app KHÔNG có toạ độ; 2 dòng khớp regex thuộc process Play Services, không phải app
- [x] `LLM.md` §3/§6/§8.4/§13 cập nhật; Todo List phase-09 + `plan.md` đã tick

## Một phát hiện quan trọng, đã sửa: đăng ký geofence cho zone tự tạo làm mất 1 trong 2 thông báo

Lần chạy thật ĐẦU TIÊN (trước khi sửa) cho thấy G4 **fail về mặt tinh thần** dù kỹ thuật đọc log
literal có thể lách qua: tạo "Zone mẫu" (`zones.isEmpty()`) rồi đăng ký geofence NGAY — Play
Services so vị trí THẬT của thiết bị với zone mới và bắn NGAY một transition khớp loại
(`INITIAL_TRIGGER_ENTER or INITIAL_TRIGGER_EXIT`, mặc định của `GeofenceRegistrar` từ phase-07).
Vì "vị trí hiện tại" dùng để đặt tâm zone là vị trí LỊCH SỬ (Room, fallback member) chứ không phải
GPS thật của thiết bị, transition tức thời đó gần như luôn LỆCH với GPS thật → bắn EXIT ngay lập
tức (5ms sau `geofence_registered`). ~25 giây sau, lộ trình mô phỏng tự nó tạo ENTER rồi EXIT —
EXIT thứ hai trùng khoá `(zoneId, memberId, EXIT)` với transition tức thời kia, rơi trong
`EVENT_DEDUPE_WINDOW_MS` (60s) nên bị `zone_event_deduped`. Đây là hiện tượng **cấu trúc**, không
phải may rủi của lần chạy — luôn đúng MỘT trong hai (ENTER hoặc EXIT) bị nuốt, bất kể vị trí thật
của thiết bị là gì, MỖI KHI simulate chạy trên nhánh "chưa có zone".

**Sửa:** `GeofenceGateway.register(zone, notifyInitialState: Boolean = true)` — `StartSimulationUseCase`
gọi `SaveZoneUseCase(sample, notifyInitialState = false)` cho ĐÚNG MỘT trường hợp này;
`GeofenceRegistrar` truyền `initialTrigger = 0` thay vì `ENTER or EXIT` khi `false`. Mọi nơi gọi
khác (`ZoneEditorViewModel` tạo zone thật qua UI) giữ mặc định `true`, hành vi không đổi. Chi tiết
đầy đủ + bằng chứng thật hai chiều (trước/sau): `LLM.md` §13 Fixed #17.

**Đây là sai lệch lớn nhất so với phase file** — không nằm trong Related Code Files, phát hiện
bằng chạy thật, không phải suy luận từ tài liệu. Xem mục "Sai lệch" bên dưới cho danh sách file bị kéo theo.

## Kiến trúc đã triển khai

```
HistoryScreen [▶ Mô phỏng] → HistoryIntent.StartSimulation
  → HistoryViewModel.onStartSimulation (isSimulating=true ngay lập tức, re-entry guard, launchSafely)
    → StartSimulationUseCase (:domain)
        - vị trí hiện tại: self.lastLocation, fallback bất kỳ member nào có vị trí (2 lớp dự phòng,
          cùng luật LLM.md §13 Fixed #11)
        - zones rỗng → SaveZoneUseCase(zone mẫu, notifyInitialState=false) — đăng ký geofence thật
          nhưng KHÔNG bắn transition tức thời (xem phát hiện ở trên)
        - zone gần nhất (GeoDistance.haversineMeters — internal nhưng cùng module :domain)
        - RouteBlueprint.build(...) → List<SimulatedFix>
        - trackingRepository.runSimulation(fixes)   [TrackingRepository — method MỚI, xem "Sai lệch"]
    → TrackingRepositoryImpl.runSimulation (:data)
        - simulatedLocationSource.load(fixes)  [SimulatedLocationSource singleton, chia sẻ với service]
        - startForegroundService(ACTION_SIMULATE)
        - await LocationTrackingService.isSimulating: true rồi false (KHÔNG delay() cố định)
    → LocationTrackingService.runSimulation (:data, chạy trong scope RIÊNG của Service)
        - cancelAndJoin trackingJob thật (job con cấu trúc của scope, MVI doc §3)
        - collectFrom(simulatedLocationSource) — CÙNG pipeline processor.process() với nguồn thật
        - log simulation_started / simulation_finished durationMs eventsRaised (không log lat/lng)
        - khôi phục trackingJob thật, hoặc stopSelf() nếu service tự khởi động riêng cho lượt này
```

**Vì sao "rời màn hình giữa chừng vẫn chạy hết" đúng theo kiến trúc, không chỉ theo lý thuyết:**
vòng lặp thật (`collectFrom(simulatedLocationSource)`) chạy trong `LocationTrackingService.scope`
(`CoroutineScope(SupervisorJob() + Dispatchers.Default)` sống theo vòng đời SERVICE), KHÔNG phải
`viewModelScope`. Nếu người dùng rời màn hình, `HistoryViewModel` bị `onCleared()`, coroutine đang
`launchSafely { ... first { !it } }` (chờ `isSimulating`) bị huỷ — nhưng đó chỉ là bên ĐANG CHỜ, vòng
lặp SINH RA sự kiện nằm ở Service, hoàn toàn độc lập, tiếp tục chạy tới hết.

## Bảng Effect → nơi collect

| Effect | Bắn ở đâu | Collect ở đâu |
|---|---|---|
| `HistoryEffect.ShowError` | `onStartSimulation` (nhánh `AppResult.Failure` VÀ nhánh `onError`) | `HistoryRoute` (`CollectEffects`) → snackbar |
| `HistoryEffect.FocusCamera` | không đổi (phase-08/10), không liên quan phase-09 | `HistoryRoute` |

Không có Effect mới nào thêm ở phase-09 — `StartSimulation` dùng lại `ShowError` đã có.

## `BuildConfig.SIMULATOR_ENABLED` ở cả hai variant

Đọc trực tiếp `app/build/generated/source/buildConfig/{debug,release}/.../BuildConfig.java` sau
build thật:
```
release: public static final boolean SIMULATOR_ENABLED = true;  // Field from default config.
debug:   public static final boolean SIMULATOR_ENABLED = true;  // Field from default config.
```
Khai ở `defaultConfig` (không phải trong `buildTypes.release {}`), nên áp cho cả hai variant tự
động — không có nhánh `DEBUG` nào can thiệp.

## US-33 — bằng chứng "ba hệ quả" (thao tác thật, `emulator-5554`, bản **release**, DB đã `pm clear` để test đúng nhánh "chưa có zone")

**Thao tác:** cài release → cấp quyền `ACCESS_FINE_LOCATION`+`POST_NOTIFICATIONS` qua `pm grant` →
mở app → tab "Lịch sử" → bấm "▶ Mô phỏng lộ trình".

**Log thật (`adb logcat -s FTD_EVENT`), đã lọc log History không liên quan:**
```
02:58:05.146  zone_saved zoneId=08cf... radius=150.0 totalZones=1
02:58:05.152  geofence_registered zoneId=08cf... success=true
02:58:05.160  simulation_started
02:58:05.164  location_recorded accuracy=8.0 filtered=false   (x20, mỗi ~1.58s)
02:58:13.091  zone_event_raised zoneId=08cf... type=ENTER source=FOREGROUND     (t+7.9s)
02:58:13.097  notification_posted zoneId=08cf... type=ENTER
02:58:30.548  zone_event_raised zoneId=08cf... type=EXIT source=FOREGROUND      (t+25.4s)
02:58:30.554  notification_posted zoneId=08cf... type=EXIT
02:58:35.308  simulation_finished durationMs=30148 eventsRaised=2
```

1. **Thông báo vào/rời zone bắn ra thật** — ảnh `p9-EVIDENCE-02-notifications-enter-exit.png`:
   notification shade hiện CẢ HAI "Đã đến Zone mẫu 02:58" và "Đã rời Zone mẫu 02:58".
2. **`zone_events` có bản ghi, đúng 1 cho mỗi lần vào/rời** — pull DB thật qua `run-as` (bản debug
   tạm cài để đọc, cài lại release ngay sau):
   ```
   zoneId=08cf... memberId=7ff9... type=ENTER occurredAt=1787342293055 source=FOREGROUND
   zoneId=08cf... memberId=7ff9... type=EXIT  occurredAt=1787342310424 source=FOREGROUND
   ```
   Đúng 2 dòng, không trùng lặp. `location_points` = 22 (20 điểm mô phỏng + 2 điểm seed của
   Minh/Lan từ `DemoDataSeeder`).
3. **Màn History vẽ được polyline của tuyến vừa mô phỏng** — ảnh
   `p9-EVIDENCE-03-history-polyline.png`: polyline xanh 599m, thẻ thống kê "599 m · 0 phút · 71.9
   km/h" (0 phút vì `DurationFormat` làm tròn phút, chuyến chỉ 30 giây — hành vi format có sẵn từ
   phase-08, không phải lỗi phase-09), nút quay lại "▶ Mô phỏng lộ trình" (đã hạ `isSimulating`).

## Gate G4

**Từ lúc bấm tới thông báo "Đã rời":** `simulation_started` (t+0, ~14ms sau tap) →
`notification_posted type=EXIT` (t+25.4s). **~25.4 giây, dưới trần 40 giây ~15 giây margin.**
Đo trên **`emulator-5554`** — brief môi trường phiên này giới hạn "Chỉ emulator-5554", không có
quyền truy cập thiết bị demo thật (Samsung SM-A165F). Theo đúng tiền lệ G5 ở phase-07 ("G5 HOÃN —
chờ máy thật"), số này CHƯA phải số cuối cùng cho `gate-evidence.md` — cần đo lại trên máy thật ở
phase-11.

## Gate G7 — không toạ độ thật trong log release

`adb logcat -d --pid=<app pid>` lọc theo PID của app: **0 dòng khớp** `10\.[0-9]{4}|106\.[0-9]{4}`.
Lệnh literal trong brief (`adb logcat -d | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"`, không lọc PID)
trả về 2 dòng, nhưng cả hai thuộc PID `4930` = `com.google.android.gms.persistent` (tiến trình
Play Services hệ thống, tag `Geofencer`), KHÔNG phải app này — log nội bộ của GMS Core, ngoài tầm
kiểm soát của code trong repo, xuất hiện với BẤT KỲ app nào dùng Geofencing API. Mọi dòng
`FTD_EVENT` của app (đã audit thủ công từng `Log.d`/`Log.w` trong `:data`) chỉ log
`accuracy`/`reason`/`durationMs`/`eventsRaised`/`zoneId` (UUID) — không bao giờ `latitude`/`longitude`.

## File tạo

- `domain/src/main/kotlin/.../domain/tracking/RouteBlueprint.kt` — `SimulatedFix` + `RouteBlueprint.build()`, hàm thuần
- `domain/src/test/kotlin/.../domain/tracking/RouteBlueprintTest.kt` — 6 test (gồm 1 test "known limitation" ghim rõ giới hạn bán kính lớn)
- `domain/src/main/kotlin/.../domain/usecase/StartSimulationUseCase.kt`
- `domain/src/test/kotlin/.../domain/usecase/StartSimulationUseCaseTest.kt` — 6 test (gồm 1 test khoá `notifyInitialState=false`)
- `ui/src/main/java/.../ui/feature/history/component/SimulateRouteButton.kt`

## File sửa

- `domain/src/main/kotlin/.../domain/repository/TrackingRepository.kt` — thêm `runSimulation(fixes)`
- `domain/src/main/kotlin/.../domain/repository/GeofenceGateway.kt` — thêm `notifyInitialState: Boolean = true`
- `domain/src/main/kotlin/.../domain/usecase/SaveZoneUseCase.kt` — truyền `notifyInitialState` xuống
- `data/src/main/java/.../data/location/SimulatedLocationSource.kt` — điền thân thật
- `data/src/main/java/.../data/location/LocationTrackingService.kt` — `ACTION_SIMULATE`, companion `isSimulating`
- `data/src/main/java/.../data/location/LocationPointProcessor.kt` — counter `zoneEventsRaised`/`consumeZoneEventsRaised()` (additive)
- `data/src/main/java/.../data/repository/TrackingRepositoryImpl.kt` — impl `runSimulation`
- `data/src/main/java/.../data/geofence/GeofenceRegistrar.kt` — `initialTrigger = 0` khi `notifyInitialState=false`
- `data/src/main/java/.../data/di/DataModule.kt` — `SimulatedLocationSource` singleton chia sẻ (concrete type + alias qualifier), thêm param `TrackingRepositoryImpl`
- `ui/src/main/java/.../ui/feature/history/HistoryContract.kt` — cập nhật KDoc `StartSimulation`
- `ui/src/main/java/.../ui/feature/history/HistoryViewModel.kt` — `onStartSimulation()` thật
- `ui/src/main/java/.../ui/feature/history/HistoryScreen.kt` — thêm `SimulateRouteButton` ở cuối màn (luôn hiện, kể cả empty-day)
- `ui/src/main/java/.../ui/di/UiModule.kt` — đăng ký `StartSimulationUseCase`
- `ui/src/main/res/values/strings.xml` — 2 chuỗi mới (xem "Sai lệch" — KHÔNG phải `app/strings.xml`)
- `app/build.gradle.kts` — `buildConfigField("boolean", "SIMULATOR_ENABLED", "true")` trong `defaultConfig`
- `app/src/main/java/.../FamilyTrackerApp.kt` — `simulatorConfigModule` (Koin `named("simulatorEnabled")`)
- `LLM.md` §3, §6, §8.4, §13 (Open #4, Fixed #17)
- `plans/.../phase-09-route-simulator.md` — Todo List, status
- `plans/.../plan.md` — dòng phase 09

**Test fakes cập nhật** (cascading từ 2 interface change — `TrackingRepository.runSimulation`, `GeofenceGateway.register`):
`data/src/test/.../LocationPointProcessorTest.kt`,
`ui/src/test/.../map/MapViewModelTest.kt`, `ui/src/test/.../map/MapViewModelLaunchSafetyTest.kt`,
`ui/src/test/.../history/HistoryViewModelTest.kt` (+ 4 test US-33 mới, thay test no-op cũ),
`ui/src/test/.../zone/ZoneListViewModelTest.kt`, `ui/src/test/.../zone/ZoneEditorViewModelTest.kt`,
`domain/src/test/.../usecase/DeleteZoneUseCaseTest.kt`,
`domain/src/test/.../usecase/SaveZoneUseCaseTest.kt` (+ 2 test mới khoá passthrough `notifyInitialState`).

## Sai lệch so với file phase (kèm lý do)

1. **`TrackingRepository.runSimulation()` — method mới, KHÔNG có trong "Related Code Files" của phase-09.**
   `StartSimulationUseCase` (`:domain`) không có `Context`/`Intent` để đánh thức
   `LocationTrackingService` trực tiếp (LLM.md §2); `:ui` cũng không thấy `:data` (§2).
   `TrackingRepository`, interface DUY NHẤT đã băng qua ranh giới đó cho `setTracking`, là cổng
   khả thi duy nhất. Cùng loại sai lệch với `ZoneRepository.exists` (§13 Fixed #8).

2. **`GeofenceGateway.register()` — thêm tham số `notifyInitialState`, `SaveZoneUseCase` truyền
   thẳng xuống — KHÔNG có trong Related Code Files.** Phát hiện bằng chạy thật (xem mục "Một phát
   hiện quan trọng" ở trên), không phải trong kế hoạch ban đầu. Đây là sai lệch LỚN NHẤT của phase
   này về mặt số file bị kéo theo (6 test fake phải thêm tham số).

3. **`app/src/main/res/values/strings.xml` — phase file liệt kê file này cho 2 chuỗi mới, nhưng
   tôi đặt chúng ở `ui/src/main/res/values/strings.xml`.** Nút vẽ ra bởi `:ui`
   (`SimulateRouteButton`), và LLM.md §12 (đã sửa từ phase-04) + chính header của `ui/strings.xml`
   nói rõ "mỗi module giữ strings.xml của chính chuỗi nó vẽ ra". `app/strings.xml` chỉ còn đúng
   `app_name` — giữ nguyên.

4. **`LocationPointProcessor.kt` — thêm counter, KHÔNG có trong Related Code Files.** Cần để log
   `eventsRaised=N` cho `simulation_finished` (PRD §10) mà không query lại Room. Thuần additive.

5. **Gate G4 đo trên `emulator-5554`, KHÔNG phải thiết bị demo thật.** Brief môi trường phiên này
   giới hạn "Chỉ emulator-5554". Theo đúng tiền lệ G5 phase-07, cần đo lại trên máy thật ở phase-11.

## Known limitation (LLM.md §13 Open #4, không sửa ở phase này)

`RouteBlueprint` dùng `pointCount=20`/`totalMillis=30_000` cố định — với zone bán kính gần
`ZONE_RADIUS_MAX_M` (2000m), tốc độ suy ra giữa 2 điểm liên tiếp vượt `MAX_SPEED_KMH` (200), khiến
`LocationFilter` loại một số điểm vì lý do SPEED (không phải lỗi định tuyến). Zone demo mặc định
150m an toàn — breakeven ở ~683m. Không sửa vì đánh đổi cấu trúc: co buffer nhỏ lại rủi ro không
cắt đủ sâu qua exit hysteresis; kéo dài thời lượng thì vượt trần G4 cho zone đủ lớn.
`RouteBlueprintTest`'s test "known limitation" ghim hành vi này lại.

## Xác nhận thêm: rời màn hình giữa chừng, mô phỏng vẫn chạy hết (thao tác tay thật)

Bấm "Mô phỏng" rồi **1.57 giây sau** chuyển sang tab "Bản đồ" (History bị rời, `HistoryViewModel`
khả năng cao bị `onCleared()`). `location_recorded` vẫn tiếp tục log đều đặn (~1.5-1.6s/điểm)
suốt ~30 giây tiếp theo, không có tương tác nào khác từ tôi, kết thúc bằng
`simulation_finished durationMs=30151 eventsRaised=2` — xác nhận đúng kiến trúc: vòng lặp thật
sống trong `LocationTrackingService.scope` (không phải `viewModelScope`), không bị huỷ khi rời màn hình.

**Phát hiện phụ, chưa sửa (rủi ro thấp, ghi vào LLM.md §13 Fixed #17 "rủi ro còn lại"):** lần chạy
này app vừa được mở lại (`FamilyTrackerApp.onCreate` gọi `registerAll()` — LUÔN giữ
`INITIAL_TRIGGER_ENTER or EXIT`, cố ý, cần cho zone thật sau reboot). `registerAll()`'s EXIT tức
thời (zone đã có sẵn từ test trước) dedupe-collide với EXIT của lộ trình mô phỏng lần này
(`zone_event_deduped ... gapMs=42880`) — CÙNG cơ chế đã sửa ở Fixed #17, nhưng qua đường
`registerAll()` (app khởi động) thay vì `register()` (tạo zone mẫu). Xác nhận qua DB (pull đúng
quy trình WAL: force-stop → pull `.db`+`-wal`+`-shm` → `PRAGMA wal_checkpoint(TRUNCATE)` → đọc):
4 dòng `zone_events` — 2 dòng từ lần test sạch trước (ENTER+EXIT), rồi EXIT của `registerAll()`,
rồi ENTER của lộ trình lần này (EXIT của lộ trình lần này bị dedupe, đúng như log). Không sửa vì
đổi `registerAll()` sẽ ảnh hưởng zone THẬT sau reboot (ngoài scope US-33); rủi ro thấp cho demo
thật vì người trình bày tự nhiên sẽ thao tác vài phút trước khi bấm Simulate, vượt xa cửa sổ 60s.

## Việc còn dở / chưa xác minh trên máy thật

- Gate G4 cần đo lại trên thiết bị demo thật (Samsung SM-A165F) ở phase-11 — số trên emulator có
  margin lớn (~15s) nên rủi ro fail trên máy thật thấp, nhưng chưa là bằng chứng cuối cùng.
- "Rủi ro còn lại" của Fixed #17 (mục ngay trên) — cân nhắc nếu phase-11 muốn loại bỏ hẳn: có thể
  thêm hướng dẫn demo "mở app xong đợi > 1 phút rồi mới bấm Simulate lần đầu", hoặc sửa sâu hơn
  (ví dụ so sánh `occurredAt` của initial-trigger events với thời điểm zone được TẠO thay vì chỉ
  dựa vào type/window) — không làm ở phase-09 vì ngoài scope US-33 và rủi ro thấp.
