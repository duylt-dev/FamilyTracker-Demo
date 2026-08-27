# Fix2 Phase 08 Report — move `PolyUtil.simplify` off the UI thread

**Status**: DONE. Device (`emulator-5554`) back on `release` build, DB clean (0 leftover seed rows,
read-back confirmed). All acceptance steps complete.

## TL;DR — before/after

| Metric (isolated measurement, cold-start excluded, 8,460 pts, same day/session) | Before | After |
|---|---|---|
| `PolyUtil.simplify` thread | Main/UI (`tid == pid`) | `Dispatchers.Default` worker (`tid != pid`) |
| UI thread blocked by simplify | ~567-576ms | ~0ms (state write only) |
| `dumpsys gfxinfo` slowest frame in this scenario | **650ms** (1 frame) | **105ms** (1 frame, normal Compose draw of 552 vertices) |
| `dumpsys gfxinfo` 99th percentile | 650ms | 105ms |
| Total pipeline (`query_split`+`simplify`+`rendered`, summed) | ~699-701ms | ~737ms (same magnitude — algorithm cost unchanged, PRD §7.1 <1000ms still met) |
| Polyline renders correctly | yes | yes (screenshot-verified, both default + secondary session) |
| Map interactive during the wait | No — completely frozen ~650ms | Yes — pannable/zoomable immediately, polyline appears ~0.6-0.8s later |

## Scope

Round 1 (`fix-phase-08-report.md`) fixed the *measurement* (renamed `renderMs`→`frameMs`, added
`history_query_split`/`history_simplify` events, proved via real-device logcat that
`PolyUtil.simplify` runs synchronously inside `remember` on the UI thread, 574ms at 8,460 points).
It explicitly did **not** fix the freeze itself (see its "Unresolved questions" #1).

This round: move that computation off composition / off the UI thread, keep `RouteStats` on the
raw list, keep the 3 telemetry events, prove before/after with real numbers.

## Inherited baseline (NOT re-measured, per ENV-BRIEFING.md + fix-phase-08-report.md)

- G6 warning baseline = **1** (`--no-configuration-cache`)
- `PolyUtil.simplify` at 8,640 pts (`:data` androidTest, `HistoryPipelineScaleTest`): median
  **466ms** for the default-selected (5,760-pt) session, full pipeline total **498ms**
- Real-device (8,460 pts, actual app DB) proof: `simplify_start`→`simplify_end` = **574ms**,
  entirely before `HistoryMap`'s `LaunchedEffect` timer even restarts (i.e. it's real synchronous
  main-thread work, not an artifact of the old metric)
- `PolyUtil.simplify` scales ~O(n^2.1) on this zigzag data shape (session0 466ms/5760pts vs
  session1 109ms/2880pts — 2.0x points, 4.3x time)

## Plan

