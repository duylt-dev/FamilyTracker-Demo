# Simplifier Report — Phase 01: Hiển thị vị trí thật trong nhà

**Ngày:** 2026-08-25 · **Plan:** `plans/260825-0956-smooth-road-following-member-movement/` ·
**Vào sau:** `reports/dev-phase-01-report.md` · **Status:** completed

## Tóm tắt

11 file được tinh gọn, **0 thay đổi hành vi**. Không đụng thứ tự publish/lọc trong
`LocationPointProcessor`, không đụng `lastKeptPoint`, không đụng logic `LocationFilter`, không đụng
thân mặc định `observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)` trên interface,
không thêm hằng số vào `TrackingConstants`, không tạo file mới, không xoá/nới test.

`./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache` → **BUILD
SUCCESSFUL, 208 test, 0 failures/errors** (đúng bằng con số baseline dev bàn giao).

## Thay đổi, từng cái một

| # | File | Thay đổi | Vì sao |
|---|---|---|---|
| 1 | `ui/.../map/MapContract.kt` | `selfLocation` getter: `memberLocations.firstOrNull { it.member.isSelf }` từ **hai** lần dò xuống **một** lần, viết dạng block + early return | Biểu thức cũ dò danh sách hai lần và lặp nguyên vế `?:`. Bản mới đọc thẳng: "không có self → null; có live → thay `lastLocation`; không → điểm Room". Bảng chân trị y hệt (self thiếu ⇒ `null` ở cả hai nhánh live) |
| 2 | `ui/.../map/MapContract.kt` | KDoc `liveSelfLocation` + `selfLocation` bỏ trùng lặp (mỗi sự kiện nói đúng một lần) | Hai KDoc cạnh nhau cùng giải thích "chưa qua `LocationFilter`" và "`null` cho tới fix đầu tiên" |
| 3 | `ui/.../map/MapViewModel.kt` | Gộp 2 đoạn KDoc nói về "nguồn thứ tư" thành 1 | Đoạn thêm vào lặp lại ý của đoạn đã có |
| 4 | `ui/.../map/component/FamilyTrackerMap.kt` | `val selfPosition = LatLng(selfPoint.latitude, selfPoint.longitude)` dùng chung cho `SelfAccuracyCircle` + `MarkerComposable`; `SelfAccuracyCircle(...)` gói về một dòng | `LatLng` được dựng 2 lần từ cùng một điểm ngay cạnh nhau — hai chỗ có thể lệch nhau khi sửa sau này |
| 5 | `ui/.../map/component/SelfAccuracyCircle.kt` | Sửa comment sai về `zIndex` (xem "Phát hiện" bên dưới); rút gọn KDoc; sửa lại lý do của 4 hằng số cấp file | Comment cũ khẳng định một điều không đúng, và viện dẫn `Dimens` một cách nửa vời |
| 6 | `data/.../location/LiveSelfLocation.kt` | `observe()` trả `_point.asStateFlow()` thay vì `_point` | `MviViewModel.state` (lõi MVI của chính repo) là `_state.asStateFlow()` — đây là idiom "phơi read-only" của codebase. Không đổi hành vi: `.value`/collect y hệt, chỉ chặn caller ép kiểu về `MutableStateFlow` để ghi vào cổng hiển thị (Security Considerations của phase: ":ui chỉ đọc") |
| 7 | `data/.../location/LiveSelfLocation.kt` | KDoc: bỏ lặp, thêm 1 dòng KDoc cho `observe()` | Đoạn 1 và 2 cùng nói lại "không qua LocationFilter" |
| 8 | `data/.../location/LocationPointProcessor.kt` | Rút gọn đoạn KDoc phase-01 (6 → 5 dòng), giữ nguyên cảnh báo "publish trước khi lọc KHÔNG phải chỗ bỏ qua bộ lọc" | Rủi ro Trung bình trong Risk Assessment nằm ở đúng câu này — giữ, chỉ nén |
| 9 | `data/.../di/DataModule.kt` | 2 comment gọn lại; comment ở `TrackingRepositoryImpl` nay nói **cái giá** ("hai instance thì chấm xanh câm") thay vì mô tả cơ chế `singleOf` | Quy ước `.claude/CLAUDE.md`: "state the rule, then the concrete cost" |
| 10 | `domain/.../tracking/LocationFilter.kt` | **Chỉ KDoc.** Bỏ link KDoc `[com.example.pion.family.tracker.demo...data.location.LocationPointProcessor]` → text thường ``​`LocationPointProcessor` ở `:data/location/` `` | `:domain` KHÔNG phụ thuộc `:data` (LLM.md §2) — link đó không bao giờ resolve được. 0 thay đổi logic, `LocationFilterTest` vẫn 7/7 |
| 11 | `data/src/test/.../LocationPointProcessorTest.kt` | KDoc test mới: bỏ đoạn mô tả trạng thái đã cũ ("`LiveSelfLocation` chưa tồn tại, file này compile lỗi"), thay bằng bất biến test khoá; `assertEquals(null, …)` → `assertNull(…)` | KDoc cũ mô tả một khoảnh khắc trong quá khứ, sai với hiện tại. `assertNull` là dạng dùng ở `MapViewModelTest`. Không assertion nào bị nới |
| 12 | `data/src/test/.../RealGpsNoSnapArchitectureTest.kt` | KDoc 20 → 13 dòng; giữ đủ 3 điều: quét cái gì, vì sao không bỏ qua comment, "tồn tại để LẦN SAU đỏ" + trỏ tới bằng chứng mutation ở dev report S6 | Phần thân test không đổi một ký tự |
| 13 | `ui/src/test/.../MapViewModelTest.kt` | Tách `val drawn = vm.state.value.selfLocation!!.lastLocation!!` cho 2 assert toạ độ; nén 2 KDoc | Chuỗi `!!` dài lặp 2 lần trong 2 dòng liền. Assert vẫn đúng dạng S2 đòi (`assertEquals(lat, …, 0.0)`) |

