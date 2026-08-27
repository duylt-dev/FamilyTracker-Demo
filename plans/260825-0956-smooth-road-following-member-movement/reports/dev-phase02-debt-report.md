# Dev Report — Phase 02 Low-severity Debt Cleanup (F-5, F-7, F-9, F-10)

**Ngày:** 2026-08-25 · **Phạm vi:** chỉ 3 file test (`domain` + `data`), chạy song song với dev
phase-03 đang sửa `ui/` + `LLM.md` + PRD delta. **Không chạm `src/main/` trong bản giao (mọi mutation
dưới đây đã hoàn nguyên sạch, xác nhận bằng `git diff --stat` rỗng).**

Nguồn: `reports/reviewer-phase-02-report.md` bảng phát hiện (dòng 34-47) + mục "Việc còn mở"
(dòng 284-303); `phase-02-buoc-di-tren-polyline-va-bearing.md` Success Criteria S1/S3/S9.

---

## Tóm tắt 4 việc

| # | Mức | Kết quả |
|---|---|---|
| F-9 | Low | **ĐÃ SỬA.** `PolylineFollowerTest` ca S1 nay chạy đúng 200 nhịp trên polyline 3 đoạn |
| F-7 | Low | **ĐÃ SỬA.** `return@repeat` → `while`+`break` thật; ca phụ "advance sau finished" tách riêng, có tên |
| F-10 | Low | **ĐÃ SỬA.** `none { == 0f }` → "không quá N mẫu LIÊN TIẾP = 0f", có KDoc giải thích |
| F-5 | Low | **KHÔNG SỬA thành ca thật — theo đúng nhánh "KHÔNG" mà chỉ dẫn dự đoán.** Đọc code xác nhận `RouteGeometryGuard` chưa được `withPath` gọi. Xoá Part B (bản sao yếu), giữ Part A |

---

## F-9 — `PolylineFollowerTest` ca S1

**Vấn đề:** S1 đòi "200 nhịp, polyline ≥ 3 đoạn"; bản cũ chạy `repeat(20)` trên `bentPath` (2 đoạn,
tổng 200m).

**Sửa:** Trong đúng ca `every sample lands exactly on the polyline, even across a corner`, dựng một
polyline riêng 4 điểm / 3 đoạn (không đụng `bentPath` — 4 ca khác của file khoá vào đúng hình học
2-đoạn của nó, đổi `bentPath` sẽ vỡ chúng): hai góc rẽ nhỏ (15m) gần xích đạo + một đoạn thẳng dài
6200m. Vòng lặp đổi từ `repeat(20)` sang `while (tick < 200)` chạy đúng 200 nhịp thật (path dài
~6230m > 200×30m=6000m nên KHÔNG hết đường giữa chừng — khoá bằng assertion mới `tick == TICKS_FOR_S1`
để không ai âm thầm rút ngắn input về lại yếu như cũ mà không bị bắt).

**Vì sao góc rẽ chỉ 15m, không phải 100m như `bentPath`:** `distanceToNearestSegment` xấp xỉ phẳng
(không có hệ số `cos(lat)`) nên càng đi xa xích đạo, đoạn thẳng càng dài, sai số hình học của phép
xấp xỉ càng lớn — tính tay: ở lệch vĩ độ 100m trên một đoạn 6200m, sai số ước ~7.6e-7 m, sát ngưỡng
`1e-6 m` của chính assertion đang kiểm (rủi ro test tự flaky vì lỗi xấp xỉ, không phải lỗi thuật
toán). Ở lệch 15m sai số chỉ ~7.6e-9 m — an toàn dư ~100 lần.

**Mutation (đỏ → xanh):** vô hiệu hoá nhánh "dừng ở đỉnh" trong `PolylineFollower.advance`
(`domain/src/main/.../PolylineFollower.kt` dòng bảo toàn đỉnh, đổi `if (nextVertexIndex != -1 && ...)`
→ `if (false && nextVertexIndex != -1 && ...)`), chạy `:domain:test`:

```
PolylineFollowerTest > a step that would overshoot a vertex stops exactly at the vertex, shortened FAILED
    java.lang.AssertionError at PolylineFollowerTest.kt:97
PolylineFollowerTest > every sample lands exactly on the polyline, even across a corner FAILED
    java.lang.AssertionError at PolylineFollowerTest.kt:85
125 tests completed, 2 failed
```

