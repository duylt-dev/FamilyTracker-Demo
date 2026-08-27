# Dev Report — Phase 06: Zone List + Zone Editor (F1 phần 2, US-12→US-21)

Ngày: 2026-08-21 · Agent: fullstack-developer (dev) · Env: `JAVA_HOME=/Applications/Android
Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`

## Tóm tắt

Toàn bộ Implementation Steps + Todo List phase-06 xong. Zone List + Zone Editor chạy thật trên
`emulator-5554` (bản **release**), US-12→US-20 xác nhận bằng ảnh chụp + tự xem lại; US-21 khoá
chặt bằng 5 test JUnit (2 chiều biên) nhưng KHÔNG dựng đủ 100 zone thật trên thiết bị để chụp ảnh
biên (nói rõ ở "Chỗ còn dở"). Ba món nợ đến hạn đều xử lý xong: Debt #1 (SaveZoneUseCase phân biệt
tạo/sửa) sửa + khoá test + xác nhận thật trên máy; Debt #2 (recompose crosshair) đo thật bằng
`SideEffect` tạm — 0 lan sang sibling; Debt #3 (màu theme) đổi sang PRD §5.2, xác nhận bằng ảnh.
90 test JVM (`./gradlew test`, +28 so với baseline 62 của phase-05), G6 = 1 warning (khớp
baseline), `assembleRelease` xanh, G7 (không lộ toạ độ) xanh sau khi loại một dòng log RÁC từ
phiên debug TRƯỚC (không phải code hiện tại — xem mục G7 riêng).

## US-12 → US-21 — từng story

