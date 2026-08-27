# Nghiên cứu kỹ thuật: Geofencing và Background Location trên Android 28–36

**Ngày:** 2026-08-21  
**Dự án:** FamilyTrackerDemo  
**Phạm vi:** Geofencing API, quyền background location, foreground service, testing, OEM constraints  
**Đối tượng:** Triển khai tính năng zone tracking + geofence notification

---

## 1. GeofencingClient API — Tính năng và ràng buộc

### 1.1 API hiện tại và cách sử dụng

**Phiên bản:** Play Services Location 21.4.0 (mới nhất)

Truy cập qua `LocationServices.getGeofencingClient(context)`:

```kotlin
val geofencingClient = LocationServices.getGeofencingClient(context)

val geofence = Geofence.Builder()
    .setRequestId("zone_id")
    .setCircularRegion(lat, lng, radiusMeters)
    .setExpirationDuration(Geofence.NEVER_EXPIRE)
    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
    .setLoiteringDelay(0) // 0ms = không chờ (DWELL disabled)
    .build()

val geofencingRequest = GeofencingRequest.Builder()
    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
    .addGeofence(geofence)
    .build()

val pendingIntent = createGeofencePendingIntent() // FLAG_MUTABLE trên API 31+

geofencingClient.addGeofences(geofencingRequest, pendingIntent)
    .addOnSuccessListener { /* success */ }
    .addOnFailureListener { exception -> /* handle error */ }
```

### 1.2 Giới hạn số geofence

| Loại thiết bị | Giới hạn | Ghi chú |
|---|---|---|
| Single-user device | 100 geofence/app | **Không được vượt** — Play Services ném lỗi nếu thêm quá 100 |
| Multi-user device | 100 geofence/app/user | Mỗi user profile riêng biệt |

**Chặn ở tầng use case, không chờ API ném:** Trước khi gọi `addGeofences()`, kiểm tra số lượng hiện tại:

```kotlin
suspend fun saveZone(zone: Zone): AppResult<Zone> {
    val count = zoneRepository.count()
    if (count >= 100) {
        return AppResult.Failure(AppError.ValidationError("Đã đạt tối đa 100 zone"))
    }
    // tiếp tục
}
```

### 1.3 Độ trễ thực tế (latency)

| Tình cảnh | Độ trễ | Nhận xét |
|---|---|---|
| Device di chuyển (GPS cập nhật) | < 2 phút | **Lý tưởng cho demo** — thường nhanh hơn |
| Device tĩnh lâu | Tới 6 phút | Geofencing API không query liên tục, chỉ khi device di chuyển |
| Bộ lọc `setNotificationResponsiveness()` | Cộng thêm N phút | Giảm pin bằng cách tăng độ trễ |

**Kết luận:** Geofencing API **không phù hợp cho phản hồi tức thì.** Cần vòng kiểm tra trong foreground service khi app mở.

### 1.4 setNotificationResponsiveness() ảnh hưởng

```kotlin
geofence.setNotificationResponsiveness(300_000) // 5 phút
```

**Hiệu ứng:** Hệ thống chỉ kiểm tra vào/ra zone mỗi 5 phút thay vì liên tục → **tiết kiệm pin lên tới 60%** nhưng **tăng độ trễ 5 phút**.

**Khuyến nghị cho demo:**
- Dùng giá trị mặc định (không gọi `setNotificationResponsiveness()`) để phản hồi nhanh nhất
- Hay để default ≈ 0–2 phút trên thiết bị di chuyển
- Không nên set < 1 phút vì hệ thống sẽ bỏ qua (không giảm độ trễ dưới ~30 giây)

### 1.5 INITIAL_TRIGGER flags

```kotlin
// Tất cả ba flags có thể kết hợp
GeofencingRequest.INITIAL_TRIGGER_ENTER   // Bắn sự kiện nếu device đã trong zone
GeofencingRequest.INITIAL_TRIGGER_EXIT    // Bắn sự kiện nếu device đã ngoài zone
GeofencingRequest.INITIAL_TRIGGER_DWELL   // Chỉ bắn khi device ở trong zone lâu
```

**Quan trọng:** Không kết hợp ENTER và DWELL mà không UNSET chúng. Nếu device trong zone và thêm geofence với cả ENTER+DWELL:
- ENTER bắn ngay tức khắc
- DWELL bắn sau khi device ở tĩnh trong zone đủ lâu (`loiteringDelay`)

