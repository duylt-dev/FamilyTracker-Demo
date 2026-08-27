# Simplifier — Phase 03 (nội suy marker ở tầng hiển thị)

Ngày: 2026-08-25 · Phạm vi: code phase-03 vừa viết (`:ui` trước), cộng một lượt nhẹ trên test phase-02.
Chức năng: **không đổi**. Không commit, không `git add`, không `adb`, không tạo file mới ngoài report này.

---

## 1. Kết quả nghiệm thu (chạy thật, sau khi đã sửa xong)

```
$ ./gradlew :ui:test :domain:test :data:test :app:assembleDebug --no-configuration-cache --rerun-tasks
> Task :domain:compileTestKotlin
> Task :domain:testClasses UP-TO-DATE
> Task :domain:test
> Task :ui:compileDebugKotlin
> Task :data:compileDebugKotlin
> Task :data:testDebugUnitTest
> Task :data:test
> Task :ui:testDebugUnitTest
> Task :ui:test
> Task :app:assembleDebug
BUILD SUCCESSFUL in 12s
92 actionable tasks: 92 executed
```

Đếm từ chính JUnit XML (`*/build/test-results/**/*.xml`) của lần chạy đó:

```
ui:     suites=11 tests=97  failures=0 errors=0 skipped=0
domain: suites=21 tests=125 failures=0 errors=0 skipped=0
data:   suites=8  tests=43  failures=0 errors=0 skipped=0
                  ------------------
                  265 test, 0 đỏ
MarkerInterpolationTest: tests=16 failures=0 errors=0
```

Hai lệnh bắt buộc chạy riêng cũng xanh:

```
$ ./gradlew :ui:test :domain:test :data:test --no-configuration-cache
BUILD SUCCESSFUL in 9s
36 actionable tasks: 36 executed

$ ./gradlew :app:assembleDebug --no-configuration-cache
BUILD SUCCESSFUL in 693ms
77 actionable tasks: 77 up-to-date
```

Không có bước nào phải hoàn nguyên — build chưa từng đỏ trong phiên này.

---

## 2. Từng thay đổi

### 2.1 Gộp phép dựng `MarkerSample` về MỘT chỗ (đúng thứ được yêu cầu tìm)

`MemberMarkers.kt` và `FamilyTrackerMap.kt` đang chép nguyên xi cùng năm dòng gán field, kể cả
phép `recordedAt.toEpochMilli()` — đúng loại trùng lặp mà thêm một field vào `MarkerSample` sẽ làm
lệch nhau.

**Trước** — `MemberMarkers.kt`:

```kotlin
val samples = members.mapNotNull { memberLocation ->
    val location = memberLocation.lastLocation ?: return@mapNotNull null
    MarkerSample(
        id = memberLocation.member.id,
        latitude = location.latitude,
        longitude = location.longitude,
        bearingDegrees = location.bearingDegrees,
        recordedAtMs = location.recordedAt.toEpochMilli(),
    )
}
```

**Trước** — `FamilyTrackerMap.kt`: cùng khối đó, 13 dòng `if/else` chỉ để bọc một phần tử.

**Sau** — một extension `internal` đặt ngay cạnh `MarkerSample` trong `AnimatedMarkerPositions.kt`:

```kotlin
internal fun LocationPoint.toMarkerSample(id: String): MarkerSample =
    MarkerSample(id, latitude, longitude, bearingDegrees, recordedAt.toEpochMilli())
```

hai call site còn đúng một dòng mỗi bên:

```kotlin
val samples = members.mapNotNull { it.lastLocation?.toMarkerSample(id = it.member.id) }      // MemberMarkers.kt
val selfSamples = listOfNotNull(selfPoint?.toMarkerSample(id = SELF_MARKER_ID))              // FamilyTrackerMap.kt
```

**Lý do:** một chỗ duy nhất biết cách quy đổi `LocationPoint` → `MarkerSample`; không còn hai bản
sao để lệch. Dạng extension (thay vì `markerSampleOf(id, point)`) rút luôn `?.let { ... }` ở cả hai
call site, nên tổng số dòng giảm ở cả ba file.

**Không phá ràng buộc nào:** `MarkerSample` vẫn chỉ primitive/`String`; `LocationPoint` chỉ **đi
qua** hàm, không bao giờ được giữ trong state animation; `AnimatedMarkerPosition` không đổi; hàm
không phải `@Composable` nên không dính suy luận stability. KDoc của hàm nói rõ hai điều đó.

### 2.2 `AnimatedMarkerPositions.kt` — dọn nhánh xoá id đã biến mất

**Trước:**

```kotlin
val trackedIds = motions.keys + displayed.keys + lastApplied.keys
for (id in trackedIds) {
    if (id !in currentIds) {
        motions.remove(id)
        displayed.remove(id)
        lastApplied.remove(id)
    }
}
```