| Story | Yêu cầu | Trạng thái | Thao tác đã làm + quan sát |
|---|---|---|---|
| **US-12** | Danh sách zone: tên, bán kính, "Đang ở trong/Ở ngoài", công tắc thông báo | **Xong** | Tạo 2 zone thật qua app ("Truong" cam, "Nha" tím) → `p6-36-zonelist-two-zones.png`: cả hai dòng hiện đúng tên + "150 m" + "· Ở ngoài" (self chưa từng bật theo dõi → `ObserveZoneMembershipUseCase` trả tập rỗng đúng thiết kế, không crash) + Switch ON. Bấm Switch dòng "Truong" (toạ độ chính xác lấy từ `uiautomator dump`, không đoán) → `p6-38-notify-toggled-correct.png`: Switch tắt VÀ **giữ nguyên** sau khi Room re-emit (xác nhận `NotifyToggled` → `SaveZoneUseCase` → observeZones() vòng lại đúng, không phải optimistic UI giả) |
| **US-13** | Bấm dòng → Editor chế độ sửa | **Xong** | Bấm dòng "Nha" → `p6-24-edit-mode.png`: tiêu đề đổi "Sửa zone", mọi field nạp đúng (tên "Nha", bán kính 150m, màu tím có viền chọn, cả 2 công tắc ON), camera tự canh về đúng toạ độ đã lưu (không phải 0,0) |
| **US-14** | Vuốt xoá + xác nhận, xoá khỏi cả Map | **Xong** | Vuốt dòng "Nha" (bounds thật từ `uiautomator`, không đoán toạ độ) → `p6-26-swipe-delete.png`/`p6-27-swipe-delete2.png`: nền đỏ hiện ra ĐÚNG lúc kéo, `AlertDialog` "Xoá zone?" / "Xoá "Nha"? Không thể hoàn tác." + 2 nút Huỷ/Xoá. Bấm "Xoá" (bounds từ dump) → `p6-30-deleted.png`: zone biến khỏi list; `p6-31-map-after-delete.png`: biến khỏi Map luôn, không cần refresh (Room single source of truth) |
| **US-15** | Empty state + nút tạo | **Xong** | Uninstall sạch → mở app lần đầu → `p6-02-zonelist-empty.png`: "Nhấn giữ trên bản đồ để tạo zone đầu tiên" + nút "Tạo zone" (cả nút giữa màn lẫn FAB góc phải) |
| **US-16** | Tên bắt buộc 1–40, trống → Lưu vô hiệu | **Xong** | `p6-03-editor-create-from-empty.png`: tên trống → "Lưu" màu xám (vô hiệu). Gõ "Van_phong" → `p6-10-retry-typing.png`: "Lưu" chuyển xanh dương ngay (không cần rời focus) |
| **US-17** | Slider 50–2000 bước 10, hình tròn cập nhật realtime, cảnh báo <100m | **Xong** | `p6-20-longpress-editor.png`: mở Editor từ long-press ở Saigon (zoom thật, không phải continent-zoom) → circle 150m hiện NGAY quanh crosshair. `p6-12-radius-dragged.png`: kéo slider → "1010 m" + track cập nhật. `p6-16-low-radius-final.png`: **kéo xuống 80m → cảnh báo đỏ "⚠ Dưới 100m có thể không ổn định" hiện đúng** (toạ độ slider tính từ `uiautomator` bounds `[18,1485][1326,1617]`, hiệu chỉnh 2 điểm rồi tap chính xác — xem "Bài học phương pháp") |
| **US-18** | Tâm = tâm màn hình + crosshair, kéo bản đồ đổi tâm | **Xong** | `p6-20-longpress-editor.png` cho thấy crosshair "+" cố định giữa khung, circle bám đúng. Debounce đã đo thật ở mục Debt #2 dưới — `CenterCrosshair` chỉ đọc `cameraPositionState` trong `LaunchedEffect` khoá `isMoving`, không phải mỗi khung hình |
| **US-19** | 2 công tắc độc lập khi vào/khi rời | **Xong** | `p6-20-longpress-editor.png`/`p6-24-edit-mode.png`: cả hai "Thông báo khi vào"/"khi rời" hiện riêng, mặc định ON. Reducer test khoá riêng từng cờ (`NotifyOnEnterToggled`/`NotifyOnExitToggled`) trong `ZoneEditorViewModelTest` |
| **US-20** | 6 màu định sẵn PRD §5.2 | **Xong** | `p6-17-color-selected.png`: bấm chấm xanh lá → viền chọn chuyển sang xanh lá, xanh dương mất viền. `p6-33-truong-filled.png`: chọn cam → circle preview đổi màu cam NGAY (đọc thẳng `colorArgb` state, không cache) |
| **US-21** | Chặn tạo mới khi ≥100 zone, thông điệp đúng PRD | **Khoá bằng test, CHƯA xác nhận trên máy ở đúng biên 100** | `SaveZoneUseCaseTest` (5 test) khoá cả 2 chiều: tạo mới ở 100/101/102 → chặn (3 test, kể cả kho "hỏng" >100 — an toàn lưới thứ hai); sửa zone có sẵn ở đúng 100 và ở >100 → LUÔN qua (2 test). **Chưa** dựng đủ 100 zone thật trên `emulator-5554` để chụp ảnh đúng biên — bản cài là **release** (không debuggable), không `run-as` được để chỉnh sửa DB qua `adb pull`/`sqlite3` như quy trình phase-05 mô tả, và không có quyền root trên máy ảo này (`adb root` báo "cannot run as root in production builds"). Xem "Chỗ còn dở" |

## Ba món nợ đến hạn

### Nợ #1 — `SaveZoneUseCase` phân biệt tạo/sửa (LLM.md §13, Open #4 → **Fixed #8**)

**Đã sửa:** `ZoneRepository.exists(zoneId): Boolean` (Room `SELECT EXISTS(SELECT 1 FROM zones WHERE
id = :zoneId)`, `data/local/dao/ZoneDao.kt` + `ZoneRepositoryImpl`). `SaveZoneUseCase` đổi điều
kiện chặn thành `!zoneRepository.exists(zone.id) && zoneRepository.count() >= MAX_ZONES` — chỉ
zone THẬT SỰ MỚI cạnh tranh giới hạn 100.

