# Phase 08 — History: chọn ngày, polyline, chuyến đi, thống kê (F3)

## Context Links

- [`plan.md`](plan.md) · [`phase-05`](phase-05-map-screen.md) · [`phase-03`](phase-03-domain-tracking-algorithms.md)
- [`LLM.md`](../../LLM.md) §3 (`:ui/feature/history`), §8.3, §8.5, §9
- [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §2 (ví dụ `HistoryState`), §8
- PRD §2.6 US-27→US-32 · §3.3 F3 · §5.7 History Screen Layout · §7.1 (<1s / 2000 điểm) · §9
- [`research/researcher-02-maps-compose.md`](research/researcher-02-maps-compose.md) §2.3, §3.3, §5

## Overview

| | |
|---|---|
| Priority | **P0** |
| Status | completed |
| Effort | 8h |
| Story ánh xạ | **US-27, US-28, US-29, US-30, US-31, US-32** — hoàn tất **F3** phần đọc lại |

Màn hình xem lại lộ trình đã đi. Toàn bộ logic tách chuyến, lọc nhiễu và tính thống kê đã có sẵn và
đã được test từ phase-03 — phase này chỉ đọc, gộp và vẽ. Nút "Mô phỏng lộ trình" ở phase-09.

## Key Insights

1. **Không simplify là giật, và ngưỡng ở PRD §7.1 là < 1 giây cho ~2000 điểm** (researcher-02 §5).
   Dùng `PolyUtil.simplify(points, tolerance)` từ `maps-compose-utils 8.4.0` (đã có trong catalog từ
   phase-01) — **không** thêm `com.googlecode.simplify-java` như researcher-02 §5.2 gợi ý, đó là một
   dependency thừa cho một thuật toán đã nằm trong thư viện đang dùng.
2. **Simplify lúc vẽ, không lúc lưu.** Điểm gốc vẫn cần cho thống kê quãng đường và cho `ZoneEvaluator`;
   giảm mẫu khi ghi là mất dữ liệu không lấy lại được (researcher-02 §12.1).
3. **Chu kỳ 10 giây = tối đa 8 640 điểm thô mỗi ngày; PRD v1.2 §7.1 đã ghi con số này và bỏ ước
   lượng "~2000 điểm" cũ.** `LocationFilter` (`distance < 10m`) cắt phần lớn khi đứng yên, nhưng
   **không có trần đảm bảo** — giảm mẫu Douglas-Peucker là bắt buộc, không phải tối ưu.
   Đo thật bằng `FTD_EVENT history_rendered day pointCount renderMs`.
4. **`newLatLngBounds` ném `IllegalStateException` nếu map chưa layout xong** (researcher-02 §3.3).
   Gọi trong `LaunchedEffect`, bắt lỗi, `delay(100)` rồi `move()` — và **lần canh camera đầu tiên không
   animate** (MVI doc §8): không thì mỗi lần vào tab là một chuyến bay từ thành phố mặc định.
5. **Chuyến được chọn giữ bằng id, không giữ đối tượng** (MVI doc §2) — `HistoryState` trong PRD §9 đã
   viết đúng như vậy (`selectedSessionId: String?`). Giữ đối tượng thì một lần refresh để lại bản sao cũ.
6. **Date picker giới hạn đúng 7 ngày gần nhất** (US-27) vì đó cũng là hạn lưu trữ. Cho chọn ngày thứ 8
   nghĩa là hứa một empty state mà thực ra là dữ liệu đã bị `PurgeOldHistoryUseCase` xoá.
7. **`TrackSession` được gom lúc query, không đọc từ bảng** (PRD v1.2 §9, `LLM.md` §9).
   `ObserveRouteForDayUseCase` đọc `location_points` của ngày rồi gọi `RouteSplitter` — mỗi lần Room
   re-emit là một lần gom lại. Vì thế `RouteSplitter` phải rẻ, và `TrackSession.id` phải ổn định
   (phase-03), nếu không chuyến đang chọn sẽ tự bỏ chọn mỗi khi có điểm mới ghi vào.
8. **US-31 nói "lộ trình sạch" nhưng bộ lọc chạy lúc *ghi*, không lúc *đọc*** (`LLM.md` §8.3: "Mọi điểm
   đi vào Room đều phải qua bộ lọc này"). Nếu màn History vẫn thấy đường nhảy lung tung thì lỗi ở
   phase-04, không ở đây — đừng lọc lần hai và che mất khuyết điểm ở tầng ghi.

## Requirements

**Chức năng**
- Date picker giới hạn 7 ngày gần nhất, mặc định hôm nay (US-27).
- `Polyline` nối các điểm theo thứ tự thời gian, dày 12dp, marker Start xanh / End đỏ (US-28).
- Thẻ thống kê: tổng km · thời lượng · tốc độ trung bình (US-29).
- Danh sách chuyến trong ngày, tách theo khoảng trống > 5 phút; chọn chuyến nào vẽ chuyến đó (US-30).
- Điểm nhiễu không xuất hiện trong polyline (US-31 — bảo đảm bởi bộ lọc lúc ghi).
- Empty state "Chưa có lộ trình nào trong ngày này" + gợi ý dùng nút mô phỏng (US-32).
- Mở qua `HistoryRoute(epochDay)` từ Timeline (phase-10 dùng tới).

**Phi chức năng**
- Vẽ lộ trình một ngày < 1 giây từ lúc chọn ngày (PRD §7.1), đo bằng `history_rendered.renderMs`.
- File screen < 200 dòng; mọi chuỗi ở `strings.xml`; đơn vị mét/km, ngày `dd/MM/yyyy`, giờ `HH:mm`.

## Architecture

```
HistoryRoute(epochDay?) ─▶ HistoryViewModel(savedStateHandle, observeRouteForDay)
   init: selectedDay = epochDay ?: hôm nay          ← tham số route LÀ initial state
   observeRouteForDay(memberId=self, day)
        └─ LocationPointDao.observeBetween  ─▶ RouteSplitter.split ─▶ List<TrackSession>
                                                    └─ RouteStats.of(selected) ─▶ stats
HistoryScreen(state, onIntent)
   ├ DayPickerBar          (US-27)
   ├ FamilyTrackerMap      dùng lại từ phase-05 + Polyline + Start/End marker
   ├ RouteStatsCard        (US-29)
   ├ SessionList           (US-30)
   └ EmptyRouteState       (US-32)
```

`HistoryState`/`Intent`/`Effect` viết đúng như PRD §9 đã phác — thêm `sessions`, bớt gì thì ghi lý do.

## Related Code Files

**Tạo**
- `ui/feature/history/HistoryContract.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`
- `ui/feature/history/component/`: `DayPickerBar.kt`, `RouteStatsCard.kt`, `SessionList.kt`,
  `RoutePolyline.kt`, `EmptyRouteState.kt`
- `ui/src/test/.../HistoryViewModelTest.kt`
- `ui/core/format/DistanceFormat.kt`, `DurationFormat.kt` (định dạng km / phút, không nằm trong `:domain`)

**Sửa**
- `ui/navigation/FamilyTrackerNavHost.kt` — `HistoryRoute(epochDay)`
- `ui/di/UiModule.kt`
- `app/src/main/res/values/strings.xml`
- `domain/usecase/ObserveRouteForDayUseCase.kt` — hoàn thiện phần ghép `RouteSplitter`

## Implementation Steps

1. `HistoryContract` theo PRD §9: `selectedDay`, `sessions`, `selectedSessionId`, `stats`, `isLoading`,
   `isSimulating` (trường cuối để dành phase-09). Computed `val selectedSession get() = sessions.firstOrNull { it.id == selectedSessionId }`.
2. `HistoryViewModel`: đọc `epochDay`, `focusLat`, `focusLng` từ `SavedStateHandle` vào **initial state**
   (PRD v1.2 §9 — route đã chở đủ ba tham số), không `setState` trong `init`. `init` chỉ mở luồng quan
   sát ngày hiện tại; nếu có `focusLat/focusLng` thì bắn một `HistoryEffect.FocusCamera` — đó là đường
   phục vụ US-35 của phase-10.
3. `SelectDay` Intent: huỷ job quan sát cũ rồi mở job mới ("cancel and replace", MVI doc §3) — người
   dùng bấm nhanh 3 ngày liên tiếp không được để ngày thứ nhất về sau ghi đè ngày thứ ba.
4. Chọn chuyến mặc định: chuyến **dài nhất** trong ngày, không phải chuyến đầu tiên — mở màn hình lên
   nhìn thấy ngay thứ đáng nhìn. Ghi quyết định này vào KDoc của reducer.
5. `RoutePolyline`: `PolyUtil.simplify(latLngs, tolerance = 10.0)` rồi `Polyline(points, width = 12.dp.toPx(), color = memberColor)`.
   Marker Start `HUE_GREEN`, End `HUE_RED`.
6. Canh camera: `LatLngBounds` từ điểm của chuyến đang chọn, `LaunchedEffect(selectedSessionId)`,
   try `animate(newLatLngBounds(bounds, padding))` — catch `IllegalStateException` → `delay(100)` → `move(...)`.
   Lần đầu tiên trong vòng đời màn hình dùng `move`, các lần sau `animate`.
7. `RouteStatsCard`: `stats` đến từ `RouteStats.of` ở `:domain`. Composable **không** tự tính lại km.
8. `SessionList`: mỗi dòng `HH:mm → HH:mm` + quãng đường, dòng đang vẽ có dấu ✓ (PRD §5.7).
9. `EmptyRouteState` (US-32) trỏ thẳng tới nút mô phỏng. Nút có mặt ở **cả hai** variant
   (`SIMULATOR_ENABLED`, PRD v1.2 §6) nên lời gợi ý luôn đúng — nhưng vẫn đọc **cùng một cờ** với nút,
   để một ngày nào đó tắt nút ở release thì lời gợi ý tắt theo, không phải sửa hai chỗ.
10. Log `FTD_EVENT history_rendered day=… pointCount=… renderMs=…` — đo từ lúc nhận `sessions` tới lúc
    `Polyline` được compose, bằng `withFrameNanos` hoặc mốc thời gian trong `LaunchedEffect`.
11. `HistoryViewModelTest`: reducer cho `SelectDay`/`SelectSession`, effect `FocusCamera`, crash
    containment, và test "ngày rỗng → `sessions` rỗng, `stats == null`, không NPE".

## Todo List

- [x] `HistoryContract` khớp PRD §9, selection giữ bằng id
- [x] `epochDay` từ route vào initial state
- [x] `SelectDay` huỷ-và-thay job quan sát
- [x] Date picker giới hạn 7 ngày
- [x] `PolyUtil.simplify` từ `maps-compose-utils`, tolerance 10m
- [x] Polyline 12dp + marker Start/End
- [x] Canh camera qua `LaunchedEffect` + fallback `move()`
- [x] `RouteStatsCard` đọc `RouteStats`, không tự tính
- [x] `SessionList` gom lúc query theo `SESSION_GAP_MS`, chuyến dài nhất được chọn mặc định, `id` ổn định
- [x] Empty state — CHỈ text gợi ý (nút mô phỏng thật + cờ `SIMULATOR_ENABLED` dời sang phase-09,
      xem dev-phase-08-report.md "Sai lệch")
- [x] `history_rendered` log đủ `pointCount` và `renderMs`
- [x] `HistoryViewModelTest` reducer · effect · ngày rỗng · crash

## Success Criteria

```bash
./gradlew :ui:test :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -s FTD_EVENT | grep history_rendered   # renderMs < 1000 với pointCount ~2000
```
- Chọn một ngày có dữ liệu → polyline hiện, camera vừa khít lộ trình, thẻ thống kê có số hợp lý.
- Chọn ngày không có dữ liệu → empty state, không crash, không polyline sót lại từ ngày trước.
- Ngày có 2 chuyến cách nhau > 5 phút → danh sách có 2 dòng, chọn dòng nào vẽ dòng đó.
- Bấm nhanh 3 ngày liên tiếp → màn hình hiển thị đúng ngày cuối cùng.

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| `pointCount` thật lớn hơn 2000 nhiều | Vượt ngưỡng < 1s | `history_rendered` đo thật; nếu vượt, tăng tolerance simplify hoặc gộp điểm ở tầng truy vấn — **không** giảm mẫu lúc ghi |
| `PolyUtil` không có trong `maps-compose-utils` mà ở `android-maps-utils` | Không compile | Kiểm bằng `./gradlew :ui:dependencies`; nếu thiếu, thêm `com.google.maps.android:android-maps-utils` và ghi version thật vào `VERSIONS-VERIFIED.md` |
| `newLatLngBounds` ANR khi gọi sớm | Treo màn hình | Bọc try/catch + `delay(100)` như researcher-02 §3.3 mô tả |
| Polyline vẽ lại mỗi khung hình khi kéo bản đồ | Giật đúng lúc người xem đang nhìn | Tách `RoutePolyline` khỏi phần chrome, `remember` danh sách đã simplify theo `selectedSessionId` |
| Lọc nhiễu bị làm lại ở tầng đọc | Che khuyết điểm ở tầng ghi, hai luật lệch nhau | Không lọc lần hai; nếu thấy đường nhảy, sửa ở phase-04 |

## Security Considerations

- `history_rendered` chỉ log `day`, `pointCount`, `renderMs` — **không** log toạ độ (gate G7).
- Không có export/chia sẻ lộ trình ở v1.0; dữ liệu chỉ được vẽ, không rời khỏi máy (PRD §7.3).

## Next Steps

→ [phase-09](phase-09-route-simulator.md). Chặn: 09 (nút mô phỏng nằm trên màn này), 10 (Timeline mở History theo ngày).
