# Dev Report — Phase 03: Nội suy marker ở tầng hiển thị (`:ui`)

Ngày: 2026-08-25

## Trạng thái: completed (phần code + test của dev; Step 7/8 — xác nhận `flat` bằng mắt và đo
`dumpsys gfxinfo` trên thiết bị — để orchestrator làm, theo đúng "KHÔNG làm" của brief)

## Files touched

### Tạo mới
- `ui/src/main/java/com/example/pion/family/tracker/demo/ui/core/motion/MarkerInterpolation.kt` (90 dòng) — thuần JVM: `lerpDegrees`, `lerpBearing`, `progressOf`, `haversineMeters`, `isSpawnJump`
- `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/AnimatedMarkerPositions.kt` (196 dòng) — `MarkerSample`, `AnimatedMarkerPosition`, `rememberAnimatedMarkerPositions()`
- `ui/src/test/java/com/example/pion/family/tracker/demo/ui/core/motion/MarkerInterpolationTest.kt` — 16 test case, JUnit thuần

### Sửa
- `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/MemberMarkers.kt` — **124 dòng** (dưới 200, NFR-4). Xoá KDoc "jump, không animate"; `rotation`/`flat = true`/`anchor` mới; `MemberDot` thêm mũi chỉ hướng
- `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/FamilyTrackerMap.kt` — 153 dòng. Self dùng chung `rememberAnimatedMarkerPositions`, không truyền `rotation`
- `ui/src/main/java/com/example/pion/family/tracker/demo/ui/designsystem/theme/Dimens.kt` — chỉ THÊM `MemberDotHeadingWidth`/`MemberDotHeadingHeight`, không sửa/xoá hằng số nào đang có
- `LLM.md` §3 — thêm `core/motion/`, `AnimatedMarkerPositions.kt`, sửa dòng mô tả `MemberMarkers.kt`/`FamilyTrackerMap.kt`
- `plans/260825-0956-smooth-road-following-member-movement/docs/prd-delta-smooth-road-movement.md` §4.2 — sửa nợ tài liệu (B)

`Color.kt` — **không sửa**: mũi chỉ hướng dùng `MaterialTheme.colorScheme.surface` (đã có, cùng màu viền dot), không cần màu mới.

**Không chạm** `domain/`, `data/`, `app/`, `domain/src/test/`, `data/src/test/` — xác nhận bằng `git status --porcelain`: ba file đang được dev khác sửa song song (`data/.../MemberMovementSimulatorTest.kt`, `domain/.../MemberRoamerTest.kt`, `domain/.../PolylineFollowerTest.kt`) xuất hiện là "modified" nhưng KHÔNG do tôi động vào — tôi chưa từng đọc hay ghi ba file đó.

## Tasks completed (Todo List phase file)

- [x] `MarkerInterpolation.kt` + `MarkerInterpolationTest` (gồm ca QA-SRM-22)
- [x] `AnimatedMarkerPositions.kt` — một vòng `withFrameNanos`, state chỉ primitive
- [x] `from` = vị trí đang hiển thị khi retarget (chống giật lùi)
- [x] Không ngoại suy: `progress = 1` thì dừng
- [x] `MemberMarkers.kt` — `rotation` + `flat = true` + `keys` chỉ `member.id`
- [x] `MemberDot` có mũi chỉ hướng; `Dimens` cập nhật (`Color.kt` không cần đổi)
- [x] Chấm xanh self nội suy vị trí (không xoay)
- [x] Xoá KDoc "jump, không animate" ở `MemberMarkers.kt`
- [ ] Xác nhận `flat` bằng mắt trên thiết bị (xoay bản đồ) — **để orchestrator**, brief cấm tôi dùng `adb`/cài app
- [ ] Đo `dumpsys gfxinfo`, áp luật chốt Step 8 — **để orchestrator**, cùng lý do trên
- [x] `LLM.md` §3 + PRD delta §4.2 cùng lần sửa này

## Test output THẬT

### `./gradlew :ui:test --no-configuration-cache`

```
> Task :ui:compileDebugKotlin
> Task :ui:compileDebugUnitTestKotlin
> Task :ui:testDebugUnitTest
> Task :ui:test

BUILD SUCCESSFUL in 2s
17 actionable tasks: 7 executed, 10 up-to-date
```

Đếm lại toàn bộ XML kết quả (`ui/build/test-results/testDebugUnitTest/TEST-*.xml`) bằng script:
**97 test, 0 failures, 0 errors** — trong đó `MarkerInterpolationTest` đóng góp đúng **16/16 pass**
(namespace `com.example.pion.family.tracker.demo.ui.core.motion.MarkerInterpolationTest`, thời
gian chạy 0.02s). Danh sách 16 ca: 5 `lerpBearing`, 5 `progressOf`, 1 `lerpDegrees` (vòng 4 đoạn ×
100 giá trị `t` = QA-SRM-22), 3 `isSpawnJump`, 2 `haversineMeters`.

