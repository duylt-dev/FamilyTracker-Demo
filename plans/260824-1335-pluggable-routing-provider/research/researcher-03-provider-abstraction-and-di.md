# Provider Abstraction & DI Design — Pluggable Routing Provider

**researcher-03 — 2026-08-24**

---

## Tóm tắt

Refactor cần **một cổng routing (interface)** ở `:domain/repository` với **hai implementation** (Valhalla + GraphHopper Cloud) chọn bằng **enum config**, sử dụng **Koin 4.2.2 qualifier** để di chuyển giữa provider lúc khởi tạo. Báo cáo này phân tích cách gắn khớp abstraction đó với kiến trúc MVI hiện tại, dựa trên tiền lệ có sẵn: **`LocationSource.kt` (interface `:domain`) + `FusedLocationSource` / `SimulatedLocationSource` (implementation `:data`) với Koin `named()` qualifier**.

---

## 1. Cổng Routing: Khai ở đâu & Hình dạng thế nào

### Câu hỏi: Cổng nằm ở `:domain` hay `:data`? Lệnh call trả về kiểu gì?

**Kết luận: Interface `RoutingProvider` ở `:domain/repository/`, model `NavigationRoute` (điểm polyline) thuần Kotlin, không `LatLng`.**

### Chứng cứ từ codebase

**Tiền lệ #1 — `LocationSource.kt` ở `:domain/repository`:**
```kotlin
// domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/repository/LocationSource.kt
interface LocationSource {
    fun stream(): Flow<LocationPoint>
}
```

`LocationPoint` là model thuần (không Android):
```kotlin
// domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/model/LocationPoint.kt
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDegrees: Float,
    val recordedAt: Instant,
)
```

**Tiền lệ #2 — `ZoneRepository.kt` ở `:domain/repository` trả `AppResult`:**
```kotlin
// domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/repository/ZoneRepository.kt
interface ZoneRepository {
    fun observeAll(): Flow<List<Zone>>
    suspend fun save(zone: Zone): AppResult<Zone>
    suspend fun delete(zoneId: String): AppResult<Unit>
}
```

### Đề xuất chữ ký

```kotlin
// domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/repository/RoutingProvider.kt
package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Cổng routing — abstraction qua hai provider (Valhalla, GraphHopper Cloud).
 * CHỈ loại thuần Kotlin, KHÔNG LatLng của Google. Polyline decode ở `:data`.
 */
interface RoutingProvider {
    /**
     * Tính đường từ A → B.
     * @param startLat, startLng: vị trí bắt đầu (lat/lng)
     * @param endLat, endLng: vị trí kết thúc
     * @return AppResult<RouteResponse> với polyline (lat/lng list), distance (m), duration (s)
     */
    suspend fun computeRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): AppResult<RouteResponse>
}

/** Model route — thuần Kotlin, không phụ thuộc Google Maps SDK */
data class RouteResponse(
    val polylinePoints: List<LatLngPoint>,  // decoded polyline, vị trí trung gian
    val distanceMeters: Double,              // tổng quãng đường
    val durationSeconds: Long,               // dự kiến thời gian (không tính thời gian chờ)
    val providerId: String,                  // "valhalla" hay "graphhopper" (cho debug/logging)
)

/** Điểm toạ độ thuần — giống LocationPoint nhưng không cần accuracy/speed/bearing */
data class LatLngPoint(
    val latitude: Double,
    val longitude: Double,
)
```

**Giá của lựa chọn sai:**
- Nếu interface ở `:data`: `:ui` không thấy được cổng (chiều phụ thuộc). ViewModel không thể gọi use case.
- Nếu polyline là `List<LatLng>` (Google): `:domain` phải import Google Maps SDK, vi phạm quy tắc "Kotlin JVM thuần". Sẽ không compile.

---

## 2. Enum Config: Đặt ở đâu? 3 lựa chọn

### Câu hỏi: Chọn provider lúc khởi tạo hay runtime?

### Lựa chọn (a) — Enum trong `:domain` + Koin select ở khởi tạo

