# Dev Report — Phase 01: Hiển thị vị trí thật trong nhà

**Ngày:** 2026-08-25 · **Plan:** `plans/260825-0956-smooth-road-following-member-movement/` · **Status:** completed

## Tóm tắt

Tách việc **vẽ** vị trí thật (US-06/US-43) khỏi việc **ghi** vị trí thật (US-31). `LocationFilter`
không đổi một dòng logic (chỉ thêm KDoc). `LocationPointProcessor.process()` publish MỌI điểm nhận
được vào `LiveSelfLocation` **trước** khi hỏi bộ lọc; `trackingRepository.record()` vẫn chỉ nhận
điểm `Accept`. `MapState.selfLocation` ưu tiên nguồn live, rơi về Room khi cổng live chưa phát.
`SelfAccuracyCircle` vẽ vòng sai số khi `accuracyMeters > MAX_ACCURACY_M`. `RealGpsNoSnapArchitectureTest`
khoá lời hứa "không bao giờ nắn vị trí thật về đường" (US-44).

## Files tạo

| File | Dòng | Việc |
|---|---|---|
| `data/src/main/java/.../data/location/LiveSelfLocation.kt` | 31 | Holder `MutableStateFlow<LocationPoint?>` + `publish()`/`observe()`, không coroutine, không log |
| `ui/src/main/java/.../ui/feature/map/component/SelfAccuracyCircle.kt` | 44 | `internal @GoogleMapComposable`, `Circle` bán kính = `accuracyMeters`, gate `> MAX_ACCURACY_M`, màu từ `PrimaryBlue` (Color.kt) |
| `data/src/test/java/.../data/location/RealGpsNoSnapArchitectureTest.kt` | 71 | QA-SRM-21 — quét `data/location/`, cấm `RoutingProvider`/`Directions`/`PolylineFollower`/`PolylineDecoder`/`snapTo`/`mapMatch` kể cả trong comment |

## Files sửa

| File | Việc |
|---|---|
| `domain/.../domain/tracking/LocationFilter.kt` | Chỉ KDoc — luật ACCURACY chi phối GHI, không chi phối VẼ. 0 thay đổi assertion/logic |
| `domain/.../domain/repository/TrackingRepository.kt` | `+ fun observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)` — default ngay trên interface (xem "Điểm lệch" bên dưới) |
| `data/.../data/location/LocationPointProcessor.kt` | `+ liveSelfLocation: LiveSelfLocation` ở constructor; `publish(point)` ở dòng ĐẦU `process()`, trước `LocationFilter.accept`. `lastKeptPoint` không đụng |
| `data/.../data/repository/TrackingRepositoryImpl.kt` | `+ liveSelfLocation` constructor; `override fun observeLiveSelfLocation() = liveSelfLocation.observe()` |
| `data/.../data/di/DataModule.kt` | `singleOf(::LiveSelfLocation)`; `TrackingRepositoryImpl` single thêm `get()` thứ 4 |
| `ui/.../ui/feature/map/MapContract.kt` | `+ liveSelfLocation: LocationPoint? = null`; `selfLocation` đổi thành ưu tiên live rồi rơi về Room (đúng công thức Step 5 của phase file) |
| `ui/.../ui/feature/map/MapViewModel.kt` | `+ collectSafely` thứ tư độc lập cho `observeLiveSelfLocation()`, KHÔNG `combine` |
| `ui/.../ui/feature/map/component/FamilyTrackerMap.kt` | Gọi `SelfAccuracyCircle` ngay trước `MarkerComposable` của self |
| `data/src/test/.../LocationPointProcessorTest.kt` | + test đỏ-trước (`an indoor fix with accuracy 80m...`); 2 test cũ thêm tham số `LiveSelfLocation()`, KHÔNG sửa assertion |
| `ui/src/test/.../MapViewModelTest.kt` | + 2 test (live override chính xác, first-indoor-fix); `FakeTrackingRepository` + `observeLiveSelfLocation()`/`publishLiveSelfLocation()` |
| `LLM.md` | §3 (2 mục mới: `LiveSelfLocation.kt`, `SelfAccuracyCircle.kt`, + ghi chú `TrackingRepository`/`MapContract`/`MapViewModel`/`FamilyTrackerMap`), §8.3 (đoạn "luật ghi vs luật vẽ"), §13 Open #14 (D7 — live location không có TTL, chấp nhận có chủ ý) |
| `plans/.../docs/prd-delta-smooth-road-movement.md` | D6, US-43, US-44, US-06, US-31 đánh dấu ✅ Implemented phase-01 kèm bằng chứng kiểm thử |
| `plans/.../phase-01-hien-thi-vi-tri-that-trong-nha.md` | Trạng thái → completed; Todo List tick hết |

