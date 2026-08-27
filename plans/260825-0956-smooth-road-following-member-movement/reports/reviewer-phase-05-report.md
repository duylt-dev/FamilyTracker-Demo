# Reviewer — Phase 05 (ghi công OSM trên màn Bản đồ)

**Ngày:** 2026-08-26 · **Phạm vi:** `git diff HEAD -- ui/ data/` (11 file) · Không sửa file sản
phẩm, không chạy `adb`, không đụng `.md` nào ngoài file này.

---

## KẾT LUẬN: **KHÔNG có BLOCKING — commit được**

Ba câu hỏi pháp lý quan trọng nhất đều có câu trả lời sạch, và tôi đã kiểm từng đường một chứ không
đọc lướt:

- **Ghi công thừa** (hiện "OpenStreetMap" khi dữ liệu trên màn không phải của OSM): **không có
  đường nào**.
- **Attribution dựng từ `engineId`**: **không** — trong toàn `:ui`, chuỗi `engineId` chỉ xuất hiện
  trong hai dòng chú thích, không một dòng mã nào.
- **Cache giữ nguyên văn attribution**: **có**, `put`/`get` chép thẳng, không dựng lại.

Cái tôi tìm được: **một câu hỏi pháp lý cần bạn quyết** (thiếu ghi công ở cold start — L-1), **một
lỗ phòng thủ một dòng** (L-2), và **một vùng mã không test nào với tới được** đúng chỗ chứa luật ba
trạng thái (T-1) — tôi đã đóng T-1 bằng test, đo được là nó bắt.

`:ui` **105 → 110** (5 ca tôi thêm), `:data` **77** (không đổi), `:app` 1, `:domain` 131 — tất cả
xanh, `./gradlew :ui:testDebugUnitTest :data:testDebugUnitTest :app:test :domain:test` exit 0.

---

## 1. Phát hiện

### BLOCKING — không có.

### Nên sửa

| # | Nơi | Vấn đề | Bản vá đề xuất |
|---|---|---|---|
| **L-1** | `RouteSourceAggregator` + vòng đời tiến trình | **Cold start: marker do OSM sinh ra vẫn trên màn, dải ghi công KHÔNG hiện.** `perMember`/`_current` là bộ nhớ trong RAM, tiến trình chết là mất; mở lại app (chưa bật theo dõi) thì `observeSource()` chưa phát gì ⇒ dải ẩn. Nhưng `MapState.otherMembers` đọc `location_points` từ Room, và những toạ độ đó **được sinh ra bằng cách đi trên polyline GraphHopper** ở phiên trước — đúng loại *Produced Work* mà Key Insight #1 của chính phase này dùng làm lý do để bắt buộc ghi công. Tức là ở trạng thái C, lập luận pháp lý của phase nói "phải ghi công" còn code nói "ẩn". **Đây là chiều THIẾU ghi công (ODbL), không phải chiều thừa.** Chỉ ảnh hưởng màn Bản đồ: tab Lịch sử chỉ vẽ lộ trình của self, mà self là GPS thật, không phải OSM | Hai hướng, chọn một và ghi lại: (a) **lưu lại** `RouteSourceInfo` phát cuối cùng (một file nhỏ cạnh `filesDir/routes/`, hoặc suy từ chính thư mục cache: còn file cache ⇒ từng có tuyến OSM) rồi seed aggregator lúc khởi động; (b) **chấp nhận có chủ ý** và ghi thẳng vào `routing-and-map-attribution.md` §3 + `LLM.md` §13 Open: "ghi công gắn với NGUỒN TUYẾN ĐANG CHẠY, không gắn với marker còn sót từ phiên trước". Tôi không tự chọn hộ: đây là quyết định pháp lý, không phải bug |
| **L-2** | `MemberRouteSource.path()` nhánh cache | **Tầng OSM với `attribution` rỗng ⇒ dải ẩn HOÀN TOÀN** (không credit, cũng không nhãn ước tính): `attributionLines` rỗng ⇒ nhánh 1 trượt; `isFallbackRoute` false vì `kind == CACHE` ⇒ nhánh 2 trượt; còn `else -> null`. **Chưa với tới được hôm nay** — `GraphHopperDirectionsMapper` có `FALLBACK_ATTRIBUTION`, `ValhallaRoutingProvider.attribution()` luôn kèm `OpenStreetMap contributors` — nhưng nhánh cache tin `dto.attribution` nguyên văn, nên một file `routes/*.json` bị sửa tay/cắt cụt với `"attribution": []` là đủ để dựng ra trạng thái đó | Một dòng ở `OnDevicePolylineCache.get()`: `if (dto.attribution.isEmpty()) { file.delete(); return@runCatching null }` — hình học OSM không kèm credit thì coi như miss, đúng tinh thần "một tuyến không có credit thì không đọc lại được" (`decisions.md` §C2 điểm 5) |
| **T-1** | `RoutingAttribution.kt` | **Luật ba trạng thái — trái tim pháp lý của phase — không một test nào với tới được.** `:ui` không có `ui/src/androidTest`, không có `compose-ui-test` trong `ui/build.gradle.kts`, nên thân `@Composable` là vùng trắng. Đo thật: đổi nhánh `isFallbackStraightLine` thành `stringResource(R.string.route_attribution_route, "OpenStreetMap contributors")` — tức **hiện credit OSM đúng lúc đang chạy tầng SyntheticPath**, vi phạm thẳng X5 — thì **314/314 vẫn XANH** | **Đã đóng** bằng `RoutingAttributionContractTest` (§3). Nguyên nhân gốc (không có hạ tầng test Compose) nên ghi vào `LLM.md` §13 Open: ngày nào có `compose-ui-test`, thay test đọc-mã-nguồn bằng ba ca dựng thật |

