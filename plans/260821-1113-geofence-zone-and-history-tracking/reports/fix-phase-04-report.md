# Fix Report — Phase 04: `MapViewModel.init` bypasses `launchSafely` via `.launchIn(viewModelScope)`

Ngày: 2026-08-21 · Agent: debugger (fix) · Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`

## Tóm tắt

Finding đúng, đã chứng minh bằng test đỏ trước khi sửa, đã sửa ở tầng `MviViewModel` (helper
dùng chung), grep sạch toàn `:ui`, thêm test kiến trúc tự động chống tái diễn, cập nhật 2 tài
liệu bắt buộc. Không phát hiện chẩn đoán sai nào của bạn — có một điểm cần làm rõ thêm (xem
"Điểm cần làm rõ").

---

## Bước 1 — Bằng chứng trước khi sửa

Viết `ui/src/test/.../ui/feature/map/MapViewModelLaunchSafetyTest.kt`: `ThrowingTrackingRepository`
có `isTracking()` trả `flow { throw IllegalStateException("boom-from-isTracking") }`, dựng
`MapViewModel` với nó, `advanceUntilIdle()`.

**Chạy trên code gốc (chưa sửa) — `./gradlew :ui:testDebugUnitTest --tests "*MapViewModelLaunchSafetyTest*" --no-configuration-cache`:**

```
MapViewModelLaunchSafetyTest > isTracking failure is caught by launchSafely instead of escaping uncaught FAILED
    java.lang.IllegalStateException at MapViewModelLaunchSafetyTest.kt:60

