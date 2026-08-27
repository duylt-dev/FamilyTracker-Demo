# dev-phase-03-report — Valhalla provider

**Ngày:** 2026-08-24 · **Phase:** phase-03-valhalla-provider.md · **Trạng thái:** ✅ completed
(trừ 2 mục ngoài phạm vi theo chỉ dẫn: đăng ký Stadia, chạy thật 2 engine trên máy)

## Files tạo mới

- `data/src/main/java/.../data/remote/dto/ValhallaDirectionsDto.kt` (41 dòng) — `ValhallaDirectionsDto`/`TripDto`/`SummaryDto`/`LegDto`
- `data/src/main/java/.../data/routing/ValhallaDirectionsMapper.kt` (104 dòng)
- `data/src/main/java/.../data/routing/ValhallaRoutingProvider.kt` (146 dòng)
- `data/src/test/java/.../data/routing/ValhallaDirectionsMapperTest.kt` (123 dòng, 6 test)
- `data/src/test/java/.../data/routing/ValhallaRoutingProviderTest.kt` (175 dòng, 9 test — MockWebServer + pure host-branch asserts)

Fixture (`data/src/test/resources/valhalla-route-hanoi.json`) và `README.md` đã có sẵn từ trước
khi bắt đầu phase — không đụng vào.

## Files sửa

- `data/src/main/java/.../data/di/DataModule.kt` — thêm `single<RoutingProvider>(named("valhalla"))`,
  bỏ nhánh `error("Valhalla chưa implement — phase-03")`, `when` giờ resolves cả 2 nhánh, vẫn không `else`.
- `data/src/main/java/.../data/routing/RoutingErrorMapper.kt` — đổi tên `map()` → `fromGraphHopper()`
  (không đổi hành vi), thêm `fromValhalla(code, body)` đọc `error_code`/`error` của Valhalla,
  `error_code 171` → `NotFound`. Sửa lại KDoc: xoá câu cũ nói "phase-03 nên extend `extractMessage`,
  không fork file này" (mâu thuẫn với chính phase-03 spec), thay bằng lý do thật của việc tách hàm.
- `data/src/main/java/.../data/routing/GraphHopperRoutingProvider.kt` — 1 dòng, đổi call site
  `RoutingErrorMapper.map(...)` → `RoutingErrorMapper.fromGraphHopper(...)`.
- `data/src/main/java/.../data/remote/RoutingHttpClient.kt` — `postJson()` thêm tham số
  `headers: Map<String, String> = emptyMap()` (mặc định rỗng, không đổi hành vi cũ của GraphHopper —
  provider đó chỉ gọi `get()`). Cần cho `X-Client-Id` mà FOSSGIS đòi; interface phase-01 để lại
  không có chỗ gắn header nào, và đây là thay đổi tối thiểu, tương thích ngược duy nhất tìm được.

**Không sửa:** `:domain`, `:ui`, `app/src/test/.../KoinModulesTest.kt`, fixture JSON, `RoutingHttpClientTest.kt`,
`local.properties.example` (đã đủ từ phase-01, không cần sửa thêm).

## Quyết định kỹ thuật đáng chú ý

1. **`RoutingHttpClient.postJson` thêm tham số `headers`.** File này thuộc phase-01, không nằm
   trong "Related Code Files" của phase-03, nhưng interface để lại (`postJson(url, body)`) không có
   chỗ nào gắn `X-Client-Id` — bắt buộc phải mở rộng nó. Tham số có default rỗng nên tương thích
   ngược 100%; `RoutingHttpClientTest.kt` không cần sửa, 3/3 test cũ vẫn xanh nguyên.

2. **`attribution()`/`requestUrl()`/`requestHeaders()` khai `internal`, không `private`.** Test host
   Stadia (`stadiamaps.com`) không thể trỏ `MockWebServer` vào đúng domain đó (DNS thật). Ba hàm
   thuần này không có I/O — expose `internal` để test trực tiếp bằng assert chuỗi, không cần mạng,
   đúng mẫu `tickOnce()`/`internal` đã dùng ở nơi khác trong repo (LLM.md §11).

3. **URL Valhalla: base URL trần (FOSSGIS/self-host) → nối thêm `/route`; base URL đã có
   `/route/v1` (Stadia, theo `local.properties.example`) → dùng nguyên.** `local.properties.example`
   liệt kê 3 dạng base URL không đồng hình dạng (Stadia đã có sẵn path, FOSSGIS/self-host là host
   trần) — nối `/route` một cách đồng nhất vào cả 3 sẽ làm sai URL Stadia. Nhánh theo host
   (`baseUrl.contains("stadiamaps.com")`) là cách duy nhất khớp đúng cả giá trị hiện có
   (`https://valhalla1.openstreetmap.de`, xác nhận đúng bằng lệnh `curl` thật trong
   `data/src/test/resources/README.md`) lẫn giá trị Stadia trong example. Ghi rõ trong KDoc vì đây
   là một quyết định không hiển nhiên từ chính phase file.

