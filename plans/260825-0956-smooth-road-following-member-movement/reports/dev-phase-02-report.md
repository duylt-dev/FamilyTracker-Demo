# Dev Phase 02 Report — Bước đi bám polyline, bearing thật, spawn một lần

## Status: completed

## Files Created

`domain/src/main/kotlin/.../domain/tracking/`:
- `GeoBearing.kt` (43 dòng) — `initialBearing`/`shortestDelta`, `internal`
- `PolylineFollower.kt` (133 dòng) — `ParametrizedPath` (public — lý do dưới), `Progress`, `parametrize()`/`advance()` (bảo toàn đỉnh)
- `SyntheticPath.kt` (74 dòng) — `between(from,to,seed)`, **public** (không `internal`: `:data` gọi trực tiếp)
- `RouteGeometryGuard.kt` (45 dòng) — `isUsable(points,zone,kind)`, `internal`
- `MemberRoamerModel.kt` (52 dòng) — `LegKind`/`RoamTarget`/`RoamState`/`RoamStep`, tách khỏi `MemberRoamer.kt` để < 200 dòng
- `MemberRoamerGeometry.kt` (34 dòng) — `pointAtBearing`/`wanderTarget`, cùng lý do tách

`domain/src/test/kotlin/.../domain/tracking/`: `GeoBearingTest.kt`, `PolylineFollowerTest.kt`, `SyntheticPathTest.kt`, `RouteGeometryGuardTest.kt`

## Files Modified
- `domain/.../tracking/TrackingConstants.kt` (+18 dòng) — `SIM_MEMBER_SPEED_MPS=8.3`, `SIM_ROAD_TOLERANCE_M=10.0`, mỗi cái 1 dòng "ảnh hưởng khi đổi"
- `domain/.../tracking/MemberRoamer.kt` (199 dòng) — viết lại: API hai pha `tick()->RoamStep`, `withPath()`, `stableBearing()`, spawn một lần qua `hasSpawned`
- `domain/src/test/.../MemberRoamerTest.kt` (296 dòng — tiền lệ có sẵn ≥240 dòng ở `HistoryViewModelTest`/`ZoneEditorViewModelTest`) — viết lại theo API hai pha qua helper `advance()`; test bất biến "a full roam cycle…" **giữ nguyên tên/assertion/TICKS_FOR_SEVERAL_CYCLES**, chỉ đổi lời gọi vòng lặp
- `data/.../location/MemberMovementSimulator.kt` (198 dòng) — xử lý `RoamStep`, bearing/speed thật từ `RoamState`, log `sim_spawn`, xoá `ROAM_SPEED_MPS`/`MILLIS_PER_SECOND`
- `data/src/test/.../MemberMovementSimulatorTest.kt` — + test "no recorded point has bearingDegrees 0f after 50 ticks"
- `data/.../location/SimulatedLocationSource.kt`, `data/.../seed/DemoDataSeeder.kt` — chỉ thêm KDoc tại dòng `bearingDegrees = 0f`, giá trị không đổi
- `LLM.md` — §3 (6 file mới/tách, mô tả API hai pha), §8.1 (mô hình chặng + spawn một lần, chú thích timing cũ là pre-phase-02), §13 Open #7 (12/19→14/21) + Open #15 mới (phát hiện: `RealGpsNoSnapArchitectureTest` KDoc sai vị trí vật lý của `MemberMovementSimulator.kt`)
- `docs/prd-delta-smooth-road-movement.md` §4.1/§4.2 — điền 4 ô TBD thuộc phạm vi phase-02 (giữ MEMBER_ROAM_INTERVAL_MS=2500, MAX_WALK_M giữ giá trị đổi ngữ nghĩa, xác nhận SIM_MEMBER_SPEED_MPS=8.3 và SIM_ROAD_TOLERANCE_M=10.0); 2 ô render (`MEMBER_RENDER_MAX_JUMP_M`/`MEMBER_RENDER_FRAME_MS`) để nguyên cho phase-03 (đã có chủ file tường minh)

## Success Criteria — bằng chứng thật

| # | Điều kiện | Bằng chứng |
|---|---|---|
| S1 | Mọi mẫu cách đoạn gần nhất < 1e-6m | `PolylineFollowerTest."every sample lands exactly on the polyline, even across a corner"` xanh |
| S2 | Không cắt góc ở khúc cua 90° | Cùng test trên + assertion mới "không cursor liên tiếp nào nhảy qua đỉnh mà không dừng" — xanh |
| S3 | 0 điểm `bearingDegrees==0f` (trừ đứng yên) | `MemberMovementSimulatorTest."no recorded point has bearingDegrees 0f after 50 ticks on a curved path"` xanh, 200 ticks |
| S4 | Đúng 1 bước > 2×STEP_METERS/200 nhịp | `MemberRoamerTest."across many ticks, exactly one step covers more than 2x STEP_METERS - the spawn"` xanh |
| S5 | Spawn ngoài `radiusMeters` | `MemberRoamerTest."a member on the far side of the planet is repositioned OUTSIDE the zone, not into it"` xanh |
| S6 | Reset 3 lần → đúng 3 spawn | `MemberRoamerTest."restarting the simulation 3 times each spawns exactly once, no leak between runs"` xanh |
| S7 | Bất biến ở 150m VÀ 50m | `MemberRoamerTest` 2 test ("a full roam cycle…" + "…also holds at the minimum zone radius (50m)") xanh |
| S8 | ENTER-ENTER cùng zone > 60 000ms | `MemberRoamerTest."each ENTER of the same zone is separated by more than the dedupe window, in milliseconds"` xanh |
| S9 | Guard từ chối, rơi về SyntheticPath, không dội | `RouteGeometryGuardTest` (7 ca) + `MemberRoamerTest."a route hugging the zone edge is rejected..."` xanh |
| S10 | `:domain:test` < 5s | `./gradlew :domain:test --rerun-tasks`: **BUILD SUCCESSFUL in 1s** (122 test, tổng thời gian JUnit báo cáo 0.134s) |