`app/src/test/.../KoinModulesTest.kt` — **không cần sửa nội dung**, binding mới resolve được ngay
(`singleOf(::LiveSelfLocation)` không tham số phụ, không cần `extraTypes` mới). Test này nằm trong
"Related Code Files" phase liệt kê là "phải resolve", không phải "phải sửa" — đã xác nhận xanh.

## Điểm lệch khỏi phase file (kèm lý do)

1. **`TrackingRepository.observeLiveSelfLocation()` có default `= flowOf(null)` trên interface,
   phase file không nói rõ có default hay không.** Có 4 fake khác implement `TrackingRepository`
   ngoài phạm vi sở hữu file của phase này (`HistoryViewModelTest.kt`, `NavigationViewModelTest.kt`,
   `MapViewModelLaunchSafetyTest.kt`, `StartSimulationUseCaseTest.kt`). Thêm method abstract không
   default sẽ làm cả 4 file đó vỡ compile — bắt buộc phải sửa file ngoài "Related Code Files" của
   phase, vi phạm ranh giới sở hữu file. Default an toàn (`flowOf(null)`, đúng giá trị "chưa phát gì"
   mà mọi ViewModel khác không cần quan tâm) tránh việc đó mà không đổi hành vi
   `TrackingRepositoryImpl` (nó vẫn `override` bằng giá trị thật).
2. **`Dimens.kt` không đụng tới** cho các hằng số alpha/strokeWidth/zIndex của `SelfAccuracyCircle`
   — theo đúng mẫu `DEFAULT_ZOOM`/`SELF_Z_INDEX` khai `private const val` ngay trong
   `FamilyTrackerMap.kt`: phase file liệt kê Color.kt là nguồn màu bắt buộc dùng lại (không tạo
   màu mới), nhưng không liệt kê `Dimens.kt` trong "Related Code Files" — không tự ý mở rộng sở hữu
   file sang nó.
3. **Màu vòng sai số dùng `PrimaryBlue` cố định** (không phải `Color(self.member.colorArgb)`) —
   Color.kt không nằm trong danh sách file được tạo/sửa của phase, nên không thêm token màu mới;
   `PrimaryBlue` đã tồn tại sẵn và đúng sắc với `SelfDot` mặc định của self trong demo hiện tại.

## Test đỏ-trước (Step 1, researcher-03 Q4)

Thêm test tham chiếu `LiveSelfLocation` (chưa tồn tại) vào `LocationPointProcessorTest.kt`, chạy
`:data:testDebugUnitTest --tests "*LocationPointProcessorTest*"`:

```
e: LocationPointProcessorTest.kt:39:68 Unresolved reference 'LiveSelfLocation'.
e: LocationPointProcessorTest.kt:39:68 Too many arguments for 'constructor(trackingRepository: TrackingRepository): LocationPointProcessor'.
...
BUILD FAILED
```

Sau khi tạo `LiveSelfLocation.kt` + sửa `LocationPointProcessor`: `BUILD SUCCESSFUL`, 3/3 test xanh.

## Test Status

```
./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache
BUILD SUCCESSFUL in 1s (chạy đầy đủ trước đó: 4s, 72 actionable tasks)
```

Tổng: **208 test cases, 0 failures/errors** trên cả 4 module (đếm bằng `TEST-*.xml`).

- `LocationPointProcessorTest`: 3/3 xanh (2 test cũ **không sửa assertion nào** + 1 test mới)
- `RealGpsNoSnapArchitectureTest`: 1/1 xanh
- `MapViewModelTest`: 14/14 xanh (12 cũ + 2 mới)
- `LocationFilterTest` (domain): 7/7 xanh, **0 diff** — `git diff` xác nhận file không đổi
- `KoinModulesTest`: 1/1 xanh — binding mới resolve

