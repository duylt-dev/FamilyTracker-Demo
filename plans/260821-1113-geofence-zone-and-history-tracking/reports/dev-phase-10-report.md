# dev-phase-10-report — Timeline nhật ký sự kiện zone (F4)

## Trạng thái: COMPLETED

## File tạo

- `ui/src/main/java/.../ui/feature/timeline/TimelineContract.kt`
- `ui/src/main/java/.../ui/feature/timeline/TimelineViewModel.kt`
- `ui/src/main/java/.../ui/feature/timeline/TimelineScreen.kt`
- `ui/src/main/java/.../ui/feature/timeline/component/TimelineRow.kt`
- `ui/src/main/java/.../ui/feature/timeline/component/DayHeader.kt`
- `ui/src/main/java/.../ui/feature/timeline/component/EmptyTimelineState.kt`
- `ui/src/test/java/.../ui/feature/timeline/TimelineViewModelTest.kt` (8 test)

`domain/usecase/ObserveZoneTimelineUseCase.kt` đã tồn tại sẵn từ trước phase-10 (không phải việc
của phase này) — chỉ đăng ký Koin (`factoryOf`) là việc mới.

## File sửa

- `ui/src/main/java/.../ui/navigation/FamilyTrackerNavHost.kt` — route `TimelineRoute` thật thay
  placeholder `Text(...)`, alias `TimelineScreenRoute`, `onOpenHistoryAt` cho US-35.
- `ui/src/main/java/.../ui/di/UiModule.kt` — `factoryOf(::ObserveZoneTimelineUseCase)`,
  `viewModelOf(::TimelineViewModel)`, `single<Clock> { Clock.systemDefaultZone() }` (bắt buộc —
  xem "Sai lệch" #1).
- `ui/src/main/res/values/strings.xml` — 7 chuỗi mới (`timeline_*`).
- `LLM.md` — §3 (cây `feature/timeline/`), §6 (mẫu Clock/Koin), §7 (Timeline route thật + bằng
  chứng US-35), §13 Fixed #19 (crash `stickyHeader` key).
- `plans/.../plan.md` dòng 10 — `pending` → `completed`.
- `plans/.../phase-10-zone-timeline.md` — Status + 8/8 Todo tick.

**Không sửa:** `app/MainActivity.kt` — đã hoàn thiện sẵn từ phase-07/08 (`onCreate`/`onNewIntent`
đọc `ftd_route`, tăng `pendingRouteNonce`), phase file liệt kê nó ở "Related Code Files > Sửa"
nhưng không có gì để sửa; xác nhận bằng đọc code + logcat thật (không đổi hành vi bấm thông báo).
`HistoryScreen.kt`/`HistoryContract.kt`/`HistoryViewModel.kt`/`HistoryMap.kt` — plumbing
`HistoryEffect.FocusCamera` đã đủ từ phase-08, không cần sửa gì để US-35 chạy (chỉ có 1 sửa TẠM
THỜI để đo, đã revert — xem "US-35 end-to-end" bên dưới).

## US-34→US-36 — bằng chứng thao tác + quan sát

| US | Thao tác | Quan sát | Ảnh |
|---|---|---|---|
| US-34 (rỗng) | `pm clear` (người dùng mới) → mở app → tab Nhật ký | "Chưa có sự kiện nào." + "Tạo zone rồi thử nút mô phỏng lộ trình." — đúng PRD §3.4, đúng `isEmpty` (chỉ true sau khi `isLoading` đã xong) | `p10/20-release-timeline-empty.png` |
| US-34 (có dữ liệu) | Tab Lịch sử → "▶ Mô phỏng lộ trình" → chờ ~30s → tab Nhật ký | 2 dòng, mới nhất trước: "Đã rời Zone mẫu" (chấm đỏ `#C62828`) 04:38 trên cùng, "Đã đến Zone mẫu" (chấm xanh `#2E7D32`) 04:38 dưới — đúng thứ tự ENTER trước EXIT theo thời gian thật | `p10/21-release-timeline-data.png` |
| US-36 (nhóm ngày) | Cùng ảnh trên | Header dính "Hôm nay" phía trên cả 2 dòng (cả hai cùng ngày hôm nay, chưa quan sát được "Hôm qua"/`dd/MM/yyyy` trên máy vì không có dữ liệu quá khứ — xem "Chỗ còn dở") | `p10/21-release-timeline-data.png` |
| US-35 | Tab Nhật ký → bấm dòng "Đã đến Zone mẫu" | Mở History, ngày đúng (22/08/2026 = ngày sự kiện), bản đồ zoom street-level (khác hẳn ảnh tĩnh trước đó — Google tiles tải xong), route polyline của chuyến vừa mô phỏng hiển thị, camera đứng ở khu vực marker đỏ gần Bến Thành | `p10/22-release-us35-focused.png` |

## US-35 end-to-end — bằng chứng chặt hơn ảnh chụp màn hình

Vì lộ trình mô phỏng ngắn (~600-900m), ảnh "History mở qua Timeline" (focus, zoom 17 cố định) và
"History mở qua tab đáy" (bounds-fit theo route) **trông gần giống hệt nhau bằng mắt** — không đủ
để khẳng định nhánh `focusPoint` thật sự chạy. Đo lại bằng cách chắc chắn hơn:

1. Đọc trực tiếp `zone_events` từ Room (bản debug, `run-as` + `sqlite3` qua Python, theo đúng quy
   trình WAL trong briefing): sự kiện ENTER có `latitude=10.801276547459198, longitude=106.7`.
2. Chèn TẠM một dòng `Log.d` vào đúng nhánh `focusPoint != null` của
   `HistoryMap.kt`'s `LaunchedEffect` (file không thuộc sở hữu phase-10, sửa xong revert ngay,
   `git diff` xác nhận **0 dòng khác biệt** sau khi xong — không có tồn dư trong commit).
