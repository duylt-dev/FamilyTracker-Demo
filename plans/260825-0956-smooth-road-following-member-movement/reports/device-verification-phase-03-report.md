# Nghiệm thu trên máy thật — Phase 03 (Step 7 + Step 8)

**Ngày:** 2026-08-25 17:27–17:55 · **Người chạy:** orchestrator (không phải subagent)
**Thiết bị:** Samsung `SM-A165F`, serial `RF8Y60B9NCZ`, Android 16, 1080×2340
**Bản dựng:** `:app:assembleDebug` từ working tree phase-03 (trước simplifier)

---

## 0. Điều kiện đo — và vì sao phải dựng lại nó

Phase yêu cầu **3 thành viên + 5 zone** (NFR-1). Trạng thái máy lúc nhận việc: 3 thành viên, **1 zone**,
và — quan trọng hơn — **self ở Hà Nội (20.98, 105.80) còn Minh/Lan ở TP.HCM (10.78, 106.71)**, cách
nhau ~1150 km. Camera canh lần đầu ưu tiên self (`FamilyTrackerMap` KDoc) nên **không marker thành
viên nào lọt vào khung**: không quan sát được gì.

Cách dựng lại điều kiện:

1. Sao lưu đủ 3 file `family_tracker.db`, `-wal`, `-shm` qua `run-as`; `pragma integrity_check` = `ok`;
   đối chiếu 3 thành viên / 1 zone / 1241 điểm.
2. `pm clear` → `DemoDataSeeder` gieo lại. Self chưa có điểm nào ⇒ `initialCameraTarget` rơi về một
   thành viên ⇒ camera canh vào TP.HCM, marker lọt khung.
3. Cấp lại 4 quyền bị `pm clear` thu hồi (`pm grant`).
4. Tạo 4 zone còn thiếu bằng `input swipe` (nhấn giữ) + Zone editor, script chạy **trên máy** để
   nhịp ổn định. Kết quả: **Z1…Z5**, đủ 5 zone.
5. Sau khi đo xong: hoàn nguyên cả 3 file DB. Xác nhận lại **3 thành viên / 1 zone "hello" / 1241
   điểm** — đúng nguyên trạng. Dọn sạch file tạm trên `/sdcard` và `/data/local/tmp`.

> **Ghi cho lần sau:** `adb pull /sdcard/` kéo cả thẻ nhớ và làm hết giờ 5 phút. Luôn pull đích danh file.
> Và shell của máy dev là **zsh** — `set -- $var` KHÔNG tách từ như bash, nên script điều khiển `adb`
> phải tránh word-splitting hoặc chạy hẳn trên máy.

---

## 1. Màn hình chạy 90 Hz, không phải 60 Hz

```
mActiveSfDisplayMode = DisplayMode{id=0, 1080x2340, peakRefreshRate=90.0, vsyncRate=90.0}
```

Phase file giả định 60 fps ở S3 ("8.3 m/s ÷ 60 ≈ 0.14 m"). Máy nghiệm thu chạy **90 Hz**, ngân sách
khung là **11.1 ms** chứ không phải 16.7 ms — tức là bài kiểm **khó hơn** giả định của spec, không
dễ hơn. Mọi số dưới đây đọc theo 90 Hz.

---

## 2. Step 8 — `dumpsys gfxinfo`, 60 giây, 3 thành viên + 5 zone

Phương pháp đúng §13 Fixed #16: `reset` **sau** khi app đã ổn định trên tab Bản đồ (không tính cold
start), ngủ 60 s **trên thiết bị** (không busy-loop trên host để khỏi đốt CPU của chính máy đang đo).

```
Total frames rendered: 5360
Janky frames: 33 (0.62%)
Janky frames (legacy): 1156 (21.57%)
50th percentile: 10ms   90th: 12ms   95th: 14ms   99th: 18ms
50th gpu percentile: 3ms  90th: 5ms  95th: 6ms  99th: 11ms
Number Missed Vsync: 0
Number Slow UI thread: 12
Number Slow bitmap uploads: 0
Number Slow issue draw commands: 9
```

