# Fix Report — Phase 06: camera (0,0) ở Zone Editor + viền đỏ 4 góc ZoneRow

Ngày: 2026-08-21 · Agent: debugger (fix) · Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`

## Tóm tắt

Cả hai chẩn đoán trong yêu cầu đúng, xác nhận lại độc lập bằng cách đọc source (không tin lời kể) —
không có điểm nào tôi thấy bạn chẩn đoán sai. Cả hai đã sửa, khoá bằng test (Finding 1) và ảnh
before/after phóng to (Finding 2), xác nhận trên thiết bị thật ở đúng trạng thái lỗi ban đầu (self
0 location points, DB thật không phải giả lập). 92 test JVM (90 → 92, +2), G6 = 1, `assembleRelease`
xanh. Máy đã đưa về release + 2 zone cho phase-07.

---

## Finding 1 — Camera (0,0) ở Zone Editor

### Xác nhận chẩn đoán (đọc source trước khi sửa)

Đọc `ZoneEditorViewModel.kt` (bản trước sửa): nhánh seed-tâm-khi-tạo-mới chỉ đọc
`locations.firstOrNull { it.member.isSelf }?.lastLocation`. Nếu `null` (self chưa từng track),
`hasSeededCenterFromSelf` không bao giờ `true` dù Flow VẪN phát bình thường (nó chỉ bỏ qua các
member khác trong cùng danh sách). Đúng như bạn mô tả — không phải Flow không phát, mà nhánh code
chỉ lọc đúng một nguồn.

Đọc `MapContract.kt`/`MapViewModel.kt` (phase-05) để xác nhận "cách đã có lời giải": `MapState.
initialCameraTarget` = `selfLocation?.lastLocation ?: memberLocations.firstNotNullOfOrNull {
it.lastLocation }` — rơi về BẤT KỲ member nào có toạ độ nếu self chưa có. Đây chính là pattern cần
áp lại, đúng như task yêu cầu — không phải suy luận riêng của tôi.

Đọc `DemoDataSeeder.kt` xác nhận vì sao bug này lộ ra ngay từ lần cài đặt đầu: seeder LUÔN chèn 2
member khác ("Minh", "Lan") kèm toạ độ HCMC thật, chỉ "Tôi" (self) không có toạ độ seed (đúng —
self chỉ có vị trí khi thật sự bật theo dõi). Kiểm DB thật trên `emulator-5554` trước khi sửa gì:
`self` = 0 dòng `location_points`, `Minh`/`Lan` = 1 dòng mỗi người — xác nhận trạng thái lỗi có
thật trên máy này ngay lúc bắt đầu, không cần dàn dựng.

### Sửa

`ui/src/main/.../ui/feature/zone/ZoneEditorViewModel.kt`, nhánh `init` seed-tâm:

```kotlin
val seedPoint = locations.firstOrNull { it.member.isSelf }?.lastLocation
    ?: locations.firstNotNullOfOrNull { it.lastLocation }
```

(trước đó chỉ có vế đầu). Đổi tên cờ `hasSeededCenterFromSelf` → `hasSeededCenter` (không còn chỉ
"từ self" nữa). Không đổi gì khác trong luồng 3-nguồn-tâm đã có (route / self-hoặc-fallback /
zone đã lưu).

**Vì sao đây đúng hướng "dùng lại cách phase-05 đã giải" thay vì tự nghĩ cách khác:** cùng field
domain (`MemberLocation`), cùng use case (`ObserveMembersWithLastLocationUseCase`), cùng thứ tự ưu
tiên (self trước, ai cũng được sau) — không có lý do kỹ thuật nào để Zone Editor cần một chiến lược
khác Map.

### Test mới (`ZoneEditorViewModelTest.kt`, 13 → 15 test)

- `create mode with no self location falls back to another member's location, never (0,0)` — self
  không có điểm, member khác có → camera canh đúng vào điểm của member đó, `hasCenteredOnce = true`.
