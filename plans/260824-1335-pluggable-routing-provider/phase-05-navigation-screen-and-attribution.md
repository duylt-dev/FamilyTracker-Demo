# Phase 05 — Màn dẫn đường, polyline trên Google Map, và attribution

## Context Links

- [plan.md](plan.md) · [phase-04](phase-04-domain-reroute-and-arrival.md)
- **[Memo pháp lý](docs/legal-memo-decision.md) mục 6 — 5 điều kiện. Phase này là nơi 3 trong 5 điều kiện đó được thực hiện hoặc bị vi phạm.**
- [`docs/routing-and-map-attribution.md`](../../docs/routing-and-map-attribution.md) — hợp đồng tuân thủ ở runtime
- [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §2 (Contract), §3 (concurrency), §8 (stability)
- [`LLM.md`](../../LLM.md) §5 (giải phẫu feature), §7 (navigation), §12

## Overview

**Ưu tiên:** P1 · **Trạng thái:** 🟩 Code xong, chờ verify trên thiết bị (chụp màn hình) — xem
`reports/dev-phase-05-report.md`

Feature `navigation` đầy đủ theo MVI: Contract + ViewModel + Screen. Vào từ MapScreen bằng cách bấm
marker một thành viên → nút "Chỉ đường". Vẽ polyline lên **chính bản đồ Google đang có**, kèm
attribution, và giảm cấp tử tế khi mất mạng.

## Key Insights

**#1 — Attribution không phải việc làm sau. Nó là điều kiện để việc này hợp lệ.** Cơ sở duy nhất
khiến kết luận "vi phạm" của researcher-04 đổ là chính trang Policies của Maps SDK for Android:

> "When overlaying third-party geospatial data with Google Maps data as a basemap, you must not
> overlap or obscure the Google data attribution with third-party data attribution, and the
> attribution of third-party data must clearly be disassociated from Google's data attributions."

Google **viết quy tắc cho việc này**, và quy tắc đó là điều kiện. Vẽ polyline OSM mà không ghi
nguồn không phải là "thiếu sót nhỏ" — nó lấy đi lập luận đã dùng để cho phép cả tính năng. ODbL
phía OSM đòi đúng một nghĩa vụ đó, nên hai bên trùng khít: **một dòng credit, không che, tách bạch.**

**#2 — Credit đặt ở đâu.** Logo + attribution của Google nằm góc **dưới-trái** bản đồ và
**không được che, không được di chuyển**. Vậy credit của ta đi vào một dải riêng **bên dưới** khung
bản đồ (không chồng lên bản đồ), hoặc góc **trên** bản đồ với nền mờ. Cách chắc chắn nhất là dải
riêng bên dưới: không có cách nào nó chồng lên thứ nằm trong bản đồ.

Nội dung **đọc từ `Directions.attribution`**, không tự ghép chuỗi:
`"Tuyến đường: " + directions.attribution.joinToString(" · ")` →
`Tuyến đường: GraphHopper · OpenStreetMap contributors`.

Field đó do mapper điền từ chính response của nhà cung cấp (GraphHopper trả sẵn
`info.copyrights`, đã kiểm response thật) hoặc từ hằng số theo host với Valhalla/Stadia. **Không
dựng credit từ `engineId`** — `engineId` là để log và chẩn đoán; ghép hai việc lại thì một ngày ai
đó đổi tên trong log sẽ âm thầm đổi nội dung pháp lý trên màn hình.

**#3 — Chỉ hiện credit OSM khi thật sự đang hiển thị dữ liệu OSM.** Ở chế độ giảm cấp (đường thẳng
tự vẽ khi mất mạng), không có dữ liệu OSM nào trên màn hình → ghi `© OpenStreetMap` lúc đó là ghi
sai nguồn. Lúc đó dải credit đổi thành `Đường thẳng ước tính — chưa có tuyến đường thật`.

**#4 — Polyline phải phân biệt được với nội dung Google.** FAQ của Google: "visually distinguish
Google Maps Platform Content from other content". Dùng một màu riêng trong `designsystem/theme/Color.kt`
(không phải màu số ma thuật trong screen — `LLM.md` §12), khác rõ với màu tuyến giao thông và với
màu polyline lịch sử đang có ở `RoutePolyline`.

**#5 — TUYỆT ĐỐI không bao giờ có bản đồ nền thứ hai.** Đây là hành vi bị cấm rõ ràng và không mơ
hồ (memo mục 1). Không thêm tile overlay của bên khác, không `TileOverlay` từ OSM raster, không
màn hình nào đặt hai bản đồ cạnh nhau. Nếu ai đó đề xuất "thử MapLibre xem nhẹ hơn không" — đó là
một quyết định pháp lý khác, không phải một thử nghiệm kỹ thuật.

