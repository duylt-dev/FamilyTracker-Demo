# Phase 04 — Reroute và "đã tới" ở `:domain`

## Context Links

- [plan.md](plan.md) · [phase-01](phase-01-network-foundation-and-routing-port.md)
- [Chính sách reroute — researcher-04 của plan 1137](../260824-1137-realtime-navigation-to-member/research/researcher-04-reroute-policy.md)
- [VERIFICATION của plan 1137](../260824-1137-realtime-navigation-to-member/research/VERIFICATION.md) — mục "Where Does Reroute Logic Live?"
- [`LLM.md`](../../LLM.md) §8.2 (`ZoneEvaluator` là hàm thuần và nó nằm ở `:domain`), §11 (bố cục test)

## Overview

**Ưu tiên:** P1 · **Trạng thái:** ✅ Hoàn thành — `:domain:test` xanh, 87 test, 0.132s. Xem
`reports/dev-phase-04-report.md`.

Quyết định *khi nào* gọi lại provider. Không có dòng nào chạm mạng, không có dòng nào chạm Android.
Toàn bộ phase này là hàm thuần + test JUnit chạy dưới 5 giây.

## Key Insights

**#1 — Logic reroute nằm ở `:domain/tracking/`, đã chốt và có lý do.** Ba report của plan 1137 đề
xuất ba chỗ khác nhau. `:ui` thì ViewModel phải chứa luật nghiệp vụ (cấm bởi `LLM.md` §12); `:data`
thì ViewModel không đọc được quyết định mà không vượt biên module (§2). Đây là đúng chỗ
`ZoneEvaluator` đang ngồi, và vì đúng lý do: **thuật toán vị trí phải test được không cần Android** (§8.2).

**#2 — Xấp xỉ phẳng, không Haversine, cho khoảng cách điểm–đoạn.** Tính khoảng cách vuông góc từ
một điểm tới một segment trên mặt cầu là bài toán khó; ở quy mô một thành phố (< 50km) phép chiếu
equirectangular sai dưới 0.1%. `GeoDistance.haversineMeters` (đã có, `internal`, cùng module) vẫn
dùng cho khoảng cách điểm–điểm.

**#3 — Ba ngưỡng, không phải một.** Off-route một mẫu là chuyện thường ở Hà Nội: hẻm nhà 4–6 tầng
làm GPS lệch 10–50m. Reroute ngay mẫu đầu = gọi API mỗi vài giây, phí quota và nhấp nháy tuyến
đường. Ba lớp chặn: **khoảng cách** (45m) → **số mẫu liên tiếp** (3, tức ~30s ở nhịp 10s) →
**debounce thời gian** (60s).

**#4 — Đích di chuyển là một nhánh riêng.** Đích ở đây là *một con người đang đi*, không phải một
địa chỉ cố định. Minh/Lan cập nhật vị trí mỗi 2.5s. Nếu chỉ xét off-route thì đích chạy đi đâu
tuyến đường cũng không đổi. Ngưỡng 200m ≈ 2–3 khúc phố.

**#5 — "Đã tới" cần hysteresis.** Tới ở 50m, thôi-tới ở 70m. Không có 20m đệm thì khi đứng cách
50m và GPS dao động, trạng thái nhấp nháy tới/chưa-tới mỗi mẫu, kéo theo bật/tắt cả UI dẫn đường.

**#6 — `RerouteEvaluator` không giữ trạng thái bên trong.** Nó nhận state cũ, trả state mới —
đúng khuôn `ZoneEvaluator(point, zones, trạng thái trước) -> ZoneEvaluation(events, insideAfter)`.
Hàm giữ state bên trong thì test phải dựng lại lịch sử bằng cách gọi đúng thứ tự, và một test hỏng
làm hỏng cả file.

## Requirements

**Chức năng**
1. `RoutingGeometry.distanceToPolylineMeters(point, polyline): Double` — thuần.
2. `RerouteEvaluator.evaluate(state, follower, target, currentDirections, nowMs): RerouteDecision` — thuần.
3. `RerouteDecision`: `Keep` | `Reroute(reason)` | `Arrived`. `reason` phân biệt `OFF_ROUTE` với
   `DESTINATION_MOVED` — để log và để đo, không phải để trang trí.
