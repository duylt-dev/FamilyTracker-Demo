# Simplifier — Phase 04 (nguồn tuyến 3 tầng)

**Phạm vi:** chỉ code vừa viết ở phase 04. Không chạm `:ui`, `LLM.md`, `docs/`, `plans/` (trừ chính
file này). Không commit, không `git add`.

**Kết quả gate:** `./gradlew :domain:test :data:test :app:test --no-configuration-cache --rerun-tasks`
→ BUILD SUCCESSFUL, **`:domain` 125 / `:data` 56 / `:app` 1**, 0 failure — đúng bằng số test trước
khi tôi sửa. Không ca nào bị bỏ, gộp hay đổi tên. Không cảnh báo Kotlin mới.

**Impact analysis (bắt buộc theo CLAUDE.md), chạy trước khi sửa:**
`impact pathFor` → 3 impacted / 1 direct, **LOW** · `impact MemberRouteSource.path` → 3 / 1, **LOW** ·
`impact MemberRouteSource` → 2 / 1, **LOW** (chỉ `DataModule.kt` + test). Không có HIGH/CRITICAL.
`detect-changes` sau khi sửa: các execution flow bị chạm đúng là `MoveOne …` và `PathFor …` — không
có flow nào ngoài phạm vi phase 04.

| File | Dòng trước | Dòng sau |
|---|---|---|
| `data/location/MemberMovementSimulator.kt` | 199 | **199** |
| `data/routing/MemberRouteSource.kt` | 140 | 153 |
| `data/routing/MemberRouteSourceTest.kt` | 245 | 233 |
| `data/routing/OnDevicePolylineCacheTest.kt` | 98 | 96 |
| `data/location/MemberMovementSimulatorTest.kt` | 319 | 329 |

Tổng +9 dòng. Mục tiêu không phải ít dòng hơn mà là ít thứ để đọc nhầm hơn.

---

## 1. Đã làm

### A. `data/routing/MemberRouteSource.kt`

1. **Gộp cặp `guard + log GEOMETRY` bị chép hai lần** (tầng cache và tầng provider) thành
   `private fun passesGeometryGuard(points, request)`. **Thứ tự tầng KHÔNG đổi:** hai khối vẫn nằm
   nguyên chỗ cũ, `path()` vẫn đọc từ trên xuống là cache → provider → synthetic; helper chỉ nhận
   phần vị-từ + đúng một dòng log. Lý do làm: chuỗi `sim_route_failed reason=GEOMETRY` là hợp đồng
   với QA §3 — chép hai bản thì một lần sửa hụt sẽ không ai thấy trên màn hình. Helper đi cùng khuôn
   sẵn có của file (`fromProvider` cũng "trả null/false + log đúng một dòng"), không phải khái niệm mới.
2. **`fromProvider` khai báo kiểu trả về tường minh `: Directions?`** và tách lời gọi
   `withTimeoutOrNull` ra dòng riêng. Trước đó kiểu trả về phải suy ra từ `AppResult.Success.data`
   nằm giữa thân `when` dài 130 ký tự.

Không đổi: thứ tự tầng, khoá cache, nội dung mọi dòng log (`sim_route_loaded`, `reason=TIMEOUT`,
`reason=GEOMETRY`, `reasonFor(...)`), `PROVIDER_TIMEOUT`, `publish`, `cacheKeyFor`.

### B. `data/location/MemberMovementSimulator.kt` (199 → 199)

1. **Sửa một lỗi tài liệu thật:** KDoc lớp ghi thứ tự tầng là *"nhà cung cấp mạng → cache →
   SyntheticPath"* — **ngược** với thứ tự thật (cache → provider → synthetic) mà chính KDoc của
   `MemberRouteSource` in đậm khẳng định, và ngược với `MemberRouteProvider`. Đây đúng là thứ tự vừa
   bị làm sai và đã phải sửa lại; để câu sai nằm lại trong KDoc là mồi cho lần sai kế tiếp. Thay
   bằng câu nói đúng vai của lớp này: **nó không biết tầng nào cấp dãy điểm, đó là việc riêng của
   `MemberRouteSource`.**
2. **Bỏ trùng lặp tài liệu:** luật rẽ nhánh `WANDER` / `ENTER_ZONE`-`LEAVE_ZONE` trước đó được viết
   ở CẢ KDoc lớp lẫn KDoc `pathFor`, cách nhau 90 dòng. Giữ ở `pathFor` — nơi có nhánh thật — và
   đưa luôn lý do NFR-2 (hạn ngạch) về đó.
