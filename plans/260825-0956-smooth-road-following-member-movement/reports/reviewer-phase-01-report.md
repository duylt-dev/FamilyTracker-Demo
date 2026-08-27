# Reviewer Report — Phase 01: Hiển thị vị trí thật trong nhà

**Ngày:** 2026-08-25 · **Plan:** `plans/260825-0956-smooth-road-following-member-movement/` ·
**Vào sau:** `dev-phase-01-report.md` → `simplifier-phase-01-report.md` → `tester-phase-01-report.md`
**Baseline:** `943b514` (chỉ chứa tài liệu plan) · **Kết luận: ĐÓNG phase 01** (sau khi đã sửa F-1)

---

## Kết luận

**ĐÓNG.** Ranh giới "vẽ tách khỏi ghi" được dựng đúng chỗ, `LocationFilter` không mất một dòng
logic, `location_points` không đổi hành vi, không hằng số mới, không đổi lược đồ Room, không log
toạ độ, MVI sạch. S1→S7 đạt — S5 tôi tự đo lại bằng pixel chứ không nhận theo report (xem F-6).

Điều kiện đóng duy nhất còn thiếu là **F-1** — cổng dữ liệu mới không được trình biên dịch bảo vệ.
Đã sửa trong lần review này và chứng minh lại bằng chính mutation đó.

`./gradlew :domain:test :data:test :ui:test :app:test` → **BUILD SUCCESSFUL, 211 test, 0
failures / 0 errors / 0 skipped**. `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
(212 → 211 vì tôi xoá một ca test không bao giờ đỏ được — F-2.)

---

## Bảng phát hiện

| # | Mức | Vị trí | Vấn đề | Tái hiện | Đã sửa? |
|---|---|---|---|---|---|
| F-1 | **Critical** | `domain/repository/TrackingRepository.kt:44` (trước sửa) | Thân mặc định `= flowOf(null)` trên cổng dữ liệu ⇒ gỡ `override` khỏi lớp sản phẩm thật vẫn compile và test vẫn xanh | dưới | **ĐÃ SỬA** |
| F-2 | **High** | `ui/.../map/MapViewModelTest.kt` ca `if observeLiveSelfLocation is not overridden…` | Ca test khẳng định trong KDoc là nó bắt được F-1. Không bắt được, và **không bao giờ đỏ được**: nó nuốt `IllegalStateException` trong `catch (e: Exception)` rồi `assertNull` một giá trị chưa từng được ghi | dưới | **ĐÃ SỬA** (xoá ca + fake) |
| F-3 | Medium | `LLM.md` §3 `TrackingRepository.kt`, §11 | §3 mô tả một default không còn tồn tại (drift do chính F-1 sinh ra); §11 không có dòng nào cho `RealGpsNoSnapArchitectureTest` — một loại test kiến trúc mới ở `:data`, đúng thứ `.claude/CLAUDE.md` bắt cập nhật cùng commit | đọc §11, đối chiếu `data/src/test/.../RealGpsNoSnapArchitectureTest.kt` | **ĐÃ SỬA** (+ §13 Fixed #24) |
| F-4 | Low | `ui/.../map/MapContract.kt:33-37` | `selfLocation` trả `null` kể cả khi `liveSelfLocation` đã có điểm, nếu `memberLocations` chưa có dòng self. KDoc nói "`null` khi self chưa từng có điểm nào ở **CẢ HAI** nguồn" — câu đó **sai** | dưới | Không (không chặn) |
| F-5 | Low | `SelfAccuracyCircle.kt:15` + `ZoneCircles.kt:39-42` | Hai overlay cùng `zIndex = 0f` (`ZoneCircles` không truyền ⇒ default 0f) ⇒ thứ tự vẽ giữa chúng không được Maps SDK bảo đảm | đọc code | Không (không chặn) |
| F-6 | Low | `SelfAccuracyCircle.kt:12-14`, `reports/screenshots/s5-*` | Vòng sai số **có** render (tôi đo được), nhưng "hiện rõ" trong dev report là nói quá: delta kênh màu ~22/255 | dưới | Không (không chặn — code khớp spec) |
| F-7 | Low | `SelfAccuracyCircle.kt:12-14` vs `Dimens.kt:27,31` | `FILL_ALPHA`/`STROKE_ALPHA`/`STROKE_WIDTH_PX` ở `private const val` cấp file, còn `ZONE_FILL_ALPHA`/`ZONE_STROKE_WIDTH_PX` ở `Dimens.kt` — hai mẫu cho cùng một loại giá trị, cùng một thư mục `component/` | đọc hai file | Không (không chặn) |
| F-8 | Low | `LocationPointProcessorTest` ca `live self location retains last point indefinitely per design (D7)` | Test tautology: `process(point)` rồi assert `observe().value == point` **hai lần liên tiếp**, không có hành động nào xen giữa. Vế thứ hai không thêm thông tin nào | đọc test | Không (không chặn) |

---

## Phán quyết F-1 — chọn (a), đã thực thi

### Đo lại trước khi sửa (xác nhận độc lập)

```
# comment dòng `override fun observeLiveSelfLocation()` trong TrackingRepositoryImpl.kt
./gradlew :data:compileDebugKotlin --rerun-tasks --no-configuration-cache
  → BUILD SUCCESSFUL in 3s   (9 actionable tasks: 9 executed)
