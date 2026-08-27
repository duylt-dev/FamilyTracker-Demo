# Fix report — phase-01 finding: `android.util.Log` in `MviViewModel`

Date: 2026-08-21. Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`.

## Bước 1 — chứng minh trước khi sửa

Viết `ui/src/test/java/.../ui/core/mvi/MviViewModelLaunchSafelyTest.kt` (mới, `:ui` chưa có
`src/test` trước đó). Test dựng `FakeViewModel` con của `MviViewModel` (chưa sửa), gọi
`launchSafely` với block ném `IllegalStateException("boom")`, `advanceUntilIdle()`, assert
`state.value.error` là `AppError.Unexpected("boom")`. `:ui/build.gradle.kts` đã sẵn
`testImplementation(libs.junit/kotlinx.coroutines.test/turbine)` từ trước — không cần sửa
catalog.

Chạy `./gradlew :ui:testDebugUnitTest` trên code **chưa sửa**. Output nguyên văn từ
`ui/build/test-results/testDebugUnitTest/TEST-....MviViewModelLaunchSafelyTest.xml`:

```
java.lang.AssertionError
	at org.junit.Assert.fail(Assert.java:87)
	at org.junit.Assert.assertTrue(Assert.java:53)
	at ...MviViewModelLaunchSafelyTest$...$1.invokeSuspend(MviViewModelLaunchSafelyTest.kt:58)
	...
	Suppressed: java.lang.RuntimeException: Method e in android.util.Log not mocked. See https://developer.android.com/r/studio-ui/build/not-mocked for details.
		at android.util.Log.e(Log.java)
		at com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel$launchSafely$2.invokeSuspend(MviViewModel.kt:52)
		at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
		...
```

Xác nhận đúng dự đoán, và còn xấu hơn: `Log.e()` ném ngay bên trong nhánh `catch` của
`launchSafely`, trước khi `onError(...)` được gọi — nghĩa là **on JVM, `launchSafely` không
chỉ log lỗi bằng thông báo vô nghĩa, nó mất luôn khả năng gọi `onError`**, khác hẳn hành vi
thật trên thiết bị. `state.error` ở lại `null`, `assertTrue` fail trước, và exception gốc bị
gắn làm `Suppressed`. `grep -rn "testOptions\|returnDefaultValues"` xác nhận lại: không module
nào bật cờ đó.

## Bước 2 — hướng sửa: (a), không chọn (b)

Chọn **(a)** — tách `Log` ra cổng `AppLogger` + adapter `AndroidAppLogger`, bơm qua Koin.

**Vì sao không chọn (b)** (`testOptions { unitTests.isReturnDefaultValues = true }`): nó câm
**mọi** API Android trên toàn `:ui` test, không riêng `Log` — một `Uri.parse()` sau này trả
`null` thay vì ném lỗi, và bug đó không có exception nào để trace, chỉ có `NullPointerException`
xa nơi gây ra. Dự án còn 10 phase phía sau kế thừa `MviViewModel`; một cờ toàn cục làm câm cả
lớp API là rủi ro tích luỹ, không phải rủi ro một lần. (a) tốn nhiều dòng hơn nhưng đúng
nguyên văn luật MVI doc dòng 978 và không có tác dụng phụ ẩn.

Phát hiện thêm khi đọc `docs/android-mvi-best-practices.md` dòng 75: snippet gốc của tài liệu
đã dùng `log.e(throwable) { "..." }` — nghĩa là bug này là **lệch giữa code thật và tài liệu
đã có sẵn**, không phải một convention mới bịa ra. Việc sửa chỉ đưa code khớp lại tài liệu.

## File đã đổi

| File | Thay đổi |
|---|---|
| `ui/src/main/.../ui/core/logging/AppLogger.kt` | Mới. `fun interface AppLogger { fun e(throwable, message: () -> String) }` (thuần Kotlin, không import platform) + `object NoopAppLogger` (mặc định JVM-safe). |
| `ui/src/main/.../ui/core/logging/AndroidAppLogger.kt` | Mới. Adapter thật, import `android.util.Log`, tag cố định `"FamilyTracker"`. |
| `ui/src/main/.../ui/core/mvi/MviViewModel.kt` | Xoá `import android.util.Log`. Thêm `logger: AppLogger = NoopAppLogger` vào constructor. Nhánh `catch` gọi `logger.e(throwable) { "Unhandled failure in ${..simpleName}" }` thay vì `Log.e(tag, msg, throwable)`. `CancellationException` vẫn được rethrow y nguyên — không đổi. |
| `ui/src/main/.../ui/di/UiModule.kt` | Thêm `single<AppLogger> { AndroidAppLogger() }`. |
| `ui/src/test/.../ui/core/mvi/MviViewModelLaunchSafelyTest.kt` | Giữ lại làm test hồi quy vĩnh viễn — tên rõ ràng, KDoc trỏ ngược về report này. |

Không sửa gì khác ngoài phạm vi finding này (không đụng `CollectEffects.kt`, vốn hợp lệ import
Compose vì nó thuộc tầng Screen, không phải ViewModel).

## Bước 3 — kiểm chứng lại (toàn bộ output thật, không rút gọn ý nghĩa)

```
$ ./gradlew :ui:testDebugUnitTest
BUILD SUCCESSFUL — MviViewModelLaunchSafelyTest: tests=1 failures=0