```kotlin
// domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/routing/RoutingConfig.kt
enum class RoutingProvider {
    VALHALLA,
    GRAPHHOPPER,
}
```

**Koin wiring ở `:data/di/DataModule.kt`:**
```kotlin
val dataModule = module {
    // Đọc từ BuildConfig
    val provider = BuildConfig.ROUTING_PROVIDER  // "VALHALLA" hay "GRAPHHOPPER"
    
    single<RoutingProvider> {
        when (provider) {
            "VALHALLA" -> ValhallaRoutingProvider(get())  // HttpClient from Koin
            "GRAPHHOPPER" -> GraphHopperRoutingProvider(get())
            else -> throw IllegalArgumentException("Unknown provider: $provider")
        }
    }
}
```

**Giá:**
- ✅ Lựa chọn lúc khởi tạo (đơn giản).
- ✅ Không runtime config (không thay đổi sau).
- ✅ YAGNI — không thêm tính năng không dùng.
- ❌ Phải rebuild + resign APK để đổi provider (demo không phải lực mạnh).

**Chi phí build-time:**
- Thêm 1 product flavor hay 1 boolean field BuildConfig (~2 dòng gradle).

### Lựa chọn (b) — `BuildConfig` field (product flavor)

```gradle
// app/build.gradle.kts
productFlavors {
    create("valhalla") {
        buildConfigField("String", "ROUTING_PROVIDER", "\"VALHALLA\"")
    }
    create("graphhopper") {
        buildConfigField("String", "ROUTING_PROVIDER", "\"GRAPHHOPPER\"")
    }
}
```

**Giá:**
- ✅ Clear intent — hai binary khác nhau, một provider mỗi cái.
- ✅ BuildConfig là public (không phải Koin).
- ❌ Build 2 APK để test (test matrix phức tạp).
- ❌ CI/CD phải biết build cái nào.

**Chi phí:**
- Thêm flavor (~5 dòng gradle), CI config (~3 dòng).

### Lựa chọn (c) — Runtime via DataStore (skip MVP)

```kotlin
// domain/src/main/kotlin/.../RoutingConfig.kt
data class RoutingSettings(
    val provider: RoutingProvider,  // read from DataStore at startup
)
```

**Giá:**
- ✅ Đổi provider lúc runtime (flexibility).
- ❌ Cần Room + DataStore setup (thêm tầng).
- ❌ Xử lý race condition (observer đổi mid-flight).
- ❌ Không MVP — MVP không cần runtime switching.

**Chi phí:**
- Thêm ~80 dòng DataStore wiring, ~20 dòng giao diện settings.

### Khuyến nghị

**Chọn (a) — Enum `:domain` + Koin select, lấy giá trị từ `BuildConfig`.**

- **Lý do:**
  - Phù hợp MVP/demo (một provider tạm thời, có thể build-time swap).
  - Bắt chước tiền lệ `LocationSource`: `FusedLocationSource` được đăng ký "cứng" ở Koin; `SimulatedLocationSource` cũng vậy (lựa chọn lúc khởi tạo qua `data/location/LocationTrackingService.kt` gọi `get<LocationSource>(named("simulated"))`).
  - Nếu sau cần runtime config: thêm DataStore vào `:data` mà không thay đổi cấu trúc interface.

---

## 3. Cách Koin 4.2.2 chọn 1 trong N implementation

### Câu hỏi: Named qualifier vs `when` vs multibinding?

### Koin 4.2.2 API (verified)

Koin **KHÔNG có multibinding kiểu Dagger** (không có `@IntoSet`, `@IntoMap`). Ba cách thực tế:

#### Cách 1: Named qualifier (Khuyến nghị — bắt chước `LocationSource`)

