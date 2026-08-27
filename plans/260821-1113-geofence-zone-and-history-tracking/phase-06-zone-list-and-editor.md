# Phase 06 — Zone List và Zone Editor (F1 phần 2)

## Context Links

- [`plan.md`](plan.md) · [`phase-05`](phase-05-map-screen.md) · [`phase-03`](phase-03-domain-tracking-algorithms.md)
- [`LLM.md`](../../LLM.md) §3 (`:ui/feature/zone`), §5, §9 (giới hạn 100 zone), §12
- [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §2, §3, §4, §9
- PRD §2.3 US-12→US-15 · §2.4 US-16→US-21 · §3.1 F1 · §5.6 Zone Editor Layout
- [`research/researcher-02-maps-compose.md`](research/researcher-02-maps-compose.md) §3.4 (crosshair)

## Overview

| | |
|---|---|
| Priority | **P0** |
| Status | completed |
| Effort | 8h |
| Story ánh xạ | **US-12→US-15** (Zone List) · **US-16→US-21** (Zone Editor) — hoàn tất **F1** |

Hai màn hình, một feature package. Sau phase này người dùng tạo/sửa/xoá được zone và dữ liệu đã sẵn
sàng cho geofence ở phase-07 — nhưng chưa có geofence nào được đăng ký.

## Key Insights

1. **Chặn 100 zone ở tầng use case, không đợi Play Services ném lỗi** (`LLM.md` §9, researcher-01 §1.2).
   `SaveZoneUseCase` đã làm việc này ở phase-03; phase này chỉ hiển thị đúng thông điệp US-21:
   "Android giới hạn 100 zone cho mỗi ứng dụng".
2. **Bán kính dưới 100m cho kết quả không ổn định trong nhà** (PRD §3.1, researcher-01 §7.4). Cảnh báo
   phải hiện **ngay trên slider** khi < 100m, không giấu trong tài liệu — nếu không, buổi demo sẽ bị
   hiểu là app lỗi.
3. **Tâm zone là tâm màn hình bản đồ, không phải một marker kéo được** (US-18, researcher-02 §3.4).
   Đọc `cameraPositionState.position.target`, vẽ crosshair bằng `Canvas` ở giữa. Cách này miễn nhiễm
   với việc ngón tay che mất marker.
4. **Slider bán kính phải cập nhật hình tròn ngay khi kéo, ở 60fps** (US-17, PRD §7.1). Bán kính là
   state của ViewModel; hình tròn đọc thẳng từ state. Nếu giật, nguyên nhân gần như chắc chắn là cả
   cây recompose chứ không phải `Circle` — xem `LLM.md` §13 #1.
5. **Tắt cả hai công tắc thông báo thì zone vẫn được vẽ và vẫn ghi sự kiện** (US-19, PRD §3.2:
   "Mọi sự kiện đều ghi vào `zone_events` kể cả khi thông báo bị tắt quyền"). Hai công tắc điều khiển
   *thông báo*, không điều khiển *ghi nhận*.
6. **Xoá zone phải huỷ đăng ký geofence** (US-14). Ở phase này `GeofenceRegistrar` chưa tồn tại —
   `DeleteZoneUseCase` chỉ xoá bản ghi; phase-07 bổ sung lời gọi huỷ. **Ghi `TODO` có tham chiếu
   phase-07 vào đúng chỗ**, đừng để nó thành một dòng bị quên.
7. **Route/Screen tách đôi là bắt buộc** (`LLM.md` §5): `ZoneEditorRoute` biết Koin và `LaunchedEffect`;
   `ZoneEditorScreen` chỉ nhận `state` + `onIntent`, nếu không `@Preview` không dựng được và test UI
   phải chạy cả Koin lẫn Room chỉ để vẽ một hình tròn.
8. **Tham số route đọc từ `SavedStateHandle` vào initial state**, không copy ở frame sau (MVI doc §3 luật 5).

## Requirements

**Chức năng — Zone List**
- Mỗi dòng: tên · bán kính · trạng thái "Đang ở trong / Ở ngoài" · công tắc bật/tắt thông báo (US-12).
- Bấm dòng → Zone Editor chế độ sửa (US-13).
- Vuốt để xoá + hộp thoại xác nhận (US-14).
- Empty state: "Nhấn giữ trên bản đồ để tạo zone đầu tiên" + nút tạo (US-15).

**Chức năng — Zone Editor**
- Tên bắt buộc 1–40 ký tự; trống → nút Lưu vô hiệu (US-16).
- Slider 50–2000m bước 10m, mặc định 150m, hình tròn cập nhật realtime, hiện giá trị bằng số (US-17).
- Tâm = tâm bản đồ + crosshair (US-18).
- Hai công tắc "khi vào" / "khi rời" độc lập (US-19).
- 6 màu định sẵn PRD §5.2 (US-20, P2).
- Zone thứ 101 → thông báo + vô hiệu Lưu (US-21).

**Phi chức năng**
- Kéo slider mượt 60fps; mọi chuỗi ở `strings.xml`; file screen < 200 dòng, phần dư sang `component/`.

## Architecture

```
feature/zone/
├ ZoneContract.kt        ZoneListState/Intent/Effect + ZoneEditorState/Intent/Effect
├ ZoneListViewModel.kt   observeZones + trạng thái "đang ở trong" từ ZoneEvaluator
├ ZoneEditorViewModel.kt SavedStateHandle(zoneId, lat, lng) → initial state
├ ZoneListScreen.kt
├ ZoneEditorScreen.kt
└ component/  ZoneRow.kt · RadiusSlider.kt · ColorPicker.kt · CenterCrosshair.kt
```

Hai ViewModel, hai Contract — một màn hình một `StateFlow` (MVI doc §2). Gộp hai màn vào một
ViewModel sẽ tạo một state class mà nửa số trường luôn vô nghĩa.

## Related Code Files

**Tạo**
- `ui/feature/zone/ZoneListContract.kt`, `ZoneListViewModel.kt`, `ZoneListScreen.kt`
- `ui/feature/zone/ZoneEditorContract.kt`, `ZoneEditorViewModel.kt`, `ZoneEditorScreen.kt`
- `ui/feature/zone/component/`: `ZoneRow.kt`, `RadiusSlider.kt`, `ColorPicker.kt`, `CenterCrosshair.kt`
- `ui/src/test/.../ZoneListViewModelTest.kt`, `ZoneEditorViewModelTest.kt`
- `domain/usecase/ObserveZoneMembershipUseCase.kt` — zone nào đang ở trong (US-12)

**Sửa**
- `ui/navigation/FamilyTrackerNavHost.kt`, `Routes.kt`
- `ui/di/UiModule.kt` — 2 ViewModel mới
- `app/src/main/res/values/strings.xml`
- `domain/usecase/DeleteZoneUseCase.kt` — `TODO(phase-07)`: huỷ đăng ký geofence

## Implementation Steps

1. `ZoneListContract`: state `zones: List<ZoneListItem>`, `isLoading`, `pendingDeleteId: String?`.
   `ZoneListItem` là model trình bày (tên, bán kính, `isInside`, `notifyEnabled`) — dựng ở ViewModel,
   **không** để composable tự suy ra (MVI doc §4 "Never derives business truth").
2. `ZoneListViewModel`: `init` quan sát `ObserveZonesUseCase` + `ObserveZoneMembershipUseCase`.
   Intent: `ZoneTapped`, `NotifyToggled(id, enabled)`, `DeleteRequested(id)`, `DeleteConfirmed`,
   `DeleteCancelled`, `CreateTapped`. Effect: `OpenEditor(zoneId?)`, `ShowMessage`.
3. `ZoneListScreen` + `ZoneRow`: vuốt xoá bằng `SwipeToDismissBox`, xác nhận bằng `AlertDialog` điều
   khiển bởi `pendingDeleteId` trong state (nó phải sống qua xoay màn hình → là State, không Effect).
4. Empty state US-15 kèm nút "Tạo zone" → `OpenEditor(null)`.
5. `ZoneEditorViewModel`: đọc `zoneId`, `lat`, `lng` từ `SavedStateHandle` vào **initial state**.
   `zoneId == null` → chế độ tạo, tâm = `(lat, lng)` từ route hoặc vị trí hiện tại nếu route không mang;
   `zoneId != null` → nạp zone và đặt camera vào tâm của nó.
6. State editor: `name`, `radiusMeters`, `centerLat/Lng`, `colorArgb`, `notifyOnEnter`, `notifyOnExit`,
   `zoneCount`, `isSaving`. Hai computed `val`: `isNameValid` (1–40 ký tự) và
   `canSave = isNameValid && !isSaving && (isEditing || zoneCount < MAX_ZONES)` — **derive, không lưu**.
7. `RadiusSlider`: `valueRange = 50f..2000f`, `steps` cho bước 10m, nhãn số bên phải, cảnh báo
   "⚠ Dưới 100m có thể không ổn định" hiện khi `radiusMeters < 100`.
8. `CenterCrosshair`: `Canvas` hai đường chéo ở giữa khung bản đồ. Tâm đọc từ
   `cameraPositionState.position.target`, đẩy về ViewModel bằng Intent `CenterMoved(lat,lng)` — có
   **debounce** để không bắn một Intent mỗi khung hình khi đang kéo.
9. `ColorPicker`: 6 màu PRD §5.2, dạng hàng chấm tròn, chấm đang chọn có viền.
10. Lưu: `SaveZoneUseCase` → `AppResult`. Failure vì đủ 100 zone → hiện thông điệp US-21 và vô hiệu Lưu.
    Success → Effect `NavigateBack`; **không** copy zone vừa lưu vào state, để `observeZones()` re-emit
    (MVI doc §3 "Room là nguồn sự thật duy nhất"). Log `FTD_EVENT zone_saved zoneId radius totalZones`.
11. Test 2 ViewModel: reducer từng Intent, effect từng Effect, crash containment (fake repository ném),
    và test "thất bại rồi thử lại": ném lỗi → `isSaving` xuống → bấm Lưu lần 2 **phải chạm repository**
    (MVI doc §1, mục `onError` phải hạ mọi cờ).

## Todo List

- [x] `ZoneListContract`/`ViewModel`/`Screen` + `ZoneRow`
- [x] Trạng thái "Đang ở trong / Ở ngoài" cho từng zone
- [x] Vuốt xoá + xác nhận, `pendingDeleteId` nằm trong state
- [x] Empty state US-15
- [x] `ZoneEditorContract`/`ViewModel`/`Screen`, tham số route vào initial state
- [x] `RadiusSlider` 50–2000 bước 10 + cảnh báo < 100m
- [x] `CenterCrosshair` + debounce `CenterMoved`
- [x] `ColorPicker` 6 màu
- [x] Chặn 101 zone kèm thông điệp US-21 (khoá bằng test 2 chiều; chưa dựng đủ 100 zone thật trên
      thiết bị để chụp ảnh biên — xem dev report "chỗ còn dở")
- [x] `TODO(phase-07)` huỷ geofence trong `DeleteZoneUseCase`
- [x] Test 2 ViewModel: reducer · effect · crash · retry-reaches-repository
- [x] Mọi file screen < 200 dòng

## Success Criteria

```bash
./gradlew :ui:test :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -s FTD_EVENT   # zone_saved xuất hiện với totalZones tăng dần
```
Kiểm tra tay theo đúng PRD §4.3 Flow 2 (phần trước bước mô phỏng):
- Nhấn giữ bản đồ → Editor mở với tâm đúng chỗ nhấn.
- Nhập tên "Nhà", kéo bán kính 200m → hình tròn đổi ngay, không giật.
- Để trống tên → nút Lưu xám.
- Lưu → về Map, zone hiện ngay không cần refresh.
- Vuốt xoá ở Zone List → có xác nhận → zone biến mất khỏi cả Map.
- Kéo slider xuống 80m → cảnh báo xuất hiện.

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| `CenterMoved` bắn mỗi khung hình khi kéo bản đồ | Giật + `setState` liên tục | Debounce ~100ms hoặc chỉ đọc tâm khi camera dừng (`cameraPositionState.isMoving == false`) |
| Hai màn hình gộp một ViewModel "cho nhanh" | State nửa vô nghĩa, test rối | Hai Contract riêng, ghi rõ trong `LLM.md` §3 |
| Xoá zone nhưng geofence còn sống (sau phase-07) | Thông báo cho zone đã xoá — lỗi lộ ngay trước khách | `TODO(phase-07)` + một mục bắt buộc trong Todo của phase-07 |
| Zone thứ 101 chặn ở UI nhưng không chặn ở use case | Play Services ném lỗi thô | Chặn đã nằm ở `SaveZoneUseCase` (phase-03), UI chỉ hiển thị |
| `SwipeToDismissBox` API Material3 đổi theo BOM 2026.02.01 | Không compile | Đối chiếu chữ ký thật trong IDE; nếu lệch, dùng nút xoá trong dòng — US-14 chỉ đòi "có xác nhận" |

## Security Considerations

- Tên zone do người dùng nhập được hiển thị trong **thông báo** ở phase-07 — giới hạn 40 ký tự (US-16)
  vừa là ràng buộc UI vừa chặn nội dung tràn notification.
- Không log toạ độ tâm zone; `zone_saved` chỉ log `zoneId`, `radius`, `totalZones` (PRD §10, gate G7).

## Next Steps

→ [phase-07](phase-07-geofence-and-notification.md). Chặn: 07 (cần zone lưu được), 09, 10.
