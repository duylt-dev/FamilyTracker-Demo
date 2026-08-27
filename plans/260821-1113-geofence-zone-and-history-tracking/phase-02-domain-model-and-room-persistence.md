# Phase 02 — Domain model, Room schema, repository, dữ liệu giả

## Context Links

- [`plan.md`](plan.md) · [`phase-01`](phase-01-module-skeleton-and-version-catalog.md)
- [`LLM.md`](../../LLM.md) §3 (`:domain`, `:data`), §6 (Koin), §9 (lược đồ Room), §11 (test), §12
- PRD §8 Internal Contracts · §9 Data Models · §3.3 F3 · §6 Configuration & Constants
- [`research/researcher-03-multimodule-toolchain.md`](research/researcher-03-multimodule-toolchain.md) §3.3 (schema export)

## Overview

| | |
|---|---|
| Priority | **P0** |
| Status | completed |
| Effort | 6h |
| Story ánh xạ | Nền cho F1/F3/F4 — không hoàn thành story nào một mình; phục vụ US-07, US-12, US-27, US-34 |

Dựng toàn bộ tầng dữ liệu: model bất biến ở `:domain`, 4 interface repository, entity + DAO +
mapper + implementation ở `:data`, `DemoDataSeeder` sinh 2 thành viên giả, và luật xoá dữ liệu
quá 7 ngày. Kết thúc phase chưa có màn hình nào đọc dữ liệu này.

## Key Insights

1. **`Member` trong PRD §9 không có toạ độ, nhưng US-08 yêu cầu marker thành viên kèm "thời điểm
   cập nhật gần nhất".** Không thêm cột vào `members`: `DemoDataSeeder` ghi vài
   `LocationPointEntity` cho mỗi member giả, và `MemberRepository` trả vị trí mới nhất bằng một
   truy vấn `MAX(recordedAt)` theo `memberId`. Index `(memberId, recordedAt)` ở `LLM.md` §9 vốn
   đã tồn tại cho đúng truy vấn này.
2. **Không có bảng `track_sessions` — đã chốt** (PRD v1.2 §9, `LLM.md` §9, cả hai đã sửa).
   `TrackSession` vẫn tồn tại như **model của `:domain`, suy ra lúc đọc**: gom các điểm liên tiếp
   cách nhau dưới `SESSION_GAP_MS`. Không `TrackSessionEntity`, không DAO, không bảng.
   Lý do: lưu thành bảng buộc foreground service phải quyết định *khi nào* một chuyến kết thúc,
   phải back-fill khi hệ thống kill app giữa chuyến, và phải hoà giải khi bảng lệch với
   `location_points` — ba việc khó cho một thứ tính được bằng một câu query.
   Thuật toán gom (`RouteSplitter`) nằm ở [phase-03](phase-03-domain-tracking-algorithms.md).
3. **`zone_events` lưu `zoneName` dạng sao chép, không join.** PRD §9 đã khai báo `zoneName` trong
   model. Xoá một zone không được xoá lịch sử Timeline của nó — nếu join sang `zones`, mọi dòng
   Timeline của zone vừa xoá biến mất, và người demo sẽ thấy nhật ký tự nhiên rỗng đi.
4. **Luật khử trùng lặp 60 giây nằm ở `ZoneEventRepositoryImpl.record()` và chỉ ở đó** (PRD §8,
   `LLM.md` §8.1). Nhưng *quyết định* trùng hay không được tách thành hàm thuần
   `ZoneEventDeduper` ở `:domain/tracking/` (phase-03) để test bằng JUnit — impl chỉ đọc event
   gần nhất cùng khoá rồi hỏi hàm đó. Đây là **sai lệch có chủ ý so với `LLM.md` Phụ lục A.1**;
   ghi lại trong cùng commit.
5. **`fallbackToDestructiveMigration()` ở giai đoạn demo** (PRD §7.4) nhưng vẫn `exportSchema = true`
   + `room.schemaLocation`. Schema JSON là thứ duy nhất cho biết bản demo đang chạy schema nào khi
   dữ liệu bỗng dưng mất sạch.
6. **Mọi ngưỡng nằm trong đúng một file** `:domain/tracking/TrackingConstants.kt` (PRD §6) để QA đọc
   được mà không mở từng chỗ. File này tạo ở phase-03; phase-02 chỉ dùng `HISTORY_RETENTION_DAYS`
   nên tạo trước hằng số đó nếu cần.
7. **Entity không bao giờ rời khỏi `:data`.** Mapper một file mỗi cặp. Giá của việc trả thẳng entity
   lên trên: một lần đổi tên cột lan tới tận Composable (`LLM.md` §9).

## Requirements

**Chức năng**
- 5 model ở `:domain/model/` đúng chữ ký PRD §9: `Zone`, `LocationPoint`, `ZoneEvent`,
  `TrackSession`, `Member` (+ `ZoneEventType`, `EventSource`).
