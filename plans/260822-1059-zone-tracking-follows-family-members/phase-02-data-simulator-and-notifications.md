# Phase 02 — `:data`: bộ mô phỏng di chuyển, gỡ geofence, thông báo mang tên thành viên

**Ưu tiên:** P0 · **Trạng thái:** ✅ Hoàn thành

## Key Insight

1. **Điểm của thành viên mô phỏng KHÔNG đi qua `LocationFilter`.** Bộ lọc tồn tại để loại nhiễu GPS
   thật (`accuracy > 50m`, `speed > 200 km/h`). Điểm mô phỏng không có nhiễu, và cú dời vị trí ở
   Key Insight #3 phase-01 sẽ bị luật `SPEED` từ chối thẳng. Bộ mô phỏng ghi thẳng qua
   `MemberRepository.recordLocation`, không qua `LocationPointProcessor`.
2. **Trạng thái `insideZoneIds` phải tách riêng cho từng thành viên.** Bản cũ giữ đúng một `Set` cho
   self trong `LocationPointProcessor`. Dùng chung một set cho 2 thành viên sẽ khiến Lan "thừa
   hưởng" sự kiện ENTER của Minh và không bao giờ nhận ENTER của chính mình.
3. **Seed `insideZoneIds` bằng hình học lúc khởi động, không bằng lịch sử sự kiện.** Bản cũ đọc lại
   `ZoneEventDao.latestPerZone`. Hỏi thẳng vị trí hiện tại (`evaluate(..., emptySet()).insideAfter`)
   vừa đơn giản hơn vừa đúng hơn: không có ENTER ma cho zone thành viên đã đứng sẵn bên trong trước
   khi app mở.
4. **Job mô phỏng gia đình KHÔNG được là con của `trackingJob`.** `runSimulation` (nút "Mô phỏng lộ
   trình" ở tab Lịch sử) gọi `trackingJob?.cancelAndJoin()`. Nếu chung job, bấm nút đó sẽ giết luôn
   chuyển động của Minh/Lan. Tách `familyJob` riêng, cũng là con cấu trúc của `scope` (MVI doc §3).

## Related Code Files

**Tạo:**
- `data/location/MemberMovementSimulator.kt`
- `data/src/test/.../data/location/MemberMovementSimulatorTest.kt`

**Sửa:**
- `data/location/LocationPointProcessor.kt` — bỏ toàn bộ phần đánh giá zone
- `data/location/LocationTrackingService.kt` — thêm `familyJob`, bỏ `seedInsideZoneIds`
- `data/repository/MemberRepositoryImpl.kt` — `recordLocation`
- `data/repository/ZoneEventRepositoryImpl.kt` — tra tên thành viên cho thông báo
- `data/notification/ZoneNotifier.kt` + `data/res/values/strings.xml` — "%1$s đã đến %2$s"
- `data/di/DataModule.kt`, `app/FamilyTrackerApp.kt`, `app/src/main/AndroidManifest.xml`

**Xoá:**
- `data/geofence/` (3 file) và `data/src/androidTest/.../geofence/GeofenceRegistrarTest.kt`

## Todo

- [x] `MemberMovementSimulator` + test JVM thuần
- [x] Gỡ đánh giá zone khỏi `LocationPointProcessor`
- [x] `familyJob` trong `LocationTrackingService`
- [x] Thông báo mang tên thành viên
- [x] Gỡ sạch geofence khỏi manifest/DI/app

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Mất khả năng phát hiện nền khi app đóng (US-24) | Bộ mô phỏng chạy trong foreground service — sống khi app ở nền. Không sống khi service bị tắt; ghi vào `LLM.md` §13 Open. |
| `EventSource.GEOFENCE_API` thành giá trị chết | Giữ trong enum (đã có dòng Room mang giá trị đó), đánh dấu legacy trong KDoc. Xoá đi cần bump version DB, không đáng. |
