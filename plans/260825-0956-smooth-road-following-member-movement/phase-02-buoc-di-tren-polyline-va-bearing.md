# Phase 02 — Bước đi bám polyline, bearing thật, spawn một lần (`:domain` thuần)

## Context Links

- [`plan.md`](plan.md) · [`decisions.md` §C1, §C4](decisions.md) (D1, D2, D6)
- Yêu cầu: [PRD delta](docs/prd-delta-smooth-road-movement.md) D1, D2, D3, D5, US-40, US-41, US-42, F7, §4.1, §4.2
- Nghiệm thu: **QA-SRM-01, 02, 03, 09, 10, 11, 12, 25, 26, 27, 28**
- Research: [researcher-01](research/researcher-01-road-following-simulation.md) §B (arc-length), §C (bearing), §D (bất biến) — **§A và §F.4 không dùng, lý do ở `decisions.md`**
- Kiến trúc: `LLM.md` §8.1 (đường sinh sự kiện), §8.2 (hàm thuần ở `:domain`), §11 (test), §12
- Mã hiện tại: `MemberRoamer.kt`, `TrackingConstants.kt`, `MemberMovementSimulator.kt`

## Overview

| | |
|---|---|
| **Ưu tiên** | P1 |
| **Trạng thái** | pending |
| **Ước lượng** | 5h |
| **Phụ thuộc** | Sau phase 01 (chỉ để tránh đụng file; kỹ thuật thì độc lập) |

Toàn bộ phần "đi thế nào" chuyển từ nội suy đường thẳng sang bám một **dãy điểm**. Phase này chưa
gọi mạng: dãy điểm đến từ `SyntheticPath` (thuần, tất định). Phase 04 chỉ thay **nguồn** của dãy
điểm đó. Kèm theo: bearing và tốc độ thật đi vào `RoamState`, và cú dời vị trí bị hạ cấp thành
spawn một lần.

Sau phase này, kể cả không có mạng và không có khoá API, thành viên đã: đi theo đường cong liên
tục thay vì gấp khúc ngẫu nhiên, có hướng thật để marker xoay (phase 03), và không "biến mất rồi
hiện ra chỗ khác" nữa.

## Key Insights

1. **Bảo toàn đỉnh là thứ làm cho nội suy ở tầng hiển thị đúng.** Nếu một bước 20.75m nhảy **qua**
   một đỉnh polyline, đoạn thẳng nối hai mẫu sẽ cắt góc. Ở một khúc cua 135°, sai lệch lên tới
   `0.92 × nửa bước ≈ 9.6m` — sát ngưỡng `SIM_ROAD_TOLERANCE_M` = 10m, tức là QA-SRM-02 sẽ đỏ ngẫu
   nhiên tuỳ vị trí đỉnh. **Cách sửa:** khi một bước vượt qua đỉnh, mẫu phát ra là **đúng đỉnh đó**
   (bước ngắn lại), không phải điểm cách đúng 20.75m. Hệ quả: hai mẫu liên tiếp **luôn** nằm trên
   cùng một đoạn thẳng ⇒ nội suy tuyến tính ở phase 03 nằm đúng trên đường ⇒ sai lệch **bằng 0**,
   không phải "dưới ngưỡng".
2. **Đó cũng là thứ trả lời C1.** Vì nội suy tuyến tính đã đúng, không cần bước đi mịn 250ms,
   không cần bộ đệm, không cần luật "ghi mỗi N bước".
3. **`DWELL_TICKS` không được đụng.** 30 nhịp × 2 500 ms = **75 s** > `EVENT_DEDUPE_WINDOW_MS`
   (60 s). Giữ nhịp tick nghĩa là giữ nguyên toàn bộ căn cứ đã đo của LLM.md §8.1.
4. **Bearing phải được DỰNG, không phải được làm mượt.** Ba nơi đang ghi cứng `0f`
   (`MemberMovementSimulator.kt:98`, `SimulatedLocationSource.kt:62`, `DemoDataSeeder.kt:39`).
   Phase này sửa nơi **thứ nhất** — hai nơi còn lại phục vụ self, và chấm xanh của self là hình
   tròn nên xoay vô nghĩa; giữ `0f` và ghi lý do vào KDoc để người sau không tưởng là sót.
