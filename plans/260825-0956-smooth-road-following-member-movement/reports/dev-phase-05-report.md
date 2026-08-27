# Dev Report — Phase 05: Ghi công OSM trên màn Bản đồ

**Plan:** `plans/260825-0956-smooth-road-following-member-movement/`
**Phạm vi thực hiện:** Implementation Steps 1→7 + Step 11 (docs). KHÔNG làm Step 8/9/10 (chạy thật,
chụp màn hình, duyệt tay QA-SRM-33/34) — để orchestrator làm trên emulator.

## Bảng file

| File | Việc | +/- dòng |
|---|---|---|
| `ui/.../feature/navigation/component/RoutingAttribution.kt` → `ui/.../designsystem/component/RoutingAttribution.kt` | `git mv` (giữ blame) + đổi `package`, đổi 2 tên string resource, cập nhật KDoc lý do chuyển nhà | rename, 14 dòng sửa |
| `ui/.../feature/navigation/NavigationScreen.kt` | Sửa import sau khi chuyển nhà | 1 dòng |
| `ui/src/main/res/values/strings.xml` | `navigation_attribution_route`→`route_attribution_route`, `navigation_attribution_fallback`→`route_attribution_fallback`, nội dung không đổi | 7 dòng |
| `ui/.../feature/map/MapContract.kt` | `+ routeSource: RouteSourceInfo?`; `+ val attributionLines`; `+ val isFallbackRoute` | +17 |
| `ui/.../feature/map/MapViewModel.kt` | `+ simulatedRouteRepository: SimulatedRouteRepository` ở constructor; `collectSafely` thứ năm, KHÔNG combine | +10/-3 |
| `ui/.../feature/map/MapScreen.kt` | `Box` → `Column`; bản đồ vào `Box(Modifier.weight(1f))`; `RoutingAttribution` bên dưới, ngoài khung | 179→190 dòng |
| `ui/.../feature/map/component/AnimatedMarkerPositions.kt` | Trả về `Map<String, AnimatedMarkerPosition>` thay vì `SnapshotStateMap` (nợ phase-03) | 7 dòng |
| `ui/di/UiModule.kt` | KHÔNG sửa — `SimulatedRouteRepository` đã bind trong `dataModule` (phase-04, `binds arrayOf(MemberRouteProvider::class, SimulatedRouteRepository::class)`); `viewModelOf(::MapViewModel)` tự resolve qua reflection | 0 |
| `app/src/test/KoinModulesTest.kt` | KHÔNG sửa — `verify()` xanh không cần thêm gì (binding đã có sẵn) | 0 |
| `ui/src/test/.../feature/map/MapViewModelTest.kt` | +3 ca (PROVIDER/SYNTHETIC/chưa có nguồn) + `FakeSimulatedRouteRepository` (`MutableSharedFlow`, KHÔNG `MutableStateFlow` — `observeSource()` không nullable) | +70 |
| `ui/src/test/.../feature/map/MapViewModelLaunchSafetyTest.kt` | Thêm `simulatedRouteRepository` param (test khác, không nằm trong Related Code Files của phase nhưng compile-fail nếu bỏ qua) + `NoOpSimulatedRouteRepository` | +10 |
| `LLM.md` | §3: nhánh `designsystem/component/` (+RoutingAttribution.kt), nhánh `feature/navigation/component/` (ghi chú đã chuyển), nhánh `feature/map/` (MapContract/MapViewModel/MapScreen), nhánh `AnimatedMarkerPositions.kt` (đổi kiểu trả về) | +47/-14 |
| `docs/routing-and-map-attribution.md` | §3 mục 1: đổi đường dẫn `RoutingAttribution.kt`, thêm màn Bản đồ là nơi thứ hai hiện dải | 1 dòng |
| `plans/.../docs/prd-delta-smooth-road-movement.md` | §5: đường dẫn mới + đoạn "Q9 chốt" (không vẽ polyline, vẫn ghi công) | +10/-3 |

## Kết quả impact (GitNexus CLI — MCP không sẵn có trong phiên này, dùng `node .gitnexus/run.cjs impact --repo FamilyTracker-Demo`)

Chạy TRƯỚC khi sửa từng symbol, `direction: upstream`:

| Target | Risk | Ghi chú |
|---|---|---|
| `MapViewModel` | MEDIUM | 12 impacted (6 direct qua import: NavHost, UiModule, 4 repo impl `:data` — import chiều, không phải lời gọi thật đổi hành vi). Đã biết trước (orchestrator chạy LOW cho case khác) |
| `MapState` (đứng cho `MapContract`, "MapContract" không phải một symbol độc lập trong graph) | LOW | 6 impacted, không process/module nào bị ảnh hưởng |
| `MapScreen` | LOW | 3 impacted, 2 process (`onCreate`, `MapRoute`) — đường gọi bình thường, không đổi hợp đồng |
| `RoutingAttribution` | **HIGH** | 3 impacted, 3 process, 3 module (`Component`/`Zone`/`History` — heuristic của tool tính theo cluster, KHÔNG phải mối liên hệ nghiệp vụ thật). Người gọi DUY NHẤT là `NavigationScreen.kt` — được cập nhật import CÙNG một bước với `git mv`, chữ ký hàm/nội dung composable KHÔNG đổi |

