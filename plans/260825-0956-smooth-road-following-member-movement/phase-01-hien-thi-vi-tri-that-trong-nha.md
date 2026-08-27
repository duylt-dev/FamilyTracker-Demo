# Phase 01 — Hiển thị vị trí thật trong nhà (P0)

## Context Links

- [`plan.md`](plan.md) · [`decisions.md` §C3](decisions.md) (quyết định D4/D6/D7)
- Yêu cầu: [PRD delta](docs/prd-delta-smooth-road-movement.md) D6, US-43, US-44, US-06 (MODIFIED), US-31 (CLARIFIED)
- Nghiệm thu: [QA/UAT](docs/qa-uat-smooth-road-movement.md) **QA-SRM-18→24**, UAT-05, UAT-06
- Research: [researcher-03](research/researcher-03-real-gps-indoor.md) §P0, §B, §C, §E, §G
- Kiến trúc: `LLM.md` §8.1 (zone theo dõi người nhà), §8.3 (`LocationFilter`), §8.4, §12 (nơi đặt file mới)
- MVI: `docs/android-mvi-best-practices.md` §2 (Contract), §3 (ViewModel), §9 (checklist)

## Overview

| | |
|---|---|
| **Ưu tiên** | **P0** — vi phạm yêu cầu đang sống, có mặt trong bản đang chạy |
| **Trạng thái** | completed — xem `reports/dev-phase-01-report.md` |
| **Ước lượng** | 3h |
| **Phụ thuộc** | Không. Ship được một mình, **không chờ** câu hỏi pháp lý của phase 04/05 |

Tách việc **vẽ** vị trí thật ra khỏi việc **ghi** vị trí thật. `MAX_ACCURACY_M` (50m) tiếp tục
quyết định cái gì vào `location_points` (US-31 không đổi một chữ); nó thôi quyết định cái gì hiện
lên bản đồ (US-43). Kèm theo: vòng sai số để app không nói dối về độ tin cậy, và một test kiến
trúc khoá lời hứa "không bao giờ nắn vị trí thật về đường".

## Key Insights

1. **Hai chế độ hỏng, không phải một.** (a) Mở app trong nhà, chưa từng có fix tốt → chưa từng có
   dòng nào trong `location_points` → `MemberLocation.lastLocation == null` → `FamilyTrackerMap`
   bỏ qua nhánh `if (selfPoint != null)`, **không vẽ chấm xanh nào**. (b) Đi bộ vào nhà sau khi đã
   có fix ngoài trời → chấm xanh **đứng im ở toạ độ ngoài trời cuối cùng** mà vẫn trông như đang
   live. (b) tệ hơn (a): nó trình bày dữ liệu cũ như dữ liệu hiện tại.
2. **Luật loại điểm là luật SỐ MỘT.** `LocationFilter.kt:24-26` kiểm `accuracy > MAX_ACCURACY_M`
   **trước** cả luật khoảng cách lẫn luật tốc độ. Điểm bị loại không bao giờ tới
   `trackingRepository.record()` (`LocationPointProcessor.kt:33-38`).
3. **Không đường GPS thật nào chạm `ZoneEvaluator`** (LLM.md §8.1). Vì vậy thay đổi này **không**
   ảnh hưởng ENTER/EXIT, không ảnh hưởng thông báo, không ảnh hưởng tab Nhật ký. Và vì vậy khuyến
   nghị §F của researcher-03 (gating độ chính xác) bị bỏ — xem `decisions.md` D6.
4. **Không đổi lược đồ Room** (PRD delta Y1). Cổng mới là một `StateFlow` trong bộ nhớ, sống theo
   process, không có bảng nào đi kèm.
5. **`LocationPointProcessor` đã nhìn thấy 100% điểm thật.** Đó là chỗ rẻ nhất và đúng nhất để
   phát điểm ra cổng hiển thị — không phải `LocationTrackingService` (không test JVM thuần được),
   không phải `FusedLocationSource` (thì nguồn giả lập của F5 sẽ không nuôi được chấm xanh).

## Requirements

**Chức năng**

- FR-1 Marker vị trí thật hiện ngay từ fix **đầu tiên**, bất kể `accuracyMeters` (US-43, QA-SRM-18).
- FR-2 Marker cập nhật theo **mọi** fix nhận được, kể cả chuỗi fix liên tiếp đều `> 50m` (QA-SRM-19).
- FR-3 Toạ độ vẽ ra **trùng khít** toạ độ nhận được, sai số 0 (US-44, QA-SRM-20).
- FR-4 Chỉ báo trực quan độ chính xác thấp: vòng tròn bán kính = `accuracyMeters`, hiện khi
  `accuracyMeters > MAX_ACCURACY_M`. **Không** dialog, **không** toast, **không** chữ "lỗi" (QA-SRM-23).