**#6 — `:ui` nhận `List<GeoPoint>`, map sang `LatLng` ngay tại composable.** Không decode gì ở `:ui`.
Không đưa `LatLng` xuống ViewModel (`LLM.md` §2: cấm import platform trong ViewModel — và ở đây là
lỗi biên dịch, không phải lỗi review).

**#7 — Effect phải `collect`, không `collectLatest`.** Luật đã có sẵn của repo. `collectLatest` bỏ
rơi effect khi effect kế tiếp tới sớm — với reroute mỗi 60s thì hiếm, nhưng "hiếm" nghĩa là bug chỉ
xuất hiện trước mặt khách.

## Requirements

**Chức năng**
1. `NavigationRoute(memberId: String)` — thêm vào `Routes.kt`, đăng ký trong `FamilyTrackerNavHost`.
2. MapScreen: chọn marker thành viên → nút "Chỉ đường" → `MapEffect.OpenNavigation(memberId)`.
3. NavigationScreen vẽ: bản đồ Google, marker self, marker target, polyline tuyến đường, thẻ
   khoảng cách + ETA, dải attribution, nút thoát.
4. Reroute tự động theo `ObserveNavigationUseCase`. "Đã tới" → thẻ đổi thành "Đã tới nơi".
5. Giảm cấp: mất mạng / hết quota / không có tuyến → **đường thẳng** + khoảng cách + nhãn "ước tính"
   + credit đổi theo Key Insight #3. Khoảng cách lấy từ `NavigationUpdate.distanceMeters` mà
   `ObserveNavigationUseCase` đã tính sẵn (phase-04 bước 6) — `:ui` **không** tự tính, vì
   `GeoDistance` là `internal` của `:domain` và mở nó ra chỉ để vẽ một con số là phá đúng ranh giới
   mà `LLM.md` §8.2 dựng lên.

**Phi chức năng**
6. `NavigationViewModel` extends `MviViewModel<NavigationState, NavigationIntent, NavigationEffect>`;
   `onIntent` là public method **duy nhất**.
7. Mọi coroutine qua `launchSafely`/`collectSafely` — `CoroutineSafetyArchitectureTest` sẽ bắt nếu không.
8. State/Intent/Effect ở `NavigationContract.kt`, không inline trong ViewModel.
9. Không import Compose hay Android trong ViewModel.
10. **Tracking tắt** — dù là lúc mở màn hình hay tắt giữa chừng: hiện banner "Bật theo dõi để lấy
    vị trí của bạn" + nút bật, **không** tự bật thay người dùng và **không** chặn màn hình. Banner
    lái bằng `state.isTracking` nên nó tự hiện/ẩn theo công tắc, không cần nhánh riêng cho hai
    tình huống. Tự bật một foreground service theo dõi vị trí mà không hỏi là một hành vi mà người
    dùng có quyền không muốn. Tuyến đường đã vẽ **giữ nguyên** khi tracking tắt — nó vẫn là thông
    tin đúng tại thời điểm cuối, chỉ ngừng cập nhật.

## Architecture

```
:ui/feature/navigation/
├── NavigationContract.kt      State + Intent + Effect
├── NavigationViewModel.kt     onIntent duy nhất; collectSafely(ObserveNavigationUseCase)
├── NavigationScreen.kt        Route composable + Screen composable
└── component/
    ├── NavigationMap.kt       GoogleMap + marker + polyline
    ├── NavigationPolyline.kt  List<GeoPoint> -> List<LatLng> -> Polyline
    ├── NavigationSummaryCard.kt  khoảng cách, ETA, trạng thái
    └── RoutingAttribution.kt  dải credit — BẮT BUỘC, xem Key Insight #1
```

```kotlin
data class NavigationState(
    val targetMember: Member? = null,
    val selfPoint: GeoPoint? = null,
    val targetPoint: GeoPoint? = null,
    val directions: Directions? = null,
    val isFallbackStraightLine: Boolean = false,
    val hasArrived: Boolean = false,
    val isTracking: Boolean = false,
    val error: AppError? = null,
) : UiState {
    /** Derive, don't duplicate (MVI doc §2) — hai field tách rời sẽ lệch nhau, một field suy ra thì không. */
    val polyline: List<GeoPoint> get() = directions?.points
        ?: listOfNotNull(selfPoint, targetPoint)   // fallback đường thẳng
    val attributionLines: List<String> get() = directions?.attribution.orEmpty()
}
```