**Sau:**

```kotlin
for (id in motions.keys + displayed.keys + lastApplied.keys) {
    if (id in currentIds) continue
    motions.remove(id)
    displayed.remove(id)
    lastApplied.remove(id)
}
```

**Lý do:** guard clause thay cho một tầng lồng; biến `trackedIds` chỉ dùng đúng một lần ở dòng ngay
dưới nên không thêm thông tin gì. Hợp nhất ba map vẫn **giữ nguyên** (không rút về mỗi
`lastApplied.keys` dù về mặt bất biến là đủ) — đó là lưới chống rò rỉ của FR-6, rẻ và không nên
mỏng đi vì một dòng.

### 2.3 `AnimatedMarkerPositions.kt` — gói `haversineMeters(...)` từ 6 dòng xuống 3

**Trước:** mỗi tham số một dòng (`previousDisplayed.latitude`, `previousDisplayed.longitude`,
`sample.latitude`, `sample.longitude`).
**Sau:** hai cặp "từ điểm đang hiển thị → tới mẫu mới" trên cùng một dòng.
**Lý do:** bốn tham số đọc thành một câu ("từ đây tới kia"), tách rời làm mất đúng cấu trúc cặp đó.

### 2.4 `AnimatedMarkerPositions.kt` — bỏ 2 comment nội dòng lặp lại KDoc

Xoá `// id mới hoàn toàn — …` và `// Quyết định (A), F-6 — snap vị trí, GIỮ bearing …`.
**Lý do:** KDoc của `rememberAnimatedMarkerPositions` ngay phía trên đã liệt kê **đúng bốn nhánh
đó** dưới dạng bullet, chữ gần như trùng khít. **Lý do không mất** — nó nằm nguyên trong KDoc, và
tên `isSpawnJump`/`SPAWN_SNAP_THRESHOLD_M` ngay tại nhánh đó vẫn dẫn thẳng tới F-6.

### 2.5 `MemberMarkers.kt` — `HeadingIndicator` không lặp lại kích thước chấm

**Trước:** `HeadingIndicator(modifier = Modifier.size(Dimens.MemberDotSize))` — trong khi `Box` cha
đã `.size(Dimens.MemberDotSize)`.
**Sau:** `HeadingIndicator(modifier = Modifier.fillMaxSize())`.

**Lý do:** hai chỗ khai cùng một kích thước là hai chỗ để lệch, mà lệch kích thước bitmap chính là
thứ KDoc `MEMBER_MARKER_ANCHOR` cảnh báo (đổi bound ⇒ đổi hình học mà `anchor`/`rotation` tính
trên đó). `Box` có `size` cố định nên constraint truyền xuống là `0..MemberDotSize`;
`fillMaxSize()` lấy đúng `maxWidth/maxHeight` ⇒ **cùng một px** với `size(MemberDotSize)`, tam giác
vẽ y hệt. KDoc của `HeadingIndicator` đã cập nhật để nói ràng buộc "bound luôn bằng bound của
`MemberDot`" thay vì chỉ mô tả.

### 2.6 `FamilyTrackerMap.kt` — tách tên "vị trí đã nội suy" khỏi "toạ độ Maps SDK"

**Trước:** `animatedSelf` (map) / `animatedSelfPosition` (phần tử) / `selfPosition` (`LatLng`).
**Sau:** `animatedSelfPositions` (map) / `selfPosition` (`AnimatedMarkerPosition`) / `selfLatLng`
(`LatLng`).

**Lý do:** `animatedSelf` vs `animatedSelfPosition` khác nhau đúng một chữ cái cuối — dễ đọc nhầm.
Tên mới nói đúng KIỂU: `selfPosition` là primitive đã nội suy, `selfLatLng` là kiểu của Maps SDK
dựng tại chỗ dùng (đúng luật "dựng `LatLng` ngay tại nơi dùng"). Map đặt số nhiều cho khớp
`animatedPositions` bên `MemberMarkers.kt`. KDoc đầu file đã sửa theo tên mới.

### 2.7 `MarkerInterpolationTest.kt` — `assertTrue(!x)` → `assertFalse(x)`

Hai chỗ (`isSpawnJump is false for a normal continuous tick step`, và vế đầu của
`isSpawnJump is exclusive at the threshold boundary`).
**Lý do:** phủ định trong `assertTrue` làm thông báo lỗi của JUnit nói ngược ("expected true") so
với ý định; `assertFalse` in đúng. Không đổi ca kiểm nào, không thêm/bớt assertion.

### 2.8 `MemberRoamerTest.kt` — cắt đoạn trích nguyên văn mã sản phẩm trong comment "bia mộ"