3. `assembleDebug` → cài → mở app → tab Nhật ký → bấm dòng "Đã đến Zone mẫu" (04:29) → logcat:
   `p10_verify_focus_branch lat=10.801276547459198 lng=106.7` — **khớp CHÍNH XÁC** với bước 1.
4. Revert dòng Log, `git diff` xác nhận sạch, `assembleRelease` lại, cài lại bản release, chạy lại
   toàn bộ luồng US-34→US-36 một lần nữa trên bản đã revert để xác nhận hành vi không đổi.

Kết luận: đường dây `TimelineEffect.OpenHistory(epochDay, lat, lng)` →
`FamilyTrackerNavHost.onOpenHistoryAt` → `HistoryRoute(epochDay=, focusLat=, focusLng=)` →
`HistoryViewModel.init` đọc `SavedStateHandle` → `HistoryEffect.FocusCamera` →
`HistoryRoute`'s `focusPoint` → `HistoryMap`'s `newLatLngZoom(focusPoint, 17f)` **chạy đúng, đúng
toạ độ, trên máy thật** — không phải suy luận từ đọc code. Đây là lần đầu tiên đường dây này (để
sẵn từ phase-08) được chứng minh chạy hết đầu-cuối.

## Effect → nơi collect

| Effect | Nơi collect |
|---|---|
| `TimelineEffect.OpenHistory(epochDay, lat, lng)` | `TimelineRoute`'s `CollectEffects` → gọi `onOpenHistoryAt(...)` → `FamilyTrackerNavHost` điều hướng `HistoryRoute(...)` |

Không có Effect thứ hai (không `ShowError`) — theo đúng tiền lệ `ZoneListViewModel.init` (không
onError riêng cho combine trong `init`, dựa vào crash-containment mặc định của `collectSafely`).
Có bổ sung `onError` cho `init`'s `collectSafely` để hạ `isLoading` (xem "Sai lệch" #2).

## Sai lệch so với phase file

1. **`TimelineViewModel(clock: Clock = Clock.systemDefaultZone())` cần thêm `single<Clock>` vào
   `UiModule.kt`, không có trong "Related Code Files" của phase file.** `viewModelOf(::X)` của Koin
   resolve MỌI tham số qua `get()`, không đọc default Kotlin — thiếu binding này,
   `KoinModulesTest.verify()` fail. Cùng mẫu `AppLogger` đã có (§4 điểm 4) — ghi vào `LLM.md` §6.