**Vì sao `isFallbackStraightLine` là field lưu chứ không suy ra:** nó là *lý do* chứ không phải
*hình dạng*. `directions == null` có thể vì chưa gọi xong, vì lỗi, hoặc vì không có đường — dải
credit và nhãn "ước tính" chỉ đúng ở nhánh cuối.

**Lệch khỏi snippet trên khi thực thi — ghi lại thay vì âm thầm đổi (đã xác nhận với người giao việc
trước khi code, không phải suy đoán):**

1. Thêm `distanceMeters: Double?` và `isDistanceEstimated: Boolean` vào `NavigationState`. Snippet
   gốc không có hai field này, nhưng `NavigationUpdate` thật (phase-04, đã commit) luôn mang sẵn
   khoảng cách đã tính — `:ui` không tự tính lại được (`GeoDistance` `internal` của `:domain`, LLM.md
   §8.2). Không lưu lại thì `NavigationSummaryCard` không có cách hợp lệ nào để vẽ khoảng cách ở
   nhánh giảm cấp.
2. Thêm `hasCenteredOnce: Boolean` + `NavigationIntent.CameraCentered`. Implementation Step 6 đòi
   "cùng luật `hasCenteredOnce` mà `MapState` đang dùng" nhưng snippet quên field đó — thêm cho khớp
   với chính câu chữ của bước 6.
3. `isFallbackStraightLine` — công thức dính chính xác:
   `directions == null && (lastError != null || isFallbackStraightLine cũ)`, không phải
   `directions == null && lastError != null` — xem `NavigationViewModel.applyUpdate` KDoc.
4. `NavigationEffect.StartTracking` được gửi kèm việc `NavigationViewModel` TỰ gọi thẳng
   `trackingRepository.setTracking(true)` (cùng mẫu `MapViewModel.onToggleTracking`, không cần
   Effect làm cầu nối vì không có gì platform-specific ở bước này) — Effect chỉ còn vai trò xác nhận
   một-lần cho Route hiện snackbar, không phải cơ chế bật tracking. Nếu bỏ hẳn Effect này thì Todo
   List/Success Criteria của phase vẫn thoả, nhưng giữ lại vì spec liệt kê rõ ràng và nó có công dụng
   thật (không phải effect chết).
5. `factoryOf(::ObserveNavigationUseCase)` KHÔNG dùng được — xem mục "Sửa" bên dưới,
   `data/di/DataModule.kt`.

## Related Code Files

**Tạo mới**
- `ui/src/main/java/.../ui/feature/navigation/NavigationContract.kt`
- `ui/src/main/java/.../ui/feature/navigation/NavigationViewModel.kt`
- `ui/src/main/java/.../ui/feature/navigation/NavigationScreen.kt`
- `ui/src/main/java/.../ui/feature/navigation/component/NavigationMap.kt`
- `ui/src/main/java/.../ui/feature/navigation/component/NavigationPolyline.kt`
- `ui/src/main/java/.../ui/feature/navigation/component/NavigationSummaryCard.kt`
- `ui/src/main/java/.../ui/feature/navigation/component/RoutingAttribution.kt`
- `ui/src/test/java/.../ui/feature/navigation/NavigationViewModelTest.kt`

**Sửa**
- `ui/src/main/java/.../ui/navigation/Routes.kt` — `NavigationRoute(memberId)`
- `ui/src/main/java/.../ui/navigation/FamilyTrackerNavHost.kt` — composable + arg
- `ui/src/main/java/.../ui/feature/map/MapContract.kt` — `NavigateToMemberRequested`, `OpenNavigation`
- `ui/src/main/java/.../ui/feature/map/MapViewModel.kt` + `MapScreen.kt` — nút "Chỉ đường"
- `ui/src/main/java/.../ui/di/UiModule.kt` — `viewModelOf(::NavigationViewModel)`
- `ui/src/main/java/.../ui/designsystem/theme/Color.kt` — màu polyline dẫn đường
- `ui/src/main/java/.../ui/designsystem/theme/Dimens.kt` — **doc drift, thêm khi thực thi:** bề dày
  polyline dẫn đường (`NavigationPolylineWidth`) — file này chạm `designsystem/theme/` cùng
  `Color.kt` nên đúng nơi trả nợ theo quy ước đã ghi trong chính `Color.kt`'s KDoc.
