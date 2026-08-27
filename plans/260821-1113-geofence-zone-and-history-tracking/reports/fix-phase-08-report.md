# Fix Phase 08 Report — History render measurement was measuring the wrong segment

**Status**: DONE. Device (`emulator-5554`) back on `release` build, DB clean. All 4 steps complete.

## Diagnosis confirmed (so far)

`HistoryMap.kt:84-91` `LaunchedEffect(session?.id) { startNanos = nanoTime(); withFrameNanos {}; renderMs = ... }`
starts timing only AFTER `session` (a fully-resolved `TrackSession` with all points already in
memory) is delivered to the composable. Everything expensive — Room query, entity→domain mapping,
`RouteSplitter.split`, `RouteStats.of`, and (to be confirmed) `PolyUtil.simplify` — happens upstream
of that point, in `HistoryViewModel.observeRoute()` / `TrackingRepositoryImpl.observeRoute()`
(`data/src/main/java/.../repository/TrackingRepositoryImpl.kt:41-43`) and in `RoutePolyline.kt`'s
`remember` blocks, all of which run during Compose's composition phase — which finishes before the
`LaunchedEffect` coroutine body starts executing. So `renderMs` mostly measures "wait for next
`withFrameNanos` callback" (~one frame), not the pipeline.

PRD §7.1 (`docs/FTD001_FamilyTrackerDemo_PRD.md:488`) is explicit: **"Vẽ lộ trình một ngày | < 1s từ
lúc chọn ngày"** — from the moment the day is *selected*, not from "session already resolved to one
frame". This confirms the user's diagnosis is correct.

## Plan

1. [DONE] Write `:data` androidTest measuring each pipeline stage separately at 8,640 points.
2. [DONE] Fix `history_rendered` measurement / rename field.
3. [DONE] Hand-calculated stats vs app-displayed stats comparison.
4. [DONE] Cleanup 8,460-point test data, confirm release build installed.

## Step 1 — Full pipeline measured at 8,640 points (REAL numbers, device: emulator-5554)

