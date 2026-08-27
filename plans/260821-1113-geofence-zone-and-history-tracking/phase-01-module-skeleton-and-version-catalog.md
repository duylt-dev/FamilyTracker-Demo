# Phase 01 — Module skeleton, version catalog, lõi MVI

## Context Links

- [`plan.md`](plan.md) · [`LLM.md`](../../LLM.md) §0, §2, §3, §6, §11, §14 · [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md) §1
- [`research/VERSIONS-VERIFIED.md`](research/VERSIONS-VERIFIED.md) — **bảng version có thẩm quyền**
- [`research/researcher-03-multimodule-toolchain.md`](research/researcher-03-multimodule-toolchain.md) — đọc cả mục "ĐÍNH CHÍNH" ở cuối
- PRD §A.3 Build Configuration · §11.1 gate G6

## Overview

| | |
|---|---|
| Priority | **P0** — mọi phase khác chặn ở đây |
| Status | completed |
| Effort | 5h |
| Gate liên quan | **G6** (`assembleDebug` không lỗi, không warning mới) · **G8** (`assembleRelease` ra APK **đã ký**, cài được, bản đồ hiện đúng) |

Biến repo từ template Android Studio một module thành 4 module `:app` / `:ui` / `:data` /
`:domain` với version catalog đầy đủ, lõi MVI dùng chung, và Koin khởi động được. Chưa có
màn hình nghiệp vụ nào — kết thúc phase app vẫn mở lên là màn hình trống.

## Key Insights

1. **Thêm dependency theo từng nhóm rồi build, không thêm cả bảng một lúc.** Tổ hợp AGP 9.2.1 +
   Gradle 9.4.1 + Kotlin 2.2.10 + Compose BOM 2026.02.01 quá mới để giả định. Thêm 10 thư viện
   cùng lúc rồi nhận một trang lỗi resolve tốn nhiều thời gian hơn 6 lần build riêng
   (`VERSIONS-VERIFIED.md`, mục "Luật cho phase-01").
2. **VERSIONS-VERIFIED.md thắng researcher-03.** Bảng catalog ở researcher-03 §7 sai 3 dòng:
   `mapsCompose 6.2.1` → **8.4.0**, `playServicesMaps 18.2.0` → **20.0.0**,
   `playServicesLocation 21.2.0` → **21.4.0**. researcher-02 §1.1 từng ghi `maps-compose 8.5.0`
   — version không tồn tại, đã sửa thành 8.4.0.
3. **Rủi ro "JDK 11 làm AGP 9 chết" ở researcher-03 §9 đã bị chính báo cáo đó rút lại.** Máy này
   chạy JDK 24 và `gradle/gradle-daemon-jvm.properties` ghim `toolchainVersion=21`. `Java 11`
   trong `compileOptions` chỉ là bytecode target. **Không có việc gì phải làm.**
4. **AGP 9 bundle Kotlin cho module Android**, nên `:app`/`:ui`/`:data` không khai báo plugin
   `kotlin-android`. Nhưng `:domain` là Kotlin JVM thuần → **vẫn cần** plugin
   `org.jetbrains.kotlin.jvm`, khai báo trong catalog và `apply false` ở root.
5. **`namespace` là bắt buộc với mọi module `com.android.library` ở AGP 9.** Thiếu → build fail
   với `A library cannot have multiple different packageName attributes`.
6. **`org.gradle.configuration-cache=true` đang bật.** Đọc `local.properties` bằng
   `Properties().load(file.inputStream())` ở configuration time **không phải input được Gradle
   theo dõi**: đổi API key xong build lại vẫn dùng key cũ từ cache. Dùng
   `providers.fileContents(...)` hoặc `providers.gradleProperty`. Triệu chứng nếu bỏ qua: bản đồ
   xám và không ai hiểu tại sao vì file đã sửa đúng.
7. **Hai version chưa ai kiểm chứng:** `kotlinx-coroutines-core/test` và
   `kotlinx-serialization-json`. `VERSIONS-VERIFIED.md` không có hai dòng này; con số `1.9.2` /
   `1.8.0` đến từ researcher-03 là **chưa xác thực**. Phải verify bằng build ở Nhóm 1 và Nhóm 4.
