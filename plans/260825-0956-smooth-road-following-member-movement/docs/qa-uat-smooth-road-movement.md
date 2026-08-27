# QA Test Cases & UAT Scenarios — Di chuyển mượt, bám đường của thành viên được theo dõi

**Project Name:** FamilyTrackerDemo
**Document ID:** FTD001-QA-SRM-01
**Version:** 0.1 (draft)
**Date:** 2026-08-25
**Status:** Draft — chờ duyệt cùng `prd-delta-smooth-road-movement.md`
**Nguồn yêu cầu:** `plans/260825-0956-smooth-road-following-member-movement/docs/prd-delta-smooth-road-movement.md`
(US-40→US-46, F7) · `docs/FTD001_FamilyTrackerDemo_PRD.md` v1.2 (US-06, US-08, US-25, US-26, US-31, US-33)

> Repo chưa từng có tài liệu QA/UAT nào — đã tìm trong `docs/` và toàn repo trước khi viết. Định
> dạng dưới đây bám kiểu bảng của PRD (cùng thang ưu tiên P0/P1/P2, cùng lối viết Acceptance
> Criteria) để đọc chéo hai tài liệu không phải đổi não.

---

## 1. Điều kiện chung

| Hạng mục | Giá trị |
|---|---|
| Thiết bị | `emulator-5554` cho vòng lặp chính; **một** lượt trên máy thật cho UAT-05 (trong nhà) — PRD §11.3 v1.2 quyết định #3 |
| Build | **Debug** cho mọi test đọc log. **Release** cho UAT — PRD §7.2 quy định bản đem demo là `release` |
| **Bẫy log** | `FTD_EVENT` **im hoàn toàn trên bản release** — cổng `FtdLog` gate theo `debugBuild` (gate G7, PRD §11.1). Mọi ca test dưới đây ghi "kiểm bằng log" **chỉ chạy được trên debug**. Trên release phải kiểm bằng mắt |
| Lệnh xem log | `adb logcat -s FTD_EVENT` |
| Dữ liệu ban đầu | App vừa cài mới: `DemoDataSeeder` gieo thành viên quanh `10.7769, 106.7009` (TP.HCM) — `data/src/main/java/.../data/seed/DemoDataSeeder.kt:54-55`; vị trí GPS emulator mặc định là Mountain View. **Khoảng lệch ~13.000km này là điều kiện tiên quyết của QA-SRM-07/08**, không phải lỗi cần dọn trước |
| Chạy test tự động | `./gradlew test --no-configuration-cache` (luật đo của gate G6, `ENV-BRIEFING.md` §8) |

**Ký hiệu loại test:** `unit` = JUnit JVM thuần (`:domain` hoặc `:data`, chạy < 5s) · `instr` =
instrumented / androidTest · `manual` = người kiểm bằng mắt trên thiết bị.

**Ngưỡng tham chiếu** (giá trị đề xuất, xem PRD delta §4 — mọi con số đánh dấu TBD ở đó thì ở đây
cũng là TBD): `MEMBER_RENDER_MAX_JUMP_M` = 2.0 · `SIM_ROAD_TOLERANCE_M` = 10.0 ·
`SIM_MEMBER_SPEED_MPS` = 8.3 · `EVENT_DEDUPE_WINDOW_MS` = 60_000 · `ZONE_EXIT_BUFFER_M` = 30 ·
`MAX_ACCURACY_M` = 50.

---

## 2. QA Test Cases

### 2.1 US-41 — Thành viên mô phỏng đi trên đường

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| QA-SRM-01 | Mọi bước mô phỏng nằm trên polyline | Có một polyline tuyến đường cố định làm dữ liệu test | 1. Nạp polyline gồm ≥ 3 đoạn có góc gấp khúc. 2. Chạy bộ đi lại 200 nhịp. 3. Với mỗi vị trí sinh ra, tính khoảng cách vuông góc tới đoạn polyline gần nhất | Mọi khoảng cách ≤ `SIM_ROAD_TOLERANCE_M`. **Không** có ngoại lệ nào ngoài điểm spawn của QA-SRM-05 | P0 | unit |
| QA-SRM-02 | Không cắt góc ở khúc cua | Như trên, polyline có một khúc cua ≥ 90° | 1. Chạy bộ đi lại qua đúng khúc cua đó. 2. Kiểm mọi vị trí giữa hai đầu khúc cua | Không vị trí nào nằm về phía trong của góc quá `SIM_ROAD_TOLERANCE_M` — tức là **không có** bước nội suy thẳng nối tắt hai cạnh (đây chính là D1 cũ: `MemberRoamer.kt:137-144`) | P0 | unit |
| QA-SRM-03 | Bearing có giá trị thật | — | 1. Chạy bộ đi lại 50 nhịp trên polyline có ít nhất 2 hướng khác nhau. 2. Đọc `bearingDegrees` của mọi điểm ghi ra | **Không** điểm nào có `bearingDegrees = 0f` chỉ vì chưa ai tính (giá trị hiện tại ghi cứng ở `MemberMovementSimulator.kt:98`). Bearing khớp hướng của đoạn polyline đang đi, sai số ≤ 5° | P1 | unit |
| QA-SRM-04 | Nhìn bằng mắt: không xuyên nhà | App chạy, bật theo dõi, thu phóng bản đồ đủ gần để thấy nhà cửa. **Đang chạy tầng 1 hoặc 2** — xác nhận bằng log `sim_route_loaded source=PROVIDER` (có khoá routing + có mạng, hoặc cache đã có chặng đó) | 1. Quan sát marker Minh và Lan liên tục 2 phút | Marker luôn nằm trên vệt đường của bản đồ. Không có đoạn nào cắt ngang khối nhà, công viên, hay sông. **Không áp dụng khi đang chạy tầng 3** (`source=FALLBACK`, ví dụ máy không khoá hoặc ngoại tuyến chưa có cache): đường cong tổng hợp có thể cắt qua nhà — giới hạn có chủ ý, chủ dự án chấp nhận 2026-08-25 (`decisions.md` §C2). Gặp ca đó thì ghi **N/A**, không ghi **Fail** | P0 | manual |

