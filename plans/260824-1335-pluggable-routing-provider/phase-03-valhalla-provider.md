# Phase 03 — Valhalla provider (và bài kiểm tra thật của abstraction)

## Context Links

- [plan.md](plan.md) · [phase-01](phase-01-network-foundation-and-routing-port.md) · [phase-02](phase-02-graphhopper-provider.md)
- [researcher-01 — Valhalla](research/researcher-01-valhalla.md) (hosting, costing, response, precision 6)
- [VERIFICATION](research/VERIFICATION.md) — mục "PolyUtil.decode Precision Parameter"

## Overview

**Ưu tiên:** P2 · **Trạng thái:** ✅ Hoàn thành 2026-08-24 (trừ đăng ký Stadia + chạy thật 2 engine trên máy — theo chỉ dẫn, dành cho phase 05/06) — [dev-phase-03-report.md](reports/dev-phase-03-report.md)

Engine thứ hai. Giá trị chính **không phải** là có thêm một nhà cung cấp — mà là chứng minh cổng
`RoutingProvider` chịu được một API có hình dạng hoàn toàn khác: POST thay vì GET, `trip.legs[].shape`
thay vì `paths[].points`, **precision 6** thay vì 5, `length` tính bằng **km** thay vì mét.

**Luật nghiệm thu của phase này:** không được sửa một dòng nào trong `:domain` hay `:ui`. Nếu phải
sửa, cổng đã thiết kế sai và phải sửa cổng chứ không phải lách qua.

## Key Insights

**#1 — Precision 6. Đây là chỗ dễ mất nửa ngày nhất trong cả plan.** Valhalla mã hoá polyline ở
precision 6; Google ở precision 5. Decode sai precision không ném lỗi, không cảnh báo — nó trả về
một danh sách toạ độ **hợp lệ về kiểu** nhưng lệch 10 lần. Hà Nội (21.03, 105.85) thành (2.10, 10.58),
tức là ngoài khơi Sumatra. Polyline vẫn vẽ, chỉ là không ai thấy nó trên màn hình.
`PolylineDecoderTest` ở phase-01 đã ghim đúng cái bẫy này lại.

**`PolyUtil.decode()` KHÔNG dùng được cho Valhalla** — chữ ký thật là `decode(String)`, một tham
số, precision cứng ở 5. Không có overload nào nhận precision.

**#2 — `length` là kilomet, và CẢ HAI số đều là số thực.** Đo trên fixture thật
(`data/src/test/resources/valhalla-route-hanoi.json`, cùng cặp điểm với fixture GraphHopper):

| Field | Giá trị thật | Kiểu Kotlin phải khai | Bẫy |
|---|---|---|---|
| `trip.summary.length` | `3.768` | `Double` | **kilomet.** `distanceMeters = length * 1000` |
| `trip.summary.time` | `742.029` | **`Double`** | **giây, nhưng có phần thập phân** |
| `trip.status` | `0` | `Int` | 0 = OK |
| `trip.legs[].shape` | chuỗi 571 ký tự | `String` | precision 6 → 143 điểm |

**`time` phải khai `Double`, không phải `Int`/`Long`.** Valhalla trả `742.029`, và
`kotlinx.serialization` **ném `SerializationException` lúc parse** nếu DTO khai số nguyên — lỗi lúc
chạy, không phải lúc build, và nó xảy ra ở 100% response chứ không phải một trường hợp biên.
`durationSeconds` của `Directions` là `Long` → `time.roundToLong()` ở mapper, làm tròn một lần, ở
đúng chỗ mapper tồn tại để làm.

Ngược hẳn với GraphHopper, nơi `distance` đã là mét và `time` là mili-giây **số nguyên**. Hai
provider, hai đơn vị, hai kiểu số — đây chính là công việc mà mapper tồn tại để làm.

**#3 — Ba cách host, chọn bằng `VALHALLA_BASE_URL`, không phải bằng enum.** Quyết định #1 ở plan.md:
enum là *engine*, hosting là chi tiết bên trong provider.

