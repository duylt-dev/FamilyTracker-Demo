# Kịch bản demo — FamilyTrackerDemo v1.0

> Viết ở phase-11. Được `LLM.md` §13 Open #2 viện dẫn làm **biện pháp giảm thiểu chính thức** cho giới
> hạn bán kính zone của trình mô phỏng. Đọc mục "Cạm bẫy" trước khi đứng trước khách hàng.

## Chuẩn bị

| | |
|---|---|
| Bản đem demo | **release** (`app-release.apk`) — không phải debug. Quyết định #2 của chủ dự án. |
| Ký | debug keystore, cố ý — SHA-1 giống nhau nên **một** hạn chế API key phủ cả hai variant |
| Thiết bị | Emulator Google APIs, hoặc máy thật đã cấp quyền vị trí **"Luôn cho phép"** |
| Trước khi bắt đầu | Mở app **một lần** rồi đóng lại — lần mở đầu tiên phải đi qua 3 bước xin quyền |

Nút **"Mô phỏng lộ trình"** có mặt trên bản release (đã xác minh, gate G4 rehearsal) — nó **không**
gắn với `BuildConfig.DEBUG`, chính là để không biến mất đúng lúc đem đi demo.

## Thứ tự trình diễn

### 1. Bản đồ (F1 — US-06→US-11)
Mở app → tab **Bản đồ**. Chỉ ra: vị trí các thành viên bằng marker màu riêng, zone vẽ dạng vòng tròn
có nhãn tên, công tắc **"Theo dõi vị trí"** ở góc dưới.

Bật công tắc → thông báo thường trực "Đang theo dõi vị trí" hiện ở khay. Đây là foreground service
thật, không phải mô phỏng.

### 2. Quản lý zone (F1 — US-12→US-21)
Tab **Zone** → danh sách zone, mỗi dòng có công tắc bật/tắt thông báo, vuốt để xoá.

Bấm **"Tạo zone"** → màn Editor: kéo bản đồ để đặt tâm (crosshair ở giữa), thanh trượt bán kính,
bảng 6 màu, hai công tắc "Thông báo khi vào"/"khi rời".

> **Để bán kính ở mức mặc định 150m** nếu sau đó có định bấm "Mô phỏng lộ trình". Xem mục Cạm bẫy #1.

Lưu → quay lại tab Bản đồ, zone mới hiện ngay dạng vòng tròn.

Cách vào nhanh hơn: **nhấn giữ** một điểm bất kỳ trên bản đồ → mở thẳng Editor tại đúng toạ độ đó.

### 3. Mô phỏng + thông báo geofence (F5 + F2 — US-33, US-22→US-26)
Tab **Lịch sử** → bấm **"▶ Mô phỏng lộ trình"**.

Khoảng **17–18 giây** sau sẽ có thông báo **"Đã đến [tên zone]"**, rồi thông báo **"Đã rời [tên zone]"**.
Toàn bộ lượt mất ~30 giây (trần gate G4 là 40 giây).

Điểm đáng nói với khách: tuyến mô phỏng đi **chung một đường ống** với vị trí GPS thật — nó sinh ra
thông báo thật, sự kiện thật trong cơ sở dữ liệu, và polyline thật. Không có đường tắt riêng cho demo.

### 4. Lịch sử di chuyển (F3 — US-27→US-32)
Vẫn ở tab **Lịch sử**: polyline của tuyến vừa mô phỏng đã hiện, có marker xanh (điểm đầu) và đỏ
(điểm cuối), kèm thống kê **quãng đường · thời lượng · vận tốc trung bình**.

Bấm nút chọn ngày ở góc trên phải → chọn ngày khác trong 7 ngày gần nhất. Danh sách chuyến đi bên
dưới: mỗi chuyến là một nhóm điểm liên tiếp, chạm để camera bay tới chuyến đó.

Con số cho người hỏi kỹ: ở quy mô PRD (**8.640 điểm/ngày**, một điểm mỗi 10 giây suốt 24 giờ), toàn
bộ đường ống truy vấn → tách chuyến → tính thống kê → giản lược polyline mất **~500ms**, và phần
giản lược chạy ngoài luồng giao diện nên màn hình không đứng.

