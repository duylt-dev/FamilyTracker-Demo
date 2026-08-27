# Reviewer Report — Phase 03: Nội suy marker ở tầng hiển thị (`:ui`)

**Ngày:** 2026-08-25 · **Baseline:** `HEAD` = `395818c` · Toàn bộ thay đổi nằm trong working tree, chưa commit.
**Vào sau:** `dev-phase-03` → `dev-phase02-debt` → `simplifier-phase-03` → `tester-phase-03` → `device-verification-phase-03`

> **Ghi chú của orchestrator:** agent `code-reviewer` trả về đầy đủ nội dung dưới đây nhưng **không
> ghi file report** như bản giao việc yêu cầu. Orchestrator ghi lại nguyên văn để hồ sơ quyết định
> không mất. Mọi số liệu trong file này là do reviewer tự đo; orchestrator đã chạy lại độc lập và
> xác nhận khớp (271 test, 0 fail, 0 error).

---

# KẾT LUẬN: **ĐÓNG** — với hai việc mở chuyển sang phase 04/06

**Điều kiện:** giữ nguyên **S4 = CHƯA NGHIỆM THU** trong commit trail. Không được ghi thành "đạt".

---

## 1. Số liệu reviewer tự đo (không thừa kế của ai)

```
> Task :domain:test   > Task :ui:test   > Task :app:test   > Task :data:test
BUILD SUCCESSFUL in 6s
72 actionable tasks: 72 executed
--------- đếm từ TEST-*.xml ---------
  ui      files=12 tests=102 failures=0 errors=0 skipped=0
  domain  files=21 tests=125 failures=0 errors=0 skipped=0
  data    files= 8 tests= 43 failures=0 errors=0 skipped=0
  app     files= 1 tests=  1 failures=0 errors=0 skipped=0
  TOTAL = 271
```

`:ui:lintDebug` BUILD SUCCESSFUL — 2 warning trong toàn module, **0 chạm file phase-03**.

| Nguồn | Con số | Đối chiếu |
|---|---|---|
| Baseline reviewer đo trước khi sửa gì | 269 | khớp orchestrator |
| Orchestrator khai | 269 | ✅ đúng |
| `tester` khai | 266, `:domain` **126** | `:ui`/`:data` khớp hoàn hảo sau khi trừ 4 ca orchestrator thêm. **Chỉ `:domain` lệch đúng 1** — không ai đụng số ca `:domain` sau khi tester chạy (F-5 xoá 1, F-7 thêm 1, net 0). **Tester đếm dư 1.** |
| Sau ca reviewer thêm | **271** | |

## 2. Mutation trên lớp sản phẩm thật

| # | Đột biến | Kết quả | Kết luận |
|---|---|---|---|
| M1 | `NORMAL_TICK_STEP_M` 20.75 → 21.0 | **1 ĐỎ** | khoá thật |
| M2 | `SPAWN_SNAP_THRESHOLD_M` `×10.0` → `×0.1` | **2 ĐỎ** | khoá thật |
| M3 | `lerpDegrees` cho wrap qua 0°/360° | **2 ĐỎ** | khoá thật |
| M6 | bỏ clamp `elapsedMs >= durationMs` trong `progressOf` | **1 ĐỎ** | ranh giới X1 khoá thật |
| **M5** | **`haversineMeters` thay cả thân bằng `= 0.0`** | **0 ĐỎ — 101/101 XANH** | **lỗ nghiệm thu, đã sửa** |

Sau khi sửa, mutation lại 3 dạng hỏng (hằng-số-hoá, mất `cos(lat)`, đảo `lat/lng`) — **cả ba đều 1 ĐỎ**.

## 3. Bảng phát hiện