### 2.2 US-40 — Chuyển động mượt, không giật

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| QA-SRM-05 | Không có bước nhảy > ngưỡng giữa hai khung hình | Bộ nội suy hiển thị đang chạy | 1. Ghi lại vị trí marker mỗi khung hình trong 30 giây. 2. Tính khoảng cách giữa mỗi cặp khung hình liên tiếp | Mọi khoảng cách ≤ `MEMBER_RENDER_MAX_JUMP_M` (2.0m). **Ngoại lệ duy nhất được phép:** đúng một mẫu ứng với cú spawn của US-42 | P0 | instr |
| QA-SRM-06 | Nội suy áp dụng cho **cả** marker thật | Đang bật theo dõi, GPS thật đang phát | 1. Lặp lại QA-SRM-05 nhưng đo chấm xanh của self | Cùng ngưỡng. Chấm xanh không nhảy từng cú mỗi 10 giây (`LOCATION_INTERVAL_MS`) mà trượt liên tục giữa hai mẫu | P1 | instr |
| QA-SRM-07 | Marker xoay theo hướng đi, không giật góc | — | 1. Quan sát marker qua một khúc cua | Góc xoay biến thiên liên tục, không nhảy từ góc này sang góc khác trong một khung hình | P1 | manual |
| QA-SRM-08 | Room vẫn nhận mẫu thô | Bật theo dõi 60 giây | 1. Đọc bảng `location_points` sau 60 giây. 2. Đếm số dòng cho một thành viên mô phỏng | Số dòng khớp nhịp lấy mẫu (`MEMBER_ROAM_INTERVAL_MS`), **không** khớp nhịp khung hình. Nội suy **không** được ghi xuống DB — quyết định đã chốt ở PRD delta §7.2 Y1 | P0 | instr |

### 2.3 US-42 — Spawn một lần

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| QA-SRM-09 | Đúng một cú dời vị trí mỗi lần bắt đầu | Thành viên gieo ở TP.HCM, zone tạo ở Mountain View (khoảng lệch mặc định, xem §1) | 1. Bắt đầu mô phỏng. 2. Chạy 200 nhịp. 3. Đếm số bước có khoảng cách > `SIM_MEMBER_SPEED_MPS × (nhịp/1000) × 2` | Đúng **1** cho mỗi thành viên. Bản cũ cho phép nhiều lần vì nhánh dời bị đánh giá mỗi tick (`MemberRoamer.kt:122-130`) | P0 | unit |
| QA-SRM-10 | Điểm spawn nằm ngoài ranh giới zone | Một zone bán kính 150m | 1. Bắt đầu mô phỏng với đích là zone đó. 2. Đo khoảng cách từ điểm spawn tới tâm zone | Khoảng cách > `bán kính zone`. Nếu spawn rơi vào trong zone thì không có lần cắt ranh giới nào và ENTER không bao giờ sinh ra (luật hiện có: `MemberRoamer.kt:175-182`) | P0 | unit |
| QA-SRM-11 | Dừng rồi bật lại theo dõi = một spawn mới, không nhiều hơn | App đang chạy | 1. Tắt công tắc theo dõi. 2. Bật lại. 3. Đếm số cú dời trong 60 giây kế tiếp | Đúng 1 cho mỗi thành viên. Bật/tắt liên tục 3 lần → đúng 3, không cộng dồn | P1 | instr |
| QA-SRM-12 | Nhìn bằng mắt: không "biến mất rồi hiện lại" | Đã chạy được > 1 phút sau spawn | 1. Quan sát liên tục 3 phút | Marker không bao giờ biến khỏi khung nhìn rồi xuất hiện ở nơi khác. Nếu đi ra khỏi khung thì phải đi ra bằng cách **đi**, thấy được từng bước | P0 | manual |

### 2.4 US-45 / US-47 — Dịch vụ tuyến hỏng (vẫn có internet) · Mất internet

Nghiệm thu viết theo **hành vi quan sát được**, không theo cách hiện thực — điều khoản
redistribution của nhà cung cấp chưa được làm rõ (PRD delta §8 Q8).

