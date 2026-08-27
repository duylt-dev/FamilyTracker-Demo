# dev-phase-01-report — Nền tảng mạng và cổng `RoutingProvider`

**Ngày:** 2026-08-24 · **Phase:** phase-01-network-foundation-and-routing-port.md · **Trạng thái:** ✅ completed

## Files tạo mới

- `domain/src/main/kotlin/.../domain/model/GeoPoint.kt` (12 dòng)
- `domain/src/main/kotlin/.../domain/model/Directions.kt` (28 dòng)
- `domain/src/main/kotlin/.../domain/model/RoutingConfig.kt` (22 dòng, gồm `enum RoutingEngine`)
- `domain/src/main/kotlin/.../domain/repository/RoutingProvider.kt` (21 dòng)
- `domain/src/main/kotlin/.../domain/tracking/PolylineDecoder.kt` (65 dòng)
- `domain/src/test/kotlin/.../domain/tracking/PolylineDecoderTest.kt` (85 dòng, 5 test case)
- `data/src/main/java/.../data/remote/RoutingHttpClient.kt` (76 dòng sau post-review fix, xem bên dưới)
- `data/src/test/java/.../data/remote/RoutingHttpClientTest.kt` (75 dòng, 3 test — thêm ở post-review fix)

## Files sửa