8. **`:domain` dùng `java.time.Instant` / `java.time.LocalDate`, không thêm `kotlinx-datetime`.**
   minSdk 28 ≥ 26 nên không cần desugaring. Bớt một dependency chưa được kiểm chứng version.
9. **Bản đem demo là `release` (PRD v1.2 §7.2), và `buildTypes.release` của template KHÔNG có
   `signingConfig`.** `assembleRelease` cho ra APK chưa ký, `adb install` từ chối với
   `INSTALL_PARSE_FAILED_NO_CERTIFICATES`. Ký release bằng **debug keystore** một cách tường minh:
   SHA-1 giữ nguyên nên **một** hạn chế API key phủ cả hai variant (`LLM.md` §10). Ký bằng keystore
   khác thì bản release ra bản đồ xám trong khi bản debug vẫn chạy tốt — triệu chứng đó rất dễ bị
   quy oan cho code.
10. **`:app` phải bật `buildFeatures { buildConfig = true }` ngay ở phase này.** Phase-09 sẽ khai
   `SIMULATOR_ENABLED` bằng `buildConfigField`, bật ở **cả hai** variant (PRD v1.2 §6). Bật cờ sớm
   để lúc đó không phải sửa lại file build rồi chạy lại cả 6 nhóm dependency.
11. **Từ phase này, lệnh kiểm chứng chạy trên variant `release`.** Bản debug chỉ còn dùng cho đúng
   một việc: lấy baseline warning của gate G6, vì PRD §11.1 viết gate đó trên `assembleDebug`.

## Requirements

**Chức năng**
- 4 module đúng đồ thị phụ thuộc ở `LLM.md` §2, ép bằng Gradle chứ không bằng thiện chí.
- `gradle/libs.versions.toml` chứa toàn bộ thư viện ở `LLM.md` §14 với version theo `VERSIONS-VERIFIED.md`.
- `MviViewModel` / `UiState` / `UiIntent` / `UiEffect` / `CollectEffects` ở `:ui/core/mvi/`.
- `AppResult` / `AppError` ở `:domain/model/`.
- Koin `startKoin` chạy được với 3 module rỗng; `KoinModulesTest` xanh.

**Phi chức năng**
- `./gradlew assembleDebug` xanh, **không warning mới** so với hiện trạng (gate G6) — lưu baseline vào `reports/`.
- `./gradlew assembleRelease` ra APK **đã ký** và `adb install` được (gate G8).
- `:app` bật `buildConfig`; **chưa** khai `SIMULATOR_ENABLED` (phase-09 khai).
- `MAPS_API_KEY` đọc từ `local.properties`, không xuất hiện trong bất kỳ file được commit nào.

## Architecture

```
:app (com.android.application)  ──▶ :ui, :data, :domain
:ui  (com.android.library + compose + serialization) ──▶ :domain
:data(com.android.library + ksp) ──▶ :domain
:domain (org.jetbrains.kotlin.jvm) ──▶ (không gì cả)
```

`:domain` không có Android plugin ⇒ `import android.*` và `import androidx.compose.*` là **lỗi
biên dịch**, không phải lời hứa. `:ui` không thấy `:data` ⇒ không ViewModel nào chạm được `ZoneDao`.

## Related Code Files

**Tạo**
- `settings.gradle.kts` (sửa): `include(":ui")`, `include(":data")`, `include(":domain")`
- `domain/build.gradle.kts`, `ui/build.gradle.kts`, `data/build.gradle.kts`
- `domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/model/AppError.kt` (+ `AppResult`)
- `ui/src/main/java/.../ui/core/mvi/MviViewModel.kt`, `UiState.kt`, `CollectEffects.kt`
- `ui/src/main/java/.../ui/designsystem/theme/` — `Color.kt`, `Type.kt`, `Dimens.kt`, `Theme.kt`
- `ui/src/main/java/.../ui/di/UiModule.kt` (rỗng), `data/src/main/java/.../data/di/DataModule.kt` (rỗng)
- `app/src/main/java/.../FamilyTrackerApp.kt`
- `app/src/test/java/.../KoinModulesTest.kt`