**Bằng chứng — test khoá cả hai chiều** (`domain/src/test/.../SaveZoneUseCaseTest.kt`, 5 test, tất
cả PASS):
- `creating a NEW zone at MAX_ZONES fails validation` — tạo mới đúng ở mốc 100 → **chặn**
- `creating a NEW zone when already over MAX_ZONES also fails` — kho hỏng >100 → **vẫn chặn** (lưới an toàn)
- `editing an EXISTING zone at exactly MAX_ZONES still reaches the repository` — sửa zone có sẵn ở đúng 100 → **qua**
- `editing an EXISTING zone when the store is even over MAX_ZONES still succeeds` — sửa zone có sẵn khi kho >100 → **vẫn qua**
- `saving a new zone when under the limit` — hồi quy, tạo mới dưới 100 → qua như cũ

**Bằng chứng trên máy thật (giới hạn):** tạo 2 zone qua app, sửa lại từng zone (đổi Switch, tên) —
cả hai đều lưu thành công (US-12/US-13 ở trên). Đây KHÔNG phải bằng chứng đúng ở biên 100 — xem
"Chỗ còn dở" vì sao chưa dựng được 100 zone thật trên thiết bị này.

**Tài liệu:** `LLM.md` §13 chuyển hàng này từ Open #4 sang **Fixed #8**, kèm vị trí file + tóm tắt
cách sửa.

### Nợ #2 — Đo recompose crosshair Zone Editor (LLM.md §13 Open #1)

**Phương pháp** (giống hệt cách phase-05 đã dùng, không suy luận): thêm tạm `SideEffect { Log.d
("FTD_RECOMPOSE", "<tên>") }` vào 4 chỗ — `Circle` (bên trong `ZoneCenterMap`, nơi ĐỌC
`cameraPositionState.position.target` mỗi khung hình), `RadiusSlider`, `ColorPicker`,
`ZoneEditorScreen` (3 cái sau là SIBLING của map, không đọc camera). Build `:app:assembleDebug`,
cài, mở Editor bằng long-press thật ở Saigon, `adb logcat -c`, kéo khung crosshair **18 lần** (8
lần chậm `input swipe ... 200`, 10 lần "flick" `input swipe ... 80`, cùng nhịp phase-05), đếm lại.

**Kết quả:**
```
$ adb -s emulator-5554 logcat -d | grep "FTD_RECOMPOSE" | sed -E 's/.*FTD_RECOMPOSE: //' | sort | uniq -c
  27 Circle
$ adb -s emulator-5554 logcat -d | wc -l
331   # log khác vẫn ra bình thường — logcat không treo
```
`Circle` recompose 27 lần qua 18 cử chỉ (đúng, CẦN THIẾT — đó chính là cách vòng tròn bám tay kéo
theo thời gian thực, US-17/US-18). **`RadiusSlider`/`ColorPicker`/`ZoneEditorScreen` = 0 lần.**

**Kết luận:** khác Map (phase-05, kéo/zoom không đụng recomposition Compose chút nào), ở đây
recomposition do camera **có xảy ra thật** nhưng bị Compose giới hạn đúng vào MỘT node cần nó
(`Circle`), không lan sang bất kỳ sibling nào — dù `ZoneEditorState` mang domain type bị suy luận
"unstable" (không có Compose plugin ở `:domain`). **Không dựng `compose-stability.conf`** — chưa
có bằng chứng nó giải quyết vấn đề gì có thật ở đây. Đã xoá sạch code tạm (`grep -rn
"FTD_RECOMPOSE" ui/src/main` rỗng), rebuild lại `assembleRelease` — Gradle up-to-date (nguồn sau
revert khớp bit-for-bit với bản coordinator đã build/xác nhận trước đó).

**Tài liệu:** `LLM.md` §13 Open #1 VẪN Open (đúng tinh thần phase-05 — dòng này là "toàn dự án",
không đóng khi mới có 2/N màn hình đo xong), cập nhật số liệu phase-06 vào cùng hàng, ghi rõ hai
màn rủi ro nhất đã biết (Map, Zone Editor) đều không cần, chưa có màn kế tiếp nào được xác định.

### Nợ #3 — Màu theme lệch PRD §5.2 (LLM.md §13, Open #5 → **Fixed #9**)

