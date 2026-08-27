# Phase 06 — Gate chất lượng và tài liệu

## Context Links

- [plan.md](plan.md) · phase-01 → phase-05
- [`LLM.md`](../../LLM.md) §3, §6, §7, §10, §11, §12, §13, §14 — mỗi mục có một hàng phải sửa
- [`.claude/CLAUDE.md`](../../.claude/CLAUDE.md) — bảng "Update rules": drift tài liệu là **defect**, sửa trong cùng thay đổi gây ra nó

## Overview

**Ưu tiên:** P1 · **Trạng thái:** ✅ Hoàn thành — 9/9 gate xanh. Bằng chứng:
`reports/gate-log-2026-08-24.txt`, `reports/g7-attribution-graphhopper.png`,
`reports/g6-attribution-valhalla.png`. Một mục còn Open ngoài tầm code: điều khoản redistribution
của GraphHopper (thư đã soạn, **chưa gửi**) — chặn phát hành, không chặn tính năng.

Không có code tính năng mới. Phase này tồn tại vì `LLM.md` là hợp đồng kiến trúc của repo, và một
tính năng thêm ba package mới, một quyền mới, một thư viện mới, một model mới mà không sửa `LLM.md`
sẽ khiến người kế tiếp — người hoặc máy — đọc một bản đồ sai.

## Requirements

1. Toàn bộ gate xanh.
2. `LLM.md` phản ánh đúng repo sau phase-05.
3. `docs/routing-and-map-attribution.md` có ngày kiểm tra điều khoản và người kiểm.
4. Các mục Open mới ghi vào `LLM.md` §13.
5. Plan 1137 đánh dấu superseded (đã làm khi lập plan này — xác nhận lại).
6. `docs/routing-and-map-attribution.md` §5: dòng GraphHopper `motorcycle`/profile đã đóng bằng kết
   quả đo thật; dòng **redistribution** chỉ được đóng khi có trả lời **bằng văn bản** từ GraphHopper.

## Gate

| # | Gate | Lệnh | Đỏ nghĩa là gì |
|---|---|---|---|
| G1 | Build cả hai variant | `./gradlew assembleDebug assembleRelease` | Không giao được |
| G2 | Test `:domain` | `./gradlew :domain:test` | Logic reroute/decode sai — nguy hiểm nhất, vì nó sai im lặng |
| G3 | Test `:data` | `./gradlew :data:test` | Mapper sai đơn vị hoặc sai precision |
| G4 | Test `:ui` | `./gradlew :ui:test` | Gồm `CoroutineSafetyArchitectureTest` và test ghim attribution |
| G5 | Koin | `./gradlew :app:testDebugUnitTest` (`KoinModulesTest.verify()`) | Thiếu định nghĩa → crash lúc mở màn hình, không phải lúc build |
| G6 | Đổi engine | Build `ROUTING_ENGINE=GRAPHHOPPER` rồi `=VALHALLA`, chạy cùng cặp điểm | Abstraction không thật |
| G7 | Attribution | Ảnh chụp màn hình có tuyến đường | **Điều kiện pháp lý.** Không có ảnh thì coi như chưa làm |
| G8 | Không cảnh báo stdlib | Đọc log G1 | Xem phase-01 Key Insight #1 |
| G9 | Không rò key | `grep -rn "GRAPHHOPPER_API_KEY\|STADIA_API_KEY" --include=*.kt .` chỉ ra ở `FamilyTrackerApp.kt`; `git status --porcelain local.properties` rỗng; và **đọc key ra khỏi `local.properties` lúc kiểm, không viết nó vào file này**: `KEY=$(sed -n 's/^GRAPHHOPPER_API_KEY=//p' local.properties); [ -n "$KEY" ] && ! grep -rq "$KEY" data/src/test/resources/` | Key lọt vào log, vào fixture, hoặc vào git |

## Cập nhật `LLM.md`