### Góp ý

| # | Nơi | Ghi chú |
|---|---|---|
| N-1 | `RouteSourceAggregator` | `perMember` vẫn không có đường xoá (G-4 của review phase-04). Ở phase này nó là chuyện TỐT: khi theo dõi dừng, giữ lại aggregate cuối = tiếp tục ghi công cho những marker vẫn đang hiển thị — đúng chiều an toàn. Chỉ cần ghi rõ là **có chủ ý**, kẻo có người "dọn dẹp" bằng cách `clear()` khi service dừng và vô tình tạo ra đúng L-1 ở một chỗ nữa |
| N-2 | `designsystem/component/` | `RoutingAttribution` là `internal`, hàng xóm `FamilyTrackerBottomBar` là `public`. Cả hai chạy được (một module `:ui`), phase file cho phép cả hai — nhưng thư mục này nay có hai quy ước. Chọn một |
| N-3 | `MapScreen` | Dải xuất hiện/biến mất làm chiều cao khung bản đồ đổi. Không gây nhảy camera: `hasCenteredOnce` chặn canh lại, và `newLatLngZoom` không phụ thuộc kích thước khung. Đã đối chiếu với rủi ro "camera canh lần đầu lệch" trong phase file — đúng như đánh giá ở đó |
| N-4 | `MemberRouteSourceTest` 2 ca mới | Dùng `launch { collect }` + `advanceUntilIdle()` + `cancel()` thay vì Turbine (`:data` không có Turbine). Đúng, và ca đối chứng thứ hai ("phát đúng MỘT giá trị") là thứ ngăn bản sửa `filterNotNull` bị nới thành "không bao giờ phát gì" |

---

## 2. Bảng mutation

Harness như các lượt trước (`--continue`, xoá `build/test-results` trước mỗi lần, khôi phục + `diff`
xác nhận sau mỗi lần). Baseline: **314** (`:ui` 105, `:data` 77, `:app` 1, `:domain` 131).

| # | Đột biến trên lớp sản phẩm | ĐỎ trước | ĐỎ sau khi tôi thêm ca |
|---|---|---|---|
| P1 | `isFallbackRoute`: `== SYNTHETIC` → `!= SYNTHETIC` | **3** | 3 |
| P2 | `attributionLines` luôn `emptyList()` | **1** | 1 |
| P3 | Gỡ bản sửa P0: `MutableStateFlow(RouteSourceInfo(SYNTHETIC, emptyList()))` + `asStateFlow()` | **1** | 1 |
| P4 | `RoutingAttribution`: nhánh fallback hiện credit OSM (vi phạm X5) | **0** ✗ | **1** |
| P5 | `RouteSourceAggregator`: đếm cả thành viên SYNTHETIC vào tầng OSM | **6** | 6 |
| P6 | `RoutingAttribution`: bỏ trạng thái thứ ba (`else -> null`) ⇒ luôn vẽ một dòng | **0** ✗ | **1** |

**Đọc bảng này:** mọi thứ ở `MapState` và ở `:data` đều được khoá chặt — P1/P2/P3/P5 chết ngay. Hai
đột biến sống sót (P4, P6) **cùng nằm trong thân `@Composable`**, và đó không phải trùng hợp: đó là
đúng vùng mà hạ tầng test hiện tại không với tới. Cả hai đều là vi phạm pháp lý trực tiếp nếu lọt ra
production — P4 hiện credit OSM khi không có dữ liệu OSM, P6 xoá trạng thái "ẩn hẳn" của FR-4.

## 3. Test tôi thêm (5 ca, không chạm file sản phẩm)

`ui/src/test/.../ui/designsystem/component/RoutingAttributionContractTest.kt` — đọc mã nguồn
`RoutingAttribution.kt` và ghim ba dòng `when` (chính là hợp đồng pháp lý), cùng khuôn
`CoroutineSafetyArchitectureTest`/`RealGpsNoSnapArchitectureTest` đã có trong repo:

| Ca | Khoá | Đo đỏ dưới |
|---|---|---|
| `the OSM credit string is only reachable when attribution lines exist` | credit chỉ hiện khi CÓ attribution | (P4 biến thể) |
| `the fallback branch never shows the OSM credit string` | tầng 3 KHÔNG được ghi công OSM (X5) | **P4** |
| `there is a third state that renders nothing` | FR-4 "ẩn hẳn", ba trạng thái không phải hai | **P6** |
| `attribution is never built from engineId` | §3 "credit lấy từ nhà cung cấp, không tự viết" | — |
| `no other file in ui renders the attribution strings` | không ai vẽ credit vòng qua luật ba trạng thái | — |