4. Sáu hằng số vào `TrackingConstants.kt`.
5. `ObserveNavigationUseCase` — nối `MemberRepository.observeLatestLocations()` + `LocationSource`
   + `RoutingProvider` thành một `Flow<NavigationUpdate>`.

**Phi chức năng**
6. Test `:domain` chạy < 5s, không `delay`, không đồng hồ thật — `nowMs` là **tham số**, không
   phải `System.currentTimeMillis()` gọi bên trong. Gọi bên trong = test bất định.

## Architecture

```
:domain/tracking/RoutingGeometry.kt     điểm→đoạn, điểm→polyline. equirectangular. internal
:domain/tracking/RerouteEvaluator.kt    RerouteState + evaluate() thuần
:domain/tracking/TrackingConstants.kt   (sửa) +6 hằng số
:domain/usecase/ObserveNavigationUseCase.kt   nơi DUY NHẤT gọi RoutingProvider
```

```kotlin
data class RerouteState(
    val consecutiveOffRoute: Int = 0,
    val lastRerouteAtMs: Long = 0L,
    val hasArrived: Boolean = false,
)

sealed interface RerouteDecision {
    data class Keep(val state: RerouteState) : RerouteDecision
    data class Reroute(val state: RerouteState, val reason: RerouteReason) : RerouteDecision
    data class Arrived(val state: RerouteState) : RerouteDecision
}
```

**Thứ tự xét trong `evaluate` — thứ tự này là hợp đồng, không phải tuỳ tiện:**
1. Khoảng cách follower→target < `ARRIVAL_M` → `Arrived`. Tới rồi thì không reroute nữa.
2. Đang `hasArrived` mà khoảng cách > `ARRIVAL_EXIT_M` → bỏ cờ, xét tiếp.
3. ~~Chưa có tuyến đường nào → `Reroute(OFF_ROUTE)` bất kể debounce (lần đầu phải gọi ngay).~~
   **Sửa khi triển khai — thứ tự này có lỗ.** Provider lỗi liên tục thì `currentDirections` null mãi,
   nên "bất kể debounce" biến thành một lời gọi trả phí **mỗi mẫu vị trí** (~2.5s), không có điểm dừng
   — đúng cái hoạ mà mục Security Considerations của chính file này gọi tên. Debounce lên trước; lý do
   đã nêu ("lần đầu phải gọi ngay") giữ nguyên bằng cách cho `lastRerouteAtMs` mang kiểu `Long?`, `null`
   = chưa từng gọi. **Không** dùng `0L` làm sentinel: như thế lần gọi đầu chỉ chạy nhờ `nowMs` tình cờ
   lớn hơn 60_000, và một đồng hồ test bắt đầu từ 0 sẽ âm thầm bị chặn.
4. `lastRerouteAtMs != null && nowMs - lastRerouteAtMs < REROUTE_DEBOUNCE_MS` → `Keep`.
   **Debounce chặn trước mọi lý do khác, gồm cả "chưa có tuyến"** — không có nó thì đích đang chạy sẽ
   kéo reroute mỗi mẫu, và một provider đang hỏng sẽ kéo một hoá đơn.
5. Đích cách điểm cuối tuyến > `DESTINATION_MOVED_M` → `Reroute(DESTINATION_MOVED)`.
6. Khoảng cách follower→polyline > `OFF_ROUTE_M` → tăng `consecutiveOffRoute`; đủ
   `OFF_ROUTE_SAMPLES` → `Reroute(OFF_ROUTE)`. Ngược lại reset về 0 và `Keep`.

**Reset về 0 ở bước 6 là bắt buộc.** Đếm cộng dồn không reset thì ba lần lệch rải rác trong 10 phút
cũng kích reroute — đó không phải off-route, đó là GPS.

**Hằng số** (vào `TrackingConstants.kt`, kèm comment "nguồn: research phase-04, **không phải PRD §6**"):

