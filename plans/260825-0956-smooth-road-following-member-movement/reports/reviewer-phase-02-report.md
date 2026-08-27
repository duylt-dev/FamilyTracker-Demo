# Reviewer Report — Phase 02: Bước đi bám polyline, bearing thật, spawn một lần

**Ngày:** 2026-08-25 · **Baseline:** `HEAD` = `cdb27a8` ·
**Vào sau:** `dev-phase-02-report.md` → `simplifier-phase-02-report.md` → `tester-phase-02-report.md`
**Kết luận: ĐÓNG phase 02** (sau khi đã sửa F-1, F-2, F-4)

---

## Kết luận

**ĐÓNG.** Hạt nhân của phase — bảo toàn đỉnh — đúng, được khoá bằng hai assertion độc lập và một
mutation thật. `:domain` sạch (không Android/Compose/coroutine/suspend trong mọi file phase-02).
Bất biến ENTER/EXIT sống sót ở cả 150m lẫn 50m. `DWELL_TICKS`/`MEMBER_ROAM_INTERVAL_MS` không bị
đụng. Hai hằng số mới có nguồn + dòng "ảnh hưởng khi đổi", tỉ lệ truy nguyên lên **14/21**.

Ba khuyết tật chặn đã sửa trong lần review này: NFR-4 bị vi phạm **và mìn đã nổ** (F-1), một ca test
đạt vô điều kiện che chính vụ nổ đó (F-2), và guard kiến trúc của phase-01 đang canh nhầm ranh giới
(F-4 — nó đỏ ngay trên tay tôi giữa phiên làm việc).

```
./gradlew :domain:test :data:test :ui:test :app:test :app:assembleDebug --no-configuration-cache
BUILD SUCCESSFUL — tests=250  failures=0  errors=0  skipped=0
(:domain 125 · :data 43 · :ui 81 · :app 1)
:domain:test  1s wall / 0.143s JUnit   (ngưỡng 5s, NFR-2/S10)
F5 non-regression: RouteBlueprintTest + StartSimulationUseCaseTest — BUILD SUCCESSFUL
```

**Số test bàn giao không phải 244.** Đếm từ `TEST-*.xml` lúc tôi nhận việc: **247**
(`:domain` 124, `:data` 41). `tester` thêm 3 ca (guard cross-case, tách S9, ca NFR-4) nhưng report
vẫn ghi con số 244 của `simplifier`. Sau review: 250.

---

## Bảng phát hiện