- FR-5 `location_points` và polyline tab Lịch sử **không đổi hành vi** (US-31, QA-SRM-24).

**Phi chức năng**

- NFR-1 Không đổi lược đồ Room, không tăng version DB.
- NFR-2 `MviViewModel` không import Compose/Android; cổng mới là `Flow` từ `:domain/repository/`.
- NFR-3 Không thêm hằng số nào vào `TrackingConstants` (§13 Open #7 đang lệch, đừng làm lệch thêm).

## Architecture

```
FusedLocationSource.stream()  /  SimulatedLocationSource.stream()
              │
              ▼
     LocationPointProcessor.process(point)
              ├──────────────► LiveSelfLocation.publish(point)      ← MỚI, :data/location/
              │                        │  MutableStateFlow<LocationPoint?>
              │                        ▼
              │                TrackingRepositoryImpl.observeLiveSelfLocation()
              │                        │  (cổng ở :domain/repository/TrackingRepository)
              │                        ▼
              │                MapViewModel  ──► MapState.liveSelfLocation
              │                                        │
              │                                        ▼
              │                       MapState.selfLocation  (ưu tiên live, rơi về Room)
              │                                        ▼
              │                       FamilyTrackerMap ─► SelfAccuracyCircle + chấm xanh
              │
              └── chỉ khi FilterResult.Accept ──► trackingRepository.record(point)   [KHÔNG ĐỔI]
                                                          ▼
                                                  location_points → tab Lịch sử [KHÔNG ĐỔI]
```

**Vì sao `LiveSelfLocation` là một lớp riêng chứ không phải một field trong `TrackingRepositoryImpl`:**
`LocationPointProcessor` và `TrackingRepositoryImpl` là hai `single` Koin khác nhau; nếu processor
phải gọi ngược vào repository để "phát", cổng `TrackingRepository` sẽ mọc thêm một method ghi chỉ
tồn tại cho một người gọi. Một holder 15 dòng, `single` Koin, tiêm vào cả hai — bên ghi và bên đọc
tách bạch, và `LocationPointProcessorTest` kiểm được nó bằng JUnit thuần.

**`MapState.selfLocation` sau thay đổi** (vẫn là `val` tính toán, MVI doc §2 "Derive, don't duplicate"):
ưu tiên `liveSelfLocation`; rơi về điểm từ Room khi cổng live chưa phát (app vừa mở, chưa bật theo
dõi) — nếu không, chấm xanh sẽ **biến mất** mỗi lần khởi động lại app cho tới fix đầu tiên, đúng
loại hồi quy mà phase này sinh ra để sửa.

## Related Code Files

**Tạo**

| Đường dẫn | Việc |
|---|---|
| `data/src/main/java/com/example/pion/family/tracker/demo/data/location/LiveSelfLocation.kt` | Holder `MutableStateFlow<LocationPoint?>` + `publish()` + `observe()` |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/SelfAccuracyCircle.kt` | `internal @GoogleMapComposable` — `Circle` bán kính `accuracyMeters` |
| `data/src/test/java/com/example/pion/family/tracker/demo/data/location/RealGpsNoSnapArchitectureTest.kt` | QA-SRM-21 — quét mã nguồn đường GPS thật, cấm mọi tham chiếu tới kiểu tuyến đường |

**Sửa**

| Đường dẫn | Việc |
|---|---|
| `domain/src/main/kotlin/.../domain/repository/TrackingRepository.kt` | `+ fun observeLiveSelfLocation(): Flow<LocationPoint?>` + KDoc nói rõ nó **không** qua `LocationFilter` |
| `domain/src/main/kotlin/.../domain/tracking/LocationFilter.kt` | **Chỉ KDoc.** Ghi rằng luật `ACCURACY` chi phối việc GHI, không chi phối việc VẼ, và trỏ sang `observeLiveSelfLocation()` |
| `data/src/main/java/.../data/location/LocationPointProcessor.kt` | `+ liveSelfLocation` ở constructor; `publish(point)` **trước** khi lọc, ở mọi nhánh |
| `data/src/main/java/.../data/repository/TrackingRepositoryImpl.kt` | `+ liveSelfLocation` ở constructor; implement `observeLiveSelfLocation()` |
| `data/src/main/java/.../data/di/DataModule.kt` | `singleOf(::LiveSelfLocation)`; cập nhật hai `single` đang dựng thủ công |
| `ui/src/main/java/.../ui/feature/map/MapContract.kt` | `+ val liveSelfLocation: LocationPoint? = null`; `selfLocation` ưu tiên live; `initialCameraTarget` dùng cùng nguồn |
| `ui/src/main/java/.../ui/feature/map/MapViewModel.kt` | `trackingRepository.observeLiveSelfLocation().collectSafely { … }` |
| `ui/src/main/java/.../ui/feature/map/component/FamilyTrackerMap.kt` | Vẽ `SelfAccuracyCircle` **dưới** chấm xanh |
| `data/src/test/java/.../data/location/LocationPointProcessorTest.kt` | + 2 ca: `accuracy = 80f` vẫn publish; điểm bị Reject **không** vào repository |
| `ui/src/test/java/.../ui/feature/map/MapViewModelTest.kt` | + ca: cổng live phát → `state.selfLocation` đổi; Room rỗng vẫn có `selfLocation` |
| `app/src/test/java/.../KoinModulesTest.kt` | Binding mới phải resolve |
| `LLM.md` | §3 (2 file mới), §8.3 (luật ghi vs luật vẽ), §13 Fixed (dòng mới cho D6) |
| `docs/prd-delta-smooth-road-movement.md` | Đánh dấu D6/US-43/US-44 đã có implementation |

**Xoá:** không.

## Implementation Steps

1. **Viết test đỏ trước** (researcher-03 Q4 chốt: trước). Trong `LocationPointProcessorTest`, thêm
   `an indoor fix with accuracy 80m is published for display even though it is not recorded` —
   phải đỏ vì `LiveSelfLocation` chưa tồn tại.
2. Tạo `LiveSelfLocation`: `private val _point = MutableStateFlow<LocationPoint?>(null)`,
   `fun publish(point: LocationPoint) { _point.value = point }`,
   `fun observe(): StateFlow<LocationPoint?> = _point`. Không coroutine, không Android import.
3. `LocationPointProcessor.process`: gọi `liveSelfLocation.publish(point)` ở **dòng đầu**, trước
   `LocationFilter.accept`. **Không** đụng `lastKeptPoint` — luật §8.3 giữ nguyên tuyệt đối.
4. Thêm `observeLiveSelfLocation()` vào `TrackingRepository` + impl. Wiring Koin.
5. `MapContract`: thêm field, đổi `selfLocation` thành
   `liveSelfLocation?.let { p -> memberLocations.firstOrNull { it.member.isSelf }?.copy(lastLocation = p) } ?: memberLocations.firstOrNull { it.member.isSelf }`
   — giữ nguyên kiểu `MemberLocation?` để `FamilyTrackerMap` không đổi chữ ký.
6. `MapViewModel`: thêm một `collectSafely` thứ tư. **Không** `combine` với 3 cái đang có (phase-05
   Implementation Step 3, nguồn độc lập thì collect độc lập).
7. `SelfAccuracyCircle`: `Circle(center, radius = accuracyMeters.toDouble(), fillColor = alpha 0.10,
   strokeColor = alpha 0.30, strokeWidth = 1f, zIndex = 0f)`. Chỉ compose khi
   `accuracyMeters > TrackingConstants.MAX_ACCURACY_M`. Màu lấy từ `designsystem/theme/Color.kt`,
   **không** literal trong composable (§12).
8. `FamilyTrackerMap`: gọi `SelfAccuracyCircle` ngay trước `MarkerComposable` của self (thứ tự vẽ
   quyết định thứ tự chồng lớp).
9. `RealGpsNoSnapArchitectureTest`: đọc `File("src/main/java/.../data/location/")` +
   `FusedLocationSource.kt` + `LocationPointProcessor.kt` + `LocationTrackingService.kt`
   (Gradle chạy test với working dir = thư mục module), assert không file nào chứa
   `RoutingProvider`, `Directions`, `PolylineFollower`, `PolylineDecoder`, `snapTo`, `mapMatch`.
   KDoc nói rõ: test này tồn tại để **lần sau** ai đó thêm snap sẽ thấy đỏ, không phải để mô tả
   hiện trạng.
10. Chạy `./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache`.
11. Chạy thật trên `emulator-5554`: `adb emu geo fix` một toạ độ, kiểm chấm xanh + `adb logcat -s FTD_EVENT`
    thấy `location_dropped reason=ACCURACY` mà marker **vẫn** đổi chỗ.
12. Cập nhật `LLM.md` §3/§8.3/§13 **trong cùng commit**.

## Todo List

- [x] Test đỏ trong `LocationPointProcessorTest` (accuracy 80m → publish, không record)
- [x] `LiveSelfLocation.kt` (`:data/location/`)
- [x] `LocationPointProcessor` publish trước khi lọc, `lastKeptPoint` không đổi
- [x] `TrackingRepository.observeLiveSelfLocation()` + impl + Koin
- [x] `MapState.liveSelfLocation` + `selfLocation` ưu tiên live + `initialCameraTarget`
- [x] `MapViewModel` collect thứ tư qua `collectSafely`
- [x] `SelfAccuracyCircle.kt` + màu ở `Color.kt` + gọi từ `FamilyTrackerMap`
- [x] `RealGpsNoSnapArchitectureTest` (QA-SRM-21)
- [x] `MapViewModelTest` + `KoinModulesTest`
- [x] Chạy thật trên emulator: fix `accuracy > 50` → marker đổi chỗ, Lịch sử không nhận điểm
- [x] `LLM.md` §3/§8.3/§13 cùng commit
- [x] Đánh dấu D6/US-43/US-44 trong PRD delta

## Success Criteria

| # | Điều kiện | Cách kiểm | QA |
|---|---|---|---|
| S1 | Phát 5 fix liên tiếp `accuracy = 80f`, cách nhau 30m → marker đổi chỗ **cả 5 lần** | JUnit + chạy thật | QA-SRM-18 |
| S2 | Toạ độ marker == toạ độ đã phát, delta **chính xác 0.0** | `MapViewModelTest` assert `assertEquals(lat, state.selfLocation!!.lastLocation!!.latitude, 0.0)` | QA-SRM-20 |
| S3 | Cùng bộ dữ liệu đó → `location_points` **không** nhận dòng nào | Fake `TrackingRepository` đếm `record()` = 0 | QA-SRM-24 |
| S4 | Mở app lần đầu trong nhà (Room rỗng, fix đầu 90m) → chấm xanh **được vẽ** | `MapViewModelTest` | QA-SRM-18 |
| S5 | Vòng sai số hiện khi `> 50m`, **không** hiện khi `≤ 50m` | Chạy thật + ảnh chụp màn hình | QA-SRM-23 |
| S6 | `RealGpsNoSnapArchitectureTest` xanh; đổi tên nó thành đỏ bằng cách chèn `// PolylineFollower` vào `FusedLocationSource.kt` rồi khôi phục | Mutation thật, ghi vào dev report | QA-SRM-21 |
| S7 | `LocationFilterTest` + `LocationPointProcessorTest` cũ **vẫn xanh, không sửa assertion nào** | `./gradlew :domain:test :data:test` | US-31 |

## Risk Assessment

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| `selfLocation` ưu tiên live làm camera lần đầu canh vào điểm sai số 200m | Thấp | `hasCenteredOnce` chỉ canh một lần; canh vào điểm sai số lớn vẫn đúng hơn là không canh |
| Ai đó tưởng "publish trước khi lọc" là chỗ để bỏ qua bộ lọc luôn | **Trung bình** | KDoc trên `process()` ghi thẳng: đây là hai đường có mục đích khác nhau, `record()` **phải** ở sau `Accept`; `S3` khoá bằng test |
| `MutableStateFlow` giữ điểm cuối vô hạn sau khi tắt theo dõi ⇒ marker "sống" khi service đã chết | Trung bình | **Chấp nhận có chủ ý** — D7: ẩn marker theo độ cũ chính là hành vi X2 cấm. Ghi vào `LLM.md` §13 Open như một giới hạn đã biết |
| Vòng sai số bán kính 200m che mất zone nhỏ | Thấp | `alpha 0.10` + `strokeWidth 1f`; `zIndex = 0f` nên nằm dưới mọi thứ |

## Security Considerations

- Không log `lat`/`lng` ở bất kỳ nhánh mới nào (PRD §7.3, gate G7). `LiveSelfLocation` **không**
  log gì cả — nó đứng ngay trên đường của dữ liệu vị trí thô.
- Cổng mới **chỉ đọc** đối với `:ui`; không có method nào cho phép `:ui` ghi vị trí.
- Không quyền mới, không thay đổi manifest.
- Dữ liệu sống trong process, không xuống đĩa ⇒ không mở rộng bề mặt dữ liệu tĩnh của app.

## Next Steps

- Phase 02 dùng lại **đúng** ranh giới này: bám đường chỉ tồn tại phía thành viên mô phỏng, và
  `RealGpsNoSnapArchitectureTest` là thứ giữ ranh giới đó khi phase 04 thêm `MemberRouteSource`.
- Không phase nào sau đây được sửa `LocationFilter` — nếu phải sửa, đọc lại `decisions.md` §C3 trước.
