# Docs Impact Report — Di chuyển mượt, bám đường

**Plan:** `plans/260825-0956-smooth-road-following-member-movement/`  
**Scope:** Documentation changes required to reflect code changes across 6 phases  
**Prepared:** 2026-08-25 · duylt (lothanhduy2003@gmail.com)

---

## Executive Summary

This plan reverses one deliberate architectural decision (no marker animation) and introduces three new architectural patterns (animation via `withFrameNanos`, vertex-preserving polyline traversal, three-tier route sourcing). Each reversal and pattern carries new documentation obligations per `.claude/rules/documentation-management.md`.

**Files requiring changes:** 8 major documents  
**New sections:** 2 (one architecture pattern, one PRD clarification)  
**Deviations:** 1 new Open, 7 existing Open rows assessed

---

## PART 1: File-by-File Documentation Changes

### 1. `LLM.md` Section 2 — Module Dependencies

**Location:** `LLM.md:40-77` (dependency graph and rules)

**Change Required:** None specifically for layer boundaries. The new `TrackingRepository.observeLiveSelfLocation()` method (Phase 01) crosses an existing boundary — it allows `:ui` to read live location from `:domain/repository` — but this is already justified by existing §8.4 text ("cổng cấp toạ độ"). The new method is a _display-only_ gate alongside the existing write gate; the existing justification covers it. No § 2 edit needed.

**Verification:** Phase 01 reads `LocationPointProcessor` which is `:data`'s concern, so it needs a `:domain/repository` interface to communicate with `:ui`. This is already the pattern for `LocationSource`.

---

### 2. `LLM.md` Section 3 — Package Layout Trees

**Location:** `LLM.md:80-390` (`:domain`, `:ui`, `:data` package trees)

**Changes Required:**

#### Phase 01 — Display-only GPS gate
- **File to add in `:data/location/`:**
  ```
  │ ├── LiveSelfLocation.kt         Holder MutableStateFlow<LocationPoint?> + publish() + observe()
  ```
- **File to add in `:ui/feature/map/component/`:**
  ```
  │ │       ├── SelfAccuracyCircle.kt  internal @GoogleMapComposable — Circle bán kính accuracyMeters
  ```

#### Phase 02 — Polyline following (`:domain` only)
- **New classes in `:domain/tracking/`:**
  ```
  │ ├── GeoBearing.kt               initialBearing(a,b) · shortestDelta(a,b) · lerpBearing
  │ ├── PolylineFollower.kt          parametrize(points) -> ParametrizedPath
  │ │                               advance(path, cursorM, stepM) -> Progress (vertex-preserving)
  │ ├── SyntheticPath.kt             between(from, to, seed) -> List<GeoPoint> — tier 3 of D5
  │ ├── RouteGeometryGuard.kt        isUsable(points, zone, kind): Boolean
  ```
- **Existing class to extend:**
  ```
  │ ├── MemberRoamer.kt              + withPath(state, points) -> RoamState method
  │ │                               + RoamStep sealed class (NeedPath, Move)
  ```

#### Phase 03 — Marker animation (`:ui` only)
- **File to add in `:ui/core/motion/`:**
  ```
  │ ├── motion/
  │ │   ├── MarkerInterpolation.kt   lerpDegrees, lerpBearing (shortest path), progressOf
  ```
- **File to add in `:ui/feature/map/component/`:**
  ```
  │ │       ├── AnimatedMarkerPositions.kt   internal — rememberAnimatedMarkerPositions(),
  │ │       │                               MarkerSample, AnimatedMarkerPosition (primitives only)
  ```

#### Phase 04 — Route sourcing (`:data/routing/` package)
- **New package and classes:**
  ```
  data/routing/
  ├── MemberRouteSource.kt         tier 1/2/3 orchestration + cache + silent downgrade
  ├── MemberRouteCacheStore.kt     filesDir/routes/, not assets/
  ├── RouteCache.kt                 cache entry with schemaVersion
  ```

#### Phase 05 — OSM attribution strip (new component)
- **File to add in `:ui/feature/map/component/`:**
  ```
  │ │       ├── RoutingAttributionStrip.kt   internal — shows only when tier 1/2 active
  ```

**Format for §3 update:** Add each file path in the location where it's listed in the package tree. Maintain alphabetical order within each `├──` list. Example format already in the doc.