- 4 interface repository đúng chữ ký PRD §8 + `LocationSource`.
- 4 bảng Room: `members`, `zones`, `location_points`, `zone_events`.
- `DemoDataSeeder`: 1 member `isSelf = true` + 2 member giả kèm điểm vị trí gần đây.
- `PurgeOldHistoryUseCase` xoá `location_points` và `zone_events` cũ hơn 7 ngày.
- `ZoneRepository.count()` để chặn ở 100 zone trước khi gọi Play Services.

**Phi chức năng**
- Kích thước DB sau 7 ngày < 20 MB (PRD §7.1) — lưu `Instant` dạng `Long` epoch millis, không chuỗi.
- Truy vấn lộ trình một ngày phải dùng index `(memberId, recordedAt)`, không quét bảng.

## Architecture

```
:ui  ──(chỉ thấy interface)──▶  :domain/repository/*.kt
                                        ▲ implement
:data/repository/*RepositoryImpl ───────┘
        │ dùng
        ▼
:data/local/dao/*Dao ──▶ Room ──▶ :data/local/entity/*Entity
        │
        └─▶ :data/local/mapper/*  (Entity ⇄ model :domain)
```

Chiều duy nhất: `:data` biết `:domain`; `:domain` không biết gì. `ZoneDao` không tồn tại đối với
`:ui` vì Gradle không cho `:ui` nhìn thấy `:data`.

## Related Code Files

**Tạo — `:domain`**
- `model/`: `Zone.kt`, `LocationPoint.kt`, `ZoneEvent.kt` (+ `ZoneEventType`, `EventSource`), `TrackSession.kt`, `Member.kt`
- `repository/`: `ZoneRepository.kt`, `TrackingRepository.kt`, `ZoneEventRepository.kt`, `MemberRepository.kt`, `LocationSource.kt`
- `usecase/PurgeOldHistoryUseCase.kt`

**Tạo — `:data`**
- `local/FamilyTrackerDatabase.kt`, `local/Converters.kt`
- `local/entity/`: `ZoneEntity.kt`, `LocationPointEntity.kt`, `ZoneEventEntity.kt`, `MemberEntity.kt`
- `local/dao/`: `ZoneDao.kt`, `LocationPointDao.kt`, `ZoneEventDao.kt`, `MemberDao.kt`
- `local/mapper/`: `ZoneMapper.kt`, `LocationPointMapper.kt`, `ZoneEventMapper.kt`, `MemberMapper.kt`
- `repository/`: `ZoneRepositoryImpl.kt`, `TrackingRepositoryImpl.kt`, `ZoneEventRepositoryImpl.kt`, `MemberRepositoryImpl.kt`
- `seed/DemoDataSeeder.kt`
- `di/DatabaseModule.kt`, `di/DataModule.kt` (điền vào file rỗng của phase-01)
- `src/androidTest/.../ZoneDaoTest.kt`, `LocationPointDaoTest.kt`, `ZoneEventDedupeTest.kt`

**Sửa**
- `LLM.md` §3 — thêm `ZoneEventDeduper` khi phase-03 tạo. **§9 và PRD §9 đã phản ánh quyết định bỏ `track_sessions`; không sửa tài liệu nữa, chỉ cần code khớp với chúng.**
- `app/FamilyTrackerApp.kt` — gọi `PurgeOldHistoryUseCase` lúc khởi động (PRD §3.3)

## Implementation Steps

1. Viết 5 data class ở `:domain/model/` **đúng nguyên văn PRD §9**, tất cả `val`, `Instant` là
   `java.time.Instant`. Thêm `AppResult`/`AppError` đã có từ phase-01 vào chữ ký repository.
2. Viết 5 interface ở `:domain/repository/` **đúng nguyên văn PRD §8**. Không thêm method nào —
   mỗi method thừa là một thứ `:ui` được phép gọi mà chưa ai cần.
3. Viết entity + `Converters` (`Instant` ⇄ `Long`, `ZoneEventType`/`EventSource` ⇄ `String`).
   Khai báo index: `location_points(memberId, recordedAt)`, `zone_events(occurredAt)`.
4. Viết DAO. Riêng `LocationPointDao` cần:
   - `observeBetween(memberId, fromMillis, toMillis): Flow<List<LocationPointEntity>>` (theo ngày)
   - `latestPerMember(): Flow<List<LocationPointEntity>>` (cho marker thành viên — US-08)
   - `deleteOlderThan(millis)`
5. Viết `FamilyTrackerDatabase` với `exportSchema = true`, `version = 1`,
   `fallbackToDestructiveMigration()`. Build để KSP sinh code, kiểm tra `data/schemas/*.json` xuất hiện.
6. Viết mapper (một file mỗi cặp) và 4 `*RepositoryImpl`.
   `ZoneEventRepositoryImpl.record()` là **nơi duy nhất** áp luật khử trùng lặp: đọc event gần nhất
   cùng `(zoneId, memberId, type)`, so `EVENT_DEDUPE_WINDOW_MS`, bỏ qua nếu < 60 000ms và ghi log
   `FTD_EVENT zone_event_deduped` (PRD §10).