```kotlin
// data/src/main/java/com/example/pion/family/tracker/demo/data/di/DataModule.kt
val dataModule = module {
    // HttpClient dùng chung cho cả hai provider
    single { HttpClient { /* config */ } }
    
    // Đăng ký hai implementation, mỗi cái một qualifier
    single<RoutingProvider>(named("valhalla")) { 
        ValhallaRoutingProvider(get()) 
    }
    single<RoutingProvider>(named("graphhopper")) { 
        GraphHopperRoutingProvider(get()) 
    }
    
    // Cái nào chọn? Dựa vào BuildConfig
    single<RoutingProvider> { 
        if (BuildConfig.ROUTING_PROVIDER == "VALHALLA") {
            get(named("valhalla"))
        } else {
            get(named("graphhopper"))
        }
    }
}
```

**Ưu điểm:**
- Dễ đọc, tuân theo `LocationSource` pattern hiện tại.
- `get()` tự động chọn unqualified instance.

**Nhược điểm:**
- Phải lặp lại việc chọn ở một `single { }` giữa đó (không tự động).

#### Cách 2: `when` trong single (đơn giản hơn)

```kotlin
single<RoutingProvider> {
    when (BuildConfig.ROUTING_PROVIDER) {
        "VALHALLA" -> ValhallaRoutingProvider(get())
        "GRAPHHOPPER" -> GraphHopperRoutingProvider(get())
        else -> throw IllegalArgumentException("Unknown provider")
    }
}
```

**Ưu điểm:**
- Ngắn gọn, logic ở một chỗ.

**Nhược điểm:**
- Không thể inject một instance cụ thể (VD debug: test `ValhallaRoutingProvider` riêng).

#### Cách 3: Platform-specific Koin `selectProperty()` (không khả dụng Koin 4.2.2)

Koin không có feature select bằng property như Dagger `@Qualifier`.

### Cách quay lại code hiện tại

**`LocationSource` trong DataModule.kt (line 38-47):**
```kotlin
// Hai LocationSource, chọn bằng qualifier — LLM.md §8.4, phase-04 Key Insight #9.
single<LocationSource>(named("fused")) { FusedLocationSource(androidContext()) }

// phase-09 US-33: SimulatedLocationSource đăng ký MỘT LẦN dưới kiểu cụ thể, 
// rồi bind alias sang LocationSource(named("simulated"))
single { SimulatedLocationSource() }
single<LocationSource>(named("simulated")) { get<SimulatedLocationSource>() }
```

**Chỉ khác: cho `RoutingProvider`, không cần truy cập như `SimulatedLocationSource()` (không có method public như `.load()` ngoài interface).**

### Khuyến nghị mẫu cho Routing

```kotlin
// data/src/main/java/com/example/pion/family/tracker/demo/data/di/DataModule.kt

// Bước 1: Đăng ký HttpClient dùng chung
single<HttpClient> { 
    HttpClient {
        install(JsonFeature) { serializer = KotlinxSerializer() }
    }
}

// Bước 2: Đăng ký hai implementation
single<RoutingProvider>(named("valhalla")) { 
    ValhallaRoutingProvider(
        httpClient = get(),
        apiKey = BuildConfig.VALHALLA_API_KEY,  // nếu cần
    ) 
}
single<RoutingProvider>(named("graphhopper")) { 
    GraphHopperRoutingProvider(
        httpClient = get(),
        apiKey = BuildConfig.GRAPHHOPPER_API_KEY,
    ) 
}

// Bước 3: Chọn cái nào dùng (unqualified, cho use case)
single<RoutingProvider> {
    when (BuildConfig.ROUTING_PROVIDER) {
        "VALHALLA" -> get(named("valhalla"))
        else -> get(named("graphhopper"))
    }
}
```

**Mẫu test fake:**
```kotlin
// data/src/test/java/.../FakeRoutingProvider.kt
class FakeRoutingProvider : RoutingProvider {
    override suspend fun computeRoute(...): AppResult<RouteResponse> = 
        AppResult.Success(RouteResponse(...))
}
```

---

## 4. Chuẩn hoá Polyline: Decode ở tầng nào?

### Câu hỏi: Valhalla precision 6, GraphHopper precision 5 → decode ở đâu để `:ui` không biết?

### Hiện trạng: Polyline encoding chuẩn