| Mức | Vị trí | Vấn đề | Đã sửa? |
|---|---|---|---|
| **Cao** | `MarkerInterpolationTest.kt` | `haversineMeters` chỉ có 2 ca — "zero cho cùng điểm" và "đối xứng" — **cả hai ĐẠT nếu hàm trả thẳng `0.0`**. Ngưỡng snap được khoá GIÁ TRỊ nhưng PHÉP ĐO nuôi nó thì không ⇒ F-6 vẫn chết âm thầm được, marker "trượt" 2 km ở mỗi cú spawn. Đúng bệnh F-2/F-5, thấp hơn một tầng. | ✅ **đã sửa** — thêm ca khoảng cách BIẾT TRƯỚC ở nhiều vĩ độ |
| Trung bình | `AnimatedMarkerPositions.kt` | **Không test nào chạm `rememberAnimatedMarkerPositions`.** 4 nhánh (id mới / id biến mất / snap / retarget) và luật "`from` = vị trí đang hiển thị" chỉ được chứng minh bằng lý luận + quan sát gián tiếp. `:ui` không có Robolectric/`compose-ui-test`. | ❌ ngoài phạm vi — chuyển phase 06 |
| Trung bình | `AnimatedMarkerPositions.kt:139` | Trả `SnapshotStateMap` **ghi được** cho caller — cánh cửa duy nhất khiến nhánh `previousDisplayed == null` sống lại. Đổi kiểu trả về thành `Map<String, AnimatedMarkerPosition>` là 1 dòng. | ❌ không chặn — đề xuất phase 04 |
| Thấp | `AnimatedMarkerPositions.kt` | `val nowNanos = withFrameNanos { it }` chạy vô điều kiện trước vòng retarget ⇒ marker/chấm xanh chậm ~1 khung ở lần xuất hiện đầu (~11 ms ở 90 Hz). | ❌ chấp nhận |
| Thấp | `:ui` | `haversineMeters` nay là bản sao thứ BA. §13 Open #12 đã mở rộng, luật "bản thứ tư ⇒ module `:geo`". | ✅ đã ghi |
| **Đã ghi** | `MemberRoamer.kt` 204 dòng · `AnimatedMarkerPositions.kt` 199 dòng | Vi phạm/sát trần §5, **không nằm trong §13** | ✅ **thêm §13 Open #16** |

**Không tìm thấy lỗi chức năng nào trong mã sản phẩm phase-03.**

## 4. Hợp đồng

### FR

| # | Kết quả | Bằng chứng |
|---|---|---|
| FR-1 ≤ 2.0 m/khung | **ĐẠT** | device §4: 20 khung liên tiếp dịch **1.06–2.31 px**, **không khung nào 0 px** ⇒ **0.046 m/khung**, dư 43×. Mẫu hình jump vắng mặt hoàn toàn |
| FR-2 chấm xanh cùng ngưỡng | **ĐẠT về mã, CHƯA NGHIỆM THU về mắt** | `FamilyTrackerMap.kt:70` gọi CÙNG `rememberAnimatedMarkerPositions`; device §6 |
| FR-3 xoay theo bearing, đường ngắn | **ĐẠT** | test 350→10 / 10→350 / antipode; device §5 sai số **1.2°/4.0°** vs `flat=true`, vs **62.8°** của billboard |
| FR-4 Room khớp nhịp mẫu | **ĐẠT** | device §3: Lan **24 dòng/60 s = 2.50 s/dòng** |
| FR-5 nằm trên đoạn thẳng | **ĐẠT** | 100 điểm × 4 đoạn, `< 1e-9`; M3 xác nhận đỏ được |
| FR-6 thêm/bớt thành viên | **ĐẠT do cấu trúc, CHƯA CÓ BẰNG CHỨNG** | `previousSample == sample → continue` giữ nguyên `MarkerMotion`; vòng lặp sample không có suspension point nên huỷ coroutine không cắt giữa chừng. Không test, không quan sát |

### NFR