1. [x] Confirm diagnosis: is `remember` really on the UI/main thread here? (read code, confirm)
2. [x] Re-run `HistoryPipelineScaleTest` once to get a fresh "before" total-pipeline number for THIS round
3. [x] Capture "before" janky-frames baseline on-device (`dumpsys gfxinfo`, isolated, 8,460 real points)
4. [x] Implement fix in `RoutePolyline.kt`
5. [x] Update `LLM.md` §13 (Fixed #16, forward-pointer from #15)
6. [x] Rebuild debug, re-verify polyline still renders correctly (screenshot, both sessions) + capture "after" janky frames + main-thread-not-blocked proof (thread id evidence)
7. [x] Full gate checklist (`test`, `connectedDebugAndroidTest`, warning count, `assembleRelease`) — all green
8. [x] Clean 8,460-point seed data (read-back confirmed 0 leftover), leave device on release build

## Findings

### 0. Diagnosis re-confirmed, not re-derived

`app/src/main/java/.../MainActivity.kt` uses plain `setContent { }` (`androidx.activity.compose`),
no custom `Recomposer`/dispatcher. Composition genuinely runs on the default `AndroidUiDispatcher.Main`
= the UI thread. Nothing in this codebase already offloads composition. So the premise is correct:
`remember(rawLatLngs) { PolyUtil.simplify(...) }` in `RoutePolyline.kt` really does block the UI
thread. (Round 1 already proved this with matching-tid logcat evidence; this round just double-checked
there's no exotic Recomposer setup that would invalidate that.)

### 1. Fresh "before" numbers, THIS round, THIS environment

**`:data:connectedDebugAndroidTest` `HistoryPipelineScaleTest` (8,640 pts, unaffected by this fix —
calls `PolyUtil.simplify` directly, not through Compose):**

```
history_pipeline_scale_test pointCount=8640 sessionCount=2 ... 5_polyutil_simplify_default_selected_sessionMs=512
totalMedianMs=546  (query=19 map=2 split=13 stats=0 simplify=512)
```

Matches round 1's range (466-574ms simplify, ~500-550ms total) — no drift.

**Real on-device proof (release-shape debug build, actual app DB, self member, 8,460 pts seeded for
TODAY 2026-08-22 so History's default day lands on it directly — see WAL procedure below), fresh
app launch → tap "Lịch sử" tab → History renders:**

```
history_query_split day=2026-08-22 pointCount=8460 sessionCount=2 pipelineMs=122
history_simplify pointCount=5760 simplifiedCount=552 simplifyMs=567       <- pid=tid=2452, i.e. UI thread
history_rendered day=2026-08-22 pointCount=5760 frameMs=12
```

Real total = 122+567+12 = **701ms**.

**`adb shell dumpsys gfxinfo` for this exact scenario (BEFORE fix), fresh reset right before the
tap that triggers the freeze:**

```
Total frames rendered: 87
Janky frames: 13 (14.94%)
99th percentile: 700ms
Number Slow UI thread: 5
HISTOGRAM: ... 700ms=2 ...
```

Two frames land in the 700ms bucket — directly corresponds to the 567ms `simplify` UI-thread block.
This is new evidence round 1 didn't gather (round 1 proved the block existed via nanoTime logs;
this shows its actual user-visible cost in dropped/late frames).

Screenshot before fix: `scratchpad/before-fix-history.png` — polyline renders correctly (straight
line, zigzag amplitude only 15m so invisible at city zoom, as expected), stats match round 1
(17.5 km / 15h 59p / 1.1 km/h for the selected session).

### 2. Seeding method (today, not a past day — avoids needing DayPickerBar automation)

`HistoryViewModel.initialStateFrom` defaults `selectedDay = LocalDate.now()`. Seeded 8,460 points
(5,760 + 20-min gap + 2,700, same shape as round 1 and `HistoryPipelineTestFixture`'s deterministic
zigzag formula: `NORTH_STEP_DEG = 1.0/111_320`, `ZIGZAG_AMP_DEG = 15.0/111_320`, phase step 0.3) for
self (`memberId=54e7c30c-a85d-4b71-af51-53da234dd7be`, day boundary computed for `Asia/Ho_Chi_Minh`,
device's actual `persist.sys.timezone`) directly for **today** (2026-08-22) instead of a past day —
lets History's default landing show the data with zero UI automation for the date picker. WAL
procedure: force-stop → pull `.db`/`.db-wal`(0 bytes, already checkpointed)/`.db-shm` → python3
sqlite3 direct insert (id prefix `perf-real-` for later targeted cleanup, same convention round 1
used) → push `.db` back via `/data/local/tmp` + `run-as cp` (direct `run-as sh -c 'cat > ...'`
piping failed with "No such file or directory" — selinux/context issue with piped stdin through
`run-as`, worked fine going through `/data/local/tmp` first) → deleted stale `-wal`/`-shm` on device
→ pulled `.db` back down and read with python3 to confirm `8460` rows with the `perf-real-` prefix
before touching the UI.

### 3. First gfxinfo attempt was confounded by cold-start — caught and corrected

First pass: reset `gfxinfo` right at app launch, then walked through permission dialogs, then
tapped History. Result showed 2 frames at 700-750ms — but `dumpsys gfxinfo <pkg> framestats`
(raw per-frame `IntendedVsync`/`FrameCompleted` timestamps) showed those 2 slow frames were
**frame #0 and #1 of the whole session** (before the History tap even happened) — i.e. cold-start
cost (GoogleMap/Play Services first-init on the Map tab), not the simplify freeze. Caught this by
parsing the raw framestats table, not by trusting the summary histogram alone.

**Corrected methodology**: launch app, let it settle 4s on the Map tab (permissions already
granted, cold-start jank already elapded), reset gfxinfo THEN, THEN tap "Lịch sử". This isolates
the metric to just the History tab's own first render. Re-measured BOTH sides with this corrected
method (temporarily reverted `RoutePolyline.kt` to the pre-fix `remember` version via a backed-up
copy, rebuilt, measured, restored the fix, rebuilt again — diff-verified byte-identical restore).

**BEFORE fix (isolated, same 8,460-pt real DB, same day, same tap):**

```
Total frames rendered: 66
Janky frames: 3 (4.55%)
99th percentile: 650ms
Number Slow UI thread: 1
HISTOGRAM: ... 101ms=1 ... 650ms=1 ...   <- exactly ONE frame at 650ms
history_query_split pipelineMs=121
history_simplify simplifyMs=576   (tid=pid=3678, i.e. UI thread)
history_rendered frameMs=2
```

One single frame stalls ~650ms — matches `simplifyMs=576` almost exactly (the remaining ~74ms is
Choreographer/traversal overhead around that block). This is the real, isolated freeze cost, not
confounded by cold-start.

**AFTER fix (isolated, identical scenario, code fixed, rebuilt+reinstalled):**

```
Total frames rendered: 46
Janky frames: 4 (8.70%)
99th percentile: 105ms
Number Slow UI thread: 1
HISTOGRAM: ... 32ms=1 34ms=1 38ms=1 65ms=1 77ms=1 105ms=1 ...   <- NOTHING above 105ms
history_query_split pipelineMs=115
history_rendered frameMs=11
history_simplify simplifyMs=611   (tid=3358, pid=3312 — DIFFERENT thread, i.e. NOT the UI thread)
```

No frame anywhere near 500ms+ — the entire >100ms tail is gone. `history_rendered` (map+markers
visible) fires at 115+11=126ms after the tap, well before `history_simplify` even finishes
(611ms later, on a worker thread) — the map is fully interactive (pannable/zoomable) while the
polyline is still computing in the background, unlike before where NOTHING responded for ~650ms.
The remaining single "Slow UI thread" frame (105ms) is normal Compose recomposition/draw cost of
committing 552 polyline vertices once the background result arrives — legitimate main-thread work
(Skia draw-command generation), an order of magnitude cheaper than the 650ms Douglas-Peucker
computation it replaced, and not the kind of freeze a user perceives as "stuck".

**Total pipeline (sum of the 3 log events) is essentially unchanged**, as expected — the fix moves
WHERE `PolyUtil.simplify` runs, not how expensive the algorithm itself is: 121+576+2=699ms before,
115+611+11=737ms after (both ~700ms, run-to-run noise ~60ms on this emulator). What changed is
which THREAD pays that cost — main thread (before) vs a `Dispatchers.Default` worker thread
(after) — which is exactly the fix's goal (PRD §7.1's "< 1s from day selection" is about the total
still finishing in time, which it does either way; the freeze was specifically about the UI thread
being unresponsive, not about the total wall-clock time).

### 4. Screenshots — polyline still renders correctly (both sessions)

- `scratchpad/before-fix-history.png` — pre-fix, default session (17.5 km / 15h 59p / 1.1 km/h)
- `scratchpad/after-fix-history.png` — post-fix, same session, pixel-identical stats and polyline
  shape
- `scratchpad/after-fix-session2.png` — post-fix, switched to the SECOND session (8.2 km / 7h 29p),
  confirms `LaunchedEffect(rawLatLngs)`'s cancel-and-replace correctly redraws the new session's
  polyline (no stale/frozen line left over from the first session)
- `scratchpad/final-release-state.png` — final device state, release build, History empty state for
  today (DB cleaned)

## Fix rationale (why this approach, what was rejected)

**Where the fix lives:** `:ui` (`RoutePolyline.kt`), not `:domain`. Considered moving the transform
to `HistoryViewModel`/a `:domain` use case per the task's suggested direction, but `PolyUtil` and
`LatLng` (`com.google.android.gms.maps.model`, `com.google.maps.android`) require a real Android
runtime — `:domain` is a pure `kotlin.jvm` module (`domain/build.gradle.kts`, no `androidTest`
capability at all, confirmed same constraint round 1 hit when placing `HistoryPipelineScaleTest`).
Importing either type into `:domain` is a straight compile error, not a style violation. So "off the
UI thread" had to mean "same tier (`:ui`), different dispatcher" — which is also consistent with
CLAUDE.md's ViewModel rule ("No Compose or platform import inside a ViewModel"): `LatLng` is exactly
the kind of platform-dependent type that rule is written to keep out of ViewModels (proven by needing
a real Android runtime — JVM unit tests hit `Stub!` on it, same evidence round 1 used for why
`PolyUtil`/`LatLng` need `androidTestImplementation`, not `implementation`, in test scope).

**How:** `remember { PolyUtil.simplify(...) }` (synchronous, composition phase) →
`LaunchedEffect(rawLatLngs) { withContext(Dispatchers.Default) { PolyUtil.simplify(...) } }`
(async, off the UI thread). `simplified` is now `mutableStateOf`, initialized empty on every
`rawLatLngs` key change so a session/day switch never shows a stale polyline while the new one
computes. `LaunchedEffect` gives free cancel-and-replace semantics on rapid session/day switching —
same pattern `HistoryViewModel.onSelectDay`/`observeRoute` already uses for its own coroutine job,
so this isn't a new concurrency idiom for the codebase, just the same one applied one layer down.

**Rejected: fast-path for `rawLatLngs.size <= 2`.** Considered adding a synchronous branch for
trivial trips (0-2 points, no actual Douglas-Peucker work happens even in the original code) to
avoid the one-frame dispatcher-hop delay for short trips. Not implemented: the delay for that case is
below perceptible threshold (a single `Dispatchers.Default` round-trip with near-zero CPU work, not a
16ms-frame-visible flicker), and the two-path code would violate KISS for a benefit that isn't
measurable. Flagging this trade-off explicitly since the task asked to state reasoning, not just the
choice.

**On "flicker / two-stage render":** the task specifically asked me to check for this. My design
does NOT show the raw (unsimplified) polyline as a first stage and then swap to the simplified one —
that would be the actual "hai giai đoạn" (two visibly different shapes) failure mode, and would also
reintroduce a different perf problem (native rendering of thousands of raw vertices). Instead
`simplified` starts **empty**, so there's a brief window (until the background computation lands)
where the map shows only the Start/End markers and no line, then the line appears once — a
single-stage delayed appearance, not a shape-swap. This is confirmed by both screenshots showing
identical polyline shape/position to the pre-fix synchronous version — the ONLY thing that changed is
what the user could do (pan/zoom) during the ~0.6-0.8s wait.

## Files changed

**Modified**
- `ui/.../history/component/RoutePolyline.kt` (92 lines, under LLM.md §5's 200-line limit) —
  `PolyUtil.simplify` moved from `remember` to `LaunchedEffect` + `withContext(Dispatchers.Default)`;
  KDoc extended with the fix2 rationale and real numbers
- `LLM.md` §13 — extended Fixed #15 with a forward pointer, added Fixed #16 (this defect: the real
  freeze, not just the mismeasurement; the fix; before/after numbers)

**Not modified**
- `HistoryMap.kt`, `HistoryViewModel.kt`, `HistoryContract.kt`, `TrackingRepositoryImpl.kt` — no
  changes needed; the 3 telemetry events (`history_query_split`/`history_simplify`/
  `history_rendered`) from round 1 are untouched and still sum to the real total
- `RouteStats.of` still computes on the raw (un-simplified) point list — verified unchanged (no
  edit made to `:domain`), matches the task's explicit "keep this" instruction

## Verification — full acceptance checklist

```
./gradlew test                                                              → BUILD SUCCESSFUL (all unit tests, incl. CoroutineSafetyArchitectureTest)
./gradlew :data:connectedDebugAndroidTest                                   → 13/13 tests pass (incl. HistoryPipelineScaleTest, unaffected — 546ms median, algorithm cost unchanged)
./gradlew clean assembleDebug --no-configuration-cache | grep -ci "warning:" → 1  (matches ENV-BRIEFING.md §8 baseline)
./gradlew assembleRelease                                                   → BUILD SUCCESSFUL
```

On-device (release build, `emulator-5554`): `run-as` fails with "package not debuggable" (confirms
release, not leftover debug); History tab shows correct empty state for today (22/08/2026);
`location_points` has 0 rows matching `perf-real-%` (read-back confirmed after WAL push-back), only
the 2 pre-existing demo rows (Minh, Lan) remain untouched.

## Where I think the orchestrator's diagnosis needed more, not less

Nothing to push back on substantively — the diagnosis (freeze exists, is real, round 1 measured but
didn't fix it) matched what I found exactly, including the specific point-count range (466-574ms)
cited in the task, which round 1's own numbers back up. The one thing I'd add: the task's framing
treated "UI thread blocked" and "gfxinfo janky frames" as two separate asks, but they turned out to
be the SAME evidence viewed two ways once I fixed a methodology bug of my own (first `gfxinfo`
attempt was confounded by cold-start jank — see Finding 3 — caught by cross-checking the summary
histogram against raw `framestats` timestamps before trusting it). Worth remembering for future
`gfxinfo`-based measurements on this project: **always reset counters after the app has settled, not
at launch**, or cold-start dominates the signal.

## Unresolved questions

1. The now-remaining single ~105ms main-thread frame (Compose committing 552 polyline vertices once
   the background result lands) is real Compose/Skia draw-command-generation cost, not something this
   fix touches. It's an order of magnitude below the old 650ms freeze and isn't perceived as a
   freeze, but if a future phase pushes point density up further (config change to a shorter GPS
   interval, per round 1's O(n²) scaling note on `PolyUtil.simplify` itself, or PRD's ceiling
   changing), this draw cost would grow too and isn't covered by any existing test.
2. `HistoryState.isLoading` still only covers the Room-query phase, not the (now background, but
   still real) simplify wait — a future phase adding a loading affordance for that ~0.6-0.8s window
   (map shown, polyline pending) would be a genuine UX improvement, not required by this task's scope
   (which was specifically "get it off the UI thread", not "add a spinner").
3. Total pipeline time (query+simplify+rendered summed) is unchanged by this fix, by design — this
   fix does not touch PRD §7.1's "<1s from day selection" margin (still ~2x per round 1's numbers,
   ~700ms/1000ms). If that margin needs widening, it's `PolyUtil.simplify`'s own near-O(n²) cost
   (round 1 finding, not re-verified this round) or the Room query/mapping/split stages that would
   need work — out of scope here, this task was specifically about which THREAD pays the cost.
