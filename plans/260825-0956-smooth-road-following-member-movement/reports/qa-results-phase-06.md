# Kết quả QA — phase 06 (FR-3)

Ngày chạy: 2026-08-26. Bản **debug** trên `emulator-5554` (`sdk_gphone16k_arm64`, API 37).

**Luật của bảng này:** chỉ đánh **ĐẠT** cho ca đã thật sự chạy và có bằng chứng chỉ ra được (log, ảnh
chụp, hoặc tên test). Ca chưa chạy ghi **HOÃN** kèm *lý do* và *điều kiện mở lại* — không có ô nào
để trống, và không ca nào được suy diễn từ ca khác.

`FTD_EVENT` **câm trên release** (gate G7): mọi ca "kiểm bằng log" ở đây chạy trên debug.

---

## 1. Tổng kết

| | Số ca |
|---|---|
| ĐẠT | 26 |
| HOÃN — cần `SM-A165F` | 7 |
| HOÃN — cần cửa sổ đo dài hơn | 3 |
| HOÃN — thiếu hạ tầng | 3 |
| **Tổng** | **39** |

Không ca nào **TRƯỢT**. (Đếm bằng cách quét từng bảng bên dưới, không đếm bằng trí nhớ — bản nháp
đầu của chính file này ghi 24/7/3/5 và cộng ra 39 chỉ vì đếm QA-SRM-12 hai lần và tính QA-SRM-18,
một số hiệu **không tồn tại**, thành một ca. Đúng loại lỗi mà `LLM.md` §13 Open #3 đã bắt trong câu
tổng kết PRD §2.)

---

## 2. ĐẠT (26)

### Bám đường và chuyển động

| Ca | Bằng chứng |
|---|---|
| QA-SRM-01 | `PolylineFollowerTest#every sample lands exactly on the polyline…` — 200 nhịp, mọi mẫu cách polyline < 1e-6 m |
| QA-SRM-02 | Cùng ca trên, khẳng định thứ hai: dây cung giữa hai mẫu liên tiếp lệch ≤ 2 m. **Đổi so với phase-02:** trước là "bằng 0 tuyệt đối"; xem `LLM.md` §13 Fixed #32 cho lý do và cái giá |
| QA-SRM-03 | `PolylineFollowerTest` + `MemberRoamerTest` — bearing suy từ hai điểm liên tiếp, `0.0` khi hai điểm trùng |
| QA-SRM-21 | `RealGpsNoSnapArchitectureTest` — quét 4 file GPS thật, cấm 6 chuỗi nắn đường; ca thứ hai đối chiếu danh sách với đĩa |
| QA-SRM-22 | `AnimatedMarkerPositionsTest` — nội suy chỉ giữa hai mẫu THẬT, `progressOf` kẹp về [0,1] |

### Spawn

| Ca | Bằng chứng |
|---|---|
| QA-SRM-09 | `MemberRoamerLapTimeTest#spawn only fires beyond MAX_WALK_M…` — đúng 1 spawn khi vượt ngưỡng |
| QA-SRM-10 | Cùng ca — điểm spawn đặt ở `approachRadiusMeters` quanh đích, ngoài ranh giới zone |

> **Giải quyết `LLM.md` §13 Open #21** ("`sim_spawn` không xuất hiện dòng nào trong mọi log đã thu").
> Không phải log thu sai lúc, cũng không phải bug: nhánh spawn chỉ chạy khi khoảng cách tới đích
> vượt `MemberRoamer.MAX_WALK_M` = 5 km, mà zone tạo tay trên màn luôn nằm trong vài km của thành
> viên. Nay ghim tất định cả hai chiều (không spawn ở kịch bản demo; CÓ spawn khi đặt thành viên ở
> Sydney). **Hệ quả: QA-SRM-09/11 không quan sát được trên kịch bản demo mặc định** — muốn thấy
> `sim_spawn` thật thì phải tạo zone cách hơn 5 km.

### Zone / ENTER-EXIT

| Ca | Bằng chứng |
|---|---|
| QA-SRM-25 | `MemberRoamerRealRouteTest#a full roam cycle on the real GraphHopper fixture…` — xen kẽ, không dội |
| QA-SRM-26 | `MemberRoamerLapTimeTest` chạy cả zone 150 m và 50 m; 50 m vẫn xen kẽ đúng |
| QA-SRM-27 | `:data:connectedDebugAndroidTest` — `ZoneEventDedupeTest` |
| QA-SRM-28 | `MemberRoamerTest` — chặng men mép zone không sinh sự kiện dội |

### Mất internet (D8)

