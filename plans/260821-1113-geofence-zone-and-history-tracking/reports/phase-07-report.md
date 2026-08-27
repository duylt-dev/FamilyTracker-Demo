# Phase 07 — Geofence + notification + khử trùng lặp (F2, US-22→US-26)

> **Báo cáo này do orchestrator viết.** Ba agent liên tiếp (dev, dev tiếp quản, fix) đều bị
> **API error** cắt ngang — hai lần ở bước viết báo cáo, một lần giữa chừng. Code, test và tài liệu
> (`LLM.md`, `plan.md`, Todo List) **đã hoàn tất** trước khi đứt; chỉ file báo cáo là mất. Nội dung
> dưới đây được dựng lại từ `LLM.md` §8.1/§13 (do chính các agent viết), ảnh chụp còn trong scratchpad,
> và **các phép kiểm tôi tự chạy lại**.

## Trạng thái

| | |
|---|---|
| Gate G6 | **1** warning (`--no-configuration-cache`), khớp baseline |
| Gate G7 | logcat không lộ toạ độ |
| Gate **G5** | **HOÃN — máy thật `RF8Y60B9NCZ` chưa cắm.** Xem mục cuối. |
| Unit test JVM | **95**, xanh |
| Instrumented | **12**, xanh |
| Bản đang cài trên `emulator-5554` | **release** (đúng bản đem demo) |

## Hai bug thật, cả hai chỉ lộ ra khi chạy trên máy

### Bug 1 — Race TOCTOU trong khử trùng lặp (`LLM.md` §13 #14)

Bằng chứng gốc, đọc thẳng từ `zone_events` trên `emulator-5554`:

```
zoneName | type | source        | occurredAt
Home     | EXIT | FOREGROUND    | 1787329991161
Home     | EXIT | GEOFENCE_API  | 1787329991168      <-- cách 7 ms
```

Cùng `zoneId` + `memberId` + `type`, **cả hai vào Room**, **không có dòng `zone_event_deduped` nào**.

**Luật không sai — nơi áp dụng luật mới sai.** `ZoneEventDeduper` là hàm thuần ở `:domain`, đã có
test khoá cửa sổ 60s cả hai chiều biên từ phase-03, và nó vẫn đúng. Sai ở `ZoneEventRepositoryImpl.record()`:
đọc `latestForKey()` → hỏi luật → `insert()` là ba bước **không nguyên tử**. Hai đường phát hiện của
§8.1 (`GeofenceBroadcastReceiver` trên `Dispatchers.IO`, `LocationTrackingService` trên
`Dispatchers.Default`) chạy đồng thời **trong cùng một tiến trình**; cả hai đọc trước khi bên nào kịp
ghi, nên cả hai đều thấy "chưa có sự kiện gần đây".

Hậu quả: đúng thứ §8.1 sinh ra để ngăn — **hai thông báo cho một lần rời nhà**, và Timeline (phase-10)
hiển thị sự kiện lặp.

**Cách sửa:** `Mutex` cấp tiến trình (`dedupeMutex`) bao trọn đoạn đọc-quyết định-ghi. Đủ vì cả hai
đường cùng một tiến trình và không có writer Room thứ hai. **Không** chọn Room `@Transaction` ở DAO,
để không kéo `ZoneEventDeduper` (hàm thuần `:domain`, Phụ lục A.1 — điều kiện đã chốt ở Q-D) xuống `:data`.

Chi tiết đáng ghi nhận trong bản sửa: **thông báo được bắn NGOÀI lock**. Chỉ đoạn đọc-ghi Room nằm
trong `withLock`; `ZoneNotifier.notify()` chạy sau khi đã nhả khoá, nên không giữ mutex trong lúc làm I/O.

**Test hồi quy có răng — tự kiểm bằng mutation:**

`ZoneEventRaceConditionTest` dùng một DAO decorator **chèn delay ngay sau khi query thật trả về** để
nới rộng cửa sổ race thành tất định, rồi gọi `record()` đồng thời trên hai dispatcher.

```
# gỡ Mutex:  dedupeMutex.withLock {  ->  run {
ZoneEventRaceConditionTest > record_concurrentCallsSameKey_onlyOneRowIsKept   FAILED
# khôi phục
Finished 12 tests on Pixel_10_Pro_XL(AVD) - 17     BUILD SUCCESSFUL
```

Đây là bằng chứng test thật sự khoá được hành vi, không phải test xanh sẵn.

### Bug 2 — Tap thông báo im lặng khi app đang chạy nền (`LLM.md` §13 #13)

`getLaunchIntentForPackage()` trả về intent mang cờ "reset task nếu cần" của launcher. Khi
`MainActivity` đã sống sẵn trong task, Android chỉ **đưa task lên trước** — không gọi `onCreate`,
cũng không gọi `onNewIntent` (launchMode mặc định `standard`, thiếu `FLAG_ACTIVITY_SINGLE_TOP`).
Tap thông báo ba lần liên tiếp đều dừng ở màn Map.