Ca S1 mới ĐỎ cùng lúc với ca overshoot cũ — chứng minh input mới (200 nhịp/3 đoạn) vẫn thật sự khoá
đúng bảo toàn đỉnh, không phải khoá hờ. Hoàn nguyên → `git diff --stat` rỗng, build lại xanh.

---

## F-7 — `PolylineFollowerTest:38` đọc nhầm ý định

**Vấn đề:** `repeat(20) { ... if (progress.finished) return@repeat }` — `return@repeat` là nhãn mặc
định của lambda truyền cho `repeat` (một `inline fun`), nên nó chỉ bỏ qua PHẦN CÒN LẠI của MỘT lần
lặp (`continue`), không thoát cả vòng (`break`). Hậu quả: sau khi path `finished`, các lần lặp còn
lại (ở bản 20-nhịp cũ, tick 9-20) vẫn âm thầm gọi lại `advance()` trên path đã hết — vô tình phủ
thêm một ca khác mà không ai đặt tên hay chủ đích viết.

**Sửa:**
1. Ca S1 (F-9) đổi sang `while (tick < TICKS_FOR_S1) { ...; if (progress.finished) break }` — `break`
   thật, ý định khớp với đọc.
2. Ca phụ "gọi `advance` trên path đã `finished`" đáng giữ (nó test tính idempotent — gọi lại không
   di chuyển thêm, không ném lỗi) nên tách thành ca riêng có tên:
   `advancing again after the path already finished stays put and keeps reporting finished` — gọi
   `advance` một lần để tới cuối đường (tái dùng đúng input của ca `a step past the total length
   finishes at the last point`, `cursorMeters=180.0, stepMeters=50.0`), rồi gọi `advance` LẦN NỮA
   trên kết quả đó với `stepMeters` khác (30.0) để chứng minh bước tiếp theo không phụ thuộc
   `stepMeters` một khi đã hết đường.

**Mutation (đỏ → xanh):** đổi `movedMeters = (path.totalMeters - cursorMeters).coerceAtLeast(0.0)`
thành `movedMeters = stepMeters` (tức báo đã đi thêm dù đã hết đường):

```
PolylineFollowerTest > advancing again after the path already finished stays put and keeps reporting finished FAILED
    java.lang.AssertionError at PolylineFollowerTest.kt:136
125 tests completed, 1 failed
```

Đúng và CHỈ đúng ca mới đỏ (không ca nào khác bị ảnh hưởng) — xác nhận ca này đo đúng và chỉ đo hành
vi nó đặt tên. Hoàn nguyên → `git diff --stat` rỗng.

---

## F-10 — `MemberMovementSimulatorTest` ca S3

**Vấn đề:** `members.recorded.none { it.second.bearingDegrees == 0f }` báo SAI nếu một mẫu thật đi
ĐÚNG hướng bắc — bearing thật lúc đó cũng là `0.0f`, không phân biệt được với hằng số cứng cũ đã bị
xoá ở phase-02 (F-1 review).

**Sửa:** đổi sang "không quá `MAX_CONSECUTIVE_ZERO_BEARING_SAMPLES` (=3) mẫu LIÊN TIẾP = 0f" — một
mẫu đơn lẻ trùng đúng hướng bắc là chuyện tự nhiên trên một đường cong (không sai), nhưng NHIỀU mẫu
LIÊN TIẾP giống hệt `0f` thì không — đường bám (`PolylineFollower`/`GeoBearing`) đổi hướng liên tục
mỗi nhịp, nên trùng CHÍNH XÁC cùng một bearing nhiều nhịp liền chỉ xảy ra khi giá trị bị đóng băng.
Đổi tên ca cho khớp; nhân tiện sửa luôn một chỗ lệch nhỏ có sẵn từ trước (không thuộc 4 việc được
giao, ghi lại để minh bạch): tên ca cũ nói "50 ticks" nhưng code luôn chạy `TICKS_FOR_A_FULL_CYCLE`
= 200 — tên mới ghi đúng "200 ticks". KDoc trên ca giải thích rõ lý do chọn ngưỡng, theo đúng yêu
cầu "không để người sau tưởng assertion cũ chặt hơn".

**Mutation (đỏ → xanh):** hoàn nguyên tạm bug cũ ở `data/src/main/.../MemberMovementSimulator.kt`
dòng gán `bearingDegrees = next.bearingDegrees.toFloat()` → `bearingDegrees = 0f`:

```
MemberMovementSimulatorTest > no long run of recorded bearingDegrees is stuck at 0f after 200 ticks on a curved path FAILED
    java.lang.AssertionError at MemberMovementSimulatorTest.kt:103
43 tests completed, 1 failed
```

Hoàn nguyên → `git diff --stat` rỗng.

---

## F-5 — `MemberRoamerTest` ca "does not dither when entering a zone" — đi nhánh KHÔNG, như dự đoán

**Bước 1 — đọc code trước khi quyết định (đúng yêu cầu, không đoán):**

`domain/src/main/.../MemberRoamer.kt`, hàm `withPath`:
```kotlin
fun withPath(state: RoamState, points: List<GeoPoint>): RoamState =
    state.copy(path = PolylineFollower.parametrize(points), pathCursorMeters = 0.0)
```
Không có lời gọi `RouteGeometryGuard` nào ở đây — `withPath` chấp nhận VÔ ĐIỀU KIỆN mọi `points`
truyền vào, không có nhánh từ chối.

`domain/src/main/.../RouteGeometryGuard.kt`, KDoc lớp xác nhận cùng kết luận: *"Chưa có người gọi
thật trong phase-02 — MemberRoamer chỉ dùng SyntheticPath (...), nên guard này được bài test của nó
và của MemberRoamerTest gọi TRỰC TIẾP để khoá hành vi trước khi phase-04 nối dây thật."*

**Kết luận: đi nhánh KHÔNG.** Guard chưa được nối vào `withPath` — đúng như chỉ dẫn dự đoán trước
khi tôi đọc code.

**Hành động:** Xoá ca `the roamer with a synthetic path does not dither when entering a zone` (Part
B) — bản sao YẾU của ca bất biến `a full roam cycle produces alternating ENTER and EXIT, starting
with ENTER` phía trên (trùng khít input, thiếu `assertEquals(ENTER, events.first())`) — không thể
đỏ một mình nếu ca bất biến kia không đỏ trước, nó chỉ tạo cảm giác an toàn giả. Giữ nguyên Part A
(`a route hugging the zone edge is rejected by the geometry guard`) — ca này gọi
`RouteGeometryGuard.isUsable` trực tiếp với một `badRoute`, không đi qua `withPath`, nên nó độc lập
với việc guard đã nối dây hay chưa và vẫn là một ca thật.

Thay vì viết một ca giả (`badRoute` qua `withPath` rồi mong đợi bị từ chối — hôm nay chắc chắn ĐẠT
vì `withPath` chấp nhận mọi thứ, đúng loại "ca đạt vô điều kiện" mà F-2 vừa sửa xong ở cùng phase),
tôi để lại một comment tại chỗ xoá, trích dẫn chính xác dòng code đã đọc, để người làm phase-04 biết
ngay việc gì cần làm khi nối dây guard.

**Bằng chứng Part A vẫn là ca thật (mutation bổ sung, không bắt buộc nhưng làm cho chắc — vì tôi
đang XOÁ code test, cần chứng minh phần GIỮ LẠI vẫn sống):** Guard chỉ chặn `badRoute` (offsets
300/100/300/100/300, 4 lần cắt biên) qua điều kiện `entryCrossings == 1` trong nhánh `when` (không
qua nhánh early-return `entryCrossings > MAX_ALLOWED_CROSSINGS`, vì bản thân `entryCrossings=4` vẫn
lọt qua nếu chỉ neuter nhánh `when`, và ngược lại nếu chỉ neuter early-return thì `entryCrossings==1`
vẫn tự chặn — phải vô hiệu hoá CẢ HAI cùng lúc mới lộ ra được ca này có phụ thuộc thật hay không):

```
# neuter cả early-return (entryCrossings > MAX_ALLOWED_CROSSINGS...) VÀ nhánh `when` ENTER_ZONE -> true
MemberRoamerTest > a route hugging the zone edge is rejected by the geometry guard FAILED
    java.lang.AssertionError at MemberRoamerTest.kt:250
(+ 3 ca khác của RouteGeometryGuardTest cùng đỏ)
125 tests completed, 4 failed
```

Hoàn nguyên cả hai chỗ → `git diff --stat` rỗng.

**Đề xuất dòng cho `LLM.md` §13 Open (không tự sửa `LLM.md` — chuyển cho phase-03/orchestrator gộp
cùng commit):**

