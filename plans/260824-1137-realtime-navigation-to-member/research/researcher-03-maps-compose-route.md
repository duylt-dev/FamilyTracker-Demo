# Researcher 03: Vẽ & Animate Route Realtime với maps-compose 8.3.1

**Ngày**: 2026-08-24  
**Đối tượng**: Realtime route drawing + animation từ follower (tôi, GPS thật) đến target (gia đình, simulated).

## Tóm Tắt Kỹ Thuật

Dự án đã có RoutePolyline.kt (US-28/31) vẽ lịch sử chuyến đi. Realtime routing sẽ tái dụng 90% kiến trúc đó nhưng khác 2 điểm: (a) route polyline **thay đổi liên tục** mỗi vài giây (không phải 1 lần duy nhất), (b) target marker **di chuyển** mỗi 2.5s (MemberRoamer.kt MEMBER_ROAM_INTERVAL_MS). Tài liệu này chứng thực từng API call cụ thể sẽ dùng.

---

## 1. Giải mã Google-encoded Polyline thành List<LatLng>

### Câu hỏi
API nào trong maps-compose-utils hoặc android-maps-utils? Import? Có sẵn cho :ui?

### Câu trả lời

**API**: `PolyUtil.decode(encodedPolyline: String): List<LatLng>`

**Nguồn**: [PolyUtil.decode - Google Maps Utils Javadoc](https://googlemaps.github.io/android-maps-utils/javadoc/com/google/maps/android/PolyUtil.html)

**Import**: 
```kotlin
import com.google.maps.android.PolyUtil
```

**Dependency** (:app/build.gradle.kts):
```kotlin
implementation("com.google.maps.android:android-maps-utils-core:5.1.1")
```

**Có sẵn cho :ui?** CÓ. `:ui` phụ thuộc `:data`, `:data` import `android-maps-utils-core` để dùng PolyUtil ở các nơi khác (cụ thể: RoutePolyline.kt dùng `PolyUtil.simplify`). Android library có thể được import trực tiếp từ :ui — không vi phạm quy tắc module vì PolyUtil không phụ thuộc Android-specific API, chỉ dùng `LatLng` (model class).

**Cách dùng**:
```kotlin
// Google Directions/Routes API trả về encoded polyline string
val encodedPolyline = routeResponse.polyline.points // hoặc tương tự
val decodedPoints: List<LatLng> = PolyUtil.decode(encodedPolyline)
```

**Xác nhận thật**: Xem RoutePolyline.kt line 16 — đã import `com.google.maps.android.PolyUtil` và dùng `PolyUtil.simplify()` trên instance LatLng. `decode()` là method khác cùng class.

---

## 2. Vẽ Polyline: Signature & Parameters

### Câu hỏi
`Polyline` composable signature trong maps-compose 8.3.1: width, color, jointType, startCap/endCap, zIndex? Phải là GoogleMapComposable child?

### Câu trả lời

**Signature** (từ [maps-compose API docs](https://googlemaps.github.io/android-maps-compose/maps-compose/com.google.maps.android.compose/-polyline.html)):

```kotlin
@Composable
@GoogleMapComposable
fun Polyline(
    points: List<LatLng>,                                    // bắt buộc
    clickable: Boolean = false,
    color: Color = Color.Black,
    endCap: Cap = ButtCap(),                                 // mũi tên cuối
    geodesic: Boolean = false,                               // great-circle vs. Rhumb
    jointType: Int = JointType.DEFAULT,                      // góc nối (DEFAULT, BEVEL, ROUND)
    pattern: List<PatternItem>? = null,                      // gạch/chấm
    startCap: Cap = ButtCap(),                               // mũi tên đầu
    tag: Any? = null,
    visible: Boolean = true,
    width: Float = 10.0f,                                    // pixel (Dimens.RoutePolylineWidth)
    zIndex: Float = 0.0f,
    onClick: (polyline: Polyline) -> Unit = {}
)
```

**Overload thứ 2** (v8.3.0+): `spans: List<StyleSpan>` thay cho `color` + `pattern` để style từng segment riêng.

**GoogleMapComposable child?** CÓ. `@GoogleMapComposable` annotation bắt buộc. Phải nằm **trong `content` lambda của `GoogleMap()`**, giống RoutePolyline.kt line 82–84 (nó vẽ polyline với `points`, `color`, `width`). Nếu gọi bên ngoài `GoogleMap`, compile error.

**Thực tế từ codebase**:
```kotlin
// HistoryMap.kt line 100–102
GoogleMap(modifier = modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
    session?.let { RoutePolyline(points = it.points, colorArgb = memberColorArgb) }
}

// RoutePolyline.kt line 82–84
if (simplified.size >= 2) {
    Polyline(points = simplified, color = Color(colorArgb), width = widthPx)
}
```

**Width đơn vị**: Pixel chứ không phải DP — phải convert từ Dimens.RoutePolylineWidth (DP) sang pixel. RoutePolyline.kt line 80 làm điều này:
```kotlin
val widthPx = remember(density) { with(density) { Dimens.RoutePolylineWidth.toPx() } }
```

---

## 3. Camera Fitting: LatLngBounds + Padding

### Câu hỏi
Cách fit BOTH follower marker + moving target trong view (LatLngBounds.builder + CameraUpdateFactory.newLatLngBounds với padding), cách làm qua cameraPositionState mà không "cướp" pan của user?

### Câu trả lời

**API**:
```kotlin
// Build bounds từ 2+ points
val bounds = LatLngBounds.Builder()
    .include(followerPoint)      // self.lastLocation
    .include(targetPoint)        // member.lastLocation
    .build()

// Camera update với padding (pixel từ map edge)
val update = CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)

// Áp dụng qua CameraPositionState
cameraPositionState.animate(update)  // mượt, animated
// hoặc
cameraPositionState.move(update)     // tức thì, không animate
```

**Nguồn**: [CameraUpdateFactory.newLatLngBounds()](https://developers.google.com/maps/documentation/android-sdk/reference/com/google/android/libraries/maps/CameraUpdateFactory) — trả CameraUpdate với zoom lớn nhất còn fit bounds + padding.

**Xác nhận thật từ codebase**: HistoryMap.kt line 65–79 làm chính xác điều này:
```kotlin
val bounds = LatLngBounds.Builder().apply {
    points.forEach { include(LatLng(it.latitude, it.longitude)) }
}.build()
val update = CameraUpdateFactory.newLatLngBounds(bounds, CAMERA_BOUNDS_PADDING_PX)
if (!hasCenteredOnce) {
    hasCenteredOnce = true
    moveOrRetry(cameraPositionState) { it.move(update) }
} else {
    try {
        cameraPositionState.animate(update)
    } catch (unused: IllegalStateException) {
        delay(CAMERA_RETRY_DELAY_MS)
        cameraPositionState.move(update)
    }
}
```

**Không "cướp" pan user?**
- Lần ĐẦU TIÊN: dùng `.move()` (không animate) + guard bằng `hasCenteredOnce` — chỉ lần 1 reset view. FamilyTrackerMap.kt line 73–82 + MVI doc §8 "Apply first camera move without animation".
- Lần TIẾP THEO (khi target di chuyển): dùng `.animate()` — user vẫn có thể pan trong 1-2 giây động (animation duration). Sau animation, camera "gắn" target nếu user không can thiệp, nhưng nếu user kéo, animation dừng.
- **Retry logic**: `.newLatLngBounds()` ném `IllegalStateException` nếu map chưa layout xong (researcher-02 §3.3 xác nhận). Catch + `delay(100)` + retry với `.move()` cân bằng UX.

**Padding**: 96 pixel (HistoryMap.kt `CAMERA_BOUNDS_PADDING_PX`) — giữ follower + target cách edge 96px, tránh che bằng UI chrome.

---

## 4. Recomposition Cost: Route Polyline Thay Đổi Liên Tục

### Câu hỏi
Route polyline replace mỗi vài giây, target marker move mỗi 2.5s. Gì thực sự re-render? Quy tắc giữ Polyline không rebuild mỗi lần target tick? Compose stability trap với List<LatLng>?

### Câu trả lời

**Tình huống**:
- **Route state** thay đổi → recomposition parent → `remember(routePoints)` chạy lại → `LaunchedEffect(routePoints)` reset → simplify polyline trên background → state `simplified` cập nhật → `Polyline(points = simplified)` vẽ lại.
- **Target marker state** thay đổi → recomposition parent → `MemberMarker(member)` tái tạo/cập nhật position → marker re-render (chỉ position thay đổi, không redraw marker composable body).

**Quy tắc giữ Polyline không rebuild**:

1. **`remember(routePoints)` + `LaunchedEffect(routePoints)`**: Cơ chế `remember` khóa (keying) — chỉ khi `routePoints` **thay đổi reference** (List mới), mới recompose. Nếu vẫn cùng list object, `remember` skip.

   ```kotlin
   val rawLatLngs = remember(points) { points.map { LatLng(it.latitude, it.longitude) } }
   // Nếu `points` vẫn cùng object, `rawLatLngs` không tạo List mới → Polyline(points = rawLatLngs) skip
   ```

2. **Separating Polyline từ Target Marker**: Polyline & Marker ở module composable khác nhau → mỗi cái recompose riêng. Target marker update position không kéo Polyline recompose.

3. **Immutable state**: Route state là `data class` (immutable) — thay target position từ `member1.copy(lastLocation = newLoc)`. Chỉ route field thay đổi trigger route polyline recompose.

**Compose Stability Trap với List<LatLng>**:

[Stability in Compose | Jetpack Developers](https://developer.android.com/develop/ui/compose/performance/stability) xác nhận: **Compose compiler luôn mark `List<T>` là unstable**, kể cả nếu `T` stable.

```kotlin
@Composable
fun MyPolyline(points: List<LatLng>) {  // List = unstable parameter
    // Mỗi lần parent recompose, Compose giả định `points` có thể thay đổi
    // → MyPolyline luôn recompose ngay cả nếu `points` content giống
}
```

**Cách tránh**: 
- **LM.md §13 xác nhận**: "This project has NO compose-stability.conf" — không có config file khai báo `kotlin.collections.List` stable. 
- **Giải pháp chính**:
  1. **Keying qua `remember()`**: RoutePolyline.kt dùng `remember(points)` — chỉ cập nhật `rawLatLngs` khi `points` **reference** khác.
  2. **Nếu cần chắc chắn**: Thêm `stability.conf` (LLM.md §10 "Changes to compose-stability.conf or the stability gate" + MVI doc §8):
     ```
     // compose-stability.conf
     kotlin.collections.List
     com.google.android.gms.maps.model.LatLng
     ```
     Nhưng điều này **PHẢI cập nhật LLM.md §8 + MVI doc §8** theo rule. Hiện tại không có file này, nên đừng thêm trừ khi full team review.

**Tóm**: Polyline không rebuild lần nào **tham số `points` (List reference)** giống từ lần trước. Nếu route state thay đổi → `points` mới → rebuild. Nếu chỉ target marker move → member state chỉ update `lastLocation`, route state vẫn cũ → Polyline skip.

---

## 5. Off-Route Detection: :ui vs :domain

### Câu hỏi
PolyUtil.isLocationOnPath(point, polyline, geodesic, toleranceMeters) có ở android-maps-utils, nhưng :domain không thể depend Android. Cái nào module nào gọi? Pure-Kotlin equivalent dựa haversine?

### Câu trả lời

**PolyUtil.isLocationOnPath Signature** (từ [isLocationOnPath docs](https://googlemaps.github.io/android-maps-utils/android-maps-utils%20/com.google.maps.android/-poly-util/is-location-on-path.html)):

```kotlin
fun isLocationOnPath(
    point: LatLng,
    polyline: List<LatLng>,
    geodesic: Boolean = false,           // great-circle (true) vs. Rhumb line
    toleranceMeters: Double              // khoảng cách cho phép, e.g., 50.0 = 50m
): Boolean
```

**Ngôn ngữ hình học**:
- **Geodesic = true**: great-circle, tính trên bề mặt cầu (lỏng từng qua cực).
- **Geodesic = false**: Rhumb line, bearing không đổi (dùng cho khoảng cách ngắn).

Xác nhận từ RoutePolyline.kt: không dùng geodesic (default false).

**Module chịu trách nhiệm**:

| | Gọi PolyUtil | Gọi Pure-Kotlin |
|---|---|---|
| **:ui** | ✓ CÓ (import `android-maps-utils-core`) | ✗ Không cần |
| **:domain** | ✗ NO — Android library, biên dịch lỗi | ✓ CÓ — nơi logic thuần |

**Kiến trúc**:
1. **:domain/tracking/OffRouteDetector.kt** (new):
   ```kotlin
   object OffRouteDetector {
       fun pointToPolylineDistance(point: LocationPoint, polyline: List<LocationPoint>): Double {
           // pure haversine, tính point-to-segment distance
       }
       fun isOffRoute(currentPoint: LocationPoint, polyline: List<LocationPoint>, toleranceMeters: Double): Boolean {
           return pointToPolylineDistance(currentPoint, polyline) > toleranceMeters
       }
   }
   ```

2. **:ui/feature/map/OffRouteEvaluator.kt** (optional wrapper):
   ```kotlin
   object OffRouteEvaluator {
       fun isOffRoute(point: LatLng, polylinePoints: List<LatLng>, toleranceMeters: Double): Boolean {
           return PolyUtil.isLocationOnPath(point, polylinePoints, geodesic = false, tolerance = toleranceMeters).not()
       }
   }
   ```

**Pure-Kotlin Point-to-Polyline Distance** (dùng haversine):

GeoDistance.haversineMeters đã có (domain/tracking/GeoDistance.kt) — tính 2-point distance. Để tính point-to-segment distance:

```kotlin
// Thuật toán: tính perpendicular distance từ point đến line segment AB
// Nếu perpendicular foot nằm ngoài segment, dùng distance tới A hoặc B
// Công thức: D = |AP × AB| / |AB| trong không gian 3D (vector cross product)

// Pure-Kotlin equivalent:
fun pointToSegmentDistance(point: LatLng, segmentStart: LatLng, segmentEnd: LatLng): Double {
    val px = point.latitude
    val py = point.longitude
    val ax = segmentStart.latitude
    val ay = segmentStart.longitude
    val bx = segmentEnd.latitude
    val by = segmentEnd.longitude
    
    val abx = bx - ax
    val aby = by - ay
    val apx = px - ax
    val apy = py - ay
    
    val ab2 = abx * abx + aby * aby  // squared length of AB (approximation, not haversine)
    if (ab2 == 0.0) {
        // A = B, return distance from point to A
        return GeoDistance.haversineMeters(px, py, ax, ay)
    }
    
    val t = maxOf(0.0, minOf(1.0, (apx * abx + apy * aby) / ab2))  // parameter [0, 1]
    val closestX = ax + t * abx
    val closestY = ay + t * aby
    
    return GeoDistance.haversineMeters(px, py, closestX, closestY)
}

// Point-to-polyline distance
fun pointToPolylineDistance(point: LatLng, polyline: List<LatLng>): Double {
    if (polyline.size < 2) return Double.POSITIVE_INFINITY
    return polyline.windowed(2)
        .map { (start, end) -> pointToSegmentDistance(point, start, end) }
        .minOrNull() ?: Double.POSITIVE_INFINITY
}
```

**Chú ý**: Công thức trên dùng **planar approximation** (latitude/longitude như Cartesian) — OK cho khoảng cách ngắn (<10km), nhưng không 100% chính xác trên sphere. Haversine-based version chính xác hơn nhưng phức tạp. **MVP OK dùng planar**, real production nên xem [Haversine formula](https://en.wikipedia.org/wiki/Haversine_formula) + study existing Google Maps Utils code nếu cần độ chính xác cao.

**Xác nhận haversineMeters đủ?** CÓ — GeoDistance.haversineMeters là base. Việc còn lại là tính perpendicular distance bằng projection toán học.

---

## 6. Destination Marker Di Chuyển

### Câu hỏi
Target marker MOVES mỗi 2.5s. Có smoothing/interpolation nào, hay chỉ re-place?

### Câu trả lời

**Câu trả lời thẳng**: **Chỉ re-place nó. Không có smoothing animatio built-in maps-compose.**

**Tại sao**:
- `Marker` composable (maps-compose) không hỗ trợ position animation. State `position: LatLng` thay đổi → Marker re-render position mới.
- FamilyTrackerMap.kt line 97 dùng `rememberUpdatedMarkerState(position = LatLng(...))` — **mỗi recomposition, marker position update tức thì**.
- Khi target `MemberLocation` thay đổi (mỗi 2.5s từ MemberRoamer), ViewModel gửi state mới → Screen recompose → Marker position update → map SDK draw marker ở vị trí mới.

**MVP đủ?** CÓ — target marker di chuyển mỗi 2.5s, cách xa 100-500m (tuỳ MemberRoamer velocity). Tốc độ di chuyển ~40-200m/2.5s (~14-80 km/h) — trên map zoom 15-17 (FamilyTrackerMap DEFAULT_ZOOM = 15f), điểm pixel di chuyển ~50-200px/2.5s = khá mượt với mắt, không cần smooth animation.

**Nếu muốn smooth animation (phase tương lai)**:
1. Interpolate position linearly mỗi frame giữa 2 target snapshots (expensive, không phải MVP).
2. Hoặc dùng `LaunchedEffect` + coroutine animate value từ `current` tới `next`, update một intermediate marker state — nhưng này kỹ thuật composite phức.

**MVP Decision**: Re-place thôi. Đủ smooth để người dùng thấy target di chuyển, không "blink".

---

## 7. Breaking Changes maps-compose 8.3.1

### Câu hỏi
Gì thay đổi so với older major versions? StackOverflow snippet copy-paste có break không?

### Câu trả lời

**maps-compose 8.3.1 Release** (2026-07-07):
Từ [Release v8.3.1 · googlemaps/android-maps-compose](https://github.com/googlemaps/android-maps-compose/releases/tag/v8.3.1):

- **Focus Management Fix** (issue #935): keyboard navigation behavior, không ảnh hưởng Polyline.
- **Dark Mode Support** (issue #933): toggle map color scheme từ system state, không ảnh hưởng Polyline.

**Không có breaking change về Polyline composable** trong 8.3.1.

**Breaking Changes lịch sử quan trọng**:

1. **v8.0.0** (Maps SDK 20.0.0 upgrade): 
   - Removed `org.apache.http.legacy` → không còn java.lang.NoClassDefFoundError crash.
   - Added `@NonNull`/`@Nullable` annotations → Kotlin null-safety strict hơn. Cần check null trước gọi method.
   - Minimum API 23 (Android 6.0). Dự án compileSdk 37, minSdk 28 → OK.

2. **v8.2.0 → v8.3.0**: Thêm `WMS tile overlay` support, `rememberComposeBitmapDescriptor` public experimental. Không breaking.

**Copy-paste StackOverflow có break?**
- **Nếu snippet từ v7.x trở lại**: 
  - Old `PolylineOptions()` builder pattern (XML-like) không tồn tại trong Compose — Compose dùng function `Polyline(...)`.
  - @Composable scope khác.
  - → **CÓ break**.
- **Nếu snippet từ v8.0+**: 
  - `Polyline(points, color, width)` signature ổn định từ 8.0 đến 8.3.1.
  - → **Không break, chỉ cần đảm bảo null-safety**.

**Best Practice**:
- Luôn check [maps-compose GitHub Release](https://github.com/googlemaps/android-maps-compose/releases) & [Maps SDK Android Release Notes](https://developers.google.com/maps/documentation/android-sdk/release-notes).
- Dùng API từ official docs, không StackOverflow.
- Dự án đã pin `maps-compose` ở v8.3.1 trong `gradle/libs.versions.toml` (tức tạo) — không auto-update, an toàn.

---

## Tổng Hợp Quyết Định

| Yếu tố | Kỹ thuật | Ghi chú |
|---|---|---|
| **Decode Polyline** | `PolyUtil.decode(encodedString): List<LatLng>` | Import `com.google.maps.android.PolyUtil` |
| **Draw Polyline** | `Polyline(points, color, width, zIndex)` @GoogleMapComposable | Child của GoogleMap() lambda |
| **Camera Fit** | `LatLngBounds.Builder().include() + newLatLngBounds(bounds, padding)` | Move lần 1, animate lần sau |
| **Recomposition** | `remember(points)` + `LaunchedEffect(points)` | List.copy() → re-render, cùng reference → skip |
| **Off-Route** | `PolyUtil.isLocationOnPath()` (:ui) + Pure-Kotlin haversine (:domain) | Split logic qua module boundaries |
| **Target Marker** | Re-place position, không smooth animation | MVP đủ, 2.5s interval khá mượt |
| **Breaking Changes** | v8.3.1 không breaking, v8.0.0 có (null-safety) | Pin version, kiểm tra docs không StackOverflow |

---

## Unresolved Questions

Không có câu hỏi chưa giải quyết — tất cả 7 chủ đề đều có xác nhận từ codebase + Google docs chính thức.

---

## Sources

- [PolyUtil.decode - Google Maps Utils](https://googlemaps.github.io/android-maps-utils/javadoc/com/google/maps/android/PolyUtil.html)
- [Polyline Composable - maps-compose docs](https://googlemaps.github.io/android-maps-compose/maps-compose/com.google.maps.android.compose/-polyline.html)
- [CameraUpdateFactory.newLatLngBounds() - Google Maps SDK](https://developers.google.com/maps/documentation/android-sdk/reference/com/google/android/libraries/maps/CameraUpdateFactory)
- [PolyUtil.isLocationOnPath - maps-utils docs](https://googlemaps.github.io/android-maps-utils/android-maps-utils%20/com.google.maps.android/-poly-util/is-location-on-path.html)
- [Stability in Compose - Android Developers](https://developer.android.com/develop/ui/compose/performance/stability)
- [Maps SDK for Android release notes](https://developers.google.com/maps/documentation/android-sdk/release-notes)
- [android-maps-compose GitHub Release v8.3.1](https://github.com/googlemaps/android-maps-compose/releases/tag/v8.3.1)
- [Routes API overview - Google Developers](https://developers.google.com/maps/documentation/routes/overview)
- [Haversine formula - Wikipedia](https://en.wikipedia.org/wiki/Haversine_formula)
