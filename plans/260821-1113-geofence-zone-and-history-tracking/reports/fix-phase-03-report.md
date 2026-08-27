# Fix Report — Phase 03 (G2): M1 test biên + M4 luật lastKept chưa được bảo vệ

Ngày: 2026-08-21 · Agent: debugger (fix) · Env: `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_SERIAL=emulator-5554`

## Việc 1 — M1: bằng chứng đỏ-rồi-xanh

### Bước 0 — tái hiện chẩn đoán TRƯỚC khi sửa (không tin report cũ, tự chạy lại)

Mutate `ZoneEvaluator.kt:54` `<` → `<=`, chạy `:domain:test --tests '*ZoneEvaluatorTest*'`:

```
BUILD SUCCESSFUL in 636ms
```

XML kết quả: `tests="6" failures="0"` — **0 test đỏ**, khớp đúng chẩn đoán trong
`test-phase-03-report.md` (M1 MISSED). Khôi phục file gốc, chạy lại xác nhận xanh trước khi sửa.

### Cách sửa đã áp dụng

Tách so sánh biên ra 2 hàm thuần `internal fun` nhận thẳng `Double`, không đi qua toạ độ/Haversine:

```kotlin
internal fun entersAt(distanceMeters: Double, radiusMeters: Double): Boolean =
    distanceMeters < radiusMeters

internal fun exitsAt(distanceMeters: Double, radiusMeters: Double): Boolean =
    distanceMeters > radiusMeters + TrackingConstants.ZONE_EXIT_BUFFER_M
```

`evaluate()` gọi 2 hàm này thay vì so sánh inline (`ZoneEvaluator.kt`).

Test mới nạp thẳng `Double`, phân biệt `<` vs `<=` tất định:
- `entersAt exactly at the radius is NOT an entry, boundary is exclusive` — `entersAt(100.0, 100.0) == false`
- `exitsAt exactly at radius plus buffer is NOT an exit, boundary is exclusive` — biên `radius + ZONE_EXIT_BUFFER_M`

2 test cũ **đổi tên** (không xoá, không đổi assertion) để phản ánh đúng thứ chúng thật sự khoá
(hành vi ở mép trong độ chính xác round-trip toạ độ, không phải biên `<`/`<=` toán học):
- `standing exactly at the radius produces no event, from outside` → `standing at radius within coordinate round-trip precision produces no event, from outside`
- (tương tự cho `..., from inside`)

### Bước xác nhận — mutate LẠI sau khi sửa

```
$ sed -i 's/distanceMeters < radiusMeters/distanceMeters <= radiusMeters/' ZoneEvaluator.kt
$ ./gradlew :domain:test --tests '*ZoneEvaluatorTest*'
> Task :domain:test FAILED
ZoneEvaluatorTest > entersAt exactly at the radius is NOT an entry, boundary is exclusive FAILED
    java.lang.AssertionError at ZoneEvaluatorTest.kt:56
8 tests completed, 1 failed
BUILD FAILED
```

**1 test đỏ, đúng test mới viết cho biên này.** Khôi phục file, chạy lại:

```
$ ./gradlew :domain:test
BUILD SUCCESSFUL in 531ms
```

Xanh lại. Mutation M1 giờ bị bắt tất định — không phụ thuộc JVM/CPU nào làm tròn double ra sao.

### Kết quả `:domain:test` sau Việc 1

34 test (tăng từ 32 → 34, thêm đúng 2 test biên mới, không xoá test nào), 0 fail:

| Test class | Số test |
|---|---|
| `GeoDistanceTest` | 5 |
| `LocationFilterTest` | 7 |
| `ZoneEvaluatorTest` | 8 (6 cũ, 2 tên đổi + 2 mới) |
| `RouteSplitterTest` | 5 |
| `RouteStatsTest` | 2 |
| `ZoneEventDeduperTest` | 4 |
| `SaveZoneUseCaseTest` | 3 |

File đổi: `domain/src/main/kotlin/.../tracking/ZoneEvaluator.kt`,
`domain/src/test/kotlin/.../tracking/ZoneEvaluatorTest.kt`.

---

## Việc 2 — M4: luật `lastKept` biến thành yêu cầu bắt buộc cho phase-04

### Đánh giá 3 lựa chọn đề bài đưa ra

1. **Ghi vào phase-04 doc (Requirements/Key Insights)** — chọn.
2. **Ghi vào `LLM.md` §8.3** — cũng chọn, cùng lúc (không loại trừ nhau, cả hai đều là sửa doc,
   không phải refactor code).