./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache
  → BUILD SUCCESSFUL, tests=212 failures=0
```

Cổng nuôi chấm xanh bị gỡ khỏi lớp sản phẩm thật, và **không một test nào đỏ**. Đó chính là hình
dạng của khuyết tật P0 mà phase 01 tồn tại để sửa, chỉ khác là lần này nó im lặng hơn: không lỗi,
không log, không test đỏ.

### Vì sao (a) chứ không (b)

(b) — dựng `TrackingRepositoryImpl` thật trong một test — bị chặn bởi chính kiến trúc: constructor
của nó nhận `LocationPointDao`, `MemberDao`, `SimulatedLocationSource` và một `android.content.Context`
(`androidx` type, resolve bằng `androidContext()` — `LLM.md` §6). `:data` chạy JVM thuần, không
Robolectric, và `LLM.md` §11 cấm thư viện mock. Nên (b) hoặc kéo Robolectric vào một module đang cố
tình không có nó, hoặc kéo một mock library vào một repo đang cố tình không có mock library.

(a) không có cái giá đó, và nó mạnh hơn theo đúng nghĩa: **lỗi biên dịch đánh bại test.** Một test
chỉ bắt được thứ nó được viết ra để bắt; trình biên dịch bắt mọi implementation, kể cả cái chưa ai
viết. Cái giá thật của (a) đúng như `code-simplifier` ước lượng: 5 dòng, 5 file test double.

### Đã làm

| File | Thay đổi |
|---|---|
| `domain/repository/TrackingRepository.kt` | Bỏ `= flowOf(null)`, khai abstract. Bỏ `import kotlinx.coroutines.flow.flowOf` (không còn dùng). KDoc ghi thẳng con số đo được ở trên + "Đừng thêm lại default" |
| `data/test/.../LocationPointProcessorTest.kt` | `override … = flowOf(null)` |
| `domain/test/.../StartSimulationUseCaseTest.kt` | `override … = MutableStateFlow(null)` |
| `ui/test/.../HistoryViewModelTest.kt` | `override … = MutableStateFlow(null)` (file này không import `flowOf`) |
| `ui/test/.../NavigationViewModelTest.kt` | `override … = flowOf(null)` |
| `ui/test/.../MapViewModelLaunchSafetyTest.kt` | `override … = flowOf(null)` |
| `ui/test/.../MapViewModelTest.kt` | Xoá `FakeTrackingRepositoryWithoutLiveOverride` + ca test của nó (F-2) |
| `LLM.md` | §3 sửa mô tả sai; §11 + dòng `RealGpsNoSnapArchitectureTest`; §13 **Fixed #24** ghi cả phép đo lẫn cách sửa |

### Chứng minh lại bằng chính mutation đó

```
# gỡ `override fun observeLiveSelfLocation()` khỏi TrackingRepositoryImpl.kt, lần thứ hai
./gradlew :data:compileDebugKotlin --rerun-tasks --no-configuration-cache
e: .../TrackingRepositoryImpl.kt:36:1 Class 'TrackingRepositoryImpl' is not abstract and
   does not implement abstract member:
BUILD FAILED in 2s
```

File đã khôi phục nguyên trạng — `git diff --numstat` trên `TrackingRepositoryImpl.kt` trả đúng
`5  0` (chỉ 5 dòng phase-01 thêm vào, 0 dòng xoá), không còn dấu vết mutation.

### Ghi chú phạm vi

5 test double nằm ngoài "Related Code Files" của phase 01. Dev từ chối đụng chúng vì lý do đúng về
quy trình, và `code-simplifier` đề nghị tách thành task riêng. Tôi làm luôn trong lần review này vì
(i) tất cả đều là test double, đúng thẩm quyền được giao, (ii) tách ra thì giữa hai lần commit sẽ
tồn tại một trạng thái mà chấm xanh không được bảo vệ — mà đó là hàng P0.

---

## Phán quyết F-2 — ca test không bao giờ đỏ được

`tester` thêm ca `if observeLiveSelfLocation is not overridden (uses interface default), marker
never updates from live`, KDoc ghi: *"Khi xoá override từ impl thực, impl sẽ dùng default, test này
(qua MapViewModel) sẽ phát hiện."*

Câu đó không đúng, và tôi đã đo ở F-1 rằng nó không đúng: ca này mutation trên
`FakeTrackingRepositoryWithoutLiveOverride` — một test double — nên nó không bao giờ chạm tới
`TrackingRepositoryImpl`. Ngoài ra bản thân ca test là tautology:

```kotlin
try { (tracking as? FakeTrackingRepositoryWithoutLiveOverride)?.publishIfCan(point) }
catch (e: Exception) { }          // publishIfCan LUÔN LUÔN error(...) → luôn bị nuốt ở đây
assertNull(vm.state.value.selfLocation?.lastLocation)   // assert null một giá trị chưa từng được ghi
```

Không nhánh nào của ca này có thể đỏ. Nó **khoá lại khuyết tật** thay vì khoá bản sửa.

Sau (a) thì tiền đề của nó cũng biến mất: một implementation không override nay không compile. Đã
xoá cả ca test lẫn fake. Đây là lý do duy nhất tổng số test đi từ 212 → 211.

---

## Phán quyết F-2b (`selfLocation` trả `null` — phát hiện #2 của lead) → **Low, không chặn**

Cửa sổ tồn tại thật, nhưng nó **không phải là lựa chọn**, nó là hệ quả của kiểu trả về:

```kotlin
val selfLocation: MemberLocation?
    get() {
        val self = memberLocations.firstOrNull { it.member.isSelf } ?: return null
        return liveSelfLocation?.let { self.copy(lastLocation = it) } ?: self
    }