5. **Spawn mỗi tick là nguyên nhân của "nhảy 13 000 km".** `MemberRoamer.kt:122-130` đánh giá
   nhánh `distance > MAX_WALK_M` **mỗi nhịp**. Chỉ chặn nó bằng một cờ là chưa đủ: sau khi cờ bật,
   một zone mới tạo ở nửa kia địa cầu sẽ làm thành viên đi bộ mãi mãi mà không bao giờ tới. Phải
   thay đích, không phải chỉ chặn cú dời.
6. **Bearing của điểm khởi hành phải TẤT ĐỊNH.** `random.nextDouble(FULL_CIRCLE_DEGREES)` cho mỗi
   chặng nghĩa là mỗi vòng lại có một cặp (from, to) khác nhau ⇒ phase 04 không bao giờ cache được
   ⇒ hạn ngạch 500 credit/ngày cháy sau ~3 giờ. Đổi sang hàm thuần của `(memberSeed, zoneId, kind)`:
   **chọn zone** vẫn ngẫu nhiên, **hình học chặng** thì tất định.

## Requirements

**Chức năng**

- FR-1 Mọi vị trí mô phỏng nằm trong `SIM_ROAD_TOLERANCE_M` tính từ dãy điểm đang theo (US-41, QA-SRM-01).
- FR-2 Không cắt góc: không vị trí nào nằm phía trong một khúc cua ≥ 90° quá dung sai (QA-SRM-02).
- FR-3 `RoamState` mang `bearingDegrees` thật (không điểm nào `= 0f` vì chưa ai tính) và `speedMps`
  suy từ quãng đường thật của nhịp đó (US-40, QA-SRM-03).
- FR-4 Đúng **một** cú dời vị trí cho mỗi thành viên cho mỗi lần bắt đầu mô phỏng (US-42, QA-SRM-09/11).
- FR-5 Điểm spawn nằm **ngoài** ranh giới zone đích (QA-SRM-10) — luật hiện có ở `MemberRoamer.kt:175-182`, giữ.
- FR-6 Bất biến ENTER/EXIT xen kẽ giữ nguyên ở bán kính 150m **và** ở `ZONE_RADIUS_MIN_M` = 50m
  (US-25/26, QA-SRM-25/26/27/28).
- FR-7 Một tuyến có hình học xấu (cắt ranh giới zone > 1 lần mỗi chiều) bị **từ chối**, rơi về
  `SyntheticPath`.

**Phi chức năng**

- NFR-1 `:domain` giữ nguyên: không Android, không Compose, không coroutine, không `suspend`.
- NFR-2 `:domain:test` tiếp tục chạy **dưới 5 giây** (LLM.md §11).
- NFR-3 Hai hằng số mới vào `TrackingConstants` kèm dòng "ảnh hưởng khi đổi" — tỉ lệ truy nguyên
  PRD §6 đi từ 12/19 lên **14/21**, không xuống.
- NFR-4 `MemberMovementSimulatorTest` giữ **JVM thuần** (không Robolectric): mọi phép đo hình học
  đã có sẵn trong `RoamState`, `:data` không phải tính lại.

## Architecture

```
                     :domain/tracking/  (thuần, không Android)
  ┌──────────────────────────────────────────────────────────────────┐
  │  GeoBearing            initialBearing(a,b) · shortestDelta(a,b)  │
  │  PolylineFollower      parametrize(points) -> ParametrizedPath   │
  │                        advance(path, cursorM, stepM) -> Progress │  ← BẢO TOÀN ĐỈNH
  │  SyntheticPath         between(from, to, seed) -> List<GeoPoint> │  ← tầng 3 của D5
  │  RouteGeometryGuard    isUsable(points, zone, kind): Boolean     │  ← giữ bất biến C4
  │  MemberRoamer          tick(...) -> RoamStep                     │
  │                        withPath(state, points) -> RoamState      │
  └──────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │ (không suspend, không I/O)
  ┌─────────────────────────────────┴────────────────────────────────┐
  │ :data/location/MemberMovementSimulator.tickOnce()                │
  │   when (MemberRoamer.tick(...)) {                                │
  │     is RoamStep.NeedPath -> pathFor(step)   ← phase 02: SyntheticPath thẳng      │
  │                             MemberRoamer.withPath(...)            │  phase 04: MemberRouteSource │
  │     is RoamStep.Move     -> ghi LocationPoint(bearing, speed từ state)           │
  │   }                                                              │
  └──────────────────────────────────────────────────────────────────┘
```