**Trước:** comment giải thích vì sao ca `does not dither` bị xoá có chép nguyên hai dòng thân hàm
`MemberRoamer.withPath` vào trong comment.
**Sau:** giữ nguyên toàn bộ lập luận (bản sao assertion yếu → không thể đỏ một mình; test thật cho
F-5 cần `withPath` gọi `RouteGeometryGuard`; hôm nay chưa gọi nên ca đó sẽ đạt vô điều kiện; việc
nối dây thuộc phase-04), **chỉ bỏ đoạn trích mã**.
**Lý do:** chép mã sản phẩm vào comment ở file khác là một bản sao sẽ mốc ngay khi phase-04 nối
dây guard — đúng lúc người đọc cần comment này nhất thì nó nói sai. Câu khẳng định "`withPath`
KHÔNG gọi guard" vẫn còn, kèm chứng cứ độc lập (KDoc của `RouteGeometryGuard`: "Chưa có người gọi
thật trong phase-02").

---

## 3. Cố ý KHÔNG đụng (quan trọng ngang phần đã sửa)

| Thứ | Vì sao để nguyên |
|---|---|
| `flat = true`, `anchor = Offset(0.5f, 0.5f)` | Đã nghiệm thu bằng thực nghiệm trên máy thật (sai số 1.2° so với dự đoán; billboard lệch 62.8°). Không có lý do "gọn hơn" nào đáng đổi một con số đã đo. |
| MỘT `withFrameNanos` loop / lần gọi | Kiến trúc lõi + số đo thật (5360 khung/60s @90Hz, jank 0.62%). Không gộp, không tách. |
| `from` khi retarget = vị trí ĐANG hiển thị | Chống giật lùi. Không đụng. |
| `progressOf` chặn `[0,1]`, `durationMs <= 0 → 1f` | Cơ chế cấm ngoại suy. Ba `if` guard đọc thẳng, không "gọn" bằng `coerceIn` (sẽ chia cho 0 trước khi chặn). |
| `keys = arrayOf(member.id)` / `arrayOf(SELF_MARKER_ID)` | Đưa vị trí vào keys = chụp lại bitmap mỗi khung. |
| Chấm xanh self không có `rotation` | Key Insight #8. |
| `SPAWN_SNAP_THRESHOLD_M`, `NORMAL_TICK_STEP_M` và nhánh snap giữ bearing cũ | Bản sửa F-6 chủ dự án đã chốt. KDoc dẫn xuất ngưỡng (20.75 → 207.5, cận dưới spawn 2 080 m) giữ nguyên từng chữ. |
| `Dimens.kt` | Chỉ đọc. Không sửa/xoá hằng số nào; cũng **không thêm** gì mới ngoài hai hằng dev đã thêm. |
| `TrackingConstants` | Không chạm, không thêm hằng. |
| `MarkerInterpolation.kt` | Thuần JVM, 90 dòng, mỗi hàm một việc — không có gì để rút. Không thêm import nào (kể cả `LocationPoint`: `toMarkerSample` cố ý đặt ở `AnimatedMarkerPositions.kt` cạnh `MarkerSample`, không kéo `:domain` vào file toán học). |
| KDoc `haversineMeters` giải thích vì sao nhân bản `GeoDistance` | Đúng loại lý do phải giữ. |
| Trùng lặp KDoc F-6 giữa `isSpawnJump` (`MarkerInterpolation.kt`) và `SPAWN_SNAP_THRESHOLD_M` (`AnimatedMarkerPositions.kt`) | **Có trùng thật**, nhưng `MarkerInterpolation.kt` là file thuần JVM phải đọc được độc lập; bắt người đọc mở file Compose mới hiểu vì sao hàm tồn tại là đắt hơn hai đoạn văn trùng. Để nguyên có chủ ý. |
| Vòng lặp khung hình: `finishedIds` cấp phát mỗi khung | Đổi sang `iterator.remove()` không giảm dòng (bỏ 2, thêm 2) và làm khó đọc hơn; một `ArrayList` rỗng mỗi khung không đáng đánh đổi. |
| Tách `SelfMarker` thành composable riêng trong `FamilyTrackerMap.kt` | **Đã cân nhắc và bỏ.** Muốn giữ hành vi y hệt thì phải hoist `rememberAnimatedMarkerPositions` ở lại hàm cha (nếu để bên trong `if`, state animation sẽ reset mỗi lần `selfPoint` null → khác hành vi). Phần còn lại chỉ là bơm 3 tham số; file đang 141 dòng, dưới ngưỡng. Không đáng rủi ro. |
| `if (self != null && selfPoint != null && selfPosition != null)` | Nhìn ồn nhưng **cả ba vế đều cần**: `self` cho smart-cast `self.member.colorArgb`, `selfPoint` cho `accuracyMeters` (mẫu THẬT, không nội suy), `selfPosition` vì map có thể còn giữ entry "self" thêm một khung sau khi `selfPoint` thành null (việc xoá chạy trong `LaunchedEffect`, bất đồng bộ). Rút bất kỳ vế nào cũng là đổi hành vi hoặc phải bịa một biến trung gian dài hơn. |
| Helper `longestConsecutiveZeroBearingRun` nằm giữa hai `@Test` trong `MemberMovementSimulatorTest.kt` | Đặt ngay dưới call site duy nhất, đọc liền mạch. Dời xuống cuối file chỉ là churn. |
| `PolylineFollowerTest`: `while (tick < TICKS_FOR_S1) { … ; if (finished) break }` | Đây chính là bản sửa F-7 (`return@repeat` = `continue`). Ý định khớp với đọc rồi. |
| `assertTrue("… đã nhảy qua một đỉnh …", !skippedVertex)` trong `PolylineFollowerTest` | Cùng bệnh với 2.7 nhưng là **dòng có sẵn từ trước phase-03**, không nằm trong diff đang rà. Để yên cho diff sạch; ghi lại ở mục 4 dưới. |