### `./gradlew :app:assembleDebug --no-configuration-cache`

```
> Task :app:compileDebugKotlin
...
> Task :app:packageDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL in 4s
77 actionable tasks: 5 executed, 72 up-to-date
```

Toàn dự án (3 module `:domain`/`:data`/`:ui` + `:app`) compile sạch, không lỗi kiểu, không cảnh báo
mới liên quan tới các file tôi sửa.

## Ngưỡng snap cho quyết định (A) — F-6

`SPAWN_SNAP_THRESHOLD_M = 207.5` (m), định nghĩa `private const val` trong
`AnimatedMarkerPositions.kt`, suy ra từ:

- **Cận trên một bước liên tục bình thường** = `SIM_MEMBER_SPEED_MPS` (8.3 m/s) ×
  `MEMBER_ROAM_INTERVAL_MS` (2 500 ms) ÷ 1000 = **20.75 m** — đọc trực tiếp từ
  `domain/tracking/TrackingConstants.kt`, không sửa file đó. `PolylineFollower.advance` bảo toàn
  đỉnh (`decisions.md` §C1) nên một bước ĐANG DI CHUYỂN LIÊN TỤC không bao giờ vượt số này.
- **Cận dưới một cú spawn THẬT (trường hợp bất lợi nhất)** = `MAX_WALK_M` (5 000 m, đọc từ
  `domain/tracking/MemberRoamer.kt:55`, `internal` — chỉ đọc thủ công, không import vì `:ui` không
  thấy `internal` của `:domain` qua biên module Gradle) trừ `approachRadiusMeters` LỚN NHẤT có thể
  — `ZONE_RADIUS_MAX_M` (2 000 m, `TrackingConstants.kt`) × `(1 + ZONE_TARGET_INSET` 0.4`)` +
  `LEAVE_MARGIN_M` (120 m, `MemberRoamer.kt:45`) = 2 920 m ⇒ cận dưới = 5 000 − 2 920 = **2 080 m**.
  Vì `pointAtBearing` (`MemberRoamer.tick`, nhánh spawn) đặt điểm spawn ở một hướng **ngẫu nhiên**
  quanh đích, khoảng cách thật từ vị trí cũ tới điểm spawn không bao giờ dưới mức này.
- **Chọn** = 10× cận trên (`20.75 × 10 = 207.5`) — cách cận dưới spawn thật (2 080 m) thêm một biên
  ~10× nữa. Hai biên an toàn 10× ở cả hai phía, không phải một con số đoán.

Hàm so sánh (`isSpawnJump(distance, threshold): Boolean = distance > threshold`) được tách ra
`MarkerInterpolation.kt` (thuần JVM) đúng như brief yêu cầu ("khoá bằng test nếu tách được") —
khoá bằng 3 test dùng `threshold = 207.5` (khớp giá trị thật, ghi rõ trong comment test vì hằng số
gốc là `private` trong file Compose, test không import được).

## Chiều quay khoá cho `lerpBearing(0f, 180f, …)`

`lerpBearing(0f, 180f, 0.5f) == 90f` — **chiều dương** (0°→90°→180°, không phải 0°→270°→180°).
Lý do: thuật toán tính `delta = (to - from) % 360`, chỉ lật dấu khi `delta > 180` hoặc
`delta < -180`; ở đúng 180° điều kiện `delta > 180` là `false` (180 không lớn hơn 180) nên delta
giữ nguyên +180 và đi chiều dương. Đây là lựa chọn tất định (một trong hai chiều phải được chọn vì
hai chiều dài bằng nhau ở đúng điểm đối cực) — khoá cứng bằng test để marker không quay ngẫu nhiên
hai chiều khác nhau tuỳ sai số làm tròn của lần tính đó.

## Số dòng cuối cùng của `MemberMarkers.kt`

**124 dòng** — dưới 200 (NFR-4, LLM.md §5). Phần animation (retarget + vòng lặp khung hình) tách
hẳn sang `AnimatedMarkerPositions.kt` (196 dòng).

## Quyết định thiết kế thêm, không có trong spec chữ nghĩa nhưng cần để đúng hành vi

**`anchor = Offset(0.5f, 0.5f)` cho marker thành viên** (mặc định của `MarkerComposable` là
`Offset(0.5f, 1.0f)` — bottom-center, đã xác nhận qua sources jar `maps-compose-8.3.1`,
`Marker.kt:322-341`). `Marker.rotation` (Google Maps SDK) xoay quanh CHÍNH `anchor`. Với anchor mặc
định (đáy hình tròn), khi bearing đổi mỗi khung, cả `MemberDot` sẽ "swing" quanh mép dưới của nó
thay vì xoay tại chỗ — trước phase-03 việc này vô hại vì `rotation` chưa bao giờ được set khác 0.
Đổi `anchor` sang tâm hình tròn để marker xoay tại chỗ, đúng cảm giác "mũi chỉ hướng xoay theo hướng
đi" mà FR-3/US-40 AC yêu cầu, không phải một cây kim quay lệch tâm. Rủi ro thấp: chỉ áp dụng cho
`MemberMarkers` (marker có xoay); self (`SelfDot`, không xoay) giữ nguyên anchor mặc định, hành vi
cũ không đổi.