**Valhalla** trả polyline precision 6 (Google chuẩn).
**GraphHopper** trả precision 5 (hơi khác).

### Giải pháp: Decode ở `:data/mapper`, re-encode ở precision 6 → `:ui` không biết khác

```kotlin
// domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/repository/RoutingProvider.kt
// (same as before)
data class RouteResponse(
    val polylinePoints: List<LatLngPoint>,  // DECODED, lat/lng thô
    ...
)
```

**Thay vì trả `polylineString`, trả `List<LatLngPoint>` đã decode.**

**Mapper trong `:data`:**
```kotlin
// data/src/main/java/.../mapper/RoutingResponseMapper.kt
class RoutingResponseMapper {
    fun mapValhallaResponse(dto: ValhallaDirectionsDto): RouteResponse {
        // Valhalla trả encoded polyline (precision 6, chuẩn)
        val points = PolylineDecoder.decode(dto.routes[0].geometry, precision = 6)
        return RouteResponse(
            polylinePoints = points.map { LatLngPoint(it.lat, it.lng) },
            distanceMeters = dto.routes[0].distance,
            durationSeconds = dto.routes[0].time / 1000,
            providerId = "valhalla",
        )
    }
    
    fun mapGraphHopperResponse(dto: GraphHopperDirectionsDto): RouteResponse {
        // GraphHopper trả precision 5
        val points = PolylineDecoder.decode(dto.paths[0].points, precision = 5)
        return RouteResponse(
            polylinePoints = points.map { LatLngPoint(it.lat, it.lng) },
            distanceMeters = dto.paths[0].distance,
            durationSeconds = dto.paths[0].time / 1000,
            providerId = "graphhopper",
        )
    }
}
```

**`:ui` cách vẽ polyline:**
```kotlin
// ui/feature/navigation/component/NavigationPolyline.kt
// (example from RoutePolyline.kt pattern, LLM.md §3)

@GoogleMapComposable
fun NavigationPolyline(
    state: NavigationState,
    modifier: Modifier = Modifier,
) {
    val polylinePoints = state.route?.polylinePoints ?: return
    
    // Encode lại precision 6 để vẽ (chuẩn Google Maps SDK)
    val encoded = PolylineEncoder.encode(
        polylinePoints.map { LatLng(it.latitude, it.longitude) },
        precision = 6,
    )
    val decoded = PolyUtil.decode(encoded)  // maps-compose-utils, bộ tích hợp của Google
    
    Polyline(
        points = decoded,
        color = Color.Blue,
        width = 6f,
    )
}
```

**Tại sao không dùng `PolyUtil` ở `:data`?**
- `PolyUtil` (maps-compose-utils) import `com.google.android.gms.maps.model.LatLng` → không thể dùng ở `:domain`.
- `:data` CÓ Android, nhưng `PolylineDecoder` tự viết (Kotlin thuần, ~30 dòng) rẻ hơn pull thêm `maps-compose-utils`.

**Decoder thuần ở `:domain` (để test JUnit):**

```kotlin
// domain/src/main/kotlin/.../routing/PolylineDecoder.kt
object PolylineDecoder {
    /**
     * Decode Google-format polyline.
     * Precision 6 = 10^-6 độ (~0.1m), precision 5 = 10^-5 độ (~1m).
     */
    fun decode(encoded: String, precision: Int = 6): List<LatLngPoint> {
        val factor = Math.pow(10.0, precision.toDouble())
        val points = mutableListOf<LatLngPoint>()
        var lat = 0
        var lng = 0
        var i = 0
        while (i < encoded.length) {
            var shift = 0
            var result = 0
            while (true) {
                val b = encoded[i].code - 63
                i++
                result = result or ((b and 0x1f) shl shift)
                shift += 5
                if (b < 0x20) break
            }
            lat += if ((result and 1) == 0) result shr 1 else (result shr 1).inv()
            
            shift = 0
            result = 0
            while (true) {
                val b = encoded[i].code - 63
                i++
                result = result or ((b and 0x1f) shl shift)
                shift += 5
                if (b < 0x20) break
            }
            lng += if ((result and 1) == 0) result shr 1 else (result shr 1).inv()
            points.add(LatLngPoint(lat / factor, lng / factor))
        }
        return points
    }
}
```