**Vì sao hai pha (`NeedPath` → `withPath`) chứ không truyền sẵn một `Map<zoneId, path>` như
researcher-01 §G đề nghị:** lấy tuyến là việc `suspend` và có thể thất bại; `:domain` không được
biết điều đó. Trả về một `RoamStep.NeedPath` để tầng gọi tự quyết định lấy tuyến ở đâu giữ
`MemberRoamer` hoàn toàn đồng bộ và thuần, và giữ `MemberRoamerTest` là JUnit không coroutine.
Bản `Map` truyền sẵn thì `:data` phải đoán trước thành viên sắp cần tuyến nào — đúng thông tin mà
chỉ `MemberRoamer` mới có.

**`PolylineFollower.advance` — hợp đồng chính xác:**

```
advance(path, cursorMeters, stepMeters) -> Progress(point, cursorMeters, bearingDegrees, movedMeters, finished)
  · Nếu trong khoảng [cursor, cursor + step] KHÔNG có đỉnh nào  -> đi đúng step, điểm nội suy trên đoạn
  · Nếu CÓ đỉnh                                                  -> dừng ở ĐỈNH ĐẦU TIÊN, movedMeters < step
  · Nếu cursor + step >= tổng chiều dài                          -> điểm cuối, finished = true
  · bearingDegrees = GeoBearing.initialBearing(điểm cũ, điểm mới)
```

Bước ngắn lại ở đỉnh **không** làm tốc độ trông giật: mắt người thấy `speedMps` qua nội suy của
phase 03, và phase 03 chia quãng đường thật cho khoảng thời gian thật giữa hai mẫu.

## Related Code Files

**Tạo** — tất cả ở `domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/tracking/`

| File | Việc |
|---|---|
| `GeoBearing.kt` | `initialBearing(lat1,lng1,lat2,lng2): Double` (atan2, chuẩn hoá `[0,360)`); `shortestDelta(from,to): Double` trả `(-180,180]` |
| `PolylineFollower.kt` | `ParametrizedPath` (points + mảng cộng dồn + `totalMeters`), `parametrize()`, `advance()`, `Progress` |
| `SyntheticPath.kt` | `between(from, to, seed): List<GeoPoint>` — cung cong nhẹ, biên độ `min(0.15×d, 120m)`, dấu và pha từ `seed`, khoảng cách đỉnh ≈ `STEP_METERS / 2` |
| `RouteGeometryGuard.kt` | `isUsable(points, zone, kind): Boolean` — đếm số lần đổi dấu của `d - radius` và `d - (radius + ZONE_EXIT_BUFFER_M)` |

**Tạo — test** (`domain/src/test/kotlin/.../domain/tracking/`)

`GeoBearingTest.kt` · `PolylineFollowerTest.kt` · `SyntheticPathTest.kt` · `RouteGeometryGuardTest.kt`

**Sửa**

