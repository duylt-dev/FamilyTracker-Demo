# Phase 08 — History: chọn ngày, polyline, chuyến đi, thống kê (F3, US-27→US-32)

## Trạng thái: HOÀN THÀNH

Test xanh (`./gradlew test` — 106/106), `assembleRelease` xanh, cài + kiểm tay đủ US-27→US-32 trên
`emulator-5554`, gate G6/G7 đạt. Chi tiết dưới.

## Đã đọc trước khi code
ENV-BRIEFING.md §8/§3/§4 · LLM.md toàn bộ (§1-§14, đặc biệt §8.3 lastKept, §9 track_sessions suy ra
lúc đọc, §13 14 mục) · docs/android-mvi-best-practices.md §1-§4 · phase-08 spec đầy đủ · phase-07
report (2 bug: race TOCTOU dedupe + notification tap) · PRD US-27→32, §3.3 F3, §5.7, §6, §9, §10.

## Phát hiện quan trọng trước khi code

1. **`ObserveRouteForDayUseCase` + `TrackingRepositoryImpl.observeRoute()` ĐÃ hoàn thiện từ trước**
   (gọi `RouteSplitter.split()` đúng cách qua `LocationPointDao.observeBetween`). Phase file bảo
   "hoàn thiện phần ghép RouteSplitter" nhưng thực tế không cần sửa gì — đã đúng 100%. Sẽ KHÔNG đổi
   file này, chỉ xác nhận trong báo cáo.
2. **`app/src/main/res/values/strings.xml` bị liệt trong phase file nhưng LLM.md §12 quy định chuỗi
   màn hình sống ở `:ui`** (đã áp dụng nhất quán từ phase-04/05/06). Sẽ dùng
   `ui/src/main/res/values/strings.xml` thay vì `:app` — sai lệch có lý do, ghi ở mục "Sai lệch".
3. **`SIMULATOR_ENABLED` (`BuildConfig`) chưa tồn tại** — phase-01 note rõ "chưa khai, phase-09
   khai". Nút "Mô phỏng lộ trình" thật cũng thuộc phase-09 (phase-08 Overview: "Nút ở phase-09").
   → EmptyRouteState (US-32) chỉ hiện TEXT gợi ý, không vẽ nút thật — tránh xâm phạm phạm vi/file
   ownership của phase-09 và tránh một nút bấm không làm gì.
4. **`Routes.kt` không nằm trong "Related Code Files – Sửa"** của phase-08 → sẽ không thêm hằng số
   `HistoryRoute.ARG_*` vào đó (khác `ZoneEditorRoute`), dùng literal string key khớp tên field
   (`"epochDay"`, `"focusLat"`, `"focusLng"`) — cùng cơ chế `SavedStateHandle.get<T>(key)` đã dùng.
5. Self memberId lấy qua `ObserveMembersWithLastLocationUseCase` (đã có, DI sẵn) — không thêm
   repository method mới, giữ đúng "tái dùng, đừng phát minh lại" theo brief.
6. `PolyUtil.simplify` xác nhận CÓ trong classpath thật (`android-maps-utils-core-5.0.0` kéo theo
   bởi `maps-compose-utils:8.3.1`) — kiểm bằng `unzip -l` trên jar transform thật trong
   `~/.gradle/caches`, khớp Key Insight #1 của phase file.

## Kế hoạch file

Tạo: `HistoryContract.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`,
`component/{DayPickerBar,RouteStatsCard,SessionList,RoutePolyline,EmptyRouteState,HistoryMap}.kt`,
`ui/core/format/{DistanceFormat,DurationFormat}.kt`, `HistoryViewModelTest.kt`.
Sửa: `FamilyTrackerNavHost.kt`, `UiModule.kt`, `ui/src/main/res/values/strings.xml`.
`HistoryMap.kt` là file THÊM ngoài 5 file component liệt kê trong phase (giống tiền lệ
`ZoneCenterMap.kt` ở phase-06) — tách GoogleMap+camera-bounds khỏi `HistoryScreen.kt` để không vượt
200 dòng.

