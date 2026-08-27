# Simplifier Report — Phase 02: Bước đi bám polyline, bearing thật, spawn một lần

**Ngày:** 2026-08-25 · **Baseline:** `HEAD` = `cdb27a8` · **Vào sau:** `reports/dev-phase-02-report.md`
· **Status:** completed

## Tóm tắt

7 file được tinh gọn, **0 dòng logic sản phẩm thay đổi** — toàn bộ thay đổi là KDoc/comment/tên
helper test/import. Suite: **244 test, 0 failures/errors** (`:domain` 122, `:data` 40, `:ui` 81,
`:app` 1 — đúng con số bàn giao). `:domain:test --rerun-tasks`: **BUILD SUCCESSFUL in 2s**, JUnit
báo 0.171s — dưới ngưỡng 5s.

Ba thứ bị cấm động **không bị động**, đã kiểm bằng `git diff --stat` trước/sau phiên làm việc:
`MemberRoamerTest.kt` (177 dòng đổi, y nguyên), `MemberRoamer.kt` (249, y nguyên),
`TrackingConstants.kt` (18, y nguyên). Nhánh bảo toàn đỉnh trong `PolylineFollower.advance` được
`diff` với bản sao lưu: **chỉ KDoc của `ParametrizedPath` khác**, thân hàm giống từng byte.

## Ba thí nghiệm — trả lời bằng chạy thật, không bằng suy luận

Đây là phần đáng đọc nhất của report. Mỗi thí nghiệm đều khôi phục nguyên văn (`diff` rỗng) sau khi
đo.

### T1 — `ParametrizedPath`/`SyntheticPath` có THẬT SỰ phải public không? → **Có**

Hạ `ParametrizedPath` xuống `internal`, compile `:domain` + `:data`:

```
e: MemberRoamerModel.kt:38:5 'public' function exposes its 'internal' parameter type 'ParametrizedPath'.
BUILD FAILED in 679ms
```

**Kết luận: dev đúng, nhưng lý do dev ghi trong KDoc thì không chính xác.** KDoc cũ nói *"Kotlin
không cho một `data class` public có tham số constructor kiểu `internal`"*. Luật thật rộng hơn và
không liên quan tới `data class`: **mọi khai báo public không được phơi ra một kiểu `internal`** —
ở đây là constructor/`copy` sinh ra của `RoamState`. Và `RoamState` bắt buộc public vì `:data` giữ
`Map<String, RoamState>`. Đã sửa KDoc để ghi đúng thông báo lỗi của compiler. Không thu hẹp được
visibility. `SyntheticPath` public cũng đúng: `MemberMovementSimulator.pathFor()` gọi trực tiếp qua
biên module.

### T2 — `android.location.Location` trong `MemberMovementSimulator` → **quả mìn đã gài**

Đặt một lời gọi `distanceMeters(...)` lên nhánh LUÔN chạy của `moveOne`, chạy `:data:test`:

```
MemberMovementSimulatorTest > deleting the zone a member stands in ... FAILED
MemberMovementSimulatorTest > self is never moved and never raises a zone event FAILED
MemberMovementSimulatorTest > a member already standing inside a zone ... FAILED
MemberMovementSimulatorTest > dwelling inside a zone stops writing duplicate points FAILED
MemberMovementSimulatorTest > no recorded point has bearingDegrees 0f after 50 ticks FAILED
MemberMovementSimulatorTest > a followed member walking into the zone raises ENTER then EXIT FAILED
40 tests completed, 6 failed

java.lang.RuntimeException: Method distanceBetween in android.location.Location not mocked.
```

`data/build.gradle.kts` **không** bật `returnDefaultValues`, **không** có Robolectric. Suite hôm nay
xanh **chỉ vì** nhánh gọi nó (`sim_spawn`, lúc `hasSpawned` chuyển `false → true`) chưa có ca test
nào chạm tới. Chi tiết + khuyến nghị ở mục "Chuyển cho `code-reviewer`" #1.