| # | Mức | Vị trí | Vấn đề | Đã sửa? |
|---|---|---|---|---|
| F-1 | **Critical** | `data/location/MemberMovementSimulator.kt` (cũ: dòng 111, 198) | Vi phạm NFR-4, và **mìn đã nổ rồi** — không phải "chưa nổ" như cả 3 report kết luận | **ĐÃ SỬA** |
| F-2 | **High** | `MemberMovementSimulatorTest` ca `NFR-4 test spawn branch accesses Location distanceBetween in JVM test` | Không assertion nào; `catch` nuốt đúng cái exception chứng minh F-1. Đạt vô điều kiện | **ĐÃ SỬA** |
| F-3 | — | `RouteGeometryGuard.kt:37` + `RouteGeometryGuardTest` | **Không phải khuyết tật.** Ca `tester` thêm là thật, tôi tự mutation lại: ĐỎ | Không cần |
| F-4 | **Medium** | `data/test/.../RealGpsNoSnapArchitectureTest.kt` | Quét cả `data/location/`, gồm 2 file MÔ PHỎNG bắt buộc phải bám đường. KDoc khẳng định sai vị trí file | **ĐÃ SỬA** (§13 Open #15 → Fixed #25) |
| F-5 | Low | `MemberRoamerTest` ca `the roamer with a synthetic path does not dither when entering a zone` | "Tách S9" không chữa được vấn đề `simplifier` nêu: Part B vẫn là bản SAO YẾU HƠN của test bất biến | Không |
| F-6 | Low | `MemberRoamer.tick` nhánh spawn | Mẫu spawn ghi ra `bearingDegrees = 0f` và `speedMps = 0f` — mâu thuẫn cách phát biểu tuyệt đối của FR-3/S3 | Không |
| F-7 | Low | `PolylineFollowerTest:38` | `return@repeat` là `continue`, không phải `break` (đã do `simplifier` nêu) | Không |
| F-8 | Low | PRD delta §4.2 | Dẫn `MemberRoamerLapTimeTest` — file này KHÔNG tồn tại | Không |
| F-9 | Low | `PolylineFollowerTest` ca S1 | S1 đòi "200 nhịp, polyline ≥ 3 đoạn"; test chạy 20 nhịp trên polyline 2 đoạn | Không |
| F-10 | Low | `MemberMovementSimulatorTest` ca S3 | `none { bearingDegrees == 0f }` sẽ báo sai nếu một mẫu đi ĐÚNG hướng bắc (bearing thật = 0.0) | Không |

---

## F-1 — NFR-4: mìn KHÔNG phải "chưa nổ", nó đã nổ và đang bị nuốt

Cả ba report đều kết luận cùng một câu: *"hôm nay suite xanh CHỈ vì nhánh spawn chưa có ca test nào
chạm tới"*. Câu đó đúng ở thời điểm `simplifier` viết, và **sai** ở thời điểm bàn giao cho tôi — vì
`tester` sau đó đã thêm một ca CÓ chạm nhánh spawn.

Đo trực tiếp: gỡ lớp `catch` khỏi ca đó, không đổi gì khác.

```
MemberMovementSimulatorTest > NFR-4 test spawn branch accesses Location distanceBetween in JVM test FAILED
  java.lang.RuntimeException: Method distanceBetween in android.location.Location not mocked.
    at android.location.Location.distanceBetween(Location.java)
    at com.example.pion.family.tracker.demo...MemberMovementSimulator...
41 tests completed, 1 failed
```

Tức là NFR-4 đang bị vi phạm **ngay bây giờ**, trong chính suite đang xanh. Cái giữ nó xanh là bốn
dòng `try/catch` trong test, không phải kiến trúc.

### Cách sửa — đúng câu trả lời NFR-4 viết sẵn

NFR-4: *"mọi phép đo hình học **đã có sẵn trong `RoamState`**, `:data` không phải tính lại."*

`RoamStep.Move` nay mang `spawnDistanceMeters: Double?`, khác `null` **đúng** ở nhịp `hasSpawned`
chuyển `false → true`, tính bằng `GeoDistance.haversineMeters` trong `MemberRoamer`. `:data` đọc và
ghi log; `import android.location.Location` biến mất khỏi `MemberMovementSimulator.kt`.

Ba lựa chọn `simplifier` liệt kê, và vì sao không lấy cái nào:

| | Vì sao không |
|---|---|
| (a) bỏ `distanceM=` khỏi log | Mất số liệu QA-SRM-09/11 đếm spawn trên bản debug |
| (b) `returnDefaultValues = true` cho `:data` | Biến MỌI API Android chưa mock thành `0`/`null` **im lặng** trong cả module — đúng loại "chết im lặng" phase-01 vừa dạy. Đắt nhất, trông rẻ nhất |
| (c) haversine thuần Kotlin trong `:data` (`simplifier` khuyến nghị) | Giữ được cả log lẫn NFR-4, **nhưng vẫn để phép đo hình học ở `:data`** — đúng thứ NFR-4 cấm — và đẻ thêm một bản sao thuật toán (§13 Open #12). Mở `GeoDistance` thành public cũng cùng điểm yếu đó |

Bản sửa đã chọn giữ được cả ba thứ: log còn nguyên, `:data` sạch API Android, và **không** bản sao
thuật toán nào mới.

### Chứng minh

```
# đưa Location.distanceBetween trở lại đúng nhánh spawn
MemberMovementSimulatorTest > NFR-4 the spawn branch runs in a pure JVM test ... FAILED
MemberMovementSimulatorTest > after the single spawn, no later tick jumps across the planet again FAILED
43 tests completed, 2 failed
# khôi phục -> BUILD SUCCESSFUL, import android.location vắng mặt
```

Thêm hai ca ở `:domain` khoá phía còn lại (nếu không, xoá dòng gán ở `MemberRoamer` sẽ làm log
`sim_spawn` **im lặng** biến mất và QA-SRM-09/11 mất cách đếm):
`spawnDistanceMeters` phải khác `null` và bằng đúng khoảng cách thật ở nhịp spawn, và phải `null` ở
một nhịp đi bình thường.

---

## F-2 — ca test đạt vô điều kiện, lần thứ hai liên tiếp

```kotlin
repeat(10) {
    try { simulator.tickOnce() }
    catch (e: RuntimeException) {
        if ("distanceBetween" in (e.message ?: "")) return@runTest   // mìn nổ  -> ĐẠT
        throw e
    }
}                                                                     // mìn không nổ -> ĐẠT
```

Không assertion nào. Tệ hơn: nó **bắt đúng** exception chứng minh khuyết tật rồi báo thành công, và
tên ca (`NFR-4 …`) khiến người đọc tin NFR-4 đang được canh.

Thay bằng hai ca thật, không `try/catch`:
1. `NFR-4 the spawn branch runs in a pure JVM test and lands the member next to the zone` — chạy
   đúng nhánh spawn và assert thành viên đã được thả cạnh zone (dung sai 0.05° ≈ 5.5km, đủ để phân
   biệt TP.HCM với Mountain View mà không khoá vào hướng spawn ngẫu nhiên).
2. `after the single spawn, no later tick jumps across the planet again` — FR-4/QA-SRM-09 ở `:data`,
   nơi `roamStates` thật sự sống qua các nhịp.

---

## F-3 — `RouteGeometryGuard`: tự xác minh, và `tester` đúng

Không tin theo report. Xoá dòng `if (entryCrossings > MAX_ALLOWED_CROSSINGS || exitCrossings >
MAX_ALLOWED_CROSSINGS) return false`, chạy `:domain:test`:

```
RouteGeometryGuardTest > an ENTER leg that crosses entry once but bounces at exit buffer is rejected (cross-case guard) FAILED
124 tests completed, 1 failed
```

Khôi phục → xanh, `git diff` sạch. **Lỗ `simplifier` T3 phát hiện đã được đóng thật.** Kiểm lại số
học của ca đó: distances `200,160,185,160,90`; biên 150 → `entryCrossings = 1`; biên 180 →
`exitCrossings = 3`. Comment trong test ghi "2" thay vì 3 — sai một con số, không ảnh hưởng kết
luận (cả hai đều `> 1`).

---

## F-4 — guard kiến trúc phase-01 canh nhầm ranh giới, và nó đỏ ngay trên tay tôi

`ls` xác nhận `data/location/` chứa `MemberMovementSimulator.kt` và `SimulatedLocationSource.kt` —
**code mô phỏng**, thứ BẮT BUỘC bám đường từ phase-02 (US-41). KDoc của test khẳng định simulator
"nằm NGOÀI thư mục này": sai sự thật.

Đây không phải rủi ro lý thuyết. Khi sửa F-1 tôi viết lại KDoc của `MemberMovementSimulator` và gọi
thẳng tên `PolylineFollower` (dev cũ phải **viết vòng** để né — chính là Open #15):

```
RealGpsNoSnapArchitectureTest > no file under data location references any road-snapping concept FAILED
```

Cách sửa rẻ nhất tại chỗ đó là **nới danh sách cấm** — tức giết guard cho cả đường GPS **thật**,
đúng thứ nó sinh ra để bảo vệ. Đó là lý do F-4 phải được sửa trong phase này chứ không phải phase 04.

### Bản sửa

- Quét một **danh sách tường minh bốn file GPS thật**: `FusedLocationSource.kt`,
  `LocationPointProcessor.kt`, `LiveSelfLocation.kt`, `LocationTrackingService.kt`.
- Ba file được miễn đều kèm **lý do tại chỗ** (2 file mô phỏng + `TrackingNotification.kt` không
  nằm trên đường dữ liệu vị trí).
- Ca thứ hai `every file under data location is classified as real-GPS or explicitly exempt` đối
  chiếu danh sách với đĩa. Đây là phần quan trọng: một danh sách tường minh là cách rất gọn để vô
  hiệu hoá guard mà không ai thấy, nên phạm vi phải không bao giờ **âm thầm** hẹp lại.
- `violationsIn` nay `check(file.isFile)` — đổi tên một file GPS thật làm ca đỏ chứ không lặng lẽ
  quét 0 dòng.

### Hai mutation, cả hai ĐỎ

```
(a) chèn `// PolylineFollower` vào FusedLocationSource.kt
    -> no file on the real GPS path references any road-snapping concept FAILED
(b) thêm data/location/ScratchProbe.kt (chưa phân loại)
    -> every file under data location is classified as real-GPS or explicitly exempt FAILED
```
Cả hai khôi phục sạch (`git diff --numstat` rỗng, file probe đã xoá).

`LLM.md` §13 **Open #15 → Fixed #25**, và §11 mô tả lại phạm vi guard.

---

## S1→S10: khoá bằng test, hay chỉ được khẳng định

| # | Khoá bằng gì | Phán quyết |
|---|---|---|
| S1 | `PolylineFollowerTest.every sample lands exactly on the polyline…` — `distanceToNearestSegment < 1e-6` | **Đạt về bản chất**, hình thức lệch spec: 20 nhịp trên polyline 2 đoạn, không phải 200 nhịp / ≥3 đoạn (F-9) |
| S2 | Cùng test + assertion thứ hai trên `cursors`: không cặp cursor liên tiếp nào chứa một đỉnh | **Đạt, và đây là assertion đúng.** Assertion "mọi điểm nằm trên đường" MỘT MÌNH không bắt được cắt góc — comment trong test nói đúng điều đó |
| S3 | `MemberMovementSimulatorTest.no recorded point has bearingDegrees 0f…` | **Đạt.** Dwell không ghi điểm nên "trừ điểm đứng yên" được xử lý bằng cấu trúc. Xem F-6/F-10 cho hai chỗ mong manh |
| S4 | `MemberRoamerTest.across many ticks, exactly one step covers more than 2x STEP_METERS` | **Đạt** |
| S5 | `…repositioned OUTSIDE the zone, not into it` — `distance > radiusMeters` **và** `< MAX_WALK_M` | **Đạt** |
| S6 | `restarting the simulation 3 times each spawns exactly once` | **Đạt.** Kiểm lại bằng đọc code: `hasSpawned` được giữ qua cả 4 nhánh `copy`/dựng lại state, không có đường rò |
| S7 | 2 ca: `a full roam cycle…` (150m) + `…also holds at the minimum zone radius (50m)` | **Đạt.** Diff của ca bất biến so với `HEAD`: chỉ dòng gọi vòng lặp đổi `MemberRoamer.tick(...)` → `advance(...)`; tên, assertion, `TICKS_FOR_SEVERAL_CYCLES = 200` y nguyên |
| S8 | `each ENTER of the same zone is separated by more than the dedupe window, in milliseconds` | **Đạt.** Đếm nhịp × `MEMBER_ROAM_INTERVAL_MS`, đúng đơn vị S8 đòi |
| S9 | `RouteGeometryGuardTest` (8 ca) + `MemberRoamerTest` phần A | **Đạt phần guard.** Phần "rơi về SyntheticPath, không dội" vẫn chưa phải ca thật — F-5 |
| S10 | `:domain:test` 1s wall / **0.143s** JUnit | **Đạt**, dư rất nhiều |

---

## Ranh giới cứng và luật kiến trúc

| Luật | Kiểm bằng gì | Kết quả |
|---|---|---|
| `:domain` không Android/Compose | `grep -rn "import android\|import androidx" domain/src/main/` | **Rỗng** |
| `:domain` phase-02 không coroutine/`suspend` | Đọc 8 file mới/sửa của phase-02 | **Sạch.** (Các `suspend` còn lại trong `:domain` đều ở `repository/`+`usecase/`, có từ trước, không thuộc phase này) |
| `DWELL_TICKS` / `MEMBER_ROAM_INTERVAL_MS` không đụng | `git diff HEAD` | **Không đổi.** `DWELL_TICKS` vẫn suy từ `EVENT_DEDUPE_WINDOW_MS`, 30 nhịp × 2 500 ms = 75s > 60s |
| 2 hằng số mới có nguồn + ảnh hưởng khi đổi | Đọc KDoc | **Đủ cả hai.** Nguồn = PRD delta §4.2; mỗi cái có "Lớn hơn → / Nhỏ hơn →" |
| Tỉ lệ truy nguyên 12/19 → **14/21** | §3 + §13 Open #7 | **Đúng, không xấu đi** (NFR-3) |
| Không log `lat`/`lng` | grep `FtdLog` trong mọi file phase-02 | **3 lời gọi, không cái nào có toạ độ**: `member_roam_started intervalMs=`, `member_roam_error`, `sim_spawn memberId= distanceM=` |
| `:domain` không log gì | grep `println\|Log\.\|FtdLog` trong `domain/src/main/` | **CLEAN** |
| Không bật `returnDefaultValues`, không Robolectric | `git status` trên `*.gradle*` / `*.toml` | **Không file build nào bị đụng** |
| F5 không vỡ | `RouteBlueprintTest` + `StartSimulationUseCaseTest` | **BUILD SUCCESSFUL.** `RouteBlueprint.kt` `git diff` rỗng |
| Bất biến ENTER/EXIT (`decisions.md` §C4) | Diff từng dòng ca bất biến + chạy lại | **Giữ** |

---

## Ba việc "cố ý không đụng" của `simplifier` — chốt

1. **`wanderTarget` nằm ở `MemberRoamerGeometry.kt` dù nó dựng `RoamTarget` và đọc
   `MemberRoamer.WANDER_RADIUS_M`.** Chốt: **giữ**. Đưa về `MemberRoamer.kt` đẩy file đó lên ~210
   dòng, vỡ đúng luật 200 dòng mà việc tách sinh ra để tuân thủ. `simplifier` gọi đúng: đánh đổi bị
   ép, không phải nhầm chỗ.
2. **`FULL_CIRCLE_DEGREES = 360.0` khai hai lần cùng package.** Chốt: **giữ**. Gộp lại buộc
   `GeoBearing` (tiện ích dùng chung) phụ thuộc một file riêng của `MemberRoamer`, hoặc phải tạo
   file hằng số mới mà phase cấm. Cái giá thật là bản `internal` top-level có tên rất chung —
   `MemberRoamer.kt` dùng nó không qualifier nên đọc không ra nó ở đâu. Ghi lại, không sửa.
3. **`ParametrizedPath`/`SyntheticPath` public.** Chốt: **đúng, bắt buộc**. `simplifier` đã đo bằng
   compiler (T1) và sửa lại lý do sai trong KDoc của dev. `internal` của Kotlin là biên theo MODULE
   Gradle — cùng bài học §13 Fixed #12.

---

## Độ trung thực của ba report trước

### `dev-phase-02-report.md` — **cao**

Mục "Deviations" tự khai đủ 6 chỗ đi chệch phase file, kèm lý do, **gồm cả** phát hiện guard
kiến trúc sai (mục 5) mà nó không có quyền sửa — và nó ghi thành `LLM.md` §13 Open #15 thay vì giấu.
Mutation Step 10 là mutation thật trên mã sản phẩm (`PolylineFollower.advance`), có log đỏ đầy đủ,
2 ca đỏ đúng như dự đoán. Con số S10 (1s, 0.134s) khớp với đo lại của tôi.

Một chỗ nói quá: "S9 … `MemberRoamerTest` xanh" — phần sau của ca S9 không kiểm thứ nó nói (F-5),
nhưng `simplifier` đã bắt được và ghi rõ, nên đây là sai sót được sửa trong dây chuyền.

### `simplifier-phase-02-report.md` — **cao nhất, và là report tốt nhất tôi đã đọc trong plan này**

Ba thí nghiệm T1/T2/T3 đều là **đo**, không phải suy luận, đều có log, đều khôi phục nguyên văn.
T2 phát hiện F-1 ở mức "quả mìn đã gài"; T3 phát hiện lỗ guard mà 122 test không giữ. Nó cũng bắt
được vấn đề S9 (F-5) mà `dev` và `tester` đều bỏ qua. Mục "cố ý không đụng" #1 — **từ chối** gộp
DRY trong `MemberRoamerTest.kt` vì đó đúng là file cần đọc từng dòng khi review — là một quyết định
đúng và hiếm: nó đặt giá trị của khâu review lên trên giá trị của việc gọn code.

Một chỗ duy nhất chưa tới: T2 kết luận "chưa nổ vì nhánh spawn chưa có ca nào chạm". Đúng tại thời
điểm đó; nhưng khuyến nghị đi kèm — (c) haversine trong `:data` — vẫn để phép đo hình học ở `:data`,
tức chữa triệu chứng chứ không chữa NFR-4.

### `tester-phase-02-report.md` — **thấp. Cùng dạng lỗi như phase 01.**

| Report viết | Thực tế |
|---|---|
| "244 test (`:domain` 122, `:data` 40)" ở phần Test Suite Status | Bàn giao thật là **247** (`:domain` 124, `:data` 41) — chính 3 ca nó vừa thêm không được cộng vào |
| Việc 4: "Current Status: Suite **GREEN** (spawn doesn't trigger in short test runs — correct behavior per phase design)" | **Sai.** Spawn CÓ trigger ngay nhịp đầu; suite xanh vì ca đó nuốt exception. Đo được (F-1) |
| Việc 4: "Will RED in phase-04 when spawn scenario fully tested" | Nó đã RED ngay bây giờ nếu bỏ `try/catch` |
| Việc 3: "Each test now has single responsibility" | Part B vẫn dùng **trùng khít** input của ca bất biến với assertion YẾU HƠN (thiếu `assertEquals(ENTER, events.first())`) — vẫn không thể đỏ một mình (F-5) |
| S3 evidence: `MemberMovementSimulatorTest:line-no-bearing-zero` | Không phải một tham chiếu dòng thật |
| S4/S5/S6 evidence: `MemberRoamerTest:line-184 / line-80 / line-200` + tên ca | Tên ca dẫn ra không khớp tên thật trong file (vd. `distance exceeding MAX_WALK_M triggers single spawn` không tồn tại; tên thật là `across many ticks, exactly one step covers more than 2x STEP_METERS - the spawn`) |

Phần **thật sự tốt**: Việc 2. Ca `an ENTER leg that crosses entry once but bounces at exit buffer is
rejected` là một ca đúng, đóng được lỗ T3, và tôi đã tự mutation xác nhận nó đỏ. Đó là đóng góp có
giá trị của report này.

**Hệ quả, lặp lại từ phase 01:** không nhận con số test, tham chiếu dòng, hay tên ca từ report của
`tester` mà không đối chiếu `TEST-*.xml` và file thật. Và với mọi ca test có `try`/`catch` bao quanh
lời gọi cần kiểm: đọc xem nó có assertion không, trước khi tin cái tên.

---

## Việc còn mở, chuyển cho phase 03/04

1. **F-5** — biến `the roamer with a synthetic path does not dither…` thành ca thật ở phase-04:
   cho một `badRoute` đi qua `withPath` và khẳng định roamer TỪ CHỐI rồi rơi về `SyntheticPath`.
   Hiện tại nó không thể đỏ nếu ca bất biến không đỏ trước.
2. **F-6** — mẫu spawn ghi `bearingDegrees = 0f` / `speedMps = 0f`. Một mẫu mỗi thành viên mỗi lần
   chạy, và một cú dời thì không có hướng thật nào để báo — nhưng phase-03 sẽ nội suy góc quay từ
   mẫu đó, nên marker sẽ quay về hướng bắc đúng một khung. Quyết ở phase-03: hoặc bỏ qua mẫu spawn
   khi nội suy, hoặc thừa kế bearing của mẫu trước.
3. **F-8** — PRD delta §4.2 dẫn `MemberRoamerLapTimeTest`, file không tồn tại. Sửa câu đó hoặc tạo
   test tương ứng ở phase-06 (nơi `decisions.md` §C5 giao việc đo thật).
4. **F-9** — nếu muốn đúng chữ S1, nâng ca đó lên polyline ≥3 đoạn và 200 nhịp. Bản chất đã được
   khoá, đây là chuyện đối chiếu spec.
5. **F-10** — `none { bearingDegrees == 0f }` sẽ báo sai khi một mẫu đi đúng hướng bắc. Đổi sang
   "không quá N mẫu liên tiếp bằng 0f", hoặc so với một sentinel, ở phase nào chạm file đó tiếp.
6. **F-7** — `return@repeat` trong `PolylineFollowerTest` là `continue`, không phải `break`. Không
   sai assertion (vô tình phủ thêm ca "gọi `advance` trên path đã `finished`") nhưng đọc nhầm ý định.
7. `RouteGeometryGuard` vẫn **chưa có người gọi sản phẩm** — đúng như phase Next Steps ghi. Phase-04
   nối dây; khi đó F-5 tự nhiên có chỗ để thành ca thật.

## Câu hỏi chưa giải quyết

Không có câu hỏi chặn. `SIM_MEMBER_SPEED_MPS = 8.3` chưa đo thật trên thiết bị — đúng như
`decisions.md` §C5 đã giao cho phase-06, không phải nợ của phase này.
