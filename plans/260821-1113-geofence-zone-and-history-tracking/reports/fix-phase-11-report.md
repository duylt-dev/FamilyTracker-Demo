# Fix report — phase-11 regression: `FtdLog` requires Koin, kills instrumented test process

Status: DONE — 14/14 xanh trở lại, G7 sạch, tài liệu đã cập nhật.

Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`.
Devices: `emulator-5554` (dùng cho vòng lặp test chính), `RF8Y60B9NCZ` (không đụng).

## Bước 1 — tái hiện

Tái hiện đúng y hệt báo cáo: `Starting 14 tests`, chỉ `1/14 completed`, sau đó
`Instrumentation run failed due to Process crashed`. Output đầy đủ (rút gọn phần task Gradle
UP-TO-DATE):

```
com.example.pion.family.tracker.demo.data.geofence.GeofenceRegistrarTest > registerAll_withInitialTriggerZero_stillSucceeds[Pixel_10_Pro_XL(AVD) - 17] FAILED
	java.lang.IllegalStateException: KoinApplication has not been started
	at org.koin.core.context.GlobalContext.get(GlobalContext.kt:36)
Pixel_10_Pro_XL(AVD) - 17 Tests 1/14 completed. (0 skipped) (1 failed)
Failed to retrieve logcat for some test cases. We retrieved logcat for 0 test cases out of 1 tests.

> Task :data:connectedDebugAndroidTest FAILED
Test run failed to complete. Instrumentation run failed due to Process crashed.
Logcat of last crash:
Process: com.example.pion.family.tracker.demo.data.test, PID: 20295
java.lang.IllegalStateException: KoinApplication has not been started
	at org.koin.core.context.GlobalContext.get(GlobalContext.kt:36)
	at org.koin.core.component.KoinComponent.getKoin(KoinComponent.kt:33)
	at com.example.pion.family.tracker.demo.data.util.FtdLog.getKoin(FtdLog.kt:25)
	at com.example.pion.family.tracker.demo.data.util.FtdLog$special$$inlined$inject$default$1.invoke(KoinComponent.kt:68)
	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
	at com.example.pion.family.tracker.demo.data.util.FtdLog.getDebugBuild(FtdLog.kt:27)
	at com.example.pion.family.tracker.demo.data.util.FtdLog.d(FtdLog.kt:30)
	at com.example.pion.family.tracker.demo.data.geofence.GeofenceRegistrar$registerAll$2$1.onComplete(GeofenceRegistrar.kt:106)
	at com.google.android.gms.tasks.zzi.run(...)
	at android.os.Handler.handleCallback(Handler.java:1082)
	...
	at android.app.ActivityThread.main(ActivityThread.java:9569)
