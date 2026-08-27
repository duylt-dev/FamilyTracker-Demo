# Phase 05 — Ghi công OpenStreetMap trên màn Bản đồ

## Context Links

- [`plan.md`](plan.md) · [`decisions.md`](decisions.md) (trả lời PRD Q9)
- Yêu cầu: [PRD delta](docs/prd-delta-smooth-road-movement.md) US-46, §5 (Delta UI), X5
- Nghiệm thu: **QA-SRM-16, 30, 31, 32, 33, 34**, UAT-07
- Pháp lý: [`docs/routing-and-map-attribution.md`](../../docs/routing-and-map-attribution.md) §3 mục 1–3, đoạn "Chỉ hiện credit OSM khi đang thật sự hiển thị dữ liệu OSM"
- Kiến trúc: `LLM.md` §12 (composable ≥2 feature → `designsystem/component/`), §3
- MVI: `docs/android-mvi-best-practices.md` §2, §4, §9

## Overview

| | |
|---|---|
| **Ưu tiên** | P1 (US-46 là P0 về nội dung, nhưng chỉ có nghĩa khi phase 04 đã bật tầng 1/2) |
| **Trạng thái** | code xanh (Step 1-7, 11 xong) — chờ Step 8/9/10 trên emulator |
| **Ước lượng** | 2h |
| **Phụ thuộc** | Phase 04 (`SimulatedRouteRepository.observeSource()`) |

Vị trí marker mà người dùng nhìn thấy được suy ra từ hình học OSM khi đang chạy tầng 1/2. Đó là
một *Produced Work* ⇒ phát sinh nghĩa vụ ghi công, kể cả khi **không vẽ polyline nào lên bản đồ**.
Phase này hiện dải ghi công đúng ba trạng thái, và **chỉ** khi thật sự có dữ liệu OSM.

## Key Insights

1. **PRD Q9 để ngỏ; plan này chốt: KHÔNG vẽ polyline, NHƯNG VẪN ghi công.** Lý do: nghĩa vụ của
   ODbL gắn với việc *sử dụng và hiển thị* dữ liệu, không gắn với việc có vẽ đường màu hồng hay
   không. Vị trí marker là thứ dữ liệu OSM sinh ra và người dùng nhìn thấy. Chi phí để đúng: một
   composable **đã tồn tại**, dùng lại. Chi phí để sai: mất chính lập luận đã dùng để cho phép
   tính năng (`routing-and-map-attribution.md` §3 mục 1).
2. **Ca âm quan trọng ngang ca dương.** Đang chạy tầng 3 (`SyntheticPath`) thì trên màn **không có
   một byte dữ liệu OSM nào** — hiện credit lúc đó là **ghi sai nguồn**, bị cấm ở X5 và ở
   `routing-and-map-attribution.md` §3. `RoutingAttribution.kt:36-40` đã cài đúng ba trạng thái;
   việc của phase này là nuôi nó bằng dữ liệu đúng, không phải viết logic mới.
3. **`RoutingAttribution` phải chuyển nhà.** LLM.md §12: một composable lên `designsystem/component/`
   **khi thật sự có 2 chỗ dùng** — bây giờ đúng là 2 (Dẫn đường + Bản đồ). Để nó ở
   `feature/navigation/component/` rồi cho `feature/map/` import chéo là một feature phụ thuộc
   feature, chính thứ mà cấu trúc package đang chặn.
4. **Dải ghi công nằm NGOÀI khung bản đồ.** Góc dưới-trái **bên trong** khung là logo/attribution
   của Google, không được che, không được dời. `MapScreen` hiện là `Box` phủ toàn màn cho bản đồ →
   phải đổi thành `Column` với bản đồ `weight(1f)` và dải ghi công bên dưới, trên `bottomBar`.
5. **Chuỗi đổi tên.** `navigation_attribution_route` / `navigation_attribution_fallback` không còn
   chỉ thuộc màn Dẫn đường. Đổi thành `route_attribution_route` / `route_attribution_fallback`
   trong cùng commit chuyển nhà composable — tên sai chỗ là loại nợ tự nhân lên.

## Requirements

**Chức năng**

- FR-1 Đang chạy tầng 1/2 → hiện `© OpenStreetMap contributors` **và** tên nhà cung cấp, lấy từ
  `Directions.attribution` đi kèm dữ liệu, **không** ghép chuỗi từ `engineId` (US-46, QA-SRM-30).
- FR-2 Đang chạy tầng 3 → **không** hiện credit OSM; hiện nhãn ước tính (QA-SRM-32, QA-SRM-16).
- FR-3 Dải ghi công nằm ngoài khung bản đồ, không che logo/credit của Google (QA-SRM-31).
- FR-4 Trạng thái thứ ba (chưa có nguồn nào, màn vừa mở) → **ẩn hẳn**, không vẽ `Text` rỗng.