4. **`haversineMeters` viết lại trong `ValhallaDirectionsMapper.kt`, không tái dùng `GeoDistance`.**
   `domain/tracking/GeoDistance.kt` là `internal` của `:domain` — `internal` của Kotlin có phạm vi
   MODULE, `:data` là module Gradle khác nên không thấy được dù cùng kiểu import path. Mở
   `GeoDistance` thành `public` sẽ là một sửa đổi `:domain`, vi phạm thẳng luật nghiệm thu của
   chính phase này. ~10 dòng công thức Haversine (cùng hằng số bán kính Trái Đất) được chép lại,
   có ghi chú giải thích tại sao không tái dùng.

5. **`X-Client-Id` giá trị:** `com.example.pion.family.tracker.demo` (package id của app) —
   không có giá trị cụ thể nào được nghiên cứu/plan chỉ định; chọn package id vì đó là cách nhận
   diện app rõ ràng nhất, không phải đoán. Không đăng ký gì với FOSSGIS maintainers (đúng chỉ dẫn —
   việc đó thuộc về bước publish, `docs/routing-and-map-attribution.md` §5).

## Số thật đo được (khớp VERIFY-2026-08-24.md + phase file)

| Field | Giá trị | Assert trong test |
|---|---|---|
| `trip.legs[0].shape` decode precision 6 | 143 điểm, đầu `(21.028833, 105.854165)` | `ValhallaDirectionsMapperTest` |
| `trip.summary.length` | `3.768` km → `distanceMeters = 3768.0` | ✅ |
| `trip.summary.time` | `742.029` → `durationSeconds = 742L` | ✅ |
| Decode CÙNG shape ở precision 5 (sai) | đầu `(210.28833, 1058.54165)` — vô nghĩa về mặt toạ độ, chắc chắn ngoài Việt Nam | test "precision 5 ra ngoài VN" — số đo thật bằng Python trước khi viết assert, không đoán |

Số precision-5 đo thật KHÁC câu chuyện minh hoạ trong phase file (Hà Nội → "ngoài khơi Sumatra",
ngụ ý chia 10) — số thật là NHÂN 10 (`210.29`, `1058.54`), vì hướng lỗi ở đây là decode chuỗi được
encode ở precision 6 bằng factor precision 5 (nhỏ hơn). Test viết theo số đo thật (`!in 8.0..24.0`
hoặc `!in 102.0..110.0`), không theo con số minh hoạ trong prose — kết quả vẫn đúng ý đồ nghiệm thu
("ra ngoài Việt Nam", và ở đây còn rõ hơn: ra ngoài toạ độ hợp lệ trên Trái Đất).

## Gradle commands chạy và kết quả

| Lệnh | Kết quả |
|---|---|
| `./gradlew :data:compileDebugKotlin :data:compileDebugUnitTestKotlin` | ✅ BUILD SUCCESSFUL (1 lỗi biên dịch ban đầu — `RecordedRequest.body` là `ByteString?` chứ không phải non-null như `javap` gợi ý; sửa bằng `?.` — xem log) |
| `./gradlew build` | ✅ BUILD SUCCESSFUL in 17s |
| `./gradlew clean && ./gradlew build --no-build-cache` | ✅ BUILD SUCCESSFUL in 48s (fresh, không cache) |
| `git diff --stat domain/ ui/` | **RỖNG** — xác nhận bằng `echo "(exit code $?)"` = 0, không có dòng output nào |
| `grep -rn "PolyUtil" data/src` | chỉ khớp trong comment giải thích ("đừng dùng") và `HistoryPipelineScaleTest.kt` (không liên quan Valhalla, dùng cho GraphHopper/History polyline simplify — có từ trước) — **không có lời gọi `PolyUtil.decode` nào trên dữ liệu Valhalla** |
| `grep -rn "RoutingErrorMapper\.map("` | rỗng — không còn call site nào dùng tên cũ |

### Test summary (sau `clean` + `--no-build-cache`)

| Class | tests | failures | errors |
|---|---|---|---|
| `data.remote.RoutingHttpClientTest` (phase-01, không đổi hành vi) | 3 | 0 | 0 |
| `data.routing.GraphHopperDirectionsMapperTest` (phase-02, không đổi) | 5 | 0 | 0 |
| `data.routing.GraphHopperRoutingProviderTest` (phase-02, không đổi) | 4 | 0 | 0 |
| `data.routing.ValhallaDirectionsMapperTest` (mới) | 6 | 0 | 0 |
| `data.routing.ValhallaRoutingProviderTest` (mới) | 9 | 0 | 0 |
| `:data` toàn bộ (mọi package, không chỉ routing) | 34 | 0 | 0 |
| `app.KoinModulesTest` (`verify()`, cả 2 engine binding) | 1 | 0 | 0 |
| `:domain` toàn bộ (không đổi) | 69 | 0 | 0 |
| `:ui` toàn bộ (không đổi) | 71 | 0 | 0 |

`ValhallaRoutingProviderTest` gồm: 200+fixture → Success (assert POST, path `/route`, body chứa
`"costing":"auto"`); `error_code 171` → NotFound; `error_code` khác + 400 → Validation; 500 →
Network; attribution đúng theo host (Stadia vs. FOSSGIS/self-host); `X-Client-Id` CHỈ gửi cho
FOSSGIS; `api_key` CHỈ gắn khi `stadiaApiKey` không rỗng; URL nối `/route` đúng cho host trần, giữ
nguyên cho Stadia.