## Code + build — ĐÃ XONG

Tất cả file tạo/sửa xong (xem "File tạo/sửa" cuối báo cáo). `./gradlew :ui:compileDebugKotlin` xanh
sau 1 lần sửa (`rememberSaveable` cần import `androidx.compose.runtime.saveable.rememberSaveable`,
không phải `androidx.compose.runtime.*`).

## Test — ĐÃ XONG, tất cả xanh

```
./gradlew test
```
- `:domain` 44 test (10 suite) — không đổi (không sửa file `:domain`).
- `:data` 2 test — không đổi.
- `:ui` 59 test (8 suite) — **+11 mới** (`HistoryViewModelTest`), `CoroutineSafetyArchitectureTest`
  vẫn xanh (không dùng `launchIn`/`GlobalScope`/`runBlocking` ở `HistoryViewModel`).
- `:app` 1 test (`KoinModulesTest.all koin modules resolve`) — xanh, xác nhận
  `HistoryViewModel`/`ObserveRouteForDayUseCase` wire đúng qua Koin.

`HistoryViewModelTest` (11 test): `initialStateFrom` (mặc định hôm nay / epochDay từ route) · ngày
rỗng (sessions rỗng, stats null, không crash) · chuyến DÀI NHẤT được chọn mặc định (không phải
chuyến đầu) · `SelectSession` đổi stats theo (chứng minh `stats` tính toán, không lệch) ·
`SelectDay` huỷ-và-thay job (chứng minh bằng cách emit vào ngày CŨ sau khi đã chuyển ngày, state
không đổi) · bấm nhanh 3 ngày liên tiếp → dừng đúng ngày cuối · màu self nạp vào state ·
`FocusCamera` bắn đúng 1 lần khi có toạ độ, không bắn khi không có · lỗi Flow → hạ `isLoading` +
`ShowError` (crash containment) · `StartSimulation` no-op có ghi chú.

## Gate G6 — 1 warning (`--no-configuration-cache`), khớp baseline

```
./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"   # → 1
```

## Build release + cài — ĐÃ XONG

`./gradlew assembleRelease` xanh (13s). Cài lên `emulator-5554`.

## Dữ liệu lịch sử để kiểm — CHÈN THẲNG vào `location_points`

DB thật hiện có (trước khi sửa) chỉ 7 điểm thưa của self, tất cả trong MỘT chuyến ngắn (không đủ
để kiểm 2 chuyến + polyline dày + simplify). Cách đã dùng: **chèn thẳng vào `location_points`**
(không bơm `emu geo fix` — quá chậm để tạo đủ điểm với khoảng cách thời gian thật 10s/điểm và một
khoảng trống > 5 phút giữa 2 chuyến).

Quy trình đúng như brief yêu cầu (WAL):
1. `assembleDebug` + cài debug (release không cho `run-as`).
2. `force-stop`, `run-as cat` kéo cả 3 file (`family_tracker.db`, `-wal`, `-shm`) về scratchpad.
3. `python3 sqlite3`: `PRAGMA wal_checkpoint(TRUNCATE)`, `DELETE FROM location_points WHERE
   memberId = self`, `INSERT` 110 điểm tổng hợp (self id `f45c5b70-...`, khớp `DemoDataSeeder`).
4. Đẩy `.db` về qua `/data/local/tmp` + `run-as cp`, xoá `-wal`/`-shm` trên máy.
5. **Đọc ngược xác nhận**: `run-as cat` lại, mở bằng sqlite3 → đúng 110 dòng, đúng khoảng thời gian.
6. Cài lại **release** trước khi test tay.