| Mục | Sửa gì |
|---|---|
| **§3 `:domain`** | `model/`: `GeoPoint.kt`, `Directions.kt` (**có field `attribution: List<String>`** — điều kiện pháp lý #1 dưới dạng một kiểu dữ liệu), `RoutingConfig.kt`. `repository/`: `RoutingProvider.kt`. `tracking/`: `PolylineDecoder.kt`, `RoutingGeometry.kt`, `RerouteEvaluator.kt`. `usecase/`: `ObserveNavigationUseCase.kt` |
| **§3 `:data`** | Package **mới** `remote/` (`RoutingHttpClient.kt`, `dto/`) và **mới** `routing/` (2 provider, 2 mapper, `RoutingErrorMapper`) |
| **§3 `:ui`** | Feature **mới** `feature/navigation/` + 4 component |
| **§3 `:app`** | `appConfigModule` thêm `single { RoutingConfig(...) }`; `build.gradle.kts` đọc 4 khoá `local.properties` |
| **§6** | Mẫu `named("graphhopper")`/`named("valhalla")` + `single<RoutingProvider>` chọn bằng `when` exhaustive |
| **§7** | `NavigationRoute(memberId)` |
| **§10** | Quyền thứ **7**: `INTERNET`. Câu "6 quyền" hiện tại thành sai — sửa cả câu đó |
| **§11** | Hàng mới: `PolylineDecoder`/`RoutingGeometry`/`RerouteEvaluator` ở `:domain/src/test`; mapper + `mockwebserver3` ở `:data/src/test`; `NavigationViewModel` ở `:ui/src/test`. Ghi thêm: `data/src/test/resources/` là thư mục fixture ĐẦU TIÊN của repo, kèm `README.md` ghi ngày lấy và lệnh `curl` |
| **§12** | Hàng mới: "Một lời gọi API mạng" → DTO ở `:data/remote/dto/`, provider ở `:data/routing/`, cổng ở `:domain/repository/` |
| **§14** | `com.squareup.okhttp3:okhttp` `5.5.0` + `mockwebserver3-junit4`. **Kèm bảng stdlib đo được ở phase-01 Key Insight #1** (Ktor 3.5.2 → `kotlin-stdlib 2.3.21` > 2.2.10 của dự án; OkHttp 5.5.0 → `2.1.21` < 2.2.10). Ghi **số đo**, không ghi cảm tính: bản nháp trước của plan này từng ghi một lý do sai ("5.x biên dịch trên Kotlin mới") và lý do sai thì bị phát hiện, rồi kéo cả mục vào chỗ không đáng tin |

**Kiểm tra thêm khi mở `LLM.md`:** §3 mô tả `appConfigModule` có `single<Boolean>(named("debugBuild"))`
cho `FtdLog`, nhưng commit `fd3bf47` đã gỡ Koin khỏi cổng log đó. Đây là drift có sẵn, không do plan
này gây ra — sửa luôn nếu đang sửa đúng đoạn ấy, hoặc ghi vào §13 Open nếu không.

## Ghi vào `LLM.md` §13 Open

| Sai lệch | Vì sao ghi |
|---|---|
| **API key routing nằm trong `BuildConfig`, không xoay được nếu lộ — phải build lại APK.** Giảm thiểu: quota trần ở console nhà cung cấp | Người đọc sau sẽ hỏi "sao không có key rotation"; câu trả lời phải nằm trong repo |
| **`TrackingConstants` có 6 hằng số không truy được về PRD §6.** Nguồn là research phase-04 | **`LLM.md` §3 (dòng ~130, cây package `:domain`)** ghi `TrackingConstants.kt  12 hằng số PRD §6` — câu đó thành sai, và người đọc sẽ đi tìm mục PRD không tồn tại. (Chú ý: chỗ phải sửa là **§3**, không phải §14 như bản nháp trước của phase này ghi nhầm.) |
| **Tính năng dẫn đường không có user story nào trong PRD.** BA sở hữu PRD (§13 tiền lệ dòng #1, #3) | Nghiệm thu không có hợp đồng để đối chiếu |
| **Free tier GraphHopper là non-commercial, và chỉ có `[car, bike, foot]`** — không có `motorcycle`/`scooter`, đã kiểm thật 2026-08-24 bằng chính key của dự án | Ràng buộc pháp lý **và** ràng buộc chức năng, cùng gắn với lựa chọn mặc định. Ở Việt Nam xe máy là phương tiện mặc định, nên đây là lý do vận hành thật để đổi sang Valhalla — engine đó có `motorcycle` miễn phí |
| **API key routing nằm trong `local.properties` của máy dev, không có trong CI** | Ai clone repo về sẽ build được nhưng routing trả 401 cho tới khi tự lấy key. `local.properties.example` nói rõ chỗ lấy |
| (nếu còn mở) **Điều khoản "redistribution" của GraphHopper chưa được làm rõ** | Chặn #2b plan.md; đóng lại khi có trả lời bằng văn bản, không phải bằng suy đoán |

## Tài liệu khác

1. **`docs/routing-and-map-attribution.md`** — điền ngày kiểm tra điều khoản, ai kiểm, và kết quả
   câu hỏi gửi GraphHopper. Memo mục 6 điều kiện #5 đòi đúng việc này: *"Ghi lại ngày kiểm tra điều
   khoản. Điều khoản của Google có thay đổi."*
2. **`docs/project-changelog.md`** — mục cho tính năng, nêu rõ nguồn dữ liệu tuyến đường là OSM.
3. **`plans/.../reports/`** — báo cáo dev + ảnh chụp attribution (bằng chứng của G7).
4. **`plans/260824-1137-realtime-navigation-to-member/plan.md`** — xác nhận đã có nhãn superseded.
5. **`local.properties.example`** — 4 khoá, link lấy key, ghi rõ khoá nào bắt buộc cho engine nào.

## Todo List

- [x] G1–G9 xanh, kết quả ở `reports/gate-log-2026-08-24.txt`
- [x] `LLM.md` §3 (4 module), §6, §7, §10 (sửa "6 quyền" → 7), §11, §12, §14
- [x] `LLM.md` §13 Open — 8 mục (#6–#13), nhiều hơn 4–5 dự kiến: thêm bản sao `haversineMeters` ở
      `ValhallaDirectionsMapper` (#12) và FOSSGIS-chỉ-dev (#13)
- [x] Kiểm tra drift `debugBuild` ở §3 — còn sót ở hai chỗ khác (§3 `:data/util/`, §3 `appConfigModule`)
      mô tả nó như cơ chế hiện tại; đã sửa cả hai
- [x] `docs/routing-and-map-attribution.md` — người kiểm `duylt`, ngày 2026-08-24. Dòng redistribution
      giữ ⬜ vì **chưa gửi thư**, và ghi rõ im lặng không phải là đồng ý
- [x] `docs/project-changelog.md`
- [x] Ảnh chụp attribution vào `reports/` — cộng ảnh bản màu bị loại làm đối chứng
- [x] `local.properties.example` — đủ 4 khoá routing, đã có sẵn từ phase-01
- [x] Xác nhận `local.properties` trong `.gitignore` (`.gitignore:9`) và `git log -p` rỗng

## Phát sinh khi chạy phase này

**0. Chính gate G9 từng là chỗ rò key.** Bản đầu của bảng gate ở trên viết thẳng
`grep -r <8 ký tự đầu của key thật> data/src/test/resources/`. Tức là một cái gate dựng ra để
canh key không lọt vào repo lại tự mang một mảnh key vào repo, và commit `8172499` đã ghi nó vào
lịch sử git. Mảnh 8 ký tự hex không đủ để dùng key, nhưng nó vẫn là dữ liệu bí mật nằm trong một
file có thể chia sẻ, và quy tắc dự án nói **không commit thông tin bí mật**, không nói "trừ khi
ngắn". Đã đổi gate sang đọc key ra khỏi `local.properties` lúc chạy. **Lịch sử git vẫn còn mảnh
đó ở `8172499`** — xem mục Security Considerations bên dưới.


**1. Màu polyline không thể xác minh bằng đọc code.** Bản đầu chọn hổ phách `0xFFFFD600` và lập luận
đúng — nó không trùng màu nào trong bảng màu của app. Nhưng ràng buộc thật là "phân biệt được với
**nội dung Google**", và Google tô đường chính của basemap bằng đúng dải vàng đó. Chỉ nhìn ảnh chụp
mới thấy. Đã đổi sang magenta `0xFFE10098`. Đây đúng là lý do G7 đòi ảnh chứ không đòi đọc code.

**2. `LLM.md` là chỗ dễ nhiễm sai sự thật nhất trong cả plan.** Bản nháp tài liệu của phase này từng
ghi `RoutingHttpClient.kt  Ktor client` (nó bọc OkHttp — và Ktor chính là thứ bị loại), khai Ktor là
dependency mới trong changelog, gán US-25→US-31 cho màn dẫn đường (chúng nói về chống trùng thông báo
và lộ trình lịch sử), đếm `TrackingConstants` thành 18 thay vì 19, và dán nhãn `phase-05` lên mọi thứ
của routing — trong khi `phase-05` của repo là **màn Map**. Mọi mục đã sửa. Bài học ghi lại vì nó sẽ
lặp: **tài liệu sai nguy hiểm hơn tài liệu thiếu, vì nó được tin.** Mỗi con số và mỗi snippet trong
`LLM.md` phải đối chiếu lại với code thật trước khi commit, không nhận từ báo cáo.

## Success Criteria

1. Chín gate xanh, có bằng chứng lưu lại (log + ảnh).
2. Người đọc `LLM.md` lần đầu tìm đúng chỗ đặt một provider thứ ba mà không cần đọc plan này.
3. `docs/routing-and-map-attribution.md` trả lời được "vì sao được phép" và "phải giữ gì" trong
   một lần đọc.
4. `git log -p -- local.properties` rỗng.

## Risk Assessment

| Rủi ro | Xác suất | Giảm thiểu |
|---|---|---|
| Bỏ qua phase này vì "tính năng chạy rồi" | **Cao** | Đây là rủi ro thật của mọi phase tài liệu. `LLM.md` sai còn tệ hơn `LLM.md` không có: nó được tin |
| Chỉ sửa §3 mà quên §10/§14 | Trung bình | Bảng ở trên là checklist, không phải gợi ý |
| Ảnh chụp attribution chụp nhầm lúc chưa có tuyến | Trung bình | Ảnh phải thấy đồng thời: polyline, credit OSM, logo Google |

## Security Considerations

- **Mảnh key trong lịch sử git.** `8172499` chứa 8 ký tự đầu của `GRAPHHOPPER_API_KEY` thật (xem
  mục "Phát sinh" #0). Tại thời điểm ghi chú này, `origin/main` còn ở `bc2f9e1` và **toàn bộ commit
  chứa mảnh đó chưa được push**, nên nó chưa rời khỏi máy dev. Hai lựa chọn, phải chọn **trước lần
  push đầu tiên**: (a) viết lại lịch sử để bỏ mảnh đó — rẻ vì chưa push; hoặc (b) chấp nhận và
  xoay key. Không làm gì rồi push là lựa chọn duy nhất sai.
- `git log -p -- local.properties` phải rỗng. Nếu key đã lỡ commit: **xoay key trước**, dọn lịch sử sau.
- Xác nhận `FtdLog` ở release không in gì liên quan tới routing.
- Quota trần đã đặt ở console cả GraphHopper lẫn Stadia, ghi mức trần vào `docs/`.

## Next Steps

Đóng plan. Việc mở ra sau đó, theo thứ tự giá trị: (1) trả lời câu hỏi redistribution của GraphHopper,
(2) đề nghị BA bổ sung user story dẫn đường vào PRD, (3) turn-by-turn từ `maneuvers` — cộng thêm
được mà không phải sửa cổng `RoutingProvider`.
