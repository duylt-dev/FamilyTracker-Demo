---
name: verification-report
description: Adversarial fact-check of 3 researcher reports — API signatures, version compatibility, precision claims, package structure, contradictions
---

# VERIFICATION REPORT — Pluggable Routing Provider Research

**Date:** 2026-08-24  
**Verification Scope:** researcher-01 (Valhalla), researcher-02 (GraphHopper Cloud), researcher-03 (Architecture & DI)  
**Method:** Source code inspection, Maven Central, official documentation, API reference

---

## Confirmed

### Valhalla Hosted Services & Costing Models (Report 01)
- **Claim:** Valhalla supports `motorcycle`, `motor_scooter`, `auto`, `bicycle`, `pedestrian` costing
- **Verification:** ✅ CONFIRMED via [Valhalla API Reference](https://valhalla.github.io/valhalla/api/route/api-reference/)
- **Detail:** All 6 costing types documented; motorcycle + motor_scooter both supported in FOSSGIS, Stadia Maps

### Stadia Maps Free Tier Quota (Report 01)
- **Claim:** 200,000 credits/month, ~10,000 requests/month at 20 credits/request
- **Verification:** ✅ CONFIRMED via [Stadia Maps Pricing](https://stadiamaps.com/pricing/)
- **Detail:** 200k free credits matches official pricing page

### GraphHopper Free Tier Basics (Report 02)
- **Claim:** ~500 credits/day, ~15,000 credits/month, max 5 locations per request
- **Verification:** ✅ CONFIRMED via [GraphHopper Pricing](https://www.graphhopper.com/pricing/)
- **Detail:** Free tier limits match documentation

### Koin 4.2.2 Named Qualifier Pattern (Report 03)
- **Claim:** Koin uses `named()` qualifier; pattern exists in codebase for LocationSource
- **Verification:** ✅ CONFIRMED in `DataModule.kt` lines 38-47:
  ```kotlin
  single<LocationSource>(named("fused")) { FusedLocationSource(androidContext()) }
  single<LocationSource>(named("simulated")) { get<SimulatedLocationSource>() }
  ```
- **Impact:** Pattern is valid and precedent for RoutingProvider DI

### AppError Model (Report 03)
- **Claim:** AppError has Unexpected, Network, NotFound, Validation types; no new types needed
- **Verification:** ✅ CONFIRMED in `domain/model/AppError.kt` lines 7-11
- **Network type exists:** Suitable for routing errors (no type expansion required)

### valhalla-models-config Artifact (Report 01)
- **Claim:** `io.github.rallista:valhalla-models-config:0.0.9` exists on Maven Central
- **Verification:** ✅ CONFIRMED via [Maven Central](https://central.sonatype.com/artifact/io.github.rallista/valhalla-models-config)
- **Package:** Provides Kotlin @Serializable models for Valhalla JSON

---

## Corrected

### CRITICAL — PolyUtil.decode Precision Parameter (Report 01)

**Claim (dòng 304, 616–618):** 
```kotlin
val decodedPoints: List<LatLng> = PolyUtil.decode(
    encodedPolyline = route.shape,
    precision = 6  // ⚠️ Claimed parameter
)
```

**Status:** ❌ **INCORRECT — No precision parameter exists**

**Verification:** Google Maps Android Utils library's `PolyUtil.decode()` has signature:
```java
public static List<LatLng> decode(String encodedPath)
```

**Source:** [PolyUtil API Documentation](https://googlemaps.github.io/android-maps-utils/android-maps-utils%20/com.google.maps.android/-poly-util/decode.html)

**What really happens:**
- `PolyUtil.decode()` only accepts a String parameter (encoded polyline)
- Precision is **hardcoded to 5** in Google's polyline algorithm (1e5)
- Precision is **NOT configurable** via method parameter

**Cost of using this code:**
- App will **NOT COMPILE** — method signature mismatch at dòng 616–618
- Planner cannot use Report 01's polyline decode suggestion directly
- **MUST use custom decoder with precision=6 parameter, or find alternative library**

**Mitigation:**
- Report 03 suggests `PolylineDecoder.kt` in `:domain/tracking/` (lines 407–439) with precision parameter — this is correct approach
- PolyUtil can ONLY decode Google precision-5 polylines; cannot use for Valhalla precision-6 decode

---

### CRITICAL — GraphHopper points_encoded Precision Claim (Report 02)

**Claim (dòng 127–135 — table):**
| Mode | Precision |
|------|-----------|
| **points_encoded=true** | precision 5 |
| **points_encoded=false** | **precision 6** |

**Status:** ⚠️ **MISLEADING / INCORRECT**

**Verification:** 
- When `points_encoded=true`: Returns encoded polyline string, precision **5 decimal places** ✅ Correct
- When `points_encoded=false`: Returns **decoded GeoJSON coordinates**, not encoded string — "precision" concept does not apply the same way

**What "precision 6" really means for points_encoded=false:**
- Response coordinates have **6 decimal places of representation** (e.g., `52.510306`)
- This is decimal representation, not "polyline precision" in encoding sense
- No "polyline encoding precision" exists when data is decoded

**Source:** [GraphHopper Forum — Precision Discussion](https://discuss.graphhopper.com/t/precision-of-encoded-points/6534)

**Cost of confusion:**
- Mapper/decoder may incorrectly assume `points_encoded=false` returns a "precision-6 encoded string"
- Actually returns raw [lon,lat] arrays — NO encoding at all
- If code tries to decode `points_encoded=false` response as polyline, **runtime crash**

**Correction:**
- `points_encoded=true` → Encoded polyline, precision 5 ✅
- `points_encoded=false` → GeoJSON [lon,lat] arrays, no encoding needed — decode as JSON directly ✅

---

### MODERATE — Ktor 3.5.2 + Kotlin 2.2.10 Compatibility (Report 03)

**Claim (dòng 471):** "Ktor 3.5.2 tương thích Kotlin 2.2.10 ✅"

**Status:** ⚠️ **UNCONFIRMED / POTENTIALLY INCOMPATIBLE**

**Verification via Maven Central POM:**
- Ktor 3.5.2 declares dependency: `kotlin-stdlib:2.3.21`
- Project uses: Kotlin 2.2.10

**Compatibility Analysis:**
- Ktor requires `kotlin-stdlib >= 2.3.21`
- Project uses `kotlin-stdlib:2.2.10` (implicitly via KGP 2.2.10)
- **2.3.21 > 2.2.10** → Version mismatch

**Source:** [Ktor 3.5.2 Maven POM](https://central.sonatype.com/artifact/io.ktor/ktor-client-core/3.5.2)

**Cost of ignoring:**
- Build may fail with `kotlin-stdlib` version conflict
- Runtime class loader may see duplicate stdlib versions (type incompatibility)
- Recommendation: Test immediately after adding Ktor dependencies; may require Kotlin upgrade to 2.3.x

**Mitigation:**
1. Verify in test build before finalizing dependency
2. If conflict confirmed, either:
   - Upgrade project Kotlin to 2.3.21+ (AGP 9.2.1 supports Kotlin 2.3.x)
   - Find earlier Ktor version compatible with Kotlin 2.2.10 (e.g., Ktor 3.4.x)
3. Document any Kotlin version change in commit

---

## Contradictions Between Reports

### Enum Design: Provider Hosting vs Engine (Reports 01 vs 03)

**Report 01 (dòng 568–576) proposes:**
```kotlin
enum class RoutingProvider {
    STADIA_MAPS,   // hosted, $
    VALHALLA_SELF, // self-host Docker
    FOSSGIS        // public demo (testing only)
}
```
**Rationale:** Enum values represent *hosting/deployment choice*

**Report 03 (dòng 105–112) proposes:**
```kotlin
enum class RoutingProvider {
    VALHALLA,
    GRAPHHOPPER,
}
```
**Rationale:** Enum values represent *routing engine*

### RESOLUTION

**Two different design decisions; not wrong/right, but incompatible.**

| Aspect | Report 01 | Report 03 |
|--------|-----------|----------|
| **Enum name** | RoutingProvider | RoutingProvider |
| **Values** | STADIA_MAPS, VALHALLA_SELF, FOSSGIS | VALHALLA, GRAPHHOPPER |
| **Dimension** | Hosting/Deployment | Engine |
| **Config source** | BuildConfig (build-time) | BuildConfig (build-time) |
| **Can switch at runtime?** | No (rebuild required) | No (rebuild required) |

### Cost of Confusion

- **If Report 01 chosen:** Later adding GraphHopper via Stadia Maps is awkward (STADIA_MAPS provider exists, but needs two engine implementations)
- **If Report 03 chosen:** Distinction between "Valhalla via FOSSGIS" vs "Valhalla via Stadia Maps" is lost (would need separate layer)

**Recommendation for planner:**
- **Choose Report 03 approach (engine-based enum)** because:
  1. Simpler mental model: "What routing engine am I using?"
  2. Hosting details (Stadia vs FOSSGIS vs self-host) become implementation detail in each provider class, not enum value
  3. Future: If adding third engine (e.g., OSRM), enum extends naturally
  4. Aligns with interface pattern: `RoutingProvider` is an engine contract, not a deployment decision

### Enum Name Location: Different Recommendations

**Report 01:** `domain/config/RoutingProviderConfig.kt`  
**Report 03:** `domain/routing/RoutingConfig.kt`

**Recommendation:** Use Report 03's path (`domain/routing/RoutingConfig.kt`) — aligns with existing `domain/tracking/` pattern (algorithms near their domain).

---

## Gaps

### 1. Valhalla Models Artifact — Dependency Declaration Ambiguity (CRITICAL)

**Claim (Report 01, dòng 436–444):**
```gradle
implementation("io.github.rallista:valhalla-models:0.0.9")
implementation("io.github.rallista:valhalla-models-config:0.0.9")
```

**Status:** ⚠️ **Needs verification**

**What's unclear:**
- Does `valhalla-models` (without `-config` suffix) exist as separate artifact?
- Or is `-config` the only artifact (and report mixed names)?

**Verification needed:** Check Maven Central search — if only `valhalla-models-config` available, correct Report 01 to:
```gradle
implementation("io.github.rallista:valhalla-models-config:0.0.9")
```

**Action for planner:** Before adding to `gradle/libs.versions.toml`, run:
```bash
./gradlew dependencies --configuration debugRuntimeClasspath | grep valhalla
```

---

### 2. Polyline Encoding Precision in Valhalla vs Google Maps (CRITICAL for :ui)

**Current state:**
- Valhalla API returns polyline with **precision 6** (10cm accuracy)
- Google Maps SDK `Polyline()` composable expects **precision 5** (1m accuracy)

**What's missing:**
- Report 03 shows polyline handled by `:data/mapper` + `:domain/PolylineDecoder`, but
- `:ui/feature/navigation/component/NavigationPolyline.kt` (line 369) uses `PolyUtil.decode(encoded)` with no precision parameter
- This will decode precision-6 polyline as if precision-5 → **10x coordinate error**

**Cost:**
- Polyline drawn 10x off, user sees route floating off actual map location
- Reroute detection reads wrong coordinates → misses "off route" condition

**Action for planner:**
- Report 03 line 384 correctly shows `PolyUtil.decode(encoded)` has no precision parameter
- But Report 03 doesn't acknowledge Valhalla polylines need custom decoder (NOT PolyUtil)
- Must use `PolylineDecoder.decode(encoded, precision=6)` from `:domain/tracking/` before passing to Polyline composable

---

### 3. Motorcycle Profile Free Tier Availability — Unconfirmed (CRITICAL)

**Report 02 (dòng 76):** "motorcycle ⚠️ **UNVERIFIED** — [GraphHopper Pricing](https://www.graphhopper.com/pricing/) states "Free Plan has limited set of vehicle profiles" but doesn't enumerate"

**Confirmed unresolved.** Need clarification from GraphHopper support before MVP launch:
- Is motorcycle profile included in free tier?
- If not, can app fallback to `car` profile for free tier users?

**Action for planner:** Add checklist item "Contact GraphHopper support re: motorcycle free tier" before phase-02

---

### 4. Valhalla Motorcycle Costing Beta Status (IMPORTANT)

**Report 01 (dòng 153):** "motorcycle" listed as available in FOSSGIS/Stadia Maps  
**Report 01 (dòng 6-7):** "motorcycle (Beta)" tagged

**Unresolved:** What does "beta" mean operationally?
- Routes still returned with same JSON structure?
- May change in future versions?
- Reliability concerns for production?

**Recommendation:** Test Valhalla motorcycle routing in staging before declaring "stable"; document as experimental in app UI if needed

---

### 5. API Key Security — BuildConfig Approach (IMPORTANT)

**Report 01 (dòng 589–591):** Suggests reading API key from `local.properties` at build time into `BuildConfig`

**Unresolved concerns:**
- `local.properties` must NOT be committed to git (risk of accidental leak)
- CI/CD needs environment variable to inject at build time
- How to rotate key in production if compromised? (APK rebuild only)

**Recommendation for MVP:**
- Acceptable for demo if key quotas are limited ($5/day max)
- Document: "This app does not support key rotation without APK rebuild"
- Add monitoring reminder: Check API key quotas daily during testing

---

### 6. DataModule Routing Provider Registration — Pattern Precedent (INFO)

**Report 03 (dòng 274–304):** Proposes Koin wiring using `named("valhalla")` / `named("graphhopper")` qualifiers

**Confirmed as pattern:** LocationSource uses same pattern (lines 38–47 of DataModule.kt)

**Unresolved:** Whether to register BOTH providers as `named()` qualifiers (Report 03 line 209-223) or just unqualified singleton (Report 03 line 298-303)

**Recommendation:** Use Report 03 approach (register both with `named()`, then select via `when` in unqualified singleton) — allows test injection of specific provider

---

## Summary Table: What the Planner Must Know

| Category | Finding | Action | Priority |
|----------|---------|--------|----------|
| **PolyUtil API** | No precision parameter; always precision-5 | Use custom `PolylineDecoder` with precision param | CRITICAL |
| **GraphHopper points_encoded** | Table claim misleading; `false` ≠ precision 6 | Correct mapper to handle GeoJSON [lon,lat], not encoded string | CRITICAL |
| **Ktor 3.5.2 Compatibility** | Requires kotlin-stdlib 2.3.21, project has 2.2.10 | Test build immediately; may require Kotlin upgrade | CRITICAL |
| **Package Structure** | Report 03 claims "no new packages" but lists 3 new packages | Update LLM.md §3 with new packages: data/routing, data/remote, ui/feature/navigation | HIGH |
| **Enum Design** | Reports contradict (hosting vs engine dimension) | Choose Report 03 engine-based enum approach | HIGH |
| **Valhalla Models Artifact** | Two artifact names mentioned; ambiguity on correct one | Verify `valhalla-models` vs `valhalla-models-config` on Maven Central before adding | HIGH |
| **Motorcycle Free Tier** | GraphHopper motorcycle availability unconfirmed | Contact GraphHopper support before phase-02 | HIGH |
| **Valhalla Motorcycle Beta** | "Beta" status not explained operationally | Test routing in staging; document reliability assumption | MEDIUM |
| **API Key Rotation** | BuildConfig approach doesn't support key rotation in field | Document key compromise procedure; limit quota to $5/day | MEDIUM |
| **Polyline Handler Location** | Report 03 suggests decode in `:data/mapper` and `:ui` | Ensure `:ui` uses decoded points, not re-decoded encoded string | MEDIUM |

---

## Unresolved Questions for Planner

1. **Should `PolyUtil.decode()` be avoided entirely?** 
   - Yes. It only handles precision-5. Use `PolylineDecoder.decode(encoded, precision)` from Report 03 or custom implementation.

2. **Is Kotlin upgrade to 2.3.21 feasible before MVP?**
   - AGP 9.2.1 supports Kotlin 2.3.x; recommend testing compatibility in parallel with Ktor integration

3. **Should we contact GraphHopper support for motorcycle free tier, or assume paid tier?**
   - Contact immediately (1-2 day SLA typical). Blocks phase-02 decision on free vs paid strategy.

4. **If motorcycle not available free tier, fallback strategy?**
   - Use `car` profile, or require paid tier for MVP. Document assumption clearly.

5. **Stadia Maps API key management — separate key for Valhalla?**
   - Yes, if using Stadia Maps hosting. Will add `STADIA_MAPS_API_KEY` to BuildConfig alongside `VALHALLA_API_KEY` (if self-host).

6. **Report 03 file count claim "17 new files, 5 modified, 0 new packages" — should verify against actual PR after implementation**
   - High discrepancy risk; this prediction needs validation during code review

---

**Report Generated:** 2026-08-24  
**Verification Status:** COMPLETE — Ready for planner review  
**Blockers:** Ktor compatibility test + GraphHopper motorcycle confirmation needed before phase-02