Chứng minh lỗi nằm ở đường intent chứ không phải logic đọc extra: `adb shell am start` với **cùng**
extra mở đúng Timeline.

**Cách sửa:** `ZoneNotifier` thêm `FLAG_ACTIVITY_SINGLE_TOP` trước khi bọc `PendingIntent`;
`MainActivity` override `onNewIntent()` và cập nhật lại `pendingRoute`/`pendingRouteNonce`.

## Xác minh trên emulator

| Hạng mục | Kết quả | Bằng chứng |
|---|---|---|
| Thông báo **rời** zone, nội dung đúng tên zone | ✅ "Đã rời Saigon Office", **đúng MỘT** thông báo | `scratchpad/p7-07-single-notification.png` |
| Thông báo thường trực của foreground service | ✅ "Đang theo dõi vị trí" ở mục Silent | cùng ảnh trên |
| Khử trùng lặp sau khi sửa | ✅ va chạm 11 ms giữa hai luồng khi bơm vị trí thật → **1** dòng Room, **1** thông báo | `LLM.md` §13 #14 |
| Tap thông báo → Timeline | ✅ sau khi sửa Bug 2 | `p7-08-timeline-opened.png`, `p7-11`→`p7-13` |
| Bật/tắt theo dõi | ✅ | `p7-02`, `p7-03` |
| Gate G7 (rò rỉ toạ độ) | ✅ rỗng | — |

**Một lưu ý về cách kiểm geofence:** `dumpsys location` có mục `Geofence Manager: service: unregistered`,
nhưng **đó là geofence của nền tảng (`LocationManager`), không phải của Play Services.** Geofence đăng
ký qua `GeofencingClient` do GMS quản lý và **không** hiện ở mục đó. Đừng dùng dòng này để kết luận
geofence chưa đăng ký — bằng chứng đúng là sự kiện `source=GEOFENCE_API` thật sự xuất hiện trong
`zone_events` (đã thấy nhiều dòng).

## Gate G5 — HOÃN, chờ cắm máy thật

`adb devices` chỉ có `emulator-5554`. Máy `RF8Y60B9NCZ` (Samsung SM-A165F, Android 16) chưa cắm.

**Vì sao emulator không thay thế được:** G5 kiểm geofence sống sót khi app ở nền dài hạn. OEM Android
(Samsung, Xiaomi, Oppo) có cơ chế tiết kiệm pin riêng giết service nền — emulator AOSP **không tái
hiện** hành vi đó. Đây đúng là rủi ro câu hỏi mở **Q-E** trong `plan.md` nêu ra, và máy đang có là
Samsung nên khớp giả định.

**Cần chạy khi có máy** (theo đúng thứ tự):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_SERIAL=RF8Y60B9NCZ
./gradlew assembleRelease
adb -s RF8Y60B9NCZ install -r app/build/outputs/apk/release/app-release.apk
```

1. Cấp đủ 3 bước quyền, trong đó **`ACCESS_BACKGROUND_LOCATION` phải chọn "Luôn cho phép"** (trên
   Samsung thường phải vào Settings, không cấp được từ dialog).
2. Tắt tối ưu pin cho app: Settings → Apps → FamilyTrackerDemo → Battery → **Unrestricted**.
   *Ghi lại xem có phải làm bước này không* — nếu geofence chỉ sống khi bỏ tối ưu pin thì đó là
   phát hiện cần vào tài liệu, không phải thứ để lặng lẽ bật rồi tick pass.
3. Tạo một zone quanh vị trí hiện tại (bán kính 150–200 m).
4. Bật theo dõi, **đóng app khỏi recents**, đi ra ngoài zone rồi quay lại (hoặc dùng mock location).
5. Tiêu chí đạt: nhận **đúng một** thông báo vào và **đúng một** thông báo rời, app **không** cần mở.
6. Đọc `zone_events` xác nhận không có dòng lặp trong 60 s.
7. Ghi rõ độ trễ từ lúc qua ranh giới tới lúc có thông báo — Play Services có thể trễ vài phút, đó là
   hành vi bình thường cần nêu trong tài liệu demo chứ không phải lỗi.

## Sai lệch so với file phase

Không có sai lệch về phạm vi. Hai bug ở trên **không nằm trong đặc tả** — chúng được phát hiện nhờ
chạy thật và đã được sửa trong phase, kèm test hồi quy và ghi vào `LLM.md` §13.

## Chỗ còn dở

- **G5** — như trên.
- Báo cáo `dev-phase-07-report.md` và `fix-phase-07-report.md` **không tồn tại** (agent chết trước khi
  viết). File này thay thế cả hai. Ảnh chụp gốc vẫn còn trong scratchpad với tiền tố `p7-*`.
