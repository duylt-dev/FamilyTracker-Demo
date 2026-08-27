# Dev Report — Phase 01: Module skeleton, version catalog, lõi MVI

Ngày: 2026-08-21 · Status: **completed**, cả G6 và G8 pass.

## Tóm tắt nhóm 0→6

| Nhóm | Nội dung | Version thực tế | Build |
|---|---|---|---|
| 0 | 4 module rỗng (`:app` `:ui` `:data` `:domain`), Gradle graph một chiều | — | Xanh |
| 1 | `:domain` + `kotlinx-coroutines-core` | **1.10.2** | Xanh |
| 2 | Koin (`koin-android`, `koin-androidx-compose`, `koin-test`) + `KoinModulesTest` | **4.2.2** | Xanh |
| 3 | Room + KSP | Room **2.8.4**, KSP **2.2.10-2.0.2** | Xanh — cần fix (xem Deviation #2) |
| 4 | Compose ở `:ui`, navigation, serialization, lifecycle | navigation **2.9.8**, serialization-json **1.9.0** (mới verify), lifecycle **2.11.0** | Xanh |
| 5 | Maps + Location | maps-compose **8.3.1** (không phải 8.4.0), play-services-maps **20.0.0**, play-services-location **21.4.0** | Xanh — cần fix (xem Deviation #1) |
| 6 | Test libs | coroutines-test **1.10.2** (mới verify), turbine **1.2.1**, room-testing **2.8.4** | Xanh |

Tất cả version đã force-resolve thật qua `./gradlew <module>:dependencies --configuration <compileClasspath|releaseRuntimeClasspath>`, không suy từ log build thường (build NO-SOURCE không luôn resolve classpath).

## Sai lệch so với file phase (đầy đủ, không giấu)

### 1. `maps-compose 8.4.0` build fail thật — dùng `8.3.1` thay thế

```
e: Incompatible classes were found in dependencies. Remove them from the classpath or use
   '-Xskip-metadata-version-check' to suppress errors
e: .../maps-compose-8.4.0-api.jar!/META-INF/maps-compose.kotlin_module Module was compiled
   with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0,
   expected version is 2.2.0.
```
Nguyên nhân: POM của `maps-compose 8.4.0` khai `kotlin-stdlib 2.4.10`; compiler dự án Kotlin
`2.2.10` chỉ đọc metadata tới `2.3.0`. Đây là artifact thật (Google mới publish rất gần ngày
hôm nay, `maven-metadata.xml` `lastUpdated=20260810...`), không phải version ảo như trường hợp
`8.5.0` ở researcher-02.

Đã thử `8.3.1` (POM khai `kotlin-stdlib 2.3.21` — đọc được) → build xanh. Cái giá: `8.3.1` tự
mang `dependencyManagement` ép `androidx.compose:compose-bom` thật ở classpath lên
`2026.03.00`, cao hơn `2026.02.01` khai trong `libs.versions.toml`. Đã kiểm `./gradlew
:ui:dependencies --configuration releaseRuntimeClasspath` — toàn bộ family `compose-*` cùng
lên `1.11.0`/tương ứng, không có version lẫn lộn giữa hai artifact. Build/run/install đều pass.
Đã ghi vào `LLM.md` §13 Open #2 và `VERSIONS-VERIFIED.md`.

**Chưa làm:** pin cứng compose-bom về đúng 2026.02.01 bằng constraint — không cần thiết vì
build đã nhất quán và xanh; để dành nếu phase sau phát hiện vấn đề thật.

### 2. KSP 2.2.10-2.0.2 xung đột "built-in Kotlin" của AGP 9

```
> com.android.builder.errors.EvalIssueException: Using kotlin.sourceSets DSL to add Kotlin
  sources is not allowed with built-in Kotlin.
  Solution: Use android.sourceSets DSL instead... or set android.disallowKotlinSourceSets=false
```
`:app`/`:ui`/`:data` không áp `kotlin-android` plugin riêng (đúng theo Key Insight #4 của phase
file — AGP 9 bundle Kotlin). KSP 2.2.10-2.0.2 vẫn đăng ký thư mục sinh mã qua DSL cũ
`kotlin.sourceSets`, xung đột thẳng. Đây là phát hiện mới, **không có trong ENV-BRIEFING.md hay
VERSIONS-VERIFIED.md**. Sửa bằng cách thêm 1 dòng vào `gradle.properties`:
```
android.disallowKotlinSourceSets=false
```
Đây là workaround Google tự gợi ý trong thông báo lỗi (link tới
`developer.android.com/r/tools/built-in-kotlin`), không phải hack riêng. Ghi vào `LLM.md` §13
Fixed #1.

### 3. `compileSdk` — đã theo đúng chỉ dẫn override, không theo file phase gốc

File phase gốc (bước 1) ghi `compileSdk { version = release(36) { minorApiLevel = 1 } }`. Theo
chỉ dẫn của orchestrator (khớp ENV-BRIEFING.md §2), dùng `release(37)` cho cả `:app` `:ui`
`:data`. `app/build.gradle.kts` đã có sẵn `release(37)` trước khi phase bắt đầu; áp cùng cấu
hình cho `:ui`/`:data`. Đã sửa nguyên văn bước 1 trong file phase-01, đánh dấu Fixed.

### 4. `KoinModulesTest` dùng `verifyAll()`, không dùng `checkModules()`

Code mẫu trong phase file dùng `koinApplication { modules(...) }.checkModules()`, nhưng chính
comment ngay phía trên nó lại ghi "import org.koin.test.verify.verify — KHÔNG phải
io.insert_koin.*" — hai chỉ dẫn mâu thuẫn nhau. Đã kiểm tra bằng `javap` trên
`koin-test-jvm-4.2.2.jar`: `checkModules()` tồn tại nhưng **deprecated** ("Migrate to verify()
API" — warning thật khi compile). Method thay thế đúng là `List<Module>.verifyAll()` trong
`org.koin.test.verify`, không phải hàm nhận `List` làm tham số như code mẫu render nhầm. Đã
dùng `listOf(dataModule, uiModule).verifyAll()` — biên dịch sạch, test pass, không deprecation
warning. `verifyAll` mang annotation `@KoinExperimentalAPI` (chỉ 1 warning ở compile test, không
tính vào G6 vì G6 đo trên `assembleDebug`, không compile test source).

### 5. `./gradlew test` chỉ chạy variant debug, không có `testReleaseUnitTest`

Chỉ dẫn "Định nghĩa xong" gọi `./gradlew test` — task này map tới `testDebugUnitTest` cho mỗi
module theo mặc định AGP (`testBuildType` mặc định là `debug`). Không có task
`testReleaseUnitTest` sinh ra tự động; thử gọi thẳng bị Gradle báo "task not found". Đây là
hành vi chuẩn của AGP, không phải lỗi cấu hình. `KoinModulesTest` không phụ thuộc build-type
nên chạy một lần trên debug là đủ ý nghĩa ở giai đoạn này — không thêm
`android.testBuildType = "release"` vì chưa cần (YAGNI), sẽ xét lại khi có `BuildConfig` field
khác nhau giữa hai variant (phase-09 `SIMULATOR_ENABLED`).

## File đã tạo

- `domain/build.gradle.kts`, `ui/build.gradle.kts`, `data/build.gradle.kts`
- `domain/src/main/kotlin/.../domain/model/AppError.kt` (28 dòng — `AppError` + `AppResult<T>` + `onSuccess`/`onFailure`)
- `ui/src/main/java/.../ui/core/mvi/UiState.kt` (10), `MviViewModel.kt` (56), `CollectEffects.kt` (27)
- `ui/src/main/java/.../ui/designsystem/theme/Color.kt` (11), `Theme.kt` (58), `Type.kt` (34) — chuyển từ `:app`
- `ui/src/main/java/.../ui/di/UiModule.kt` (8, rỗng)
- `data/src/main/java/.../data/di/DataModule.kt` (8, rỗng)
- `app/src/main/java/.../FamilyTrackerApp.kt` (20 — `startKoin`)
- `app/src/test/java/.../KoinModulesTest.kt` (21)

## File đã sửa

- `settings.gradle.kts` — `include(":ui" ":data" ":domain")`
- `build.gradle.kts` (root) — thêm plugin `android.library`, `kotlin.jvm`, `ksp`, `kotlin.serialization` (apply false)
- `gradle/libs.versions.toml` — thêm toàn bộ mục ở LLM.md §14 + 2 plugin mới
- `gradle.properties` — thêm `android.disallowKotlinSourceSets=false` (Deviation #2)
- `app/build.gradle.kts` — thêm `project(":ui" ":data" ":domain")`, `signingConfigs.demo` (debug keystore), `buildFeatures.buildConfig = true`, `manifestPlaceholders["MAPS_API_KEY"]` qua `providers.fileContents`, thêm koin/test deps
- `app/src/main/AndroidManifest.xml` — `android:name=".FamilyTrackerApp"`, `<meta-data com.google.android.geo.API_KEY>`
- `app/src/main/java/.../MainActivity.kt` — bỏ `Greeting`/`Scaffold`/`Text`, chỉ còn `Surface` rỗng + `FamilyTrackerDemoTheme` từ `:ui`
- `LLM.md` — xoá §0 + bảng "hiện có vs đích"; §13 thêm 1 Open (maps-compose/BOM) + 1 Fixed (KSP/built-in-kotlin); §14 viết lại bảng version thật + sửa `compileSdk 36.1` → `37`
- `plans/.../phase-01-module-skeleton-and-version-catalog.md` — Status → completed, tick 13/13 Todo, sửa bước 1 (compileSdk 37), ghi chú deviation ở dòng Nhóm 3/5
- `plans/.../research/VERSIONS-VERIFIED.md` — thêm mục "CẬP NHẬT sau build thật", sửa dòng maps-compose, điền bảng "Chưa kiểm chứng" → "Đã xác minh"

## File đã xoá

- `app/src/main/java/.../ui/theme/{Color,Theme,Type}.kt` (chuyển sang `:ui`)
- `app/src/test/java/.../ExampleUnitTest.kt`
- `app/src/androidTest/java/.../ExampleInstrumentedTest.kt`

## Output thật — "Định nghĩa xong"

```
$ export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
$ export ANDROID_SERIAL=emulator-5554
$ ./gradlew clean assembleDebug 2>&1 | tee reports/baseline-build-debug.log
...
BUILD SUCCESSFUL in 30s
103 actionable tasks: 68 executed, 32 from cache, 3 up-to-date

$ grep -ci "warning:" reports/baseline-build-debug.log
1
$ grep -i warning reports/baseline-build-debug.log
WARNING: The option setting 'android.disallowKotlinSourceSets=false' is experimental.
```
Baseline G6 = **1 warning** (cờ thử nghiệm tự thêm ở Deviation #2, không phải warning code).
Con số này phase-11 dùng làm mốc "không warning mới".

```
$ ./gradlew assembleRelease
BUILD SUCCESSFUL

$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Performing Streamed Install
Success
```
G8 pass — APK ký bằng debug keystore, cài được. Đã `am start` MainActivity, `topResumedActivity`
xác nhận chạy, logcat không có `FATAL`/`AndroidRuntime`/lỗi Koin.

```
$ aapt dump xmltree app-release.apk AndroidManifest.xml | grep -A1 "com.google.android.geo.API_KEY"
A: android:name="com.google.android.geo.API_KEY"
A: android:value="AIzaSy..." (giá trị thật, đã redact trong báo cáo này)
```
Xác nhận key thật được `providers.fileContents` đưa vào manifest đã đóng gói — không rỗng,
không placeholder chết.

```
$ ./gradlew test
BUILD SUCCESSFUL — KoinModulesTest PASS (app:testDebugUnitTest), domain/ui NO-SOURCE (chưa có
test nào cần viết ở phase này, đúng phạm vi)

$ ./gradlew :ui:dependencies --configuration releaseRuntimeClasspath | grep -c '\->'
449   ← toàn bộ family androidx.compose.* + androidx.lifecycle.* + androidx.* bị maps-compose
        8.3.1 ép lên version cao hơn catalog khai — xem Deviation #1. KHÔNG có version lẫn lộn
        (mọi artifact compose-* hội tụ về đúng 1 version sau resolve).

$ grep -r "import android" domain/src ; echo "exit=$?"
exit=1   ← rỗng, domain sạch

$ grep -rn "AIza" --include='*.kts' --include='*.toml' --include='*.xml' . ; echo "exit=$?"
exit=1   ← rỗng, không leak key

$ git status --porcelain=v1 -- local.properties
(rỗng)   ← không tracked, đúng .gitignore
```

## Tests Status

- Type check / compile: **pass** (assembleDebug + assembleRelease, cả 4 module)
- Unit tests: **pass** — `KoinModulesTest` xanh trên variant debug (`:app:testDebugUnitTest`).
  `:domain` và `:ui` chưa có test file nào — đúng phạm vi phase-01 (ZoneEvaluator etc. thuộc
  phase-03).
- Integration / instrumented: chưa chạy — `:data` androidTest (Room DAO) chưa có file nào viết,
  đúng phạm vi (phase-02).

## `:ui:dependencies` — có ép version Compose không?

**Có.** `maps-compose 8.3.1` tự mang `compose-bom 2026.03.00` trong `dependencyManagement` của
nó, cao hơn `2026.02.01` khai trong `libs.versions.toml`. Gradle giải quyết về **một** version
thống nhất (không có 2 version compose-ui khác nhau tồn tại song song) — build/run/cài đặt đều
pass. Chi tiết đầy đủ ở Deviation #1 và `LLM.md` §13 Open #2.

## Việc còn dở / chưa kiểm chứng

- **Không có việc nào bị chặn.** Emulator `emulator-5554` đã boot sẵn từ trước khi phiên chạy,
  không cần chờ `wait-for-device`.
- `testReleaseUnitTest` không tồn tại (Deviation #5) — chấp nhận được ở giai đoạn này, ghi chú
  lại để phase sau biết nếu cần bật `testBuildType = "release"`.
- Chưa pin cứng `compose-bom` — để nguyên vì build nhất quán; nếu phase sau gặp lỗi runtime
  Compose khó hiểu, xem lại Deviation #1 trước tiên.
- `signingReport` (SHA-1 debug keystore) chưa chạy trong phase này — không nằm trong "Định
  nghĩa xong" nhưng cần trước khi khoá Maps API key theo package+SHA-1 (Security Considerations
  của phase file). Để dành cho người phụ trách hạn chế API key trên Google Cloud Console.

## Docs impact

**Major.** `LLM.md` §0 đã xoá theo đúng yêu cầu tự thân của mục đó; §13, §14 cập nhật với sự
thật đã build. `VERSIONS-VERIFIED.md` và phase-01 file đã đồng bộ với build thật, không còn
version chưa xác minh nào sót lại trong phạm vi phase-01.