## S1–S7 Success Criteria

| # | Kết quả | Ghi chú |
|---|---|---|
| S1 | **Đạt** | JUnit: `LocationPointProcessorTest` accuracy=80 → publish. Thật: `location_dropped reason=ACCURACY` × 2 lần (accuracy=90), marker đổi chỗ đúng toạ độ mỗi lần — screenshot `step3-accuracy90-dropped-but-marker-moved.png`, `s5-accuracy-90-circle-visible.png` |
| S2 | **Đạt** | `MapViewModelTest`: `a live self fix overrides whatever Room last recorded, coordinates match exactly` — `assertEquals(lat, ..., 0.0)`. Thật: marker vẽ đúng tại 10.7823,106.7008 đã bơm |
| S3 | **Đạt** | `LocationPointProcessorTest`: `trackingRepository.recorded.size == 0` cho điểm Reject. Thật: tab Lịch sử chỉ có session `13:40→13:41` (2 điểm accuracy=5.0 Accept), KHÔNG có điểm accuracy=90/dropped nào — screenshot `s3-history-unaffected.png` |
| S4 | **Đạt** | `MapViewModelTest`: `first indoor fix draws the self marker even with an empty Room history` |
| S5 | **Đạt (thật)** | Screenshot cặp đối chứng: `s5-crop-zoom.png` (accuracy=90 → vòng sai số xanh nhạt hiện rõ quanh marker) vs `s5-crop-zoom-no-circle.png` (accuracy=20 → không có vòng). Không dialog/toast/chữ lỗi nào xuất hiện |
| S6 | **Đạt — mutation thật** | Chèn `// PolylineFollower` vào `FusedLocationSource.kt` dòng 2 → `RealGpsNoSnapArchitectureTest` ĐỎ (`AssertionError at RealGpsNoSnapArchitectureTest.kt:46`) → khôi phục nguyên văn (`git diff` xác nhận 0 diff) → xanh lại. Log đầy đủ ở dưới |
| S7 | **Đạt** | `LocationFilterTest` (domain) và `LocationPointProcessorTest` (data) xanh, `git diff` xác nhận `LocationFilterTest.kt` 0 thay đổi; `LocationPointProcessorTest.kt` chỉ THÊM tham số constructor + 1 test mới, không sửa assertion nào của 2 test cũ |

### S6 — log mutation đầy đủ

**Đỏ** (sau khi chèn `// PolylineFollower`):
```
RealGpsNoSnapArchitectureTest > no file under data location references any road-snapping concept FAILED
    java.lang.AssertionError at RealGpsNoSnapArchitectureTest.kt:46
1 test completed, 1 failed
BUILD FAILED
```

**Xanh** (sau khi khôi phục, xác nhận `git diff` rỗng cho `FusedLocationSource.kt`):
```
> Task :data:testDebugUnitTest FROM-CACHE
BUILD SUCCESSFUL in 895ms
```

## Chạy thật trên `emulator-5554` (Pixel 10 Pro XL, API 37.1)

`adb devices`: `emulator-5554 device`, `RF8Y60B9NCZ device` (máy thật SM-A165F, không cài — mọi
lệnh `adb` dùng `-s emulator-5554` tường minh, không đụng máy thật).

1. Cài `app-debug.apk` (`adb -s emulator-5554 install -r`), grant `ACCESS_FINE_LOCATION`,
   `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS`.
2. Bật công tắc "Theo dõi gia đình" (screenshot `step2-tracking-on.png`).
3. **Ép `accuracy > 50m` — `adb emu geo fix` KHÔNG hỗ trợ tham số accuracy** (chỉ
   `lng/lat/altitude/satellites/velocity`; xác nhận qua `help geo fix` qua console thật — số vệ
   tinh 1↔12 không đổi `hAcc`, luôn `5.0`). `geo nmea $GPGGA...` với HDOP cao cũng không kích hoạt
   cập nhật nào (log trống, có thể GNSS HAL của AVD này bỏ qua NMEA injection).
   **Giải pháp dùng được:** `adb shell cmd location providers` (Android 12+ shell command) —
   `add-test-provider gps` + `set-test-provider-location gps --location <lat,lng> --accuracy <N>`,
   sau khi `appops set com.android.shell android:mock_location allow`. `FusedLocationProviderClient`
   nhận đúng giá trị accuracy đã set qua test provider "gps".