**Áp luật chốt ở Step 8: Janky frames 0.62% < 5% ⇒ GIỮ NGUYÊN.** Không hạ xuống 30 fps, không bỏ nội
suy góc.

Hai điều phải nói thẳng thay vì chọn con số đẹp:

- **Chỉ số "legacy" là 21.57%, không phải 0.62%.** Hai chỉ số này đo khác nhau (`Janky frames` hiện
  đại tính theo deadline vsync thật; `legacy` theo mốc 16 ms cứng và gộp cả thời gian xếp hàng).
  Luật ở Step 8 nói "Janky frames", và `Number Missed Vsync: 0` cùng phân vị 90th = 12 ms ủng hộ con
  số hiện đại. Nhưng **con số legacy đã được ghi lại ở đây** để phase-06 so lại chứ không biến mất.
- **`Number High input latency: 10502`** trong khi không có thao tác nào suốt 60 s. Bộ đếm này của
  Samsung không đáng tin trong ngữ cảnh này; không dùng nó để kết luận gì.

**5360 khung / 60 s ≈ 89.3 fps** — tự nó là bằng chứng `withFrameNanos` **tick được bên trong
subcomposition của maps-compose**, thứ không hiển nhiên trước khi chạy thật.

**Hệ quả về pin, nói rõ:** vòng lặp gần như không bao giờ rỗi, vì mẫu mới về mỗi 2.5 s trong khi
`durationMs` cũng là 2.5 s. Cơ chế "thoát khi mọi `progress >= 1`" trong `AnimatedMarkerPositions`
là đúng và cần thiết, nhưng ở nhịp mô phỏng nó hiếm khi được kích hoạt. Đây là bản chất của tính
năng (mượt = vẽ liên tục), không phải khuyết tật — nhưng phase-06 nên đo pin, không chỉ đo khung.

---

## 3. S5 — số dòng Room khớp nhịp lấy mẫu, không khớp nhịp khung hình

Máy **không có `sqlite3`**, nên đếm bằng cách kéo DB về đếm ở host.

| Thành viên | Dòng/60 s | Giây mỗi dòng |
|---|---|---|
| Lan | **24** | **2.50** |
| Minh | 10 | 6.00 |

`MEMBER_ROAM_INTERVAL_MS = 2500` ⇒ kỳ vọng 24 dòng/60 s. **Lan khớp chính xác.** Nếu nội suy có ghi
xuống Room thì con số phải là ~5360.

Minh chỉ 10 dòng vì đang **dwell trong Z1**: `MemberRoamer` trả `RoamStep.Move` với toạ độ không đổi
và tầng gọi bỏ qua việc ghi. Đây là hành vi đã thiết kế, và nó là bằng chứng phụ rằng đường ghi bị
chi phối bởi **mẫu**, không phải bởi khung hình. **S5 ĐẠT.**

---

## 4. S3 / FR-1 / QA-SRM-05 — marker trượt liên tục, không nhảy

20 khung chụp liên tiếp trải 15 giây (~0.79 s/khung, phủ ~6 chu kỳ lấy mẫu 2.5 s). Bám trọng tâm
cụm pixel màu `#7B3FF2` của Lan:

```
khung  n_px       x        y   dịch(px)
  00    405   435.5  1253.5      —
  01    413   435.1  1252.5    1.06
  02    412   435.0  1250.3    2.17
  …      …       …       …       …
  18    411   435.9  1220.9    1.80
  19    407   436.3  1218.7    2.20
```

**Không khung nào dịch 0 px.** Dải dịch chuyển 1.06–2.31 px, trung bình ~1.9 px. Nếu marker nhảy theo
mẫu (không nội suy), với nhịp chụp 0.79 s và mẫu 2.5 s ta bắt buộc phải thấy **~3 khung liên tiếp
dịch 0 px rồi một cú nhảy ~6 px** — mẫu hình đó **hoàn toàn vắng mặt**.

Quỹ đạo là đường thẳng (`x` giữ 435.5→436.3, `y` giảm đều 1253.5→1218.7), khớp với việc đi trên một
đoạn của polyline — đúng Key Insight #1 của phase (bảo toàn đỉnh ở phase-02 làm cho nội suy tuyến
tính ở `:ui` nằm đúng trên đường).