**Không đụng:** `domain/.../repository/TrackingRepository.kt`, `data/.../repository/TrackingRepositoryImpl.kt`.

## Cố ý KHÔNG đụng (kèm lý do)

1. **`observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)` — thân mặc định trên
   interface.** Theo chỉ thị, để `code-reviewer` phán. Ý kiến ở mục riêng bên dưới.
2. **`MapState.liveSelfLocation` là field lưu, không phải `val` suy ra.** Nhìn qua tưởng phạm luật
   "Derive, don't duplicate" (MVI doc §2) vì nó có thể lệch với
   `memberLocations[self].lastLocation`. Thực ra **không** suy ra được từ nhau: hai nguồn dữ liệu
   khác nhau (cổng live trong bộ nhớ vs. Room). Đúng luật là ở chỗ chỉ có **một** `val` suy ra
   (`selfLocation`) hoà giải hai nguồn, và composable đọc `selfLocation` chứ không đọc thẳng field
   thô — đã ghi rõ trong KDoc.
3. **`initialCameraTarget` không tách `val` dùng chung với `selfLocation`.** Nhiệm vụ nêu nghi vấn
   trùng lặp; kiểm tra thì **không có trùng lặp**: `initialCameraTarget` đã đọc
   `selfLocation?.lastLocation` nên nó thừa hưởng ưu tiên-live miễn phí, phần còn lại
   (`?: memberLocations.firstNotNullOfOrNull { … }`) là luật riêng của camera (rơi về thành viên
   bất kỳ), không dùng lại được ở `selfLocation` — marker self mà rơi về điểm của người khác thì
   sai hoàn toàn. Tách thêm một `val` trung gian sẽ **thêm** một khái niệm chứ không bớt.
