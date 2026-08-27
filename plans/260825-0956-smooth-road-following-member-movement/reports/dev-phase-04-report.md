# Dev Report — Phase 04: Nguồn tuyến đường lai 3 tầng

**Phạm vi thực hiện:** Implementation Steps 1–8 + phần docs của Step 12 + **Step 11** (thêm ở lượt
sửa thứ hai, xem §0-B). Steps 9, 10 (chạy thật trên emulator, đếm request 10 phút) **KHÔNG làm bởi
tôi** — orchestrator tự chạy và mang số đo thật quay lại (xem §0-A/§0-B, các con số đo được từ đó).

## 0. Fix-up sau review của orchestrator (2026-08-26)

Orchestrator review lần đầu tìm ra **một lỗi chặn** + 3 việc nhỏ. Cả 4 đã sửa, xanh lại. Ghi ở đây
để commit trail có dấu vết — không viết đè lên các mục gốc bên dưới.

1. **CHẶN — thứ tự tầng bị đảo ngược.** Bản đầu của `MemberRouteSource.path()` gọi
   `RoutingProvider` TRƯỚC, `OnDevicePolylineCache` SAU — ngược với Architecture của phase file
   (cache → provider → synthetic). Đây là lỗi thật của tôi khi viết code, không phải phase file sai
   (đã đọc lại nguyên văn: phase file luôn ghi đúng thứ tự cache trước). Hậu quả nếu không sửa: FR-2
   ("lần sau đọc cache, không gọi mạng") bị đảo ngược nghĩa, và NFR-2/QA-SRM-36 ("từ vòng thứ hai
   trở đi là 0 request") không bao giờ đúng — mỗi chặng vẫn gọi mạng trước, cache chỉ được đọc khi
   provider hỏng. **Sửa:** đảo `path()` thành `cache.get(key)` → `fromProvider(request)` →
   `SyntheticPath.between(...)`, giữ nguyên `RouteGeometryGuard.isUsable` chạy trên cả kết quả tầng
   1 (cache) lẫn tầng 2 (provider) trước khi nhận. Đường SYNTHETIC → PROVIDER (S6/QA-SRM-17) vẫn
   sống nguyên vẹn: `SyntheticPath` không bao giờ được `cache.put`, nên chặng kế tiếp luôn là
   cache-miss và vẫn hỏi provider bình thường — không cần thêm cơ chế gì.
2. **Test khoá hạn ngạch — thêm `MemberRouteSourceTest`.** Ca mới
   `` `NFR-2 calling path() twice with the same request only calls the provider once` `` — gọi
   `path()` hai lần với CÙNG một `MemberRouteRequest`, khẳng định lần hai trả đúng dãy điểm đã cache,
   `observeSource()` phát `CACHE`, và `FakeRoutingProvider.calls == 1` (thêm bộ đếm `calls` vào
   fake). Đây là phiên bản chạy được trong CI của QA-SRM-36 — S7 (Step 10) vẫn là phép đo thật bằng
   log trên emulator, ca này chỉ khoá ĐÚNG cơ chế sinh ra con số đó. Nhân tiện sửa lại tên/KDoc ca
   `S2` cũ (`ToggleRoutingProvider`) cho khớp ngữ nghĩa mới: cache chặn TRƯỚC khi provider có cơ hội
   chạy lần hai, nên `afterResult` (401 giả lập) của nó không bao giờ thật sự bị hỏi tới nữa — bản
   thân điều đó đã là bằng chứng cache thắng, không cần đợi provider lỗi.
3. **KDoc ghi sai thứ tự — sửa ở 3 nơi + `LLM.md`.** `MemberRouteProvider.kt` (`:domain`),
   `MemberRouteSource.kt` (KDoc lớp), `OnDevicePolylineCache.kt` (KDoc + 1 comment nội bộ), và 3 chỗ
   trong `LLM.md` (§3 `:domain/usecase/ObserveNavigationUseCase.kt`'s ghi chú, §3
   `:data/routing/MemberRouteSource.kt`/`OnDevicePolylineCache.kt`, §8.1 đoạn "nguồn tuyến của
   chuyển động gia đình") — tất cả đổi từ "provider → cache → synthetic" sang "cache → provider →
   synthetic", và số tầng đổi theo (`OnDevicePolylineCache` = tầng 1, provider = tầng 2).
4. **`reasonFor()` mất loại lỗi khi `message == null`.** Trước: `error.message ?: "NETWORK"` — một
   401 của GraphHopper (thường không có trường `message` trong body) log ra đúng chữ `NETWORK`,
   nhưng khi CÓ message thì loại lỗi biến mất khỏi dòng log. **Sửa:** luôn có tiền tố loại
   (`"NETWORK:${message.orEmpty()}"`, tương tự cho `VALIDATION`/`NOT_FOUND`/`UNEXPECTED`). **Giới
   hạn còn lại, không sửa được ở tầng này và không phải nợ mới của phase-04:** 401 và 429 log ra
   CÙNG tiền tố `NETWORK:` vì `RoutingErrorMapper` (viết từ phase-02/03) đã gộp mọi mã khác 400/501
   vào `AppError.Network` từ trước — muốn phân biệt 401 với 429 trên log phải sửa
   `RoutingErrorMapper`/`AppError`, ngoài phạm vi phase-04 và ngoài File Ownership của phase này.

**Kết quả sau fix-up:** `./gradlew :domain:test :data:test :app:test --no-configuration-cache`
BUILD SUCCESSFUL. `:domain` 125/125 (không đổi), `:data` 56/56 (+1 so với lần trước — ca NFR-2
mới), `:app` 1/1. `MemberRouteSourceTest` 7/7 (+1).

## 0-A. Fix P0 — cache trả gốc tuyến CŨ làm thành viên teleport (2026-08-26, đo thật)

Orchestrator chạy thật 12 phút, khoá thật, 2 thành viên/2 zone, bắt được 3 cú nhảy vị trí lớn
(**346.14 m** ×2 lần y hệt nhau cho Minh, **872.99 m** cho Lan), TẤT CẢ trùng đúng lúc log
`sim_route_loaded source=CACHE`. Truy tận cùng bằng file cache thật của Lan: gốc tuyến cache cách vị
trí hiện tại **876.09 m** ngay trước cú nhảy, còn **14.24 m** sau đó.

**Nguyên nhân:** `MemberRoamer.withPath` đặt `pathCursorMeters = 0.0` vô điều kiện — ngầm giả định
polyline luôn bắt đầu tại vị trí HIỆN TẠI. Tầng provider và tầng synthetic đều neo vào `request.from`
hiện tại nên không lệch; tầng cache trả hình học đã tính cho một `from` CŨ (khoá cache cố ý không
chứa `from`, để trúng cache được — `decisions.md` §C2). Lỗi này CÓ TỪ TRƯỚC bản sửa cache-first ở
§0 trên, nhưng thứ tự provider-first ban đầu che nó (cache gần như không bao giờ được đọc) — chuyển
sang cache-first làm nó LỘ RA, không phải do cache-first gây ra.

**Sửa:** thêm `RouteGeometryGuard.startsNear(points, from, toleranceMeters)` (`:domain`, public —
cùng lý do Fixed #27 cần public). Nhánh cache của `MemberRouteSource.path()` chỉ nhận khi ĐỦ CẢ
`isUsable(...)` LẪN `startsNear(...)`; trượt → coi cache như miss, log `reason=STALE_ORIGIN`, rơi
xuống tầng provider — tầng đó `cache.put` ĐÈ lên đúng khoá cũ với gốc mới, nên chặng lặp lại lần sau
lại trúng cache (tự lành). Ngưỡng = `MemberRoamer.STEP_METERS` (20.75 m, suy từ
`SIM_MEMBER_SPEED_MPS × MEMBER_ROAM_INTERVAL_MS`, không phải số ma thuật) — nhỏ hơn 1/16 cú nhảy nhỏ
nhất đo được. **Không đổi thiết kế theo lo ngại "gốc ENTER_ZONE do `random` chọn nên không bao giờ
hội tụ về cache-hit"** — số đo thật nói ngược lại: hai cú nhảy 346.14 m của Minh giống nhau tới hai
chữ số thập phân, nghĩa là gốc chặng của Minh ỔN ĐỊNH qua hai lần (bearing tất định của phase-02),
nên sau một lần ghi đè là hội tụ về cache-hit.

**Test:** `MemberRouteSourceTest` — gốc cache 900 m (cùng bậc số đo thật) bị từ chối, rơi về
provider, `calls==1`, tuyến trả về bắt đầu gần `from`; gốc 14 m (đối chứng, trong ngưỡng) vẫn trúng
cache, `calls==0`. `RouteGeometryGuardTest` — 4 ca `startsNear`, hai ca dùng đúng số đo thật
876.09 m → 14.24 m.

## 0-B. Fix quota-leak logging + Step 11 (real-route fixture) + VIỆC A/B/C/D (code review)

Ba lượt sửa tiếp theo, gộp một mục cho gọn — chi tiết đầy đủ nằm trong code, đây là tóm tắt:

1. **Tách `reason=GEOMETRY` theo nhánh.** Đo thật 10 phút, cache ấm: `PROVIDER=1, CACHE=4,
   SYNTHETIC=3, reason=GEOMETRY=3` trên 8 chặng — không đếm được request thật vì cache
   (không tốn request) và provider (đã tốn một request) log CÙNG một chuỗi. Tách thành
   `GEOMETRY_CACHE`/`GEOMETRY_PROVIDER`, giữ `STALE_ORIGIN` riêng (cũng không tốn request). Ghi
   công thức đếm request vào KDoc lớp `MemberRouteSource` (§C, dưới).
2. **Step 11 — `MemberRoamerRealRouteTest` + `RealRouteFixture`** (`:domain/src/test/kotlin/.../tracking/`):
   43 điểm THẬT (GraphHopper, lấy 2026-08-26 qua `MemberRouteSource` chạy thật), literal Kotlin
   (không JSON — `:domain` không có parser, phải giữ `:domain:test` < 5s). Chạy `MemberRoamer.tick`/
   `withPath` thật qua fixture này (forward = ENTER_ZONE, đảo ngược = LEAVE_ZONE) và khoá lại đúng
   bất biến "mỗi vòng đúng một ENTER rồi đúng một EXIT, xen kẽ, không dội" — lần này trên hình học
   đường THẬT, không chỉ `SyntheticPath`. Xanh, không cần chỉnh gì thêm.
3. **VIỆC A (gate G7) — `reasonFor()` log nguyên văn message của nhà cung cấp, có thể chứa toạ độ.**
   Body lỗi 400 của GraphHopper thường echo lại toạ độ đã gửi (ví dụ `"Point
   10.762081,106.660172 is not in the routing graph"`) — log thẳng là rò toạ độ ra logcat, dù tiền
   tố loại lỗi không rò gì. Sửa: `sanitizeRoutingErrorMessage()` (file mới
   `RoutingErrorSanitizer.kt`) xoá mọi cụm khớp `[-+]?\d+\.\d+` (toạ độ luôn dạng số thập phân) rồi
   cắt về 120 ký tự, TRƯỚC khi ghép vào `reason=NETWORK:…`/…. Một câu không mang số thập phân (đo
   thật khi khoá rỗng: "No API key specified. Please register and see documentation:
   https://www.graphhopper.com/developers/") đi qua nguyên vẹn. Test: `RoutingErrorSanitizerTest`
   (4 ca).
4. **VIỆC B (chặn phase-05, nghĩa vụ pháp lý) — ghi công có thể SAI NGUỒN.** `observeSource()` là
   MỘT `MutableStateFlow` dùng chung mọi thành viên, ghi-sau-thắng; chặng `WANDER` còn không publish
   gì (bỏ sót). Minh đang PROVIDER, Lan chuyển SYNTHETIC (hoặc ngược lại) ⇒ chỉ ai gọi
   `path()`/`wander()` sau cùng mới thắng — đúng thứ `docs/routing-and-map-attribution.md` §3 cấm
   ("chỉ hiện credit OSM khi đang thật sự hiển thị dữ liệu OSM"). Sửa: `RouteSourceAggregator` (file
   mới, `internal class`) giữ `RouteSourceInfo` RIÊNG mỗi `memberId`, phát trạng thái TỔNG HỢP: còn
   ai ở tầng provider/cache ⇒ hợp attribution của (các) người đó (`distinct()`, giữ thứ tự); không ai
   còn ⇒ SYNTHETIC, attribution rỗng. Thêm `MemberRouteProvider.wander(memberId, from, to)` —
   `MemberMovementSimulator.pathFor()` gọi nó cho chặng WANDER thay vì `SyntheticPath.between(...)`
   trực tiếp, để nguồn của thành viên đó được cập nhật đúng (0 lời gọi mạng cho WANDER, không đổi).
   Test: 2 ca mới trong `MemberRouteSourceTest` (hai-thành-viên trộn nguồn; một thành viên
   PROVIDER→WANDER).
5. **VIỆC C — thống nhất số hiệu tầng theo `decisions.md` §C2.** Reviewer bắt được 6 chỗ đánh SỐ
   HIỆU tầng ngược lại canonical (tầng 1 = provider, tầng 2 = cache, tầng 3 = synthetic — cố định
   theo bảng gốc, KHÔNG theo thứ tự kiểm tra thực thi) ở `MemberRouteSource.kt`,
   `OnDevicePolylineCache.kt` (KDoc + 1 comment), và 3 chỗ trong `LLM.md`. Thứ tự THỰC THI (cache
   trước, provider sau) không đổi — chỉ con số nhãn bị sửa lại cho khớp `decisions.md`. Cũng cập
   nhật KDoc lớp `MemberRouteSource` để liệt kê đủ 5 dạng `reason` mới (trước ghi "đúng hai dạng").
6. **VIỆC D — dọn nhỏ.** KDoc ca `S2` (test) sửa lại: nó KHÔNG khoá được thứ tự tầng (đảo ngược thứ
   tự thì S2 vẫn xanh, chỉ ca `NFR-2` bắt được) — đã viết lại cho đúng việc nó thật sự chứng minh.
   `RouteGeometryGuard.startsNear`'s KDoc trỏ thẳng vào mục §0-A này thay vì một tham chiếu treo.
   Số test `:domain` cập nhật đúng — xem §4.

**File mới thêm ở §0-A/§0-B (ngoài các file đã liệt kê ở §1 gốc):**

| File | Việc | Dòng |
|---|---|---|
| `data/.../data/routing/RouteSourceAggregator.kt` | Tạo — gộp `RouteSourceInfo` theo `memberId` | 37 |
| `data/.../data/routing/RoutingErrorSanitizer.kt` | Tạo — lọc toạ độ khỏi message lỗi trước khi log | 18 |
| `data/.../data/routing/RoutingErrorSanitizerTest.kt` | Tạo — 4 test | 39 |
| `domain/.../domain/tracking/RealRouteFixture.kt` | Tạo — 43 điểm THẬT (Step 11) | 68 |
| `domain/.../domain/tracking/MemberRoamerRealRouteTest.kt` | Tạo — 1 test, bất biến ENTER/EXIT trên tuyến thật | 92 |
| `domain/.../domain/tracking/RouteGeometryGuardTest.kt` | Sửa — +4 test `startsNear` | — |

**Kết quả sau §0-A/§0-B:** `./gradlew :domain:test :data:test :app:test --no-configuration-cache`
BUILD SUCCESSFUL. `:domain` **130/130** (+5: 4 `startsNear` + 1 `MemberRoamerRealRouteTest`),
`:data` **68/68** (+12: 2 stale-origin + 2 aggregation + 4 sanitizer + 4 đã có từ review giữa chừng
— khoá cache/PRD Q13/bán kính zone/guard-trên-cache), `:app` 1/1. Không file `:ui` nào bị chạm.

## 1. File tạo / sửa

| File | Việc | Dòng |
|---|---|---|
| `domain/.../domain/model/RouteSourceInfo.kt` | Tạo — `RouteSourceKind` + `RouteSourceInfo` | 18 |
| `domain/.../domain/repository/SimulatedRouteRepository.kt` | Tạo — `observeSource(): Flow<RouteSourceInfo>` | 14 |
| `domain/.../domain/repository/MemberRouteProvider.kt` | Tạo — cổng `path()` + `MemberRouteRequest` | 29 |
| `domain/.../domain/tracking/RouteGeometryGuard.kt` | Sửa — bỏ `internal` (xem §2 "quyết định tự chọn" #1) | +6/-1 |
| `data/.../data/routing/CachedRouteDto.kt` | Tạo — DTO lưu trữ cache | 24 |
| `data/.../data/routing/OnDevicePolylineCache.kt` | Tạo — đọc/ghi `filesDir/routes/*.json` | 87 |
| `data/.../data/routing/MemberRouteSource.kt` | Tạo — 3 tầng, `MemberRouteProvider` + `SimulatedRouteRepository` | 130 |
| `data/.../data/routing/OnDevicePolylineCacheTest.kt` | Tạo — 5 test | 98 |
| `data/.../data/routing/MemberRouteSourceTest.kt` | Tạo — 6 test (7 assertion-case, S4 gộp 3 mã lỗi) | 211 |
| `data/.../data/location/MemberMovementSimulator.kt` | Sửa — `+ memberRouteProvider`, `pathFor()` hỏi nó cho ENTER/LEAVE, WANDER giữ nguyên | 199 (từ 193) |
| `data/.../data/location/MemberMovementSimulatorTest.kt` | Sửa — `FakeMemberRouteProvider` + 1 test mới ("route source degrades") | 319 (từ 281) |
| `data/.../data/di/DataModule.kt` | Sửa — `OnDevicePolylineCache` + `MemberRouteSource` bindings | +18 |
| `app/.../KoinModulesTest.kt` | Sửa — `+ File::class` vào `extraTypes` | +9 |
| `LLM.md` | Sửa — §3 (`:domain` +3 file, `:data/routing/` +3 file), §8.1 (đoạn nguồn tuyến), §13 Open (+#18, #19; sửa #17), §13 Fixed (+#27, đóng #15 cũ) | |
| `docs/routing-and-map-attribution.md` | Sửa — §3 hàng #1 thêm `MemberRouteSource` | |
| `plans/.../docs/prd-delta-smooth-road-movement.md` | Sửa — thêm §8.1 trả lời Q8/Q13 | +11 |
| `plans/.../phase-04-nguon-tuyen-duong-lai.md` | Sửa — Todo List, Trạng thái | |

**Xoá:** không file nào.

## 2. Quyết định tự chọn (phase file để ngỏ hoặc mâu thuẫn với code hiện trạng)

1. **`RouteGeometryGuard` phải đổi từ `internal` sang `public` — KHÔNG nằm trong File Ownership
   của phase-04, nhưng là lỗi biên dịch hiển nhiên nếu không sửa.** Architecture của phase file bảo
   `MemberRouteSource` (`:data`) gọi `RouteGeometryGuard.isUsable(...)` (`:domain/tracking/`, khai
   `internal`). `internal` của Kotlin là biên theo MODULE Gradle (đã trả giá y hệt cho
   `SyntheticPath`/`ParametrizedPath`, `LLM.md` §13 Fixed #12) — `:data` không thấy được nó, sẽ
   không biên dịch. Đã sửa (bỏ `internal`), ghi rõ trong KDoc + `LLM.md` §13 Fixed #27 (đóng Open
   #15 cũ). **Đề nghị:** nếu orchestrator không đồng ý mở rộng file ownership, đây là điểm cần bàn
   trước khi merge — không có cách nào khác để guard chạy được từ `:data` mà giữ `internal`.
2. **Guard chạy ở `MemberRouteSource` (`:data`), KHÔNG ở `MemberRoamer.withPath` (`:domain`).**
   `LLM.md` §13 Open #15 (cũ) hình dung việc nối dây xảy ra TRONG `withPath`. Phase-04's Architecture
   lại vẽ guard chạy TRƯỚC khi `MemberRouteSource` trả điểm về — tức là một tuyến xấu không bao giờ
   tới được `withPath` để mà cần chặn ở đó nữa. Hai cách đều giữ đúng bất biến ENTER/EXIT; tôi theo
   đúng Architecture của phase file (không theo Open #15 cũ) vì đó là bản mới hơn và khớp sơ đồ. Đã
   ghi rõ trong `LLM.md` §13 Fixed #27.
3. **Koin: `binds(arrayOf(...))`, KHÔNG `bind X::class bind Y::class` như snippet phase file viết
   nguyên văn.** Đã thử đúng snippet — KHÔNG biên dịch: `bind` là
   `KoinDefinition<out S>.bind(KClass<S>): KoinDefinition<out S>`, hiệp biến trên `S`; bind lần hai
   đòi kiểu tương thích với kiểu TRẢ VỀ của bind lần đầu (`MemberRouteProvider`), không phải kiểu gốc
   (`MemberRouteSource`) — mà `SimulatedRouteRepository` không phải supertype của
   `MemberRouteProvider` nên lỗi `Argument type mismatch`. Sửa bằng `binds(Array<KClass<*>>)` —
   không bị ràng buộc hiệp biến đó. Đây là lỗi biên dịch hiển nhiên, không phải một lựa chọn thiết
   kế; đã ghi lý do vào comment tại chỗ trong `DataModule.kt`.
4. **`OnDevicePolylineCache` nhận `File`, KHÔNG `Context` — khác cách các class khác trong
   `DataModule.kt` xử lý phụ thuộc Android.** Bắt buộc để lớp này chạy JVM thuần
   (`OnDevicePolylineCacheTest`, không Robolectric — `Context.filesDir` là stub `not mocked`).
   `DataModule.kt` tính `File(androidContext().filesDir, "routes")` một lần lúc đăng ký Koin. Hệ
   quả: `KoinModulesTest.extraTypes` cần thêm `File::class` (đã thêm, comment giải thích tại chỗ).
5. **Log `reason=` không mang đúng mã HTTP nguyên văn.** `AppError` (sealed, dùng chung toàn app)
   chỉ giữ `message: String?`, không giữ mã HTTP số nguyên (`RoutingErrorMapper` đã bóc mã ra thành
   `AppError.Network`/`Validation`/`NotFound` rồi bỏ số). Phase file viết
   `reason=<HTTP code|TIMEOUT|GEOMETRY>`. **Cập nhật sau review (xem mục 0.4):** bản đầu
   `error.message ?: "NETWORK"` làm mất loại lỗi khi `message` có giá trị — sửa thành LUÔN có tiền tố
   loại (`NETWORK:`/`VALIDATION:`/`NOT_FOUND:`/`UNEXPECTED:`) rồi mới tới `message` (rỗng nếu
   không có). Không đổi `AppError` (model dùng chung) chỉ để phục vụ một dòng log. **Giới hạn đã
   biết, không sửa được ở tầng này:** 401 và 429 cùng log tiền tố `NETWORK:` vì `RoutingErrorMapper`
   đã gộp hai mã đó từ phase-02/03, trước phase-04 — không phải nợ mới.
6. **`MemberRouteRequest.zone` không nullable** — chặng `WANDER` (trường hợp duy nhất không có
   zone) không bao giờ tạo request này (Step 6 đã tách nó ra khỏi `memberRouteProvider` hoàn toàn ở
   `MemberMovementSimulator.pathFor`), nên không cần `Zone?`.
7. **Cache key dùng `String.format(Locale.ROOT, "%.5f", ...)`**, không phải `"%.5f".format(...)`
   mặc định (đọc locale máy) — locale dùng dấu phẩy thập phân sẽ làm khoá cache không nhất quán.
   Phase file không nói tới locale; tự chọn `Locale.ROOT` vì đây là khoá nội bộ, không phải chuỗi
   hiển thị.

## 3. GitNexus — impact analysis

Index bị stale (`node .gitnexus/run.cjs status` báo "stale", đứng ở commit `9c314d1`, hiện tại
`3a95726`) và `analyze` lại thất bại với lỗi nội bộ GitNexus (`Found duplicated primary key value
Property:...PolylineFollowerTest.kt:METERS_PER_DEGREE_LAT:0`) — không liên quan tới thay đổi của
phase này, không tự sửa (ngoài phạm vi). Impact chạy trên index cũ (đã tự cập nhật một phần khi
phát hiện file mới, ví dụ `MemberRouteSource.kt` xuất hiện trong kết quả dù chưa re-analyze):

| Symbol | Direction | Risk | Ghi chú |
|---|---|---|---|
| `RouteGeometryGuard` | upstream | **MEDIUM** (19 impacted, 9 direct) | Đã đổi `internal→public`; danh sách upstream gồm `MemberRouteSource.kt`, `TrackingRepositoryImpl.kt`, `SimulatedLocationSource.kt`, `ObserveNavigationUseCase.kt`, `MapViewModel.kt`, `NavigationViewModel.kt`, `FamilyTrackerApp.kt`, `FamilyTrackerNavHost.kt`, `UiModule.kt`, `HistoryViewModel.kt` — coarse-grained (quan hệ `IMPORTS` cấp file qua `RouteGeometryGuard.kt`'s package/module, không phải call graph cấp hàm tới `isUsable`). Không HIGH/CRITICAL |
| `dataModule` | upstream | LOW (0 impacted) | |
| `MemberRouteSource` (mới) | upstream | LOW (2 impacted, ghi chú "lower-bound": interface `MemberRouteProvider` có 2 implementation, caller qua DI không trace hết được) | |
| `KoinModulesTest` | upstream | LOW (0 impacted) | |
| `MemberMovementSimulator` | upstream | LOW, 2 upstream | Đã chạy trước bởi orchestrator (không chạy lại) |

`detect_changes({scope: "compare", base_ref: "main"})` (chỉ thấy 5 file ĐÃ TRACKED vì chưa `git
add` — 8 file mới KHÔNG hiện ra ở đây): **"Risk level: high"** tổng hợp, 25 symbol, 8 execution
flow bị ảnh hưởng, tất cả xoay quanh `moveOne`/`pathFor` (`MemberMovementSimulator.kt`) toả ra
`InitialBearing`/`PointAt`/`HaversineMeters`/`CountCrossings`/`FileFor`/`PerpendicularUnitVector`.
**Đây đúng là hai hàm phase-04 được giao thay đổi** (seam có chủ ý của phase-02) — không phải mở
rộng blast radius ngoài kế hoạch. Báo cáo mức "high" này ở đây để orchestrator tự đánh giá, không tự
ý coi là "đã ổn" — mọi `impact()` per-symbol tôi tự chạy đều LOW/MEDIUM, không HIGH/CRITICAL.

## 4. Test — `./gradlew :domain:test :data:test :app:test --no-configuration-cache`

**BUILD SUCCESSFUL (số cuối cùng, sau tất cả các lượt sửa ở §0/§0-A/§0-B).** 0 fail, 0 error.

| Module | Test suite mới/sửa | Pass/Total |
|---|---|---|
| `:domain` | `RouteGeometryGuardTest` (+4 `startsNear`, 8→12), `MemberRoamerRealRouteTest` (mới, 1), `RealRouteFixture.kt` (fixture, không phải test) | **130/130** |
| `:data` | `OnDevicePolylineCacheTest` (mới, 5), `MemberRouteSourceTest` (mới → 15: 3 gốc S1/S4/S5/S8/WANDER + NFR-2 + khoá cache/PRD-Q13/bán-kính/guard-trên-cache + 2 stale-origin + 2 aggregation), `RoutingErrorSanitizerTest` (mới, 4), `MemberMovementSimulatorTest` (sửa, 9 — 8 cũ + 1 mới, `FakeMemberRouteProvider` + `wander()`) | **68/68** |
| `:app` | `KoinModulesTest` (sửa — `+File::class`) | 1/1 |

Tổng 199 test qua cả 3 module cho phase-04 (đúng lúc chốt), 0 fail.

## 5. Việc CHƯA làm (Step 9, 10 — orchestrator tự chạy; Step 11 ĐÃ LÀM, xem §0-B)

- **Step 9 (3 kịch bản chạy thật trên `emulator-5554`) — orchestrator đã tự chạy**, mang số đo thật
  quay lại cho tôi sửa (§0-A, §0-B mục 1): cú nhảy 346.14m/872.99m (9c), 3 dòng GEOMETRY/8 chặng (9c).
  9a/9b không thấy báo lỗi từ orchestrator, coi như qua.
- **Step 10 (đếm request 10 phút, QA-SRM-36):** orchestrator đo được PROVIDER=1, CACHE=4,
  SYNTHETIC=3, GEOMETRY=3 trên 8 chặng — dưới trần 12 rất thoải mái, nhưng orchestrator nói sẽ ĐO
  LẠI sau khi tôi xong (P0 fix + tách reason) để có con số request thật chính xác. Chưa có kết quả
  đo lại cuối cùng khi viết báo cáo này.
- ~~Step 11~~ **ĐÃ LÀM** — `MemberRoamerRealRouteTest` + `RealRouteFixture`, xem §0-B mục 2. Xanh.
- **Chưa đo lại `LLM.md` §13 Open #17** (SyntheticPath đi đúng nửa tốc độ khai báo, 4.15 m/s thay vì
  8.3) — CẢNH BÁO: bây giờ tầng 2 (provider mạng thật) đã nối dây, polyline OSM thật (đỉnh cách nhau
  ~5m theo ước tính cũ) rất có thể tái hiện đúng lỗi này ở mức NẶNG HƠN SyntheticPath (~2 m/s). Không sửa trong
  phase này vì `PolylineFollower.kt`/`SyntheticPath.kt` không nằm trong File Ownership của phase-04.
  Đề nghị đo thật ở Step 9a (log `speedMps` ghi vào `location_points` khi `source=PROVIDER`) trước
  khi phase-06 chốt B4 — nếu lệch nặng như dự đoán, đây là việc phải sửa ở `PolylineFollower.advance`
  (ví dụ: gộp nhiều đỉnh liền kề trong một bước thay vì dừng ở đỉnh đầu tiên), ngoài phạm vi phase-04.

## 6. Mâu thuẫn phase file vs. code hiện trạng (báo cáo, không tự ý đổi hướng)

Xem mục 2 ở trên — 3 điểm mâu thuẫn/lỗi biên dịch đã tự sửa vì hiển nhiên (RouteGeometryGuard
`internal`, Koin `bind` chuỗi, không có gì khác cần dừng lại hỏi). Không phát hiện mâu thuẫn nào
khác giữa phase file và code hiện trạng ngoài các mục đã liệt kê.

## 7. Ghi chú môi trường — file bị ghi đè giữa lượt sửa

Trong lúc làm §0-A, một lần `Edit` vào `MemberRouteSource.kt` báo "file đã đổi trên đĩa kể từ lần
đọc trước" và khi đọc lại thì đúng thay đổi `passesOriginGuard`/import `MemberRoamer` tôi vừa thêm đã
biến mất — file quay về đúng trạng thái NGAY TRƯỚC bản sửa đó. Đã áp lại y hệt bản sửa và xác nhận
bằng `grep` rồi build xanh; không đoán nguyên nhân (có khả năng `code-reviewer`/`code-simplifier`
chạy song song trong cùng phiên đã tự thêm rồi tự viết lại file này — thấy dấu vết từ
`reports/reviewer-phase-04-report.md`/`simplifier-phase-04-report.md` mới xuất hiện, không phải do
tôi tạo). Không phát hiện thêm lần thứ hai sau đó. Nêu ở đây để orchestrator biết, không phải một
lỗi logic trong code.