| Ca | Bằng chứng |
|---|---|
| QA-SRM-13 | Lớp phủ hiện **396 ms** sau khi bật máy bay (trần 5 s). Back / chạm dưới scrim / nhấn giữ đều không đóng; xoay màn hình vẫn còn |
| QA-SRM-17 | Tự đóng **2,5 s** sau khi tắt máy bay (trần 10 s), không thao tác nào |
| QA-SRM-39 | Đang bị chặn vẫn chuyển được sang tab Zone (ZoneA/ZoneB hiện, không lớp phủ); quay lại Bản đồ thì lớp phủ vẫn còn |
| QA-SRM-40 | Khoá sai + mạng bình thường ⇒ `sim_route_failed reason=NETWORK:Wrong credentials…` + `source=SYNTHETIC`, **0 lớp phủ**. Đơn vị: `MapViewModelTest#provider failure while online never blocks the map` |

### Hạ cấp khi nhà cung cấp hỏng

| Ca | Bằng chứng |
|---|---|
| QA-SRM-14 | Build khoá rỗng: chỉ `source=SYNTHETIC`, thành viên vẫn đi |
| QA-SRM-15 | Build khoá sai: `reason=NETWORK:Wrong credentials…` rồi `source=SYNTHETIC`, không lỗi nào lên màn |
| QA-SRM-16 | Ảnh chụp trạng thái B: nhãn ước tính, **không** chuỗi OpenStreetMap |

### Ghi công OSM

| Ca | Bằng chứng |
|---|---|
| QA-SRM-30 | Ảnh trạng thái A: `Tuyến đường: GraphHopper · OpenStreetMap contributors`, kèm log `source=PROVIDER` ×2 |
| QA-SRM-31 | Cả ba trạng thái: logo Google nằm trong khung bản đồ, dải ghi công ở ngoài, không che |
| QA-SRM-32 | Ảnh trạng thái B (khoá rỗng + `pm clear` để xoá cache) |
| QA-SRM-33 | `NavigationRouteColor` chỉ xuất hiện ở `feature/navigation/component/NavigationPolyline.kt`; màn Bản đồ không vẽ tuyến |
| QA-SRM-34 | 4 nơi gọi `GoogleMap(`, tất cả Google Maps SDK; `grep -riE "TileOverlay\|MapLibre\|osmdroid\|WebView"` trả về đúng một dòng và đó là câu chú thích khẳng định luật |

### Hạn ngạch

| Ca | Bằng chứng |
|---|---|
| QA-SRM-36 | Đo phase-04, hai cửa sổ 10 phút liên tiếp: **10 → 4** request thật (trần 12). Cách đếm dùng `GEOMETRY_CACHE` vs `GEOMETRY_PROVIDER` — đếm bằng `source=PROVIDER` một mình thì hụt 4 so với 10 |

### Room

| Ca | Bằng chứng |
|---|---|
| QA-SRM-08 | `:data:connectedDebugAndroidTest` — `LocationPointDaoTest`; và đo trực tiếp: 566 điểm sau ~13 phút, `speedMps` = 8.30 |
| QA-SRM-24 | `:data:connectedDebugAndroidTest` — `LocationPointDaoTest.observeBetween_returnsOnlyPointsInRangeForMember` (lọc theo khoảng thời gian + theo thành viên) |

---

## 3. HOÃN — cần `SM-A165F` (7)

Emulator không dựng được các điều kiện này. **Điều kiện mở lại:** chạy trên `SM-A165F`
(serial `RF8Y60B9NCZ`) và dán kết quả vào bảng ở mục 2.

| Ca | Lý do emulator không làm được |
|---|---|
| QA-SRM-37 | Cần wifi captive portal thật chưa đăng nhập. Emulator không có trạng thái "có transport nhưng thiếu `NET_CAPABILITY_VALIDATED`" |
| QA-SRM-38 | Cần **di chuyển thật** 5 phút lúc mất mạng. `geo fix` không thay được: nó không sinh chuỗi fix liên tục như GPS thật |
| QA-SRM-19 | Cần ở trong nhà thật để GPS trả sai số lớn — emulator luôn cho fix hoàn hảo |
| QA-SRM-20 | Cần fix GPS thật để so "hiển thị đúng như nhận được" |
| QA-SRM-23 | Cần độ chính xác thấp thật để chỉ báo hiện lên |
| QA-SRM-35 | **Nhịp khung hình.** Đo được trên emulator là **42,41% janky**, nhưng con số nền của phase-03 (0,62%) đo trên `SM-A165F` ở 90 Hz — hai loại máy khác nhau, so với nhau là vô nghĩa. Và không có mốc nền hợp lệ ngay trên emulator: tắt theo dõi thì `Total frames rendered: 0` (không có gì chuyển động để vẽ). `Number High input latency: 1750` là đặc trưng emulator, không phải của app. **Chưa kết luận được tầng 1/2 có làm rớt khung hình hay không** |
| QA-SRM-05 | Ngưỡng bước nhảy giữa hai khung hình — cùng lý do QA-SRM-35, cần đúng máy đã lập mốc nền |

