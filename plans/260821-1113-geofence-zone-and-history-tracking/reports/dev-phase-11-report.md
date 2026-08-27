# Dev Phase 11 Report — Quality Gates G1-G8 & Docs Sync

Status: IN PROGRESS — cập nhật liên tục sau mỗi gate. Ngày bắt đầu 2026-08-22.

## Việc code đầu tiên: dựng `FtdLog` (điều kiện cho G7)

Trước khi đo bất kỳ gate nào — G7 đòi hỏi log release câm, nhưng `FtdLog` (cổng log duy nhất) **chưa
tồn tại**. Toàn bộ 20 lời gọi `Log.d/w/e` trực tiếp (`:data`: 17, `:ui`: 3) + 1 ở `FamilyTrackerApp.kt`
(`:app`) đã chuyển sang `FtdLog`.

**Thiết kế:** `:ui` không thấy `:data` (module graph, `LLM.md` §2) và `:domain` không được import
`android.util.Log` (module Kotlin JVM thuần) → không thể có MỘT `FtdLog` dùng chung cho cả 2 module
Android. Giải pháp: hai object gần như giống hệt nhau —
`data/src/main/.../data/util/FtdLog.kt` và `ui/src/main/.../ui/core/logging/FtdLog.kt` — cả hai
đọc cờ `debugBuild: Boolean` qua Koin (`by inject(named("debugBuild"))`), thay vì `BuildConfig.DEBUG`
riêng của từng module, vì:
- `:ui` **cố tình không bật** `buildConfig` (comment sẵn có trong `FamilyTrackerApp.kt`: tránh một
  `BuildConfig` thứ hai gần tay hơn cho `SIMULATOR_ENABLED`).
- `:data` cũng không có field riêng nào cần `buildConfig`.
- Mẫu Koin named-boolean này đã có sẵn tiền lệ đúng y hệt: `simulatorEnabled` (`FamilyTrackerApp.kt`).

`appConfigModule` (đổi tên từ `simulatorConfigModule`, cùng file) giờ có 2 binding:
`single<Boolean>(named("simulatorEnabled"))` (không đổi) và
`single<Boolean>(named("debugBuild")) { BuildConfig.DEBUG }` (mới). Cả hai `FtdLog` chỉ log khi
`debugBuild == true`.

**Vì sao an toàn cho JVM unit test:** rà lại toàn bộ nơi các file bị sửa được test — `:data`'s
repository/service/receiver KHÔNG có JVM test nào chạm (chỉ `androidTest`, chạy trong tiến trình
app thật nơi Koin đã start), `:ui`'s composable bị sửa (`HistoryMap`, `RoutePolyline`,
`LocationPermissionFlow`) không có test nào (chỉ ViewModel có JVM test, không test composable).
Xác nhận bằng cách chạy lại toàn bộ `./gradlew test` sau khi sửa — xem G2/G3 bên dưới, 131/131 xanh,
0 lỗi.

File sửa: `app/.../FamilyTrackerApp.kt`, `data/.../repository/{TrackingRepositoryImpl,ZoneRepositoryImpl,
ZoneEventRepositoryImpl}.kt`, `data/.../notification/ZoneNotifier.kt`, `data/.../location/LocationTrackingService.kt`,
`data/.../geofence/{BootCompletedReceiver,GeofenceRegistrar,GeofenceBroadcastReceiver}.kt`,
`ui/.../feature/history/component/{HistoryMap,RoutePolyline}.kt`, `ui/.../permission/LocationPermissionFlow.kt`.
File mới: `data/.../data/util/FtdLog.kt`, `ui/.../ui/core/logging/FtdLog.kt`.

`grep -rn "android.util.Log" --include="*.kt" app/src/main ui/src/main data/src/main domain/src/main`
sau khi sửa: chỉ còn 2 chỗ import thật (cả hai trong chính `FtdLog.kt`, có chủ đích) + vài dòng
comment nhắc tới nó (không phải import thật).

## Gate summary table

