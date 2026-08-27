# Phase 03 — Nội suy marker ở tầng hiển thị (`:ui`)

## Context Links

- [`plan.md`](plan.md) · [`decisions.md` §C1, D3, D7](decisions.md)
- Yêu cầu: [PRD delta](docs/prd-delta-smooth-road-movement.md) D4, US-40, US-06/US-08 (MODIFIED), §4.2
- Nghiệm thu: **QA-SRM-05, 06, 07, 08, 22, 35**, UAT-02
- Research: [researcher-02](research/researcher-02-marker-interpolation.md) toàn bộ — **§C mô tả `flat` NGƯỢC, xem `decisions.md` §Sai lệch #1**
- Kiến trúc: `LLM.md` §12 (composable riêng một feature → `feature/map/component/`, `internal`), §13 Fixed #20 (`compose-stability.conf` không dựng)
- MVI: `docs/android-mvi-best-practices.md` §4 (screen), §8 (Compose performance), §9

## Overview

| | |
|---|---|
| **Ưu tiên** | P1 |
| **Trạng thái** | completed |
| **Ước lượng** | 4h |
| **Phụ thuộc** | Phase 02 (cần `bearingDegrees` thật trong `LocationPoint`) |

Marker không còn "nhảy" mỗi lần có mẫu mới. Một vòng lặp `withFrameNanos` **duy nhất** nội suy vị
trí và góc của tất cả marker giữa hai mẫu THẬT liên tiếp. **Không ghi gì xuống Room** — dữ liệu
vẫn là mẫu thô (QA-SRM-08). **Không ngoại suy** — hết mẫu thì dừng đúng ở mẫu cuối, không đoán
tiếp (US-44, QA-SRM-22).

## Key Insights

1. **Nội suy tuyến tính ở đây đúng trên đường là nhờ phase 02, không nhờ `:ui`.** Bảo toàn đỉnh
   (D2) đảm bảo hai mẫu liên tiếp của thành viên mô phỏng luôn nằm trên **cùng một đoạn thẳng**
   của polyline. `:ui` chỉ nội suy giữa hai điểm — và điều đó đủ để mọi khung hình nằm đúng trên
   đường. `:ui` **không** được biết polyline là gì; biết là mở cửa cho việc nắn vị trí thật.
2. **Với vị trí thật, cùng một phép nội suy đó là hợp đồng trung thực, không phải nói dối.** Mọi
   khung hình nằm **trên đoạn thẳng nối hai mẫu thật**, không lệch về phía con đường gần nhất
   (QA-SRM-22). Đây chính là ranh giới X1 của PRD delta: được nội suy giữa hai sự thật, không được
   bịa ra sự thật thứ ba.
3. **Không ngoại suy, và cũng không ẩn.** Hết `progress = 1` thì marker **đứng lại đúng mẫu cuối**
   và ở đó. Không dead-reckoning theo vận tốc, không xám, không ẩn (D7 — X2 cấm ẩn marker).
4. **Thời lượng nội suy KHÔNG phải hằng số.** Nó là hiệu `recordedAt` giữa hai mẫu gần nhất, chặn
   trên 5 s. Vậy là đúng cho **cả** nguồn mô phỏng (2 500 ms) **lẫn** GPS thật (10 000 ms) mà
   không cần biết mình đang xem cái nào, và tự đúng nếu phase 06 đổi `SIM_MEMBER_SPEED_MPS`.
   Hệ quả: `MEMBER_RENDER_FRAME_MS` mà PRD delta §4.2 đề xuất **không được thêm** — `withFrameNanos`
   đã theo nhịp màn hình, một hằng số nữa chỉ là một chỗ nữa để sai.
