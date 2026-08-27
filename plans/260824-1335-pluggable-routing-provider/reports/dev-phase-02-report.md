# dev-phase-02-report — GraphHopper Cloud provider

**Ngày:** 2026-08-24 · **Phase:** phase-02-graphhopper-provider.md · **Trạng thái:** ✅ completed

## Files tạo mới

- `data/src/main/java/.../data/remote/dto/GraphHopperDirectionsDto.kt` (51 dòng) — `GraphHopperDirectionsDto`/`PathDto`/`InfoDto`
- `data/src/main/java/.../data/routing/GraphHopperDirectionsMapper.kt` (61 dòng)
- `data/src/main/java/.../data/routing/RoutingErrorMapper.kt` (43 dòng)
- `data/src/main/java/.../data/routing/GraphHopperRoutingProvider.kt` (84 dòng)
- `data/src/test/java/.../data/routing/GraphHopperDirectionsMapperTest.kt` (89 dòng, 5 test)
- `data/src/test/java/.../data/routing/GraphHopperRoutingProviderTest.kt` (106 dòng, 4 test — MockWebServer)

## Files sửa

- `data/src/main/java/.../data/di/DataModule.kt` — thêm `single<RoutingProvider>(named("graphhopper"))` +
  unqualified `single<RoutingProvider>` với `when` exhaustive trên `RoutingEngine` (không `else`).

**Không sửa:** `:domain`, `:ui`, `app/src/test/.../KoinModulesTest.kt`, fixture JSON — đúng scope
(xem "Vì sao không cần sửa `KoinModulesTest`" bên dưới).

## Gradle commands chạy và kết quả

| Lệnh | Kết quả |
|---|---|
| `./gradlew :data:compileDebugKotlin :data:compileDebugUnitTestKotlin` | ✅ BUILD SUCCESSFUL |
| `./gradlew build` | ✅ BUILD SUCCESSFUL in 20s |
| `./gradlew clean && ./gradlew build --no-build-cache` | ✅ BUILD SUCCESSFUL in 42s (fresh, không cache) |
| `./gradlew :data:testDebugUnitTest :app:testDebugUnitTest` | ✅ tất cả pass (xem bảng dưới) |
| `grep -rn "import android" domain/src/main` | rỗng |
| `find data/src/main -iname "*Valhalla*"` | rỗng — đúng scope, chưa đụng phase-03 |
| `git status --short` | chỉ 3 mục thuộc `:data` (DataModule.kt sửa, `data/remote/dto/`, `data/routing/` mới) |

### Test summary (sau `clean` + `--no-build-cache`)

| Class | tests | failures | errors |
|---|---|---|---|
| `data.remote.RoutingHttpClientTest` (phase-01, không đổi) | 3 | 0 | 0 |
| `data.routing.GraphHopperDirectionsMapperTest` | 5 | 0 | 0 |
| `data.routing.GraphHopperRoutingProviderTest` | 4 | 0 | 0 |
| `app.KoinModulesTest` (`verify()`) | 1 | 0 | 0 |
| `domain` (toàn bộ, không đổi) | 69 | 0 | 0 |

`GraphHopperRoutingProviderTest` đúng 4 case yêu cầu: 200+fixture → Success; 401 → Network; 400 →
Validation; 200 với `"paths": []` → NotFound. Case 200 còn assert thêm `server.takeRequest().method
== "GET"` — khoá lại Non-negotiable "dùng GET, không POST" bằng test thay vì chỉ đọc code.

### `grep -rn "key=" data/src/main`

```
GraphHopperRoutingProvider.kt:49:            // ... Never log the request URL itself: it carries `key=`.
RoutingErrorMapper.kt:12: * ... never logs the request URL, which carries `key=`.
```

Cả hai match chỉ nằm trong **comment giải thích lý do không log**, không nằm trong bất kỳ lời gọi
`FtdLog` nào. Lời gọi `FtdLog` DUY NHẤT trong code mới (`GraphHopperRoutingProvider.kt:50`,
`FtdLog.w(TAG, "routing_auth_failed engine=graphhopper code=$code")`) không chứa `key=`, không
chứa URL, không chứa API key — chỉ chứa engine id và HTTP code.

## Đối chiếu Success Criteria

1. ✅ `:data:test` xanh, gồm 4 case MockWebServer.
2. ⬜ Không làm — theo đúng chỉ dẫn ("Do not attempt Success Criterion 2 — device verification riêng").
3. ✅ `grep -rn "key=" data/src/main` — không match nào nằm trong lời gọi `FtdLog`.
4. ✅ `KoinModulesTest.verify()` xanh, **không sửa** `extraTypes` — xem giải thích dưới.
5. Không test trên máy thật (`ROUTING_ENGINE=VALHALLA` khiến app nổ lúc khởi động) — đã xác nhận
   bằng đường tĩnh: `dataModule`'s `single<RoutingProvider>` gọi `error("Valhalla chưa implement —
   phase-03")` trong nhánh `RoutingEngine.VALHALLA`, và binding này được resolve ngay khi có ai
   `get<RoutingProvider>()` đầu tiên (chưa có use case nào gọi ở phase-04, nên chưa thể chạy hết
   đường ống tới UI để đo — đúng phạm vi phase-02, không phải một khoảng trống).

## Vì sao không cần sửa `KoinModulesTest.extraTypes`

Đọc trực tiếp source `koin-test-jvm:4.2.2` (`org.koin.test.verify.Verification`/`VerifyModule`,
giải nén từ `~/.gradle/caches`) trước khi viết `GraphHopperRoutingProvider`, vì `baseUrl: String`
(tham số test-only, xem dưới) là một constructor param không có Koin definition tương ứng:

- `single<T>(qualifier) { ConcreteClass(...) }` (khai `<T>` **tường minh**, đúng cú pháp phase file
  Step 7 `single<RoutingProvider>(named("graphhopper")) { GraphHopperRoutingProvider(get(), get(),
  get()) }`) khiến `beanDefinition.primaryType` là **interface `RoutingProvider`**, không phải
  `GraphHopperRoutingProvider`. `RoutingProvider` là interface, `functionType.constructors` rỗng →
  `verify()` không soi constructor thật của `GraphHopperRoutingProvider` — khác hẳn mẫu `single {
  TrackingRepositoryImpl(get(), get(), get(), androidContext()) } bind TrackingRepository::class`
  (không khai `<T>`, primaryType suy từ kiểu trả về của lambda = `TrackingRepositoryImpl` cụ thể,
  nên `Context` mới cần `extraTypes`).
- Kể cả nếu có soi, `Verify.primitiveTypes` (whitelist mặc định, đọc trực tiếp từ source) đã sẵn
  `String::class` — tham số `baseUrl: String` sẽ tự pass, không cần thêm vào `extraTypes`.

Hai lý do trùng nhau nên không có cách nào path này làm `verify()` đỏ. Ghi lại đầy đủ vì đây là kiểu
giả định có thể sai lặng lẽ nếu không đọc source thật.

## Sai lệch / làm rõ so với phase file

1. **`durationSeconds = (time / 1000.0).roundToLong()`, không phải `time / 1000` như câu văn xuôi
   trong phase file viết.** Số học thật: `585990 / 1000 = 585.99`. Chia nguyên (`Long / Int` trong
   Kotlin) **truncates xuống 585**, không phải 586 — mâu thuẫn với chính assert `durationSeconds ==
   586` mà phase file yêu cầu. Đã kiểm bằng Python trước khi viết code (không đoán): `585990 // 1000
   == 585`, `round(585990 / 1000) == 586`. Dùng `roundToLong()` khớp đúng convention phase-01 đã
   dùng cho Valhalla's `time.roundToLong()` (VERIFY-2026-08-24.md mục 2). Test
   `GraphHopperDirectionsMapperTest` khoá `586L` lại — nếu ai đổi về truncate, test đỏ ngay.
2. **`baseUrl: String = BASE_URL` thêm vào constructor `GraphHopperRoutingProvider`** — không có
   trong phase file's Architecture/Step 6/Step 7 pseudocode. Cần thiết để
   `GraphHopperRoutingProviderTest` trỏ vào `MockWebServer` thay vì domain thật
   (`https://graphhopper.com`), theo đúng Requirement #4 "test phụ thuộc internet sẽ đỏ vào đúng
   ngày cần nó xanh nhất". DI ở `DataModule.kt` vẫn gọi đúng 3 tham số như phase file viết
   (`GraphHopperRoutingProvider(get(), get(), get())`) — `baseUrl` dùng default. Không cần
   `extraTypes` (xem mục trên).
3. **`RoutingErrorMapper.map` nhận `body: String`, không phải `body: String?`** như chữ ký gợi ý ở
   Step 5 (`(code: Int, body: String?)`). Theo đúng interface phase-01 thật để lại:
   `RoutingHttpClient.HttpResponse.body: String` không nullable — không có nguồn nào tạo ra `body =
   null` để truyền vào. Giữ `String?` sẽ chỉ thêm một nhánh không bao giờ xảy ra.
4. **Code HTTP không nằm trong bảng (403, 404, 422, các 5xx khác ngoài 501, …) mặc định map về
   `AppError.Network`** (nhánh `else` của `RoutingErrorMapper.map`) — phase file's bảng chỉ liệt kê
   400/401/429/501/5xx tường minh. Chọn `Network` (không phải `Validation`) làm mặc định vì code lạ
   nhiều khả năng là sự cố tạm thời phía server hơn là lỗi client cố định; sẽ xem lại nếu phase-03
   (Valhalla, hình dạng lỗi khác) lộ ra một quy tắc rõ ràng hơn.
5. **`RoutingErrorMapper.extractMessage` giả định body lỗi có hình dạng GraphHopper
   (`{"message": "..."}`)** — đúng như Architecture note "dùng chung cả 2 provider" chỉ nói về việc
   TÁI DÙNG hàm, không nói hình dạng body cũng dùng chung. Ghi rõ trong KDoc: phase-03 (Valhalla) mở
   rộng `extractMessage` nếu hình dạng lỗi của Valhalla khác, không fork file.

## Verification thủ công khác

- Giải mã fixture bằng script Python độc lập (không phụ thuộc code Kotlin) trước khi viết assert:
  69 điểm, điểm đầu `(21.0285, 105.85387)`, khớp chính xác giá trị plan yêu cầu.
- Không in/log/echo giá trị `GRAPHHOPPER_API_KEY` thật trong suốt phiên làm việc — test dùng
  `"test-key"` giả, không đọc `local.properties`.
- `local.properties` không xuất hiện trong `git status --short`.

Không commit — theo đúng yêu cầu.

## Next Steps

Phase-03 (Valhalla) cắm vào cùng `RoutingProvider`/`RoutingErrorMapper`: thêm
`single<RoutingProvider>(named("valhalla"))`, đổi nhánh `RoutingEngine.VALHALLA` trong
`DataModule.kt` từ `error(...)` sang `get(named("valhalla"))`, và mở rộng
`RoutingErrorMapper.extractMessage` nếu hình dạng lỗi JSON của Valhalla khác GraphHopper (mục "Sai
lệch" #5 ở trên). `GeoPoint`, `Directions`, `PolylineDecoder(precision = 6)`, `RoutingErrorMapper`
đều đã sẵn sàng dùng thật, không cần sửa gì ở `:domain`.