| File | Việc |
|---|---|
| `domain/.../tracking/TrackingConstants.kt` | `+ SIM_MEMBER_SPEED_MPS = 8.3`, `+ SIM_ROAD_TOLERANCE_M = 10.0`, mỗi cái một dòng "ảnh hưởng khi đổi" (PRD §6 delta §4.2) |
| `domain/.../tracking/MemberRoamer.kt` | `STEP_METERS` thành `val` suy ra; `RoamState` + `bearingDegrees`/`speedMps`/`hasSpawned`/`path`/`pathCursorMeters`; `RoamTarget` + `kind: LegKind`; `tick()` trả `RoamStep`; `+ withPath()`; `+ stableBearing()`; nhánh `MAX_WALK_M` gác bằng `hasSpawned` |
| `domain/src/test/.../MemberRoamerTest.kt` | Viết lại theo API hai pha; **giữ nguyên tên và ý nghĩa** của test bất biến; thêm ca bán kính 50m, ca spawn-một-lần, ca đường men mép zone |
| `data/.../location/MemberMovementSimulator.kt` | Xử lý `RoamStep`; `bearingDegrees = next.bearingDegrees`, `speedMps = next.speedMps`; xoá hằng số `ROAM_SPEED_MPS`; `pathFor()` gọi `SyntheticPath` (phase 04 thay thân hàm này) |
| `data/src/test/.../MemberMovementSimulatorTest.kt` | Cập nhật theo API mới; + ca "không điểm nào `bearingDegrees == 0f` sau 50 nhịp" (QA-SRM-03) |
| `data/.../location/SimulatedLocationSource.kt` | **Chỉ KDoc** ở dòng `bearingDegrees = 0f` — giải thích vì sao self không cần bearing |
| `data/.../seed/DemoDataSeeder.kt` | **Chỉ KDoc**, cùng lý do |
| `LLM.md` | §3 (4 file mới ở `domain/tracking/`), §8.1 (mô hình chặng + spawn một lần), §13 (Open #7 cập nhật tỉ lệ 14/21) |
| `docs/prd-delta-smooth-road-movement.md` | Điền giá trị cho các ô "TBD — planner xác nhận" ở §4.1/§4.2 |

**Xoá:** hằng số `MemberMovementSimulator.ROAM_SPEED_MPS` và `MILLIS_PER_SECOND` (không còn ai đọc).

## Implementation Steps

1. `GeoBearing.kt` + test. Ca: 4 hướng chính (N=0, E=90, S=180, W=270); `shortestDelta(350, 10) == 20`;
   `shortestDelta(10, 350) == -20`; `shortestDelta(0, 180)` = `180` (chọn một chiều, ghi vào KDoc,
   khoá bằng test — nếu không, marker sẽ quay ngẫu nhiên hai chiều ở đúng 180°).
2. `PolylineFollower.kt` + test. Ca bắt buộc:
   - đi trên polyline có đoạn dài/ngắn xen kẽ → tổng quãng đường sau N bước bằng `N × step` **trừ**
     phần bị cắt ở đỉnh, và **mọi mẫu nằm đúng trên một đoạn** (khoảng cách tới đoạn gần nhất `< 1e-6`);
   - bước vượt qua đỉnh → mẫu phát ra **chính là đỉnh** (so sánh bằng `assertEquals(..., 0.0)`);
   - `cursor + step` vượt tổng chiều dài → `finished = true`, điểm = điểm cuối;
   - polyline 1 điểm / 0 điểm → không ném, `finished = true` ngay.
3. `SyntheticPath.kt` + test. Ca: cùng `seed` cho cùng kết quả (tất định); độ lệch lớn nhất so với
   đoạn thẳng nằm trong `[0.05×d, 0.15×d]` và `≤ 120m`; điểm đầu == `from`, điểm cuối == `to`;
   khoảng cách hai đỉnh liên tiếp `≤ STEP_METERS`.
4. `RouteGeometryGuard.kt` + test. Ca: tuyến đi thẳng qua zone → `true`; tuyến men theo mép cắt
   ranh giới 4 lần → `false`; tuyến không bao giờ vào trong zone → `false` cho `ENTER_ZONE`; tuyến
   ra khỏi zone nhưng không vượt `radius + ZONE_EXIT_BUFFER_M` → `false` cho `LEAVE_ZONE`.
5. `TrackingConstants`: thêm 2 hằng số. KDoc mỗi cái ghi **nguồn** (PRD delta §4.2) và **ảnh hưởng
   khi đổi** — đây là điều kiện để §13 Open #7 không xấu đi.
6. `MemberRoamer`:
   a. `STEP_METERS` thành `val = TrackingConstants.SIM_MEMBER_SPEED_MPS * (MEMBER_ROAM_INTERVAL_MS / 1000.0)`
      = 20.75. KDoc cũ ("~72 km/h … ô tô") phải bị thay, không được để lại.
   b. `RoamTarget` thêm `kind: LegKind` (`ENTER_ZONE` / `LEAVE_ZONE` / `WANDER`).
   c. `nextTarget(..., memberSeed: Int)`; thay `random.nextDouble(FULL_CIRCLE_DEGREES)` bằng
      `stableBearing(memberSeed, zoneId, kind)` cho `ENTER_ZONE` và `LEAVE_ZONE`; `WANDER` giữ `random`.
   d. `tick()` trả `RoamStep`: dwell → `Move` (không đổi toạ độ); chưa có `path` cho `target` hiện
      tại → `NeedPath`; có `path` → `PolylineFollower.advance` → `Move` kèm `bearingDegrees`,
      `speedMps = movedMeters / (MEMBER_ROAM_INTERVAL_MS / 1000.0)`.
   e. Nhánh dời vị trí: `distance > MAX_WALK_M && !state.hasSpawned` → dời, đặt `hasSpawned = true`.
      `distance > MAX_WALK_M && state.hasSpawned` → **bỏ đích đó**, lấy đích `WANDER` quanh vị trí
      hiện tại. KDoc `MAX_WALK_M` phải nói rõ ngữ nghĩa mới (PRD delta §4.1).
   f. `withPath(state, points)`: `state.copy(path = PolylineFollower.parametrize(points), pathCursorMeters = 0.0)`.
      Tới đích (`finished`) → xoá `path`, đặt `dwellTicksLeft` như luật cũ.
7. `MemberRoamerTest`: helper `advance(state, zones, random)` xử lý `NeedPath` bằng `SyntheticPath`,
   rồi gọi lại `tick`. Giữ nguyên tên test bất biến. Thêm ba test mới (xem Success Criteria).
8. `MemberMovementSimulator`: `when (step)`; điểm ghi ra lấy `bearingDegrees`/`speedMps` từ state.
   Nhịp `NeedPath` **không ghi điểm** (chưa di chuyển) — cùng luật với nhịp dwell.
   Thêm log `FtdLog.d(TAG, "sim_spawn memberId=$id distanceM=$d")` đúng ở nhịp `hasSpawned` chuyển
   `false → true` (QA §3 đề xuất sự kiện này; nó là cách QA-SRM-09/11 đếm số cú spawn trên bản
   debug). **Không log `lat`/`lng`** — gate G7, PRD §7.3.
9. `./gradlew :domain:test :data:test --no-configuration-cache`, xác nhận `:domain:test` vẫn < 5s.
10. **Mutation thật, ghi vào dev report:** bỏ bước bảo toàn đỉnh trong `PolylineFollower.advance`
    (đi đúng `step` luôn) → `PolylineFollowerTest` và QA-SRM-02 phải **đỏ**; khôi phục → xanh.
11. Cập nhật `LLM.md` §3/§8.1/§13 và các ô TBD của PRD delta **trong cùng commit**.

## Todo List

- [ ] `GeoBearing.kt` + `GeoBearingTest`
- [ ] `PolylineFollower.kt` + `PolylineFollowerTest` (bảo toàn đỉnh là ca trung tâm)
- [ ] `SyntheticPath.kt` + `SyntheticPathTest`
- [ ] `RouteGeometryGuard.kt` + `RouteGeometryGuardTest`
- [ ] 2 hằng số mới trong `TrackingConstants` kèm dòng ảnh hưởng
- [ ] `RoamState`/`RoamTarget`/`RoamStep` + `tick()` + `withPath()` + `stableBearing()`
- [ ] Spawn một lần: `hasSpawned` + nhánh thay đích khi đã spawn
- [ ] `MemberRoamerTest` viết lại, giữ nguyên test bất biến; + 50m, + spawn, + men mép zone
- [ ] `MemberMovementSimulator` xử lý `RoamStep`, bearing/speed thật, xoá `ROAM_SPEED_MPS`
- [ ] Log `sim_spawn memberId=… distanceM=…` (không toạ độ)
- [ ] KDoc cho 2 chỗ `bearingDegrees = 0f` còn lại (self)
- [ ] Mutation test bảo toàn đỉnh (đỏ → xanh), ghi vào dev report
- [ ] `LLM.md` §3/§8.1/§13 + điền TBD của PRD delta, cùng commit

## Success Criteria

| # | Điều kiện | Cách kiểm | QA |
|---|---|---|---|
| S1 | Chạy 200 nhịp trên polyline ≥ 3 đoạn gấp khúc → **mọi** mẫu cách đoạn gần nhất `< 1e-6 m` (không phải "< 10m") | `PolylineFollowerTest` | QA-SRM-01 |
| S2 | Polyline có khúc cua 90° → không mẫu nào nằm phía trong góc | `PolylineFollowerTest` | QA-SRM-02 |
| S3 | 50 nhịp trên đường ≥ 2 hướng → **0 điểm** có `bearingDegrees == 0f` (trừ điểm đứng yên) | `MemberMovementSimulatorTest` | QA-SRM-03 |
| S4 | 200 nhịp → đúng **1** bước có quãng đường `> 2 × STEP_METERS` | `MemberRoamerTest` | QA-SRM-09 |
| S5 | Khoảng cách từ điểm spawn tới tâm zone `> zone.radiusMeters` | `MemberRoamerTest` | QA-SRM-10 |
| S6 | Reset state 3 lần (mô phỏng tắt/bật) → đúng 3 cú dời, không cộng dồn | `MemberRoamerTest` | QA-SRM-11 |
| S7 | Bất biến ENTER/EXIT xen kẽ, bắt đầu bằng ENTER — ở **cả** zone 150m **và** zone 50m | `MemberRoamerTest` (2 ca) | QA-SRM-25/26 |
| S8 | Khoảng cách **tính bằng ms** giữa hai `ENTER` cùng zone `> 60 000` | `MemberRoamerTest` — đếm nhịp × `MEMBER_ROAM_INTERVAL_MS` | QA-SRM-27 |
| S9 | Tuyến chạy song song mép zone (cách tâm ≈ bán kính) → `RouteGeometryGuard.isUsable` trả `false`; roamer rơi về `SyntheticPath`; không dội | `RouteGeometryGuardTest` + `MemberRoamerTest` | QA-SRM-28 |
| S10 | `:domain:test` chạy < 5 giây | Thời gian trong output Gradle | LLM.md §11 |

## Risk Assessment

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| **Viết lại `MemberRoamerTest` làm mất chính bất biến nó bảo vệ** | **Cao** | Test bất biến **giữ nguyên tên, nguyên assertion, nguyên `TICKS_FOR_SEVERAL_CYCLES`**; chỉ vòng lặp gọi qua helper `advance()`. Diff của test đó phải được đọc từng dòng khi review |
| Bước ngắn lại ở đỉnh làm `speedMps` nhấp nhô ⇒ có ai đó "sửa" bằng cách bỏ bảo toàn đỉnh | Trung bình | KDoc `advance()` ghi rõ đây là đánh đổi có chủ ý và mutation ở Step 10 là bằng chứng; `speedMps` không hiển thị cho người dùng |
| `stableBearing` làm mọi vòng đi đúng một đường ⇒ demo trông "chết" | Thấp | Chọn **zone** vẫn ngẫu nhiên; hai thành viên có seed khác nhau. Và đây là điều kiện để hạn ngạch không cháy (phase 04) |
| Zone bị xoá đúng lúc thành viên đang bám tuyến tới nó | Trung bình | `tick()` kiểm `target.zoneId` còn trong `zones` không; mất thì xoá `path` và lấy đích mới. Ca test đã có sẵn trong `MemberMovementSimulatorTest` |
| `path` nằm trong `RoamState` (`data class`) làm `equals`/`copy` đắt | Thấp | `ParametrizedPath` giữ `DoubleArray` cộng dồn; state chỉ được `copy` 0.4 lần/giây/thành viên |
| Sửa `MemberRoamer` làm hỏng `StartSimulationUseCase`/`RouteBlueprint` (F5) | Thấp | Không file nào của F5 import `MemberRoamer`. Chạy `RouteBlueprintTest` + `StartSimulationUseCaseTest` để chứng minh |

## Security Considerations

- `:domain` không có I/O, không log — không có bề mặt mới.
- `SyntheticPath` dùng seed từ `member.id.hashCode()`, **không** dùng `Random.Default`: không có
  nguồn ngẫu nhiên nào cần chất lượng mật mã ở đây, và tất định là yêu cầu chức năng.
- Không hằng số nào mới chứa toạ độ thật của ai.

## Next Steps

- **Phase 03** đọc `bearingDegrees` và `recordedAt` từ điểm phase này ghi ra để nội suy.
- **Phase 04** thay đúng **một hàm**: `MemberMovementSimulator.pathFor(step)`. Mọi thứ khác đứng yên
  — đó là bài kiểm tra xem seam ở phase này có đúng chỗ không.
- `RouteGeometryGuard` chưa có người gọi thật cho tới phase 04 (phase 02 dùng nó để từ chối tuyến
  tổng hợp hỏng). Ghi vào KDoc để không ai xoá vì tưởng là code chết.
