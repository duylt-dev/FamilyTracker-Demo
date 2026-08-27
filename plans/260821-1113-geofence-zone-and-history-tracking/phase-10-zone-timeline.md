# Phase 10 — Timeline nhật ký sự kiện zone (F4)

## Context Links

- [`plan.md`](plan.md) · [`phase-07`](phase-07-geofence-and-notification.md) · [`phase-08`](phase-08-history-and-route-playback.md)
- [`LLM.md`](../../LLM.md) §3 (`:ui/feature/timeline`), §5, §7, §9
- PRD §2.7 US-34→US-36 · §3.4 F4 · §5.8 Timeline Screen Layout · §5.2 màu
- [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §2, §4

## Overview

| | |
|---|---|
| Priority | **P1** |
| Status | completed |
| Effort | 4h |
| Story ánh xạ | **US-34** (P0) · **US-35** (P1) · **US-36** (P2) — feature **F4** |

Màn hình đọc thuần: nhật ký các lần vào/rời zone trong 7 ngày, nhóm theo ngày, bấm một dòng thì mở
History của ngày đó. Đây cũng là đích đến khi người dùng bấm vào thông báo (phase-07).

## Key Insights

1. **Timeline là thứ duy nhất chứng minh app đang làm việc khi thông báo bị tắt quyền** (PRD §3.2,
   US-04: "sự kiện zone vẫn được ghi vào Timeline"). Nếu người demo từ chối quyền thông báo, đây là
   màn hình cứu buổi demo — nó không phải màn hình phụ.
2. **`zoneName` đọc từ `zone_events`, không join sang `zones`** (quyết định phase-02). Xoá zone không
   được làm rỗng nhật ký của nó.
3. **`HistoryRoute` đã được mở rộng thành `(epochDay, focusLat, focusLng)` ở PRD v1.2 §9** — đúng
   phương án A đã đề xuất. US-35 chở được vị trí sự kiện; chép nguyên văn chữ ký đó, không tự nghĩ.
4. **Header dính "Hôm nay"/"Hôm qua"/`dd/MM/yyyy`** (US-36) là `stickyHeader` của `LazyColumn`; nhóm
   theo ngày phải tính ở ViewModel, không ở composable (MVI doc §4: composable không suy ra sự thật
   nghiệp vụ).
5. **Danh sách có thể tăng không giới hạn.** 7 ngày × nhiều zone × 2 loại sự kiện. Cắt ở ViewModel
   (`.take(n)`) chứ không ở `LazyColumn` — `LazyColumn` vẽ 10 dòng của 500 vẫn giữ cả 500 (MVI doc §8).
6. **Effect rỗng thì khai báo `sealed interface` rỗng, đừng bỏ đi** (MVI doc §2). Nhưng ở đây có ít
   nhất một Effect thật: mở History. Điều hướng là Effect, không phải cờ trong state (`LLM.md` §7).

## Requirements

**Chức năng**
- Danh sách mới nhất trước: icon vào/ra, tên zone, giờ `HH:mm`, ngày (US-34).
- Icon và màu: vào `#2E7D32`, rời `#C62828` (PRD §5.2).
- Bấm một sự kiện → mở History của ngày đó, camera canh vào vị trí sự kiện (US-35).
- Nhóm theo ngày với header dính: "Hôm nay" / "Hôm qua" / `dd/MM/yyyy` (US-36).
- Empty state: "Chưa có sự kiện nào. Tạo zone rồi thử nút mô phỏng lộ trình." (PRD §3.4).
- Là đích của `ftd_route=timeline` khi bấm thông báo (phase-07).

**Phi chức năng**
- Nguồn dữ liệu: `ObserveZoneTimelineUseCase(sinceDays = HISTORY_RETENTION_DAYS)`.
- File screen < 200 dòng; chuỗi ở `strings.xml`; không `.dp`/`.sp` rời rạc trong `feature/timeline/`.

## Architecture

```
TimelineRoute ─▶ TimelineViewModel(observeZoneTimeline)
   init: observeZoneTimeline(7).map { nhóm theo ngày, cắt số lượng }.onEach { setState }
TimelineScreen(state, onIntent)
   └ LazyColumn
       ├ stickyHeader  DayHeader("Hôm nay" | "Hôm qua" | dd/MM/yyyy)
       ├ items         TimelineRow(icon, zoneName, HH:mm, memberName nếu ≠ mình)
       └ EmptyTimelineState

Intent: EventTapped(id)      Effect: OpenHistory(epochDay, lat, lng)
```

## Related Code Files

**Tạo**
- `ui/feature/timeline/TimelineContract.kt`, `TimelineViewModel.kt`, `TimelineScreen.kt`
- `ui/feature/timeline/component/TimelineRow.kt`, `DayHeader.kt`, `EmptyTimelineState.kt`
- `ui/src/test/.../TimelineViewModelTest.kt`

**Sửa**
- `ui/navigation/FamilyTrackerNavHost.kt`, `Routes.kt` — `HistoryRoute(epochDay, focusLat, focusLng)` theo PRD v1.2 §9
- `ui/di/UiModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/MainActivity.kt` — hoàn thiện điều hướng từ extra `ftd_route`
- `LLM.md` §7 — đối chiếu với PRD v1.2 §9

## Implementation Steps

1. `TimelineContract`: state `days: List<TimelineDay>` (mỗi `TimelineDay` = nhãn + danh sách
   `TimelineItem`), `isLoading`. `TimelineItem` giữ `eventId`, `zoneName`, `type`, `time`, `memberName?`,
   `epochDay`, `lat`, `lng`.
2. `TimelineViewModel.init`: `observeZoneTimeline(HISTORY_RETENTION_DAYS)` → nhóm theo `LocalDate` của
   `occurredAt` ở múi giờ thiết bị → sắp xếp giảm dần → `.take(...)` giới hạn số dòng.
   Nhãn ngày ("Hôm nay"/"Hôm qua") tính ở đây, **không** ở composable.
3. `TimelineScreen`: `LazyColumn` + `stickyHeader`. `TimelineRow` là composable `internal`, một file riêng.
4. Bấm dòng → `EventTapped(id)` → Effect `OpenHistory(epochDay, lat, lng)` → `navController.navigate(HistoryRoute(...))`.
5. `MainActivity`: nếu `intent.getStringExtra("ftd_route") == "timeline"`, điều hướng tới `TimelineRoute`
   sau khi NavHost sẵn sàng; xử lý cả `onNewIntent` (app đang chạy nền thì `onCreate` không chạy lại).
6. `EmptyTimelineState` trỏ tới nút mô phỏng. Nút có ở cả hai variant (PRD v1.2 §6) nên lời gợi ý
   luôn đúng; vẫn đọc **chung** cờ `simulatorEnabled` với phase-08 để hai màn không lệch nhau.
7. `TimelineViewModelTest`: nhóm ngày đúng (3 sự kiện của 2 ngày → 2 nhóm), nhãn "Hôm nay"/"Hôm qua"
   đúng với một `Clock` cố định, effect `OpenHistory` mang đúng `epochDay`, và test giới hạn số dòng
   (500 sự kiện trong fake → state chỉ giữ n).

## Todo List

- [x] `TimelineContract` + model trình bày dựng ở ViewModel
- [x] Nhóm theo ngày + nhãn "Hôm nay"/"Hôm qua" tính ở ViewModel
- [x] `stickyHeader` + `TimelineRow` màu theo PRD §5.2
- [x] Bấm dòng → Effect `OpenHistory`, điều hướng không qua cờ state
- [x] `MainActivity` xử lý `ftd_route` ở cả `onCreate` và `onNewIntent` — đã hoàn thiện sẵn từ
      phase-07/08, xác nhận lại (không sửa gì) — xem dev-phase-10-report.md "Sai lệch"
- [x] Empty state đọc chung cờ `simulatorEnabled` với phase-08
- [x] Giới hạn số dòng ở ViewModel (`MAX_VISIBLE_EVENTS = 200`)
- [x] `TimelineViewModelTest`: nhóm ngày · nhãn · effect · giới hạn

## Success Criteria

```bash
./gradlew :ui:test :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
# Chạy mô phỏng (phase-09) rồi mở Timeline
adb shell am start -n com.example.pion.family.tracker.demo/.MainActivity
```
- Sau một lượt mô phỏng: Timeline có đúng 2 dòng mới ("Đã đến" xanh, "Đã rời" đỏ), giờ khớp logcat.
- Bấm dòng → mở History đúng ngày, camera ở đúng chỗ sự kiện.
- Bấm thông báo zone khi app đang ở nền → mở thẳng Timeline.
- Tắt quyền thông báo, chạy mô phỏng → **không** có thông báo nhưng Timeline **vẫn** có 2 dòng (US-04).

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| ~~`HistoryRoute` không chở được vị trí sự kiện~~ — **đã giải quyết ở PRD v1.2 §9** | — | Khai đúng `HistoryRoute(epochDay, focusLat, focusLng)`; `HistoryViewModel` đọc `focusLat/focusLng` vào initial state rồi bắn `HistoryEffect.FocusCamera` |
| `stickyHeader` còn là API experimental ở Compose BOM 2026.02.01 | Warning hoặc đổi chữ ký | Chấp nhận `@OptIn` với chú thích; nếu API đổi, dùng header thường (US-36 là P2) |
| Nhóm ngày sai khi thiết bị đổi múi giờ | Sự kiện nhảy ngày | Dùng `ZoneId.systemDefault()` một chỗ duy nhất, truyền `Clock` vào ViewModel để test được |
| Danh sách phình theo thời gian demo dài | Cuộn chậm | `.take(n)` ở ViewModel; 7 ngày lưu trữ đã là trần trên |
| `onNewIntent` không được xử lý | Bấm thông báo khi app ở nền không đi đâu cả | Kiểm bằng tay: mở app → về home → bấm thông báo |

## Security Considerations

- Timeline hiển thị tên zone và giờ, **không** hiển thị toạ độ dạng số cho người xem.
- `lat`/`lng` chỉ đi qua tham số điều hướng trong tiến trình, không ra logcat (gate G7).
- Không có chức năng xuất/chia sẻ nhật ký ở v1.0.

## Next Steps

→ [phase-11](phase-11-quality-gates-and-docs.md). Đây là feature cuối; sau đây chỉ còn nghiệm thu.