---

### 3. `LLM.md` Section 8 — Location Architecture

**Location:** `LLM.md:691-950` (eight subsections covering zones, filters, sources, service lifecycle, attribution, and notification)

**Changes Required:**

#### §8.3 `LocationFilter` — Display vs. Write Gates (CRITICAL)
**Current:** Lines 797-827 describe the filter rules (accuracy > 50m, distance < 10m, speed > 200km/h) but DON'T mention that `MAX_ACCURACY_M` governs WRITING only.

**Exact text to add** (after KDoc of `LocationFilter.accept()`, before the three-rule table):

```
**§8.3 addendum — Phase 01 (D4): Display-only gate upstream of filter**

`LocationPointProcessor.process()` publishes **every** point to `LiveSelfLocation` **before** 
running `LocationFilter`. The filter determines what goes into `location_points` (Room) and 
History tab. It does NOT determine what appears on the map as the blue dot.

Rule set:
- `MAX_ACCURACY_M = 50.0` governs **storage** (what goes in location_points)
- Display-only gate `TrackingRepository.observeLiveSelfLocation()` governs **visualization** 
  (what the map draws)

This separation enables:
(a) In-home GPS (accuracy 80–200m) to show a blue dot immediately, with accuracy circle
(b) Polyline tab (History) to remain clean — no kinks from low-accuracy points (US-31)
(c) Single source of truth for `lastKeptPoint` in `LocationFilter` — unchanged logic

See Phase 01 Architecture diagram and `decisions.md` §D4 for full reasoning.
```

**Update plan.md reference (line 113):** Change from a passing comment to a explicit sentence pointing to this new addendum.

---

#### §8.4 `LocationSource` Interface — NEW DOCUMENTATION FOR PHASE 04 THREE-TIER ROUTING

**Current:** Lines 828-865 describe `LocationSource` as a simple interface with `FusedLocationSource` (real) and `SimulatedLocationSource` (demo).

**Exact text to add** (after current §8.4 content, before §8.5):

```
### 8.4b Route sourcing — Three tiers with silent fallback (Phase 04, D5)

`ObserveNavigationUseCase` and `MemberMovementSimulator.pathFor()` both call a route provider. 
The implementation stacks three tiers:

| Tier | Source | Data | Attribution | When Used | Caching |
|---|---|---|---|---|---|
| 1 | `RoutingProvider.directions()` (GraphHopper / Valhalla) | OSM | Mandatory | Network + API key available | —; fetch each time |
| 2 | `MemberRouteCacheStore` (filesDir/routes/) | OSM | Same as tier 1 | Tier 1 succeeded at least once | Key: `zoneId + centerLat-5digits + radius` |
| 3 | `SyntheticPath` (pure geometry, no OSM) | None | "estimated" label, NOT OSM credit | Always fallback | Deterministic per `(member.id, zoneId, fromBearing)` |

**Phase 04 implementation:** `MemberRouteSource` tries each tier, logs failure silently (no toast, 
no dialog), and accepts whatever succeeds. The app state never crashes or "looks broken" — it 
gracefully shows a synthetic curve instead of a real road when offline or API-key-less.

**Attribution rule:** Show OSM credit ONLY when tier 1 or 2 is active. Phase 05 adds the 
attribution strip; it checks which tier the current route came from and shows or hides credit 
accordingly. Tier 3 contains zero OSM data, so crediting it would be a false claim.

**Limitation (Open #41 in §13):** US-41 "member walks on real road" is met only by tiers 1/2. 
Tier 3 is a purely estimated curve. This is acceptable for offline/keyless fallback; it is not 
suitable for a feature requiring "real routing." Documented here for next developer who 
encounters this phase and wonders if tier 3 is a bug.
```

---

### 4. `LLM.md` Section 11 — Test Layout

**Location:** `LLM.md:1086-1106` (test file organization)

**Change Required:** Add new test files under appropriate categories:

**Exact text to add** (in the `:domain` test section, after `LocationFilterTest`):

