# Phase 03 — Thuật toán tracking thuần ở `:domain` + unit test (G2)

## Context Links

- [`plan.md`](plan.md) · [`phase-02`](phase-02-domain-model-and-room-persistence.md)
- [`LLM.md`](../../LLM.md) §8.2 (ZoneEvaluator), §8.3 (LocationFilter), §11 (bố cục test), §12
- PRD §6 hằng số · §3.2 F2 · §3.3 F3 · §11.1 **gate G2** · §10 telemetry
- [`research/researcher-01-geofencing-and-background-location.md`](research/researcher-01-geofencing-and-background-location.md) §6.3, §7.8

## Overview

| | |
|---|---|
| Priority | **P0** |
| Status | completed |
| Effort | 5h |
| Story ánh xạ | US-25, US-26 (F2) · US-29, US-30, US-31 (F3) |
| Gate | **G2** — unit test cho `ZoneEvaluator`, `LocationFilter`, `RouteStats` xanh, phủ trường hợp biên |

**Phase này đứng trước mọi phase UI, và đó là lý do nó tồn tại riêng.** Toàn bộ quyết định "đã vào
hay đã ra zone", "điểm này có phải nhiễu không", "quãng đường bao nhiêu" nằm trong các hàm không có
`Context`, không có Play Services, không có coroutine. Nhét chúng vào service hay ViewModel thì cách
duy nhất kiểm tra một trường hợp biên là chạy emulator với mock location — tức là trên thực tế sẽ
không ai kiểm tra.

## Key Insights

1. **Hysteresis là bắt buộc, không phải tinh chỉnh.** Vào khi `d < R`, chỉ ra khi `d > R + 30m`
   (`ZONE_EXIT_BUFFER_M`). Không có khoảng đệm, người đứng yên ngay mép zone nhận ENTER/EXIT liên
   tục theo từng sai số GPS (`LLM.md` §8.2, researcher-01 §7.8). US-26 nói thẳng: đứng yên 5 phút
   ở mép → **tối đa 1 sự kiện**.
2. **`ZoneEvaluator` nhận trạng thái trước đó làm tham số, không giữ trạng thái bên trong.**
   Chữ ký `evaluate(point, zones, previouslyInside): ZoneEvaluation` (`LLM.md` §8.2). Hàm giữ
   trạng thái không test được hai lần liên tiếp mà không dựng lại đối tượng.
3. **Ba luật lọc chạy theo thứ tự và mỗi luật có một lý do đo được** (`LLM.md` §8.3):
   `accuracy > 50m` (trong nhà GPS trả bán kính hàng trăm mét) · `distance < 10m` so với điểm giữ
   lại gần nhất (đứng yên 10 phút tạo 60 điểm chồng nhau) · tốc độ suy ra `> 200 km/h` (một cú
   nhảy GPS kéo polyline sang tỉnh khác rồi quay lại).
   **Bẫy:** luật khoảng cách phải so với **điểm cuối cùng được giữ**, không phải điểm cuối cùng
   nhận được — nếu không, đi chậm 9m mỗi nhịp sẽ bị loại vĩnh viễn.
4. **Khoảng cách dùng công thức Haversine viết tay trong `:domain`.** `android.location.Location.distanceBetween`
   nằm ở `android.*` — `:domain` không có Android plugin nên gọi nó là lỗi biên dịch, đúng như thiết kế.
5. **Tách chuyến là hàm thuần, không phải bảng — đã chốt** (PRD v1.2 §9, `LLM.md` §9).
   `RouteSplitter.split(points, SESSION_GAP_MS)` trả `List<TrackSession>` và nằm **cạnh `RouteStats`**
   trong `:domain/tracking/`. `TrackSession.id` sinh lúc đọc từ `memberId + startedAt`, nên nó phải
   **ổn định giữa hai lần gọi trên cùng dữ liệu**: màn History giữ chuyến đang chọn bằng
   `selectedSessionId`, và một id đổi mỗi lần Room re-emit sẽ tự bỏ chọn chuyến đang vẽ.