| Hosting | Base URL | Key | Dùng khi |
|---|---|---|---|
| **Stadia Maps** | `https://api.stadiamaps.com/route/v1` | `api_key` query param | Mặc định khi bật Valhalla — 200k credit/tháng, ~10k request |
| **FOSSGIS** | `https://valhalla1.openstreetmap.de` | không cần | **Chỉ dev/thử.** Fair-use 1 req/user/giây; publish app dùng server này phải báo maintainer qua GitHub Discussions + gửi header `X-Client-Id` |
| **Self-host** | `http(s)://<host>:8002` | không | Khi ToS bên thứ ba là vấn đề — không còn nhà cung cấp nào để cấm gì |

**#4 — `motorcycle` của Valhalla là Beta — nhưng nó TỒN TẠI, và đó là điểm khác biệt thật giữa hai
engine.** GraphHopper free tier **không cho** `motorcycle` (phase-02 Key Insight #4, đã kiểm: trả
400 và đòi nâng gói). Valhalla có, ở cả FOSSGIS lẫn Stadia, không tính thêm tiền. Với một app theo
dõi gia đình ở Việt Nam — nơi xe máy là phương tiện mặc định — đây là lý do vận hành đầu tiên khiến
việc cắm-rút provider không chỉ là bài tập kiến trúc.

