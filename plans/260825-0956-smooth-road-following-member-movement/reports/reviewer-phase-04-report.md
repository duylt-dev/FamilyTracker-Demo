# Reviewer — Phase 04 (nguồn tuyến 3 tầng)

**Ngày:** 2026-08-26 · **Phạm vi:** diff `git status --short` (bỏ `plans/`, `docs/` khi soát code;
vẫn đối chiếu chúng ở §5) · **KHÔNG chạy `adb`**, không sửa code sản phẩm, không commit.

---

## KẾT LUẬN: **KHÔNG ĐÓNG**

Ba lý do, theo thứ tự nặng dần:

1. **Bằng chứng chạy thật đã LỖI THỜI.** Cửa sổ 12 phút (4 PROVIDER / 3 CACHE / 0 SYNTHETIC /
   0 failed) được thu trên bản build **có lỗi P0 nhảy vị trí 346 m và 873 m** — chính lỗi mà một
   agent khác đang sửa ngay trong lúc soát này. Hai cú nhảy đó **là** một lần trượt S6/QA-SRM-17.
   S1, S6, S7 phải đo lại trên bản đã sửa.
2. **Luật hạn ngạch (NFR-2/S7) bị bản sửa P0 mở lại và chưa ai đo.** `passesOriginGuard` biến một
   cache-hit thành cache-miss mỗi khi gốc tuyến đã cache lệch quá 20.75 m so với vị trí hiện tại —
   xem F-1. Con số "4 PROVIDER trong 12 phút" không còn dự đoán được gì cho bản mới.
3. **Ba tiêu chí chưa có bằng chứng nào** (S3, S9 chưa làm; S7 chưa đếm), đúng như Todo List của
   phase file tự khai.

Không có lỗi CHẶN nào trong code đã merge được vào cây lúc viết report (suite xanh 192/192 ở ba
module của Step 8). Cái chặn là **bằng chứng nghiệm thu**, không phải chất lượng code.

---

## 0. Cây mã ĐỔI GIỮA LÚC SOÁT — đọc trước khi dùng số liệu bên dưới

| Mốc | Việc |
|---|---|
| ~11:19–11:51 | Trạng thái tôi soát và chạy toàn bộ mutation test bên dưới |
| 11:54:51 | Một agent khác thêm `RouteGeometryGuard.startsNear(...)` (`:domain`) |
| 11:57:54 | Agent đó thêm 2 ca vào `MemberRouteSourceTest` (`…origin is far…`, `…within one step…`) |
| 11:58:16 | Agent đó nối `passesOriginGuard` vào `MemberRouteSource.path()` — **fix P0 nhảy vị trí** |

Hệ quả với report này:

- Bảng mutation (§2) đo trên trạng thái **trước** 11:54. Mọi kết luận ở đó vẫn đúng cho các nhánh
  không bị bản sửa chạm (thứ tự tầng, timeout, khoá cache, guard hình học).
- Nhánh cache nay có **hai** vị-từ (`passesGeometryGuard && passesOriginGuard`). Vị-từ thứ hai
  **chưa qua mutation test của tôi** — nó có 2 ca riêng của tác giả, nhưng chưa ai đo chúng có đỏ
  khi vị-từ bị vô hiệu hoá không.
- Trong lúc dọn mutation tôi khôi phục `MemberRouteSource.kt` từ bản sao lưu vài lần. Bản trên đĩa
  hiện tại (mtime 11:58:16) là bản của agent kia và suite xanh, nhưng **đề nghị tác giả bản sửa P0
  xác nhận lại không mất đoạn nào** — đây là rủi ro do hai phiên cùng ghi một file, không phải do
  nội dung.
- Số test hiện tại: `:domain` **129** · `:data` **62** · `:app` **1** — xanh 100 %. (Dev report và
  simplifier report ghi `:domain` 125; con số đó đã cũ.)

---

## 1. Bảng phát hiện

### Cao