**Mũi chỉ hướng vẽ TRONG cùng bound `Dimens.MemberDotSize`** (không mở rộng box ra ngoài như một
"cây kim" nhô lên trên hình tròn) — lý do: giữ nguyên kích thước bitmap marker để không phải tính
lại toạ độ anchor tương ứng với một hình học phức tạp hơn (circle + triangle nhô ra), tránh
over-engineering cho một chi tiết hình ảnh nhỏ (YAGNI). Kết quả: một tam giác nhỏ (8dp × 6dp, hằng
số mới trong `Dimens.kt`) nằm lồng vào mép trên của hình tròn, màu `MaterialTheme.colorScheme.surface`
(trùng viền, đã dùng sẵn — không cần màu mới trong `Color.kt`).

## Chỗ spec phase-03 sai hoặc mâu thuẫn với code thật — nói ra

1. **`PRD delta §4.2` dẫn `MemberRoamerLapTimeTest`** — file này **không tồn tại** trong repo
   (`grep -r "MemberRoamerLapTimeTest"` trước khi sửa chỉ ra đúng dòng đó và `decisions.md` §C5,
   không có file `.kt` nào tên vậy). Đã sửa theo quyết định (B): trỏ đúng tới các test đếm nhịp có
   sẵn trong `MemberRoamerTest` và giao việc đo thật cho phase-06.
2. **Architecture diagram của phase file liệt kê `lerpDegrees(a, b, t)` và `lerpBearing(a, b, t)`
   cùng chữ ký `(a, b, t)`** nhưng không nói rõ `lerpDegrees` KHÔNG xử lý vòng qua 0°/360° (chỉ
   `lerpBearing` mới xử lý) — tôi suy luận từ Requirements (FR-5 "đoạn thẳng nối hai mẫu", tức
   không được đi vòng) và implement `lerpDegrees` là lerp THẲNG (dùng cho lat/lng), `lerpBearing`
   mới đi đường ngắn (dùng cho hướng marker). Nếu đọc nhầm và áp `lerpBearing`-style cho lat/lng thì
   QA-SRM-22 sẽ SAI ở các cặp toạ độ cách nhau > 180° kinh độ (hiếm nhưng có thể, ví dụ demo dời máy
   ảo). Đã khoá đúng bằng test S2 (4 đoạn, gồm 1 đoạn "cực đoan" Sydney↔NYC).
3. **`Dimens.kt` "chỉ được thêm hằng số"** — tuân thủ đúng, nhưng lưu ý `Dimens.kt` KHÔNG có test
   kiểm tra việc này; tôi tự kiểm bằng `git diff` (chỉ có 2 dòng `val` mới, không dòng nào bị đổi).
4. **Không có mismatch nào khác được phát hiện** giữa spec, `decisions.md`, và code thật của
   `MemberRoamer`/`TrackingConstants`/`LocationPoint` — mọi tên field (`bearingDegrees` không phải
   `bearing`) và giá trị hằng số đã đọc trực tiếp từ file nguồn trước khi dùng trong KDoc/test.

## Việc KHÔNG làm được và lý do

- **Step 7 (xác nhận `flat = true` bằng mắt, xoay bản đồ trên `emulator-5554`)** — brief cấm dùng
  `adb`/cài app. Đã implement `flat = true` đúng theo Key Insight #7 (đã double-check bằng
  `decisions.md` "Sai lệch phát hiện trong chính research" — xác nhận `flat = true` là đúng, không
  phải `false`), nhưng CHƯA xác nhận bằng mắt. Orchestrator cần làm bước này trước khi đóng phase.
- **Step 8 (đo `dumpsys gfxinfo`, áp luật chốt NFR-1)** — cùng lý do, cấm `adb`. Không có số đo thật
  để báo cáo janky-frame %; orchestrator cần chạy và, nếu > 5%, áp nhánh giảm cấp (bỏ khung xen kẽ
  hoặc bỏ nội suy góc) đã mô tả ở Step 8 của phase file.
- **Không sửa `Color.kt`** — không cần màu mới nên không có gì để làm ở đây; ghi lại để rõ đây là
  chủ động không làm, không phải bỏ sót.

## Câu hỏi chưa giải được

- Không có — mọi quyết định thiết kế phát sinh ngoài spec chữ nghĩa (anchor, hình học triangle) đã
  tự quyết theo KISS/YAGNI và ghi lý do ở trên; nếu orchestrator/chủ dự án muốn đổi hướng sau khi
  xem trên thiết bị thật (Step 7), chỗ cần sửa chỉ là `MEMBER_MARKER_ANCHOR` và
  `HeadingIndicator`/`Dimens.MemberDotHeadingWidth/Height` trong `MemberMarkers.kt` — không lan ra
  file khác.