3. **Trả lại một "vì sao" bị phase-04 xoá mất:** KDoc `pathFor` cũ (phase-02) giải thích `seed =
   member.id.hashCode()` cùng nguồn với `randomFor` nên tái hiện được; bản phase-04 xoá câu đó mà
   không thay bằng gì, trong khi dòng code vẫn còn. Ghép lại vào KDoc mới, **0 dòng thêm**.
4. `val memberSeed = member.id.hashCode()` (dùng đúng một lần) → truyền thẳng bằng đối số có tên
   `memberSeed = member.id.hashCode()`. Tên biến không mất, một dòng bớt.

### C. `data/routing/MemberRouteSourceTest.kt` (245 → 233, vẫn 7 `@Test`)

1. `File.createTempFile("routes", "").apply { delete(); mkdirs() }` → **`Files.createTempDirectory("routes").toFile()`**.
   Cùng kết quả, một dòng, và không còn cái mẹo "tạo một FILE rồi xoá đi để mượn chỗ làm THƯ MỤC" —
   đúng loại phải đọc hai lần. Giữ `@Before`/`@After` thủ công, đúng khuôn `GraphHopperRoutingProviderTest`
   nằm cùng thư mục.
2. **Đặt tên hai mốc hình học:** `zoneCenter` và `northOfZone` thay cho biểu thức
   `zone.latitude + 300.0 / METERS_PER_DEGREE_LAT` rải ở 5 chỗ. `goodEnterRoute()`/`goodLeaveRoute()`
   từ 4 dòng còn 1 dòng và giờ nhìn ra ngay là hai chiều ngược nhau của cùng một chặng; cặp
   `enterRequest()`/`leaveRequest()` cũng vậy.
3. **Bỏ `zoneOf(...)`** — builder 4 tham số dùng đúng một lần; `zone` khai báo thẳng, bớt một cú nhảy.
4. **Bỏ 3 lần truyền `attribution = listOf("GraphHopper", "OpenStreetMap contributors")`** y hệt giá
   trị mặc định của `directionsOf`. Ca `Valhalla` vẫn truyền tường minh vì nó KHÁC mặc định — giờ chỗ
   nào truyền attribution là chỗ đó có ý.
5. **Xoá hẳn fake thứ hai `ToggleRoutingProvider`.** `FakeRoutingProvider` nay đưa **số thứ tự lời
   gọi** vào lambda (`{ call -> … }`), nên ca "fetch được đúng một lần rồi mới hỏng" (S2) tả ngay tại
   chỗ nó xảy ra. 5 lambda còn lại không đổi một chữ (`it` ngầm không dùng vẫn biên dịch). Một khái
   niệm ít đi, hành vi y hệt.

### D. `data/routing/OnDevicePolylineCacheTest.kt` (98 → 96, vẫn 5 `@Test`)

1. Cùng `Files.createTempDirectory`.
2. `Json { ignoreUnknownKeys = true }` dựng hai lần → một `private val json`. Ca "fresh instance" vẫn
   dựng **instance cache mới** (đó là thứ nó chứng minh), chỉ dùng chung `Json`.
3. Bỏ `routesDir.mkdirs()` thừa trong ca file hỏng — `setUp()` đã tạo thư mục rồi.

### E. `data/location/MemberMovementSimulatorTest.kt` (319 → 329, vẫn 9 `@Test`)

`private fun simulatorWith(members, zones = …, events = …, routes = …)` với mặc định là fake trung
tính. 9 chỗ dựng simulator từ ~120–140 ký tự còn ~55: mỗi ca giờ chỉ nêu **thứ khác nhau của nó**
(`simulatorWith(members)` / `…(members, events = events)` / `…(members, events = events, routes = degradedProvider)`).
Đây là chỗ phase-04 làm xấu nhất — thêm tham số thứ tư vào 8 dòng có sẵn khiến mọi ca phải viết lại
cả bốn fake. Đổi 10 dòng thân helper lấy 9 dòng đọc được; đúng thứ đáng đổi trong file test.

---

## 2. Đã cân nhắc và quyết định **KHÔNG** làm