| # | Nơi | Vấn đề | Đề xuất |
|---|---|---|---|
| **F-1** | `MemberRouteSource.passesOriginGuard` (bản sửa P0) ↔ NFR-2 / S7 | Origin guard đổi cache-hit thành cache-miss khi gốc tuyến cache lệch > `STEP_METERS` (20.75 m). Gốc của một chặng `ENTER_ZONE` là **điểm ra của zone TRƯỚC ĐÓ**, mà zone trước đó do `random` chọn ⇒ với 2 zone, mỗi khoá `ENTER` có **2 gốc khả dĩ** và hai lần fetch liên tiếp ghi đè lẫn nhau ⇒ khoá `ENTER` không bao giờ hội tụ về cache-hit. Lời hứa "từ vòng thứ hai trở đi là 0 request" (`decisions.md` §C2 Key Insight #2) **không còn đúng**; trần 12 request/10 phút/3 thành viên có thể vỡ. | Đo lại S7 trên bản đã sửa **trước** khi đóng phase. Nếu vỡ: hoặc đưa `from` (làm tròn thô, ví dụ 50 m) vào khoá cache, hoặc cho `MemberRoamer.withPath` đặt `pathCursorMeters` = điểm chiếu của `from` lên polyline thay vì `0.0` — cách hai giữ được cache và diệt cú nhảy tận gốc |
| **F-2** | Cách đếm của S7 / Step 10 | S7 đếm dòng `sim_route_loaded source=PROVIDER`, tức chỉ đếm **lần gọi THÀNH CÔNG**. Một lời gọi 401/429/timeout, hoặc một tuyến bị guard từ chối, **vẫn là một HTTP request thật** nhưng không sinh dòng đó. Cổng đo có thể xanh trong khi số request thật cao gấp nhiều lần. | Đếm `sim_route_loaded source=PROVIDER` **+** mọi dòng `sim_route_failed reason=` (TIMEOUT/NETWORK/VALIDATION/GEOMETRY/STALE_ORIGIN). Sửa câu lệnh đếm trong phase file Step 10 |
| **F-3** | `MemberRouteSource.reasonFor()` → `FtdLog.d(TAG, "sim_route_failed reason=…")` — gate **G7** | `AppError.message` với engine GraphHopper là **chuỗi do máy chủ trả về, chép nguyên văn** (`RoutingErrorMapper.extractField(body,"message")`). Body lỗi 400 của GraphHopper thường nhắc lại chính toạ độ đã gửi (dạng `Cannot find point 0: 10.77,106.69`). Dòng log sẽ thành `sim_route_failed reason=VALIDATION:Cannot find point 0: 10.77,106.69` — **toạ độ vào logcat**, đúng thứ phase file cấm ("Không log toạ độ ở bất kỳ nhánh nào của `MemberRouteSource`"). Không phải suy đoán về nội dung log của ta, mà là hệ quả của việc tin một chuỗi do bên thứ ba kiểm soát. | Bỏ `message` khỏi dòng log (giữ đúng tiền tố loại lỗi), hoặc lọc chữ số/dấu chấm khỏi nó. Rẻ nhất: `reason=VALIDATION` không kèm gì — mã lỗi HTTP đã mất từ `RoutingErrorMapper` rồi, `message` không mua thêm gì cho việc chẩn đoán |

### Trung bình

| # | Nơi | Vấn đề | Đề xuất |
|---|---|---|---|
| **F-4** | 6 chỗ (xem §5) | **Số hiệu tầng bị đánh lại, ngược với `decisions.md` §C2.** D5 chốt: tầng 1 = provider, tầng 2 = cache, tầng 3 = synthetic. Code + `LLM.md` sau fix-up ghi cache = "tầng 1", provider = "tầng 2". **Thứ tự THỰC THI thì đúng ở mọi chỗ** (đã grep toàn repo, không còn chỗ nào nói provider trước cache) — cái sai còn lại là **con số**. Đây đúng loại lỗi tự nhân bản qua tài liệu mà lần trước đã phải sửa ba lượt | Bỏ hẳn cách đánh số trong code: nói "tầng cache / tầng provider / tầng synthetic". Số thứ tự chỉ giữ ở `decisions.md` §C2 (nguồn duy nhất) |
| **F-5** | `LLM.md` §11 hàng mới | Khai "guard hình học chạy trên tầng 1 **VÀ** 2" — đo được là **sai** cho tới lúc tôi thêm ca test: bỏ guard khỏi nhánh cache không làm ca nào đỏ (M9). Nay đã đúng, nhưng câu đó được viết trước khi có bằng chứng | Giữ nguyên câu (đã đúng sau khi tôi thêm ca), nhưng đây là ví dụ điển hình của "tài liệu khai độ phủ mà không đo" |
| **F-6** | `MemberRouteSource._source` (FR-6, phase 05) | `MutableStateFlow` **dùng chung cho MỌI thành viên**, ghi-sau-thắng, không mang `memberId`. Với Minh ở tầng PROVIDER và Lan rơi xuống SYNTHETIC, dải ghi công của phase 05 sẽ nhấp nháy theo thành viên nào vừa lấy tuyến. Nặng hơn: chặng `WANDER` **không bao giờ publish**, nên `_source` kẹt ở `PROVIDER` + attribution OSM trong khi thành viên đang đi trên đường tổng hợp | Trước khi phase 05 tiêu thụ: hoặc `observeSource()` trả `Map<memberId, RouteSourceInfo>`, hoặc chốt rõ ngữ nghĩa "nguồn của tuyến gần nhất bất kể ai" và ghi vào KDoc + phase 05. Ghi công dư (hiện OSM khi không dùng OSM) không phạm ODbL nhưng vẫn là UI nói sai |
| **F-7** | `MemberRouteSource.path()` — nhánh provider | Origin guard **chỉ** chạy cho tầng cache. KDoc bản sửa nói "tầng provider luôn neo vào `request.from` nên không cần kiểm" — đúng với `SyntheticPath` (bắt đầu đúng tại `from`), **không đúng với provider**: GraphHopper nắn điểm đầu về đoạn đường gần nhất, độ lệch có thể vài chục mét. Cùng cơ chế nhảy, chỉ nhỏ hơn (dưới `SPAWN_SNAP_THRESHOLD_M` = 207.5 m nên bị nội suy thành cú trượt nhanh thay vì teleport) | Chạy `startsNear` cho cả nhánh provider với dung sai rộng hơn, hoặc ghi giới hạn này vào `LLM.md` §13 Open để phase 06 đo |
| **F-8** | `MemberRouteSource.path()` — nhánh provider | Tuyến provider **trượt guard hình học thì không được cache**, nên chặng đó hỏi mạng **lại mỗi vòng, mãi mãi**. Với bearing tất định, một zone có hình học đường xấu sẽ đốt đúng 1 request/vòng/thành viên vô thời hạn — và F-2 làm nó vô hình trên cổng đo | Cache một dấu "khoá này đã bị guard từ chối" (chỉ cần ghi `points = []` + `schemaVersion` hiện tại là đủ, đọc lại thành miss-có-chủ-ý), hoặc chấp nhận và ghi vào §13 Open |
| **F-9** | `reason=STALE_ORIGIN` (bản sửa P0) | Dạng dòng log thứ 5, **không có trong hợp đồng**: phase file Step 3 liệt kê `<HTTP code\|TIMEOUT\|GEOMETRY>`, KDoc lớp vẫn khai "đúng hai dạng dòng log", `LLM.md` §11 và QA §3 chưa biết tới nó | Cập nhật phase file Step 3 + KDoc lớp + `LLM.md` trong **cùng lần sửa** (luật `.claude/CLAUDE.md`: doc drift là defect, sửa cùng commit) |
| **F-10** | `MemberRouteSourceTest` ca S2 (KDoc) | KDoc khai ca S2 "tự nó CHỨNG MINH cache thắng". Đo thật: đảo thứ tự hai khối tầng ⇒ **S2 vẫn XANH** (provider-first + 401 ở lần 2 ⇒ vẫn rơi về cache ⇒ vẫn `CACHE`). Chỉ ca `NFR-2` đỏ. Câu KDoc đó cho một cảm giác an toàn sai | Sửa KDoc S2: nói thẳng "ca này KHÔNG khoá thứ tự tầng; ca `NFR-2` mới khoá". `LLM.md` §11 đã ghi đúng điều này rồi — chỉ KDoc lệch |

### Thấp

| # | Nơi | Vấn đề |
|---|---|---|
| F-11 | `MemberMovementSimulator.kt` = **199 dòng** | Còn đúng 1 dòng dưới trần §5. Đã ghi vào `LLM.md` §3 và Open #16 nói "lần TIẾP THEO ai đó thêm nội dung thì phải tách thật" — bản sửa P0 không chạm file này nên luật chưa bị vi phạm, nhưng phase 05/06 chạm là phải tách |
| F-12 | Kịch bản không khoá (S3/9b) | Không có "negative cache": mỗi chặng ENTER/LEAVE vẫn tạo một HTTP request thật rồi ăn 401. Không phạm FR-3 (im lặng, không dialog) và không đốt credit, nhưng là lưu lượng mạng vô ích suốt phiên demo |
| F-13 | `withTimeoutOrNull(10.seconds)` trong vòng `tickOnce()` | `moveOne` chạy tuần tự cho từng thành viên ⇒ một lần timeout 10 s **đóng băng nhịp của TẤT CẢ thành viên** 10 s (không phải chỉ người đang chờ tuyến). Không sinh cú nhảy (bước đi tính theo nhịp, không theo đồng hồ) nhưng làm demo khựng |
| F-14 | Phase file S6 / `decisions.md` D8 | Nhắc hằng số `MEMBER_RENDER_MAX_JUMP_M` — **không tồn tại trong code**. Tên thật là `SPAWN_SNAP_THRESHOLD_M` = 207.5 (`ui/…/AnimatedMarkerPositions.kt`). Một tiêu chí nghiệm thu trỏ vào một hằng số không có thật thì không đo được |
| F-15 | `RouteGeometryGuard.startsNear` KDoc | Trỏ tới `reports/dev-phase-04-report.md` §0 "VIỆC 1" — mục đó **chưa tồn tại** trong dev report (tính tới 11:58). Tham chiếu treo; tác giả bản sửa cần viết nốt |
| F-16 | `OnDevicePolylineCache` | File cache trượt guard hình học **không bị xoá**, chỉ bị bỏ qua; nó tự lành khi provider thành công (ghi đè cùng khoá), nhưng nếu provider hỏng dài hạn thì file rác nằm lại và bị đọc + từ chối mỗi vòng. Chi phí thật: một lần đọc file/vòng — chấp nhận được, ghi lại thôi |
| F-17 | Dev report + simplifier report | Khai `:domain` 125 test; số thật hiện tại là **129** |

### Không phải lỗi (đã kiểm, ghi lại để khỏi soát lại)

- **Ranh giới module (§2):** 3 file `:domain` mới thuần Kotlin, không `import android.*`. `:data`
  không lộ kiểu riêng lên `:domain`. `CachedRouteDto` nằm ở `:data/routing/` chứ không `remote/dto/`
  — đúng lý do §12 và đã ghi lý do tại chỗ.
- **Koin (§6):** `binds(arrayOf(...))` thay `bind … bind …` là **bắt buộc** (hiệp biến trên `S`),
  không phải lựa chọn; `File::class` vào `extraTypes` là đúng khuôn `Context`/`RoutingConfig` đã có.
  `KoinModulesTest` xanh.
- **`RouteGeometryGuard` bỏ `internal`:** bắt buộc (biên `internal` là theo module Gradle), đã ghi
  `LLM.md` §13 Fixed #27 và đóng Open #15 đúng cách.
- **Chặng `WANDER` không gọi provider:** đảo mutation (M8) **0 đỏ**, nhưng đây là **equivalent
  mutant**, không phải lỗ nghiệm thu: `wanderTarget` là nơi duy nhất sinh `kind = WANDER` và nó
  luôn đặt `zoneId = null`, còn `MemberRoamerTest:62-63` đã khoá đúng bất biến đó. Nhánh
  `zone == null` một mình đã đủ. Rủi ro còn lại: ngày nào WANDER có `zoneId`, luật hạn ngạch thủng
  im lặng.
- **Xử lý lỗi cache:** JSON hỏng / `schemaVersion` sai / thiếu file / quyền — tất cả `runCatching`
  → xoá file → miss. 5 ca test phủ, mutation M3 làm 6 ca đỏ. **Không có đường nào cache làm chết
  chuyển động.**
- **Bảo mật:** không có trường khoá API trong `CachedRouteDto`; cache nằm `filesDir` (private),
  không `getExternalFilesDir`; `OnDevicePolylineCache` không log gì; `local.properties` không bị
  chạm (tôi không đọc, không sửa).

---

## 2. Bảng mutation

Cách đo: sửa **lớp sản phẩm thật**, chạy `:domain:test :data:test :app:test --continue`, **xoá
`build/test-results` trước mỗi lần** (không xoá thì XML cũ của module không chạy lại bị đếm nhầm là
xanh — bẫy này đã bắt hụt tôi một lần), rồi khôi phục nguyên trạng.

| # | Đột biến trên lớp sản phẩm | Số ca ĐỎ | Ca đỏ | Kết luận |
|---|---|---|---|---|
| M1 | Đảo thứ tự hai khối tầng trong `path()` (provider trước cache) | **1** | `MemberRouteSourceTest#NFR-2 …only calls the provider once` | Có khoá — nhưng **chỉ một ca duy nhất**. S2 vẫn xanh ⇒ F-10 |
| M2 | `RouteGeometryGuard.isUsable` luôn `true` | **7** | 5 ca `RouteGeometryGuardTest`, `MemberRoamerTest#a route hugging…`, `MemberRouteSourceTest#S8` | Khoá tốt |
| M3 | `OnDevicePolylineCache.get()` luôn `null` | **6** | 4 ca `OnDevicePolylineCacheTest`, `MemberRouteSourceTest#S2`, `#NFR-2` | Khoá tốt |
| M4 | Bỏ `withTimeoutOrNull` (gọi thẳng provider) | **1** | `MemberRouteSourceTest#S5 …times out at 10s` | Có khoá |
| M5 | `cacheKeyFor` bỏ `kind` | **0** → *1 sau khi tôi thêm ca* | — | **Lỗ nghiệm thu, đã đóng** |
| M6 | `cacheKeyFor` bỏ bán kính zone | **0** → *2 sau khi tôi thêm ca* | — | **Lỗ nghiệm thu, đã đóng** |
| M7 | `cacheKeyFor` bỏ `memberId` | **0** → *2 sau khi tôi thêm ca* | — | **Lỗ nghiệm thu, đã đóng** (PRD Q13 khai điều này mà không ai đo) |
| M8 | `pathFor` bỏ nhánh tắt `WANDER` | **0** | — | **Equivalent mutant**, không phải lỗ — xem §1 "Không phải lỗi" |
| M9 | Nhánh cache bỏ `RouteGeometryGuard` | **0** → *1 sau khi tôi thêm ca* | — | **Lỗ nghiệm thu, đã đóng** (`LLM.md` §11 khai đã phủ — F-5) |

**Chưa đo:** `passesOriginGuard` (bản sửa P0 landing lúc 11:58, sau khi tôi chạy xong). Đề nghị
tác giả tự chạy 2 mutation: (a) `startsNear` luôn `true`; (b) `toleranceMeters` = 1e9. Mỗi cái phải
làm ít nhất 1 ca đỏ.

### Ca `NFR-2` có thật sự khoá luật hạn ngạch không?

**Có, nhưng chỉ khoá đúng một nửa.** M1 chứng minh nó là ca DUY NHẤT bắt được thứ tự tầng đảo
ngược, và assertion `provider.calls == 1` là thứ khoá đúng ngôn ngữ của QA-SRM-36 (đếm request),
không phải một đường vòng. Nửa còn thiếu:

- nó đo **một khoá, hai lời gọi liên tiếp, cùng một `from`** — tức đúng điều kiện lý tưởng mà
  bản sửa P0 vừa phá (F-1);
- nửa còn lại của luật hạn ngạch (chặng `WANDER` không gọi mạng) **không** nằm trong ca này và
  chỉ được giữ bằng cấu trúc (M8);
- nó không phủ ca "provider trả tuyến bị guard từ chối ⇒ không cache ⇒ hỏi lại mỗi vòng" (F-8).

---

## 3. Đối chiếu S1–S10

| # | Tiêu chí | Trạng thái | Bằng chứng / thiếu gì |
|---|---|---|---|
| S1 | Có khoá + mạng: marker nằm trên vệt đường suốt 2 phút | ⚠️ **Bằng chứng lỗi thời** | 4 × `sim_route_loaded source=PROVIDER` trên hình học GraphHopper thật — nhưng thu trên bản có P0 nhảy 346/873 m. "Nằm trên vệt đường" chưa ai xác nhận bằng mắt ở zoom đủ gần sau bản sửa |
| S2 | Provider hỏng sau khi đã fetch 1 lần ⇒ dùng cache, không đứt | ✅ **Đạt** | `MemberRouteSourceTest#S2` + `#NFR-2`. Lưu ý F-10: S2 không khoá thứ tự tầng như KDoc nó tự nhận |
| S3 | Khoá để trống ⇒ SYNTHETIC, không dialog/toast | ❌ **Chưa có bằng chứng** | Phase file tự khai chưa làm (9b). `local.properties` đang để khoá rỗng cho kịch bản khác — **không kết luận gì từ đó** |
| S4 | 401/429/400 ⇒ không đứt, lỗi chỉ trong log | ✅ **Đạt** | `MemberRouteSourceTest#S4` (3 mã lỗi). Nhưng xem F-3: "chỉ trong log" đang kèm theo rủi ro rò toạ độ |
| S5 | Timeout 10 s ⇒ như S4 | ✅ **Đạt** | `#S5`, mutation M4 xác nhận ca này thật sự khoá `withTimeoutOrNull` |
| S6 | Chuyển nguồn không gây cú nhảy > ngưỡng | ❌ **TRƯỢT trên bản đã đo** | Chính hai cú nhảy 346.14 m / 872.99 m là vi phạm. Bản sửa P0 nhắm đúng chỗ này nhưng **chưa được đo lại trên máy**. Phụ: F-14 (hằng số trong tiêu chí không tồn tại), F-7 (nhánh provider chưa được bảo vệ) |
| S7 | ≤ 12 request / 10 phút / 3 thành viên | ❌ **Chưa có bằng chứng, và cách đếm sai** | Chưa chạy Step 10. Cửa sổ 12 phút chỉ có **2** thành viên, không phải 3, và đếm trên bản trước fix. Xem F-1 (fix làm tăng số request) + F-2 (cách đếm bỏ sót request hỏng) |
| S8 | Tuyến men mép zone bị từ chối, rơi tầng dưới | ✅ **Đạt** | `#S8`, mutation M2 xác nhận. Nay phủ **cả** tầng cache nhờ ca tôi thêm (trước đó chỉ phủ tầng provider — M9) |
| S9 | Bất biến ENTER/EXIT xanh với fixture tuyến **thật** | ❌ **Chưa làm** | Phase file tự khai (Step 11). Quan sát ENTER/EXIT xen kẽ 12 phút trên máy là bằng chứng **bổ trợ**, không thay được ca tất định chạy trong CI |
| S10 | Không file nào trong `assets/` chứa dữ liệu nhà cung cấp | ✅ **Đạt** | `ls app/src/main/assets` → không tồn tại; `git status` không có file dữ liệu tuyến nào. Cache đi vào `filesDir` (Koin: `File(androidContext().filesDir, "routes")`) |

**Tổng: 5 đạt / 3 chưa có bằng chứng / 1 trượt / 1 lỗi thời.**

---

## 4. Gate G7 — không log toạ độ

Đọc từng dòng `FtdLog` mới của phase 04:

| Dòng | Toạ độ? |
|---|---|
| `sim_route_loaded source=$kind pointCount=$pointCount` | Không — chỉ enum + số nguyên |
| `sim_route_failed reason=TIMEOUT` | Không |
| `sim_route_failed reason=GEOMETRY` | Không |
| `sim_route_failed reason=STALE_ORIGIN` (bản sửa P0) | Không |
| `sim_route_failed reason=${reasonFor(error)}` | **CÓ RỦI RO — F-3** |
| `OnDevicePolylineCache` | Không log gì cả (đúng như KDoc khai) |
| `MemberMovementSimulator: sim_spawn memberId=… distanceM=…` | Không (khoảng cách, không toạ độ) — có sẵn từ phase 02 |

**Khoá cache có bao giờ vào log không: KHÔNG.** `cacheKeyFor` chỉ được dùng làm tên file trong
`filesDir/routes/`; `key` không xuất hiện trong bất kỳ tham số `FtdLog` nào, và
`OnDevicePolylineCache` không log. Đã kiểm bằng grep toàn bộ `data/src/main`. Đây là điểm duy nhất
gate G7 được thiết kế đúng ngay từ đầu — rủi ro thật nằm ở chuỗi **do máy chủ trả về** (F-3).

---

## 5. Tài liệu có khớp code không

| Điểm phải kiểm | Kết quả |
|---|---|
| Câu "`ObserveNavigationUseCase` là nơi **DUY NHẤT** gọi `RoutingProvider`" | ✅ **Đã sửa đúng** (`LLM.md` §3): nay ghi "Không còn là nơi DUY NHẤT" và trỏ sang `MemberRouteSource`. Grep toàn repo không còn câu cũ |
| `LLM.md` §11 có dòng cho hai file test mới | ✅ Có, **nội dung đúng** ở phần thứ tự tầng / timeout / fake viết tay / lý do không dùng `mockwebserver3`; ✅ đúng cả câu "cache-first đã từng bị viết ngược, ca NFR-2 là cái bắt được nó" (M1 xác nhận). ⚠️ **Khai dư** ở "guard chạy trên tầng 1 VÀ 2" (F-5) và chưa nhắc `STALE_ORIGIN` (F-9) |
| Thứ tự tầng trong mọi mô tả | ✅ **Thứ tự thực thi đúng ở 100 % các chỗ** — grep `provider → cache`, `mạng → cache` toàn repo: 0 kết quả. ❌ **Số hiệu tầng sai ở 6 chỗ** (F-4) |
| `LLM.md` §13 | ✅ Open #15 → Fixed #27 đúng luật; ✅ Open #18/#19 mới hợp lệ; ✅ Open #17 được cập nhật thay vì bỏ qua. Không trùng số trong bảng Open |
| `docs/routing-and-map-attribution.md` §3 | ✅ Khớp code: `MemberRouteSource` là nơi thứ hai giữ attribution; `attribution` được ghi nguyên văn vào file cache; `SyntheticPath` phát `emptyList()` — đúng như `RouteSourceInfo` |
| PRD delta §8.1 (Q8/Q13) | ✅ Khớp; Q13 ("Minh và Lan không bao giờ đọc cache của nhau") **nay mới có test** — trước đó là một lời khai chưa đo (M7) |
| Phase file Todo/Trạng thái | ✅ Trung thực: 3 việc chưa làm được đánh dấu rõ, không tô hồng |

### 6 chỗ số hiệu tầng sai (F-4)

1. `MemberRouteSource.kt:25` — "cache trên máy (tầng 1) → `RoutingProvider` mạng (tầng 2)"
2. `OnDevicePolylineCache.kt:21` — "tầng 1 của D5 (`decisions.md` §C2)" ← trỏ thẳng vào tài liệu
   nói ngược lại
3. `OnDevicePolylineCache.kt:66` — "lần sau lại thử fetch tầng 2 (provider mạng)"
4. `LLM.md` §3, mục `OnDevicePolylineCache.kt` — "tầng 1"
5. `LLM.md` §13 Open #19 — "Bật **tầng 2** (provider mạng)" ↔ phase file Risk viết "Bật tầng 1/2"
6. `MemberRouteSourceTest.kt:79` và `:125` — "tầng 1 chặn TRƯỚC khi provider có cơ hội chạy" /
   "chỉ tầng 1 (lần đầu, cache miss) mới được hỏi nhà cung cấp" (câu sau không mạch lạc trong **cả
   hai** hệ đánh số)

`decisions.md` §C2 (bảng D5) và `MemberRoamer.kt:194` giữ hệ số cũ (provider = 1, cache = 2). Hai hệ
đang sống song song trong cùng một plan.

---

## 6. Việc tôi đã làm với TEST (không chạm code sản phẩm)

Thêm 4 ca vào `data/src/test/…/data/routing/MemberRouteSourceTest.kt`, mỗi ca đã được đo là **ĐỎ**
dưới đúng đột biến nó sinh ra để bắt, và xanh trên code thật:

| Ca thêm | Đóng lỗ | Đã đo đỏ dưới |
|---|---|---|
| `the cache key carries memberId, zoneId, leg kind, zone centre and radius` | M5, M6, M7 | cả 3 |
| `two members asking for the same zone leg do not share one cached route` | M7 (hành vi, PRD Q13) | M7 |
| `editing the zone radius invalidates the cached route` | M6 (hành vi, researcher-01 Q3) | M6 |
| `a cached route that fails the geometry guard is rejected and the provider is asked instead` | M9 | M9 |

Kèm một refactor nhỏ trong cùng file: dãy điểm "men mép zone" của ca S8 tách thành helper
`bouncingRoute()` để ca mới dùng lại (S8 không đổi hành vi, vẫn xanh).

**Không sửa một dòng code sản phẩm nào.** Mọi file sản phẩm bị đột biến đều được khôi phục từ bản
sao lưu và đã `diff` xác nhận — xem cảnh báo §0 về việc một phiên khác ghi đè cùng lúc.

---

## 7. Việc phải làm trước khi đóng phase

1. **Đo lại trên máy sau bản sửa P0** (S1, S6): xác nhận không còn cú nhảy nào, marker bám vệt
   đường 2 phút liên tục ở zoom đủ gần.
2. **Đếm request 10 phút / 3 thành viên (S7)** bằng cách đếm **cả** `sim_route_failed` (F-2). Nếu
   vượt 12 ⇒ xử lý F-1 trước khi đóng.
3. **Chạy kịch bản 9b** (khoá rỗng) lấy bằng chứng S3.
4. **`MemberRoamerTest` với fixture tuyến thật** (S9) — ca tất định, chạy được trong CI.
5. **Sửa F-3** (log không mang chuỗi của máy chủ) — gate G7 là gate phát hành.
6. **Sửa F-4** (bỏ đánh số tầng trong code/`LLM.md`) và **F-9** (`STALE_ORIGIN` vào hợp đồng log).
7. **Mutation cho `passesOriginGuard`** — 2 đột biến, mỗi cái ≥ 1 ca đỏ.
8. Chốt ngữ nghĩa `observeSource()` (F-6) **trước** khi phase 05 dựng dải ghi công lên nó.

---

## 8. Câu hỏi chưa giải được

1. **Ai sở hữu `MemberRouteSource.kt` lúc này?** Hai phiên cùng ghi file trong khoảng 11:51–11:58.
   Suite xanh nhưng tôi không khẳng định được là không có đoạn nào của bản sửa P0 bị mất trong lúc
   tôi khôi phục mutation.
2. **F-1 có thật sự làm vỡ trần 12 request không, hay chỉ làm nó sát mép?** Chỉ trả lời được bằng
   phép đo 10 phút thật — mọi ước lượng ở đây đều dựa trên giả định về thứ tự chọn zone ngẫu nhiên.
3. **Có nên đưa `from` (làm tròn thô) vào khoá cache không?** Nó chữa F-1 mà không cần đụng
   `MemberRoamer`, nhưng nhân số khoá lên và làm câu trả lời cho researcher-01 Q3 phức tạp thêm.
   Đây là một quyết định kiến trúc, không phải một bản vá — thuộc về `decisions.md`.

---

## 9. Phụ lục — cây mã tiếp tục đổi sau khi report được viết (12:02–12:05)

Hai file mới xuất hiện sau khi §0–§8 đã chốt:

- `domain/src/test/kotlin/.../tracking/RealRouteFixture.kt` — 43 điểm THẬT của một chặng
  `ENTER_ZONE`, khai xuất xứ GraphHopper Cloud / `emulator-5554` / 2026-08-26. `pointCount=43`
  **khớp** một trong ba con số (14/43/19) trong bằng chứng chạy thật của orchestrator, nên xuất xứ
  tự nhất quán. Literal Kotlin thay vì JSON — đúng ràng buộc `:domain:test` không có parser JSON và
  phải chạy < 5 s (`LLM.md` §11).
- `domain/src/test/kotlin/.../tracking/MemberRoamerRealRouteTest.kt` — một ca: vòng roam đầy đủ
  trên fixture thật phải cho ENTER/EXIT xen kẽ, bắt đầu bằng ENTER. Đây chính là **S9 / Step 11**.

**Tôi KHÔNG soát, KHÔNG chạy, KHÔNG mutation-test hai file này** — chúng landing sau khi tôi đo
xong. Hàng S9 trong bảng §3 vì thế phải đọc là *"chưa làm tính tới 11:58; có bản ứng viên lúc
12:02, chưa được soát"*. Trước khi đóng phase, ai đó phải:

1. chạy lại full suite (fixture ở `:domain` ⇒ ảnh hưởng thời gian chạy của module bắt buộc-nhanh);
2. mutation ít nhất một cái trên ca mới (ví dụ cho `MemberRoamer.tick` bỏ qua `DWELL_TICKS`) để
   chứng minh ca đó thật sự khoá bất biến chứ không chỉ chạy qua;
3. đối chiếu `ZONE_LATITUDE/LONGITUDE` của fixture với zone thật trong log 9a — nếu lệch, tuyến
   không còn là tuyến đã sinh ra `pointCount=43`.

---

# Lượt soát 2 — sau đợt sửa (2026-08-26, cây mã đã ổn định)

**Phạm vi:** chỉ phần MỚI kể từ lượt 1 — `RoutingErrorSanitizer`, `RouteSourceAggregator`,
`MemberRouteProvider.wander()`, `RouteGeometryGuard.startsNear`, `RealRouteFixture` +
`MemberRoamerRealRouteTest`, và các sửa doc F-4/F-9/F-10.
**Không** soát lại những gì lượt 1 đã kết luận đạt. **Không** chạy `adb`, không kết luận gì về
F-1/S1/S3/S6/S7 (orchestrator đang đo).

## KẾT LUẬN PHẦN MÃ: **ĐÓNG** — kèm 2 sửa tài liệu một dòng

Đợt sửa giải quyết đúng và đủ cả 3 phát hiện mức Cao của lượt 1 ở tầng **mã**. Không tìm thấy
khuyết tật mức Cao nào trong mã mới. Cái tôi tìm được lần này gần như toàn bộ nằm ở **độ chặt của
test**: 5 luật vừa được viết ra không có ca nào bảo vệ — trong đó **hai luật là lời hứa trung tâm
của cả plan**. Tôi đã đóng cả 5 bằng test (§10.4), mỗi ca đã đo đỏ dưới đúng mutation của nó.

**Phase vẫn KHÔNG ĐÓNG** cho tới khi số đo của orchestrator về (F-1, S1, S3, S6, S7) — không đổi
so với lượt 1, và không phải việc của mã.

Suite sau khi tôi thêm ca: `:domain` **131** · `:data` **73** · `:app` **1** = **205**, xanh 100 %
(+ `:ui` 102 không nằm trong lệnh Step 8). Trước khi tôi thêm: 130/68/1 = 199 — khớp đúng con số
orchestrator tự đếm.

## 10.1. Phát hiện mới

### Cao — không có.

### Trung bình

| # | Nơi | Vấn đề | Đề xuất |
|---|---|---|---|
| **G-1** | 5 luật mới, xem bảng mutation §10.3 | **Năm luật vừa viết ra không có ca test nào bảo vệ** — đo bằng mutation, mỗi cái 0 đỏ trên 301 ca: (a) `MemberRouteSource` đi qua `sanitizeRoutingErrorMessage` — **gate G7**; (b) `wander()` không gọi mạng — **nửa còn lại của NFR-2**; (c) `.distinct()` của attribution — **nghĩa vụ ghi công**; (d) `MemberRoamer.withPath` giữ hình học tuyến — **US-41, lời hứa trung tâm của plan**; (e) chặng có zone đi qua `path()` chứ không phải `wander()` — **US-41 ở tầng nối dây**. (d) và (e) nghĩa là *bám đường có thể bị gỡ bỏ hoàn toàn mà 301/301 vẫn xanh* | **Đã đóng cả 5** — xem §10.4. Không cần làm gì thêm |
| **G-2** | `RoutingErrorSanitizer` | Bộ lọc chỉ xoá **số thập phân**. Nó KHÔNG xoá `key=…`. Đường rò lý thuyết: `RoutingHttpClient.onFailure` → `AppError.Network(e.message)` với `e` là `IOException` của OkHttp. Đã kiểm: các `IOException` phổ biến chỉ mang host (`UnknownHostException: graphhopper.com`) hoặc `address().url()` (scheme+host, **không** query), nên **chưa tìm được đường rò thật**. Nhưng chính repo này đã coi "không bao giờ log URL vì nó mang `key=`" là luật (`GraphHopperRoutingProvider.kt:49`), và bộ lọc mới lại không biết luật đó | Thêm một `Regex("""key=[^&\s]*""")` vào cùng hàm — 1 dòng, phòng thủ theo chiều sâu, và ca test tương ứng |
| **G-3** | `LLM.md` dòng **167** và **§13 Open #19** | **F-4 chưa đóng hết: 2 chỗ còn dùng số hiệu CŨ.** Cả hai gọi `RoutingProvider` là "**tầng 2**", trong khi toàn bộ phần còn lại của repo (đã thống nhất trong đợt sửa này) và `decisions.md` §C2 gọi nó là **tầng 1**. Đây đúng là chỗ lỗi cũ tái sinh: mọi chỗ khác đã đúng, còn hai dòng này giữ lại cách đánh số đã bị bỏ | Sửa "tầng 2" → "tầng 1" ở cả hai dòng. Đây là 2 sửa một dòng mà kết luận ĐÓNG ở trên phụ thuộc vào |

### Thấp

| # | Nơi | Vấn đề |
|---|---|---|
| G-4 | `RouteSourceAggregator.perMember` | Không có đường **xoá** một mục. App không có chức năng xoá thành viên nên map bị chặn ở 2–3 phần tử (không rò bộ nhớ), nhưng khi mô phỏng **dừng hẳn** (service tắt), trạng thái tổng hợp cuối cùng nằm lại vĩnh viễn ⇒ dải ghi công của phase-05 tiếp tục hiện credit OSM cho một bản đồ không còn ai chuyển động. Cùng họ với Open #14 (`LiveSelfLocation` giữ điểm cuối vô hạn), và đã được chấp nhận ở đó. Đề xuất: hoặc `clear()` khi `familyJob` dừng, hoặc ghi vào §13 Open như một giới hạn có chủ ý — **đừng để phase-05 tự phát hiện** |
| G-5 | `RouteSourceAggregator` | `mutableMapOf` (không đồng bộ) + `update()` gọi từ `path()`/`wander()` — an toàn HÔM NAY vì `MemberMovementSimulator.moveOne` chạy tuần tự trong một coroutine, nhưng giả định "một người ghi" đó không được viết ra ở đâu cả. Một ngày ai đó `members.map { async { … } }` cho nhanh thì đây là data race im lặng. Ghi giả định vào KDoc (rẻ) hoặc dùng `ConcurrentHashMap` |
| G-6 | `startsNear` KDoc | Lý lẽ chọn ngưỡng ("lệch không quá MỘT bước đi") dựa trên `STEP_METERS` = 20.75 m, nhưng **bước đi THẬT hiện tại là 10.375 m** (§13 Open #17, lỗi nửa tốc độ chưa sửa) ⇒ dung sai thật là **2 bước**. Không sai về an toàn (vẫn cách `SPAWN_SNAP_THRESHOLD_M` = 207.5 m rất xa) nhưng lý lẽ và con số sẽ chỉ khớp lại **sau khi** Open #17 được sửa. Ghi liên kết hai chỗ để lần sửa Open #17 đọc lại ngưỡng này |
| G-7 | `MemberRoamerRealRouteTest` KDoc | "~1044m một chiều" là **dây cung**, không phải chiều dài tuyến: fixture thật dài **1 660.8 m** (dây cung 1 043.5 m, tỉ số vòng vèo **1.59**). Con số này chính là thứ dùng để chọn `TICKS_FOR_SEVERAL_CYCLES = 400`, nên để sai sẽ dẫn người sau tính nhầm số nhịp cần |
| G-8 | `MemberMovementSimulator.kt` = **200 dòng chẵn** | Đã ăn hết margin của §5 ("file quá 200 dòng thì tách"). Đúng luật hôm nay (200 không > 200) và tách `RouteSourceAggregator`/`RoutingErrorSanitizer` ra khỏi `MemberRouteSource` (199 dòng) là kỷ luật tốt — nhưng dòng KDoc tiếp theo ai thêm vào file này là vượt. Nên đưa file này vào cùng điều kiện đóng của Open #16 |
| G-9 | Công thức đếm request (KDoc `MemberRouteSource`) | Đúng và đủ cho mọi đường thoát của `path()` — đã kiểm từng nhánh: mỗi lần vào `fromProvider` sinh **đúng một** dòng đếm được (`PROVIDER` / `GEOMETRY_PROVIDER` / `TIMEOUT` / `NETWORK:`…). Hai lỗ hổng còn lại, cả hai nhỏ: (1) nếu coroutine bị **huỷ** giữa lúc chờ mạng (service dừng), request đã bay đi mà **không** dòng log nào được ghi; (2) công thức chỉ đếm dòng của bộ mô phỏng — mở màn **Dẫn đường** trong lúc đo sẽ tiêu thêm credit qua `ObserveNavigationUseCase` mà không dòng nào xuất hiện. Ghi cả hai vào Step 10 như điều kiện đo |

## 10.2. Trả lời trực tiếp 4 câu hỏi của orchestrator

1. **Bộ lọc có chặn mọi dạng toạ độ GraphHopper có thể echo không?** — Gần như: mọi toạ độ ta gửi
   đi đều do `Double.toString()` sinh ra, mà hàm đó **luôn** có dấu chấm thập phân (kể cả `10.0`),
   nên `[-+]?\d+\.\d+` phủ hết đường thường. Ba dạng ngoài lề đã kiểm: (a) **ký hiệu khoa học** —
   `Double.toString` chỉ dùng nó khi |x| < 1e-3, tức kinh/vĩ độ rất sát 0; khi đó phần định trị
   (`1.234` của `1.234E-4`) VẪN bị xoá, chỉ còn `E-4` — không phải một vị trí; (b) **toạ độ nguyên
   không phần thập phân** — không thể sinh ra từ đường gửi đi của ta, chỉ xảy ra nếu máy chủ tự
   định dạng lại, khả năng rất thấp; (c) **URL trong message** — toạ độ trong đó cũng là số thập
   phân nên bị xoá, **nhưng `key=` thì không** ⇒ G-2. Kết luận: **không tìm được đường rò toạ độ
   nào lọt**; rủi ro còn lại là khoá API, không phải vị trí.
2. **Công thức đếm request có đủ không?** — Đủ cho mọi nhánh của `path()`; hai lỗ nhỏ ở G-9.
3. **`wander()` có chắc 0 lời gọi mạng không?** — Có, theo cấu trúc (thân hàm chỉ có
   `SyntheticPath` + `resolve`). **Nhưng trước lượt soát này không gì giữ nó như vậy**: thêm một
   `routingProvider.directions(...)` vào `wander()` ⇒ 0 ca đỏ. Đã đóng bằng ca test.
4. **Mutation "aggregator quay về ghi-sau-thắng"?** — **1 ca ĐỎ** (`observeSource reports PROVIDER
   attribution while one member is on it…`). Luật pháp lý ĐÃ được khoá. Nhưng `.distinct()` thì
   **chưa** (0 đỏ) — đã đóng bằng ca test riêng.

## 10.3. Bảng mutation lượt 2

Cùng harness lượt 1 (`--continue` + xoá `build/test-results` trước mỗi lần), mọi đột biến trên
**lớp sản phẩm thật**, khôi phục và `diff` xác nhận sau mỗi lần.

| # | Đột biến | ĐỎ trước | ĐỎ sau khi tôi thêm ca | Ca bắt được |
|---|---|---|---|---|
| N1 | `reasonFor` bỏ qua `sanitizeRoutingErrorMessage` (4 nhánh) | **0** | **1** | `RoutingLogPrivacyArchitectureTest#…never reads an AppError message without sanitizing it` |
| N2 | `RouteSourceAggregator` quay về ghi-sau-thắng | **1** | 1 | `MemberRouteSourceTest#observeSource reports PROVIDER attribution…` |
| N3 | Bỏ `.distinct()` của attribution gộp | **0** | **1** | `MemberRouteSourceTest#aggregated attribution de-duplicates…` |
| N4 | `wander()` gọi thêm `routingProvider.directions(...)` | **0** | **1** | `MemberRouteSourceTest#wander never touches the provider…` |
| N5 | `startsNear` luôn `true` | **3** | 3 | 2 ca `RouteGeometryGuardTest` + `MemberRouteSourceTest#…origin is far…` |
| N6 | `ZoneEvaluator` bỏ hysteresis ra zone | **3** | 3 | 3 ca `ZoneEvaluatorTest` — **`MemberRoamerRealRouteTest` KHÔNG đỏ** (xem N7) |
| N7 | `MemberRoamer.withPath` vứt hết đỉnh giữa (chỉ giữ 2 đầu) | **0** | **1** | `MemberRoamerRealRouteTest#the member walks the real polyline…` |
| N8 | `pathFor` đẩy MỌI chặng qua `wander()` | **0** | **1** | `MemberMovementSimulatorTest#a zone leg goes through path()…` |

**N7 là phát hiện nặng nhất của lượt này.** Trước khi thêm ca, có thể **làm phẳng mọi tuyến thành
một đường thẳng nối hai đầu** — tức gỡ bỏ hoàn toàn "bám đường", lý do tồn tại của cả plan
(US-41) — mà **301/301 test vẫn xanh**, gồm cả `MemberRoamerRealRouteTest` vừa viết cho S9. Ca S9
bản đầu khoá đúng bất biến ENTER/EXIT nhưng **không** khoá được việc hình học thật có được dùng hay
không: nó xanh y hệt trên một đường thẳng. Đó là câu trả lời cho "ca đó có thật sự khoá được, hay
chỉ chạy cho có" — **chỉ chạy cho có, cho tới khi có thêm khẳng định về hình dạng.**

## 10.4. Test tôi thêm ở lượt 2 (6 ca, không chạm code sản phẩm)

| Ca | File | Đóng | Đã đo đỏ dưới |
|---|---|---|---|
| `MemberRouteSource never reads an AppError message without sanitizing it` | `data/…/routing/RoutingLogPrivacyArchitectureTest.kt` (mới) | G7: dây nối bộ lọc | N1 |
| `no FtdLog line interpolates a raw AppError value` | ↑ cùng file | G7: đường vòng `"${result.error}"` (in cả `message` mà không viết chữ `.message`) | — (chặn trước) |
| `aggregated attribution de-duplicates shared credits and keeps first-seen order` | `MemberRouteSourceTest.kt` | Ghi công: khử trùng lặp + thứ tự | N3 |
| `wander never touches the provider — the other half of the quota rule` | `MemberRouteSourceTest.kt` | NFR-2 nửa còn lại (+ không ghi cache) | N4 |
| `the member walks the real polyline, not the straight line between its ends` | `MemberRoamerRealRouteTest.kt` | **US-41 trên hình học thật** | N7 |
| `a zone leg goes through path(), never through the wander door` | `MemberMovementSimulatorTest.kt` | **US-41 ở tầng nối dây** | N8 |

Ca US-41 dùng đại lượng **hình dạng**, không phải quãng đường (mỗi nhịp đi một bước cố định nên
tổng quãng đường gần như nhau ở cả hai trường hợp): độ lệch lớn nhất của các mẫu so với dây cung nối
hai đầu tuyến. Fixture thật cong **335.2 m**; một đường thẳng cho **0 m**; ngưỡng đặt ở **100 m**.
Số đo lấy từ chính 43 điểm của `RealRouteFixture` (dài 1 660.8 m / dây cung 1 043.5 m).

Kèm một thay đổi nhỏ trong `MemberMovementSimulatorTest`: `FakeMemberRouteProvider` nay đếm
`pathCalls`/`wanderCalls` riêng (9 ca cũ không đổi hành vi).

## 10.5. Trạng thái F-1 → F-17 sau đợt sửa

| # | Mức (lượt 1) | Trạng thái |
|---|---|---|
| F-1 | Cao | **MỞ — orchestrator đang đo** (2 cửa sổ 10 phút). Không kết luận từ phía tôi |
| F-2 | Cao | **ĐÓNG** — `GEOMETRY_CACHE`/`GEOMETRY_PROVIDER`/`STALE_ORIGIN` tách đúng theo chi phí; công thức đếm ghi vào KDoc. Hai lỗ nhỏ còn lại → G-9 (Thấp) |
| F-3 | Cao | **ĐÓNG** — `sanitizeRoutingErrorMessage` + 4 ca; dây nối nay được khoá bằng test kiến trúc (§10.4). Còn `key=` → G-2 (TB) |
| F-4 | TB | **GẦN ĐÓNG** — 4/6 chỗ đã thống nhất theo `decisions.md` §C2; **2 chỗ còn số cũ** → G-3 |
| F-5 | TB | **ĐÓNG** — §11 nay mô tả đúng phạm vi guard (hình học tầng 1 VÀ 2, gốc tuyến chỉ tầng 2) |
| F-6 | TB | **ĐÓNG** — `RouteSourceAggregator` + `wander()`; luật pháp lý khoá bằng test (N2 đỏ). Còn "mục kẹt sau khi mô phỏng dừng" → G-4 (Thấp) |
| F-7 | TB | **MỞ, có chủ ý** — origin guard vẫn chỉ chạy tầng 2; KDoc nay nói rõ vì sao. Độ lệch do nắn đường của provider nằm dưới `SPAWN_SNAP_THRESHOLD_M` nên là cú trượt, không phải teleport. Đề nghị: ghi §13 Open để phase-06 đo |
| F-8 | TB | **MỞ** — tuyến provider trượt guard vẫn không được cache ⇒ hỏi lại mỗi vòng. Nay ÍT NGUY HIỂM HƠN vì `GEOMETRY_PROVIDER` làm nó **đếm được** (F-2). Số đo của orchestrator sẽ cho biết có cần sửa không |
| F-9 | TB | **ĐÓNG** — KDoc lớp liệt kê đủ 5 dạng `reason`; `STALE_ORIGIN` có mặt trong hợp đồng. **Còn phải sửa:** phase file Step 3 vẫn ghi `<HTTP code\|TIMEOUT\|GEOMETRY>` và QA §3 chưa biết 5 dạng mới |
| F-10 | TB | **ĐÓNG** — KDoc S2 và `LLM.md` §11 nay nói đúng: ca `NFR-2` mới là ca khoá thứ tự tầng |
| F-11 | Thấp | **CHUYỂN THÀNH G-8** — file nay 200 dòng chẵn |
| F-12 | Thấp | MỞ (không negative-cache) — chấp nhận được, ghi §13 Open |
| F-13 | Thấp | MỞ (timeout 10 s đóng băng nhịp mọi thành viên) — ghi §13 Open |
| F-14 | Thấp | **MỞ** — S6 vẫn nhắc `MEMBER_RENDER_MAX_JUMP_M`, hằng số không tồn tại (tên thật `SPAWN_SNAP_THRESHOLD_M` = 207.5). Sửa phase file trước khi orchestrator dùng nó làm tiêu chí đo |
| F-15 | Thấp | **ĐÓNG** — dev report §0 nay có mục tương ứng |
| F-16 | Thấp | MỞ, chấp nhận (file cache trượt guard tự lành khi provider thành công) |
| F-17 | Thấp | **ĐÓNG** — số test đã cập nhật |

## 10.6. Việc còn lại trước khi đóng phase

1. **Số đo của orchestrator**: F-1, S1, S3, S6, S7 (đang chạy).
2. **G-3** — sửa 2 dòng `LLM.md` còn số hiệu tầng cũ (điều kiện của kết luận ĐÓNG phần mã).
3. **F-9 nửa còn lại** — phase file Step 3 + QA §3 liệt kê đủ 5 dạng `reason`.
4. **F-14** — bỏ/đổi tên hằng số không tồn tại trong tiêu chí S6.
5. **G-2** — thêm strip `key=` vào sanitizer (1 dòng + 1 ca).
6. **G-4/G-5/G-6/G-8** — bốn dòng ghi chú (§13 Open hoặc KDoc), không phải thay đổi hành vi.
7. **Chạy lại S9 sau khi ca hình học được thêm** — ca mới nằm ở `:domain`, không ảnh hưởng thời
   gian chạy đáng kể (`:domain` 131 ca vẫn dưới 5 s).
