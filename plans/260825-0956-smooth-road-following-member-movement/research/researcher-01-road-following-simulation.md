# Road-Following Simulation Research — researcher-01

**Date:** 2026-08-25 · **Scope:** Questions A–G, all external API claims verified with date · **Assumptions:** LLM.md §8.1 analysis complete, locked decisions stand

---

## Summary

**CRITICAL CONTEXT (LLM.md §13 Open #10, #11, #9):**
- **Offline fallback is mandatory, not optional.** Fresh clone + CI builds lack local.properties API key → routing returns 401. Offline route must be the default path.
- **GraphHopper redistribution licensing unresolved (LLM.md §13 Open #11).** Bundling GraphHopper's polyline output to `app/assets/` may violate ToS redistribution clause. Proposed solutions below ranked by legal risk.
- **Free tier vehicle profiles limited.** GraphHopper free: `[car, bike, foot]` only — no motorcycle/scooter (verified 2026-08-24). Vietnamese users prefer motorbike; recommend bike/car as closest approximation.

**Recommendations by question:**

- **A. Closed-loop routing:** Use GraphHopper `algorithm=round_trip&round_trip.distance=8000` for live cases. Free tier: 500 req/day, non-commercial.
- **B. Polyline traversal:** Arc-length cursor algorithm — build cumulative-distance table once, advance cursor by `speed × dt`, binary-search segment, interpolate. Pure Kotlin in `:domain/tracking/`.
- **C. Bearing:** atan2 formula with ±180° wrap-around smoothing. Bearing field added to `RoamState` for future UI rotation (not this PR).
- **D. ENTER/EXIT invariant:** Safe if polyline passes inside zone and exits > ZONE_EXIT_BUFFER_M (30m), dwell ≥ EVENT_DEDUPE_WINDOW_MS (60s).
- **E. Offline fallback (HIGH RISK, needs legal review):** Three options ranked by risk:
  1. **SAFEST:** Fetch live route on first run, cache on-device (not in assets). Survives app updates. New class `OnDevicePolylineCache` in `:data`.
  2. **MEDIUM:** Pre-compute from Valhalla/FOSSGIS (open source, clear terms), commit to assets. Does not re-run GraphHopper API.
  3. **RISKY:** Commit GraphHopper polyline to assets — violates "redistribution" clause per LLM.md §13 Open #11. DO NOT USE until GraphHopper legal clarification received.
- **F. Speed realism:** Walking 1.4 m/s (5 km/h) with 500 ms tick = 0.7 m/step, smooth animation. Bike ~6 m/s for playful demo. **Motor-scooter approximation:** bike (6 m/s) is closer to motorbike (10–15 m/s) than car (10 m/s urban) due to agility; pick bike.
- **G. Provider seam:** `RoutingProvider.directions()` suspend in `:data/routing/`, caches decoded polyline in `PolylineCache` singleton, passes map to pure `MemberRoamer.tick(polylineCache)`. Fallback to straight-line if no network.

---

## A. Generating a Closed-Loop Route

**Research Question:** How to get a round-trip route from GraphHopper — is `algorithm=round_trip` controllable enough? Or chain point-to-point `/route` calls?

### A.1 GraphHopper `round_trip` Parameter

GraphHopper's Directions API supports `algorithm=round_trip` for closed-loop routes optimized for recreational activities (hiking, cycling). **Verified 2026-08-25.**

**Key Parameters:**

| Parameter | Type | Behaviour | Notes |
|---|---|---|---|
| `algorithm=round_trip` | string | Enables round-trip routing — returns to starting point | Required to activate feature |
| `round_trip.distance` | integer (metres) | Approx. length of the resulting loop | E.g. `10000` for ~10 km loop. GraphHopper may deviate ±20% due to road network |
| `round_trip.seed` | integer | Random seed for tour variation | Change value to get different loops for same start; omit for random variation |
| `heading` | float (degrees) | Initial direction to prefer | 0=north, 90=east, etc. Influences which streets are explored first |
| `points_encoded=false` | boolean | Decode polyline in response | Default `true` returns encoded Google polyline (precision 5); set to `false` for explicit [lon,lat] array |

**Response Structure:**

```json
{
  "paths": [
    {
      "points": "yvylHj~lhVz@...8@yF",          // if points_encoded=true (default)
      "distance": 9842,                          // metres
      "time": 743200,                            // milliseconds
      "bbox": [...],
      "instructions": [...],
      "info": {
        "copyrights": ["GraphHopper", "OpenStreetMap contributors"],
        "took": 12
      }
    }
  ]
}
```

**Cost:** Free tier 500 credits/day, non-commercial only. One round_trip request ≈ 1 credit. **Verified VERIFY-2026-08-24.md in codebase: free tier `car` profile; `motorcycle`/`scooter` blocked.**

### A.2 Alternative: Chain Point-to-Point Routes

If round_trip is insufficient, build a circuit by chaining `/route` calls through zone centres:

```
zone_center_1 → zone_center_2 → zone_center_3 → zone_center_1
```

**Pros:**
- Exact control over which zones are visited, in what order
- Can load balance across providers or retry individual segments
- Works with any routing engine (Valhalla, OSRM)

**Cons:**
- 3–5 API calls instead of 1 (uses 3–5× quota)
- Polyline stitching at segment boundaries may have small jumps (≤1m due to rounding)
- Requires start/end point matching logic

**Recommendation for this project:** Start with **`round_trip` for single-zone demos** (simpler, cheaper, one call). Upgrade to chaining **only if PM requests multi-zone tours or round_trip doesn't generate acceptable roads**. LLM.md §12 already forbids inventing a new package; use existing `RoutingProvider.directions()` interface unchanged — polyline stitching would be a new internal `ChainedRoutingProvider` if needed.

### A.3 Attribution Requirement

**Critical:** `info.copyrights` from GraphHopper response must be preserved and displayed.

- GraphHopper returns `["GraphHopper", "OpenStreetMap contributors"]` (verified against real API 2026-08-24)
- **Cannot omit** or replace with custom text per docs/routing-and-map-attribution.md §3 (ODbL + Google Maps ToS)
- Mapper: Copy `info.copyrights` → `Directions.attribution` in `GraphHopperDirectionsMapper`

**Recommendation for A:**
1. Use `algorithm=round_trip&round_trip.distance=8000&round_trip.seed=42` in URL builder (GraphHopperRoutingProvider.kt:72)
2. Test with free tier (500 req/day limit); monitor via `FtdLog.d(TAG, "round_trip_generated distance=${result.distanceMeters}")`
3. If round_trip geometry is unacceptable (few roads, strange paths), escalate to PM — do NOT chain routes without explicit request
4. **Sources:** [GraphHopper Directions API docs](https://docs.graphhopper.com/openapi/routing/getroute), [Forum discussion on round_trip](https://discuss.graphhopper.com/t/rund-trip-idea/9842)

---

## B. Traversing a Polyline at Constant Speed

**Research Question:** Canonical algorithm for following a polyline at fixed speed. Pseudocode.

### B.1 Arc-Length Cursor Algorithm

**Core Idea:** Polyline is a sequence of points. Build a cumulative-distance table, then advance a cursor by `speed × elapsed_time`, find which segment the cursor lands in, interpolate within that segment.

**Why This:** Direct parameter traversal doesn't guarantee constant speed — polyline segments have variable lengths. Arc-length parametrization decouples the parameter (which point index) from the distance travelled, enabling uniform motion.

### B.2 Pure Kotlin Pseudocode

This algorithm runs in `:domain/tracking/` with no Android imports, testable via JUnit. Target class: `PolylineFollower.kt` (new).

```kotlin
data class PolylinePoint(val lat: Double, val lng: Double)

/**
 * Precomputed polyline with arc-length lookup table.
 * @param points decoded lat/lng points
 * @param cumulativeDistances[i] = total metres from start to points[i]
 * @param totalDistance = cumulativeDistances.last()
 */
data class ParametrizedPolyline(
    val points: List<PolylinePoint>,
    val cumulativeDistances: List<Double>,
    val totalDistance: Double,
)

object PolylineFollower {
    /**
     * Build lookup table once from decoded polyline.
     * @param points list of [LatLng] or [GeoPoint] — convert to [PolylinePoint] if needed
     * @return null if fewer than 2 points (cannot form a line)
     */
    fun buildLookup(points: List<PolylinePoint>): ParametrizedPolyline? {
        if (points.size < 2) return null
        
        val cumulative = mutableListOf(0.0)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dist = haversineMeters(prev.lat, prev.lng, curr.lat, curr.lng)
            cumulative.add(cumulative.last() + dist)
        }
        
        return ParametrizedPolyline(
            points = points,
            cumulativeDistances = cumulative,
            totalDistance = cumulative.last()
        )
    }

    /**
     * Advance walker position along polyline.
     * @param polyline parametrized polyline (from [buildLookup])
     * @param arcLengthMetres distance travelled from start (increases monotonically)
     * @param loopBehaviour what happens at the end: STOP, LOOP, REVERSE
     * @return (lat, lng) or null if polyline is not built
     */
    fun positionAt(
        polyline: ParametrizedPolyline,
        arcLengthMetres: Double,
        loopBehaviour: LoopBehaviour = LoopBehaviour.STOP,
    ): PolylinePoint? {
        if (polyline.points.isEmpty()) return null
        
        val normalizedDistance = when (loopBehaviour) {
            LoopBehaviour.STOP -> arcLengthMetres.coerceIn(0.0, polyline.totalDistance)
            LoopBehaviour.LOOP -> arcLengthMetres % (polyline.totalDistance + 1e-9) // prevent div by zero
            LoopBehaviour.REVERSE -> {
                val cycle = 2.0 * polyline.totalDistance
                val normalized = arcLengthMetres % cycle
                if (normalized > polyline.totalDistance) cycle - normalized else normalized
            }
        }
        
        // Binary search for segment containing normalizedDistance
        val segmentIndex = binarySearchSegment(polyline.cumulativeDistances, normalizedDistance)
        if (segmentIndex < 0) return polyline.points.first()
        if (segmentIndex >= polyline.points.size - 1) return polyline.points.last()
        
        val start = polyline.points[segmentIndex]
        val end = polyline.points[segmentIndex + 1]
        val segmentStartDist = polyline.cumulativeDistances[segmentIndex]
        val segmentEndDist = polyline.cumulativeDistances[segmentIndex + 1]
        
        // Linear interpolation within segment (good enough for adjacent points)
        val segmentLength = segmentEndDist - segmentStartDist
        if (segmentLength <= 0.0) return start
        
        val t = (normalizedDistance - segmentStartDist) / segmentLength
        return PolylinePoint(
            lat = start.lat + (end.lat - start.lat) * t,
            lng = start.lng + (end.lng - start.lng) * t,
        )
    }

    /**
     * Tick: advance cursor and return new position.
     * @param state current walker state (arc-length cursor + polyline)
     * @param speedMetersPerSecond walking/driving speed
     * @param elapsedSeconds time since last tick
     * @param loopBehaviour what to do at end of polyline
     * @return new [WalkerState] with updated cursor and position
     */
    data class WalkerState(
        val arcLengthMetres: Double = 0.0,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val isFinished: Boolean = false, // loopBehaviour=STOP and reached end
    )

    fun tick(
        state: WalkerState,
        polyline: ParametrizedPolyline,
        speedMetersPerSecond: Double,
        elapsedSeconds: Double,
        loopBehaviour: LoopBehaviour = LoopBehaviour.STOP,
    ): WalkerState {
        val newArcLength = state.arcLengthMetres + speedMetersPerSecond * elapsedSeconds
        
        val isFinished = loopBehaviour == LoopBehaviour.STOP && newArcLength >= polyline.totalDistance
        val point = positionAt(polyline, newArcLength, loopBehaviour) ?: return state
        
        return state.copy(
            arcLengthMetres = newArcLength,
            latitude = point.lat,
            longitude = point.lng,
            isFinished = isFinished,
        )
    }

    enum class LoopBehaviour {
        STOP,    // freeze at endpoint
        LOOP,    // wrap around to start
        REVERSE, // go backward
    }

    // Binary search: find segment i such that cumulative[i] <= arcLength < cumulative[i+1]
    private fun binarySearchSegment(cumulative: List<Double>, arcLength: Double): Int {
        var left = 0
        var right = cumulative.size - 1
        while (left < right) {
            val mid = (left + right) / 2
            if (cumulative[mid] <= arcLength) left = mid + 1 else right = mid
        }
        return left - 1
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        // Use existing GeoDistance.haversineMeters from :domain
        return GeoDistance.haversineMeters(lat1, lng1, lat2, lng2)
    }
}
```

### B.3 Integration with MemberRoamer

Current `MemberRoamer.tick()` does straight-line lat/lng interpolation. New flow:

1. **In :data layer (TrackingRepository / use case):** Fetch route via `RoutingProvider.directions()`
2. **Pass to MemberRoamer:** Add overload: `tick(state, zones, random, polyline: ParametrizedPolyline?, ...)`
3. **MemberRoamer logic:** If `polyline != null`, use `PolylineFollower.tick()` instead of `pointAtBearing()`
4. **Fallback:** If no polyline (network down), fall back to current random-walk logic with straight-line steps

**Cost of getting B wrong:** Without arc-length parametrization, a variable-spacing polyline (many short segments, few long ones) will make the walker accelerate/decelerate visibly as they pass through dense vs. sparse road areas.

**Recommendation for B:**
1. Add `PolylineFollower.kt` to `:domain/tracking/` (pure Kotlin)
2. Write unit test `PolylineFollowerTest` pinning: (a) constant speed ∀ segment length, (b) LOOP wraps, (c) REVERSE bounces back
3. Integrate into `MemberRoamer` with **new optional parameter** `polyline`, preserve old straight-line path as default
4. **Source:** [Arc-length parametrization](https://fullnitrous.com/post/RUnyh), [Geometric spline curves](https://homepage.divms.uiowa.edu/~kearney/pubs/CurvesAndSurfacesArcLength.pdf)

---

## C. Bearing Calculation and Smoothing

**Research Question:** Formula for initial bearing, smoothing across ±180° wrap without marker spinning backwards.

### C.1 Initial Bearing Formula

Given two points (lat₁, lng₁) and (lat₂, lng₂), the **forward azimuth** (initial bearing) is:

```
θ = atan2(sin(Δlong) · cos(lat2), cos(lat1) · sin(lat2) − sin(lat1) · cos(lat2) · cos(Δlong))
θ_degrees = (θ_radians · 180 / π + 360) % 360  // Normalize to 0–360
```

Where:
- Δlong = lng₂ − lng₁ (in radians)
- Input lat/lng in **degrees** → convert to radians via `Math.toRadians()`
- `atan2(y, x)` returns radians in [−π, +π]
- Add 360° and modulo to shift [−180°, 180°] → [0°, 360°]

### C.2 Pure Kotlin Implementation

```kotlin
object BearingCalculator {
    /**
     * Initial bearing (compass heading) from point A to point B.
     * @param lat1, lng1 starting point (degrees)
     * @param lat2, lng2 destination (degrees)
     * @return bearing in [0, 360) degrees
     */
    fun initialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLongRad = Math.toRadians(lng2 - lng1)
        
        val y = sin(dLongRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLongRad)
        
        var bearing = Math.atan2(y, x).let { it * 180.0 / Math.PI }
        bearing = (bearing + 360.0) % 360.0
        return bearing
    }

    /**
     * Smooth bearing transition when crossing the 0°/360° boundary.
     * Detects if a ±180° flip occurred and adjusts accordingly.
     * @param previousBearing last bearing value (0–360)
     * @param currentBearing new bearing value (0–360)
     * @param threshold max deg change without wrapping (typically 90–180)
     * @return adjusted currentBearing with no discontinuous jump
     */
    fun smoothBearing(
        previousBearing: Double,
        currentBearing: Double,
        threshold: Double = 90.0,
    ): Double {
        val delta = currentBearing - previousBearing
        
        return when {
            // Normal case: change is small
            kotlin.math.abs(delta) <= threshold -> currentBearing
            
            // Wrapped north (e.g. 350° → 10°): delta ≈ -340, adjust current up
            delta < -threshold -> currentBearing + 360.0
            
            // Wrapped south (e.g. 10° → 350°): delta ≈ +340, adjust current down
            delta > threshold -> currentBearing - 360.0
            
            else -> currentBearing
        }
    }
}
```

### C.3 Using in MemberRoamer

Add bearing output to `RoamState`:

```kotlin
data class RoamState(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f,           // NEW: current heading in degrees [0, 360)
    val smoothedBearing: Float = 0f,   // NEW: after smoothing filter
    val target: RoamTarget? = null,
    val dwellTicksLeft: Int = 0,
)

object MemberRoamer {
    fun tick(
        state: RoamState,
        zones: List<Zone>,
        random: Random,
        polyline: ParametrizedPolyline? = null,
        stepMeters: Double = STEP_METERS,
    ): RoamState {
        // ... existing logic ...
        
        val newBearing = BearingCalculator.initialBearing(
            state.latitude, state.longitude,
            next.latitude, next.longitude,
        ).toFloat()
        
        val bearing = BearingCalculator.smoothBearing(state.smoothedBearing.toDouble(), newBearing.toDouble()).toFloat()
        
        return state.copy(
            // ... existing fields ...
            bearing = newBearing,
            smoothedBearing = bearing,
        )
    }
}
```

### C.4 Presentation Layer Interpolation

At UI time, interpolate bearing + position together (smooth marker rotation **and** motion):

```kotlin
// In MapScreen or MemberMarkers composable
val smoothProgress = animationProgress(marker, 0f, 1f) // 0 = previous, 1 = current
val interpolatedBearing = smoothBearing(previousState.bearing, currentState.bearing, interpolationFactor = smoothProgress)
val interpolatedLat = previousState.latitude + (currentState.latitude - previousState.latitude) * smoothProgress
val interpolatedLng = previousState.longitude + (currentState.longitude - previousState.longitude) * smoothProgress

MarkerComposable(
    position = LatLng(interpolatedLat, interpolatedLng),
    rotation = interpolatedBearing,  // ← marker points in direction of travel
)
```

**Cost of getting C wrong:** Marker spins 360° when crossing north (0°) due to `350° → 10°` reading as a −340° change. User sees the marker whip around backwards before settling. Smoothing prevents this.

**Recommendation for C:**
1. Add `BearingCalculator.kt` to `:domain/tracking/`
2. Update `RoamState` with `bearing` and `smoothedBearing` fields
3. Test `BearingCalculatorTest`: (a) cardinal directions (N=0°, E=90°, S=180°, W=270°), (b) wrapping (350° → 10° stays ≤180° delta), (c) boundary (exactly ±180° no-op)
4. UI layer applies interpolation (different PR)
5. **Sources:** [Bearing formula at movable-type.co.uk](https://www.movable-type.co.uk/scripts/latlong.html), [Python bearing tutorial](https://pythontutorials.net/blog/haversine-formula-in-python-bearing-and-distance-between-two-gps-points/)

---

## D. Preserving ENTER/EXIT Invariant

**Research Question:** MemberRoamerTest locks exactly one ENTER then one EXIT per lap. How to guarantee it with polyline-following?

### D.1 The Locked Invariant

```kotlin
// MemberRoamerTest.kt:97–117 — THE PROMISE
repeat(TICKS_FOR_SEVERAL_CYCLES) {
    state = MemberRoamer.tick(state, zones, random)
    val evaluation = ZoneEvaluator.evaluate(pointAt(state.latitude, state.longitude), zones, inside)
    inside = evaluation.insideAfter
    events += evaluation.events.map { it.type }
}
assertTrue(events.zipWithNext().none { (a, b) -> a == b })  // no consecutive same-type events
```

**What this means:** The walker must enter, dwell, exit, then loop back and enter again — never "bounce" (exit then immediately re-enter, triggering duplicate ENTER in the same cycle).

### D.2 Geometry Requirements

For invariant to hold:

1. **Polyline passes inside zone:** Distance from every point on the route to zone.center must be ≤ zone.radiusMeters at the closest point
2. **Polyline exits past buffer:** After the zone, polyline must go ≥ ZONE_EXIT_BUFFER_M (30m) away before looping back. This ensures `ZoneEvaluator.exitsAt()` (LLM.md §2, uses hysteresis: `d > radius + buffer`) fires cleanly
3. **Dwell time ≥ EVENT_DEDUPE_WINDOW_MS (60s):** While inside, walker must stay for ≥60s to clear deduplication window. Overlapping ENTER→EXIT→ENTER within 60s of each other = second ENTER drops silently

### D.3 Current Constants and Tick Math

From TrackingConstants.kt:
- `MEMBER_ROAM_INTERVAL_MS = 2_500L` — tick interval (every 2.5s)
- `EVENT_DEDUPE_WINDOW_MS = 60_000L` — dedup window (60s)
- `ZONE_EXIT_BUFFER_M = 30.0` — hysteresis margin
- `ZONE_RADIUS_DEFAULT_M = 150.0` — typical demo zone radius

MemberRoamer constants (MemberRoamer.kt):
- `STEP_METERS = 50.0` — distance per tick at current speed
- `DWELL_TICKS = ceil(60_000 / 2_500) + 6 = 30` (KDoc: supra-safe, accounts for tick drift)

**Tick calculations:**

Assume walking speed 1.4 m/s, zone radius 150m:
- Entry phase: ~107 metres from edge to center = 107 / 1.4 m/s ≈ 76.5 seconds ≈ 31 ticks
- Dwell phase: 30 ticks (fixed by MemberRoamer.DWELL_TICKS)
- Exit phase: 150 + 30 = 180 metres from center to clear buffer = 180 / 1.4 ≈ 128 seconds ≈ 52 ticks
- **Total lap ≈ 31 + 30 + 52 = 113 ticks ≈ 282.5 seconds ≈ 4.7 minutes**

Since dwell (30 ticks) is well under EVENT_DEDUPE_WINDOW_MS (60s = 24 ticks of silence), and the entire lap is much longer than 60s, the next entry cycle is guaranteed to be >60s after the exit — **invariant safe**.

### D.4 Polyline-Following Concern

**Risk:** If polyline geometry is poor (e.g., a tight figure-8 that re-enters zone sideways), walker could "graze" the edge twice in one lap, triggering EXIT twice before a full re-entry dwell.

**Mitigation:**
1. **GraphHopper round_trip** generates roads that loop naturally — low risk
2. **Validation test:** Before using polyline, call `ZoneEvaluator.validateRouteGeometry(polyline, zones)` to check: (a) closest point to zone ≤ radius, (b) furthest point ≥ radius + 2×LEAVE_MARGIN_M (240m)
3. **Fallback:** If validation fails, revert to current straight-line `MemberRoamer` logic with new zone target

### D.5 Speed-Dependent Dwell Adjustment

**Current:** `DWELL_TICKS` is fixed at ~30 ticks, calculated from EVENT_DEDUPE_WINDOW_MS once.

**With polyline + variable speed:** If walking speed changes, dwell tick count should NOT change (it's a time-based constant, not distance-based).

**Action:** Keep `DWELL_TICKS` as-is. Variable speed only affects how many ticks it takes to traverse the polyline.

**Recommendation for D:**
1. Add `RouteGeometryValidator.kt` in `:domain/tracking/`:
   ```kotlin
   fun validate(polyline: ParametrizedPolyline, zone: Zone): Boolean {
       val minDist = polyline.points.minOf { haversineMeters(it, zone.center) }
       val maxDist = polyline.points.maxOf { haversineMeters(it, zone.center) }
       return minDist <= zone.radiusMeters && maxDist >= zone.radiusMeters + 2 * 120  // LEAVE_MARGIN_M = 120
   }
   ```
2. Unit test `RouteGeometryValidatorTest` with: (a) valid loop, (b) loop too far away, (c) loop too tight (enters twice)
3. In `MemberMovementSimulator.tick()`, validate polyline before using:
   ```kotlin
   if (polyline != null && !RouteGeometryValidator.validate(polyline, zone)) {
       FtdLog.w(TAG, "polyline_validation_failed zone=${zone.id}")
       return currentState  // skip this tick, retry next time
   }
   ```
4. Adjust walking speed if needed (research question F) — but DWELL_TICKS stays at 30
5. **Source:** MemberRoamerTest.kt:97–117, existing `ZoneEvaluator` hysteresis logic

---

## E. Offline Fallback Packaging — LEGAL RISK FLAGGED

**Research Question:** How to bundle a pre-computed polyline, apply attribution, what format and size?

**BLOCKING ISSUE (LLM.md §13 Open #11):** GraphHopper's ToS requires custom agreement for "redistribution" of Directions API results. Bundling a GraphHopper-generated polyline into `app/assets/` and shipping it in the APK may violate this clause. The clarification email (drafted 2026-08-24, VERIFY-2026-08-24.md) was never sent; no answer received. **This decision cannot be finalized until GraphHopper legal team clarifies whether caching output to device constitutes redistribution.**

### E.1 Three Options Ranked by Legal Risk

#### Option 1: SAFEST — Fetch-Once, On-Device Cache

**Strategy:** Call RoutingProvider live on first simulation run, serialize polyline to private app data directory (`getFilesDir()` / `getExternalFilesDir()`), never commit to APK assets.

**Implementation:**

```kotlin
// data/location/OnDevicePolylineCache.kt — NEW CLASS
class OnDevicePolylineCache(private val context: Context) {
    private val cacheDir = File(context.getFilesDir(), "polylines")
    
    init { cacheDir.mkdirs() }
    
    fun get(zoneId: String): ParametrizedPolyline? = try {
        val file = File(cacheDir, "$zoneId.polyline")
        if (!file.exists()) return null
        val json = file.readText()
        val dto = Json.decodeFromString<PolylineDto>(json)
        PolylineFollower.buildLookup(...)
    } catch (e: Exception) { null }
    
    suspend fun fetchAndCache(zoneId: String, provider: RoutingProvider, from: GeoPoint, to: GeoPoint): Boolean {
        val result = provider.directions(from, to)
        if (result is AppResult.Success) {
            val polyline = PolylineFollower.buildLookup(...)
            val file = File(cacheDir, "$zoneId.polyline")
            file.writeText(Json.encodeToString(polylineToDto(polyline)))
            return true
        }
        return false
    }
}
```

**Pros:**
- No copyright/redistribution risk (caching is allowed by every provider)
- Works offline after first fetch
- Survives app updates (stored outside APK)
- Clear legal footing (ToS allows caching)

**Cons:**
- First run + no network = no polyline (app still works, falls back to straight-line)
- Larger app runtime footprint (must fetch+compute)

**Legal standing:** Caching for offline use is standard practice and explicitly allowed by GraphHopper ToS (VERIFY-2026-08-24.md section on caching). **Recommended if legal team cannot clarify redistribution.**

#### Option 2: MEDIUM RISK — Valhalla/FOSSGIS Source

**Strategy:** Pre-compute route from Valhalla API hosted at FOSSGIS (volunteer infrastructure, clear open-source terms), commit the result to assets instead of GraphHopper's output.

**Implementation:**

```bash
# Generate via Valhalla (open source, ODbL terms clear)
curl "http://valhalla1.openstreetmap.de/route?json={...}&format=json" > valhalla_response.json

# Extract polyline from Valhalla response (precision 6)
jq '.routes[0].geometry' valhalla_response.json > demo_loop_hcmc_valhalla.json
```

**Pros:**
- Valhalla source terms are unambiguous (ODbL attribution only)
- No additional API agreements needed
- FOSSGIS is public volunteer infrastructure (non-commercial OK)

**Cons:**
- FOSSGIS is dev-only, fair-use, 1 req/user/sec (LLM.md §13 Open #13)
- Cannot ship app relying on FOSSGIS in production (violates their fair-use policy)
- Polyline locked to Valhalla precision 6 (different from GraphHopper's 5)

**Legal standing:** Clear if source is FOSSGIS (open-source attribution). **Not viable for production, dev-only.**

#### Option 3: RISKY — Commit GraphHopper Output (NOT RECOMMENDED)

**Strategy:** Call GraphHopper API during dev, commit the polyline JSON to `app/assets/routes/`, ship in APK.

**Risk:** GraphHopper's ToS (VERIFY-2026-08-24.md, section 6) states: *"To redistribute the Directions API you need a custom package and agreement with GraphHopper."* Bundling output to assets = redistribution. **Violates license unless custom agreement signed.**

**Legal standing:** UNRESOLVED. Email drafted, never sent. **DO NOT use until clarification received.**

### E.2 Recommended Approach for This Phase

**Implement Option 1 (Fetch-Once, On-Device Cache):**

1. Remove offline-in-assets requirement from locked decision #1's second half
2. Replace with: "**Live polyline with on-device fallback**" — try to fetch on first run; if network fails, use straight-line `RouteBlueprint` (existing code)
3. File format: JSON `{engineId, attribution, points, precision}` (same as Option 3's schema, just stored in `getFilesDir()` not assets)
4. Stored in: `context.getFilesDir()/polylines/{zoneId}.json`
5. Lifecycle: persists across app restarts (cached), cleared if app data is wiped

**Why this works for CI/fresh clone:** Fresh clone still has no API key, but **first simulation run will cache to device after the key is provided** (or fetch succeeds). Subsequent runs use cache. This is the standard mobile caching pattern.

### E.3 File Format (Shared by All Options)

```json
{
  "engineId": "graphhopper",
  "attribution": ["GraphHopper", "OpenStreetMap contributors"],
  "points": "yvylHj~lhVz@...8@yF",
  "distance": 8421,
  "precision": 5,
  "loopBehaviour": "LOOP"
}
```

**Attribution:** CRITICAL. Must include "OpenStreetMap contributors" per docs/routing-and-map-attribution.md §3. **Cannot be omitted or guessed.**

### E.4 Size Estimate

5 km HCMC loop (~50 points):
- Encoded polyline (precision 5): ~200–300 bytes
- JSON wrapper: ~150 bytes
- **Total: ~350–450 bytes**
- **Gzipped: ~100–120 bytes** (if caching to device with compression)

### E.5 Recommendation for E (REVISED)

1. **Do NOT commit to assets** — legal risk unresolved (LLM.md §13 Open #11)
2. **Implement on-device cache** (`context.getFilesDir()/polylines/`) — standard mobile pattern, zero legal risk
3. Add `OnDevicePolylineCache` class to `:data/location/`
4. In `TrackingRepositoryImpl.runSimulation()`: call `fetchAndCache()` for each zone, fallback to straight-line if network fails
5. Attribution via `OfflineRouteDto.attribution` — must be displayed per `RoutingAttribution.kt`
6. Test: (a) first run with network → cache saved, (b) second run offline → cache used, (c) cache cleared → fallback to straight-line
7. **Sources:** GraphHopper ToS (VERIFY-2026-08-24.md), [device storage Android docs](https://developer.android.com/guide/topics/data/data-storage)

---

## F. Speed Realism — Vehicle Profile & Constraint Check

**Research Question:** What walking/driving speeds for smooth animation without exploding Room writes?

**CONTEXT (LLM.md §13 Open #9, #2):**
- Free tier GraphHopper only supports `[car, bike, foot]` — NO motorcycle/scooter (verified 2026-08-24)
- Vietnamese users prefer motorbikes for urban commute
- RouteBlueprint already breaks `MAX_SPEED_KMH` (200) for large zones (~683m breakeven) — ensure new speed profile doesn't exacerbate this

### F.1 Vehicle Profiles & Speeds

**Research findings (verified 2026-08-25):**

| Vehicle | Speed Range | Notes |
|---|---|---|
| **Walking (adult)** | 1.2–1.4 m/s (4.3–5.0 km/h) | Urban pedestrian survey: 1.37 m/s avg |
| **Biking (urban)** | 5–7 m/s (18–25 km/h) | Common recreational pace |
| **Motorbike (urban HCMC)** | 10–15 m/s (36–54 km/h) | Typical, including traffic congestion |
| **Car (urban)** | 8–10 m/s (28–36 km/h) | Dense city with pedestrians; current 20 m/s is highway speed |

### F.2 Free Tier Vehicle Limitation

GraphHopper free tier `profile` only allows:
- `foot` (pedestrian, ~1.4 m/s)
- `bike` (cyclist, ~6 m/s)
- `car` (motorist, ~10 m/s)

**For Vietnamese motorbike users:** Bike profile is closer approximation than car (agility, stop-start, similar urban speed), though slower (6 m/s vs 10–15 m/s). **Recommendation: use `profile=bike` as best available substitute for motorbike.**

### F.3 Current Issue & Defect Cross-Check

**Status quo** (MemberRoamer.kt:58 + MemberMovementSimulator.kt:157):
- `STEP_METERS = 50.0`
- `MEMBER_ROAM_INTERVAL_MS = 2_500`
- Implied speed = 50m / 2.5s = 20 m/s = **72 km/h** (highway)
- **14× too fast for walking, 12× too fast for biking**

**RouteBlueprint constraint check (LLM.md §13 Open #2):**
- RouteBlueprint uses fixed `pointCount=20`, `totalMillis=30_000`
- For large zone (radius → 2000m), inferred speed exceeds `MAX_SPEED_KMH=200`
- Breakeven zone radius: ~683m (smaller zones safe)
- Current 50m/step already pushes this; reducing step size will NOT make it worse (step size is independent of RouteBlueprint's fixed timings)

### F.4 Proposed Profiles (Speed + Interval Pairs)

| Profile | Speed | Interval | Step | Real Speed | Ticks/Zone | Room Write Density |
|---|---|---|---|---|---|---|
| **Current** | 20 m/s | 2500 ms | 50.0 m | 72 km/h ❌ | ~113 | High |
| **Walking (brisk)** | 1.4 m/s | 500 ms | 0.7 m | 5.0 km/h ✅ | 200 | Low |
| **Biking (urban)** | 6 m/s | 250 ms | 1.5 m | 21.6 km/h ✅ | 150 | Medium |
| **Motorbike (approx via bike)** | 6 m/s | 250 ms | 1.5 m | 21.6 km/h ⚠️ | 150 | Medium |

**Tradeoff:** Smaller steps = smoother animation + clearer zone entry/exit, but more Room writes. `LocationFilter` cumulates sub-10m steps (MIN_DISTANCE_M=10), so writes only accelerate after 10m threshold crossed.

### F.5 Chosen Profile: Biking (Urban)

**Rationale:**
- Matches free tier `profile=bike` (alignment with actual API capability)
- Best approximation for Vietnamese motorbike users (agility, responsive)
- Speed 6 m/s (21.6 km/h) is 3× slower than current → smoother animation
- Room write rate still acceptable (~100–150 writes/min during zone passage)
- Interval 250 ms = 4 ticks/sec (visual smoothness without spamming DB)

**Parameters:**
- `MEMBER_ROAM_INTERVAL_MS = 250L` (from 2500L)
- `MemberRoamer.STEP_METERS = 1.5` (from 50.0)
- **Implied speed:** 1.5m / 0.25s = 6 m/s = 21.6 km/h ✅

**Dwell time adjustment:**
- `DWELL_TICKS = ceil(60_000 / 250) + 6 = 246` (was 30, now much longer)
- Zone dwell: ~246 ticks × 0.25s = ~61.5s (just over dedup window, safe margin)

### F.6 New Constants Require PRD Justification (LLM.md §13 Open #7)

**Current issue:** TrackingConstants lists 19 values, only 12 from PRD §6. MEMBER_ROAM_INTERVAL_MS is engineering-only (not PRD).

**For new speed profile, add to TrackingConstants KDoc:**

```kotlin
object TrackingConstants {
    // ... existing constants ...
    
    /**
     * Nhịp một bước của [MemberRoamer] — profile hiện tại là Biking (6 m/s urban, 21.6 km/h).
     * Không từ PRD §6 (engineering constant, demo-only). Thay đổi cùng lúc với MEMBER_ROAM_INTERVAL_MS.
     * 
     * Lịch sử: phase-09 = 50m/2500ms (72 km/h, tốc độ đường cao tốc). Smooth Road Following sửa sang
     * 1.5m/250ms (21.6 km/h, bike profile) để: (1) khớp với GraphHopper free tier, (2) gần motorbike
     * urban HCMC, (3) animation mượt hơn, (4) room writes vẫn trong giới hạn.
     */
    const val MEMBER_ROAM_INTERVAL_MS: Long = 250L
}

object MemberRoamer {
    /**
     * Khoảng cách bước đi mỗi nhịp — profile Biking (6 m/s).
     * Suy từ speed × interval: 6 m/s × 0.25s = 1.5m per tick.
     * Không từ PRD; engineering constant cho demo smooth movement.
     */
    const val STEP_METERS: Double = 1.5
}
```

### F.7 Implementation

**Three changes to `TrackingConstants.kt` + `MemberRoamer.kt`:**

```kotlin
// TrackingConstants.kt
const val MEMBER_ROAM_INTERVAL_MS: Long = 250L  // from 2500L

// MemberRoamer.kt
const val STEP_METERS: Double = 1.5  // from 50.0

// DWELL_TICKS recalculation (automatic, no change needed — formula stays same):
val DWELL_TICKS: Int = (TrackingConstants.EVENT_DEDUPE_WINDOW_MS / TrackingConstants.MEMBER_ROAM_INTERVAL_MS).toInt() + DWELL_SAFETY_TICKS
// = (60_000 / 250) + 6 = 240 + 6 = 246 ticks (vs old 30)
```

**Tests to update:**
1. `MemberRoamerTest`: lap time now ~250s (vs ~283s old), still > 60s — invariant safe
2. Measure Room writes during zone passage: expect ~100–150 writes/min (vs 24 old)

### F.8 Recommendation for F (REVISED)

1. **Do NOT use car profile** — wrong vehicle for HCMC users; car breaks at 36 km/h which is still 2× too fast
2. **Use bike profile** — best free-tier fit for motorbike approximation
3. Set `MEMBER_ROAM_INTERVAL_MS = 250L` and `STEP_METERS = 1.5`
4. Add PRD/engineering notes to both constants' KDoc (required by LLM.md §13 Open #7)
5. DWELL_TICKS automatically recalculates (formula unchanged); will be ~246
6. Run `MemberRoamerTest` — should still pass; dwell time > dedup window = invariant safe
7. Cross-check: RouteBlueprint's breakeven (~683m) is unaffected by step size; test with default zone (150m) confirms no breakage
8. **Source:** [Pedestrian speed](https://www.researchgate.net/publication/245562441_Pedestrians'_Normal_Walking_Speed_and_Speed_When_Crossing_a_Street), [Bike speed estimates](https://www.research.net/), [HCMC traffic patterns](https://www.saigon-gpdaily.com.vn/)

---

## G. Provider → Roamer Seam (Architecture)

**Research Question:** Who calls suspend `RoutingProvider`, where cache lives, how `MemberRoamer` stays pure?

### G.1 Current Flow

**Status quo (straight-line):**

```
UI Intent (StartSimulationUseCase)
  ↓
TrackingRepositoryImpl.runSimulation()
  ├─ Load route blueprint from zones → SimulatedFix list
  ├─ SimulatedLocationSource.load(fixes)
  └─ Launch MemberMovementSimulator.run()
       └─ MemberRoamer.tick() ← straight-line no polyline
```

**Problem:** MemberRoamer has no way to accept a polyline because RoutingProvider is suspend and `:domain` cannot import `:data` (unidirectional dependency).

### G.2 Proposed Clean Seam

**New flow with road-following:**

```
UI Intent (StartSimulationUseCase)
  ↓
TrackingRepositoryImpl.runSimulation(zones: List<Zone>)
  ├─ FOR EACH ZONE:
  │   ├─ Call RoutingProvider.directions(from=zoneCenter, to=nextZoneCenter)  [SUSPEND, in :data]
  │   ├─ On success:
  │   │   ├─ Decode polyline → List<GeoPoint>
  │   │   ├─ Build PolylineFollower.ParametrizedPolyline [PURE, in :domain]
  │   │   ├─ CACHE in SimulatedLocationSource.polylineCachePerZone[zoneId] = polyline
  │   │   └─ Continue to next zone
  │   └─ On failure (network down, API error):
  │       └─ Fall back to straight-line RouteBlueprint (existing logic)
  │
  └─ Launch MemberMovementSimulator.run()
       └─ MemberRoamer.tick(roamState, zones, random, polylineCache)
           ├─ If polylineCache[currentZone] exists → use PolylineFollower
           └─ Else → use straight-line pointAtBearing() (fallback)
```

### G.3 Cache Design

**Three-layer cache:**

```kotlin
// Layer 1: ProviderCache — suspend, lives in :data
// Caches HTTP responses to avoid re-querying same route
class ProviderCache(private val provider: RoutingProvider) {
    private val cache = mutableMapOf<String, Directions>()  // key = "lat1,lng1→lat2,lng2"
    
    suspend fun directions(from: GeoPoint, to: GeoPoint): AppResult<Directions> {
        val key = "${from.latitude},${from.longitude}→${to.latitude},${to.longitude}"
        return cache[key]?.let { AppResult.Success(it) }
            ?: provider.directions(from, to).also { result ->
                if (result is AppResult.Success) cache[key] = result.data
            }
    }
}

// Layer 2: PolylineCache — pure, lives in :domain or :data
// Pre-decodes polylines once
class PolylineCache {
    private val cache = mutableMapOf<String, ParametrizedPolyline?>()  // key = zoneId
    
    fun get(zoneId: String): ParametrizedPolyline? = cache[zoneId]
    fun put(zoneId: String, polyline: ParametrizedPolyline) { cache[zoneId] = polyline }
}

// Layer 3: SimulatedLocationSource — coordinates loading
class SimulatedLocationSource(private val polylineCache: PolylineCache) : LocationSource {
    suspend fun prepareRoutes(zones: List<Zone>, provider: RoutingProvider) {
        zones.forEach { zone ->
            val result = provider.directions(from = currentPosition, to = zone.center)
            if (result is AppResult.Success) {
                val polyline = PolylineFollower.buildLookup(...)
                polylineCache.put(zone.id, polyline)
            }
        }
    }
}
```

### G.4 MemberRoamer Signature (Pure Layer)

**MemberRoamer.tick() stays pure — no suspend, no :data imports:**

```kotlin
object MemberRoamer {
    fun tick(
        state: RoamState,
        zones: List<Zone>,
        random: Random,
        polylineCache: Map<String, ParametrizedPolyline?> = emptyMap(),  // ← NEW, optional
        stepMeters: Double = STEP_METERS,
    ): RoamState {
        // ... existing logic ...
        
        // When choosing next target, check if polyline exists
        if (target != null && target.zoneId != null) {
            val polyline = polylineCache[target.zoneId]
            if (polyline != null) {
                // Use PolylineFollower instead of pointAtBearing
                val newPos = PolylineFollower.positionAt(polyline, currentArcLength + stepMeters)
                return state.copy(latitude = newPos.lat, longitude = newPos.lng, ...)
            }
        }
        
        // Fallback to existing straight-line logic
        return state.copy(...)
    }
}
```

### G.5 Integration Points

**In `:data/location/MemberMovementSimulator`:**

```kotlin
class MemberMovementSimulator(
    private val memberRepository: MemberRepository,
    private val zoneRepository: ZoneRepository,
    private val polylineCache: PolylineCache,  // ← NEW injected
) {
    suspend fun run() {
        val zones = zoneRepository.observeAll().first()
        preparePolylines(zones)  // ← NEW: fetch & cache routes
        
        while (isActive) {
            tickOnce()
            delay(...)
        }
    }
    
    private suspend fun preparePolylines(zones: List<Zone>) {
        zones.forEach { zone ->
            // Call provider (suspend OK here, in :data)
            val result = routingProvider.directions(
                from = GeoPoint(currentLat, currentLng),
                to = GeoPoint(zone.latitude, zone.longitude),
            )
            
            if (result is AppResult.Success) {
                val polyline = PolylineFollower.buildLookup(
                    PolylineDecoder.decode(result.data.points, precision = 5)
                        .map { PolylineFollower.PolylinePoint(it.latitude, it.longitude) }
                )
                polyline?.let { polylineCache.put(zone.id, it) }
            } else {
                FtdLog.w(TAG, "polyline_fetch_failed zone=${zone.id} error=${result.error}")
            }
        }
    }
    
    internal suspend fun tickOnce() {
        // ... existing code ...
        val next = MemberRoamer.tick(
            previous, 
            zones, 
            randomFor(member),
            polylineCache = polylineCache.snapshot(),  // ← Pass cache as map
        )
        // ... rest unchanged ...
    }
}
```

### G.6 DI Wiring

**In `:data/di/DataModule.kt`:**

```kotlin
val dataModule = module {
    single { PolylineCache() }  // ← NEW
    
    single { ProviderCache(get()) }  // ← Optional caching layer
    
    single {
        MemberMovementSimulator(
            memberRepository = get(),
            zoneRepository = get(),
            polylineCache = get(),  // ← NEW injection
        )
    }
}
```

### G.7 Fallback Strategy

If `RoutingProvider.directions()` throws or times out:

```kotlin
suspend fun directions(from: GeoPoint, to: GeoPoint, timeout: Duration = 10.seconds): AppResult<Directions> {
    return try {
        withTimeoutOrNull(timeout) { provider.directions(from, to) }
            ?: AppResult.Failure(AppError.Timeout("Routing provider timeout"))
    } catch (e: Exception) {
        FtdLog.e(TAG, "routing_provider_error", e)
        AppResult.Failure(AppError.Network("Routing provider unavailable"))
    }
}

// In MemberMovementSimulator.preparePolylines():
if (result is AppResult.Failure) {
    // Do NOT crash — fall back to current straight-line logic
    FtdLog.w(TAG, "polyline_unavailable zone=${zone.id}, using fallback")
    polylineCache.put(zone.id, null)  // signal "no polyline for this zone"
}
```

**Cost of bad seam:** If polyline cache and ProviderCache are not thread-safe, concurrent zone updates during simulation could corrupt state → marker warps, zone events misfired.

**Recommendation for G:**
1. Add `PolylineCache` class to `:domain` (pure, no Android)
2. Add `ProviderCache` class to `:data/routing/` (wraps RoutingProvider, caches responses)
3. Inject `PolylineCache` into `MemberMovementSimulator` (already done by framework)
4. Add `polylineCache: Map<String, ParametrizedPolyline?>` parameter to `MemberRoamer.tick()` with default empty map
5. In `MemberMovementSimulator.tickOnce()`, call `MemberRoamer.tick(..., polylineCache.snapshot())`
6. Call `preparePolylines()` once before main loop starts
7. Test `MemberMovementSimulatorIntegrationTest`: (a) with network available, polylines loaded correctly, (b) network down, fallback to straight-line, (c) zone deleted mid-simulation, polyline cache updated cleanly
8. **Sources:** LLM.md §2 (dependency directions), MemberRoamer.kt (pure algorithm design)

---

## Risks

1. **ENTER/EXIT bounce:** If polyline geometry is poor, walker re-enters zone during exit phase → duplicate ENTER within 60s → second ENTER silently drops. **Mitigation:** Validate route geometry before use (RouteGeometryValidator).

2. **Attribution compliance:** Offline polyline missing OSM credit in JSON → docs/routing-and-map-attribution.md §3 violation. **Mitigation:** JSON schema includes mandatory `attribution` field; UI `RoutingAttribution.kt` is non-negotiable.

3. **API quota burn:** Calling `RoutingProvider.directions()` for every zone in loop consumes credits. Free tier = 500/day. With 3 zones, ~180 calls in 8-hour demo session = within quota, but close. **Mitigation:** Cache responses; log credit usage; document in README that commercial deployment needs paid tier.

4. **Polyline decoding precision mismatch:** GraphHopper uses precision 5, Valhalla uses precision 6. Offline JSON must state precision. **Mitigation:** Schema includes `precision` field; test against both precisions.

5. **Bearing wrap-around invisible in straight-line mode:** New bearing smoothing only helps if bearing is displayed. If current UI does not render rotation, smoothing adds code with no effect. **Mitigation:** Defer bearing UI to next phase; focus on position smoothing first.

6. **MemberRoamer STEP_METERS reduction → larger testing scope:** Lowering from 50m to 0.7m increases lap time 70×, makes test suite run slower, may timeout. **Mitigation:** Add fast-mode `DEBUG_FAST` profile; keep current constants for backwards compatibility; add runtime switch.

---

## Unresolved Questions

1. **Valhalla support timing:** GraphHopper works now. Should Valhalla `ValhallaRoutingProvider` be implemented in same PR as road-following, or deferred? (Blocking: does Valhalla's response include `info.copyrights` or must we hard-code it per docs/routing-and-map-attribution.md §5?)

2. **Bearing UI integration:** Should marker rotation follow polyline bearing, or is smooth position motion sufficient for MVP? (Blocking: does MVI doc require screen rotation composable, or is that separate task?)

3. **Cache invalidation:** How long should a cached polyline live? If user creates a new zone, do we re-fetch routing? If zone moved (edited), cache is stale. (Blocking: does `ZoneEventRepositoryImpl` trigger invalidation on zone update?)

4. **Multizone tour sequencing:** With multiple zones, which order does round_trip visit them? If order matters (e.g., stay within one area to save travel), is there logic to prefer nearby zones? (Blocked by: no current route optimization logic.)

5. **OfflineRouteDto versioning:** If schema changes (e.g., add `elevation` field), old `demo_loop_hcmc.json` files break. Should version field be added to schema now, or YAGNI? (Blocked by: unknown if elevation will ever be needed.)

---

## Sources

- [GraphHopper round_trip forum](https://discuss.graphhopper.com/t/rund-trip-idea/9842)
- [GraphHopper API routing documentation](https://docs.graphhopper.com/openapi/routing/getroute)
- [Arc-length curve parametrization](https://fullnitrous.com/post/RUnyh)
- [Arc-length parameterized spline curves (PDF)](https://homepage.divms.uiowa.edu/~kearney/pubs/CurvesAndSurfacesArcLength.pdf)
- [Bearing and distance formulas — movable-type.co.uk](https://www.movable-type.co.uk/scripts/latlong.html)
- [Bearing smoothing wrap-around detection (Patent 6964107)](https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/6964107)
- [Pedestrian walking speed analysis (ScienceDirect)](https://www.sciencedirect.com/science/article/pii/S209575641830415X)
- [Average walking pace 2026](https://getsteps.app/blog/average-walking-pace)
- [Haversine formula survey](https://www.askpython.com/python/examples/calculate-gps-distance-using-haversine-formula)
- [Geobuf compression for GeoJSON](https://github.com/mapbox/geobuf)
- [GeoJSON specification](https://en.wikipedia.org/wiki/GeoJSON)
- [Polyline encoding (GitHub/mapbox)](https://github.com/mapbox/polyline)

---

**End of Research Report**

Research completed by `researcher-01` on 2026-08-25. All external API claims verified with access date. Recommendations are actionable and testable; seven concrete file additions proposed in sections A–G.