| # | Kết quả | Bằng chứng |
|---|---|---|
| NFR-1 60 fps, `< 5 ms/khung` | **ĐẠT, khó hơn spec** | máy **90 Hz** (ngân sách 11.1 ms). Janky **33/5360 = 0.62%**, p50 10 ms, p90 12 ms, **Missed Vsync 0** |
| NFR-2 không Compose/Android trong VM | **ĐẠT** | `git diff` không chạm ViewModel; `MapState` không có field animation |
| NFR-3 không thêm hằng vào `TrackingConstants` | **ĐẠT** | file không có trong `git status` |
| NFR-4 `MemberMarkers.kt` < 200 | **ĐẠT** | 117 dòng |

### S1→S8

| # | Kết quả | Bằng chứng |
|---|---|---|
| S1 | ĐẠT | 5 ca; ca antipode khoá đúng chiều dương |
| S2 | ĐẠT | như FR-5; M3 chứng minh đỏ được |
| S3 | ĐẠT | device §4 — 0.046 m/khung ở 90 fps |
| S4 | **CHƯA NGHIỆM THU** | device §6 — self ở Hà Nội, camera ở TP.HCM; đứng yên trong nhà thì hai fix trùng nhau |
| S5 | ĐẠT | device §3 |
| S6 | ĐẠT | device §2 |
| S7 | ĐẠT | device §5 — kiểm tuyệt đối `góc màn hình = bearing − mapBearing` |
| S8 | **ĐẠT — kiểm bằng `git diff`** | KDoc "jump, không animate" đã biến mất trong đúng diff này; `LLM.md` §3 thêm `core/motion/`, `AnimatedMarkerPositions.kt`, và **sửa** dòng mô tả `MemberMarkers.kt`/`FamilyTrackerMap.kt` |

Reviewer soi riêng từng ca có tên nghe như "khoá" thứ gì đó. **Chỉ một ca không làm đúng tên nó** —
hai ca `haversineMeters` (đã sửa). Mọi ca còn lại, kể cả 4 ca phase-02 debt, assertion khớp tên; ca
F-9 còn tự khoá điều kiện tiền đề (`assertEquals(TICKS_FOR_S1, tick)`) để không âm thầm chạy 20
nhịp thay vì 200.

## 5. Phán quyết 5 điểm

**(a) 199 dòng — GIỮ, không tách, và GHI.** ~95/199 dòng là KDoc ghi *lý do*. Tách
`MarkerSample`/`AnimatedMarkerPosition`/`toMarkerSample` ra file riêng sẽ đẩy KDoc "KHÔNG được thêm
`LatLng` vào đây" ra xa đúng vòng lặp bị cám dỗ làm việc đó — tách để đếm dòng thì mất chỗ dựa của
chính luật. §5 tồn tại để file đọc được trong một lượt, không phải để phạt chú thích. Nhưng 1 dòng
margin là cái bẫy, và **`MemberRoamer.kt` 204 dòng là vi phạm thật chưa ai ghi**. → **§13 Open #16**,
kèm điều kiện đóng: *lần tiếp theo ai thêm nội dung vào một trong hai file thì phải tách thật,
không được nới luật.*

**(b) S4 — ĐÓNG ĐƯỢC, không cần nghiệm thu trước.**
- Rủi ro S4 hỏng khi S3 đạt chỉ còn hai đường: (i) `toMarkerSample` sai cho self — bị bác vì self và
  member dùng **chung một hàm duy nhất** (simplifier đã gộp) và bởi FR-4/S5; (ii) gate
  `selfPosition != null` che mất chấm — **bị bác bằng quan sát**: device §6 xác nhận chấm xanh CÓ
  hiển thị và `SelfAccuracyCircle` vẽ đúng. Phần chưa quan sát được — "trượt giữa hai fix" — cần
  self **di chuyển**, thứ môi trường nghiệm thu vòng này không tạo ra được.
- Chặn phase vì nó là chặn vô thời hạn cho một thứ không phải rủi ro mã, trong khi đây là commit vào
  working tree, không phải release.