"Beta" vẫn chưa được giải thích là gì về mặt vận hành (VERIFICATION Gap #4). Mặc định vẫn `auto`;
`motorcycle` để sẵn nhưng tắt, bật sau khi thử thật trên đường Việt Nam và so với `auto`.

**#5 — Self-host cần HTTP cleartext.** Android 9+ (minSdk 28) chặn HTTP thuần. Nếu ai đó dựng
Valhalla Docker cục bộ, cần `network_security_config.xml` với `cleartextTrafficPermitted` **chỉ cho
đúng domain đó** — không bao giờ bật toàn cục.

## Requirements

**Chức năng**
1. `ValhallaRoutingProvider` implement cùng `RoutingProvider`, `named("valhalla")`.
2. `RoutingConfig.valhallaBaseUrl` + `stadiaApiKey` quyết định hosting; key rỗng → không gắn `api_key`.
3. `when` trong `DataModule` xử lý đủ cả hai nhánh, bỏ `error(...)` tạm của phase-02.

**Phi chức năng**
4. `:domain` và `:ui` **không có diff nào** trong phase này.
5. Test mapper trên fixture Valhalla thật, riêng biệt với fixture GraphHopper.

## Architecture

```
:data/remote/dto/ValhallaDirectionsDto.kt    trip { summary { time, length }, legs[] { shape } }
:data/routing/ValhallaRoutingProvider.kt     POST JSON body
:data/routing/ValhallaDirectionsMapper.kt    shape -> PolylineDecoder(precision = 6); length km -> m
                                             nối shape của NHIỀU leg lại (n điểm -> n-1 leg)
:data/routing/RoutingErrorMapper.kt          dùng lại của phase-02, thêm nhánh đọc `error_code`
```

`legs` là **mảng**. Với 2 điểm thì đúng 1 leg, nhưng mapper phải `flatMap` chứ không `first()` —
ngày nào thêm waypoint, `first()` sẽ im lặng vẽ mất một nửa tuyến đường.

**Format lỗi Valhalla khác GraphHopper:** `{"error_code": 171, "error": "No suitable edges near location"}`.
`RoutingErrorMapper` nhận thêm một tham số cho biết đang đọc format nào, hoặc tách hàm riêng cho
mỗi provider — chọn tách hàm, vì gộp lại thành một hàm biết cả hai format là đúng thứ mà cổng
này sinh ra để tránh.

## Related Code Files

**Tạo mới**
- `data/src/main/java/.../data/remote/dto/ValhallaDirectionsDto.kt`
- `data/src/main/java/.../data/routing/ValhallaRoutingProvider.kt`
- `data/src/main/java/.../data/routing/ValhallaDirectionsMapper.kt`
- `data/src/test/java/.../data/routing/ValhallaDirectionsMapperTest.kt`
- `data/src/test/java/.../data/routing/ValhallaRoutingProviderTest.kt`
- `data/src/test/resources/valhalla-route-hanoi.json`
- (chỉ khi self-host HTTP) `app/src/main/res/xml/network_security_config.xml`

**Sửa**
- `data/src/main/java/.../data/routing/RoutingErrorMapper.kt` — tách hàm theo provider
- `data/src/main/java/.../data/di/DataModule.kt` — `named("valhalla")`, bỏ `error(...)`
- `local.properties.example` — `STADIA_API_KEY`, `VALHALLA_BASE_URL`

## Implementation Steps

1. **Chọn hosting.** Mặc định Stadia Maps (đăng ký free tier, lấy key). FOSSGIS chỉ khi thử nhanh
   và **không** cho bản demo phát hành.
2. ~~**`curl` một lần, lưu fixture.**~~ — **xong.** Fixture thật đã ở
   `data/src/test/resources/valhalla-route-hanoi.json` (FOSSGIS, `costing=auto`,
   `units=kilometers`, 2026-08-24). Lệnh đầy đủ nằm trong `README.md` cùng thư mục.
3. **DTO.** Kiểu lấy từ fixture (bảng ở Key Insight #2), **không phải từ trí nhớ**:
   ```kotlin
   @Serializable data class ValhallaDirectionsDto(val trip: TripDto? = null)
   @Serializable data class TripDto(
       val summary: SummaryDto,
       val legs: List<LegDto> = emptyList(),
       val status: Int = 0,
       @SerialName("status_message") val statusMessage: String? = null,
   )
   @Serializable data class SummaryDto(val time: Double, val length: Double)  // giây (thực), km
   @Serializable data class LegDto(val shape: String)
   ```
   `trip` nullable: response lỗi của Valhalla là `{"error_code":..., "error":...}`, không có `trip`.
4. **Mapper.** `legs.flatMap { PolylineDecoder.decode(it.shape, precision = 6) }`;
   `distanceMeters = summary.length * 1000`; `durationSeconds = summary.time.roundToLong()`;
   `engineId = "valhalla"`.

   **`attribution`** — Valhalla **không** trả credit trong response (khác GraphHopper, phase-02 Key
   Insight #5), nên provider phải tự dựng theo host, và dựng cho đủ:
   - base URL chứa `stadiamaps.com` → `listOf("Stadia Maps", "OpenStreetMap contributors")`
   - còn lại (FOSSGIS, self-host) → `listOf("Valhalla", "OpenStreetMap contributors")`

   Bỏ tên nhà cung cấp hosting ra khỏi credit khi dùng Stadia là thiếu đúng thứ điều khoản của họ
   đòi. `OpenStreetMap contributors` thì không bao giờ được vắng ở bất kỳ nhánh nào.
   **Thêm một assert phòng thủ:** nếu điểm đầu cách toạ độ yêu cầu > 5km thì trả `Validation` kèm
   log — đó là chữ ký của lỗi sai precision, và nó phải nổ ở mapper chứ không phải trên bản đồ.
5. **Provider.** POST body JSON dựng bằng `kotlinx.serialization` (đừng nối chuỗi tay).
   `costing` là `private const val COSTING = "auto"` — một chỗ, có comment về Beta của `motorcycle`.
   Gắn `api_key` chỉ khi `stadiaApiKey` không rỗng. Nếu base URL là FOSSGIS, gửi header
   `X-Client-Id` (yêu cầu của maintainer).
6. **`RoutingErrorMapper`** — thêm `fun fromValhalla(code: Int, body: String?): AppError`, đọc
   `error_code`. 171 (no suitable edges) → `NotFound`, không phải `Network`.
7. **DI** — hoàn thiện `when`, bỏ `error(...)`.
8. **Test.** Cùng bộ 4 case như phase-02, cộng **một test then chốt**: decode fixture Valhalla ở
   precision 5 phải cho toạ độ **ngoài Việt Nam** — ghim lại rằng sai precision là sai nhìn thấy được.
9. **Chạy thật cả hai engine.** Build với `ROUTING_ENGINE=GRAPHHOPPER`, rồi `=VALHALLA`, cùng một
   cặp điểm.

   **Đừng kỳ vọng hai tuyến trùng nhau.** Đã đo thật trên cùng cặp điểm Hồ Gươm → Văn Miếu:

   | | GraphHopper | Valhalla | Chênh |
   |---|---|---|---|
   | Quãng đường | 3166 m | 3768 m | **19%** |
   | Thời gian | 586 s | 742 s | **27%** |
   | Điểm cuối lệch so với toạ độ yêu cầu | 66 m | 176 m | hai kiểu snap khác nhau |
   | Điểm cuối của engine này so với engine kia | — | — | **192 m** |

   Hai engine chọn đường khác nhau và snap vào cạnh đường khác nhau — đó là hành vi **đúng**, không
   phải lỗi. Cái phải kiểm là: cả hai tuyến đều **nằm trên đường phố Hà Nội**, cùng đi từ vùng xuất
   phát tới vùng đích, và không tuyến nào rơi ra biển (dấu hiệu sai precision).
10. `git diff --stat domain/ ui/` → **phải rỗng**.

## Todo List

- [ ] Đăng ký Stadia Maps free tier, lấy key — bỏ qua theo chỉ dẫn phase-03 (dành cho phase 05/06); nhánh Stadia vẫn implement + test bằng string, chỉ chưa chạy thật trên mạng
- [x] ~~`curl` Valhalla, lưu fixture thật~~ — xong 2026-08-24, FOSSGIS, kèm `README.md`
- [x] `ValhallaDirectionsDto.kt` (`time: Double` — khai số nguyên là crash lúc parse)
- [x] `ValhallaDirectionsMapper.kt` (precision 6, km→m, `roundToLong`, `flatMap` legs, assert phòng thủ 5km, `attribution` theo host)
- [x] `RoutingErrorMapper.fromValhalla` (171 → `NotFound`)
- [x] `ValhallaRoutingProvider.kt` (POST, `X-Client-Id` nếu FOSSGIS)
- [x] `DataModule` — `when` đủ hai nhánh
- [x] `ValhallaDirectionsMapperTest` + test "precision 5 ra ngoài Việt Nam" + assert `durationSeconds == 742`
- [x] `ValhallaRoutingProviderTest` MockWebServer
- [ ] Chạy thật cả hai engine, xác nhận cả hai polyline nằm trên phố (không so trùng khớp) — bỏ qua theo chỉ dẫn phase-03 (dành cho phase 05/06, device verification)
- [x] `git diff --stat domain/ ui/` rỗng — xác nhận, xem `reports/dev-phase-03-report.md`

## Success Criteria

1. `:data:test` xanh với cả hai bộ fixture.
2. Đổi `ROUTING_ENGINE` trong `local.properties` + rebuild → đổi engine, **không sửa code nào**.
3. Hai polyline từ hai engine cùng nằm trên đường phố Hà Nội và cùng nối được xuất phát → đích.
   **Không** yêu cầu chúng trùng nhau: đo thật cho thấy chênh 19% quãng đường, 27% thời gian, điểm
   cuối lệch 192m (bước 9). Một tiêu chí "chồng nhau trong vài chục mét" sẽ làm gate đỏ trên code đúng.
4. `git diff --stat domain/ ui/` rỗng — abstraction chịu được engine thứ hai.
5. Không có lời gọi `PolyUtil.decode` nào trên dữ liệu Valhalla ở bất kỳ đâu.

## Risk Assessment

| Rủi ro | Xác suất | Giảm thiểu |
|---|---|---|
| Decode sai precision, không ai phát hiện | Trung bình | Assert phòng thủ 5km ở mapper + test "ra ngoài Việt Nam". Hai lớp, vì đây là lỗi im lặng |
| FOSSGIS chặn vì fair-use | Trung bình | Chỉ dùng khi dev; Stadia cho bản demo |
| Stadia đổi endpoint/tính credit | Thấp | Base URL là config, không hardcode |
| Phải sửa `:domain` để Valhalla vừa | Thấp | Đó là tín hiệu thiết kế sai — sửa cổng, đừng lách. Ghi vào `LLM.md` §13 nếu buộc phải lệch |

## Security Considerations

- `STADIA_API_KEY` cùng chế độ như `GRAPHHOPPER_API_KEY`: `local.properties`, quota trần, không log.
- **`cleartextTrafficPermitted` chỉ cho đúng domain self-host**, không bao giờ toàn cục. Bật toàn cục
  là mở lại cửa cho mọi lời gọi HTTP thuần trong app, kể cả những lời gọi chưa tồn tại hôm nay.
- FOSSGIS là hạ tầng tình nguyện — gửi `X-Client-Id`, tôn trọng 1 req/giây, và đừng để nó lọt vào
  bản build phát hành.

## Next Steps

Phase 04 dựng logic reroute ở `:domain` — nó là thứ quyết định *khi nào* gọi provider, và nó phải
thuần để test được mà không cần một quả GPS nào.