### 5. Nhật ký zone (F4 — US-34→US-36)
Tab **Nhật ký** → các lần vào/rời zone, nhóm theo ngày với tiêu đề **"Hôm nay" / "Hôm qua" /
`dd/MM/yyyy`**, chấm xanh cho lượt vào, chấm đỏ cho lượt rời.

Chạm một mục → mở màn Lịch sử của đúng ngày đó, **camera căn thẳng vào toạ độ sự kiện**.

## Cạm bẫy — đọc trước khi demo

### 1. Đừng tạo zone bán kính lớn rồi bấm Mô phỏng
`LLM.md` §13 Open #2. Ngân sách thời gian của trình mô phỏng cố định (~30 giây, để lọt trần G4 40
giây), nhưng quãng đường phải đi thì tỉ lệ với bán kính zone. Zone càng lớn, tốc độ suy ra giữa hai
điểm càng cao — vượt `MAX_SPEED_KMH` (200 km/h) khi bán kính quá **~683m**, và bộ lọc nhiễu GPS sẽ
loại bớt điểm.

**Hậu quả nhìn thấy được: chỉ hiện một trong hai thông báo.** Nó trông y hệt một lỗi geofence, trong
khi thực chất là giới hạn đã biết của trình mô phỏng.

**Cách tránh:** dùng bán kính mặc định **150m** cho mọi zone định đem mô phỏng. Nếu cần trình bày
zone bán kính lớn (ví dụ minh hoạ mức tối đa 2000m), cứ tạo — chỉ **đừng bấm nút mô phỏng** trên
zone đó.

### 2. Đóng app bằng cách vuốt khỏi recents, đừng "Force stop"
`Force stop` xoá đăng ký geofence khỏi hệ thống. Vuốt khỏi recents thì không. Nếu định trình diễn
"app đã đóng vẫn nhận được thông báo", phải vuốt.

### 3. Hai gate chưa xác minh trên máy thật
| Gate | Trạng thái |
|---|---|
| **G4** — mô phỏng sinh đủ 2 thông báo ≤ 40s **trên thiết bị demo thật** | **HOÃN.** Đã đo trên emulator: 17,4s và 17,5s (2/2 lượt). |
| **G5** — đóng app, bước qua ranh giới thật → thông báo ≤ 3 phút | **HOÃN.** Cần người cầm máy đi bộ. |

Đừng hứa quá về hai điều này. Nếu khách hỏi, nói thẳng: đã kiểm đầy đủ trên emulator có Play Services
thật, phần còn thiếu là một lượt chạy ngoài trời trên máy thật.

Cụ thể hơn về hạn chế: nút mô phỏng bơm vị trí vào đường ống **nội bộ** của app, nên nó kiểm được
đường phát hiện của foreground service, **nhưng không kích hoạt được geofence của Play Services**.
Emulator làm được điều đó qua `adb emu geo fix` (đi qua provider của hệ thống); máy thật không có
đường tương đương nếu không cài app mock-location.

### 4. Trên máy Samsung/Xiaomi/Oppo: kiểm quyền nền và tối ưu pin trước
Quyền `ACCESS_BACKGROUND_LOCATION` thường phải cấp qua **Settings**, dialog không cấp thẳng được. Và
cơ chế tiết kiệm pin của hãng có thể giết service nền — vào Settings → Apps → FamilyTrackerDemo →
Battery → **Unrestricted** trước khi demo.

Nếu geofence **chỉ** hoạt động sau khi tắt tối ưu pin, đó là thông tin cần nói với khách, không phải
thứ để lặng lẽ bật rồi coi như không có.

### 5. Lần mở app đầu tiên
Phải đi qua **3 bước xin quyền** (vị trí khi dùng → vị trí nền → thông báo). Làm việc này **trước**
buổi demo, đừng để khách ngồi xem.

Nếu từ chối quyền vị trí, app **không crash** — bản đồ chuyển sang chế độ giảm chức năng kèm banner
giải thích. Đây là hành vi có chủ đích (PRD §7.4), có thể trình diễn nếu khách hỏi.
