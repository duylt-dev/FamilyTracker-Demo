# PRD Delta — Di chuyển mượt, bám đường của thành viên được theo dõi

**Project Name:** FamilyTrackerDemo
**Document ID:** FTD001-PRD-DELTA-01
**Version:** 0.1 (draft)
**Date:** 2026-08-25
**Status:** Draft — chờ chủ dự án / BA duyệt trước khi trộn vào PRD
**PRD đích:** `docs/FTD001_FamilyTrackerDemo_PRD.md` v1.2 → đề xuất thành v1.3
**Plan:** `plans/260825-0956-smooth-road-following-member-movement/`

> **File này KHÔNG phải PRD, và không được thay thế PRD.** Nó là một đề xuất trộn: mỗi mục dưới
> đây ghi rõ nó sửa mục nào của PRD, bằng đúng lược đồ ID (`US-xx`, `F-x`) và đúng kiểu đánh số của
> PRD, để người có thẩm quyền chép vào PRD trong một lần sửa. Việc sửa `docs/FTD001_FamilyTrackerDemo_PRD.md`
> là quyết định của con người, không phải hệ quả của tài liệu này.

**Ngôn ngữ:** tiếng Việt — cùng ngôn ngữ với PRD đích. Delta viết bằng tiếng khác thì không trộn
vào được.

---

## Mục lục