1 test completed, 1 failed
BUILD FAILED in 2s
```

XML chi tiết (`ui/build/test-results/testDebugUnitTest/TEST-...MapViewModelLaunchSafetyTest.xml`):
`IllegalStateException: boom-from-isTracking` ném từ `ThrowingTrackingRepository`, đi qua
`SafeFlow.collectSafely` → `onEach` → `launchIn` → `advanceUntilIdle()` → thẳng ra JUnit, làm
**cả task `testDebugUnitTest` FAILED** — không phải chỉ in ra stderr.

**Kết luận bước 1: đúng như bạn dự đoán, RED — kể cả mạnh hơn dự đoán một chút** (xem "Điểm cần
làm rõ" bên dưới về CƠ CHẾ chính xác). Không có lưới an toàn nào bắt exception này.

---

## Bước 2 — Sửa ở tầng `MviViewModel`

**Helper mới** (`ui/src/main/.../ui/core/mvi/MviViewModel.kt`), đặt cạnh `launchSafely`:

```kotlin
protected fun <T> Flow<T>.collectSafely(
    onError: (AppError) -> Unit = {},
    onEach: suspend (T) -> Unit,
): Job = launchSafely(onError) { collect { onEach(it) } }
```

**Vì sao ở `MviViewModel`, không bọc riêng `MapViewModel`:** đúng như bạn chỉ ra — vấn đề gốc là
"quan sát một `Flow` trong ViewModel" chưa có idiom được chúc phúc, nên `launchIn` trần là lối đi
tự nhiên nhất khi không biết `collectSafely` tồn tại. Đặt helper cạnh `launchSafely` trên cùng
lớp cơ sở làm dạng an toàn thành dạng ngắn nhất viết ra — 5 màn hình còn lại (Zone List, Zone
Editor, History, Timeline, và bất cứ ai quan sát lại `TrackingRepository`) sẽ thấy nó ngay khi
gõ `collect` trong IDE (autocomplete trên `this` bên trong ViewModel).

**`MapViewModel.init` đổi sang:**

```kotlin
init {
    trackingRepository.isTracking().collectSafely { enabled -> setState { copy(isTracking = enabled) } }
}
```

Xoá 2 import không còn dùng (`androidx.lifecycle.viewModelScope`, `kotlinx.coroutines.flow.launchIn`,
`kotlinx.coroutines.flow.onEach`).

**Sau sửa, cùng test bước 1 → GREEN:**
```
./gradlew :ui:testDebugUnitTest --no-configuration-cache
BUILD SUCCESSFUL in 2s
```

**Grep sạch `:ui/src/main`** (chỉ `launchIn`/`GlobalScope`/`runBlocking`/`CoroutineScope(` — không
có `viewModelScope.launch` trần nào khác ngoài base class):
```
$ grep -rn "launchIn\|GlobalScope\|runBlocking\|CoroutineScope(" ui/src/main --include="*.kt"
ui/src/main/.../ui/core/mvi/MviViewModel.kt:62:     * `someFlow.onEach { ... }.launchIn(viewModelScope)` is ...   ← KDoc, không phải code
ui/src/main/.../ui/feature/map/MapViewModel.kt:15:  // collectSafely, không gọi launchIn trần ...          ← comment, không phải code
```
Không còn chỗ nào khác lách luật trong `:ui`.

**Quyết định về `MviViewModel.sendEffect` (dòng 43, `viewModelScope.launch { _effects.send(effect) }`)
— KHÔNG sửa, có lý do:**
- `Channel(BUFFERED)` capacity mặc định 64; `send()` chỉ suspend nếu đầy, và chỉ ném nếu channel
  đã `close()` — không có chỗ nào trong codebase gọi `_effects.close()`, nên trên thực tế lệnh này
  không bao giờ ném.
- Đây là hạ tầng base-class dùng cho MỌI ViewModel, không phải code nghiệp vụ của một feature cụ
  thể — không có `onError` app-cụ-thể nào hợp lý để gắn vào đây; nếu bản thân `sendEffect` fail,
  gọi `onError` tổng quát không giúp được gì hơn để lộ ra qua logcat mặc định.
- Đây KHÔNG phải là "quan sát Flow" (đối tượng của finding này) — nó là gửi MỘT giá trị vào một
  Channel VM tự sở hữu, hình dạng rủi ro khác hẳn với `.launchIn()` quan sát Flow bên ngoài do
  repository trả về.

Đồng ý đây là điểm bạn chủ động không yêu cầu sửa — giữ nguyên.

---

## Bước 3 — Chống tái diễn

**1. `docs/android-mvi-best-practices.md`:**
- §1 "Why `launchSafely`, not `viewModelScope.launch`" — thêm đoạn giải thích `collectSafely`,
  ví dụ ✅/❌ có cả `launchIn`.
- §9 checklist — bullet "Every coroutine goes through `launchSafely`" giờ liệt kê rõ 5 dạng lách
  cần grep: `viewModelScope.launch`, `.launchIn(viewModelScope)`, `GlobalScope.launch`,
  `CoroutineScope(...)`, `runBlocking`.
- §10 Anti-patterns — thêm 3 dòng: `launchIn(viewModelScope)` (dẫn thẳng tới bug thật này),
  `GlobalScope.launch`, `runBlocking`.

**2. `LLM.md`:**
- §4 — chữ ký `MviViewModel` thêm `collectSafely`; "Bốn điều không thương lượng" → "Năm điều",
  thêm điểm 5 giải thích `launchIn` là `viewModelScope.launch` viết khác đi.
- §13 Fixed — thêm dòng #8: finding, vị trí, cách sửa, cách xác minh.
- §11 Bố cục test — thêm dòng cho `CoroutineSafetyArchitectureTest`.

**3. Test kiến trúc tự động — CÓ làm, giữ gọn:**
`ui/src/test/.../ui/core/mvi/CoroutineSafetyArchitectureTest.kt`. Đọc mọi file `.kt` dưới
`ui/src/main` (trừ `MviViewModel.kt`) bằng `File.walkTopDown()`, fail nếu dòng KHÔNG PHẢI comment
chứa 1 trong 4 chuỗi cấm (`viewModelScope.launch`, `launchIn(viewModelScope)`, `GlobalScope`,
`runBlocking`). Không dùng thư viện phân tích tĩnh (detekt custom rule, …) — task rõ ràng nói
"chỉ làm nếu viết được gọn gàng", và ~65 dòng grep-trên-file là đủ cho một dự án demo 6 module.

**Xác minh test này thật sự bắt được lỗi (mutation, không chỉ đọc code):**
```
$ cp MapViewModel.kt MapViewModel.kt.bak
$ sed ... đổi lại thành .onEach{}.launchIn(viewModelScope) + thêm import
$ ./gradlew :ui:testDebugUnitTest --tests "*CoroutineSafetyArchitectureTest*" --no-configuration-cache
CoroutineSafetyArchitectureTest > no file under ui src main bypasses launchSafely or collectSafely FAILED
    java.lang.AssertionError at CoroutineSafetyArchitectureTest.kt:38
BUILD FAILED

$ cp MapViewModel.kt.bak MapViewModel.kt   # khôi phục
$ diff MapViewModel.kt.bak MapViewModel.kt  # rỗng — khớp bản đã sửa
$ ./gradlew :ui:testDebugUnitTest --no-configuration-cache
BUILD SUCCESSFUL
```
Đỏ khi lỗi tái diễn, xanh khi khôi phục — khoá được luật thật.

---

## Bước 4 — Nghiệm thu

```
$ ./gradlew :ui:testDebugUnitTest --no-configuration-cache
BUILD SUCCESSFUL in 2-3s   # gồm cả MapViewModelLaunchSafetyTest + CoroutineSafetyArchitectureTest mới

$ ./gradlew test --no-configuration-cache
BUILD SUCCESSFUL in 1-2s
# Tổng: 50 JVM test xanh (domain 34, ui 13, data 2, app 1) — +2 so với 48 của test-phase-04
# (2 test mới: MapViewModelLaunchSafetyTest, CoroutineSafetyArchitectureTest)

$ ./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
1   # khớp baseline ENV-BRIEFING.md §8

$ ./gradlew assembleRelease
BUILD SUCCESSFUL in 4s

$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Success

$ grep -rn "launchIn\|GlobalScope\|runBlocking" ui/src/main --include="*.kt"
# 2 dòng — cả 2 là comment/KDoc giải thích anti-pattern, không phải code thật (liệt kê ở Bước 2)
```

**Smoke test thật trên `emulator-5554`** (app force-stop → mở lại → tap toggle 2 lần):
```
08-21 16:58:56 FTD_EVENT: purge_completed deletedPoints=0 deletedEvents=0
08-21 16:58:58 FTD_EVENT: tracking_toggled enabled=true
08-21 16:59:00 FTD_EVENT: tracking_toggled enabled=false
# grep FATAL/AndroidRuntime: rỗng
# dumpsys activity services | grep -c LocationTrackingService (sau OFF): 0
```
Screenshot xác nhận switch phản ánh đúng trạng thái cả 2 chiều (OFF → tap → ON có
`isForeground=true foregroundId=1001 types=0x00000008` → tap → OFF). Tính năng không đổi hành vi
so với trước khi sửa — chỉ đường coroutine bên dưới đổi.

---

## Điểm cần làm rõ (không phải "bạn sai", nhưng cơ chế khác một chút so với mô tả)

Bạn dự đoán "test đỏ / crash" và "nếu `viewModelScope` nuốt nó êm thì nói tôi sai". Kết quả THẬT
là RED — đúng — nhưng qua một cơ chế cụ thể hơn đáng nói: exception từ `.launchIn(viewModelScope)`
không đơn thuần "thoát ra ngoài, in ra stderr rồi bị nuốt" như JVM mặc định vẫn làm với coroutine
không có `CoroutineExceptionHandler`. Thay vào đó, vì test dùng
`Dispatchers.setMain(dispatcher)` với CÙNG một `StandardTestDispatcher`/`TestCoroutineScheduler`
mà `runTest(dispatcher)` dùng, `kotlinx-coroutines-test` chủ động bắt lại exception đó và làm
**chính `advanceUntilIdle()`/`runTest` ném lại nó**, khiến task Gradle FAILED thẳng — mạnh hơn
"chỉ mất log, không ai biết" một chút.

Điều này **không làm yếu kết luận của bạn** — trên thiết bị thật (không có `TestScope`), đường đi
duy nhất còn lại của exception là default uncaught-exception handler của Android → crash tiến
trình chính, đúng như rủi ro bạn mô tả cho các Flow Room ở phase-10+. Chỉ là: trong CHÍNH môi
trường JVM test (không phải production), `kotlinx-coroutines-test` tình cờ đã biến bug loại này
thành test đỏ ngay — nghĩa là một khi phase sau có test kiểu "khởi tạo ViewModel qua
`advanceUntilIdle()` với repository giả ném lỗi" (đúng pattern `MapViewModelLaunchSafetyTest`),
CI sẽ tự bắt được nó mà không cần biết trước tên hàm `launchIn`. `CoroutineSafetyArchitectureTest`
(Bước 3) là lớp phòng thủ THỨ HAI, không phụ thuộc việc có viết đúng loại test đó hay không.

## Không có câu hỏi treo.