**Đọc kỹ ranh giới trước khi chạy mục này (D8, `decisions.md` §C2):** *mất internet* và *lỗi nhà
cung cấp* là hai ca **khác nhau, kết quả mong đợi ngược nhau**. Mất internet ⇒ dialog chặn màn Bản
đồ. Có internet mà nhà cung cấp hỏng ⇒ **không** dialog, chạy tiếp im lặng. Chấm nhầm một ca ở đây
là giấu mất chế độ hỏng tệ nhất của tính năng: một dialog không bao giờ tự tắt được.

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| QA-SRM-13 | Mất internet → chặn màn Bản đồ | App đang chạy, đang ở màn Bản đồ, đang bật theo dõi | 1. Bật chế độ máy bay. 2. Đợi 5 giây. 3. Bấm nút Back. 4. Chạm ra ngoài dialog. 5. Xoay màn hình | Dialog "mất mạng" hiện trong ≤ 5 giây và **không đóng được** bằng bất kỳ thao tác nào ở bước 3–4. Sau khi xoay màn hình dialog **vẫn còn** — nó là state, không phải sự kiện một lần (MVI doc §1) | **P0** | manual |
| QA-SRM-14 | Không có khoá API → vẫn di chuyển | Build với khoá routing để trống trong `local.properties`, **máy có internet bình thường** | 1. Cài, bật theo dõi. 2. Quan sát 2 phút | Thành viên **vẫn di chuyển**, vẫn trên đường tổng hợp, vẫn thoả ngưỡng QA-SRM-05. Không màn hình lỗi, không toast kỹ thuật, **không dialog** — máy có internet nên D8 không kích hoạt | P1 | manual |
| QA-SRM-15 | Nhà cung cấp trả lỗi → vẫn di chuyển | Giả lập phản hồi 401 / 429 / 400 từ nhà cung cấp | 1. Chạy 3 lần, mỗi lần một mã lỗi | Cả 3 lần: chuyển động không đứt. Lỗi chỉ xuất hiện trong log, không lên UI | P1 | unit |
| QA-SRM-16 | Ghi công vẫn đúng ở chế độ dự phòng | Đang chạy ở nguồn dự phòng **và vẫn có internet** (QA-SRM-14 — không dùng QA-SRM-13 nữa, ca đó nay bị dialog chặn) | 1. Kiểm dải ghi công | **Nếu** tuyến đường đang vẽ có nguồn OSM → vẫn hiện `© OpenStreetMap contributors`. **Nếu** đã tụt xuống đường thẳng tự vẽ (không dữ liệu OSM nào trên màn) → **không** được hiện credit OSM. Luật: `docs/routing-and-map-attribution.md` §3, "Chỉ hiện credit OSM khi đang thật sự hiển thị dữ liệu OSM" | P0 | manual |
| QA-SRM-17 | Có internet trở lại → dialog tự tắt | Đang hiện dialog của QA-SRM-13 | 1. Tắt chế độ máy bay. 2. **Không chạm vào màn hình.** 3. Quan sát 1 phút | Dialog **tự đóng** trong ≤ 10 giây, không cần một thao tác nào. Màn Bản đồ hiện lại và **không có cú nhảy vị trí nào** vượt `MEMBER_RENDER_MAX_JUMP_M` — mô phỏng đã chạy suốt phía sau dialog | P1 | manual |
| QA-SRM-37 | Captive portal cũng tính là mất internet | Wifi quán cà phê / hotspot chưa đăng nhập, có sóng đầy | 1. Nối wifi, **không** đăng nhập. 2. Mở màn Bản đồ | Dialog **vẫn hiện**. Máy có transport nhưng thiếu `NET_CAPABILITY_VALIDATED` ⇒ vẫn là mất internet. Đây là ca hay gặp nhất khi demo ngoài văn phòng | **P0** | manual |
| QA-SRM-38 | Theo dõi thật không đứt sau lưng dialog | Đang bật theo dõi, dialog QA-SRM-13 đang hiện | 1. Giữ máy bay 5 phút, có di chuyển thật. 2. Tắt máy bay. 3. Mở tab Lịch sử | `location_points` **liên tục**, không có lỗ hổng 5 phút. `zone_event_raised` vẫn nổ đúng trong lúc mất mạng. Polyline Lịch sử không đứt đoạn | **P0** | manual |
| QA-SRM-39 | Màn khác không bị chặn | Chế độ máy bay bật | 1. Mở tab Zone. 2. Sửa một zone. 3. Mở tab Lịch sử. 4. Mở tab Nhật ký (**không có màn "Cài đặt"** — sai lệch tài liệu, app chỉ có 4 tab; ghi nhận 2026-08-26) | Cả bốn dùng được bình thường, **không** dialog nào. Chúng đọc Room, không gọi mạng — chặn chúng là chặn thứ đang chạy đúng | P1 | manual |
| QA-SRM-40 | **Ca âm sống còn:** nhà cung cấp lỗi ≠ mất mạng | Máy **có** internet, giả lập nhà cung cấp trả 401 | 1. Mở màn Bản đồ. 2. Đợi qua ít nhất 2 chặng | **Không** dialog "mất mạng" nào hiện. Hạ cấp im lặng xuống tầng 3, thành viên đi tiếp. Hỏng ca này ⇒ dialog kẹt vĩnh viễn vì điều kiện tắt (có internet) đã đúng sẵn từ đầu | **P0** | unit |