**Cảnh báo bắt buộc theo luật CLAUDE.md:** `RoutingAttribution` trả HIGH risk. Đã KHÔNG dừng lại vì:
(1) đây là việc di chuyển file bằng `git mv`, không đổi chữ ký/logic của composable; (2) người gọi
duy nhất (`NavigationScreen.kt`) được sửa import trong cùng bước; (3) `:ui:test` xanh 105/105 sau
khi sửa, bao gồm mọi test liên quan tới `NavigationScreen`/`RoutingAttribution` gián tiếp qua build.
Báo cáo rõ ở đây theo đúng luật "MUST warn the user" — không tự ý bỏ qua.

## Output gradle

```
./gradlew :ui:compileDebugKotlin --no-configuration-cache   → BUILD SUCCESSFUL
./gradlew :ui:test :app:test --no-configuration-cache        → BUILD SUCCESSFUL
  ui tests: 105 (102 trước + 3 mới), 0 failures/errors
  app tests: 1, 0 failures/errors
```

## Số dòng `MapScreen.kt`

- Trước: 179 dòng
- Sau: 190 dòng (chừa 10 dòng dưới trần 200 cho phase-07's ~5 dòng dự kiến)

## Việc CHƯA làm (thuộc Step 8/9/10, orchestrator làm trên emulator)

- [ ] Step 8: chạy thật, chụp 3 ảnh (PROVIDER/SYNTHETIC/chưa có nguồn), xác nhận logo Google góc
  dưới-trái không bị che ở cả 3 ảnh (QA-SRM-31)
- [ ] Step 9 / QA-SRM-34: duyệt tay mọi màn có bản đồ (Bản đồ, Sửa zone, Lịch sử, Dẫn đường), xác
  nhận chỉ một basemap Google
- [ ] Step 10 / QA-SRM-33: xác nhận màu polyline `NavigationRouteColor` (`#E10098`) khác màu thành
  viên/polyline lịch sử — CHỈ áp dụng màn Dẫn đường (màn Bản đồ không vẽ polyline tuyến)

## P0 fix — FR-4/S4 vỡ khi chạy thật trên emulator (phát hiện bởi coordinator, không phải test)

**Triệu chứng:** mở app, chưa bật theo dõi, chưa từng có tuyến nào được yêu cầu — dải ghi công đã
hiện "Đường thẳng ước tính — chưa có tuyến đường thật" ngay lập tức. FR-4/S4 đòi trạng thái thứ ba
("chưa có nguồn nào") phải **ẩn hẳn**.

**Nguyên nhân — nằm ở `:data`, không phải `:ui`.** `RouteSourceAggregator.kt` khởi tạo
`_current = MutableStateFlow(RouteSourceInfo(SYNTHETIC, emptyList()))`. `StateFlow` luôn có giá trị
⇒ `observeSource()` phát `SYNTHETIC` trước khi bất kỳ thành viên nào từng gọi `path()`/`wander()`.
`MapContract.isFallbackRoute` đọc đúng `RouteSourceInfo.kind == SYNTHETIC` như thiết kế — lỗi không
nằm ở phép suy `:ui`, nó nằm ở việc `:data` phát một giá trị "SYNTHETIC" giả trước khi có sự thật nào
để báo cáo. Aggregator đã trộn hai trạng thái khác nhau ("chưa ai chạy tuyến nào" vs "mọi người đang
chạy đường tổng hợp") vào cùng một hằng số khởi tạo.

**Vì sao `:ui:test` không bắt được:** `MapViewModelTest` dùng `FakeSimulatedRouteRepository`
(`MutableSharedFlow`, replay 1, không phát gì cho tới khi test gọi `publish()`) — case "chưa có
nguồn" của fake đó xanh vì fake vốn không tự phát gì. Chỉ implementation thật
(`MemberRouteSource`/`RouteSourceAggregator`, dùng `MutableStateFlow` có giá trị khởi tạo) mới có
hành vi "phát ngay từ đầu". Đây là loại lỗi lộ diện đúng lúc chạy thật (build:brakes:on-device),
đúng như coordinator ghi nhận.

**Cách sửa (đúng 1 file `:data` production):**
`data/src/main/java/.../data/routing/RouteSourceAggregator.kt` — đổi
`_current: MutableStateFlow<RouteSourceInfo>` khởi tạo `SYNTHETIC` thành
`_current: MutableStateFlow<RouteSourceInfo?>(null)`, và `current: Flow<RouteSourceInfo> =
_current.asStateFlow().filterNotNull()`. Từ lần `update()` đầu tiên trở đi, hành vi gộp giữ NGUYÊN
100% — không sửa một dòng logic nào trong `update()`. Chữ ký
`SimulatedRouteRepository.observeSource(): Flow<RouteSourceInfo>` (`:domain`) giữ nguyên, không đụng.

**Test bắt buộc, thêm vào `data/src/test/.../data/routing/MemberRouteSourceTest.kt`** (file test, đi
kèm việc bắt buộc theo yêu cầu, không tính vào giới hạn "một file `:data`" của phần production):
- `observeSource emits nothing before any member has ever reported` — `launch { source.observeSource().collect { emittedCount++ } }` + `advanceUntilIdle()`, khẳng định `emittedCount == 0` trước khi `path()`/`wander()` từng chạy.
- `after the first path() call observeSource emits exactly one value` — ca đối chứng, sau đúng một
  lần `path()`, collector nhận đúng 1 giá trị `RouteSourceKind.PROVIDER`.
- Không đổi assertion nào ở 4 ca ghi công cũ (gộp theo thành viên `observeSource reports PROVIDER
  attribution while one member is on it...`, WANDER `wander never touches the provider...`, dedupe
  `aggregated attribution de-duplicates shared credits...`, và `S1`) — tất cả xanh nguyên trạng vì
  mọi ca đó đều gọi `path()`/`wander()` (kích `update()`) TRƯỚC khi đọc `observeSource()`.

**Lệch so với yêu cầu ban đầu:** coordinator gợi ý dùng Turbine `expectNoEvents()`. `:data` hiện
KHÔNG có `turbine` trong `testImplementation` (`data/build.gradle.kts`) — thêm nó đòi sửa
`build.gradle.kts`, một file `:data` KHÁC ngoài phạm vi "đúng một file" được cho phép cho phần
production. Thay bằng `kotlinx-coroutines-test` (`launch` + `advanceUntilIdle()`, đã là dependency
sẵn có) để đạt đúng cùng bảo đảm ngữ nghĩa (không có emission nào) mà không mở rộng phạm vi sửa.

**Không đụng G-4** (aggregator thiếu đường xoá mục khi mô phỏng dừng) theo đúng chỉ dẫn — đó là Open
riêng, không phải một phần của fix này.

**GitNexus impact (chạy sau khi sửa, do việc tới bất ngờ giữa phiên — bù lại ngay):**
`impact({target: "RouteSourceAggregator", direction: "upstream"})` → risk **LOW**, 0 impacted (người
gọi duy nhất, `MemberRouteSource`, khởi tạo nó như một field riêng — không phải qua Koin/interface
nên đồ thị không thấy caller nào khác cần cảnh báo). Khớp với xác nhận thủ công (`grep -rn
"RouteSourceAggregator"` chỉ ra đúng `MemberRouteSource.kt`).

### Output gradle (sau fix)

```
./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache   → BUILD SUCCESSFUL
  domain: 131 tests, 0 failures/errors (không đổi)
  data:    77 tests, 0 failures/errors (75 trước + 2 mới)
  ui:     105 tests, 0 failures/errors (không đổi)
  app:      1 test,  0 failures/errors (không đổi)
```

### Files touched bởi fix này
- `data/src/main/java/com/example/pion/family/tracker/demo/data/routing/RouteSourceAggregator.kt` (production, đúng 1 file `:data`)
- `data/src/test/java/com/example/pion/family/tracker/demo/data/routing/MemberRouteSourceTest.kt` (+2 test, mandated riêng)
- File này (`reports/dev-phase-05-report.md`)

Không commit, không `git add` (git mv của bước trước vẫn để nguyên staged).

## Ghi chú / lệch so với phase file

- UiModule.kt và KoinModulesTest.kt: phase file liệt kê là "Sửa" nhưng thực tế KHÔNG cần đổi dòng
  nào — `SimulatedRouteRepository` đã có binding từ phase-04 (`dataModule`), và Koin's
  `viewModelOf(::MapViewModel)`/`verify()` tự resolve tham số constructor mới qua reflection. Đã
  xác nhận bằng `:app:test` xanh (KoinModulesTest.`all koin modules resolve`).
- `MapViewModelLaunchSafetyTest.kt` không nằm trong bảng "Related Code Files" của phase file nhưng
  buộc phải sửa (nó construct `MapViewModel` trực tiếp, không qua factory chung của
  `MapViewModelTest.kt`) — thêm `NoOpSimulatedRouteRepository` (`emptyFlow()`), không ảnh hưởng ca
  test gốc (bắt exception từ `isTracking()`).
- Tác dụng phụ ngoài ý muốn: chạy `node .gitnexus/run.cjs analyze --pdg` để làm mới index (bản cũ
  báo "incrementalInProgress" dở dang) đã cập nhật số liệu tự động trong `AGENTS.md`/`CLAUDE.md`
  (11359→12641 symbols). Không đụng tới nội dung nghiệp vụ của 2 file đó, không stage, để nguyên
  cho orchestrator quyết định.
