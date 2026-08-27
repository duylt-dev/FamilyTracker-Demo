# Phase 01 — Nền tảng mạng và cổng `RoutingProvider`

## Context Links

- [plan.md](plan.md) · [VERIFICATION.md](research/VERIFICATION.md) · [researcher-03](research/researcher-03-provider-abstraction-and-di.md)
- [`LLM.md`](../../LLM.md) §2 (chiều phụ thuộc), §6 (Koin), §10 (quyền), §12 (file mới nằm ở đâu), §14 (version catalog)
- [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §3 (concurrency)

## Overview

**Ưu tiên:** P1 · **Trạng thái:** ✅ Hoàn thành (2026-08-24) — xem `reports/dev-phase-01-report.md`

Dự án **chưa có một dòng code mạng nào** — không HTTP client, không quyền `INTERNET`, không DTO.
Phase này dựng đủ nền để một provider bất kỳ cắm vào, và **không implement provider nào cả**.
Kết thúc phase, `./gradlew build` xanh và `PolylineDecoder` đã test ở cả hai precision.

## Key Insights

**#1 — OkHttp 5.5.0 thay vì Ktor. Đây không phải sở thích, đã đo POM thật trên Maven Central
(kiểm lại 2026-08-24):**

| Thư viện | `kotlin-stdlib` khai trong POM | So với Kotlin 2.2.10 của dự án |
|---|---|---|
| `io.ktor:ktor-client-core-jvm:3.5.2` | `2.3.21` | **Cao hơn** — Gradle kéo stdlib lên 2.3.21 trong khi compiler vẫn 2.2.10 |
| `io.ktor:ktor-client-core-jvm:3.4.0` | `2.3.0` | Vẫn cao hơn |
| `io.ktor:ktor-client-core-jvm:3.3.1` | `2.2.20` | Vẫn cao hơn |
| `com.squareup.okhttp3:okhttp:5.5.0` | `2.1.21` | **Thấp hơn** — Gradle giữ nguyên 2.2.10 của dự án |
| `com.squareup.okhttp3:okhttp:4.12.0` | `kotlin-stdlib-jdk8:1.8.21` | Cũng thấp hơn, cũng an toàn — nhưng xem bên dưới |

Luật đằng sau: stdlib **cũ hơn** compiler thì Gradle nâng lên bản của dự án, không sao. Stdlib
**mới hơn** compiler thì Kotlin plugin cảnh báo và có thể lỗi biên dịch. Chọn Ktor = phải nâng
Kotlin lên 2.3.x, kéo theo KSP (`ksp = "2.2.10-2.0.2"` khoá cứng theo Kotlin, `LLM.md` §13 Fixed #1),
Room, và compose compiler — một cuộc nâng cấp toolchain nằm ngoài phạm vi tính năng này.
**Giá của việc bỏ qua:** build đỏ ở bước cuối cùng, sau khi đã viết xong hai provider.

**Vì sao 5.5.0 chứ không phải 4.12.0** — bản trước của phase này chọn 4.12.0 với lý do *"5.x biên
dịch trên Kotlin mới, lặp lại đúng vấn đề của Ktor"*. **Lý do đó sai, đã đo:** `okhttp:5.5.0` khai
`kotlin-stdlib 2.1.21`, tức là *thấp hơn* 2.2.10 của dự án — nó không hề tạo ra vấn đề của Ktor.
Còn 4.x thì **đã dừng phát triển**: bản cuối là `4.12.0` (10/2023), không có 4.13. Chọn một nhánh
đã đóng chỉ vì một lý do không đứng được là cách chắc chắn nhất để sáu tháng nữa có người "nâng cấp
giúp" mà không ai biết nên hay không.

5.5.0 publish biến thể `androidApiElements` dạng AAR riêng (đọc từ Gradle module metadata), nên AGP
lấy đúng artifact Android, không phải jar JVM.

**#2 — `Directions`, không phải `Route`.** Repo đã dùng "Route" cho *chuyến đi lịch sử*:
`RouteSplitter`, `RouteStats`, `ObserveRouteForDayUseCase`, `RoutePolyline`, và `Routes.kt` của
navigation. Thêm `Route` thứ ba nghĩa là mỗi lần đọc code phải hỏi "route nào".

**#3 — Cổng tên `RoutingProvider`, enum tên `RoutingEngine`.** researcher-03 đặt cả hai là
`RoutingProvider` ở cùng module `:domain` — hai kiểu trùng tên buộc phải `import as` ở mọi call site.

**#4 — `:domain` KHÔNG được biết `LatLng`.** `:domain` là `kotlin.jvm` thuần (`LLM.md` §2);
`import com.google.android.gms.maps.model.LatLng` là **lỗi biên dịch**, không phải lỗi review.
Vì vậy `Directions.points: List<GeoPoint>` và `:ui` tự map sang `LatLng` khi vẽ.

**#5 — Decode ở `:domain`, không ở `:ui`.** `PolyUtil.decode(String)` của
`android-maps-utils` **không có tham số precision** (VERIFICATION đã xác thực chữ ký) và luôn
decode precision 5. Valhalla trả precision 6. Dùng `PolyUtil` cho Valhalla = toạ độ sai **10 lần**
— polyline nằm ngoài biển thay vì trên phố, và `RerouteEvaluator` đọc sai luôn.

**#6 — `KoinModulesTest.verify()` KHÔNG thấy `appConfigModule`, và `RoutingConfig` sống ở đó.**
`KoinModulesTest` chỉ include `dataModule, databaseModule, uiModule`; `appConfigModule` là `private`
trong `FamilyTrackerApp.kt` của `:app`. `verify()` soi **constructor** của từng definition, nên
`GraphHopperRoutingProvider(…, RoutingConfig)` ở phase-02 sẽ làm nó ném
`MissingKoinDefinitionException` — dù ở runtime binding vẫn có thật.

`simulatorEnabled` thoát được điều này chỉ vì nó được đọc qua `koinInject()` trong composable, tức
là không bao giờ là tham số constructor. `RoutingConfig` thì có. Cách sửa: thêm `RoutingConfig::class`
vào `extraTypes` — đúng cơ chế `Context::class`/`SavedStateHandle::class` đang dùng, và KDoc của
file test đã giải thích sẵn hình dạng này. **Làm ở phase-01, trước khi có provider nào**, vì lúc
gate đỏ giữa phase-02 thì phản xạ tự nhiên là làm yếu test đi cho xanh.

**#7 — Cancellation phải thật.** MVI doc §3 bắt mọi coroutine đi qua `launchSafely` và rethrow
`CancellationException`. `OkHttpClient.newCall(req).execute()` là lời gọi **chặn** và không biết gì
về coroutine: huỷ job sẽ để lại một thread nằm chờ tới khi timeout. Dùng `enqueue` +
`suspendCancellableCoroutine` + `invokeOnCancellation { call.cancel() }`.

## Requirements

**Chức năng**
1. `:domain` có cổng `RoutingProvider` mà `:ui` gọi được qua use case, không thấy HTTP.
2. `:domain` có `PolylineDecoder` thuần, nhận precision làm tham số.
3. `:data` có một `OkHttpClient` dùng chung + `Json` dùng chung, đăng ký ở Koin.
4. `RoutingConfig` (engine + key + base URL) đọc từ `local.properties` lúc build, vào Koin qua `:app`.

**Phi chức năng**
5. `./gradlew build` xanh — cụ thể là **không có cảnh báo stdlib version mismatch**.
6. `:domain` không có bất kỳ import Android nào (ép bởi Gradle plugin, không cần kiểm tra tay).
7. Timeout: connect 10s, read 15s, call 20s. Không có timeout = job treo tới khi service chết.
8. `local.properties` không bao giờ vào git (đã có trong `.gitignore` — xác nhận lại).

## Architecture

```
:ui  ──(chưa dùng ở phase này)
      │
:domain
  model/GeoPoint.kt            data class GeoPoint(latitude, longitude)
  model/Directions.kt          data class Directions(points, distanceMeters, durationSeconds,
                               engineId, attribution: List<String>)
  model/RoutingConfig.kt       enum RoutingEngine { GRAPHHOPPER, VALHALLA } + data class RoutingConfig
  repository/RoutingProvider.kt  suspend fun directions(from, to, profile): AppResult<Directions>
  tracking/PolylineDecoder.kt    decode(encoded: String, precision: Int): List<GeoPoint>
      ▲
:data
  remote/RoutingHttpClient.kt  OkHttp + suspendCancellableCoroutine, trả String body + HTTP code
  di/DataModule.kt             single<OkHttpClient>, single<Json>, single<RoutingHttpClient>
      ▲
:app
  build.gradle.kts             đọc local.properties -> buildConfigField
  FamilyTrackerApp.kt          appConfigModule: single { RoutingConfig(...) }
```

**Vì sao `RoutingConfig` nằm ở `:domain/model/` chứ không phải package `config` mới:** `LLM.md` §10
cấm phát minh package. Nó là một `data class` bất biến mà cả `:data` (dựng provider) lẫn `:app`
(nạp giá trị) cần thấy — đúng định nghĩa "một model dữ liệu" ở §12.

**Vì sao `:app` đọc `BuildConfig` chứ không phải `:data`:** `:data` hiện **không bật**
`buildFeatures.buildConfig`. Bật nó lên là thêm một `BuildConfig` thứ hai vào repo và một chỗ
thứ hai để cấu hình rò rỉ ra. `FamilyTrackerApp.kt` đã là "file DUY NHẤT thấy được `BuildConfig`
để đăng ký vào Koin cho `:ui`/`:data` đọc" (`LLM.md` §3) — đi tiếp đúng đường đó.

## Related Code Files

**Tạo mới**
- `domain/src/main/kotlin/.../domain/model/GeoPoint.kt`
- `domain/src/main/kotlin/.../domain/model/Directions.kt`
- `domain/src/main/kotlin/.../domain/model/RoutingConfig.kt`
- `domain/src/main/kotlin/.../domain/repository/RoutingProvider.kt`
- `domain/src/main/kotlin/.../domain/tracking/PolylineDecoder.kt`
- `domain/src/test/kotlin/.../domain/tracking/PolylineDecoderTest.kt`
- `data/src/main/java/.../data/remote/RoutingHttpClient.kt`

**Sửa**
- `gradle/libs.versions.toml` — thêm `okhttp`, `okhttp-mockwebserver3-junit4`
- `data/build.gradle.kts` — plugin `kotlin.serialization`, dep okhttp + kotlinx-serialization-json + mockwebserver3-junit4 (test)
- `app/build.gradle.kts` — đọc 4 khoá từ `local.properties` -> `buildConfigField`
- `app/src/main/AndroidManifest.xml` — `<uses-permission android:name="android.permission.INTERNET"/>`
- `app/src/main/java/.../FamilyTrackerApp.kt` — `appConfigModule` thêm `single { RoutingConfig(...) }`
- `data/src/main/java/.../data/di/DataModule.kt` — `single<OkHttpClient>`, `single<Json>`, `single<RoutingHttpClient>`
- `app/src/test/java/.../KoinModulesTest.kt` — `extraTypes` thêm `RoutingConfig::class` (Key Insight #6)

**Đã có sẵn, không phải làm lại** (chuẩn bị trước khi lập lịch implement, 2026-08-24):
- `local.properties` — 4 khoá routing đã điền, `GRAPHHOPPER_API_KEY` thật đã kiểm chạy được
- `local.properties.example` — mẫu rỗng đầy đủ comment, đã tạo ở gốc repo
- `data/src/test/resources/graphhopper-route-hanoi.json` + `valhalla-route-hanoi.json` + `README.md`
  — fixture response THẬT của cùng một cặp điểm Hà Nội, dùng ở phase-02/03

**Xoá:** không có.

## Implementation Steps

1. **Version catalog.** Thêm `okhttp = "5.5.0"` vào `[versions]`; hai library
   `okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }` và
   `okhttp-mockwebserver3-junit4 = { group = "com.squareup.okhttp3", name = "mockwebserver3-junit4", version.ref = "okhttp" }`.
   Không hardcode version trong `build.gradle.kts` (`LLM.md` §14).

   **`mockwebserver3-junit4`, không phải `mockwebserver`.** OkHttp 5.x vẫn ship artifact
   `mockwebserver` cũ, nhưng chỉ như một cầu tương thích (`okhttp3/mockwebserver/DeprecationBridgeKt`).
   API được bảo trì là `mockwebserver3.MockWebServer` + `mockwebserver3.MockResponse.Builder`
   (**bất biến, dựng bằng builder** — mọi snippet cũ dạng `MockResponse().setBody(...)` sẽ không
   biên dịch), và `mockwebserver3.junit4.MockWebServerRule` khớp đúng JUnit4 mà repo đang dùng.
2. **`data/build.gradle.kts`.** Thêm `alias(libs.plugins.kotlin.serialization)` vào khối `plugins`
   (chưa có — `:ui` có, `:data` chưa), `implementation(libs.okhttp)`,
   `implementation(libs.kotlinx.serialization.json)`, `testImplementation(libs.okhttp.mockwebserver3.junit4)`.
3. **Build thử ngay tại đây.** `./gradlew :data:compileDebugKotlin`. Nếu có cảnh báo stdlib
   version, dừng lại — đừng viết tiếp code trên một nền chưa đứng vững.
4. **Quyền INTERNET.** Thêm vào `app/src/main/AndroidManifest.xml`. Không cần
   `network_security_config.xml`: mọi endpoint đều HTTPS (`graphhopper.com`, `api.stadiamaps.com`,
   `valhalla1.openstreetmap.de`). Chỉ cần khi self-host Valhalla qua HTTP thuần — phase-03 xử lý.
5. **`:domain` models.** `GeoPoint`, `Directions`, `RoutingEngine` + `RoutingConfig`. Tất cả
   `data class` bất biến, không default value cho field bắt buộc.

   **`Directions.attribution: List<String>` — field bắt buộc, không nullable, không default.**
   Đây là điều kiện pháp lý #1 của [memo](docs/legal-memo-decision.md) mang hình dạng một kiểu dữ
   liệu: không dựng được một `Directions` mà quên credit. GraphHopper **tự trả** chuỗi credit trong
   `info.copyrights` (`["GraphHopper", "OpenStreetMap contributors"]` — đã kiểm response thật), nên
   mapper chép thẳng cái nhà cung cấp đòi thay vì đoán hộ họ. `engineId` vẫn giữ, nhưng để log và
   chẩn đoán, **không** để dựng câu credit — hai việc khác nhau, ghép lại thì đổi tên engine trong
   log sẽ âm thầm đổi cả nội dung pháp lý trên màn hình.
6. **`:domain` cổng.**
   ```kotlin
   interface RoutingProvider {
       suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions>
   }
   ```
   Chưa cần tham số profile — thêm được sau mà không phá call site nào nếu để default. `AppResult`
   đã có sẵn, `AppError.Network` đủ dùng, **không thêm kiểu lỗi mới** (VERIFICATION đã xác nhận).
7. **`PolylineDecoder`.** Thuật toán Google polyline chuẩn, `precision` làm tham số:
   `factor = 10.0.pow(precision)`. Trả `List<GeoPoint>`. Chuỗi rỗng -> `emptyList()`, chuỗi hỏng ->
   `emptyList()` chứ không ném (một byte lỗi từ server không được làm sập app).
8. **`PolylineDecoderTest`.** Ít nhất 4 case: vector precision-5 đã biết, vector precision-6 đã
   biết, chuỗi rỗng, chuỗi rác. **Thêm một test khẳng định cùng một chuỗi decode ở precision 5 và 6
   cho kết quả lệch đúng 10 lần** — đó là cái bẫy phase-03 sẽ đâm vào, ghim nó lại từ bây giờ.
9. **`RoutingHttpClient`.** Một hàm `suspend fun get(url: String): AppResult<String>` và
   `suspend fun postJson(url: String, body: String): AppResult<String>`. Dùng
   `suspendCancellableCoroutine` + `enqueue` + `invokeOnCancellation { call.cancel() }`.
   Map HTTP code -> `AppError` ở phase-02 (mỗi provider có format lỗi riêng), ở đây chỉ trả
   body + code thô.
10. **`app/build.gradle.kts`.** Tổng quát hoá cách đọc `local.properties` đang có cho `MAPS_API_KEY`
    thành một hàm đọc nhiều khoá — **vẫn qua `providers.fileContents(...)`**, tuyệt đối không
    `Properties().load(...)` (configuration cache đóng băng giá trị im lặng, xem comment sẵn có trong
    file và ENV-BRIEFING §5). Bốn khoá: `ROUTING_ENGINE` (mặc định `GRAPHHOPPER`),
    `GRAPHHOPPER_API_KEY`, `STADIA_API_KEY`, `VALHALLA_BASE_URL`.
11. **`appConfigModule`.** `single { RoutingConfig(engine = RoutingEngine.valueOf(BuildConfig.ROUTING_ENGINE), ...) }`.
    `valueOf` ném nếu gõ sai tên engine trong `local.properties` — **cố ý**: sai cấu hình phải nổ
    lúc khởi động, không phải im lặng rơi về mặc định rồi gọi nhầm nhà cung cấp suốt buổi demo.
12. **`DataModule`.** `single<OkHttpClient>` với 3 timeout, `single<Json> { Json { ignoreUnknownKeys = true } }`
    (`ignoreUnknownKeys` bắt buộc — hai API đều trả field mà ta không khai, thiếu cờ này là crash
    mỗi response), `single { RoutingHttpClient(get(), get()) }`.
13. **`local.properties.example`** — đã tạo sẵn, chỉ xác nhận 4 khoá routing còn khớp với những gì
    `app/build.gradle.kts` thật sự đọc. `local.properties` đã được xác nhận nằm trong `.gitignore`
    (`git check-ignore -v local.properties` → `.gitignore:9`) và **chưa từng có trong lịch sử git**
    (`git log -- local.properties` rỗng).
14. **`KoinModulesTest`.** Thêm `RoutingConfig::class` vào `extraTypes` (Key Insight #6), kèm một
    dòng KDoc nói vì sao — cùng khuôn giải thích đang có sẵn cho `Context`/`SavedStateHandle`:
    định nghĩa nằm ở `appConfigModule` của `:app`, module mà test này không include được.
15. `./gradlew build` + `./gradlew :app:testDebugUnitTest` (KoinModulesTest `verify()` phải xanh).

## Todo List

- [x] `okhttp = "5.5.0"` + `mockwebserver3-junit4` vào `gradle/libs.versions.toml`
- [x] `data/build.gradle.kts`: plugin serialization + 3 dependency
- [x] `./gradlew :data:compileDebugKotlin` xanh, không cảnh báo stdlib
- [x] `INTERNET` vào `app/src/main/AndroidManifest.xml`
- [x] `GeoPoint.kt`, `Directions.kt`, `RoutingConfig.kt`
- [x] `RoutingProvider.kt` (cổng)
- [x] `PolylineDecoder.kt` + `PolylineDecoderTest.kt` (5 case, có case lệch-10-lần)
- [x] `RoutingHttpClient.kt` với cancellation thật
- [x] `app/build.gradle.kts` đọc 4 khoá qua `providers.fileContents`
- [x] `appConfigModule` đăng ký `RoutingConfig`
- [x] `DataModule` đăng ký `OkHttpClient`, `Json`, `RoutingHttpClient`
- [x] `KoinModulesTest`: `extraTypes` += `RoutingConfig::class`
- [x] Xác nhận `local.properties.example` khớp các khoá `app/build.gradle.kts` đọc
- [x] `./gradlew build` và `:app:testDebugUnitTest` xanh

## Success Criteria

1. `./gradlew build` xanh, log **không** chứa `kotlin-stdlib ... is newer than`.
2. `PolylineDecoderTest` xanh, gồm case chứng minh precision sai làm lệch 10 lần.
3. `KoinModulesTest.verify()` xanh với các definition mới **và** với `RoutingConfig::class` trong
   `extraTypes` — không phải bằng cách gỡ bớt module khỏi test.
4. `grep -rn "import android" domain/src/main` không ra kết quả nào.
5. Không có provider nào tồn tại — phase này cố ý không kết nối tới bất kỳ máy chủ nào.

## Risk Assessment

| Rủi ro | Xác suất | Giảm thiểu |
|---|---|---|
| OkHttp 5.5.0 xung đột với dep khác | Thấp | Bước 3 build ngay sau khi thêm dep, trước khi viết code |
| `providers.fileContents` viết lại làm hỏng `MAPS_API_KEY` đang chạy | Trung bình | Build một APK và mở bản đồ trước khi commit — key hỏng thì bản đồ xám, thấy ngay |
| `PolylineDecoder` viết sai ở biên (giá trị âm, chunk 5 bit cuối) | Trung bình | Test vector đã biết trước, không tự chế dữ liệu test |
| OkHttp 5.x là KMP, AGP lấy nhầm artifact JVM | Thấp | Module metadata có biến thể `androidApiElements` dạng AAR — đã kiểm. Nếu vẫn sai, `:data` sẽ đỏ ngay ở bước 3 |
| Ai đó "nâng cấp giúp" sang Ktor sau này | Trung bình | `LLM.md` §14 ghi lý do **đo được** (bảng stdlib ở Key Insight #1), không ghi cảm tính. Một lý do sai còn nguy hiểm hơn không có lý do: nó bị phát hiện, rồi cả mục bị bỏ qua |

## Security Considerations

- **API key không vào git.** Chỉ `local.properties` (ignored) + `local.properties.example` (rỗng).
- **Key nằm trong APK.** `BuildConfig` là hằng số trong DEX, `strings` trên file APK là đọc được.
  Chấp nhận cho demo, nhưng **phải** đặt quota trần ở console nhà cung cấp. Không có đường xoay
  key nào ngoài build lại APK — ghi vào `LLM.md` §13 Open ở phase-06.
- **Chỉ HTTPS.** Không thêm `usesCleartextTraffic`, không thêm `network_security_config.xml` ở phase này.
- `Json { ignoreUnknownKeys = true }` — không bao giờ `isLenient = true` (nuốt JSON hỏng thành dữ liệu sai).

## Next Steps

Phase 02 cắm GraphHopper Cloud vào cổng vừa dựng. **API key đã có và đã kiểm chạy được** — mục
Chặn #1 ở [plan.md](plan.md) đã đóng.