New permanent test: `data/src/androidTest/.../perf/HistoryPipelineScaleTest.kt` (+
`HistoryPipelineTestFixture.kt`, split to stay < 200 lines). Runs via
`./gradlew :data:connectedDebugAndroidTest`. File-backed Room DB (own test-APK data dir, cannot
collide with the real app DB), 8,640 rows seeded (16h session of 5,760 pts + 20-min gap +
8h session of 2,880 pts — matches PRD §7.1's exact ceiling), 5 iterations/stage, median reported
(first run excluded from conclusions — JIT/disk-cache warmup, visible in raw timings below).

Added `androidTestImplementation(libs.maps.compose.utils)` to `data/build.gradle.kts` — test-scoped
only (not shipped in the app/`:data` AAR) — needed because `PolyUtil`/`LatLng` require a real
Android runtime (JVM unit tests hit `Stub!` on `LatLng`'s constructor); `:domain` cannot host this
test at all — it's a pure-`kotlin.jvm` module (`domain/build.gradle.kts`), no androidTest capability
whatsoever, so "at `:data` or `:domain`" resolved to `:data`.

**Realistic single render** = whole-day query/map/split (always paid) + stats/simplify for only the
**default-selected session** (`HistoryViewModel.applySessions` picks the longest by
`distanceMeters` — `SessionList` shows the other session's distance straight from the split result,
no extra `RouteStats`/`PolyUtil` call for it):

| Stage | What | Median (ms) | Raw 5 runs (ms) |
|---|---|---|---|
| 1 | `LocationPointDao.observeBetween` (8,640 rows, file-backed Room) | **15** | 39, 19, 15, 15, 15 |
| 2 | entity → `:domain` mapping (8,640 rows) | **4** | 4, 4, 4, 4, 4 |
| 3 | `RouteSplitter.split` (8,640 rows → 2 sessions) | **13** | 15, 13, 13, 13, 12 |
| 4 | `RouteStats.of` (selected session only) | **0** | 0, 0, 0, 0, 0 |
| 5 | `PolyUtil.simplify` (selected session, 5,760 pts → 552, tol=10m) | **466** | 525, 485, 465, 462, 466 |
| **Total** | | **498** | |

PRD §7.1 budget: **< 1000ms from day selection**. **498ms passes**, but the margin is **~2x, not
"100x" as `test-phase-08-report.md` §A.2 concluded.** `PolyUtil.simplify` alone is **94% of the
total** and, critically, **is main-thread, synchronous work inside a Compose `remember` block**
(`RoutePolyline.kt:40-42`) — meaning session selection blocks the UI thread for ~466ms at this
scale, a real jank/freeze the tester's "No jank/lag detected" note (§A.2) did not actually verify
with any frame-timing instrumentation (see Step-2 finding below on why `renderMs` couldn't have
caught it).

**Extra finding — `PolyUtil.simplify` scales worse than linearly on this data shape.** Per-session
breakdown: session0 (5,760 raw → 552 simplified) = 466ms; session1 (2,880 raw → 277 simplified) =
109ms. Point count ratio is 2.0x but time ratio is 4.3x (466/109) — roughly O(n^2.1) locally on this
zigzag pattern, not O(n) or O(n log n). This means a future increase in point density (e.g. a
config change to a shorter GPS interval, or multi-day view) would cost **disproportionately** more
than a linear projection suggests. Not a phase-08 fix (tolerance/algorithm choice was already
correct per PRD — Douglas-Peucker via `maps-compose-utils` is the specified approach) but a
concrete data point phase-11's gate should carry forward, and worth a note for anyone tuning
`SIMPLIFY_TOLERANCE_METERS` later.

**Verdict for "production-ready at 8,640-point scale, 100x margin below target"
(`test-phase-08-report.md` §A.2 final verdict)**: **not supported.** The screen is usable within
budget (498ms < 1000ms) but the safety margin is roughly 2x, dominated almost entirely by one
super-linear call, and does not include actual Compose layout/draw/GoogleMap-native-rendering time
(not measured by this test either — see "Chỗ còn dở" at the end of this report).

## Step 2 — Fixed the measurement, with live on-device proof of the original bug

### Proof `PolyUtil.simplify` was never inside `renderMs`'s window (real device log, not inference)

Before touching the fix, added temporary `nanoTime()`-tagged logs at (a) the start of
`RoutePolyline.kt`'s `remember(rawLatLngs) { PolyUtil.simplify(...) }` block and (b) the start of
`HistoryMap.kt`'s `LaunchedEffect(session?.id)` body — installed `debug` build, seeded 8,460 real
`location_points` rows into the **actual app DB** (WAL procedure, self memberId, day 2026-08-20 —
sized to fit one calendar day with a 20-min gap: 5,760 + 2,880 doesn't fit in 24h with the gap, used
5,760 + 2,700 = 8,460 like the tester did), opened History on `emulator-5554`, selected the day.
Real logcat (`pid=31442/31442`, same thread — i.e. this is genuinely blocking the UI thread):

```
01:21:20.442  effect_start        nanos=36363971616668   (LaunchedEffect fires for the STALE/null session key)
01:21:20.525  simplify_start      nanos=36364054402334    points=5760
01:21:21.099  simplify_end        nanos=36364628875293    out=552            (574ms later)
01:21:21.156  effect_start        nanos=36364685342543    (56ms after simplify ends — the REAL effect restart)
01:21:21.174  history_rendered … renderMs=18
```

`simplify_start`→`simplify_end` = **574ms**, entirely **before** the effect that actually times
`renderMs` even restarts. `renderMs=18` only covers the trailing frame wait. This is unambiguous:
the diagnosis in the task prompt was correct, and if anything **understates** the problem — this
isn't just "measuring the wrong segment", it's a ~574ms **synchronous main-thread freeze** during
session selection that the old telemetry made invisible and the tester's "No jank/lag detected"
note never actually instrumented. Temp logs removed after capturing this evidence — not shipped.

### The fix

Kept the event name `history_rendered` (least disruptive) but renamed its field
`renderMs` → **`frameMs`**, with a KDoc explaining exactly what it does and doesn't cover. Added two
new `FTD_EVENT`s so the full pipeline can be reconstructed by summing, per the task's fallback
instruction ("log riêng các mốc để cộng lại được"):

| Event | Where | Fields | Covers |
|---|---|---|---|
| `history_query_split` | `TrackingRepositoryImpl.observeRoute()` (`:data`) — logs only the FIRST emission per fresh day/session selection, not the ~10s re-emissions while tracking stays on | `day`, `pointCount`, `sessionCount`, `pipelineMs` | Room query + entity→domain mapping + `RouteSplitter.split` |
| `history_simplify` | `RoutePolyline.kt` (`:ui`) | `pointCount`, `simplifiedCount`, `simplifyMs` | `PolyUtil.simplify` |
| `history_rendered` | `HistoryMap.kt` (`:ui`) | `day`, `pointCount`, `frameMs` (renamed) | Trailing single-frame wait only |

`android.util.Log` cannot be imported inside `HistoryViewModel` (MVI doc §9, enforced —
`ZoneEditorViewModel.kt:126-128` already documents this rule and the "log from `:data`" workaround),
so `history_query_split` lives in `TrackingRepositoryImpl` where every other `FTD_EVENT` in this
codebase already lives, not in the ViewModel.

**Live re-verification after the fix** (rebuilt debug, same 8,460-point real DB, selected the day
again):

```
01:27:29.222  history_query_split day=2026-08-20 pointCount=8460 sessionCount=2 pipelineMs=102
01:27:29.810  history_simplify pointCount=5760 simplifiedCount=552 simplifyMs=572
01:27:29.900  history_rendered day=2026-08-20 pointCount=5760 frameMs=16
```

Real total = 102 + 572 + 16 = **690ms** (single cold real-device run, debug build, some contention
from the rest of the app — the `:data` instrumented test's 498ms median is the cleaner isolated
number; both agree simplify dominates and both are under 1000ms but with modest margin).

### Docs — deliberately did NOT edit the PRD

PRD §7.1/§10 still say `history_rendered`/`renderMs` alone proves the < 1s threshold — left
**unedited**. This project's own convention (`LLM.md` §13 "Open" #3: *"PRD không được sửa lại
(BA-owned, có version history); chỉ ghi lệch ở đây"*) says PRD defects get logged as a deviation in
`LLM.md`, not silently edited by an engineering agent. Added `LLM.md` §13 **Fixed #15** documenting
this exact defect, the fix, and the fact that PRD's wording is now stale until the BA/phase-11 walks
through it. (I did try editing the PRD directly first, then reverted both edits on finding this
precedent — see git diff if you want to see what I almost did.)

## Step 3 — Hand-calculated stats vs. app-displayed stats (US-29 gap the tester skipped)

Used the exact same deterministic point-generation formula as the seeded 8,460-row dataset
(constant `1.0/111_320.0`° north drift + zigzag longitude, `math.sin`) to independently hand-compute
expected distance/duration/speed in Python (own haversine implementation, not copied from
`GeoDistance.kt`), then compared against what the **release-equivalent app UI actually displayed**
on `emulator-5554` (screenshot evidence, `RouteStatsCard` + `SessionList`):

| | Hand-calculated | App displayed | Match |
|---|---|---|---|
| Session 1 (00:00→15:59, selected) distance | 17,520.736 m → `DistanceFormat` → "17.5 km" | **"17.5 km"** | ✓ exact |
| Session 1 duration | 57,590,000 ms → `DurationFormat` → "15h 59p" | **"15h 59p"** | ✓ exact |
| Session 1 avg speed | 1.0952 km/h → `"%.1f"` → "1.1" | **"1.1 km/h"** | ✓ exact |
| Session 2 (16:20→23:49) distance | 8,207.447 m → "8.2 km" | **"8.2 km"** | ✓ exact |
| Session rows clock format | `formatClock` → "00:00 → 15:59", "16:20 → 23:49" | **same** | ✓ exact |

**0% discrepancy on every field checked.** Distinguishing the two possible failure sources the task
asked about:
- **(a) `RouteStats.of` computing wrong values** — not observed. `distanceMeters`/`durationMs`/
  `averageSpeedKmh` all matched an independently-implemented haversine calculation to the displayed
  precision.
- **(b) `:ui` format layer (`DistanceFormat`/`DurationFormat`) rounding/unit bugs** — not observed
  either. `m` vs `km` threshold (< 1000m → meters, else 1-decimal km), `%.1f` speed rounding, and
  `HH:mm` clock truncation (drops seconds, e.g. `15:59:50` → `"15:59"`) all matched expected output
  exactly.

US-29's "values correct" claim (which the tester's report asserted without comparing to any
hand-computed number) **is actually true** for this dataset — just previously unverified, not
previously wrong.

## Step 4 — Cleanup

- Removed all temporary `FTD_TEMP_ORDER` proof-of-ordering logs (not shipped in any build).
- Deleted all 8,460 seeded `location_points` rows for self (`id LIKE 'perf-real-%'`) via the WAL
  procedure (force-stop → pull 3 files → `PRAGMA wal_checkpoint(TRUNCATE)` → `DELETE` → push `.db`
  back → delete `-wal`/`-shm` on device → **read back and confirm 0 leftover rows** — done, verified
  `SELECT count(*) WHERE id LIKE 'perf-real-%'` → `0`). Left the 2 pre-existing rows (Minh, Lan) from
  a prior session untouched — not mine to remove.
- `data:connectedDebugAndroidTest`'s own `HistoryPipelineScaleTest` uses a **separate, file-backed
  test-APK database** (`history-pipeline-scale-test.db`, its own app-under-test data dir, deleted in
  `@After`) — never touches the real app DB, so its 8,640-point dataset needed no manual cleanup.
- Rebuilt `assembleRelease`, reinstalled on `emulator-5554`, confirmed `run-as` now fails with
  `"package not debuggable"` (release, as required), launched the app — no crash, History tab shows
  the correct empty state for today (22/08/2026), no leftover polyline.

## Verification — full acceptance checklist run

```
./gradlew test                                                              → BUILD SUCCESSFUL (all unit tests incl. 106 pre-existing)
./gradlew :data:connectedDebugAndroidTest                                   → 13/13 tests pass (9 pre-existing + ZoneDaoTest etc. + 1 new HistoryPipelineScaleTest, incl. its own PRD-§7.1 assertion)
./gradlew clean assembleDebug --no-configuration-cache | grep -ci "warning:" → 1  (matches ENV-BRIEFING.md §8 baseline)
./gradlew assembleRelease                                                   → BUILD SUCCESSFUL
```

## Files changed

**Created**
- `data/src/androidTest/java/.../data/perf/HistoryPipelineScaleTest.kt` (173 lines) — permanent
  regression test, asserts full pipeline < 1000ms at 8,640 points (PRD §7.1 gate, now a real
  automated one for phase-11 instead of a self-reported number)
- `data/src/androidTest/java/.../data/perf/HistoryPipelineTestFixture.kt` (68 lines) — seed data +
  timing helpers, split out to keep the test file under LLM.md §5's 200-line limit

**Modified**
- `data/build.gradle.kts` — `androidTestImplementation(libs.maps.compose.utils)`, test-scoped only
- `ui/.../history/component/HistoryMap.kt` — `renderMs` → `frameMs`, KDoc explains scope
- `ui/.../history/component/RoutePolyline.kt` — added `history_simplify` log around `PolyUtil.simplify`
- `data/.../repository/TrackingRepositoryImpl.kt` — added `history_query_split` log (first emission
  per fresh collection only)
- `LLM.md` — §13 Fixed #15 (this defect, full writeup, why PRD itself wasn't touched)

**Not modified (considered, reverted)**
- `docs/FTD001_FamilyTrackerDemo_PRD.md` — edited then reverted; see "Docs" note in Step 2.

## Where I think the ORIGINAL diagnosis undersold the problem

The task's diagnosis said the metric "đo nhầm đoạn" (measures the wrong segment) and that the 100x
conclusion is "chưa được chứng minh" (unproven). Both correct, but the live device evidence in Step
2 shows something stronger: it's not just an unproven number, the real cost (498-690ms, ~94% of it
one synchronous call) leaves only a **~2x** margin, and that ~500-700ms is a **silent main-thread
freeze with no loading indicator** — `HistoryState.isLoading` only covers the Room-query phase
(`observeRoute`'s `collectSafely`), not the composable-side `PolyUtil.simplify`, so the UI shows the
map already "loaded" while actually frozen. This is worth phase-11 or a future phase treating as a
real UX defect, not just a telemetry-naming defect — flagged here, not fixed (out of scope: task
was "sửa phép đo", not "sửa hiệu năng").

## Unresolved questions

1. Should `PolyUtil.simplify` move off the UI thread (e.g. `remember` + `LaunchedEffect`/
   `produceState` computing it in a background dispatcher) to stop the ~500-700ms main-thread
   freeze this report proves exists at 8,640-point scale? Not fixed here — out of the stated
   "fix the measurement" scope, but the freeze itself is now proven real, not hypothetical.
2. `PolyUtil.simplify`'s near-O(n²) scaling on this zigzag data shape (Step 1) — is
   `SIMPLIFY_TOLERANCE_METERS = 10.0` still right at higher point densities, or would a cheap
   radial-distance pre-filter before Douglas-Peucker help? Not investigated; flagged for whoever
   owns future performance tuning.
3. This report's `HistoryPipelineScaleTest` doesn't measure actual Compose layout/draw or
   GoogleMap's native tile/overlay rendering of the simplified polyline — only the data-layer +
   `PolyUtil` cost. A Macrobenchmark-style on-screen frame-timing test would be needed to close that
   last gap in the < 1s claim; not attempted here (would need `:app`/`:ui` instrumentation
   infrastructure that doesn't exist yet).