### T3 — dòng `if (entryCrossings > MAX || exitCrossings > MAX) return false` có thừa không? → **Không thừa, nhưng KHÔNG có test nào phủ**

Xoá hẳn dòng đó, chạy `:domain:test`: **BUILD SUCCESSFUL, 122/122 xanh.**

Nghĩa là: dòng bảo vệ đúng thứ `decisions.md` §C4 tồn tại để bảo vệ (một chặng `ENTER_ZONE` cắt
đúng một lần vòng bán kính nhưng quăng qua quăng lại vòng `radius + ZONE_EXIT_BUFFER_M` → dội EXIT),
mà **không assertion nào giữ nó**. Đúng bài học phase-01: "trông thừa" + "gỡ ra không có gì đỏ" =
phát hiện đáng báo. **Không xoá** (nó load-bearing về ngữ nghĩa); thay vào đó ghi thẳng vào comment
tại chỗ để lần sau không ai "gọn hoá" nó, và đề xuất phase-04 bổ sung ca test chéo.

## Thay đổi, từng cái một

| # | File | Thay đổi | Vì sao |
|---|---|---|---|
| 1 | `domain/tracking/GeoBearing.kt` | KDoc: `[MemberRoamer.pointAtBearing]` → `[pointAtBearing]` (+ nói rõ nó ở `MemberRoamerGeometry.kt`) | Link chết: `pointAtBearing` đã rời `MemberRoamer` thành hàm top-level khi dev tách 3 file. Sai lệch sinh ra bởi chính phase này |
| 2 | `domain/tracking/PolylineFollower.kt` | Viết lại KDoc lý do public của `ParametrizedPath` bằng thông báo lỗi compiler thật (T1) | Lý do cũ nêu sai luật Kotlin. Người sau đọc "vì là data class" sẽ thử đổi sang `class` thường để lấy lại `internal` và mất 20 phút |
| 3 | `domain/tracking/RouteGeometryGuard.kt` | 3 comment: (a) vì sao `WANDER` phải return ở dòng đầu (bỏ → 1 test đỏ, đã kiểm), (b) vì sao dòng `> MAX_ALLOWED_CROSSINGS` KHÔNG thừa dù suite xanh khi xoá (T3), (c) đánh dấu nhánh `LegKind.WANDER -> true` trong `when` là không tới được | Cả ba đều là thứ một người đọc lướt sẽ tưởng là rác và xoá. Không đổi một ký tự logic |
| 4 | `data/location/MemberMovementSimulator.kt` | Viết lại KDoc `distanceMeters()` — từ *":data CÓ Android nên không cần né"* thành cảnh báo kèm bằng chứng T2 | KDoc cũ **trấn an sai**. Nó nói ngược lại NFR-4 của chính phase này, ngay dưới chỗ dev vừa xoá hằng số `ROAM_SPEED_MPS` mà KDoc cũ của hằng số đó tồn tại để giữ luật JVM thuần |
| 5 | `domain/test/.../SyntheticPathTest.kt` | `northOfInDegreesButNamedTo()` → `oneKilometreEastOfFrom()` + KDoc; `import kotlin.math.abs` thay FQN | Tên helper cũ tự thú là sai ("named to"), buộc phải có comment "xem helper" ở call site. Tên đúng thì cả hai chỗ tự giải thích |
| 6 | `domain/test/.../PolylineFollowerTest.kt` | `import kotlin.math.sqrt` thay FQN giữa hàm; comment vì sao `northOf`/`eastOf` trùng công thức | File đã import `assertEquals`/`assertTrue` nhưng lại gọi `kotlin.math.sqrt` đầy đủ ở một chỗ. Hai helper trùng thân trông như copy-paste sót — thực ra đúng, vì fixture ở vĩ độ 0 |
| 7 | `data/test/.../MemberMovementSimulatorTest.kt` | KDoc cho `eastOf()`: nói rõ đây là xấp xỉ dùng mét/độ VĨ cho kinh độ (~1.5% lệch ở vĩ độ 10°) | Công thức đúng cho nhu cầu của test nhưng sai nếu ai đó chép đi nơi khác |