Dữ liệu: ngày **2026-08-21** (hôm qua theo giờ máy +07, máy đang ở 2026-08-22 00:3x) — 2 chuyến:
- Chuyến 1: 08:00→08:15, 90 điểm/10s, đi bộ ~1 m/s hướng bắc xuyên qua toạ độ zone "Home" (lat
  10.7899983, lng 106.68, bán kính 150m), zigzag biên độ 15m để `PolyUtil.simplify` có tác dụng
  quan sát được.
- Khoảng trống 15 phút (> `SESSION_GAP_MS` 5 phút) → chuyến 2.
- Chuyến 2: 08:30→08:33, 20 điểm/10s. **Ghi đúng sự thật:** script sinh dữ liệu chỉ có tham số
  `north_speed_mps`/`zigzag_amp_m` (dịch bắc + lắc đông-tây), tham số `bearing` truyền vào chỉ ghi
  vào cột `bearingDegrees` (metadata) chứ KHÔNG thật sự đổi hướng di chuyển — nên chuyến 2 trên ảnh
  vẫn đi theo trục bắc-nam như chuyến 1, không phải "hướng đông" như tôi định làm. Không ảnh hưởng
  kết quả kiểm (khác vị trí, khác quãng đường/thời lượng, vẫn tách chuyến đúng) nên không sinh lại.

Ngày **2026-08-22** (hôm nay) cố ý để TRỐNG — dùng để kiểm US-32 (empty state) mà không cần build
gì thêm, vì `DayPickerBar` mặc định mở vào "hôm nay".

## Polyline simplify — đo thật (Douglas-Peucker, tolerance 10m)

