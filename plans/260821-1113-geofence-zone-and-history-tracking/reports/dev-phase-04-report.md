# Dev Report — Phase 04: Quyền 3 bước, nav shell, foreground service, LocationSource

Ngày: 2026-08-21 · Agent: fullstack-developer (dev) · Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`

## Tóm tắt

Toàn bộ Implementation Steps + Todo List phase-04 đã xong. Tests xanh, `assembleRelease` xanh,
G6 = 1 warning (khớp baseline). Chạy thật trên `emulator-5554` (API 37.1) toàn bộ luồng: 3 bước
xin quyền → Map → bật công tắc → foreground service thật → bơm GPS → điểm ghi vào Room qua đúng
`LocationFilter` → tắt công tắc trong ≤2s. **Phát hiện và sửa 2 bug thật khi chạy thiết bị**
(không nằm trong đặc tả gốc) — chi tiết ở mục riêng bên dưới.

## Luồng quyền 3 bước — thực tế trên API 37.1

Xác nhận từng bước bằng `dumpsys package ... | grep -A6 "runtime permissions"` + logcat, không
tin vào UI:

| Bước | Dialog hệ thống thật | Logcat |
|---|---|---|
| 1. POST_NOTIFICATIONS | Dialog chuẩn Android 13+ (Allow/Don't allow) | `permission_result type=NOTIFICATIONS granted=true` |
| 2. ACCESS_FINE_LOCATION | Dialog kiểu mới: Precise/Approximate + While using/Only this time/Don't allow | `permission_result type=FINE_LOCATION granted=true` |
| 3. ACCESS_BACKGROUND_LOCATION | KHÔNG dialog — mở `ACTION_APPLICATION_DETAILS_SETTINGS`, người dùng tự chọn "Allow all the time" trong Permissions > Location | `permission_result type=BACKGROUND_LOCATION granted=true` (chỉ log SAU khi quay lại app, không phải lúc vào bước 3) |

`dumpsys package com.example.pion.family.tracker.demo` cuối cùng:
```
android.permission.POST_NOTIFICATIONS: granted=true
android.permission.ACCESS_FINE_LOCATION: granted=true
android.permission.ACCESS_COARSE_LOCATION: granted=true
android.permission.ACCESS_BACKGROUND_LOCATION: granted=true
```

Không khác tài liệu (LLM.md §10, researcher-01 §2) về THỨ TỰ và HÌNH DẠNG dialog. Khác tài liệu ở
**hành vi của `Lifecycle.addObserver()`** — xem "Bug #2" bên dưới.

## Vòng đời foreground service — thực tế

`dumpsys activity services` khi bật công tắc:
```
isForeground=true foregroundId=1001 types=0x00000008
foregroundNoti=Notification(channel=location_tracking ... flags=ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE ...)
```
`types=0x8` = `FOREGROUND_SERVICE_TYPE_LOCATION` — đúng loại khai trong manifest. Notification
thường trực "Đang theo dõi vị trí" xuất hiện trong shade dưới mục "Silent" (kênh `IMPORTANCE_LOW`
đúng thiết kế). Tắt công tắc: `dumpsys activity services | grep -c LocationTrackingService` → `0`
trong vòng ~1s (đo bằng lệnh chạy ngay sau tap, không đợi timeout) — thoả `≤ 2 giây`.

## Bằng chứng `lastKeptPoint` threading đúng

Luật ở `LocationPointProcessor.process()` (`data/location/LocationPointProcessor.kt`): so điểm
mới với `lastKeptPoint` (field riêng của processor), chỉ gán lại field đó trong nhánh
`FilterResult.Accept`. Service không tự giữ biến này — nó chỉ gọi `processor.process(point, zones,
memberId)` mỗi điểm nhận từ `LocationSource.stream()`.

**Test khoá luật:** `data/src/test/java/.../data/location/LocationPointProcessorTest.kt` —
`` `a slow walker keeps points measured against the last KEPT point, not the last seen point` ``
+ `` `lastKeptPoint is never updated on a Reject` ``. Mô phỏng đúng kịch bản 5 bước 9m/bước của
`LocationFilterTest` (đã có ở phase-03) qua `processor.process()` thay vì gọi thẳng
`LocationFilter.accept()`.

**Đỏ-rồi-xanh xác nhận bằng mutation thật** (không suy luận):
```
$ sed thay `if (result is Accept) { lastKeptPoint = point; ... }`
       -> `lastKeptPoint = point; if (result is Accept) { ... }`   # cập nhật ở MỌI nhánh
