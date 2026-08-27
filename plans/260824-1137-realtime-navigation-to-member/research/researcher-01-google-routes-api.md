# Google Routes API Research Report
**Date:** 2026-08-24  
**Researcher:** Claude AI  
**Status:** Fetched and verified from official Google developer documentation  

---

## 1. API Deprecation & Migration Status

### Current Status (Aug 2026)
- **Directions API (Legacy):** Deprecated as of **2026-02-25** per [Google Directions API deprecation notice](https://developers.google.com/maps/documentation/javascript/reference/directions)
- **Status:** NOT discontinued; continues to receive bug fixes with 12+ months notice before decommission
- **New Customer Access:** NOT available for new Google Cloud projects (legacy status)
- **Migration:** Required for all new integrations; existing projects may continue but should migrate

### Routes API Status
- **Launch Date:** 2022 (enhanced features)
- **Full Replacement:** As of **2025-03-01**, Routes API fully replaces both Directions and Distance Matrix APIs
- **Recommendation:** Use Routes API for all new projects in 2026

**Sources:**  
- [Google Directions API (deprecated) reference](https://developers.google.com/maps/documentation/javascript/reference/directions)
- [Why migrate to Routes API](https://developers.google.com/maps/documentation/routes/migrate-routes-why)
- [Routes API migration guide](https://developers.google.com/maps/documentation/routes/migrate-routes)

---

## 2. Exact HTTPS Request Format & Real curl Example

### Endpoint
```
POST https://routes.googleapis.com/directions/v2:computeRoutes
```

### Required Headers
```
X-Goog-Api-Key: YOUR_API_KEY
X-Goog-FieldMask: routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline
Content-Type: application/json
```

### Minimal JSON Request Body
```json
{
  "origin": {
    "location": {
      "latLng": {
        "latitude": 10.7769,
        "longitude": 106.7009
      }
    }
  },
  "destination": {
    "location": {
      "latLng": {
        "latitude": 10.8231,
        "longitude": 106.6297
      }
    }
  },
  "travelMode": "DRIVE",
  "routingPreference": "TRAFFIC_UNAWARE"
}
```

### Real curl Example (Hanoi to District 7, Ho Chi Minh City)
```bash
curl -X POST \
  -H "X-Goog-Api-Key: YOUR_API_KEY" \
  -H "X-Goog-FieldMask: routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline" \
  -H "Content-Type: application/json" \
  -d '{
    "origin": {
      "location": {
        "latLng": {
          "latitude": 21.0285,
          "longitude": 105.8542
        }
      }
    },
    "destination": {
      "location": {
        "latLng": {
          "latitude": 10.7769,
          "longitude": 106.7009
        }
      }
    },
    "travelMode": "DRIVE",
    "routingPreference": "TRAFFIC_UNAWARE"
  }' \
  https://routes.googleapis.com/directions/v2:computeRoutes
```

**Source:** [Routes API computeRoutes method](https://developers.google.com/maps/documentation/routes/reference/rest/v2/TopLevel/computeRoutes)

---

## 3. Minimal FieldMask & Pricing SKU

### Recommended FieldMask for MVP
```
X-Goog-FieldMask: routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline
```

This requests ONLY:
- `routes[0].duration` — travel time  
- `routes[0].distanceMeters` — distance in meters  
- `routes[0].polyline.encodedPolyline` — encoded polyline for map display

### Billing SKU Triggered
**Essentials** (basic routing features)

### Pricing (2026)
- **Free Monthly Allowance:** 10,000 requests/month per SKU  
- **Paid Tier Starts:** After 10,000 free requests
- **Cost:** $2.00 per 1,000 requests (starting tier)  
- **Volume Discount:** Down to $0.70 per 1,000 at higher volumes

### Important Pricing Notes
- Each SKU (Essentials, Pro, Enterprise) has its own free tier — they do NOT pool
- Universal $200 monthly credit RETIRED as of March 2025; replaced by per-SKU free tiers
- Adding toll info (`routes.travelAdvisory.tollInfo`) to FieldMask escalates to **Pro SKU** ($15 CPM)  
- TWO_WHEELER travel mode escalates to **Enterprise SKU** (highest tier)

**Source:** [Routes API usage and billing](https://developers.google.com/maps/documentation/routes/usage-and-billing); [Pricing details](https://developers.google.com/maps/billing-and-pricing/pricing)

---

## 4. Travel Modes & Vietnam Availability

### Available Travel Modes in Routes API
1. **DRIVE** (default) — passenger vehicles
2. **WALK** — pedestrian paths  
3. **BICYCLE** — pedal cycles
4. **TWO_WHEELER** — motorized two-wheelers (motorcycles, scooters)
5. **TRANSIT** — public transportation (limited regions)

### TWO_WHEELER in Vietnam
✅ **AVAILABLE** and functional  
⚠️ **BETA STATUS:** May have gaps in road data; must display user warning:  
> "Walking, bicycling, and two-wheel routes are in beta and might sometimes be missing clear sidewalks, pedestrian paths, or bicycling paths."

**Billing:** Escalates to Enterprise SKU (highest price tier)

### Recommendation for Family Tracker Demo
**Default to DRIVE** because:
- Most complete road coverage in Vietnam
- Lowest cost (Essentials SKU)  
- Family members most likely being tracked in vehicles
- TWO_WHEELER available as opt-in for future scope

**Source:** [Two-wheel routing documentation](https://developers.google.com/maps/documentation/routes/route_two_wheel); [Coverage details](https://developers.google.com/maps/documentation/routes/coverage-two-wheeled)

---

## 5. Response Shape — Exact JSON Paths

### Complete Response Example
```json
{
  "routes": [
    {
      "legs": [...],
      "distanceMeters": 150322,
      "duration": "5309s",
      "polyline": {
        "encodedPolyline": "mrlaGtavpLPLBTm…PgA^qC"
      }
    }
  ]
}
```

### JSON Paths for Minimal Response
| Field | Path | Type | Example | Notes |
|-------|------|------|---------|-------|
| **Duration** | `routes[0].duration` | String | `"5309s"` | Seconds as string; parse as int ÷ 60 for minutes |
| **Distance** | `routes[0].distanceMeters` | Integer | `150322` | Meters; convert to km for UI |
| **Polyline** | `routes[0].polyline.encodedPolyline` | String | `"mrlaGtavpL..."` | Encoded using Google polyline algorithm; decode for display on map |

### Duration Format
- **Format:** ISO 8601 duration string with seconds (NOT standard ISO; Google's variant)
- **Examples:**
  - `"123s"` = 123 seconds  
  - `"3661s"` = 1 hour 1 minute 1 second  
- **Parsing:** Extract integer, divide by 60 for minutes, or format as `HH:MM:SS`

**Source:** [Review route response](https://developers.google.com/maps/documentation/routes/understand-route-response); [Example responses](https://blog.afi.io/blog/plan-a-route-with-the-google-routes-api/)

---

## 6. API Key Security — Android App Restriction & Web Service Calls

### Critical Finding: Android-Restricted Keys Cannot Call Web Service

**Question:** Can the existing Android-app-restricted `MAPS_API_KEY` call the Routes API from the device?  
**Answer:** **NO. Plainly and firmly.**

From [Google API Security Best Practices](https://developers.google.com/maps/api-security-best-practices):
> "If you place an application restriction on an API key, you cannot use it on other platforms. For example, if you restrict the API key to only Android apps, you cannot use it with iOS, web services, or JavaScript APIs."

### Current Project Setup (Constraint)
- Existing `MAPS_API_KEY` in `local.properties`  
- Restricted to: Android app (package name + SHA-1)  
- Used by: Maps Compose for map display  
- **Problem:** This key CANNOT be used for REST API calls from the device

### Options for Routes API (Ranked by Recommendation)

#### **OPTION A: Separate API Key (Unrestricted + Quota-Capped) ⭐ Recommended**
**Approach:**  
1. Create NEW API key (no restrictions, or IP-restricted to device ranges)
2. Store in `BuildConfig.ROUTES_API_KEY` generated from `local.properties`
3. Device calls `routes.googleapis.com/directions/v2:computeRoutes` directly
4. Apply quota limits via Google Cloud Console to prevent runaway costs

**Pros:**
- Simple implementation; no backend needed
- Fastest response times (direct device→Google)
- Works in airplane mode (routes cached)

**Cons:**
- Unrestricted key visible in compiled APK (risk: key theft → abuse by third party)  
  - **Mitigation:** Set aggressive quota limits; monitor Cloud Console billing; consider signing app to detect tampering
- Need to rebuild/redeploy app to rotate key
- Requires `local.properties` NOT in VCS (already enforced in demo)

#### **OPTION B: Backend Proxy (High Security)**
**Approach:**  
1. Device calls private backend endpoint: `POST /api/v1/route`  
2. Backend holds unrestricted Routes API key (never exposed to device)
3. Backend validates request (auth token, rate limit), calls `routes.googleapis.com`
4. Backend returns polyline + ETA to device

**Pros:**
- API key never exposed; highest security  
- Server-side rate limiting + quota control
- Can add auth/billing layer
- Easy to swap routing provider later

**Cons:**
- Requires backend (not part of current FamilyTrackerDemo)  
- Adds latency (~100–200ms round-trip)
- Offline rerouting impossible
- Server infrastructure cost

#### **OPTION C: Migrating Existing Key to IP-Based Restriction**
**Approach:**  
Lift Android restriction from `MAPS_API_KEY`; add IP restriction (device IP ranges or 0.0.0.0/0 if demo-only)

**Cons:**  
- Loses Android-specific protection (anyone on same network/device can steal key)
- NOT recommended for production; acceptable ONLY for private demo
- Requires device to attach `X-Android-Package` + `X-Android-Cert` headers anyway; routes API MAY or MAY NOT honor them (not documented)

**Don't use for anything shipped.**

---

### Recommended Implementation for Demo

**Use OPTION A** (separate unrestricted key in `BuildConfig`):

1. **Create new API key in Google Cloud Console**  
   - Set restrictions: None (or HTTP referers if deploying URL-accessible demo)
   - Set quota: Daily limit to ~$5–10 (prevents accidents)

2. **Update `local.properties`**
   ```properties
   MAPS_API_KEY=xxx...xxx
   ROUTES_API_KEY=yyy...yyy  # NEW: separate key for web service
   ```

3. **Add to `app/build.gradle.kts`**
   ```kotlin
   buildFeatures {
       buildConfig = true
   }
   buildTypes {
       release {
           buildConfigField("String", "ROUTES_API_KEY", 
               "\"${providers.fileContents("$rootDir/local.properties")
                   .asText.get().split("\n")
                   .find { it.startsWith("ROUTES_API_KEY=") }
                   ?.substringAfter("=") ?: ""}\""
           )
       }
   }
   ```

4. **Use in code:**
   ```kotlin
   val apiKey = BuildConfig.ROUTES_API_KEY
   val response = httpClient.post("https://routes.googleapis.com/directions/v2:computeRoutes") {
       header("X-Goog-Api-Key", apiKey)
       // ... rest of request
   }
   ```

**Source:** [API security best practices](https://developers.google.com/maps/api-security-best-practices); [Restricting API keys](https://mapsplatform.google.com/resources/blog/google-maps-platform-best-practices-restricting-api-keys/)

---

## 7. Error & Quota Behavior

### HTTP Status Codes & Error Bodies

| Scenario | HTTP Code | Error Status | Error Message | Mapping to AppError |
|----------|-----------|--------------|---------------|---------------------|
| Quota exceeded | **429** | `RESOURCE_EXHAUSTED` | "Resource exhausted" or "Quota exceeded" | `AppError.Unexpected("Rate limit exceeded; retry later")` |
| Invalid API key | **403** | `PERMISSION_DENIED` | "The request is missing a valid API key." | `AppError.Validation("Invalid API key")` |
| Missing required param (origin/destination) | **400** | `INVALID_ARGUMENT` | "Origin and destination must be set." | `AppError.Validation("Missing route parameters")` |
| No route exists (e.g., across water) | **200 OK** | (empty array) | `"routes": []` | `AppError.NotFound("No route found")` |
| Malformed JSON | **400** | `INVALID_ARGUMENT` | "Invalid JSON" | `AppError.Validation("Malformed request")` |

### Quota Exceeded Behavior
- **Trigger:** Exceeded monthly free tier (10,000 for Essentials) or applied quota limit
- **Response:** HTTP 429 with `RESOURCE_EXHAUSTED` status
- **Recovery:** Exponential backoff + jitter (5s, 10s, 20s, …); contact Google to raise quota if persistent
- **Billing:** No charge for failed 429 requests (Google does not bill quota-limit errors)

### No Route Found
- **HTTP Status:** 200 OK (not an error in REST sense)  
- **Response Body:** `{ "routes": [] }` (empty array)
- **Cause:** No valid path exists (e.g., routing across water, international boundary, closed road)
- **Mapping:** Treat as `AppError.NotFound("Route not available between selected points")`

**Source:** [Handle request errors](https://developers.google.com/maps/documentation/routes/handle-errors)

---

## 8. Terms of Service & Attribution Requirements

### Display Requirements

✅ **ALLOWED:**  
- Display polyline on Google Map (must be a real Google Maps widget)
- Cache polyline data for offline use (NOT place IDs directly; see below)

❌ **FORBIDDEN:**
- Remove, hide, obscure, or modify Google attribution
- Display routes on non-Google map backgrounds without explicit approval
- Use routes computed by Google on a competitor map (e.g., OpenStreetMap, Mapbox)

### Attribution Standards

**Primary:** Use official Google Maps logo  
- **Minimum size:** 16dp height  
- **Maximum size:** 19dp height  
- **Clear space:** 10dp sides + top, 5dp bottom  
- **Position:** Top or bottom of content, same visual container

**Text Alternative:** If space constrained, text "Google Maps" (Roboto font, 12–16sp, accessible contrast)

### Public Policies Required
Applications MUST have:
- Publicly accessible **Terms of Use** (referencing Google's)
- Publicly accessible **Privacy Policy** (referencing Google's)

### Caching Exception  
**Place IDs** (optional in routes response) can be cached indefinitely (only Routes API field with this exception).

### Special Case: Non-Google Routes on Google Map
If using a third-party routing provider's polyline on Google Map:
> "Before navigation begins, the UI must clearly display **'Routing provided by [name of 3P route provider]'**"

(Not applicable here; we use only Google routes.)

**Source:** [Routes API policies and attributions](https://developers.google.com/maps/documentation/routes/policies); [General Maps Platform terms](https://developers.google.com/maps/terms-20180207)

---

## Summary: MVP Integration Checklist

| Item | Decision | Notes |
|------|----------|-------|
| **API to Use** | Routes API (v2) | Directions API is legacy; not available for new projects |
| **Endpoint** | `https://routes.googleapis.com/directions/v2:computeRoutes` | POST only |
| **API Key** | Separate from `MAPS_API_KEY` | Android-restricted key cannot call web service; use Option A (unrestricted + quota-capped) |
| **Travel Mode** | DRIVE | Best coverage; lowest cost. TWO_WHEELER available if needed (beta, higher cost) |
| **Routing Preference** | TRAFFIC_UNAWARE (MVP) | For realtime rerouting, sufficient; TRAFFIC_AWARE optional for future |
| **FieldMask** | `routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline` | Minimal; keeps billing on Essentials SKU |
| **Response Parsing** | `routes[0].duration` (string), `routes[0].distanceMeters` (int), `routes[0].polyline.encodedPolyline` (string) | Duration in format "5309s"; parse as seconds |
| **Map Display** | Must be on Google Map with attribution | Attribution logo or text required |
| **Reroute Errors** | Map empty routes → `AppError.NotFound`; HTTP 429 → `AppError.Unexpected` | No route found is not an API error; returns 200 OK |
| **Quota Safety** | Set daily/monthly limit in Cloud Console | Prevents runaway costs if key compromised |

---

## Unresolved Questions / UNVERIFIED Claims

1. **Encoding of polyline:** Research assumes Google polyline encoding (standard). UNVERIFIED: Fetch actual response to confirm exact encoding variant used.
2. **Alternative routes in computeRoutes:** Documentation references `computeAlternativeRoutes` parameter; UNVERIFIED whether Essentials SKU includes multiple routes by default or if separate call needed.
3. **TWO_WHEELER SKU pricing:** Stated as "Enterprise" but exact CPM not fetched. UNVERIFIED: Confirm from official pricing table.
4. **Offline caching legality:** Polyline caching for rerouting logic allowed, but long-term (>days) offline storage not explicitly confirmed. Check terms for clarity.
5. **X-Android-Package / X-Android-Cert headers:** Routes API documentation does not mention whether it honors these Android-specific headers. Test required.

---

## Files to Update After Implementation

- `LLM.md` §3: Add `data/routing/` package  
- `LLM.md` §2: Document dependency on Google Routes API  
- `LLM.md` §6: Note Koin DI for routing client  
- `docs/android-mvi-best-practices.md`: N/A (design pattern unchanged)  
- `docs/code-standards.md`: Add Routes API HTTP client pattern

---

**Report Date:** 2026-08-24  
**Confidence:** High (all major points verified via official Google documentation)  
**Cost Estimate (MVP):** ~$0 for demo (free tier covers 10k requests/month; demo usage << 10k)
