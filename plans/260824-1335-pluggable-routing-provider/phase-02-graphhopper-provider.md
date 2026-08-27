# Phase 02 — GraphHopper Cloud provider

## Context Links

- [plan.md](plan.md) · [phase-01](phase-01-network-foundation-and-routing-port.md)
- [researcher-02 — GraphHopper Cloud](research/researcher-02-graphhopper-cloud.md) (endpoint, profile, giá, lỗi)
- [VERIFICATION](research/VERIFICATION.md) — mục "GraphHopper points_encoded Precision Claim"
- [`LLM.md`](../../LLM.md) §6 (Koin `named()`), §12

## Overview

**Ưu tiên:** P1 · **Trạng thái:** ✅ Hoàn thành 2026-08-24 — [dev-phase-02-report.md](reports/dev-phase-02-report.md)

Implementation đầu tiên sau cổng. Kết thúc phase, gọi được một tuyến đường thật từ
`https://graphhopper.com/api/1/route` và nhận về `Directions` với polyline đã decode.

## Key Insights

**#1 — Dùng `points_encoded=true` (mặc định). Không đụng vào `points_encoded=false`.**
researcher-02 có một bảng nói `points_encoded=false` cho "precision 6" — **sai và nguy hiểm**.
Sự thật (VERIFICATION đã xác thực): `false` trả về mảng GeoJSON `[lon, lat]` thô, **không mã hoá
gì cả**. Code nào cầm chuỗi đó đưa vào `PolylineDecoder` sẽ crash lúc chạy chứ không phải lúc build.
`true` trả polyline mã hoá **precision 5** — trùng chuẩn Google, và đó là lý do GraphHopper được
chọn làm mặc định.

**Và đừng hardcode số 5.** Response thật có sẵn field `points_encoded_multiplier: 100000.0` —
GraphHopper khai luôn hệ số của chính họ để client không phải đoán. Mapper đọc field đó, suy ra
`precision = log10(multiplier)` (= 5), rồi truyền vào `PolylineDecoder`. Field vắng mặt → mặc định
100000.0. Hệ số không phải luỹ thừa của 10 → `Validation`, đừng làm tròn cho qua chuyện. Chi phí
bằng ba dòng, và nó biến đúng cái bẫy precision của phase-03 thành một thứ không thể sập ở đây.

**#2 — GET và POST đảo thứ tự toạ độ.** GET: `point=lat,lon`. POST JSON: `points: [[lon, lat]]`.
Đảo nhầm ở Hà Nội (21.03, 105.85) cho ra một điểm giữa Ấn Độ Dương, và API trả 400 "Cannot find
point" — thông báo không hề gợi ý nguyên nhân. **Dùng GET**: 2 điểm, ngắn, cache được, và tránh
hẳn nhánh đảo toạ độ.

**#3 — `time` là mili-giây, `distance` là mét. Kiểu JSON cũng khác nhau.** Đo trên fixture thật
(`data/src/test/resources/graphhopper-route-hanoi.json`, Hồ Gươm → Văn Miếu):

| Field | Giá trị thật | Kiểu Kotlin phải khai | Bẫy |
|---|---|---|---|
| `distance` | `3166.054` | `Double` | Khai `Int` → `SerializationException` lúc parse |
| `time` | `585990` | `Long` | **mili-giây.** `durationSeconds = time / 1000` = 586s |
| `points_encoded_multiplier` | `100000.0` | `Double` | Xem Key Insight #1 |
| `points` | chuỗi 69 điểm | `String` | Chỉ đúng khi `points_encoded == true` |

`Directions.durationSeconds` là giây → phải chia 1000. Quên chia = ETA sai 1000 lần, và không có
test nào tự bắt được điều đó ngoài một assert cụ thể — fixture cho phép assert bằng số thật:
586 giây cho 3.17 km, không phải 585990.

**#4 — Profile free tier là ĐÚNG BA: `car`, `bike`, `foot`. Đã kiểm thật, không còn là câu hỏi mở.**
Gọi API bằng chính key của dự án ngày 2026-08-24:

| `profile` | HTTP | Response |
|---|---|---|
| `car` | 200 | tuyến 3166m / 586s ✅ |
| `bike` | 200 | ✅ |
| `foot` | 200 | ✅ |
| `motorcycle` | **400** | `"For your account the profile parameter can only be one of [car, bike, foot] but was motorcycle"` |
| `scooter` | **400** | cùng thông báo |