**Phi chức năng**

- NFR-1 `MapViewModel` không import Compose/Android; nguồn là `Flow` từ `:domain/repository/`.
- NFR-2 `MapScreen.kt` giữ dưới 200 dòng (LLM.md §5).
- NFR-3 Không chuỗi mới nào được **viết**; chỉ đổi tên chuỗi đã có (PRD delta §5).

## Architecture

```
:data MemberRouteSource ──► SimulatedRouteRepository.observeSource(): Flow<RouteSourceInfo>
                                              │  (cổng ở :domain/repository/, phase 04)
                                              ▼
                              MapViewModel.collectSafely { setState { copy(routeSource = it) } }
                                              ▼
   MapState.routeSource: RouteSourceInfo?
     val attributionLines: List<String>  get() = routeSource?.attribution.orEmpty()
     val isFallbackRoute: Boolean        get() = routeSource?.kind == RouteSourceKind.SYNTHETIC
                                              ▼
   MapScreen  Column {
                  FamilyTrackerMap(Modifier.weight(1f))     ← khung bản đồ, Google credit BÊN TRONG
                  RoutingAttribution(                        ← dải riêng, BÊN NGOÀI khung
                      attributionLines = state.attributionLines,
                      isFallbackStraightLine = state.isFallbackRoute,
                  )
              }   bottomBar = FamilyTrackerBottomBar
```

**`attributionLines` và `isFallbackRoute` là `val` tính toán**, không phải field lưu riêng — MVI
doc §2 "Derive, don't duplicate", cùng mẫu `selfLocation`/`otherMembers` đang có trong
`MapContract.kt`. Hai field tách rời sẽ có ngày nói hai chuyện khác nhau, và ngày đó là ngày app
ghi sai nguồn.

## Related Code Files

**Di chuyển** (không phải tạo mới)

| Từ | Đến |
|---|---|
| `ui/.../feature/navigation/component/RoutingAttribution.kt` | `ui/.../designsystem/component/RoutingAttribution.kt` (bỏ `internal` → `public` hoặc giữ `internal` vì cùng module `:ui`) |

**Sửa**

| Đường dẫn | Việc |
|---|---|
| `ui/src/main/java/.../ui/feature/navigation/NavigationScreen.kt` | Cập nhật import sau khi chuyển nhà |
| `ui/src/main/res/values/strings.xml` | Đổi tên `navigation_attribution_route` → `route_attribution_route`, `navigation_attribution_fallback` → `route_attribution_fallback` (nội dung **không đổi**) |
| `ui/src/main/java/.../ui/feature/map/MapContract.kt` | `+ routeSource: RouteSourceInfo? = null`; `+ val attributionLines`, `+ val isFallbackRoute` |
| `ui/src/main/java/.../ui/feature/map/MapViewModel.kt` | `+ simulatedRouteRepository` ở constructor; `collectSafely` thứ năm |
| `ui/src/main/java/.../ui/feature/map/MapScreen.kt` | `Box` → `Column`; bản đồ `weight(1f)`; `RoutingAttribution` bên dưới. Banner/`TrackingToggle` giữ overlay trên **khung bản đồ**, không trên dải ghi công |
| `ui/src/main/java/.../ui/di/UiModule.kt` | `MapViewModel` có tham số mới — `viewModelOf` phải resolve được |
| `ui/src/test/java/.../ui/feature/map/MapViewModelTest.kt` | + 3 ca: PROVIDER → có credit; SYNTHETIC → không credit, có nhãn ước tính; chưa có nguồn → cả hai rỗng |
| `app/src/test/java/.../KoinModulesTest.kt` | Binding mới |
| `LLM.md` | §3: chuyển `RoutingAttribution.kt` sang nhánh `designsystem/component/`, ghi lý do (§12 đủ 2 chỗ dùng); nhánh `feature/map/` ghi dải ghi công |
| `docs/routing-and-map-attribution.md` | §3 mục 1 cột "Thực hiện ở": đổi đường dẫn `RoutingAttribution.kt`, thêm màn Bản đồ là nơi thứ hai hiện dải |
| `docs/prd-delta-smooth-road-movement.md` | §5: chốt câu trả lời Q9 (không vẽ polyline, vẫn ghi công) |

**Xoá:** không.

## Implementation Steps

1. Chuyển `RoutingAttribution.kt` sang `designsystem/component/` bằng **`git mv`** (giữ lịch sử
   `git blame` — file này mang lập luận pháp lý trong KDoc, mất blame là mất dấu vết ai quyết định
   gì). Cập nhật import ở `NavigationScreen.kt`.
2. Đổi tên hai chuỗi trong `strings.xml`, cập nhật hai chỗ dùng.
3. `MapContract`: thêm `routeSource` + hai `val` tính toán. Import `RouteSourceInfo` từ `:domain/model/`.
4. `MapViewModel`: thêm tham số constructor và `collectSafely` thứ năm. **Không** `combine` với
   các nguồn khác.