**Khuyến nghị:**  Dùng `INITIAL_TRIGGER_ENTER | INITIAL_TRIGGER_EXIT` cho trường hợp bình thường (vào/ra zone).

---

## 2. Chính sách quyền Android 28–36

### 2.1 Bảng chính sách quyền theo API level

| Quyền | API 28–30 | API 31–32 (S) | API 33+ (T+) | Ghi chú |
|---|---|---|---|---|
| `ACCESS_FINE_LOCATION` | Dialog thường (1 bước) | Dialog thường | Dialog thường | `When using app` (app-only) |
| `ACCESS_BACKGROUND_LOCATION` | Dialog thường | **Không có dialog** | **Không có dialog** | Phải xin sau FINE_LOCATION; user phải tự vào Settings |
| `POST_NOTIFICATIONS` (thông báo) | N/A | N/A | Dialog thường (API 33+) | Bắt buộc cho thông báo vào/rời zone |

### 2.2 Quy tắc incremental request (bắt buộc API 30+)

**Luật vàng:** Không được xin `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` cùng một lần `requestPermissions()`.

**Hệ quả nếu vi phạm:**
- Hệ thống **từ chối im lặng** phần background (không ném lỗi, không hiện dialog)
- `ActivityCompat.checkSelfPermission()` trả về `PERMISSION_DENIED` cho background location
- Geofence **không bắn khi app đóng** → triệu chứng giống lỗi code

**Luồng đúng** (3 bước riêng biệt):

```kotlin
// Bước 1: POST_NOTIFICATIONS (Android 13+)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    requestPermissions(arrayOf(POST_NOTIFICATIONS), PERMISSION_NOTIFICATION)
}
// UI hiển thị kết quả

// Bước 2: ACCESS_FINE_LOCATION
requestPermissions(arrayOf(ACCESS_FINE_LOCATION), PERMISSION_FINE_LOCATION)
// UI kiểm tra: nếu từ chối, dừng. Nếu cấp, tiếp tục

// Bước 3: ACCESS_BACKGROUND_LOCATION (chỉ sau bước 2 đã cấp)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    // API 30+: cần ACTION_APPLICATION_DETAILS_SETTINGS
    openApplicationDetailsSettings()
} else {
    // API 28–29: dialog thường
    requestPermissions(arrayOf(ACCESS_BACKGROUND_LOCATION), PERMISSION_BACKGROUND_LOCATION)
}
```

### 2.3 Hành vi "Only this time" (chỉ lần này)

**Kỳ hạn:** API 30+ (Android 11+)

Khi user chọn "Allow only this time":
- Quyền được cấp **cho session hiện tại** (app đang mở)
- Khi app đóng, quyền bị **tự động revoke**
- Lần mở lại, phải xin lại → **User sẽ được hỏi nhiều lần** nếu không cấp toàn thời

**Tác động:** Geofence **không bắn sau khi app đóng** (vì không có background location permission dài hạn).

### 2.4 Dấu hiệu "từ chối im lặng"

App có thể có `ACCESS_FINE_LOCATION` nhưng **không có** `ACCESS_BACKGROUND_LOCATION`. Cách kiểm tra:

```kotlin
fun hasBackgroundLocationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // API 30+
        ContextCompat.checkSelfPermission(
            context,
            ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        // API 28–29: coi như cấp nếu FINE_LOCATION được cấp
        ContextCompat.checkSelfPermission(
            context,
            ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
```

**Manifest khai báo (bắt buộc):**

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 3. Foreground Service — Khai báo và ràng buộc

### 3.1 Khai báo trong manifest

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<application>
    <service
        android:name=".data.location.LocationTrackingService"
        android:foregroundServiceType="location"
        android:exported="false" />