**Sửa**
- `gradle/libs.versions.toml`, `build.gradle.kts` (root)
- `app/build.gradle.kts` — thêm `signingConfigs` cho release + `buildFeatures { buildConfig = true }`
- `app/src/main/AndroidManifest.xml` — `android:name=".FamilyTrackerApp"`, meta-data Maps API key
- `LLM.md` — **xoá §0 và bảng "hiện có vs đích"** ngay khi 4 module build xanh (§0 tự yêu cầu)

**Xoá**
- `app/src/main/java/.../ui/theme/` (Color/Theme/Type) — chuyển sang `:ui/designsystem/theme/`
- `app/src/test/java/.../ExampleUnitTest.kt`, `app/src/androidTest/java/.../ExampleInstrumentedTest.kt`

## Implementation Steps

1. **Nhóm 0 — 4 module rỗng, chưa thêm dependency nào.** Tạo 3 `build.gradle.kts` tối thiểu.
   `:domain` khai báo bytecode target rõ ràng (snippet `kotlin { compileOptions { ... } }` ở
   researcher-03 §2.2 **không phải DSL hợp lệ** của Kotlin JVM plugin):
   ```kotlin
   // domain/build.gradle.kts
   plugins { alias(libs.plugins.kotlin.jvm) }
   java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
   kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
   ```
   `:ui` và `:data` bắt buộc có `namespace`, `minSdk = 28`, `compileSdk { version = release(37) }`
   — **Fixed:** bản gốc của bước này ghi `release(36) { minorApiLevel = 1 } }`, đã kiểm chứng
   build fail thật (androidx.core 1.19.0 / lifecycle 2.11.0 đòi compileSdk ≥ 37, xem
   ENV-BRIEFING.md §2). `app/build.gradle.kts` đã dùng `release(37)` từ trước khi phase bắt đầu;
   `:ui`/`:data` áp cùng cấu hình trong phase này.
   Dùng `project(":domain")`, **không** bật `TYPESAFE_PROJECT_ACCESSORS` (một ẩn số nữa, không đổi lấy gì).
   → `./gradlew :domain:compileKotlin :ui:assembleRelease :data:assembleRelease :app:assembleRelease`
2. **Nhóm 1 — `:domain` + coroutines.** Thêm `kotlinx-coroutines-core` vào catalog. Trước khi
   chọn số, xác minh version tồn tại; nếu `./gradlew :domain:build` báo không resolve được thì
   ghi lại version thật vào `VERSIONS-VERIFIED.md`. → `./gradlew :domain:build`
3. **Nhóm 2 — Koin.** `koin-android` (:data, :app), `koin-androidx-compose` (:ui, :app),
   `koin-test` (:app test) — tất cả `4.2.2`. Viết `FamilyTrackerApp.startKoin { androidContext(...); modules(dataModule, uiModule) }`
   với hai module rỗng. Viết `KoinModulesTest`:
   ```kotlin
   // import org.koin.test.verify.verify — KHÔNG phải io.insert_koin.* như researcher-03 §4.3 ghi
   koinApplication { modules(dataModule, uiModule) }.checkModules()
   ```
   → `./gradlew :app:test --tests '*KoinModulesTest*'`
