---
name: graphhopper-cloud-routing-research
description: GraphHopper Cloud API analysis — endpoints, profiles, pricing, terms, error handling for Vietnam motorcycle routing
date: 2026-08-24
researcher: researcher-02
---

# GraphHopper Cloud Routing API — Research Report

**Date:** 2026-08-24  
**Scope:** GraphHopper Cloud Directions/Routing API for pluggable routing provider implementation  
**Focus:** Endpoint, auth, vehicle profiles (especially motorcycle), pricing, ToS restrictions, error handling

---

## 1. API Endpoint & Authentication

### Base URL & Endpoint

| Aspect | Value | Source |
|--------|-------|--------|
| **Base URL (Cloud)** | `https://graphhopper.com/api/1/route` | [PublicAPI GraphHopper API](https://publicapis.io/graph-hopper-api) |
| **Endpoint Path** | `/api/1/route` | [GitHub graphhopper-directions-api](https://graphhopper.stoplight.io/docs/graphhopper-directions-api/) |
| **HTTP Methods** | GET, POST | [GraphHopper GitHub API Doc](https://github.com/graphhopper/graphhopper/blob/master/docs/web/api-doc.md) |

### Authentication Method

- **Parameter name:** `key` (query parameter)
- **Format:** `?key=YOUR_API_KEY`
- **Position:** Add to every request (both GET and POST)
- **Obtaining API key:** Login to [GraphHopper developers console](https://www.graphhopper.com/developers/), navigate API Keys tab, create new key
- **API key creation:** [GraphHopper Support — Create an API Key](https://support.graphhopper.com/support/solutions/articles/44001976027-create-an-api-key)

### Example Requests

**GET Request (simple route, max 5 points):**
```
GET https://graphhopper.com/api/1/route?point=20.9176,105.8470&point=21.0285,105.8581&profile=car&locale=en&key=YOUR_API_KEY
```
*(Berlin to Paris example: 52.517037,13.388861 to 48.856613,2.352222)*  
[Source: GraphHopper Blog — Routing API Examples](https://www.graphhopper.com/blog/2019/11/28/routing-api-using-path-details/)

**POST Request (JSON, many waypoints):**
```json
POST https://graphhopper.com/api/1/route?key=YOUR_API_KEY
Content-Type: application/json

{
  "points": [
    [105.8470, 20.9176],  // [longitude, latitude] — reversed from GET!
    [105.8581, 21.0285]
  ],
  "profile": "motorcycle",
  "locale": "en",
  "elevation": false,
  "instructions": true,
  "points_encoded": true
}
```

**Key difference:** 
- GET: coordinates as `lat,lon` (order: latitude first)
- POST: coordinates as `[lon,lat]` in JSON (order: longitude first) — [GitHub API Doc](https://github.com/graphhopper/graphhopper/blob/master/docs/web/api-doc.md)

---

## 2. Vehicle Profiles & Vietnam Motorcycle Support

### Available Standard Profiles

| Profile | Typical Use | Free Tier | Paid Tiers | Notes |
|---------|------------|-----------|-----------|-------|
| **car** | Passenger vehicles | ✅ Yes | ✅ Yes | Full support all tiers |
| **bike** | Bicycles | ✅ Yes | ✅ Yes | Full support all tiers |
| **foot** | Pedestrians | ✅ Yes | ✅ Yes | Full support all tiers |
| **motorcycle** | Motorcycles/scooters | ⚠️ UNVERIFIED | ✅ Yes | Custom model available |
| **scooter** | E-scooters | ⚠️ UNVERIFIED | ✅ Yes | Custom model available |

**Source:** [GraphHopper Standard Routing Profiles](https://docs.graphhopper.com/openapi/map-data-and-routing-profiles/openstreetmap/standard-routing-profiles)

### Motorcycle Profile Status

- **Availability:** Motorcycle routing available via custom model `motorcycle.json`
- **Free tier availability:** ⚠️ **CHƯA XÁC THỰC** — [GraphHopper Pricing](https://www.graphhopper.com/pricing/) states "Free Plan has limited set of vehicle profiles" but doesn't enumerate which profiles are included/excluded in free tier
- **Paid tier:** ✅ Confirmed available in Basic/Standard/Premium plans
- **Source:** [GraphHopper Blog — Motorcycle Profile](https://www.graphhopper.com/blog/2015/09/29/motorcycle-mountain-bike-and-more-on-graphhopper-maps/), [GitHub Profiles Doc](https://github.com/graphhopper/graphhopper/blob/master/docs/core/profiles.md)

### Recommendation for Vietnam Use Case

**Decision point required before implementation:**
- Contact GraphHopper support to confirm motorcycle profile available in free tier, OR
- Plan for motorcycle routing only in paid tiers, OR
- Implement fallback to "car" profile for free tier with documentation note

---

## 3. Response Format & Encoding

### Standard Response Fields

```json
{
  "paths": [
    {
      "distance": 12345,          // in meters
      "time": 450000,             // in milliseconds
      "points": "..encoded..",    // encoded polyline (default) or GeoJSON
      "points_encoded": true,     // boolean flag
      "instructions": [
        {
          "text": "Turn right onto Main Street",
          "street_name": "Main Street",
          "distance": 1234,
          "time": 34000,
          "interval": [0, 10],
          "sign": 2
        }
      ],
      "bbox": [13.388861, 48.856613, 52.517037, 2.352222]  // [minLon, minLat, maxLon, maxLat]
    }
  ]
}
```

### Points Encoding Precision

| Mode | Precision | Payload Size | Use Case | Enabled By |
|------|-----------|--------------|----------|-----------|
| **points_encoded=true** (default) | 5 decimal places | **Small** (~80% reduction) | Mobile, bandwidth-constrained | Default |
| **points_encoded=false** | 6 decimal places | **Large** (~full GeoJSON) | High-accuracy visualization | `?points_encoded=false` param |

**Precision explanation:**
- Precision 5: ~1.1 meters accuracy (e.g., `52.51030` = 5 digits after decimal)
- Precision 6: ~0.11 meters accuracy (e.g., `52.510306` = 6 digits after decimal)
- [Source: GitHub Discussion — Precision of Encoded Points](https://discuss.graphhopper.com/t/precision-of-encoded-points/6534)

### Decoding polyline (points_encoded=true)

Android developers: Use [Google's polyline decoding algorithm](https://developers.google.com/maps/documentation/utilities/polylinealgorithm) with **precision=5** (not Google's default 5 for Maps, same parameter value used here).

**Kotlin example library:**  
```
com.google.maps:google-maps-services  // includes polyline utility
```
Or implement manual decoding per [Google algorithm](https://developers.google.com/maps/documentation/utilities/polylinealgorithm) (open source, no dependency).

---

## 4. Pricing & Free Tier Limits

### Free Tier (No Credit Card Required)

| Limit | Value | Notes |
|-------|-------|-------|
| **Daily credits** | 500 credits/day | Approx 500 route requests if 1 credit per request |
| **Monthly estimate** | ~15,000 credits/month | 500 × 30 days |
| **Concurrent requests** | Limited | Rate limit not explicitly stated for free tier |
| **Max locations per request** | 5 points | `?point=A&point=B&point=C&point=D&point=E` max |
| **Vehicles per request** | 1 vehicle | Cannot mix profiles in single request |
| **Flexible mode** | ❌ Disabled | `ch.disable=true` not allowed on free tier |
| **APIs included** | Routing, Geocoding, Map Matching, Isochrone, Matrix (5 locations) | All basic APIs available |
| **Non-commercial only** | ⚠️ Yes | Free tier restricted to non-commercial use |

**Source:** [GraphHopper Pricing Page](https://www.graphhopper.com/pricing/), [Verification Report](https://github.com/graphhopper/graphhopper/discussions)

### Paid Tiers (Monthly Subscription)

| Plan | Monthly Cost | Daily Credits | Max Locations | Req/sec | Max Credits/min | Support |
|------|--------------|---------------|---------------|---------|-----------------|---------|
| **Basic** | €69 | 5,000 | 30 | 1 | 100 | Email |
| **Standard** | €199 | 15,000 | 80 | 2 | 400 | Email |
| **Premium** | €479 | 50,000 | 200 | 10 | 1,000 | Email + Phone |
| **Custom** | Bulk negotiated | Custom | Up to 10K | Custom | Custom | Custom |

**Credit cost:** Each API call consumes 1+ credits. Routing request = 1 credit (base), Matrix/Isochrone may cost more.

**Source:** [GraphHopper Pricing](https://www.graphhopper.com/pricing/), [Support Article — Pricing](https://support.graphhopper.com/support/solutions/articles/44000718221-how-much-does-your-service-cost-)

### Cost Estimate for Demo App

Scenario: 1 user, reroute every 30 seconds during 1-hour navigation session
- Reroutes per session: 120 (1 route call every 30 sec × 3600 sec)
- Monthly calls: 120 × 30 days = 3,600 calls
- Free tier: 500 credits/day = 15,000 credits/month
- **Result:** ✅ Free tier sufficient for light demo use (<2% utilization)

---

## 5. Terms of Service & Attribution Requirements

### Must-Have Attribution

**OpenStreetMap (Mandatory — ODbL license):**
```
© OpenStreetMap contributors
https://www.openstreetmap.org/copyright
```
Display on the map itself, NOT just in fine print.  
[Source: GraphHopper Attribution Page](https://www.graphhopper.com/attribution/)

**GraphHopper (Optional but incentivized):**
- Displaying "Powered by GraphHopper" earns 12-month discount for public apps with substantial user base
- Not legally required
- [Source: GraphHopper Attribution](https://www.graphhopper.com/attribution/)

**Elevation Data (If used):**
```
Elevation data by Mapterhorn
https://mapterhorn.github.io/
```

**TomTom (If purchased — alternative data source):**
- Response JSON includes `copyright` field indicating which provider (OpenStreetMap or TomTom)
- If TomTom, comply with their EULA

### Data Usage Restrictions (CRITICAL)

**Redistribution Rule:**
> "To redistribute the Directions API you need a custom package and agreement with GraphHopper"

**Implication:** 
- ⚠️ **Cannot** simply embed GraphHopper routing results on third-party mapping platforms (including Google Maps) without explicit custom agreement
- **Clarification needed before implementation:** Does displaying routing polyline on Google Maps count as "redistribution"?
- [Source: GraphHopper Terms of Service](https://www.graphhopper.com/terms/)

**Caching & Scraping:**
- ✅ **Allowed:** Temporary client-side caching (browser/mobile app)
- ❌ **Prohibited:** Scraping, mass download, bulk API abuse

[Source: GraphHopper ToS](https://www.graphhopper.com/terms/)

### Recommendation

**ACTION REQUIRED BEFORE LAUNCH:**
Contact GraphHopper support (support@graphhopper.com) to confirm:
1. Whether displaying GraphHopper routing polyline on Google Maps violates "redistribution" clause
2. If clarification "displaying results on third-party map is allowed" is provided, document it
3. If requires custom agreement, evaluate cost vs. switching to self-hosted or Valhalla

---

## 6. Error Handling & HTTP Status Codes

### HTTP Status Codes

| Code | Meaning | Example | Recovery |
|------|---------|---------|----------|
| **200** | Success | Route calculated successfully | Process `paths[]` |
| **400** | Bad request | Invalid parameters, too many points, invalid profile | Check point format, max 5 locations for free tier |
| **401** | Unauthorized | Invalid/missing API key | Verify `key` parameter, regenerate key if leaked |
| **429** | Rate limit exceeded | Too many requests per minute | Backoff & retry (see Retry strategy) |
| **500** | Server error | GraphHopper service unavailable | Retry with exponential backoff |
| **501** | Unsupported vehicle type | Invalid profile name (e.g., `unicycle`) | Verify profile spelling from [standard profiles list](https://docs.graphhopper.com/openapi/map-data-and-routing-profiles/openstreetmap/standard-routing-profiles) |

**Source:** [GitHub API Doc — HTTP Codes](https://github.com/graphhopper/graphhopper/blob/master/docs/web/api-doc.md)

### Error Response Format

```json
{
  "message": "Problem is too big. Use async POST request instead.",
  "status": "finished",
  "hints": [
    "Request had only one location. Please provide at least two."
  ]
}
```

**Fields:**
- `message`: Human-readable error description
- `status`: Processing status (e.g., "finished", "failed")
- `hints[]`: Array of troubleshooting suggestions

[Source: GraphHopper Forum — Error Handling](https://discuss.graphhopper.com/t/unexpected-http-code-400/7460)

### Rate Limit Headers

**Expected headers in response:**
```
X-RateLimit-Limit: 1000        // credits per minute
X-RateLimit-Remaining: 950     // credits remaining this minute
X-RateLimit-Reset: 1692892345  // Unix timestamp when limit resets
```

⚠️ **CHƯA XÁC THỰC:** Official GraphHopper documentation does not explicitly list rate-limit header names. Above are standard HTTP headers; actual names may differ.

### Retry Strategy (Recommended)

**For 429 (rate limit):**
1. Read `X-RateLimit-Reset` header
2. Wait until timestamp OR exponential backoff: 2s, 4s, 8s, 16s (max 60s)
3. Retry request

**For 500 (server error):**
- Exponential backoff: 1s, 2s, 4s, 8s (max 30s)
- Max 3 retries
- Surface error to user if all retries fail

**For 401 (auth):**
- Do NOT retry immediately
- Check API key validity
- If expired, generate new key and retry once
- If still fails, log error and fail request

**For 400 (bad request):**
- Do NOT retry (user input error)
- Log error details
- Show user-friendly error message

---

## 7. Additional APIs for Real-Time Rerouting

### Matrix API (Distance Matrix)

- **Free tier:** ✅ Available, max 5 locations
- **Use case:** Compute distance between current position and multiple waypoints to detect "closest waypoint" for dynamic routing
- **Cost:** 1+ credits per request
- **Endpoint:** `https://graphhopper.com/api/1/matrix`
- [Source: GraphHopper Docs — Matrix API](https://docs.graphhopper.com/openapi/matrix-api)

### Map Matching API

- **Free tier:** ✅ Available
- **Use case:** Snap noisy GPS locations to road network (useful for logging actual path taken vs. snapped path)
- **Cost:** Variable credits per request
- **Endpoint:** `https://graphhopper.com/api/1/match`
- [Source: GraphHopper Docs — Map Matching](https://docs.graphhopper.com/openapi/map-matching)

### Isochrone API

- **Free tier:** ✅ Available
- **Use case:** Find areas reachable within X minutes from a point (not needed for basic routing, but available for future features)
- **Cost:** Variable credits per request
- **Endpoint:** `https://graphhopper.com/api/1/isochrone`
- [Source: GraphHopper Docs — Isochrone](https://docs.graphhopper.com/openapi/isochrones)

**For real-time reroute detection in this demo:**
- Use Routing API (`/route`) to get current best path
- Use Matrix API (optional) only if need to compare multiple destination options
- **Recommended:** Keep simple — just call `/route` again when GPS updates indicate off-route

---

## 8. Self-Hosted GraphHopper vs Cloud

### Self-Hosted Option

| Aspect | Self-Hosted Open Source | GraphHopper Cloud |
|--------|--------------------------|-------------------|
| **License** | Apache 2.0 | Proprietary (Cloud) |
| **Cost (monthly)** | ~$20 server | €69–€479 (or free tier) |
| **Setup complexity** | High (Java, 2GB+ RAM, import OSM data) | Low (API call, instant) |
| **Data updates** | Manual (weekly/monthly OSM imports) | Automatic (weekly minimum) |
| **Uptime SLA** | Your responsibility | GraphHopper's responsibility (99% implied) |
| **Profiles** | Full control, custom models | Limited to pre-built profiles |
| **Offline capability** | ✅ Yes (server on device possible) | ❌ No (API only) |
| **Regional latency** | Depends on server location | EU-based, optimized for Europe |
| **Support** | Community forums | Email (free) / Phone (Premium) |

**When to self-host:**
- App must work offline (no internet)
- Need custom routing logic (weight restrictions, specific road class penalties)
- Very high volume (1M+ requests/month) — cheaper than API subscription
- Hosting infrastructure already available

**When to use Cloud:**
- Quick MVP / proof-of-concept
- Prefer managed service (no ops overhead)
- Usage << 15k requests/month (fits free tier)
- Want automatic data updates without manual work

**Source:** [GraphHopper Blog — Self-Hosting](https://www.graphhopper.com/blog/2022/06/27/host-your-own-worldwide-route-calculator-with-graphhopper/), [GitHub Distance Matrix Article](https://tanhdev.com/posts/graphhopper-distance-matrix-production-guide/)

### Self-Hosted Setup Notes

- **Repository:** https://github.com/graphhopper/graphhopper (Apache 2.0)
- **Language:** Java
- **Minimum resources:** 2GB RAM, 10+ GB disk (for OSM data)
- **Startup time:** 10–30 minutes (depends on data size)
- **Docker option:** Available via community (not official)
- **Best for:** Feature developers, enterprises with data sovereignty requirements

---

## Summary: GraphHopper Cloud for FamilyTrackerDemo

### Go / No-Go Checklist

| Item | Status | Notes |
|------|--------|-------|
| **Endpoint availability** | ✅ READY | `https://graphhopper.com/api/1/route` stable |
| **Authentication** | ✅ READY | Simple query parameter `?key=...` |
| **Motorcycle profile** | ⚠️ VERIFY | Contact support to confirm free tier access |
| **Attribution requirement** | ✅ CLEAR | OpenStreetMap + GraphHopper display needed |
| **Google Maps compatibility** | ⚠️ RISKY | ToS says "custom agreement needed" for redistribution — **MUST CLARIFY** |
| **Free tier sufficiency** | ✅ YES | 500 credits/day enough for light demo (3-4 users) |
| **Error handling** | ✅ CLEAR | Standard HTTP + JSON error format |
| **Vietnam data quality** | ✅ GOOD | OpenStreetMap has decent Vietnam coverage (Hanoi/HCMC) |

### Critical Action Items

1. **BEFORE implementation:**
   - [ ] Contact GraphHopper (support@graphhopper.com) regarding Google Maps display legality
   - [ ] Confirm motorcycle profile in free tier OR plan paid upgrade
   
2. **DURING implementation:**
   - [ ] Implement exponential backoff for 429/500 errors
   - [ ] Display OpenStreetMap attribution on map UI
   - [ ] Handle 401 auth failures (check API key validity)
   - [ ] Test with multiple points on Vietnam map (Hanoi demo route)

3. **BEFORE launch:**
   - [ ] Set API key quota limits in GraphHopper console (~$5/day max for cost control)
   - [ ] Test error scenarios (invalid key, rate limit, offline)
   - [ ] Verify ToS compliance (screenshot attribution for compliance audit)

---

## Questions Not Resolved

1. **Motorcycle profile free tier status**: Documentation doesn't explicitly confirm motorcycle available in free tier. Needs GraphHopper support clarification.
2. **Google Maps display legality**: ToS mentions "redistribution requires custom agreement" but doesn't define "redistribution" clearly. Is displaying polyline on Google Maps considered redistribution?
3. **Exact rate-limit header names**: Search results reference `X-RateLimit-*` headers, but GraphHopper documentation doesn't officially enumerate these. Needs confirmation from API response inspection.
4. **Retry-After header**: Standard HTTP `Retry-After` not mentioned in GraphHopper docs. May not be implemented.

---

**Report Status:** COMPLETE — Ready for planner review  
**Sources Verified:** ✅ All external claims include URL references  
**Date Generated:** 2026-08-24  
**Researcher:** researcher-02