- `data/src/main/java/.../data/di/DataModule.kt` — **doc drift, thêm khi thực thi, không có trong
  bản gốc của phase file này.** `ObserveNavigationUseCase` sống ở `:domain/usecase/`, và theo đúng
  quy ước LLM.md §6 (use case đăng ký cạnh `factoryOf(::PurgeOldHistoryUseCase)`, không phải
  `UiModule`), binding của nó PHẢI vào `DataModule.kt`, không phải `UiModule.kt` như phase file này
  ngầm định (`UiModule.kt` chỉ liệt kê `viewModelOf(::NavigationViewModel)`, không liệt kê use case).
  Viết tay `factory { ObserveNavigationUseCase(locationSource = get(named("fused")), memberRepository
  = get(), routingProvider = get()) }` — KHÔNG `factoryOf(::ObserveNavigationUseCase)`, vì constructor
  có tham số thứ 4 `nowMs: () -> Long` (có default Kotlin); `factoryOf` phản chiếu toàn bộ constructor
  và đòi một binding cho `Function0<Long>` không tồn tại, làm đỏ `KoinModulesTest`. `locationSource`
  bắt buộc `named("fused")` — `DataModule` không có binding `LocationSource` KHÔNG qualifier, và vị
  trí thật của máy người dùng mới là ý nghĩa của "chỉ đường từ tôi tới X" (không phải `"simulated"`).
  Đã chạy `./gradlew build` xanh (gồm `:app:test` → `KoinModulesTest`) sau khi thêm — xem dev report.
- `ui/src/main/res/values/strings.xml` — mọi chuỗi mới (`:ui` không thấy `R` của `:app`)

## Implementation Steps

1. **Route.** `@Serializable data class NavigationRoute(val memberId: String)` + `ARG_MEMBER_ID`.
   ViewModel đọc bằng `savedStateHandle.get<String>(ARG_MEMBER_ID)`, **không** `toRoute<T>()` —
   `toRoute()` chạm `android.os.Bundle` và ném ngay trên JVM unit test (đã đo thật, xem KDoc
   `ZoneEditorRoute` trong `Routes.kt`).
2. **Contract.** State như trên. Intent: `Retry`, `StopNavigation`, `EnableTrackingRequested`.
   Effect: `NavigateBack`, `ShowError(AppError)`, `StartTracking`.
3. **ViewModel.** `collectSafely(observeNavigationUseCase(memberId))` cập nhật state. Lỗi từ use case
   vào `state.error` **và** không xoá `directions` đang có.
4. **`NavigationPolyline`.** `remember(points) { points.map { LatLng(it.latitude, it.longitude) } }`.
   Vẽ khi `size >= 2`. Màu từ theme. `PolyUtil.simplify` **không cần** ở đây — tuyến đường từ
   provider đã tối giản sẵn (khác polyline lịch sử với hàng nghìn điểm GPS thô).