KDoc của file nói rõ đây là **giải pháp cho hạn chế hạ tầng**, không phải mẫu đáng nhân rộng: có
`compose-ui-test` thì thay bằng ba ca dựng thật rồi xoá file.

## 4. Những thứ đã kiểm và ĐẠT

- **A — Ghi công thừa:** không có đường nào. Aggregator lọc bỏ SYNTHETIC trước khi hợp attribution;
  chặng WANDER publish SYNTHETIC qua `wander()` (bản sửa phase-04); `attribution` chảy nguyên văn
  `Directions.attribution` → cache → aggregator → `MapState` → composable, không chỗ nào dựng lại.
  Ca hỗn hợp (Minh PROVIDER, Lan SYNTHETIC) hiện credit — đúng: marker của Minh **là** dữ liệu OSM,
  ghi công dư cho Lan không phải vi phạm, thiếu cho Minh thì mới là.
- **A — `engineId`:** không xuất hiện ở một dòng MÃ nào trong `:ui` (chỉ 2 dòng chú thích), và
  `RoutingAttribution` chỉ nhận `List<String>`. Nay có test ghim.
- **A — Cache giữ attribution:** `put(key, points, attribution, engineId)` ghi thẳng, `get()` đọc
  thẳng `dto.attribution`; một cache hit vẫn hiện credit OSM (đúng — dữ liệu OSM vẫn là dữ liệu OSM),
  khớp với cách bạn dựng trạng thái B bằng `pm clear`.
- **C — Vị trí dải:** `Column { Box(weight(1f)) { bản đồ + banner + toggle + nút Dẫn đường },
  RoutingAttribution }`. Dải là **anh em** của `Box`, nhận `Modifier` mặc định, không `align`, không
  `zIndex`, không offset/padding âm. Grep toàn `:ui/src/main`: mọi `zIndex` đều là z-index của
  **marker/circle Maps SDK** (thứ tự vẽ TRONG bản đồ), không phải `Modifier.zIndex` của Compose —
  không có đường nào cho dải đè lại lên khung. `MapScreen.kt` **190 dòng** < 200 (S6 đạt).
- **D — Chuyển nhà:** rename được git nhận (`similarity index 67%`, blame giữ nguyên). `internal`
  vẫn đúng — `:ui` là MỘT module Gradle nên cả hai màn thấy được; không import chéo feature nào
  (cả `MapScreen` lẫn `NavigationScreen` đều import từ `designsystem.component`), đúng LLM.md §12.
- **E — MVI:** `collectSafely` ×5, không `launchIn`; `onIntent` là public method duy nhất;
  `MapViewModel` không import Compose/Android; `attributionLines`/`isFallbackRoute` là `val` tính
  toán chứ không phải field song song ("Derive, don't duplicate"); `simulatedRouteRepository` không
  `private val` vì chỉ dùng trong `init` — đúng luật đã ghi trong KDoc của chính lớp.
- **DI:** `MemberRouteSource` là Koin **`single`** `binds` cả hai interface, nên ViewModel và bộ mô
  phỏng dùng CHUNG một instance — cả tính năng đứng trên chi tiết này (một `factory` là dải ghi công
  không bao giờ cập nhật, và không test nào bắt được). `viewModelOf(::MapViewModel)` tự resolve tham
  số mới; `KoinModulesTest` xanh.
- **F — `AnimatedMarkerPositions`:** đổi kiểu TRẢ VỀ `SnapshotStateMap` → `Map` **không** làm mất ổn
  định Compose và **không** gây recompose thừa, vì hai lý do độc lập: (1) đối tượng runtime vẫn là
  `SnapshotStateMap`, nên đọc `animatedPositions[member.id]` vẫn là một snapshot read được theo dõi;
  (2) giá trị này chỉ được dùng **cục bộ trong `MemberMarkers`**, không truyền làm tham số cho một
  `@Composable` nào — nên suy luận ổn định theo tham số của Compose không bao giờ nhìn thấy kiểu
  `Map`. Trả nợ này sạch.
- **Hình dạng lỗi thứ ba ("trạng thái khởi tạo / trạng thái gộp"):** đã soi. `MapState.routeSource`
  mặc định `null` và `filterNotNull()` chặn đúng chỗ. Thể hiện còn lại của cùng hình dạng đó là
  **L-1** — cũng là "trạng thái khởi tạo", nhưng ở chiều ngược lại: reset về rỗng trong khi trên màn
  vẫn còn dữ liệu của phiên trước.

## 5. Việc còn lại

1. **L-1** — quyết định (a) lưu lại nguồn cuối, hay (b) chấp nhận + ghi vào `routing-and-map-attribution.md` §3.
2. **L-2** — một dòng ở `OnDevicePolylineCache.get()`.
3. **T-1** — ghi `LLM.md` §13 Open: `:ui` không có hạ tầng test Compose; luật ba trạng thái đang được
   ghim bằng test đọc-mã-nguồn.
4. N-2 — chọn một quy ước visibility cho `designsystem/component/`.