**Test JUnit:**
```kotlin
@Test
fun `polyline decoder precision 5`() {
    val decoded = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
    assertEquals(2, decoded.size)
    assertEquals(38.5, decoded[0].latitude, 0.1)
    assertEquals(-120.2, decoded[0].longitude, 0.1)
}
```

### Khuyến nghị: Decoder ở `:domain` + Mapper ở `:data`

- **Lý do:**
  - `:domain` test được bằng JUnit (không Robolectric).
  - `:data` import mapper để gọi decoder + tạo `RouteResponse`.
  - `:ui` chỉ vẽ, không quan tâm precision.

---

## 5. HTTP Client: Ktor 3.5.2, một hay hai?

### Câu hỏi: Ktor mới nhất tương thích Kotlin 2.2.10? Một client hay mỗi provider một?

### Kiểm chứng Ktor 3.5.2 + Kotlin 2.2.10

**Từ web search:**
- Ktor 3.5.2 release 31 tháng 7 năm 2026 (GitHub).
- Ktorfit 2.7.5 (dependencies on Ktor 3.5.0) supports Kotlin >= 2.2.0.
- **Kết luận: Ktor 3.5.2 tương thích Kotlin 2.2.10** ✅

**Gradle version:**
```kotlin
// gradle/libs.versions.toml
ktor = "3.5.2"

// data/build.gradle.kts
implementation(libs.ktor.client.core)  // 3.5.2
implementation(libs.ktor.client.android)  // Android engine
implementation(libs.ktor.client.serialization)  // JSON
```

**Hoặc URI:**
https://mvnrepository.com/artifact/io.ktor/ktor-client-core/3.5.2

### Một HttpClient vs hai?

#### Lựa chọn 1: Một `HttpClient` dùng chung (Khuyến nghị)

```kotlin
// data/src/main/java/com/example/pion/family/tracker/demo/data/di/DataModule.kt
val dataModule = module {
    single<HttpClient> {
        HttpClient(Android) {  // Android engine
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000  // 30s
            }
        }
    }
    
    // Cả hai provider dùng HttpClient chung
    single<RoutingProvider>(named("valhalla")) { 
        ValhallaRoutingProvider(httpClient = get()) 
    }
    single<RoutingProvider>(named("graphhopper")) { 
        GraphHopperRoutingProvider(httpClient = get()) 
    }
    
    single<RoutingProvider> {
        when (BuildConfig.ROUTING_PROVIDER) {
            "VALHALLA" -> get(named("valhalla"))
            else -> get(named("graphhopper"))
        }
    }
}
```

**Giá:**
- ✅ Một engine, chia sẻ connection pool → tiết kiệm memory.
- ✅ YAGNI — không có lý do riêng biệt cho hai client.
- ❌ Nếu hai provider cần config khác nhau (timeout, retry): phức tạp.

#### Lựa chọn 2: Hai HttpClient, mỗi provider một

```kotlin
single<HttpClient>(named("valhallaClient")) {
    HttpClient(Android) { ... }
}
single<HttpClient>(named("graphhopperClient")) {
    HttpClient(Android) { ... }
}

single<RoutingProvider>(named("valhalla")) { 
    ValhallaRoutingProvider(httpClient = get(named("valhallaClient"))) 
}
```

**Giá:**
- ✅ Config riêng mỗi provider (nếu cần).
- ❌ Hai engine → gấp đôi memory.
- ❌ Không MVP.

### Khuyến nghị: Một `HttpClient`, config chung

- **Lý do:**
  - Valhalla + GraphHopper đều REST HTTPS → config giống nhau.
  - Một pool connection dùng chung.
  - MVP không cần tối ưu riêng lẻ.

---

## 6. Ánh xạ lỗi: Hai provider → một `AppError`