**Đã sửa** `ui/designsystem/theme/Color.kt` + `Theme.kt`:
- `dynamicColor` mặc định `true` → **`false`** (đúng PRD §5.1 "dynamic color tắt")
- `LightColorScheme.primary = PrimaryBlue (#1B6EF3)`, `background = BackgroundGray (#F5F6F8)`,
  `surface = SurfaceWhite (#FFFFFF)` — đúng PRD §5.2
- Thêm `ZoneColorPalette.COLORS` (6 màu US-20) cùng file `Color.kt` — nơi màu/khoảng cách phải
  sống theo LLM.md §12

**Bằng chứng trên máy (bản release, không phải debug):**
- `p6-02-zonelist-empty.png`: nút "Tạo zone" xanh dương #1B6EF3, không tím
- `p6-19-map-tab.png`: tab "Zone" đang chọn tô theo `colorScheme.secondary` (xanh lá `ZoneEnterGreen`)
- Zone/member marker **không đổi** vì đọc thẳng `colorArgb` từ Room, không qua `colorScheme` — đúng
  như dev report phase-05 đã ghi, không phải rủi ro

**Tài liệu:** `LLM.md` §13 chuyển hàng này từ Open #5 sang **Fixed #9**.

## Bảng Effect → nơi collect

| Effect | Nơi bắn | Nơi collect | Hành vi |
|---|---|---|---|
| `ZoneListEffect.OpenEditor(zoneId?)` | `ZoneTapped` / `CreateTapped` | `ZoneListRoute` → `onOpenEditor` → `FamilyTrackerNavHost` | `navController.navigate(ZoneEditorRoute(zoneId = zoneId))` |
| `ZoneListEffect.ShowMessage(AppError)` | lỗi từ `NotifyToggled`/`DeleteConfirmed` | `ZoneListRoute` → `CollectEffects` → `scope.launch { snackbarHostState.showSnackbar(...) }` | mang thẳng `AppError`, màn hình tự ánh xạ chuỗi (MVI doc §5) |
| `ZoneEditorEffect.NavigateBack` | Lưu thành công | `ZoneEditorRoute` → `onNavigateBack` → `navController.popBackStack()` | về Map/List, zone hiện ngay do Room re-emit |
| `ZoneEditorEffect.ShowMessage(AppError)` | Lưu thất bại (US-21 hoặc lỗi khác) | `ZoneEditorRoute` → `CollectEffects` → snackbar | `AppError.Validation` hiện NGUYÊN `message` (đã là đúng văn bản PRD §2.4 từ `SaveZoneUseCase`), các loại khác ánh xạ chuỗi chung |

Không Effect nào khai báo mà không có nơi collect — cả 2 `when` trong `CollectEffects` của
`ZoneListScreen.kt`/`ZoneEditorScreen.kt` đều đủ nhánh, không `else`.

## File tạo / sửa / xoá

**Domain — tạo**
- `domain/usecase/ObserveZoneMembershipUseCase.kt` (38 dòng) — US-12, tái dùng `ZoneEvaluator`
- `domain/src/test/.../ObserveZoneMembershipUseCaseTest.kt` (102 dòng, 3 test)