$ ./gradlew :data:test --no-configuration-cache
LocationPointProcessorTest > lastKeptPoint is never updated on a Reject FAILED
LocationPointProcessorTest > a slow walker ... FAILED
2 tests completed, 2 failed
$ (khôi phục file gốc)
$ ./gradlew :data:test --no-configuration-cache
BUILD SUCCESSFUL   # xanh lại
```
Cả 2 test đỏ đúng khi luật bị phá, xanh lại khi khôi phục — khoá được luật thật, không phải test
giả xanh vô điều kiện.

## Hai bug thật phát hiện khi chạy thiết bị (không có trong đặc tả gốc)

Cả hai đều bắt được bằng chạy thật trên `emulator-5554`, không suy luận từ đọc code. Đã sửa và
xác nhận lại bằng chạy thật lần hai. Ghi chi tiết vào `LLM.md` §8.5, §10, §13 Fixed #6/#7.

### Bug #1 — bật công tắc theo dõi khi thiếu quyền vị trí crash cả app

**Triệu chứng thật:**
```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: java.lang.RuntimeException: Unable to start service ...LocationTrackingService
E AndroidRuntime: Caused by: java.lang.SecurityException: Starting FGS with type location
    callerApp=... targetSDK=36 requires permissions: all of the permissions
    [FOREGROUND_SERVICE_LOCATION] and any of the permissions [ACCESS_COARSE_LOCATION,
    ACCESS_FINE_LOCATION] and the app must be in the eligible state/exemptions ...
    at android.app.Service.startForeground(Service.java:775)
    at ...LocationTrackingService.onStartCommand(LocationTrackingService.kt:55)