6. **`ZoneEventDeduper` là hàm thuần thứ tư.** `LLM.md` Phụ lục A.1 đặt luật khử trùng lặp ở
   `ZoneEventRepositoryImpl`. Vẫn giữ *nơi áp dụng* ở đó, nhưng *quyết định* tách ra thành
   `shouldRecord(lastSameKey: ZoneEvent?, incoming: ZoneEvent, windowMs: Long): Boolean` để US-25
   test được bằng JUnit thay vì bằng emulator. **Sai lệch có chủ ý — cập nhật `LLM.md` §3 và Phụ lục
   A.1 trong cùng commit.**
7. **`TrackingConstants.kt` là file duy nhất chứa ngưỡng** (PRD §6). Nếu một số xuất hiện ở hai nơi,
   QA đọc một nơi và code chạy nơi kia.

## Requirements

**Chức năng**
- `TrackingConstants`: 12 hằng số đúng giá trị PRD §6.
- `LocationFilter.accept(point, lastKept): FilterResult` — trả lý do loại (`ACCURACY`/`DISTANCE`/`SPEED`)
  để log `FTD_EVENT location_dropped reason=…` (PRD §10).
- `ZoneEvaluator.evaluate(point, zones, previouslyInside): ZoneEvaluation` — tôn trọng
  `notifyOnEnter`/`notifyOnExit` ở mức *sinh thông báo*, nhưng **vẫn ghi sự kiện** (PRD §3.2).
- `RouteSplitter.split(points): List<TrackSession>`, `RouteStats.of(session): RouteStats`
  (tổng mét, thời lượng, tốc độ trung bình).
- `ZoneEventDeduper.shouldRecord(...)`.
- Use case: `ObserveZonesUseCase`, `SaveZoneUseCase` (chặn ở `MAX_ZONES`), `DeleteZoneUseCase`,
  `ObserveRouteForDayUseCase`, `ObserveZoneTimelineUseCase`.

**Phi chức năng**
- `:domain/src/test/` chạy được bằng JUnit thuần, không Robolectric, không thiết bị.
- Không có `import android.*` hay `androidx.*` trong `:domain` — kiểm tra bằng grep ở Success Criteria.

## Architecture

```
LocationPoint ─▶ LocationFilter.accept ─┬─ REJECT(reason) ─▶ log, dừng
                                        └─ ACCEPT ─▶ TrackingRepository.record()
                                                   └─▶ ZoneEvaluator.evaluate(point, zones, inside)
                                                          └─▶ ZoneEvaluation(events, insideAfter)
                                                                 └─▶ ZoneEventRepository.record()
                                                                        └─ ZoneEventDeduper.shouldRecord
đọc lại:  List<LocationPoint> ─▶ RouteSplitter.split ─▶ List<TrackSession> ─▶ RouteStats.of
```

Không mắt xích nào trong sơ đồ này biết Android tồn tại.

## Related Code Files

**Tạo**
- `domain/tracking/TrackingConstants.kt`
- `domain/tracking/GeoDistance.kt` (Haversine, `internal`)
- `domain/tracking/LocationFilter.kt` (+ `FilterResult`, `DropReason`)
- `domain/tracking/ZoneEvaluator.kt` (+ `ZoneEvaluation`)
- `domain/tracking/RouteSplitter.kt`, `domain/tracking/RouteStats.kt`
- `domain/tracking/ZoneEventDeduper.kt`
- `domain/usecase/`: `ObserveZonesUseCase.kt`, `SaveZoneUseCase.kt`, `DeleteZoneUseCase.kt`,
  `ObserveRouteForDayUseCase.kt`, `ObserveZoneTimelineUseCase.kt`
- `domain/src/test/kotlin/.../tracking/`: `LocationFilterTest.kt`, `ZoneEvaluatorTest.kt`,
  `RouteStatsTest.kt`, `RouteSplitterTest.kt`, `ZoneEventDeduperTest.kt`
