---
title: "Di chuyển mượt, bám đường của thành viên được theo dõi"
description: "Sửa P0 mất vị trí trong nhà, cho thành viên mô phỏng bám polyline tuyến đường, nội suy marker ở tầng hiển thị."
status: pending
priority: P1
effort: 25h
branch: main
tags: [tracking, simulation, routing, compose, maps, p0-bugfix]
created: 2026-08-25
---

# Di chuyển mượt, bám đường của thành viên được theo dõi

**Yêu cầu gốc:** thành viên được theo dõi đang đi lung tung → phải mượt, không giật, **chỉ đi trên
đường**; nhưng ràng buộc đó **chỉ áp dụng cho dữ liệu test** — GPS thật vẫn phải tracking và hiển
thị kể cả khi người dùng ở trong toà nhà.

**Yêu cầu:** [`docs/prd-delta-smooth-road-movement.md`](docs/prd-delta-smooth-road-movement.md) (US-40→US-47, F7) ·
**Nghiệm thu:** [`docs/qa-uat-smooth-road-movement.md`](docs/qa-uat-smooth-road-movement.md) (QA-SRM-01→40, UAT-01→08) ·
**Research:** [01 bám đường](research/researcher-01-road-following-simulation.md) · [02 nội suy marker](research/researcher-02-marker-interpolation.md) · [03 GPS thật trong nhà](research/researcher-03-real-gps-indoor.md)

## Ranh giới cứng

**Bám đường CHỈ áp dụng cho dữ liệu mô phỏng.** Không map-matching, không snap-to-road, không
"làm đẹp" cho bất kỳ điểm nào đến từ nguồn vị trí thật, ở bất kỳ tầng nào. Điểm thật trong nhà
hiển thị **đúng như thiết bị báo về**. Bước nào của plan này vi phạm điều đó là bước sai.

## Quyết định đã chốt (chi tiết + lý do ở [`decisions.md`](decisions.md))

| # | Quyết định | Thay cho |
|---|---|---|
| D1 | **Giữ `MEMBER_ROAM_INTERVAL_MS = 2_500`.** Chỉ hạ TỐC ĐỘ; `STEP_METERS` suy ra từ `SIM_MEMBER_SPEED_MPS` | researcher-01 §F: 250ms/1.5m — tăng 10× số ghi Room mà không mua thêm độ mượt nào (D3 đã lo) |
| D2 | **Bước đi bảo toàn đỉnh polyline** (`vertex-preserving`): mẫu ghi ra luôn rơi đúng đỉnh khi bước vượt qua đỉnh | Nội suy thẳng giữa 2 mẫu cách 20m — cắt góc, trượt QA-SRM-02 |
| D3 | **Nội suy vị trí + bearing ở composable** (`withFrameNanos`), Room nhận mẫu thô không đổi | Nội suy trong ViewModel (cấm import Compose) hoặc trong `:data` (phình Room) |
| D4 | **`MAX_ACCURACY_M` giữ 50, chỉ chi phối việc GHI.** Thêm cổng hiển thị `observeLiveSelfLocation()` không qua bộ lọc | researcher-03 B2 (nới ngưỡng/phân tầng) — phá US-31 và làm bẩn polyline Lịch sử |
| D5 | **Nguồn đường 3 tầng:** GraphHopper live → cache trên máy → **đường cong tổng hợp tự sinh** (`SyntheticPath`, thuần hình học, không dữ liệu OSM) | Đóng gói polyline của nhà cung cấp vào `assets/` — chặn bởi LLM.md §13 Open #11 |
| D6 | **KHÔNG** thêm gating độ chính xác vào `ZoneEvaluator` | researcher-03 §F — kịch bản đó không tồn tại được: từ fix-zone-follows-members, không điểm GPS thật nào chạm `ZoneEvaluator` (§8.1) |
| D8 | **Mất internet ⇒ CHẶN màn Bản đồ bằng dialog `cancelable = false`, tự tắt khi internet trở lại.** Chỉ màn Bản đồ; theo dõi GPS thật vẫn chạy nền; **không** áp cho lỗi nhà cung cấp | Hạ cấp im lặng khi ngoại tuyến (US-45 bản đầu, QA-SRM-13 bản đầu) — chủ dự án bác 2026-08-25 |
| D7 | **KHÔNG** ẩn/xám marker theo độ cũ (staleness) vòng này | researcher-02 Q1/Q2, researcher-03 Q3 — X2 của PRD delta cấm ẩn marker; thêm hành vi ẩn là tái tạo đúng cái lỗi đang sửa |

**Xác nhận của chủ dự án — 2026-08-25 (chưa code):** D8 lật một nửa của D5. Mất internet **không**
còn rơi xuống tầng 3 mà bị **chặn** — dialog không đóng được, tự tắt khi mạng về, chỉ trên màn Bản
đồ, và theo dõi GPS thật vẫn chạy nền. Tầng 3 vẫn sống cho ba ca *vẫn có internet* (không khoá /
nhà cung cấp lỗi / tuyến bị từ chối), và **giới hạn tầng 3 được chấp nhận**: US-41 chỉ đạt ở tầng
1/2. **D8 đã được xếp phase** — lý do, ranh giới và cách hiện thực:
[`phase-07`](phase-07-chan-man-ban-do-khi-mat-internet.md).