$ ./gradlew clean assembleDebug
BUILD SUCCESSFUL in 3s
grep -c '^w: file' → 0   (baseline reports/baseline-build-debug.log cũng 0 — không tăng)

$ ./gradlew assembleRelease
BUILD SUCCESSFUL in 21s
grep -c '^w: file' → 0

$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Performing Streamed Install
Success

$ ./gradlew test
BUILD SUCCESSFUL — :app:test (KoinModulesTest 1/1 pass, verify AppLogger single resolves),
:ui:test (1/1 pass), :data:test (NO-SOURCE), :domain:test (NO-SOURCE)

$ grep -rn "import android" ui/src/main/.../ui/core/mvi/
(rỗng — chỉ còn androidx.lifecycle.ViewModel / viewModelScope, không phải android.util.*)
```

`KoinModulesTest` (`app/src/test/.../KoinModulesTest.kt`, dùng `verifyAll()`) giờ cũng phủ
luôn binding mới `single<AppLogger>` — nếu sau này ai đó xoá đăng ký Koin cho `AppLogger` mà
quên, CI bắt được ngay, không phải đợi tới lúc mở app.

## Tài liệu đã cập nhật (cùng lúc)

- `LLM.md` §4: thêm điểm không thương lượng thứ 4 (cổng `AppLogger`, lý do, cái giá nếu một
  ViewModel thật quên forward `logger` qua `super(...)`).
- `LLM.md` §6: ví dụ `uiModule` thêm dòng `single<AppLogger> { AndroidAppLogger() }`.
- `LLM.md` §13: chuyển sang mục Fixed #2 — mô tả sai lệch, bằng chứng, cách sửa, lý do không
  chọn hướng (b).
- `docs/android-mvi-best-practices.md`: thêm đoạn ngay sau snippet base class §1, giải thích
  `log` trong snippet gốc không phải gọi platform trực tiếp mà là một cổng nhỏ — đóng khoảng
  trống tài liệu gốc (viết cho KMP, dùng `expect`/`actual`) chưa nói rõ cho dự án Android
  thuần dùng gì thay thế.

## Việc KHÔNG làm (đúng phạm vi)

Không có ViewModel thật nào tồn tại ở phase-01 để phải sửa lại constructor — `uiModule` hiện
chỉ có `AppLogger` binding, comment cũ "Populated from phase-05 onward" giữ nguyên cho
ViewModel. Không refactor `CollectEffects.kt`, không đổi `AppError`, không tạo file "v2".

## Kết luận

Đã sửa xong và verify sạch: bug được chứng minh trước khi sửa (RuntimeException "not mocked"
nguyên văn), fix theo hướng (a) loại platform import khỏi `MviViewModel`, toàn bộ gate ở bước
3 xanh (test JVM mới pass, `assembleDebug`/`assembleRelease` 0 warning so với baseline, cài
đặt lên `emulator-5554` thành công, `./gradlew test` toàn bộ pass gồm `KoinModulesTest`, và
`grep import android` trong `ui/core/mvi/` rỗng). Tài liệu (`LLM.md` §4/§6/§13, MVI doc §1) đã
cập nhật cùng lúc. Không có câu hỏi còn treo.
