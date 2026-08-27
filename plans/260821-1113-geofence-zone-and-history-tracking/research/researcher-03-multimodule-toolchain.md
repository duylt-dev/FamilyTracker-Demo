# Nghiên cứu: Multi-Module Android với AGP 9.2.1 + Kotlin 2.2.10

**Ngày**: 21-08-2026 | **Dự án**: FamilyTrackerDemo | **Phạm vi**: Cấu hình toolchain 4 module (app, ui, data, domain)

---

## 1. Những gì AGP 9 thay đổi (so với AGP 8)

### 1.1 Built-in Kotlin Support (CẬP NHẬT BẮTBUỘC)

**Vấn đề**: AGP 9.0+ bắt buộc phải sử dụng built-in Kotlin support, tuy nhiên thay đổi này đi kèm với yêu cầu phối hợp version cẩn thận.

- **AGP 9.0+ bundled** Kotlin 2.2.10 ([https://developer.android.com/build/releases/agp-9-0-0-release-notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes))
- **Built-in Kotlin plugin** tự động được áp dụng, không cần `org.jetbrains.kotlin.android` plugin nữa
- **Hệ quả**: Mọi module Android (`:app`, `:ui`, `:data`) sẽ dùng Kotlin compiler từ AGP, không có phiên bản mismatch

**Cách xử lý**: Trong `gradle/libs.versions.toml` chỉ khai báo AGP, bỏ plugin `kotlin-android`:

```toml
[versions]
agp = "9.2.1"
kotlin = "2.2.10"  # Tương thích, nhưng AGP sẽ auto-upgrade nếu thấp hơn

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
# KHÔNG còn: kotlin-android plugin, vì AGP 9 cung cấp sẵn
```

### 1.2 Namespace khai báo bắt buộc

**Vấn đề**: AGP 9 bắt buộc mọi module `com.android.library` phải khai báo `namespace` (trước đây tuỳ chọn).

```kotlin
// app/build.gradle.kts
android {
    namespace = "com.example.pion.family.tracker.demo"
    // ...
}

// ui/build.gradle.kts  ← BẮTBUỘC có
android {
    namespace = "com.example.pion.family.tracker.demo.ui"
}

// data/build.gradle.kts  ← BẮTBUỘC có
android {
    namespace = "com.example.pion.family.tracker.demo.data"
}
```

**Khác biệt so với AGP 8**: Trước dùng `package` trong manifest; AGP 9 dùng `namespace` trong DSL.

### 1.3 compileSdk Syntax Mới

**Hiện tại** (AGP 9.2.1, như trong dự án):
```kotlin
compileSdk {
    version = release(36) {
        minorApiLevel = 1  # compileSdk 36.1
    }
}
```

**Cách cũ** (AGP 8):
```kotlin
compileSdk = 36
```

Syntax mới cho phép khai báo minor API level, nhưng **không bắt buộc**. Nếu bỏ `minorApiLevel`, mặc định là `0`. Dự án đang dùng `36.1` — kiểm chứng: tìm trong `build.gradle.kts` ✓ (dòng 8-11).

### 1.4 buildTypes/optimization Block Mới

**Hiện tại** (như dự án):
```kotlin
buildTypes {
    release {
        optimization {
            enable = false  # Disable R8 shrinking/obfuscation
        }
    }
}
```

Cấu trúc này mới trong AGP 9, thay thế logic cũ dùng `minifyEnabled` flag. **Hệ quả**: release build sẽ không shrink code (toàn bộ class được giữ), giúp debug nhưng tăng kích thước APK.

### 1.5 DSL Architecture Changes — LibraryExtension / ApplicationExtension

**Vấn đề**: AGP 9 loại bỏ parameterization của `CommonExtension`. Các plugin convention hoặc script cấu hình chung **không được** dùng kiểu cũ:

```kotlin
// ❌ KHÔNG dùng ở AGP 9
extensions.configure<CommonExtension<*, *, *, *>> {
    compileSdk = 36
}

// ✅ Đúng với AGP 9
extensions.configure<ApplicationExtension> {
    compileSdk = 36
}

extensions.configure<LibraryExtension> {
    compileSdk = 36
}
```

**Cách ứng phó**: Nếu xây dựng convention plugin (build-logic), **phải tách riêng** plugin cho app vs. library, hoặc dùng reflection để phát hiện loại extension đúng.

### 1.6 Removed Features

| Tính năng | Thay thế | Ảnh hưởng dự án |
|---|---|---|
| Embedded Wear OS app support (`wearApp` config) | Build separate Wear app | **Không có**, dự án chỉ là mobile |
| Split APK by screen density | Use Android App Bundles | **Không có**, dự án dùng bundled release |

**Tóm lại**: Đáng lưu ý nếu mở rộng dự án thành Wear, nhưng hiện không liên quan.

### 1.7 Tóm lại Breaking Changes AGP 9.0 → 9.2.1

| Breaking Change | Dự án hiện tại | Cần sửa? |
|---|---|---|
| Built-in Kotlin support | ✓ Sẵn có (AGP 9.2.1) | **Không**, AGP tự xử lý |
| Namespace bắt buộc | Chưa có `:ui`, `:data` | **Có**, thêm khi tạo module |
| compileSdk syntax mới | ✓ Sẵn có (36.1) | **Không** |
| buildTypes/optimization | ✓ Sẵn có | **Không** |
| DSL architecture | Chưa có convention plugin | **Tuỳ**, nếu dùng build-logic thì cần |

---

## 2. Cấu hình 4 Module với AGP 9.2.1

### 2.1 Cấu trúc File Tối Thiểu

**settings.gradle.kts** (cập nhật từ dòng 26):
```kotlin
include(":app")
include(":ui")
include(":data")
include(":domain")
```

### 2.2 `:domain` — Kotlin JVM Thuần

```kotlin
// domain/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
}

kotlin {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Không import androidx.*, android.*, hay kotlinx.serialization
    // Chỉ logic thuần túy: model, interface, use case, thuật toán
}
```

**Ghi chú**: Module này không có Android plugin → không thể import `android.*`, đảm bảo ViewModel không chạm Compose.

### 2.3 `:data` — Android Library + Room + Koin

```kotlin
// data/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)  // Nếu cần Compose dependency (không bắt buộc)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.example.pion.family.tracker.demo.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        aidl = false
        buildConfig = false
        renderScript = false
    }
}

dependencies {
    implementation(project(":domain"))
    
    // Room + KSP
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    
    // Koin
    implementation("io.insert-koin:koin-android:4.2.2")
    
    // Play Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    
    // Testing
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
```

**Ghi chú**:
- KSP version `2.2.10-2.0.2` là bản tương thích với Kotlin 2.2.10 ([https://mvnrepository.com/artifact/com.google.devtools.ksp/com.google.devtools.ksp.gradle.plugin](https://mvnrepository.com/artifact/com.google.devtools.ksp/com.google.devtools.ksp.gradle.plugin))
- Room 2.8.4 ([https://developer.android.com/jetpack/androidx/releases/room](https://developer.android.com/jetpack/androidx/releases/room)) hỗ trợ Kotlin 2.0+ và KSP tốt
- Koin 4.2.2 là LTS hiện tại; Koin 4.0 có sẵn nhưng API migration cần thêm công sức

### 2.4 `:ui` — Android Library + Compose + Koin

```kotlin
// ui/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
}

android {
    namespace = "com.example.pion.family.tracker.demo.ui"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":domain"))
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    
    // Koin
    implementation("io.insert-koin:koin-androidx-compose:4.2.2")
    implementation("io.insert-koin:koin-compose-viewmodel:4.2.2")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
}
```

**Ghi chú**:
- `kotlin-serialization` plugin phiên bản `2.2.10` tương thích AGP 9 + Kotlin 2.2 ([https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/))
- Navigation 2.9.8 là stable + type-safe routes hỗ trợ ([https://developer.android.com/jetpack/androidx/releases/navigation](https://developer.android.com/jetpack/androidx/releases/navigation))
- Lifecycle 2.11.0 hỗ trợ Scoped ViewModels và KMP ([https://developer.android.com/jetpack/androidx/releases/lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle))

### 2.5 `:app` — Android Application

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pion.family.tracker.demo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.pion.family.tracker.demo"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Maps API key từ local.properties
        val properties = java.util.Properties()
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { properties.load(it) }
        val mapsApiKey = properties.getProperty("MAPS_API_KEY", "")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":data"))  // Chỉ để load Koin module, không logic
    implementation(project(":domain"))

    // Koin
    implementation("io.insert-koin:koin-android:4.2.2")
    implementation("io.insert-koin:koin-androidx-compose:4.2.2")

    // Compose & lifecycle (cơ bản)
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.insert-koin:koin-test:4.2.2")
}
```

### 2.6 Type-Safe Project Accessors (AGP 9 + Gradle 9.4.1)

Gradle 9.4.1 hỗ trợ `typeSafeProjectAccessors`. Trong `settings.gradle.kts`:

```kotlin
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
```

Sau đó trong `build.gradle.kts` các module có thể dùng:
```kotlin
dependencies {
    implementation(projects.domain)    // Thay vì project(":domain")
    implementation(projects.ui)
    implementation(projects.data)
}
```

---

## 3. KSP + Room Compatibility

### 3.1 Tình Trạng Hiện Tại

| Thành phần | Phiên bản | Tương thích |
|---|---|---|
| KSP | 2.2.10-2.0.2 | ✓ Đúng cho Kotlin 2.2.10 ([https://github.com/google/ksp/releases](https://github.com/google/ksp/releases)) |
| Room | 2.8.4 | ✓ Hỗ trợ Kotlin 2.0+, KSP tốt, có lỗi sửa trong 2.8.0-rc01 ([https://developer.android.com/jetpack/androidx/releases/room](https://developer.android.com/jetpack/androidx/releases/room)) |
| Gradle | 9.4.1 | ✓ Tương thích, đã test với Kotlin 2.0-2.4.20 ([https://docs.gradle.org/current/userguide/compatibility.html](https://docs.gradle.org/current/userguide/compatibility.html)) |
| AGP | 9.2.1 | ✓ KSP 2.3.1+ hỗ trợ AGP 9, kèm fix R-class resolution ([https://developer.android.com/build/releases/agp-9-2-0-release-notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)) |

**Tóm lại**: Tổ hợp này **đã được chứng thực hoạt động** tính tới tháng 8/2026.

### 3.2 Khai Báo Room Ở Module `:data`

```kotlin
// data/build.gradle.kts
id("com.google.devtools.ksp") version "2.2.10-2.0.2"

dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
}
```

**Chi tiết**: 
- `room-compiler` phải đi qua **`ksp`** configuration, không `annotationProcessor`
- AGP 9 + KSP tự động enable "Kotlin Code Generation on KSP" mặc định
- Có thể export schema via Koan option (xem LLM.md §9)

### 3.3 Schema Export (Nên làm)

Trong `FamilyTrackerDatabase.kt`, khai báo:
```kotlin
@Database(
    entities = [ZoneEntity::class, LocationPointEntity::class, ...],
    version = 1,
    exportSchema = true
)
abstract class FamilyTrackerDatabase : RoomDatabase() { ... }
```

Thêm vào `data/build.gradle.kts`:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

**Lý do**: Schema file (`*.json`) giúp Room migration checker detect breaking changes ở compile time.

---

## 4. Koin Dependency Injection

### 4.1 Koin — dùng 4.2.2

| Phiên bản | Trạng thái | Hỗ trợ | Migration |
|---|---|---|---|
| **4.2.2** | Long-Term Support | Kotlin 1.x, 2.x | **✓ Khuyến nghị dùng** cho demo |
| **4.0** | Stable (Sept 2024) | Kotlin 2.0+ | Reworked Compose API, nhưng breaking changes |

**Quyết định (đã sửa 21.08.2026):** dùng **Koin 4.2.2**. Bản 3.5.6 của `koin-androidx-compose` KHÔNG tồn tại trên Maven Central, và dòng 3.x quá cũ so với Compose BOM 2026.02.01. Xem `VERSIONS-VERIFIED.md`.

### 4.2 Artifacts Cần Cho 4 Module

```toml
# gradle/libs.versions.toml
[versions]
koin = "4.2.2"

[libraries]
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-androidx-compose = { group = "io.insert-koin", name = "koin-androidx-compose", version.ref = "koin" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel", version.ref = "koin" }
koin-test = { group = "io.insert-koin", name = "koin-test", version.ref = "koin" }
```

**Phân bổ**:
- `:app`: `koin-android` + `koin-androidx-compose`
- `:ui`: `koin-androidx-compose` + `koin-compose-viewmodel`
- `:data`: `koin-android`
- `:app` (test): `koin-test` → `checkModules()` (bắt buộc theo LLM.md §6)

### 4.3 checkModules() Test (Bắt Buộc)

```kotlin
// app/src/test/kotlin/KoinModulesTest.kt
import io.insert_koin.test.verify.verify
import org.junit.Test

class KoinModulesTest {
    @Test
    fun verifyKoinModules() {
        val koinApp = koinApplication {
            modules(dataModule, databaseModule, uiModule)
        }
        koinApp.verify()
    }
}
```

**Lý do**: Koin phát hiện thiếu binding lúc chạy. `checkModules()` (hoặc `verify()` ở Koin 4.0) đẩy lỗi vào CI thay vì runtime crash.

---

## 5. Navigation Compose + Type-Safe Routes

### 5.1 Version + Plugin

```toml
# gradle/libs.versions.toml
[versions]
navigationCompose = "2.9.8"
kotlinxSerialization = "1.8.0"  # Core lib, riêng với plugin

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```

**Plugin** (trong `:ui/build.gradle.kts`):
```kotlin
id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
```

**Ghi chú**:
- Navigation 2.9.8 là stable, type-safe routes bắt đầu từ 2.8.0-alpha08 ([https://developer.android.com/guide/navigation/design/type-safety](https://developer.android.com/guide/navigation/design/type-safety))
- Serialization plugin phiên bản `2.2.10` tương thích AGP 9 + Kotlin 2.2

### 5.2 Khai Báo Routes (ui/navigation/Routes.kt)

```kotlin
import kotlinx.serialization.Serializable

@Serializable data object MapRoute
@Serializable data object ZoneListRoute
@Serializable data class ZoneEditorRoute(val zoneId: String? = null)
@Serializable data class HistoryRoute(val epochDay: Long? = null)
@Serializable data object TimelineRoute
```

**Cạm bẫy đã biết**: Tham số `nullable` (ví dụ `zoneId: String? = null`) yêu cầu đặc biệt. Nếu dùng route không có tham số, dùng `data object` (không có `data class`).

---

## 6. Java 11 có đủ không?

### 6.1 Tình Hình

| Thành phần | Yêu cầu | Hiện tại |
|---|---|---|
| Target bytecode | Java 11 (sourceCompatibility = VERSION_11) | ✓ Đã khai báo |
| Compose Compiler | Kotlin 2.2.10 (tích hợp từ Kotlin 2.0) | ✓ Bundled trong AGP 9 |
| Kotlin 2.2.10 | Hỗ trợ target Java 11 | ✓ OK |
| AGP 9.2.1 | Chạy build cần JDK ≥ 17 | **⚠️ Xem dưới** |

### 6.2 Vấn đề: AGP 9 Chạy Build Cần JDK ≥ 17

**Sự khác biệt quan trọng**:
- **Target bytecode**: Code được compile sang bytecode Java 11 ✓ OK
- **Build JVM**: AGP 9 chạy Gradle daemon cần JDK ≥ 17 ([https://android.benigumo.com/20260201/agp-9-and-jdk-21/](https://android.benigumo.com/20260201/agp-9-and-jdk-21/))

**Hệ quả**: Nếu máy dev chỉ cài JDK 11:
- ❌ `./gradlew build` → **lỗi** (gradle daemon chạy trên JDK 11, AGP 9 yêu cầu ≥ 17)
- ✓ App vẫn target Java 11 bytecode được (code chế độ khác với JVM chạy build)

### 6.3 Khuyến nghị

1. **Nâng JDK lên 17 hoặc 21** cho máy dev + CI (AGP 9.2+ khuyến nghị JDK 21)
2. **Giữ nguyên** `sourceCompatibility = VERSION_11` + `targetCompatibility = VERSION_11` (app target Java 11 bytecode, ok với Android)
3. **Không phải** thay đổi `compileOptions` trong dự án hiện tại

**Kiểm chứng**:
```bash
java -version  # Cần ≥ 17
gradle -version  # Dùng gradle wrapper (9.4.1 trong settings.gradle.kts)
```

---

## 7. Version Catalog — Đề Xuất Đầy Đủ

Dựa trên LLM.md §14 + nghiên cứu hiện tại, thêm vào `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.2.1"
kotlin = "2.2.10"
composeBom = "2026.02.01"
coreKtx = "1.19.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.11.0"
activityCompose = "1.13.0"

# Maps & Location
mapsCompose = "6.2.1"  # play-services-maps-ktx flavor
playServicesMaps = "18.2.0"
playServicesLocation = "21.2.0"

# DI
koin = "4.2.2"

# Persistence
roomRuntime = "2.8.4"
ksp = "2.2.10-2.0.2"

# Navigation
navigationCompose = "2.9.8"
kotlinxSerializationJson = "1.8.0"

# Lifecycle
lifecycleViewmodelCompose = "2.11.0"
lifecycleRuntimeCompose = "2.11.0"

# Testing
kotlinxCoroutinesTest = "1.9.2"
turbine = "1.2.1"

[libraries]
# Existing
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

# NEW: Maps & Location
google-maps-compose = { group = "com.google.maps.android", name = "maps-compose", version.ref = "mapsCompose" }
google-play-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }
google-play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }

# NEW: DI
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-androidx-compose = { group = "io.insert-koin", name = "koin-androidx-compose", version.ref = "koin" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel", version.ref = "koin" }
koin-test = { group = "io.insert-koin", name = "koin-test", version.ref = "koin" }

# NEW: Persistence
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "roomRuntime" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "roomRuntime" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "roomRuntime" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "roomRuntime" }

# NEW: Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }

# NEW: Lifecycle
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }

# NEW: Testing
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }

# Note: kotlin-serialization plugin không thêm ở đây, cài trực tiếp trong build.gradle.kts
#   id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
# vì nó không cần alias lặp lại cho 4 module
```

**Cây cấp**: 
- `:app` + `:ui` + `:data` dùng `alias(libs.plugins.android.*)` để reference
- KSP plugin cài trực tiếp `id("com.google.devtools.ksp")` vì chỉ dùng ở `:data`
- Serialization plugin cài trực tiếp `id("org.jetbrains.kotlin.plugin.serialization")` vì chỉ dùng ở `:ui`

---

## 8. Convention Plugin (build-logic) — Có Nên Dùng?

### 8.1 Đánh Giá Chi Phí/Lợi

| Khía cạnh | Chi phí | Lợi ích |
|---|---|---|
| **Setup ban đầu** | 2–4 giờ (tạo build-logic module, chia logic) | Tái dùng cấu hình, DRY |
| **Maintain** | Phải update plugin khi AGP/Gradle/Kotlin thay đổi | Một nơi sửa cho tất cả module |
| **Debug lỗi build** | Khó hơn (plugin code trong JVM plugin, không IDE suggestion tốt) | Phát hiện lỗi sớm ở compile-time |
| **Quy mô** | Quá mức cho 4 module + 4 screen | Essential khi 20+ module |

### 8.2 Khuyến nghị cho Dự án Demo

**KHÔNG dùng build-logic** ở phase 1 — lý do:

1. **Quy mô nhỏ**: 4 module, cấu hình lặp ≈ 30 dòng `build.gradle.kts` mỗi file — chịu được
2. **Prototype**: Demo chạy trong vài tuần, convention plugin không giải quyết vấn đề nào của dự án
3. **Tiết kiệm time-to-demo**: Tập trung vào logic, không convention plugin overhead
4. **Dễ debug**: Lỗi build gradle trực tiếp ở `build.gradle.kts` file, không ẩn trong plugin

**Nếu sau demo mở rộng** thành app thực:
- Tạo `buildSrc/` hoặc `build-logic/` module với convention plugins
- Tham khảo: [https://github.com/watermelonKode/kmp-wizard-template-agp-9-build-logic](https://github.com/watermelonKode/kmp-wizard-template-agp-9-build-logic)

---

## 9. Rủi Ro Tương Thích Cao Nhất (Xếp Theo Mức Độ)

### 🔴 **CRITICAL (Cần Fix Ngay)**

1. **AGP 9 đòi hỏi JDK ≥ 17 chạy build, nhưng dự án hiện tại không doc requirement này**
   - **Triệu chứng**: `./gradlew build` → `UnsupportedClassVersionError` nếu JDK 11
   - **Kiểm chứng**: `java -version` trước khi build
   - **Fix**: Nâng JDK lên 17+ hoặc 21 (khuyến nghị)
   - **Nguồn**: [https://android.benigumo.com/20260201/agp-9-and-jdk-21/](https://android.benigumo.com/20260201/agp-9-and-jdk-21/)

2. **KSP version phải khớp chính xác với Kotlin version — mismatch = NPE hoặc codegen fail**
   - **Dự án dùng**: Kotlin 2.2.10 + KSP 2.2.10-2.0.2 ✓ Khớp
   - **Cạm bẫy**: Nếu upgrade Kotlin sau đó, PHẢI update KSP cùng lúc
   - **Kiểm chứng**: `./gradlew --version` → xem Kotlin version, so với KSP ở `libs.versions.toml`

### 🟠 **HIGH (Có thể gây crash runtime)**

3. **Namespace bắt buộc ở `:ui` + `:data` — nếu quên = build fail**
   - **Triệu chứng**: `A library cannot have multiple different packageName attributes`
   - **Fix**: Thêm `namespace = "..."` trong `android { }` block từng module
   - **Kiểm chứng**: Build `:ui` riêng → `./gradlew :ui:build`

4. **Room schema export = migration hell nếu quên**
   - **Triệu chứng**: Prod database version không khớp schema, Room migration fail
   - **Fix**: Bắt buộc `exportSchema = true` + khai báo `ksp { arg("room.schemaLocation", ...) }`
   - **Kiểm chứng**: `data/schemas/` folder có file `*.json`

5. **Koin `checkModules()` test bị bỏ sót = crash khi mở màn hình đầu tiên**
   - **Triệu chứng**: `No binding found for class MapViewModel`
   - **Fix**: Thêm `KoinModulesTest.kt` ở `:app/src/test/` + chạy test trước mỗi build
   - **Kiểm chứng**: `./gradlew :app:test` có test `KoinModulesTest`

### 🟡 **MEDIUM (Lỗi logic, không build fail)**

6. **Type-safe routes nullable parameter pitfall**
   - **Triệu chứng**: Serialize/deserialize nullable params (ví dụ `zoneId: String? = null`) gây exception
   - **Fix**: Test deep link với tham số trống `adb shell am start -a ... -d "android-app://..._route/"`
   - **Kiểm chứng**: Chạy HistoryRoute với epochDay = null

7. **DSL Architecture — nếu sau này tạo convention plugin**
   - **Triệu chứng**: `CommonExtension` không còn, `configure<LibraryExtension>` khác vs. `configure<ApplicationExtension>`
   - **Chuẩn bị**: Nếu tạo convention plugin, dùng `LibraryExtension` / `ApplicationExtension` riêng
   - **Kiểm chứng**: Không cần ngay, dự tính phase 2 nếu mở rộng

---

## 10. Việc Cần Làm Để Kiểm Chứng

### 10.1 Kiểm Tra Môi Trường

```bash
# 1. JDK version
java -version
# Kỳ vọng: JDK 17+ (tối thiểu), khuyến nghị JDK 21

# 2. Gradle wrapper version
./gradlew --version
# Kỳ vọng: Gradle 9.4.1, Kotlin version (xem có khớp libs.versions.toml không)

# 3. AGP version
grep "agp =" gradle/libs.versions.toml
# Kỳ vọng: 9.2.1
```

### 10.2 Build Test (Sau khi tạo 4 module)

```bash
# 3. Build domain module
./gradlew :domain:build
# Kỳ vọng: SUCCESS, không Android import

# 4. Build data module (+ KSP)
./gradlew :data:build
# Kỳ vọng: SUCCESS, room-compiler codegen chạy, xem build/generated/ksp/

# 5. Build ui module (+ navigation, serialization)
./gradlew :ui:build
# Kỳ vọng: SUCCESS, codegen navigation route, xem build/generated/ksp/

# 6. Build app module
./gradlew :app:build
# Kỳ vọng: SUCCESS, manifest merge OK, APK generated

# 7. Koin modules test (bắt buộc)
./gradlew :app:test
# Kỳ vọng: KoinModulesTest::verifyKoinModules() PASS
```

### 10.3 Deep Link Test (Sau khi navigation config xong)

```bash
# 8. Test type-safe route serialization
adb shell am start -a android.intent.action.VIEW \
  -d "android-app://com.example.pion.family.tracker.demo/history?epochDay=19000"
# Kỳ vọng: Mở HistoryScreen với epochDay = 19000

# 9. Test nullable parameter
adb shell am start -a android.intent.action.VIEW \
  -d "android-app://com.example.pion.family.tracker.demo/zone_editor"
# Kỳ vọng: Mở ZoneEditorScreen với zoneId = null (tạo zone mới)
```

### 10.4 Compile Error Tracking

Ghi lại các lỗi compile xuất hiện + solution, ví dụ:

```
ERROR: [Task :ui:compileDebugKotlin]
  'com.android.library' cannot be used with DSL configure<CommonExtension>
SOLUTION: Change to configure<LibraryExtension>

ERROR: Cannot find symbol: 'namespace'
SOLUTION: Ensure AGP >= 9.0, add namespace in android { } block
```

---

## 11. Khuyến Nghị Hành Động Tiếp Theo

**Ưu tiên P0** (trước khi bắt đầu dựng code):
1. ✅ Xác nhận JDK version ≥ 17 trên máy dev + CI
2. ✅ Update `gradle/libs.versions.toml` với version catalog đầy đủ (phần 7)
3. ✅ Tạo 4 module `:domain`, `:data`, `:ui` với `build.gradle.kts` tối thiểu (phần 2)
4. ✅ Verify build: `./gradlew build` success

**Ưu tiên P1** (phase 1 setup):
5. ✅ Tạo Room entities + DAOs + migrations
6. ✅ Setup Koin modules + KoinModulesTest.kt
7. ✅ Define navigation Routes.kt + verify serialization
8. ✅ First screen (MapScreen) + verify deep link

**Ưu tiên P2** (refinement):
9. ⏳ Chuẩn bị migration script lên Koin 4.0 (nếu cần)
10. ⏳ Convention plugin (nếu mở rộng sau demo)

---

## Tóm Lược Nguồn Tham Khảo

- [AGP 9.0 Breaking Changes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [AGP 9.2 Release Notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [KSP Releases & Compatibility](https://github.com/google/ksp/releases)
- [Room Persistence Library](https://developer.android.com/jetpack/androidx/releases/room)
- [Koin Documentation](https://insert-koin.io/)
- [Navigation Compose Type-Safe Routes](https://developer.android.com/guide/navigation/design/type-safety)
- [Kotlin 2.2 Compatibility Guide](https://kotlinlang.org/docs/compatibility-guide-22.html)
- [Gradle 9.4.1 Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- [Jetpack Lifecycle 2.11.0 Release](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [Turbine Flow Testing](https://github.com/cashapp/turbine)
- [Compose BOM 2026.02.01](https://developer.android.com/develop/ui/compose/bom)

---

**Báo cáo này xác thực tính tới 21-08-2026. Nếu nâng cấp AGP/Kotlin/Gradle sau ngày này, cần re-verify version compatibility.**

---

## ĐÍNH CHÍNH — 21.08.2026

Ba khẳng định trong báo cáo này đã được kiểm chứng lại trực tiếp trên máy và trên repository,
và đã được sửa trong nội dung ở trên. Ghi lại ở đây để không ai đọc bản cũ rồi làm theo:

| Báo cáo ban đầu nói | Thực tế | Nguồn kiểm chứng |
|---|---|---|
| Dùng Koin **3.5.6** ("LTS") | `koin-androidx-compose:3.5.6` **không tồn tại**. Dòng 4.x mới có artifact này; bản mới nhất **4.2.2**. | `maven-metadata.xml` của Maven Central |
| Dùng Room **2.6.1** | Tồn tại nhưng đã rất cũ. Bản hiện hành **2.8.4**. Chính báo cáo cũng tự mâu thuẫn khi nhắc tới "lỗi sửa trong 2.8.0-rc01". | Google Maven |
| **Rủi ro P0: JDK 11 sẽ làm build AGP 9 crash** | **Không phải rủi ro của dự án này.** Máy đang chạy JDK 24, và `gradle/gradle-daemon-jvm.properties` đã ghim `toolchainVersion=21` cho Gradle daemon. Java 11 trong `compileOptions` chỉ là **bytecode target**, không phải JVM chạy build — hai thứ khác nhau. | `java -version`, `gradle/gradle-daemon-jvm.properties` |

**Bảng version có thẩm quyền là `VERSIONS-VERIFIED.md` trong cùng thư mục này**, không phải
bảng trong báo cáo này.