```
`domain/src/test/kotlin/…/tracking/PolylineFollowerTest.kt`
    Vertex-preserving arc-length parametrization — bảo toàn đỉnh là bắt buộc cho nội suy 
    phase-03, mọi kiểm chặn như một nguyên tắc kiến trúc (QA-SRM-02, researcher-01 §D.2)

`domain/src/test/kotlin/…/tracking/GeoBearingTest.kt`
    Shortest-path bearing delta — critical for marker rotation phase-03
    (QA-SRM-07, researcher-02 §C). `lerpBearing(a, b, 0.5)` must reach exactly one solution.

`domain/src/test/kotlin/…/tracking/SyntheticPathTest.kt`
    Deterministic curve generation (tier 3 fallback) — seed-based geometry for caching phase-04

`domain/src/test/kotlin/…/tracking/RealGpsNoSnapArchitectureTest.kt`
    (Phase 01) Grep scan: no mã code references routing types in GPS pipeline.
    Prevents future developer from adding snap-to-road to live GPS (D4 boundary).
```

**Exact text to add** (in the `:ui` test section, after existing tests):

```
`ui/src/test/kotlin/…/core/motion/MarkerInterpolationTest.kt`
    JVM-pure — `lerpDegrees`, `lerpBearing`, `progressOf` (phase-03, QA-SRM-22).
    Ca bắt buộc: 100 random `t` values between any two points must yield distances 
    from the segment < 1e-9 (interpolated path == linear segment on sphere).
```

---

### 5. `LLM.md` Section 12 — Where Do New Files Go?

**Location:** `LLM.md:1107-1129` (file placement table)

**Change Required:** This section already has a table with placement rules. Verify that new package `ui/core/motion/` is mentioned OR add a row:

**Exact text to add** (if not present; if already in table, no change):

```
| Type | Package | Rule | Why |
|---|---|---|---|
| Animation helpers (pure math) | `ui/core/motion/` | JVM-testable, no Compose imports | Reuse in other screens; test without Compose |
| Feature-local animation | `ui/feature/{feature}/component/` | Mark `internal` | Like other feature components |
```

---

### 6. `LLM.md` Section 13 — Known Deviations

**Location:** `LLM.md:1130-end` (open and fixed deviations table)

**Changes Required:**

#### A. Move existing Open rows to Fixed (closing them)
None of the current "Open" deviations are closed by this plan.

#### B. Assess existing Open rows for NEW issues introduced
Check these rows and note if the plan TOUCHES but DOESN'T FIX them:

| Open # | Name | Plan Touches? | Action |
|---|---|---|---|
| 2 | `RouteBlueprint` speed defect (F5/US-33) | Phase 04 reads it, doesn't fix it | Stay Open; add note "Phase 04 reads, does not modify" |
| 7 | Constants not traceable to PRD §6 | Phase 02 adds `SIM_MEMBER_SPEED_MPS`, phase 06 adds measurement constants | Update row: count rises from 12/19 to 15+ — still incomplete but measured. Add "Phase 02/06 add: ..." |
| 8 | Navigation screen has no user story | Phase 05 adds attribution strip for navigation | No code change to user story; update row note only |
| 10 | CI key (GraphHopper API key) missing in CI | Phase 04/05 use tier 3 fallback when key missing | No fix — update note: "Phase 04: tier 3 fallback handles keyless case" |
| 11 | GraphHopper redistribution terms unclear | Phase 05 implements attribution strip | Still Open (legal, not technical) — update date "last reviewed 260825" |
| 13 | Valhalla FOSSGIS fair-use terms | Phase 04 uses Valhalla optionally | Open (legal) — acknowledge in phase 04's §13 |

#### C. ADD NEW OPEN row for marker animation pattern

**Exact text to add** to §13 Open subsection:

```
| 42 | **Marker animation via `withFrameNanos` is new pattern; not in MVI doc**  | 
| | Phase 03 introduces animation loop inside composable (`AnimatedMarkerPositions.kt`). 
| | This is first use of `withFrameNanos` in this codebase and first time mutable state 
| | animates between pure-data samples. Pattern documented inline via KDoc in 
| | `AnimatedMarkerPositions.kt` and §8 of MVI doc (new section added phase-03), but 
| | no section in LLM.md yet. Next phase touching animation should extract rule. | 
| | `ui/feature/map/component/AnimatedMarkerPositions.kt` |
```

#### D. ADD NEW OPEN row for tier 3 limitation

**Exact text to add** to §13 Open subsection:

```
| 43 | **US-41 "walk on real road" not met by tier 3 synthetic path** | 
| | Phase 04 implements three-tier route sourcing (§8.4b). Tier 3 
| | (`SyntheticPath`) is pure geometry — no OSM data, no real road. US-41 
| | acceptance criteria requires real routing; tier 3 is fallback for 
| | offline/keyless cases. Feature is correct but acceptance is limited. 
| | Code comment in `MemberRouteSource` and §8.4b document the boundary. | 
| | `domain/tracking/SyntheticPath.kt`, `data/routing/MemberRouteSource.kt` |
```

---

### 7. `docs/android-mvi-best-practices.md` — New Architectural Pattern

**Location:** `docs/android-mvi-best-practices.md` (section 8, "Compose performance")

**Change Required:** Add NEW subsection describing the animation pattern introduced by Phase 03.

**Location in file:** After existing section 8 (Compose stability), add §8.1 (or wherever it fits):

**Exact text to add:**

```markdown
### 8.1 Animating between pure-data samples — `withFrameNanos` at composable layer

**When this pattern applies:**
- UI must animate smoothly between discrete state samples (e.g., marker position updates every 2.5 seconds)
- Animation targets are geometric/mathematical (lat/lng, bearing, progress ∈ [0,1])
- Interpolation math is reusable and JVM-testable (unit tests before Compose)
- Rendering must stay at 60 fps while sample rate is much slower

**Pattern: Lift animation math to JVM-pure module, animate in composable via `withFrameNanos`**

```kotlin
// ui/core/motion/MarkerInterpolation.kt — JVM-pure
fun lerpDegrees(from: Float, to: Float, t: Float): Float { /* math only */ }
fun progressOf(elapsedMs: Long, durationMs: Long): Float { /* bounds math */ }

// ui/feature/map/component/AnimatedMarkerPositions.kt — Compose
@Composable
fun rememberAnimatedMarkerPositions(
    samples: List<MarkerSample>,  // (id, lat, lng, bearing, recordedAtMs)
): SnapshotStateMap<String, AnimatedMarkerPosition> {
    val state = remember { mutableStateMapOf<String, AnimatedMarkerPosition>() }
    
    LaunchedEffect(samples) { /* update animation targets */ }
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                // One state update per frame, all markers
                state.forEach { (id, pos) ->
                    val elapsed = (nanos - startNanos) / 1_000_000
                    val progress = progressOf(elapsed, pos.durationMs)
                    pos.latitude = lerpDegrees(pos.fromLat, pos.toLat, progress)
                    // ... other fields
                }
            }
        }
    }
    
    return state
}
```

**Why this pattern**:
1. **One animation loop for N markers** (single `withFrameNanos`, one state map) — N coroutines with 
   separate `Animatable` would cause N recompositions per frame.
2. **JVM-testable math** — interpolation functions are pure (no Android/Compose), tested in isolation 
   before integration.
3. **Composable stays simple** — animation state lives as primitives (Float, Double) in the state 
   map, never as LatLng (which is unstable for Compose).

**Cost of getting it wrong**:
- Separate `Animatable` per marker → O(N) recompositions per frame → 60 fps fails at 3–4 markers
- Putting `LatLng` in state → Compose stability issues, missing `compose-stability.conf` 
  (see §8 Fixed #20)
- Animating in ViewModel → ViewModel imports Compose, violates MVI doc §9

**Example: Handling sample arrival mid-animation**
When a new sample arrives before the current animation finishes, set `from = currentPosition` 
(not `previousSample`) so the marker path remains continuous. Without this, marker jumps backward 
to old sample, then re-animates forward.

See Phase 03 of plan `260825-0956-smooth-road-following-member-movement` for implementation 
and QA-SRM-05–08 for acceptance criteria.
```

---

### 8. `docs/routing-and-map-attribution.md` — Three-Tier Attribution Mapping

**Location:** `docs/routing-and-map-attribution.md` (section 3, "Five things code must keep")

**Change Required:** Expand rule #1 (attribution display) to clarify when attribution IS and IS NOT shown.