- `create mode with no location anywhere never fakes a center, stays uncentered` — biên còn lại
  (không ai có toạ độ, edge case lý thuyết vì `DemoDataSeeder` luôn seed ≥2 điểm): `hasCenteredOnce`
  phải ở lại `false`, KHÔNG được âm thầm nhận `(0,0)` làm toạ độ thật. Test này khoá đúng điều task
  yêu cầu ("chứ không phải (0,0)") theo cả hai hướng — có dữ liệu dự phòng thì dùng, không có thì
  thà chưa canh còn hơn canh sai.

### Xác nhận trên thiết bị (release, đúng trạng thái lỗi thật — không dàn dựng)

DB thật lúc đó: self 0 location points, `Minh`/`Lan` mỗi người 1 điểm gần văn phòng "14/3A Lương
Định Của - Thủ Thiêm". Mở app (bản release fix xong) → tab Zone → "Tạo zone":

`f-03-editor.png` — camera canh thẳng vào khu vực Thủ Thiêm (toạ độ thật của Minh), crosshair "+"
giữa khung, circle 150m hiện quanh nó — **không phải Đại Tây Dương**. "Theo dõi vị trí" ở màn Map
lúc đó vẫn OFF (`f-01-launch.png`), xác nhận self thật sự chưa track, đây không phải trường hợp
self tình cờ đã có vị trí làm mất tác dụng của test.

---

## Finding 2 — Viền đỏ 4 góc ZoneRow

### Xác nhận chẩn đoán

Đọc `ZoneRow.kt` (bản trước sửa): `DeleteBackground` là `Box(Modifier.fillMaxSize().background
(ZoneExitRed))` — hình chữ nhật, không `clip`. Nó là `backgroundContent` của `SwipeToDismissBox`,
`Card` (không khai `shape` riêng → mặc định `MaterialTheme.shapes.medium`, M3 default 12dp) là nội
dung trước mặt. `SwipeToDismissBox` (Material3 1.5.0-alpha17, đọc từ AAR đã resolve, không đoán)
compose + layout `backgroundContent` ở kích thước ĐẦY ĐỦ của dòng bất kể `currentValue` —
**không** gate theo trạng thái Settled/đang vuốt. Vậy nền đỏ hình chữ nhật luôn ở đó, sau lưng
`Card` bo góc — góc vuông ló ra là tất yếu hình học, không phải chỉ khi vuốt.

Ảnh `zonelist100.png` bạn gửi kèm khớp với suy luận trên: viền đỏ hiện ở cả 4 góc, không có thao
tác vuốt nào trong ảnh đó. Xác nhận: **báo cáo test ban đầu ("cosmetic, chỉ khi swipe") sai**, chẩn
đoán của bạn đúng.

### Sửa

`ui/src/main/.../ui/feature/zone/component/ZoneRow.kt`:

```kotlin
Box(modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(ZoneExitRed))
```

**Chọn clip, không chọn gate-theo-swipe-state — lý do:** clip là 1 dòng, không cần đọc thêm state
từ `dismissState` (giữ `DeleteBackground` không tham số như cũ). Gate-theo-state phải truyền
`dismissState` vào, thêm nhánh điều kiện, VÀ vẫn cần clip để nền không có góc vuông lúc đang vuốt
(M3 default Card shape không đổi theo swipe) — nên clip một mình đã giải quyết cả hai trạng thái,
gate không giải quyết thêm gì mà lại phức tạp hơn (YAGNI).

### Ảnh trước/sau — tự xem lại

**Trước** (không chụp lại — dev report đã có `p6-23`/`p6-36`/`zonelist100.png` cho thấy viền đỏ,
không lặp lại việc dựng 100 zone chỉ để chụp "trước" vì hành vi hình học không đổi theo số zone).

**Sau** (bản release fix xong, DB 2 zone thật):
- `f-02-zonelist.png` — 2 dòng "Saigon Office"/"Home", cả hai góc rõ ràng bo tròn sạch, không có
  vệt đỏ nào ở trạng thái nghỉ (không vuốt).