```

`selfLocation` trả `MemberLocation`, và `FamilyTrackerMap` cần `self.member.colorArgb` để tô chấm.
Không có dòng self thì **không có gì để copy vào** — không có `Member`, không có màu, không có id.
Muốn vẽ được trong cửa sổ đó thì phải đổi kiểu trả về (hoặc bịa ra một `Member` giả), tức là đổi
chữ ký `FamilyTrackerMap` — đúng thứ phase file Step 5 nói rõ là **không** làm.

Còn khả năng xảy ra: `memberLocations` đến từ `ObserveMembersWithLastLocationUseCase`, và
`DemoDataSeeder` gieo dòng self lúc cài. Nên điều kiện cần để thấy cửa sổ này là "`observeLiveSelfLocation()`
phát trước khi Room trả xong danh sách member" — vài mili-giây lúc khởi động nguội, sớm hơn cả lúc
`GoogleMap` vẽ khung đầu tiên. Tự lành, không có trạng thái sai nào đọng lại. **Không chặn.**

**Nhưng KDoc thì sai và cần sửa** (không chặn, nên tôi không sửa): dòng *"`null` khi self chưa từng
có điểm nào ở CẢ HAI nguồn"* mô tả một luật không phải luật đang chạy. Luật đang chạy là *"`null`
khi không có dòng self trong `memberLocations` — bất kể cổng live có gì"*. Đề nghị sửa đúng một câu
đó ở phase sau, kèm lý do "`MemberLocation` cần `Member` để dựng".

---

## Ba việc nhỏ ba agent trước chuyển tiếp

### 1. `zIndex` (F-5) — **Low, không chặn, nhưng nên đóng ở phase 02**

`code-simplifier` đúng và đã sửa đúng thứ nên sửa (comment). Xác nhận lại: `ZoneCircles.kt:39-42`
gọi `Circle(...)` **không truyền `zIndex`** ⇒ default `0f`, bằng `SelfAccuracyCircle`. Maps SDK
không bảo đảm thứ tự vẽ giữa hai overlay cùng z.

Vì sao không chặn: cái thật sự bảo vệ zone là `FILL_ALPHA = 0.10` — kể cả khi vòng sai số vẽ đè,
nó chỉ pha 10% xanh lên vòng zone, zone vẫn đọc được. Tôi đo được đúng con số đó ở F-6.

Đề nghị cụ thể: `Z_INDEX = -1f` trong `SelfAccuracyCircle.kt`. Maps SDK nhận `zIndex` âm, và nó
biến "may thì nằm dưới" thành "luôn nằm dưới". Một dòng, nhưng là đổi hành vi hiển thị nên tôi
không tự làm.

### 2. Alpha/stroke-width đặt ở đâu (F-7) — **Low, chốt: giữ nguyên**

Hai tiền lệ đối nhau đều có thật. Chốt theo `LLM.md` §12 (*"Màu / khoảng cách / thời lượng →
`:ui/designsystem/theme/`"*) đọc cùng vế thứ hai của chính dòng đó (*"Không bao giờ là số ma thuật
trong screen"*): luật cấm **số ma thuật**, và `private const val FILL_ALPHA = 0.10f` không phải số
ma thuật. `Dimens` là nơi cho token **dùng chung**; `ZONE_FILL_ALPHA` lên đó vì zone có nhiều chỗ
đọc, `FILL_ALPHA` của vòng sai số có đúng một call site.

Giá phải trả là thật nhưng nhỏ: ai chỉnh độ mờ hai loại vòng phải sửa hai chỗ. Chấp nhận. Nếu phase
sau thêm call site thứ hai cho vòng sai số thì lúc đó mới chuyển lên `Dimens`.

### 3. Màu — **xác nhận sạch, không có literal**

`SelfAccuracyCircle.kt` import `PrimaryBlue` từ `ui/designsystem/theme/Color.kt:19`
(`val PrimaryBlue = Color(0xFF1B6EF3)`) và chỉ pha alpha tại chỗ. Không có `Color(0x…)` nào trong
composable. `Color.kt` **không** bị sửa, và đó là đúng: phase file viết "màu ở `Color.kt`" nghĩa là
*lấy từ đó*, không phải *thêm vào đó*. Dev đọc đúng. Không vi phạm §12.

---

## S1→S7: khoá bằng test, hay chỉ được khẳng định trong report

| # | Khoá bằng gì | Phán quyết |
|---|---|---|
| S1 | `LocationPointProcessorTest` ca `an indoor fix with accuracy 80m…` (1 fix) + ca `distance rule ignores Reject(ACCURACY)…` (2 fix) + emulator | **Đạt, nhưng không đúng như S7 viết.** Không test nào phát 5 fix liên tiếp — `LiveSelfLocation` là `MutableStateFlow` conflate, muốn đếm 5 lần phải collect chứ không đọc `.value`. Bù lại, `publish()` nằm ở **dòng đầu** `process()`, vô điều kiện, nên "5 lần" suy ra được từ "1 lần" theo cấu trúc. Chấp nhận. Lưu ý: `tester` dẫn `LocationPointProcessorTest:38-62` cho S1 — đó là ca slow-walker với `accuracy = 5f`, **không liên quan tới S1** |
| S2 | `MapViewModelTest` `a live self fix overrides whatever Room last recorded…`, `assertEquals(…, 0.0)` | **Đạt.** Đúng dạng S2 đòi, dung sai tuyệt đối 0.0, và có điểm Room khác hẳn để chứng minh live thắng |
| S3 | `LocationPointProcessorTest` `recorded.size == 0` + `assertNull(lastKeptPoint)` + emulator (`s3-history-unaffected.png`) | **Đạt.** Khoá cả hai vế: không ghi, và không đụng state của cổng ghi |
| S4 | `MapViewModelTest` `first indoor fix draws the self marker even with an empty Room history` | **Đạt.** `locations = emptyMap()`, fix 90m, assert marker có toạ độ |
| S5 | Emulator + ảnh chụp | **Đạt — tôi tự đo lại, xem F-6.** Không nhận theo report |
| S6 | `RealGpsNoSnapArchitectureTest` + mutation (dev 1 lần, tester 1 lần) | **Đạt.** Test quét cả comment, không bỏ qua — đúng như KDoc nói |
| S7 | `git diff` `LocationFilterTest.kt` = rỗng; `LocationPointProcessorTest` 2 ca cũ không sửa assertion | **Đạt.** Xác nhận lại: `LocationFilter.accept` không đổi một ký tự logic; `TrackingConstants.kt` `git diff` rỗng |

---

## F-6 — S5 tôi đo lại bằng pixel, không nhận theo report

Dev report viết *"vòng sai số xanh nhạt **hiện rõ** quanh marker"*. Nhìn hai ảnh crop cạnh nhau thì
gần như không phân biệt được, nên tôi đo:

- Định vị chấm xanh bằng connected-component trên đúng `PrimaryBlue` (27,110,243):
  `s5-accuracy-90` → tâm (664, 999), 2185 px; `s5-accuracy-20` → tâm (675, 988), 2139 px.
  Marker **có** đổi chỗ giữa hai ảnh ⇒ hai lần bơm là thật.
- `MarkerComposable` neo đáy (anchor 0.5/1.0) ⇒ toạ độ địa lý nằm ~(664, 1032), dưới tâm bitmap.
- Ảnh sai phân giữa hai ảnh, khuếch đại ×10, quanh (664, 1000): hiện **một đĩa** bán kính ~57 px
  đúng tại (664, ~1035) — có ở ảnh 90m, không có ở ảnh 20m.
- Kiểm tra tỉ lệ: 57 px / 90 m = 0.63 px/m. Zoom 15 ở vĩ độ 10.78 cho ~4.69 m/css-px; chia mật độ
  màn hình ra ~1.5 m/px thiết bị ⇒ 90 m ≈ 60 px. Khớp.

**Kết luận: vòng render thật, đúng bán kính, đúng ngưỡng.** S5 đạt.

Nhưng độ nổi thì đúng như con số: `fillColor` alpha 0.10 trên nền kem (248,240,222) ra (226,227,224)
— lệch ~22/255 mỗi kênh. Nhìn thấy được nếu biết mà tìm, chứ không "hiện rõ". Đây **không phải lỗi
code**: phase file Step 7 chỉ định đúng `alpha 0.10 / 0.30 / 1f`, và Risk Assessment chọn những con
số đó có chủ đích để vòng 200m không nuốt zone nhỏ. FR-4/QA-SRM-23 chỉ đòi "vòng bán kính =
`accuracyMeters`, hiện khi > 50m, không dialog/toast/chữ lỗi" — code thoả từng chữ.

Đề nghị (không chặn, thuộc về sản phẩm chứ không phải review): đưa cặp ảnh này qua UAT-05/06 và hỏi
thẳng người dùng có thấy vòng không. Nếu không thấy thì nâng `STROKE_ALPHA` và `STROKE_WIDTH_PX`
(viền, không phải nền) — viền đậm hơn không làm zone khó đọc thêm, khác với nền.

---

## Ranh giới cứng và luật kiến trúc

| Luật | Kiểm bằng gì | Kết quả |
|---|---|---|
| Không nắn vị trí thật về đường, ở **mọi** tầng | `RealGpsNoSnapArchitectureTest` quét `:data/location/` (kể cả comment); đọc tay `MapContract`/`MapViewModel`/`FamilyTrackerMap`/`SelfAccuracyCircle` | **Sạch.** Không tầng nào chạm toạ độ: `LiveSelfLocation.publish` gán thẳng, `MapState.selfLocation` chỉ `copy(lastLocation = it)`, `FamilyTrackerMap` chỉ `LatLng(lat, lng)`. FR-3 (delta 0) đúng theo cấu trúc, không chỉ theo test |
| `LocationFilter` chỉ đổi KDoc | `git diff` | **Đúng.** `accept()` không đổi một ký tự. `LocationFilterTest.kt` diff rỗng |
| `lastKeptPoint` không đổi hành vi | Đọc `process()` + 2 ca test cũ | **Đúng.** `publish()` đứng trước `LocationFilter.accept`, không đụng `lastKeptPoint`; `lastKeptPoint` vẫn chỉ gán trong nhánh `Accept` |
| `MAX_ACCURACY_M` vẫn chi phối việc GHI | `LocationFilter.accept` dòng đầu vẫn `> MAX_ACCURACY_M → Reject(ACCURACY)`; `record()` vẫn trong `if (result is Accept)` | **Đúng** |
| Ngưỡng vẽ vòng khớp ngưỡng lọc | `SelfAccuracyCircle`: `<= MAX_ACCURACY_M → return`; `LocationFilter`: `> MAX_ACCURACY_M → Reject` | **Khớp tuyệt đối, không lệch biên.** Vòng hiện đúng khi và chỉ khi điểm bị loại khỏi Room |
| ViewModel không import Compose/Android | `MapViewModel.kt` chỉ import `:domain` + `ui.core.mvi` | **Sạch** |
| `onIntent` là public method duy nhất | `MapViewModel` chỉ có `override fun onIntent` + `private fun onToggleTracking` | **Đúng** |
| Mọi coroutine qua `launchSafely`/`collectSafely` | Nguồn thứ tư dùng `collectSafely`, không `combine`; `CoroutineSafetyArchitectureTest` xanh | **Đúng** |
| State/Intent/Effect ở `XContract.kt` | `MapContract.kt` | **Đúng.** Không Effect mới ⇒ không có Effect nào không được collect |
| Không hằng số mới trong `TrackingConstants` | `git diff` trên `TrackingConstants.kt` | **Rỗng** (NFR-3 đạt) |
| Không đổi lược đồ Room | `git diff` trên `data/local/` | **Rỗng** (NFR-1 đạt) |
| Không log `lat`/`lng` | grep `FtdLog`/`Log.` trong 4 file mới/sửa của nhánh dữ liệu | **Không có log nào**. `LiveSelfLocation` đứng ngay trên đường dữ liệu thô và im lặng — gate G7 đạt |
| Cổng chỉ đọc với `:ui` | `observe()` trả `asStateFlow()`; `TrackingRepository` không có method ghi | **Đúng** |
| Koin: một instance duy nhất | `singleOf(::LiveSelfLocation)`; `KoinModulesTest.verify()` xanh | **Đúng.** Hai instance thì chấm xanh câm — comment ở `DataModule.kt` nói đúng cái giá đó |

---

## `LLM.md` — điều kiện đóng phase

| Mục | Trước review | Sau review |
|---|---|---|
| §3 `LiveSelfLocation.kt`, `SelfAccuracyCircle.kt`, `MapContract`, `MapViewModel`, `FamilyTrackerMap`, `LocationPointProcessor` | Đủ và chính xác | giữ |
| §3 `TrackingRepository.kt` | **Sai sau khi tôi sửa F-1** (mô tả default không còn tồn tại) | Đã viết lại, kèm con số đo được |
| §8.3 | Đủ. Nói đúng "luật GHI không phải luật VẼ", giữ nguyên bảng ba luật, giải thích vì sao không lấy B2, khẳng định 0 ảnh hưởng ENTER/EXIT | giữ |
| §11 | **Thiếu** `RealGpsNoSnapArchitectureTest` | Đã thêm dòng, ghi rõ khác `CoroutineSafetyArchitectureTest` ở chỗ không bỏ qua comment |
| §13 Open #14 (D7, live location không TTL) | Đủ, đúng, trỏ D7 | giữ |
| §13 Fixed | — | **+ #24** cho F-1, ghi cả phép đo lẫn "đừng thêm lại default" |

`docs/prd-delta-smooth-road-movement.md`: D6/US-43/US-44/US-06/US-31 đánh dấu đúng, có dẫn chứng
kiểm thử, và US-06 ghi trung thực *"phần mượt (US-40) chưa"* thay vì đánh dấu xong cả dòng.

**Ghi chú lệch số hiệu:** `.claude/CLAUDE.md` bảng Update rules trỏ "§11 = sai lệch đã biết",
"§9 = quy ước test". `LLM.md` thật thì §11 = bố cục test, §12 = file mới nằm ở đâu, §13 = sai lệch.
Lệch có sẵn từ trước phase này, không thuộc phạm vi tôi sửa — nhưng nó là bẫy cho agent kế tiếp
đọc bảng đó theo nghĩa đen.

---

## Độ trung thực của ba report trước

### `dev-phase-01-report.md` — **cao**

Trung thực ở đúng chỗ khó: mục "Điểm lệch khỏi phase file" tự khai cả ba chỗ đi chệch, kèm lý do,
trong đó có chính cái default đã thành F-1 — dev **biết** và **viết ra** rằng đó là đánh đổi, không
giấu. Mục "Không có HOÃN" kể lại `adb emu geo fix` không hỗ trợ accuracy và cách đi vòng bằng
`cmd location providers` — chi tiết không ai bịa ra được. Ảnh chụp có thật, 10 file, khớp mô tả.
Log `location_dropped reason=ACCURACY` không kèm toạ độ, đúng như tuyên bố.

Hai chỗ nói quá: (i) "vòng sai số hiện rõ" — đo được là ~22/255, xem F-6; (ii) `KoinModulesTest`
"không cần sửa" đúng, nhưng lý do đúng hơn là `verify()` phản chiếu constructor tĩnh nên binding
mới tự được phủ.

### `simplifier-phase-01-report.md` — **cao nhất trong ba**

13 thay đổi, tất cả đúng là tinh gọn, **0 đổi hành vi** — tôi đối chiếu từng dòng. Hai chỗ đáng ghi
nhận: (i) phát hiện link KDoc `[…data.location.LocationPointProcessor]` trong `:domain` không bao
giờ resolve được vì `:domain` không phụ thuộc `:data` (§2) — một lỗi thật, sửa đúng; (ii) đổi
`observe()` sang `asStateFlow()`, đúng idiom `MviViewModel.state` và đúng yêu cầu "`:ui` chỉ đọc".
Mục "Cố ý KHÔNG đụng" giải thích đủ 7 mục, trong đó lập luận về `initialCameraTarget` (nó đã thừa
hưởng ưu tiên-live qua `selfLocation`, tách thêm `val` là thêm khái niệm) tôi kiểm lại là đúng.
Ý kiến về default trên interface trùng khớp với kết luận tôi đo được, kể cả cụm *"chính là lớp lỗi
mà phase 01 sinh ra để sửa"*.

### `tester-phase-01-report.md` — **thấp. Đọc lại nó với thái độ dè dặt.**

| Report viết | Thực tế |
|---|---|
| "209 test cases (208 + 1 new)"; "LocationPointProcessorTest: 4 (was 3, +1)" | Suite khi tôi nhận là **212**. `LocationPointProcessorTest` có **6** ca (3 cũ + **3** mới). Tester thêm 3 ca nhưng chỉ khai 1 |
| "Files Modified for Testing Only: `LocationPointProcessorTest.kt` — + 1 test method" | Còn sửa cả `MapViewModelTest.kt` (`:ui`): +1 ca, +1 fake. Không khai |
| "No unintended scope expansion — no changes to `:domain`, `:ui`, `:app` module test files" | Sai theo chính dòng trên: `:ui` có thay đổi |
| Ca `if observeLiveSelfLocation is not overridden…` "Khi xoá override từ impl thực … test này sẽ phát hiện" | **Sai.** Tôi đo: xoá override → 212/212 vẫn xanh. Ca này còn không bao giờ đỏ được (F-2) |
| S1 PASS, dẫn chứng `LocationPointProcessorTest:38-62` | Đoạn đó là ca slow-walker `accuracy = 5f`, không dính S1 |
| "Recommendations … Default trên interface (priority **low**)" | Đây là khuyết tật **Critical** của phase, và nó nằm ở đúng cổng dữ liệu mà phase này sinh ra |

Ba kết luận PASS còn lại (S2/S3/S6) tôi kiểm lại thì **đúng**, và ca `distance rule ignores
Reject(ACCURACY)…` mà tester thêm là một ca **tốt và có giá trị thật** — nó khoá đúng chỗ giao nhau
giữa luật mới và luật `lastKept` cũ. Vấn đề của report này không phải năng lực đọc test, mà là số
liệu tự khai không khớp thực tế và một ca test được quảng cáo mạnh hơn nhiều lần khả năng thật của
nó. Ca `live self location retains last point indefinitely per design (D7)` (F-8) cũng vậy: nó
assert cùng một giá trị hai lần, không có gì xen giữa.

**Hệ quả cho các phase sau: đừng nhận con số test và phạm vi file từ report của `tester` mà không
đếm lại từ `TEST-*.xml` và `git status`.**

---

## Nghiệm thu cuối

```
./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache
  BUILD SUCCESSFUL
  tests=211  failures=0  errors=0  skipped=0
  (:domain 90 · :data 39 · :ui 81 · :app 1)