```

**Xác nhận chẩn đoán của orchestrator đúng 100%:** exception ném từ `FtdLog.getDebugBuild` (lazy
`by inject(...)` — `KoinComponent.getKoin()` → `GlobalContext.get()`) trong `onComplete` callback
của Play Services (`GeofenceRegistrar.registerAll`), chạy trên **main thread** của process test
(`android.os.Handler` → `ActivityThread.main`), KHÔNG phải thread của JUnit test runner. Exception
không bắt được không dừng riêng test case — nó **crash cả process instrumentation**
(`com.example.pion.family.tracker.demo.data.test`, PID 20295), giết luôn 13 test còn lại
chưa kịp chạy. Đúng nguyên nhân → đúng hệ quả quan sát được ("chỉ 1/14 chạy được rồi hỏng").

## Bước 2 — sửa

**Hướng chọn: đúng hướng orchestrator đề xuất — bỏ `KoinComponent`, dùng `@Volatile var
debugBuild: Boolean = false`, `FamilyTrackerApp.onCreate` gán trực tiếp (không qua Koin), TRƯỚC
`startKoin`.** Áp cho CẢ HAI `FtdLog` (`:data/util/FtdLog.kt`, `:ui/core/logging/FtdLog.kt` — hai
object gần giống hệt nhau, không share được vì `:ui` không phụ thuộc `:data`, LLM.md §2).

File đổi:

| File | Thay đổi |
|---|---|
| `data/src/main/.../data/util/FtdLog.kt` | Bỏ `KoinComponent`, `by inject(named("debugBuild"))`. Thêm `@Volatile var debugBuild: Boolean = false`. `d`/`w`/`e` không đổi chữ ký. |
| `ui/src/main/.../ui/core/logging/FtdLog.kt` | Y hệt, chỉ có `d`. |
| `app/src/main/.../FamilyTrackerApp.kt` | Import `ui.core.logging.FtdLog as UiFtdLog` (tránh trùng tên 2 object khác package). Đầu `onCreate()`, TRƯỚC `startKoin`: `FtdLog.debugBuild = BuildConfig.DEBUG; UiFtdLog.debugBuild = BuildConfig.DEBUG`. `appConfigModule` xoá `single<Boolean>(named("debugBuild"))` (không còn ai đọc — grep xác nhận `debugBuild` chỉ còn xuất hiện trong 3 file này). `simulatorEnabled` binding giữ nguyên (vẫn cần Koin — `SimulateRouteButton` là composable, đọc qua `koinInject`). |

**Vì sao đúng hướng orchestrator đề xuất, không chọn hướng khác:** đã cân nhắc giữ Koin binding
nhưng đổi `by inject()` thành `get()` gọi lười trong mỗi hàm log (tránh crash lúc class-init) —
loại bỏ vì vẫn crash y hệt lần gọi đầu tiên nếu Koin chưa start, chỉ dời thời điểm ném lỗi, không
sửa gốc. `@Volatile var` gán trực tiếp là cách duy nhất loại bỏ hẳn phụ thuộc DI khỏi một cổng log
lẽ ra phải gọi được từ bất kỳ đâu, bất kỳ lúc nào — đúng bài học Fixed #2.

## Bước 3 — nghiệm thu

```
$ ./gradlew :data:connectedDebugAndroidTest
Starting 14 tests on Pixel_10_Pro_XL(AVD) - 17
Finished 14 tests on Pixel_10_Pro_XL(AVD) - 17
BUILD SUCCESSFUL in 11s
```
`TEST-....xml`: `tests="14" failures="0" errors="0" skipped="0"` — **về lại 14/14**.

```
$ ./gradlew test
BUILD SUCCESSFUL in 2s
```
Tổng `tests=` cộng dồn tất cả file `TEST-*.xml` (trừ androidTest) = **131**, tổng `failures+errors`
= **0**.

```
$ ./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
1
```
Đúng khớp baseline (ENV-BRIEFING §8) — warning duy nhất vẫn là
`android.disallowKotlinSourceSets=false` (kiểm nội dung, không chỉ đếm số dòng).

```
$ ./gradlew assembleRelease
BUILD SUCCESSFUL in 12s
$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Success
```

**Chạy thật trên `emulator-5554`, bản release, `pm clear` trước, quyền vị trí + thông báo cấp qua
`pm grant` (bỏ qua onboarding để đi thẳng vào luồng cần test):**

1. Mở app (`am start`) → tab Bản đồ mặc định, `pidof` = 21047, không crash.
2. Sang tab Lịch sử → dump UI hierarchy lấy đúng toạ độ nút → bấm **"▶ Mô phỏng lộ trình"**.
3. Theo dõi `pidof` mỗi 2s trong 40s liên tục — **PID giữ nguyên 21047 suốt cả cửa sổ mô phỏng**,
   đúng đoạn code từng crash test (`GeofenceRegistrar.register`/`registerAll`'s `onComplete`
   callback gọi `FtdLog.d/w`).
4. Screenshot sau mô phỏng: tuyến 599m hiện trên bản đồ Lịch sử, thống kê đầy đủ.
5. Tab Nhật ký: đúng 2 dòng **"Đã đến Zone mẫu"** (xanh) + **"Đã rời Zone mẫu"** (đỏ), nhóm "Hôm nay".
6. Tab Zone: "Zone mẫu · Ở ngoài · 150 m" — dữ liệu nhất quán xuyên cả 3 màn.
7. `adb shell cmd notification list` xác nhận 2 thông báo đang đứng trong khay.

**G7 — bằng chứng:**
```
$ PID=$(adb -s emulator-5554 shell pidof com.example.pion.family.tracker.demo | tr -d '\r')
$ adb -s emulator-5554 logcat -d | awk -v p="$PID" '$3==p' > g7-full-log.txt
$ wc -l g7-full-log.txt
5306
$ grep -E '10\.[0-9]{4}|106\.[0-9]{4}|latitude|longitude|AIza' g7-full-log.txt
(rỗng, exit=1)
$ grep -c FTD_EVENT g7-full-log.txt
0
$ grep -i "FATAL\|IllegalStateException\|koin" g7-full-log.txt
(rỗng, exit=1)
```
G7 sạch (không toạ độ, không API key rò rỉ trong logcat theo PID app), và `FTD_EVENT` = 0 dòng xác
nhận fix KHÔNG vô tình bật log lại ở release (đúng ý đồ: `debugBuild=false` mặc định, và
`BuildConfig.DEBUG=false` ở bản release nên gán lại vẫn `false`). Không FATAL, không
IllegalStateException, không nhắc tới Koin trong toàn bộ log phiên — app sống hết buổi test.

**Dọn dẹp cuối:** `adb -s emulator-5554 shell pm clear com.example.pion.family.tracker.demo` —
DB gọn, không để lại zone/event test. Máy ảo hiện đứng ở bản **release** vừa cài (chưa mở lại sau
`pm clear`, đúng trạng thái "cài xong, dữ liệu sạch").

## Bước 4 — tài liệu

- `LLM.md` §13: thêm **Fixed #22** — nêu rõ đây là hồi quy do phase-11 gây ra và là bài học Fixed
  #2/fix-phase-01 lặp lại nguyên văn (cổng log không được đòi hạ tầng sẵn sàng), kèm bằng chứng
  tái hiện, cách sửa, và nghiệm thu đầy đủ.
- `LLM.md` §6: sửa đoạn mô tả `appConfigModule`/`FtdLog` — bỏ nhắc `single<Boolean>(named("debugBuild"))`
  (đã xoá khỏi code), thêm đoạn giải thích `FtdLog` giờ không qua Koin nữa và vì sao.
- `docs/android-mvi-best-practices.md` §1: thêm đoạn ngay sau đoạn `AppLogger`/fix-phase-01 cũ,
  tổng quát hoá thành luật chung: một cổng log dạng `object` singleton toàn tiến trình (không qua
  constructor injection, có thể bị gọi từ `BroadcastReceiver`, callback SDK bên thứ ba, test không
  start DI) phải mặc định AN TOÀN bằng giá trị gán trực tiếp, không phải lazy DI lookup — vì
  đường gọi của nó rộng hơn hẳn một ViewModel (Koin luôn resolve được ViewModel trước khi ai gọi
  nó, nhưng không gác được `FtdLog`).

## Chỗ tôi thấy chẩn đoán CỦA ORCHESTRATOR đúng, không có chỗ nào sai

- Tái hiện đúng 100% (1/14, `Process crashed`, đúng stack trace `FtdLog.getKoin` →
  `GeofenceRegistrar$registerAll$2$1.onComplete`).
- Hướng sửa đề xuất (`@Volatile var`, gán trực tiếp trong `onCreate`, TRƯỚC `startKoin`) áp dụng
  được y nguyên, không cần điều chỉnh.
- Đánh giá rủi ro `BootCompletedReceiver`: đúng là ĐÃ an toàn ở code CŨ nhờ đảm bảo của Android
  framework (`Application.onCreate()` luôn chạy trước `onReceive()` cùng process, kể cả cold-start
  do hệ thống phát boot broadcast) — không phải một lỗ hổng đã từng nổ thật, nhưng đúng như đề bài
  gợi ý, đó là một PHỤ THUỘC vào đúng thứ tự framework mà bản thân code không tự đảm bảo, khác hẳn
  code MỚI (mặc định `false`, không cần đúng thứ tự gì để an toàn). Không có provider riêng nào
  của app gọi `FtdLog` (chỉ thư viện androidx tự merge, không đụng code app) nên không có đường nào
  khác sớm hơn `Application.onCreate()`.

Không có câu hỏi còn treo.

## Kết quả

**14/14** instrumented test xanh trở lại. **G7 sạch** (rỗng toạ độ/API key, 0 dòng FTD_EVENT ở
bản release). App không crash khi chạy đúng đường code từng làm sập test (`GeofenceRegistrar`
callback gọi `FtdLog` sau mô phỏng lộ trình đầy đủ).