7. Viết `DemoDataSeeder`: chạy một lần khi bảng `members` rỗng. Tạo `Member(isSelf = true)` màu
   `#1B6EF3` và 2 member giả màu `#E5820C`, `#7B3FF2` (PRD §5.2), kèm 1–2 `LocationPointEntity`
   gần thời điểm hiện tại cho mỗi member giả.
8. Viết `PurgeOldHistoryUseCase(days = HISTORY_RETENTION_DAYS)`; gọi từ `FamilyTrackerApp.onCreate`
   trong một coroutine, log `FTD_EVENT purge_completed deletedPoints=… deletedEvents=…`.
9. Đăng ký tất cả vào `DatabaseModule` + `DataModule` (`singleOf(::XRepositoryImpl) bind XRepository::class`).
10. Viết androidTest với in-memory database: CRUD zone, truy vấn theo ngày, và **một test riêng cho
    luật 60 giây** (ghi 2 event cùng khoá cách 30s → chỉ 1 dòng; cách 90s → 2 dòng).
11. Đối chiếu schema JSON vừa export với `LLM.md` §9: đúng **4 bảng**, không có `track_sessions`.
    Nếu KSP sinh ra bảng thứ 5 thì có người đã thêm entity — xoá entity đó, **không** sửa tài liệu cho khớp.

## Todo List

- [x] 5 model `:domain/model/` khớp PRD §9
- [x] 5 interface `:domain/repository/` khớp PRD §8 (2 chữ ký lệch có chủ ý — xem LLM.md §13 Open #3)
- [x] 4 entity + `Converters` + index
- [x] 4 DAO, trong đó `latestPerMember()` phục vụ US-08
- [x] `FamilyTrackerDatabase` + `data/schemas/1.json` được sinh ra
- [x] 4 mapper, entity không rò ra ngoài `:data`
- [x] 4 repository impl; dedupe 60s chỉ nằm trong `ZoneEventRepositoryImpl.record()`
- [x] `DemoDataSeeder` 3 member + điểm vị trí giả
- [x] `PurgeOldHistoryUseCase` gọi lúc khởi động
- [x] Koin: `DatabaseModule` + `DataModule`, `KoinModulesTest` vẫn xanh (verify() dùng `includes(...)` — xem LLM.md §13 Fixed #3)
- [x] androidTest: CRUD + truy vấn ngày + dedupe 60s (9/9 pass trên emulator-5554)
- [x] Đúng 4 bảng trong schema export, không có `track_sessions`, không có `TrackSessionEntity`

## Success Criteria

```bash
./gradlew :data:assembleRelease                     # KSP sinh code Room, không lỗi
grep -o '"tableName": "[^"]*"' data/schemas/*/1.json | sort -u
#   -> đúng 4 dòng: members, zones, location_points, zone_events. KHÔNG có track_sessions
./gradlew test                                      # KoinModulesTest xanh ở cả hai variant
./gradlew :data:connectedDebugAndroidTest           # DAO + dedupe test, chạy trên emulator
```
**Vì sao instrumented test vẫn ở variant `debug`:** đổi sang release cần đặt `testBuildType = "release"`,
và điều đó lấy mất androidTest của variant debug. Test là vòng lặp dev, còn *artefact* đem demo mới là
release — hai thứ khác nhau. Mọi lệnh **đóng gói/cài đặt** dùng release; riêng `connected*AndroidTest`
giữ debug.
- `grep -rn "Entity" ui/src` trả rỗng — entity không rời `:data`.
- Chạy app, `adb logcat -s FTD_EVENT` thấy `purge_completed` đúng một lần lúc khởi động.

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| Bỏ `track_sessions` rồi sau này BA đòi ghi chú cho từng chuyến | Phải thêm bảng | Chuyến được suy ra có `id` ổn định (`memberId + startedAt`), thêm bảng ghi chú sau vẫn tham chiếu được |
| `fallbackToDestructiveMigration` xoá zone của người demo giữa chừng | Demo mất zone đã tạo | Chỉ đổi schema giữa các phase, không giữa buổi demo; `DemoDataSeeder` tạo lại zone mẫu |
| Truy vấn ngày quét toàn bảng | Vẽ History > 1s (vi phạm §7.1) | Test bằng `EXPLAIN QUERY PLAN` trong androidTest, khẳng định dùng index |
| Dedupe dựa vào đồng hồ thiết bị | Đổi giờ hệ thống làm sai luật 60s | Chấp nhận ở bản demo; ghi vào `LLM.md` §13 nếu thấy triệu chứng |

## Security Considerations

- Không có network call nào trong `:data` ở phase này (PRD §7.3).
- `android:allowBackup` hiện đang `true` trong manifest — nghĩa là **toạ độ có thể theo bản sao lưu
  ra khỏi máy**. Đặt `allowBackup="false"` hoặc loại DB khỏi `backup_rules.xml` ngay ở phase này.
- Không log toạ độ ở build release: `FTD_EVENT location_recorded` chỉ log `accuracy` và `filtered`,
  **không log lat/lng** (PRD §10, gate G7).

## Next Steps

→ [phase-03](phase-03-domain-tracking-algorithms.md). Chặn: 03 (cần model), 04, 05, 06, 07, 08, 10.