- `gradle/libs.versions.toml` — `okhttp = "5.5.0"`, 2 library entries (`okhttp`, `okhttp-mockwebserver3-junit4`)
- `data/build.gradle.kts` — `alias(libs.plugins.kotlin.serialization)`, `implementation(libs.okhttp)`, `implementation(libs.kotlinx.serialization.json)`, `testImplementation(libs.okhttp.mockwebserver3.junit4)`
- `app/src/main/AndroidManifest.xml` — `<uses-permission android:name="android.permission.INTERNET"/>`
- `app/build.gradle.kts` — generalized `mapsApiKey` reader into `localProperty(key, default)`; 4 new `buildConfigField` entries (`ROUTING_ENGINE`, `GRAPHHOPPER_API_KEY`, `STADIA_API_KEY`, `VALHALLA_BASE_URL`); `mapsApiKey` behavior unchanged
- `app/src/main/java/.../FamilyTrackerApp.kt` — `appConfigModule` gains `single { RoutingConfig(...) }`, imports `RoutingConfig`/`RoutingEngine`
- `data/src/main/java/.../data/di/DataModule.kt` — `dataModule` gains `single<OkHttpClient>` (10s/15s/20s timeouts), `single<Json> { Json { ignoreUnknownKeys = true } }`, `single { RoutingHttpClient(get()) }`
- `app/src/test/java/.../KoinModulesTest.kt` — `extraTypes` += `RoutingConfig::class`, KDoc explaining why (Key Insight #6 shape, matching `Context`/`SavedStateHandle`)

**Không sửa:** `local.properties` (đã điền sẵn), `local.properties.example` (đã đúng, chỉ verify), fixture JSON, `:ui` — đúng scope.

## Gradle commands chạy và kết quả

| Lệnh | Kết quả |
|---|---|
| `./gradlew :data:compileDebugKotlin` (Step 3 gate, ngay sau khi thêm dep, trước khi viết Kotlin) | ✅ BUILD SUCCESSFUL, **không** có dòng `kotlin-stdlib ... is newer than` |
| `./gradlew :domain:test --tests "*PolylineDecoderTest*"` | ✅ 5/5 pass |
| `./gradlew build` (đầu tiên, có cache) | ✅ BUILD SUCCESSFUL in 1m 15s |
| `./gradlew clean && ./gradlew build --no-build-cache` (fresh compile, không cache — để chắc chắn không có warning nào bị nuốt bởi `FROM-CACHE`) | ✅ BUILD SUCCESSFUL in 38s, `grep -iE "is newer than|stdlib"` trên log → rỗng |
| `./gradlew :domain:test :app:testDebugUnitTest` | ✅ domain 69/69 pass (0 failures/errors), app 1/1 pass (`KoinModulesTest.all koin modules resolve`) |
| `./gradlew build :domain:test :app:testDebugUnitTest --no-build-cache` (chạy lại lần cuối để xác nhận ổn định) | ✅ BUILD SUCCESSFUL |
| `grep -rn "import android" domain/src/main` | rỗng — đúng Success Criteria 4 |

## Success Criteria — đối chiếu

1. ✅ `./gradlew build` xanh, không có `kotlin-stdlib ... is newer than` (kiểm cả bản cache lẫn `--no-build-cache`).
2. ✅ `PolylineDecoderTest` xanh, 5 test: known precision-5 vector (canonical Google reference), known precision-6 vector (2 điểm thật từ `valhalla-route-hanoi.json`), chuỗi rỗng, chuỗi bị cắt cụt (thiếu byte kết thúc), và test "cùng chuỗi decode ở precision 5 vs 6 lệch đúng 10 lần".
3. ✅ `KoinModulesTest.verify()` xanh với `RoutingConfig::class` trong `extraTypes` — không gỡ module nào khỏi test.
4. ✅ `grep -rn "import android" domain/src/main` rỗng.
5. ✅ Không provider nào tồn tại — `grep -rniE "graphhopper|valhalla"` trong `:domain/src/main`, `:data/src/main`, `:app/src/main` chỉ ra comment/KDoc/config field, không có class `GraphHopperRoutingProvider`/`ValhallaRoutingProvider` hay lời gọi mạng nào.

## Sai lệch so với phase file

**Đã sửa sau review — xem "Post-review fixes" bên dưới.** Bản gốc phần này lý giải cách đọc "chỉ trả body + code thô" của Step 9 là bỏ code vào message string của `AppError.Network`. Coordinator chỉ ra điều đó sai (đối chiếu cả câu Architecture block lẫn Step 9), đã sửa — không còn là "diễn giải chấp nhận được" nữa, để nguyên phần dưới làm lịch sử quyết định.

1. ~~`RoutingHttpClient` — cách diễn giải "chỉ trả body + code thô"~~ — SAI, xem Post-review fixes #1.
2. **`continuation.isActive` guard thêm vào callback OkHttp** (không có trong phase file, nhưng cần thiết): tránh `IllegalStateException` nếu callback chạy sau khi coroutine đã bị huỷ — bổ sung nhỏ, không đổi hành vi cancellation đã yêu cầu (`invokeOnCancellation { call.cancel() }`). Giữ nguyên qua post-review fix.

## Điều plan có thể đã bỏ sót / đáng ghi chú cho phase sau

- **Canonical polyline vector trong tài liệu công khai đôi khi bị chép sai một ký tự** (một bản tôi nhớ ban đầu có thừa 1 ký tự `m` — `vxq\`m@` thay vì `vxq\`@`). Đã cross-check bằng cách viết encoder độc lập (Python) rồi encode ngược 3 điểm đã biết `(38.5,-120.2),(40.7,-120.95),(43.252,-126.453)` để xác nhận đúng chuỗi 27 ký tự trước khi đưa vào test — không tự chế dữ liệu, nhưng ai copy vector này từ trí nhớ/blog thứ cấp nên tự encode lại để verify thay vì tin ngay.
- **`app/build.gradle.kts`'s `localProperty()` helper gọi `localPropertiesText.map { }` 5 lần** (1 cho mỗi khoá) thay vì đọc file 1 lần rồi parse tất cả — chấp nhận được cho 1 file nhỏ đọc 1 lần lúc configuration, nhưng nếu sau này thêm nhiều khoá nữa (>10), nên đổi sang parse 1 lần thành `Map<String,String>` rồi tra cứu.
- **Bài học rút ra sau review, đáng ghi vào quy ước chung:** khi phase file cho MỘT chữ ký code cụ thể ("Step 9: `suspend fun get(url: String): AppResult<String>`") NHƯNG một câu văn xuôi ngay cạnh đó nói khác đi ("trả body + code thô"), ưu tiên câu văn xuôi — nó mô tả *ý định*, chữ ký code trong prose thường bị rút gọn cho dễ đọc chứ không phải hợp đồng kiểu chặt chẽ. Lẽ ra nên hỏi lại thay vì tự chọn cách đọc thuận tiện hơn cho việc giữ nguyên literal signature.

## Verification thủ công khác

- Xác nhận `local.properties` có đủ 5 khoá (`grep -oE '^[A-Z_]+=' local.properties`, không in giá trị) và `local.properties.example` khớp chính xác 5 khoá `app/build.gradle.kts` đọc.
- Xác nhận `local.properties` vẫn nằm trong `.gitignore` (`.gitignore:9`).
- Không in/log/echo giá trị thật của bất kỳ API key nào trong suốt phiên làm việc.

## Post-review fixes (2026-08-24, sau feedback coordinator)

Coordinator xác nhận build/test xanh độc lập, nhưng chỉ ra 2 sai lệch thật so với phase file. Cả hai đã sửa.

### Defect 1 (blocking phase-02) — `RoutingHttpClient` làm mất HTTP status code

**Vấn đề:** bản trước collapse mọi non-2xx thành `AppResult.Failure(AppError.Network("HTTP $code: $body"))` — code chỉ còn sống trong một chuỗi message. Phase-02 Step 5 cần `RoutingErrorMapper(code: Int, body: String?): AppError` map 400→`Validation`, 401→`Network`+log, 429→`Network`, 5xx→`Network`; với chữ ký cũ, cách duy nhất lấy lại code là string-parse `"HTTP 401: ..."` — đúng thứ một mapper riêng sinh ra để tránh, và vỡ âm thầm nếu ai sửa message string.

**Sửa:** đổi hẳn shape trả về.

```kotlin
data class HttpResponse(val code: Int, val body: String)

suspend fun get(url: String): AppResult<HttpResponse>
suspend fun postJson(url: String, body: String): AppResult<HttpResponse>
```

- `AppResult.Failure(AppError.Network(...))` giờ CHỈ dành cho lỗi **transport** (`IOException` — mất mạng, DNS, timeout, huỷ qua `call.cancel()`).
- Bất kỳ response nào server trả về thật, 2xx hay không, đều là `AppResult.Success(HttpResponse(code, body))` — provider (phase-02's `RoutingErrorMapper`) tự quyết định code đó nghĩa là gì.
- Không thêm kiểu `AppError` mới — `HttpResponse` là type riêng của `RoutingHttpClient`, không đụng `AppError`/`AppResult` ở `:domain`.
- Nhân tiện bỏ `?.` ở `response.body?.string()` — đã kiểm bytecode thật của `okhttp-android-5.5.0.aar` (`javap -v` trên `Response.class`): `public final okhttp3.ResponseBody body()` mang `@NotNull`, không nullable trong OkHttp 5.x. `.body.string()` giờ compile thẳng, không cần Elvis.

**File sửa:** `data/src/main/java/.../data/remote/RoutingHttpClient.kt` (viết lại toàn bộ, 76 dòng). `DataModule.kt` không cần đổi — constructor `RoutingHttpClient(OkHttpClient)` không đổi chữ ký.

**Test mới ghim lại hành vi này:** `data/src/test/java/.../data/remote/RoutingHttpClientTest.kt` (75 dòng, 3 case, dùng `mockwebserver3.MockWebServer` thật — không mock `OkHttpClient`):

| Test | Kết quả mong đợi | Kết quả thật |
|---|---|---|
| 200 với body | `AppResult.Success(HttpResponse(200, body))` | ✅ pass |
| 401 với body lỗi | `AppResult.Success(HttpResponse(401, errorBody))` — **KHÔNG** phải `Failure` | ✅ pass |
| Server đóng trước khi gọi (transport failure thật, không giả lập) | `AppResult.Failure` với `error is AppError.Network` | ✅ pass |

`./gradlew :data:testDebugUnitTest --tests "*RoutingHttpClientTest*"` → 3/3 pass (0 failures, 0 errors).

### Defect 2 — `ROUTING_ENGINE=` (có mặt nhưng rỗng) làm app crash thay vì rơi về mặc định

**Vấn đề:** `local.properties.example` viết rõ "Bỏ trống -> mặc định GRAPHHOPPER". `localProperty(key, default)` bản trước chỉ áp `default` qua `.getOrElse(default)` ở Provider ngoài — cái đó chỉ kích hoạt khi **cả file `local.properties` không tồn tại**, không phải khi thiếu khoá hay khoá rỗng. Cả hai trường hợp đó đều rơi vào `""` trước khi `getOrElse` kịp thấy, nên `RoutingEngine.valueOf("")` ném `IllegalArgumentException` lúc khởi động — đúng trường hợp file example hứa là an toàn.

**Sửa** — đưa `takeIf { it.isNotEmpty() } ?: default` vào NGAY trong `map`, để khoá thiếu, khoá rỗng, và file thiếu đều rơi về CÙNG một `default`:

```kotlin
fun localProperty(key: String, default: String = ""): String =
    localPropertiesText.map { text ->
        text.lineSequence().firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")?.trim()?.takeIf { it.isNotEmpty() } ?: default
    }.getOrElse(default)
```

**Không đổi:** một giá trị gõ sai nhưng KHÔNG rỗng (vd. `ROUTING_ENGINE=GRAPHOPPER`, thiếu 1 chữ H) vẫn đi thẳng vào `BuildConfig` nguyên văn — `RoutingEngine.valueOf("GRAPHOPPER")` vẫn nổ lúc khởi động như spec yêu cầu (Step 11: "sai cấu hình phải nổ lúc khởi động").

**File sửa:** `app/build.gradle.kts`.

**Verify thật trên chính `local.properties` của máy này** (backup trước, restore đúng nguyên văn sau — `diff` xác nhận khớp 100%, không đổi giá trị `GRAPHHOPPER_API_KEY`/key khác):

| Kịch bản | `local.properties` | `BuildConfig.ROUTING_ENGINE` sinh ra | Kết quả |
|---|---|---|---|
| Khoá rỗng | `ROUTING_ENGINE=` | `"GRAPHHOPPER"` | ✅ đúng default |
| Khoá vắng mặt hoàn toàn (xoá cả dòng) | (không có dòng `ROUTING_ENGINE=`) | `"GRAPHHOPPER"` | ✅ đúng default |
| Gõ sai, không rỗng | `ROUTING_ENGINE=GRAPHOPPER` | `"GRAPHOPPER"` (nguyên văn, không default hoá) | ✅ vẫn sẽ nổ ở `valueOf` lúc runtime, đúng ý đồ |
| Khôi phục thật | `ROUTING_ENGINE=GRAPHHOPPER` | `"GRAPHHOPPER"` | ✅ khớp giá trị gốc |

Xác nhận bằng cách đọc trực tiếp `app/build/generated/source/buildConfig/debug/.../BuildConfig.java` sau mỗi lần `./gradlew :app:generateDebugBuildConfig --rerun`, không suy đoán.

### Re-verify sau cả 2 fix

| Lệnh | Kết quả |
|---|---|
| `./gradlew clean && ./gradlew build --no-build-cache` | ✅ BUILD SUCCESSFUL in 36s. `grep -iE "is newer than\|stdlib"` trên log → rỗng. `grep -iE "FAILED"` → rỗng. |
| `./gradlew :domain:test :app:testDebugUnitTest :data:testDebugUnitTest --no-build-cache` | ✅ tất cả UP-TO-DATE/pass sau build ở trên |
| Domain test summary | 69/69 pass, 0 failures, 0 errors (không đổi so với trước fix — `PolylineDecoderTest` không bị đụng) |
| Data test summary | **10/10 pass** (7 cũ + 3 `RoutingHttpClientTest` mới), 0 failures, 0 errors |
| App test summary | 1/1 pass (`KoinModulesTest.verify()`, không đổi) |
| `grep -rn "import android" domain/src/main` | rỗng |
| `git status --short` | chỉ các file thuộc phase-01 đã sửa/tạo; không file rác, `local.properties` không xuất hiện trong diff (vẫn ignored, giá trị khôi phục nguyên văn) |

Không commit — theo đúng yêu cầu.

## Next Steps

Phase-02 (GraphHopper Cloud) cắm vào `RoutingProvider` vừa dựng — `RoutingHttpClient` (nay trả `HttpResponse(code, body)`), `RoutingConfig`, `PolylineDecoder`, `Directions.attribution` đều sẵn sàng dùng thật. `RoutingErrorMapper(code, body)` ở phase-02 giờ có đúng thứ nó cần để map 400/401/429/5xx mà không phải string-parse bất cứ thứ gì.