- `f-02-crop-row1.png` — crop + phóng to 2x góc trên-trái/trên-phải dòng "Saigon Office": nhìn kỹ
  ở pixel level, viền ngoài `Card` là màu nền xám `#F5F6F8` (`BackgroundGray`) liền mạch, không có
  điểm đỏ nào lọt qua góc bo. Tôi tự xem ảnh này (không chỉ tin log) trước khi kết luận đã hết.
- `f-04-final-release-zonelist.png` — chụp lại sau khi reinstall bản release CUỐI CÙNG (bản để lại
  cho phase-07) — cùng kết quả sạch, xác nhận build/install cuối cùng đúng là bản đã sửa.

---

## Output nghiệm thu

```
$ ./gradlew :ui:test --no-configuration-cache
BUILD SUCCESSFUL

$ ./gradlew test --no-configuration-cache
BUILD SUCCESSFUL
# Tổng theo test-results XML: domain 41, data 2, ui 48, app 1 = 92 test (90 → 92, +2)
# ZoneEditorViewModelTest: 13 → 15 (TEST-...ZoneEditorViewModelTest.xml tests="15" failures="0")

$ ./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
1   # khớp baseline ENV-BRIEFING.md §8

$ ./gradlew assembleRelease --no-configuration-cache
BUILD SUCCESSFUL in 12s
```

`CoroutineSafetyArchitectureTest` vẫn nằm trong `./gradlew test` xanh — không đụng gì tới coroutine
trong ViewModel (chỉ đổi biểu thức seed bên trong `collectSafely` đã có sẵn), không có `launchIn`/
`GlobalScope`/`runBlocking`/`CoroutineScope(` nào được thêm.

## Test mới — tên đầy đủ

`ui/src/test/.../ui/feature/zone/ZoneEditorViewModelTest.kt`:
1. `create mode with no self location falls back to another member's location, never (0,0)`
2. `create mode with no location anywhere never fakes a center, stays uncentered`

(Finding 2 không cần test JVM mới — hành vi là thuần render/layout Compose, xác nhận bằng ảnh thật
trên thiết bị theo đúng yêu cầu, không có unit test nào trong dự án này test Compose UI tree.)

## File sửa

- `ui/src/main/java/.../ui/feature/zone/ZoneEditorViewModel.kt` — Finding 1
- `ui/src/main/java/.../ui/feature/zone/component/ZoneRow.kt` — Finding 2
- `ui/src/test/java/.../ui/feature/zone/ZoneEditorViewModelTest.kt` — 2 test mới
- `LLM.md` §13 — thêm Fixed #11 (Finding 1, kèm luật mới) và #12 (Finding 2)

---

## Trạng thái máy sau dọn dẹp

- **Bản cài:** `app-release.apk` (build lại từ source đã sửa — `assembleRelease --no-configuration-
  cache` BUILD SUCCESSFUL, `adb install -r`). Xác nhận không debuggable:
  `run-as com.example.pion.family.tracker.demo ls databases/` → `package not debuggable` ✓.
- **DB:** đúng **2 zone** — `Saigon Office` (200m) và `Home` (1020m, giá trị này do một test
  US-21 nửa-sau của báo cáo test-phase-06 sửa `radiusMeters` 150→1020 lúc kiểm end-to-end mốc 100
  zone; không phải lỗi, không revert vì không thuộc phạm vi fix này). Đã xoá toàn bộ 98 zone
  `bulk-*`, checkpoint WAL (`wal_checkpoint(TRUNCATE)`), xoá `-wal`/`-shm` cũ trước khi push lại.
- **Rác:** `databases/ue3.db`, `databases/ue3.db-journal` đã xoá (thấy lúc còn cài bản debug để
  thao tác `run-as`).
- **Ảnh xác nhận cuối:** `f-04-final-release-zonelist.png` — bản release, 2 zone, không viền đỏ.

## Không có chỗ nào tôi thấy bạn chẩn đoán sai.

Cả hai finding đúng như mô tả, kể cả phần "báo cáo test ban đầu nói cosmetic/chỉ-khi-swipe là sai"
— tôi xác nhận độc lập bằng cách đọc `SwipeToDismissBox` không gate theo state, khớp với ảnh
`zonelist100.png` bạn gửi.

## Không có câu hỏi treo.
