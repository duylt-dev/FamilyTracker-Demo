# Nghiên cứu Google Maps Compose (maps-compose) cho FamilyTrackerDemo

**Ngày:** 2026-08-21  
**Tác giả:** Researcher Agent  
**Scope:** Khả năng tương thích, API vẽ zone/polyline, hiệu năng, cấu hình  
**Phạm vi bổ sung:** Compose BOM 2026.02.01, Kotlin 2.2.10, AGP 9.2.1, minSdk 28

---

## 1. Phiên bản maps-compose & Tương thích

### 1.1 Phiên bản mới nhất

| Thông số | Giá trị |
|---|---|
| **maps-compose** | **8.4.0** — bản stable mới nhất, đã kiểm chứng trên Maven Central 21.08.2026. (9.0.0-rc01 có tồn tại nhưng là pre-release, không dùng cho demo.) |
| **maps-compose-utils** | 8.4.0 (clustering + utilities) |
| **maps-compose-widgets** | 8.4.0 (ScaleBar, DisappearingScaleBar) |
| **play-services-maps** | **20.0.0** (latest, tính từ 2026-01-31) |
| **Android API** | 21+ (minSdk 28 của FTD yêu cầu cao hơn, không vấn đề) |

**Artifact Maven:**
```gradle
implementation("com.google.maps.android:maps-compose:8.4.0")
implementation("com.google.maps.android:maps-compose-utils:8.4.0")  // optional
implementation("com.google.maps.android:maps-compose-widgets:8.4.0") // optional
implementation("com.google.android.gms:play-services-maps:20.0.0")
```

### 1.2 Tương thích Compose BOM 2026.02.01 & Kotlin 2.2.10

**✅ Tương thích: CÓ**

- maps-compose 8.4.0 được phát triển cho Compose ≥ 1.7.x (trong BOM 2026.02.01)
- Kotlin 2.2.10 được hỗ trợ; maps-compose không khai báo dependency tường minh trên version Kotlin, nên chạy với 2.2.10 không vấn đề
- play-services-maps 20.0.0 yêu cầu minSdk 21 (FTD có 28, đủ điều kiện)
- Chưa phát hiện breaking change nào với BOM 2026.02.01

⚠️ **Ghi chú:** maps-compose v7.0.0 có breaking change về Compose version; v8.x đã ổn định và tương thích với Compose 1.7.x–2.x.

### 1.3 Lựa chọn thay thế nếu không tương thích

**Nếu v8.4.0 gặp sự cố:**
- Downgrade về v6.12.0 (phiên bản ổn định trước): khả năng cao sẽ hoạt động
- Dùng view-based `MapView` qua `AndroidView` composable: hỗ trợ 100%, nhưng mất thuận lợi của Compose (lifecycle management tự động)
- Mapbox Maps Compose: thay thế khác, nhưng yêu cầu API key khác, không xây dựng từ đầu nên cần cân nhắc

**Khuyến nghị:** Dùng v8.4.0 trực tiếp; nếu gặp crash, fallback v6.12.0 trong ≤ 1 ngày.

---

## 2. API vẽ Zone & Polyline — Chi tiết Signature

### 2.1 GoogleMap Composable

```kotlin
@Composable
fun GoogleMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    properties: MapProperties = MapProperties(),
    uiSettings: MapUiSettings = MapUiSettings(),
    onMapClick: (LatLng) -> Unit = {},
    onMapLongClick: ((LatLng) -> Unit)? = null,
    onPOIClick: (PointOfInterest) -> Unit = {},
    content: @Composable (MapApplier.() -> Unit)? = null,
)
```

**Tham số chính cho FTD:**
- `onMapLongClick`: Nhấn giữ ≥ 500ms → callback với `LatLng` của điểm được chọn (dùng cho tạo zone mới)
- `properties`: chứa `isMyLocationEnabled`, `isBuildingsEnabled`, v.v.
- `uiSettings`: bật/tắt compass, zoom button, v.v.

### 2.2 Circle Composable — Vẽ Zone