- `domain/src/test/kotlin/.../usecase/SaveZoneUseCaseTest.kt` (+ fake repository)

**Sửa**
- `data/repository/ZoneEventRepositoryImpl.kt` — gọi `ZoneEventDeduper` thay vì tự so sánh
- `LLM.md` §3 (thêm `ZoneEventDeduper`, `RouteSplitter`, `GeoDistance`), Phụ lục A.1

## Implementation Steps

1. `TrackingConstants.kt` — chép nguyên 12 dòng bảng PRD §6, mỗi hằng số một dòng comment nói **đổi
   thì hỏng gì**. `MAX_ZONES = 100` kèm ghi chú "giới hạn cứng Play Services, không được tăng".
2. `GeoDistance.haversineMeters(lat1, lng1, lat2, lng2)` — bán kính Trái Đất 6 371 008.8 m.
   Test đối chiếu 3 cặp toạ độ đã biết khoảng cách, sai số < 0.5%.
3. `LocationFilter.accept(point, lastKept)`:
   - `point.accuracyMeters > MAX_ACCURACY_M` → `Reject(ACCURACY)`
   - `lastKept != null && distance < MIN_DISTANCE_M` → `Reject(DISTANCE)`
   - `lastKept != null && dt > 0 && distance/dt > MAX_SPEED_KMH` → `Reject(SPEED)`
   - còn lại → `Accept`
   **`lastKept` là điểm cuối cùng được giữ lại**, không phải điểm cuối cùng nhìn thấy.
4. `ZoneEvaluator.evaluate`: với mỗi zone tính `d`. Đang ở ngoài và `d < R` → `ENTER`, thêm vào
   `insideAfter`. Đang ở trong và `d > R + ZONE_EXIT_BUFFER_M` → `EXIT`, bỏ khỏi `insideAfter`.
   Các trường hợp còn lại: giữ nguyên, không sinh sự kiện.
5. `RouteSplitter.split`: sắp theo `recordedAt`, cắt khi khoảng cách thời gian giữa 2 điểm liên tiếp
   `> SESSION_GAP_MS`. Chuyến chỉ có 1 điểm vẫn là một chuyến (quãng đường 0) — quyết định này phải
   khớp với empty state ở phase-08.
6. `RouteStats.of`: tổng khoảng cách các đoạn liên tiếp, thời lượng `endedAt - startedAt`, tốc độ TB
   = quãng đường / thời lượng. Thời lượng 0 → tốc độ 0, **không chia cho 0**.
7. `ZoneEventDeduper.shouldRecord`: `lastSameKey == null` → true; ngược lại
   `incoming.occurredAt - last.occurredAt >= windowMs`.
8. Viết 5 use case. `SaveZoneUseCase` kiểm `count() >= MAX_ZONES` → `AppResult.Failure(AppError.Validation)`
   **trước** khi chạm repository (researcher-01 §1.2).
9. Viết test. Bắt buộc phủ các trường hợp biên dưới đây (G2):

   | Test | Khẳng định |
   |---|---|
   | Đứng đúng mép `d == R` | Không sinh sự kiện nào (không vào vì `d < R` sai, không ra vì `d > R+30` sai) |
   | Đứng yên ở mép, 30 điểm dao động `R ± 5m` | **Đúng 1** sự kiện ENTER, 0 EXIT (US-26) |
   | Vào rồi ra thật (`d = R+40m`) | 1 ENTER, 1 EXIT |
   | GPS nhảy 5 km giữa 2 điểm cách 1 giây | `Reject(SPEED)` |
   | 60 điểm trùng nhau khi đứng yên | 1 điểm được giữ, 59 `Reject(DISTANCE)` |
   | `accuracy = 200m` trong nhà | `Reject(ACCURACY)` |
   | `RouteSplitter` với **0 điểm** | Danh sách rỗng, không ném |
   | `RouteSplitter` với **1 điểm** | Đúng 1 chuyến, `startedAt == endedAt`, quãng đường 0 |
   | 2 điểm cách nhau **đúng** `SESSION_GAP_MS` | 1 chuyến — ngưỡng là `>`, không phải `>=`; chốt một chiều và test đúng chiều đó |
   | 2 điểm cách nhau 6 phút | 2 chuyến (US-30) |
   | 7 điểm sinh **3 chuyến** trong một ngày | 3 chuyến, thứ tự tăng theo thời gian, `id` **giống hệt** khi gọi lại lần hai |
   | Chuyến 1 điểm | Stats = 0 km, 0 phút, 0 km/h, không chia 0 |
   | Event trùng khoá cách 30s / 90s | false / true (US-25) |
   | 101 zone | `SaveZoneUseCase` trả Failure, repository không bị gọi (US-21) |