## Cố ý KHÔNG đụng (kèm lý do)

1. **`MemberRoamerTest.kt` — không sửa một ký tự.** Có hai thứ tôi *đã định* làm và đã bỏ:
   (a) gộp vòng lặp `repeat(TICKS_FOR_SEVERAL_CYCLES) { advance → ZoneEvaluator → thu events }` đang
   lặp ở 4 test thành một helper; (b) cho `northOf` dùng lại `metersToLatDegrees`. Cả hai đều làm
   diff của file này rối thêm, mà đây đúng là file coordinator yêu cầu đọc **từng dòng** so với
   `HEAD`. Giá trị DRY không đáng đổi lấy việc làm khó chính khâu review quan trọng nhất phase.
2. **Nhánh bảo toàn đỉnh, `DWELL_TICKS`, `MEMBER_ROAM_INTERVAL_MS`** — theo chỉ thị. Đã `diff` xác
   nhận nguyên vẹn.
3. **`FULL_CIRCLE_DEGREES` khai hai lần** (`GeoBearing` private 360.0 + `MemberRoamerGeometry`
   top-level `internal` 360.0, cùng package). Gộp lại thì hoặc `GeoBearing` (tiện ích bearing dùng
   chung) phải phụ thuộc một file riêng của `MemberRoamer` — tệ hơn — hoặc phải tạo file hằng số mới,
   mà phase cấm tạo file. Giữ nguyên, báo ở dưới.
4. **`wanderTarget` nằm trong `MemberRoamerGeometry.kt`.** Nó không phải hình học thuần: nó dựng
   `RoamTarget` và đọc `MemberRoamer.WANDER_RADIUS_M` — về mặt cohesion nó thuộc về `MemberRoamer`
   (cạnh `nextTarget`) hoặc `MemberRoamerModel` (cạnh `RoamTarget`). Nhưng trả nó về `MemberRoamer.kt`
   đẩy file đó từ 199 lên ~210 dòng, vỡ đúng luật 200 dòng mà việc tách sinh ra để tuân thủ. Là đánh
   đổi bị ép, không phải nhầm chỗ — để reviewer chốt.
5. **`RouteGeometryGuard` không có người gọi sản phẩm** — đã xác nhận bằng grep (chỉ KDoc + test tham
   chiếu). **KDoc của nó nói rõ điều này** ("Chưa có người gọi thật trong phase-02 … phase-04 nối dây"),
   đúng yêu cầu Next Steps. Không xoá, không sửa.
6. **`:data` không có bản haversine thuần Kotlin.** Sửa T2 bằng cách tự viết lại haversine trong
   `:data` sẽ (a) đổi con số trong log `sim_spawn` = đổi hành vi, (b) đẻ thêm một bản sao thuật toán
   đúng kiểu LLM.md §13 Open #12. Ngoài thẩm quyền simplifier.

## Chuyển cho `code-reviewer`