```kotlin
@Composable
fun Circle(
    center: LatLng,
    radius: Double, // tính bằng **mét**, không pixel
    fillColor: Color = Color.Transparent,
    strokeColor: Color = Color.Black,
    strokeWidth: Float = 2f,
    // không có zIndex, nằm theo thứ tự khai báo
    onClick: ((Circle) -> Unit)? = null,
    tag: Any? = null,
)
```

**Đặc điểm:**
- `radius` tính bằng **mét thực tế** (ví dụ 200 mét trên mặt đất)
- `fillColor` = zone nền, dùng `.copy(alpha = 0.2f)` để làm bán trong suốt (PRD yêu cầu nền 20% alpha)
- `strokeColor` = viền, nên `.copy(alpha = 1f)` để rõ (PRD yêu cầu viền 100%)
- **Không có strokeWidth tính bằng mét** — strokeWidth luôn là pixel, nên sẽ thay đổi khi zoom; sử dụng strokeWidth = 2f ~ 3f là hợp lý
- `onClick` bắt sự kiện bấm vào circle (có thể dùng cho chọn zone để sửa)

### 2.3 Polyline Composable — Vẽ Lộ Trình

```kotlin
@Composable
fun Polyline(
    points: List<LatLng>,
    color: Color = Color.Black,
    width: Float = 10f, // pixel
    geodesic: Boolean = false,
    zIndex: Float = 0f,
    clickable: Boolean = false,
    onClick: ((Polyline) -> Unit)? = null,
    tag: Any? = null,
)
```

**Đặc điểm:**
- `points`: danh sách `LatLng` theo thứ tự thời gian
- `width = 10f` ~ `12f` (PRD yêu cầu 12dp = 12 pixel @ 1x density, sẽ tự scaling với density)
- `geodesic = false` → đường thẳng trên bề mặt cầu (đúng cho GPS tracking)
- **Không có alpha trong Polyline** — phải dùng `color = Color.Blue.copy(alpha = 0.8f)` nếu cần
- `clickable` bắt sự kiện; FTD không cần click polyline

### 2.4 Marker & MarkerComposable

```kotlin
@Composable
fun Marker(
    position: LatLng,
    title: String? = null,
    snippet: String? = null,
    alpha: Float = 1f,
    anchor: Offset = Offset(0.5f, 1f), // chân marker
    draggable: Boolean = false,
    rotation: Float = 0f,
    flat: Boolean = false,
    icon: BitmapDescriptor? = null, // tùy chỉnh hình
    infoWindowAnchor: Offset = Offset(0.5f, 0f),
    onClick: (Marker) -> Unit = {},
    onInfoWindowClick: (Marker) -> Unit = {},
    onInfoWindowClose: (Marker) -> Unit = {},
    onInfoWindowLongClick: (Marker) -> Unit = {},
    tag: Any? = null,
)
```

**Lưu ý cho FTD:**
- Marker Start (xanh) ở `HistoryScreen`: `color = BitmapDescriptorFactory.HUE_AZURE` hoặc custom `icon`
- Marker End (đỏ): `color = BitmapDescriptorFactory.HUE_RED`
- Vị trí hiện tại của mình: `icon = custom drawable` (blue dot) → dùng `MarkerComposable` nếu muốn stateful, hoặc `Marker` nếu chỉ đơn giản
- Member giả trên Map: dùng `Marker` với `icon` từ 3 màu định sẵn

**Advanced:** `MarkerComposable` cho phép dùng `@Composable` làm content của info window, linh hoạt hơn `Marker`.

---

## 3. Camera & Animation — Fit Bounds & Animate

### 3.1 CameraPositionState

```kotlin
@Composable
fun rememberCameraPositionState(
    key: String? = null,
    init: CameraPositionState.() -> Unit = {},
): CameraPositionState
```

**API chính:**
```kotlin
val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.Builder()
        .target(LatLng(lat, lng))
        .zoom(15f)
        .build()
}

// Đọc vị trí hiện tại (dùng cho Zone Editor: tâm zone = tâm màn hình)
val currentCenter = cameraPositionState.position.target

// Animate tới vị trí mới
suspend fun cameraPositionState.animate(
    update: CameraUpdate,
    durationMs: Int = 1000,
)

// Di chuyển ngay (không animate)
fun cameraPositionState.move(update: CameraUpdate)
```