**Exact text to add** (after rule #1, before rules #2–#5):

```markdown
#### 3.1 Attribution tiers — show credit only for OSM data present on screen

(Phase 04 adds three-tier routing; Phase 05 implements this rule)

| Tier | Data | Attribution shown | Implementation |
|---|---|---|---|
| 1 | GraphHopper live routing (OSM-derived) | Yes | From `Directions.attribution` in API response |
| 2 | Cached route from tier 1 (OSM-derived) | Yes | Preserved in cache entry; shown on cache hit |
| 3 | `SyntheticPath` (pure geometry, no OSM) | **No** — show "estimated" instead | `MemberRouteSource` detects tier 3, passes `attribution = []` to UI |

**Critical rule:** Do not credit OSM for tier 3. Tier 3 contains zero OpenStreetMap data — crediting it 
is a factual error. It is a fallback for offline/keyless scenarios; the app state remains correct 
and no user story fails.

**Implementation:** `RoutingAttribution.kt` (UI) checks which tier is active. Only tiers 1/2 carry 
actual `Directions.attribution` strings; tier 3 carries an empty list + a flag marking it synthetic.
```

---

### 9. `MemberMarkers.kt` — KDoc Reversal (CRITICAL)

**Location:** `ui/feature/map/component/MemberMarkers.kt:36-38` (current KDoc)

**Current text (from Phase 05 implementation):**
```
// Không animate vị trí giữa 2 lần cập nhật — đúng yêu cầu 'jump, không animate' 
// của chủ dự án. Mỗi mẫu mới là một cú nhảy.
```

**Exact replacement** (Phase 03 reverses this decision):
```
// Nội suy vị trí giữa các mẫu ở tầng Compose via rememberAnimatedMarkerPositions().
// Phase 03 quyết định #3 (D3): độ mượt sinh ra tại tầng hiển thị, không tầng data.
// Mỗi mẫu từ Repository là cơ sở cho animation smooth về mẫu kế tiếp;
// bất kỳ khung hình nào từ Compose đều nằm trên đoạn thẳng nối hai mẫu thực.
// Không ngoại suy sau mẫu cuối (marker đứng lại tại mẫu cuối), không ẩn (D7).
// Xem rememberAnimatedMarkerPositions.kt, phase-03, QA-SRM-05/06/07/08.
```

**Why this is mandatory:** Per `.claude/rules/documentation-management.md`, "documentation drift is 
fixed in the SAME commit that caused it — never deferred." Phase 03 code reverses the KDoc's stated 
design decision; the KDoc must be updated in the same commit.

---

### 10. `docs/project-changelog.md` and `docs/development-roadmap.md`

**Location:** These files (if they exist; check structure per `.claude/rules/documentation-management.md`)

**Changes Required:** Track each phase as it ships.

**For project-changelog.md**, add entries per phase:

```markdown
### Phase 01 — Display-only GPS gate (2026-08-25)
- **Status:** Pending implementation
- **Impact:** P0 bugfix — in-home GPS now displays with accuracy circle; location_points unchanged
- **Files changed:** 4 new, 6 modified
- **Deviation closed:** #D6 (separate display gate from storage filter)
- **User story:** US-43, US-44, US-06 (modified)

### Phase 02 — Polyline following (2026-08-25)
- **Status:** Pending implementation
- **Impact:** Member simulation now follows actual road polylines; bearing and speed become real
- **Files changed:** 8 new (GeoBearing, PolylineFollower, SyntheticPath, RouteGeometryGuard)
- **Constant changes:** SIM_MEMBER_SPEED_MPS (new), STEP_METERS (now derived)
- **User story:** US-40, US-41, US-42

### Phase 03 — Marker animation (2026-08-25)
- **Status:** Pending implementation
- **Impact:** REVERSES no-animation KDoc — markers now interpolate smoothly between samples
- **New pattern:** withFrameNanos animation loop at composable layer (documented §8.1 MVI doc)
- **Files changed:** 3 new, 5 modified
- **User story:** US-40

### Phase 04 — Three-tier route sourcing (2026-08-25)
- **Status:** Pending implementation
- **Impact:** Offline-capable routing — GraphHopper live → on-device cache → synthetic fallback
- **Deviation:** #43 opened (US-41 limited to tiers 1/2 only)
- **Files changed:** 8 new, 12 modified
- **User story:** US-40, US-41, F7

### Phase 05 — OSM attribution strip (2026-08-25)
- **Status:** Pending implementation
- **Impact:** Attribution shown only when OSM data present; tier 3 synthetic paths show "estimated"
- **Files changed:** 2 new, 3 modified
- **User story:** US-40, completes attribution story from phase 04

### Phase 06 — Lap-time measurement and gates (2026-08-25)
- **Status:** Pending implementation
- **Impact:** Validates smooth motion; closes measurement blockers for next project phase
- **Files changed:** 3 new, 1 modified
- **User story:** QA-SRM-29, UAT-01-08
```

**For development-roadmap.md**, update phase status to "In Progress" once implementation begins.

---

## PART 2: Documentation Defects Found

### Defect #1: `.claude/CLAUDE.md` Has Wrong Section Numbers for `LLM.md`

**Severity:** High — actively misdirects every agent and developer reading it

**Locations in `.claude/CLAUDE.md`:**

| Line | Current text | Problem | Fix |
|---|---|---|---|
| 17 | `§11 the list of known deviations` | §11 is actually "Test layout"; §13 is "Known deviations" | Change to `§13` |
| 24 | `Check LLM.md §11 before copying a pattern from an existing file` | §11 is test layout; deviations are §13 | Change to `§13` |
| 25 | `Check LLM.md §12 before "improving" something` | §12 is "Where do new files go"; deliberate patterns are in §13 | Change to `§13` |
| 42 | `Move that row from "Open" to "Fixed" with commit reference` | Same — deviations live in §13 | Change to `§13` |
| 43 | `Add a row to LLM.md §11 "Open"` | Same — deviations are §13 | Change to `§13` |
| 251 | `rewrite LLM.md §2–§11` | Should be §2–§13 to include deviations | Change to `§2–§13` |

**Exact fixes** (six one-line changes, shown with line numbers):

```diff
Line 17:  §11 the list of known deviations
+         §13 the list of known deviations

Line 24:  Check `LLM.md` §11 before copying a pattern
+         Check `LLM.md` §13 before copying a pattern

Line 25:  Check `LLM.md` §12 before "improving" something
+         Check `LLM.md` §13 before "improving" something

Line 42:  Move that row from "Open" to "Fixed" with the commit reference
+         Same guidance, but in LLM.md §13 not §11

Line 43:  Add a row to `LLM.md` §11 "Open"
+         Add a row to `LLM.md` §13 "Open"

Line 251: rewrite `LLM.md` §2–§11 to describe
+         rewrite `LLM.md` §2–§13 to describe
```

**Impact:** Current `.claude/CLAUDE.md` teaches readers to check the WRONG sections. Agents and developers copying patterns from files listed in §13 will cause the EXACT architectural issues those listings are meant to prevent.

---

### Defect #2: Phase 01 Introduces Display Gate, But `LocationFilter` KDoc Doesn't Explain Why

**Current state:** `LocationFilter.kt` line 803 says accuracy > 50m rejects points. Nowhere does it explain that this ONLY governs writes, not reads.

**Result:** Next developer reading code will assume a 80m GPS point is "filtered out" everywhere and wonder why the map shows a blue dot.

**Fix:** Add the Phase 01 addendum to §8.3 (already specified above).

---

### Defect #3: researcher-02 Documents `flat` Parameter Backwards

**In:** `plans/260825-0956-smooth-road-following-member-movement/research/researcher-02-marker-interpolation.md` §C

**Problem:** The document states "`flat = false` ⇒ marker xoay THEO bản đồ" (marker rotates with map). The actual Maps SDK behavior is the opposite: `flat = false` is billboard mode (marker stays upright on screen); `flat = true` makes the marker rotate with camera/map.

**Fix:** This is ALREADY called out in Phase 03 file at line 227 as "Sai lệch phát hiện trong chính research #1". Phase 03 Implementation Step says "phải xác nhận bằng mắt trên thiết bị". Do not trust the researcher doc; verify on device.

**No doc change needed** — the deviation is already documented; Phase 03 blocks on visual verification.

---

## PART 3: Unresolved Questions

1. **`SIM_MEMBER_SPEED_MPS` measurement (Phase 06, B4):** The plan delays choosing the final speed constant until lap-time is measured. The gate is at phase-06 §Implementation. Spec for docs: should we pre-document the measurement protocol or wait until phase-06 to write it?
   - **Recommendation:** Write measurement protocol now in Phase 06 plan, not in permanent docs. Permanent docs will get the final measured value.

2. **PRD delta merge (Phase 01-06):** The BA produced `docs/prd-delta-smooth-road-movement.md` with questions for each phase. Should implementer mark answers in that delta file or in PRD §2 proper?
   - **Recommendation:** Mark in delta as "implemented phase X" (not in PRD §2 yet, since delta is awaiting sign-off from BA). After plan ships and UAT passes, merge delta into PRD §2 in a separate doc-sync commit.

3. **Deviation #43 (US-41 tier 3 limitation):** Should this appear in §13 or in a separate "known limitations" section of `docs/product-roadmap.md`?
   - **Recommendation:** §13 (architectural deviation) because it's a code-design choice (three-tier fallback), not a product limitation. Future phase that removes tier 3 will close it here.

4. **MVI doc section 8.1 (animation pattern):** The new subsection is long. Should it link out to a separate `docs/animation-patterns.md` or stay embedded?
   - **Recommendation:** Stay embedded in MVI doc. It's one pattern, one file, one phase. Extract to separate doc only if a second animation pattern emerges.

5. **`compose-stability.conf` note:** Phase 03 Key Insight #6 says "doesn't get built" and refs §13 Fixed #20. Should Phase 03 implementation instructions include a comment in the code explaining why we don't need it?
   - **Recommendation:** Add KDoc to `AnimatedMarkerPositions.kt`: "State held as primitives (Float, Double) not LatLng, so `compose-stability.conf` not needed. See LLM.md §13 Fixed #20 for context."

---

## PART 4: Summary Table — What Each Phase Documents

| Phase | Primary change | Doc files affected | New sections | Deviations |
|---|---|---|---|---|
| 01 | Display gate for GPS | LLM §8.3, README if exists | None (addendum to §8.3) | None closed; #43 context prep |
| 02 | Polyline following | LLM §3, §11; changelog | None | #7 grows (constants now 15/21) |
| 03 | Marker animation | LLM §3, §12; MVI doc §8.1 **[NEW]**; changelog | §8.1 in MVI doc | #42 opened (new pattern) |
| 04 | Three-tier routing | LLM §8.4b **[NEW]**; routing-attribution.md §3.1 **[NEW]**; changelog | §8.4b, §3.1 in two docs | #43 opened (tier 3 limit); #11 reviewed |
| 05 | OSM attribution strip | routing-attribution.md (reinforces §3.1) | None new | None |
| 06 | Lap-time gates & measurement | Measurement protocol in plan (not permanent docs) | None | #2 may close if speed defect fixed |

---

## Implementation Checklist for Implementer

When creating each phase commit, check off:

- [ ] **Phase 01 commit:** Update LLM.md §8.3 with addendum; update §13 table (Open #43 prep note)
- [ ] **Phase 02 commit:** Update LLM.md §3 (add 4 files), §11 (add test files); update changelog
- [ ] **Phase 03 commit:** Update LLM.md §3, §12; ADD MVI doc §8.1; update `MemberMarkers.kt` KDoc (line 36-38); update changelog
- [ ] **Phase 04 commit:** Update LLM.md §8.4b **[NEW SUBSECTION]**; update routing-attribution.md §3.1 **[NEW SUBSECTION]**; add §13 Open #43; update changelog
- [ ] **Phase 05 commit:** routing-attribution.md already has §3.1 from phase 04 — just update changelog
- [ ] **Phase 06 commit:** Measurement protocol in phase plan only, NOT in permanent docs yet; update changelog

---

## Files to Read Before Implementation

1. **This report** — you are here
2. **Phase-specific plan files** — `phase-0X-*.md` for context and acceptance criteria
3. **`LLM.md` sections 2, 3, 8, 11, 12, 13** — the architecture contract
4. **`docs/routing-and-map-attribution.md`** — legal context for tiers
5. **`docs/android-mvi-best-practices.md` section 8** — where the new §8.1 goes
6. **`.claude/CLAUDE.md`** — note the section number defect before applying fixes

---

**Report prepared by:** docs-manager (scope: plan analysis only)  
**Date:** 2026-08-25  
**Actual file edits:** To be applied by implementer in each phase commit per checklist above
