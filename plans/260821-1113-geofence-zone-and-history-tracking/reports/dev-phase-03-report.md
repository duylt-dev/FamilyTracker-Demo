# Dev Report — Phase 03: Thuật toán tracking thuần ở `:domain` + unit test (G2)

Ngày: 2026-08-21 · Status: **completed**. Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`.

## Tóm tắt

7 file thuật toán thuần mới ở `domain/tracking/` (+ `TrackingConstants` bổ sung 11 hằng số), 5 use
case mới, 32 unit test JUnit thuần (`:domain:test` < 1s), xoá `RouteSessionAssembler` nợ kỹ thuật
từ phase-02, tách `ZoneEventDeduper` thành hàm thuần theo Q-D + viết `LLM.md` Phụ lục A.1 mới.

## Các hàm thuần đã viết — chữ ký + luật biên

| File | Chữ ký | Luật biên cài đặt |
|---|---|---|
| `GeoDistance.kt` (`internal`) | `haversineMeters(lat1, lng1, lat2, lng2): Double` | Bán kính Trái Đất `6_371_008.8` m (spec Step 2). Đối chiếu 3 cặp toạ độ: 1° vĩ độ tại xích đạo (≈111.195km, suy giảm đại số chính xác), 90° kinh độ tại xích đạo (≈10.007.557m, suy giảm đại số chính xác), Hà Nội→TP.HCM (≈1.137.806m, tính độc lập bằng Python cùng công thức để bắt lỗi sai dấu/đơn vị). |
| `LocationFilter.kt` | `accept(point, lastKept): FilterResult` (`Accept` \| `Reject(DropReason)`) | 3 luật **theo thứ tự**: `accuracy > MAX_ACCURACY_M` → `Reject(ACCURACY)`; so với **điểm được giữ** `distance < MIN_DISTANCE_M` → `Reject(DISTANCE)`; `dt>0 && speed > MAX_SPEED_KMH` → `Reject(SPEED)`. `lastKept=null` (điểm đầu tiên) luôn Accept (chỉ áp luật accuracy). |
| `ZoneEvaluator.kt` | `evaluate(point, zones, previouslyInside): ZoneEvaluation(events: List<ZoneCrossing>, insideAfter: Set<String>)` | Hysteresis: vào khi `!wasInside && d < radius`; ra khi `wasInside && d > radius + ZONE_EXIT_BUFFER_M`; còn lại giữ nguyên, không sinh event. `ZoneCrossing.shouldNotify` tôn trọng `notifyOnEnter`/`notifyOnExit` nhưng event LUÔN có mặt trong `events` (PRD §3.2 — Timeline không bao giờ thiếu dòng). |
| `RouteSplitter.kt` | `split(memberId, points, gapMs = SESSION_GAP_MS): List<TrackSession>` | Sắp theo `recordedAt`; cắt chuyến mới khi khoảng cách hai điểm liên tiếp **lớn hơn** `gapMs` (ngưỡng một chiều, `>` không phải `>=`). 0 điểm → rỗng, không ném. 1 điểm → 1 chuyến, `startedAt==endedAt`, distance 0. `id = "$memberId-<epochMilli điểm đầu>"` — ổn định giữa 2 lần gọi trên cùng dữ liệu kể cả khi input chưa sắp xếp (test dùng `shuffled`). |
| `RouteStats.kt` | `data class RouteStats(distanceMeters, durationMs, averageSpeedKmh)` với `companion.of(session): RouteStats` | `durationMs <= 0` (chuyến 1 điểm) → `averageSpeedKmh = 0.0`, **không chia cho 0**. |
| `ZoneEventDeduper.kt` | `shouldRecord(lastSameKey: ZoneEvent?, incoming, windowMs = EVENT_DEDUPE_WINDOW_MS): Boolean` | `lastSameKey == null` → `true`. Ngược lại `gapMs >= windowMs` (ngưỡng **inclusive** phía "giữ lại" — test riêng khoá đúng `gapMs == windowMs` → record). |
| `TrackingConstants.kt` | object, 12 `const val` | Mỗi hằng số một dòng comment "đổi thì hỏng gì", khớp PRD §6 từng dòng. |

5 use case mới: `ObserveZonesUseCase`, `SaveZoneUseCase` (chặn `count() >= MAX_ZONES` **trước** khi
gọi `zoneRepository.save()`), `DeleteZoneUseCase`, `ObserveRouteForDayUseCase`, `ObserveZoneTimelineUseCase`
— tất cả `operator fun invoke`, theo đúng pattern `PurgeOldHistoryUseCase` đã có từ phase-02.

## Bảng test — tên test → trường hợp biên khoá lại

32 test, 0 fail, 0 skip. `:domain:test` chạy < 1 giây (không thiết bị, không Robolectric).

| # | Test class | Test name | Trường hợp biên |
|---|---|---|---|
| 1–5 | `GeoDistanceTest` | `same point is zero distance` | Haversine(A,A) = 0 |
| | | `1 degree of latitude at the equator is ~111195 m` | Cặp toạ độ đã biết #1 (suy giảm đại số) |
| | | `90 degrees of longitude at the equator is ~10007557 m` | Cặp toạ độ đã biết #2 |
| | | `Hanoi to Ho Chi Minh City is ~1137806 m` | Cặp toạ độ đã biết #3 (real-world, độc lập Python) |
| | | `distance is symmetric regardless of argument order` | `d(A,B) == d(B,A)` |
| 6–12 | `LocationFilterTest` | `first point with no lastKept is accepted...` | `lastKept=null` |
| | | `accuracy worse than MAX_ACCURACY_M is rejected, indoor GPS case` | **accuracy=200m trong nhà → Reject(ACCURACY)** (bảng phase-03) |
| | | `accuracy exactly at MAX_ACCURACY_M is still accepted, boundary is exclusive` | `accuracy == 50` không bị loại (`>` không phải `>=`) |
| | | `60 identical points while standing still keep only the first, reject the other 59 as DISTANCE` | **60 điểm trùng nhau → 1 giữ, 59 Reject(DISTANCE)** (bảng phase-03) |
| | | `distance rule compares against the last KEPT point, not the last seen point` | "Bẫy" Key Insight #3 — đi chậm 9m/nhịp vẫn tích luỹ đúng so với điểm ĐƯỢC GIỮ |
| | | `GPS jump of 5km within 1 second is rejected as SPEED` | **GPS nhảy 5km/1s → Reject(SPEED)** (bảng phase-03) |
| | | `walking at 5kmh with a 10s cadence keeps points, about 14m per tick` | Risk Assessment mitigation — đi bộ chậm không bị loại hết |
| 13–18 | `ZoneEvaluatorTest` | `standing exactly at the radius produces no event, from outside` | **d == R, từ ngoài → không sự kiện** (bảng phase-03) |
| | | `standing exactly at the radius produces no event, from inside` | **d == R, từ trong → không sự kiện** (bảng phase-03, nửa còn lại) |
| | | `standing at the edge oscillating within radius plus-minus 5m for 30 points fires exactly one ENTER and zero EXIT` | **30 điểm dao động R±5m → đúng 1 ENTER, 0 EXIT** (US-26, bảng phase-03) |
| | | `entering then genuinely leaving at radius plus 40m fires one ENTER and one EXIT` | **vào rồi ra thật (d=R+40m) → 1 ENTER, 1 EXIT** (bảng phase-03) |
| | | `notifyOnEnter false still records the ENTER event but marks shouldNotify false, PRD 3-2` | PRD §3.2 — event luôn ghi dù tắt thông báo |
| | | `two zones are evaluated independently in the same tick` | Nhiều zone cùng lúc không giao thoa nhau |
| 19–23 | `RouteSplitterTest` | `zero points returns an empty list without throwing` | **0 điểm → rỗng, không ném** (bảng phase-03) |
| | | `one point is exactly one session with startedAt equal to endedAt and zero distance` | **1 điểm → 1 chuyến, distance 0** (bảng phase-03) |
| | | `two points exactly SESSION_GAP_MS apart stay in the same session, threshold is one-sided` | **đúng ngưỡng gap → 1 chuyến** (bảng phase-03) |
| | | `two points 6 minutes apart split into 2 sessions, US-30` | **gap 6 phút → 2 chuyến** (US-30, bảng phase-03) |
| | | `7 points across gaps produce 3 sessions, ordered ascending, with stable ids across calls` | **7 điểm → 3 chuyến, id ổn định** kể cả khi gọi lại với input xáo trộn (bảng phase-03) |
| 24–25 | `RouteStatsTest` | `single-point session has zero distance, zero duration, zero speed, no division by zero` | **chuyến 1 điểm → 0/0/0, không chia 0** (bảng phase-03) |
| | | `a session covering 1 hour and 36km averages 36kmh` | Trường hợp thường — tốc độ trung bình tính đúng |
| 26–29 | `ZoneEventDeduperTest` | `no previous event with the same key always records` | `lastSameKey=null` |
| | | `same key 30s apart is deduped, gap under EVENT_DEDUPE_WINDOW_MS` | **cách 30s → false** (US-25, bảng phase-03) |
| | | `same key 90s apart is recorded, gap past EVENT_DEDUPE_WINDOW_MS` | **cách 90s → true** (US-25, bảng phase-03) |
| | | `gap exactly equal to the window is recorded, threshold is inclusive on this side` | Ngưỡng `>=` — biên đúng 60000ms |
| 30–32 | `SaveZoneUseCaseTest` | `saving when under the limit delegates to the repository and returns its result` | Trường hợp thường — 99 zone, lưu OK |
| | | `saving the 101st zone fails validation and never calls the repository, US-21` | **101 zone → Failure, repository KHÔNG bị gọi** (US-21, bảng phase-03) |
| | | `saving when already exactly at MAX_ZONES also fails, boundary is inclusive` | Biên `count() == MAX_ZONES` cũng chặn |

Tổng: 14/14 hàng bảng test biên phase-03 đều có ít nhất 1 test tên rõ ràng khớp mô tả (đánh dấu
**in đậm** ở trên) — RouteSplitter có 5 test riêng (yêu cầu tối thiểu 4). Mỗi hằng số PRD §6 được
đọc qua `TrackingConstants.X` trong test, không hardcode lại số (ví dụ
`base.plusMillis(TrackingConstants.EVENT_DEDUPE_WINDOW_MS)`).

### Floating-point ở test "d == R" — đã kiểm chứng thật, không chỉ tính bằng tay

`pointDueNorth` dựng điểm bằng công thức nghịch đảo (`Math.toDegrees(distance / EARTH_RADIUS_M)`)
cùng hằng số Trái Đất với `GeoDistance`, nên khi `ZoneEvaluator` tính lại khoảng cách, sai số làm
tròn double có thể lệch ~1e-10m theo một hướng bất kỳ. Đã chạy thật `:domain:test` (không chỉ suy
luận) — cả 2 test "exactly at radius" đều pass; xem output bên dưới. Rủi ro còn lại: nếu chạy trên
JVM/CPU khác có thể lệch hướng làm tròn khác 1e-10m và test này có thể flaky — biên độ này nhỏ hơn
độ chính xác GPS hàng triệu lần nên không ảnh hưởng hành vi thật, nhưng nói thẳng ra đây.

## `RouteSessionAssembler` — đã xoá, chuyển đi đâu

- **Xoá:** `data/repository/RouteSessionAssembler.kt` (58 dòng, `internal object` tạm từ phase-02).
- **`TrackingRepositoryImpl.observeRoute()`**: `RouteSessionAssembler.assemble(memberId, points)` →
  `RouteSplitter.split(memberId, entities.map { it.toDomain() })`.
- **`ZoneEventRepositoryImpl.record()`**: logic tự đọc `latestForKey` rồi tự so sánh `Duration` →
  đọc `latestForKey`, `.toDomain()`, gọi `ZoneEventDeduper.shouldRecord(latest, event)`.
- Hai hằng số hardcode cục bộ (`SESSION_GAP_MS = 300_000L` trong `RouteSessionAssembler`,
  `EVENT_DEDUPE_WINDOW_MS = 60_000L` trong `ZoneEventRepositoryImpl`) đã xoá, giờ đọc từ
  `TrackingConstants` duy nhất.
- Verify: `grep -rn "RouteSessionAssembler" --include="*.kt" .` → rỗng (chỉ còn nhắc tên trong
  bảng "Sai lệch" của `LLM.md` §13 Fixed #5 và `dev-phase-02-report.md` cũ, đúng như yêu cầu).
- Regression: `data/src/androidTest/.../ZoneEventDedupeTest.kt` (30s→dedupe, 90s→giữ, viết ở
  phase-02) **không sửa gì**. Đã chạy lại thật trên `emulator-5554` sau khi `ZoneEventRepositoryImpl`
  đổi sang gọi `ZoneEventDeduper`: `./gradlew :data:connectedDebugAndroidTest` → **9/9 pass**
  ("Starting 9 tests on Pixel_10_Pro_XL(AVD) - 17" / "Finished 9 tests... BUILD SUCCESSFUL in 9s").
  Ngưỡng 60s không đổi hành vi.

Đã đọc kỹ `RouteSessionAssembler` trước khi xoá theo đúng khuyến nghị của test-phase-02-report.md
("diff against new RouteSplitter before deletion") — không có logic drift: cùng công thức
Haversine (đổi bán kính Trái Đất từ `6_371_000.0` tạm sang `6_371_008.8` đúng spec phase-03 Step
2, sai lệch 0.0001%, không ảnh hưởng thực tế), cùng luật cắt theo `SESSION_GAP_MS`, cùng công thức
`id`.

## Q-D — `ZoneEventDeduper` hiện thực hoá ra sao

- Hàm thuần `domain/tracking/ZoneEventDeduper.kt`, test riêng
  `domain/src/test/.../tracking/ZoneEventDeduperTest.kt` (4 test, không thiết bị).
- *Nơi áp dụng* vẫn đúng một chỗ: `ZoneEventRepositoryImpl.record()` — không có chỗ thứ hai nào
  gọi hàm này.
- `LLM.md` cập nhật cùng lúc: §3 (thêm `ZoneEventDeduper.kt` vào cây `domain/tracking/`), §8.1
  (đoạn "vì sao phải khử trùng lặp" trỏ sang hàm thuần + Phụ lục A.1), và **Phụ lục A** mới (mục
  A.1) — phần này **chưa từng tồn tại** trong `LLM.md` trước phase-03 dù `plan.md`/phase-03 đã
  nhắc "Phụ lục A.1" như thể đã có; giờ đã viết thật ở cuối `LLM.md`, sau §14.
- `plan.md`: Q-D chuyển từ bảng "Câu hỏi còn mở" sang dòng "Đã đóng".

## Output thật — chạy đầy đủ theo "Định nghĩa xong"

```
$ export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
$ ./gradlew :domain:test
BUILD SUCCESSFUL in 2s
4 actionable tasks: 4 executed