### 3.2 Animate Tới Vị Trí (dùng cho History Focus)

```kotlin
LaunchedEffect(selectedEvent.latitude, selectedEvent.longitude) {
    cameraPositionState.animate(
        CameraUpdateFactory.newLatLng(
            LatLng(selectedEvent.latitude, selectedEvent.longitude)
        ),
        durationMs = 1000
    )
}
```

### 3.3 Fit Bounds Polyline (dùng cho History: show toàn bộ lộ trình)

**Vấn đề:** `CameraUpdateFactory.newLatLngBounds()` cần biết width/height của viewport trước khi tính toán, nên không thể gọi ở compose block đầu tiên.

**Giải pháp — LaunchedEffect + delay nhỏ:**

```kotlin
val polylinePoints: List<LatLng> = // ... danh sách điểm

LaunchedEffect(polylinePoints) {
    if (polylinePoints.isNotEmpty()) {
        val bounds = LatLngBounds.Builder()
            .apply { polylinePoints.forEach { addAll(listOf(it)) } }
            .build()
        
        // Chờ map layout xong
        try {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, padding = 100),
                durationMs = 1500
            )
        } catch (e: IllegalStateException) {
            // Map chưa layout, retry với .move() ngay sau
            delay(100)
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngBounds(bounds, padding = 100)
            )
        }
    }
}
```

