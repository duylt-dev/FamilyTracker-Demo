# Phase 09 — Route Simulator (F5)

## Context Links

- [`plan.md`](plan.md) · [`phase-08`](phase-08-history-and-route-playback.md) · [`phase-07`](phase-07-geofence-and-notification.md)
- [`LLM.md`](../../LLM.md) §8.4 (`LocationSource` — cổng cho demo đi chung đường với thật)
- PRD §2.6 US-33 · §3.5 F5 · §6 (`SIMULATOR_ENABLED`) · §11.1 **gate G4**
- [`research/researcher-01-geofencing-and-background-location.md`](research/researcher-01-geofencing-and-background-location.md) §6.3, §9 câu hỏi 2

## Overview

| | |
|---|---|
| Priority | **P0 — bắt buộc để demo**, không phải "có thì tốt" |
| Status | completed |
| Effort | 5h |
| Story ánh xạ | **US-33** — feature **F5** |
| Gate | **G4** — nút mô phỏng sinh **cả** thông báo vào và ra, trong ≤ 40 giây, trên thiết bị demo thật |

Buổi demo diễn ra trong phòng họp. Không có nút này thì cách duy nhất để BA nhìn thấy thông báo zone
là có người cầm điện thoại đi bộ ra ngoài rồi quay lại, và cả phòng ngồi chờ 3 phút cho geofence bắn
(PRD §3.5).

## Key Insights

1. **Nguồn giả lập đi vào hệ thống bằng đúng cửa mà nguồn thật đi** (`LLM.md` §8.4). Cùng
   `LocationFilter`, cùng `ZoneEvaluator`, cùng `ZoneEventRepository`, cùng `ZoneNotifier`.
   Giá của việc viết một đường riêng cho demo: thứ được demo không phải thứ được ship, và lỗi trong
   đường thật không lộ ra cho tới khi ai đó cầm điện thoại ra đường.
2. **Simulator không thay thế được gate G5.** Nó chứng minh đường foreground (tức thì). Đường
   `GeofencingClient` khi app đóng vẫn phải đo bằng người đi bộ thật — hai gate khác nhau cho hai
   đường khác nhau (`LLM.md` §8.1).
3. **Lộ trình phải cắt qua zone thật, tính từ vị trí hiện tại của thiết bị** (PRD §3.5). Thuật toán:
   lấy zone gần vị trí hiện tại nhất; dựng đoạn thẳng đi từ điểm cách tâm `R + 150m` ở một phía, qua
   tâm, ra tới `R + 150m` ở phía đối diện; nội suy đều theo thời gian. Buffer 150m > `ZONE_EXIT_BUFFER_M`
   (30m) nên chắc chắn sinh cả ENTER và EXIT.
4. **Chưa có zone nào thì tạo một zone mẫu trước** (PRD §3.5) — bán kính 150m quanh vị trí hiện tại,
   tên "Zone mẫu", cả hai công tắc thông báo bật. Zone này đi qua `SaveZoneUseCase` bình thường nên
   nó cũng được đăng ký geofence thật.
5. **Nhịp phát nhanh hơn thực tế nhưng dấu thời gian phải hợp lý.** Nếu 20 điểm phát trong 30 giây và
   `recordedAt` cũng cách nhau 1.5 giây, thì với `MIN_DISTANCE_M = 10m` tốc độ suy ra là 24 km/h —
   dưới `MAX_SPEED_KMH = 200`, an toàn. **Không được** giả thời gian thành 10 giây/điểm để "trông
   thật hơn": khi đó `RouteSplitter` và `RouteStats` sẽ báo một chuyến đi 5 phút mà thực tế 30 giây,
   và số liệu trên thẻ thống kê sẽ vô lý.
6. **`SIMULATOR_ENABLED` KHÔNG được gắn vào `BuildConfig.DEBUG`** (PRD v1.2 §6). Buổi demo chạy bản
   **`release`**; gắn nút vào cờ DEBUG sẽ ẩn mất F5 — một feature P0 và là thứ quyết định buổi demo —
   khỏi chính bản đem đi demo. Khai riêng:
   `buildConfigField("boolean", "SIMULATOR_ENABLED", "true")` trong `defaultConfig`, có mặt ở **cả hai**
   variant. `buildFeatures { buildConfig = true }` của `:app` đã bật từ phase-01.
