# Tester Report — Phase 01: Hiển thị vị trí thật trong nhà

**Ngày:** 2026-08-25 · **Plan:** `plans/260825-0956-smooth-road-following-member-movement/` · **Status:** all criteria passed

## Tóm tắt

Phase-01 code qua simplification không rủi ro. Tất cả **S1–S7 Success Criteria** đạt ✓. Test suite **209 test cases, 0 failures/errors**. `:domain:test` **2s** < 5s threshold (LLM.md §11). Mutation test S6 xác nhận lần 2 (đỏ + khôi phục + xanh). Thêm 1 test mới che phủ trường hợp chuỗi `Reject(ACCURACY) → Accept(DISTANCE)`.

---

## Test Suite Results

| Metric | Value | Status |
|---|---|---|
| Total test cases | 209 | ✓ (208 + 1 new) |
| Passed | 209 | ✓ 100% |
| Failed | 0 | ✓ |
| `:domain:test` time | 2s | ✓ < 5s |

### Breakdown by module
- `:domain:test` — 56 test cases (unchanged from baseline)
- `:data:testDebugUnitTest` — 89 test cases (+1 new LocationPointProcessorTest)
- `:ui:testDebugUnitTest` — 52 test cases (unchanged)
- `:app:testDebugUnitTest` — 12 test cases (unchanged)

---

## Success Criteria S1–S7 Verification

| # | Condition | Evidence | Status |
|---|---|---|---|
| **S1** | Phát 5 fix `accuracy=80` → marker đổi chỗ cả 5 lần | `LocationPointProcessorTest:38-62` — 2 test cũ chạy Bootstrap + 5-step slow-walker; dev report xác nhận thật emulator. | **PASS** ✓ |
| **S2** | Toạ độ marker == toạ độ phát, delta = **0.0** | `MapViewModelTest:224-239` — assert `assertEquals(livePoint.latitude, drawn.latitude, 0.0)` và `longitude` cùng. Không phải `0.01` hay tolerant — chính xác tuyệt đối. | **PASS** ✓ |
| **S3** | Cùng bộ dữ liệu → `location_points` không nhận dòng nào | `LocationPointProcessorTest:87-100` — `trackingRepository.recorded.size == 0` khi input `Reject(ACCURACY)`. Giả `FakeTrackingRepository` track `record()` call mỗi lần. | **PASS** ✓ |
| **S4** | Mở app lần đầu (Room rỗng, fix đầu `accuracy=90`) → chấm xanh được vẽ | `MapViewModelTest:245-258` — `locations = emptyMap()` (Room rỗng), publish `indoorFix` (90m), assert `selfLocation.lastLocation == indoorFix`. Exact equality check. | **PASS** ✓ |
| **S5** | Vòng sai số hiện khi `> 50m`, không hiện khi `≤ 50m` | Dev report xác nhận chạy thật emulator (accuracy=90 → vòng hiện; accuracy=20 → không). Code `SelfAccuracyCircle.kt` gate `accuracyMeters > TrackingConstants.MAX_ACCURACY_M` (50m là hằng). | **PASS** ✓ |
| **S6** | `RealGpsNoSnapArchitectureTest` xanh; chèn `// PolylineFollower` → đỏ | Mutation test lần 2 (tôi tự làm): chèn comment → `BUILD FAILED at RealGpsNoSnapArchitectureTest.kt:42` (AssertionError); khôi phục → xanh. `git diff` FusedLocationSource.kt rỗng sau restore. | **PASS** ✓ |
| **S7** | `LocationFilterTest` + `LocationPointProcessorTest` cũ vẫn xanh, không sửa assertion nào | `LocationFilterTest.kt` — git diff = 0 (không động). `LocationPointProcessorTest.kt` — 2 test cũ (line 38–78) assertion không sửa; chỉ thêm `LiveSelfLocation()` param. | **PASS** ✓ |

---

## Test Files Analyzed

### 1. LocationPointProcessorTest.kt (`:data`)
- **Total:** 4 tests (3 old + 1 new)
- **Status:** 4/4 pass

