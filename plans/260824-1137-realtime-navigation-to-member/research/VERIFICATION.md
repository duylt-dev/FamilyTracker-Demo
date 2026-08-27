---
name: verification-report
description: Adversarial fact-check of 5 researcher reports — versions, APIs, file paths, contradictions, gaps
---

# VERIFICATION REPORT — Real-time Navigation Feature Research

**Date:** 2026-08-24  
**Verification Scope:** All 5 researcher reports (01–05)  
**Method:** Direct source verification, Maven Central checks, official documentation, codebase inspection

---

## Confirmed

### API & Deprecation Status (Report 01)
- **Claim:** Android-restricted API keys cannot call web service REST APIs
- **Verification:** ✅ CONFIRMED by [Google API Security Best Practices](https://developers.google.com/maps/api-security-best-practices)
- **Quote:** "If you place an application restriction on an API key, you cannot use it on other platforms. For example, if you restrict the API key to only Android apps, you cannot use it with iOS, web services, or JavaScript APIs."

### Maps Compose (Report 03)
- **Claim:** maps-compose 8.3.1 has no breaking changes
- **Verification:** ✅ CONFIRMED by [GitHub Release v8.3.1](https://github.com/googlemaps/android-maps-compose/releases/tag/v8.3.1)
- **Changes:** Only bug fixes (#935 focus management, #933 color scheme toggle); no breaking changes

### Project Build Config (Report 02)
- **Claim:** kotlin-serialization plugin already in libs.versions.toml at line 64
- **Verification:** ✅ CONFIRMED in `gradle/libs.versions.toml` line 64: `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`

### Project Build Type (App build.gradle.kts)
- **Claim (Report 02):** "Release build type has R8 optimization disabled"
- **Verification:** ✅ CONFIRMED in `app/build.gradle.kts` line 55: `optimization { enable = false }`

### Existing Files
- **Claim (Report 05):** MviViewModel.kt exists at specified path
- **Verification:** ✅ CONFIRMED at `./ui/src/main/java/com/example/pion/family/tracker/demo/ui/core/mvi/MviViewModel.kt`
- **Claim (Report 05):** TrackingConstants.kt exists
- **Verification:** ✅ CONFIRMED at `./domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/tracking/TrackingConstants.kt`
- **Claim (Report 05):** MemberMovementSimulator.kt exists with MEMBER_ROAM_INTERVAL_MS
- **Verification:** ✅ CONFIRMED at `./data/src/main/java/com/example/pion/family/tracker/demo/data/location/MemberMovementSimulator.kt`
- **Claim (Report 05):** LocationTrackingService.kt exists
- **Verification:** ✅ CONFIRMED at `./data/src/main/java/com/example/pion/family/tracker/demo/data/location/LocationTrackingService.kt`

---

## Corrected

### INTERNET Permission (Report 02)
- **Claim:** "INTERNET permission must be added to AndroidManifest.xml"
- **Status:** ❌ **CORRECTION: Permission does NOT exist currently**
- **Current AndroidManifest.xml:** Declared permissions are only:
  - ACCESS_COARSE_LOCATION
  - ACCESS_FINE_LOCATION
  - ACCESS_BACKGROUND_LOCATION
  - POST_NOTIFICATIONS
  - FOREGROUND_SERVICE
  - FOREGROUND_SERVICE_LOCATION
- **Action Required:** Add `<uses-permission android:name="android.permission.INTERNET" />` before implementing routing calls
- **Source:** `/Users/macbook/Desktop/WorkSpace/MobileDev/Android/Organization/Pion/FamilyTrackerDemo/app/src/main/AndroidManifest.xml` (lines 13–18, missing on line 19)

### Ktor 3.5.2 Kotlin Compatibility (Report 02)
- **Claim:** "Ktor 3.5.2 requires Kotlin 1.9.20+; Project uses Kotlin 2.2.10 ✅ (compatible)"
- **Status:** ⚠️ **UNVERIFIED — No official source found for minimum Kotlin version**
- **What we know:** 
  - Ktor 3.5.2 exists on Maven Central ✅
  - Project uses Kotlin 2.2.10 ✅
  - Ktor 3.5.2 pom.xml lists `kotlin-stdlib:2.3.21` as dependency
- **Risk:** Minimum Kotlin requirement claim cannot be confirmed; recommend testing in build
- **Mitigation:** Add explicit version constraint in libs.versions.toml; run build immediately after adding Ktor dependencies to catch compatibility issues early

### Android Maps Utils Version (Report 03)
- **Claim:** "android-maps-utils-core:5.1.1"
- **Status:** ❌ **UNVERIFIED — Version 5.1.1 not confirmed to exist**
- **What we know:**
  - Maven Central shows android-maps-utils-core 6.0.0-rc01 available
  - Version 5.1.1 not explicitly listed in available versions search
- **Action Required:** Verify correct version before adding to gradle/libs.versions.toml; consider 6.0.0-rc01 as alternative or confirm 5.1.1 availability via Maven Central UI
- **Why it matters:** Wrong version number causes build failure immediately

### Directions API vs Routes API Deprecation Dates (Report 01)
- **Claims:**
  - "Directions API deprecated as of 2026-02-25"
  - "Routes API fully replaces both as of 2025-03-01"
- **Status:** ⚠️ **UNVERIFIED — No official source confirms these specific dates**
- **Official Status:** Google documentation says Routes API "replaces" Directions and Distance Matrix APIs, but no explicit deprecation dates found
- **Recommendation:** Use Routes API (newer), but do NOT hard-code these dates in documentation; treat dates as "researcher's educated guess" not confirmed deadline
- **Impact:** Low if feature uses Routes API regardless

### Report 02 Network Layer Choice
- **Claim:** "Recommends Ktor 3.5.2 as HTTP client"
- **Status:** ⚠️ **NOT YET IN GRADLE** — Ktor is not in `gradle/libs.versions.toml`
- **Action:** All 5 Ktor dependencies must be added to version catalog:
  - `ktor-client-core:3.5.2`
  - `ktor-client-android:3.5.2`
  - `ktor-client-content-negotiation:3.5.2`
  - `ktor-serialization-kotlinx-json:3.5.2`
  - `ktor-client-mock:3.5.2`

---

## Contradictions Between Reports

### Where Does Reroute Logic Live? (Reports 03, 04, 05)

**Report 03** proposes:
```
:domain/tracking/OffRouteDetector.kt  (pure Kotlin haversine)
:ui/feature/map/OffRouteEvaluator.kt  (wrapper using PolyUtil.isLocationOnPath)
```
**Rationale:** :domain holds pure algorithm; :ui holds PolyUtil wrapper

**Report 04** proposes:
```
:domain/tracking/RerouteEvaluator.kt  (complete reroute logic with state machine)
```
**Rationale:** All routing decisions in pure :domain, with equirectangular projection algorithm

**Report 05** proposes:
```
:data/location/RerouteDetector.kt  (stateless utility)
```
**Rationale:** :data layer responsibility for routing detection

### RESOLUTION
**The correct placement is `:domain/tracking/RerouteEvaluator.kt` (Report 04's approach).**

**Why:**
1. **Module dependency rules:** :ui can see :domain, but :domain cannot see :ui. Report 03's split violates this: :domain would need to know about :ui's OffRouteEvaluator
2. **Purity:** Report 04's pure-Kotlin haversine approach keeps :domain free of Android/Compose imports
3. **Testability:** All reroute logic testable in JUnit without Robolectric
4. **Contradiction cost:** If Report 05 is followed (put in :data), then :ui ViewModel cannot access reroute decision logic cleanly without crossing module boundaries

**Recommendation for planner:** Use Report 04's `RerouteEvaluator` in `:domain/tracking/RerouteEvaluator.kt` with pure haversine + equirectangular projection. Report 03's PolyUtil.isLocationOnPath is optional UI-level enhancement for map visualization, not part of core reroute decision.

### Arrival Threshold (Reports 04)
**Report 04** proposes:
- Arrival threshold: 50m
- Arrival hysteresis: 70m

**No contradiction found** — this is specified clearly. No other report provides arrival logic.

---

## Gaps

### 1. Network Security Configuration (CRITICAL for HTTPS)
**What's missing:** No `network_security_config.xml` found in project  
**Why it matters:** Routes API endpoint (`https://routes.googleapis.com/...`) is HTTPS. By default, Android 9+ (API 28+, project's minSdk) blocks cleartext HTTP. If misconfigured, app crashes with `CLEARTEXT_NOT_PERMITTED`.  
**Recommended answer:**
- No explicit config needed if ONLY calling HTTPS endpoints (Routes API is HTTPS ✅)
- Create `res/xml/network_security_config.xml` if fallback to HTTP needed (not for Routes API):
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <network-security-config>
      <domain-config>
          <domain includeSubdomains="true">routes.googleapis.com</domain>
          <trust-anchors>
              <certificates src="system" />
          </trust-anchors>
      </domain-config>
  </network-security-config>
  ```
- Link in AndroidManifest.xml: `android:networkSecurityConfig="@xml/network_security_config"`
- **Action for phase plan:** Confirm all routing endpoints are HTTPS; if yes, no config needed. If fallback to HTTP, add config.

### 2. R8/ProGuard Rules for kotlinx-serialization (CRITICAL if R8 enabled)
**What's missing:** No `proguard-rules.pro` or `proguard-rules.txt` with serialization keep rules  
**Why it matters:** 
- App uses R8 minification (NOT currently; line 55 has `optimization { enable = false }`)
- But release builds may enable R8 in future
- kotlinx-serialization needs `@Serializable` classes kept in compiled code
**Recommended answer:**
```
# proguard-rules.pro or keep section in build.gradle.kts:
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep class kotlin.serialization.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
```
- **Action for phase plan:** Since R8 is disabled now, this is low-priority. If R8 is enabled later, add these rules BEFORE enabling to avoid crashes at runtime.

### 3. Configuration Change Handling (IMPORTANT for UX)
**What's missing:** No explicit handling specified for screen orientation change mid-navigation  
**Scenario:** User starts navigation, then rotates phone. ViewModel is recreated, state is lost or restored from SavedStateHandle.  
**Recommended answer:**
- NavigationViewModel must save critical state to `SavedStateHandle`:
  ```kotlin
  private val targetMemberId: String = savedStateHandle.get<String>("memberId") ?: ""
  ```
- SavedStateHandle survives configuration change; current route polyline survives in memory if ViewModel is retained
- Polyline redraw is fast (Compose re-renders); no issue
- **Action for phase plan:** Explicitly document this in phase-03 ViewModel implementation

### 4. Foreground Service Lifecycle & Navigation Termination (IMPORTANT)
**What's missing:** No specification of what happens if LocationTrackingService is stopped while navigation is active  
**Scenario:** User stops tracking (service stops), but Navigation screen is still open. MemberMovementSimulator stops, so target member position stops updating. What should UI show?  
**Current behavior (inferred from LocationTrackingService code):**
- `familyJob` runs member simulator only if service is running
- If service stops, simulator stops → `observeLatestLocations()` stops emitting new positions → target marker freezes
- Navigation screen has no error state for this
**Recommended answer:**
- NavigationViewModel should observe tracking service status
- If service stops mid-navigation, show banner: "Tracking stopped; navigation paused"
- Allow user to restart tracking OR dismiss navigation
- Code pattern: `TrackingRepository` should expose `observeTrackingStatus(): Flow<Boolean>`
- **Action for phase plan:** Add tracking status observer to NavigationViewModel; emit UI error if tracking stops

### 5. Battery/Screen-On Behavior During Navigation (IMPORTANT for user experience)
**What's missing:** No guidance on whether app should request wake lock or prevent screen sleep during navigation  
**Current:** GPS frequency is 10 seconds (good for battery). But navigation is an "active use" case — user needs screen to see map.  
**Recommended answer:**
- DO NOT request wake lock; Android already keeps screen on while user is interacting with Navigation screen
- If user puts phone down mid-navigation (screen dims), app naturally stops rerouting when screen OFF (lifecycle pause)
- Resume when screen ON again (lifecycle resume)
- **Cost:** GPS continues in background even when screen OFF if tracking is ON — this is OK, it's the tracking service's responsibility
- **Action for phase plan:** No code changes needed; document in design that screen behavior is default Android behavior

### 6. Offline Fallback Clarification (IMPORTANT for reliability)
**What's missing:** No specification of what offline route looks like or how long it persists  
**Report 04** mentions "fallback to straight line + haversine distance" but no details on:
- How long is straight line drawn? Permanently until online again, or 5 minutes?
- Can user interact with straight-line route (e.g., tap to recenter)?
- Is straight line cached in Room or only in-memory?
**Recommended answer:**
- Straight line is drawn **in-memory only** during current session
- Persists until network returns OR user dismisses navigation
- No Room persistence of offline routes
- User CAN tap polyline to recenter camera (same as normal route)
- **Action for phase plan:** Explicitly define offline UX in phase-02 design

### 7. API Key Rotation & Security (IMPORTANT for production)
**What's missing:** No guidance on rotating Routes API key without rebuild  
**Current approach (Report 02):** Separate `ROUTES_API_KEY` in `local.properties`, read at build time into `BuildConfig`  
**Problem:** If key is compromised, only option is rebuild and redeploy APK — cannot rotate key in field  
**Recommended answer for MVP:**
- Accept the risk for demo (MVP is not production)
- Set aggressive quota limits in Google Cloud Console (e.g., $5/day max)
- Monitor Cloud Console dashboard daily during testing
- Document key compromise procedure: rebuild with new key, redeploy APK
- **Production improvement (future):** Use backend proxy pattern (Option B from Report 01) to rotate key server-side without APK rebuild
- **Action for phase plan:** Document quota monitoring responsibility; add comment in code

### 8. Reroute Threshold Tuning (MODERATE — runtime adjustability)
**What's missing:** Thresholds (OFF_ROUTE_TOLERANCE_M = 45, DESTINATION_MOVED_TOLERANCE_M = 200) are hardcoded in TrackingConstants  
**Issue:** No A/B testing or user preference way to adjust thresholds without code change  
**Recommended answer:**
- MVP: Hardcoded thresholds are fine (Report 04 recommends 45m/200m with sound reasoning)
- Future enhancement: Store in Room `settings` table, allow user adjustment via Settings screen
- **Action for phase plan:** Leave hardcoded for MVP; note as future feature

### 9. Test Coverage for RerouteEvaluator Edge Cases (IMPORTANT for quality)
**What's missing:** Report 04 provides 15 test cases, but none verify:
- Floating-point precision edge cases (e.g., distance exactly 45.0000000001m)
- Polyline with duplicate consecutive points
- Polyline crossing itself (loop)
**Recommended answer:**
- Add 3 additional test cases beyond Report 04's list:
  - T16: Polyline with duplicate consecutive points → pointToSegmentDistance handles gracefully
  - T17: Polyline crosses itself (loop) → distance calculation still correct
  - T18: Float precision edge case: distance = 45.0 (boundary exactly) → should NOT trigger reroute (uses `>` not `>=`)
- **Action for phase plan:** Include in test implementation checklist

### 10. Google Routes API Pricing Verification Before Deployment (CRITICAL)
**What's missing:** Report 01 provides 2026 pricing but doesn't recommend verification step  
**Issue:** Google pricing changes; reported prices may be stale  
**Recommended answer:**
- Before first deploy to staging, open [Google Cloud Billing](https://console.cloud.google.com/billing) with actual API key
- Navigate to Maps Platform → Routes API → verify **current** pricing:
  - Essentials SKU: Should be $2/1000 (Report 01 claims)
  - Free tier: Should still be 10,000/month (Report 01 claims)
- **Cost estimate for MVP:** ~$0 if usage << 10k/month (e.g., 1 user × 1 reroute/minute × 24h = 1440 calls/day = ~0.1% of free tier)
- **Action for phase plan:** Add pre-deploy verification task: "Confirm Routes API pricing in Google Cloud Console"

---

## Summary Table: What the Planner Must Know

| Category | Finding | Action |
|----------|---------|--------|
| **Network Client** | Ktor 3.5.2 recommended; NOT YET in gradle catalog | Add all 5 Ktor entries to libs.versions.toml immediately |
| **INTERNET Permission** | Missing from AndroidManifest.xml | Add before building; error otherwise |
| **Reroute Logic Location** | Reports contradict (3 vs 4 vs 5); Report 04 is correct | Use `:domain/tracking/RerouteEvaluator.kt` per Report 04 |
| **Android Maps Utils Version** | 5.1.1 claimed; not verified; 6.0.0-rc01 available | Verify 5.1.1 or use 6.0.0-rc01 |
| **API Deprecation Dates** | Specific dates unverified; use Routes API regardless | Don't hard-code dates; Routes API is current choice |
| **Ktor Kotlin Compatibility** | Minimum version unverified; test recommended | Run build immediately after adding Ktor |
| **Network Security Config** | Not needed if only HTTPS; document if HTTP added | Confirm Routes API is HTTPS only (it is) |
| **R8 Rules** | Not needed now (R8 disabled); needed if enabled later | Don't add yet; add if R8 enabled in future |
| **Config Change Handling** | Not specified; use SavedStateHandle | Document in phase-03 |
| **Service Lifecycle** | Not specified what if tracking stops mid-nav | Add tracking status observer to ViewModel |
| **Offline Fallback** | Straight line, but lifecycle unclear | Define persistence (in-memory only) |
| **Pricing Verification** | Report recommends but doesn't mandate check | Add pre-deploy checklist item |

---

## Unresolved Questions for Planner

1. **Should off-route detection use PolyUtil.isLocationOnPath (:ui) or pure haversine (:domain)?**
   - Report 03 wants both; Report 04 wants pure haversine; Report 05 unsure
   - Decision: Use Report 04's pure haversine for core logic (testable, no Android deps). Optional: add PolyUtil wrapper in :ui for visualization only.

2. **Is android-maps-utils-core 5.1.1 the correct version or should we use 6.0.0-rc01?**
   - 5.1.1 not confirmed available; need verification before writing gradle

3. **Should Ktor's timeouts (10s connect, 30s request, 15s idle) be customized for Hanoi/HCMC latency?**
   - Report 02 provides pattern but no region-specific tuning
   - Recommendation: Use Report 02's defaults for MVP; adjust if tests show timeout issues

4. **Should navigation screen auto-resume route computation on screen rotation, or preserve in-flight request?**
   - Not specified; use SavedStateHandle to restore target member ID, but let ViewModel restart route computation

5. **When should app request user to enable tracking if navigation screen opens while tracking is OFF?**
   - Report 05 mentions this but doesn't decide: force-enable, show error, or ask dialog?
   - Recommendation: Show error dialog "Turn on tracking?" with action button

---

**Report Generated:** 2026-08-24  
**Verification Status:** COMPLETE — Ready for planner review

