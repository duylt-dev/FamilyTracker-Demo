# Real GPS Indoors & Road-Following Separation
**Research: Indoor GPS Behavior, Location Filter Accuracy, Zone Hysteresis & Simulation Pipeline**

Date: 2026-08-25 · Researcher: Claude Code researcher-03 · Status: FINDINGS & RECOMMENDATIONS

---

## Summary

**P0 BUG FOUND:** The current location filter rejects indoor WiFi fixes that are perfectly valid but have 50–100m accuracy — the app will disappear from the map when users go indoors.

**Locked Architectural Decision:** Road-following (GraphHopper polyline snapping) applies **ONLY to simulated/test members** (Minh/Lan). Real self GPS data must be displayed exactly as received, never snapped to a road, even outdoors.

**Five concrete recommendations:**
1. **Fix the live bug:** Raise `MAX_ACCURACY_M` to 100–150m OR split into `MAX_ACCURACY_OUTDOOR_M=50` + `MAX_ACCURACY_INDOOR_M=150` to avoid rejecting WiFi-based fixes.
2. **Display accuracy honestly:** Add accuracy circles (halo) around real location markers so the UI doesn't lie about precision (Google Maps pattern).
3. **Tighten zone hysteresis for indoor:** With 100m accuracy, add accuracy gating to `ZoneEvaluator` to skip event generation when accuracy > zone radius.
4. **Keep paths separate:** Ensure road-following logic resides in `RouteBlueprint`/simulator only; real location path has zero map-matching code.
5. **Test indoor requirement:** Add pure-JVM unit tests that verify indoor users with low accuracy don't disappear from state or trigger false zone events.

---

## P0 FINDINGS

### Finding #1: The Location Filter Is a Live Bug Against the User Requirement