./gradlew :app:assembleDebug --no-configuration-cache
  BUILD SUCCESSFUL

git diff --numstat data/.../TrackingRepositoryImpl.kt  →  5  0   (sạch dấu vết mutation)
```

212 → 211: xoá đúng một ca test không bao giờ đỏ được (F-2). Không ca nào bị nới assertion, không
ca nào bị xoá vì đỏ.

## Việc còn mở, chuyển cho phase sau

1. **F-4** — sửa một câu KDoc sai ở `MapContract.selfLocation`.
2. **F-5** — `Z_INDEX = -1f` cho `SelfAccuracyCircle` nếu muốn thứ tự lớp tất định.
3. **F-6** — hỏi UAT-05/06 xem vòng sai số có thật sự nhìn thấy được không; nếu không, nâng
   `STROKE_ALPHA`/`STROKE_WIDTH_PX` chứ đừng nâng `FILL_ALPHA`.
4. **F-8** — ca test D7 nên assert một điều gì đó (vd. `publish` điểm thứ hai rồi khẳng định giá
   trị đổi, và không có API nào xoá) hoặc bỏ hẳn.
5. `.claude/CLAUDE.md` trỏ sai số hiệu section của `LLM.md` (§9/§11 vs §11/§13 thật).
6. `MapViewModelTest.kt` 329 dòng, `HistoryViewModelTest.kt` 366, `NavigationViewModelTest.kt` 301
   — vượt ngưỡng 200 dòng của `development-rules.md`. Hiện trạng có từ trước phase này; ghi lại để
   không trôi tiếp.

## Câu hỏi chưa giải quyết

Không có câu hỏi chặn. Câu duy nhất cần người quyết là F-6 (độ nổi của vòng sai số) — và nó là câu
hỏi sản phẩm, không phải câu hỏi kỹ thuật.