1. [Tóm tắt thay đổi](#1-tóm-tắt-thay-đổi)
2. [Hiện trạng vs. Mục tiêu](#2-hiện-trạng-vs-mục-tiêu)
3. [Delta yêu cầu](#3-delta-yêu-cầu)
4. [Delta ngưỡng — PRD §6](#4-delta-ngưỡng--prd-6)
5. [Delta UI — PRD §5.2](#5-delta-ui--prd-52)
6. [Nợ tài liệu kế thừa phải đóng cùng lần sửa này](#6-nợ-tài-liệu-kế-thừa-phải-đóng-cùng-lần-sửa-này)
7. [Ngoài phạm vi — nói rõ cái gì bị cấm](#7-ngoài-phạm-vi--nói-rõ-cái-gì-bị-cấm)
8. [Câu hỏi cần chủ dự án trả lời](#8-câu-hỏi-cần-chủ-dự-án-trả-lời)

---

## 1. Tóm tắt thay đổi

Thành viên được theo dõi (Minh/Lan) hiện đi lung tung: marker nhảy từng cú 50m mỗi 2,5 giây (≈72
km/h) theo đường chim bay xuyên qua nhà cửa, hướng đi đổi ngẫu nhiên mỗi chặng, và thỉnh thoảng
biến mất rồi hiện lại cách đó hàng nghìn km. Thay đổi này làm ba việc, và **chỉ** ba việc: (1) đường
đi của **dữ liệu mô phỏng** phải bám theo một tuyến đường thật lấy từ OpenStreetMap, không còn là
đoạn thẳng nội suy; (2) marker trên bản đồ — của **cả** thành viên mô phỏng **lẫn** người dùng thật
— được nội suy vị trí và hướng ở **tầng hiển thị**, nên chuyển động liên tục thay vì giật cục,
trong khi dữ liệu ghi xuống Room vẫn là mẫu thô không đổi; (3) cú dời vị trí xa được hạ cấp thành
**một lần duy nhất lúc bắt đầu mô phỏng**, sau đó mọi bước đều bám polyline. Ranh giới quan trọng
nhất của tài liệu này: **việc bám đường chỉ áp dụng cho dữ liệu mô phỏng.** Vị trí GPS thật hiển
thị đúng như nhận được — kể cả khi người dùng ở trong nhà, trong tầng hầm, sai số lớn — và **không
bao giờ** được nắn (map-matching / snap-to-road) về đường.

---

## 2. Hiện trạng vs. Mục tiêu

Mọi dòng "Hiện trạng" đều có dẫn chứng `file:dòng` đọc trực tiếp từ mã nguồn ngày 2026-08-25.

| # | Khuyết tật | Hiện trạng (dẫn chứng) | Mục tiêu |
|---|---|---|---|
| D1 | **Đi xuyên nhà** | `MemberRoamer.tick()` nội suy thẳng lat/lng theo tỉ lệ `stepMeters / distance` — `domain/src/main/kotlin/.../domain/tracking/MemberRoamer.kt:137-144`. Không có khái niệm "đường" ở bất kỳ đâu trong file | Mỗi bước của thành viên **mô phỏng** nằm trên một polyline tuyến đường lấy từ OSM (GraphHopper trực tuyến, hoặc nguồn dự phòng ngoại tuyến). Sai lệch tối đa so với polyline: `SIM_ROAD_TOLERANCE_M` (§4) |
| D2 | **Đổi hướng đột ngột** | `nextTarget()` chọn zone ngẫu nhiên (`zones[random.nextInt(zones.size)]` — `MemberRoamer.kt:167`) và hướng ngẫu nhiên quanh tâm (`random.nextDouble(FULL_CIRCLE_DEGREES)` — `MemberRoamer.kt:161, :172, :188`). Chặng mới bắt đầu ở bất kỳ góc nào so với chặng vừa xong | Hướng đi (bearing) liên tục: đích kế tiếp vẫn có thể ngẫu nhiên, nhưng **đường tới đích là tuyến đường**, nên góc quay bị hình học của đường ràng buộc thay vì nhảy tự do. Marker xoay theo bearing nội suy |
| D3 | **Nhảy ~13.000 km** | `MAX_WALK_M = 5_000.0` (`MemberRoamer.kt:84`) và nhánh dời vị trí ở `MemberRoamer.kt:122-130` được đánh giá **mỗi tick**, không phải một lần. Nguyên nhân gốc: `DemoDataSeeder` gieo thành viên ở TP.HCM — `DEMO_CENTER_LAT = 10.7769`, `DEMO_CENTER_LNG = 106.7009` (`data/src/main/java/.../data/seed/DemoDataSeeder.kt:54-55`) — còn zone người dùng tạo nằm ở vị trí GPS máy (emulator: Mountain View) | Cú dời giữ lại nhưng **hạ cấp thành spawn một lần** cho mỗi thành viên mỗi lần bắt đầu mô phỏng. Sau spawn, tuyệt đối không có cú dời nào nữa. Điểm spawn vẫn phải nằm **ngoài** ranh giới zone để lần cắt ranh giới sinh ENTER là thật (luật hiện có ở `MemberRoamer.kt:175-182`, giữ nguyên) |
| D4 | **Giật hình thấy rõ** | Marker **cố ý** không animate: `MemberMarkers.kt:36-38` ghi "Không animate vị trí giữa 2 lần cập nhật … đúng yêu cầu 'jump, không animate'", thực hiện bằng `rememberUpdatedMarkerState(position = …)` (`ui/src/main/java/.../ui/feature/map/component/MemberMarkers.kt:48`). Kết hợp với `STEP_METERS = 50.0` (`MemberRoamer.kt:58`) và `MEMBER_ROAM_INTERVAL_MS = 2_500` (`domain/.../TrackingConstants.kt:18`) → 20 m/s ≈ **72 km/h**, tự tính lại ở `MemberMovementSimulator.kt:157-158` | Nội suy vị trí **và** bearing ở tầng hiển thị cho **cả** marker thật lẫn marker mô phỏng. Giữa hai khung hình liên tiếp, marker dịch không quá `MEMBER_RENDER_MAX_JUMP_M` (§4). Dữ liệu ghi Room **không đổi** — vẫn là mẫu thô |
| D5 | **(bổ sung) Không có dữ liệu hướng** | `MemberMovementSimulator.kt:98` ghi cứng `bearingDegrees = 0f` cho mọi điểm mô phỏng. `LocationPoint.bearingDegrees` (PRD §9) tồn tại nhưng luôn bằng 0 ở nhánh mô phỏng | Bearing phải có giá trị thật (suy từ hai điểm liên tiếp trên polyline) thì marker mới xoay đúng. Không có nó thì "nội suy bearing" ở D4 không có gì để nội suy |
| D6 | **(bổ sung) Trong nhà là marker đứng hình** — **✅ Implemented phase-01** (`plans/260825-0956-smooth-road-following-member-movement/phase-01-hien-thi-vi-tri-that-trong-nha.md`, `reports/dev-phase-01-report.md`) | Chấm xanh của self đọc từ `location_points` qua `latestPerMember()` (`data/.../repository/MemberRepositoryImpl.kt:22-25` → `MapContract.kt:27`). Điểm chỉ vào bảng đó khi `LocationFilter` chấp nhận (`data/.../location/LocationPointProcessor.kt:33-38`), mà luật đầu tiên loại thẳng `accuracy > MAX_ACCURACY_M` = 50m (`domain/.../LocationFilter.kt:24-26`). Trong nhà sai số thường > 50m ⇒ **không điểm nào được ghi ⇒ chấm xanh đứng im ở vị trí ngoài trời cuối cùng** | Hiển thị vị trí thật **không** bị bộ lọc lịch sử chặn. `MAX_ACCURACY_M` tiếp tục chi phối cái được **ghi** vào `location_points` (US-31 giữ nguyên), không chi phối cái được **vẽ**. Đây là yêu cầu nghiệm thu đầu bảng của thay đổi này. **Implementation:** `LiveSelfLocation` (`data/location/`) — `LocationPointProcessor.process()` publish MỌI điểm trước khi lọc; `TrackingRepository.observeLiveSelfLocation()`; `MapState.selfLocation` ưu tiên nguồn live |

**D6 không nằm trong bản mô tả gốc của yêu cầu nhưng là điều kiện cần của nó.** Yêu cầu "trong
trường hợp thật vẫn phải tracking và hiển thị vị trí nếu user ở trong toà nhà" hiện **đang không
đạt**, và không đạt vì một lý do độc lập hẳn với chuyện bám đường. Sửa bám đường mà bỏ D6 thì câu
nghiệm thu quan trọng nhất vẫn trượt.

---

## 3. Delta yêu cầu

### 3.0 Quy ước đánh số và một khoảng ID được giữ chỗ

PRD hiện có US-01→US-36 và F1→F5. Hai khối ID mới không được đụng nhau:

| Khối | Dành cho | Lý do |
|---|---|---|
| **US-37 → US-39, và F6** | **Giữ chỗ** cho màn Dẫn đường (Navigation) đang tồn tại trong code mà **không có user story nào trong PRD** | `LLM.md` §13 Open #8 ghi thẳng: "Tiếp xúc điểm là BA" — PRD viết trước khi plan routing tồn tại. Xem §3.4 dưới |
| **US-40 → US-46, và F7** | Thay đổi của tài liệu này | Không giẫm lên khối trên |

### 3.1 User story mới — PRD §2, thêm mục 2.8

Đề xuất một mục mới **§2.8 Chuyển động của thành viên & vị trí thật**, đặt sau §2.7 Timeline.

| ID | Trạng thái | User Story | P | Acceptance Criteria |
|---|---|---|---|---|
| US-40 | **NEW** | Là người dùng, tôi thấy thành viên được theo dõi di chuyển liên tục và mượt trên bản đồ, không nhảy giật | P0 | Giữa hai khung hình vẽ liên tiếp, marker dịch không quá `MEMBER_RENDER_MAX_JUMP_M` (§4). Marker xoay theo hướng đi, góc cũng nội suy. Ngoại lệ **duy nhất** được phép nhảy: cú spawn một lần của US-42. Áp dụng cho **cả** marker thành viên mô phỏng **lẫn** chấm xanh của người dùng thật |
| US-41 | **NEW** | Là người dùng, tôi thấy thành viên mô phỏng đi trên đường, không xuyên qua nhà cửa | P0 | Mọi vị trí mô phỏng nằm trong `SIM_ROAD_TOLERANCE_M` (§4) tính từ polyline tuyến đường đang theo. **Chỉ áp dụng cho dữ liệu mô phỏng.** Một điểm GPS thật không bao giờ bị kiểm tra theo luật này, và không bao giờ bị dời để thoả luật này |
| US-42 | **NEW** | Là người dùng, tôi thấy thành viên xuất hiện đúng một lần lúc bắt đầu, rồi sau đó không bao giờ "biến mất chỗ này hiện ra chỗ khác" | P1 | Đúng **một** cú dời vị trí cho mỗi thành viên cho mỗi lần khởi động mô phỏng. Mọi tick sau đó là bước đi bám polyline. Điểm spawn nằm **ngoài** ranh giới mọi zone mà thành viên đó sắp đi vào (giữ lần cắt ranh giới sinh ENTER) |
| US-43 | **NEW · ✅ Implemented phase-01** | Là người dùng đang ở trong nhà / tầng hầm / nơi GPS yếu, tôi vẫn thấy vị trí của mình trên bản đồ và vẫn đang được theo dõi | **P0** | Marker vị trí thật **không** biến mất và **không** đứng hình khi `accuracy > MAX_ACCURACY_M` (50m). Vị trí hiển thị **đúng như thiết bị báo về**, không dời, không làm tròn về đường. Chỉ báo trực quan cho biết độ chính xác đang thấp (vòng sai số hoặc nhãn), **không** phải thông báo lỗi. `MAX_ACCURACY_M` tiếp tục chi phối việc **ghi** vào `location_points` — xem US-31, không đổi. **Kiểm chứng:** `LocationPointProcessorTest`, `MapViewModelTest` (QA-SRM-18→20), chạy thật `emulator-5554` (`reports/dev-phase-01-report.md`) |
| US-44 | **NEW · ✅ Implemented phase-01** | Là người dùng, tôi tin rằng app không bao giờ "sửa" vị trí thật của tôi cho đẹp mắt | P0 | Không có map-matching, không có snap-to-road, không có kéo về tuyến đường cho bất kỳ điểm nào đến từ nguồn vị trí thật — kể cả khi điểm đó rơi giữa toà nhà, giữa hồ, hay giữa hai làn đường. Nội suy ở tầng hiển thị chỉ được **nội suy giữa hai mẫu thật liên tiếp**, không được kéo mẫu về phía đường. **Kiểm chứng:** `RealGpsNoSnapArchitectureTest` (QA-SRM-21), mutation thật đỏ-rồi-xanh — xem `reports/dev-phase-01-report.md` |
| US-45 | **NEW · thu hẹp 2026-08-25 (D8)** | Là người dùng, khi dịch vụ tuyến đường không dùng được **trong khi máy vẫn có internet**, tôi vẫn thấy thành viên mô phỏng đi lại mượt trên đường | P1 | Áp dụng cho: thiếu khoá API, nhà cung cấp trả 401 / 429 / 400, timeout. Thành viên **không** đứng yên, **không** quay về đường chim bay xuyên nhà, chuyển động vẫn thoả US-40. Không thông báo lỗi kỹ thuật nào lên màn hình. Ghi công vẫn đúng theo US-46. **Ca mất internet KHÔNG thuộc user story này nữa** — xem US-47 |
| US-46 | **NEW** | Là người dùng, tôi thấy dòng ghi công nguồn dữ liệu khi trên màn hình có một tuyến đường lấy từ OpenStreetMap | P0 | Khi và **chỉ khi** một polyline tuyến đường có nguồn OSM đang được vẽ: hiện `© OpenStreetMap contributors` cùng tên nhà cung cấp, ở dải riêng **ngoài** khung bản đồ, không che và tách bạch khỏi ghi công/logo của Google. Không có dữ liệu OSM trên màn thì **không** hiện dòng đó |
| US-47 | **NEW 2026-08-25 (D8)** | Là người dùng, khi máy tôi không có internet, tôi được báo thẳng thay vì nhìn một bản đồ trông như đang chạy | P0 | Máy không có internet đã kiểm chứng (`NetworkCapabilities` thiếu `NET_CAPABILITY_INTERNET` **hoặc** thiếu `NET_CAPABILITY_VALIDATED`, nên wifi captive portal cũng tính là mất) ⇒ màn **Bản đồ** bị chặn bằng dialog "mất mạng", `cancelable = false`: không nút đóng, chạm ngoài không đóng, Back không đóng. Có internet trở lại ⇒ dialog **tự** đóng, không cần thao tác. **Chỉ** màn Bản đồ — Zone, Lịch sử, Cài đặt vẫn dùng được. Theo dõi GPS thật **vẫn chạy nền** suốt thời gian đó: service, ghi `location_points`, ENTER/EXIT zone không đứt. **Implementation: phase-07** (`data/network/AndroidNetworkMonitor.kt` → `domain/repository/NetworkMonitor.kt` → `MapState.hasInternet` → `ui/feature/map/component/NoInternetOverlay.kt`). Cơ chế đã chốt là **lớp phủ trong nội dung**, không phải `Dialog` — chữ "dialog" ở đây là hành vi người dùng thấy, không phải API. **Bổ sung 2026-08-26:** **công tắc theo dõi vẫn bấm được** trong lúc bị chặn — theo dõi chạy hoàn toàn ngoại tuyến (GPS + Room), chỉ TUYẾN ĐƯỜNG mới cần internet. Chặn cả công tắc thì mở app lúc ngoại tuyến với theo dõi đang tắt sẽ không ghi được điểm vị trí nào — đúng cái lỗ mà D8 sinh ra để chặn (`LLM.md` §13 Fixed #33). Nút "Chỉ đường" thì vẫn bị chặn, vì màn Dẫn đường thật sự cần mạng. **Sai lệch tài liệu:** dòng này (và QA-SRM-39) liệt kê màn "Cài đặt", nhưng app KHÔNG có màn Cài đặt — thanh tab chỉ có Bản đồ / Zone / Lịch sử / Nhật ký. Đọc là "mọi màn khác" |

**US-43 là câu nghiệm thu đầu bảng của cả thay đổi.** Nếu chỉ một tiêu chí được kiểm trước buổi
demo, kiểm nó.

### 3.2 User story bị sửa / được làm rõ — PRD §2

| ID | Trạng thái | Nội dung delta |
|---|---|---|
| US-06 | **MODIFIED · phần vẽ trong nhà ✅ Implemented phase-01, phần mượt (US-40) chưa** | AC hiện tại: "Marker xanh dương tại vị trí thiết bị; camera tự canh vào vị trí đó lần đầu mở". **Thêm:** marker cập nhật kể cả khi độ chính xác vượt `MAX_ACCURACY_M`, và di chuyển mượt theo US-40. Lý do: hiện marker đọc gián tiếp qua bảng đã lọc (`MemberRepositoryImpl.kt:22-25` ← `LocationPointProcessor.kt:33-38`), nên trong nhà nó đứng hình — xem D6. **Kiểm chứng phần đã làm:** `MapContract.selfLocation` ưu tiên `observeLiveSelfLocation()` — `reports/dev-phase-01-report.md` |
| US-08 | **MODIFIED** | AC hiện tại: "2–3 marker màu khác nhau, có tên; bấm vào hiện tên + thời điểm cập nhật gần nhất". **Thêm:** marker di chuyển mượt và xoay theo hướng đi (US-40); đường đi bám đường (US-41). Ghi chú: KDoc hiện tại ở `MemberMarkers.kt:36-38` khẳng định ngược lại ("jump, không animate") — dòng đó phải được sửa cùng lúc, nếu không code và PRD nói hai chuyện khác nhau |
| US-31 | **UNCHANGED-BUT-CLARIFIED · ✅ Xác nhận phase-01** | "Điểm có `accuracy > 50m` … bị loại **trước khi vẽ [lộ trình lịch sử]**". Làm rõ: luật này chi phối **lịch sử** (`location_points` + polyline tab Lịch sử), **không** chi phối marker vị trí trực tiếp trên màn Bản đồ. Hôm nay hai thứ này dùng chung một đường dữ liệu, và đó chính là D6. **Kiểm chứng:** `LocationFilterTest`/`LocationPointProcessorTest` cũ giữ nguyên assertion, xanh nguyên trạng; xác nhận thật `emulator-5554` — Lịch sử không nhận điểm bị `Reject` (QA-SRM-24, `reports/dev-phase-01-report.md`) |
| US-33 | **UNCHANGED-BUT-CLARIFIED** | Nút "Mô phỏng lộ trình" (F5) mô phỏng lộ trình của **chính mình** bằng `RouteBlueprint` (đường thẳng cắt qua zone). Thay đổi này **không** đụng vào F5. Việc có mở rộng bám đường sang F5 hay không là câu hỏi Q4 ở §8 |
| US-25 | **UNCHANGED** | Khử trùng lặp 60s giữ nguyên. Đây là **ràng buộc** của thay đổi này, không phải mục tiêu: đổi nhịp tick sẽ đổi số tick dwell, và bất biến "dwell dài hơn cửa sổ khử trùng lặp" phải tiếp tục đúng theo cấu trúc |
| US-26 | **UNCHANGED** | Chống rung ranh giới (`vào khi d < R`, `ra khi d > R + 30m`) giữ nguyên. Bám đường không được làm thành viên men theo mép zone rồi dội ENTER/EXIT |

### 3.3 Feature mới — PRD §3

Thêm dòng vào bảng §3.0 và một mục §3.6:

| ID | Feature | Screens liên quan | Story | Ưu tiên |
|---|---|---|---|---|
| **F7** | **Member Movement Simulation (bám đường)** | Map | US-08, US-40→US-42, US-45, US-46, US-47 | P0 |

**§3.6 F7 — Member Movement Simulation (đề xuất)**

| Thuộc tính | Giá trị |
|---|---|
| Chủ thể | **Chỉ** thành viên được theo dõi (`isSelf = false`). Không bao giờ áp dụng cho self |
| Nguồn đường | **Lai (hybrid):** nhà cung cấp tuyến đường trực tuyến khi có khoá + có mạng; nếu không, một nguồn polyline ngoại tuyến đóng sẵn trong app |
| Chuyển nguồn | Tự động, im lặng. Người dùng không thấy thông báo lỗi, không thấy nút chọn nguồn |
| Bám đường | Mọi vị trí mô phỏng nằm trong `SIM_ROAD_TOLERANCE_M` tính từ polyline đang theo |
| Spawn | Đúng **một lần** cho mỗi thành viên mỗi lần bắt đầu mô phỏng, ra **ngoài** ranh giới zone đích |
| Tốc độ | `SIM_MEMBER_SPEED_MPS` (§4) — xấp xỉ tốc độ xe máy trong đô thị, thay cho ≈72 km/h hiện tại |
| Dwell | Giữ luật hiện có: thời gian đứng yên trong zone **suy ra** từ `EVENT_DEDUPE_WINDOW_MS`, không chọn tay (`MemberRoamer.kt:72-74`) |
| Bất biến bắt buộc giữ | Mỗi vòng đúng **một** ENTER rồi đúng **một** EXIT, xen kẽ, không dội. `MemberRoamerTest.kt:96-117` khoá bất biến này; `LLM.md:1090` gọi nó là "lời hứa duy nhất cả tính năng dựa vào" |
| Ghi dữ liệu | Không đổi: điểm mô phỏng ghi qua `MemberRepository.recordLocation`, **không** đi qua `LocationFilter` (lý do ở `MemberMovementSimulator.kt:35-38`) |
| Ghi công | Bắt buộc theo US-46 khi có polyline nguồn OSM hiện trên màn |

### 3.4 Quan hệ với tính năng Dẫn đường chưa có tài liệu (LLM.md §13 Open #8)

**Nói thẳng, không che:** thay đổi này dựng trên cùng hạ tầng routing (`RoutingProvider` —
`domain/src/main/kotlin/.../domain/repository/RoutingProvider.kt:19-21`; `Directions` —
`domain/.../model/Directions.kt:22-27`; `ObserveNavigationUseCase` là nơi **duy nhất** gọi provider —
`domain/.../usecase/ObserveNavigationUseCase.kt:20, :61-66`) mà **PRD v1.2 không mô tả một dòng
nào**. `LLM.md` §13 Open #8 đã ghi nhận và chỉ đích danh BA là tiếp xúc điểm.

Hệ quả cho tài liệu này:

1. US-40→US-46 **được viết ở tầng người dùng nhìn thấy** (marker mượt, đi trên đường, có ghi công),
   không tham chiếu tới màn Dẫn đường, nên chúng nghiệm thu được ngay cả khi khoảng trống kia chưa
   được lấp.
2. Nhưng US-45 và US-46 **thừa hưởng** cả rủi ro pháp lý lẫn ràng buộc nhà cung cấp của hạ tầng
   chưa có tài liệu đó (`docs/routing-and-map-attribution.md` §5 còn một mục ⬜ chưa đóng).
3. **Khuyến nghị:** trong cùng lần cập nhật PRD, backfill một khối US-37→US-39 + F6 cho màn Dẫn
   đường. Không làm thì mỗi thay đổi kế tiếp lại đẻ thêm một delta treo trên một tính năng không có
   hợp đồng nghiệm thu. Việc backfill **không** thuộc phạm vi vòng này — đây là khuyến nghị, không
   phải yêu cầu.

---

## 4. Delta ngưỡng — PRD §6

Bảng §6 hiện có 13 dòng. Delta đề xuất: **2 dòng đổi giá trị, 1 dòng đổi ngữ nghĩa, 4 dòng mới, 2
dòng làm rõ.** Mọi ô ghi "TBD — planner xác nhận" là chỗ tài liệu này **không** có căn cứ để chốt.

### 4.1 Hằng số đổi

| Hằng số | Cũ | Mới | Vì sao |
|---|---|---|---|
| `MemberRoamer.STEP_METERS` | `50.0` | **suy ra từ `SIM_MEMBER_SPEED_MPS × (MEMBER_ROAM_INTERVAL_MS / 1000)`**, không còn là hằng số tự do | 50m/2,5s = 20 m/s ≈ 72 km/h (`MemberMovementSimulator.kt:157-158` tự tính lại đúng con số này). Đây là nguồn trực tiếp của cảm giác giật. Neo vào tốc độ thay vì vào bước đi để đổi nhịp tick không âm thầm đổi tốc độ |
| `TrackingConstants.MEMBER_ROAM_INTERVAL_MS` | `2_500` | **Xác nhận phase-02: giữ nguyên `2_500`, không đổi** (`decisions.md` §C1) | Nội suy đã chuyển sang tầng hiển thị (D3) qua bảo toàn đỉnh của `PolylineFollower` (phase-02), nên nhịp lấy mẫu thật sự không còn quyết định độ mượt — hạ nó xuống chỉ trả giá lưu trữ (14 400 điểm/giờ/thành viên ở 250ms so với ≈1 980 ở 2 500ms) mà không mua thêm gì. `DWELL_TICKS` (`MemberRoamer.kt`) giữ nguyên 30, tiếp tục > `EVENT_DEDUPE_WINDOW_MS` (60 000ms) |
| `MemberRoamer.MAX_WALK_M` | `5_000.0` | **Xác nhận phase-02: giữ giá trị, đổi ngữ nghĩa — KHÔNG bỏ hằng số** | Nhánh dời vị trí giờ chỉ được tham chiếu **một lần** mỗi thành viên mỗi lần bắt đầu mô phỏng (`RoamState.hasSpawned`, US-42): quá xa **và chưa spawn** → dời; quá xa **và đã spawn** → bỏ đích đó, thay bằng đích đi loanh quanh (`LegKind.WANDER`), không dời thêm. Giữ hằng số vì nó vẫn là ngưỡng phân biệt "đi bộ được" với "phải xử lý đặc biệt" — không có cách nào rẻ hơn để quyết định spawn mà không cần nó |

### 4.2 Hằng số mới

| Hằng số | Giá trị đề xuất | Ảnh hưởng khi đổi |
|---|---|---|
| `SIM_MEMBER_SPEED_MPS` | **8.3** (≈30 km/h) — **Xác nhận phase-02, chốt làm giá trị khởi điểm** (`decisions.md` §C1/§C5) | Lớn hơn → thành viên tới zone nhanh hơn, demo ngắn hơn, nhưng trông sai với đường phố. Nhỏ hơn → một vòng vào/ra zone kéo dài. **Chưa đo thật trên thiết bị** — `decisions.md` §C5 định luật đổi số dựa trên ENTER→EXIT đo được ở phase-06 (≤180s giữ nguyên, 180–260s nâng 11.1, >260s nâng 13.9 — trần cứng). **Sửa (phase-03):** không có file `MemberRoamerLapTimeTest` nào trong repo — phép đo tất định (cận dưới, không cần máy) là các test đếm nhịp có sẵn trong `MemberRoamerTest` (`domain/src/test/.../tracking/MemberRoamerTest.kt`); phép đo THẬT trên `emulator-5554` là việc của phase-06 (`decisions.md` §C5, `phase-06-do-luong-gate-va-tai-lieu.md`), chưa làm ở đây |
| `SIM_ROAD_TOLERANCE_M` | **10.0** — **Xác nhận phase-02, chốt** (`PolylineFollowerTest`, `RouteGeometryGuardTest`) | Ngưỡng QA dùng để phán "có nằm trên đường không" (US-41). Lớn hơn → test không bắt được lỗi lệch đường. Nhỏ hơn → test đỏ vì sai số hình học của phép giải mã polyline (phase-04) chứ không phải vì lỗi thật. Phase-02 bảo toàn đỉnh (`PolylineFollower.advance`) giữ sai lệch THẬT ở 0m nên ngưỡng này còn nhiều dư địa cho sai số của phase-04 |
| `MEMBER_RENDER_MAX_JUMP_M` | **2.0** — **Chốt phase-03, sống trong file test, KHÔNG vào `TrackingConstants`** (NFR-3) | Ngưỡng NGHIỆM THU "không giật" (US-40, QA-SRM-05), không phải hằng số runtime — không một dòng code sản phẩm nào đọc nó. Suy ra: ở `SIM_MEMBER_SPEED_MPS` = 8.3 và 60fps, mỗi khung hình đi ≈ 0.14m; trần 2.0m cho hơn 10 lần dự phòng khi rớt khung, mà vẫn nhỏ hơn bước 50m hiện tại 25 lần |
| `MEMBER_RENDER_FRAME_MS` | **KHÔNG THÊM — chốt phase-03** (phase-03 Key Insight #4) | Đề xuất ban đầu là nhịp nội suy cố định. Phase-03 không dùng: `durationMs` mỗi cặp mẫu suy ra trực tiếp từ hiệu `recordedAt` của hai mẫu gần nhất (`coerceIn(1, 5_000)`), không phải một hằng số cố định — công thức này đúng cho CẢ nguồn mô phỏng (2 500ms) LẪN GPS thật (10 000ms) mà không cần biết đang xem nguồn nào. `withFrameNanos` đã tự theo nhịp làm mới màn hình của thiết bị; thêm một hằng số nhịp riêng chỉ là một chỗ nữa để số liệu lệch khỏi code thật (đúng lỗi mà §13 Open #7 đang cảnh báo) |

**Hai hằng số đầu là ngưỡng của PRD §6 thật** (chúng quyết định hành vi người dùng nhìn thấy và QA
phải đọc được ở một chỗ). `MEMBER_RENDER_MAX_JUMP_M` là ngưỡng nghiệm thu, sống trong test.
`MEMBER_RENDER_FRAME_MS` đã bị bác ở phase-03 — không đưa vào `TrackingConstants` dưới bất kỳ tên nào.

### 4.3 Hằng số **không** đổi, chỉ làm rõ phạm vi

| Hằng số | Giá trị | Làm rõ |
|---|---|---|
| `MAX_ACCURACY_M` | `50` — **KHÔNG ĐỔI** | Chi phối **việc ghi vào `location_points`** (US-31, lộ trình lịch sử sạch). **Không** chi phối việc **vẽ** vị trí thật lên bản đồ (US-43). Hôm nay hai việc đó dùng chung một đường dữ liệu, và đó là D6 |
| `MAX_SPEED_KMH` | `200` — **KHÔNG ĐỔI** | Điểm mô phỏng vốn không đi qua `LocationFilter` (`MemberMovementSimulator.kt:35-38`), nên hạ tốc độ mô phỏng không đụng tới luật này |
| `ZONE_EXIT_BUFFER_M` | `30` — **KHÔNG ĐỔI** | Ràng buộc: `MemberRoamer.LEAVE_MARGIN_M` (`120.0`, `MemberRoamer.kt:81`) phải tiếp tục lớn hơn nó, nếu không EXIT không bao giờ sinh ra |
| `EVENT_DEDUPE_WINDOW_MS` | `60_000` — **KHÔNG ĐỔI** | Neo của `DWELL_TICKS`. Xem ràng buộc ở §4.1 |

### 4.4 Nợ kế thừa: 7 hằng số không có nhà trong PRD §6

`LLM.md` §13 Open #7: `TrackingConstants` có 19 hằng số, chỉ 12 truy được về PRD §6. Bảy cái không
có nguồn PRD, và **chúng đã tồn tại trước thay đổi này**:

| Hằng số | Giá trị hiện tại | Nguồn thật |
|---|---|---|
| `MEMBER_ROAM_INTERVAL_MS` | `2_500` (`TrackingConstants.kt:18`) | fix-zone-follows-members |
| `OFF_ROUTE_TOLERANCE_M` | `45.0` (`TrackingConstants.kt:64`) | research của plan routing phase-04 |
| `OFF_ROUTE_CONSECUTIVE_SAMPLES` | `3` (`TrackingConstants.kt:72`) | như trên |
| `DESTINATION_MOVED_TOLERANCE_M` | `200.0` (`TrackingConstants.kt:80`) | như trên |
| `REROUTE_DEBOUNCE_MS` | `60_000` (`TrackingConstants.kt:88`) | như trên |
| `ARRIVAL_M` | `50.0` (`TrackingConstants.kt:96`) | như trên |
| `ARRIVAL_EXIT_M` | `70.0` (`TrackingConstants.kt:104`) | như trên |

**Khuyến nghị:** đưa cả bảy vào §6 trong cùng lần cập nhật, **không đổi giá trị nào**, chỉ chép
đúng con số đang chạy kèm một dòng "ảnh hưởng khi đổi". Bốn hằng số mới ở §4.2 cộng vào một §6
chưa dọn sẽ đẩy tỉ lệ truy nguyên được từ 12/19 xuống 12/23. Đây là cơ chế duy nhất chặn cái trôi
đó lớn thêm.

---

## 5. Delta UI — PRD §5.2

| Vai trò | Màu | Dùng ở đâu | Trạng thái |
|---|---|---|---|
| Routed polyline (nguồn OSM) | `#E10098` | Polyline tuyến đường lấy từ OSM, ở **bất kỳ** màn nào vẽ nó | **NEW trong PRD** — màu đã tồn tại trong code (`ui/.../designsystem/theme/Color.kt:60` `NavigationRouteColor`), chỉ là §5.2 chưa từng ghi. Bắt buộc phân biệt trực quan với nội dung của Google — `docs/routing-and-map-attribution.md` §3 mục 2 |
| Member colors | `#1B6EF3` `#E5820C` `#7B3FF2` | Ba thành viên | **UNCHANGED** — không dùng lại cho polyline tuyến đường, nếu không polyline OSM lẫn với dữ liệu của app |

**Dải ghi công (US-46):** đặt **ngoài** khung bản đồ, không phải overlay chồng lên — góc dưới-trái
bên trong khung là logo/ghi công của Google, không được che, không được dời
(`docs/routing-and-map-attribution.md` §3 mục 1). Thành phần đã tồn tại:
`ui/.../designsystem/component/RoutingAttribution.kt` (chuyển nhà từ
`feature/navigation/component/` ở phase-05 — LLM.md §12: đủ 2 chỗ dùng), ba trạng thái giữ nguyên.

**Q9 chốt (phase-05):** **không vẽ polyline** tuyến đường của thành viên mô phỏng lên màn Bản đồ.
**Vẫn hiện dải ghi công US-46** trên chính màn đó khi đang chạy tầng PROVIDER/CACHE — vị trí marker
người dùng thấy là *Produced Work* suy ra từ dữ liệu OSM, nên vẫn phát sinh nghĩa vụ attribution dù
không có polyline nào trên màn hình (`decisions.md` §"Câu trả lời cho các câu hỏi treo", PRD Q9).
Rẻ vì dùng lại đúng composable trên; đóng luôn vùng xám đã nêu ở §8 Q9 dưới đây.

**Chuỗi hiện có, dùng lại, không viết mới:** `navigation_attribution_route` = "Tuyến đường: %1$s" và
`navigation_attribution_fallback` = "Đường thẳng ước tính — chưa có tuyến đường thật"
(`ui/src/main/res/values/strings.xml:93-94`).

---

## 6. Nợ tài liệu kế thừa phải đóng cùng lần sửa này

| # | Nợ | Hành động khi trộn delta |
|---|---|---|
| N1 | **Câu tổng kết §2 của PRD đang sai.** PRD ghi "36 user story — 22×P0, 11×P1, 3×P2". Đếm trực tiếp cột P của US-01→US-36 ra **26×P0, 8×P1, 2×P2** (tổng 36, đúng) — `LLM.md` §13 Open #3 | Delta thêm 7 story (5×P0: US-40/41/43/44/46; 2×P1: US-42/45). Câu tổng kết mới phải là: **"43 user story — 31×P0, 10×P1, 2×P2."** Sửa từ con số **đúng** (26/8/2), không cộng thêm vào con số sai |
| N2 | **PRD §8 mô tả sai chữ ký `purgeOlderThan`** (`Unit` thay vì `Int`), và thiếu hẳn method xoá của `ZoneEventRepository` — `LLM.md` §13 Open #1 | Không thuộc phạm vi delta này. Ghi lại để lần cập nhật PRD nào cũng thấy |
| N3 | **7 hằng số không truy được về §6** — xem §4.4 | Đưa vào §6 cùng lần sửa, giữ nguyên giá trị |
| N4 | **Màn Dẫn đường không có user story** — `LLM.md` §13 Open #8 | Backfill US-37→US-39 + F6, xem §3.4. Khuyến nghị, không bắt buộc |

---

## 7. Ngoài phạm vi — nói rõ cái gì bị cấm

Phân biệt hai loại: **không làm vòng này** (có thể làm sau) và **bị cấm** (không được làm, kể cả
sau, trừ khi có quyết định sản phẩm mới).

### 7.1 BỊ CẤM

| # | Điều bị cấm | Vì sao |
|---|---|---|
| X1 | **Map-matching / snap-to-road cho vị trí GPS thật.** Không nắn, không kéo về đường, không "làm đẹp" điểm thật ở bất kỳ tầng nào — data, domain, hay presentation | Người dùng ở trong nhà, trong hẻm, trong tầng hầm phải được hiển thị **đúng chỗ thiết bị báo**. Nắn về đường gần nhất là **nói dối về vị trí một con người**, và trong app theo dõi gia đình đó là loại lỗi tệ nhất có thể có. Nội suy hiển thị chỉ được nội suy **giữa hai mẫu thật liên tiếp** |
| X2 | **Ẩn hoặc đóng băng marker vị trí thật khi độ chính xác kém** | Đó chính là D6, và là điều yêu cầu gốc đòi phải sửa |
| X3 | **Bản đồ nền thứ hai** (tile của bên thứ ba hiện cùng lúc với Google Maps) | Hành vi bị cấm rõ ràng, không mơ hồ — `docs/routing-and-map-attribution.md` §3 mục 3 |
| X4 | **Mapbox hoặc HERE làm nguồn routing** | `docs/routing-and-map-attribution.md` §3 mục 4 — rủi ro nằm ở điều khoản của **họ**. Thêm nguồn mới phải xác thực ToS trước, tách riêng |
| X5 | **Hiện ghi công OSM khi trên màn không có dữ liệu OSM nào** | Ghi sai nguồn — `docs/routing-and-map-attribution.md` §3, đoạn "Chỉ hiện credit OSM khi đang thật sự hiển thị dữ liệu OSM" |

### 7.2 KHÔNG LÀM VÒNG NÀY

| # | Ngoài phạm vi | Ghi chú |
|---|---|---|
| Y1 | **Đổi lược đồ Room.** Không bảng mới, không cột mới, không đổi version | Quyết định đã chốt: nội suy sống ở tầng hiển thị; cái ghi xuống Room vẫn là mẫu thô như hôm nay |
| Y2 | **Định vị trong nhà bằng phần cứng** — BLE beacon, Wi-Fi RTT, UWB, dead-reckoning bằng cảm biến | Không phần cứng, không hiệu chuẩn, không thuộc bản demo. US-43 chỉ đòi **hiển thị đúng cái đang nhận được**, không đòi làm nó chính xác hơn |
| Y3 | **Hồ sơ định tuyến xe máy.** Chuyển động mô phỏng là **xấp xỉ** cách xe máy chạy, **không** phải một routing profile xe máy | Gói miễn phí của nhà cung cấp hiện tại chỉ có `[car, bike, foot]`; `motorcycle`/`scooter` trả 400 — kiểm thật 2026-08-24, `LLM.md` §13 Open #9. Xe máy là phương tiện mặc định ở Việt Nam, nên đây là một khoảng lệch có thật, đã biết, và được chấp nhận cho bản demo |
| Y4 | **Bám đường cho F5 Route Simulator** (nút "Mô phỏng lộ trình", US-33, `RouteBlueprint`) | F5 vẫn dựng đường thẳng cắt qua zone. Xem Q4 |
| Y5 | **Sửa `LLM.md` §13 Open #2** (`RouteBlueprint` vượt `MAX_SPEED_KMH` với zone bán kính lớn) | Khuyết tật riêng của F5, đã có quyết định giữ Open |
| Y6 | **Backend, đồng bộ nhiều thiết bị thật** | Vẫn ngoài phạm vi như PRD §1.2 |
| Y7 | **Khôi phục phát hiện vào/rời zone khi tiến trình đã chết** (US-24) | `LLM.md` §13 Open #4 — không sửa được nếu không có backend |

---

## 8. Câu hỏi cần chủ dự án trả lời

Đánh số tiếp nối PRD §A.4 (Q1→Q7 đã dùng).

| # | Câu hỏi | Giả định đang dùng | Ảnh hưởng nếu trả lời khác |
|---|---|---|---|
| **Q8** | **Đóng gói sẵn một tuyến đường lấy từ nhà cung cấp routing vào trong app — điều khoản có cho phép không?** Điều khoản "redistribution" của GraphHopper **chưa được làm rõ**: thư hỏi soạn 2026-08-24, **chưa gửi, chưa có trả lời**, và `docs/routing-and-map-attribution.md` §5 ghi rõ "im lặng không phải là đồng ý" | Tài liệu này **cố ý không** chỉ định cách hiện thực nguồn dự phòng. US-45 chỉ nghiệm thu **hành vi quan sát được**: vẫn di chuyển, vẫn mượt, vẫn trên đường, vẫn có ghi công | Nếu redistribution **không** được phép: nguồn dự phòng phải là polyline tự dựng từ dữ liệu OSM thô hoặc tự vẽ tay, không phải kết quả trả về của nhà cung cấp. US-45 không đổi một chữ; chỉ cách làm đổi. **Đây là câu hỏi chặn duy nhất của delta này** |
| **Q9** | Polyline tuyến đường của thành viên mô phỏng có **được vẽ lên màn Bản đồ** không, hay chỉ dùng ngầm để tính bước đi? | Không vẽ — chỉ dùng ngầm. Marker đi trên đường là đủ để mắt thấy | Nếu **có vẽ**: màn Bản đồ phải mang dải ghi công US-46, và §5.2 phải chốt màu polyline. Nếu **không vẽ**: có cần ghi công khi vị trí marker suy ra từ dữ liệu OSM không? Đây là vùng xám — khuyến nghị hỏi người có thẩm quyền, đừng tự quyết |
| **Q10** | Tốc độ mô phỏng nên là bao nhiêu? | ≈30 km/h (`SIM_MEMBER_SPEED_MPS = 8.3`) | Chậm hơn → trông thật hơn nhưng một vòng vào/ra zone lâu hơn, buổi demo phải ngồi chờ. Nhanh hơn → về lại đúng vấn đề đang sửa. **Phải đo lại thời gian một vòng** và đối chiếu kịch bản demo trước khi chốt |
| **Q11** | Bám đường có mở rộng sang **F5 Route Simulator** (US-33) không? | Không — F5 giữ nguyên `RouteBlueprint` đường thẳng | Nếu có: `RouteBlueprint` phải đổi, và `LLM.md` §13 Open #2 (vượt `MAX_SPEED_KMH` với zone lớn) phải được xử lý cùng lúc. Phạm vi tăng đáng kể |
| **Q12** | Trong nhà, hiển thị độ chính xác thấp bằng cách nào? | Vòng sai số mờ quanh marker | Nếu chủ dự án muốn nhãn chữ ("Độ chính xác thấp") thì cần chuỗi mới trong `strings.xml`, và cần chốt ngưỡng nào thì hiện nhãn |
| **Q13** | Ba thành viên có nên đi trên **ba tuyến đường khác nhau** không? | Có — mỗi người một tuyến, gieo ngẫu nhiên riêng (luật hiện có: `MemberMovementSimulator.kt:146`) | Nếu dùng chung một tuyến: hai marker chồng lên nhau, demo trông như một người. Nếu mỗi người một tuyến: số lần gọi nhà cung cấp nhân theo số thành viên — chạm trần hạn ngạch nhanh hơn |
| **Q14** | Có backfill user story cho màn **Dẫn đường** trong cùng lần cập nhật PRD không? (§3.4, `LLM.md` §13 Open #8) | Khuyến nghị có, đã giữ chỗ US-37→US-39 + F6 | Không backfill: mỗi thay đổi sau lại treo trên một tính năng không có hợp đồng nghiệm thu, và khoảng ID cứ phải giữ chỗ mãi |

### 8.1 Trả lời đã chốt (phase-04, `decisions.md` §C2/D5)

- **Q8 — không cần trả lời để làm.** D5 chốt nguồn dự phòng (tầng 3) là `SyntheticPath`: một hàm
  **tự sinh** ở `:domain/tracking/`, không đóng gói/redistribute bất cứ tuyến nào của nhà cung cấp.
  Câu hỏi vẫn nên gửi cho GraphHopper vì nó chặn phát hành của màn Dẫn đường
  (`docs/routing-and-map-attribution.md` §5), nhưng nó **không chặn** việc chuyển động của thành
  viên mô phỏng đi mượt trên một bản clone/CI không có khoá.
- **Q13 — có, đã hiện thực.** `MemberMovementSimulator.pathFor()` gọi `memberRouteProvider.path(...)`
  với `request.memberId` riêng của từng thành viên; khoá cache (`decisions.md` §C2 "Khoá cache")
  cũng bắt đầu bằng `memberId` — Minh và Lan không bao giờ đọc lại cache của nhau, kể cả khi cùng
  nhắm một zone ở cùng thời điểm.

---

## 9. Câu hỏi chưa giải được (từ phía người viết delta)

Khác với §8 — mục này là những chỗ **tài liệu này không đủ căn cứ để đề xuất**, chứ không phải chỗ
chờ chủ dự án chọn hướng.

1. **Thời gian một vòng vào/ra zone sau khi hạ tốc độ chưa được đo.** Con số duy nhất có thật là 63
   giây ở ≈72 km/h (`LLM.md` §13 Fixed #23, đo trên `emulator-5554`). Ở ≈30 km/h, phép nhân thẳng ra
   ~150 giây — nhưng bám đường làm quãng đường **dài hơn** đường chim bay, nên con số thật cao hơn
   nữa. Cần đo, không cần đoán. Ảnh hưởng trực tiếp tới nhịp buổi demo.
2. **Chưa rõ nội suy hiển thị đặt ở đâu cho đúng luật kiến trúc.** ViewModel không được import
   Compose (`.claude/CLAUDE.md`, luật MVI); một vòng lặp nội suy theo khung hình thì tự nhiên thuộc
   về composable. Ranh giới này là quyết định của planner, không phải của BA.
3. **Chưa rõ trên emulator có sinh ra được điểm GPS `accuracy > 50m` để kiểm US-43 hay không.** Nếu
   không, câu nghiệm thu đầu bảng phải kiểm bằng máy thật trong nhà — cùng loại chặn đã làm G4/G5
   phải HOÃN ở phase-11 (PRD §11.1).
4. **`MAX_WALK_M` sau khi hạ cấp thành spawn một lần thì còn cần tồn tại không** — phụ thuộc cách
   planner quyết định chọn điểm spawn.
5. **Q9 (có vẽ polyline hay không) đang chặn §5.2.** Không trả lời được Q9 thì không chốt được delta
   màu, và cũng không chốt được màn nào phải mang dải ghi công.
