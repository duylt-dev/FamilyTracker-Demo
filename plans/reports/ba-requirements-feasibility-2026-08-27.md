# Báo cáo khả thi & hiện trạng — 3 nhóm yêu cầu của BA

**Ngày:** 2026-08-27 · **Repo:** FamilyTrackerDemo (`main`, commit `5819f05`)
**Cơ sở đối chiếu:** đọc mã nguồn `:domain`/`:data`/`:ui` + `LLM.md` §13, `docs/project-changelog.md`
**Kiểm chứng build:** `./gradlew test` → **354 unit test, PASS toàn bộ** (exit 0)

---

## 0. Kết luận một dòng

Cả **3/3 nhóm yêu cầu đều đã có implementation thật, chạy được, có test** — kết luận
"đã đáp ứng đủ" là **đúng ở mức demo**. Nhưng có **4 điểm lệch so với chữ nghĩa của
yêu cầu** mà BA cần biết trước khi nghiệm thu (mục 4), và toàn bộ đều đã được ghi
sẵn trong `LLM.md` §13 Open — không phải phát hiện mới.

| # | Yêu cầu BA | Khả thi | Hiện trạng | Lệch |
|---|---|---|---|---|
| 1 | Tạo zone trên map + thông báo vào/rời | Cao — đã xong | ✅ Đầy đủ | Chỉ chạy khi app còn sống |
| 2 | History tracking lộ trình trên map | Cao — đã xong | ✅ Đầy đủ | Hiệu năng vượt ngân sách PRD 1,87× |
| 3a | Chỉ đường, tuyến ngắn nhất | Cao — đã xong | ✅ Có tuyến thật OSM | "Nhanh nhất" ≠ "ngắn nhất"; không có profile xe máy |
| 3b | Tự đổi tuyến khi 2 phía di chuyển | Cao — đã xong | ✅ Có reroute | Không realtime: debounce 60s / ngưỡng 200m |

---

## 1. Place Zone + thông báo vào/rời khu vực — ✅ ĐẠT

**Có gì:**

- Model `Zone` (tâm/bán kính/màu/2 công tắc `notifyOnEnter`/`notifyOnExit`), CRUD đầy đủ
  qua màn `ZoneEditor` vẽ trực tiếp trên map, giới hạn 100 zone, bán kính 50–2000m.
- Phát hiện vào/ra: `ZoneEvaluator` — hàm thuần, **có hysteresis** (vào khi `d < R`,
  chỉ ra khi `d > R + 30m`). Không có vùng đệm này thì đứng ngay mép zone sẽ nhận
  ENTER/EXIT liên tục theo sai số GPS.