7. **`:ui` không được bật `buildConfig` chỉ để đọc cờ này.** `BuildConfig` sinh riêng cho từng module;
   bật ở `:ui` sẽ tạo một lớp `BuildConfig` thứ hai **không có** `SIMULATOR_ENABLED`, và người viết
   code sẽ import nhầm lớp gần tay hơn. Truyền xuống bằng Koin:
   `single(named("simulatorEnabled")) { BuildConfig.SIMULATOR_ENABLED }` khai ở `:app`, composable đọc
   qua `koinInject`.

## Requirements

**Chức năng**
- Nút "▶ Mô phỏng lộ trình" ở cuối màn History, hiện ở **cả hai** variant (PRD v1.2 §6 và §5.7).
- Lộ trình chạy ~30 giây, đi qua ít nhất 1 zone (vào rồi ra), sinh thông báo **thật** và ghi vào lịch
  sử **thật** (US-33).
- Chưa có zone → tạo zone mẫu trước rồi mới chạy.
- Đang chạy → nút chuyển thành "Đang mô phỏng…" và bị vô hiệu (`isSimulating` đã có trong state phase-08).
- Rời màn hình giữa chừng → mô phỏng **vẫn chạy tới hết** (nó đang sinh thông báo, dừng nửa chừng để
  lại một ENTER không có EXIT trong Timeline).

**Phi chức năng**
- Tổng thời gian từ lúc bấm tới thông báo "Đã rời" ≤ 40 giây (gate G4).
- Không có nhánh code riêng cho demo trong `LocationFilter`, `ZoneEvaluator`, `ZoneEventRepository`.

## Architecture

```
HistoryScreen [▶ Mô phỏng] ─▶ HistoryIntent.StartSimulation
        └─▶ StartSimulationUseCase
              ├─ zones rỗng? → SaveZoneUseCase(zone mẫu quanh vị trí hiện tại)
              ├─ dựng RouteBlueprint(zone gần nhất, buffer 150m, N điểm)
              └─▶ SimulatedLocationSource.play(blueprint)
                       │  Flow<LocationPoint>  ← cùng kiểu, cùng cổng
                       ▼
              LocationTrackingService  (đang chạy hoặc được khởi động)
                       └─ LocationFilter ─▶ TrackingRepository.record()
                                          └─ ZoneEvaluator ─▶ ZoneEventRepository.record()
                                                                └─ ZoneNotifier 🔔
```

`SimulatedLocationSource` là `LocationSource` thứ hai, chọn bằng Koin qualifier `named("simulated")`
đã khai báo từ phase-04. Service chuyển nguồn khi mô phỏng bắt đầu và trả lại nguồn thật khi xong.

## Related Code Files

**Tạo**
- `domain/tracking/RouteBlueprint.kt` — hàm thuần sinh danh sách `(lat, lng, offsetMs)` đi xuyên zone
- `domain/usecase/StartSimulationUseCase.kt`
- `domain/src/test/.../RouteBlueprintTest.kt`
- `ui/feature/history/component/SimulateRouteButton.kt`

**Sửa**
- `data/location/SimulatedLocationSource.kt` — điền thân (phase-04 để rỗng)
- `data/location/LocationTrackingService.kt` — chuyển nguồn khi mô phỏng
- `ui/feature/history/HistoryContract.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`
- `data/di/DataModule.kt`, `ui/di/UiModule.kt`, `app/FamilyTrackerApp.kt` (khai `named("simulatorEnabled")`)
- `app/build.gradle.kts` — `buildConfigField("boolean", "SIMULATOR_ENABLED", "true")` trong `defaultConfig`
- `app/src/main/res/values/strings.xml`
- `LLM.md` §3 — thêm `RouteBlueprint`

## Implementation Steps

1. `RouteBlueprint.build(currentLat, currentLng, zone, pointCount = 20, totalMillis = 30_000)`:
   trả `List<SimulatedFix>` gồm toạ độ + `offsetMs`. Đường đi: từ `(tâm + hướng × (R + 150m))` qua
   tâm tới `(tâm − hướng × (R + 150m))`, hướng là vector từ vị trí hiện tại tới tâm zone (nếu trùng
   tâm thì chọn hướng bắc). Nội suy tuyến tính, `offsetMs` cách đều.
   **Hàm thuần ở `:domain`, test bằng JUnit** — không có Android trong đó.