4. **4 hằng số cấp file trong `SelfAccuracyCircle.kt` — GIỮ.** Mẫu dev viện dẫn là **có thật**, đã
   xác minh: `private const val DEFAULT_ZOOM = 15f` / `SELF_Z_INDEX = 2f` trong `FamilyTrackerMap.kt`
   và `private const val ZONE_LABEL_Z_INDEX = 0.5f` trong `ZoneCircles.kt`. Không phải literal nằm
   trong composable ⇒ không phạm LLM.md §12. (Một nửa nhận xét vẫn cần reviewer — xem "Phát hiện" #2.)
5. **`Member(id = "m-self", …)` lặp 5 lần trong `MapViewModelTest.kt` — GIỮ.** 3/5 lần là code có
   trước phase này; gom vào helper sẽ sửa test không thuộc phase và làm phình diff của reviewer.
   Dựng dữ liệu tại chỗ trong từng test cũng là kiểu viết nhất quán của cả file (không fixture dùng
   chung, không state chia sẻ giữa các ca).
6. **Không gom `FakeTrackingRepository` giữa `MapViewModelTest` (`:ui`) và
   `LocationPointProcessorTest` (`:data`).** Hai module khác nhau, không có test-fixtures source set
   chung; gom lại phải dựng thêm hạ tầng build. LLM.md §11 vốn quy định fake là
   `private class` ngay trong file test, không thư viện mock — hiện trạng đúng quy ước.
7. **Không đụng `Dimens.kt`, `Color.kt`, `TrackingConstants.kt`, `LLM.md`, PRD delta, phase file.**
   Ngoài phạm vi được giao.

## Phát hiện chuyển cho `code-reviewer`

1. **Comment `zIndex` của `SelfAccuracyCircle` trước đây nói sai — đã sửa comment, KHÔNG sửa code.**
   KDoc cũ (và Risk Assessment của phase file) khẳng định vòng sai số "nằm dưới cả `ZoneCircles`"
   nhờ `zIndex = 0f`. Nhưng `ZoneCircles` gọi `Circle(...)` **không truyền `zIndex`** ⇒ mặc định
   cũng là `0f`. Hai overlay cùng mức z, thứ tự vẽ giữa chúng không được Maps SDK bảo đảm — vòng sai
   số có thể nằm **trên** vòng zone. Thứ thật sự giữ cho zone còn nhìn thấy là `FILL_ALPHA = 0.10` +
   `STROKE_WIDTH_PX = 1f`, và KDoc nay nói đúng như vậy. Đây là thay đổi **tài liệu**; đổi `zIndex`
   sẽ là đổi hành vi hiển thị nên tôi không làm. Reviewer quyết định có cần một z-index âm/riêng cho
   vòng sai số không.
2. **`FILL_ALPHA`/`STROKE_ALPHA`/`STROKE_WIDTH_PX` đặt ở đâu là quyết định còn mở.** Với `zIndex`
   thì hằng số cấp file là mẫu đúng (3 tiền lệ). Với alpha/bề rộng nét thì tiền lệ gần nhất lại
   ngược: `ZoneCircles` dùng `Dimens.ZONE_FILL_ALPHA` và `Dimens.ZONE_STROKE_WIDTH_PX`. Dev không
   đụng `Dimens.kt` vì file đó không nằm trong "Related Code Files" của phase — lý do đúng về quy
   trình, nhưng nó để lại hai mẫu khác nhau cho cùng một loại giá trị trong cùng một thư mục
   `component/`. Tôi giữ nguyên (đụng `Dimens.kt` là ra ngoài phạm vi); reviewer nên chốt một mẫu.
3. **`LiveSelfLocation` không có TTL** — đã được ghi là sai lệch có chủ ý ở `LLM.md` §13 Open #14
   (D7). Không phát sinh gì mới từ phía tôi, chỉ nhắc để reviewer không coi là sót.

## Ý kiến về thân mặc định trên interface (không sửa, theo chỉ thị)

`fun observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)`

**Đánh đổi dev chọn là hợp lý cho phase này, nhưng nên có hạn dùng.**

- *Được:* 4 test double ngoài phạm vi phase (`HistoryViewModelTest`, `NavigationViewModelTest`,
  `MapViewModelLaunchSafetyTest`, `StartSimulationUseCaseTest`) không phải sửa; ranh giới sở hữu
  file của phase được tôn trọng; `flowOf(null)` đúng nghĩa "cổng này chưa phát gì", là giá trị mà
  mọi ViewModel không quan tâm tới vị trí self đều xử lý đúng.
- *Mất:* Đây là **một mặc định im lặng trên một cổng dữ liệu**. Một
  `TrackingRepository` implementation thật sự thứ hai (giả sử phase sau thêm một impl cho chế độ mô
  phỏng) sẽ **im lặng** phát `null` mãi mãi thay vì đỏ lúc compile. Cái giá đúng bằng "chấm xanh
  không bao giờ hiện, không lỗi, không log" — chính là lớp lỗi mà phase 01 sinh ra để sửa.
- *Đề xuất, nếu reviewer muốn đóng:* bỏ default, khai abstract, và cho 4 fake kia một dòng
  `override fun observeLiveSelfLocation() = flowOf(null)` — 4 dòng, 4 file, compiler chỉ mặt đúng
  chỗ. Việc này **phải là một task riêng** (4 file ngoài "Related Code Files" của phase 01), không
  phải một sửa lén trong phase này.

## Nghiệm thu

```
./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache
BUILD SUCCESSFUL
tests=208 failures=0 errors=0   (đếm từ TEST-*.xml của cả 4 module)
```

Compile riêng `:domain:compileKotlin :data:compileDebugKotlin :ui:compileDebugKotlin`: BUILD
SUCCESSFUL. `RealGpsNoSnapArchitectureTest` vẫn xanh sau khi tôi sửa comment trong
`data/location/` (không comment mới nào chứa từ khoá bị cấm).

## Không làm

- Không `git commit` / `git push`.
- Không đụng file ngoài danh sách phase 01; không đụng `LLM.md`, PRD delta, phase file.
- Không tạo file mới, không file "v2"/"enhanced".
- Không xoá test, không nới assertion, không đổi con số 208.
