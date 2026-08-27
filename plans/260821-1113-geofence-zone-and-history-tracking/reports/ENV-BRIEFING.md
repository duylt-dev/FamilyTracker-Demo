# Briefing môi trường — bắt buộc đọc trước mọi phase

> Viết ngày 2026-08-21 sau khi kiểm chứng trực tiếp trên máy. Mọi dòng dưới đây đã chạy thật,
> không phải giả định. Bảng này **thắng** mọi lệnh mẫu ghi trong file phase khi hai bên khác nhau.

## 1. JAVA_HOME — bắt buộc export trước MỌI lệnh gradle

`java` trên PATH là **24.0.2**. Gradle 9.4.1 đọc `gradle/gradle-daemon-jvm.properties`
(`toolchainVersion=21`) nên vẫn chạy được, nhưng launcher JVM 24 gây cảnh báo và có thể
làm AGP 9.2.1 hỏng bất ngờ. Luôn dùng:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # JBR 21.0.10
```

Đã xác minh: `./gradlew --version` → `Launcher JVM: 21.0.10`, `Daemon JVM: Compatible with Java 21`.

**Không** thêm `org.gradle.java.home` vào `gradle.properties` — file đó được commit, đường dẫn này
chỉ đúng trên máy này.

## 2. compileSdk phải là 37, KHÔNG phải 36.1 — sai lệch đã kiểm chứng so với plan

`phase-01` bước 1 ghi `compileSdk { version = release(36) { minorApiLevel = 1 } }`. Với bảng
version ở `VERSIONS-VERIFIED.md`, cấu hình đó **build fail**:

```
Dependency 'androidx.core:core-ktx:1.19.0'                        requires compileSdk >= 37
Dependency 'androidx.core:core:1.19.0'                            requires compileSdk >= 37
Dependency 'androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0' requires compileSdk >= 37
:app is currently compiled against android-36.1
```

Cách sửa đã kiểm chứng (`./gradlew assembleDebug` → BUILD SUCCESSFUL in 7s):

```kotlin
compileSdk {
    version = release(37)
}
```

Áp cho **cả 3 module Android** `:app` `:ui` `:data`. `targetSdk = 36` giữ nguyên (chỉ là opt-in
hành vi runtime, không liên quan). Platform `android-37.0` đã cài sẵn trong SDK.

Đã áp vào `app/build.gradle.kts` trước khi phase-01 bắt đầu. Ghi dòng này vào `LLM.md` §13
và sửa `phase-01` khi cập nhật tài liệu.

## 3. Thiết bị — LUÔN dùng `-s`, có 2 device cùng lúc

| | Serial | Mô tả | Dùng cho |
|---|---|---|---|
| Emulator | `emulator-5554` | Pixel_10_Pro_XL · API 37.1 · **Google APIs PlayStore** · arm64 · RAM 4096 · heap 576 | **Vòng lặp test chính** — mọi `adb install`, `connectedAndroidTest`, mock location |
| Máy thật | `RF8Y60B9NCZ` | Samsung **SM-A165F** · Android 16 | **Chỉ gate G5** ở phase-07/11 (đóng Q-E: là Samsung, đúng giả định) |

`adb install ...` trần sẽ fail `more than one device/emulator`. Luôn:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=...   # đặt ANDROID_SERIAL=emulator-5554
```

Cách chắc chắn nhất cho gradle: `export ANDROID_SERIAL=emulator-5554`.

Chờ emulator boot xong: `adb -s emulator-5554 wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'`

## 4. Mock location trên emulator

Emulator có Play Services thật nên `FusedLocationProviderClient` + Geofencing API chạy đúng.
Bơm vị trí:

```bash
adb -s emulator-5554 emu geo fix <lon> <lat>            # một điểm
adb -s emulator-5554 emu geo gpxfile /path/route.gpx    # phát lại tuyến (dùng ở phase-09/11)
```

Lưu ý thứ tự: `geo fix` nhận **lon trước, lat sau**.

## 5. MAPS_API_KEY

Có sẵn trong `local.properties` (`MAPS_API_KEY=AIza...`), file này đã nằm trong `.gitignore`.
**Không** đưa key vào bất kỳ file được commit nào. Đọc qua `providers.fileContents(...)` —
`Properties().load()` ở configuration time bị configuration cache (`org.gradle.configuration-cache=true`)
làm cũ giá trị, triệu chứng là bản đồ xám sau khi đã sửa đúng key.

## 6. build-tools

Cao nhất đã cài: **36.1.0**. Chưa có 37.x. Nếu AGP 9.2.1 đòi build-tools 37 thì cài bằng:
`~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "build-tools;37.0.0"`
Hiện tại `assembleDebug` với compileSdk 37 đã xanh nên chưa cần.

## 7. Git

Branch `main`, mới 1 commit `init project`, 421 file đang staged (`.claude/`, PRD, plan).
Quy ước đã chốt với chủ dự án: **commit sau mỗi phase pass**, conventional commit, không có
tham chiếu AI, `LLM.md` cập nhật **trong cùng commit** với thay đổi cấu trúc.

## 8. Gate G6 — số warning KHÔNG tất định, phải đo bằng `--no-configuration-cache`

Phát hiện ở phase-02, đã kiểm chứng hai chiều:

```bash
./gradlew clean assembleDebug --no-configuration-cache  2>&1 | grep -ci "warning:"   # → 1
./gradlew clean assembleDebug                           2>&1 | grep -ci "warning:"   # → 0
```

Warning duy nhất hiện có (`The option setting 'android.disallowKotlinSourceSets=false' is
experimental`) phát ở **configuration time**. `org.gradle.configuration-cache=true` đang bật, nên
khi cache còn ấm Gradle bỏ qua toàn bộ pha configuration và **không phát lại warning đó** — kể cả
sau `clean`, vì `clean` xoá output chứ không xoá config cache.

Hệ quả: gate G6 ("không warning mới so với baseline") sẽ cho kết quả khác nhau giữa hai lần chạy
giống hệt nhau, tuỳ máy vừa build gì trước đó. Một gate như vậy chấm được cả pass lẫn fail cho cùng
một commit.

**Luật đo G6 từ đây:** luôn dùng `--no-configuration-cache`. Baseline hiện tại đo bằng cách đó là
**1 warning**. `reports/baseline-build-debug.log` cũng chứa 1 — khớp. Phase-11 phải viết luật này
vào tiêu chí gate, không để mỗi lần chạy ra một số.