| Hằng số | Giá trị | Giá của việc chọn nhỏ hơn | Giá của việc chọn lớn hơn |
|---|---|---|---|
| `OFF_ROUTE_TOLERANCE_M` | 45.0 | < 30m: reroute liên tục khi GPS dao động trong hẻm | > 60m: người dùng đã sang đường khác mà app vẫn chỉ tuyến cũ |
| `OFF_ROUTE_CONSECUTIVE_SAMPLES` | 3 | 1 mẫu: một lần nhiễu là reroute | 6 mẫu (60s): lạc một phút rồi mới được cứu |
| `DESTINATION_MOVED_TOLERANCE_M` | 200.0 | < 100m: thành viên đi loanh quanh cũng reroute | > 300m: đích chạy quá xa mới đổi tuyến |
| `REROUTE_DEBOUNCE_MS` | 60_000 | < 30s: cạn quota, tốn tiền | > 120s: phản ứng chậm rõ rệt |
| `ARRIVAL_M` | 50.0 | < 30m: chỉ đường tới tận nơi, thừa | > 100m: nói "tới rồi" khi còn cách một dãy phố |
| `ARRIVAL_EXIT_M` | 70.0 | đệm < 10m: nhấp nháy tới/chưa-tới | đệm > 50m: đi xa rồi vẫn tưởng đã tới |

## Related Code Files

**Tạo mới**
- `domain/src/main/kotlin/.../domain/model/NavigationUpdate.kt` — thêm khi triển khai: kiểu phát ra
  của use case, cộng cờ `isDistanceEstimated` để `:ui` phân biệt quãng đường thật với đường chim bay
- `domain/src/main/kotlin/.../domain/tracking/RoutingGeometry.kt`
- `domain/src/main/kotlin/.../domain/tracking/RerouteEvaluator.kt`
- `domain/src/main/kotlin/.../domain/usecase/ObserveNavigationUseCase.kt`
- `domain/src/test/kotlin/.../domain/tracking/RoutingGeometryTest.kt`
- `domain/src/test/kotlin/.../domain/tracking/RerouteEvaluatorTest.kt`
- `domain/src/test/kotlin/.../domain/usecase/ObserveNavigationUseCaseTest.kt`

**Sửa**
- `domain/src/main/kotlin/.../domain/tracking/TrackingConstants.kt` — +6 hằng số

## Implementation Steps

1. **`RoutingGeometry`.** `distanceToSegmentMeters(p, a, b)` bằng chiếu equirectangular:
   `x = (lon - lon0) * cos(lat0)`, `y = lat - lat0`, đổi sang mét bằng bán kính Trái Đất; rồi
   chiếu điểm lên đoạn, kẹp `t` vào `[0, 1]`. `distanceToPolylineMeters` = min trên mọi đoạn.
   **Xử lý polyline 0 điểm (trả `Double.MAX_VALUE`) và 1 điểm (khoảng cách điểm–điểm)** — hai case
   này xảy ra thật khi provider trả tuyến rỗng, và `min()` trên danh sách rỗng thì ném.
2. **`TrackingConstants`.** Sáu hằng số, mỗi cái một comment nêu **cả hai** giá như bảng trên.
   Ghi rõ nguồn là research chứ không phải PRD §6 — người đọc sau sẽ đi tìm mục PRD không tồn tại.
3. **`RerouteEvaluator`.** Đúng sáu bước theo thứ tự trên. `nowMs` là tham số.
4. **`RoutingGeometryTest`.** Điểm nằm đúng trên tuyến (≈0m); điểm vuông góc giữa đoạn; điểm ngoài
   đoạn về phía đầu (phải cho khoảng cách tới đầu mút, không phải tới đường thẳng kéo dài);
   polyline rỗng; polyline 1 điểm; một case toạ độ Hà Nội thật đối chiếu với khoảng cách đo tay.
5. **`RerouteEvaluatorTest`.** Mỗi nhánh một test, cộng: 2 mẫu off-route rồi 1 mẫu on-route →
   đếm reset (không reroute); đúng ngưỡng debounce (`nowMs - last == 60_000`) → cho phép; tới rồi,
   đích đi ra 75m → bỏ cờ arrived.