</application>
```

**Lưu ý:** API 34+ (Android 14+) **bắt buộc** khai báo `foregroundServiceType`. Thiếu → ứng dụng sẽ **không compile**.

### 3.2 Khai báo trong build.gradle.kts (AGP 9.2.1+)

```kotlin
android {
    targetSdk = 36
    compileSdk = 36
}
```

**Không cần `android.useAndroidX`** — AGP 9.2.1+ tự động hỗ trợ AndroidX.

### 3.3 Ràng buộc theo API level

| API | Yêu cầu | Hành vi |
|---|---|---|
| 28–33 | `foregroundServiceType` tuỳ chọn | Nếu khai báo, hệ thống kiểm tra permission + notification |
| 34–35 | `foregroundServiceType` **bắt buộc** | **Crash lúc chạy**, không phải lỗi biên dịch: `startForeground()` ném `MissingForegroundServiceTypeException`. Build vẫn xanh — chỉ nổ khi bật công tắc theo dõi. |
| 36+ | Giống 34–35 | Không có thay đổi mới |

### 3.4 Android 15 (API 35+) — Time limit cho dataSync/mediaProcessing

**Lưu ý:** Giới hạn 6 giờ/24 giờ **chỉ áp dụng cho `dataSync` và `mediaProcessing`**, **KHÔNG áp dụng cho `location`**.

```kotlin
// Foreground service loại "location" không có time limit
service.startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
```

**Location FGS có thể chạy vô thời hạn miễn là:**
1. Có notification thường trực
2. User không force-stop app
3. Device không bật Doze Mode quá chặt

### 3.5 Thông báo thường trực (persistent notification)

```kotlin
val notification = NotificationCompat.Builder(this, "location_channel")
    .setContentTitle("FamilyTracker")
    .setContentText("Đang theo dõi vị trí...")
    .setSmallIcon(R.drawable.ic_location)
    .setCategory(NotificationCompat.CATEGORY_SERVICE)
    .setOngoing(true)
    .build()