2. Test `RouteBlueprintTest`: chạy blueprint qua `ZoneEvaluator` với đúng zone đó, khẳng định chuỗi
   sự kiện là **đúng một ENTER rồi đúng một EXIT**. Đây là bài test rẻ nhất bảo vệ gate G4 — nếu nó
   xanh, thất bại trên thiết bị chỉ còn có thể do quyền hoặc thông báo.
3. `SimulatedLocationSource.stream()` phát theo `offsetMs` bằng `delay`, `accuracyMeters = 8f`,
   `speedMps` suy từ hai điểm liên tiếp, `recordedAt = now + offsetMs` (Key Insight #5).
4. `StartSimulationUseCase`: nếu `zoneRepository.count() == 0` → tạo zone mẫu qua `SaveZoneUseCase`
   (để nó cũng được đăng ký geofence); chọn zone gần nhất; dựng blueprint; ra lệnh cho service chuyển
   sang nguồn giả lập. Log `FTD_EVENT simulation_started` và `simulation_finished durationMs eventsRaised`.
5. `LocationTrackingService`: thêm action `ACTION_SIMULATE`. Khi nhận, huỷ job đang collect nguồn thật,
   collect nguồn giả lập tới khi flow kết thúc, rồi quay lại nguồn thật. **Job con phải là con cấu
   trúc của job cha, không phải một field bị huỷ bằng tay** (MVI doc §3) — cùng luật, áp cho service.
   Nếu công tắc theo dõi đang tắt, service được khởi động cho riêng lượt mô phỏng rồi dừng lại sau đó.
6. `HistoryViewModel`: Intent `StartSimulation` → `isSimulating = true` → `launchSafely(onError = { setState { copy(isSimulating = false) } })`.
   **Cờ phải hạ ở cả nhánh lỗi**, nếu không nút mô phỏng chết vĩnh viễn (MVI doc §1).
   Kết thúc → `isSimulating = false`; polyline mới đến qua `observeRouteForDay`, không copy tay.
7. `SimulateRouteButton` chỉ compose khi `simulatorEnabled` (Koin, Key Insight #6, #7). Cùng cờ đó
   được `EmptyRouteState` (phase-08) và `EmptyTimelineState` (phase-10) đọc lại — một cờ, ba nơi dùng.
8. Kiểm chứng G4 **trên APK release đã cài** (đó là bản đem đi demo): bấm nút, bấm giờ tới thông báo
   "Đã rời". Đo trước trên emulator cho nhanh, nhưng số ghi vào `gate-evidence.md` phải là số đo trên
   thiết bị demo thật (PRD §11.1 G4 nói rõ "trên thiết bị demo thật"). Ghi 3 lần.

## Todo List

- [x] `RouteBlueprint` hàm thuần + test "1 ENTER rồi 1 EXIT" qua `ZoneEvaluator` (thật ra qua CẢ `LocationFilter` lẫn `ZoneEvaluator`, xem Risk Assessment — `RouteBlueprintTest`, 6 test)
- [x] `SimulatedLocationSource` phát theo `offsetMs`, dấu thời gian nhất quán với nhịp phát
- [x] `StartSimulationUseCase` + tạo zone mẫu khi chưa có zone (+ 2 lớp dự phòng vị trí hiện tại, + `notifyInitialState=false` cho zone mẫu — xem LLM.md §13 Fixed #17)
- [x] Service chuyển nguồn giả ⇄ thật, job con là con cấu trúc
- [x] `isSimulating` hạ ở **cả** nhánh lỗi
- [x] `buildConfigField("boolean", "SIMULATOR_ENABLED", "true")` ở **cả hai** variant; `:ui` **không** bật `buildConfig`
- [x] Rời màn hình giữa chừng, mô phỏng vẫn chạy hết (kiến trúc: job sống trong `LocationTrackingService.scope`, không phải `viewModelScope`)
- [x] `FTD_EVENT simulation_started` / `simulation_finished`
- [x] Đo G4 — **trên `emulator-5554`, KHÔNG phải thiết bị demo thật** (brief phiên này chỉ cấp emulator, xem dev-phase-09-report.md "Sai lệch" #4): ~25.4s (t+7.9s ENTER, t+25.4s EXIT), dưới trần 40s ~15s margin. Cần đo lại trên máy thật ở phase-11.

## Success Criteria

```bash
./gradlew :domain:test --tests '*RouteBlueprintTest*'    # 1 ENTER + 1 EXIT
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -s FTD_EVENT
# Bấm nút, quan sát chuỗi log phải theo đúng thứ tự:
#   simulation_started
#   location_recorded (nhiều lần)
#   zone_event_raised type=ENTER source=FOREGROUND
#   notification_posted type=ENTER
#   zone_event_raised type=EXIT  source=FOREGROUND
#   notification_posted type=EXIT
#   simulation_finished eventsRaised=2
```
- **Gate G4:** từ lúc bấm tới thông báo "Đã rời" ≤ 40 giây, đo bằng đồng hồ bấm tay trên **APK release**
  đã cài ở thiết bị demo.
- Sau khi xong, màn History có một chuyến mới với polyline cắt qua zone, và Timeline có 2 dòng mới.
- **Nút phải CÓ MẶT trên APK release đã cài** — đó là bản đem đi demo. Nếu nút không hiện, kiểm
  `BuildConfig.SIMULATOR_ENABLED` của `:app`, đừng kiểm `:ui` (Key Insight #7).

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| `MIN_DISTANCE_M` loại bớt điểm mô phỏng khiến ENTER hoặc EXIT không bắn | G4 fail, và fail đúng lúc demo | `RouteBlueprintTest` chạy blueprint qua **cả** `LocationFilter` lẫn `ZoneEvaluator`, không chỉ `ZoneEvaluator` |
| Dấu thời gian giả làm `RouteStats` báo số vô lý | Thẻ thống kê nói 0.4 km/h cho một chuyến 30 giây | `recordedAt` cách đều đúng theo `offsetMs` thật (Key Insight #5) |
| Người dùng bấm nút hai lần | Hai luồng cùng bơm vị trí | `isSimulating` là re-entry guard trong reducer, không phải ở UI |
| Mô phỏng chạy khi công tắc theo dõi đang tắt | Không có service để nhận | Use case tự khởi động service cho lượt mô phỏng rồi dừng |
| Zone mẫu tự tạo làm người demo bối rối | "Zone này ở đâu ra?" | Đặt tên rõ "Zone mẫu", và chỉ tạo khi thật sự chưa có zone nào |
| Nhầm simulator là bằng chứng cho G5 | Bỏ sót một gate P0 | Ghi thẳng vào Key Insight #2 và vào checklist phase-11 |
| Ai đó "dọn dẹp" bằng cách gắn lại `SIMULATOR_ENABLED = BuildConfig.DEBUG` | Nút biến mất khỏi bản demo, phát hiện đúng lúc mở màn hình trước khách | Comment ngay tại `buildConfigField` nói rõ vì sao không dùng cờ DEBUG; checklist phase-11 kiểm nút trên APK release |
| `:ui` import nhầm `BuildConfig` của chính nó | Không compile, hoặc tệ hơn: compile nhưng cờ luôn `false` | `:ui` không bật `buildConfig`, nên lớp đó không tồn tại — sai là lỗi biên dịch |

## Security Considerations

- Simulator có mặt ở **cả hai** variant là chủ ý cho bản demo (PRD v1.2 §6). Khi nào dựng bản phát hành
  thật cho người dùng cuối, đổi `buildConfigField` của `release` thành `false` — và **đừng trông vào R8
  xoá mã hộ**: R8 đang tắt (`optimization { enable = false }`, PRD v1.2 §7.2), nên mã sinh vị trí giả
  vẫn nằm trong APK. Cờ là thứ duy nhất chặn nó.
- `simulation_started`/`_finished` chỉ log `durationMs` và `eventsRaised`, không log toạ độ (gate G7).
- Vị trí mô phỏng vẫn được ghi vào cùng bảng với vị trí thật — bản demo chấp nhận; nếu sau này có
  người dùng thật, cần cột đánh dấu nguồn.

## Next Steps

→ [phase-10](phase-10-zone-timeline.md). Chặn: 11 (gate G4 đo ở đây).