- Chống spam: `ZoneEventDeduper` — cùng (member, zone, loại) trong 60s chỉ ghi 1 lần.
- Thông báo: `ZoneNotifier`, kênh riêng, tiêu đề mang **tên thành viên** ("Minh đã đến
  Trường học"), tap mở thẳng tab Nhật ký.
- Zone List hiển thị "ai đang ở trong zone nào" (`ObserveZoneMembershipUseCase`).
- Sự kiện **luôn được ghi vào Room kể cả khi tắt công tắc thông báo** — 2 công tắc chỉ
  chặn notification, không chặn timeline.

**Đánh giá:** vượt yêu cầu BA. BA chỉ hỏi "thông báo khi vào/rời", code còn có
timeline lịch sử sự kiện + chống rung + chống trùng.

**Cần biết:**

> **Không còn phát hiện vào/rời zone khi tiến trình app đã chết** (`LLM.md` §13 Open #4).
> Đường Geofencing API của Android đã bị gỡ vì nó chỉ bắn cho **thiết bị đang chạy app**,
> tức chỉ cho "tôi" — trong khi zone của app này theo dõi **người nhà**. Không có cách sửa
> nào tồn tại trong phạm vi một demo không có server (xem mục 4.1).

---

## 2. History tracking lộ trình trên map — ✅ ĐẠT

**Có gì:**

- Ghi điểm: foreground service, `LocationFilter` loại nhiễu (`accuracy > 50m`,
  `speed > 200km/h`, `distance < 10m`), lưu Room.
- Tách chuyến: `RouteSplitter` — khoảng lặng > 5 phút thì cắt sang chuyến mới.
- Màn Lịch sử: chọn ngày (`DayPickerBar`), danh sách chuyến, vẽ polyline theo màu
  thành viên trên map, `RouteStatsCard` (quãng đường / thời gian / tốc độ).
- Vòng đời dữ liệu: `PurgeOldHistoryUseCase` xoá điểm + sự kiện cũ hơn **7 ngày**,
  chạy 1 lần lúc khởi động app.

**Cần biết:**

> **Ngân sách hiệu năng PRD §7.1 ("< 1 giây" cho 8 640 điểm) KHÔNG ĐẠT trên máy thật**
> (`LLM.md` §13 Open #24): đo 1 870ms / 1 861ms trên `SM-A165F` — vượt 1,87×. Con số
> "498ms, dư 2×" trước đó là **đo trên emulator**, host CPU nhanh hơn chip máy thật.
> Đây là con số cần BA quyết: hoặc nới ngân sách, hoặc phải simplify polyline mạnh hơn.

---

## 3. Chỉ đường tới thành viên theo thời gian thực — ✅ ĐẠT (có điều kiện)

### 3a. Tuyến đường ngắn nhất

- Kiến trúc `RoutingProvider` cắm rời được, **2 engine đã implement thật**:
  `GraphHopperRoutingProvider` và `ValhallaRoutingProvider`, dữ liệu OSM.
- Có mapper DTO→domain riêng, decode polyline, guard hình học (`RouteGeometryGuard`
  từ chối tuyến giải mã sai precision thay vì vẽ đường sai lên map), sanitize toạ độ
  khỏi log lỗi, cache tuyến trên máy.
- Đã có ghi công OSM/nhà cung cấp trên UI (`RoutingAttribution`) — yêu cầu pháp lý.

**Lệch chữ nghĩa:** GraphHopper mặc định trả **tuyến NHANH NHẤT (fastest)**, không phải
tuyến **NGẮN NHẤT theo mét (shortest)**. Trong đô thị hai tuyến này thường khác nhau.
Nếu BA thật sự cần "ngắn nhất theo quãng đường", cần thêm tham số optimize — là thay
đổi 1 dòng URL, nhưng **phải BA chốt** vì nó đổi hành vi người dùng thấy.

### 3b. Đổi tuyến khi một trong hai phía di chuyển

`RerouteEvaluator` — hàm thuần, có test, xử lý **cả hai chiều** BA yêu cầu:

| Trigger | Ngưỡng | Ghi chú |
|---|---|---|
| **Người bị theo dõi đổi chỗ** | đích lệch > **200m** so với điểm cuối tuyến | Đúng vế "người bị theo dõi thay đổi" |
| **Người theo dõi đi chệch** | > **45m** khỏi polyline, **3 mẫu liên tiếp** | Đúng vế "người theo dõi thay đổi lộ trình" |
| Đã tới nơi | < 50m (thoát ở 70m — hysteresis) | Dừng gọi lại |
| Chặn gọi lặp | debounce **60 giây** | Chống vòng lặp lỗi đốt quota |

Có cả nhánh giảm cấp: mất mạng/provider lỗi thì **giữ nguyên tuyến đã vẽ** (không xoá
trắng) và hiển thị đường chim bay nét đứt kèm khoảng cách ước tính.

---

## 4. Bốn điểm BA cần biết trước khi nghiệm thu

### 4.1. Chuyển động của Minh/Lan là MÔ PHỎNG, không phải thiết bị thật

Đây là điểm quan trọng nhất về khả thi khi lên production. App **không có server**.
Minh và Lan không có thiết bị nào phát vị trí lên, nên `MemberMovementSimulator` sinh
chuyển động cho họ (bám polyline OSM thật, tốc độ 8.3 m/s, nhịp 2.5s).

**Hệ quả:** toàn bộ nhóm yêu cầu 1 và 3 hiện đang chạy trên dữ liệu mô phỏng. Bản
thương mại cần thêm: backend nhận vị trí, app trên máy người được theo dõi, push
notification server-side. **Đó là một dự án riêng, không phải phần bổ sung.**

### 4.2. "Thời gian thực" hiện đang là 60 giây, không phải tức thời

Trong mã nguồn có ghi rõ *"BA đã chốt tính năng KHÔNG phải realtime navigation"*
(`NavigationContract.kt`). Lý do: **quota GraphHopper free tier là 500 credit/NGÀY**.
Gọi lại tuyến mỗi lần đích nhúc nhích sẽ đốt hết quota trong vài phút.

App bù đắp bằng thủ thuật 0 chi phí: nối marker với hai đầu tuyến bằng **đoạn nét đứt**,
nên tuyến "trông như" bám theo dù chưa gọi lại provider. Marker cũng được nội suy mượt
(đo thật: jank 0.62% @ 90Hz).

**Nếu BA cần realtime đúng nghĩa** → phải nâng gói trả phí hoặc self-host routing engine.
Đây là quyết định ngân sách, không phải quyết định kỹ thuật.

### 4.3. Ràng buộc pháp lý & phương tiện của engine hiện tại

- GraphHopper free tier là **non-commercial** — không dùng cho bản phát hành thương mại.
- Free tier chỉ có profile `[car, bike, foot]` — **không có `motorcycle`**. Xe máy là
  phương tiện mặc định ở Việt Nam, nên tuyến hiện tại đang tính theo **ô tô**.
- Valhalla (có `motorcycle`, miễn phí) đang trỏ **FOSSGIS — hạ tầng tình nguyện, chỉ
  dùng cho dev**. Bản release phải trỏ Stadia (trả phí) hoặc self-host.
- Điều khoản redistribution của GraphHopper **chưa được làm rõ** — thư đã soạn, chưa gửi.
- API key nằm trong `BuildConfig`, lộ thì phải build lại APK — sửa được khi có backend.

### 4.4. Tính năng chỉ đường KHÔNG có user story nào trong PRD

`LLM.md` §13 Open #8: PRD v1.2 có US-01→US-36, **không story nào mô tả "dẫn đường tới
thành viên khác"** — PRD được viết trước khi plan routing tồn tại. Nghiệm thu tính năng
này hiện **không có hợp đồng để đối chiếu**. Đề nghị BA bổ sung US-37→US-N.

*(Phụ: PRD §2 câu tổng kết "22×P0" đếm sai, đếm lại cột P thật ra 26×P0 — Open #3.)*

---

## 5. Chất lượng kỹ thuật — cơ sở để tin các kết luận trên

| Hạng mục | Trạng thái |
|---|---|
| Unit test | **354 test, PASS 100%**, chạy < vài giây |
| Instrumented test | 6 file (DAO Room, dedupe zone event, race condition) |
| Architecture test | 6 loại — chặn coroutine lách `launchSafely`, chặn nắn đường vào GPS thật, chặn rò toạ độ vào log, chặn đọc chéo "mất mạng" vs "lỗi provider" |
| Kiến trúc | MVI + Clean 4 module (`:app`/`:ui`/`:domain`/`:data`), toàn bộ thuật toán vị trí là hàm thuần test được không cần Android |
| Nợ kỹ thuật đã ghi nhận | 24 mục trong `LLM.md` §13, mỗi mục có quyết định rõ ràng (sửa / cố ý giữ / chờ BA) |

Điểm đáng chú ý: các số đo hiệu năng trong repo là **đo trên máy thật** (`SM-A165F`),
và repo đã tự bắt được 2 lần kết luận sai do đo nhầm trên emulator. Độ tin của số liệu
ở đây cao hơn mức thường thấy.

---

## 6. Đề xuất

**Với BA — 3 việc cần chốt:**

1. "Tuyến ngắn nhất" là **shortest (mét)** hay **fastest (thời gian)**? (mục 3a)
2. Ngân sách hiệu năng History: giữ "< 1s" (phải tối ưu thêm) hay nới lên "< 2s"? (mục 2)
3. Bổ sung user story cho tính năng Dẫn đường vào PRD version kế tiếp. (mục 4.4)

**Với PM — trước khi nghĩ tới bản thương mại:**

- Ngân sách routing engine (mục 4.2, 4.3) — chặn cả "realtime" lẫn "xe máy" lẫn giấy phép.
- Backend + app phía người được theo dõi (mục 4.1) — chặn toàn bộ giá trị thật của
  nhóm yêu cầu 1 và 3.

**Không cần làm gì thêm:** cả 3 nhóm yêu cầu đã có implementation hoàn chỉnh ở mức demo.