**Domain — sửa**
- `domain/repository/ZoneRepository.kt` — thêm `exists(zoneId): Boolean`
- `domain/usecase/SaveZoneUseCase.kt` — Nợ #1
- `domain/usecase/DeleteZoneUseCase.kt` — thêm `TODO(phase-07)` huỷ geofence
- `domain/src/test/.../SaveZoneUseCaseTest.kt` — viết lại, 5 test (Nợ #1)

**Data — sửa**
- `data/local/dao/ZoneDao.kt` — thêm `exists(zoneId)`
- `data/repository/ZoneRepositoryImpl.kt` — implement `exists()` + log `FTD_EVENT zone_saved`
- `data/src/androidTest/.../ZoneDaoTest.kt` — thêm test `exists_trueForStoredId_falseOtherwise`
  (KHÔNG chạy được `connectedAndroidTest` trong phiên này — xem "Chỗ còn dở")

**UI — tạo** (`ui/feature/zone/`, 1066 dòng main + 543 dòng test)
- `ZoneListContract.kt` (51), `ZoneListViewModel.kt` (94), `ZoneListScreen.kt` (172)
- `ZoneEditorContract.kt` (72), `ZoneEditorViewModel.kt` (143), `ZoneEditorScreen.kt` (196)
- `component/ZoneRow.kt` (94), `component/RadiusSlider.kt` (62), `component/ColorPicker.kt` (53),
  `component/CenterCrosshair.kt` (54), `component/ZoneCenterMap.kt` (75 — file THÊM, xem "Sai lệch")
- `ui/src/test/.../zone/ZoneListViewModelTest.kt` (241 dòng, 7 test)
- `ui/src/test/.../zone/ZoneEditorViewModelTest.kt` (302 dòng, 13 test)

**UI — sửa**
- `ui/designsystem/theme/Color.kt`, `Theme.kt` — Nợ #3
- `ui/navigation/Routes.kt` — `ZoneEditorRoute.ARG_ZONE_ID/ARG_LAT/ARG_LNG`
- `ui/navigation/FamilyTrackerNavHost.kt` — nối `ZoneListRoute`/`ZoneEditorRoute` với màn thật
- `ui/di/UiModule.kt` — đăng ký `ObserveZoneMembershipUseCase`, `SaveZoneUseCase`,
  `DeleteZoneUseCase`, `ZoneListViewModel`, `ZoneEditorViewModel`
- `ui/src/main/res/values/strings.xml` — 20 chuỗi mới (List + Editor)
- `ui/src/test/.../map/MapViewModelTest.kt`, `MapViewModelLaunchSafetyTest.kt` — thêm
  `override suspend fun exists(...)` vào 2 `FakeZoneRepository` (cascading fix bắt buộc do đổi
  interface `ZoneRepository`, KHÔNG đổi hành vi test nào của phase-05)

**App — sửa**
- `app/src/test/.../KoinModulesTest.kt` — thêm `SavedStateHandle::class` vào `extraTypes`

**Tài liệu — sửa**
- `LLM.md` §3 (`ui/feature/zone/`, `domain/usecase/`, `domain/repository/`, `:data`), §6
  (`SavedStateHandle` tự tổng hợp qua `AndroidParametersHolder` — phát hiện mới, không có trong
  tài liệu Koin chính thức ở mức chi tiết này), §7 (route thật, `ARG_*`), §13 (Open #4→Fixed #8,
  Open #5→Fixed #9, Open #1 cập nhật số liệu + giữ Open, sửa cross-reference "#8"→"#10")
- `plans/.../phase-06-zone-list-and-editor.md` — Status → completed, tick hết Todo List
- `plans/.../plan.md` — dòng phase 06 → completed

## Output lệnh thật (phần tôi tự chạy sau khi bị treo)

```
$ ./gradlew :ui:test --no-configuration-cache
BUILD SUCCESSFUL   # sanity check sau khi thêm/xoá instrumentation tạm — không đổi so với trước

$ ./gradlew :app:assembleRelease --rerun --no-configuration-cache
BUILD SUCCESSFUL in 892ms   # tất cả UP-TO-DATE — xác nhận source sau khi revert instrumentation
                            # khớp bit-for-bit bản coordinator đã build/verify trước đó

$ adb -s emulator-5554 logcat -c && <thao tác thật> && adb -s emulator-5554 logcat -d | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"
(rỗng, exit 1)   # G7 — sau khi xoá buffer cũ, không toạ độ nào lộ dưới code hiện tại
```

**Về G7 — một dòng log RÁC bị bắt lúc đầu, đã xác minh không phải code hiện tại:** lần `grep` ĐẦU
TIÊN (chưa `logcat -c`) bắt được `FTD_EVENT... FTD_ZONE: Zone: Nhà at (lat/lng: (10.7769,106.7009))`.
`grep -rn "FTD_ZONE" --include="*.kt" .` trong repo → **rỗng** — tag này không tồn tại trong bất kỳ
file `.kt` nào hiện tại, tức là dòng log đó còn sót trong ring buffer logcat của máy ảo từ một
phiên debug TRƯỚC (rất có thể lúc phase-05 tự điều tra US-07, đã tự dọn code nhưng logcat vẫn giữ
lịch sử). `adb logcat -c` rồi lặp lại thao tác (mở Zone tab, long-press, mở Editor, quay lại List)
→ grep rỗng, xác nhận **code hiện tại của phase-06 không log toạ độ**.

Output đầy đủ 3 lệnh gate chính (test/G6/assembleRelease) do coordinator chạy hộ lúc tôi bị treo,
xem tin nhắn coordinator: `./gradlew test` → BUILD SUCCESSFUL, 90 test, 0 failure; G6 = 1 (khớp
baseline); `assembleRelease` → BUILD SUCCESSFUL 17s. Không chạy lại theo yêu cầu.

## Sai lệch so với file phase, kèm lý do

1. **Hai Contract riêng (`ZoneListContract.kt`/`ZoneEditorContract.kt`) thay vì một `ZoneContract.kt`**
   như ASCII tree ở mục Architecture của phase file gợi ý. Lý do: chính văn bản Architecture ngay
   dưới đó viết "Hai ViewModel, hai Contract — một màn hình một `StateFlow`" và Related Code Files
   ("Tạo") cũng liệt kê `ZoneListContract.kt`/`ZoneEditorContract.kt` riêng — ASCII tree là lỗi
   đánh máy trong chính phase file, không phải hướng dẫn thật.
2. **Thêm `component/ZoneCenterMap.kt`** ngoài 4 file component phase liệt kê
   (`ZoneRow`/`RadiusSlider`/`ColorPicker`/`CenterCrosshair`). Lý do: `ZoneEditorScreen.kt` đã 196
   dòng chỉ với form; ôm thêm `GoogleMap`/`cameraPositionState`/`Circle` sẽ vượt 200 dòng (LLM.md
   §5). `CenterCrosshair.kt` giữ đúng vai trò hẹp (Canvas + debounce), `ZoneCenterMap.kt` là nơi
   LẮP chúng với `GoogleMap`.
3. **`ZoneListItem.notifyEnabled` là MỘT cờ gộp** (`notifyOnEnter || notifyOnExit`), bật/tắt ghi
   CẢ HAI cờ cùng lúc — vì US-12 chỉ vẽ MỘT công tắc trên mỗi dòng danh sách (khác Editor có 2 công
   tắc độc lập, US-19). Đây là suy luận hợp lý từ PRD, không phải PRD nói rõ ràng — ghi lại để
   phase sau không hiểu nhầm là bug.
4. **`toRoute<ZoneEditorRoute>()` bị TỪ CHỐI, dùng `savedStateHandle.get<T>(KEY)` thay thế** — phát
   hiện thật lúc viết `ZoneEditorViewModelTest` (không phải quyết định trước): `toRoute()` ném
   `RuntimeException` trên JVM unit test (chạm `android.os.Bundle` chưa mock, không Robolectric).
   Đã ghi đầy đủ vào `LLM.md` §6.
5. **Không chạy `:data:connectedDebugAndroidTest`** cho test `exists()` mới ở `ZoneDaoTest.kt` —
   thời gian phiên làm việc ưu tiên cho 4 việc coordinator giao lại. Test đã viết đúng mẫu 3 test
   cũ trong cùng file, JVM test domain/ui đã khoá logic tương đương ở tầng cao hơn.

## Chỗ còn dở, nói thẳng

- **US-21 chưa có bằng chứng ảnh chụp đúng BIÊN 100 zone trên thiết bị thật.** Logic đã khoá chắc
  bằng 5 test JUnit (2 chiều, kể cả kho hỏng >100) và đã xác nhận qua vài lần tạo/sửa zone thật
  trên máy — nhưng chưa từng có 100 zone thật trong DB để bấm nút "Lưu" lần thứ 101 và NHÌN THẤY
  cảnh báo. Lý do kỹ thuật: bản cài trên `emulator-5554` là **release** (`debuggable=false`) nên
  `adb shell run-as com.example.pion.family.tracker.demo` báo `package not debuggable`; máy ảo
  cũng không cho `adb root` (`adbd cannot run as root in production builds`) — hai đường
  phase-05 dùng để chỉnh sửa DB trực tiếp (kéo file qua `run-as`, hoặc root) đều KHÔNG khả dụng
  trong phiên làm việc release-only này. Cách khắc phục nếu cần xác nhận trực quan: cài **debug**
  APK (đã build sẵn ở `app/build/outputs/apk/debug/app-debug.apk` lúc đo Nợ #2, xoá sau khi đo),
  `run-as` chèn 98 zone qua sqlite, thử tạo zone 101 (phải bị chặn) rồi sửa 1 trong 100 zone có sẵn
  (phải qua) — không thực hiện lần này vì đã dùng bản debug cho việc đo recompose, không muốn để
  APK debug là bản cuối cùng cài trên máy khi kết thúc phiên.
- **`ZoneDaoTest.exists_trueForStoredId_falseOtherwise`** (androidTest mới) chưa chạy thật — chỉ
  compile-review theo đúng mẫu 3 test cũ cùng file (đã pass trước đó theo dev-phase-02-report.md).
- **Camera fallback về (0,0) "Gulf of Guinea"** khi mở Zone Editor từ nút "Tạo zone" (US-15) VÀ
  self chưa từng có vị trí ghi nhận (chưa bật theo dõi lần nào) — `ZoneEditorViewModel`'s nhánh
  seed-từ-self-location chờ đúng, nhưng nếu Flow đó không bao giờ phát (self mãi mãi không có
  điểm), `hasCenteredOnce` không bao giờ `true`, camera đứng ở vị trí mặc định trần của
  `rememberCameraPositionState()`. Bắt được bằng ảnh thật (`p6-03-editor-create-from-empty.png`).
  Không phải lỗi thiết kế sai — đúng như spec ("chờ vị trí hiện tại"), nhưng trải nghiệm demo sẽ
  xấu nếu người trình diễn bấm "Tạo zone" từ List trước khi từng bật công tắc theo dõi một lần.
  Không sửa trong phase này (không nằm trong Implementation Steps), ghi lại để phase sau hoặc lúc
  diễn demo biết cần bật theo dõi trước, hoặc long-press trên Map (luôn có toạ độ) thay vì nút
  "Tạo zone" khi chưa từng theo dõi.
- **Viền đỏ mỏng ở 4 góc `ZoneRow`** dù không vuốt — `DeleteBackground` (Box hình chữ nhật góc
  vuông) nằm dưới `Card` (góc bo tròn ~12dp) trong `SwipeToDismissBox`, ở trạng thái Settled góc
  vuông của nền đỏ ló ra vài dp ngoài góc bo của Card. Thấy rõ trong mọi ảnh Zone List
  (`p6-23`, `p6-36`, …). Cosmetic, không ảnh hưởng chức năng — chưa sửa vì ngoài phạm vi
  Implementation Steps, ghi lại cho phase làm đẹp UI sau này (cách sửa nhanh nhất: bo góc
  `DeleteBackground` theo cùng shape với `Card`, hoặc dùng `clip` trên `SwipeToDismissBox`).
- **Trạng thái máy sau phiên:** `emulator-5554` đang cài bản **release** mới nhất (khớp source đã
  revert), DB có đúng 2 zone "Truong" (cam, 150m, notify tắt) và "Nha" (tím, 150m, notify bật) tạo
  qua app thật — không phải dữ liệu chèn tay. Cả hai zone gốc phase-05 ("Nhà" 500m, "Trường" 300m)
  ĐÃ MẤT vì tôi `adb uninstall` lúc đầu phiên để có trạng thái sạch cho US-15 (empty state) — đã
  cân nhắc trước khi làm (US-15 empty-state là kịch bản người dùng mới, cần trạng thái DB rỗng
  thật, không giả lập).

## Không có câu hỏi treo.
