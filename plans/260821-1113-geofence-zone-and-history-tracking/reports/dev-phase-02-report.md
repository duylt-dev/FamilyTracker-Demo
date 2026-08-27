# Dev Report — Phase 02: Domain model, Room schema, repository, dữ liệu giả

Ngày: 2026-08-21 · Status: **completed**. Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`.

## Tóm tắt

5 model `:domain/model/`, 5 interface `:domain/repository/`, 4 entity + `Converters` + 4 DAO +
4 mapper + 4 `*RepositoryImpl` ở `:data`, `DemoDataSeeder`, `PurgeOldHistoryUseCase`, Koin wiring
đầy đủ (`DatabaseModule` + `DataModule`), 9 androidTest chạy thật trên `emulator-5554`. Schema
export ra đúng **4 bảng**, không `track_sessions`.

## Lược đồ Room thật (từ `data/schemas/.../1.json`)

| Bảng | Cột | Index | Foreign key |
|---|---|---|---|
| `zones` | id(TEXT,PK), name, latitude, longitude, radiusMeters, colorArgb, notifyOnEnter, notifyOnExit, createdAt(INTEGER) | — | không |
| `location_points` | id(TEXT,PK), memberId, latitude, longitude, accuracyMeters, speedMps, bearingDegrees, recordedAt(INTEGER) | `index_location_points_memberId_recordedAt` | không |
| `zone_events` | id(TEXT,PK), zoneId, zoneName, memberId, type(TEXT), occurredAt(INTEGER), latitude, longitude, source(TEXT) | `index_zone_events_occurredAt` | không |
| `members` | id(TEXT,PK), name, colorArgb, isSelf(INTEGER) | — | không |

Không FK nào — có chủ ý: `zone_events.zoneName` là bản sao (Key Insight #3), không join sang
`zones`, nên xoá zone không kéo theo xoá lịch sử Timeline.

```
$ python3 -c "import json,glob;f=sorted(glob.glob('data/schemas/**/*.json',recursive=True))[-1];d=json.load(open(f));print(f);print('tables:',[t['tableName'] for t in d['database']['entities']])"
data/schemas/com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase/1.json
tables: ['zones', 'location_points', 'zone_events', 'members']
```
Đúng 4 bảng, đúng tên, khớp `LLM.md` §9.

## Seed data

`DemoDataSeeder.seedIfEmpty()`: chạy khi `members` rỗng → 1 member `isSelf=true` tên "Tôi"
(`#1B6EF3`) + 2 member giả "Minh" (`#E5820C`), "Lan" (`#7B3FF2`) — đúng PRD §5.2. Mỗi member giả
được ghi 1 `LocationPointEntity` quanh toạ độ trung tâm TP.HCM (10.7769, 106.7009) ± 0.01°.
Verify thật trên thiết bị (kéo `.db`+`-wal`+`-shm` khỏi app debuggable, mở bằng `sqlite3` local):
```
Tôi|1
Minh|0
Lan|0
-- location_points count: 2
```

## File đã tạo

**`:domain`** (13 file, 235 dòng):
`model/{Zone,LocationPoint,ZoneEvent,TrackSession,Member}.kt` (ZoneEvent.kt gồm cả
`ZoneEventType`/`EventSource`) · `repository/{ZoneRepository,TrackingRepository,
ZoneEventRepository,MemberRepository,LocationSource}.kt` · `tracking/TrackingConstants.kt`
(chỉ `HISTORY_RETENTION_DAYS` — phase-03 bổ sung phần còn lại) · `usecase/PurgeOldHistoryUseCase.kt`

