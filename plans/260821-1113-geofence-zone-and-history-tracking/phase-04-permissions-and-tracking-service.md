# Phase 04 — Luồng quyền 3 bước, nav shell, foreground service, LocationSource

## Context Links

- [`plan.md`](plan.md) · [`phase-03`](phase-03-domain-tracking-algorithms.md)
- [`LLM.md`](../../LLM.md) §7 (navigation), §8.4 (LocationSource), §8.5 (vòng đời service), §10 (quyền và manifest)
- [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §4 "Platform state lives in the composable"
- PRD §2.1 US-01→US-05 · US-09 · §4.3 Flow 1 · §7.3 · §7.4
- [`research/researcher-01-geofencing-and-background-location.md`](research/researcher-01-geofencing-and-background-location.md) §2, §3

## Overview

| | |
|---|---|
| Priority | **P0** |
| Status | completed |
| Effort | 8h |
| Story ánh xạ | **US-01, US-02, US-03, US-04, US-05** (onboarding) · **US-09** (công tắc theo dõi) |

Từ đây app bắt đầu chạm phần cứng: xin quyền đúng thứ tự, chạy foreground service, và ghi điểm
thật vào Room qua đúng bộ lọc của phase-03. Màn Map ở phase này chỉ là chỗ giữ chỗ — nó được viết
đầy đủ ở phase-05.

## Key Insights

1. **Gộp `ACCESS_FINE_LOCATION` và `ACCESS_BACKGROUND_LOCATION` vào một lần `requestPermissions`
   khiến hệ thống từ chối im lặng phần background** — không lỗi, không dialog, và geofence sẽ không
   bao giờ bắn khi app đóng. Triệu chứng giống hệt "code sai" (`LLM.md` §10, researcher-01 §2.2).
   Ba bước, ba màn hình trạng thái riêng.
2. **Từ Android 11 (API 30+) không còn dialog cho background location.** Phải giải thích rồi mở
   thẳng `ACTION_APPLICATION_DETAILS_SETTINGS`, và **kiểm tra lại quyền khi quay lại app**
   (`ON_RESUME`), vì không có callback nào báo người dùng đã chọn gì (US-03).
3. **"Chỉ lần này" (`Allow only this time`) bị thu hồi khi app đóng** (researcher-01 §2.3). US-02
   chấp nhận nó cho luồng onboarding, nhưng nó **không đủ** cho geofence nền — phải phản ánh trong
   banner trạng thái ở màn Map, nếu không người demo sẽ tưởng app hỏng.
4. **API 34+ thiếu `foregroundServiceType` KHÔNG phải lỗi biên dịch.** researcher-01 §3.1 ghi "không
   compile" là sai; bảng §3.3 của chính báo cáo đó mới đúng: `startForeground()` ném
   `MissingForegroundServiceTypeException` **lúc chạy** — build vẫn xanh, app chỉ chết đúng lúc bật
   công tắc theo dõi trước mặt khách.
5. **Giới hạn 6 giờ/24 giờ của Android 15 chỉ áp cho `dataSync` và `mediaProcessing`, không áp cho
   `location`** (researcher-01 §3.4). Không cần cơ chế restart định kỳ.
6. **`startForeground()` phải gọi ngay trong `onStartCommand`**, nếu không hệ thống kill service sau
   vài giây mà không báo gì (researcher-01 §7.6).
7. **Quyền là việc của composable, không của ViewModel** (MVI doc §4): `rememberLauncherForActivityResult`
   sống trong `LocationPermissionFlow`, kết quả được báo lên ViewModel bằng **Intent**, không bằng
   method public.
8. **Vòng lặp kiểm chứng từ đây trở đi là emulator ảnh Google APIs** (phase-11 tầng b). Extended
   Controls > Location nạp được GPX/KML và phát lại theo tốc độ chọn được, đủ cho toàn bộ luồng
   quyền, foreground service và geofence. Máy thật chỉ cần cho **G5** — và emulator **không** chứng
   minh được gì về G5, vì đó đúng là chỗ nó mô phỏng lỏng lẻo nhất (Doze khác, không có OEM tự kill).
9. **`SimulatedLocationSource` được khai báo ngay ở phase này** (Koin qualifier `named("simulated")`)
   dù thân nó rỗng tới phase-09 — để cửa vào hệ thống chỉ có đúng một cái ngay từ đầu (`LLM.md` §8.4).
10. **`LocationFilter.accept(point, lastKept)` là hàm thuần — nó không nhớ gì giữa hai lần gọi.**
    `LocationTrackingService` PHẢI tự giữ biến `lastKeptPoint` và chỉ cập nhật nó khi `accept()` trả
    về `Accept`, KHÔNG phải mỗi khi nhận được điểm mới từ `LocationSource.stream()` (`LLM.md` §8.3,
    phase-03 "bẫy" trong KDoc `LocationFilter.kt:18-20`). Truyền nhầm "điểm vừa nhận" thay vì "điểm
    vừa giữ" không ném lỗi và không crash — hậu quả là người đi bộ chậm bị `Reject(DISTANCE)` mãi
    mãi vì luôn so với điểm ngay trước chứ chưa từng nhích đủ xa so với điểm thật sự được giữ, và
    triệu chứng chỉ lộ ra ở phase-08 dưới dạng "lịch sử trống khi đi bộ" — lúc đó không ai còn nhớ
    để truy ngược về service. **Tự kiểm:** viết ít nhất một test cho `LocationTrackingService` (hoặc
    lớp giữ state tương đương) mô phỏng 3+ điểm cách nhau <10m so với điểm NGAY TRƯỚC nhưng ≥10m so
    với điểm ĐƯỢC GIỮ gần nhất (giống input trong `LocationFilterTest`), xác nhận các điểm sau vẫn
    được ghi — nếu test đó không tồn tại, `lastKeptPoint` gần như chắc chắn đang được thread sai.

## Requirements

**Chức năng**
- Manifest: 6 quyền ở `LLM.md` §10 + `RECEIVE_BOOT_COMPLETED` (dùng ở phase-07) + khai báo service
  `foregroundServiceType="location"`, `exported="false"`.
- `PermissionOnboardingRoute` là start destination khi thiếu quyền; `MapRoute` khi đủ (US-05).
- Ba bước theo đúng thứ tự PRD §4.3 Flow 1: POST_NOTIFICATIONS → FINE_LOCATION → BACKGROUND.
- Từ chối vị trí → vào Map ở chế độ giảm chức năng kèm banner giải thích (US-02).
- Từ chối thông báo → banner thường trực "Thông báo đang tắt — sự kiện zone vẫn được ghi vào Timeline" (US-04).
- Công tắc theo dõi: bật → service chạy + thông báo thường trực; tắt → service dừng trong ≤ 2 giây (US-09).
- `FusedLocationSource` phát `LocationPoint` mỗi 10 giây, `PRIORITY_HIGH_ACCURACY`, `minDistance 10m`.
- Service lọc bằng `LocationFilter`, ghi qua `TrackingRepository.record()`, gọi `ZoneEvaluator`
  và đẩy kết quả qua `ZoneEventRepository.record()` với `source = FOREGROUND`.
- Service giữ `lastKeptPoint` trong bộ nhớ và chỉ cập nhật nó khi `LocationFilter.accept()` trả
  `Accept` — **không** cập nhật bằng mỗi điểm nhận từ `LocationSource.stream()` (Key Insight #10).

**Phi chức năng**
- Thông báo thường trực nói rõ app đang theo dõi vị trí và có nút "Dừng theo dõi" (PRD §3.3, §7.3).
- Tắt công tắc → **không ghi thêm điểm nào** (PRD §7.3).
- Mất tín hiệu GPS → không crash, lộ trình chỉ đứt đoạn (PRD §7.4).

## Architecture

```
PermissionOnboardingScreen ──(Intent)──▶ PermissionViewModel ──(state)──▶ 3 bước
      │ rememberLauncherForActivityResult / ACTION_APPLICATION_DETAILS_SETTINGS
      ▼
FamilyTrackerNavHost: PermissionOnboardingRoute | MapRoute | (các route khác thêm sau)

MapViewModel.onIntent(ToggleTracking) ─▶ TrackingRepository.setTracking(true)
                                              └─▶ start LocationTrackingService
LocationTrackingService
   └─ LocationSource(named("fused")).stream()
        └─ LocationFilter.accept ──▶ TrackingRepository.record()
             └─ ZoneEvaluator.evaluate(point, zones, insideSet)
                  └─ ZoneEventRepository.record(source = FOREGROUND)
```

`LocationTrackingService` không vẽ gì và không biết ViewModel nào tồn tại (`LLM.md` §8.5).

## Related Code Files

**Tạo — `:ui`**
- `navigation/Routes.kt`, `navigation/FamilyTrackerNavHost.kt`
- `permission/LocationPermissionFlow.kt` (state holder composable), `permission/PermissionStatus.kt`
- `feature/permission/`: `PermissionContract.kt`, `PermissionViewModel.kt`, `PermissionScreen.kt`
- `feature/map/`: `MapContract.kt`, `MapViewModel.kt`, `MapScreen.kt` — **bản tối thiểu**, chỉ công tắc
  theo dõi + banner trạng thái quyền; bản đồ thật ở phase-05
- `designsystem/component/PermissionBanner.kt`

**Tạo — `:data`**
- `location/FusedLocationSource.kt`, `location/SimulatedLocationSource.kt` (thân rỗng, `TODO` ở phase-09)
- `location/LocationTrackingService.kt`, `location/TrackingNotification.kt`

**Sửa**
- `app/src/main/AndroidManifest.xml` — quyền + service + `allowBackup=false`
- `app/src/main/res/values/strings.xml` — mọi chuỗi tiếng Việt của onboarding (PRD §7.5)
- `MainActivity.kt` — `setContent { FamilyTrackerTheme { FamilyTrackerNavHost() } }`
- `data/di/DataModule.kt`, `ui/di/UiModule.kt`

## Implementation Steps

1. Khai báo manifest: 6 quyền §10 + `RECEIVE_BOOT_COMPLETED`, service với
   `android:foregroundServiceType="location"` `android:exported="false"`, `allowBackup="false"`.
2. `Routes.kt` — 6 route `@Serializable` đúng nguyên văn PRD §9 / `LLM.md` §7. Viết cả các route chưa
   có màn hình; NavHost tạm trỏ chúng vào một `Text("…")`.
3. `LocationPermissionFlow.kt`: composable đọc trạng thái 3 quyền, cung cấp
   `requestNotifications()`, `requestFineLocation()`, `openAppSettings()`, và một `LifecycleEventObserver`
   `ON_RESUME` để đọc lại quyền sau khi người dùng từ Settings quay về (US-03).
4. `PermissionContract` + `PermissionViewModel`: state là bước hiện tại + kết quả từng quyền;
   Intent gồm `Continue`, `Skip`, `PermissionResolved(type, granted)`, `ScreenResumed`;
   Effect gồm `RequestNotifications`, `RequestFineLocation`, `OpenAppSettings`, `GoToMap`.
   ViewModel **không** import `android.*` — nó chỉ ra lệnh bằng Effect.
5. `PermissionScreen`: 3 trang giải thích, mỗi trang có "Tiếp tục" và "Để sau" (US-01).
   Log `FTD_EVENT permission_result type=… granted=…` (PRD §10).
6. Ghi cờ "đã hoàn tất onboarding" — dùng bảng `members` (`isSelf`) hay `SharedPreferences`?
   **Chọn: kiểm tra quyền thật lúc khởi động**, không lưu cờ. Cờ lưu sẵn sẽ nói dối khi người dùng
   thu hồi quyền trong Settings, và US-05 chỉ cần "đủ quyền thì vào thẳng Map".
7. `FusedLocationSource`: `LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)`
   `.setMinUpdateDistanceMeters(MIN_DISTANCE_M)`. Bọc `callbackFlow` + `awaitClose { removeLocationUpdates }`.
   Kiểm quyền trước khi gọi, thiếu quyền → flow kết thúc, không ném.
8. `TrackingNotification`: kênh `location_tracking` importance LOW, `setOngoing(true)`, nội dung
   "Đang theo dõi vị trí", một action "Dừng theo dõi" gửi về service.
9. `LocationTrackingService`: `startForeground()` **ngay trong `onStartCommand`**; một `CoroutineScope`
   với `SupervisorJob`; collect `LocationSource.stream()`; giữ `insideZoneIds` trong bộ nhớ, nạp lại
   từ Room khi service khởi động. Giữ thêm `lastKeptPoint: LocationPoint?` (khởi tạo `null`), cập
   nhật **chỉ** trong nhánh `FilterResult.Accept` của `LocationFilter.accept()` — không cập nhật ở
   nhánh nhận điểm thô từ stream (Key Insight #10, `LLM.md` §8.3).
10. `TrackingRepositoryImpl.setTracking(enabled)` start/stop service qua `ContextCompat.startForegroundService`
    / `context.stopService`; `isTracking()` là `StateFlow` để công tắc phản ánh đúng khi service bị hệ
    thống giết.
11. Đăng ký Koin: `named("fused")` và `named("simulated")` cho `LocationSource`; `viewModelOf(::PermissionViewModel)`,
    `viewModelOf(::MapViewModel)`. Chạy lại `KoinModulesTest`.
12. Viết ViewModel test cho `PermissionViewModel` (`:ui/src/test/`): mỗi Intent một reducer test, mỗi
    Effect một test, theo MVI doc §7.

## Todo List

- [x] Manifest: 7 quyền, service `foregroundServiceType="location"`, `allowBackup=false`
- [x] `Routes.kt` 6 route type-safe + NavHost skeleton
- [x] `LocationPermissionFlow` + kiểm tra lại quyền ở `ON_RESUME`
- [x] `PermissionContract`/`ViewModel`/`Screen` — 3 bước riêng biệt, đúng thứ tự
- [x] Banner "từ chối vị trí" và "thông báo đang tắt"
- [x] `FusedLocationSource` 10s / HIGH_ACCURACY / 10m
- [x] `SimulatedLocationSource` khai báo sẵn với Koin qualifier
- [x] `LocationTrackingService` + thông báo thường trực + nút Dừng
- [x] Công tắc theo dõi bật/tắt, tắt trong ≤ 2 giây
- [x] Điểm đi qua `LocationFilter` trước khi vào Room
- [x] `lastKeptPoint` cập nhật CHỈ khi `accept()` trả `Accept` (Key Insight #10) — test riêng khoá
      luật này bằng chuỗi điểm <10m so với điểm ngay trước nhưng ≥10m so với điểm được giữ gần nhất
      (`LocationPointProcessorTest`, đỏ-rồi-xanh xác nhận bằng mutation thật)
- [x] Koin đăng ký đủ; `KoinModulesTest` xanh
- [x] `PermissionViewModel` test: reducer + effect + crash containment

**Hai bug thật phát hiện + sửa khi chạy thật trên `emulator-5554` (không có trong đặc tả gốc)**,
xem `LLM.md` §8.5 và §10, §13 Fixed #6/#7:
- `TrackingRepositoryImpl.setTracking(true)` crash cả app (`SecurityException`) nếu thiếu
  `ACCESS_FINE_LOCATION` — chặn bằng kiểm quyền trước khi `startForegroundService`.
- `LocationPermissionFlow`'s `Lifecycle.addObserver()` bắn ON_RESUME "bắt kịp" ngay khi mount,
  nhảy cóc onboarding thẳng ra Map trước khi người dùng làm gì — chặn bằng `isFirstResume` +
  gác theo `currentStep`.

## Success Criteria

```bash
./gradlew :app:assembleRelease :ui:test
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell dumpsys package com.example.pion.family.tracker.demo | grep -A2 BACKGROUND_LOCATION
adb logcat -s FTD_EVENT      # thấy permission_result ×3, tracking_toggled, location_recorded
adb shell dumpsys activity services com.example.pion.family.tracker.demo | grep -i foreground

# Trên emulator (ảnh Google APIs) — vòng lặp chính từ phase này trở đi:
adb emu geo fix 105.8 21.0                 # một điểm
adb emu geo playback plans/260821-1113-geofence-zone-and-history-tracking/reports/demo-route.gpx
```
- Cấp đủ 3 quyền → mở lại app vào thẳng Map (US-05).
- Bật công tắc → thông báo thường trực xuất hiện; tắt → biến mất trong ≤ 2 giây, `location_recorded`
  ngừng xuất hiện trong logcat (US-09).
- Từ chối vị trí → app vẫn mở, có banner, không crash (PRD §7.4).

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| Xin gộp quyền do sơ ý | Geofence chết im lặng ở phase-07 | Kiểm chứng bằng `dumpsys package` ở Success Criteria, không tin vào UI |
| Thiếu `foregroundServiceType` | Crash **lúc chạy**, build vẫn xanh | Bật công tắc trên thiết bị API 34+ trong chính phase này, không đợi phase-11 |
| OEM Trung Quốc (Xiaomi/Oppo/Vivo) kill FGS sau 1–2 phút | Mất đường phản hồi tức thì giữa buổi demo | Chọn thiết bị demo Pixel/Samsung; nếu buộc dùng OEM đó, bật "Autostart" trước demo (researcher-01 §4.3) |
| "Chỉ lần này" làm người demo tưởng app hỏng | Mất niềm tin ngay ở phút đầu | Banner trạng thái nói rõ quyền hiện tại, không chỉ ẩn/hiện chức năng |
| Service bị kill, công tắc vẫn hiện "đang bật" | UI nói dối | `isTracking()` đọc trạng thái service thật, không đọc cờ đã lưu |

## Security Considerations

- Thông báo thường trực **phải** nói rõ đang theo dõi vị trí (PRD §7.3) — đây là yêu cầu minh bạch,
  không phải trang trí.
- `location_recorded` chỉ log `accuracy` và `filtered`; **không log lat/lng** (gate G7).
- Tắt công tắc phải thực sự `removeLocationUpdates`, không chỉ ngừng ghi.
- `allowBackup="false"` để cơ sở dữ liệu vị trí không rời khỏi máy qua Google Backup.

## Next Steps

→ [phase-05](phase-05-map-screen.md). Chặn: 05, 06, 07, 08, 09, 10.
