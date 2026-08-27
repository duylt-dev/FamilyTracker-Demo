# Gate evidence — số đo thật G4, G5, G6, G7, G8

Chi tiết đầy đủ + lệnh đã chạy: `dev-phase-11-report.md`. File này chỉ gom số đo, để dễ đối chiếu
nhanh không phải đọc cả báo cáo.

## G4 — Mô phỏng sinh ENTER+EXIT ≤ 40s

**Chính thức (thiết bị demo thật, `RF8Y60B9NCZ`): CHƯA ĐO ĐƯỢC — máy khoá màn hình bằng mật khẩu
thật, không mở khoá được trong phiên này** (`wm dismiss-keyguard` thất bại → khoá có mật khẩu, không
phải kiểu vuốt). Xem "Cần chủ dự án làm" trong `dev-phase-11-report.md`.

**Rehearsal trên `emulator-5554` (APK release, không phải số chính thức):**

| Lần | ENTER | EXIT | Gap | Trong hạn 40s? |
|---|---|---|---|---|
| 1 | 05:20:49.760 | 05:21:07.205 | 17.4s | Có |
| 2 | 05:22:48.911 | 05:23:06.368 | 17.5s | Có |

## G5 — Đóng app, bước qua ranh giới → thông báo ≤ 3 phút

**Chính thức (đi bộ thật ngoài trời): CHƯA ĐO ĐƯỢC — cùng lý do G4** (máy khoá, không di chuyển
thật được, và mô phỏng nội bộ của nút "Mô phỏng lộ trình" không kích hoạt được geofence nền của Play
Services trên máy thật theo cách không cần di chuyển — chỉ emulator làm được nhờ `adb emu geo fix`
đi qua provider hệ thống).

**Đã xác nhận được, không cần mở khoá/di chuyển (trên `RF8Y60B9NCZ` thật):**

| Hạng mục | Kết quả |
|---|---|
| Cài APK release | Success |
| Chạy không crash | `pidof` có PID sống, `logcat` không có `FATAL EXCEPTION` |
| Cấp đủ 4 quyền qua `pm grant` | `granted=true` cả 4 |
| Geofence đăng ký được với dữ liệu thật | `geofence_registered zoneId=all count=1 success=true` (đo qua bản debug tạm, chèn 1 zone qua quy trình WAL, xoá sau khi xong) |
| Không thông báo ma lúc mở app | Không có `zone_event_raised`/`notification_posted` nào ngoài ý muốn |
| Log release câm | `FTD_EVENT` = 0 dòng |

## G6 — `assembleDebug`, không warning mới

```
./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
→ 1
```
Baseline (`reports/baseline-build-debug.log`, phase-01) = 1. **Khớp — không warning mới.**
**Luật đo bắt buộc: `--no-configuration-cache`** — thiếu cờ này, config cache còn ấm sẽ bỏ qua pha
configuration và trả về `0`, không tất định (ENV-BRIEFING.md §8).

## G7 — Không toạ độ/API key trong log release

```
adb logcat -d | grep -c "FTD_EVENT"                                    → 0
PID=$(adb shell pidof com.example.pion.family.tracker.demo)
adb logcat -d --pid="$PID" | grep -iE "10\.[0-9]{4}|106\.[0-9]{4}"      → rỗng
adb logcat -d --pid="$PID" | grep -c "AIza"                             → 0
git log -p --all -- local.properties                                    → rỗng (chưa từng commit)
```
Đo trên `emulator-5554`, APK release, sau khi cấp quyền + bật theo dõi + mở Zone/History/Timeline +
chạy 2 lần mô phỏng.

## G8 — `assembleRelease` ký, cài được, bản đồ đúng

```
./gradlew clean assembleRelease --no-configuration-cache   → BUILD SUCCESSFUL in 15s
app-release.apk                                             → 28,886,388 bytes
apksigner verify --print-certs                              → CN=Android Debug, SHA-1 7dcf641344ddd235bc0b449f028c87c04dda8b43
adb install -r app-release.apk                               → Success (emulator-5554 VÀ RF8Y60B9NCZ)
apkanalyzer manifest print → android:allowBackup="false"     → đúng
```
Bản đồ hiện đúng phố Sài Gòn thật sau khi cài — ảnh `scratchpad/g8-map-screen.png`. Không xám, không
watermark → SHA-1 debug keystore nằm trong hạn chế Maps API key (đúng thiết kế PRD v1.2 §7.2).