**`:data`** (20 file, 756 dòng):
`local/Converters.kt`, `local/FamilyTrackerDatabase.kt` ·
`local/entity/{Zone,LocationPoint,ZoneEvent,Member}Entity.kt` ·
`local/dao/{Zone,LocationPoint,ZoneEvent,Member}Dao.kt` ·
`local/mapper/{Zone,LocationPoint,ZoneEvent,Member}Mapper.kt` ·
`repository/RouteSessionAssembler.kt` (helper tạm, xem Sai lệch #2) ·
`repository/{Zone,Tracking,ZoneEvent,Member}RepositoryImpl.kt` ·
`seed/DemoDataSeeder.kt` · `di/DatabaseModule.kt`

**androidTest `:data`** (3 file, 9 test case):
`local/dao/ZoneDaoTest.kt` (4) · `local/dao/LocationPointDaoTest.kt` (3) ·
`repository/ZoneEventDedupeTest.kt` (2 — luật 60s: cách 30s dedupe còn 1 dòng, cách 90s giữ 2 dòng)

## File đã sửa

- `domain/build.gradle.kts` — `implementation(coroutines-core)` → `api(...)` (Sai lệch #1)
- `data/build.gradle.kts` — thêm `androidTestImplementation(libs.androidx.test.runner)` (Sai lệch #4)
- `data/src/main/java/.../data/di/DataModule.kt` — điền 4 repository binding + seeder + use case
- `gradle/libs.versions.toml` — thêm `androidxTestRunner = "1.7.0"` + `androidx-test-runner`
- `app/src/main/java/.../FamilyTrackerApp.kt` — `KoinComponent`, inject seeder + purge use case,
  chạy trong `applicationScope` lúc `onCreate`, log `FTD_EVENT purge_completed`
- `app/src/main/AndroidManifest.xml` — `allowBackup="true"` → `"false"` (PRD §7.3, Security
  Considerations "ngay ở phase này")
- `app/src/test/java/.../KoinModulesTest.kt` — `verifyAll()` → `module { includes(...) }.verify()`
  (Sai lệch #3)
- `LLM.md` — §13 thêm 2 Open (#3 chữ ký repository lệch PRD §8, #4 `RouteSessionAssembler` tạm) +
  2 Fixed (#3 `verifyAll()` không gộp module, #4 thiếu `androidx.test:runner`); §14 thêm dòng
  `androidx.test:runner`
- `plans/.../phase-02-....md` — Status → completed, tick 12/12 Todo
- `plans/.../plan.md` — dòng phase 02 → completed

Không xoá file nào.

## Sai lệch so với file phase (đầy đủ, có lý do)

### 1. `domain/build.gradle.kts`: `implementation` → `api(kotlinx-coroutines-core)`

Không nằm trong "Related Code Files" của phase-02 nhưng bắt buộc: mọi interface repository ở
`:domain` giờ trả `Flow<...>` (phần PRD §8 công khai). Với `implementation`, `:data` compile
`ZoneRepositoryImpl : ZoneRepository { override fun observeAll(): Flow<...> }` sẽ không thấy lớp
`Flow` (implementation không lộ transitive lên classpath compile của module tiêu thụ). Xác nhận
qua build thật trước/sau đổi — `:domain:compileKotlin` xanh cả hai, nhưng `:data:compileDebugKotlin`
chỉ xanh sau khi đổi.

### 2. `TrackingRepository.purgeOlderThan` trả `Int`; `ZoneEventRepository` thêm `purgeOlderThan`

PRD §8 chép `purgeOlderThan(days: Int)` không giá trị trả về, và `ZoneEventRepository` chỉ có
`observeTimeline`/`record`. Nhưng phase-02 Requirements đòi xoá **cả hai** bảng theo 7 ngày, và
Success Criteria đòi log `deletedPoints=… deletedEvents=…` — không thể làm được với `Unit` và
không có method xoá `zone_events`. Coi đây là lỗ hổng trong PRD §8 (không sửa PRD — tài liệu do
BA giữ, có version history), chỉ ghi lệch ở `LLM.md` §13 Open #3. Không thêm method nào khác
ngoài cái này.

### 3. `RouteSessionAssembler` (`:data/repository/`) lặp tạm logic gom chuyến + khoảng cách

Phase-02 Key Insight #2 giao thuật toán gom chuyến (`RouteSplitter`) cho phase-03, nhưng
`TrackingRepositoryImpl.observeRoute()` cần chạy thật ngay bây giờ (không mock/giả — luật
`development-rules.md`). Viết một helper nội bộ `internal object RouteSessionAssembler` trong
`:data` (gom theo `SESSION_GAP_MS=300_000` + haversine), rõ ràng đánh dấu "xoá khi phase-03 tạo
`RouteSplitter`/`RouteStats` thật, đừng mở rộng". Tương tự, `EVENT_DEDUPE_WINDOW_MS=60_000` bị
hardcode cục bộ trong `ZoneEventRepositoryImpl` — đúng theo Key Insight #4 (quyết định dedupe
tách thành `ZoneEventDeduper` ở phase-03, impl hiện tại tự đọc-so sánh).

### 4. `KoinModulesTest`: `verifyAll()` → `module { includes(...) }.verify()`

`listOf(dataModule, databaseModule, uiModule).verifyAll()` (theo đúng gợi ý ban đầu, mở rộng từ
phase-01) **fail** với `MissingKoinDefinitionException` cho `ZoneDao` dù binding tồn tại ở
`databaseModule`. Đọc source `koin-test-jvm-4.2.2` (`VerifyModule.kt`): `verifyAll()` chỉ gọi
`module.verify()` cho **từng module riêng lẻ** (`forEach`), và `Verification`'s definition index
= `module.includedModules + module` (`Verification.kt:38`) — không gộp các module khác trong
cùng `List`. Phase-01 pass vì cả hai module khi đó rỗng, không lộ ra bug. Sửa bằng module bọc
`includes(...)`, xem file đã sửa ở trên. Bằng chứng lỗi gốc: xem log dưới "Bước xác minh".

### 5. `gradle/libs.versions.toml` + `data/build.gradle.kts`: thêm `androidx.test:runner`

`:data:connectedDebugAndroidTest` crash tức thì `ClassNotFoundException:
androidx.test.runner.AndroidJUnitRunner` — `androidx.test.ext:junit` không tự kéo theo
`androidx.test:runner`. Đây là lỗ hổng catalog từ phase-01 (khai `testInstrumentationRunner`
nhưng chưa từng chạy androidTest thật để lộ ra). Thêm `androidxTestRunner = "1.7.0"` (khớp
`androidx.test:core` 1.7.0 đã có trong cache) + dependency. 9/9 test pass sau khi sửa.

### 6. `android:allowBackup` sửa ở phase-02, không đợi phase-11

Phase-02's Security Considerations tự nói "ngay ở phase này" — làm luôn, không hoãn.

## Output thật — chạy đầy đủ theo "Định nghĩa xong"

```
$ ./gradlew :data:assembleRelease
BUILD SUCCESSFUL

$ ./gradlew test
BUILD SUCCESSFUL — :app:test (KoinModulesTest pass), :ui:test (1/1 pass), :data:test (NO-SOURCE
— chưa có JVM unit test cho :data ở phase này, đúng phạm vi), :domain:test (NO-SOURCE)

$ ./gradlew clean
BUILD SUCCESSFUL

$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 19s
$ grep -ci "warning:" <log>   → 1
$ grep -ci "warning:" reports/baseline-build-debug.log   → 1   (không tăng — G6 pass)

$ ./gradlew assembleRelease
BUILD SUCCESSFUL in 28s

$ ls data/schemas/**/*.json
data/schemas/com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase/1.json

$ python3 -c "import json,glob;f=sorted(glob.glob('data/schemas/**/*.json',recursive=True))[-1];d=json.load(open(f));print(f);print('tables:',[t['tableName'] for t in d['database']['entities']])"
tables: ['zones', 'location_points', 'zone_events', 'members']

$ ./gradlew :data:connectedDebugAndroidTest
Starting 9 tests on Pixel_10_Pro_XL(AVD) - 17
Finished 9 tests on Pixel_10_Pro_XL(AVD) - 17
BUILD SUCCESSFUL in 8s
```

9 test case: `ZoneDaoTest` (4: upsert+observe, update-in-place, delete, count) ·
`LocationPointDaoTest` (3: observeBetween theo memberId+ngày, latestPerMember, deleteOlderThan) ·
`ZoneEventDedupeTest` (2: 30s→dedupe còn 1, 90s→giữ 2).

### Cài đặt release APK + logcat thật

```
$ adb -s emulator-5554 uninstall com.example.pion.family.tracker.demo   # cài mới hoàn toàn
$ adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
Success
$ adb -s emulator-5554 shell am start -n com.example.pion.family.tracker.demo/.MainActivity
$ adb -s emulator-5554 logcat -d -s FTD_EVENT
08-21 15:00:06.026 10744 10761 D FTD_EVENT: purge_completed deletedPoints=0 deletedEvents=0
$ adb -s emulator-5554 logcat -d | grep -iE "FATAL EXCEPTION|AndroidRuntime: FATAL"
(rỗng — không crash)
```
Đúng một dòng `purge_completed`, `deletedPoints=0 deletedEvents=0` hợp lý vì DB vừa tạo mới
(không có gì cũ hơn 7 ngày để xoá). Xác nhận riêng qua debug build (debuggable, `run-as` được)
rằng `DemoDataSeeder` chạy đúng: `Tôi|1`, `Minh|0`, `Lan|0`, 2 `location_points`.

### Boundary / security checks

```
$ grep -rn "Entity" ui/src            → rỗng (entity không rời :data)
$ grep -rn "import android" domain/src → rỗng (:domain sạch)
$ grep -rn "AIza" --include='*.kts' --include='*.toml' --include='*.xml' --include='*.kt' .  → rỗng
$ grep -n "allowBackup" app/src/main/AndroidManifest.xml → android:allowBackup="false"
```

## Tests Status

- Type check / compile: **pass** (`:data:assembleRelease`, `assembleDebug`, `assembleRelease` toàn dự án)
- Unit tests: **pass** — `KoinModulesTest` xanh (sau khi sửa cách gọi `verify()`), `:ui` 1/1 pass
- Instrumented tests: **pass** — 9/9 trên `emulator-5554` (`:data:connectedDebugAndroidTest`)
- G6 (warning không tăng): **pass** — 1 warning cả hai lần, cùng dòng `disallowKotlinSourceSets`

## Việc còn dở / chưa làm — nói thẳng

- `RouteSessionAssembler` và 2 hằng số hardcode (`SESSION_GAP_MS`, `EVENT_DEDUPE_WINDOW_MS`) là
  nợ kỹ thuật **có chủ ý**, phải dọn ở phase-03 khi `RouteSplitter`/`RouteStats`/`ZoneEventDeduper`
  thật xuất hiện ở `:domain/tracking/`. Đã ghi rõ trong code (KDoc) và `LLM.md` §13 Open #4.
- Không có JVM unit test nào cho `:domain`/`:data` ở phase này (`test` task NO-SOURCE cho cả
  hai) — đúng phạm vi phase-02 (logic thuật toán cần test JUnit thuộc phase-03); phase-02 chỉ có
  CRUD/DAO nên được phủ bằng androidTest, không unit test JVM giả lập Room.
- `MAX_ZONES=100` chưa được enforce ở đâu (chỉ có `ZoneRepository.count()` sẵn sàng) — thuộc use
  case `SaveZoneUseCase` ở phase-06, đúng theo Requirements của phase-02 ("Kết thúc phase chưa
  có màn hình nào đọc dữ liệu này").
- Chưa pin cứng version `androidx.test:runner` 1.7.0 có tương thích lâu dài với
  `androidx.test.ext:junit 1.3.0`/`espresso 3.7.0` hay không ngoài việc build+test đã xanh thật —
  để `VERSIONS-VERIFIED.md` cập nhật nếu phase sau phát hiện xung đột.

## Docs impact

**Major.** `LLM.md` §13 thêm 2 Open + 2 Fixed, §14 thêm 1 dòng version. `plan.md` và phase-02 file
cập nhật trạng thái + todo. Không sửa PRD (deviation #2 ở trên là lệch so với PRD, nhưng PRD là
tài liệu BA-owned với version history riêng — không tự sửa).

Không có câu hỏi còn treo.