5. **`MEMBER_RENDER_MAX_JUMP_M` (2.0) là ngưỡng NGHIỆM THU, không phải hằng số runtime.** Không
   dòng mã sản phẩm nào đọc nó ⇒ nó sống trong file test, **không** vào `TrackingConstants`
   (§13 Open #7 đang lệch 12/19, đừng làm lệch thêm bằng một con số không ai dùng).
6. **`compose-stability.conf` không được dựng** (§13 Fixed #20 đã chốt dứt điểm). Kiến trúc ở đây
   né vấn đề `LatLng` bất ổn định bằng cách **chỉ giữ primitive** trong state animation và dựng
   `LatLng` bên trong composable.
7. **`flat = true`, không phải `false`.** researcher-02 §C viết ngược. `flat = false` là billboard
   (marker dựng đứng theo màn hình); `flat = true` dán phẳng lên mặt bản đồ nên xoay/nghiêng theo
   camera — đó mới là thứ một mũi chỉ hướng cần. **Phải xác nhận bằng mắt trên thiết bị** trước khi
   đóng phase, không tin tài liệu.
8. **Chấm xanh của self là hình tròn nên không xoay.** Chỉ nội suy vị trí. Đó là lý do hai chỗ
   `bearingDegrees = 0f` còn lại (`SimulatedLocationSource`, `DemoDataSeeder`) được giữ nguyên ở
   phase 02.

## Requirements

**Chức năng**

- FR-1 Giữa hai khung hình liên tiếp, marker dịch không quá `MEMBER_RENDER_MAX_JUMP_M` = 2.0 m
  (US-40, QA-SRM-05). Ngoại lệ **duy nhất**: cú spawn một lần của US-42.
- FR-2 Cùng ngưỡng áp dụng cho chấm xanh của self (QA-SRM-06).
- FR-3 Marker thành viên xoay theo `bearingDegrees`; góc cũng nội suy, đi đường ngắn qua mốc 0°/360°
  (QA-SRM-07).
- FR-4 Số dòng ghi vào `location_points` **khớp nhịp lấy mẫu**, không khớp nhịp khung hình (QA-SRM-08).
- FR-5 Mọi vị trí nội suy nằm **trên đoạn thẳng nối hai mẫu thật** (QA-SRM-22).
- FR-6 Thêm/bớt thành viên giữa chừng không làm marker còn lại nhảy hay mất trạng thái animation.

**Phi chức năng**

- NFR-1 3 thành viên + 5 zone: giữ 60 fps (QA-SRM-35). Chi phí main thread của vòng nội suy
  `< 5 ms/khung`.
- NFR-2 Không import Compose/Android trong ViewModel; không thêm field animation vào `MapState`.
- NFR-3 Không thêm hằng số vào `TrackingConstants`.
- NFR-4 File `MemberMarkers.kt` giữ dưới 200 dòng (LLM.md §5) — phần animation ở file riêng.

## Architecture

```
  :ui/core/motion/MarkerInterpolation.kt          THUẦN JVM — không Compose, không Android
      lerpDegrees(a, b, t)  ·  lerpBearing(a, b, t)  ·  progressOf(elapsedMs, durationMs)
                      ▲                          test: ui/src/test/.../core/motion/
                      │
  :ui/feature/map/component/AnimatedMarkerPositions.kt      internal, Compose
      @Composable rememberAnimatedMarkerPositions(
          samples: List<MarkerSample>,          // (id, lat, lng, bearing, recordedAtMs)
      ): SnapshotStateMap<String, AnimatedMarkerPosition>

      · MỘT LaunchedEffect + MỘT withFrameNanos loop cho TẤT CẢ marker
      · state mỗi marker: fromLat/fromLng/fromBearing, toLat/toLng/toBearing,
        startedAtNanos, durationMs      ← TOÀN BỘ là primitive, KHÔNG có LatLng
      · mẫu mới tới  -> from = vị trí ĐANG hiển thị (không phải mẫu cũ) => không giật khi retarget
      · progress = 1 -> dừng, không ngoại suy
                      ▲                       ▲
        ┌─────────────┘                       └───────────────┐
  MemberMarkers.kt                                    FamilyTrackerMap.kt
    MarkerComposable(keys = arrayOf(member.id),         MarkerComposable(keys = arrayOf("self"),
        rotation = pos.bearing, flat = true, …)             …)  ← KHÔNG rotation (chấm tròn)
    { MemberDot(color, hasHeading = true) }              { SelfDot(color) }
```

**Vì sao `from` là vị trí ĐANG hiển thị chứ không phải mẫu trước đó:** khi mẫu mới tới sớm/muộn hơn
dự kiến, animation cũ chưa chạy hết. Lấy `from` = mẫu cũ sẽ làm marker **giật lùi** về mẫu cũ rồi
chạy lại. Lấy `from` = vị trí đang vẽ thì đường đi liên tục tuyệt đối. Đây cũng là cách chuyển
nguồn tuyến ở phase 04 không gây cú nhảy (QA-SRM-17).

**Vì sao một vòng lặp cho tất cả marker** (researcher-02 §E): N `Animatable` riêng = N coroutine,
N lần cập nhật state mỗi khung, N lần recompose cha. Một vòng lặp cập nhật một `SnapshotStateMap`
một lần mỗi khung.

## Related Code Files

**Tạo**

| Đường dẫn | Việc |
|---|---|
| `ui/src/main/java/.../ui/core/motion/MarkerInterpolation.kt` | Hàm thuần: `lerpDegrees`, `lerpBearing` (đi đường ngắn), `progressOf` |
| `ui/src/main/java/.../ui/feature/map/component/AnimatedMarkerPositions.kt` | `internal` — `MarkerSample`, `AnimatedMarkerPosition`, `rememberAnimatedMarkerPositions()` |
| `ui/src/test/java/.../ui/core/motion/MarkerInterpolationTest.kt` | JUnit thuần — bao gồm ca "mọi điểm nội suy nằm trên đoạn thẳng" (QA-SRM-22) |

**Sửa**

| Đường dẫn | Việc |
|---|---|
| `ui/src/main/java/.../ui/feature/map/component/MemberMarkers.kt` | Dùng `rememberAnimatedMarkerPositions`; `rotation = pos.bearingDegrees`, `flat = true`; `keys` giữ **chỉ** `member.id`; **thay KDoc dòng 36-38** |
| `ui/src/main/java/.../ui/feature/map/component/FamilyTrackerMap.kt` | Chấm xanh self đọc cùng bộ nội suy (chỉ vị trí, không `rotation`) |
| `ui/src/main/java/.../ui/designsystem/theme/Dimens.kt` | Kích thước mũi chỉ hướng của `MemberDot` |
| `LLM.md` | §3: thêm `core/motion/` và `AnimatedMarkerPositions.kt`; **sửa dòng mô tả `MemberMarkers.kt`** |
| `docs/prd-delta-smooth-road-movement.md` | §4.2: ghi `MEMBER_RENDER_FRAME_MS` **không thêm**, kèm lý do (Key Insight #4) |

**Xoá:** KDoc "Không animate vị trí giữa 2 lần cập nhật … đúng yêu cầu 'jump, không animate'"
(`MemberMarkers.kt:36-38`). **Đây là điều kiện bắt buộc của phase**: quyết định #2 đảo ngược đúng
câu đó, và tài liệu lệch là một khuyết tật phải sửa trong cùng commit gây ra nó.

## Implementation Steps

1. `MarkerInterpolation.kt` + test **trước**. Ca bắt buộc:
   - `lerpBearing(350f, 10f, 0.5f)` == `0f` (đi 20°, không đi 340°);
   - `lerpBearing(10f, 350f, 0.5f)` == `0f`;
   - `lerpBearing(0f, 180f, 0.5f)` == `90f` — chọn một chiều và **khoá bằng test** (nếu không,
     marker quay ngẫu nhiên hai chiều ở đúng 180°);
   - `progressOf` chặn trong `[0,1]`, `durationMs <= 0` → `1f` (không chia cho 0);
   - **QA-SRM-22**: 100 giá trị `t` giữa hai điểm bất kỳ → khoảng cách từ điểm nội suy tới đoạn
     thẳng nối hai đầu `< 1e-9`.
2. `AnimatedMarkerPositions.kt`:
   - `data class MarkerSample(val id: String, val latitude: Double, val longitude: Double, val bearingDegrees: Float, val recordedAtMs: Long)` — `@Immutable`, **chỉ primitive**;
   - `AnimatedMarkerPosition` giữ `latitude`, `longitude`, `bearingDegrees` — **chỉ primitive**,
     KDoc ghi thẳng "dựng `LatLng` bên trong composable, không lưu ở đây" (researcher-02 Risk #2);
   - `LaunchedEffect(samples)` cập nhật mục tiêu: `from` = giá trị đang hiển thị, `to` = mẫu mới,
     `durationMs = (mẫu mới.recordedAtMs - mẫu cũ.recordedAtMs).coerceIn(1, 5_000)`;
   - một `LaunchedEffect(Unit)` chạy `while (isActive) { withFrameNanos { … } }`, mỗi khung cập
     nhật tất cả mục còn `progress < 1`;
   - id biến mất khỏi `samples` → xoá khỏi map (tránh rò rỉ khi thành viên bị xoá).
3. `MemberMarkers.kt`: map `MemberLocation` → `MarkerSample`, gọi
   `rememberAnimatedMarkerPositions`, truyền `rotation`/`flat`. `keys = arrayOf(member.id)` —
   **không** đưa vị trí vào `keys`, nếu không bitmap `MemberDot` bị chụp lại mỗi khung.
4. `MemberDot` thêm mũi chỉ hướng (một tam giác nhỏ ở mép trên hình tròn). Màu và kích thước lấy
   từ `Color.kt`/`Dimens.kt`, không literal (§12).
5. `FamilyTrackerMap.kt`: self dùng cùng bộ nội suy, **không** truyền `rotation`.
6. Chạy `./gradlew :ui:test --no-configuration-cache`.
7. **Xác nhận bằng mắt trên `emulator-5554`** (không suy từ tài liệu): (a) marker trượt liên tục,
   không giật; (b) mũi chỉ hướng chỉ đúng hướng đi khi **xoay bản đồ** — nếu nó xoay theo màn hình
   thay vì theo bản đồ thì `flat` đang sai chiều, đổi và thử lại; (c) chấm xanh của self cũng
   trượt giữa hai fix.
8. **Đo chi phí khung hình** (NFR-1): `adb shell dumpsys gfxinfo <pkg> reset` sau khi app ổn định
   trên tab Bản đồ (không tính cold start — cùng phương pháp §13 Fixed #16), chạy 60 s với 3 thành
   viên + 5 zone, đọc lại `dumpsys gfxinfo`. Luật chốt:

   | Kết quả | Hành động |
   |---|---|
   | Janky frames < 5% | Giữ nguyên 60 fps |
   | 5–15% | Vòng lặp bỏ khung xen kẽ (≈30 fps), đo lại |
   | > 15% | Giữ nội suy **vị trí**, bỏ nội suy **góc** (đặt `rotation` mỗi mẫu thay vì mỗi khung), đo lại, và ghi vào `LLM.md` §13 Open |

9. Cập nhật `LLM.md` §3 và **xoá KDoc cũ** ở `MemberMarkers.kt` trong cùng commit.

## Todo List

- [x] `MarkerInterpolation.kt` + `MarkerInterpolationTest` (gồm ca QA-SRM-22)
- [x] `AnimatedMarkerPositions.kt` — một vòng `withFrameNanos`, state chỉ primitive
- [x] `from` = vị trí đang hiển thị khi retarget (chống giật lùi)
- [x] Không ngoại suy: `progress = 1` thì dừng
- [x] `MemberMarkers.kt` — `rotation` + `flat = true` + `keys` chỉ `member.id`
- [x] `MemberDot` có mũi chỉ hướng; `Dimens`/`Color` cập nhật
- [x] Chấm xanh self nội suy vị trí (không xoay)
- [x] **Xoá KDoc "jump, không animate"** ở `MemberMarkers.kt:36-38`
- [x] Xác nhận `flat` bằng mắt trên thiết bị (xoay bản đồ)
- [x] Đo `dumpsys gfxinfo`, áp luật chốt ở Step 8, ghi số vào dev report
- [x] `LLM.md` §3 + PRD delta §4.2 cùng commit

## Success Criteria

| # | Điều kiện | Kết quả | Bằng chứng / Số đo |
|---|---|---|---|
| S1 | `lerpBearing` đi đường ngắn ở mọi ca vòng qua 0°/360° | **ĐẠT** | `MarkerInterpolationTest` xanh |
| S2 | Mọi điểm nội suy nằm trên đoạn thẳng nối hai mẫu, sai lệch `< 1e-9` | **ĐẠT** | `MarkerInterpolationTest` xanh |
| S3 | Bước dịch mỗi khung ≤ 2.0 m ở 90 fps: **0.046 m/khung, dư 43×** | **ĐẠT** | Device thật: 34.8 px / 15 s = 0.56 m/px; 4.15/90 = 0.046 m/khung |
| S4 | Chấm xanh self trượt liên tục giữa hai fix | **CHƯA NGHIỆM THU** | Self ở HN, camera ở HCMC; mẫu liên tiếp gần trùng (đứng yên trong nhà). Đường code dùng cùng component như member markers (verified), nhưng quan sát chưa có. |
| S5 | Chạy 60 s → số dòng `location_points` khớp nhịp lấy mẫu | **ĐẠT** | **24 dòng/60 s = 2.50 s/dòng** (Lan member), khớp `MEMBER_ROAM_INTERVAL_MS = 2500` |
| S6 | Janky frames theo luật ở Step 8 (`< 5% → giữ 60 fps`) | **ĐẠT** | **0.62% (33/5360 khung @ 90 Hz)** < 5%; Missed Vsync = 0; p50=10ms, p90=12ms |
| S7 | Mũi chỉ hướng giữ đúng hướng thật khi xoay bản đồ | **ĐẠT** | `flat=true` dự đoán vs. đo: trước **1.2°**, sau **4.0°** sai số (góc hiển thị đã nội suy, tam giác 103px ~±2°) |
| S8 | KDoc cũ ở `MemberMarkers.kt` đã biến mất; `LLM.md` §3 mô tả đúng | **ĐẠT** | Reviewer kiểm `git diff` |

## Risk Assessment

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Recompose mỗi khung của `MemberMarkers` gây jank (researcher-02 §E, issue #551) | **Trung bình** | Một vòng lặp duy nhất; `keys` không đổi nên bitmap không chụp lại; **luật chốt đo được ở Step 8** thay vì hy vọng |
| `LatLng` không stable, `compose-stability.conf` không có (§13 Fixed #20) | Trung bình | State animation **chỉ primitive**; `LatLng` dựng bên trong composable. KDoc ghi luật này ngay trên `AnimatedMarkerPosition` |
| Ai đó thêm vận tốc để "chạy tiếp khi mẫu về trễ" | **Cao (về hậu quả)** | Ngoại suy = bịa vị trí một con người ⇒ vi phạm X1. KDoc ghi cấm; `MarkerInterpolationTest` khoá `progressOf` chặn ở 1 |
| Vòng `withFrameNanos` chạy mãi khi không còn gì animate ⇒ tốn pin | Thấp | Vòng lặp thoát khi mọi mục `progress >= 1`, chạy lại khi có mẫu mới |
| Thêm/bớt thành viên làm map animation lệch khoá | Thấp | Khoá theo `member.id`; `DemoDataSeeder` gieo id một lần, id không đổi trong phiên (researcher-02 Q6) |
| Mũi chỉ hướng làm marker rối ở zoom thấp | Thấp | Kích thước ở `Dimens`, chỉnh một chỗ |

## Security Considerations

- `:ui` **không** ghi gì; không có đường dữ liệu mới nào ra khỏi thiết bị.
- Không log toạ độ trong vòng lặp khung hình (PRD §7.3, gate G7) — một log mỗi khung vừa là rò rỉ
  vị trí vừa là nguồn jank.
- Nội suy **không** làm thay đổi dữ liệu đã lưu: `location_points` giữ nguyên mẫu thô, nên bằng
  chứng vị trí thật vẫn nguyên vẹn cho tab Lịch sử.

## Next Steps

- Phase 04 đổi **nguồn** dãy điểm. Phase này không phải sửa gì: `:ui` chỉ thấy `LocationPoint`.
- Ngưỡng jank đo ở Step 8 là số nền để phase 06 so lại sau khi bật tầng 1/2 của nguồn tuyến.