6. **`ObserveNavigationUseCase`.** `combine` vị trí self + vị trí target; mỗi lần phát, gọi
   `RerouteEvaluator`; nếu `Reroute` thì gọi `routingProvider.directions(...)`; phát
   `NavigationUpdate(directions, distanceMeters, hasArrived, lastError)`. Provider lỗi → **giữ tuyến
   cũ** và kèm lỗi, không xoá tuyến đang hiển thị (mất mạng 3 giây không được làm biến mất đường đi).

   **`distanceMeters` LUÔN có giá trị, kể cả khi `directions == null`.** Có tuyến →
   `directions.distanceMeters` (quãng đường thật theo phố). Chưa/không có tuyến →
   `GeoDistance.haversineMeters(self, target)`, tức khoảng cách đường chim bay.

   Đây không phải chi tiết trang trí: `GeoDistance` là `internal` của `:domain` (đúng thiết kế —
   `LLM.md` §8.2), nên `:ui` **không gọi được nó**. Nếu use case để trống `distanceMeters` ở nhánh
   giảm cấp, phase-05 sẽ không có cách nào hợp lệ để hiện khoảng cách, và người viết sẽ hoặc bịa
   một công thức trong composable, hoặc mở `GeoDistance` thành `public` — cả hai đều là vượt biên
   mà không ai kịp nhận ra vì nó *biên dịch được*. Tính ở đây, một lần, đúng chỗ.

   `NavigationUpdate` mang thêm một cờ cho biết `distanceMeters` là loại nào (thật hay chim bay) —
   phase-05 cần nó cho nhãn "ước tính" và cho dải attribution (Key Insight #3 của phase đó).
7. **`ObserveNavigationUseCaseTest`** với `FakeRoutingProvider` + Turbine: gọi lần đầu; không gọi
   lại trong debounce; provider trả lỗi thì tuyến cũ còn nguyên.

## Todo List

- [x] `RoutingGeometry.kt` (+ case rỗng / 1 điểm)
- [x] 6 hằng số vào `TrackingConstants.kt`, comment hai chiều, ghi nguồn không phải PRD
- [x] `RerouteEvaluator.kt` đúng thứ tự 6 bước
- [x] `RoutingGeometryTest` (6 case)
- [x] `RerouteEvaluatorTest` (8 test — mọi nhánh + 3 case biên)
- [x] `ObserveNavigationUseCase.kt`
- [x] `ObserveNavigationUseCaseTest` với `FakeRoutingProvider` + Turbine (4 test)
- [x] `./gradlew :domain:test` xanh và < 5s — 87 test, 0.132s

## Success Criteria

1. `:domain:test` xanh, dưới 5 giây, không `delay`, không `System.currentTimeMillis()` trong `main`.
2. `grep -rn "import android\|import com.google" domain/src` không ra gì.
3. Mọi nhánh của `RerouteDecision` có ít nhất một test.
4. Provider lỗi → tuyến cũ vẫn còn (test khẳng định, không chỉ nói).

## Risk Assessment

| Rủi ro | Xác suất | Giảm thiểu |
|---|---|---|
| Ngưỡng chọn sai cho đường Việt Nam | Trung bình | Hằng số một chỗ, đổi trong một dòng; chỉnh sau khi chạy thật ngoài đường |
| Chiếu phẳng sai ở vĩ độ cao | Rất thấp | App dùng ở Việt Nam (8–23°N); ghi giới hạn vào KDoc |
| `combine` phát quá dày (target 2.5s) → gọi provider quá nhiều | Trung bình | Debounce 60s chặn ở tầng quyết định, không ở tầng flow — đúng chỗ, vì flow còn dùng để cập nhật khoảng cách |
| Không có user story nào cho tính năng này trong PRD | Chắc chắn | Chặn #4 plan.md — đề nghị BA bổ sung. Dev không tự sửa PRD (§13 tiền lệ) |

## Security Considerations

Không có bề mặt tấn công mới: phase này không chạm mạng, không chạm quyền, không chạm lưu trữ.
Chỉ lưu ý **quota**: `REROUTE_DEBOUNCE_MS` là thứ duy nhất đứng giữa một vòng lặp lỗi và một hoá đơn.
Bất kỳ ai hạ nó xuống đều phải tính lại số request/ngày trước.

## Next Steps

Phase 05 vẽ kết quả lên bản đồ và gắn attribution — điều kiện bắt buộc của memo pháp lý.