```
Xảy ra khi: US-02 bỏ qua vị trí lúc onboarding → Map ở chế độ giảm chức năng (đúng thiết kế) →
người dùng vẫn bấm được công tắc theo dõi (không có gì chặn UI) → crash. `assembleRelease` xanh
trước khi sửa — chỉ nổ lúc chạm nút trước mặt khách. Tài liệu đọc trước phase (LLM.md §10 cũ,
researcher-01 §3.3) chỉ nói `foregroundServiceType` thiếu gây crash lúc chạy — không nói
`startForeground(type=location)` còn tự kiểm tra CẢ quyền vị trí runtime.

**Sửa:** `TrackingRepositoryImpl.setTracking(true)` kiểm `ACCESS_FINE_LOCATION` bằng
`ContextCompat.checkSelfPermission` trước khi gọi `ContextCompat.startForegroundService()`;
thiếu quyền → bỏ qua, log `FTD_EVENT tracking_toggle_ignored reason=NO_LOCATION_PERMISSION`,
không gọi service.

**Xác nhận lại bằng chạy thật:** fresh install → skip vị trí ở onboarding → Map degraded → bấm
công tắc → logcat `tracking_toggled enabled=true` rồi `tracking_toggle_ignored
reason=NO_LOCATION_PERMISSION`, KHÔNG có `FATAL EXCEPTION`; `adb shell ps -A | grep
familytracker` xác nhận pid không đổi (process sống); switch trên UI giữ nguyên trạng thái tắt
(vì `isTracking()` đọc `LocationTrackingService.isRunning` thật, service chưa từng chạy).

### Bug #2 — onboarding bị nhảy cóc thẳng ra Map ngay khi mở app lần đầu

**Triệu chứng thật:** cài mới, mở app → logcat có ngay `permission_result
type=BACKGROUND_LOCATION granted=false` trong vòng 100ms sau `am start`, **trước khi có bất kỳ
tương tác nào** — màn hình nhảy thẳng ra Map thay vì hiện trang giải thích bước 1.

**Nguyên nhân:** `Lifecycle.addObserver()` phát lại NGAY LẬP TỨC các sự kiện "bắt kịp"
(ON_CREATE/ON_START/ON_RESUME) nếu lifecycle đã ở RESUMED tại thời điểm đăng ký — đúng trường
hợp `LocationPermissionFlow`'s observer, vì composable chỉ mount SAU khi `MainActivity` đã
resume. Không phòng bị, observer nhận một `ON_RESUME` "bắt kịp" giả ngay lần mount đầu, và vì
`PermissionViewModel.onPermissionResolved(BACKGROUND_LOCATION, ...)` luôn `sendEffect(GoToMap)`
bất kể bước hiện tại, toàn bộ luồng bị nhảy cóc. Hành vi này không được ghi ở bất kỳ tài liệu
nào đọc trước phase (MVI doc, LLM.md, researcher-01).

**Sửa hai lớp** (`ui/permission/LocationPermissionFlow.kt`):
1. Cờ `isFirstResume` bỏ qua đúng một lần ON_RESUME "bắt kịp" đầu tiên.
2. Gác thêm theo `currentStep` (tham số mới của `rememberLocationPermissionFlow`) — dialog hệ
   thống của bước 1/2 (POST_NOTIFICATIONS, FINE_LOCATION) cũng che app rồi trả về foreground,
   bắn ON_RESUME THẬT (không phải bắt kịp) mà không phải do quay từ Settings. Không có lớp này,
   lớp 1 một mình vẫn sai ở dialog thứ hai trở đi — chỉ đọc lại quyền background khi
   `currentStep == BACKGROUND_LOCATION`.

**Xác nhận lại bằng chạy thật:** `pm clear` → mở app → screenshot đúng trang bước 1 (không nhảy
cóc) → đi hết cả 3 bước qua dialog thật → `permission_result type=BACKGROUND_LOCATION` chỉ log
đúng MỘT lần, đúng lúc quay lại từ Settings (không phải lúc mount).

## Nav shell

`FamilyTrackerNavHost` chọn `startDestination` bằng `context.currentPermissionStatus().hasUsableLocation`
(= `fineLocationGranted`) đọc trực tiếp hệ thống lúc composition đầu — không lưu cờ (Implementation
Step 6). `ZoneListRoute`/`ZoneEditorRoute`/`HistoryRoute`/`TimelineRoute` là `Text("... — phaseXX")`
placeholder. **Trùng tên có chủ ý** giữa route type `MapRoute` (`ui/navigation/Routes.kt`) và
composable `MapRoute` (`ui/feature/map/MapScreen.kt`, đúng quy ước `XRoute` stateful) — giải quyết
bằng import alias `import ...feature.map.MapRoute as MapScreenRoute` trong NavHost. Ghi lại ở
`LLM.md` §7 vì mọi phase sau (05→10) sẽ gặp lại đúng bẫy đặt tên này.

## File tạo mới

**`:data`**
- `location/FusedLocationSource.kt` (67 dòng) — `LocationSource` thật, `PRIORITY_HIGH_ACCURACY`, 10s, `minUpdateDistance` 10m, tự đóng flow nếu thiếu quyền
- `location/SimulatedLocationSource.kt` (17 dòng) — thân rỗng, Koin qualifier `named("simulated")` đăng ký sẵn (phase-09)
- `location/LocationPointProcessor.kt` (74 dòng) — lọc → ghi Room → đánh giá zone → ghi event; giữ `lastKeptPoint` + `insideZoneIds`; Android-free, test được JVM thuần
- `location/LocationTrackingService.kt` (110 dòng) — foreground service, chỉ nối dây
- `location/TrackingNotification.kt` (61 dòng) — kênh + nội dung thông báo thường trực
- `src/main/res/values/strings.xml`, `src/main/res/drawable/ic_location_tracking.xml` — chuỗi/icon riêng của `:data` (xem "Sai lệch" bên dưới)
- `src/test/java/.../data/location/LocationPointProcessorTest.kt` (120 dòng) — 2 test, khoá luật `lastKeptPoint`

**`:ui`**
- `navigation/Routes.kt` (37 dòng), `navigation/FamilyTrackerNavHost.kt` (50 dòng)
- `permission/LocationPermissionFlow.kt` (121 dòng), `permission/PermissionStatus.kt` (45 dòng)
- `feature/permission/PermissionContract.kt` (38 dòng), `PermissionViewModel.kt` (56 dòng), `PermissionScreen.kt` (87 dòng)
- `feature/map/MapContract.kt` (32 dòng), `MapViewModel.kt` (40 dòng), `MapScreen.kt` (91 dòng), `component/PermissionBanner.kt` (35 dòng, `internal`)
- `src/main/res/values/strings.xml` — chuỗi mọi màn hình (xem "Sai lệch" bên dưới)
- `src/test/java/.../ui/feature/permission/PermissionViewModelTest.kt` (154 dòng) — 10 test: reducer mỗi Intent, effect mỗi Effect, `ScreenResumed` no-op

## File sửa

- `app/src/main/AndroidManifest.xml` — 7 quyền (thêm `RECEIVE_BOOT_COMPLETED`), khai `<service>` `foregroundServiceType="location" exported="false"`
- `app/src/main/java/.../MainActivity.kt` — `setContent { FamilyTrackerDemoTheme { FamilyTrackerNavHost() } }`
- `data/src/main/java/.../data/di/DataModule.kt` — 2 `LocationSource` qualifier, `LocationPointProcessor`, `TrackingRepositoryImpl` nhận thêm `Context` qua `androidContext()`
- `data/src/main/java/.../data/local/dao/ZoneEventDao.kt` — thêm `latestPerZone(memberId)` (nạp lại `insideZoneIds` khi service khởi động, Step 9)
- `data/src/main/java/.../data/repository/TrackingRepositoryImpl.kt` — `isTracking()`/`setTracking()` lái/đọc thẳng `LocationTrackingService`; chặn crash Bug #1
- `data/build.gradle.kts` — thêm `testImplementation(junit, kotlinx-coroutines-test)` (chưa từng có JVM test ở `:data` trước phase này)
- `ui/src/main/java/.../ui/di/UiModule.kt` — đăng ký `PermissionViewModel`, `MapViewModel`
- `app/src/test/java/.../KoinModulesTest.kt` — `verify(extraTypes = listOf(Context::class))` (xem "Sai lệch")
- `LLM.md` §3, §6, §7, §8.3, §8.5, §10, §11, §12, §13 — xem mục Cập nhật tài liệu
- `plans/.../phase-04-permissions-and-tracking-service.md`, `plan.md` — tick Todo List, status `completed`

## Output lệnh thật (chạy tuần tự, không xen kẽ giả định)

```
$ ./gradlew test
BUILD SUCCESSFUL in 2s   # :domain, :data (mới có JVM test), :ui, :app đều xanh

