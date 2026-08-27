# Product Requirements Document (PRD)

**Project Name:** FamilyTrackerDemo
**Document ID:** FTD001-PRD
**Version:** 1.0
**Date:** 2026-08-21
**Status:** Approved — BA xác nhận 2026-08-21
**Platform:** Android (minSdk 28 · targetSdk 36)

---

## Table of Contents

1. [Overview](#1-overview)
2. [User Stories](#2-user-stories)
3. [Feature Specifications](#3-feature-specifications)
4. [Screen Flows & Navigation](#4-screen-flows--navigation)
5. [UI Specifications](#5-ui-specifications)
6. [Configuration & Constants](#6-configuration--constants)
7. [Non-Functional Requirements](#7-non-functional-requirements)
8. [Internal Contracts](#8-internal-contracts)
9. [Data Models](#9-data-models)
10. [Telemetry & QA Events](#10-telemetry--qa-events)
11. [Release Plan](#11-release-plan)
- [Appendix](#appendix)

---

## 1. Overview

### 1.1 Product Vision

Một app Android **demo** chứng minh hai năng lực kỹ thuật cho stakeholder:

1. Người dùng khoanh một **zone** (vùng địa lý hình tròn) trên bản đồ, và nhận **thông báo
   khi vào hoặc rời** zone đó.
2. Người dùng **xem lại lộ trình đã di chuyển** trong một ngày, vẽ trên bản đồ.

Đây không phải sản phẩm thương mại. Mục tiêu là để BA và khách hàng nhìn thấy hành vi thật
của geofencing và tracking trên thiết bị thật, đủ để quyết định có đầu tư làm sản phẩm đầy
đủ hay không.

### 1.2 Scope

**Trong phạm vi (v1.0):**

| # | Hạng mục |
|---|---|
| 1 | Tạo / sửa / xoá zone trên bản đồ (tâm + bán kính) |
| 2 | Thông báo push cục bộ khi vào / rời zone |
| 3 | Ghi lại lộ trình di chuyển của thiết bị, lưu 7 ngày |
| 4 | Xem lại lộ trình theo ngày, vẽ polyline trên bản đồ |
| 5 | Timeline nhật ký sự kiện vào / rời zone |
| 6 | Nút "Mô phỏng lộ trình" để demo trong nhà, không cần ra đường |
| 7 | 2–3 thành viên gia đình giả lập, hiển thị trên bản đồ để minh hoạ bối cảnh |

**Ngoài phạm vi (v1.0):**

| Hạng mục | Lý do |
|---|---|
| Backend, đồng bộ nhiều thiết bị thật | Demo chạy hoàn toàn cục bộ. Có backend thì khối lượng việc tăng ~3 lần. |
| Đăng nhập / tài khoản / ghép nhóm gia đình | Không cần để chứng minh 2 năng lực trên. |
| iOS | Dự án là Android thuần. |
| Zone hình đa giác | Geofencing API của Android chỉ hỗ trợ hình tròn. Đa giác phải tự tính, để version sau. |
| Cảnh báo pin yếu / SOS / chat | Không nằm trong yêu cầu BA đưa ra. |
| Kiếm tiền (quảng cáo, IAP) | Không áp dụng cho bản demo. |

### 1.3 Assumptions

| # | Giả định | Rủi ro nếu sai |
|---|---|---|
| A1 | Thiết bị demo có Google Play Services và đã bật GPS | Geofencing API không hoạt động, chỉ còn đường foreground |
| A2 | Người demo sẵn sàng cấp quyền "Cho phép mọi lúc" (background location) | Thông báo zone không bắn khi app đóng |
| A3 | Google Maps API key đã được cấu hình và restrict đúng package | Bản đồ hiện màn hình xám |
| A4 | Buổi demo diễn ra trong phòng, không đi lại thật | Đây là lý do F5 (mô phỏng lộ trình) là bắt buộc, không phải tuỳ chọn |
| A5 | Chỉ một thiết bị thật; thành viên khác là dữ liệu giả | Nếu BA kỳ vọng nhiều máy thật thấy nhau real-time thì phải mở rộng scope |

---

## 2. User Stories

Ưu tiên: **P0** = không có thì demo thất bại · **P1** = cần cho một buổi demo thuyết phục ·
**P2** = có thì tốt.

### 2.1 Permission Onboarding

| ID | User Story | P | Acceptance Criteria |
|---|---|---|---|
| US-01 | Là người dùng lần đầu mở app, tôi được giải thích vì sao app cần vị trí, trước khi hệ thống hỏi quyền | P0 | Màn giải thích hiện **trước** dialog hệ thống; có nút "Tiếp tục" và "Để sau" |
| US-02 | Là người dùng, tôi cấp quyền vị trí chính xác qua dialog hệ thống | P0 | Chọn "Khi dùng app" hoặc "Chỉ lần này" đều dẫn tới bước 3; từ chối thì về màn Map ở trạng thái giảm chức năng, có banner giải thích |
| US-03 | Là người dùng, tôi được hướng dẫn cấp quyền "Cho phép mọi lúc" ở màn Settings hệ thống | P0 | Bước này **chỉ hiện sau khi** US-02 đã cấp. Có nút mở thẳng `ACTION_APPLICATION_DETAILS_SETTINGS`. Quay lại app thì trạng thái được kiểm tra lại |
| US-04 | Là người dùng Android 13+, tôi cấp quyền thông báo | P0 | Nếu từ chối, app hiện banner thường trực "Thông báo đang tắt — sự kiện zone vẫn được ghi vào Timeline" |
| US-05 | Là người dùng đã cấp đủ quyền, tôi không bao giờ thấy lại màn onboarding | P1 | Mở app lần 2 vào thẳng màn Map |

### 2.2 Map (màn chính)

| ID | User Story | P | Acceptance Criteria |
|---|---|---|---|
| US-06 | Là người dùng, tôi thấy vị trí hiện tại của mình trên bản đồ | P0 | Marker xanh dương tại vị trí thiết bị; camera tự canh vào vị trí đó lần đầu mở |
| US-07 | Là người dùng, tôi thấy tất cả zone đã tạo vẽ dưới dạng hình tròn | P0 | Mỗi zone là một `Circle` có viền và nền bán trong suốt theo màu của zone; tên zone hiển thị ở tâm |
| US-08 | Là người dùng, tôi thấy các thành viên gia đình khác trên bản đồ | P1 | 2–3 marker màu khác nhau, có tên; bấm vào hiện tên + thời điểm cập nhật gần nhất |
| US-09 | Là người dùng, tôi bật/tắt việc theo dõi vị trí bằng một công tắc | P0 | Bật → foreground service chạy, có thông báo thường trực; tắt → service dừng trong ≤ 2 giây |
| US-10 | Là người dùng, tôi tạo zone mới bằng cách nhấn giữ một điểm trên bản đồ | P0 | Nhấn giữ ≥ 500ms mở màn Zone Editor với tâm là điểm vừa chọn |
| US-11 | Là người dùng, tôi đi tới các màn khác từ bản đồ | P0 | Có lối vào Zone List, History, Timeline |

### 2.3 Zone List

| ID | User Story | P | Acceptance Criteria |
|---|---|---|---|
| US-12 | Là người dùng, tôi xem danh sách zone đã tạo | P0 | Mỗi dòng: tên, bán kính, trạng thái "Đang ở trong / Ở ngoài", công tắc bật/tắt thông báo |
| US-13 | Là người dùng, tôi sửa một zone từ danh sách | P0 | Bấm dòng → mở Zone Editor ở chế độ sửa |
| US-14 | Là người dùng, tôi xoá một zone | P0 | Vuốt để xoá, có xác nhận. Xoá xong geofence tương ứng bị huỷ đăng ký khỏi hệ thống |
| US-15 | Là người dùng, tôi thấy thông báo rõ ràng khi danh sách trống | P1 | Empty state có hướng dẫn "Nhấn giữ trên bản đồ để tạo zone đầu tiên" + nút tạo |

### 2.4 Zone Editor

| ID | User Story | P | Acceptance Criteria |
|---|---|---|---|
| US-16 | Là người dùng, tôi đặt tên cho zone | P0 | Tên bắt buộc, 1–40 ký tự; để trống thì nút Lưu bị vô hiệu |
| US-17 | Là người dùng, tôi chỉnh bán kính zone và thấy hình tròn thay đổi theo thời gian thực | P0 | Slider 50m–2000m, bước 10m; hình tròn trên bản đồ cập nhật ngay khi kéo; giá trị hiện bằng số |
| US-18 | Là người dùng, tôi chỉnh lại tâm zone bằng cách kéo bản đồ | P0 | Tâm luôn là điểm giữa màn hình bản đồ; có crosshair chỉ tâm |
| US-19 | Là người dùng, tôi chọn nhận thông báo khi vào, khi ra, hoặc cả hai | P0 | Hai công tắc độc lập; tắt cả hai thì zone vẫn được vẽ nhưng không sinh thông báo |
| US-20 | Là người dùng, tôi chọn màu cho zone | P2 | 6 màu định sẵn |
| US-21 | Là người dùng, tôi bị chặn khi tạo quá 100 zone | P1 | Hiện thông báo "Android giới hạn 100 zone cho mỗi ứng dụng" và vô hiệu nút Lưu |

### 2.5 Notification (xuyên màn hình)

| ID | User Story | P | Acceptance Criteria |
|---|---|---|---|
| US-22 | Là người dùng, tôi nhận thông báo khi bước vào một zone | P0 | Nội dung: "Đã đến {tên zone}" + giờ. Bấm vào mở app tới Timeline |
| US-23 | Là người dùng, tôi nhận thông báo khi rời một zone | P0 | Nội dung: "Đã rời {tên zone}" + giờ |
| US-24 | Là người dùng, tôi nhận thông báo kể cả khi app đã đóng | P0 | Force-stop không tính; đóng app khỏi recents thì geofence vẫn bắn trong vòng 3 phút |
| US-25 | Là người dùng, tôi **không** nhận hai thông báo giống nhau cho một lần bước qua ranh giới | P0 | Sự kiện trùng `(zone, loại)` trong vòng 60 giây bị bỏ qua |
| US-26 | Là người dùng đứng yên ngay mép zone, tôi không bị dội thông báo liên tục | P0 | Vào khi `d < R`, chỉ ra khi `d > R + 30m`. Đứng yên 5 phút ở mép → tối đa 1 sự kiện |

### 2.6 History

| ID | User Story | P | Acceptance Criteria |
|---|---|---|---|
| US-27 | Là người dùng, tôi chọn một ngày để xem lại lộ trình | P0 | Date picker giới hạn trong 7 ngày gần nhất; mặc định là hôm nay |
| US-28 | Là người dùng, tôi thấy lộ trình vẽ thành đường liền trên bản đồ | P0 | `Polyline` nối các điểm theo thứ tự thời gian, dày 12dp, có marker Start (xanh) và End (đỏ) |
| US-29 | Là người dùng, tôi thấy tổng quãng đường và thời gian của lộ trình | P1 | Thẻ thông tin: tổng km, thời lượng, tốc độ trung bình. Quãng đường tính bằng tổng khoảng cách giữa các điểm liên tiếp |
| US-30 | Là người dùng, tôi thấy nhiều chuyến đi trong một ngày tách biệt nhau | P1 | Khoảng trống > 5 phút không có điểm nào → tách thành chuyến mới. Danh sách chuyến ở dưới, chọn chuyến nào thì vẽ chuyến đó |
| US-31 | Là người dùng, tôi thấy lộ trình sạch, không có đường nhảy lung tung | P0 | Điểm có `accuracy > 50m`, cách điểm trước `< 10m`, hoặc suy ra tốc độ `> 200 km/h` đều bị loại trước khi vẽ |
| US-32 | Là người dùng, tôi thấy thông báo khi ngày được chọn không có dữ liệu | P1 | Empty state: "Chưa có lộ trình nào trong ngày này" + gợi ý dùng nút mô phỏng |
| US-33 | Là người demo, tôi bấm một nút để sinh lộ trình mô phỏng đi xuyên qua các zone | P0 | Nút "Mô phỏng lộ trình". Lộ trình chạy trong ~30 giây, đi qua ít nhất 1 zone, sinh ra thông báo vào **và** ra thật, ghi vào lịch sử thật |

### 2.7 Timeline

| ID | User Story | P | Acceptance Criteria |
|---|---|---|---|
| US-34 | Là người dùng, tôi xem nhật ký các lần vào/rời zone | P0 | Danh sách theo thứ tự mới nhất trước: icon vào/ra, tên zone, giờ, ngày |
| US-35 | Là người dùng, tôi bấm một sự kiện để xem nó xảy ra ở đâu | P1 | Mở màn History của ngày đó, camera canh vào vị trí sự kiện |
| US-36 | Là người dùng, tôi thấy sự kiện được nhóm theo ngày | P2 | Header dính "Hôm nay" / "Hôm qua" / `dd/MM/yyyy` |

**Tổng: 36 user story — 22×P0, 11×P1, 3×P2.**

---

## 3. Feature Specifications

### 3.0 Overview

| ID | Feature | Screens liên quan | Story | Ưu tiên |
|---|---|---|---|---|
| F1 | Zone Management | Map, Zone List, Zone Editor | US-07, US-10, US-12→US-21 | P0 |
| F2 | Geofence Notification | xuyên màn hình | US-22→US-26 | P0 |
| F3 | History Tracking | Map, History | US-09, US-27→US-32 | P0 |
| F4 | Zone Timeline | Timeline | US-34→US-36 | P1 |
| F5 | Route Simulator | History | US-33 | P0 (bắt buộc để demo) |

### 3.1 F1 — Zone Management

| Thuộc tính | Giá trị |
|---|---|
| Hình dạng | Hình tròn (tâm + bán kính). Đa giác không được Geofencing API hỗ trợ |
| Bán kính | 50m – 2000m, bước 10m, mặc định 150m |
| Số lượng tối đa | **100** — giới hạn cứng của Play Services cho mỗi app |
| Lưu trữ | Room, bảng `zones`, tồn tại qua các lần mở app |
| Đăng ký geofence | Ngay khi lưu zone; huỷ đăng ký ngay khi xoá |
| Đăng ký lại | Sau khi khởi động lại thiết bị (`BOOT_COMPLETED`) và sau khi app bị kill — geofence **không** tự sống lại, phải đăng ký lại thủ công |

**Bán kính dưới 100m sẽ cho kết quả không ổn định trong nhà.** Ghi rõ điều này trong UI khi
người dùng kéo slider xuống dưới 100m, để buổi demo không bị hiểu là app lỗi.

### 3.2 F2 — Geofence Notification

| Thuộc tính | Giá trị |
|---|---|
| Hai đường phát hiện | (a) `GeofencingClient` — nền, app đóng vẫn chạy, độ trễ 30s–3 phút · (b) vòng kiểm tra trong foreground service — tức thì, chỉ khi service sống |
| Khử trùng lặp | Bỏ qua sự kiện trùng `(zoneId, memberId, type)` nếu sự kiện cùng khoá gần nhất < 60 giây |
| Chống rung ranh giới | Vào khi `d < R`; ra khi `d > R + 30m` |
| Kênh thông báo | `zone_events`, importance `DEFAULT`, có âm thanh, không rung liên tục |
| Nội dung | "Đã đến {zone}" / "Đã rời {zone}" · subtitle là giờ `HH:mm` |
| Hành động khi bấm | Mở app tới màn Timeline |
| Ghi nhận | Mọi sự kiện đều ghi vào `zone_events` **kể cả khi thông báo bị tắt quyền** |

**Cả hai đường phải ghi qua cùng một hàm** `ZoneEventRepository.record()`. Cột `source`
(`GEOFENCE_API` / `FOREGROUND`) tồn tại để QA kiểm chứng luật khử trùng lặp đang chạy đúng.

### 3.3 F3 — History Tracking

| Thuộc tính | Giá trị |
|---|---|
| Chu kỳ lấy vị trí | 10 giây, `PRIORITY_HIGH_ACCURACY`, khoảng cách tối thiểu 10m |
| Nơi chạy | Foreground service, thông báo thường trực có nút "Dừng theo dõi" |
| Lọc nhiễu | `accuracy > 50m` → bỏ · `distance < 10m` so với điểm trước → bỏ · tốc độ suy ra `> 200 km/h` → bỏ |
| Tách chuyến | Khoảng trống > 5 phút không có điểm → chuyến mới |
| Lưu trữ | 7 ngày. `PurgeOldHistoryUseCase` chạy lúc app khởi động |
| Vẽ | `Polyline` dày 12dp theo màu thành viên, marker Start/End |
| Thống kê | Tổng quãng đường (tổng khoảng cách các đoạn), thời lượng, tốc độ trung bình |

### 3.4 F4 — Zone Timeline

| Thuộc tính | Giá trị |
|---|---|
| Nguồn | Bảng `zone_events`, 7 ngày |
| Sắp xếp | Mới nhất trước, nhóm theo ngày với header dính |
| Mỗi dòng | Icon vào/ra · tên zone · `HH:mm` · tên thành viên (nếu ≠ mình) |
| Bấm vào | Mở History của ngày đó, canh camera vào vị trí sự kiện |
| Empty state | "Chưa có sự kiện nào. Tạo zone rồi thử nút mô phỏng lộ trình." |

### 3.5 F5 — Route Simulator

| Thuộc tính | Giá trị |
|---|---|
| Kích hoạt | Nút "Mô phỏng lộ trình" ở màn History |
| Hành vi | Sinh chuỗi `LocationPoint` đi theo một đường dựng sẵn, phát ra với nhịp nhanh hơn thực tế |
| Thời lượng | ~30 giây cho một lộ trình đi qua ít nhất 1 zone (vào rồi ra) |
| Đường đi | Sinh quanh vị trí hiện tại của thiết bị, đảm bảo cắt qua zone gần nhất; nếu chưa có zone nào thì tạo trước một zone mẫu |
| Điểm mấu chốt | Đi vào hệ thống qua **cùng cửa** với nguồn thật: cùng `LocationFilter` → `ZoneEvaluator` → `ZoneEventRepository`. Không có nhánh code riêng cho demo |
| Phạm vi build | Bật ở **cả hai** variant. Nút chỉ bị gỡ khi dựng bản phát hành thật cho người dùng cuối — không áp dụng ở giai đoạn demo |

**Vì sao F5 là P0:** buổi demo diễn ra trong phòng họp. Không có nó thì cách duy nhất để BA
nhìn thấy thông báo zone là có người cầm điện thoại đi bộ ra ngoài rồi quay lại — và cả phòng
ngồi chờ 3 phút cho geofence bắn.

---

## 4. Screen Flows & Navigation

### 4.1 Activity Map

```
MainActivity (single activity)
└── FamilyTrackerNavHost
    ├── PermissionOnboardingRoute   (start destination nếu thiếu quyền)
    ├── MapRoute                    (start destination khi đủ quyền)
    ├── ZoneListRoute
    ├── ZoneEditorRoute(zoneId?, lat?, lng?)
    ├── HistoryRoute(epochDay?)
    └── TimelineRoute
```

### 4.2 App Navigation Structure

```
┌──────────────────────────────────────────────────┐
│                    MAP  (home)                    │
│  ┌──────────┬───────────┬──────────┐             │
│  │ Zone List│  History  │ Timeline │  bottom bar │
│  └────┬─────┴─────┬─────┴────┬─────┘             │
└───────┼───────────┼──────────┼───────────────────┘
        │           │          │
        ▼           ▼          ▼
   ZONE LIST     HISTORY    TIMELINE
        │           │          │
        ▼           │          └──▶ HISTORY(ngày của sự kiện)
   ZONE EDITOR      │
        │           └──▶ [Mô phỏng lộ trình] ──▶ thông báo zone thật
        └──▶ lưu ──▶ về MAP, zone mới hiện ngay

   MAP ──(nhấn giữ bản đồ)──▶ ZONE EDITOR (tâm = điểm vừa chọn)
```

### 4.3 Key User Flows

**Flow 1 — Lần đầu mở app tới lúc theo dõi được (P0)**

```
Mở app
  └─▶ Onboarding: giải thích vì sao cần vị trí
        └─▶ [Tiếp tục] ─▶ dialog POST_NOTIFICATIONS (Android 13+)
              └─▶ dialog ACCESS_FINE_LOCATION
                    ├─ Từ chối ─▶ Map (chế độ giảm chức năng + banner)
                    └─ Cấp ─▶ màn giải thích "Cho phép mọi lúc"
                          └─▶ [Mở Cài đặt] ─▶ Settings hệ thống
                                └─▶ quay lại app ─▶ kiểm tra lại quyền
                                      └─▶ MAP, công tắc theo dõi sẵn sàng
```

**Flow 2 — Tạo zone và nhận thông báo (P0, đây là kịch bản demo chính)**

```
MAP ─(nhấn giữ)─▶ ZONE EDITOR
       ├ nhập tên "Nhà"
       ├ kéo bán kính = 200m          (hình tròn cập nhật ngay)
       ├ bật "Thông báo khi vào" + "khi rời"
       └ [Lưu] ─▶ ghi Room ─▶ đăng ký geofence ─▶ về MAP (zone hiện ngay)
                    │
                    ▼
              HISTORY ─▶ [Mô phỏng lộ trình]
                    │
                    ├─ ~10s: đi vào zone ─▶ 🔔 "Đã đến Nhà"
                    ├─ ~25s: rời zone    ─▶ 🔔 "Đã rời Nhà"
                    └─ kết thúc ─▶ polyline hiện trên bản đồ
                                 ─▶ 2 dòng mới trong TIMELINE
```

**Flow 3 — Xem lại lộ trình một ngày (P0)**

```
HISTORY
  ├ chọn ngày (giới hạn 7 ngày gần nhất)
  ├─ không có dữ liệu ─▶ empty state + gợi ý mô phỏng
  └─ có dữ liệu
       ├ danh sách chuyến trong ngày (tách theo khoảng trống > 5 phút)
       ├ chọn chuyến ─▶ polyline + marker Start/End
       └ thẻ thống kê: tổng km · thời lượng · tốc độ TB
```

---

## 5. UI Specifications

### 5.1 Design System

Material 3, dynamic color **tắt** (để màu zone không bị đổi theo wallpaper máy demo).
Theme sáng là chính; theme tối hoạt động nhưng không phải mục tiêu demo.

### 5.2 Color Palette

| Vai trò | Màu | Dùng ở đâu |
|---|---|---|
| Primary | `#1B6EF3` | Nút chính, marker của mình, polyline của mình |
| Zone enter | `#2E7D32` | Icon "đã đến" ở Timeline |
| Zone exit | `#C62828` | Icon "đã rời" ở Timeline |
| Zone fill | màu zone @ 20% alpha | Nền hình tròn zone |
| Zone stroke | màu zone @ 100% | Viền hình tròn, dày 2dp |
| Member colors | `#1B6EF3` `#E5820C` `#7B3FF2` | Ba màu cho ba thành viên, cố định |
| Surface / Background | `#FFFFFF` / `#F5F6F8` | Thẻ, nền |

**Sáu màu zone định sẵn (US-20):** `#1B6EF3` `#2E7D32` `#E5820C` `#C62828` `#7B3FF2` `#00838F`.

### 5.3 Spacing

Thang 4dp: `xs 4` · `sm 8` · `md 16` · `lg 24` · `xl 32`.
Lề màn hình 16dp. Khoảng cách giữa các thẻ 12dp. Chiều cao tối thiểu vùng chạm 48dp.

### 5.4 Typography

| Style | Dùng cho |
|---|---|
| `headlineSmall` | Tiêu đề màn hình |
| `titleMedium` | Tên zone trong danh sách, tên chuyến đi |
| `bodyMedium` | Nội dung chung |
| `labelLarge` | Nhãn nút |
| `labelSmall` | Giờ, đơn vị, chú thích |

Không hardcode `.sp` ngoài `Type.kt`.

### 5.5 Map Screen Layout

```
┌─────────────────────────────────────┐
│  FamilyTracker          [⚙]         │  top bar
├─────────────────────────────────────┤
│                                     │
│         ○ ─ ─ ─ ─ ─ ─               │  zone "Nhà" (circle)
│       ╱   Nhà        ╲              │
│      │        ●       │             │  ● = vị trí của mình
│       ╲             ╱               │
│         ─ ─ ─ ─ ─ ─                 │
│                                     │
│              ▲ Minh                 │  marker thành viên giả
│                                     │
│                          ┌────────┐ │
│                          │ ◉ Theo │ │  công tắc theo dõi (US-09)
│                          │  dõi   │ │
│                          └────────┘ │
├─────────────────────────────────────┤
│  [Bản đồ] [Zone] [Lịch sử] [Nhật ký]│  bottom navigation
└─────────────────────────────────────┘
```

### 5.6 Zone Editor Layout

```
┌─────────────────────────────────────┐
│  ←   Zone mới                 [Lưu] │  Lưu bị vô hiệu khi tên trống
├─────────────────────────────────────┤
│         ○ ─ ─ ─ ─ ─ ─               │
│       ╱       ✛       ╲             │  ✛ crosshair = tâm zone,
│      │                │             │     luôn ở giữa; kéo bản đồ để đổi
│       ╲             ╱               │
│         ─ ─ ─ ─ ─ ─                 │
├─────────────────────────────────────┤
│  Tên zone                           │
│  ┌─────────────────────────────────┐│
│  │ Nhà                             ││  1–40 ký tự
│  └─────────────────────────────────┘│
│                                     │
│  Bán kính              200 m        │
│  ├────●──────────────────────┤      │  50m ─ 2000m
│  ⚠ Dưới 100m có thể không ổn định   │  chỉ hiện khi < 100m
│                                     │
│  ● ● ● ● ● ●                        │  6 màu định sẵn
│                                     │
│  Thông báo khi vào          [ON  ]  │
│  Thông báo khi rời          [ON  ]  │
└─────────────────────────────────────┘
```

### 5.7 History Screen Layout

```
┌─────────────────────────────────────┐
│  Lịch sử       [📅 21/08/2026]      │  date picker, giới hạn 7 ngày
├─────────────────────────────────────┤
│      ╭──╮                           │
│   ●──╯  ╰──╮      ○ ─ ─ ─           │  ● Start (xanh)
│            ╰───▶ ◉│  Nhà   │        │  ◉ End (đỏ)
│                   ╲ ─ ─ ─ ╱         │  polyline cắt qua zone
├─────────────────────────────────────┤
│  3.2 km  ·  28 phút  ·  6.8 km/h    │  thẻ thống kê (US-29)
├─────────────────────────────────────┤
│  Chuyến trong ngày                  │
│  ┌─────────────────────────────────┐│
│  │ 07:12 → 07:40    2.1 km         ││  tách theo khoảng trống > 5'
│  │ 17:05 → 17:15    1.1 km    ✓    ││  ✓ = chuyến đang được vẽ
│  └─────────────────────────────────┘│
│                                     │
│            [ ▶ Mô phỏng lộ trình ]  │  chỉ có ở build debug
└─────────────────────────────────────┘
```

### 5.8 Timeline Screen Layout

```
┌─────────────────────────────────────┐
│  Nhật ký                            │
├─────────────────────────────────────┤
│  Hôm nay                            │  header dính
│  ┌─────────────────────────────────┐│
│  │ ↘ 🟢  Đã đến Nhà        17:15   ││
│  │ ↗ 🔴  Đã rời Trường     16:40   ││
│  └─────────────────────────────────┘│
│  Hôm qua                            │
│  ┌─────────────────────────────────┐│
│  │ ↘ 🟢  Đã đến Trường     07:38   ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

---

## 6. Configuration & Constants

Không có remote config — app chạy hoàn toàn cục bộ. Mọi ngưỡng nằm trong một file hằng số
duy nhất ở `:domain` để QA đọc được mà không cần mở code từng chỗ.

| Hằng số | Giá trị | Ảnh hưởng khi đổi |
|---|---|---|
| `LOCATION_INTERVAL_MS` | 10_000 | Nhỏ hơn → lộ trình mượt hơn, tốn pin hơn |
| `MIN_DISTANCE_M` | 10 | Nhỏ hơn → polyline rối khi đứng yên |
| `MAX_ACCURACY_M` | 50 | Lớn hơn → nhận điểm rác trong nhà |
| `MAX_SPEED_KMH` | 200 | Lớn hơn → một cú nhảy GPS kéo polyline đi xa |
| `ZONE_EXIT_BUFFER_M` | 30 | Nhỏ hơn → dội thông báo khi đứng ở mép zone |
| `EVENT_DEDUPE_WINDOW_MS` | 60_000 | Nhỏ hơn → nhận 2 thông báo cho 1 lần vào zone |
| `SESSION_GAP_MS` | 300_000 (5 phút) | Quyết định cách tách chuyến đi |
| `HISTORY_RETENTION_DAYS` | 7 | Lớn hơn → màn History vẽ chậm dần |
| `MAX_ZONES` | 100 | Giới hạn cứng của Play Services, **không được tăng** |
| `ZONE_RADIUS_MIN_M` / `MAX_M` | 50 / 2000 | |
| `ZONE_RADIUS_DEFAULT_M` | 150 | |
| `SIMULATOR_ENABLED` | `true` ở **cả debug lẫn release** | Demo chạy bản release (xem §7.2), mà F5 là P0 — gắn nút mô phỏng vào `BuildConfig.DEBUG` sẽ ẩn mất nó khỏi chính bản đem đi demo. Khai bằng `buildConfigField` riêng, không dùng lại cờ DEBUG. |

**Kiếm tiền (quảng cáo, IAP): không áp dụng cho bản demo.**

---

## 7. Non-Functional Requirements

### 7.1 Performance

| Yêu cầu | Ngưỡng | Cách đo |
|---|---|---|
| Thời gian mở app tới khi bản đồ vẽ xong | < 2.5s trên thiết bị tầm trung | Đồng hồ bấm tay, 3 lần lấy trung bình |
| Vẽ lộ trình một ngày | < 1s từ lúc chọn ngày | Trace thủ công. **Chu kỳ 10 giây = tối đa 8.640 điểm thô mỗi ngày** trước khi lọc; sau `LocationFilter` con số thực tế thấp hơn nhiều nhưng không có trần đảm bảo. Bắt buộc giảm mẫu Douglas-Peucker (dung sai 10m) trước khi vẽ, dùng `maps-compose-utils`. |
| Kéo slider bán kính | Hình tròn cập nhật không giật ở 60fps | Quan sát |
| Tiêu thụ pin khi theo dõi liên tục | < 8%/giờ | Battery Historian hoặc màn Pin của hệ thống |
| Kích thước DB sau 7 ngày theo dõi | < 20 MB | `adb shell du` |

### 7.2 Compatibility

| Hạng mục | Giá trị |
|---|---|
| minSdk | 28 (Android 9) |
| targetSdk / compileSdk | 36 / 36.1 |
| Thiết bị demo bắt buộc | Có Google Play Services, có GPS |
| Không hỗ trợ | Thiết bị không có Play Services (Huawei mới) — geofence API không tồn tại |
| Hướng màn hình | Chỉ dọc ở v1.0 |
| **Build đem demo** | **`release`** — để đo hiệu năng Compose đúng như môi trường thật (bản `debug` có chi phí của composition tracing và live literals nên số đo vô nghĩa) |
| Ký bản release | Ký bằng **debug keystore** một cách tường minh. Lý do: giữ nguyên SHA-1, nhờ đó **một** hạn chế API key phủ cả hai variant. Đây là lựa chọn cho demo, không dùng để phát hành thật |
| Rút gọn mã (R8) | Giữ **tắt** như template. Bật R8 sẽ kéo theo việc phải viết keep-rule cho Room, Koin và kotlinx-serialization — rủi ro không tương xứng với lợi ích ở bản demo |

### 7.3 Security & Privacy

| Yêu cầu |
|---|
| Toàn bộ dữ liệu vị trí nằm trên thiết bị. Không có network call nào gửi vị trí đi |
| **`android:allowBackup="false"`** — manifest template hiện đang để `true`, nghĩa là CSDL vị trí có thể theo Google Backup rời khỏi máy. Mâu thuẫn trực tiếp với dòng trên |
| Maps API key đọc từ `local.properties`, không commit, phải restrict theo package + SHA-1 |
| Không log toạ độ thật ra logcat ở build release |
| Thông báo thường trực của foreground service phải nói rõ app đang theo dõi vị trí |
| Người dùng tắt công tắc theo dõi → service dừng và không ghi thêm điểm nào |

### 7.4 Reliability

| Yêu cầu |
|---|
| Geofence được đăng ký lại sau khi khởi động lại thiết bị (`BOOT_COMPLETED`) |
| Foreground service bị hệ thống kill → thông báo qua Geofencing API vẫn hoạt động |
| Mất tín hiệu GPS → app không crash, lộ trình chỉ bị đứt đoạn |
| Từ chối quyền → app vẫn mở được, ở chế độ giảm chức năng có giải thích |
| Room dùng `fallbackToDestructiveMigration()` ở giai đoạn demo — dữ liệu demo mất khi đổi schema là chấp nhận được |

### 7.5 Localization

Chỉ **tiếng Việt** ở v1.0. Mọi chuỗi nằm trong `res/values/strings.xml`, không hardcode
trong composable — để thêm `values-en/` sau này là việc dịch chứ không phải việc sửa code.
Định dạng ngày `dd/MM/yyyy`, giờ 24h `HH:mm`, khoảng cách theo hệ mét.

---

## 8. Internal Contracts

**Không có API mạng.** Phần này thay chỗ cho mục API: nó mô tả các hợp đồng nội bộ giữa các
tầng, vì đó là thứ đóng vai trò tương đương trong dự án này.

```kotlin
// :domain/repository/ — tầng UI chỉ nhìn thấy những interface này
interface ZoneRepository {
    fun observeAll(): Flow<List<Zone>>
    suspend fun save(zone: Zone): AppResult<Zone>
    suspend fun delete(zoneId: String): AppResult<Unit>
    suspend fun count(): Int
}

interface TrackingRepository {
    fun observeRoute(memberId: String, day: LocalDate): Flow<List<TrackSession>>
    suspend fun record(point: LocationPoint)
    suspend fun purgeOlderThan(days: Int)
    fun isTracking(): Flow<Boolean>
    suspend fun setTracking(enabled: Boolean)
}

interface ZoneEventRepository {
    fun observeTimeline(sinceDays: Int): Flow<List<ZoneEvent>>
    suspend fun record(event: ZoneEvent)   // nơi DUY NHẤT áp dụng luật khử trùng lặp 60s
}

interface LocationSource {                  // hai impl: thật và mô phỏng
    fun stream(): Flow<LocationPoint>
}
```

**Mọi sự kiện zone, dù đến từ Geofencing API hay từ foreground service, đều đi qua
`ZoneEventRepository.record()`.** Đó là lý do luật khử trùng lặp chỉ cần viết một lần và
không thể bị bỏ sót ở một trong hai đường.

---

## 9. Data Models

```kotlin
// :domain/model/
data class Zone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,      // 50f .. 2000f
    val colorArgb: Int,
    val notifyOnEnter: Boolean,
    val notifyOnExit: Boolean,
    val createdAt: Instant,
)

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDegrees: Float,
    val recordedAt: Instant,
)

data class ZoneEvent(
    val id: String,
    val zoneId: String,
    val zoneName: String,
    val memberId: String,
    val type: ZoneEventType,      // ENTER | EXIT
    val occurredAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val source: EventSource,      // GEOFENCE_API | FOREGROUND — để QA kiểm chứng dedupe
)

// Kieu SUY RA luc doc, KHONG co bang Room tuong ung.
// Mot "chuyen di" = mot cum diem lien tiep cach nhau duoi SESSION_GAP_MS.
// Luu thanh bang rieng se buoc service phai quyet dinh *khi nao* chuyen ket thuc,
// phai back-fill khi app bi kill, va phai xu ly khi hai nguon lech nhau.
data class TrackSession(
    val id: String,               // sinh luc doc: memberId + mocc thoi gian bat dau
    val memberId: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val points: List<LocationPoint>,
    val distanceMeters: Double,
)

data class Member(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val isSelf: Boolean,
)
```

```kotlin
// :ui/feature/history/HistoryContract.kt — ví dụ một Contract
data class HistoryState(
    val selectedDay: LocalDate,
    val sessions: List<TrackSession> = emptyList(),
    val selectedSessionId: String? = null,
    val stats: RouteStats? = null,
    val isLoading: Boolean = false,
    val isSimulating: Boolean = false,
) : UiState

sealed interface HistoryIntent : UiIntent {
    data class SelectDay(val day: LocalDate) : HistoryIntent
    data class SelectSession(val id: String) : HistoryIntent
    data object StartSimulation : HistoryIntent
}

sealed interface HistoryEffect : UiEffect {
    data class ShowError(val message: String) : HistoryEffect
    data class FocusCamera(val lat: Double, val lng: Double) : HistoryEffect
}
```

```kotlin
// :ui/navigation/Routes.kt
@Serializable data object MapRoute
@Serializable data object ZoneListRoute
// US-10 nhan giu ban do -> mo editor voi tam la diem vua chon, nen route phai cho toa do.
@Serializable data class ZoneEditorRoute(
    val zoneId: String? = null,      // null = tao moi
    val lat: Double? = null,         // tam ban dau khi tao moi
    val lng: Double? = null,
)
// US-35 bam mot su kien o Timeline -> mo History va canh camera vao dung vi tri do.
@Serializable data class HistoryRoute(
    val epochDay: Long? = null,
    val focusLat: Double? = null,
    val focusLng: Double? = null,
)
@Serializable data object TimelineRoute
```

---

## 10. Telemetry & QA Events

Bản demo **không tích hợp SDK analytics**. Thay vào đó, các sự kiện dưới đây được ghi ra
logcat với tag `FTD_EVENT` để QA xác minh hành vi bằng `adb logcat -s FTD_EVENT`.
Đây là công cụ nghiệm thu, không phải sản phẩm phân tích.

| Sự kiện | Tham số | Dùng để xác minh |
|---|---|---|
| `permission_result` | `type`, `granted` | US-01→US-04 |
| `tracking_toggled` | `enabled` | US-09 |
| `location_recorded` | `accuracy`, `filtered` | Bộ lọc nhiễu đang chạy — US-31 |
| `location_dropped` | `reason` (`ACCURACY`/`DISTANCE`/`SPEED`) | Từng luật lọc bắn đúng |
| `zone_saved` | `zoneId`, `radius`, `totalZones` | US-16→US-21 |
| `geofence_registered` | `zoneId`, `success` | Đăng ký thật sự thành công |
| `zone_event_raised` | `zoneId`, `type`, `source` | Phân biệt hai đường phát hiện |
| `zone_event_deduped` | `zoneId`, `type`, `gapMs` | **Luật khử trùng lặp 60s — US-25** |
| `notification_posted` | `zoneId`, `type` | US-22→US-24 |
| `simulation_started` / `_finished` | `durationMs`, `eventsRaised` | US-33 |
| `history_rendered` | `day`, `pointCount`, `renderMs` | Ngưỡng < 1s ở §7.1 |
| `purge_completed` | `deletedPoints`, `deletedEvents` | Lưu trữ 7 ngày |

---

## 11. Release Plan

### 11.1 Current Release — v1.0 (Demo)

| Feature | Trạng thái |
|---|---|
| F1 Zone Management | Hoàn thành (phase-05/06) — US-07, US-10, US-12→US-21 kiểm trên APK release |
| F2 Geofence Notification | Hoàn thành phần tầng (b); US-24 (app đóng + geofence nền) HOÃN — cần máy thật mở khoá được (phase-07/11) |
| F3 History Tracking | Hoàn thành (phase-08) — US-09, US-27→US-32 kiểm trên APK release |
| F4 Zone Timeline | Hoàn thành (phase-10) — US-34→US-36 kiểm trên APK release |
| F5 Route Simulator | Hoàn thành (phase-09) — US-33 kiểm trên APK release, rehearsal G4 xem dưới |

**Quality gates — không đạt thì không demo. Kết quả đo phase-11 (`reports/dev-phase-11-report.md`,
`reports/gate-evidence.md`, `reports/g1-p0-story-checklist.md`):**

| # | Gate | Kết quả phase-11 |
|---|---|---|
| G1 | Toàn bộ story P0 đạt acceptance criteria | **25/26 Đạt, 1 HOÃN** (US-24 — cùng lý do G5). Số P0 thật là 26, không phải 22 — xem `reports/g1-p0-story-checklist.md` mục "Lệch số đếm" |
| G2 | Unit test cho `ZoneEvaluator`, `LocationFilter`, `RouteStats` xanh, phủ các trường hợp biên (đứng đúng mép zone, GPS nhảy, điểm trùng) | **PASS** — 58 test `:domain`, đủ 14/14 trường hợp biên |
| G3 | `KoinModulesTest.checkModules()` xanh — không có binding thiếu | **PASS** — dùng `verify()` (`checkModules()` đã deprecated, LLM.md §13 Fixed #3) |
| G4 | Nút mô phỏng lộ trình sinh ra **cả** thông báo vào và ra, trong ≤ 40 giây, trên thiết bị demo thật | **HOÃN** — máy thật khoá màn hình bằng mật khẩu thật. Rehearsal emulator: 17.4s và 17.5s (2/2 lần, xa dưới 40s) |
| G5 | Đóng app khỏi recents rồi bước qua ranh giới zone thật → có thông báo trong ≤ 3 phút | **HOÃN** — cùng lý do G4. Đã xác nhận không cần mở khoá: cài release không crash, quyền cấp được, geofence đăng ký được với dữ liệu thật, không thông báo ma |
| G6 | Chạy `./gradlew assembleDebug` không lỗi, không warning mới | **PASS** — 1 warning (khớp baseline). **Luật đo: bắt buộc `--no-configuration-cache`**, xem `ENV-BRIEFING.md` §8 |
| G7 | Không có toạ độ thật hoặc API key nào trong log của build release. **R8 đang tắt nên nó không xoá log hộ** — phải tự chặn bằng một cờ trong code, không trông vào trình rút gọn | **PASS** — cổng `FtdLog` mới (phase-11), `FTD_EVENT` = 0 dòng trên release; grep toạ độ/API key theo PID app = rỗng |
| G8 | `./gradlew assembleRelease` ra APK **đã ký** và cài được, và bản đồ hiện đúng trên APK đó (chứng minh SHA-1 của bản release nằm trong hạn chế của API key) | **PASS** |

### 11.2 Next Release — nếu demo được duyệt

| Hạng mục |
|---|
| Backend + đồng bộ nhiều thiết bị thật, ghép nhóm gia đình |
| Đăng nhập / phân quyền xem vị trí của nhau |
| Zone đa giác (tự tính, không dùng Geofencing API) |
| Lịch sử dài hạn + xuất báo cáo |
| Cổng kiểm tra Compose stability (đang mở ở `LLM.md` §13) |
| Migration thật cho Room, thay `fallbackToDestructiveMigration()` |

### 11.3 Version History

| Version | Ngày | Nội dung |
|---|---|---|
| 1.0 | 2026-08-21 | Bản PRD đầu tiên, BA xác nhận |
| 1.2 | 2026-08-21 | Ba quyết định của chủ dự án: (1) bỏ bảng `track_sessions`, suy chuyến đi lúc đọc; (2) **demo chạy bản `release`** — kéo theo việc gỡ `SIMULATOR_ENABLED` khỏi `BuildConfig.DEBUG`, thêm signing config, thêm gate G8; (3) emulator là vòng lặp test chính, G5 vẫn giữ một lượt trên máy thật |
| 1.1 | 2026-08-21 | Sửa 3 khuyết tật nội tại phát hiện lúc lập kế hoạch, **không đổi phạm vi**: (a) §9 hai route không chở được toạ độ mà US-10/US-35 bắt buộc phải truyền; (b) §7.1 ước lượng số điểm mỗi ngày sai (8.640 thô, không phải ~2000); (c) §7.3 thiếu `allowBackup="false"` trong khi vẫn tuyên bố dữ liệu không rời thiết bị |

---

## Appendix

### A.1 Key Files Reference

| Vai trò | Đường dẫn |
|---|---|
| Bản đồ mã nguồn, luật kiến trúc | `LLM.md` |
| Cách viết một màn hình MVI | `docs/android-mvi-best-practices.md` |
| Thuật toán vào/rời zone | `:domain/tracking/ZoneEvaluator.kt` |
| Lọc nhiễu GPS | `:domain/tracking/LocationFilter.kt` |
| Nguồn vị trí (thật + mô phỏng) | `:data/location/` |
| Đăng ký geofence | `:data/geofence/GeofenceRegistrar.kt` |
| Khử trùng lặp sự kiện | `:data/repository/ZoneEventRepositoryImpl.kt` |
| Hằng số ngưỡng | `:domain/tracking/TrackingConstants.kt` |

### A.2 Third-Party Integrations

| Thư viện | Vai trò | Ghi chú |
|---|---|---|
| Google Maps SDK + maps-compose | Bản đồ, Circle, Polyline, Marker | Cần API key có billing |
| Play Services Location | FusedLocationProvider | |
| Play Services Location (Geofencing) | Phát hiện vào/rời khi app đóng | Giới hạn 100 geofence/app |
| Room | Lưu trữ cục bộ | KSP |
| Koin | Dependency injection | `checkModules()` bắt buộc trong test |
| navigation-compose | Điều hướng type-safe | + kotlinx-serialization |

### A.3 Build Configuration

```
applicationId  com.example.pion.family.tracker.demo
minSdk 28 · targetSdk 36 · compileSdk 36.1 · Java 11
AGP 9.2.1 · Kotlin 2.2.10 · Compose BOM 2026.02.01
MAPS_API_KEY  đọc từ local.properties -> manifestPlaceholders (không commit)
Build đem demo  release, ký bằng debug keystore (giữ nguyên SHA-1 -> 1 API key cho cả 2 variant)
R8             tắt (optimization { enable = false }) -> R8 KHONG tu xoa log ho
Simulator      buildConfigField rieng, bat o ca debug lan release
```

### A.4 Câu hỏi cần BA xác nhận

Đây là những chỗ tôi đã tự quyết để không chặn tiến độ. Nếu BA trả lời khác, phạm vi thay đổi.

| # | Câu hỏi | Giả định đang dùng | Ảnh hưởng nếu khác |
|---|---|---|---|
| Q1 | Demo chạy trên **một** thiết bị thật, các thành viên khác là dữ liệu giả — đúng không? | Đúng | Nếu cần nhiều máy thật thấy nhau → phải thêm backend, scope tăng ~3× |
| Q2 | Lưu lịch sử **7 ngày** có đủ cho buổi demo không? | 7 ngày | Dài hơn thì cần đo lại hiệu năng vẽ ở §7.1 |
| Q3 | BA có chấp nhận độ trễ **30 giây – 3 phút** của thông báo khi app đóng không? | Chấp nhận, và bù bằng phản hồi tức thì khi app mở | Nếu yêu cầu luôn tức thì → phải giữ foreground service chạy 24/7, pin tụt rõ rệt |
| Q4 | Zone chỉ là **hình tròn** — có đủ không? | Đủ | Đa giác thì mất đường Geofencing API, phải tự tính hoàn toàn |
| Q5 | App chỉ có **tiếng Việt** ở bản demo? | Đúng | Thêm tiếng Anh là việc dịch, không phải sửa code |
| Q6 | "Thông báo" nghĩa là **push cục bộ trên máy** hay còn cần gửi cho người khác (SMS/app khác)? | Push cục bộ | Gửi cho người khác cần backend |
| Q7 | Có cần xem lộ trình của **thành viên giả** không, hay chỉ của mình? | Chỉ của mình; thành viên giả chỉ hiện marker tĩnh trên bản đồ | Nếu cần → phải sinh cả lịch sử giả cho họ |