> 📌 **Ghi lại một sự cố tài liệu, để lần sau ai đọc `git log` không phải đoán.** `decisions.md` bị
> ghi đè lúc 2026-08-25 11:49 về một bản cũ hơn (309 → 240 dòng), mất toàn bộ khối D8 và làm bảng
> 3 tầng của D5 quay về mô tả cũ ("vòng khép kín quanh tâm zone") — bản đó mâu thuẫn với API
> `SyntheticPath.between(from, to, seed)` mà `phase-02` đã chốt từ 10:46. **Đã phục hồi cùng ngày**
> theo quyết định của chủ dự án; `decisions.md` §C2 lại là nguồn chuẩn của D8. Bài học: `plans/`
> chưa vào git nên không có `git checkout` để cứu — commit sớm thư mục plan là cách phòng duy nhất.

## Các phase

| Phase | Nội dung | Ship được độc lập | Status |
|---|---|---|---|
| [01](phase-01-hien-thi-vi-tri-that-trong-nha.md) | **P0** — cổng hiển thị vị trí thật không qua `LocationFilter`, vòng sai số, test cấm snap | ✅ Không phụ thuộc routing | ✅ DONE |
| [02](phase-02-buoc-di-tren-polyline-va-bearing.md) | `:domain` thuần — `PolylineFollower`, `GeoBearing`, `SyntheticPath`, spawn-một-lần, bearing/tốc độ thật | ✅ Sau 01 | ✅ DONE |
| [03](phase-03-noi-suy-marker-o-tang-hien-thi.md) | `:ui` — `rememberAnimatedMemberPositions()`, marker xoay, chấm xanh cũng nội suy | ✅ Sau 02 | ✅ DONE (S1–S3,S5–S8 ✓; S4 CHƯA NGHIỆM THU) |
| [04](phase-04-nguon-tuyen-duong-lai.md) | `:data` — `MemberRouteSource` 3 tầng + cache trên máy + hạ cấp im lặng | ✅ Sau 02 | ⏳ PENDING |
| [05](phase-05-ghi-cong-osm-tren-man-ban-do.md) | Dải ghi công OSM trên màn Bản đồ, chỉ hiện khi thật sự có dữ liệu OSM | ✅ Sau 04 | ⏳ PENDING |
| [07](phase-07-chan-man-ban-do-khi-mat-internet.md) | **P0, D8** — `NetworkMonitor` (`:domain`) + `NetworkCallback` (`:data`) + dialog `cancelable=false` chỉ trên màn Bản đồ | ✅ Sau 05 | ⏳ PENDING |
| [06](phase-06-do-luong-gate-va-tai-lieu.md) | Đo thời gian một vòng, đo khung hình, gate build/test, đóng nợ tài liệu | — | ⏳ PENDING |

Phụ thuộc: `01` độc lập · `02 → 03`, `02 → 04 → 05 → 07` · `06` sau tất cả.
**Thứ tự thực thi: 01 → 02 → 03 → 04 → 05 → 07 → 06.** `07` phụ thuộc `05` không phải vì logic mà vì
**file**: cả hai sửa `MapScreen.kt` và `MapViewModel.kt`. Đảo thứ tự thì phase 05 phải giải xung đột
trên đúng hai file đó.
**Không gộp 01 vào bất cứ phase nào** — nó là vi phạm yêu cầu đang sống và không được chờ câu hỏi
pháp lý của phase 04/05.

## Chặn

| # | Việc | Chặn | Trạng thái |
|---|---|---|---|
| B1 | Điều khoản redistribution của GraphHopper (LLM.md §13 Open #11) | **Phát hành** bản có tầng 1/2 bật | ⬜ Chưa gửi thư. **Không chặn phase 01–03 và 06** — chúng không chạm dữ liệu nhà cung cấp |
| B2 | Khoá API routing không có trong CI / bản clone mới (§13 Open #10) | Không chặn gì | ✅ Đóng bởi D5 tầng 3: đường cong tổng hợp **tự sinh**, không cần khoá, không cần mạng |
| B3 | Máy thật + vị trí trong nhà để nghiệm thu US-43 | UAT-05 | ⬜ Cùng loại chặn đã làm G5 hoãn ở phase-11. Emulator không sinh được `accuracy > 50m` tin cậy |
| B4 | Chốt `SIM_MEMBER_SPEED_MPS` sau khi đo vòng (PRD delta Q10) | 06 | ✅ **ĐÓNG 2026-08-26 — giữ 8.3.** Đo tất định (`MemberRoamerLapTimeTest`): ENTER→EXIT = **120,0 s** ở zone 150 m, dưới trần 180 s của luật C5 ⇒ nhánh "giữ 8.3". Zone 50 m: 92,5 s. Con số chỉ đáng tin sau khi `LLM.md` §13 Fixed #32 được sửa — đường tổng hợp và polyline GraphHopper thật nay cho ĐÚNG cùng 120,0 s, trước đó lệch ~2× |
| B5 | **D8 chưa có phase.** Chạm manifest (`ACCESS_NETWORK_STATE`) + `:domain` + `:data` + `:ui`; repo chưa có `ConnectivityManager` ở bất kỳ đâu | US-47, QA-SRM-13/17/37→40 | ✅ **Đã xếp phase — [`phase-07`](phase-07-chan-man-ban-do-khi-mat-internet.md)**, chạy sau 05, trước 06. Còn **một** điểm chờ chủ dự án: `AlertDialog` modal chặn luôn thanh tab (phase-07 §Risk #1) |

## Không làm vòng này

Đổi lược đồ Room · bám đường cho F5 (`RouteBlueprint`, US-33) · định vị trong nhà bằng phần cứng ·
routing profile xe máy (free tier không có) · `compose-stability.conf` (§13 Fixed #20) ·
backfill user story cho màn Dẫn đường (BA sở hữu) · ẩn marker theo staleness (D7).