$ ./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
1                        # khớp baseline ENV-BRIEFING.md §8

$ ./gradlew assembleRelease
BUILD SUCCESSFUL in 5s

$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Success

$ adb -s emulator-5554 shell dumpsys package com.example.pion.family.tracker.demo | grep -A6 "runtime permissions"
POST_NOTIFICATIONS: granted=true
ACCESS_FINE_LOCATION: granted=true
ACCESS_COARSE_LOCATION: granted=true
ACCESS_BACKGROUND_LOCATION: granted=true

$ adb -s emulator-5554 shell dumpsys activity services com.example.pion.family.tracker.demo | grep -E "isForeground|foregroundNoti"
isForeground=true foregroundId=1001 types=0x00000008 foregroundNoti=Notification(channel=location_tracking ...)

$ adb -s emulator-5554 emu geo fix 106.7009 10.7769   # rồi 106.7020 10.7780
$ adb -s emulator-5554 logcat -d | grep FTD_EVENT
permission_result type=NOTIFICATIONS granted=true
permission_result type=FINE_LOCATION granted=true
permission_result type=BACKGROUND_LOCATION granted=true
tracking_toggled enabled=true
location_recorded accuracy=5.0 filtered=false
location_recorded accuracy=5.0 filtered=false
tracking_toggled enabled=false