### 2.5 US-43 / US-44 — GPS thật: trong nhà và không bị nắn (đầu bảng)

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| **QA-SRM-18** | **Sai số lớn → vẫn hiển thị, vẫn theo dõi** | Nguồn vị trí phát điểm có `accuracy = 80f` (> `MAX_ACCURACY_M` = 50) | 1. Bật theo dõi. 2. Phát 5 điểm liên tiếp `accuracy = 80f`, mỗi điểm cách điểm trước 30m. 3. Đọc vị trí marker self sau mỗi điểm | Marker **di chuyển theo cả 5 điểm**. Không biến mất, không đứng lại ở điểm cuối cùng có sai số tốt. Công tắc theo dõi vẫn báo "đang bật". **Đây là ca hồi quy của D6:** hôm nay `LocationFilter.kt:24-26` loại thẳng điểm này, `LocationPointProcessor.kt:33-38` không ghi, nên `MemberRepositoryImpl.kt:22-25` không phát gì mới và marker đứng hình | **P0** | instr |
| QA-SRM-19 | Trong nhà thật, marker không chết | Máy thật, người kiểm đứng trong nhà / tầng hầm | 1. Bật theo dõi. 2. Đi bộ trong nhà 3 phút | Marker cập nhật theo bước chân, dù lệch. Không có khoảng thời gian > 30 giây nào marker đứng im trong khi người đang đi | **P0** | manual |
| QA-SRM-20 | Vị trí thật hiển thị **đúng như nhận được** | — | 1. Phát một điểm nằm giữa một khối nhà, cách con đường gần nhất 40m. 2. Đọc toạ độ marker | Toạ độ marker **trùng khít** toạ độ đã phát, sai số 0. Không bị kéo về đường. Không bị "làm tròn" | **P0** | instr |
| QA-SRM-21 | Không có snap-to-road ở bất kỳ tầng nào | — | 1. Test kiến trúc: quét mọi đường đi của điểm từ nguồn vị trí thật tới UI. 2. Khẳng định không có lời gọi nào tới hàm nắn/khớp đường trên nhánh đó | Không tồn tại. Nếu sau này ai đó thêm vào, test này phải đỏ. Cùng tinh thần với `CoroutineSafetyArchitectureTest` đang có ở `ui/src/test/java/.../core/mvi/` | **P0** | unit |
| QA-SRM-22 | Nội suy hiển thị không kéo điểm thật về đường | Hai mẫu thật liên tiếp, cả hai đều nằm trong khối nhà | 1. Đọc mọi vị trí nội suy giữa hai mẫu đó | Mọi vị trí nằm **trên đoạn thẳng nối hai mẫu thật**, không lệch về phía con đường gần nhất. Nội suy chỉ được nội suy giữa hai mẫu, không được "cải thiện" chúng | P0 | unit |
| QA-SRM-23 | Chỉ báo độ chính xác thấp, không phải báo lỗi | Điểm `accuracy = 80f` | 1. Quan sát màn Bản đồ | Có chỉ báo trực quan (vòng sai số hoặc nhãn) cho biết độ chính xác thấp. **Không** dialog, **không** toast, **không** chữ "lỗi" | P1 | manual |
| QA-SRM-24 | Lịch sử vẫn được lọc như cũ | Đã phát cả điểm sai số tốt lẫn điểm `accuracy = 80f` | 1. Mở tab Lịch sử, chọn hôm nay. 2. Xem polyline | Polyline **chỉ** gồm điểm sai số ≤ 50m — US-31 không đổi. `MAX_ACCURACY_M` chi phối cái được **ghi**, không chi phối cái được **vẽ trực tiếp** (PRD delta §4.3) | P0 | instr |

### 2.6 US-25 / US-26 — Bất biến zone phải sống sót

`MemberRoamerTest.kt:96-117` đang khoá bất biến "mỗi vòng đúng một ENTER rồi đúng một EXIT, xen kẽ,
không dội". `LLM.md:1090` gọi đây là "lời hứa duy nhất cả tính năng dựa vào". **Bám đường làm quãng
đường dài hơn và tốc độ chậm hơn — cả hai đều đổi thời gian một vòng, nên bất biến này phải được
kiểm lại, không được coi là đương nhiên.**

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| QA-SRM-25 | ENTER/EXIT xen kẽ, không dội (bám đường) | Một zone bán kính 150m, tuyến đường đi xuyên qua zone | 1. Chạy bộ đi lại 200 nhịp **qua `ZoneEvaluator` thật**. 2. Thu mọi sự kiện | Sự kiện đầu tiên là `ENTER`. Không có hai sự kiện liên tiếp cùng loại. Đây là `MemberRoamerTest` hiện có, chạy lại trên đường thật thay vì đường thẳng | **P0** | unit |
| QA-SRM-26 | Bất biến đúng ở bán kính zone nhỏ nhất | Zone bán kính `ZONE_RADIUS_MIN_M` = 50m | 1. Như QA-SRM-25 | Vẫn xen kẽ. Zone nhỏ + đường đi dài hơn là tổ hợp dễ vỡ nhất | P0 | unit |
| QA-SRM-27 | Dwell vẫn dài hơn cửa sổ khử trùng lặp | — | 1. Đo khoảng thời gian **tính bằng ms** giữa hai `ENTER` liên tiếp của cùng một zone | > `EVENT_DEDUPE_WINDOW_MS` (60_000). Nếu nhịp tick đổi (PRD delta §4.1) thì con số tick đổi theo — bất biến phải đúng theo **cấu trúc**, không theo số học trong một comment (`MemberRoamer.kt:60-74`) | P0 | unit |
| QA-SRM-28 | Không dội khi đường men theo mép zone | Tuyến đường chạy song song mép zone, cách tâm ≈ bán kính | 1. Chạy 200 nhịp | Không dội ENTER/EXIT liên tục. Hysteresis `ZONE_EXIT_BUFFER_M` = 30m làm việc — US-26 | P0 | unit |
| QA-SRM-29 | Đầu-cuối: 2 thông báo mỗi vòng | Máy thật/emulator, zone quanh vị trí thiết bị | 1. Bật theo dõi. 2. Chờ một vòng đầy đủ. 3. Đọc shade thông báo + tab Nhật ký | Đúng 1 thông báo "đã đến" rồi đúng 1 "đã rời" cho mỗi thành viên. Nhật ký xen kẽ, mọi dòng mang tên Minh hoặc Lan | P0 | manual |