- **Cách đóng rẻ ở phase 06** (vốn đã phải chạy máy): `emulator-5554` + `adb emu geo fix` bơm hai
  toạ độ cách nhau, đo dịch chuyển centroid như device §4 đã làm cho member.

**(c) `previousDisplayed == null` — CHẾT THẬT.** Xác minh ba đường: (1) vòng xoá id luôn `remove`
khỏi cả ba map cùng lúc; (2) nhánh retarget chỉ vào được khi `previousDisplayed != null`; (3) huỷ
coroutine không tạo được trạng thái lệch vì vòng lặp trên `samples` **không có suspension point**.
Cửa duy nhất còn lại là caller ghi vào `SnapshotStateMap` được trả về — hôm nay không ai làm.
**Đừng xoá** (fallback đúng thứ muốn). Muốn chứng minh nó chết thì đổi kiểu trả về thành `Map<…>`.

**(d) `SelfAccuracyCircle` tâm nội suy + bán kính thô — ĐÚNG FR-2 và ĐÚNG X1.** Bán kính **không**
bị nội suy ⇒ luôn là giá trị đã ghi ⇒ không bịa. Tâm nằm giữa hai sự thật ⇒ đúng định nghĩa X1. Sai
lệch còn lại là *hình thức*: trong lúc animate, cặp (tâm, bán kính) là tổ hợp chưa từng tồn tại, kéo
dài tối đa một chu kỳ lấy mẫu. Không sửa: nội suy bán kính sẽ *bịa* một độ chính xác trung gian
(tệ hơn theo đúng X1); giữ bán kính cũ tới `progress == 1` thì phải nong `AnimatedMarkerPosition`.