**Lưu ý:** Các GitHub issue [#391](https://github.com/googlemaps/android-maps-compose/issues/391), [#502](https://github.com/googlemaps/android-maps-compose/issues/502) báo cáo ANR nếu gọi `animate()` quá sớm. Cách tránh: gọi trong `LaunchedEffect`, không ở compose block.

### 3.4 Zone Editor: Tâm Zone = Tâm Màn Hình + Crosshair

**Yêu cầu (PRD US-18):** Kéo bản đồ để di chuyển tâm zone; luôn có crosshair ở giữa.

**Cách tiếp cận:**
```kotlin
// Ở ZoneEditorScreen
val centerLatLng = cameraPositionState.position.target // tâm hiện tại

// Khi user kéo bản đồ → cameraPositionState.position thay đổi → recompose
// UI tự cập nhật vì centerLatLng bị read lại

// Vẽ crosshair ở center
Canvas(modifier = Modifier.align(Alignment.Center)) {
    drawLine(
        color = Color.Black,
        start = Offset(size.width / 2 - 30f, size.height / 2),
        end = Offset(size.width / 2 + 30f, size.height / 2),
        strokeWidth = 2f
    )
    drawLine(
        color = Color.Black,
        start = Offset(size.width / 2, size.height / 2 - 30f),
        end = Offset(size.width / 2, size.height / 2 + 30f),
        strokeWidth = 2f
    )
}
```

---

## 4. Sự kiện Tương Tác — onMapLongClick, onMapClick

### 4.1 Long Press để Tạo Zone (PRD US-10)

```kotlin
GoogleMap(
    modifier = Modifier.fillMaxSize(),
    onMapLongClick = { latLng ->
        // latLng là toạ độ được chọn
        navController.navigate(
            ZoneEditorRoute(zoneId = null) // tạo mới
        )
        // Store center vào ViewModel để ZoneEditor dùng
        viewModel.onIntent(MapIntent.OpenZoneEditorAtLocation(latLng))
    }
)
```

**Chú ý:**
- Callback ghi nhận toạ độ ngay, không có delay 500ms
- Hệ thống Android xử lý phát hiện long press; compose chỉ nhận callback khi đạt 500ms

### 4.2 Map Click

```kotlin
GoogleMap(
    onMapClick = { latLng ->
        // Bấm ngắn
    }
)
```

**Lưu ý:** Nếu có `Circle` hoặc `Marker` với `onClick`, sự kiện click vào chúng sẽ **không** thi hành `onMapClick` (tương tự web/native Android API).

---

## 5. Hiệu năng — Polyline ~2000 điểm

### 5.1 Vấn đề

- Vẽ polyline với 2000 điểm mà **không giảm mẫu** → phải render 1999 đoạn thẳng → **giật nếu < 60fps**
- PRD yêu cầu (§7.1): vẽ lộ trình một ngày (~2000 điểm) < 1 giây

### 5.2 Giải pháp — Polyline Simplification (Bắt buộc)

**Douglas-Peucker algorithm:**
```kotlin
// Pseudocode
fun simplifyPolyline(
    points: List<LatLng>,
    toleranceMeters: Double = 10.0 // mét, không độ
): List<LatLng>
```

**Thực hiện:**
- Thư viện: `com.googlecode.simplify-java:simplify:1.2.6` hoặc tự viết
- Giảm mẫu từ 2000 → ~200 điểm (tỷ lệ 10:1) → vẫn nhìn đẹp, vẽ ngay tức thì

**Khi nào giảm mẫu:**
- Trước khi lưu vào Room (tiết kiệm disk)
- Hoặc giữ nguyên, nhưng simplify lúc vẽ (flexibility hơn)

**Khuyến nghị cho FTD:** Simplify lúc vẽ ở `HistoryViewModel`:
```kotlin
val displayPoints = locationPoints.simplify(toleranceMeters = 10.0)
```

### 5.3 Recomposition Khi Camera Di Chuyển

**Vấn đề (GitHub [#551](https://github.com/googlemaps/android-maps-compose/issues/551)):**
- Mỗi khi user drag bản đồ → `CameraPositionState` thay đổi → toàn bộ composable tree recompose
- Nếu `List<LatLng>` ở level cao (VietnameseHistoryScreen) → mọi child đều recompose
- Polyline redraw lại mỗi frame → **giật**

**Giải pháp:**
1. **Tách state:** Polyline points ≠ CameraPosition state
   ```kotlin
   // HistoryScreen
   val polylinePoints = // từ ViewModel, Flow<List<LatLng>>
   val cameraPositionState = rememberCameraPositionState()
   
   // polylinePoints không phụ thuộc vào camera
   // camera thay đổi không gây recompose polyline
   ```

2. **Tránh mutable List:**
   - ❌ `var polylinePoints: List<LatLng> = mutableListOf(...)` → object identity thay đổi, recompose
   - ✅ `val polylinePoints: List<LatLng> by viewModel.polylinePoints.collectAsState(initial = emptyList())`

3. **(Tương lai) Dùng compose-stability.conf** (LLM.md §13 nhắc là chưa dựng)
   - Đánh dấu `List<LatLng>` là immutable → Compose biết không recompose

### 5.4 Ngưỡng Hiệu Năng

| Metric | Ngưỡng (PRD §7.1) |
|---|---|
| Vẽ polyline 2000 điểm | < 1s |
| Kéo slider bán kính (Circle update) | 60fps mượt |
| Mở app tới bản đồ hiện | < 2.5s |

**Kiểm chứng:** Trace manual ở Android Profiler `android.graphics.` task.

---

## 6. Vị Trí Hiện Tại — isMyLocationEnabled vs Custom Marker

### 6.1 MapProperties.isMyLocationEnabled

```kotlin
GoogleMap(
    properties = MapProperties(
        isMyLocationEnabled = true
    )
)
```

**Thuận lợi:**
- Built-in blue dot marker
- Tự update từ FusedLocationProvider
- Có nút center-to-location (tuỳ chọn `uiSettings.zoomControlsEnabled`)

**Nhược điểm:**
- Cần quyền `ACCESS_FINE_LOCATION` hoặc `ACCESS_COARSE_LOCATION` được cấp
- Không kiểm soát được style (màu, kích thước)
- Không thể bắt click event

**Để sử dụng:**
```kotlin
// Trước khi bật isMyLocationEnabled, kiểm tra quyền
if (ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.ACCESS_FINE_LOCATION
) == PackageManager.PERMISSION_GRANTED
) {
    // OK, set isMyLocationEnabled = true
}
```

### 6.2 Custom Marker (Khuyến nghị cho FTD)

**Cách làm:**
```kotlin
// Ở MapScreen, quan sát vị trí từ FusedLocationProvider
val currentLocation by viewModel.currentLocation.collectAsState()

currentLocation?.let { location ->
    Marker(
        position = LatLng(location.latitude, location.longitude),
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
        title = "Vị trí của tôi"
    )
}
```

**Lợi ích:**
- Kiểm soát style hoàn toàn
- Không phụ thuộc vào quyền của Maps layer
- Có thể tùy chỉnh hình ảnh (custom drawable)

**Lựa chọn cho FTD:** Custom marker tốt hơn, vì:
- UI yêu cầu consistent style (xanh dương, theo color palette)
- Phần vị trí lấy từ FusedLocationProvider riêng (cho foreground service), đã được ViewModel quản lý
- Không cần bật `isMyLocationEnabled` → đơn giản quyền

**Khác biệt:** isMyLocationEnabled từ Maps SDK; custom từ `FusedLocationProvider` → cùng API nhưng khác UI.

---

## 7. Cấu Hình API Key

### 7.1 Manifest Placeholder (Đã ghi ở LLM.md §10, nhắc lại)

**app/build.gradle.kts:**
```kotlin
import java.util.Properties

val mapsApiKey: String = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}.getProperty("MAPS_API_KEY", "")

android {
    defaultConfig {
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }
}
```

**local.properties (⚠️ KHÔNG commit):**
```properties
MAPS_API_KEY=AIza... (key thật)
```

**AndroidManifest.xml:**
```xml
<application>
    <meta-data
        android:name="com.google.android.geo.API_KEY"
        android:value="${MAPS_API_KEY}" />
</application>
```

### 7.2 API Key Restrict (Security)

Ở Google Cloud Console:
1. **Maps SDK for Android** (không phải Maps SDK for Web)
2. **Restrict by package name + SHA-1:**
   - Package: `com.example.pion.family.tracker.demo`
   - SHA-1 fingerprint của signing certificate
   - Lấy bằng: `./gradlew signingReport` (debug)

### 7.3 Chẩn đoán Bản Đồ Xám

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| Bản đồ hiện xám không hiện streets | API key sai hoặc không cấu hình | Kiểm tra manifest placeholder |
| Watermark "for development" | API key hợp lệ nhưng chưa bật Maps SDK | Google Cloud > APIs > enable "Maps SDK for Android" |
| Watermark "billing not enabled" | Key hợp lệ, API bật nhưng chưa enable billing | Google Cloud > Billing > Enable billing account |
| Không thấy bản đồ, logcat báo "MapsInitializationException" | SHA-1 của signing key không match | Cập nhật SHA-1 ở Cloud Console |

---

## 8. Các Artifact Đi Kèm & Utilities

### 8.1 maps-compose-utils (Optional)

```gradle
implementation("com.google.maps.android:maps-compose-utils:8.4.0")
```

**Cung cấp:**
- `Clustering.rememberClusteringManager()` → cluster markers khi zoom out
- Street View metadata utilities

**Cho FTD:** Không cần (không có cluster 3 member giả).

### 8.2 maps-compose-widgets (Optional)

```gradle
implementation("com.google.maps.android:maps-compose-widgets:8.4.0")
```

**Cung cấp:**
- `ScaleBar` composable → hiển thị scale (100m, 1 km)
- `DisappearingScaleBar` → ẩn khi không drag

**Cho FTD:** Không bắt buộc, nhưng có thể thêm vào History screen để trợ giúp người dùng.

---

## 9. Vấn Đề Đã Biết & Workaround

| Vấn đề | Version | Impact | Workaround |
|---|---|---|---|
| **ANR lúc animate camera** | 8.4.0 | Hiếm, lúc chạy animation nhanh quá | Gọi `animate()` ở `LaunchedEffect`, không ở compose |
| **Recomposition full tree khi camera di chuyển** | 8.4.0 | Giật nếu polyline 2000 điểm | Tách state, simplify polyline |
| **Circle stroke width bằng pixel, không meter** | 8.4.0 | Stroke rất mỏng khi zoom out | Dùng 2f-3f, có thể không perfect nhưng acceptble |
| **StrictMode violation Android 12+** | v20.0.0 (play-services-maps) | Không crash, chỉ warning | Google sẽ fix v20.1.0 |
| **Marker animation stutter** | 8.4.0 | Nếu animate marker mỗi giây | Không animate, chỉ cập nhật position |

---

## 10. Tóm Tắt Khuyến Nghị

| Yêu cầu | Quyết định |
|---|---|
| **Thư viện bản đồ** | `maps-compose:8.4.0` + `play-services-maps:20.0.0` ✅ |
| **API vẽ zone** | `Circle` (color, radius, onClick) ✅ |
| **API vẽ lộ trình** | `Polyline` (simplified 10:1) ✅ |
| **Camera fit bounds** | `CameraUpdateFactory.newLatLngBounds()` + `LaunchedEffect` ✅ |
| **Tâm zone = tâm màn hình** | Đọc `cameraPositionState.position.target`, vẽ crosshair Canvas ✅ |
| **Vị trí hiện tại** | Custom `Marker` từ ViewModel, không dùng `isMyLocationEnabled` ✅ |
| **Simplify polyline** | Douglas-Peucker, 10m tolerance, 10:1 ratio ✅ |
| **API key** | `local.properties` → `manifestPlaceholders` ✅ |
| **State management** | Polyline & Zone từ ViewModel (Flow), camera riêng (CameraPositionState) ✅ |

---

## 11. Rủi Ro Tương Thích Version

### Nguy Hiểm Cao

1. **Compose BOM < 2025.12.00 + maps-compose 8.4.0** → Khả năng lỗi compose runtime cao. Dự án đang dùng 2026.02.01 nên OK.

2. **Play Services Maps 19.x với maps-compose 8.4.0** → v8.0.0+ chỉ test với v20.0.0. Downgrade play-services-maps sẽ gây conflict.

### Nguy Hiểm Trung Bình

3. **Kotlin < 2.0.0** → maps-compose 8.x yêu cầu Kotlin 2.0+. Dự án dùng 2.2.10 nên OK.

4. **AGP 8.x + maps-compose** → Yêu cầu AGP 8.0+, dự án dùng 9.2.1 nên OK.

### Nguy Hiểm Thấp

5. **Polyline không simplify** → Không crash, chỉ giật. Nhưng ảnh hưởng user experience.

---

## 12. Câu Hỏi Chưa Giải Đáp

1. **Polyline simplification:** Có nên simplify lúc lưu (Room) hay lúc vẽ (ViewModel)? → Tùy vào trade-off disk space vs flexibility. Khuyến nghị: simplify lúc vẽ.

2. **Circle onClick:** Khi user bấm zone để chọn sửa, màn Map có cần nhảy sang Zone Editor hay chỉ highlight zone? → PRD chưa nói rõ. Khuyến nghị: nhấn zone → mở danh sách Zone → chọn sửa (như US-13).

3. **Marker animation (3 members):** Có nên animate member từ vị trí cũ sang mới mỗi update, hay chỉ jump? → Animate sẽ giật camera. Khuyến nghị: jump (không animate).

4. **compose-stability.conf:** Khi nào thêm? → Nếu History screen giật ở main loop. Để phase 2.

5. **Billing quota:** Nếu demo chạy 1 giờ, API call tính bao nhiêu? → Maps SDK for Android đếm theo "Calls" (per 1000), demo cục bộ không call backend, nên là 0. Chỉ tinh tế khi thêm backend.

---

## Nguồn Tham Khảo

- [Google Maps Compose Library](https://developers.google.com/maps/documentation/android-sdk/maps-compose)
- [android-maps-compose GitHub](https://github.com/googlemaps/android-maps-compose)
- [Maps SDK for Android Release Notes](https://developers.google.com/maps/documentation/android-sdk/release-notes)
- [Maps JavaScript API Polyline Documentation](https://developers.google.com/maps/documentation/javascript/shapes)
- [GitHub Issue #551: Jittery animation](https://github.com/googlemaps/android-maps-compose/issues/551)
- [GitHub Issue #391: ANR in animate](https://github.com/googlemaps/android-maps-compose/issues/391)
- [Simplify.js Algorithm](https://github.com/emcconville/point-reduction-algorithms)
