package com.example.pion.family.tracker.demo.ui.designsystem.component

/*
 * Tách khỏi `AnimatedMarkerPositions.kt` ở feedback #2 (2026-08-26) vì file đó chạm 209 dòng sau khi
 * `git mv` sang `designsystem/component/` — `LLM.md` §13 Open #16 đặt sẵn điều kiện: "lần TIẾP THEO
 * ai đó thêm nội dung vào file này thì phải tách thật, không được nới luật".
 *
 * **Đường cắt chọn ở đây, không phải ở `MarkerSample`/`AnimatedMarkerPosition`.** §13 Open #16 đã
 * cảnh báo đúng rằng tách ba kiểu dữ liệu ra sẽ đẩy KDoc "KHÔNG được thêm `LatLng` vào đây" ra xa
 * đúng vòng lặp bị cám dỗ làm việc đó. Hai hằng số dưới đây thì ngược lại: KDoc của chúng nói về
 * phép suy từ hằng số `:domain` (tốc độ × nhịp, quy mô một cú spawn), không nói gì về vòng nội suy —
 * và chúng đã có file test riêng (`AnimatedMarkerPositionsThresholdTest`) từ phase-03. Đây là mối
 * nối rẻ nhất mà không lớp bảo vệ nào bị kéo ra khỏi chỗ nó bảo vệ.
 */

/**
 * Quyết định (A) — F-6: mẫu spawn của `MemberRoamer.tick` (`:domain/tracking/MemberRoamer.kt`) ghi
 * `bearingDegrees = 0f`/`speedMps = 0f` cứng khi dời một thành viên tới gần một zone mới quá xa để
 * đi bộ tới. Nội suy góc TỚI mẫu đó sẽ quay marker về hướng bắc đúng MỘT LẦN mỗi thành viên mỗi lần
 * chạy — chốt: sửa Ở ĐÂY (`:ui`), KHÔNG sửa `:domain`. `MAX_WALK_M` (5 000 m — `internal`, `:ui`
 * không thấy qua biên module) chỉ ĐỌC thủ công từ `MemberRoamer.kt`, không import.
 *
 * **Cận trên một bước đi liên tục** — [NORMAL_TICK_STEP_M]: `SIM_MEMBER_SPEED_MPS` (8.3 m/s) ×
 * `MEMBER_ROAM_INTERVAL_MS` (2 500 ms) ÷ 1000 = 20.75 m (`TrackingConstants.kt`).
 * `PolylineFollower.advance` bảo toàn đỉnh (`decisions.md` §C1) nên một bước LIÊN TỤC không bao giờ
 * vượt số này.
 *
 * **Cận dưới một cú spawn THẬT, trường hợp bất lợi nhất:** `MAX_WALK_M` (5 000 m) trừ
 * `approachRadiusMeters` LỚN NHẤT — `ZONE_RADIUS_MAX_M` (2 000 m) × 1.4 + `LEAVE_MARGIN_M` (120 m)
 * = 2 920 m — vì `pointAtBearing` đặt điểm spawn NGẪU NHIÊN quanh đích, nên khoảng cách thật không
 * bao giờ dưới `5 000 − 2 920 = 2 080 m`.
 *
 * **Ngưỡng chọn:** 10× cận trên bước bình thường (`20.75 × 10 = 207.5 m`) — vẫn cách cận dưới
 * spawn thật (2 080 m) một biên ~10× nữa. Không dùng chung với `MEMBER_RENDER_MAX_JUMP_M` (2.0 m —
 * ngưỡng NGHIỆM THU của bước MỖI KHUNG HÌNH, sống trong file test, NFR-3): hai ngưỡng đo hai đại
 * lượng khác nhau — mỗi khung (~16.7ms) so với mỗi MẪU (~2 500ms mô phỏng, ~10 000ms GPS thật).
 */
internal const val NORMAL_TICK_STEP_M: Double = 20.75
internal const val SPAWN_SNAP_THRESHOLD_M: Double = NORMAL_TICK_STEP_M * 10.0