Quy ra mét: 34.8 px trong 15 s ứng với ~62 m ⇒ **~0.56 m/px**. Bước mỗi khung ở 90 fps = 4.15/90 =
**0.046 m**, dưới ngưỡng `MEMBER_RENDER_MAX_JUMP_M = 2.0` khoảng **43×**. **S3 ĐẠT.**

---

## 5. S7 — `flat = true`, xác nhận bằng số

Cử chỉ xoay hai ngón **không bơm được** trên máy này: `sendevent /dev/input/event3` bị SELinux từ
chối (máy không root) và `input` chỉ hỗ trợ một điểm chạm. Chủ dự án đã **xoay tay**; orchestrator dò
tự động thời điểm xoay bằng độ lệch pixel trung bình giữa các khung.

Không dùng la bàn của Maps SDK để đọc góc (dễ nhầm chiều kim đỏ). Thay vào đó: **giải góc xoay bản
đồ từ chính hình học hai marker** — hướng thật Minh→Lan tính từ toạ độ trong DB, so với hướng
Minh→Lan đo trên màn hình.

| | Trước | Sau |
|---|---|---|
| Minh trên màn hình | (409.3, 924.6), blob 1192 px | (555.8, 1105.4), blob 1196 px |
| Lan trên màn hình | (536.3, 975.5), blob 1216 px | (628.9, 1045.5), blob 1214 px |
| Hướng Minh→Lan thật | 240.5° | 239.3° |
| Hướng Minh→Lan trên màn hình | 111.9° | 50.7° |
| **Map bearing** | **128.7°** | **188.6°** |

⇒ **bản đồ xoay 59.9°.** (Blob cùng kích thước ở hai ảnh ⇒ phép nhận dạng ổn định, không phải bắt nhầm.)

Góc mũi chỉ hướng của Minh, đo bằng trọng tâm tam giác trắng bên trong bán kính 0.78 r (loại viền):

| | Trước | Sau | Δ |
|---|---|---|---|
| Góc mũi trên màn hình | 84.8° | 33.4° | **−51.4°** |
| `bearingDegrees` (DB) | 214.67° | 226.03° | +11.36° |

| Giả thuyết | Δ dự đoán | Sai số |
|---|---|---|
| **`flat = true`** (dán phẳng mặt bản đồ) | 11.36 − 59.9 = **−48.5°** | **2.9°** ✅ |
| `flat = false` (billboard) | **+11.4°** | 62.8° ❌ |

Kiểm tra **tuyệt đối** còn chặt hơn kiểm tra vi phân — `flat = true` ⇒ góc màn hình = `bearing − mapBearing`:

| | Dự đoán `flat=true` | Đo được | Sai số | Dự đoán billboard |
|---|---|---|---|---|
| Trước | 214.67 − 128.7 = **85.97°** | 84.8° | **1.2°** | 214.7° |
| Sau | 226.03 − 188.6 = **37.43°** | 33.4° | **4.0°** | 226.0° |

**S7 ĐẠT.** Sai số 1–4° giải thích được trọn vẹn: góc hiển thị là góc **đã nội suy** nên trễ so với
mẫu mới nhất trong DB; tam giác chỉ 103 px nên lượng tử hoá ~±2°; và toạ độ màn hình dùng để giải
map bearing là vị trí **đã nội suy** trong khi toạ độ DB là mẫu **đã ghi**.

**researcher-02 §C viết `flat` NGƯỢC — nay đã bác bằng thực nghiệm, không phải bằng đọc tài liệu.**
Đúng như Key Insight #7 của phase file yêu cầu.

---

## 6. S4 — chấm xanh self