10. Sửa `ZoneEventRepositoryImpl` dùng `ZoneEventDeduper`; cập nhật `LLM.md` §3 + A.1 cùng commit.

## Todo List

- [x] `TrackingConstants` khớp từng dòng PRD §6
- [x] `GeoDistance` Haversine + test sai số
- [x] `LocationFilter` 3 luật, so với **điểm được giữ**
- [x] `ZoneEvaluator` + hysteresis 30m
- [x] `RouteSplitter` (cạnh `RouteStats`) + `TrackSession.id` ổn định
- [x] `ZoneEventDeduper` + đấu nối vào `ZoneEventRepositoryImpl`
- [x] 5 use case, `SaveZoneUseCase` chặn 100 zone
- [x] 14 test biên ở bảng trên, tất cả xanh — trong đó 5 test riêng cho `RouteSplitter` (0 điểm, 1 điểm, đúng ngưỡng gap, gap 6 phút, nhiều chuyến — 1 test hơn mức tối thiểu 4)
- [x] `LLM.md` §3 + Phụ lục A.1 cập nhật cùng commit

## Success Criteria

```bash
./gradlew :domain:test                                   # G2 — phải xanh
./gradlew :domain:test --tests '*ZoneEvaluatorTest*' -i  # đọc tên test, phải thấy đủ 4 case biên
grep -rn "import android\|import androidx" domain/src    # phải trả rỗng
```
- Chạy `:domain:test` **không cần** thiết bị, emulator hay Robolectric. Thời gian chạy < 5 giây.
- Mỗi luật trong bảng PRD §6 có ít nhất một test đọc hằng số đó, không hardcode lại số.

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| Hysteresis 30m quá nhỏ với GPS trong nhà (accuracy 50m) | Vẫn dội thông báo khi demo trong phòng | `LocationFilter` đã loại điểm `accuracy > 50m` trước khi tới `ZoneEvaluator`; nếu vẫn dội, tăng `ZONE_EXIT_BUFFER_M` — chỉ sửa một dòng vì hằng số tập trung |
| Haversine tự viết sai dấu / sai đơn vị | Mọi thứ phía trên sai theo | Test đối chiếu 3 cặp toạ độ có khoảng cách đã biết |
| Luật `distance < 10m` loại hết điểm khi đi bộ chậm | Polyline đứt đoạn | Test "đi bộ 5 km/h, nhịp 10s" → giữ lại đủ điểm (≈14m mỗi nhịp) |
| Tách `ZoneEventDeduper` bị hiểu là hai nơi áp luật | Ai đó thêm luật thứ hai ở service | `record()` vẫn là nơi duy nhất *gọi* deduper; ghi rõ trong KDoc và `LLM.md` |

## Security Considerations

- `:domain` không đọc/ghi gì, không log gì. Mọi log `FTD_EVENT` phát ra ở tầng gọi (`:data`), nơi
  có thể tắt theo build type — điều kiện cần cho gate G7.
- Test không được chứa toạ độ thật của bất kỳ ai; dùng toạ độ tròn số (`21.0, 105.8`).

## Next Steps

→ [phase-04](phase-04-permissions-and-tracking-service.md). Chặn: 04, 05, 06, 07, 08, 09, 10.