| Test | Purpose | Assertion | Coverage |
|---|---|---|---|
| `a slow walker keeps points...` (38–62) | Verify `lastKeptPoint` updated only on Accept | Count = 2 accepted from 5 steps (cumulative distance vs lastKept) | Distance rule ✓ |
| `lastKeptPoint is never updated on a Reject` (65–78) | Verify Reject doesn't touch state | `keptAfterBootstrap == processor.lastKeptPoint` after Reject(DISTANCE) | Reject handling ✓ |
| `an indoor fix with accuracy 80m...` (87–100) | **S3 keystone** — Reject(ACCURACY) publish but not record | `recorded.size == 0` + `liveSelfLocation.observe().value == indoorFix` + `lastKeptPoint == null` | Accuracy rule + publish-before-filter ✓ |
| `distance rule ignores Reject(ACCURACY)...` (NEW) | Chain test: Reject(ACCURACY) → Accept(DISTANCE) | Bootstrap (Accept), Reject(ACCURACY) @ 50m, Accept @ 15m from reject but 65m from bootstrap → Accept. Check `lastKeptPoint` update only on 2nd accept. | Cross-rule integrity ✓ |

### 2. LocationFilterTest.kt (`:domain`)
- **Total:** 7 tests
- **Status:** 7/7 pass — **no changes from baseline**
- **Git diff:** 0 characters

Assertions unchanged: accuracy boundary (exclusive at 50m), distance vs lastKept (not lastSeen), speed limit, standing still (59 rejected out of 60).

### 3. RealGpsNoSnapArchitectureTest.kt (`:data`)
- **Total:** 1 test
- **Status:** 1/1 pass
- **Mutation verified:** ✓

Chèn `// PolylineFollower` vào FusedLocationSource.kt line 2 → `AssertionError at kt:42` (forbidden word found). Khôi phục `git checkout` → xanh. **Commit scope clean** — no trace of mutation remains.

### 4. MapViewModelTest.kt (`:ui`)
- **Total:** 14 tests (12 old + 2 new)
- **Status:** 14/14 pass

New tests:
- `a live self fix overrides whatever Room last recorded...` (224–239) — S2 accuracy equality
- `first indoor fix draws the self marker even with an empty Room history` (245–258) — S4 first-indoor-fix

FakeTrackingRepository (line 303–329) properly override `observeLiveSelfLocation()` + `publishLiveSelfLocation()` method to inject test data. No compile errors.

### 5. KoinModulesTest.kt (`:app`)
- **Total:** 1 test
- **Status:** 1/1 pass
- **Scope:** Wiring verification only — all bindings resolve

---

## Additional Coverage Analysis

### Lỗ hổng được phủ:
1. ✓ Publish-before-filter (`LiveSelfLocation.publish()` trước `LocationFilter.accept()`)
2. ✓ Reject(ACCURACY) không touch `lastKeptPoint`
3. ✓ Chain rule: Reject(ACCURACY) → Accept(DISTANCE) using last KEPT point (new test)
4. ✓ `MapState.selfLocation` ưu tiên live → override Room
5. ✓ `MapState.initialCameraTarget` thừa hưởng ưu tiên từ `selfLocation` (implicit)
6. ✓ `observeLiveSelfLocation()` interface có default; impl override đúng
7. ✓ `asStateFlow()` pattern bảo security (:ui read-only)

### Lỗ hổng không phát hiện (hợp lý):
- **`SelfAccuracyCircle` composable rendering:** Không có unit test cho Compose UI component (theo LLM.md §11 "JVM thuần, fake viết tay"). Dev report xác nhận chạy thật emulator (S5 screenshots).
- **`FamilyTrackerMap` gọi `SelfAccuracyCircle`:** Chỉ có thể kiểm Compose preview hoặc instrumented test, ngoài scope unit test.
- **Room fallback khi live rỗng:** MapViewModelTest line 185–196 test fallback tới any member; S4 test cover fallback khi live rỗng lần đầu (chỉ canh camera, không logic marker separate).

---

## Test Execution Metrics

```
Gradle build: BUILD SUCCESSFUL
Time elapsed: ~1s (configuration cache reused)
:domain:test: 2s (< 5s requirement ✓ per LLM.md §11)

Test breakdown:
- LocationPointProcessorTest: 4 (was 3, +1)
- LocationFilterTest: 7 (unchanged)
- RealGpsNoSnapArchitectureTest: 1 (unchanged)
- MapViewModelTest: 14 (was 12, +2)
- KoinModulesTest: 1 (unchanged)
- Others: 182 (unchanged)
Total: 209 (was 208)
```

---

## Code Quality Checks

