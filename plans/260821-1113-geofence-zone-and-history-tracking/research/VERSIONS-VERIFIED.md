# Version đã kiểm chứng trực tiếp từ repository — 21.08.2026

> Bảng này **thắng** mọi version nêu trong các báo cáo researcher. Mỗi dòng dưới đây được
> đọc từ `maven-metadata.xml` của chính repository phục vụ artifact đó, không phải từ trí nhớ
> của model hay từ blog.
>
> Lý do bảng này tồn tại: báo cáo `researcher-02` ban đầu ghi `maps-compose 8.5.0` — **một
> version không tồn tại**. Nếu đi thẳng vào `libs.versions.toml`, phase-01 sẽ chết ở bước
> resolve dependency và mất thời gian truy ngược.

## CẬP NHẬT sau build thật ở phase-01 (21.08.2026)

`maps-compose 8.4.0` **build fail thật** — không dùng được, dù tồn tại trên Maven Central.
POM của nó khai `kotlin-stdlib 2.4.10`; compiler dự án ở Kotlin `2.2.10` chỉ đọc được binary
metadata tới `2.3.0`. Lỗi thật:

```
e: Incompatible classes were found in dependencies. Remove them from the classpath or use
   '-Xskip-metadata-version-check' to suppress errors
e: .../maps-compose-8.4.0-api.jar!/META-INF/maps-compose.kotlin_module Module was compiled
   with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0,
   expected version is 2.2.0.
```

**Dùng `maps-compose 8.3.1` thay thế** (POM khai `kotlin-stdlib 2.3.21` — đọc được).
Cái giá: `8.3.1` tự mang `dependencyManagement` ép `androidx.compose:compose-bom` lên
`2026.03.00`, cao hơn `2026.02.01` khai trong `libs.versions.toml`. Toàn cây Compose vẫn
thống nhất một version sau khi Gradle giải quyết xung đột (`./gradlew :ui:dependencies` không
có version lẫn lộn giữa các artifact `compose-*`) — build xanh, `assembleRelease` cài được,
app mở lên không crash. Ghi chi tiết ở `LLM.md` §13 Open #2.

## Đã xác nhận tồn tại

| Artifact | Version | Repo | Ghi chú |
|---|---|---|---|
| `com.google.maps.android:maps-compose` | ~~8.4.0~~ → **8.3.1** | Maven Central | `8.4.0` build fail — xem mục CẬP NHẬT ở trên. `9.0.0-rc01` tồn tại nhưng là pre-release — không dùng. |
| `com.google.maps.android:maps-compose-utils` | ~~8.4.0~~ → **8.3.1** | Maven Central | Cần cho simplify polyline (Douglas-Peucker). Khoá cùng version với `maps-compose`. |
| `com.google.android.gms:play-services-maps` | **20.0.0** | Google Maven | |
| `com.google.android.gms:play-services-location` | **21.4.0** | Google Maven | FusedLocation + Geofencing cùng artifact này. |
| `androidx.room:room-runtime` / `room-ktx` / `room-compiler` | **2.8.4** | Google Maven | |
| `androidx.navigation:navigation-compose` | **2.9.8** | Google Maven | Stable. `2.10.0-rc01` tồn tại — không dùng. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | **2.11.0** | Google Maven | Khớp `lifecycleRuntimeKtx = 2.11.0` đã có trong catalog. |
| `io.insert-koin:koin-androidx-compose` (+ `koin-android`, `koin-test`) | **4.2.2** | Maven Central | |
| `com.google.devtools.ksp` | **2.2.10-2.0.2** | Maven Central | **Bị khoá cứng theo Kotlin 2.2.10.** Đổi Kotlin là phải đổi dòng này. |
| `org.jetbrains.kotlin:kotlin-serialization` (plugin) | **2.2.10** | Maven Central | Bắt buộc cho type-safe route của navigation-compose. |
| `app.cash.turbine:turbine` | **1.2.1** | Maven Central | |

## Đã xác minh bằng build thật ở phase-01 (kết quả)

| Câu hỏi | Kết quả |
|---|---|
| `maps-compose` có thật sự chạy với Compose BOM 2026.02.01 không? | **Không với 8.4.0** (build fail). **Có với 8.3.1**, nhưng ép BOM thật lên 2026.03.00 — xem mục CẬP NHẬT ở trên. |
| `maps-compose 8.3.1` kéo theo `play-services-maps` version nào? | Vẫn đúng `20.0.0` như khai trong catalog — không bị ép. |
| Room 2.8.4 có chạy với Java 11 target không, hay bắt buộc 17? | Chạy được. `:data:assembleRelease` xanh với `sourceCompatibility/targetCompatibility = VERSION_11`. |
| AGP 9.2.1 có còn hỗ trợ cú pháp `build.gradle.kts` của module library như tài liệu hiện hành không? | Có, nhưng **KSP 2.2.10-2.0.2 xung đột với "built-in Kotlin" của AGP 9** — cần `android.disallowKotlinSourceSets=false` trong `gradle.properties`. Xem `LLM.md` §13 Fixed #1. |
| `kotlinx-coroutines-core` / `kotlinx-coroutines-test` — version thật? | **1.10.2** (cùng version cho cả hai, xác nhận bằng `./gradlew :domain:dependencies --configuration testCompileClasspath`). |
| `kotlinx-serialization-json` — version thật? | **1.9.0**, xác nhận bằng `./gradlew :ui:dependencies --configuration releaseRuntimeClasspath`. |

**Luật cho phase-01 (đã áp dụng):** thêm dependency theo **từng nhóm một** rồi build. Cách này
đã bắt được lỗi maps-compose 8.4.0 và lỗi KSP/built-in-Kotlin ngay ở nhóm gây ra, thay vì lẫn
trong một trang lỗi resolve của cả 10+ thư viện.