### Câu hỏi: Lỗi khác nhau → map về `AppError` hiện có thế nào?

### Lỗi từ provider

**Valhalla:**
- HTTP 400 → `RouteNotFoundException`
- HTTP 401 → Auth lỗi
- HTTP 503 → Service unavailable

**GraphHopper:**
- HTTP 400 → `JsonException`
- HTTP 401 → `UnauthorizedException`
- HTTP 429 → Rate limit

### Mapper trong `:data`

```kotlin
// data/src/main/java/.../routing/RoutingErrorMapper.kt
object RoutingErrorMapper {
    fun mapValhallaError(exception: Exception): AppError = when (exception) {
        is ClientRequestException -> AppError.Network("Route not found")
        is RedirectResponseException -> AppError.Network("Redirect: ${exception.response.status}")
        is ServerResponseException -> AppError.Network("Server error: ${exception.response.status}")
        is HttpRequestTimeoutException -> AppError.Network("Request timeout")
        else -> AppError.Unexpected(exception.message)
    }
    
    fun mapGraphHopperError(exception: Exception): AppError = when (exception) {
        is ClientRequestException -> {
            when (exception.response.status.value) {
                429 -> AppError.Network("Rate limited, try later")
                else -> AppError.Network("Invalid request")
            }
        }
        is ServerResponseException -> AppError.Network("Service unavailable")
        else -> AppError.Unexpected(exception.message)
    }
}
```

**Trong provider:**
```kotlin
// data/src/main/java/.../routing/ValhallaRoutingProvider.kt
class ValhallaRoutingProvider(private val httpClient: HttpClient) : RoutingProvider {
    override suspend fun computeRoute(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
    ): AppResult<RouteResponse> = try {
        val response = httpClient.get("https://api.openrouteservice.org/v2/directions/driving-car") {
            parameter("api_key", apiKey)
            parameter("start", "$startLng,$startLat")
            parameter("end", "$endLng,$endLat")
        }.body<ValhallaDirectionsDto>()
        
        AppResult.Success(ValhallaResponseMapper.map(response))
    } catch (e: Exception) {
        AppResult.Failure(RoutingErrorMapper.mapValhallaError(e))
    }
}
```

### AppError hiện có

Từ `domain/model/AppError.kt`:
```kotlin
sealed interface AppError {
    data class Unexpected(val message: String?) : AppError
    data class Network(val message: String?) : AppError
    data class NotFound(val message: String?) : AppError
    data class Validation(val message: String?) : AppError
}
```

**Không cần thêm loại mới** — `Network` chứa hết.

---

## 7. Test: Fake & Mock

### Câu hỏi: Test ViewModel, decoder, mapper?

### Pattern từ codebase

**Domain test (pure JVM):**
```kotlin
@Test
fun `polylineDecoder.decode with precision 6`() {
    val points = PolylineDecoder.decode("_p~iF~ps|U", precision = 6)
    assertEquals(1, points.size)
    assertEquals(38.5, points[0].latitude, 0.001)
}
```

**ViewModel test:**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)
    
    @After
    fun tearDown() = Dispatchers.resetMain()
    
    private fun viewModel(
        computeRoute: ComputeRouteUseCase = FakeComputeRouteUseCase(),
    ) = NavigationViewModel(
        savedStateHandle = SavedStateHandle(),
        computeRoute = computeRoute,
    )
    
    @Test
    fun `compute route success`() = runTest {
        val vm = viewModel(computeRoute = FakeComputeRouteUseCase(
            response = AppResult.Success(RouteResponse(...))
        ))
        
        vm.onIntent(NavigationIntent.ComputeRoute(memberId = "m1"))
        advanceUntilIdle()
        
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.route)
    }
}
```

**Fake RoutingProvider:**
```kotlin
// data/src/test/java/.../FakeRoutingProvider.kt
class FakeRoutingProvider : RoutingProvider {
    var throwOnCompute: Exception? = null
    