5. **`RoutingAttribution`.** Dải bên dưới bản đồ. Ba trạng thái:
   - có tuyến → `Tuyến đường: {state.attributionLines.joinToString(" · ")}`
   - fallback đường thẳng → `Đường thẳng ước tính — chưa có tuyến đường thật` (**không** credit OSM:
     lúc đó không có dữ liệu OSM nào trên màn hình, Key Insight #3)
   - chưa có gì → ẩn

   Text `bodySmall`, contrast đủ đọc, **không** đặt trong khung bản đồ. Composable này không được
   nhận `engineId` làm tham số — chỉ nhận `List<String>` đã dựng sẵn, để không có chỗ nào trong `:ui`
   có thể tự chế ra một câu credit khác.
6. **`NavigationMap`.** Camera bao cả hai điểm (`LatLngBounds` + padding) lần đầu, sau đó không tự
   kéo nữa — cùng luật `hasCenteredOnce` mà `MapState` đang dùng, vì cùng một lý do: kéo camera về
   giữa lúc người dùng đang xem chỗ khác là hành vi khó chịu.
7. **Đường vào từ MapScreen.** `MapIntent.NavigateToMemberRequested(memberId)` →
   `MapEffect.OpenNavigation(memberId)`. Nút hiện khi `selectedMemberId != null` và
   member đó **không phải** self (chỉ đường tới chính mình là vô nghĩa).
8. **Chuỗi.** Toàn bộ vào `ui/src/main/res/values/strings.xml`. Không literal trong composable.
9. **Test ViewModel.** Fake use case + Turbine: có tuyến → state có `directions`; lỗi → `error` set
   mà `directions` còn nguyên; `StopNavigation` → `NavigateBack`; `hasArrived` → card đổi.
   **Cộng một test khẳng định `attributionLines` KHÔNG RỖNG bất cứ khi nào `directions` khác null,
   và luôn chứa `"OpenStreetMap contributors"`** — ghim điều kiện pháp lý bằng test, không bằng lời
   hứa. Cộng một test nghịch: ở trạng thái fallback, `attributionLines` **rỗng** (ghi credit OSM khi
   không hiển thị dữ liệu OSM cũng là ghi sai nguồn).
10. `./gradlew :ui:test` + chạy thật trên thiết bị.

## Todo List

- [x] `NavigationRoute` + NavHost + arg đọc bằng `get<String>(KEY)`
- [x] `NavigationContract.kt`
- [x] `NavigationViewModel.kt` (`onIntent` duy nhất, `collectSafely`)
- [x] `NavigationScreen.kt` + `NavigationMap` + `NavigationPolyline` + `NavigationSummaryCard`
- [x] **`RoutingAttribution.kt` — 3 trạng thái, không che attribution Google**
- [x] Màu polyline vào `Color.kt`
- [x] Đường vào từ MapScreen (`MapIntent` + `MapEffect` + nút)
- [x] Giảm cấp đường thẳng + nhãn "ước tính" + credit đổi theo
- [x] Banner khi tracking tắt, không tự bật
- [x] Chuỗi vào `ui/res/values/strings.xml`
- [x] `viewModelOf(::NavigationViewModel)` vào `UiModule`
- [x] `NavigationViewModelTest` + 2 test ghim attribution (có tuyến → có OSM; fallback → rỗng)
- [ ] Chụp màn hình có tuyến đường + attribution, lưu vào `reports/` — **để lại cho người verify
      trên emulator/thiết bị thật, không làm trong phase này (theo yêu cầu khi giao việc)**

## Success Criteria

1. Bấm marker Minh → "Chỉ đường" → thấy tuyến đường thật trên bản đồ Google trong < 3 giây.
2. **Attribution `© OpenStreetMap contributors` hiện rõ, không chồng lên attribution/logo Google,
   nằm ngoài khung bản đồ.** Kiểm bằng ảnh chụp màn hình, không bằng đọc code.
3. Credit đổi theo `ROUTING_ENGINE`: GraphHopper → `GraphHopper · OpenStreetMap contributors`;
   Valhalla/FOSSGIS → `Valhalla · OpenStreetMap contributors`; Valhalla/Stadia →
   `Stadia Maps · OpenStreetMap contributors`. Kiểm bằng mắt trên máy, không bằng đọc code.
4. Tắt mạng → đường thẳng + "ước tính" + credit đổi, app không crash, tuyến cũ không biến mất
   ngay lập tức.
5. `:ui:test` xanh, gồm `CoroutineSafetyArchitectureTest` và test ghim attribution.
6. `grep -rn "import androidx.compose\|import android\." ui/src/main/.../feature/navigation/NavigationViewModel.kt` → rỗng.
7. Không có bản đồ nền thứ hai ở bất kỳ đâu trong app.

## Risk Assessment

| Rủi ro | Xác suất | Giảm thiểu |
|---|---|---|
| **Ship thiếu attribution** | Trung bình | `Directions.attribution` là field BẮT BUỘC nên không dựng được `Directions` mà quên credit; cộng 2 test ghim `attributionLines` + ảnh chụp trong Success Criteria. Đây là rủi ro nghiêm trọng nhất của cả plan: nó biến một việc hợp lệ thành một việc không hợp lệ |
| Dải credit vô tình che logo Google | Trung bình | Đặt **ngoài** khung bản đồ, không dùng overlay có `align(BottomStart)` |
| Reroute làm polyline nhấp nháy | Trung bình | Chỉ thay `directions` khi provider trả `Success`; giữ tuyến cũ trong lúc gọi |
| Camera tự kéo khi người dùng đang xem chỗ khác | Cao nếu không xử lý | `hasCenteredOnce`, đúng mẫu `MapState` |
| Màn hình mở lúc tracking tắt → trống trơn không giải thích | Cao nếu không xử lý | Yêu cầu #10 |
| `Directions` không stable với Compose → recompose thừa | Thấp | `data class` bất biến + `List` — kiểm bằng `LLM.md` §8 nếu thấy giật |

## Security Considerations

- Không hiện toạ độ thô của thành viên ra UI dạng số. Marker và khoảng cách là đủ.
- Không ghi tuyến đường vào Room. Tuyến đường là dữ liệu tạm; lưu lại là tạo ra một kho lịch sử
  "ai đã tìm đường tới ai" mà không tính năng nào cần và không màn hình nào xoá được.
- Lỗi provider hiện dạng thông điệp tiếng Việt của app, không phải `message` thô của server.
- Wake lock / giữ màn hình sáng: **không thêm** trong phase này. Muốn thêm thì phải là một quyết
  định riêng có đo pin.

## Next Steps

Phase 06 chạy gate, cập nhật `LLM.md` + `docs/`, và ghim ngày kiểm tra điều khoản.