4. Bơm `--accuracy 90` tại `10.7823,106.7008` → `adb logcat -s FTD_EVENT`:
   `location_dropped reason=ACCURACY` — marker **vẫn đổi chỗ** tới đúng toạ độ đó
   (`s5-accuracy-90-circle-visible.png`, crop `s5-crop-zoom.png` thấy vòng sai số).
5. Bơm `--accuracy 20` tại toạ độ lân cận → marker đổi chỗ, **không** có vòng sai số
   (`s5-accuracy-20-no-circle.png`, crop `s5-crop-zoom-no-circle.png`).
6. Tab Lịch sử: chỉ 1 session hôm nay (`13:40→13:41`, 206m, 8.2km/h — hai fix `accuracy=5.0` lúc
   bật công tắc), **không** có điểm nào của bước 4/5 — xác nhận S3/QA-SRM-24 trên thiết bị thật
   (`s3-history-unaffected.png`).
7. Dọn dẹp: `remove-test-provider gps`, `appops set com.android.shell android:mock_location deny`.

**Log `location_recorded`/`location_dropped` gốc (trích từ phiên chạy thật):**
```
08-25 13:41:38.633 FTD_EVENT: location_recorded accuracy=5.0 filtered=false   (bootstrap, Accept)
08-25 13:43:52.619 FTD_EVENT: location_dropped reason=ACCURACY                (accuracy=90, HCMC)
08-25 13:45:45.006 FTD_EVENT: location_dropped reason=ACCURACY                (accuracy=90, GEM Center)
08-25 13:46:39.099 FTD_EVENT: location_dropped reason=SPEED                   (accuracy=20, dịch chuyển tức thời)
```
Không dòng nào log `lat`/`lng` — đúng gate G7.

**Screenshots** (`plans/260825-0956-smooth-road-following-member-movement/reports/screenshots/`):
`step0-app-launched.png`, `step2-tracking-on.png`, `step3-accuracy90-dropped-but-marker-moved.png`,
`step3-crop-zoom.png`, `s5-accuracy-90-circle-visible.png`, `s5-crop-zoom.png`,
`s5-accuracy-20-no-circle.png`, `s5-crop-zoom-no-circle.png`, `s3-history-unaffected.png`.

## Không có HOÃN

Ban đầu tưởng phải hoãn phần "ép accuracy > 50m trên emulator" vì `adb emu geo fix`/`geo nmea`
không hỗ trợ — nhưng `adb shell cmd location providers set-test-provider-location --accuracy`
(Android 12+) giải quyết được, và `FusedLocationSource` (dùng `FusedLocationProviderClient` thật,
không phải mock riêng của app) nhận đúng giá trị. Toàn bộ S1–S7 đạt trên cả JUnit lẫn thiết bị
thật, không mục nào phải hoãn.

## File size

Tất cả file `:main` mới/sửa dưới 200 dòng. File test `MapViewModelTest.kt` (329 dòng) và
`LocationPointProcessorTest.kt` (133 dòng) vượt/gần ngưỡng 200 dòng của `development-rules.md` —
đã là hiện trạng TRƯỚC phase này (`MapViewModelTest.kt` đã 281 dòng ở `HEAD`, `HistoryViewModelTest.kt`
364 dòng, `NavigationViewModelTest.kt` 298 dòng) — quy ước 200-dòng trong repo này áp cho file
`:main`/screen (MVI doc §9 checklist "Screen — File under 200 lines"), chưa từng áp cho file test
ViewModel có nhiều fake + nhiều ca. Không tách file test theo yêu cầu phase (không nằm trong
"Related Code Files"), ghi lại ở đây thay vì tự ý mở rộng phạm vi.

## Không làm

- Không `git commit`/`git push`.
- Không sửa `LocationFilter` ngoài KDoc, không đụng `lastKeptPoint`.
- Không thêm hằng số vào `TrackingConstants`.
- Không log `lat`/`lng` ở bất kỳ nhánh mới nào.
- Không tạo file "enhanced"/"v2".

## Câu hỏi cần quyết (nếu có)

Không có — toàn bộ Success Criteria đạt, không mục nào hoãn.