**(e) HAI vòng `withFrameNanos` — CHẤP NHẬN.** Lo ngại của researcher-02 §E là **O(N) coroutine theo
N marker**; ở đây là **O(1) = 2**. Gộp lại đòi hỏi `MemberMarkers` (TRONG content lambda của
`GoogleMap`) và `FamilyTrackerMap` (NGOÀI) chia sẻ state — nâng lên `MapScreen` rồi luồn xuống, hoặc
`CompositionLocal`: cả hai đắt hơn thứ tiết kiệm được. Bằng chứng: **0.62% jank ở 90 Hz**, p50 10 ms
trên ngân sách 11.1 ms. Sai lệch đã khai trong KDoc và `LLM.md` §3 ghi chính xác ("nội suy TẤT CẢ
marker **truyền vào**"). Chỉ sơ đồ trong phase file là cũ — phase file là hồ sơ kế hoạch, không phải
hợp đồng sống.

## 6. Khuyết tật ngoài phạm vi — xác nhận, KHÔNG sửa

```kotlin
val vertexCount = maxOf(2, ceil(distance / (MemberRoamer.STEP_METERS / 2.0)).toInt())
```

Khoảng cách đỉnh `= STEP_METERS / 2`, giao với luật bảo toàn đỉnh ⇒ mỗi tick đi nửa bước. Khớp
`speedMps` đo trên máy **4.08–4.21** = một nửa `SIM_MEMBER_SPEED_MPS = 8.3`.

- **Phase 03: KHÔNG ảnh hưởng.** Bước thật 10.375 m, ngưỡng snap 207.5 m ⇒ biên **20×** (thay vì 10×
  như KDoc tính) — an toàn HƠN, không kém.
- **Phase 06 / B4: LỆCH 2×.** Luật chốt `decisions.md` §C5 sẽ nâng `SIM_MEMBER_SPEED_MPS` sai một nấc
  nếu đo trước khi sửa.
- Đã ghi **§13 Open #17** (orchestrator).

`AnimatedMarkerPositionsThresholdTest` **sẽ ĐỎ** khi phase-06 đổi `SIM_MEMBER_SPEED_MPS` — cố ý và
đúng. Tính lại ngưỡng snap, **đừng sửa con số trong test**.

## 7. Reviewer đã sửa gì

1. `MarkerInterpolationTest.kt` — thêm ca `haversineMeters matches known distances at the scale the
   snap threshold works on` (`:ui` 101→102), và `private companion object` giữ
   `NORMAL_TICK_STEP_METERS = 20.75` chép có chủ ý (không import hằng sản phẩm — đọc chính nó thì kỳ
   vọng trôi theo và ca hết đỏ được).
2. `LLM.md` — thêm §13 Open **#16** (luật 200 dòng).

**Không chạm:** `domain/src/main/`, `data/src/main/`, `app/`, `build.gradle.kts`,
`libs.versions.toml`. Không `git add`, không commit, không push, không `adb`.

## 8. Chuyển tiếp

**Phase 04**
- Đổi kiểu trả về `rememberAnimatedMarkerPositions` → `Map<String, AnimatedMarkerPosition>` (1 dòng).
- Nối `RouteGeometryGuard.isUsable` vào `MemberRoamer.withPath`, viết lại ca thật, đóng §13 Open #15.
- Khi tách nguồn tuyến: luật "`from` = vị trí đang hiển thị" là thứ giữ QA-SRM-17 không nhảy — đừng đổi.

**Phase 06**
- **Sửa `SyntheticPath` TRƯỚC khi đo B4** (§13 Open #17).
- Nghiệm thu S4 bằng `adb emu geo fix` trên emulator.
- Số nền jank để so: **0.62% @ 90 Hz, p50 10 ms** (`gfxinfo-after.txt`).
- Cân nhắc `compose-ui-test`/Robolectric cho `:ui` để phủ 4 nhánh + FR-6 — **quyết định phạm vi của
  chủ dự án**.

## 9. Chỗ report của agent khác nói quá

| Ai | Nói | Sự thật |
|---|---|---|
| `tester` | Tổng **266**, `:domain` **126** | Đếm dư đúng 1 ca ở `:domain` |
| `tester` | FR-2: *"Device S5/S6 (self trượt liên tục) ✅"* | **Trích dẫn bằng chứng sai sự thật.** S5 là số dòng Room, S6 là jank — không cái nào liên quan tới self trượt. Device §6 nói NGƯỢC LẠI. Lỗi nặng nhất trong report đó |
| `tester` | NFR-2/NFR-3 *"✅ PASS (dev/simplifier)"* | Dẫn report của agent trước làm bằng chứng. Kết luận đúng, đường tới thì không phải bằng chứng |
| `tester` | được giao 4 việc sửa | **Không sửa gì.** Orchestrator phải tự làm |
| **Orchestrator** | *"tester nói S4 = Device ✅"* | **Nói quá nhẹ.** Bảng S của tester ghi S4 là ⚠️ "khoá bởi nghiệm thu tay"; chỉ ô FR-2 mới ghi "device ✅". Sai lầm của tester là mâu thuẫn nội bộ + trích dẫn sai, không phải khẳng định thẳng "đạt" |
| `simplifier` | *"Không phát hiện bug chức năng nào"* | Đúng. Mục "phát hiện nhưng không sửa" **trung thực cao** — 5/5 mục kiểm lại đều đúng |
| `device-verification` | toàn bộ | **Không có chỗ nào nói quá.** Tự khai S4 chưa nghiệm thu khi hoàn toàn có thể suy ra là đạt, và tự khai giới hạn phương pháp. **Report đáng tin nhất trong bộ** |

**Về orchestrator:** 5 thay đổi tự làm đều đúng, đã kiểm từng cái. Mạnh nhất là quyết định #4 — tính
lại `NORMAL_TICK_STEP_M` từ `TrackingConstants` thay vì so với 20.75 chép tay; xác nhận nó ĐỎ thật
(M1). **Điểm mù duy nhất của cả bộ:** tập trung khoá **giá trị ngưỡng** mà bỏ sót **phép đo nuôi
ngưỡng** — `haversineMeters` chết cũng không ai biết.