Vậy **`car` là lựa chọn duy nhất hợp lý cho dẫn đường bằng xe** ở free tier — không phải một mặc
định tạm chờ xác nhận nữa, mà là trần của gói. Vẫn để `PROFILE` thành hằng số một chỗ, nhưng comment
phải nói đúng sự thật: đổi sang `motorcycle` **đòi nâng gói trả phí**, không phải đổi một dòng.
Đây cũng là điểm khác biệt thật giữa hai engine: Valhalla (phase-03) có `motorcycle` ở cả FOSSGIS
lẫn Stadia mà không cần trả thêm.

**#5 — Credit lấy từ `info.copyrights`, không tự viết.** Response thật có
`"info": {"copyrights": ["GraphHopper", "OpenStreetMap contributors"], ...}`. Đó chính xác là thứ
điều kiện pháp lý #1 đòi hiển thị, do chính nhà cung cấp phát ra. Mapper chép nó vào
`Directions.attribution`; `RoutingAttribution` ở phase-05 chỉ việc `joinToString(" · ")`.

Tự chế câu credit từ `engineId` thì đúng hôm nay và sai vào ngày GraphHopper đổi yêu cầu — mà họ
đổi bằng cách đổi field này, không bằng cách gửi email cho chúng ta. Nếu `copyrights` vắng hoặc
rỗng → fallback `listOf("GraphHopper", "OpenStreetMap contributors")`, **không bao giờ** để rỗng.

**#6 — Free tier là non-commercial.** 500 credit/ngày, tối đa 5 điểm/request. Với debounce 60s
(phase-04) thì một phiên dẫn đường 1 giờ tốn ~60 credit — thoải mái. Nhưng điều khoản
non-commercial là ràng buộc **pháp lý**, không phải kỹ thuật; xem mục Chặn #3 ở plan.md.

## Requirements

**Chức năng**
1. `GraphHopperRoutingProvider` implement `RoutingProvider`, đăng ký Koin dưới `named("graphhopper")`.
2. `single<RoutingProvider>` không qualifier chọn theo `RoutingConfig.engine` bằng `when` **exhaustive**.
3. Lỗi HTTP và lỗi mạng map về `AppError` sẵn có, không thêm kiểu mới.

**Phi chức năng**
4. Test mapper chạy trên **`MockWebServer`**, không gọi mạng thật. Test phụ thuộc internet là test
   sẽ đỏ vào đúng ngày cần nó xanh nhất.
5. Không log API key. `FtdLog` của `:data` đã câm ở release, nhưng URL có `key=` thì debug log cũng
   không được in.

## Architecture

```
:data/remote/dto/GraphHopperDirectionsDto.kt   @Serializable, chỉ field thật sự dùng
:data/routing/GraphHopperRoutingProvider.kt    dựng URL -> RoutingHttpClient -> mapper
:data/routing/GraphHopperDirectionsMapper.kt   DTO -> Directions (gọi PolylineDecoder precision 5)
:data/routing/RoutingErrorMapper.kt            HTTP code + body -> AppError   (dùng chung cả 2 provider)
```

`GraphHopperRoutingProvider` **không tự parse JSON và không tự decode polyline** — nó chỉ dựng URL
và nối ba mảnh lại. Mapper thuần thì test được bằng một chuỗi JSON, không cần dựng provider.

**Ánh xạ lỗi:**

| Tình huống | `AppError` | Vì sao |
|---|---|---|
| Không có mạng, DNS fail, timeout | `Network` | Người dùng bật lại mạng là xong |
| 401 (key sai/thiếu) | `Network` + log `FTD_EVENT routing_auth_failed` | Người dùng không sửa được; log để dev thấy |
| 429 (hết quota phút) | `Network` | Phase-04 debounce đã giảm; UI báo "thử lại sau" |
| 400 / 501 (profile sai, điểm sai) | `Validation` | Lỗi lập trình hoặc toạ độ vô lý |
| 200 nhưng `paths` rỗng | `NotFound` | Không có đường đi — khác hẳn lỗi mạng, UI phải nói khác |
| 5xx | `Network` | Phía họ hỏng |

Phân biệt `NotFound` với `Network` là quan trọng: `NotFound` thì retry vô nghĩa, `Network` thì retry
có nghĩa. Gộp chung lại thì UI phải đoán, và nó sẽ đoán sai.

## Related Code Files

**Tạo mới**
- `data/src/main/java/.../data/remote/dto/GraphHopperDirectionsDto.kt`
- `data/src/main/java/.../data/routing/GraphHopperRoutingProvider.kt`
- `data/src/main/java/.../data/routing/GraphHopperDirectionsMapper.kt`
- `data/src/main/java/.../data/routing/RoutingErrorMapper.kt`
- `data/src/test/java/.../data/routing/GraphHopperDirectionsMapperTest.kt`
- `data/src/test/java/.../data/routing/GraphHopperRoutingProviderTest.kt` (MockWebServer)