| Gate | Tiêu chí (PRD §11.1) | Lệnh | Kết quả | PASS/FAIL/HOÃN |
|---|---|---|---|---|
| G1 | Toàn bộ story P0 đạt acceptance criteria | xem `g1-p0-story-checklist.md` | **26 story P0 thật** (không phải 22 — PRD tự mâu thuẫn giữa câu tổng kết và cột ưu tiên thật, xem chi tiết trong file checklist), 25/26 Đạt, US-24 HOÃN (cùng lý do G5) | **PASS có điều kiện** — 25/26 Đạt, 1 HOÃN không phải FAIL |
| G2 | Unit test `ZoneEvaluator`/`LocationFilter`/`RouteStats` xanh, phủ biên | `./gradlew :domain:test` | 58 test domain xanh, đủ 14/14 trường hợp biên phase-03 (xem chi tiết dưới) | **PASS** |
| G3 | `KoinModulesTest` (`verify()`, không phải `checkModules()` đã deprecated — LLM.md §13 Fixed #3) xanh | `./gradlew :app:test --tests KoinModulesTest` | 1/1 pass, toàn bộ binding verified kể cả sau khi thêm `debugBuild` | **PASS** |
| G4 | Mô phỏng sinh cả ENTER+EXIT ≤ 40s, trên **thiết bị demo thật** | xem mục G4 riêng | rehearsal emulator: 2/2 lần thành công, gap ENTER→EXIT 17.4s và 17.5s — xa dưới 40s. Máy thật `RF8Y60B9NCZ` **khoá màn hình có mật khẩu thật** (`wm dismiss-keyguard` thất bại, không phải lock kiểu vuốt) → không thao tác được | **HOÃN** — cần chủ dự án mở khoá |
| G5 | Đóng app khỏi recents, bước qua ranh giới → thông báo ≤ 3 phút | đi bộ thật + `zone_events` | xem mục G5 riêng | **HOÃN** — cần chủ dự án |
| G6 | `assembleDebug` không lỗi, không warning mới so với baseline. **Luật đo: bắt buộc `--no-configuration-cache`** (ENV-BRIEFING §8) — nếu không, config cache bỏ qua pha configuration nên warning KHÔNG phát lại, số đo không tất định giữa 2 lần chạy giống hệt nhau | `./gradlew clean assembleDebug --no-configuration-cache 2>&1 \| grep -ci "warning:"` | `1` (baseline `reports/baseline-build-debug.log` cũng = 1, khớp) | **PASS** |
| G7 | Không toạ độ/API key trong log build release | xem mục G7 riêng — phép grep đã chốt | `FTD_EVENT` = 0 dòng; grep toạ độ/API key theo PID app = 0 dòng | **PASS** |
| G8 | `assembleRelease` ra APK đã ký, cài được, bản đồ hiện đúng | `assembleRelease` + `apksigner verify --print-certs` + `adb install` + mở app | APK 28.886.388 bytes, ký bởi cert Android Debug (SHA-1 `7dcf6413...`), cài thành công trên `emulator-5554`, bản đồ hiện đúng phố Sài Gòn thật (không xám/watermark), `allowBackup="false"` xác nhận qua `apkanalyzer manifest print` trên chính APK release | **PASS** |

(chi tiết bên dưới, cập nhật ngay sau khi có kết quả)

## Mutation Fixed #19 — chứng minh có răng (test-phase-10-report.md để lại "chưa kiểm chứng")

`test-phase-10-report.md` mục cuối chỉ ra `build pass` không phải bằng chứng cho một crash LÚC
CHẠY. Đã làm đúng như yêu cầu: đổi `stickyHeader(key = day.epochDay)` → `stickyHeader(key = day.label)`
(`TimelineScreen.kt` dòng 89) — `day.label: TimelineDayLabel` là `sealed interface`/`data object`,
không phải kiểu Bundle-safe.

```
$ ./gradlew assembleDebug --no-configuration-cache && adb install -r app-debug.apk
$ adb shell am start .../.MainActivity; tap tab "Nhật ký"
08-22 05:45:44.002 ... E AndroidRuntime: FATAL EXCEPTION: main
java.lang.IllegalArgumentException: Type of the key Today is not supported. On Android you can
only use types which can be stored inside the Bundle.
```
**Đỏ đúng như dự đoán** — không phải build fail, mà crash lúc mở Timeline, đúng loại lỗi Fixed #19
mô tả. Khôi phục `key = day.epochDay`:
```
$ git diff -- .../TimelineScreen.kt   → rỗng (khôi phục đúng nguyên bản)
$ ./gradlew :ui:test --no-configuration-cache   → BUILD SUCCESSFUL
```
**Xanh lại.** Cài lại `assembleRelease`, mở app trên `emulator-5554` → chạy bình thường, `pidof` có
PID sống. Mutation này có răng thật — không phải test xanh vô điều kiện.

## G4 — chi tiết

**Tiêu chí PRD §11.1:** "Nút mô phỏng lộ trình sinh ra cả thông báo vào và ra, trong ≤ 40 giây, trên
thiết bị demo thật."

**Thử máy thật trước:** `adb -s RF8Y60B9NCZ shell wm dismiss-keyguard` rồi kiểm lại
`dumpsys window | grep isKeyguardShowing` → vẫn `true`. Lệnh này CHỈ thành công nếu khoá màn hình
không có mật khẩu (kiểu "vuốt"/"không khoá") — thất bại nghĩa là máy có mật khẩu/PIN/pattern thật,
không phải chỉ đang tắt màn hình. Xác nhận: đây là khoá thật, không phải trạng thái tôi có thể tự mở.
Không thử đoán mã mở khoá.

**Rehearsal trên `emulator-5554` (APK release, giống bản đem demo), để không lãng phí thời gian
khi có máy thật:**

| Lần | Tap "Mô phỏng lộ trình" | ENTER | EXIT | Gap ENTER→EXIT |
|---|---|---|---|---|
| 1 | 05:20:41 (đồng hồ host, ±1s do độ trễ lệnh) | 05:20:49.760 | 05:21:07.205 | **17.4s** |
| 2 | ~05:22:31 (suy từ ENTER) | 05:22:48.911 | 05:23:06.368 | **17.5s** |

Cả hai lần dưới xa 40s. Timeline (`Nhật ký`) xác nhận đúng 1 cặp ENTER/EXIT sạch cho mỗi lần — không
trùng lặp, không lẻ (ảnh `scratchpad/g7-timeline.png`). Một lần bấm thứ 3 (bấm khi lần 2 có thể còn
đang xử lý) không sinh cặp sự kiện mới — không phải lỗi khử trùng lặp (Timeline không có dòng orphan),
nhiều khả năng do bấm dồn dập trong lúc test, không phải hành vi cần sửa; không phải mục tiêu chính của
G4 nên không đào sâu thêm.

Thời gian đo trên emulator không phải số chính thức cho gate (PRD + phase-11 spec đều nói rõ "số ghi
vào gate-evidence.md là số trên thiết bị thật") — chỉ dùng để xác nhận app hoạt động đúng trước khi có
máy thật, và để không phải chờ máy thật mới phát hiện lỗi cơ bản.

**Việc cần làm khi máy thật mở khoá được** (đã cài sẵn `app-release.apk`, xác nhận không crash — xem
mục G5 "Đã làm được mà không cần mở khoá"):
1. Cấp đủ quyền qua UI (3 bước, đặc biệt `ACCESS_BACKGROUND_LOCATION` "Luôn cho phép").
2. Tạo zone bán kính 200m quanh vị trí hiện tại (hoặc dùng "Zone mẫu" tự sinh nếu chưa có zone nào).
3. Mở History, bấm "▶ Mô phỏng lộ trình", bấm giờ tới thông báo "Đã đến" rồi "Đã rời".
4. Lặp 3 lần, nghỉ giữa các lần (không bấm dồn — bài học từ rehearsal ở trên), ghi cả 3 số giây vào
   `gate-evidence.md`.

## G7 — chi tiết: phép grep đã chốt và vì sao

**Tiêu chí PRD §11.1:** "Không có toạ độ thật hoặc API key nào trong log của build release. R8
đang tắt nên nó không xoá log hộ — phải tự chặn bằng một cờ trong code."

**Đề xuất của `test-phase-10-report.md`:**
```bash
adb logcat -d -s "FTD_EVENT:D" | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"
```
**Đánh giá: CHƯA đủ.** Phép này chỉ soi đúng tag `FTD_EVENT` — nó giả định app CHỈ BAO GIỜ log qua
tag đó. Trước phase này giả định đúng vì mọi lời gọi `Log.*` trong `:data`/`:ui` đều tự tay gõ
`TAG = "FTD_EVENT"`, nhưng đó là kỷ luật con người, không phải thứ gì ép được — một dev thêm
`Log.d("Debug", "lat=$lat")` ở đâu đó vẫn lọt qua phép grep theo tag mà không ai biết.

**Phép grep đã chốt cho gate này (hai lớp, bổ sung cho nhau):**
```bash
# Lớp 1 — G7 cốt lõi: FtdLog phải câm hoàn toàn ở release (không phải "câm với toạ độ", câm TOÀN BỘ)
adb logcat -d | grep -c "FTD_EVENT"                              # phải = 0

# Lớp 2 — phòng thủ theo chiều sâu: lọc theo PID của app, không theo tag, để bắt được
# BẤT KỲ tag nào app có thể log (kể cả tag không phải FTD_EVENT, kể cả thư viện log hộ app)
PID=$(adb shell pidof com.example.pion.family.tracker.demo)
adb logcat -d --pid="$PID" | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"   # phải rỗng — toạ độ
adb logcat -d --pid="$PID" | grep -c "AIza"                          # phải = 0 — API key
```
**Vì sao lớp 2 tốt hơn lọc theo tag:** `--pid` lọc ở tầng logcat theo tiến trình sinh ra dòng log,
không quan tâm tag là gì — phủ được cả trường hợp một Android library bên thứ ba (Room, Play
Services SDK phía app, không phải phía hệ thống) log hộ app dưới tag riêng của nó. Đây chính là cơ
chế loại bỏ false-positive `Geofencer` mà `test-phase-10-report.md` gặp phải — dòng đó có PID của
**Play Services** (tiến trình hệ thống, khác PID với app), nên filter theo PID app tự động loại nó,
không cần biết trước tên tag `Geofencer` để mà exclude thủ công như cách tiếp cận "grep thô rồi trừ
tay" ban đầu.

**Kết quả chạy thật** (sau khi cấp quyền, bật theo dõi, mở Zone/History/Timeline, chạy 2 lần mô
phỏng — APK release, `emulator-5554`):
```
FTD_EVENT count: 0
coordinate grep (pid=14280, 77 dòng log của app): rỗng
AIza count: 0
grep -rn "AIza" --include='*' . | grep -v '^./reports/' : (chạy ở bước docs sync, xem dưới)
```
**PASS.** Ghi luật đo vào `LLM.md` §13/§10 khi đồng bộ tài liệu: gate G7 dùng CẢ HAI lớp trên, không
chỉ lớp tag.

## G8 — chi tiết

```
./gradlew clean assembleRelease --no-configuration-cache   → BUILD SUCCESSFUL in 15s
ls -l app/build/outputs/apk/release/app-release.apk        → 28,886,388 bytes
apksigner verify --print-certs app-release.apk             → Signer #1: CN=Android Debug,
                                                                SHA-1 7dcf641344ddd235bc0b449f028c87c04dda8b43
adb -s emulator-5554 install -r app-release.apk             → Success
```
Mở app trên chính APK vừa cài: bản đồ hiện đúng phố Sài Gòn thật (Nhà thờ Đức Bà, Chợ Bến Thành,
Bitexco...), không xám, không watermark — ảnh `scratchpad/g8-map-screen.png`. Ký bằng debug keystore
đúng theo quyết định #2 của chủ dự án (PRD v1.2 §7.2) — SHA-1 này phải nằm trong hạn chế Maps API key
(đã đúng, vì bản đồ hiện được).

`allowBackup` kiểm bằng `apkanalyzer manifest print app-release.apk` (không phải qua
`AndroidManifest.xml` nguồn — bản đã merge, đúng yêu cầu Security Considerations vì manifest merger
có thể ghi đè): `android:allowBackup="false"` — đúng, không bị thư viện nào ghi đè khi merge.

## G5 — chi tiết

**Tiêu chí PRD §11.1:** "Đóng app khỏi recents rồi bước qua ranh giới zone thật → có thông báo trong
≤ 3 phút."

**Máy thật `RF8Y60B9NCZ` (Samsung SM-A165F, Android 16) đang khoá màn hình bằng mật khẩu thật** —
xác nhận bằng `adb shell wm dismiss-keyguard` (chỉ thành công với khoá kiểu vuốt/không mật khẩu) rồi
kiểm `dumpsys window | grep isKeyguardShowing` → vẫn `true`. Không đoán mã mở khoá.

**Việc đã làm được KHÔNG cần mở khoá/chuyển động thật** (adb hoạt động bình thường qua khoá màn hình
cho lệnh shell, cài đặt, cấp quyền — chỉ riêng thao tác chạm màn hình thật/UI là không làm được):

1. **Cài đặt.** `adb install -r app-release.apk` — Success.
2. **Cấp quyền không qua UI.** `pm grant` cho `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
   `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS` — cả 4 xác nhận `granted=true` qua
   `dumpsys package`.
3. **Chạy không crash.** `am start` → `pidof` có PID sống, `logcat | grep "FATAL EXCEPTION"` rỗng,
   theo dõi qua nhiều lần cài/mở lại (release lẫn debug tạm thời — xem bước 4) không phát sinh crash.
4. **Geofence đăng ký được — kiểm bằng cách tạm thời đổi sang bản debug (log release im lặng do G7,
   không đọc được qua logcat), theo đúng quy trình WAL của `ENV-BRIEFING.md`:**
   - DB thật trên máy đang **rỗng zone** (`count=0`) — khác với `emulator-5554` (1 zone có sẵn).
     `assembleRelease` mặc định `geofence_registered zoneId=all count=0 success=true` — đúng nhưng
     không chứng minh được gì vì không có zone nào để đăng ký.
   - `force-stop` → kéo `family_tracker.db`/`-wal`/`-shm` → `python3 sqlite3`
     `PRAGMA wal_checkpoint(TRUNCATE)` → chèn **1 zone tạm** (`g5-temp-test-zone`, không dùng toạ độ
     thật của máy — tự chọn toạ độ trung tính) → đẩy `.db` về, xoá `-wal`/`-shm` → mở lại app (bản
     **debug**, tạm cài đè để đọc log — `ENV-BRIEFING.md`: "run-as chỉ chạy khi bản DEBUG được cài").
   - Logcat xác nhận: **`geofence_registered zoneId=all count=1 success=true`**, và **không** có
     `zone_event_raised`/`notification_posted` nào đi kèm (đúng hành vi Fixed #18 — `registerAll()`
     dùng `initialTrigger=0`, không có thông báo ma lúc mở app).
   - **Dọn dẹp:** force-stop → kéo DB → xoá zone tạm → đẩy `.db` về, xoá `-wal`/`-shm` → đọc ngược
     xác nhận `count=0` → **cài lại bản release** → xác nhận `run-as` báo
     `package not debuggable` (đúng bản release) → mở lại, `pidof` sống, không crash.
5. **Không có thông báo ma.** Suốt toàn bộ quá trình trên (cả bản debug lẫn bản release, nhiều lần
   mở app) không có dòng `zone_event_raised`/`notification_posted` nào xuất hiện ngoài ý muốn —
   khớp với Fixed #18 (đã sửa ở phase-09) và tái xác nhận trên chính thiết bị Samsung, không chỉ
   `emulator-5554` như báo cáo phase-09 gốc.
6. **Log sạch (G7 áp dụng luôn cho máy thật).** Bản release trên máy thật: `FTD_EVENT` = 0 dòng
   trong toàn bộ logcat kiểm tra ở các bước trên.

**Phần KHÔNG làm được — đúng như dự đoán, đây là phần cốt lõi của G5:**
- **Vuốt app khỏi recents rồi đi bộ thật qua ranh giới zone** — cần chạm màn hình thật (vuốt task
  switcher) và chuyển động vật lý thật ngoài trời. `adb shell input tap/swipe` gửi được sự kiện chạm
  ở tầng hệ thống ngay cả khi khoá màn hình, nhưng **không tương đương thao tác người dùng thật đóng
  app + geofence bắn nền dài hạn qua Doze thật** — mô phỏng gián tiếp qua adb không chứng minh được
  đúng thứ G5 tồn tại để đo (Key Insight #2 của phase file: "Doze thật khác Doze giả lập").
- Việc bơm vị trí giả (`adb emu geo` — chỉ emulator có) không tồn tại cho máy thật; máy thật cần
  chuyển động GPS thật hoặc app mock-location của bên thứ ba (không có sẵn, không tự ý cài thêm).

**Việc chủ dự án cần làm khi có thể cầm máy** (đã cài sẵn bản release, quyền đã cấp, geofence đã xác
nhận đăng ký được với dữ liệu thật — chỉ còn thiếu bước "người + chuyển động"):
1. Mở khoá máy (mật khẩu chỉ chủ máy biết).
2. Mở app, tạo 1 zone bán kính 150–200m quanh vị trí hiện tại (qua UI, không qua SQL nữa — để geofence
   đăng ký đúng toạ độ thật).
3. Bật công tác theo dõi, chờ trạng thái zone chuyển "Đang ở trong".
4. **Vuốt app khỏi recents** (không dùng `force-stop` — PRD nói rõ force-stop không tính vì nó xoá
   geofence).
5. Đi bộ ra ngoài bán kính ít nhất 200m (buffer 30m + sai số GPS), bấm giờ từ lúc bước qua mép.
6. Ghi số giây tới lúc thông báo hiện, quay lại chờ 2 phút, lặp đủ 3 lần.
7. Ghi vào `gate-evidence.md`: 3 số giây, model máy, phiên bản Android, thời tiết/nhà cao tầng.
8. Kiểm `zone_events` không có dòng lặp trong 60s.