1. **Gộp 3 tầng của `path()` thành một danh sách/vòng lặp** (`listOf(::fromCache, ::fromProvider, …).firstNotNullOf`).
   **KHÔNG.** Thứ tự tầng là thứ vừa bị làm sai và đã phải sửa lại; vòng lặp biến thứ tự thành *dữ
   liệu*, mất tính "đọc từ trên xuống là thấy". Ba tầng cũng không đồng dạng: tầng provider còn
   `cache.put`, tầng synthetic không có nhánh trượt guard. Helper tôi làm cố ý chỉ rút phần vị-từ + log.
2. **Thêm nhãn `// Tầng 1 / 2 / 3` vào `path()`.** KHÔNG: `cache.get` / `fromProvider` /
   `SyntheticPath.between` đã tự nói, và KDoc lớp đã có một đoạn in đậm riêng cho thứ tự. Thêm nhãn
   là thêm một chỗ nữa để trôi khỏi code.
3. **Tách `raiseZoneEvents` khỏi `MemberMovementSimulator`** (xuống ~175 dòng). KHÔNG: đổi một file
   199 dòng lấy hai file + một chỗ nối, trong khi `LLM.md` §8.1 muốn thấy "di chuyển → sinh
   `ZoneEvent`" ở CÙNG một chỗ vì đây là nơi DUY NHẤT sinh `ZoneEvent` trong app. Đã kiểm: không có
   chỗ thứ hai nào dựng `ZoneEvent` từ crossing (chỉ còn `ZoneEventMapper` từ entity), nên cũng không
   có DRY nào để hưởng. Rút dòng bằng cách bẻ mạch nhân quả là đổi xấu.
4. **Đối số có tên cho `MemberRouteRequest(member.id, from, to, zone, step.target.kind)`.** KHÔNG:
   `from`/`to` là hai local khai báo ngay hai dòng trên, cùng tên và cùng thứ tự với tham số — đọc đã
   rõ; viết tên đầy đủ phải xuống dòng, +2 dòng cho một file đang ở 199.
5. **`@get:Rule TemporaryFolder`** cho hai test routing (giải pháp "chuẩn JUnit" cho thư mục tạm).
   KHÔNG: nó sẽ là JUnit `Rule` **duy nhất của cả repo**, trong khi hai test ngay cùng thư mục
   (`GraphHopperRoutingProviderTest`, `ValhallaRoutingProviderTest`) dựng/dọn tài nguyên bằng
   `@Before`/`@After`. Đổi khuôn test là đổi *quy ước*, kéo theo nghĩa vụ cập nhật `LLM.md` §11 mà
   tôi không được phép chạm. `Files.createTempDirectory` giải quyết đúng phần khó đọc mà không đổi
   quy ước nào.
6. **Tách helper dùng chung (thư mục tạm / `Zone` / `Directions`) cho hai test routing.** KHÔNG — và
   một nửa tiền đề không đúng: `OnDevicePolylineCacheTest` **không dựng `Zone` hay `Directions` nào**
   (nó chỉ có hằng `KEY`), nên phần trùng thật chỉ còn **2 dòng** thư mục tạm. Một file dùng chung cho
   2 dòng sẽ phá tính "mỗi file test tự đứng được, fake viết ngay trong file" mà `LLM.md` §11 dựa vào.
7. **Ghép `S2` với `NFR-2 quota`** (hai ca cùng chứng minh cache thắng). KHÔNG: luật cứng "không bỏ
   ca test nào", và hai ca khẳng định hai thứ khác nhau — S2 khẳng định **giá trị trả về** là tuyến
   đã cache, NFR-2 khẳng định **số lần gọi provider = 1**.
8. **Bỏ `engineId` khỏi `CachedRoute`/`CachedRouteDto`** (đọc lên rồi không ai dùng ngoài test).
   KHÔNG: đổi hình dạng file cache là đổi hành vi (và kéo theo `schemaVersion`), ngoài phạm vi "giữ
   nguyên 100% hành vi"; `engineId` là dữ liệu xuất xứ đi kèm attribution, đúng tinh thần
   `routing-and-map-attribution.md`.
9. **Bỏ `runCatching { file.delete() }` lồng trong `getOrElse`** ở `OnDevicePolylineCache.get`.
   KHÔNG: `delete()` có thể ném `SecurityException`, và KDoc đã ghi rõ ca "quyền đọc bị thu hồi". Rút
   1 dòng đổi lấy một đường crash hiếm là đổi xấu.