5. `MapScreen`: `Box` → `Column`. Bản đồ vào một `Box(Modifier.weight(1f))` để banner và
   `TrackingToggle` vẫn `align()` được **trong khung bản đồ**; `RoutingAttribution` ở dưới, ngoài
   `Box` đó. Kiểm lại số dòng file (< 200).
6. `UiModule` + `KoinModulesTest`.
7. `./gradlew :ui:test :app:test --no-configuration-cache`.
8. **Chạy thật, chụp màn hình cả 3 trạng thái** và dán vào dev report:
   a. tầng PROVIDER → thấy `Tuyến đường: GraphHopper · OpenStreetMap contributors`;
   b. tầng SYNTHETIC (build không khoá) → thấy nhãn ước tính, **không** có chữ OpenStreetMap;
   c. màn vừa mở, chưa có nguồn → không có dải nào.
   Trong cả 3 ảnh, **logo và credit của Google ở góc dưới-trái khung bản đồ phải nhìn thấy đầy đủ**
   (QA-SRM-31).
9. **QA-SRM-34** — duyệt hết mọi màn có bản đồ (Bản đồ, Sửa zone, Lịch sử, Dẫn đường), xác nhận chỉ
   có một basemap của Google, không tile bên thứ ba nào. Ghi vào dev report.
10. **QA-SRM-33** — màu polyline tuyến (`NavigationRouteColor` `#E10098`, `Color.kt:60`) khác màu
    thành viên và khác màu polyline lịch sử. Màn Bản đồ **không vẽ polyline tuyến** nên ca này chỉ
    áp cho màn Dẫn đường; xác nhận lại và ghi rõ phạm vi trong dev report thay vì đánh dấu N/A.
11. Cập nhật `LLM.md` §3, `routing-and-map-attribution.md` §3, PRD delta §5 cùng commit.

## Todo List

- [x] `git mv RoutingAttribution.kt` → `designsystem/component/`, sửa import
- [x] Đổi tên 2 chuỗi trong `strings.xml` + 2 chỗ dùng
- [x] `MapState.routeSource` + `attributionLines` + `isFallbackRoute` (tính toán, không lưu)
- [x] `MapViewModel` `collectSafely` thứ năm + `UiModule` + `KoinModulesTest` — `UiModule`/`KoinModulesTest`
      không cần sửa dòng nào, binding `SimulatedRouteRepository` đã có từ phase-04; xác nhận bằng
      `KoinModulesTest` xanh (`reports/dev-phase-05-report.md`)
- [x] `MapScreen`: `Column`, bản đồ `weight(1f)`, dải ghi công bên ngoài khung
- [x] `MapViewModelTest` 3 ca (PROVIDER / SYNTHETIC / chưa có nguồn)
- [x] 3 ảnh chụp màn hình cho 3 trạng thái, kèm góc dưới-trái của Google — **orchestrator, trên emulator**
- [x] QA-SRM-34 duyệt hết màn có bản đồ — **orchestrator, trên emulator**
- [x] QA-SRM-33 xác nhận phạm vi (chỉ màn Dẫn đường) — **orchestrator, trên emulator**
- [x] `LLM.md` §3 + `routing-and-map-attribution.md` §3 + PRD delta §5 cùng commit

## Success Criteria

| # | Điều kiện | Cách kiểm | QA |
|---|---|---|---|
| S1 | Tầng PROVIDER/CACHE → dải hiện đúng nội dung `Directions.attribution`, không ghép từ `engineId` | `MapViewModelTest` + ảnh chụp | QA-SRM-30 |
| S2 | Tầng SYNTHETIC → **không** có chuỗi "OpenStreetMap" trên màn | `MapViewModelTest` assert `attributionLines.isEmpty()` + ảnh chụp | QA-SRM-32, QA-SRM-16 |
| S3 | Logo + credit Google ở góc dưới-trái khung bản đồ nhìn thấy đầy đủ ở cả 3 trạng thái | 3 ảnh chụp | QA-SRM-31 |
| S4 | Chưa có nguồn → không vẽ `Text` nào | `MapViewModelTest` + ảnh | FR-4 |
| S5 | Chỉ một basemap trong toàn app | Duyệt tay, ghi vào dev report | QA-SRM-34 |
| S6 | `MapScreen.kt` < 200 dòng | `wc -l` | LLM.md §5 |
| S7 | `NavigationScreen` vẫn hiện ghi công đúng như trước khi chuyển nhà | Chạy thật màn Dẫn đường | Hồi quy |

### Bằng chứng nghiệm thu — Step 8, emulator-5554 (`sdk_gphone16k_arm64`, 1344×2992)