$ ./gradlew :domain:test --tests '*ZoneEvaluatorTest*' -i
BUILD SUCCESSFUL — 6/6 test trong ZoneEvaluatorTest pass (tên đọc từ XML, xem bảng test ở trên: đủ
4+ trường hợp biên: d==R từ ngoài, d==R từ trong, dao động 30 điểm, vào-ra thật, + 2 test bổ sung)

$ ./gradlew test
BUILD SUCCESSFUL in 5s — :domain:test, :ui:test (1/1), :data:test (NO-SOURCE, đúng phạm vi),
:app:test (KoinModulesTest vẫn pass, không đổi)

$ ./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
1   # G6 — khớp baseline (ENV-BRIEFING.md §8)

$ ./gradlew assembleRelease
BUILD SUCCESSFUL in 12s

$ grep -rn "RouteSessionAssembler" --include="*.kt" .
(rỗng)

$ grep -rn "import android\|import androidx" domain/src
(rỗng)
```

### Cài đặt + logcat thật (kiểm chứng bổ sung, không bắt buộc bởi phase-03 nhưng đã đụng `:data`)

```
$ adb -s emulator-5554 uninstall com.example.pion.family.tracker.demo
$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Success
$ adb -s emulator-5554 shell am start -n .../.MainActivity
$ adb -s emulator-5554 logcat -d -s FTD_EVENT
08-21 15:26:02.918 15477 15492 D FTD_EVENT: purge_completed deletedPoints=0 deletedEvents=0
$ adb -s emulator-5554 logcat -d | grep -iE "FATAL EXCEPTION|AndroidRuntime: FATAL"
(rỗng — không crash)