10. **Rút gọn comment 12 dòng ở `DataModule.kt`** giải thích vì sao phải `binds(arrayOf(...))` chứ
    không `bind … bind …`. KHÔNG: đó là ghi chép một lần thử **thất bại có thật**
    (`Argument type mismatch`, hiệp biến trên `S`) — đúng loại "vì sao" mà luật dự án bảo giữ. Người
    sau không đọc nó sẽ mất đúng chừng ấy thời gian để phát hiện lại.
11. **Rút gọn KDoc 9 dòng về `File::class` ở `KoinModulesTest.kt`.** KHÔNG: nó trả lời "vì sao phải
    khai `File` là `extraTypes`", câu hỏi người sau chắc chắn sẽ hỏi khi thấy một `java.io.File` nằm
    cạnh `Context`/`SavedStateHandle`.
12. **`RouteGeometryGuard.kt`** — không đụng. Đoạn KDoc phase-04 mới thêm (vì sao bỏ `internal`) là
    đúng và cần; phần thân đã mang cảnh báo "đừng gọn hoá dòng này, suite vẫn xanh khi xoá mà bất biến
    thì thủng" từ review phase-02 — tôi tôn trọng nguyên văn.
13. **3 file `:domain` mới + `CachedRouteDto.kt` + `OnDevicePolylineCache.kt` (main)** — đọc kỹ,
    **không sửa gì**. Đúng chỗ theo §12, mỗi file 15–88 dòng, KDoc toàn là "vì sao" chứ không phải
    "cái gì". Không có gì rút được mà không mất nghĩa.

---

## 3. `MemberMovementSimulator.kt` — vì sao vẫn 199 dòng

Cấu tạo: **29 dòng import + 30 dòng KDoc lớp + ~17 dòng KDoc/comment trong thân + ~123 dòng code**.

- **KDoc lớp là 7 đoạn, mỗi đoạn một "vì sao" khác nhau** (vì sao phải mô phỏng; vì sao điểm không đi
  qua `LocationFilter`; vì sao `insideZoneIds` tách theo từng người; vì sao `pathFor` là seam; vì sao
  cấm đo hình học bằng API Android). Sau khi tôi bỏ phần trùng với KDoc `pathFor`, **không đoạn nào
  còn lặp đoạn nào**. Cắt thêm bất kỳ đoạn nào là xoá một bài học đã trả giá bằng bug thật.
- **29 dòng import** không rút được nếu không dùng wildcard — cả repo không dùng chỗ nào.
- Chỗ "to" duy nhất còn lại trong thân là `raiseZoneEvents` (24 dòng, gồm 3 dòng comment giải thích
  phép `intersect liveZoneIds`); tách nó ra là mục #3 ở trên, đã từ chối.
- Hai thứ tôi làm được (bỏ `memberSeed`, gọn đoạn phase-04 của KDoc lớp) cho **−2 dòng**, và tôi tiêu
  **đúng 2 dòng đó** để (a) trả lại lý do của `seed` bị phase-04 xoá, (b) ghi lý do NFR-2 vào đúng chỗ
  có nhánh. Đổi 2 dòng lấy 2 mẩu lý do.

**Kết luận: không rút được thêm mà không mất thông tin.** File đang ở 199/200 nhưng "gần trần" ở đây
không phải nợ kỹ thuật — hai phần ba độ dài là import và lý do thiết kế. Nếu phase sau cần **thêm
code** vào lớp này, đường đi đúng là tách theo `LLM.md` §5 (một file cộng tác có tên riêng, ví dụ
tách phần sinh `ZoneEvent`), **không phải cắt KDoc để lấy chỗ**. Tôi không tách trước vì hôm nay chưa
cần (YAGNI) và vì nó bẻ mạch "di chuyển → sự kiện" mà §8.1 muốn thấy liền một mạch.

---

## 4. Việc cho lead (ngoài quyền của tôi)

- **`LLM.md` §11 (Bố cục test) chưa có dòng nào cho hai file test mới** `MemberRouteSourceTest` và
  `OnDevicePolylineCacheTest` — hàng "Mapper routing" hiện có nói về `mockwebserver3` + fixture, không
  mô tả hai file này (JVM thuần, fake viết tay, thư mục tạm thật). Phase-04 có sửa §3/§8.1/§13 nhưng
  bỏ sót §11. Tôi không được chạm `LLM.md` nên chỉ báo lại.
- Không phát hiện sai lệch hành vi nào khác trong phạm vi phase 04.
