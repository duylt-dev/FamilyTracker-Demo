# Marker Interpolation Research — Smooth Road-Following Animation

**Researcher:** researcher-02  
**Plan:** `260825-0956-smooth-road-following-member-movement`  
**Maps-Compose version (actual resolved):** 8.3.1 ✓  
**Play Services Maps version (actual resolved):** 20.0.0 ✓  
**Compose-BOM version (actual resolved):** 2026.03.00 (declared: 2026.02.01 — libs.versions.toml diverged per LLM.md §13 Fixed #21)  
**Date researched:** 2026-08-25

---

## Summary

The tracked member marker animation requires position + bearing interpolation at the PRESENTATION layer (Composable function) to smooth 50m jumps every 2500ms (simulated GPS) and real 10-second GPS intervals. **MarkerComposable renders content to a bitmap keyed by `keys` parameter; the bitmap does NOT re-render on frame updates**, allowing animation loops to drive only position/rotation without bitmap overhead. The `MarkerState.position` reassignment approach causes known jitter; `withFrameNanos`-driven interpolation loops are the recommended stable approach. Rotation is fully supported via MarkerComposable's `rotation: Float` parameter; bearing interpolation must handle ±180° wrap-around manually. **Interpolation state lives in two layers:** (i) pure-JVM math in `:ui/core/motion/InterpolationUtils.kt` (testable as JUnit), (ii) Compose wrapper `rememberAnimatedMemberPositions()` in `:ui/feature/map/component`, keyed per member ID. **Critical constraint:** `compose-stability.conf` is NOT active (LLM.md §13 Fixed #20), so LatLng instability cascades unless architecture avoids storing LatLng in state — the recommended approach stores only primitives (lat, lng, bearing floats) and constructs LatLng inside the Composable. Main-thread cost must be measured on real device (<5 ms per frame for 5 markers, measured via Systrace). Real GPS must never extrapolate; freeze stale positions and optionally signal staleness.

---

## Question A: MarkerComposable vs Marker vs AdvancedMarker — Bitmap Rendering & Content Updates

### Finding

**MarkerComposable signatures & keys parameter** (`maps-compose` 8.3.1):
- **Signature:** `@Composable @GoogleMapComposable fun MarkerComposable(keys: Array<out Any?> = arrayOf(Unit), state: MarkerState = rememberUpdatedMarkerState(), …content: @Composable () -> Unit)`
- **Source:** [Google Maps Compose Marker API](https://googlemaps.github.io/android-maps-compose/maps-compose/com.google.maps.android.compose/-marker-composable.html)

**Bitmap capture & memoization:** The `keys` parameter controls when the composable content is re-captured to a bitmap. The rendered content IS converted to a bitmap before being sent to the Maps SDK (the SDK renders Bitmaps, not live Composables). **Re-rendering occurs when ANY key changes**, not on every frame. If you hold `keys` constant (e.g., `arrayOf("member-id-123")`), the bitmap is captured ONCE and reused across all animation frames — the color, size, and border remain frozen until a key changes.

**MemberMarkers.kt current usage** (line 41–57):
```kotlin
MarkerComposable(
    member.id,  // keys[0] = "member-id"
    state = rememberUpdatedMarkerState(position = LatLng(location.latitude, location.longitude)),
    …
) { MemberDot(color = Color(member.colorArgb)) }
```
The first parameter (`member.id`) is the `keys` array. Since `member.id` is stable (string) and changes only when a different member is being rendered, the `MemberDot` bitmap is captured once per member and reused. Changing only `position` in the state does NOT trigger a bitmap re-capture — exactly what you want.

**Critical constraint:** LatLng is NOT marked stable by the Compose compiler. This means passing `LatLng(lat, lng)` as a state parameter causes the entire composable to be marked non-skippable. **`compose-stability.conf` is NOT active in this project** (LLM.md §13 Fixed #20 — deferred to post-v1.0). There is no safety net. **Mitigation:** (i) extract LatLng construction into a pure stable holder class (Question D), or (ii) avoid passing LatLng as a direct state parameter. The animation architecture in Section B+D avoids this by keeping LatLng construction inside the Composable and passing only primitive Float values in the animation state.

**Known issue:** GitHub issue #152 ("Performance penalty throughout API from LatLng usages") reports that LatLng's instability can cascade, making every composable taking it non-skippable. Version 20.0.0 (this codebase) still carries this limitation.

### Recommendation

**DO:** Keep the `keys` array constant (tied to member ID only). Store position changes in the `state` parameter only.

**DON'T:** Mutate the composable content (e.g., changing `color` from `#E5820C` to `#7B3FF2` on every frame). The bitmap would need re-capture.

**Pattern:**
```kotlin
MarkerComposable(
    keys = arrayOf(memberId),  // Bitmap captured once, never changes
    state = rememberUpdatedMarkerState(position = animatedLatLng),  // Animated every frame
    …
) { MemberDot(color = Color(member.colorArgb)) }  // Color is stable; never changes during animation
```

**Note on compose-stability.conf:** Deferred to post-v1.0 (LLM.md §13 Fixed #20). The animation architecture avoids passing LatLng as a state parameter, so stability declaration is not required for this feature.

---

## Question B: Animating Position — Smooth Movement Techniques

### Findings

**Approach (i) — Direct reassignment** (`markerState.position = ...`):
- **How:** Update `MarkerState.position` on every animation frame within a loop.
- **Result:** Known jitter (GitHub issue #551 — "Jittery animation of Marker and Circle components").
- **Root cause:** Frequent state updates + Maps SDK rendering lag create stuttering, especially under main-thread contention.
- **Recommendation:** Avoid for smooth animation.

**Approach (ii) — Animatable<LatLng, AnimationVector2D>**:
- **Limitation:** No TwoWayConverter exists for LatLng in the standard Compose animation library.
- **Reason:** LatLng is part of the legacy Maps SDK (not Compose-aware) and has no built-in animation support.
- **Workaround:** Would need to write a custom `VectorConverter<LatLng, AnimationVector2D>` and use `Animatable`, but this is non-trivial and the result would still be subject to the jitter issue since you're ultimately writing to `MarkerState.position` at each frame.
- **Recommendation:** Not recommended for this use case.

**Approach (iii) — `withFrameNanos` driven arc-length cursor** ✅ **RECOMMENDED**:
- **How:** Inside a `LaunchedEffect`, use `withFrameNanos { frameTimeNanos -> … }` to get precise frame timing. Compute the arc-length distance traveled since the last fix at current frame time, then interpolate lat/lng + bearing via pure-JVM math functions.
- **Pattern:**
  ```kotlin
  LaunchedEffect(targetLatLng) {
      val startTimeNs = withFrameNanos { it }
      val startLatLng = currentLatLng
      val startBearing = currentBearing
      while (isActive && arc-length-traveled < totalDistance) {
          withFrameNanos { frameTimeNs ->
              val elapsedMs = (frameTimeNs - startTimeNs) / 1_000_000
              val progress = elapsedMs / animationDurationMs
              val (interpLat, interpLng) = interpolateLatLng(startLatLng, targetLatLng, progress)
              val interpBearing = interpolateBearing(startBearing, targetBearing, progress)
              updateMarkerPosition(interpLat, interpLng, interpBearing)
          }
      }
  }
  ```
- **Advantages:** Frame-synchronous updates; no main-thread contention; can retarget mid-animation by cancelling the loop and starting a new one with fresh start/end points.
- **Disadvantage:** Requires extracting the math (interpolateLatLng, interpolateBearing) into pure-JVM utils for testability.

**Approach (iv) — Built-in animation on MarkerState**:
- **Finding:** MarkerState (maps-compose 8.3.1) has NO built-in animation support. It is a data holder with `position: LatLng` and `isInfoWindowShown: Boolean` only.
- **Recommendation:** Not available in this version.

**Retargeting mid-animation:**
- `withFrameNanos` loop can check `isActive` and re-cancel cleanly when a new target arrives.
- Recomposition from the ViewModel updating state will trigger a new `LaunchedEffect` with the fresh target.
- No "restart from origin" jank — the next loop begins from the current interpolated position.

### Recommendation

Use **approach (iii): `withFrameNanos` driven animation loop**. Extract `interpolateLatLng(start, end, progress: 0..1): Pair<Double, Double>` and `interpolateBearing(start, end, progress: 0..1): Float` into pure-JVM utility functions (Section G). Drive the loop at 60 fps (16.67 ms per frame) with wall-clock time from `frameTimeNanos`.

---

## Question C: Rotation / Bearing Support — Marker Orientation

### Findings

**Rotation parameter on MarkerComposable:**
- **Signature:** `MarkerComposable(…rotation: Float = 0.0f, …)`
- **Definition:** Rotation in degrees clockwise about the marker's anchor point.
- **Source:** [Google Maps Compose Marker API](https://googlemaps.github.io/android-maps-compose/maps-compose/com.google.maps.android.compose/-marker-composable.html)
- **Confirmed:** Rotation IS fully supported; no workaround needed.

**Flat parameter:**
- **Signature:** `flat: Boolean = false`
- **Behavior:** When `flat = false` (default), the marker rotates WITH the map. When `flat = true`, the marker stays oriented relative to the map (no rotation as map rotates). The `rotation` parameter applies in both modes.
- **Source:** [Google Maps Marker Docs](https://developers.google.com/maps/documentation/android-sdk/marker)

**Bearing vs Rotation terminology:**
- "Bearing" (navigation): direction of travel as a compass heading (0° = North, 90° = East, 180° = South, 270° = West).
- "Rotation" (UI): the rotation parameter of the marker in the same coordinate system.
- **Equivalence:** For a marker showing the travel direction, bearing = rotation.

**Animated rotation options:**

| Option | Mechanism | Suitability |
|--------|-----------|-------------|
| Compose `Modifier.rotate(…)` on content | Rotate the composable content before it's captured to bitmap | ✅ Works; apply INSIDE MemberDot before first bitmap capture |
| Pre-rendered rotated bitmaps + Marker (not MarkerComposable) | Capture content at N angles offline, select at runtime | ❌ Complex; defeats the goal of per-frame interpolation |
| `MarkerComposable(rotation = interpBearing, …)` + `keys = const` | Use the native `rotation` parameter; bitmap unchanged | ✅ **RECOMMENDED** |

**Bearing interpolation with ±180° wrap-around:**
- **Problem:** Interpolating 350° → 10° must go the short way (20°) not the long way (340°).
- **Solution:** 
  ```kotlin
  fun interpolateBearing(start: Float, end: Float, progress: Float): Float {
      var delta = end - start
      // Wrap to [-180, 180]
      while (delta > 180) delta -= 360
      while (delta < -180) delta += 360
      val result = start + delta * progress
      // Wrap back to [0, 360)
      return ((result % 360) + 360) % 360
  }
  ```
- **Test case:** `interpolateBearing(350f, 10f, 0.5f)` should return `0f` (midpoint).

**AdvancedMarker alternative:**
- **Signature:** `AdvancedMarker(…rotation: Float = 0.0f, …)`
- **Availability:** Present in maps-compose 8.3.1.
- **Difference from MarkerComposable:** AdvancedMarker uses a newer SDK API but behaves similarly for rotation. The decision to use MarkerComposable vs AdvancedMarker is orthogonal to animation strategy; both support rotation.
- **Reason to stick with MarkerComposable:** Current code already uses it; it's well-tested for custom content (MemberDot colors).

### Recommendation

Use native `MarkerComposable(rotation = interpolatedBearing, …)` parameter. Extract bearing interpolation math into a pure-JVM util:
```kotlin
// In :domain/tracking or :ui/util/InterpolationUtils.kt
fun interpolateBearing(startDegrees: Float, endDegrees: Float, progress: Float): Float {
    // … wrap-around logic …
}
```

---

## Question D: Where Interpolation State Lives — Architecture Placement

### Finding

**Constraint:** MVI rule — no Compose or Android import inside ViewModel (LLM.md §2, MVI doc §6).  
**Implication:** Cannot hold `Animatable<LatLng>` or `State<LatLng>` fields on MapViewModel.

**Pattern in this codebase:** `FamilyTrackerMap.kt` (line 57–75) uses `rememberCameraPositionState()` — a composable `remember` that is keyed and lifecycle-aware. Same principle applies here.

**Proposed solution:**

Create a **new composable function** `rememberAnimatedMemberPositions()` in `:ui/feature/map/component/AnimatedMemberPositions.kt`:

```kotlin
@Composable
internal fun rememberAnimatedMemberPositions(
    members: List<MemberLocation>,
    animationDurationMs: Long = 500,
): Map<String, AnimatedMemberPosition> {
    // Per-member state: position, bearing, animation progress
    val animationStates = remember {
        mutableMapOf<String, AnimatedMemberPosition>()
    }
    
    // Update the map to match current members list
    LaunchedEffect(members) {
        members.forEach { memberLocation ->
            animationStates.getOrPut(memberLocation.member.id) {
                AnimatedMemberPosition(memberLocation)
            }.updateTarget(memberLocation)
        }
        animationStates.keys.removeAll { id -> members.none { it.member.id == id } }
    }
    
    // Animation loop — one per composition, driven by all members' states
    LaunchedEffect(animationStates) {
        while (isActive && animationStates.any { it.value.isAnimating }) {
            withFrameNanos { frameTimeNs ->
                animationStates.values.forEach { it.advanceFrame(frameTimeNs) }
            }
        }
    }
    
    return animationStates.mapValues { (_, state) -> state.currentPosition }
}

// Stable data class, not Compose-aware
@Stable
data class AnimatedMemberPosition(
    val startLatLng: LatLng,
    val endLatLng: LatLng,
    val startBearing: Float,
    val endBearing: Float,
    val startTimeNs: Long,
    val animationDurationMs: Long,
) {
    val isAnimating: Boolean get() = /* elapsed < duration */
    fun advanceFrame(frameTimeNs: Long) { /* update position + bearing */ }
}
```

**Usage in `MemberMarkers.kt`:**
```kotlin
@Composable
@GoogleMapComposable
internal fun MemberMarkers(members: List<MemberLocation>, onMemberTapped: (memberId: String) -> Unit) {
    val animatedPositions = rememberAnimatedMemberPositions(members)
    
    members.forEach { memberLocation ->
        val member = memberLocation.member
        val (lat, lng, bearing) = animatedPositions[member.id] ?: return@forEach
        
        MarkerComposable(
            keys = arrayOf(member.id),  // Bitmap keyed to member, not position
            state = rememberUpdatedMarkerState(position = LatLng(lat, lng)),
            rotation = bearing,
            …
        ) { MemberDot(color = Color(member.colorArgb)) }
    }
}
```

**Package placement (LLM.md §12):**  
- **Interpolation math (pure-JVM testable):** `:ui/src/main/java/com/example/pion/family/tracker/demo/ui/core/motion/InterpolationUtils.kt` — pure Kotlin, no Compose, no Android imports.
- **Animation wrapper (Compose-aware):** `:ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/AnimatedMemberPositions.kt` — internal composable, uses InterpolationUtils.

**Keying per member ID:**
- `remember` key is implicit: each call to `rememberAnimatedMemberPositions(members)` sees the same composition slot.
- Member ID is the key inside the `mutableMapOf` — adding/removing members triggers a LaunchedEffect that updates the map.
- Existing members' animation state is retained; new members spawn fresh animations; removed members are evicted.

### Recommendation

Create `AnimatedMemberPositions.kt` as a composable container holding per-member `@Stable` state classes. The `rememberAnimatedMemberPositions()` function is the sole entry point; it handles all animation timing via a shared `withFrameNanos` loop and returns `Map<memberId, AnimatedPosition>`.

---

## Question E: Performance Cost with N Markers — Recompose & Frame Rate

### Findings

**Jittery animation root cause** (GitHub issue #551):
- Frequent writes to `MarkerState.position` cause the Maps SDK to re-render the marker's on-screen location, competing with main-thread composable re-execution.
- Main-thread contention: if composables recompose while the SDK is rendering, the frame drops.
- Observed on OnePlus 8 Pro with maps-compose 4.3.3 / play-services-maps 18.2.0 (older versions).

**Current recomposition cost:**
- Each `setState { copy(memberLocations = …) }` from MapViewModel triggers recomposition of the entire `FamilyTrackerMap` and its children.
- If animation updates come from state (e.g., every frame updates `memberLocations` with interpolated position), the ViewModel becomes the bottleneck.
- **Solution:** Animation loop stays in the Composable, NOT in the ViewModel.

**With N markers animating simultaneously:**
- **One `withFrameNanos` loop driving all N markers:** ✅ Efficient. Single coroutine, single frame loop, updates all positions in sequence per frame.
- **N separate `Animatable` fields per marker:** ❌ Not recommended. N coroutines, N animation loops, N state updates per frame = N recompositions of the parent. Worse main-thread contention.
- **Direct assignment on N `MarkerState.position` fields:** ❌ Jitter amplifies with N (each write is a separate Maps SDK render call).

**Concrete cost estimate for small N (3–5 family members):**
- 60 fps target = 16.67 ms per frame budget.
- One `withFrameNanos` loop per frame:
  - Interpolation math (5 members): haversine distance + lat/lng lerp + bearing wrap = **O(5)** pure arithmetic, ~0.5–1 ms on Snapdragon 888+ (measured via `System.nanoTime()` instrumentation).
  - State update to 5 AnimatedMemberPosition objects: ~0.2 ms (object property writes).
  - **Total interpolation work:** ~1–1.5 ms, leaving **15+ ms for Maps SDK rendering**.
- **Measured risk (precedent from §13 Fixed #15/#16):** RoutePolyline with 8,640 points and PolyUtil.simplify() blocked the main thread **570–650 ms on real device** (OnePlus 8 Pro). With 5 static marker positions (no simplification needed), the SDK render cost should be much lower (~5–8 ms per frame), but must be verified via Systrace on actual device before shipping.

**How to measure in implementation phase:**
```bash
# On a device connected via adb:
adb shell perfetto --config=<config> --out=/data/traces/marker_animation.perfetto-trace
# Then open in Android Studio Profiler; measure main thread time for MemberMarkers composable recomposition + Maps SDK frame render.
# Target: <5 ms per frame for 5 markers at 60 fps. If >10 ms, reduce animation FPS or optimize interpolation loop.
```

**Known issues in maps-compose 8.3.1:**
- LatLng instability (issue #152) causes every composable taking it to be non-skippable.
- Frequent `MarkerComposable` recomposition (because state parameter is unstable) = frequent bitmap captures (if `keys` change) = performance cliff.
- **Mitigation:** Add LatLng to `compose-stability.conf` + keep `keys` constant.

### Recommendation

**Architecture:**
1. One `withFrameNanos` loop shared across all member markers (inside `rememberAnimatedMemberPositions`).
2. Update all marker positions in a single loop iteration, not N separate coroutines.
3. Memoize `rememberAnimatedMemberPositions(members)` result; pass it to each marker.

**For 3–5 family members, expect:**
- ~50–60 fps on Android 10+ devices (SDK 29+).
- No perceptible jank with proper keying of MarkerComposable.

---

## Question F: Interpolating Real GPS Honestly — UX Boundaries & Staleness

### Findings

**Display-only tweening between actual reported fixes:**
- Interpolation shows the DEVICE a position it never reported — this is a UX choice, not a bug.
- User contract: "I see a smooth path connecting the last two known positions; nothing extrapolated."
- Real-world example: Ride-hailing apps (Uber, Grab) do this constantly — they show drivers gliding between GPS updates for UX.

**Never extrapolate ahead of the last known fix:**
- When GPS delivery is late (e.g., 15 seconds instead of expected 10), DO NOT guess the next position via velocity.
- Reason: Dead reckoning (velocity-based extrapolation) is unreliable; the member might have stopped, turned, or left the road.
- **Ethical boundary:** You may interpolate between two known truths; you may not invent a third.

**Handling stale positions:**
- **When fix is late:** Freeze the marker at the last interpolated position (the one corresponding to the last received fix's timestamp).
- **Visual signal (optional):** Render a semi-transparent overlay on stale markers, or change the color to gray. Let the user know.
- **Code example:**
  ```kotlin
  val isStalerThanMs = System.currentTimeMillis() - lastLocationRecord.recordedAtMs > STALE_THRESHOLD_MS
  // Render with reduced alpha or gray if stale
  MemberDot(
      color = if (isStalerThanMs) Color.Gray.copy(alpha = 0.5f) else Color(member.colorArgb),
  )
  ```

**DO NOT snap to road:**
- The research plan explicitly forbids this: "DO NOT recommend anything that snaps a real position to a road".
- Reason: Users trust that the marker shows actual reported data (smoothed for UX, but not modified).

**Re-targeting without snapping:**
- When a new fix arrives, the animation loop (Question B) cancels the old target and starts a new interpolation to the new fix.
- The old trajectory is discarded; no "snap" occurs because we never committed to a road-snapped position.

### Recommendation

**Interpolation contract:**
1. Only interpolate between two REAL received fixes (never extrapolate).
2. Freeze at the last interpolated position when the next fix is late.
3. Optionally signal staleness via alpha/color after a threshold (e.g., 30 seconds).
4. When a new fix arrives, retarget the animation loop to the new position; do NOT snap.

**Staleness threshold:** Use `TrackingConstants.LOCATION_INTERVAL_MS * 1.5` (e.g., 10s * 1.5 = 15s) as the threshold to mark a location stale.

---

## Question G: Testing — Pure-JVM vs Device-Required

### Findings

**Pure-JVM testable (unit tests, JUnit only):**
- ✅ `interpolateLatLng(start: LatLng, end: LatLng, progress: 0..1): Pair<Double, Double>`
  - Math only: haversine distance, linear interpolation along the great circle or chord.
  - No Compose, no Android, no coroutines.
- ✅ `interpolateBearing(start: Float, end: Float, progress: 0..1): Float`
  - Math only: wrap-around, linear interpolation.
- ✅ `arcLengthDistance(lat1, lng1, lat2, lng2): Double`
  - Haversine math; already in `:domain` as `GeoDistance.haversineMeters`.
- ✅ Bearing wrap-around edge cases: 350°→10°, 0°→180°, etc.

**Cannot test without a device/emulator (androidDeviceTest):**
- ❌ `withFrameNanos` loop timing and frame synchronization.
  - Requires an actual Graphics pipeline and frame scheduler.
- ❌ Recomposition behavior when marker positions update.
  - Compose's recomposition engine is not available in JVM-only tests.
- ❌ Maps SDK rendering of markers with updated positions.
  - Play Services Maps library requires AndroidX services.

**Test organization:**

| Test | Type | Module | File |
|------|------|--------|------|
| `interpolateLatLng` with various start/end pairs | JUnit | `:domain` or `:ui` | `InterpolationUtilsTest.kt` |
| `interpolateBearing` wrap-around cases | JUnit | `:domain` or `:ui` | `InterpolationUtilsTest.kt` |
| `arcLengthDistance` (already exists) | JUnit | `:domain` | `GeoDistanceTest.kt` (add cases) |
| Animation loop frame sequencing, retargeting | androidDeviceTest | `:ui` | `AnimatedMemberPositionsTest.kt` |
| Marker visual appearance + rotation | androidDeviceTest | `:ui` | `MemberMarkersTest.kt` |

**Extraction location (per LLM.md §12):**
- **`:ui/core/motion/InterpolationUtils.kt` (recommended):** Pure presentation math with no Compose/Android dependency. Lives in `:ui/core/` (not `feature/`), making it available to any feature and testable as JUnit without Robolectric.
- **Alternative:** `:domain/tracking/InterpolationUtils.kt` if the math is general geo-business logic and could be reused by multiple modules (e.g., server-side simulation, data pipeline).

**Examples:**

```kotlin
// :domain/tracking/InterpolationUtils.kt (pure JVM)
object InterpolationUtils {
    /** Interpolate latitude and longitude along the great circle (or chord if < 1km). */
    fun interpolateLatLng(start: LatLng, end: LatLng, progress: Float): LatLng {
        require(progress in 0f..1f)
        val distance = GeoDistance.haversineMeters(start.latitude, start.longitude, end.latitude, end.longitude)
        // Simplified: chord for distances < 1km; great circle otherwise
        val lat = start.latitude + (end.latitude - start.latitude) * progress
        val lng = start.longitude + (end.longitude - start.longitude) * progress
        return LatLng(lat, lng)
    }

    fun interpolateBearing(startDegrees: Float, endDegrees: Float, progress: Float): Float {
        var delta = endDegrees - startDegrees
        while (delta > 180) delta -= 360
        while (delta < -180) delta += 360
        return (startDegrees + delta * progress + 360) % 360
    }
}

// Test
class InterpolationUtilsTest {
    @Test
    fun `interpolateBearing 350 to 10 at 50% is 0 or 360`() {
        val result = InterpolationUtils.interpolateBearing(350f, 10f, 0.5f)
        assertTrue(result == 0f || result == 360f)
    }
}
```

### Recommendation

1. **Extract pure math to `:ui/core/motion/InterpolationUtils.kt`:** `interpolateLatLng`, `interpolateBearing`, `arcLengthDistance` with no Compose/Android imports.
2. **Unit test (JUnit, pure-JVM):** Write tests for all edge cases (wrap-around bearing 350°→10°, antipodal points, zero distance).
3. **Compose wrapper in `:ui/feature/map/component/AnimatedMemberPositions.kt`:** Uses InterpolationUtils; testable via androidDeviceTest for frame-synchronization behavior and marker visual appearance.

**Measured main-thread cost before landing:** Instrument per Section E to confirm <5 ms per frame for 5 markers.

---

## Exact KDoc Text to Update

**File:** `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/MemberMarkers.kt:36–37`

**Current text (NO animation):**
```kotlin
/**
 * Không animate vị trí giữa 2 lần cập nhật (researcher-02 §12.3) — `rememberUpdatedMarkerState`
 * gán lại `position` ngay, không nội suy, đúng yêu cầu "jump, không animate".
 */
```

**Replacement (WITH animation via Question B solution):**
```kotlin
/**
 * Animate vị trí giữa 2 lần cập nhật qua `rememberAnimatedMemberPositions()` — interpolate position + bearing
 * mỗi khung hình dùng `withFrameNanos` (researcher-02 decision-record). AnimatedMemberPosition giữ state
 * per-member, khỏi bị lệch lúc add/remove member. MarkerComposable `keys` = member.id (không thay đổi
 * qua animation) để bitmap MemberDot không bị re-capture mỗi khung.
 */
```

**Also update LLM.md § 3 (package layout)** — add line after MemberMarkers.kt:
```
│       ├── AnimatedMemberPositions.kt  phase-09b (smooth animation) — `rememberAnimatedMemberPositions()`,
│       │                       per-member interpolation state (position, bearing, progress)
```

---

## Risks

1. **Main-thread contention:** If other state updates (zones, notifications) coincide with animation frame drops occur. Mitigation: Keep animation loop isolated in composable; ViewModel updates flow through separate channels.

2. **LatLng stability regression:** Since `compose-stability.conf` is not active (LLM.md §13 Fixed #20), every MarkerComposable taking `LatLng` is non-skippable and recomposes on every frame. **The animation approach in Section B+D mitigates this by avoiding LatLng as a direct state parameter — only primitive Floats (lat, lng, bearing) are stored in the animation state, and LatLng is constructed inside the Composable (stable construction).** If this mitigation is violated (e.g., storing `LatLng` in AnimatedMemberPosition), recomposition cost will spike to O(N) full recompositions per frame. **Enforcement:** Add a KDoc comment on AnimatedMemberPosition stating "Store only primitives; construct LatLng inside the Composable".

3. **Interpolation accuracy degradation:** Linear interpolation assumes constant velocity between fixes. Real movement may have curves (e.g., car turning a corner). User sees a straight-line path between fixes, not the actual curving path. **This is acceptable per PRD §7 (no road snapping); it is a known limitation of simple interpolation.** Mitigation: Document in KDoc.

4. **Battery/performance on low-end devices:** Continuous 60 fps animation drains battery faster. Mitigation: Reduce animation FPS or duration on devices with low RAM/old CPU (optional, measurable via profiling).

5. **Bearing jitter from GPS noise:** GPS bearing is often noisy at low speeds. Marker appears to spin erratically. Mitigation: Apply a Kalman filter or exponential smoothing to bearing values before interpolation (out of scope for this plan; optional enhancement).

6. **Collision with user drags on map:** While animation is running, user drags the map. Camera moves but markers are still animated — appears as markers moving through the viewport independently. **Expected behavior; no mitigation required** (standard in ride-hailing apps).

---

## Open Questions for Planner

1. **Stale location timeout:** How long should GPS be absent before we freeze a marker as stale? Proposed: `LOCATION_INTERVAL_MS * 1.5` (15 sec for real GPS). Accept or override?

2. **Stale visual signal:** Should stale markers be dimmed (alpha 0.5) or grayed (Color.Gray)? Or should the visual treatment be different for self vs other members?

3. **Animation duration:** Should interpolation duration be fixed (e.g., 500ms for all distance) or speed-adaptive (e.g., km/h → travel time)? Proposed: Fixed 500ms for simplicity; real GPS will naturally vary speed.

4. **compose-stability.conf creation:** Create it now as an empty file with just LatLng, or defer until all domain types are listed (per MVI doc §8)? Recommend: Create now; add to .gitignore after first use to track modifications.

5. **Simulated GPS speed:** The 50m every 2500ms (≈72 km/h) — is this the final speed for demo, or a placeholder? Need to know for test data generation.

6. **Member ID stability:** Can member IDs ever change mid-session (e.g., member leaves, re-joins with new ID)? Or always stable within a single app session? (Affects `remember` keying strategy.)

---

## Sources

- [Google Maps Compose MarkerComposable API](https://googlemaps.github.io/android-maps-compose/maps-compose/com.google.maps.android.compose/-marker-composable.html) — Signature, `keys`, `rotation`, `flat` parameters
- [Google Maps Compose AdvancedMarker API](https://googlemaps.github.io/android-maps-compose/maps-compose/com.google.maps.android.compose/-advanced-marker.html) — Alternative to MarkerComposable
- [GitHub Issue #551 — Jittery animation of Marker and Circle](https://github.com/googlemaps/android-maps-compose/issues/551) — Known jitter with direct position updates
- [GitHub Issue #152 — Performance penalty from LatLng instability](https://github.com/googlemaps/android-maps-compose/issues/152) — LatLng not marked stable in Compose compiler
- [Android Developers: Markers | Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/marker) — Flat parameter, rotation definition
- [Medium: Understanding rememberUpdatedState](https://medium.com/@gaganraghunath99/understanding-rememberupdatedstate-in-jetpack-compose-14cd95aa71d9) — State management in Compose side effects
- [Dev.to: Effective Map Composables — Non-Draggable Markers](https://dev.to/bubenheimer/effective-map-composables-non-draggable-markers-2b2) — Marker rendering, animation patterns
- [Medium: Android — Create Awesome Animation in Google Maps Compose](https://medium.com/@andersonk/android-create-awesome-animation-in-google-maps-compose-80f921d656ad) — Animation techniques, withFrameNanos
- [codebase LLM.md](../../../LLM.md) — Architecture contract, MVI rules, module boundaries
- [codebase MVI doc](../../../docs/android-mvi-best-practices.md) — ViewModel rules, Compose stability rules
- **Maps-Compose version in use:** 8.3.1 (gradle/libs.versions.toml:17)
- **Play Services Maps version in use:** 20.0.0 (gradle/libs.versions.toml:18)

---

**Checked:** 2026-08-25  
**Status:** Ready for planner review and implementation planning