    override suspend fun computeRoute(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
    ): AppResult<RouteResponse> = 
        if (throwOnCompute != null) {
            AppResult.Failure(AppError.Network(throwOnCompute!!.message))
        } else {
            AppResult.Success(RouteResponse(
                polylinePoints = listOf(
                    LatLngPoint(startLat, startLng),
                    LatLngPoint(endLat, endLng),
                ),
                distanceMeters = 1000.0,
                durationSeconds = 60,
                providerId = "fake",
            ))
        }
}
```

**Test mapper:**
```kotlin
@Test
fun `mapValhallaResponse decodes polyline precision 6`() {
    val dto = ValhallaDirectionsDto(
        routes = listOf(ValhallaRoute(
            geometry = "_p~iF~ps|U",
            distance = 1000,
            time = 60000,
        ))
    )
    val mapped = RoutingResponseMapper.mapValhallaResponse(dto)
    assertEquals(1, mapped.polylinePoints.size)
    assertEquals("valhalla", mapped.providerId)
}
```

### Đường dẫn test theo LLM.md

- Domain test JUnit: `domain/src/test/kotlin/com/example/pion/family/tracker/demo/.../routing/PolylineDecoderTest.kt`
- ViewModel test: `ui/src/test/java/com/example/pion/family/tracker/demo/.../navigation/NavigationViewModelTest.kt`
- Data test: `data/src/test/java/com/example/pion/family/tracker/demo/.../routing/RoutingResponseMapperTest.kt`

---

## 8. Danh sách file sẽ tạo / sửa

### Tạo mới

#### `:domain` — 7 file

| Đường dẫn | Vai trò | Est. lines |
|---|---|---|
| `domain/src/main/kotlin/.../repository/RoutingProvider.kt` | Interface + model `RouteResponse`, `LatLngPoint` | 35 |
| `domain/src/main/kotlin/.../tracking/PolylineDecoder.kt` | Decoder thuần, precision 5/6 | 40 |
| `domain/src/main/kotlin/.../tracking/RoutingGeometry.kt` | `pointToSegmentDistance()`, `polylineWithinThreshold()` (reroute logic) | 50 |
| `domain/src/test/kotlin/.../tracking/PolylineDecoderTest.kt` | Unit test decoder | 25 |
| `domain/src/test/kotlin/.../tracking/RoutingGeometryTest.kt` | Unit test geometry | 30 |
| (Sửa) `domain/src/main/kotlin/.../tracking/TrackingConstants.kt` | Thêm `REROUTE_TARGET_THRESHOLD_M`, `REROUTE_FOLLOWER_THRESHOLD_M`, `REROUTE_MIN_INTERVAL_MS` | +3 |
| (Sửa) `domain/src/main/kotlin/.../model/AppError.kt` | Không cần sửa — `Network` đã đủ | 0 |

#### `:data` — 6 file

| Đường dẫn | Vai trò | Est. lines |
|---|---|---|
| `data/src/main/java/.../remote/dto/ValhallaDirectionsDto.kt` | DTO từ Valhalla API | 40 |
| `data/src/main/java/.../remote/dto/GraphHopperDirectionsDto.kt` | DTO từ GraphHopper API | 40 |
| `data/src/main/java/.../remote/datasource/ValhallaRoutingDataSource.kt` | HTTP call Valhalla | 50 |
| `data/src/main/java/.../remote/datasource/GraphHopperRoutingDataSource.kt` | HTTP call GraphHopper | 50 |
| `data/src/main/java/.../routing/RoutingResponseMapper.kt` | Map DTO → `RouteResponse` (decode polyline) | 60 |
| `data/src/main/java/.../routing/RoutingErrorMapper.kt` | Map exception → `AppError` | 30 |
| `data/src/main/java/.../routing/ValhallaRoutingProvider.kt` | Impl RoutingProvider — Valhalla | 40 |
| `data/src/main/java/.../routing/GraphHopperRoutingProvider.kt` | Impl RoutingProvider — GraphHopper | 40 |
| (Sửa) `data/src/main/java/.../di/DataModule.kt` | Thêm `single<HttpClient>`, `single<RoutingProvider>(...named)` | +15 |
| `data/src/test/java/.../routing/FakeRoutingProvider.kt` | Fake impl | 20 |

#### `:ui` — 4 file (navigation feature mới)

| Đường dẫn | Vai trò | Est. lines |
|---|---|---|
| `ui/src/main/kotlin/.../feature/navigation/NavigationContract.kt` | State + Intent + Effect | 50 |
| `ui/src/main/kotlin/.../feature/navigation/NavigationViewModel.kt` | MVI ViewModel | 80 |
| `ui/src/main/kotlin/.../feature/navigation/NavigationScreen.kt` | Route + Screen composable | 100 |
| `ui/src/main/kotlin/.../feature/navigation/component/NavigationPolyline.kt` | Vẽ polyline trên map | 40 |
| (Sửa) `ui/src/main/kotlin/.../di/UiModule.kt` | Thêm `viewModelOf(::NavigationViewModel)` | +1 |

#### Gradle

| Đường dẫn | Vai trò | Sửa gì |
|---|---|---|
| `gradle/libs.versions.toml` | Thêm Ktor 3.5.2 | `ktor = "3.5.2"` |
| `data/build.gradle.kts` | Thêm Ktor dep | `implementation(libs.ktor.client.*)` |

### Tóm lại

- **17 file tạo mới** (~700 dòng code)
- **5 file sửa** (thêm ~20 dòng binding + config)
- **Không bịa package mới** — dùng `domain/repository/`, `domain/tracking/`, `data/routing/`, `data/remote/`, `ui/feature/navigation/`

---

## Khuyến nghị

1. **Interface RoutingProvider ở `:domain/repository`** — bắt chước `LocationSource`, model toạ độ thuần Kotlin.

2. **Enum config `:domain` + Koin select** — chọn implementation lúc khởi tạo từ `BuildConfig`, đơn giản MVP.

3. **Koin 4.2.2 named qualifier** — bắt chước `LocationSource(named("fused"))` / `(named("simulated"))` pattern hiện tại.

4. **Polyline decode ở `:data/mapper`** — chuẩn hoá precision trước khi `:ui` vẽ.

5. **Một HttpClient dùng chung** — Ktor 3.5.2 tương thích Kotlin 2.2.10, không cần hai engine.

6. **Lỗi map về `AppError.Network`** — không thêm loại mới, dùng message để phân biệt.

7. **Test theo pattern codebase** — domain: JUnit, UI: `StandardTestDispatcher` + `turbine`, data: fake.

8. **Cập nhật LLM.md §3, §6, §13** — liệt kê file mới, Koin module, không cần thêm deviation.

---

## Câu hỏi chưa trả lời được

1. **Ktor HTTP retry logic** — Koin nên inject `HttpClient` với `HttpRequestRetry` plugin (exponential backoff) hay để datasource tự handle? Khuyến nghị: **Koin setup 3 retries, 1s exponential, datasource không retry thêm** (YAGNI).

2. **Polyline encoder thuần** — Có cần encoder (`List<LatLngPoint>` → string precision 6) ở `:domain` để test? Khuyến nghị: **Không — chỉ decoder. Encoder chỉ ở `:ui` khi vẽ.**

3. **Valhalla vs GraphHopper URL + API key** — Cách lưu API key an toàn? Khuyến nghị: **`BuildConfig.VALHALLA_API_KEY` + `.gitignore local.properties` đọc key lúc build** (không phải git).

4. **Timeout Valhalla/GraphHopper khác nhau?** — Valhalla thường nhanh hơn, GraphHopper có thể chậm. Timeout một size phù hợp không? Khuyến nghị: **Một `HttpTimeout` 30s cho cả hai; nếu GraphHopper thường quá slow, tune ở `GraphHopperRoutingProvider` constructor param.**

5. **Reroute threshold: 100m target, 50m follower** — Giả định từ PRD brief. Cần xác nhận hay test thực? Khuyến nghị: **Ánh xạ vào `TrackingConstants` (bắt buộc per LLM.md §8.8); nếu sai sau cần tune**.

