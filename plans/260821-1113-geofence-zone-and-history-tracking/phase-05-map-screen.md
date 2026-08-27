# Phase 05 — Màn Map: vị trí của mình, zone, thành viên (F1 phần 1)

## Context Links

- [`plan.md`](plan.md) · [`phase-04`](phase-04-permissions-and-tracking-service.md)
- [`LLM.md`](../../LLM.md) §3 (`:ui/feature/map`), §5 (giải phẫu feature), §7 (navigation), §13 #1
- [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §2, §3, §4, §8
- PRD §2.2 US-06→US-11 · §5.5 Map Screen Layout · §5.2 màu · §7.1 (<2.5s)
- [`research/researcher-02-maps-compose.md`](research/researcher-02-maps-compose.md) §2, §3.4, §4.1, §5.3, §6, §7.3

## Overview

| | |
|---|---|
| Priority | **P0** |
| Status | completed |
| Effort | 6h |
| Story ánh xạ | **US-06, US-07, US-08, US-09, US-10, US-11** — F1 phần bản đồ |

Thay màn Map tối thiểu của phase-04 bằng màn hình chính thật: `GoogleMap` vẽ vị trí của mình, các
zone dạng `Circle`, marker thành viên giả, nhấn giữ để mở Zone Editor, và bottom navigation đi tới
3 màn còn lại.

## Key Insights

1. **Dùng `Marker` tự vẽ cho vị trí của mình, không dùng `MapProperties.isMyLocationEnabled`**
   (researcher-02 §6.2): app đã có luồng vị trí riêng từ `FusedLocationSource`, bật thêm lớp vị trí
   của Maps SDK là hai nguồn cho một chấm xanh, và lớp đó không theo được bảng màu ở PRD §5.2.
2. **`Circle.radius` tính bằng mét thật, `strokeWidth` tính bằng pixel.** Viền sẽ mảnh dần khi zoom
   ra — chấp nhận, dùng 2–3f (researcher-02 §2.2, §9).
3. **Kéo bản đồ làm `CameraPositionState` đổi liên tục, và nếu `List<Zone>` đi thẳng vào composable
   thì cả cây recompose mỗi khung hình** — đúng màn hình mà việc đó đắt nhất (`LLM.md` §13 #1,
   researcher-02 §5.3). Hai việc rẻ tiền phải làm ngay ở phase này:
   `val onIntent = remember(viewModel) { viewModel::onIntent }` (MVI doc §8) và tách phần vẽ bản đồ
   thành composable riêng chỉ nhận đúng thứ nó cần.
4. **`MapsInitializer.initialize` chạy đồng bộ trên luồng gọi và bill rơi vào khung hình đầu tiên**
   (MVI doc §8). PRD §7.1 đòi < 2.5s tới lúc bản đồ vẽ xong. Khởi động engine trên `Dispatchers.IO`
   sau `startKoin` và compose `GoogleMap` sớm, che bằng lớp phủ mờ thay vì chờ dữ liệu rồi mới dựng.
5. **`onMapLongClick` đã là ngưỡng ≥500ms của hệ thống** — không tự đếm giờ (researcher-02 §4.1, US-10).
6. **Marker thành viên lấy vị trí từ `location_points`, không từ bảng `members`** — quyết định ở
   phase-02, key insight #1. "Thời điểm cập nhật gần nhất" chính là `recordedAt` của điểm đó (US-08).
7. **Bản đồ xám là triệu chứng của cấu hình, không của code** (researcher-02 §7.3): key sai → xám;
   watermark "for development" → chưa bật Maps SDK for Android; "billing not enabled" → chưa bật billing;
   `MapsInitializationException` → SHA-1 không khớp. Kiểm 4 thứ này **trước** khi đọc code.

## Requirements

**Chức năng**
- Marker xanh dương tại vị trí thiết bị; camera tự canh vào đó **lần đầu mở, không animate** (US-06).
- Mỗi zone là một `Circle`: nền màu zone @20% alpha, viền màu zone @100% dày 2dp, tên zone ở tâm (US-07).
- 2–3 marker thành viên màu khác nhau; bấm hiện tên + thời điểm cập nhật gần nhất (US-08).
- Công tắc theo dõi nổi ở góc phải dưới (US-09 — logic đã có từ phase-04).
- Nhấn giữ bản đồ → điều hướng tới `ZoneEditorRoute` mang theo tâm vừa chọn (US-10).
- Bottom navigation: Bản đồ · Zone · Lịch sử · Nhật ký (US-11).

**Phi chức năng**
- Mở app tới lúc bản đồ vẽ xong < 2.5s trên thiết bị tầm trung (PRD §7.1).
- Kéo bản đồ ở 60fps với 10 zone hiển thị.
- Mọi chuỗi ở `strings.xml`; mọi màu/khoảng cách ở `designsystem/theme/` (PRD §7.5, `LLM.md` §12).

## Architecture

```
MapRoute (stateful)
 ├ koinViewModel<MapViewModel>()
 ├ CollectEffects → OpenZoneEditor(lat,lng) / ShowError
 └ MapScreen(state, onIntent = remember(viewModel){viewModel::onIntent})
      ├ FamilyTrackerMap(zones, members, self, onLongClick)   ← composable riêng, tách khỏi chrome
      ├ TrackingToggle(isTracking, onIntent)
      ├ PermissionBanner(state.permissionState)
      └ FamilyTrackerBottomBar(onIntent)

MapViewModel(observeZones, observeMembers, observeSelfLocation, trackingRepository)
```

`MapViewModel` không import Compose, không biết `LatLng` tồn tại — nó nói bằng
`Zone`/`LocationPoint` của `:domain`; chuyển sang `LatLng` xảy ra trong composable.

## Related Code Files

**Tạo**
- `ui/feature/map/MapContract.kt` — viết lại đầy đủ (bản phase-04 chỉ có công tắc)
- `ui/feature/map/component/FamilyTrackerMap.kt`, `ZoneCircles.kt`, `MemberMarkers.kt`, `TrackingToggle.kt`
- `ui/designsystem/component/FamilyTrackerBottomBar.kt`
- `ui/src/test/.../MapViewModelTest.kt`
- `domain/usecase/ObserveMembersWithLastLocationUseCase.kt`

**Sửa**
- `ui/feature/map/MapViewModel.kt`, `MapScreen.kt`
- `ui/navigation/FamilyTrackerNavHost.kt` — nối `MapRoute` ↔ `ZoneEditorRoute(lat,lng)`
- `ui/navigation/Routes.kt` — `ZoneEditorRoute(zoneId, lat, lng)` chép đúng nguyên văn PRD v1.2 §9
- `data/repository/MemberRepositoryImpl.kt` — trả kèm điểm mới nhất
- `app/src/main/res/values/strings.xml`
- `LLM.md` §7 — đối chiếu, chữ ký route đã chốt ở PRD v1.2 §9

## Implementation Steps

1. **Kiểm tra Maps API key trước khi viết dòng code nào:** `./gradlew signingReport`, đối chiếu SHA-1
   với Cloud Console, xác nhận "Maps SDK for Android" đã bật và billing đã bật. Bốn triệu chứng ở
   Key Insight #7 chiếm phần lớn thời gian mất vào việc này.
2. Mở rộng `MapContract`: state gồm `zones`, `members`, `selfPoint`, `isTracking`, `permissionState`,
   `hasCenteredOnce`. Intent gồm `ToggleTracking`, `MapLongPressed(lat,lng)`, `MemberTapped(id)`,
   `OpenZoneList/History/Timeline`. Effect gồm `OpenZoneEditor(lat,lng)`, `Navigate(route)`, `ShowError`.
   **Không giữ đối tượng đã chọn trong state, chỉ giữ id** (MVI doc §2).
3. `MapViewModel.init` chỉ *quan sát*: `observeZones()`, `observeMembersWithLastLocation()`,
   `observeSelfLocation()`, `isTracking()` — mỗi cái một `.onEach { setState { … } }.launchIn(viewModelScope)`.
   Không gọi hành động nào trong `init` (MVI doc §3 luật 3).
4. `FamilyTrackerMap`: `GoogleMap(cameraPositionState, properties, uiSettings, onMapLongClick)`.
   Camera canh lần đầu bằng `cameraPositionState.move(...)` **không animate** (MVI doc §8), gác bằng
   `hasCenteredOnce` để lần cập nhật vị trí thứ hai không kéo camera về giữa lúc người dùng đang kéo.
5. `ZoneCircles`: `Circle(center, radius = zone.radiusMeters.toDouble(), fillColor = color.copy(alpha = .2f),
   strokeColor = color, strokeWidth = 2f)`. Tên zone ở tâm bằng một `Marker` có icon trong suốt +
   `title`, hoặc `MarkerComposable` — chọn cái nào build được, ghi lý do vào KDoc.
6. `MemberMarkers`: 3 màu cố định PRD §5.2, `title = tên`, `snippet = "Cập nhật HH:mm"`.
   **Không animate** marker giữa hai lần cập nhật (researcher-02 §12.3) — animate làm giật camera.
7. `onMapLongClick` → `MapIntent.MapLongPressed(lat, lng)` → Effect `OpenZoneEditor(lat, lng)` →
   `navController.navigate(ZoneEditorRoute(zoneId = null, lat = …, lng = …))`.
8. Bottom bar 4 mục; điều hướng là Effect, **không phải cờ trong state** (`LLM.md` §7).
9. Hiệu năng: `val onIntent = remember(viewModel) { viewModel::onIntent }` trong `MapRoute`;
   khởi động `MapsInitializer.initialize` trên `Dispatchers.IO` sau `startKoin`.
10. Viết `MapViewModelTest`: reducer cho từng Intent, effect cho từng Effect, và một test "500 zone
    trong fake → state chỉ giữ những gì cần" (MVI doc §7, mục Unbounded growth).

## Todo List

- [x] Xác minh API key + SHA-1 + billing trước khi code
- [x] `MapContract` đầy đủ, selection giữ bằng id
- [x] `MapViewModel.init` chỉ quan sát, không hành động
- [x] `FamilyTrackerMap` + camera canh lần đầu không animate
- [x] `ZoneCircles` đúng alpha 20% / viền 2dp / tên ở tâm
- [x] `MemberMarkers` 3 màu + thời điểm cập nhật
- [x] Nhấn giữ → `ZoneEditorRoute` mang tâm
- [x] Bottom bar 4 mục, điều hướng bằng Effect
- [x] `remember(viewModel){::onIntent}` + `MapsInitializer` trên IO
- [x] `MapViewModelTest` reducer + effect + unbounded growth
- [x] Không `.dp`/`.sp` rời rạc trong `feature/map/`

## Success Criteria

```bash
./gradlew :ui:test :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.example.pion.family.tracker.demo/.MainActivity
adb logcat -s FTD_EVENT
```
- Bản đồ hiện đường phố (không xám, không watermark) trong < 2.5s, đo 3 lần bằng đồng hồ bấm tay.
- Zone tạo sẵn bởi `DemoDataSeeder` hiện đúng màu và bán kính; kéo/zoom mượt.
- Nhấn giữ mở Zone Editor với tâm đúng điểm đã nhấn.
- Bấm marker thành viên hiện tên + giờ cập nhật.

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| ~~`ZoneEditorRoute` không chở được toạ độ~~ — **đã giải quyết ở PRD v1.2 §9**: route đã có `lat`/`lng` | — | Chỉ cần khai đúng như PRD §9, không tự nghĩ chữ ký khác |
| Recompose toàn cây khi kéo bản đồ | Giật ở đúng màn hình chính | Tách composable + `remember(::onIntent)`; nếu vẫn giật, dựng `compose-stability.conf` (đang mở ở `LLM.md` §13 #1) |
| `maps-compose 8.4.0` API khác chữ ký ở researcher-02 §2.2 | Không compile | Báo cáo đó tự nhận chưa kiểm chứng chữ ký; sửa theo IDE, ghi chữ ký thật vào phase file này |
| Camera kéo về giữa lúc người dùng đang xem chỗ khác | Khó chịu, trông như lỗi | Cờ `hasCenteredOnce`, chỉ canh camera đúng một lần |
| Marker tên zone che marker vị trí | Rối bản đồ | Đặt `zIndex`/thứ tự khai báo: zone trước, member sau, self cuối |

## Security Considerations

- Không log `LatLng` khi xử lý `onMapLongClick` — đó là toạ độ thật (gate G7).
- API key chỉ tồn tại trong `local.properties`; nếu bản đồ xám ở máy khác, **không** hardcode key
  vào manifest để "thử nhanh" — key bị commit là key phải thu hồi (`LLM.md` §10).

## Next Steps

→ [phase-06](phase-06-zone-list-and-editor.md). Chặn: 06, 07, 08 (dùng chung `FamilyTrackerMap`).