4. **Nhóm 3 — Room + KSP.** Plugin `com.google.devtools.ksp` version `2.2.10-2.0.2` (khoá cứng
   theo Kotlin 2.2.10). Ở `:data` thêm `room-runtime`, `room-ktx`, `ksp(room-compiler)` — `2.8.4` —
   và `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Chưa viết entity nào.
   → `./gradlew :data:assembleRelease`
5. **Nhóm 4 — Compose ở `:ui`, navigation, serialization, lifecycle.** Compose BOM 2026.02.01,
   `navigation-compose 2.9.8`, plugin `org.jetbrains.kotlin.plugin.serialization` 2.2.10 (khai báo
   trong catalog, không hardcode như researcher-03 §5.1 gợi ý), `kotlinx-serialization-json`
   (verify version), `lifecycle-viewmodel-compose` / `lifecycle-runtime-compose` 2.11.0.
   Chuyển `ui/theme/` từ `:app` sang `:ui/designsystem/theme/`, đổi package, sửa `MainActivity`.
   → `./gradlew :ui:assembleRelease :app:assembleRelease`
6. **Nhóm 5 — Maps + Location.** `:ui` nhận `maps-compose 8.4.0` + `maps-compose-utils 8.4.0` +
   `play-services-maps 20.0.0`; `:data` nhận `play-services-location 21.4.0`.
   Ngay sau khi build xanh, **kiểm tra maps-compose có ép nâng/hạ Compose không**:
   → `./gradlew :ui:dependencies --configuration releaseRuntimeClasspath > /tmp/ui-deps.txt`
   → `grep -E "compose-(ui|runtime)|play-services-maps" /tmp/ui-deps.txt` — tìm dấu `->` (ép version).
7. **Nhóm 6 — thư viện test.** `kotlinx-coroutines-test` (verify version), `turbine 1.2.1`,
   `room-testing 2.8.4` (androidTest của `:data`). → `./gradlew test`

8. **Ký bản release bằng debug keystore, tường minh** (PRD v1.2 §7.2, `LLM.md` §10):
   ```kotlin
   // app/build.gradle.kts
   signingConfigs {
       // Dùng lại debug keystore để SHA-1 không đổi giữa hai variant — nhờ đó MỘT hạn chế
       // API key phủ được cả hai. Lựa chọn cho bản demo, không dùng để phát hành thật.
       create("demo") {
           storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
           storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
       }
   }
   buildTypes { release { signingConfig = signingConfigs.getByName("demo") } }
   buildFeatures { buildConfig = true }
   ```
   Kiểm ngay, không đợi phase-11:
   → `./gradlew :app:assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk`
9. **Maps API key qua `providers`, không qua `Properties().load` ở configuration time:**
   ```kotlin
   // app/build.gradle.kts
   val mapsApiKey = providers.fileContents(
       rootProject.layout.projectDirectory.file("local.properties")
   ).asText.map { text ->
       text.lineSequence().firstOrNull { it.startsWith("MAPS_API_KEY=") }
           ?.substringAfter("=")?.trim().orEmpty()
   }.getOrElse("")
   defaultConfig { manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey }
   ```
   Thêm `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}"/>` vào manifest.
10. **Viết lõi MVI** đúng nguyên văn hợp đồng ở MVI doc §1 và `CollectEffects` ở §4 (Channel BUFFERED,
   `repeatOnLifecycle(STARTED)`, `collect` không `collectLatest`, handler qua `rememberUpdatedState`).
11. **Xoá `LLM.md` §0** (chính §0 yêu cầu điều này khi phase 1 xong) và cập nhật §14 nếu có version
    nào khác bảng đã ghi. Commit cùng lúc với thay đổi cấu trúc.

## Todo List