startForeground(NOTIFICATION_ID, notification)
```

**Yêu cầu:** Thông báo **phải nói rõ app đang theo dõi vị trí** (PRD §7.3). Không có notification thường trực → hệ thống sẽ ANR service sau vài giây.

### 3.6 Điều kiện được phép start FGS từ background

Chỉ được start foreground service từ background nếu:
1. User vừa mở app (foreground)
2. App có `SCHEDULE_EXACT_ALARM` hoặc `ALARM_MANAGER` alarm bắn
3. App nhận broadcast system (ví dụ: `BOOT_COMPLETED`)
4. App đang được trigger từ notification push

**Quan trọng:** Khi user bật/tắt công tắc "Theo dõi" từ UI → app đang ở foreground → được phép start/stop service.

---

## 4. Đăng ký lại geofence sau reboot và kill app

### 4.1 Vòng đời geofence

| Sự kiện | Hành vi |
|---|---|
| App gọi `addGeofences()` | Geofence **sống cho đến khi bị xoá** (app gọi `removeGeofences()`) |
| App bị force-stop / kill | Geofence **tự động xoá** |
| App bị uninstall | Geofence **tự động xoá** |
| Device reboot | Geofence **tự động xoá** → **PHẢI đăng ký lại** |
| App upgrade | Geofence **tự động xoá** → **PHẢI đăng ký lại** |

### 4.2 Đăng ký lại sau BOOT_COMPLETED

**Receiver trong manifest:**

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver
    android:name=".data.geofence.BootCompletedReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

**Implementation:**

```kotlin
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Không được gọi từ main thread
            val workRequest = OneTimeWorkRequestBuilder<GeofenceReregistrationWorker>()
                .addTag("geofence_reregister")
                .build()
            WorkManager.getInstance(context!!).enqueueUniqueWork(
                "geofence_reregister",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
```

**Worker (thực hiện trong background):**

```kotlin
class GeofenceReregistrationWorker(
    context: Context,
    params: WorkerParameters,
    private val zoneRepository: ZoneRepository,
    private val geofenceRegistrar: GeofenceRegistrar,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val zones = zoneRepository.observeAll().firstOrNull() ?: emptyList()
            zones.forEach { zone ->
                geofenceRegistrar.register(zone)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

### 4.3 OEM Trung Quốc — Rủi ro lớn

| OEM | Vấn đề | Giải pháp |
|---|---|---|
| **Xiaomi** | BOOT_COMPLETED bị chặn mặc định nếu app không được cho phép "Autostart" | Cần user chủ động vào Settings > Permissions > Autostart |
| **Oppo / Vivo** | Chạy cleanup sweep định kỳ, kill background service bất chấp FGS | Không có giải pháp perfect — FGS sẽ bị kill mà không thông báo |
| **Huawei** | Tương tự Xiaomi | Cần "Autostart" permission |

**Hiệu ứng demo:** Nếu device demo là Xiaomi/Oppo/Vivo không được config "Autostart":
- Sau khi reboot → geofence không hoạt động nữa
- User phải vào Settings cấu hình → demo bị gián đoạn
- FGS có thể bị kill sau 1–2 phút chạy → geofence từ foreground service cũng mất

**Khuyến nghị:** Demo nên **kiểm tra thiết bị sau reboot** — nếu Xiaomi/Oppo, cấu hình "Autostart" trước demo.

---

## 5. PendingIntent flags cho geofence (API 31+)

### 5.1 Bảng flags theo API level

| API | Quy tắc | Giải pháp |
|---|---|---|
| 28–30 | Không bắt buộc flag | Có thể không pass flag hoặc dùng `FLAG_UPDATE_CURRENT` |
| 31–32 (S) | **Bắt buộc** `FLAG_MUTABLE` hoặc `FLAG_IMMUTABLE` | GeofencingClient yêu cầu `FLAG_MUTABLE` |
| 33+ (T+) | **Bắt buộc** một trong hai flag | Giống API 31 |

### 5.2 GeofencingClient yêu cầu FLAG_MUTABLE

```kotlin
fun createGeofencePendingIntent(): PendingIntent {
    val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
    return PendingIntent.getBroadcast(context, 0, intent, flags)
}
```

**Lý do `FLAG_MUTABLE`:** GeofencingClient cần sửa intent (thêm geofence transition data) trước khi gửi broadcast.

**Lỗi phổ biến:** Dùng `FLAG_IMMUTABLE` cho GeofencingClient → **Crash:**
```
E: Cannot create a mutable PendingIntent with a geofence request
```

### 5.3 Notification PendingIntent (khác)

Notification PendingIntent (intent mở app từ notification) phải dùng `FLAG_IMMUTABLE`:

```kotlin
val notificationIntent = Intent(context, MainActivity::class.java)
val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
val intent = PendingIntent.getActivity(context, 0, notificationIntent, flags)
```

**Tóm tắt:**
- **Geofence PendingIntent** → `FLAG_MUTABLE`
- **Notification PendingIntent** → `FLAG_IMMUTABLE`
- **Lên API 31+** → luôn khai báo flag

---

## 6. Cách test geofencing

### 6.1 Test trên emulator

**Phương pháp 1: Extended Controls UI**

```
1. Mở emulator
2. Toolbar → ⋮ (more options)
3. Extended Controls → Location
4. Nhập latitude, longitude
5. Click Send
```

**Phương pháp 2: ADB emu geo fix**

```bash
# Single location
adb emu geo fix <longitude> <latitude>
# Ví dụ: Hà Nội (20.8°N, 105.8°E)
adb emu geo fix 105.8 20.8

# Từ file GPX (mô phỏng chuỗi vị trí)
adb emu geo playback ./route.gpx
```

**Phương pháp 3: Programmatic mock location (instrumented test)**

```kotlin
@Test
fun testGeofenceEnter() {
    val location = Location("gps").apply {
        latitude = 20.8
        longitude = 105.8
        accuracy = 10f
    }
    // Inject vào FusedLocationProvider
    mockLocationProvider.pushLocation(location)
    
    // Kiểm tra event được sinh
    Thread.sleep(2000)
    assert(zoneEventRepository.lastEvent?.type == ZoneEventType.ENTER)
}
```

### 6.2 Test trên máy thật

**Công cụ 1: Fake GPS Location (app)** — Cài từ Play Store, cho phép mock vị trí mà không cần developer mode.

**Công cụ 2: ADB mock location**

```bash
# Bật mock location từ Developer Options trước
adb shell settings put secure mock_location 1

# Sau đó dùng adb emu geo fix
adb emu geo fix 105.8 20.8
```

**Công cụ 3: Route simulator (ngắn vòng lặp)**

Nút "Mô phỏng lộ trình" trong app (PRD F5) sinh chuỗi vị trí qua các zone trong ~30 giây, rút ngắn vòng test từ vài phút xuống vài giây.

### 6.3 Rút ngắn vòng lặp test

**Không làm việc này:**
- Chịu đợi 30–180 giây mỗi lần test geofence = rất chậm

**Làm thay vào đó:**
1. Dùng `SimulatedLocationSource` trong `:data/location/`
2. Nút demo lộ trình sinh ra sự kiện thật **qua cùng cửa** với nguồn thật → test được geofence logic
3. Test `ZoneEvaluator` độc lập bằng unit test (không cần emulator)

```kotlin
// Test ZoneEvaluator trong :domain/src/test/
@Test
fun testZoneEnterWithHysteresis() {
    val zone = Zone(id = "1", lat = 20.8, lng = 105.8, radius = 100f)
    val pointInside = LocationPoint(lat = 20.801, lng = 105.801, accuracy = 10f)
    val pointOutside = LocationPoint(lat = 20.803, lng = 105.803, accuracy = 10f)
    
    val eval1 = ZoneEvaluator.evaluate(pointInside, listOf(zone), emptySet())
    assert(eval1.events.first().type == ZoneEventType.ENTER)
    
    val eval2 = ZoneEvaluator.evaluate(pointOutside, listOf(zone), setOf("1"))
    assert(eval2.events.isEmpty()) // Không ra vì chưa vượt buffer 30m
}
```

---

## 7. Cạm bẫy phổ biến

### 7.1 Xin quyền sai (incremental request vi phạm)

**Triệu chứng:** Geofence không bắn khi app đóng, nhưng bắn khi app mở.

**Nguyên nhân:** Gộp `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` trong một lần `requestPermissions()`.

**Sửa:** Xin riêng lẻ, cách nhau ít nhất 1 screen.

### 7.2 Quên FLAG_MUTABLE trên API 31+

**Triệu chứng:** App crash khi thêm geofence:
```
W: Cannot create a mutable PendingIntent
```

**Sửa:** Thêm `FLAG_MUTABLE` cho PendingIntent của geofence.

### 7.3 Bỏ qua BOOT_COMPLETED

**Triệu chứng:** Geofence hoạt động bình thường, nhưng sau reboot hoàn toàn không hoạt động.

**Sửa:** Implement `BootCompletedReceiver`, đăng ký lại từ database.

### 7.4 Radius quá nhỏ (< 100m) trong nhà

**Triệu chứng:** Trong nhà, GPS bán kính lên tới 50–100m, zone không bao giờ trigger đúng.

**Giải pháp:**
- Hiện warning ở UI nếu radius < 100m
- Ghi trong docs: "Dùng ≥ 100m cho indoor location, ≥ 50m cho outdoor"

### 7.5 Không khử trùng lặp (duplicate events)

**Triệu chứng:** Một lần vào zone nhận 2 thông báo giống hệt.

**Nguyên nhân:** Geofence API + foreground service cùng bắn sự kiện, không có deduplication.

**Sửa:** `ZoneEventRepository.record()` bỏ qua event `(zoneId, memberId, type)` nếu cách event gần nhất < 60 giây (PRD §8.1).

### 7.6 Không có thông báo thường trực cho FGS

**Triệu chứng:** LocationTrackingService start nhưng sau 5 giây hệ thống kill, không báo lỗi.

**Sửa:** Gọi `startForeground()` **ngay** trong `onCreate()` hoặc `onStartCommand()`, notification phải rõ ràng.

### 7.7 OEM Trung Quốc kill FGS sau vài phút

**Triệu chứng:** App chạy ổn, nhưng trên Xiaomi/Oppo/Vivo, FGS tự dừng sau 1–2 phút.

**Chủ yếu là lỗi OEM.** Giải pháp:
- Dùng `WorkManager.PeriodicWorkRequest` để restart service
- Hoặc chấp nhận limitation này ở bản demo (ghi vào PRD "Demo tốt nhất trên Pixel/Samsung")

### 7.8 Đứng đúng mép zone → dội thông báo

**Triệu chứng:** Đứng yên ở ranh giới zone → nhận ENTER/EXIT liên tiếp.

**Sửa:** Implement hysteresis — vào khi `d < R`, ra khi `d > R + 30m` (PRD §8.2).

---

## 8. Rủi ro cho buổi demo

| # | Rủi ro | Tác động | Cách giảm thiểu |
|---|---|---|---|
| **R1** | Thiết bị demo không bật GPS hoặc Play Services lỗi | Geofence hoàn toàn không hoạt động | Kiểm tra trước demo: Settings > Location > ON, Play Services version |
| **R2** | User chỉ cấp "Only this time" cho quyền vị trí | Geofence không bắn khi app đóng | Hướng dẫn user cấp "Allow all the time", kiểm tra trước demo |
| **R3** | Quên xin `ACCESS_BACKGROUND_LOCATION` | Thông báo chỉ bắn khi app mở | Kiểm tra `adb shell dumpsys package com.example.pion.family.tracker.demo...` tìm `android.permission.ACCESS_BACKGROUND_LOCATION` |
| **R4** | Geofence bị xoá sau reboot thiết bị | Demo lần 2 (nếu demo 2 ngày) không hoạt động | Pre-populate zone từ `DemoDataSeeder`, hoặc tái-register ngay trong app startup |
| **R5** | Thiết bị là Xiaomi/Oppo/Vivo không cấu hình Autostart | BOOT_COMPLETED bị block, FGS tự kill sau vài phút | Kiểm tra model trước, hướng dẫn cấu hình "Autostart" trong Permissions, hoặc demo trên Pixel |
| **R6** | Delay geofence 3–5 phút không acceptable | BA nghĩ app lỗi, không phải là Play Services latency | Giải thích: demo nút "Mô phỏng lộ trình" để thấy response tức thì khi app mở, phần background chỉ demo nút ở phía người khác (simulator) |
| **R7** | Notification bị tắt quyền | Thông báo zone không hiện, sự kiện vẫn ghi (theo PRD §3.2) | Kiểm tra quyền trước demo, highlight banner "Thông báo tắt" nếu không có quyền |

---

## 9. Câu hỏi chưa giải đáp

1. **Precision of GPS indoor location:** PRD nói "Dưới 100m sẽ không ổn định trong nhà" — Liệu có thể cải thiện độ chính xác bằng cách integrate Google Play Services **Fused Location Provider** với ngữ cảnh WiFi? Hay chấp nhận giới hạn GPS?

2. **Simulator location source — làm sao quanh vị trí hiện tại mà đảm bảo nó cắt qua zone?** Nếu user chưa tạo zone, nên auto-create zone mẫu không? PRD ghi "nếu chưa có zone nào thì tạo trước một zone mẫu" — Cần tìm hiểu thuật toán này.

3. **Play Services version pinning:** Hiện tại `play-services-location` 21.4.0 là mới nhất. Có khả năng version 22+ ra khi nào? Có breaking change hay không? Cần pin version hay để floating?

4. **Migration DB sau reboot:** PRD dùng `fallbackToDestructiveMigration()`. Nếu schema thay đổi sau khi user setup geofence, geofence có bị xoá không? Hay chỉ điểm location/event bị xoá?

5. **Notification click → điều hướng:** PRD ghi "Bấm thông báo vào mở app tới Timeline". Nếu app đang chạy background (FGS), click notification có trigger recompose Timeline không? Hay phải activity trigger?

---

## Tóm tắt nhanh — Checklist triển khai

- [ ] Xin quyền riêng lẻ theo thứ tự: POST_NOTIFICATIONS → ACCESS_FINE → ACCESS_BACKGROUND
- [ ] PendingIntent: `FLAG_MUTABLE` cho geofence, `FLAG_IMMUTABLE` cho notification (API 31+)
- [ ] Implement `BootCompletedReceiver` + `GeofenceReregistrationWorker`
- [ ] FGS notification luôn gọi `startForeground()` ngay, notification phải nói rõ "Đang theo dõi vị trí"
- [ ] Khử trùng lặp: 60 giây giữa các event cùng `(zoneId, memberId, type)`
- [ ] Hysteresis: vào `d < R`, ra `d > R + 30m`
- [ ] Test nút "Mô phỏng lộ trình" trên thiết bị thật, kiểm tra thông báo vào + ra
- [ ] Kiểm tra geofence không hoạt động nếu user từ chối background location
- [ ] Chuẩn bị hướng dẫn: "Nếu demo không hoạt động, vào Settings > Apps > Permissions > Autostart (Xiaomi/Oppo)"

---

**Nguồn tham khảo:**
- [Create and monitor geofences | Android Developers](https://developer.android.com/develop/sensors-and-location/location/geofencing)
- [Request background location | Android Developers](https://developer.android.com/develop/sensors-and-location/location/permissions/background)
- [Foreground service types | Android Developers](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Changes to foreground service types for Android 15 | Android Developers](https://developer.android.com/about/versions/15/changes/foreground-service-types)
- [GeofencingClient | Google Play services](https://developers.google.com/android/reference/com/google/android/gms/location/GeofencingClient.html)
- [Optimize location use for real-world scenarios | Android Developers](https://developer.android.com/develop/sensors-and-location/location/battery/scenarios)
- [Android Geofencing: How to Set Up and Troubleshoot | Bugfender](https://bugfender.com/blog/android-geofencing/)