Chụp ngày 26/08/2026. Ảnh gốc nằm ngoài git (scratchpad phiên làm việc); dưới đây là điều đọc được
trên màn, cùng cách dựng ra từng trạng thái.

| Trạng thái | Dựng bằng | Dải ghi công đọc được | Logo Google |
|---|---|---|---|
| **C — chưa có nguồn** | mở app, CHƯA bật theo dõi | *không vẽ gì*, bản đồ chạm thẳng thanh dưới | thấy đủ, trong khung |
| **A — tầng OSM** | khoá thật, có zone ⇒ `source=PROVIDER pointCount=12` ×2, vòng sau `source=CACHE` | `Tuyến đường: GraphHopper · OpenStreetMap contributors` | thấy đủ, trong khung |
| **B — tầng SYNTHETIC** | build với `GRAPHHOPPER_API_KEY=` rỗng + `pm clear` (xoá `files/routes`) ⇒ chỉ `source=SYNTHETIC` | `Đường thẳng ước tính — chưa có tuyến đường thật`, **không** có chuỗi "OpenStreetMap" | thấy đủ, trong khung |

Trạng thái B phải `pm clear` chứ không chỉ xoá khoá: cache trên máy còn giữ polyline GraphHopper
thật từ lần chạy trước, và một cache hit **vẫn phải** hiện credit OSM (dữ liệu OSM vẫn là dữ liệu
OSM). Không xoá cache thì đo nhầm sang trạng thái A.

**S5 / QA-SRM-34** — chỉ một basemap: 4 nơi gọi `GoogleMap(` (`FamilyTrackerMap`, `NavigationMap`,
`HistoryMap`, `ZoneCenterMap`), tất cả đều là Google Maps SDK. `grep -riE
"TileOverlay|MapLibre|osmdroid|WebView"` trên `ui/src/main` + `app/src/main` trả về đúng **một**
dòng, và đó là câu chú thích khẳng định luật trong `NavigationMap.kt:35`, không phải chỗ dùng.
`gradle/libs.versions.toml` chỉ khai báo `maps-compose` + `play-services-maps`.

**QA-SRM-33** — phạm vi polyline tuyến: `NavigationRouteColor` chỉ xuất hiện ở
`feature/navigation/component/NavigationPolyline.kt`. Màn Bản đồ không vẽ tuyến, đúng như Q9 chốt.

**S7** — màn Dẫn đường sau khi chuyển composable sang `designsystem/component/`: mở từ marker của
Lan → thẻ `Đang chỉ đường tới Lan · 937 m · 2 phút`, dải ghi công đọc đúng
`Tuyến đường: GraphHopper · OpenStreetMap contributors`, nằm **ngoài** khung bản đồ, logo Google
không bị che. Không hồi quy.

## Risk Assessment

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| **Dải ghi công vẽ đè lên khung bản đồ** ⇒ che credit Google ⇒ vi phạm cả ToS Google lẫn ODbL | **Cao** | Đổi `Box` → `Column` là thay đổi cấu trúc, không phải `align()`. S3 kiểm bằng ảnh, không bằng đọc code |
| Hiện credit OSM khi đang chạy tầng 3 (ghi sai nguồn, X5) | **Cao** | `isFallbackRoute` là `val` tính từ `kind`, không có đường nào để hai giá trị lệch nhau; S2 khoá bằng test |
| Chuyển nhà composable làm hỏng màn Dẫn đường | Trung bình | S7; `git mv` giữ nội dung nguyên vẹn, chỉ đổi package |
| Bản đồ `weight(1f)` làm cao độ khung đổi ⇒ camera canh lần đầu lệch | Thấp | `hasCenteredOnce` + `newLatLngZoom` không phụ thuộc kích thước khung |
| Dải ghi công chiếm chỗ vĩnh viễn dù đang ở tầng 3 | Thấp | Trạng thái ba của `RoutingAttribution` là **ẩn hẳn**; tầng 3 hiện một dòng ngắn — chấp nhận, đó là thông tin thật cho người dùng |

## Security Considerations

- Không dữ liệu mới nào rời thiết bị. Dải ghi công chỉ hiển thị chuỗi do nhà cung cấp trả về.
- **Không** hiển thị `engineId`, URL, hay bất kỳ mảnh nào của khoá API trên UI.
- Chuỗi attribution đến từ mạng ⇒ được vẽ bằng `Text` thường (không HTML, không `AnnotatedString`
  dựng từ dữ liệu ngoài) — không có bề mặt inject.

## Next Steps

- Phase 06 chạy UAT-07 dựa trên đúng 3 ảnh chụp của Step 8.
- Nếu Q9 sau này được chủ dự án đổi thành "có vẽ polyline tuyến trên màn Bản đồ", `MapState` đã có
  sẵn `routeSource`; chỉ thêm `points` và một `Polyline` màu `NavigationRouteColor`.