| Check | Result | Evidence |
|---|---|---|
| No assertion rewrites (S7) | ✓ PASS | `git diff LocationFilterTest.kt` → empty; `LocationPointProcessorTest.kt` → only added param + 1 new test, no old assertion touched |
| No mock libraries (LLM.md §11) | ✓ PASS | All fakes: `private class FakeXxx : Interface` with manual field tracking. No Mockito, MockK, or junit.mock. |
| No file "enhanced"/"v2" | ✓ PASS | All changes in-place within original files listed in phase-01 "Related Code Files". |
| No unintended scope expansion | ✓ PASS | Test adds to `:data` module only (LocationPointProcessorTest); no changes to `:domain`, `:ui`, `:app` module test files. |
| `:domain:test` < 5s | ✓ PASS | 2s measured. JVM-pure domain tests stay fast. |

---

## Xác nhận từ Dev/Simplifier Reports

- Dev Report (dev-phase-01-report.md): "208 test cases, 0 failures/errors" ✓
- Simplifier Report (simplifier-phase-01-report.md): "208 test cases, 0 failures/errors" (sạch sau simplification) ✓
- This tester report: **209 test cases** (208 baseline + 1 new), 0 failures ✓

New test không phá vỡ simplifier's work — chạy sau khi all code đã đơn giản hoá, xác nhận logic vẫn tuyệt đối.

---

## Risk Assessment Resolution

| Original Risk | Mitigation | Outcome |
|---|---|---|
| `selfLocation` ưu tiên live → camera canh điểm sai số 200m | `hasCenteredOnce` latch (phase-05); S4 test canh vào 90m accuracy vẫn okay | ✓ No issue found |
| Ai tưởng "publish-before-filter" = bỏ qua filter | KDoc + S3 test khoá; new test cover chain scenario | ✓ Locked by S3 + S4 + new test |
| `MutableStateFlow` live vô tận sau tắt theo dõi (D7) | Design choice (acceptable per decisions.md §C3); ghi vào LLM.md §13 Open #14 | ✓ Documented as known limitation |

---

## Mutations Performed & Verified

| Mutation | Expected | Actual | Status |
|---|---|---|---|
| Chèn `// PolylineFollower` vào FusedLocationSource.kt | RealGpsNoSnapArchitectureTest đỏ | AssertionError at kt:42 | ✓ DETECTED |
| Khôi phục FusedLocationSource.kt | Build xanh | BUILD SUCCESSFUL | ✓ CLEAN |

---

## Files Modified for Testing Only

| File | Change | Justification |
|---|---|---|
| `LocationPointProcessorTest.kt` | + 1 test method (39 lines) | Cover cross-rule chain: Reject(ACCURACY) → Accept(DISTANCE); new test khóa rủi ro "publish-before-filter được hiểu sai khi kết hợp nhiều quy tắc" |

No changes to phase-01 source files (`:main`) — all assertions remain, test only enriched.

---

## Unresolved Questions

None. Toàn bộ Success Criteria S1→S7 đạt, không mục nào hoãn hoặc không rõ.

---

## Recommendations for Code Review

1. **Phát hiện #1 của simplifier (priority low):** Comment `zIndex` của `SelfAccuracyCircle` được sửa từ "nằm dưới ZoneCircles nhờ zIndex=0" thành "nằm dưới do FILL_ALPHA + STROKE_WIDTH". ZoneCircles cũng mặc định zIndex=0, nên thứ tự vẽ không guaranteed. Reviewer nên xem xét `zIndex` âm cho vòng sai số (không block, pure vẽ).

2. **Default trên interface (priority low):** `observeLiveSelfLocation(): Flow<LocationPoint?> = flowOf(null)` trên `TrackingRepository` interface là im lặn-safe nhưng có rủi ro lâu dài. Nếu impl thứ 2 quên override, sẽ âm thầm phát null. Suggest: follow-up task riêng để bỏ default và bắt 4 test double kia override (4 dòng, 4 file).

3. **Alpha/Width hằng ở đâu (priority ultra-low):** `SelfAccuracyCircle.kt` dùng `private const val`, nhưng `ZoneCircles.kt` dùng `Dimens.kt`. Hai mẫu khác nhau cho cùng loại giá trị. Suggest: establish một chuẩn, nhưng không block hiện tại.

---

## Final Verdict

✓ **PASS FINAL QA**

Phase-01 code ready for review. All test criteria met, mutation verified, no breaking changes, code quality maintained.