**Đã có sẵn:** `data/src/test/resources/graphhopper-route-hanoi.json` — response THẬT lấy
2026-08-24, kèm `README.md` ghi lệnh `curl` và ngày. Không chứa API key (đã kiểm).

**Sửa**
- `data/src/main/java/.../data/di/DataModule.kt` — 2 `single` mới

**Không phải làm:** `local.properties` đã có `GRAPHHOPPER_API_KEY` thật (đã kiểm gọi được);
`local.properties.example` đã ghi sẵn nơi lấy key và giới hạn free tier.

## Implementation Steps

1. ~~**Lấy API key**~~ — **xong.** Key đã nằm trong `local.properties`, đã kiểm gọi thật ra 200.
2. ~~**Gọi tay một lần bằng `curl`**~~ — **xong.** Fixture thật đã lưu ở
   `data/src/test/resources/graphhopper-route-hanoi.json` (2026-08-24, `profile=car`, `locale=vi`,
   Hồ Gươm → Văn Miếu). Fixture là response **thật**, không phải JSON tự bịa — JSON bịa chỉ test
   được mapper chống lại trí tưởng tượng của người viết test. Muốn lấy lại: xem lệnh trong
   `data/src/test/resources/README.md`.
3. **DTO.** Chỉ khai field dùng, **kiểu lấy từ fixture chứ không từ trí nhớ** (bảng ở Key Insight #3):
   ```kotlin
   @Serializable
   data class GraphHopperDirectionsDto(
       val paths: List<PathDto> = emptyList(),
       val info: InfoDto? = null,
   )
   @Serializable
   data class PathDto(
       val distance: Double,                                    // mét
       val time: Long,                                          // MILI-giây
       val points: String,
       @SerialName("points_encoded") val pointsEncoded: Boolean = true,
       @SerialName("points_encoded_multiplier") val pointsEncodedMultiplier: Double = 100_000.0,
   )
   @Serializable
   data class InfoDto(val copyrights: List<String> = emptyList())
   ```
   `info` nullable + `copyrights` có default: response lỗi (401/400) không có `paths` lẫn `info`,
   và DTO không được là lý do khiến nhánh lỗi ném thay vì map thành `AppError`.
4. **Mapper.** `paths.firstOrNull()` → nếu null trả `AppError.NotFound`. Khẳng định
   `points_encoded == true`; nếu false thì trả `Validation` chứ **không** thử decode (Key Insight #1).
   Precision **suy ra từ `pointsEncodedMultiplier`**, không hardcode:
   `precision = log10(multiplier).roundToInt()`, và nếu `10^precision != multiplier` thì `Validation`.
   `durationSeconds = time / 1000`. `distanceMeters = distance`. `engineId = "graphhopper"`.
   `attribution = info?.copyrights?.takeIf { it.isNotEmpty() } ?: listOf("GraphHopper", "OpenStreetMap contributors")`.
5. **`RoutingErrorMapper`.** Hàm thuần theo bảng ở trên. Nhận `(code: Int, body: String?)`.
   Đọc field `message` của GraphHopper để đưa vào log, **không** đưa thẳng ra UI (tiếng Anh, kỹ thuật).
6. **Provider.** Dựng URL bằng `HttpUrl.Builder` (encode giúp, không tự nối chuỗi).
   `profile` là `private const val PROFILE = "car"` — một chỗ duy nhất, comment ghi đúng sự thật đã
   đo: free tier chỉ cho `[car, bike, foot]`, `motorcycle` trả 400 và **đòi nâng gói trả phí**
   (Key Insight #4). Đừng viết "TODO: đổi sang motorcycle" — nó không phải một TODO, nó là một hoá đơn.
7. **DI.**
   ```kotlin
   single<RoutingProvider>(named("graphhopper")) { GraphHopperRoutingProvider(get(), get(), get()) }
   single<RoutingProvider> {
       when (get<RoutingConfig>().engine) {
           RoutingEngine.GRAPHHOPPER -> get(named("graphhopper"))
           RoutingEngine.VALHALLA -> error("Valhalla chưa implement — phase-03")
       }
   }
   ```
   `when` trên enum **không có `else`**: thêm engine thứ ba là lỗi biên dịch tại đây, không phải
   một nhánh im lặng rơi về mặc định.
8. **Test mapper** trên fixture, assert bằng **số thật của fixture** chứ không bằng khoảng mơ hồ:
   69 điểm decode ra; điểm đầu ≈ (21.02850, 105.85387), cách toạ độ yêu cầu < 100m;
   `distanceMeters ≈ 3166.05`; `durationSeconds == 586` (**không phải 585990** — đây là test duy nhất
   chặn được lỗi chia 1000); `attribution == listOf("GraphHopper", "OpenStreetMap contributors")`.
   Thêm một case `points_encoded_multiplier` lạ (ví dụ `123.0`) → `Validation`.
9. **Test provider** bằng `MockWebServer`: 200 + fixture → `Success`; 401 → `Network`; 400 → `Validation`;
   200 với `"paths": []` → `NotFound`.
10. `./gradlew :data:test :app:testDebugUnitTest`.

## Todo List

- [x] ~~Lấy API key, đặt vào `local.properties`~~ — xong 2026-08-24, đã kiểm gọi ra 200
- [x] ~~`curl` một lần, lưu fixture JSON thật~~ — xong, kèm `README.md` ghi ngày và lệnh
- [x] ~~Hỏi profile `motorcycle` có trong free tier không~~ — **có câu trả lời: KHÔNG.**
      Free tier = `[car, bike, foot]`, kiểm bằng chính key của dự án
- [x] `GraphHopperDirectionsDto.kt` (`distance: Double`, `time: Long`, `+ points_encoded_multiplier`, `+ info.copyrights`)
- [x] `GraphHopperDirectionsMapper.kt` (chặn `points_encoded == false`)
- [x] `RoutingErrorMapper.kt`
- [x] `GraphHopperRoutingProvider.kt` (`HttpUrl.Builder`, profile 1 chỗ)
- [x] `DataModule` — `named("graphhopper")` + `when` exhaustive
- [x] `GraphHopperDirectionsMapperTest` trên fixture thật (assert `durationSeconds == 586`)
- [x] `GraphHopperRoutingProviderTest` với `mockwebserver3`, 4 case
- [x] Xác nhận không có log nào in URL chứa `key=`
- [ ] Hỏi GraphHopper về điều khoản **redistribution** (Chặn #2b ở plan.md — phần `motorcycle` đã đóng) — chặn PHÁT HÀNH, không chặn phase-02, vẫn mở

## Success Criteria

1. `:data:test` xanh, gồm 4 case MockWebServer.
2. Một lần gọi thật từ thiết bị trả về polyline > 2 điểm ở Hà Nội hoặc TP.HCM.
   (Đường mạng đã được xác nhận hoạt động từ máy dev; nếu thiết bị đỏ mà máy dev xanh thì lỗi nằm ở
   quyền `INTERNET` hoặc DNS của thiết bị, không phải ở key.)
3. `grep -rn "key=" data/src/main` không xuất hiện trong bất kỳ lời gọi `FtdLog` nào.
4. `KoinModulesTest.verify()` xanh.
5. Đổi `ROUTING_ENGINE=VALHALLA` trong `local.properties` → app **nổ lúc khởi động** với thông báo
   rõ ràng (đúng thiết kế cho tới hết phase-03), không phải im lặng dùng GraphHopper.

## Risk Assessment

| Rủi ro | Xác suất | Giảm thiểu |
|---|---|---|
| ~~`motorcycle` không có trong free tier~~ | **Đã xảy ra** | Xác nhận thật: free tier chỉ `[car, bike, foot]`. Dùng `car`. Muốn `motorcycle` thì hoặc trả phí GraphHopper, hoặc đổi `ROUTING_ENGINE=VALHALLA` (phase-03 có `motorcycle` miễn phí) — chính là giá trị mà cổng cắm-rút sinh ra để có |
| ToS "redistribution" cấm vẽ trên Google Map | Trung bình | Chặn #2b — hỏi trước khi ship. Nếu bị cấm, phase-03 đã có đường thoát sang Valhalla self-host, nơi không có bên thứ ba nào để cấm |
| 429 giữa buổi demo | Thấp | 500 credit/ngày, debounce 60s ở phase-04 |
| Fixture cũ đi so với API thật | Thấp | Ghi ngày lấy fixture vào tên/comment; test là ghim hợp đồng, không phải giám sát API |

## Security Considerations

- Key chỉ đi qua query param HTTPS (TLS mã hoá cả query string). Không log, không đưa vào crash report.
- Đặt **quota trần** ở console GraphHopper ngay khi tạo key — key nằm trong APK, ai giải nén cũng đọc được.
- `RoutingErrorMapper` không đưa `message` của server thẳng ra UI (rò rỉ chi tiết hạ tầng, và tiếng Anh).

## Next Steps

Phase 03 cắm Valhalla vào cùng cổng đó. Nếu phải sửa `:domain` hay `:ui` để làm được, cổng đã sai
và phải sửa ở đây trước.