$ ./gradlew :data:connectedDebugAndroidTest
Starting 9 tests on Pixel_10_Pro_XL(AVD) - 17
Finished 9 tests on Pixel_10_Pro_XL(AVD) - 17
BUILD SUCCESSFUL in 9s   # xác nhận ZoneEventDeduper refactor không đổi hành vi 60s-dedupe
```

## Tests Status

- Type check / compile: **pass** (`:domain:compileKotlin`, `:data:compileDebugKotlin`, toàn dự án qua `assembleRelease`)
- Unit tests: **pass** — 32/32 `:domain:test`, toàn dự án `./gradlew test` xanh
- G2 (gate của phase này): **pass** — 32 test, tất cả 14 hàng bảng biên đều khoá, chạy < 1s, không thiết bị
- Instrumented tests (regression, không bắt buộc bởi phase-03): **pass** — 9/9 `:data:connectedDebugAndroidTest` trên `emulator-5554`, xác nhận refactor `ZoneEventDeduper` không đổi hành vi
- G6: **pass** — 1 warning, khớp baseline `--no-configuration-cache`
- Module boundary: **pass** — `domain/src` không `import android`/`androidx`

## Sai lệch so với file phase (đầy đủ, có lý do)

1. **`RouteSplitter.split` nhận `memberId` làm tham số đầu, không khớp chữ ký rút gọn ở phase-03
   Key Insight #5 (`split(points, SESSION_GAP_MS)`).** Bắt buộc: `TrackSession.memberId` phải
   được điền, và `LocationPoint` **cố ý không mang `memberId`** (đã ghi trong KDoc model từ
   phase-02) — không có cách nào suy ra memberId từ danh sách điểm. Giữ đúng convention của
   `RouteSessionAssembler.assemble(memberId, points)` đang bị thay thế. `gapMs` là tham số có
   default `TrackingConstants.SESSION_GAP_MS`, khớp ý "gapMs" trong Key Insight #5.
2. **`ZoneEvaluator.evaluate` trả `events: List<ZoneCrossing>`, không phải `List<ZoneEvent>`.**
   `ZoneCrossing` là type mới (`zoneId, zoneName, type, shouldNotify`) vì hàm thuần không biết
   `memberId`/`id`/`source` của một `ZoneEvent` đầy đủ — những trường đó chỉ tầng `:data` biết.
   Đã ghi rõ trong `LLM.md` §8.2 cùng commit.
3. **`SaveZoneUseCase` chặn `MAX_ZONES` cho MỌI lần `save()`, không phân biệt tạo mới với sửa.**
   US-21 nói "bị chặn khi TẠO quá 100 zone", nhưng `ZoneRepository` không có `exists(id)` để use
   case phân biệt. Ghi vào `LLM.md` §13 Open #4 (mới) — chỗ còn hở, sẽ lộ ra khi phase-06 dựng
   `ZoneEditorScreen` cho phép sửa zone đã có.
4. **Thêm `GeoDistanceTest.kt`** không nằm trong danh sách "Tạo" của phase-03 Related Code Files,
   nhưng Implementation Step 2 + Risk Assessment đòi hỏi rõ ràng ("Test đối chiếu 3 cặp toạ độ").
   Bổ sung file test riêng thay vì nhét vào `LocationFilterTest`/`ZoneEvaluatorTest` cho rõ ràng,
   không phải sai lệch thực chất — chỉ là danh sách file gốc thiếu sót.
5. **Bán kính Trái Đất đổi từ `6_371_000.0` (số tạm trong `RouteSessionAssembler`) sang
   `6_371_008.8`** đúng theo phase-03 Implementation Step 2 ("bán kính Trái Đất 6 371 008.8 m") —
   không phải sai lệch, ghi ở đây để rõ ràng con số khác `RouteSessionAssembler` cũ một chút
   (0.0001%, không ảnh hưởng hành vi).

## Việc còn dở / chưa làm — nói thẳng

- **Test "d == R" (`ZoneEvaluatorTest`) phụ thuộc floating-point round-trip ~1e-10m** — đã chạy
  pass thật trên máy này, nhưng lý thuyết có rủi ro flaky cực nhỏ trên JVM/CPU khác (nói ở mục
  test bên trên). Không sửa vì biên độ nhỏ hơn độ chính xác GPS hàng triệu lần; nói thẳng ra đây
  theo yêu cầu thay vì im lặng.
- **`SaveZoneUseCase` không phân biệt tạo/sửa ở giới hạn MAX_ZONES** — xem Sai lệch #3, đã ghi
  `LLM.md` §13 Open #4, chưa sửa vì `ZoneEditorScreen` (nơi duy nhất gọi "sửa") chưa tồn tại.
- Độ phủ nhánh: mọi nhánh trong `LocationFilter`/`ZoneEvaluator`/`RouteSplitter`/`RouteStats`/
  `ZoneEventDeduper` đều có ít nhất 1 test trực tiếp; không phát hiện nhánh hở nào ngoài 2 điểm
  đã nói ở trên.

## Docs impact

**Major.** `LLM.md`: §3 (cây `domain/tracking/` đầy đủ 6 file + `usecase/` cập nhật), §8.1–§8.2
(mô tả khớp hành vi thật), §11 (bố cục test domain/tracking + usecase), §13 (Open #3 giữ nguyên,
Open #4 mới — `SaveZoneUseCase`, Fixed #5 mới — `RouteSessionAssembler` xoá), **Phụ lục A mới**
(mục A.1 — `ZoneEventDeduper`, viết lần đầu vì trước đây được nhắc tới nhưng chưa từng tồn tại).
`plan.md`: phase-03 → completed, Q-D chuyển sang Đã đóng. Phase-03 file: Status → completed, 9/9
Todo tick.

Không có câu hỏi còn treo ngoài 2 điểm "chưa làm" đã nói thẳng ở trên.