---

## 4. Phát hiện nhưng KHÔNG sửa

1. **`AnimatedMarkerPositions.kt` sát trần 200 dòng (199).** LLM.md §5 buộc tách file quá 200 dòng.
   Bản dev giao là 196; sau khi thêm `toMarkerSample` và dọn 4 chỗ ở trên còn **199**. Còn đúng
   1 dòng margin — lần thêm KDoc kế tiếp sẽ vượt. Trong repo `:domain/tracking/MemberRoamer.kt`
   **đã 204 dòng** (vượt sẵn, không thấy nằm trong §13). Đề xuất: hoặc ghi cả hai vào LLM.md §13
   Open, hoặc tách `MarkerSample`/`AnimatedMarkerPosition`/`toMarkerSample` sang file riêng ở một
   phase sau — **tôi không tự tách vì bị cấm tạo file mới.**
2. **`isSpawnJump(distance, threshold)` không được gọi ở đâu khác ngoài một chỗ**, và ngưỡng thật
   `207.5` bị chép tay vào 5 chỗ trong `MarkerInterpolationTest.kt`. Đây là hệ quả cố ý của việc
   `SPAWN_SNAP_THRESHOLD_M` là `private` (đúng yêu cầu phase-03), nhưng nghĩa là **test không khoá
   được giá trị ngưỡng thật** — chỉ khoá phép so sánh. Nếu ai sửa `NORMAL_TICK_STEP_M` từ 20.75
   thành số khác, toàn bộ test vẫn xanh. Không phải bug hôm nay; là một lỗ nghiệm thu cần biết.
3. **Nhánh `previousSample != null && previousDisplayed == null` không có đường tới.** `displayed`
   và `lastApplied` luôn được ghi/xoá cùng nhau, nên vế `previousDisplayed == null` chỉ phòng thủ.
   Vô hại (fallback là vẽ thẳng, đúng thứ muốn), nhưng nó là nhánh không test nào chạm tới.
4. **`assertTrue("…", !skippedVertex)` (`PolylineFollowerTest.kt` dòng 85)** — cùng dạng assertion
   phủ định đã sửa ở 2.7, nhưng là code có trước phase-03. Nên đổi sang `assertFalse` khi ai đó
   chạm vào ca đó.
5. **`selfPoint.accuracyMeters` không nội suy trong khi vị trí thì có.** Đúng ý FR-2 và đã ghi
   comment. Hệ quả nhìn thấy được: lúc bán kính sai số đổi, vòng tròn đổi kích thước **tức thì**
   trong khi tâm đang trượt mượt. Không phải lỗi — ghi lại để lần verify trên máy thật tiếp theo
   không báo nhầm thành regression.

Không phát hiện bug chức năng nào trong code phase-03.

---

## 5. Số dòng cuối của mỗi file đã đụng

| File | Dòng |
|---|---|
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/AnimatedMarkerPositions.kt` | **199** (was 196) |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/MemberMarkers.kt` | **117** (was 124 — dưới 200 ✅) |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/FamilyTrackerMap.kt` | **141** (was 153) |
| `ui/src/test/java/com/example/pion/family/tracker/demo/ui/core/motion/MarkerInterpolationTest.kt` | **153** (was 152 — thêm 1 dòng `import assertFalse`) |
| `domain/src/test/kotlin/com/example/pion/family/tracker/demo/domain/tracking/MemberRoamerTest.kt` | **313** (was 316) |

Không đụng: `MarkerInterpolation.kt` (90), `Dimens.kt` (52), `PolylineFollowerTest.kt` (186),
`MemberMovementSimulatorTest.kt` (280), `LLM.md`, PRD delta, mọi `src/main` của `:domain`/`:data`.

Tổng: mã sản phẩm `:ui` giảm 16 dòng, không mất một câu lý do nào.