> **F-5 — `RouteGeometryGuard.isUsable` chưa có đường gọi thật; `MemberRoamer.withPath` chấp nhận
> mọi `points` vô điều kiện.** `withPath` (`domain/tracking/MemberRoamer.kt`) không gọi
> `RouteGeometryGuard`, nên một tuyến hình học xấu (routing thật ở phase-04 có thể trả về) sẽ được
> chấp nhận thẳng, không bị từ chối/rơi về `SyntheticPath` như S9 mô tả. `MemberRoamerTest` chỉ khoá
> được nhánh "guard tự nó đúng" (`RouteGeometryGuard.isUsable` gọi trực tiếp), không khoá được nhánh
> "roamer THẬT SỰ dùng guard để từ chối" — ca đó từng là một bản sao yếu (đã xoá, phase-02 debt
> report, F-5) vì không thể viết ca thật cho tới khi phase-04 nối dây. **Quyết định: giữ Open, việc
> của phase-04.** | `domain/tracking/MemberRoamer.kt`, `domain/tracking/RouteGeometryGuard.kt`

---

## Kiểm chứng bắt buộc — output thật

```
$ ./gradlew :domain:test :data:test --no-configuration-cache --rerun-tasks
...
> Task :domain:test
> Task :data:testDebugUnitTest
> Task :data:test

BUILD SUCCESSFUL in 7s
21 actionable tasks: 21 executed
```

Số liệu đọc trực tiếp từ `TEST-*.xml` (không suy đoán):

```
domain: tests=125 skipped=0 failures=0 errors=0   (wall time tổng các suite = 0.147s, dưới ngưỡng 5s NFR-2/S10)
data:   tests=43  skipped=0 failures=0 errors=0

PolylineFollowerTest:            tests=7  (6 cũ + 1 ca mới "advancing again after…", F-7)
MemberRoamerTest:                tests=13 (14 cũ - 1 ca Part B bị xoá, F-5)
MemberMovementSimulatorTest:     tests=8  (không đổi số lượng — F-10 chỉ đổi assertion + tên ca)
```

125/43 khớp CHÍNH XÁC với baseline `reviewer-phase-02-report.md` (domain 125, data 43) — không tăng
không giảm, đúng như kỳ vọng vì F-9/F-7 cộng 1 trừ 0 (S1 sửa tại chỗ, thêm 1 ca mới), F-5 trừ 1
(xoá Part B), F-10 không đổi số lượng.

`git diff --stat` cuối cùng — chỉ 3 file được giao, không file nào khác:

```
domain/.../tracking/MemberRoamerTest.kt        | 36 +++++-------
domain/.../tracking/PolylineFollowerTest.kt    | 68 +++++++++++++++++++---
data/.../location/MemberMovementSimulatorTest.kt | 32 +++++++++-
```

`src/main/` — `git status --short domain/src/main data/src/main` rỗng, không dòng nào. Mọi mutation
ở trên đã hoàn nguyên trước khi kết thúc phiên làm việc.

---

## Chỗ reviewer phase-02 có thể đã nói chưa đủ rõ (không phải kết luận sai)

Không tìm thấy kết luận SAI nào của reviewer trong 4 việc này — đối chiếu code thật (`return@repeat`
đúng là `continue`; `withPath` đúng là không gọi guard; S1 đúng là chạy 20/2-đoạn; `none{==0f}` đúng
là có thể báo sai) đều khớp 100% với report. Một chỗ đáng nói thêm: report gọi
`the roamer with a synthetic path does not dither…` là "Part B" của S9 nhưng không nói rõ "Part A"
là ca nào — tôi suy ra (và xác nhận đúng qua đọc file) Part A = ca liền trước nó,
`a route hugging the zone edge is rejected by the geometry guard`. Không phải lỗi, chỉ là thiếu một
câu tham chiếu tường minh — ghi lại phòng khi người đọc report gốc bị mất thời gian tìm như tôi.

---

## Không làm (đúng ranh giới được giao)

- Không sửa `src/main/` trong bản giao — mọi mutation đã hoàn nguyên, xác nhận bằng `git diff`.
- Không sửa `LLM.md` — đề xuất dòng Open ở trên, chuyển orchestrator gộp cùng commit dev phase-03.
- Không commit, không `git add`, không `adb`.
- Không tạo file mới ngoài report này.

## Câu hỏi chưa giải quyết

Không có câu hỏi chặn. F-5 đã đóng đúng phạm vi được giao (xoá test giả, không mở rộng sang sửa
`src/main`); phần còn lại (nối dây guard thật) đã được ghi lại làm việc của phase-04 như phase-02
report gốc đã dự kiến.