- [x] Nhóm 0: 4 module rỗng build xanh
- [x] Nhóm 1: `:domain` + coroutines-core, version đã xác minh (1.10.2)
- [x] Nhóm 2: Koin 4.2.2 + `KoinModulesTest` xanh (dùng `verifyAll()` từ `org.koin.test.verify`, không phải `checkModules()` — API đã deprecated, xem báo cáo dev-phase-01)
- [x] Nhóm 3: Room 2.8.4 + KSP 2.2.10-2.0.2 + schemaLocation (cần thêm `android.disallowKotlinSourceSets=false` — xem `LLM.md` §13 Fixed #1)
- [x] Nhóm 4: Compose BOM + navigation 2.9.8 + serialization 1.9.0 + lifecycle 2.11.0
- [x] Nhóm 5: maps-compose **8.3.1** (không phải 8.4.0 — build fail, xem `VERSIONS-VERIFIED.md`) + play-services-maps 20.0.0 + location 21.4.0
- [x] Nhóm 6: coroutines-test 1.10.2 + turbine 1.2.1 + room-testing 2.8.4
- [x] `AppResult`/`AppError` ở `:domain/model/`
- [x] `MviViewModel` + `UiState` + `CollectEffects` ở `:ui/core/mvi/`
- [x] Theme chuyển từ `:app` sang `:ui/designsystem/theme/`, `:app` không còn composable nào ngoài placeholder `Surface` (NavHost chưa tồn tại, đến phase-04)
- [x] Maps API key qua `providers`, manifest placeholder — xác minh bằng `aapt dump xmltree` trên APK đã đóng gói
- [x] `signingConfigs` cho release + `buildConfig = true`; APK release cài được (**G8** — `adb install` Success)
- [x] Lưu baseline warning của `assembleDebug` vào `reports/` (1 warning: `android.disallowKotlinSourceSets` experimental flag — baseline cho **G6** ở phase-11)
- [x] Xoá `LLM.md` §0; ghi version thực tế đã dùng vào `VERSIONS-VERIFIED.md`

## Success Criteria

```bash
# G6 — PRD §11.1 viết gate này trên variant debug, giữ nguyên. Lưu số này làm baseline.
./gradlew clean assembleDebug 2>&1 | tee reports/baseline-build-debug.log
grep -ci "warning:" reports/baseline-build-debug.log

# G8 — bản đem đi demo
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk   # cài được = APK đã ký
./gradlew test                                                  # KoinModulesTest xanh, cả hai variant
./gradlew :ui:dependencies --configuration releaseRuntimeClasspath | grep -c '\->'
```
- 4 module tồn tại; `:domain` không có `android.*` nào (`grep -r "import android" domain/src` trả rỗng).
- `git status` không hiện `local.properties`; `grep -r "AIza" --include=*.kts --include=*.toml --include=*.xml .` trả rỗng.

## Risk Assessment

| Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu |
|---|---|---|---|
| `maps-compose 8.4.0` ép Compose khác BOM 2026.02.01 | Trung bình | Lỗi runtime khó truy | Bước 6 in cây dependency; nếu bị ép thì thử `maps-compose 9.0.0-rc01` hoặc pin Compose bằng constraint, ghi vào `LLM.md` §13 |
| KSP 2.2.10-2.0.2 không tương thích config cache | Thấp | Build chậm/ lỗi | Thử `./gradlew --no-configuration-cache :data:assembleRelease` để khoanh vùng |
| Version coroutines/serialization đoán sai | Cao | Fail resolve | Đã tách thành nhóm riêng, lỗi lộ ngay ở bước 2 và 5 |
| **`assembleRelease` ra APK chưa ký** | Cao nếu quên | `adb install` từ chối, G8 mở | Bước 7b cấu hình `signingConfigs` và cài thử ngay trong phase này |
| Ký release bằng keystore khác `debug.keystore` | Trung bình | Bản đồ xám **chỉ ở** release, dễ quy oan cho code | Dùng đúng `~/.android/debug.keystore`; nếu buộc đổi, thêm SHA-1 mới vào hạn chế API key **trước** |
| `optimization { enable = false }` ở release khiến G7 khó đạt | Thấp | Log lộ toạ độ | R8 tắt là quyết định đã chốt (PRD v1.2 §7.2) — chặn log bằng cờ ở phase-11, không bật R8 |

## Security Considerations

- `MAPS_API_KEY` **chỉ** nằm ở `local.properties` (đã có trong `.gitignore`). Không đưa vào catalog,
  không đưa vào manifest dạng chữ.
- Key phải được restrict theo package `com.example.pion.family.tracker.demo` + SHA-1 debug
  (`./gradlew signingReport`) trước khi bật billing (PRD §7.3). Vì release cũng ký bằng debug
  keystore, **một** hạn chế SHA-1 phủ cả hai variant — đó chính là lý do chọn cách ký này.
- Không thêm dependency nào có network ngoài Play Services — app không được gửi vị trí đi đâu.

## Next Steps

→ [phase-02](phase-02-domain-model-and-room-persistence.md). Chặn: tất cả các phase còn lại.