2. **Thêm `onError` cho `collectSafely` trong `TimelineViewModel.init`** — phase file không yêu
   cầu rõ, nhưng viết crash-containment test ("a failing timeline flow lowers isLoading instead of
   crashing") lộ ra: không có `onError`, `isLoading` mặc định `true` sẽ kẹt vĩnh viễn nếu Room ném
   lỗi (màn hình quay vòng vô hạn, không rơi về empty state, không crash — lỗi câm). Sửa theo đúng
   luật MVI doc §1 "onError must lower every flag the call raised".
3. **`TimelineDay` có thêm field `epochDay: Long` không có trong phase file Implementation Step 1**
   (phase file chỉ liệt `label` + `items`) — **bắt buộc để sửa một crash thật trên máy**, xem mục
   tiếp theo. Đây không phải một lựa chọn thiết kế tuỳ chọn.
4. **`app/MainActivity.kt`** nằm trong "Related Code Files > Sửa" của phase file nhưng không có gì
   để sửa — logic đọc `ftd_route` + `pendingRouteNonce` đã hoàn thiện từ phase-07/08. Xác nhận
   bằng đọc code và bằng logcat thật (tap thông báo mở đúng Timeline, xem `p10/` không có ảnh riêng
   vì hành vi không đổi so với phase-07's báo cáo).
5. Không dùng `MviViewModel.collectSafely`'s `onError` riêng cho `EventTapped` — vì thao tác đó là
   một hàm thuần đọc lại state hiện có (`currentState.days...`), không có coroutine/IO nào để bắt lỗi.

## Lỗi thật bắt được trên máy — không phải review tĩnh

**`LazyListScope.stickyHeader(key = day.label)` crash app khi mở Timeline có dữ liệu:**

```
java.lang.IllegalArgumentException: Type of the key Today is not supported. On Android you
can only use types which can be stored inside the Bundle.
	at androidx.compose.runtime.saveable.SaveableStateHolderImpl.SaveableStateProvider
	at androidx.compose.foundation.lazy.layout.LazySaveableStateHolder.SaveableStateProvider
	at androidx.compose.foundation.lazy.layout.LazyLayoutItemContentFactoryKt.SkippableItem-JVlU9Rs
```

Compose bọc `key` của `items`/`stickyHeader` qua `SaveableStateHolder` (giữ trạng thái cuộn qua đổi
cấu hình) — đòi kiểu Bundle-safe. `TimelineDayLabel.Today` (sealed interface, `data object`) không
thoả. `./gradlew test`/`assembleDebug`/`assembleRelease` đều XANH trước khi sửa — lỗi chỉ lộ khi
chạm màn hình thật, đúng lý do brief này nhấn mạnh "chạy thật, dán bằng chứng". Sửa: thêm
`TimelineDay.epochDay: Long` (đã duy nhất theo nhóm nhờ `groupBy` theo `LocalDate`),
`stickyHeader(key = day.epochDay)` thay `key = day.label`. Xác nhận lại: cài bản đã sửa, mở
Timeline có 2 dòng — không crash; `adb logcat -d | grep FATAL` rỗng ở mọi lần chạy sau đó.
Ghi vào `LLM.md` §13 Fixed #19.

## Kiểm chứng build/test

```
./gradlew test                                                       → BUILD SUCCESSFUL, 131 test
./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"   → 1  (đúng baseline G6)
./gradlew assembleRelease                                            → BUILD SUCCESSFUL
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk       → Success
```

Gate G7 (`adb logcat -d | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"`) chạy lại SAU KHI revert dòng Log
tạm và cài lại bản release sạch → **rỗng** ngay sau lượt Timeline→History→US-35 (exit code 1). Ở
lần chạy CUỐI CÙNG (relaunch cuối để chụp ảnh trạng thái sạch), grep thô bắt được ĐÚNG 1 dòng —
nhưng đọc kỹ thì đó KHÔNG PHẢI leak từ code app:

```
1258  8950 W Geofencer: registration not active, ... GeofenceRequest(10.772307280386052,106.699...
```

`pid=1258` là tiến trình hệ thống Play Services, tag `Geofencer` (không phải `FTD_EVENT`) — log
chẩn đoán nội bộ của `GeofencingClient` khi Play Services xử lý `addGeofences()`, không phải dòng
do app này in ra. Lọc riêng `adb logcat -d -s FTD_EVENT:D | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"`
(chỉ log của app) → **rỗng**, đúng gate. Dòng `Geofencer` này xuất hiện ở MỌI phase có đăng ký
geofence từ phase-07 trở đi (không phải hồi quy của phase-10), và gate G7 đọc thô bằng `grep` như
briefing ghi sẽ bắt nhầm nó mỗi lần build có gọi `addGeofences()`. Ghi lại để phase-11 (quality
gates) tinh chỉnh luật đo G7 (lọc theo `-s FTD_EVENT:D` thay vì logcat thô toàn hệ thống) — cùng
tinh thần ENV-BRIEFING §8 đã làm cho G6.

Không crash (`grep -i "FATAL EXCEPTION"` rỗng) trên toàn bộ phiên xác minh cuối cùng (bản release
đã revert log tạm).

## Chỗ còn dở

- **Chưa quan sát được header "Hôm qua" / `dd/MM/yyyy` thật trên máy** — dữ liệu demo chỉ có sự
  kiện "hôm nay" (mô phỏng vừa chạy). Nhóm ngày và nhãn đã được khoá bằng
  `TimelineViewModelTest` với `Clock` cố định (test "day label is Today, Yesterday, or a formatted
  date"), nhưng đó là bằng chứng unit test, không phải ảnh chụp màn hình thật nhiều ngày — cần dữ
  liệu lịch sử ≥1 ngày để chụp ảnh thật, ngoài khả năng của phiên làm việc này (không thể tua thời
  gian hệ thống một cách an toàn mà không ảnh hưởng các test khác đang chạy trên cùng emulator).
- **Danh sách chỉ có 2-3 sự kiện thật trong phiên test** — chưa xác nhận trực quan việc cắt
  `MAX_VISIBLE_EVENTS = 200` trên máy (đã khoá bằng test "500 events... capped to 200").
- Trong lúc test bắt được thêm một hiện tượng ĐÃ TÀI LIỆU HOÁ (không phải bug mới, không sửa):
  mở lại app debug sau một khoảng nghỉ ngắn có thể kích hoạt thêm 1 `zone_event_raised
  source=GEOFENCE_API` thật (đúng "Phát hiện phụ ngoài scope, KHÔNG sửa" ở `LLM.md` §13 Fixed #18)
  — Timeline hiển thị đúng sự kiện này như một sự kiện thật (không phải lỗi hiển thị), chỉ là nó
  đến từ hành vi Play Services đã biết, ngoài phạm vi phase-10.