$ adb -s emulator-5554 logcat -d | grep -i "FTD_EVENT" | grep -E "10\.7|106\.7"
(rỗng — không có toạ độ nào bị log, gate G7)
```

**Kiểm DB thật (phải dùng build debug tạm thời để `run-as` — release không debuggable, đúng thiết
kế PRD §7.2, không sửa được).** Cài `app-debug.apk` đè lên, `pm grant` thủ công 4 quyền, lặp lại
bơm GPS, rồi:
```
$ adb shell run-as com.example.pion.family.tracker.demo cat databases/family_tracker.db(-wal/-shm) > ...
$ sqlite3 family_tracker.db "SELECT id, memberId, latitude, longitude, accuracyMeters, recordedAt FROM location_points;"
c1a910f6-... | a13130a9-... | 10.7801210304902 | 106.700080466975 | 15.0 | ...   # DemoDataSeeder (Minh)
4c105133-... | 3f501ddc-... | 10.7678996652466 | 106.708573382206 | 15.0 | ...   # DemoDataSeeder (Lan)
948368d2-... | 3d69393c-... | 10.778            | 106.7019983      | 5.0  | ...   # điểm thật vừa bơm — khớp geo fix
```
Sau khi xác nhận, cài lại `app-release.apk` (`adb install -r`) để trạng thái máy khớp bản đem
demo — đã làm.

## Sai lệch so với đặc tả gốc, có lý do

1. **`res/strings.xml` không nằm ở `:app` như LLM.md cũ ghi, mà ở module vẽ ra chuỗi đó (`:ui`
   cho màn hình, `:data` cho thông báo service).** `:app` là module DUY NHẤT phụ thuộc `:ui`/`:data`
   (LLM.md §2), nên đặt string ở `:app` như bản gốc tài liệu ghi khiến `:ui`/`:data` không build
   được — không có cách nào compose trong `:ui` hay `Notification` dựng ở `:data` thấy được `R`
   của `:app`. Đây là gap thật của tài liệu (chưa có screen/service nào trước phase-04 cần string),
   không phải lỗi implementation. Đã sửa LLM.md §12 (bảng) và §3 (thêm ghi chú `res/` riêng từng
   module) trong cùng commit.
2. **`KoinModulesTest.verify()` cần `extraTypes = listOf(Context::class)`** vì `TrackingRepositoryImpl`
   giờ nhận `Context` qua `androidContext()` trong lambda `single { }`, không qua `get()` — `verify()`
   phân tích tĩnh qua constructor thật, không chạy lambda, nên không "thấy" `androidContext()`.
   Đây đúng tham số Koin tài liệu chính thức dành cho trường hợp này (`VerifyModule.kt` KDoc), không
   phải workaround riêng. File không nằm trong "Related Code Files" gốc của phase-04 nhưng bắt buộc
   phải sửa để `./gradlew test` xanh — ghi lại đây thay vì bỏ qua.
3. **`ZoneEventDao.latestPerZone(memberId)` là query mới**, không có trong PRD §8 hay phase-02/03.
   Cần cho Implementation Step 9 (nạp lại `insideZoneIds` khi service khởi động). File không nằm
   trong "Related Code Files" gốc của phase-04 nhưng là phần mở rộng tự nhiên, tối thiểu, để hoàn
   thành đúng yêu cầu step đó.
4. **`PermissionBanner` đặt ở `feature/map/component/` (`internal`), KHÔNG ở `designsystem/component/`**
   dù về ngữ nghĩa nó là banner chung. LLM.md §12: một composable chỉ lên `designsystem/component/`
   khi THẬT SỰ có ≥2 feature dùng — ở phase-04 chỉ Map dùng. Chuyển lên khi phase-06/07 có màn thứ
   hai cần banner tương tự (tự sửa lần đầu tiên viết code, phát hiện lúc soạn báo cáo này — không
   phải do review sau).
5. **Bước 3 (background location) luôn mở Settings, kể cả API 28–29** dù về lý thuyết một dialog
   thường vẫn xin được ở dải API đó (researcher-01 §2.1). Chọn một đường duy nhất cho mọi API level
   vì (a) đơn giản hơn, (b) mọi thiết bị test ở ENV-BRIEFING.md đều API 30+, (c) khớp đúng 4 Effect
   phase-04 khai (`RequestNotifications`, `RequestFineLocation`, `OpenAppSettings`, `GoToMap` — không
   có Effect thứ 5 riêng cho dialog thường).

## Chỗ còn dở, nói thẳng

- **Chưa test trên máy thật `RF8Y60B9NCZ`** — đúng theo chỉ đạo, máy thật để dành G5 (phase-07/11).
- **`ZoneListRoute`/`ZoneEditorRoute`/`HistoryRoute`/`TimelineRoute` chỉ là `Text(...)` placeholder** —
  đúng phạm vi phase-04, các phase 05/06/08/10 sẽ thay từng nhánh.
- **Chưa kiểm chứng hành vi khi OEM Trung Quốc kill FGS** (Xiaomi/Oppo/Vivo) — theo đúng Risk
  Assessment, chấp nhận rủi ro demo trên Pixel/Samsung, không phải việc code sửa được.
- **`insideZoneIds` reload từ `ZoneEventDao.latestPerZone` chưa có test riêng** — logic đơn giản
  (filter + map trên list đã test qua DAO ở phase-02 pattern), nhưng chưa viết instrumented test
  xác nhận đúng zone "đang trong" được khôi phục sau restart service. Để phase-07 (khi geofence
  thật chạy song song) viết test tích hợp đầy đủ hơn cho luồng này.

## Không có câu hỏi treo.