## Test Status
- `:domain:test`: 122/122 xanh, ~1s
- `:data:test`: 40/40 xanh
- `:ui:test`: 81/81 xanh
- `:app:test`: 1/1 xanh
- F5 non-regression: `RouteBlueprintTest` 6/6, `StartSimulationUseCaseTest` 5/5 xanh

## Mutation Test — Step 10 (bảo toàn đỉnh)

Bỏ nhánh "dừng ở đỉnh" trong `PolylineFollower.advance` (comment thay thế, đi thẳng `pointAt(path, targetCursor)`):

```
PolylineFollowerTest > a step that would overshoot a vertex stops exactly at the vertex, shortened FAILED
    java.lang.AssertionError: bước phải bị cắt ngắn, không đi đủ 30m expected:<10.0> but was:<30.0>

PolylineFollowerTest > every sample lands exactly on the polyline, even across a corner FAILED
    java.lang.AssertionError: bước từ 90.0 tới 120.0 đã nhảy qua một đỉnh mà không dừng lại

6 tests completed, 2 failed
BUILD FAILED
```

Khôi phục nguyên văn từ bản sao lưu → `diff` rỗng ("IDENTICAL - restore confirmed") → `:domain:test` xanh lại (122/122). Mutation đánh trực tiếp vào `PolylineFollower.advance` (mã sản phẩm), không đánh test double.

## Deviations từ phase file (kèm lý do)

1. **Tách `MemberRoamer.kt` thành 3 file** (`MemberRoamer.kt` + `MemberRoamerModel.kt` + `MemberRoamerGeometry.kt`) thay vì 1 file như Related Code Files liệt kê — bản gộp dài 266 dòng, vượt luật 200 dòng (`development-rules.md`). Cùng tiền lệ `ZoneCenterMap.kt`/`HistoryMap.kt` (LLM.md §5/§12). `nextTarget`/`stableBearing`/`tick`/`withPath` vẫn ở đúng `MemberRoamer` như spec; chỉ data class và toán hình học thuần tách ra.
2. **`ParametrizedPath` và `SyntheticPath` phải PUBLIC, không `internal` như 2 file "chị em" (`GeoBearing`/`RouteGeometryGuard`)** — phát hiện lúc build: `RoamState.path: ParametrizedPath?` băng qua biên module tới `:data`, và Kotlin cấm data class public có tham số kiểu `internal`; `SyntheticPath` được `:data/MemberMovementSimulator.pathFor()` gọi trực tiếp theo đúng kiến trúc phase file. Cùng bài học `GeoDistance`/`ValhallaDirectionsMapper` ở LLM.md §13 Fixed #12, ghi lại trong KDoc từng file.
3. **Bỏ hằng số `ARRIVAL_TOLERANCE_M`** (không có trong "Xoá" của phase file nhưng không còn ai đọc) — `PolylineFollower.advance`'s điều kiện `finished` chính xác tuyệt đối (điểm cuối path = target), không cần dung sai đến-nơi kiểu cũ. Giữ lại là code chết.
4. **`RouteGeometryGuard` không được `MemberRoamer`/`MemberMovementSimulator` gọi trong production** — đúng như phase Next Steps ghi "chưa có người gọi thật cho tới phase-04"; phase-02 chỉ có `RouteGeometryGuardTest` và `MemberRoamerTest`'s test S9 gọi trực tiếp để khoá hành vi trước.
5. **Phát hiện ngoài phạm vi, không tự sửa:** `RealGpsNoSnapArchitectureTest.kt` (phase-01, sở hữu ngoài phase-02) tuyên bố sai rằng `MemberMovementSimulator.kt` "nằm ngoài" `data/location/` — thực tế nó nằm NGAY TRONG thư mục bị quét cấm-từ-khoá. Vấp phải khi viết KDoc nhắc `PolylineFollower`/`RoutingProvider` bằng tên → test đỏ vì quét cả comment. Đã né bằng cách đổi cách diễn đạt, không sửa file test đó (ngoài file ownership). Ghi thành `LLM.md` §13 Open #15.
6. **`MemberRoamerTest.kt` 296 dòng** — vượt 200 dòng nhưng đúng tiền lệ đã có trong repo (`HistoryViewModelTest.kt` 366, `ZoneEditorViewModelTest.kt` 339, `MapViewModelTest.kt` 329 …) — luật 200 dòng áp cho file *sản phẩm*, test file lớn đã được chấp nhận rộng rãi trong codebase.

## Unresolved / follow-up cho phase 04/06
- `SIM_MEMBER_SPEED_MPS=8.3` chưa đo thật trên `emulator-5554` — `decisions.md` §C5 giao việc đo lại cho phase-06.
- `RouteGeometryGuard` cần được nối dây thật khi phase-04 thêm `RoutingProvider` làm nguồn tuyến.