3. **Type-level (wrapper type bọc "điểm đã giữ")** — **không chọn.** Lý do: một `data class
   KeptPoint(val point: LocationPoint)` không thực sự ngăn được lỗi — caller vẫn có thể gói bất kỳ
   biến nào (kể cả "điểm vừa nhận") vào `KeptPoint(...)`. Bất biến thật ("biến này chỉ được gán từ
   nhánh Accept") là một ràng buộc runtime, không phải ràng buộc kiểu — kiểu dữ liệu không dịch
   được luật đó. Thêm type mới chỉ đổi chỗ đọc code phải nhìn (từ tên biến sang tên type) mà không
   tăng an toàn tương xứng với việc phải sửa chữ ký `LocationFilter.accept()`, `LocationFilterTest`
   (32→34 test đã viết ở phase-03), và mọi call site tương lai — vi phạm "đừng vẽ thêm trừu tượng
   cho một dự án demo" trong đề bài.

### Đã ghi ở đâu

**`LLM.md` §8.3`** — thêm đoạn luật kiến trúc ngay sau bảng 3 luật lọc GPS: nêu rõ `lastKept` PHẢI
là điểm cuối `accept()` từng trả `Accept`, không phải điểm cuối nhận từ `LocationSource`; hậu quả
(người đi bộ chậm bị `Reject(DISTANCE)` vĩnh viễn, lộ ra ở phase-08 dưới dạng "History trống", không
ai truy ngược được); trỏ tới test hiện có (`LocationFilterTest`) và nói rõ test đó **không** kiểm
tra được việc service threading đúng — đó là việc của phase-04.

**`phase-04-permissions-and-tracking-service.md`** — 4 chỗ:
- Key Insight #10 (mới): luật + hậu quả + cách tự kiểm (viết test cho `LocationTrackingService` mô
  phỏng chuỗi điểm <10m so với điểm ngay trước nhưng ≥10m so với điểm được giữ gần nhất).
- Requirements: thêm dòng "Service giữ `lastKeptPoint` ... chỉ cập nhật khi `Accept`".
- Implementation Step 9: cụ thể hoá — giữ `lastKeptPoint: LocationPoint?` bên cạnh `insideZoneIds`
  đã có sẵn trong step, cập nhật CHỈ trong nhánh `Accept`.
- Todo List: thêm dòng tự kiểm tương ứng để không bị bỏ sót khi tick xong phase-04.

Không viết `LocationTrackingService` — đúng phạm vi giao (đó là việc của phase-04).

---

## Nghiệm thu chung — output thật

```
$ ./gradlew :domain:test
BUILD SUCCESSFUL in 587ms   # 34/34, 0 fail

$ ./gradlew test
BUILD SUCCESSFUL in 4s      # :domain, :ui, :data (NO-SOURCE, đúng phạm vi), :app (KoinModulesTest) đều xanh

$ ./gradlew clean assembleDebug --no-configuration-cache 2>&1 | grep -ci "warning:"
1                            # khớp baseline ENV-BRIEFING.md §8

$ ./gradlew assembleRelease
BUILD SUCCESSFUL in 13s

$ grep -rn "import android\|import androidx" domain/src
(rỗng)                       # :domain vẫn thuần Kotlin JVM

$ git status --short
(chỉ còn thay đổi thật của 2 việc trên + các file untracked sẵn có từ phase-03 chưa commit —
 không còn mutation nào sót lại; đã dọn /tmp)
```

## Chỗ tôi thấy chẩn đoán ban đầu đúng, không có chỗ nào sai

Tự chạy lại độc lập cả M1 (đỏ khi mutate, xanh khi khôi phục — trước VÀ sau khi sửa) lẫn đọc code
M4 (`LocationFilter.accept` đúng là hàm thuần nhận `lastKept` làm tham số, `LocationTrackingService`
đúng là chưa tồn tại, KDoc `LocationFilter.kt:18-20` đúng là mô tả đúng hậu quả) — không phát hiện
sai lệch nào so với đề bài.

## File đã đổi

- `domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/tracking/ZoneEvaluator.kt`
- `domain/src/test/kotlin/com/example/pion/family/tracker/demo/domain/tracking/ZoneEvaluatorTest.kt`
- `LLM.md` (§8.3)
- `plans/260821-1113-geofence-zone-and-history-tracking/phase-04-permissions-and-tracking-service.md`

## Docs impact

**Minor.** Không đổi kiến trúc, không đổi package layout — chỉ thêm 1 đoạn luật vào `LLM.md` §8.3
(đã làm cùng report này) và 4 chỗ vào phase-04 doc. Không cần đổi `LLM.md` §3/§11 vì không có file
mới, không có convention test mới ngoài phạm vi đã có.

## Câu hỏi còn treo

Không có.