1. **[Cao] `Location.distanceBetween` trong `MemberMovementSimulator` phá NFR-4 — chỉ chưa nổ.**
   Bằng chứng ở T2. Ba lựa chọn, đều ngoài thẩm quyền của tôi:
   (a) bỏ `distanceM=` khỏi log `sim_spawn` (mất số liệu QA-SRM-09/11 đếm được trên bản debug);
   (b) bật `testOptions.unitTests.returnDefaultValues = true` cho `:data` — rẻ nhất, nhưng biến MỌI
   API Android chưa mock thành `0`/`null` im lặng trong toàn module, đúng loại "chết im lặng" mà
   phase-01 vừa dạy;
   (c) viết một haversine thuần Kotlin trong `:data` (thêm một bản sao thuật toán, §13 Open #12).
   Khuyến nghị của tôi: **(c)**, kèm một dòng §13 Open ghi nhận bản sao — vì nó giữ được cả log lẫn
   NFR-4, và bản sao đó đã có tiền lệ được chấp nhận (`ValhallaDirectionsMapper`).
2. **[Trung bình] S9 chỉ được kiểm một nửa.** Test `a route hugging the zone edge is rejected by the
   guard, and the fallback synthetic path does not double-fire` gồm hai phần: phần đầu
   (`assertFalse(RouteGeometryGuard.isUsable(badRoute, …))`) là thật; phần sau chạy 200 nhịp rồi
   khẳng định "không dội" — nhưng `badRoute` **không bao giờ được đưa vào roamer** (roamer không hề
   gọi guard trong phase-02), và inputs của nó (`northOf(10.0, 600.0)`, `listOf(zone)`, `Random(42)`,
   cùng `TICKS_FOR_SEVERAL_CYCLES`) **trùng khít** test bất biến. Tức là phần sau chạy lại đúng test
   bất biến dưới một cái tên khác, với assertion yếu hơn. Không đỏ được ở bất kỳ hồi quy nào mà test
   bất biến không đỏ trước. Phase-04, khi guard được nối dây, nên biến nó thành ca thật: cho
   `badRoute` đi qua `withPath` và khẳng định roamer từ chối rồi rơi về `SyntheticPath`.
3. **[Trung bình] Ca chéo của `RouteGeometryGuard` chưa có test** (T3). Đề xuất một ca cho phase-04:
   chặng `ENTER_ZONE` cắt vòng `radius` đúng 1 lần nhưng cắt vòng `radius + ZONE_EXIT_BUFFER_M` 3
   lần → phải `false`. Hiện tại xoá cả dòng bảo vệ mà suite vẫn xanh.
4. **[Thấp] `every sample lands exactly on the polyline`: `if (progress.finished) return@repeat`
   KHÔNG thoát vòng lặp** — `return@repeat` là `continue`, nên sau khi hết đường test vẫn gọi
   `advance` thêm vài lần và nhét điểm cuối lặp lại vào `samples`. Assertion vẫn đúng (và vô tình
   phủ thêm ca "gọi advance trên path đã finished"), nên tôi không đổi — nhưng ý định viết ra là
   "dừng", và người sau sẽ đọc nhầm.
5. **[Thấp] `FULL_CIRCLE_DEGREES` = 360.0 khai hai lần trong cùng package** (mục "không đụng" #3).
   Bản `internal` top-level trong `MemberRoamerGeometry.kt` có tên rất chung và không gắn với file
   nào — `MemberRoamer.kt` dùng nó không qualifier nên đọc không ra nó ở đâu.
6. **[Thấp] `GeoBearing` không trùng lặp với `GeoDistance`/`RoutingGeometry`** — đã kiểm từng hàm:
   haversine (khoảng cách điểm-điểm), equirectangular (điểm-đoạn), forward azimuth (bearing) là ba
   phép khác nhau, không có hàm lượng giác nào bị viết lại lần hai. Hằng số `METERS_PER_DEGREE_LAT`
   thì có 3 bản (`RouteBlueprint`, `SyntheticPath`, `MemberRoamerGeometry`) — cả 3 đều `private`,
   đã là hiện trạng trước phase này ở `RouteBlueprint`.

## Nghiệm thu

```
./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache
BUILD SUCCESSFUL in 4s

domain   tests=122  failures=0 errors=0   ./gradlew :domain:test --rerun-tasks -> BUILD SUCCESSFUL in 2s
data     tests=40   failures=0 errors=0
ui       tests=81   failures=0 errors=0
app      tests=1    failures=0 errors=0
TOTAL    244        failures=0 errors=0
```

`:domain:test` **2s wall / 0.171s JUnit** — ngưỡng 5s (LLM.md §11) còn dư nhiều.

## Không làm

- Không `git commit` / `git push`.
- Không tạo, không xoá file nào. Không đụng `LLM.md`, PRD delta, phase file, hay `plans/` (trừ report này).
- Không thêm hằng số vào `TrackingConstants`.
- Không xoá test, không nới assertion — 244 con số giữ nguyên trước và sau.
- Không đổi hành vi: mọi thay đổi là KDoc, comment, tên helper trong test, và import.
