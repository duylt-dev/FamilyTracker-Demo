# G1 — Bảng đối chiếu story P0

**Tiêu chí PRD §11.1 G1:** "Toàn bộ story P0 đạt acceptance criteria." Kiểm trên **APK release**,
theo đúng 3 flow ở PRD §4.3.

## Lệch số đếm trong PRD — ghi nhận, không tự sửa PRD

PRD §2 dòng cuối ghi "Tổng: 36 user story — 22×P0, 11×P1, 3×P2." Đếm lại trực tiếp cột **P** của
từng dòng US-01→US-36 (`grep -E "^\| US-" docs/FTD001_...md`): **26×P0, 8×P1, 2×P2** (tổng vẫn 36,
đúng — chỉ sai ở cách chia theo mức ưu tiên). Bảng dưới dùng **26 dòng thật** (nguồn P0 = cột ưu
tiên của từng story, không phải câu tổng kết) vì đó là hợp đồng chấp nhận thật (acceptance criteria
đi kèm cột đó). PRD là tài liệu do BA sở hữu (có version history) — không tự sửa; ghi lệch ở đây và
ở `LLM.md` §13 Open mới, đề nghị BA sửa câu tổng kết ở lần cập nhật PRD kế tiếp.

## Bảng 26 story P0

| ID | Acceptance criteria (rút gọn) | Tầng | Kết quả | Bằng chứng | Người kiểm · Ngày |
|---|---|---|---|---|---|
| US-01 | Màn giải thích hiện TRƯỚC dialog hệ thống, có "Tiếp tục"/"Để sau" | b | Đạt | `dev-phase-04-report.md` — luồng 3 bước xác nhận thứ tự đúng qua `dumpsys package` + logcat, không đoán từ UI | dev-phase-04, 2026-08-21 |
| US-02 | Cấp quyền chính xác qua dialog hệ thống, từ chối → Map giảm chức năng + banner | b | Đạt | `dev-phase-04-report.md` bảng "Luồng quyền 3 bước" — dialog Android 13+ thật, `permission_result type=FINE_LOCATION granted=true` | dev-phase-04, 2026-08-21 |
| US-03 | Hướng dẫn "Cho phép mọi lúc" CHỈ hiện sau US-02, mở đúng `ACTION_APPLICATION_DETAILS_SETTINGS` | b | Đạt | cùng bảng trên — bước 3 xác nhận không có dialog hệ thống (đúng hành vi Android 11+), quay lại app đọc lại quyền đúng lúc (bẫy `ON_RESUME` đã sửa, Fixed #7) | dev-phase-04, 2026-08-21 |
| US-04 | Android 13+ xin `POST_NOTIFICATIONS`; từ chối → banner "sự kiện vẫn ghi Timeline" | b | Đạt | cùng bảng trên, bước 1 | dev-phase-04, 2026-08-21 |
| US-06 | Marker xanh dương tại vị trí thiết bị, camera tự canh lần đầu | b | Đạt | `dev-phase-05-report.md`; tái xác nhận phase-11: `scratchpad/g8-map-screen.png` (marker xanh dương giữa khung, camera đã canh) | dev-phase-05, 2026-08-21 · re-kiểm dev-phase-11, 2026-08-22 |
| US-07 | Mỗi zone là `Circle` viền + nền bán trong suốt, tên ở tâm | b | Đạt | `dev-phase-05-report.md`, `dev-phase-06-report.md`; tái xác nhận: `scratchpad/g8-map-screen.png`, `g7-zone-tab.png` — "Zone mẫu" vẽ đúng hình tròn cam, tên hiện ở tâm | dev-phase-05/06 · re-kiểm dev-phase-11, 2026-08-22 |
| US-09 | Công tắc bật → foreground service + thông báo thường trực; tắt → dừng ≤2s | b | Đạt | `dev-phase-04-report.md`: `isForeground=true foregroundId=1001`, tắt → dừng trong ~1s. Tái xác nhận phase-11: `scratchpad/g7-tracking-toggled2.png` (switch ON), notification "Đang theo dõi vị trí" trong shade (`g4-notif-shade1.png`) | dev-phase-04, 2026-08-21 · re-kiểm dev-phase-11, 2026-08-22 |
| US-10 | Nhấn giữ ≥500ms mở Zone Editor với tâm là điểm vừa chọn | b | Đạt | `dev-phase-05-report.md`, `dev-phase-06-report.md`: `p6-20-longpress-editor.png`. Tái xác nhận phase-11 (US-21 boundary test): `scratchpad/g1-us21-longpress.png` — long-press mở đúng "Zone mới", crosshair đúng vị trí nhấn giữ | dev-phase-05/06 · re-kiểm dev-phase-11, 2026-08-22 |
| US-11 | Lối vào Zone List, History, Timeline từ bản đồ | b | Đạt | `dev-phase-05-report.md`; tái xác nhận phase-11: 4 tab dưới cùng hoạt động (`g7-zone-tab.png`, `g7-history.png`, `g7-timeline.png`) | dev-phase-05 · re-kiểm dev-phase-11, 2026-08-22 |
| US-12 | Danh sách zone: tên, bán kính, "Đang ở trong/Ở ngoài", công tắc thông báo | b | Đạt | `dev-phase-06-report.md`: `p6-36-zonelist-two-zones.png`. Tái xác nhận phase-11: `scratchpad/g1-us21-edit-saved.png` — 100 dòng đều hiện đúng tên/bán kính/trạng thái/switch | dev-phase-06, 2026-08-21 · re-kiểm dev-phase-11, 2026-08-22 |
| US-13 | Bấm dòng → Editor chế độ sửa | b | Đạt | `dev-phase-06-report.md`: `p6-24-edit-mode.png`. Tái xác nhận phase-11 ở **đúng biên 100 zone**: `scratchpad/g1-us21-edit-mode.png` — "Sửa zone", field nạp đúng, Lưu hoạt động (`g1-us21-edit-saved.png`) | dev-phase-06 · re-kiểm dev-phase-11 (bổ sung biên 100), 2026-08-22 |
| US-14 | Vuốt xoá + xác nhận; xoá geofence tương ứng | b | Đạt | `dev-phase-06-report.md`: `p6-26/27/30/31` — dialog xác nhận, biến khỏi list VÀ khỏi Map ngay (Room single source of truth) | dev-phase-06, 2026-08-21 |
| US-16 | Tên bắt buộc 1–40 ký tự, trống → Lưu vô hiệu | b | Đạt | `dev-phase-06-report.md`: `p6-03`, `p6-10`. Tái xác nhận gián tiếp phase-11: `g1-us21-longpress.png` — trường tên trống, "Lưu" xám dù còn lý do khác (>100 zone) cùng lúc | dev-phase-06 · re-kiểm dev-phase-11, 2026-08-22 |
| US-17 | Slider 50–2000m bước 10, hình tròn realtime, cảnh báo <100m | b | Đạt | `dev-phase-06-report.md`: `p6-12`, `p6-16-low-radius-final.png` (cảnh báo đỏ đúng ngưỡng 100m) | dev-phase-06, 2026-08-21 |
| US-18 | Tâm = tâm màn hình + crosshair, kéo bản đồ đổi tâm | b | Đạt | `dev-phase-06-report.md`: `p6-20-longpress-editor.png`, debounce đo thật (Debt #2) | dev-phase-06, 2026-08-21 |
| US-19 | 2 công tắc độc lập vào/ra | b | Đạt | `dev-phase-06-report.md`: `p6-20`/`p6-24`; `ZoneEditorViewModelTest` khoá riêng từng cờ. Tái xác nhận: `g1-us21-longpress.png`/`g1-us21-edit-mode.png` — cả 2 switch hiện đúng, độc lập | dev-phase-06 · re-kiểm dev-phase-11, 2026-08-22 |
| US-22 | Thông báo "Đã đến {zone}" + giờ, bấm vào mở Timeline | b | Đạt | `phase-07-report.md` (Bug 2 tap-notification đã sửa); phase-11 rehearsal G4: notification shade `g4-notif-shade1.png` — "Đã đến Zone mẫu 05:20" | phase-07 · re-kiểm dev-phase-11, 2026-08-22 |
| US-23 | Thông báo "Đã rời {zone}" + giờ | b | Đạt | `phase-07-report.md`: `p7-07-single-notification.png` ("Đã rời Saigon Office", đúng 1 thông báo). Tái xác nhận: `g4-notif-shade1.png` — "Đã rời Zone mẫu 05:21" | phase-07 · re-kiểm dev-phase-11, 2026-08-22 |
| US-24 | Thông báo kể cả khi app đã đóng (force-stop không tính, ≤3 phút qua vuốt khỏi recents) | **c bắt buộc** | **CHƯA kiểm đủ — HOÃN** | Máy thật `RF8Y60B9NCZ` khoá màn hình bằng mật khẩu thật, không mở khoá được trong phiên này. Đã xác nhận được PHẦN KHÔNG CẦN chuyển động: cài release không crash, quyền cấp được, **geofence đăng ký được với dữ liệu thật** (`geofence_registered zoneId=all count=1 success=true`, xem `dev-phase-11-report.md` mục G5), không thông báo ma. Phần lõi (đóng app + đi bộ qua ranh giới) **không kiểm được** — trùng nguyên nhân G5 | dev-phase-11, 2026-08-22 — **HOÃN, xem "Cần chủ dự án làm"** |
| US-25 | Không 2 thông báo giống nhau cho 1 lần qua ranh giới trong 60s | a+b | Đạt | `ZoneEventDeduperTest` (4 test, domain), `phase-07-report.md` Bug 1 (race TOCTOU) đã sửa bằng Mutex + `ZoneEventRaceConditionTest` (mutation đỏ/xanh). Tái xác nhận phase-11: 2 lần mô phỏng liên tiếp trên release → Timeline không có dòng trùng (`g7-timeline.png`) | dev-phase-03/phase-07 · re-kiểm dev-phase-11, 2026-08-22 |
| US-26 | Hysteresis: vào `d<R`, ra `d>R+30m`; đứng yên ở mép ≤1 sự kiện | a | Đạt | `ZoneEvaluatorTest`: "standing at the edge oscillating within radius plus-minus 5m for 30 points fires exactly one ENTER and zero EXIT" + 2 test biên loại trừ (exitsAt/entersAt exactly at radius) — chạy `:domain:test`, 58/58 xanh (phase-11) | dev-phase-03 · re-xác nhận dev-phase-11, 2026-08-22 |
| US-27 | Date picker giới hạn 7 ngày gần nhất, mặc định hôm nay | b | Đạt | `dev-phase-08-report.md`: `p8-02`, `p8-03` — đúng 7 dòng, không có ngày thứ 8 | dev-phase-08, 2026-08-21 |
| US-28 | Polyline nối điểm theo thời gian, marker Start xanh/End đỏ | b | Đạt | `dev-phase-08-report.md`: `p8-04-history-yesterday.png`. Tái xác nhận phase-11: `g7-history.png` (marker xanh đầu tuyến, đỏ cuối tuyến) | dev-phase-08 · re-kiểm dev-phase-11, 2026-08-22 |
| US-31 | Lộ trình sạch — không đoạn nhảy lung tung | a+b | Đạt | `LocationFilterTest` (domain, lọc `accuracy`/`distance`/`speed`), `dev-phase-08-report.md` quan sát trực quan polyline mượt | dev-phase-03/08, 2026-08-21 |
| US-33 | Nút "Mô phỏng lộ trình" sinh lộ trình ~30s, qua ≥1 zone, thông báo vào+ra thật, ghi lịch sử thật | b | Đạt | `dev-phase-09-report.md` (log đầy đủ `zone_saved`→`geofence_registered`→2×`zone_event_raised`→2×`notification_posted`). Tái xác nhận phase-11 **trên bản release vừa build lại** (khác bản debug của phase-09): 2 lần mô phỏng, gap ENTER→EXIT 17.4s/17.5s, Timeline sạch (`g7-timeline.png`, xem G4 trong `dev-phase-11-report.md`) | dev-phase-09 · re-kiểm dev-phase-11 (bản release mới), 2026-08-22 |
| US-34 | Nhật ký vào/rời theo thứ tự mới nhất trước: icon, tên zone, giờ, ngày | b | Đạt | `dev-phase-10-report.md`; tái xác nhận phase-11: `g7-timeline.png` (icon đỏ/xanh, tên "Zone mẫu", giờ, đúng thứ tự mới nhất trước); mutation Fixed #19 kiểm lại riêng — xem mục §13 dưới | dev-phase-10 · re-kiểm dev-phase-11, 2026-08-22 |

## Tổng kết

- **26/26 dòng "Đạt"**, trừ **US-24 = HOÃN** (thật ra 25/26 Đạt, 1 HOÃN).
- US-24 là dòng DUY NHẤT bắt buộc tầng (c) theo đúng Risk Assessment của phase file ("cột 'tầng' bắt
  phải ghi (a/b/c); G5 chỉ nhận (c)") — không tick khống bằng bằng chứng tầng (b).
- Không dòng nào FAIL. Không phát hiện regression nào so với các phase 04–10 khi re-kiểm trên bản
  release build lại ở phase-11 (sau khi thêm `FtdLog`).