### 2.7 US-46 — Ghi công OpenStreetMap

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| QA-SRM-30 | Có polyline OSM → có ghi công | Một tuyến đường nguồn OSM đang được vẽ | 1. Xem màn đang vẽ tuyến | Hiện `© OpenStreetMap contributors` **và** tên nhà cung cấp. Nội dung lấy từ dữ liệu đi kèm tuyến đường, không phải chuỗi tự ghép (`Directions.attribution` là bắt buộc, không nullable — `domain/.../model/Directions.kt:22-27`) | **P0** | manual |
| QA-SRM-31 | Ghi công không che ghi công của Google | Như trên | 1. Chụp màn hình. 2. Kiểm góc dưới-trái **bên trong** khung bản đồ | Logo/ghi công của Google nhìn thấy đầy đủ, không bị chồng. Dải OSM nằm **ngoài** khung bản đồ, tách bạch rõ — `docs/routing-and-map-attribution.md` §3 mục 1; thành phần đã có: `RoutingAttribution.kt:31-48` | **P0** | manual |
| QA-SRM-32 | **Ca âm:** không có dữ liệu OSM → không có ghi công | Đang ở chế độ đường thẳng tự vẽ | 1. Kiểm dải ghi công | **Không** hiện credit OSM. Hiện nhãn ước tính. Ghi credit lúc này là ghi sai nguồn — `RoutingAttribution.kt:36-40` đã cài đúng ba trạng thái này, test là để nó không bị phá | **P0** | manual |
| QA-SRM-33 | Polyline tuyến phân biệt được với nội dung Google | Đang vẽ tuyến | 1. Nhìn màu polyline | Màu riêng (`#E10098`, `Color.kt:60`), không trùng màu thành viên, không trùng màu polyline lịch sử — yêu cầu "visually distinguish" ở `docs/routing-and-map-attribution.md` §3 mục 2 | P1 | manual |
| QA-SRM-34 | Không có bản đồ nền thứ hai | Toàn app | 1. Duyệt hết mọi màn có bản đồ | Chỉ một basemap của Google. Không tile bên thứ ba nào. Đây là hành vi **bị cấm rõ ràng** — §3 mục 3 | P0 | manual |

### 2.8 Hiệu năng

| ID | Tiêu đề | Điều kiện tiên quyết | Các bước | Kết quả mong đợi | P | Loại |
|---|---|---|---|---|---|---|
| QA-SRM-35 | Nội suy không làm rớt khung hình | 3 thành viên + 5 zone trên màn | 1. Đo nhịp khung hình trong 60 giây | Giữ 60fps, cùng ngưỡng PRD §7.1 đặt cho slider bán kính. Nội suy chạy theo khung hình nên đây là rủi ro thật, không phải kiểm cho có | P1 | instr |
| QA-SRM-36 | Số lần gọi nhà cung cấp có trần | 3 thành viên, chạy 10 phút | 1. Đếm request tuyến đường | Không vượt trần đã chốt. Gói miễn phí giới hạn 500 credit/ngày — hết quota giữa buổi demo là hỏng demo | P1 | instr |

**Tổng: 40 ca — 28×P0, 12×P1, 0×P2.** Theo loại: 13 `unit` · 9 `instr` · 18 `manual`.
*(2026-08-25 · D8: QA-SRM-13 và 17 viết lại, 13 nâng P1→P0, thêm QA-SRM-37→40.)*

*(2026-08-26 · phase-07 đã implement. Nghiệm thu trên `emulator-5554`: **QA-SRM-13 ĐẠT** — lớp phủ
hiện sau **396 ms** (trần 5 s), Back/chạm/nhấn-giữ đều không đóng, xoay màn hình vẫn còn.
**QA-SRM-17 ĐẠT** — tự đóng sau **2,5 s** (trần 10 s), không thao tác nào. **QA-SRM-39 ĐẠT** — đang
bị chặn vẫn chuyển được sang tab Zone, quay lại thì lớp phủ vẫn còn. **QA-SRM-40 ĐẠT** — khoá sai +
mạng bình thường ⇒ `sim_route_failed reason=NETWORK:Wrong credentials…` + `source=SYNTHETIC`, không
lớp phủ nào. Thêm ca ngoài kịch bản: mở app KHI ĐÃ ở chế độ máy bay ⇒ lớp phủ hiện sau 1,9 s kể cả
khởi động nguội (đường "đọc trạng thái ban đầu"; chỉ dựa vào callback thì ca này không bao giờ chạy).
**Còn nợ, cần máy thật:** QA-SRM-37 (captive portal) và QA-SRM-38 (đi bộ thật 5 phút) — emulator
không dựng được.)*
(Đếm bằng cách quét cột P của 36 dòng trên, không đếm bằng trí nhớ — bản nháp đầu của chính tài
liệu này ghi "20×P0, 15×P1, 1×P2" và sai cả ba con số. Đúng loại lỗi `LLM.md` §13 Open #3 đã bắt
được trong câu tổng kết PRD §2.)