**KHÔNG nghiệm thu được vòng này.** Sau `pm clear`, self không có điểm nào cho tới khi có fix GPS
thật; và trong toàn bộ phiên đo, self ở Hà Nội còn camera ở TP.HCM. Chấm xanh **có** hiển thị và
`SelfAccuracyCircle` vẽ đúng ở lần chạy đầu (ảnh `01-launch.png`, `02-tracking-on.png`), nhưng
"trượt liên tục giữa hai fix cách nhau 10 s" thì chưa quan sát được — đứng yên trong nhà thì hai fix
liên tiếp gần như trùng nhau, không có gì để trượt.

Đường code là **CÙNG MỘT** `rememberAnimatedMarkerPositions` đã được chứng minh ở mục 4, chỉ khác là
không truyền `rotation`. Nhưng đó là suy luận, không phải quan sát — nên ghi là **CHƯA NGHIỆM THU**,
không ghi là đạt.

---

## 7. Khuyết tật phát hiện được nhờ chạy thật — KHÔNG thuộc phase 03

**Lỗi tốc độ mô phỏng vẫn còn nguyên, chưa từng được sửa.**

`SyntheticPath.kt:53` vẫn đặt khoảng cách đỉnh `= MemberRoamer.STEP_METERS / 2`. Giao với luật
"`advance()` dừng ở đỉnh ĐẦU TIÊN trong `(cursor, cursor+step]`" ⇒ mỗi tick chỉ đi được nửa bước.
Dữ liệu máy thật xác nhận: `speedMps` ghi xuống Room là **4.08–4.21**, đúng một nửa
`SIM_MEMBER_SPEED_MPS = 8.3`.

Handoff `handoff-orchestrator-260825-1540.md` đã mô tả đúng lỗi này và ghi "cách sửa đã chốt: giãn
đỉnh `SyntheticPath` lên ~2 × STEP_METERS". **Việc đó chưa bao giờ xảy ra**, và
`reviewer-phase-02-report.md` đóng phase 02 mà không liệt kê nó trong F-1…F-10.

Ảnh hưởng:
- **Phase 03: KHÔNG.** `SPAWN_SNAP_THRESHOLD_M = 207.5 m` vẫn cách bước thật (10.375 m) 20×.
- **Phase 06 / B4: CÓ.** Phép đo thời gian một vòng sẽ lệch **2×**, và `SIM_MEMBER_SPEED_MPS` sẽ bị
  chốt sai nếu đo mà không sửa lỗi này trước.

Không tự sửa: ngoài phạm vi lần chạy này, và `impact({target:"MemberRoamer.tick"})` = **HIGH, 22
symbol, 5 caller trực tiếp**.

---

## 8. Tổng kết Success Criteria

| # | Điều kiện | Kết quả | Bằng chứng |
|---|---|---|---|
| S1 | `lerpBearing` đi đường ngắn qua 0°/360° | ĐẠT | `MarkerInterpolationTest` (đơn vị) |
| S2 | Điểm nội suy nằm trên đoạn thẳng, `< 1e-9` | ĐẠT | `MarkerInterpolationTest` (đơn vị) |
| S3 | Bước mỗi khung ≤ 2.0 m | **ĐẠT** | §4 — 0.046 m/khung, dư 43× |
| S4 | Chấm xanh self trượt giữa hai fix | **CHƯA NGHIỆM THU** | §6 |
| S5 | Số dòng Room khớp nhịp lấy mẫu | **ĐẠT** | §3 — 24 dòng/60 s = 2.5 s/dòng |
| S6 | Janky frames theo luật Step 8 | **ĐẠT** | §2 — 0.62% < 5%, giữ nguyên |
| S7 | Mũi chỉ hướng giữ hướng thật khi xoay bản đồ | **ĐẠT** | §5 — sai số 1.2°/4.0° so với `flat=true` |
| S8 | KDoc cũ biến mất, `LLM.md` §3 đúng | (reviewer kiểm bằng `git diff`) | — |

## 9. Ảnh và dữ liệu thô

Trong scratchpad của phiên (`shots/`, `db/`, `gfxinfo-after.txt`). Đáng giữ lại nếu phase-06 cần so
nền: `11-map-5zones.png` (điều kiện NFR-1), `20-ref.png` / `21-rotated.png` (cặp ảnh S7),
`gfxinfo-after.txt` (số nền jank cho phase-06).