## Đối chiếu Success Criteria (phase file)

1. ✅ `:data:test` xanh với cả hai bộ fixture (GraphHopper 9 test không đổi + Valhalla 15 test mới).
2. ✅ Đổi `ROUTING_ENGINE` trong `local.properties` + rebuild → đổi engine, không sửa code nào —
   xác nhận tĩnh: `DataModule`'s `when` đã exhaustive cho cả `GRAPHHOPPER`/`VALHALLA`, không còn
   `error(...)`. **Không** tự đổi `local.properties` (giữ nguyên `ROUTING_ENGINE=GRAPHHOPPER` như
   người dùng đã đặt) — đổi engine + build thật là việc của phase 05/06 theo chỉ dẫn.
3. ⬜ Chưa làm — theo đúng chỉ dẫn ("Skip Implementation Step 9's real two-engine device run").
4. ✅ **`git diff --stat domain/ ui/` RỖNG** — xác nhận trực tiếp, không suy diễn.
5. ✅ `grep -rn "PolyUtil" data/src` — không có lời gọi `PolyUtil.decode` nào chạm dữ liệu Valhalla.

## Vì sao không cần sửa `KoinModulesTest.extraTypes`

Cùng lý do phase-02 đã ghi lại (`dev-phase-02-report.md`): `single<RoutingProvider>(named("valhalla")) { ValhallaRoutingProvider(get(), get(), get()) }`
khai `<RoutingProvider>` tường minh nên `verify()`'s `primaryType` là interface `RoutingProvider`
(không có constructor để soi), và tham số test-only `baseUrl: String` đã nằm sẵn trong
`Verify.primitiveTypes` whitelist. Xác nhận lại bằng chính `KoinModulesTest` xanh (1/1) sau khi thêm
binding mới, không đoán.

## Đối chiếu KDoc sai của `RoutingErrorMapper` (yêu cầu bắt buộc sửa của đề bài)

KDoc cũ (phase-02) viết: *"Valhalla (phase-03) may return a different shape... a phase-03 provider
that finds a different body shape extends `extractMessage`, it does not fork this file."* — đúng
NGƯỢC với quyết định thật của phase-03 spec ("chọn tách hàm, vì gộp lại thành một hàm biết cả hai
format là đúng thứ mà cổng này sinh ra để tránh"). Đã xoá câu đó, thay bằng đoạn giải thích lý do
tách `fromGraphHopper`/`fromValhalla` thành hai hàm độc lập, không hàm nào gọi hàm kia.

## `local.properties` / hosting — không đổi

Đúng theo chỉ dẫn: `ROUTING_ENGINE=GRAPHHOPPER`, `VALHALLA_BASE_URL=https://valhalla1.openstreetmap.de`
(FOSSGIS), `STADIA_API_KEY` rỗng — giữ nguyên, không đăng ký Stadia, không tự đổi engine đang chạy.
Nhánh Stadia (attribution, `api_key`, không gửi `X-Client-Id`) được implement đầy đủ và test bằng
string/host-branch assert (`ValhallaRoutingProviderTest`), chỉ chưa từng chạm mạng thật — đúng ghi
chú "Phase 06 records this in LLM.md §13 Open" của đề bài.

## LLM.md §3 — chưa cập nhật, có chủ ý

`LLM.md` §3's cây package `:data` chưa có mục `remote/` hay `routing/` — kiểm bằng `grep -n
"remote/\|routing/" LLM.md` ra rỗng, kể cả trước khi phase-03 bắt đầu (phase-01/02 cũng chưa cập
nhật). Không tự thêm trong phase này: phạm vi phase-03 (đề bài) không liệt kê `LLM.md` trong
"Related Code Files", và plan có hẳn `phase-06-quality-gates-and-docs.md` cho việc này — sửa lệch
phạm vi file ownership của phase khác là rủi ro lớn hơn một dòng doc thiếu tạm thời. Gắn cờ ở đây để
phase-06 không bỏ sót.

## Issues / deviations

- `RoutingHttpClient.postJson` mở rộng thêm tham số (mục "Quyết định kỹ thuật" #1) — file không nằm
  trong "Related Code Files" của phase-03 nhưng bắt buộc phải sửa để có chỗ gắn `X-Client-Id`. Thay
  đổi tối thiểu, tương thích ngược, có test cũ xác nhận không hồi quy.
- `LLM.md` §3 chưa cập nhật cho `remote/`/`routing/` — có chủ ý, xem mục ngay trên. Không phải thiếu sót quên.

## Unresolved questions

- Giá trị chuỗi `X-Client-Id` (`com.example.pion.family.tracker.demo`) là lựa chọn hợp lý nhưng
  chưa được FOSSGIS maintainers xác nhận — publish thật vẫn cần báo qua GitHub Discussions trước
  (đã ghi ở `docs/routing-and-map-attribution.md` §5, không phải việc của phase-03).