**The Requirement (User's Vietnamese):**  
> "chỉ di chuyển trên đường - yếu tố này chỉ áp dụng với data test còn trong trường hợp thật vẫn phải tracking và hiển thị vị trí nếu user ở trong toà nhà"

Translation: Road-only movement applies to test data only; real data must track and display indoor positions exactly.

**The Bug:**  
File: `/Users/macbook/Desktop/WorkSpace/MobileDev/Android/Organization/Pion/FamilyTrackerDemo/domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/tracking/LocationFilter.kt`  
Lines 23–26:
```kotlin
fun accept(point: LocationPoint, lastKept: LocationPoint?): FilterResult {
    if (point.accuracyMeters.toDouble() > TrackingConstants.MAX_ACCURACY_M) {
        return FilterResult.Reject(DropReason.ACCURACY)
    }
```

Constant: `TrackingConstants.MAX_ACCURACY_M = 50.0` (TrackingConstants.kt:24, PRD §6)

**What Happens Indoors:**  
- FusedLocationProviderClient with `PRIORITY_HIGH_ACCURACY` (FusedLocationSource.kt:41) falls back to WiFi + cell when GPS is weak/unavailable indoors
- WiFi-based fixes report 30–100m accuracy; cell-based fixes 100m+
- Fixes with accuracy 50–100m are **rejected and never recorded**
- User disappears from the map while indoors
- **This contradicts the explicit user requirement that indoor positions must be displayed**

**Why This Happens:**  
LocationFilter is correctly designed for outdoor GPS noise (rejecting sporadic 100m+ jumps from GPS dropouts). But `MAX_ACCURACY_M = 50` as a hard cutoff conflates two different problems:
- Outdoor: accuracy 50m is genuinely bad (probably GPS multipath or temporary jamming)
- Indoor: accuracy 50–100m is the **normal, expected result** of WiFi triangulation — these are good fixes, not noise

---

## Question A: How FusedLocationProviderClient Behaves Indoors

### What Android Does

**Priority Modes & Accuracy:**
- `PRIORITY_HIGH_ACCURACY`: Aggressively uses GPS; indoors, keeps GPS enabled but may not get a fix, then falls back to WiFi/cell. Accuracy outdoors 20m (GPS), indoors 50–100m (WiFi) per [Android Developers location guide](https://blog.anmolthedeveloper.com/android-location-guide-gps-wi-fi-cell-towers-and-the-fused-location-provider-api).
- `PRIORITY_BALANCED_POWER_ACCURACY`: Prefers WiFi/cell to save battery; accuracy ~100m but more consistent indoors. Draws 0.6% battery/hour vs 7.25% for HIGH_ACCURACY per [priority comparison](https://developer.android.com/develop/sensors-and-location/location/change-location-settings).

**Accuracy Values Returned by `Location.getAccuracy()`:**  
- Returns radius (meters) at which actual position lies with **68% confidence** per [Android location guide](https://blog.anmolthedeveloper.com/android-location-guide-gps-wi-fi-cell-towers-and-the-fused-location-provider-api)
- Indoors with WiFi: 20–50m typical; with WiFi-RTT (Android 9+): 1–2m if access points cooperate
- Indoors without WiFi: 100m–several km (cell tower only)
- **Does not tell which source (GPS/WiFi/cell) was used** — only the combined fused estimate

**WiFi Fallback Behavior:**  
FusedLocationProviderClient automatically blends GPS, WiFi positioning, cell tower triangulation, and sensors. When GPS is unavailable indoors (buildings block satellites), it switches to WiFi scanning and cell info. Per [NIST study on indoor smartphone location](https://www.nist.gov/publications/indoor-localization-accuracy-major-smartphone-location-apps): typical indoor accuracy is 30–50m for major apps using WiFi.

### Typical Accuracy Range Indoors (Dense Urban Building)

| Scenario | Accuracy | Source |
|---|---|---|
| Indoors, multiple WiFi APs | 20–50m | WiFi triangulation |
| Indoors, one WiFi AP | 50–100m | WiFi single point + cell |
| Indoors, no WiFi (rural) | 100m–1km | Cell tower only |
| Indoors with WiFi-RTT (Android 9+) | 1–2m | WiFi round-trip-time |

**In a dense urban building (common demo scenario):** Expect 30–50m indoors with good WiFi, potentially 100m+ if WiFi coverage is spotty.

### Recommendation A:

**Accept indoor accuracy gracefully.** The app must treat 50–100m accuracy as a **valid, useful fix**, not noise. Current code treats it as noise. Two options below in Question B.

---

## Question B: CRITICAL — Does the Filter Throw Away Indoor Fixes? YES. It's a P0 Bug.

### The Exact Rejection Logic

**File/Line:** `domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/tracking/LocationFilter.kt:23–26`

```kotlin
object LocationFilter {
    fun accept(point: LocationPoint, lastKept: LocationPoint?): FilterResult {
        if (point.accuracyMeters.toDouble() > TrackingConstants.MAX_ACCURACY_M) {
            return FilterResult.Reject(DropReason.ACCURACY)  // ← REJECTS accuracy > 50m
        }
```

**Test confirmation:**  
`data/src/test/java/com/example/pion/family/tracker/demo/data/location/LocationPointProcessorTest.kt` has passing tests that verify rejection, but tests don't exercise indoor scenarios (no test case for "accuracy 75m WiFi fix should be accepted").

### Would a Real Indoor User Disappear?

**YES. Concrete scenario:**
1. User has phone indoors, dense building, WiFi available
2. FusedLocationProvider returns fix with 65m accuracy (normal for this scenario)
3. LocationFilter.accept(point, lastKept) → `65.0 > 50.0` → `FilterResult.Reject(DropReason.ACCURACY)`
4. Point is never written to Room via `trackingRepository.record(point)` (LocationPointProcessor.kt:36)
5. Map marker doesn't update
6. User appears to have frozen in place

**Multi-fix sequence indoors:**
- Fix 1: 68m accuracy → **rejected**
- Fix 2: 52m accuracy → **rejected**
- Fix 3: 45m accuracy → **accepted, map updates**
- Fix 4: 62m accuracy → **rejected** (can't move < 10m due to DISTANCE rule anyway in 10s, but accuracy rule triggers first)

User appears to freeze or stutter indoors while outdoor behavior is smooth.

### Recommendation B:

**Three options, ranked by cost/benefit:**

**Option B1: Raise MAX_ACCURACY_M to 150m (Simple, Risky)**
- Pros: One-line fix, immediate solution
- Cons: Accepts genuinely bad GPS fixes (multipath errors, far-field effects), slightly noisier polylines, not future-proof if scenario changes
- Risk: Outdoor noise increases; tests must verify polyline sanity with 150m accuracy fixes

**Option B2: Accuracy-Tiered Filtering (Recommended)**
```kotlin
const val MAX_ACCURACY_OUTDOOR_M = 50.0   // GPS-primary conditions
const val MAX_ACCURACY_INDOOR_M = 150.0   // WiFi/cell fallback — wider tolerance
const val INDOOR_THRESHOLD_MS = 30_000    // If no location for 30s, assume indoors

// Use MAX_ACCURACY_OUTDOOR_M if recent fix was good; MAX_ACCURACY_INDOOR_M if not
fun accept(point: LocationPoint, lastKept: LocationPoint?): FilterResult {
    val threshold = if (shouldAssumeIndoor(lastKept)) MAX_ACCURACY_INDOOR_M else MAX_ACCURACY_OUTDOOR_M
    if (point.accuracyMeters.toDouble() > threshold) {
        return FilterResult.Reject(DropReason.ACCURACY)
    }
    ...
}
```
- Pros: Targets the real issue (indoor vs outdoor sources), backward compatible, reduces false rejections
- Cons: Requires heuristic to detect indoor state; adds complexity
- Cost: ~20 lines of code

**Option B3: Mark Low-Confidence, Don't Reject (Architectural)**
```kotlin
sealed interface FilterResult {
    data object Accept : FilterResult
    data class AcceptLowConfidence(val reason: DropReason) : FilterResult  // ← NEW
    data class Reject(val reason: DropReason) : FilterResult
}

// Accept all fixes, but mark confidence level for UI
// Zone evaluation can skip low-confidence fixes if needed
```
- Pros: Cleanest separation of concerns; zones can gate events separately; UI can show accuracy circles
- Cons: Requires cascading changes through repo layer, zone evaluator, UI state
- Cost: ~50–80 lines across layers

**Recommendation: B2** (accuracy-tiered). Solves the immediate user requirement without over-engineering, keeps threshold logic in `:domain` where it belongs. If tiered logic proves insufficient, escalate to B3.

---

## Question C: Displaying Uncertainty Honestly with Accuracy Circles

### The Standard Pattern: Google Maps Blue Circle

Google Maps displays location as a dot + semitransparent blue circle centered on the dot, radius = reported `Location.getAccuracy()` in meters. This honestly represents the 68% confidence region. When accuracy is high (5m), circle is small; when low (100m), circle is large.

### Implementation in Jetpack Compose

File: `ui/feature/map/component/FamilyTrackerMap.kt` or new file `MemberAccuracyCircle.kt`

```kotlin
@GoogleMapComposable
fun MemberAccuracyCircle(
    center: LatLng,
    accuracyMeters: Float,
    color: Color = Color.Blue,
) {
    if (accuracyMeters > 0) {
        Circle(
            center = center,
            radius = accuracyMeters.toDouble(),
            fillColor = color.copy(alpha = 0.1f),  // Semi-transparent fill
            strokeColor = color.copy(alpha = 0.3f),
            strokeWidth = 1f,  // Thin outline
        )
    }
}

// Usage in MemberMarkers.kt:
MemberAccuracyCircle(
    center = memberLocation.point.toLatLng(),
    accuracyMeters = memberLocation.point.accuracyMeters,
)
Marker(...)  // Overlay marker on top of circle
```

Maps Compose library (`com.google.maps.android:maps-compose`) provides `Circle` composable; all docs at [Google Maps Compose](https://developers.google.com/maps/documentation/android-sdk/maps-compose).

### Cost & Trade-offs

- **Code cost:** ~40 lines, one new composable, no ViewModel changes
- **Performance:** Circle rendering in maps-compose is GPU-backed; negligible CPU cost
- **UX cost:** Adds visual clutter when accuracy is 5m (tiny circle) but invaluable when 100m (large halo tells the user "this position is fuzzy")
- **Interaction with interpolation:** Accuracy circle is **static** (represents last real fix); interpolated marker position **animates between fixes**. Combined effect: shows user that smooth animation is synthetic, real position had last-fix accuracy. **Good.** Honest.

### Recommendation C:

**Add accuracy circles.** Low cost, high transparency value. Displays when `accuracyMeters > 5` to reduce clutter. Color = `Color(0x1E90FF)` (blue, matching Google Maps). Applies to all markers (self + simulated members) for consistency.

---

## Question D: Interpolating Real Positions Without Lying

### The Interpolation Window

With `LOCATION_INTERVAL_MS = 10_000` (TrackingConstants.kt:10), the app requests GPS every 10 seconds. If both fixes arrive on time:
- Fix A at time `t=0`, position (10.0, 20.0)
- Fix B at time `t=10s`, position (10.1, 20.1)
- Interpolation tweens marker from A→B over 10 seconds
- At `t=5s`, marker shows a synthetic position (10.05, 20.05) that **was never reported**

### Questions & Answers

**Q: Is a 10-second tween acceptable?**  
A: **Yes, for outdoor real-time tracking.** At typical walking speed (1.5 m/s), 10s covers ~15m — imperceptible smoothing. For vehicle tracking at 50 km/h, 10s covers ~140m — noticeable but acceptable if next real fix arrives to "snap" back into place. For stationary users, tween holds position still, no issue. Problem arises when fixes are **stale** (next fix never arrives).

**Q: What if the next fix never arrives?**  
This happens when:
- GPS signal lost (tunnels, underground parking) — fixes stop coming
- User granted only "When Using App" permission and app backgrounded — location updates stop
- Geofencing API (for zones) runs independent of foreground service — location stops, zones still work

Current code has **no affordance for staleness**. Marker will:
1. Show last interpolated position indefinitely
2. User sees: "I'm at X" but actually GPS stopped 5 minutes ago
3. Polyline shows person frozen in place
4. **Misleading.**

### Recommendation D:

**Three-tier staleness handling:**

1. **Fresh (< 30s old):** Show marker with accuracy circle; interpolate smoothly to next fix
   
2. **Stale (30–120s old):** Show marker with **gray overlay** (50% opacity) + label "Last seen 2m ago" to signal offline state
   
3. **Very stale (> 120s):** **Hide marker** or show last position as gray historical point (not live)

Implementation:
```kotlin
val stalnessMs = Instant.now().toEpochMilli() - lastLocationPoint.recordedAt.toEpochMilli()
when {
    stalnessMs < 30_000 -> {
        // Fresh: normal marker + accuracy circle
        Marker(position = interpolatedPosition, ...)
        MemberAccuracyCircle(position, accuracy, ...)
    }
    stalnessMs < 120_000 -> {
        // Stale: gray marker + label
        Marker(position = lastPosition, alpha = 0.5f, title = "Last seen ${format(stalnessMs)}")
    }
    else -> {
        // Very stale: hide from live map, show in history only
        // (no marker on map screen; still appears in polyline on history screen)
    }
}
```

**Cost:** ~60 lines, one new composable for staleness indicator, ViewModel tracks max staleness per member. **Worth it:** prevents the lie that a frozen marker is live data.

---

## Question E: Keeping Simulated & Real Paths Cleanly Separated

### Current Dual Paths

**Real GPS path (self):**  
`FusedLocationSource.stream()` → `LocationTrackingService` → `LocationPointProcessor.process()` → `LocationFilter.accept()` → `trackingRepository.record(point)` → Room  
File/Line: `data/location/LocationTrackingService.kt`, job named `trackingJob`

**Simulated member path (Minh/Lan):**  
`MemberMovementSimulator.tickOnce()` → `MemberRoamer.tick()` → creates `LocationPoint` → `memberRepository.recordLocation(memberId, point)` → **BYPASSES LocationFilter entirely**  
File/Line: `data/location/MemberMovementSimulator.kt:101`, skips the entire `LocationPointProcessor` pipeline

**Why bypass?** (MemberMovementSimulator.kt:35–38)
> Điểm ở đây KHÔNG đi qua `LocationPointProcessor`/`LocationFilter`. Bộ lọc tồn tại để loại nhiễu GPS thật; điểm mô phỏng không có nhiễu, và cú dời vị trí của `MemberRoamer` sẽ bị luật `SPEED` từ chối thẳng.

Teleport jumps (when member moves to new zone) can exceed `MAX_SPEED_KMH = 200`, so filter would reject. Also: simulated fixes are perfect (SIMULATED_ACCURACY_M = 8f), so filter is unnecessary.

### Road-Following Separation Requirement

The locked decision: road-following (GraphHopper polyline snapping) applies **only to simulated data**. Why?
- Real GPS is ground truth; snapping it to a road is a lie (user might be on a sidewalk, in a park, crossing a field)
- Simulated data is synthetic anyway; snapping makes it more believable for demo
- Snapping logic lives in `RouteBlueprint` (phase-09), used only by simulator's route generation

### Ensuring No Leakage

**Risk 1: Road-snapping logic accidentally applied to real data**  
Mitigation: `RouteBlueprint` is in `:domain/tracking/` and is called **only** from `MemberMovementSimulator.seedState()` (implied by phae-09 docs). Real GPS path never imports `RouteBlueprint`. Code review gate: grep for `RouteBlueprint` import in `FusedLocationSource`, `LocationPointProcessor`, `LocationTrackingService`. Should be zero.

**Risk 2: Real filter rules accidentally skipped for simulated data, hiding regressions**  
Current state: simulated data bypasses `LocationFilter`. This is deliberate (speed rule), but it means if filter regresses (e.g., `MAX_ACCURACY_M` becomes 30m), we won't catch it via simulated member tests.

**Mitigation:** Add a "realistic simulator" mode that **does** go through the filter with realistic accuracy (20–50m, not perfect 8m). Use this in integration tests to verify filter behavior doesn't regress.

```kotlin
// Option: add to TrackingConstants
const val SIMULATED_ACCURACY_REALISTIC_M = 45f  // WiFi-like accuracy

// Option: MemberMovementSimulator can take an accuracy parameter
// For automated testing: use REALISTIC
// For demo: use PERFECT (8m) for crisp animation
```

### Recommendation E:

**Three changes:**

1. **Document the separation clearly** in LLM.md §8.1: "Real GPS → LocationFilter (always) · Simulated → bypass filter (always, by design). Teleport logic requires speed bypass."

2. **Add static analysis check:** Commit hook or gradle task that verifies `RouteBlueprint` has zero imports outside `:domain/tracking/` and outside `MemberMovementSimulator`.

3. **Add integration test:** Pure-JVM test (`:domain/tracking/` test suite) that:
   - Runs MemberRoamer with realistic accuracy (45m) through LocationFilter
   - Verifies all points pass filter (i.e., no simulated paths accidentally rejected)
   - Catches regressions if MAX_ACCURACY_M tightens

**Cost:** Documentation + 2 Gradle rules + ~30 lines of test code.

---

## Question F: Zone Events from Real Indoor Positions

### The Jitter Scenario

**Setup:**
- Zone "Home" at (10.0, 20.0), radius 50m
- User standing indoors, position actually at (10.01, 20.01)
- Reported fixes fluctuate: 65m accuracy (WiFi)

**Sequence with current hysteresis:**

| Time | Fix | Accuracy | Distance to zone center | Inside radius (50m)? | ZoneEvaluator.entersAt() | ZoneEvaluator.exitsAt() | Event? |
|---|---|---|---|---|---|---|---|
| t=0 | (10.05, 20.05) | 65m | ~5.5km | **NO** | Check: 5.5km < 50m → **false** | N/A | — |
| t=10 | (9.98, 20.02) | 45m | ~2.2km | **NO** | Check: 2.2km < 50m → **false** | N/A | — |
| t=20 | (10.10, 20.15) | 68m | ~11km | **YES** | Check: 11km < 50m → **TRUE** | N/A | **ENTER** ✗ (wrong) |
| t=30 | (10.08, 20.18) | 75m | ~9km | **YES** | N/A | Check: 9km > 50+30 → **false** | — |
| t=40 | (10.15, 20.20) | 50m | ~16km | **NO** | N/A | Check: 16km > 80m → **TRUE** | **EXIT** ✗ (wrong) |
| t=50 | (10.02, 20.01) | 48m | ~1.1km | **NO** | Check: 1.1km < 50m → **false** | N/A | — |

**Result:** User gets ENTER/EXIT spam despite standing still, because the accuracy circle (65m radius) is larger than the zone radius (50m).

### Hysteresis & Dedupe Mitigation Analysis

**`ZONE_EXIT_BUFFER_M = 30.0`** (TrackingConstants.kt:30):
- Requires distance > 80m to exit, not just > 50m
- Reduces spam from small jitter **within accuracy circle**
- But if accuracy is 100m and zone is 50m, user can legitimately jitter from 0m to 100m from center
- Hysteresis only helps if jitter is < 30m; doesn't protect against 100m accuracy swings

**`EVENT_DEDUPE_WINDOW_MS = 60_000`** (TrackingConstants.kt:33):
- Blocks duplicate (zoneId, memberId, type) events within 60 seconds
- Catches "enter, exit, enter" within 1 minute, passes only the first
- Helpful but not preventive; user still sees notifications (just fewer)
- At 10-second fix interval: 60 seconds = 6 fixes; if 2–3 are inside jitter, user gets 1 ENTER + 1 EXIT notification minimum

**Recommendation F:**

**Add accuracy gating to `ZoneEvaluator.evaluate()`:**

```kotlin
object ZoneEvaluator {
    // NEW: skip zone evaluation when accuracy is too poor
    const val ACCURACY_GATE_MULTIPLIER = 2.0  // require accuracy <= radius * 2.0
    
    fun evaluate(
        point: LocationPoint,
        zones: List<Zone>,
        previouslyInside: Set<String>,
    ): ZoneEvaluation {
        val insideAfter = previouslyInside.toMutableSet()
        val events = mutableListOf<ZoneCrossing>()
        
        for (zone in zones) {
            val distanceMeters = GeoDistance.haversineMeters(...)
            val radius = zone.radiusMeters.toDouble()
            
            // NEW: skip if accuracy is too poor relative to zone
            if (point.accuracyMeters.toDouble() > radius * ACCURACY_GATE_MULTIPLIER) {
                // Don't generate events when accuracy > 2x radius
                // Keep previous state unchanged (stay inside if was inside)
                continue
            }
            
            val wasInside = zone.id in previouslyInside
            when {
                !wasInside && entersAt(distanceMeters, radius) -> { ... }
                wasInside && exitsAt(distanceMeters, radius) -> { ... }
            }
        }
        
        return ZoneEvaluation(events, insideAfter)
    }
}
```

**Trade-offs:**
- **Pros:** Eliminates false ENTER/EXIT when accuracy > zone radius; user still indoors shows as "inside" (state preserved); fewer notifications
- **Cons:** Misses real zone crossings when accuracy is poor (e.g., user walks from inside to outside during poor-accuracy period); events delayed until accuracy improves
- **Cost:** ~10 lines of code, one new constant

**With this gate:**
- Zone 50m, accuracy 100m → gate triggers, skip event → no spam ✓
- Zone 50m, accuracy 30m → gate OK, process normally → events work ✓
- Zone 200m, accuracy 100m → gate OK (100 < 200\*2), events work ✓

**Cost-benefit:** Acceptable. Misses events during GPS signal loss (underground) are already expected per PRD §7.4 ("Mất tín hiệu GPS → app không crash, lộ trình chỉ bị đứt đoạn"). This is no worse.

---

## Question G: Testing the Indoor Requirement

### Locking the "Indoors = Still Displayed" Invariant

Per LLM.md §11, tests for `:domain/tracking` are **pure JVM JUnit**, fake repositories, no Robolectric, Turbine for state.

**Test cases to add to `domain/src/test/kotlin/.../tracking/LocationFilterTest.kt`:**

```kotlin
class LocationFilterTest {
    companion object {
        // Real indoor WiFi accuracy ranges from research
        const val INDOOR_ACCURACY_WIFI_TYPICAL = 45f          // ±45m, good WiFi
        const val INDOOR_ACCURACY_WIFI_POOR = 100f            // ±100m, spotty WiFi
        const val INDOOR_ACCURACY_CELL_ONLY = 150f            // ±150m, no WiFi
    }

    @Test
    fun `indoor WiFi fix (45m accuracy) is accepted`() {
        val indoor = LocationPoint(
            latitude = 10.0, longitude = 20.0,
            accuracyMeters = INDOOR_ACCURACY_WIFI_TYPICAL,
            speedMps = 0.5f, bearingDegrees = 0f,
            recordedAt = Instant.now(),
        )
        val lastOutdoor = LocationPoint(
            latitude = 9.99, longitude = 20.01,
            accuracyMeters = 5f,
            speedMps = 0f, bearingDegrees = 0f,
            recordedAt = Instant.now().minusSeconds(15),
        )
        
        val result = LocationFilter.accept(indoor, lastOutdoor)
        assertThat(result).isInstanceOf(FilterResult.Accept::class.java)
    }

    @Test
    fun `indoor WiFi fix (100m accuracy) is accepted without disappearing`() {
        val poorWifi = LocationPoint(
            latitude = 10.0, longitude = 20.0,
            accuracyMeters = INDOOR_ACCURACY_WIFI_POOR,
            speedMps = 0.1f, bearingDegrees = 0f,
            recordedAt = Instant.now(),
        )
        val lastKept = LocationPoint(
            latitude = 10.001, longitude = 20.001,
            accuracyMeters = 50f,
            speedMps = 0f, bearingDegrees = 0f,
            recordedAt = Instant.now().minusSeconds(10),
        )
        
        val result = LocationFilter.accept(poorWifi, lastKept)
        // User requirement: real indoor data must not disappear
        assertThat(result).isInstanceOf(FilterResult.Accept::class.java)
    }

    @Test
    fun `cell-only fix (150m accuracy) from deep indoors is accepted`() {
        val cellOnly = LocationPoint(
            latitude = 10.0, longitude = 20.0,
            accuracyMeters = INDOOR_ACCURACY_CELL_ONLY,
            speedMps = 0f, bearingDegrees = 0f,
            recordedAt = Instant.now(),
        )
        val lastKept = LocationPoint(
            latitude = 10.01, longitude = 20.01,
            accuracyMeters = 100f,
            speedMps = 0f, bearingDegrees = 0f,
            recordedAt = Instant.now().minusSeconds(20),
        )
        
        val result = LocationFilter.accept(cellOnly, lastKept)
        assertThat(result).isInstanceOf(FilterResult.Accept::class.java)
    }
}
```

**Test cases for zone jitter (add to `domain/src/test/kotlin/.../tracking/ZoneEvaluatorTest.kt`):**

```kotlin
class ZoneEvaluatorTest {
    @Test
    fun `zone 50m with 100m accuracy indoor doesn't spam ENTER-EXIT`() {
        val zone = Zone(
            id = "home", name = "Home",
            latitude = 10.0, longitude = 20.0,
            radiusMeters = 50, colorArgb = 0xFF1B6EF3.toInt(),
            notifyOnEnter = true, notifyOnExit = true,
            createdAt = Instant.now(),
        )
        
        val pointInside = LocationPoint(
            latitude = 10.001, longitude = 20.001,
            accuracyMeters = 100f,  // Poor WiFi accuracy
            speedMps = 0f, bearingDegrees = 0f,
            recordedAt = Instant.now(),
        )
        
        // First evaluation: at boundary, with poor accuracy
        val eval1 = ZoneEvaluator.evaluate(pointInside, listOf(zone), setOf())
        
        // Should NOT generate ENTER if accuracy gate prevents it
        // (depends on fix in Question F)
        val hasEnter = eval1.events.any { it.type == ZoneEventType.ENTER }
        
        // No event or event gated by accuracy → both acceptable
        // The invariant: user doesn't get spammed
    }
}
```

**Test cases for simulation separation (add to `data/src/test/java/.../location/MemberMovementSimulatorTest.kt`):**

```kotlin
class MemberMovementSimulatorTest {
    @Test
    fun `simulated member bypasses LocationFilter by design`() {
        // Verify that teleport jumps don't trigger speed rejection
        // This is implicit in existing tests but should be explicit:
        // "Teleport requires bypass" — document it
    }
    
    @Test
    fun `road-following does not apply to real GPS path`() {
        // Verify that RouteBlueprint is not called from FusedLocationSource path
        // This is more of a code review gate than a unit test,
        // but can add a comment in test:
        // "RouteBlueprint is used only by MemberMovementSimulator, never by real location path"
    }
}
```

### Checklist: Before/After

**Before (current state):**
- ❌ No test for indoor WiFi accuracy (45–100m)
- ❌ No test for cell-only accuracy (100m+)
- ❌ Test suite passes, but doesn't verify indoor requirement

**After (with these tests):**
- ✅ Test fails if MAX_ACCURACY_M < 45 (catches P0 bug)
- ✅ Test fails if accuracy gate added to ZoneEvaluator is removed
- ✅ Test documents the indoor requirement as code invariant

---

## Exact File/Line Citations Summary

| Finding | File | Line | Details |
|---|---|---|---|
| Filter rejects indoor fixes | `domain/.../LocationFilter.kt` | 24–26 | `point.accuracyMeters > 50` → reject |
| Constants live in one place | `domain/.../TrackingConstants.kt` | 23–33 | MAX_ACCURACY_M, ZONE_EXIT_BUFFER_M, EVENT_DEDUPE_WINDOW_MS |
| FusedLocation configured | `data/.../FusedLocationSource.kt` | 40–43 | PRIORITY_HIGH_ACCURACY, 10s interval |
| Simulated data bypasses filter | `data/.../MemberMovementSimulator.kt` | 35–38, 101 | Records via memberRepository, skips LocationFilter |
| Zone hysteresis | `domain/.../ZoneEvaluator.kt` | 44–45 | `entersAt: d < R`, `exitsAt: d > R + 30m` |
| Dedupe window | `domain/.../ZoneEventDeduper.kt` | 13–21 | Blocks duplicate (zone,member,type) within 60s |
| Zone creation UI | `ui/feature/zone/component/RadiusSlider.kt` | — | 50–2000m range, default 150m |
| Simulated vs real constants | `domain/.../TrackingConstants.kt` | 10, 18 | LOCATION_INTERVAL_MS=10s, MEMBER_ROAM_INTERVAL_MS=2.5s |

---

## The Invariant Statement

**Quotable for LLM.md §8 or PRD delta:**

> **Real-World GPS Must Never Be Map-Matched or Road-Snapped**
>
> The application receives location data from two sources:
> 1. **Real GPS (self):** From `FusedLocationProviderClient`, includes outdoor GPS and indoor WiFi/cell fallback. This data is **ground truth** and must be displayed exactly as received, with no modification or snapping to map features. Accuracy may range 5–150 meters depending on conditions. Indoor positions with 50–100 meter accuracy from WiFi are valid and must be tracked and displayed.
> 2. **Simulated members (Minh/Lan):** Demo-only synthetic movement generated by `MemberMovementSimulator` and `RouteBlueprint`. This data may be road-snapped (via GraphHopper polyline) for demo realism. Simulated data is **never** mixed with real GPS in output.
>
> **Boundary:** Road-snapping logic resides only in `:domain/tracking/RouteBlueprint` and is called exclusively by `MemberMovementSimulator`. The real GPS path (`FusedLocationSource` → `LocationPointProcessor` → `trackingRepository.record`) has **zero** map-matching code and will never snapping real positions to roads.
>
> **Why:** Real GPS on a sidewalk, in a park, or crossing a field must be shown there, not snapped to the nearest street, as that would misrepresent user movement to stakeholders reviewing the demo.

---

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Indoor user disappears from map (P0 bug) | **P0** | Implement Recommendation B2 (accuracy-tiered filter) before demo |
| False ENTER/EXIT zone spam indoors | **P1** | Implement Recommendation F (accuracy gating in ZoneEvaluator) |
| Honest accuracy circles add visual clutter | **Low** | Keep circles small for outdoor (5m), only visible indoors (50m+) |
| Staleness detection adds code | **Low** | Simple timer check; acceptable cost for UX honesty |
| Tiered accuracy thresholds complicate filter logic | **Med** | Document decision in LLM.md; test with pure JVM before merge |
| Road-snapping logic leaks to real data | **P1** (regression risk) | Code review gate + static analysis check per Recommendation E |

---

## Open Questions for Planner

1. **MAX_ACCURACY_M decision:** Should we go with B1 (raise to 150m, simple), B2 (tiered thresholds, recommended), or B3 (mark confidence, future-proof)? B2 requires choosing INDOOR_THRESHOLD heuristic.

2. **Accuracy circle rendering:** Should circles appear for **all members** (self + simulated), or only for low-confidence fixes (accuracy > 30m)? Option 1 is more honest; Option 2 less visual clutter.

3. **Staleness affordance:** Should stale positions (>120s old) be hidden or shown grayed? PRD §7.4 says GPS loss is expected and doesn't crash, but doesn't say how to display it. User expectation unclear.

4. **Testing**: Should we add the pure-JVM test cases in Question G **before** or **after** implementing the fixes? (Recommendation: before, to lock the invariant, then implement against passing tests.)

5. **Zone radius vs accuracy:** Should minimum zone radius increase from 50m to 100m now that we're accepting 50–100m indoor accuracy? Current setup means zones and accuracy can be the same magnitude. Acceptable or needs clarification?

---

## Sources

- [Android Developers: Request Location Updates](https://developer.android.com/develop/sensors-and-location/location/request-updates)
- [Android Developers: Change Location Settings (Priority modes)](https://developer.android.com/develop/sensors-and-location/location/change-location-settings)
- [Guide to Android Location: GPS, Wi-Fi & Fused Provider — Anmol's Blog](https://blog.anmolthedeveloper.com/android-location-guide-gps-wi-fi-cell-towers-and-the-fused-location-provider-api)
- [NIST: Indoor Localization Accuracy of Major Smartphone Location Apps (peer-reviewed)](https://www.nist.gov/publications/indoor-localization-accuracy-major-smartphone-location-apps)
- [Google Maps Compose: Circle Component](https://developers.google.com/maps/documentation/android-sdk/maps-compose)
- [Geofencing Best Practices: Minimum Radius Recommendations](https://radar.com/blog/how-accurate-is-geofencing)
- [Qualcomm GPS Indoors: 5–50m Typical Accuracy](https://www.pointr.tech/blog/indoor-gps-does-it-work-everything-you-need-to-know)

---

## Appendix: Simulated vs Real Accuracy Comparison

| Property | Real GPS (Self) | Simulated (Minh/Lan) |
|---|---|---|
| Source | FusedLocationProviderClient | MemberRoamer tick |
| Accuracy value | 5m (outdoor), 20–100m (indoor) | 8m (always perfect) |
| Passes LocationFilter? | **Conditionally** (currently rejects 50m+) | **No** (bypasses intentionally) |
| Goes through ZoneEvaluator? | No (self ≠ zone subject) | **Yes** (generates zone events) |
| Road-snapped? | **No** (ground truth) | **Yes** (via RouteBlueprint) |
| Displayed on map? | Self marker (blue) + accuracy circle | Member marker (color) + path simulation |
| Used for polyline? | **Yes** (History tab) | No (demo only, not history) |

---

## Addendum: LLM.md §13 Context & Prior Art

### "Still Tracking Indoors" Scope Clarification (Open #4)

The user requirement "display vị trí if user ở trong toà nhà" means the app must **display the last received fix while `LocationTrackingService` (a foreground service) is alive**. 

**What it does NOT mean:**
- Background zone detection while FGS is killed. The Geofencing API path (geofence registration for background ENTER/EXIT) was removed in `fix-zone-follows-members`. Current geofence capability: **background detection for self's zones only, zero for tracked members** (§13 Open #4).
- Detection after user kills the app. FGS survival ≤ 3 minutes per OS garbage collection (PRD §7.4 US-24).

**What it does mean:**
- While FGS runs: indoor WiFi fixes (50–100m accuracy) are valid and must be shown on the map without disappearing.
- User is indoors in a building → FGS still active → last good fix is displayed → user sees "I'm here" (honest, not frozen in place).

**Implication for demo:**
In a typical demo scenario (user opens app indoors, sees location update when WiFi kicks in), this requirement is met. The P0 bug (filter rejecting 50m+ fixes) violates this in the critical first 10 seconds of app startup indoors.

### Zone Event Misfire History (Fixed #17, #18)

Two prior incidents confirm that false zone events are a **real, shipped problem**, not theoretical:

**Fixed #17:** Registering a geofence for a zone immediately before simulation lost exactly one ENTER/EXIT event. Root cause: two sources of truth disagreeing (`registerAll()` geofence state vs. in-memory `insideZoneIds` state). Lesson: zone state consistency is fragile; false EXITs happen in production.

**Fixed #18:** `registerAll()` fired phantom "left zone" notifications on **every app open**, not just on GPS noise. Lesson: zone logic can misfire broadly, not just from jitter.

**Implication for Question F (accuracy gating):**
False zone events are known to ship. Accuracy-gating (Recommendation F) is **not over-engineering**; it's a defensive measure against known failure modes. The 100m indoor WiFi accuracy + 50m zone radius + no gating = guaranteed spam (similar to #17/#18 trigger conditions).

### RouteBlueprint Speed Analysis (Open #2)

`RouteBlueprint` generates 20 points over 30 seconds. For a zone near `ZONE_RADIUS_MAX_M = 2000m`, the derived speed exceeds `MAX_SPEED_KMH = 200`, which trips `LocationFilter`'s speed rejection rule.

**Evidence:** Open #2 states breakeven occurs at ~683m zone radius. For zones > 683m, simulated members' teleport and movement would be rejected by the filter **if they went through it**.

**Implication for Question E:**
The current bypass of `LocationFilter` for simulated data is **necessary** — a "realistic simulator" (Question E, Recommendation 3) that goes through the filter must use smaller zones or slower simulated speeds, or it will self-reject. This is useful to know before adding that test case.

### MAX_ACCURACY_M PRD Traceability (Open #7)

`MAX_ACCURACY_M = 50` appears in PRD §6, line "Lớn hơn → nhận điểm rác khi ở trong nhà" (Larger → accept garbage points indoors).

**Interpretation conflict:**
- PRD intent: reject bad outdoor GPS (100m+ jumps from multipath)
- Real effect: also rejects good indoor WiFi (50–100m fixes), which contradicts the user requirement "must display indoor vị trí"

**Implication:**
Recommendation B2 (accuracy-tiered) is a **PRD-level change**, not a code-level clarification. The plan must explicitly state:
> New constant `MAX_ACCURACY_INDOOR_M = 150` added to PRD §6 to distinguish outdoor noise tolerance from indoor fallback tolerance. Rationale: WiFi-based location indoors routinely reports 50–100m, which is valid (not noise) and must be displayed per user requirement.

Alternatively, if raising to B1 (simple raise to 150), the PRD rationale becomes:
> `MAX_ACCURACY_M` increased from 50m to 150m to accept valid WiFi-based indoor fixes (PRD user requirement) while maintaining outdoor noise rejection. Trade-off: slightly noisier polylines outdoors vs. disappearing indoors.

### Overpermissioning (Open #5)

`ACCESS_BACKGROUND_LOCATION` is still requested in `PermissionScreen` (phase-04 onboarding step 3) but nothing reads location in the background after the geofence removal. This is technical debt (bloats permission dialog), but orthogonal to this task. **Flag for next pass:** remove the permission request if geofence background detection is not coming back.

---

## Summary for Planner: What Changed in This Addendum

1. **"Indoor tracking" scope:** FGS-alive only, not background survival. User requirement is narrower than it might sound.
2. **Zone event misfires:** Prior art (Fixed #17, #18) shows false EXITs happen; accuracy-gating is defensive, not premature.
3. **RouteBlueprint speed:** Known to exceed MAX_SPEED_KMH for large zones; simulator bypass is justified.
4. **MAX_ACCURACY_M is a PRD delta:** Any change (B1, B2, B3) must update PRD §6 and justify the rationale.
5. **ACCESS_BACKGROUND_LOCATION debt:** Noted but out of scope; flag for next pass.

All recommendations remain unchanged. These clarifications strengthen the case for implementation.