---

## 3. Sự kiện log dùng để nghiệm thu

PRD §10 đã có 12 sự kiện `FTD_EVENT`. Thay đổi này cần thêm — **đề xuất**, không phải yêu cầu chốt:

| Sự kiện | Tham số | Dùng để xác minh |
|---|---|---|
| `sim_route_loaded` | `source` (`PROVIDER`/`CACHE`/`SYNTHETIC` — **không phải `FALLBACK`**: tài liệu này viết sai tên từ đầu, code luôn ghi `SYNTHETIC`; sửa 2026-08-26 khi nghiệm thu phase-07), `pointCount` | QA-SRM-14→17, QA-SRM-40 — biết đang chạy nguồn nào. **Không** dùng cho QA-SRM-13/37: ca đó quyết bởi trạng thái mạng, không bởi nguồn tuyến |
| `sim_spawn` | `memberId`, `distanceM` | QA-SRM-09/11 — đếm số cú spawn |
| `sim_route_failed` | `reason` | QA-SRM-15 — lỗi có xảy ra và có bị nuốt đúng cách không |
| `network_state` | `hasInternet` (`true`/`false`) | QA-SRM-13/17/37/40 (phase-07) — **đúng MỘT dòng mỗi lần ĐỔI trạng thái**, và đó là điều kiện nghiệm thu chứ không phải mô tả: bản đầu phát một dòng cho MỖI `MapViewModel` đang sống, nên 3 lần chuyển tab làm một lần đổi trạng thái sinh 5 dòng (`LLM.md` §13 Open #23). Nếu đếm ra nhiều hơn 1, đừng chỉnh phép đếm — đó là callback đang tích luỹ. **Không** log SSID, tên mạng, hay toạ độ (G7) |

**Nhắc lại:** không sự kiện nào trong số này thấy được trên bản release (gate G7). Mọi ca dựa vào
log phải chạy trên debug; UAT chạy trên release và kiểm bằng mắt.

---

## 4. UAT Scenarios

Viết cho người dùng gia đình, không cho kỹ sư. Mỗi kịch bản phải phán đạt/không đạt **chỉ bằng
mắt**, không mở log, không mở code.

### UAT-01 — Minh đi trên đường, không xuyên nhà

**Cho rằng** tôi đã cài app, đã cấp quyền vị trí, và đang mở màn Bản đồ.
**Khi** tôi bật công tắc theo dõi và ngồi xem chấm của Minh trong 2 phút.
**Thì** tôi thấy Minh đi dọc theo các con đường trên bản đồ, giống như một người đang chạy xe.

| Đạt | Không đạt |
|---|---|
| Chấm của Minh luôn nằm trên vệt đường | Chấm cắt ngang qua khối nhà, công viên, sông |
| Chuyển động liên tục, như xem một chiếc xe chạy | Chấm nhảy từng cú, đứng im rồi nhảy tiếp |
| Tốc độ trông hợp lý với đường phố | Trông như đang bay, hoặc nhanh như ô tô trên cao tốc |

### UAT-02 — Không có cảnh "biến mất rồi hiện ra chỗ khác"

**Cho rằng** app vừa được bật theo dõi và tôi đã thấy Minh và Lan xuất hiện lần đầu.
**Khi** tôi xem liên tục 3 phút mà không chạm vào màn hình.
**Thì** hai người luôn ở nơi tôi có thể theo dõi được, và mọi lần đổi chỗ đều thấy được từng bước.

| Đạt | Không đạt |
|---|---|
| Sau khi xuất hiện lần đầu, không ai nhảy chỗ nữa | Có người biến mất rồi hiện lại ở nơi khác trên bản đồ |
| Nếu đi khỏi khung hình thì thấy rõ họ **đi** ra | Đột nhiên mất hút không dấu vết |

**Ghi chú cho người kiểm:** lần xuất hiện **đầu tiên** ngay khi bắt đầu là **được phép** — đó là
lúc app đưa thành viên demo về gần chỗ bạn. Từ lần đó trở đi thì không.

### UAT-03 — Vào và rời zone vẫn báo đúng một lần

**Cho rằng** tôi đã tạo một zone tên "Nhà" quanh vị trí của mình.
**Khi** tôi để app chạy và chờ Minh đi tới rồi đi khỏi zone đó.
**Thì** tôi nhận đúng một thông báo "Minh đã đến Nhà", rồi sau đó đúng một thông báo "Minh đã rời Nhà".

| Đạt | Không đạt |
|---|---|
| Đúng 2 thông báo cho một vòng, theo đúng thứ tự đến → rời | Nhận 2 thông báo "đã đến" liên tiếp |
| Nhật ký hiện đúng 2 dòng tương ứng | Thông báo dội liên tục khi Minh ở gần mép zone |
| | Đến/rời mà không có thông báo nào |

### UAT-04 — Mất mạng, app nói thẳng

**Cho rằng** app đang chạy và Minh đang di chuyển bình thường.
**Khi** tôi bật chế độ máy bay và xem tiếp 2 phút, rồi tắt máy bay đi.
**Thì** app báo cho tôi biết là mất mạng, và tự trở lại bình thường khi mạng về — tôi không phải
bấm gì cả.

| Đạt | Không đạt |
|---|---|
| Hiện thông báo mất mạng ngay, bằng tiếng người, không mã lỗi | Bản đồ đứng im không giải thích gì |
| Không đóng được thông báo khi vẫn chưa có mạng | Đóng được rồi nhìn thấy một bản đồ trông như đang chạy nhưng không chạy |
| Có mạng lại thì thông báo **tự** biến mất | Phải tự tắt/mở lại app mới dùng tiếp được |
| Xem lại tab Lịch sử thấy quãng đường lúc mất mạng vẫn được ghi | Lịch sử thủng đúng khoảng thời gian mất mạng |
| Trong lúc đó vẫn vào được tab Zone và Lịch sử | Cả app bị khoá cứng |

### UAT-05 — **Trong nhà vẫn thấy tôi ở đâu (kịch bản đầu bảng)**

**Cho rằng** tôi đang ở trong nhà, hoặc trong tầng hầm, nơi GPS bắt kém.
**Khi** tôi bật theo dõi và đi lại vài phòng trong 3 phút.
**Thì** chấm xanh của tôi vẫn hiện trên bản đồ và vẫn nhúc nhích theo bước chân tôi.

| Đạt | Không đạt |
|---|---|
| Chấm xanh luôn hiển thị | Chấm xanh biến mất |
| Chấm nhúc nhích khi tôi đi lại, dù có lệch | Chấm đứng chết ở chỗ tôi đứng lúc còn ngoài trời |
| Công tắc theo dõi vẫn báo đang bật | App báo "mất tín hiệu" rồi ngừng hẳn |
| Có dấu hiệu cho biết vị trí đang kém chính xác | Hiện hộp thoại lỗi |

**Đây là kịch bản quan trọng nhất của cả đợt nghiệm thu.** Chỉ kiểm được một kịch bản thì kiểm cái
này. Cần **máy thật**, trong nhà thật — emulator có thể không tái hiện được sai số lớn.

### UAT-06 — App không nói dối về vị trí của tôi

**Cho rằng** tôi đang đứng bên trong một toà nhà, cách con đường gần nhất vài chục mét.
**Khi** tôi nhìn chấm xanh của mình trên bản đồ.
**Thì** chấm nằm ở chỗ tôi thật sự đang đứng — trong toà nhà — chứ không bị đẩy ra ngoài đường.

| Đạt | Không đạt |
|---|---|
| Chấm nằm trong toà nhà, đúng nơi tôi đứng | Chấm bị dán lên con đường gần nhất |
| Nếu lệch thì lệch theo kiểu GPS kém, không theo kiểu bị nắn về đường | Chấm trượt dọc theo đường mỗi khi tôi đi |

**Vì sao kịch bản này tồn tại:** thành viên demo **được** đi trên đường vì họ là dữ liệu giả. Vị trí
thật của một con người thì không. Nếu app nắn vị trí thật về đường, nó đang nói dối về chỗ một người
đang đứng — và đó là loại lỗi tệ nhất một app theo dõi gia đình có thể mắc.

### UAT-07 — Ghi công nguồn bản đồ hiện đúng chỗ

**Cho rằng** trên màn hình đang vẽ một tuyến đường.
**Khi** tôi nhìn khắp màn hình.
**Thì** tôi thấy dòng chữ ghi nguồn dữ liệu tuyến đường, ở dải riêng, và nó không đè lên chữ của
Google ở góc bản đồ.

| Đạt | Không đạt |
|---|---|
| Thấy `© OpenStreetMap contributors` | Không thấy dòng nào |
| Chữ của Google ở góc dưới-trái bản đồ vẫn đọc được đầy đủ | Dòng ghi công đè lên chữ của Google |
| Khi không có tuyến đường nào được vẽ thì dòng đó không hiện | Dòng ghi công hiện cả khi màn hình không có tuyến đường nào |

### UAT-08 — Buổi demo chạy đúng nhịp

**Cho rằng** tôi là người trình bày, đang chạy bản release trên máy demo.
**Khi** tôi tạo một zone rồi đợi Minh đi tới.
**Thì** thông báo nổ trong khoảng thời gian tôi đã tập dượt, đủ ngắn để cả phòng không phải ngồi chờ.

| Đạt | Không đạt |
|---|---|
| Thời gian tới zone nằm trong ngân sách đã chốt | Cả phòng ngồi chờ quá lâu, mất nhịp trình bày |
| Ba lần chạy liên tiếp cho thời gian tương đương | Lúc nhanh lúc chậm không đoán được |

**Ghi chú:** hạ tốc độ mô phỏng làm thời gian một vòng **dài ra**. Số đo cũ ở tốc độ cũ là 63 giây
(`LLM.md` §13 Fixed #23, `emulator-5554`). Số mới **phải được đo lại** và chốt vào kịch bản demo
trước ngày trình bày — xem câu hỏi chưa giải #1.

---

## 5. Danh sách hồi quy

Những thứ đang chạy đúng và có thể vỡ vì thay đổi này.

| # | Hạng mục | Vì sao có rủi ro | Cách kiểm |
|---|---|---|---|
| R1 | **Bất biến ENTER/EXIT xen kẽ** | Đổi tốc độ và đổi hình dạng đường đi làm đổi thời gian một vòng. `DWELL_TICKS` neo vào `EVENT_DEDUPE_WINDOW_MS` (`MemberRoamer.kt:60-74`), nhưng đường dài hơn cũng làm đổi nhịp | QA-SRM-25→28 · `MemberRoamerTest` |
| R2 | **Khử trùng lặp 60 giây (US-25)** | Nếu một vòng ngắn hơn 60s ở zone nhỏ, lần ENTER thứ hai bị nuốt | QA-SRM-27 · `ZoneEventDedupeTest` (androidTest) |
| R3 | **Chống rung ranh giới (US-26)** | Đường bám sát mép zone là tình huống mới, chưa từng xảy ra với đường chim bay | QA-SRM-28 |
| R4 | **Lọc nhiễu GPS thật (US-31)** | Sửa D6 đụng vào chính đường dữ liệu mà bộ lọc đang canh. Rủi ro: gỡ nhầm bộ lọc khỏi lịch sử | QA-SRM-24 · `LocationFilterTest`, `LocationPointProcessorTest` |
| R5 | **Điểm mô phỏng không đi qua `LocationFilter`** | Luật hiện có, có lý do (`MemberMovementSimulator.kt:35-38`). Đổi tốc độ có thể khiến ai đó tưởng giờ cho điểm mô phỏng qua bộ lọc được | `MemberMovementSimulatorTest` |
| R6 | **F5 Route Simulator (US-33)** | Dùng chung `LocationSource` và chung `LocationTrackingService`. `familyJob` **không** bị `ACTION_SIMULATE` huỷ (`LocationTrackingService.kt:33`) — nếu spawn một lần bị gắn nhầm vào vòng đời của `trackingJob` thì bấm nút mô phỏng sẽ sinh spawn thừa | QA-SRM-11 · `StartSimulationUseCaseTest` · G4 |
| R7 | **Chấm xanh của self (US-06)** | Đổi nguồn dữ liệu của marker là thay đổi có rủi ro nhất trong cả đợt | QA-SRM-06, QA-SRM-18, QA-SRM-20 |
| R8 | **Polyline lịch sử (F3)** | Đọc cùng bảng `location_points`. Nếu nội suy vô tình bị ghi xuống DB, lịch sử phình và vẽ chậm — PRD §7.1 ngưỡng < 1s | QA-SRM-08 · `HistoryPipelineScaleTest` |
| R9 | **Màn Dẫn đường** | Dùng chung `RoutingProvider` và chung hạn ngạch. Thêm một nguồn tiêu thụ mới có thể làm cạn quota giữa buổi demo | QA-SRM-36 · `ObserveNavigationUseCaseTest`, `NavigationViewModelTest` |
| R10 | **Hiệu năng mở app < 2.5s (PRD §7.1)** | Nạp tuyến đường lúc khởi động thêm việc vào đường khởi động | Đo tay 3 lần như PRD §7.1 quy định |
| R11 | **Gate G7 — không rò toạ độ ra log ở release** | Ba sự kiện log mới ở §3 mang toạ độ và tên nhà cung cấp | `adb logcat` theo PID app trên bản release = rỗng |
| R12 | **Koin `verify()` (gate G3)** | Nguồn tuyến đường mới cần binding mới | `KoinModulesTest` |

---

## 6. Câu hỏi chưa giải được

1. **`MEMBER_RENDER_MAX_JUMP_M` = 2.0m là con số suy ra, chưa đo.** Suy từ tốc độ đề xuất (8.3 m/s)
   và 60fps. Nếu Q10 của PRD delta chốt tốc độ khác thì ngưỡng này phải tính lại — QA-SRM-05 sẽ
   khoá sai số nếu chốt trước.
2. **Chưa biết emulator có sinh được điểm `accuracy > 50m` hay không.** Nếu không, QA-SRM-18 phải
   bơm điểm giả ở tầng test, và UAT-05 trở thành **bắt buộc chạy trên máy thật** — cùng loại chặn
   đã làm G4/G5 phải HOÃN (PRD §11.1).
3. **Ngân sách thời gian một vòng chưa chốt** — UAT-08 không phán đạt/không đạt được cho tới khi đo
   lại ở tốc độ mới. Đây là mục chặn của cả UAT-08.
4. **QA-SRM-16 và QA-SRM-32 phụ thuộc Q8/Q9 của PRD delta.** Nguồn dự phòng có mang dữ liệu OSM hay
   không quyết định dòng ghi công phải hiện hay phải ẩn. Trả lời sai chiều là vi phạm điều khoản,
   không phải lỗi hiển thị.
5. **QA-SRM-21 (test kiến trúc chặn snap-to-road) chưa có khuôn mẫu để bám.** `CoroutineSafetyArchitectureTest`
   là thứ gần nhất trong repo, nhưng nó quét import chứ không quét luồng dữ liệu. Cách hiện thực là
   quyết định của planner.
6. **Chưa rõ ai chạy QA-SRM-35 (60fps).** Repo có `HistoryPipelineScaleTest` cho hiệu năng lịch sử,
   nhưng không có khuôn đo nhịp khung hình nào.