---

## 4. HOÃN — cần cửa sổ đo dài hơn (3)

**Điều kiện mở lại:** một lần chạy ≥ 20 phút với zone đặt trên đường thành viên thật sự đi qua.

| Ca | Trạng thái |
|---|---|
| QA-SRM-29 | Cửa sổ 7 phút chỉ bắt được **1** ENTER, và thành viên vẫn còn trong zone lúc cửa sổ đóng ⇒ chưa có vòng đầy-đủ nào để đếm "đúng 2 thông báo". **Không phải lỗi:** đã đối chiếu từng mẫu — lần ở trong ZoneB xảy ra TRƯỚC khi zone được tạo, và ENTER ZoneA nổ đúng lúc mẫu đầu tiên lọt vào bán kính |
| QA-SRM-11 | Dừng-bật lại theo dõi ⇒ một spawn mới. Không quan sát được vì spawn không kích hoạt ở kịch bản demo (xem Open #21 ở trên) |
| QA-SRM-12 | "Không biến mất rồi hiện lại" — cần quan sát liên tục một vòng đầy đủ |

---

## 5. HOÃN — thiếu hạ tầng (3)

| Ca | Vì sao |
|---|---|
| QA-SRM-04 | "Nhìn bằng mắt: không xuyên nhà" — cần người nhìn bản đồ và phán, không tự động hoá được. Bằng chứng gián tiếp: QA-SRM-01/02 khoá việc mẫu nằm trên polyline |
| QA-SRM-06 | Đánh loại `instr` nhưng `:ui` **không có** `androidTest` và **không có** `compose-ui-test` — không có chỗ để viết ca này |
| QA-SRM-07 | Cùng lý do; thêm nữa "không giật góc" là phán đoán thị giác |

> **QA-SRM-18 không tồn tại** — dãy số trong tài liệu QA nhảy từ 17 sang 19. Không phải ca bị bỏ
> sót; ghi lại để lần đếm sau khỏi đi tìm.
>
> **Sai lệch tài liệu cần BA xử lý:** 8 ca đánh loại `instr` (05, 06, 08, 11, 20, 24, 35, 36) giả định
> có hạ tầng instrumented ở `:ui`. Thực tế chỉ `:data` có (`data/src/androidTest/`, 6 file). Ca thuộc
> `:ui` trong nhóm đó không có chỗ để viết.

---

## 6. UAT

Chưa chạy. UAT chạy trên bản **release**, và bản release làm `FTD_EVENT` câm nên mọi bằng chứng
phải là quan sát bằng mắt. **UAT-05 (trong nhà, máy thật) là câu nghiệm thu chính của cả plan** và
chỉ chạy được trên `SM-A165F`.

**Điều kiện mở lại:** dựng bản release, chạy 8 kịch bản trên `SM-A165F`.

---

## 7. Phát hiện ngoài phạm vi — ngân sách hiệu năng PRD §7.1 KHÔNG ĐẠT trên máy thật

Chạy `:data:connectedDebugAndroidTest` với **cả hai máy đang cắm** cho một kết quả không ai đi tìm:

| Máy | `HistoryPipelineScaleTest` ở 8 640 điểm |
|---|---|
| `emulator-5554` | ✅ xanh |
| `SM-A165F` | ❌ **1 870 ms**, chạy lại **1 861 ms** — ngân sách PRD §7.1 là **< 1 000 ms** |

23/24 ca instrumented còn lại xanh trên cả hai máy (`LocationPointDaoTest` ×3, `ZoneDaoTest` ×5,
`ZoneEventDedupeTest` ×2, `ZoneEventRaceConditionTest` ×1, mỗi máy một lượt).

**Không do plan này gây ra:** `git diff d5236bc~1..HEAD --name-only` không có file nào thuộc đường
Lịch sử, và sản lượng `location_points` không đổi (vẫn 1 mẫu / 2,5 s / thành viên).

**Điều đáng nói là vì sao trước giờ không ai thấy.** Kết luận "dư ~2×" của `fix-phase-08` (498 ms)
là con số đo **trên emulator**. Đây đúng là loại sai lầm mà `LLM.md` §13 Fixed #15 đã ghi một lần
rồi — "100x margin" của `test-phase-08-report.md` sai vì đo nhầm đoạn; lần này đo đúng đoạn nhưng
sai máy. Ghi thành **§13 Open #24**, kèm điều kiện đóng: đo lại từng chặng trên `SM-A165F`, và
**không đóng bằng một phép đo trên emulator nữa**.
