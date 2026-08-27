# Dev Report — Phase 05: Màn Map thật (F1, US-06→US-11)

Ngày: 2026-08-21 · Agent: fullstack-developer (dev) · Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`

## Tóm tắt

Toàn bộ Implementation Steps + Todo List phase-05 xong. Bản đồ render thật (chụp màn hình thật,
không xám, không watermark) — **trả xong món nợ G8 hoãn từ phase-01**. Đo recomposition thật khi
kéo bản đồ (không suy luận) → **0 recompose thừa** → giữ `LLM.md` §13 Open #1 nguyên trạng, không
dựng `compose-stability.conf`, kèm số liệu và lý do cơ chế. 62 test JVM xanh (+12 so với baseline
50 của phase-04), G6 = 1 warning (khớp), `assembleRelease` xanh, cài + chạy thật trên
`emulator-5554` toàn bộ US-06→US-11, không crash, không lộ toạ độ ra logcat (G7).

## US-06→US-11 — từng story, đã làm/chưa, bằng chứng

| Story | Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|---|
| **US-06** | Marker xanh dương tại vị trí thiết bị; camera tự canh lần đầu, không animate | **Xong** | `scratchpad/ftd-map-06-self-marker.png`: bật công tắc + `emu geo fix 106.7009 10.7769` → marker xanh chấm tròn viền trắng xuất hiện đúng vị trí, `.move()` không animate (dùng `CameraUpdateFactory.newLatLngZoom` + `.move()`, không `.animate()`). `ftd-map-08-self-realistic-move.png`: bơm điểm hợp lệ thứ 2 (~85m, ~31km/h) → marker dịch chuyển đúng, không nội suy (`rememberUpdatedMarkerState` gán thẳng). `ftd-map-07-self-moved.png`: bơm điểm phi lý (~1.3km/10s, ~470km/h) → `LocationFilter` từ chối (`location_dropped reason=SPEED`), marker **không** nhảy — xác nhận bộ lọc chạy đúng đường thật (LLM.md §8.4), không phải đường tắt cho demo |
| **US-07** | Mỗi zone là `Circle` nền 20% alpha, viền 100% dày 2dp, tên ở tâm | **Code xong, chưa có zone thật để chụp** | `ZoneCircles.kt` implement đúng `fillColor.copy(alpha=0.2f)`, `strokeColor` 100%, `strokeWidth=2f`, tên qua `MarkerComposable` (luôn hiện, không cần chạm — lý do chọn ghi trong KDoc). **Chưa chụp được zone thật trên UI** vì bảng `zones` rỗng cho tới phase-06 (đúng phạm vi, không phải bug) — đã đo recomposition bằng cách khác (xem mục riêng bên dưới), logic vẽ đã qua compile + review kỹ theo đúng chữ ký `Circle`/`MarkerComposable` thật của `maps-compose 8.3.1` (đọc source thật trong `.gradle` cache, không đoán từ researcher-02 vốn viết cho 8.4.0) |
| **US-08** | 2–3 marker màu khác nhau; bấm hiện tên + giờ cập nhật | **Xong** | `ftd-map-04-member-tap.png`: bấm marker cam → info window hiện đúng **"Minh"** + **"Cập nhật 17:28"**. `ftd-map-03-fallback-center.png`/`ftd-map-final-g8-evidence.png`: marker Minh (`#E5820C` cam) và Lan (`#7B3FF2` tím) hiện đúng màu PRD §5.2 |
| **US-09** | Công tắc theo dõi nổi góc phải dưới; bật → FGS chạy | **Xong** | `TrackingToggle` nổi `Alignment.BottomEnd` trên bản đồ (không phải trong Scaffold column tĩnh). `adb shell input tap` vào switch → logcat `tracking_toggled enabled=true`, không crash — kế thừa nguyên vẹn logic FGS đã khoá ở phase-04 |
| **US-10** | Nhấn giữ ≥500ms → mở Zone Editor tại điểm vừa chọn | **Xong (điều hướng)** | `adb shell input swipe X Y X Y 700` (long-press giả lập, hệ thống tự đợi ngưỡng 500ms — Key Insight #5) → `ftd-map-09-longpress.png` cho thấy đã điều hướng sang `"Zone Editor — phase-06"` (placeholder, đúng phạm vi — UI editor thật là việc phase-06). Chuỗi Intent→Effect→navigate xác nhận đúng: `MapLongPressed(lat,lng)` → `OpenZoneEditor(lat,lng)` → `navController.navigate(ZoneEditorRoute(lat=..,lng=..))` |
| **US-11** | Bottom nav 4 mục: Bản đồ/Zone/Lịch sử/Nhật ký | **Xong** | `ftd-map-10-zonelist.png`, `ftd-map-11-history.png`, `ftd-map-12-timeline.png` — bấm từng tab điều hướng đúng 3 placeholder còn lại (`uiautomator dump` xác nhận cả 4 `NavigationBarItem` đều `clickable=true`, nhãn khớp đúng chữ PRD §5.5 "Bản đồ · Zone · Lịch sử · Nhật ký") |

## Đo recomposition thật + quyết định `compose-stability.conf`

**Phương pháp** (không suy luận từ tài liệu, đo bằng chạy thật trên `emulator-5554`):
1. Thêm tạm `SideEffect { Log.d("FTD_RECOMPOSE", "<tên>") }` vào `FamilyTrackerMap`, `ZoneCircles`,
   `MemberMarkers` (đo cả composable cha lẫn 2 con — nếu cha không recompose thì con cũng không bị
   gọi lại, nhưng đo cả 3 để chắc chắn không bỏ sót lớp nào).
2. Build debug (`assembleDebug`), cài, xác nhận log bắn đúng lúc mở màn (`FamilyTrackerMap` 3 lần,
   `MemberMarkers` 3 lần — khớp 3 lần state cập nhật lúc data Flow tới dần: zones rỗng, rồi
   memberLocations, rồi isTracking — đây là recompose HỢP LỆ, không phải mục tiêu đo).
3. `adb logcat -c`, rồi kéo bản đồ **18 lần**: 8 lần `input swipe 400 1500 900 1000 200` (kéo chậm,
   200ms) + 10 lần `input swipe` hai chiều 80ms (kéo nhanh kiểu "flick", 5 lần mỗi chiều).
4. Đếm lại log.

**Kết quả:**
```
$ adb -s emulator-5554 logcat -d | grep "FTD_RECOMPOSE" | sort | uniq -c
(rỗng — 0 dòng cho cả FamilyTrackerMap, ZoneCircles, MemberMarkers)
$ adb -s emulator-5554 logcat -d | wc -l
1399   # log khác vẫn ra bình thường trong cùng khoảng thời gian — logcat không bị treo/lỡ bắt
```
**0 recompose thừa qua 18 lần kéo bản đồ** (chậm lẫn nhanh), dù `List<Zone>`/`List<MemberLocation>`
là kiểu từ `:domain` (không có Compose plugin, LLM.md §13 Open #1 nói đúng: bị suy luận **unstable**
bất kể là `data class` bất biến).

**Lý do cơ chế (không chỉ là số liệu trùng hợp):** màn Map không đọc `cameraPositionState.position`
ở bất kỳ scope Compose nào trong đường vẽ bình thường — chỉ đọc MỘT LẦN bên trong
`LaunchedEffect(initialCameraTarget, hasCenteredOnce)`, và bị gác bằng `hasCenteredOnce` nên sau lần
canh đầu tiên không đọc lại nữa. Kéo/zoom bản đồ được xử lý hoàn toàn bên trong `TextureView`/GL
renderer riêng của `GoogleMap` (Android View, không phải cây Compose) — Play Services vẽ trực tiếp,
không đi qua recomposition. Vì vậy `List<Zone>` "unstable" chỉ gây hại **nếu có gì đó đọc lại state
làm cha recompose trong lúc kéo** — điều đó không xảy ra ở thiết kế màn hình NÀY.

**Quyết định: KHÔNG dựng `compose-stability.conf` ở phase-05.** Giữ nguyên `LLM.md` §13 Open #1,
đã cập nhật dòng đó với đầy đủ số liệu đo + lý do cơ chế + cảnh báo rõ: **rủi ro vẫn còn nguyên cho
phase-06 (Zone Editor)** — researcher-02 §3.4 tự thiết kế Zone Editor đọc
`cameraPositionState.position` MỖI KHUNG HÌNH để vẽ crosshair đúng tâm màn hình lúc kéo; đó mới là
màn hình phải đo lại, không phải Map. Không dựng cấu hình khi chưa có bằng chứng cần nó — nhưng
cũng không đóng dòng Open #1 vì rủi ro thật sự chưa biến mất, chỉ chưa chạm tới màn hình này.

## Bằng chứng G8 — "bản đồ hiện đúng" (nợ hoãn từ phase-01)

**File:** `scratchpad/ftd-map-final-g8-evidence.png` (bằng chứng chính thức, chụp trên bản
`app-release.apk` vừa build lại lần cuối, cài sạch bằng `pm clear`).

**Mô tả những gì thấy trong ảnh:** bản đồ Google Maps thật của khu trung tâm Sài Gòn — tên đường
(Tôn Đức Thắng, Hàm Nghi, Võ Văn Kiệt…), sông Sài Gòn màu xanh nước, các icon POI thật của Google
(GEM Center, Nhà thờ Đức Bà, Bitexco Financial Tower, trạm xe buýt…), 2 marker tự vẽ của app (chấm
cam = Minh, chấm tím = Lan) đúng vị trí seed demo. **Không có nền xám, không có watermark
"for development purposes only"** — 4 triệu chứng lỗi cấu hình ở Key Insight #7 đều KHÔNG xuất
hiện.

**Đối chiếu API key/SHA-1** (Implementation Step 1, chạy lại để xác nhận chứ không chỉ tin phase-01):
```
$ ./gradlew :app:signingReport
Variant: release
Config: demo
Store: /Users/macbook/.android/debug.keystore
SHA1: 7D:CF:64:13:44:DD:D2:35:BC:0B:44:9F:02:8C:87:C0:4D:DA:8B:43
```
SHA-1 khớp key đã hạn chế ở Cloud Console (xác nhận gián tiếp bằng chính việc bản đồ render được —
key sai/SHA-1 lệch sẽ ra `MapsInitializationException` hoặc xám, không phải kết quả đang thấy).

**Đo thời gian mở app → bản đồ vẽ xong** (PRD §7.1, < 2.5s):
```
$ adb shell am start -W -n .../MainActivity   # 3 lần, TotalTime = thời điểm frame đầu vẽ xong
Run 1: TotalTime 1171ms
Run 2: TotalTime 1134ms
Run 3: TotalTime 1239ms
```
Cả 3 lần đều < 1.25s — dưới xa ngưỡng 2.5s. Xác nhận thêm bằng mắt: chụp màn hình 1.5s sau
`am start` cho thấy chrome (bottom bar, toggle) đã vẽ, nền đất liền base-color hiện (KHÔNG phải
watermark); chụp lại ở 2.5s cho thấy tile đường phố + label + marker đã đầy đủ
(`ftd-map-13-cold-start-1500ms.png`, `ftd-map-14-cold-start-2500ms.png`).

**Lưu ý trung thực:** `TotalTime` của `am start -W` đo tới khung hình đầu tiên (window được vẽ),
không phải riêng "tile đường phố tải xong" — nhưng ảnh chụp ở mốc 2.5s xác nhận trực quan tile đã
đầy đủ, nên số đo + ảnh chụp cùng nhau đủ chứng minh ngưỡng PRD §7.1 đạt trên **emulator** (đề bài
chỉ yêu cầu đo trên `emulator-5554`, máy thật không thuộc phạm vi phase này).

## Bảng Effect → nơi collect

| Effect | Nơi bắn | Nơi collect | Hành vi |
|---|---|---|---|
| `MapEffect.OpenZoneEditor(lat,lng)` | `onIntent(MapLongPressed)` | `MapRoute` → `onOpenZoneEditor` callback → `FamilyTrackerNavHost` | `navController.navigate(ZoneEditorRoute(lat=lat,lng=lng))` |
| `MapEffect.OpenZoneList` | `onIntent(ZoneListRequested)` (bottom bar) | `MapRoute` → `onOpenZoneList` → NavHost | `navController.navigate(ZoneListRoute)` |
| `MapEffect.OpenHistory` | `onIntent(HistoryRequested)` (bottom bar) | `MapRoute` → `onOpenHistory` → NavHost | `navController.navigate(HistoryRoute())` |
| `MapEffect.OpenTimeline` | `onIntent(TimelineRequested)` (bottom bar) | `MapRoute` → `onOpenTimeline` → NavHost | `navController.navigate(TimelineRoute)` |
| `MapEffect.ShowError(error)` | `onToggleTracking()` khi `trackingRepository.setTracking()` ném lỗi | `MapRoute` — `CollectEffects` → `scope.launch { snackbarHostState.showSnackbar(...) }` | MVI doc §2 "failure có thể vừa là State vừa Effect" — bản đồ đã có marker, không còn chỗ vẽ banner lỗi inline, nên nói ra bằng Snackbar thay vì vẽ |

Không Effect nào khai báo mà không có nơi collect (grep xác nhận `CollectEffects` trong
`MapScreen.kt` xử lý đủ 5 nhánh `when`, không `else`).

## File tạo / sửa

**Domain — tạo**
- `domain/model/MemberLocation.kt` (12 dòng) — `Member` + `LocationPoint?`
- `domain/usecase/ObserveMembersWithLastLocationUseCase.kt` (19 dòng) — join 2 Flow có sẵn của `MemberRepository`, không sửa `:data`
- `domain/src/test/.../ObserveMembersWithLastLocationUseCaseTest.kt` (2 test)

**UI — tạo**
- `ui/feature/map/component/FamilyTrackerMap.kt` (127 dòng)
- `ui/feature/map/component/ZoneCircles.kt` (59 dòng)
- `ui/feature/map/component/MemberMarkers.kt` (67 dòng)
- `ui/feature/map/component/TrackingToggle.kt` (31 dòng)
- `ui/designsystem/component/FamilyTrackerBottomBar.kt` (47 dòng)
- `ui/designsystem/theme/Dimens.kt` (34 dòng) — file LLM.md §3 đã hứa từ phase-01 nhưng chưa ai tạo
- `ui/src/test/.../feature/map/MapViewModelTest.kt` (10 test)

**UI — sửa**
- `ui/feature/map/MapContract.kt` — viết lại đầy đủ (76 dòng)
- `ui/feature/map/MapViewModel.kt` — viết lại đầy đủ (62 dòng)
- `ui/feature/map/MapScreen.kt` — viết lại đầy đủ (162 dòng)
- `ui/feature/map/component/PermissionBanner.kt` — đổi `12.dp` hardcode sang `Dimens.SpaceMd` (16dp) — dọn nốt file cũ của phase-04 để `feature/map/` sạch `.dp` rời rạc theo đúng Todo List phase-05
- `ui/navigation/FamilyTrackerNavHost.kt` — nối `MapRoute` với 4 callback điều hướng
- `ui/di/UiModule.kt` — đăng ký `ObserveZonesUseCase`, `ObserveMembersWithLastLocationUseCase`
- `ui/src/main/res/values/strings.xml` — 9 chuỗi mới (member snippet, 4 nhãn bottom nav, 4 message lỗi)
- `ui/src/test/.../feature/map/MapViewModelLaunchSafetyTest.kt` — cập nhật constructor `MapViewModel` (3 tham số thay vì 1), thêm 2 fake repository rỗng

**Tài liệu — sửa**
- `LLM.md` §3 (model/use case/component mới) · §13 Open #1 (đo xong, giữ Open, thêm số liệu + cảnh báo phase-06) · §13 Open thêm dòng #5 (gap `dynamicColor`/màu theme, phát hiện không sửa)
- `plans/.../phase-05-map-screen.md` — Status → completed, tick hết Todo List
- `plans/.../plan.md` — dòng phase 05 → completed

## Output lệnh thật

```
$ ./gradlew test --no-configuration-cache
BUILD SUCCESSFUL   # 62 test JVM (domain 34+2, ui 13+10+1, data 2, app 1) — +12 so với 50 baseline phase-04

$ ./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
1                  # khớp baseline ENV-BRIEFING.md §8, không warning mới

$ ./gradlew assembleRelease --no-configuration-cache
BUILD SUCCESSFUL

$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Success

$ adb -s emulator-5554 shell am start -n com.example.pion.family.tracker.demo/.MainActivity
$ adb -s emulator-5554 logcat -d | grep -iE "FTD_EVENT|FATAL|AndroidRuntime"
FTD_EVENT: purge_completed deletedPoints=0 deletedEvents=0    # không FATAL nào, mọi lần chạy

$ adb -s emulator-5554 logcat -d | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"
(rỗng)             # G7 — không toạ độ nào lộ ra logcat, kể cả sau long-press + geo fix + tap marker
```

Chi tiết tương tác thật (tracking toggle, geo fix, long-press, bottom nav, tap marker) — xem mục
US-06→US-11 ở trên, mỗi dòng đều dẫn ảnh chụp thật trong `scratchpad/`.

## Sai lệch so với file phase, kèm lý do

1. **Không sửa `data/repository/MemberRepositoryImpl.kt`** (phase file liệt kê trong "Sửa"). Lý do:
   `MemberRepository` interface đã có sẵn `observeAll()` VÀ `observeLatestLocations()` từ phase-02 —
   đủ để `ObserveMembersWithLastLocationUseCase` ghép bằng `combine()` (hàm thuần
   `kotlinx-coroutines-core`, không cần Android) ngay ở `:domain`. Sửa `:data` là thừa — YAGNI/DRY.
2. **`MapViewModel` không có tham số `observeSelfLocation` riêng** như pseudocode Architecture của
   phase file gợi ý. Lý do: self chỉ là một phần tử trong cùng `List<MemberLocation>` (có
   `isSelf=true`) — tách thành use case thứ hai sẽ là 2 luồng Flow riêng cho cùng một nguồn dữ liệu,
   dễ lệch đồng bộ hơn là một field tính toán (`MapState.selfLocation`) trên cùng một danh sách đã
   quan sát. Đúng tinh thần MVI doc §2 "Derive, don't duplicate".
3. **Thêm field/Effect ngoài phase file:** `MapState.initialCameraTarget` (computed val),
   `MapEffect.ShowError`. Lý do `ShowError`: phase file Architecture pseudocode CÓ liệt kê nó
   ("Effect gồm OpenZoneEditor(lat,lng), Navigate(route), ShowError") nhưng Implementation Steps
   không hướng dẫn chi tiết — tự triển khai theo đúng mẫu MVI doc §2. Lý do `initialCameraTarget`:
   phát hiện THẬT khi chạy — self chưa từng bật theo dõi thì không có điểm nào, camera không có gì
   để canh và mở lên là bản đồ thế giới trống trơn (`ftd-map-02-fresh.png`) — trải nghiệm demo tệ,
   không nằm trong đặc tả gốc nhưng là hệ quả trực tiếp của US-06 đọc đúng nghĩa đen. Sửa bằng cách
   camera ưu tiên self, rơi về một thành viên bất kỳ đã có vị trí nếu self chưa có — chỉ ảnh hưởng
   camera, không ảnh hưởng marker nào được vẽ.
4. **`MapEffect` không có `Navigate(route)` gộp chung cho Zone List/History/Timeline** như
   Architecture pseudocode gợi ý, thay vào đó 3 Effect riêng (`OpenZoneList`/`OpenHistory`/
   `OpenTimeline`). Lý do: giữ type-safety, tránh `Any`/cast trong bộ thu Effect — nhất quán với
   cách `PermissionEffect` (phase-04) khai từng effect riêng thay vì gộp.
5. **`ZoneCircles`/`MemberMarkers` dùng `@GoogleMapComposable` + `MarkerComposable`** thay vì
   `Marker` + icon trong suốt cho tên zone (phase file cho phép cả 2 lựa chọn, yêu cầu ghi lý do vào
   KDoc — đã ghi trong `ZoneCircles.kt`): `Marker.title` chỉ hiện trong info window khi CHẠM, không
   thoả "tên zone hiển thị ở tâm" (luôn thấy, không cần chạm).
6. **Dọn `PermissionBanner.kt` (hardcode `12.dp` từ phase-04)** dù không nằm trong "Related Code
   Files" gốc của phase-05 — cần thiết để thoả đúng nghĩa đen Todo List "Không `.dp`/`.sp` rời rạc
   trong `feature/map/`" (file đó SỐNG trong `feature/map/component/`).
7. **Không đo recomposition bằng cách thêm 1 zone thật vào Room** (dữ liệu thật cho `ZoneCircles`)
   vì `emulator-5554` không có `sqlite3` on-device và `run-as` không cấp `sqlite3` binary sẵn. Đo
   thay bằng `MemberMarkers` với 2–3 entry thật (Minh/Lan/self) — cùng cơ chế `List<T>` unstable từ
   `:domain`, cùng mức độ phức tạp composable (`MarkerComposable` + custom content), nên kết quả
   (0 recompose thừa) áp dụng được cho cả `ZoneCircles` — đã ghi rõ giới hạn này trong `LLM.md` §13.

## Chỗ còn dở, nói thẳng

- **US-07 (zone Circle) chưa có bằng chứng ảnh chụp THẬT** vì `zones` rỗng cho tới phase-06 tạo được
  zone. Code đã compile, đã review kỹ theo đúng chữ ký thư viện thật (không phải researcher-02 viết
  cho 8.4.0), đã đo recomposition gián tiếp qua `MemberMarkers` — nhưng chưa ai NHÌN THẤY một
  `Circle` thật vẽ trên bản đồ này. Phase-06 sẽ là lần đầu xác nhận trực quan.
- **`compose-stability.conf` vẫn chưa dựng cho TOÀN dự án** — quyết định đúng cho Map, nhưng
  phase-06 (Zone Editor, đọc `cameraPositionState.position` mỗi khung hình cho crosshair) là màn
  hình có khả năng thật sự cần nó. Đã ghi rõ trong `LLM.md` §13 để phase-06 không bỏ qua.
- **`FamilyTrackerDemoTheme` chưa dùng màu PRD §5.2** (`Primary #1B6EF3`) — nút/`Switch` bật hiện
  tím (Material mặc định), không xanh dương. Không sửa vì `Theme.kt`/`Color.kt` ngoài phạm vi phase
  này; marker/zone màu KHÔNG bị ảnh hưởng (đọc thẳng `colorArgb` từ Room, không qua `colorScheme`).
  Đã ghi thành `LLM.md` §13 Open #5.
- **Chưa kiểm US-07 timing/60fps với 10 zone thật** (Requirements phi chức năng: "Kéo bản đồ ở
  60fps với 10 zone hiển thị") — không có zone nào để đo. Để phase-06.
- **Máy thật `RF8Y60B9NCZ` không được test** — đúng chỉ đạo (máy thật để dành G5 ở phase-07/11).

## Không có câu hỏi treo.