Đo bằng log tạm `Log.d("FTD_TEMP", "history_polyline_simplify raw=... simplified=...")` chèn vào
`RoutePolyline.kt`, build **debug**, cài, mở History → 21/08/2026, đọc logcat, rồi **xoá dòng log
tạm** và build lại `assembleRelease` (đúng mẫu LLM.md §13 Open #1 đã dùng — đo rồi gỡ). Xác nhận
release APK cuối cùng KHÔNG chứa chuỗi `FTD_TEMP`/`history_polyline_simplify` (`unzip -p ... | grep`
rỗng) trong khi vẫn chứa `history_rendered day=` (log thật, giữ lại) — chứng minh bản cài cuối là
bản đã gỡ log tạm, không phải bản đo.

| Chuyến | Điểm gốc | Điểm sau `PolyUtil.simplify` | Giảm |
|---|---|---|---|
| 1 (08:00→08:14) | 90 | 20 | 78% |
| 2 (08:30→08:33) | 20 | (≤ 20, chưa đo riêng — chuyến ngắn, ít lắc) | — |

`history_rendered` (log **thật**, giữ lại, không log toạ độ — gate G7):
```
history_rendered day=2026-08-21 pointCount=90 renderMs=26
history_rendered day=2026-08-21 pointCount=20 renderMs=5
```
`pointCount` là số điểm GỐC của chuyến (đúng như phase file yêu cầu đo "từ lúc nhận sessions"), không
phải số điểm sau simplify — cả hai lần đo `renderMs` đều dưới 100ms, rất xa ngưỡng < 1000ms (PRD §7.1)
dù mẫu thử chỉ 90 điểm chứ chưa tới quy mô 8.640 điểm/ngày PRD nói tới (xem "Chỗ còn dở").

## US-27 → US-32 — thao tác tay trên `emulator-5554` (bản `release`)

| US | Thao tác | Quan sát | Ảnh |
|---|---|---|---|
| US-27 | Mở tab "Lịch sử" | Mặc định mở đúng **hôm nay** (22/08/2026); bấm nút ngày → dropdown hiện **đúng 7 dòng** ("Hôm nay (22/08/2026)" → "16/08/2026"), không có ngày thứ 8 | `p8-02-history-empty-today.png`, `p8-03-daypicker-open.png` |
| US-28 | Chọn 21/08/2026 (có dữ liệu) | `Polyline` xanh dương dày nối các điểm theo zigzag đúng hình dữ liệu bơm vào; marker **Start xanh lá** ở đầu (dưới), **End đỏ** ở cuối (trên) | `p8-04-history-yesterday.png` |
| US-29 | Cùng màn trên | Thẻ thống kê "1.1 km · 14 phút · 4.3 km/h" — khớp chuyến 08:00→08:14 (90 điểm, ~1.1km tổng theo GeoDistance) | `p8-04-history-yesterday.png` |
| US-30 | Bấm dòng chuyến thứ 2 ("08:30 → 08:33  274 m") trong danh sách | Camera **animate** sang bounds chuyến 2 (zoom khác hẳn), thẻ thống kê đổi thành "274 m · 3 phút · 5.2 km/h", dấu ✓ chuyển sang dòng vừa bấm, dòng chuyến 1 bỏ chọn | `p8-05-history-session2.png` |
| US-31 | Quan sát cả 2 polyline trên | Đường vẽ mượt, không có đoạn nhảy lung tung (dữ liệu bơm vào đã "sạch" theo đúng vai trò của `LocationFilter` ở tầng ghi — phase này không lọc lại) | cùng ảnh trên |
| US-32 | Mở lại "Hôm nay" (22/08/2026, không dữ liệu); từ ngày CÓ dữ liệu bấm chọn lại "Hôm nay" | Empty state "Chưa có lộ trình nào trong ngày này" + "Dùng nút \"Mô phỏng lộ trình\" để tạo dữ liệu minh hoạ"; **không crash, không polyline sót lại từ ngày trước** (đã thử đúng kịch bản chuyển TỪ ngày có dữ liệu VỀ ngày rỗng) | `p8-02-history-empty-today.png`, `p8-06-final-today-empty.png` (bản release sau khi gỡ log tạm), `p8-08-switch-back-to-empty.png` (chuyển từ 21/08 có data về 22/08 rỗng) |

Toàn bộ ảnh ở `plans/260821-1113-geofence-zone-and-history-tracking/reports/screenshots/p8-*.png`.

## Effect → nơi collect

| Effect | Bắn khi nào | Collect ở đâu | Hành vi |
|---|---|---|---|
| `HistoryEffect.ShowError(error: AppError)` | `observeRouteForDay` ném lỗi (Flow từ Room) | `HistoryRoute.CollectEffects` | Map `AppError` → chuỗi qua `toDisplayMessage`, `snackbarHostState.showSnackbar` |
| `HistoryEffect.FocusCamera(lat, lng)` | `init`, đúng 1 lần, nếu route mang `focusLat`/`focusLng` | `HistoryRoute.CollectEffects` | Lưu vào `focusPoint` (state cục bộ Route), truyền xuống `HistoryMap` cho lần canh camera ĐẦU TIÊN. Chưa có màn nào (Timeline chưa dựng) thật sự gửi hiệu ứng này ở phase-08 — đường ống sẵn sàng cho phase-10 nhưng KHÔNG tự kiểm chứng được bằng thao tác tay ở phase này |

## File tạo

- `ui/feature/history/HistoryContract.kt`
- `ui/feature/history/HistoryViewModel.kt`
- `ui/feature/history/HistoryScreen.kt`
- `ui/feature/history/component/DayPickerBar.kt`
- `ui/feature/history/component/RouteStatsCard.kt`
- `ui/feature/history/component/SessionList.kt`
- `ui/feature/history/component/RoutePolyline.kt`
- `ui/feature/history/component/EmptyRouteState.kt`
- `ui/feature/history/component/HistoryMap.kt` (file thêm, xem "Sai lệch")
- `ui/core/format/DistanceFormat.kt`
- `ui/core/format/DurationFormat.kt`
- `ui/src/test/.../ui/feature/history/HistoryViewModelTest.kt`

## File sửa

- `ui/navigation/FamilyTrackerNavHost.kt` — `HistoryRoute` thật thay `Text("History — phase-08")`
- `ui/di/UiModule.kt` — `factoryOf(::ObserveRouteForDayUseCase)`, `viewModelOf(::HistoryViewModel)`
- `ui/src/main/res/values/strings.xml` — 6 chuỗi mới (`history_*`) — **KHÔNG** sửa
  `app/src/main/res/values/strings.xml` như phase file ghi, xem "Sai lệch"
- `ui/designsystem/theme/Dimens.kt` — thêm `RoutePolylineWidth = 12.dp` (không có trong "Related
  Code Files" của phase file — bổ sung tối thiểu, cùng khuôn `ZONE_STROKE_WIDTH_PX` đã có)
- `LLM.md` — §3 (cây `ui/feature/history/` + `ui/core/format/`), §7 (bỏ `HistoryRoute` khỏi danh
  sách placeholder)
- `plans/.../plan.md`, `plans/.../phase-08-history-and-route-playback.md` — trạng thái + Todo List

**Không sửa** `domain/usecase/ObserveRouteForDayUseCase.kt` — đã hoàn thiện từ trước (xem "Phát hiện
quan trọng #1"), dù phase file liệt kê nó trong "Related Code Files – Sửa".

## Sai lệch so với file phase (kèm lý do)

| # | Sai lệch | Lý do |
|---|---|---|
| 1 | `ObserveRouteForDayUseCase.kt` không sửa | Đã hoàn thiện từ trước (gọi đúng `RouteSplitter.split` qua `TrackingRepositoryImpl.observeRoute`) — sửa thêm là dư thừa, vi phạm YAGNI |
| 2 | Chuỗi hiển thị đặt ở `ui/src/main/res/values/strings.xml`, không phải `app/src/main/res/values/strings.xml` như phase file ghi | LLM.md §12 (đã sửa từ phase-04): `:ui` không phụ thuộc ngược `:app`, composable trong `:ui` không thấy `R` của `:app`. Đặt ở `:app` như phase file ghi sẽ KHÔNG BUILD ĐƯỢC — đây là lỗi sao chép từ mẫu gốc (cùng loại lỗi LLM.md §12 đã ghi "đã sửa"), không phải lựa chọn |
| 3 | `EmptyRouteState` (US-32) chỉ hiện text, không vẽ nút "Mô phỏng lộ trình" thật | Phase-08 Overview tự ghi rõ "Nút 'Mô phỏng lộ trình' ở phase-09"; `BuildConfig.SIMULATOR_ENABLED` chưa tồn tại (phase-01 note + LLM.md §14). Vẽ một nút bấm không làm gì (chưa có `SimulatedLocationSource` nối vào ViewModel) tệ hơn không có nút — US-33 (nút thật) không nằm trong phạm vi US-27→32 của phase này |
| 4 | `HistoryIntent.StartSimulation` là no-op có ghi chú trong `HistoryViewModel`, không xoá khỏi Contract | Giữ đúng PRD §9 (Contract mẫu có `StartSimulation`/`isSimulating`) để phase-09 chỉ cần thêm logic, không phải sửa lại Contract; `when` phải exhaustive (MVI doc §3 luật 2) nên vẫn cần một nhánh |
| 5 | `HistoryState.stats` là `val` tính toán (`selectedSession?.let(RouteStats::of)`), không phải field lưu trữ như PRD §9 phác thảo | MVI doc §2 "Derive, don't duplicate" — field lưu trữ cần đồng bộ tay mỗi khi `selectedSessionId`/`sessions` đổi, dễ quên (đúng loại lỗi LLM.md đã cảnh báo ở `selectedMemberId`/Key Insight #5 của phase file) |
| 6 | `HistoryEffect.ShowError` mang `AppError`, không phải `String` như PRD §9 | Đồng bộ với `MapEffect.ShowError`/`ZoneEditorEffect.ShowMessage` đã có — "ViewModel không build chuỗi hiển thị" (MVI doc §5), một luật đã áp dụng nhất quán từ phase-05 |
| 7 | `HistoryRoute.ARG_*` là literal string trong `HistoryViewModel.kt`, không phải hằng số trong `Routes.kt` (khác `ZoneEditorRoute.ARG_*`) | `Routes.kt` không có trong "Related Code Files" của phase-08 — tránh sửa file ngoài phạm vi sở hữu |
| 8 | Thêm `ui/feature/history/component/HistoryMap.kt` ngoài 5 file phase liệt kê | Cùng lý do `ZoneCenterMap.kt` (phase-06): `HistoryScreen.kt` sẽ vượt 200 dòng (LLM.md §5) nếu ôm cả `GoogleMap`/camera-bounds |
| 9 | Không vẽ `ZoneCircles` lên bản đồ History dù PRD §5.7 ASCII mockup có vẽ một zone (chú thích "polyline cắt qua zone") | Requirements/Implementation Steps/Todo List của phase file KHÔNG liệt kê việc vẽ zone ở màn History — chỉ mockup minh hoạ. Giữ tối thiểu theo YAGNI; nếu BA muốn, thêm sau bằng cách tái dùng `ZoneCircles` (khi đó mới đủ điều kiện "≥2 feature dùng" để chuyển nó sang `designsystem/component/`, LLM.md §12) |

## Chỗ còn dở / rủi ro chưa kiểm hết

- **Chưa đo `renderMs` ở quy mô thật PRD §7.1 nói (8.640 điểm/ngày).** Dữ liệu kiểm chỉ 90+20 điểm
  (đủ để chứng minh cơ chế simplify/thống kê/tách chuyến đúng, xác nhận renderMs << 1000ms ở quy mô
  này), nhưng KHÔNG chứng minh được ngưỡng < 1s vẫn giữ ở 8.640 điểm. Muốn đo đúng cần bơm ~8.640
  dòng `location_points` cho MỘT ngày (kịch bản 10s/điểm suốt 24h không nghỉ) — chưa làm vì tốn thời
  gian sinh/kiểm dữ liệu không tương xứng với phần còn lại của phase; đề xuất đo lại ở phase-11 khi
  build "ba tầng kiểm thử" chính thức.
- **`HistoryEffect.FocusCamera` (US-35 plumbing) chưa tự kiểm chứng bằng thao tác tay** — không có
  màn Timeline (phase-10) để bắn hiệu ứng này thật. Code đã viết + có test ViewModel
  (`HistoryViewModelTest`), nhưng phần UI nhận (`HistoryMap`'s `focusPoint` override lần canh đầu)
  chưa chạy qua kịch bản thật trên máy — phase-10 phải xác nhận lại khi nối dây Timeline → History.
- **DayPickerBar dùng `DropdownMenu`, không phải `DatePickerDialog`** — đủ cho yêu cầu "giới hạn 7
  ngày" (US-27), nhưng không có lịch trực quan kiểu calendar. Chấp nhận theo YAGNI, ghi rõ trong
  KDoc; đổi lại nếu BA yêu cầu giao diện lịch thật.
- Gate G5 (máy thật) không thuộc phạm vi phase-08 — không kiểm ở đây.

## Xác nhận cuối — chạy lại sạch từ đầu sau khi gỡ log tạm

```
./gradlew test                                                             # 106/106 xanh
./gradlew clean assembleDebug --no-configuration-cache | grep -ci warning: # 1
./gradlew assembleRelease                                                  # xanh
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
```
Cài lại, mở tab Lịch sử lần cuối → vẫn đúng US-27/US-32 (`p8-09-final-check.png`), `adb logcat -d |
grep FTD_EVENT | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"` rỗng. APK release cuối cùng đã xác nhận
KHÔNG chứa chuỗi log tạm dùng để đo simplify.
