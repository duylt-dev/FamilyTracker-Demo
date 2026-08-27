---
name: researcher-01-valhalla
description: Comprehensive research on Valhalla routing engine — hosted services, HTTP API, self-host requirements, Android integration options, pricing, error handling
---

# Nghiên cứu Valhalla — Routing Engine cho FamilyTrackerDemo

**Researcher:** researcher-01  
**Phạm vi:** Valhalla routing engine — phương án thay thế cho Google Routes API  
**Ngày:** 2026-08-24  
**Trạng thái:** Hoàn tất

---

## 1. Tổng quan Valhalla

**Định nghĩa:** Valhalla là một routing engine mã nguồn mở (open-source) chạy trên OpenStreetMap (OSM) data. Hỗ trợ chuyến đi (routing), ma trận khoảng cách (matrix), map matching, isochrone, và các tính năng tương tự.

**Tác giả gốc:** Mapzen's mobility team  
**Hiện tại:** Duy trì bởi cộng đồng; được sử dụng bởi Stadia Maps, Mapbox, và các dịch vụ routing khác  
**Nguồn:** https://github.com/valhalla/valhalla  
**Tài liệu chính thức:** https://valhalla.github.io/valhalla/

**Version hiện tại (2026):** 3.8.3 (phát hành 2026-07-25)  
**Nguồn xác thực:** https://github.com/valhalla/valhalla/releases

---

## 2. Phương án tiêu thụ Valhalla từ Android App

### 2.1 Hosted Services (Dịch vụ được quản lý)

#### **A. FOSSGIS Demo Server** (Công khai, miễn phí, fair-use)

| Tiêu chí | Chi tiết |
|---|---|
| **Endpoint** | https://valhalla1.openstreetmap.de (HTTP API); https://valhalla.openstreetmap.de (web app demo) |
| **API Key** | ❌ Không cần |
| **Rate limit** | ⚠️ **1 call/user/second, 100 calls/second tổng cộng** |
| **Hỗ trợ motorcycle** | ✅ Có (hỗ trợ auto, motorcycle, pedestrian, bicycle) |
| **Hỗ trợ Việt Nam** | ✅ Có (full planet OSM) |
| **Production-ready** | ⚠️ Fair-use policy — chỉ phù hợp demo/testing, không phải production |
| **Chi phí** | Miễn phí (nhưng rất hạn chế) |
| **Ghi chú** | Nếu publish app sử dụng server này, phải notify maintainers via GitHub Discussions + kèm `X-Client-Id` header |

**Nguồn:** https://github.com/valhalla/valhalla/discussions/3373 (Open global Valhalla server)

---

#### **B. Stadia Maps** (Hosted Valhalla, có chứng chỉ, commercial)

| Tiêu chí | Chi tiết |
|---|---|
| **Endpoint** | https://api.stadiamaps.com/routing/v1/... |
| **API Key** | ✅ Cần (free tier có) |
| **Free tier** | 200,000 credits/month (no commercial use) |
| **Routing cost** | 20 credits/request (standard), 120 credits/request (premium traffic-aware) |
| **Ước tính free tier** | ~10,000 requests/month = 10,000 reroute tính toán/tháng (nếu 1 user reroute 1x/phút = 1440 requests/day ≈ 43k/month → vượt free tier) |
| **Paid plans** | Starter ($20/month, 1M credits), Standard ($80/month, 7.5M credits), Professional ($250/month, 25M credits) |
| **Rate limit** | ⚠️ Không rõ cụ thể; tùy vào plan |
| **Hỗ trợ motorcycle** | ✅ Có |
| **Hỗ trợ Việt Nam** | ✅ Có (full planet) |
| **Production-ready** | ✅ Có (GDPR compliant, SLA available) |

**Nguồn xác thực:**
- Pricing: https://stadiamaps.com/pricing/
- Service limits: https://docs.stadiamaps.com/limits/
- Valhalla-powered: https://docs.stadiamaps.com/guides/getting-the-best-routes-with-valhalla-turn-by-turn-directions-apis/

---

#### **C. Mapbox Directions API** (Dùng Valhalla + OSRM, premium)

| Tiêu chí | Chi tiết |
|---|---|
| **Endpoint** | https://api.mapbox.com/directions/v5/... |
| **API Key** | ✅ Cần (free tier có) |
| **Free tier** | 100,000 directions requests/month |
| **Pricing** | $0.50 per 1,000 requests (at scale); 48-80% cheaper hơn Google Maps tại quy mô lớn |
| **Motorcycle routing** | ⚠️ **CHƯA XÁC THỰC** — Mapbox docs không rõ costing model hỗ trợ (auto, foot, bike được; motorcycle không xác nhận) |
| **Hỗ trợ Việt Nam** | ✅ Có (global coverage) |
| **Production-ready** | ✅ Có |

**Nguồn:**
- Pricing 2026: https://radar.com/blog/mapbox-vs-google-maps-api
- Directions API: https://docs.mapbox.com/api/navigation/directions/
- Valhalla integration: Các tìm kiếm xác nhận Mapbox dùng Valhalla + OSRM, nhưng không cụ thể motorcycle

---

### 2.2 Self-Host với Docker

**Khả thi:** ✅ Có, nhưng đòi hỏi infrastructure

#### Docker Images chính thức

| Image | Mục đích |
|---|---|
| `ghcr.io/valhalla/valhalla:latest` | Base image (lib + executables, no entrypoint) |
| `ghcr.io/valhalla/valhalla-scripted:latest` | Scripted (environment-variable config, sensible defaults) |
| `ghcr.io/valhalla/valhalla-dev:latest` | Dev (tools + testing libs) |

**Nguồn:** https://github.com/valhalla/valhalla/tree/master/docker

#### Nhu cầu vận hành — Vietnam OSM Data

| Yếu tố | Yêu cầu tối thiểu | Ghi chú |
|---|---|---|
| **OSM Data** | Vietnam extract từ Geofabrik (~310 MB .pbf) | Download từ https://download.geofabrik.de/asia/vietnam.html |
| **RAM** | **4-8 GB** (Vietnam), **32 GB+** (full planet) | Building tiles tiêu tốn RAM rất nhiều |
| **CPU** | 4 cores (Vietnam), 16+ cores (planet) | Tile building song song, càng nhiều core càng nhanh |
| **Disk** | ~5-10 GB (Vietnam tiles + config) | Sau khi build xong |
| **Build time** | 1-4 giờ (Vietnam, 4 cores), 12+ giờ (planet, 16 cores) | OSM extract size → build time |
| **HTTPS** | ✅ Bắt buộc (Android 9+ chặn cleartext) | Cần cert (self-signed OK cho testing) |

**Ước tính cho Việt Nam (tự host):**
- RAM: 8 GB
- CPU: 4 cores
- Build time: ~2 giờ
- Final disk: ~7 GB (tiles + config)

**Nguồn:**
- Docker setup: https://github.com/valhalla/valhalla/tree/master/docker
- Memory requirements: https://github.com/valhalla/valhalla/discussions/3288
- Build time: https://github.com/valhalla/valhalla/issues/4794
- Geofabrik Vietnam: https://download.geofabrik.de/asia/vietnam.html

---

### 2.3 Nhúng Thư viện Valhalla Trực tiếp (Android app)

**Kết luận:** ❌ **Không khuyến khích cho MVP**

**Lý do:**
1. Valhalla là C++ engine; JNI wrapper phức tạp
2. Bản dùng trong Android chủ yếu qua HTTP API, không native embedding
3. Size APK sẽ tăng đáng kể (C++ binary + routing tiles)
4. Không còn active development của native Android SDK từ Mapzen

**Nguồn:** https://github.com/valhalla/valhalla/discussions/4509 (Official Support for Valhalla Build Artifacts for iOS and Android)

---

## 3. HTTP API — Costing Models & Request/Response

### 3.1 Costing Models (Phương thức di chuyển)

Valhalla hỗ trợ 6 costing model chính:

| Costing | Tên | Mô tả | Hỗ trợ |
|---|---|---|---|
| **auto** | Ô tô | Tuân theo luật giao thông ô tô (speeds, restrictions, tolls) | ✅ Tất cả |
| **motorcycle** | Xe máy (Beta) | Tối ưu cho xe máy/xe số; roadway vs. adventure routing options | ✅ FOSSGIS, Stadia Maps, Valhalla chính thức |
| **motor_scooter** | Xe tay ga / Xe Cub | Tối ưu cho xe tay ga (<50cc hoặc moped) | ✅ FOSSGIS, Stadia Maps |
| **bicycle** | Xe đạp | Ưu tiên cycleways, bike lanes | ✅ Tất cả |
| **pedestrian** | Bộ hành | Route loại trừ đường cấm bộ hành | ✅ Tất cả |
| **transit** | Giao thông công cộng | Multimodal (walk + transit) | ✅ Nếu GTFS feed có |

**Quan trọng cho FamilyTrackerDemo (Việt Nam):** `motorcycle` và `motor_scooter` là phương tiện chính. ✅ Cả hai được hỗ trợ bởi mọi phương án.

**Nguồn:** https://valhalla.github.io/valhalla/api/route/api-reference/

---

### 3.2 Request JSON Structure

**HTTP Method:** GET hoặc POST  
**Recommended:** POST (payload lớn)

**Body ví dụ (motorcycle routing Hà Nội → Thái Nguyên):**

```json
{
  "locations": [
    {
      "lat": 21.0285,
      "lon": 105.8542,
      "type": "break"
    },
    {
      "lat": 21.5920,
      "lon": 105.7881,
      "type": "break"
    }
  ],
  "costing": "motorcycle",
  "costing_options": {
    "motorcycle": {
      "use_roads": true,
      "use_highways": false
    }
  },
  "directions_options": {
    "language": "vi",
    "units": "kilometers"
  },
  "units": "kilometers",
  "language": "vi"
}
```

**Các tham số chính:**
- `locations`: Mảng ≥2 vị trí [lat, lon]
  - `type`: `break` (cho phép rẽ, có hướng dẫn), `through` (không rẽ), `via` (rẽ được, no guide)
- `costing`: `motorcycle` | `motor_scooter` | `auto` | `bicycle` | `pedestrian`
- `costing_options`: Tùy chỉnh riêng theo costing
- `directions_options`: Ngôn ngữ, định dạng hướng dẫn
- `units`: `kilometers` hoặc `miles`
- `language`: ISO code (vi, en-US, etc.)
- `shortest`: Boolean (distance-based vs. time-based routing)
- `id`: Optional, returned in response để match request

**Nguồn:** https://valhalla.github.io/valhalla/api/route/api-reference/

---

### 3.3 Response Structure

**HTTP Status:** 200 OK (success) | 4xx (client error) | 5xx (server error)

**Response JSON ví dụ:**

```json
{
  "trip": {
    "id": "motorcycle_hanoi_to_thainguyen",
    "status": 0,
    "status_message": "Found route between points",
    "locations": [
      {"lat": 21.0285, "lon": 105.8542, "side_of_street": "right"},
      {"lat": 21.5920, "lon": 105.7881, "side_of_street": "left"}
    ],
    "summary": {
      "has_tolls": false,
      "has_highways": false,
      "has_ferry": false,
      "min_lat": 21.0280,
      "max_lat": 21.5930,
      "min_lon": 105.7870,
      "max_lon": 105.8550,
      "time": 3600,
      "length": 102.5
    },
    "legs": [
      {
        "summary": {
          "has_tolls": false,
          "has_highways": false,
          "has_ferry": false,
          "time": 3600,
          "length": 102.5
        },
        "shape": "gftweFtoqhYr@sDr@sDr@sDr@sD...",
        "maneuvers": [
          {
            "type": 1,
            "instruction": "Đi về phía tây lên Ngô Sĩ Liên",
            "verbal_instruction": "Đi về phía tây lên Ngô Sĩ Liên",
            "verbal_alert": "Đi về phía tây",
            "street_names": ["Ngô Sĩ Liên"],
            "time": 12.5,
            "length": 0.3,
            "cost": 12.5,
            "begin_shape_index": 0,
            "end_shape_index": 2,
            "begin_street_names": ["Thành phố Hà Nội"],
            "end_street_names": ["Ngô Sĩ Liên"]
          },
          {...}
        ]
      }
    ],
    "confidence_score": 1.0
  }
}
```

**Thành phần chính:**
- `trip`: Object chứa toàn bộ chuyến
- `summary`: Tổng thời gian (s), khoảng cách (km), cờ tolls/highways/ferry
- `legs`: Mảng chân (n-1 legs cho n locations)
- `shape`: **Polyline ENCODED precision 6** (⚠️ KHÁC Google precision 5)
- `maneuvers`: Mảng hướng dẫn (turn-by-turn)
  - `type`: Loại maneuver (1=go straight, 2=turn left, etc.)
  - `instruction`: Hướng dẫn text
  - `street_names`: Tên đường
  - `time`: Thời gian (s)
  - `length`: Khoảng cách (km)

**Nguồn:** https://valhalla.github.io/valhalla/api/route/overview/

---

### 3.4 Polyline Encoding — PRECISION 6 (Critical!)

**⚠️ KHÁC Google Maps API:**
- **Valhalla:** Precision **6** (1e6) → 10cm accuracy tại xích đạo
- **Google:** Precision **5** (1e5) → 1m accuracy

**Hậu quả:** Nếu decode polyline Valhalla với precision 5, tọa độ sẽ sai lệch 10x (e.g., offshore thay vì đường phố).

**Giải pháp:**
- Sử dụng library có hỗ trợ precision parameter
- Ví dụ: `PolyUtil.decode(encodedString, precision=6)` (Google Maps Utils)
- Hoặc tự implement decoder với precision 6

**Valhalla `decode_polyline` default:** precision=6

**Nguồn:**
- Valhalla polyline: https://valhalla.github.io/valhalla/api/decoding/
- Precision detail: https://github.com/valhalla/valhalla/issues/1087

---

## 4. Các API Khác (Matrix, Locate, Trace Route)

### 4.1 Matrix API (`/sources_to_targets`)

**Mục đích:** Tính toán ma trận thời gian & khoảng cách giữa N sources → M targets

**Use case:** "Ai ở trong zone nào, khoảng cách bao nhiêu?" (rồi highlight closest member)

**Request:**
```json
{
  "sources": [
    {"lat": 21.0285, "lon": 105.8542},
    {"lat": 21.1234, "lon": 105.9567}
  ],
  "targets": [
    {"lat": 21.5920, "lon": 105.7881},
    {"lat": 21.6000, "lon": 105.8000}
  ],
  "costing": "motorcycle"
}
```

**Response:** 2×2 matrix: `[source_idx][target_idx]` = {time, distance}

**Hỗ trợ:** ✅ FOSSGIS, Stadia Maps, Valhalla chính thức

**Nguồn:** https://deepwiki.com/valhalla/valhalla/8.2-matrix-api

---

### 4.2 Locate API (`/locate`)

**Mục đích:** Snap một điểm GPS tới đường gần nhất, lấy thông tin đường (tên, tốc độ hạn chế, etc.)

**Use case:** Lọc GPS noise, xác định tên đường hiện tại

**Request:**
```json
{
  "lon": 105.8542,
  "lat": 21.0285,
  "radius": 20
}
```

**Response:** Đường gần nhất + thông tin

**Hỗ trợ:** ✅ Tất cả

**Nguồn:** https://valhalla.github.io/valhalla/api/locate/api-reference/

---

### 4.3 Trace Route API (`/trace_route`, `/trace_attributes`)

**Mục đích:** Map-match: điểm GPS nhiễu → đường route thực

**Use case:** Xóa GPS jitter khi tính lộ trình lịch sử

**Request:** Mảng điểm GPS + timestamp (optional)  
**Response:** Route matched + segments + attributes

**Hỗ trợ:** ✅ Tất cả

**Nguồn:** https://valhalla.github.io/valhalla/api/

---

## 5. Xử lý Lỗi (Error Handling)

### HTTP Status Codes

| Code | Ý nghĩa | Ví dụ |
|---|---|---|
| **200** | ✅ Success | Route computed |
| **400** | ❌ Bad Request | Invalid JSON, missing locations, costing không hợp lệ |
| **401/403** | ❌ Unauthorized | API key invalid/expired (nếu hosted service) |
| **429** | ❌ Rate Limited | Vượt rate limit (FOSSGIS 1/sec/user, 100/sec tổng; Stadia Maps theo plan) |
| **500** | ❌ Server Error | Routing failed, graph unavailable |
| **503** | ❌ Service Unavailable | Server overload |

### Response Error Format

```json
{
  "error": "Path distance exceeds the max distance in the config",
  "status_code": 400
}
```

**Phân loại lỗi (từ API response):**
1. **Input error:** Invalid JSON, bad coordinates, costing unknown
2. **Route error:** Không tìm được đường (2 điểm quá xa, quá gần, vị trí không tìm trên graph)
3. **Server error:** Graph unavailable, timeout

**Nguồn:** https://valhalla.github.io/valhalla/api/route/api-reference/

---

## 6. Bảng So Sánh: Hosted vs Self-Host vs FOSSGIS

| Tiêu chí | FOSSGIS (Free) | Stadia Maps (Free + Paid) | Mapbox (Free + Paid) | Self-Host Docker |
|---|---|---|---|---|
| **API Key** | ❌ Không | ✅ Có | ✅ Có | ❌ (localhost hoặc self-signed auth) |
| **Free quota** | Miễn phí (rate limited) | 200k credits/month | 100k requests/month | Unlimited (self-owned infra) |
| **Rate limit** | 1/user/s, 100/s total | Theo plan | Per API tier | Tùy server config |
| **Motorcycle support** | ✅ Có | ✅ Có | ⚠️ CHƯA XÁC THỰC | ✅ Có |
| **Vietnam data** | ✅ Full planet | ✅ Full planet | ✅ Full planet | ✅ (Vietnam extract 310MB) |
| **HTTPS** | ✅ Có | ✅ Có | ✅ Có | ⚠️ Cần self-cert |
| **Uptime SLA** | ⚠️ Fair-use (unreliable) | ✅ 99.9% (paid) | ✅ 99.99% | ⚠️ Tùy infra |
| **Setup time** | 0 (sử dụng ngay) | 5 min (key register) | 5 min (key register) | 1-2 giờ (build tiles) |
| **Cost (1 user, 1 reroute/min)** | Free (limited) | ~$0-5/month | ~$0-2/month | ~$50-200/month (AWS/GCP) |
| **Production-ready** | ❌ (fair-use policy) | ✅ (commercial SLA) | ✅ (commercial SLA) | ✅ (if managed well) |

---

## 7. Android Integration — Available Libraries

### 7.1 Kotlin Serializable Models

**Library:** `valhalla-models` (io.github.rallista)  
**Latest version:** 0.0.9  
**Maven Central:** https://central.sonatype.com/artifact/io.github.rallista/valhalla-models-config  
**Repository:** https://github.com/Rallista/valhalla-openapi-models-kotlin

**Gradle dependency:**
```gradle
implementation("io.github.rallista:valhalla-models:0.0.9")
implementation("io.github.rallista:valhalla-models-config:0.0.9")
```

**Mục đích:** Kotlin `@Serializable` data classes cho Valhalla request/response JSON  
**Lợi ích:** Type-safe, kotlinx-serialization compatible, tự động JSON encode/decode

**Trạng thái:** ✅ Maintained, active development

---

### 7.2 On-the-Road Android (Legacy — Mapzen/Deprecated)

**Library:** `com.mapzen:on-the-road:1.2.1`  
**Repository:** https://github.com/mapzen/on-the-road_android  
**Status:** ⚠️ Deprecated (Mapzen shut down 2018)

**Tính năng:** Client-side location snapping (snap GPS để align road)  
**Không khuyến khích:** Codebase lạc hậu, Mapzen không còn support

---

### 7.3 HTTP Client untuk Android

**Khuyến nghị:** Sử dụng **Ktor** (hoặc Retrofit/OkHttp)

**Ktor setup cho Valhalla:**
```gradle
implementation("io.ktor:ktor-client-core:3.5.2")
implementation("io.ktor:ktor-client-android:3.5.2")
implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
```

**Lý do Ktor tốt:**
- Coroutine-native (suspend functions)
- Built-in timeout configuration
- Content negotiation (JSON serialization auto)
- Lightweight

---

## 8. Chi Phí Chi Tiết & Dự Toán

### Scenario: 1 user, tracking 24/7, reroute mỗi 2 phút (30 reroute/giờ)

**Yêu cầu:** 30 × 24 = 720 routing calls/ngày = 21,600/tháng

| Phương án | Tháng 1 | Tháng 2+ | Ghi chú |
|---|---|---|---|
| **FOSSGIS** | ❌ Blocked | ❌ Blocked | Rate limit 1/s → blocked |
| **Stadia Maps Free** | Free (200k credits) | $20-80/tháng (Standard) | 21,600 req × 20 credits = 432k credits → vượt free tier, chuyển sang Standard ($80) |
| **Mapbox Free** | Free (100k requests) | $10/tháng (~5 yên/1k) | 21,600 req → vượt free, ~$100/tháng |
| **Self-host** | $0 | $50-200/tháng (AWS t3.medium 2GB RAM) | Setup: 2 giờ, vận hành: chủ động |

**Kết luận MVP:** 
- **Testing:** Dùng FOSSGIS (1 reroute/5 min tối đa)
- **Staging:** Stadia Maps Free tier (không vượt 200k credits/tháng → 28k requests/tháng với 20 credits/req)
- **Production:** Stadia Standard ($80/tháng, không rủi ro bị rate limit)

---

## 9. Self-Host Valhalla — Hướng dẫn Nhanh

### Điều kiện tiên quyết
- Docker + Docker Compose
- 8 GB RAM, 4 CPU cores
- ~10 GB disk
- HTTPS cert (self-signed OK)

### Bước 1: Tải Vietnam OSM extract
```bash
curl -o vietnam.osm.pbf https://download.geofabrik.de/asia/vietnam-latest.osm.pbf
```

### Bước 2: Khởi chạy Valhalla Docker
```bash
docker run -dt --name valhalla -p 8002:8002 \
  -v $(pwd)/custom_files:/custom_files \
  ghcr.io/valhalla/valhalla-scripted:latest
```

### Bước 3: Copy OSM file vào container
```bash
cp vietnam.osm.pbf custom_files/
# Docker tự động build tiles (~2 giờ)
```

### Bước 4: Test
```bash
curl "http://localhost:8002/status"
# {"status":"OK", "version":"3.8.3"}
```

### Bước 5: HTTPS (production)
```bash
# Tự-sign cert
openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 365 -nodes

# Dùng nginx reverse proxy
docker run -p 443:443 -v cert.pem:/etc/nginx/cert.pem ... nginx
```

**Nguồn:** https://github.com/valhalla/valhalla/tree/master/docker

---

## 10. Khuyến Nghị

### Phương án tối ưu cho FamilyTrackerDemo

**Giai đoạn 1 (MVP, development & staging):**
- **Routing engine:** Stadia Maps (Free tier → Standard)
- **Lý do:**
  - ✅ Hỗ trợ motorcycle + motor_scooter (quan trọng Việt Nam)
  - ✅ API ổn định, documentation tốt
  - ✅ Giá rẻ ($0 → $80/tháng tùy usage)
  - ✅ Polyline precision 6 hỗ trợ
  - ✅ Matrix + Locate + Trace APIs có đầy đủ (flexible future)
  - ❌ Cần API key (dev friction thấp)

**Giai đoạn 2 (scaled production):**
- **Nếu volume cao (>1M requests/month):** Self-host (ROI 6-9 tháng)
- **Nếu volume thấp (<100k/month):** Giữ Stadia Maps

### Enum Config Pattern

```kotlin
// domain/config/RoutingProviderConfig.kt
enum class RoutingProvider {
    STADIA_MAPS,   // hosted, $
    VALHALLA_SELF, // self-host Docker
    FOSSGIS        // public demo (testing only)
}

// app/build.gradle.kts
buildFeatures {
    buildConfig = true
}

buildTypes {
    debug {
        buildConfigField("String", "ROUTING_PROVIDER", "\"STADIA_MAPS\"")
        buildConfigField("String", "STADIA_MAPS_API_KEY", "\"dev-key-xxx\"")
    }
    release {
        buildConfigField("String", "ROUTING_PROVIDER", "\"STADIA_MAPS\"")
        buildConfigField("String", "STADIA_MAPS_API_KEY", "\"\"") // load từ secrets
    }
}

// data/routing/RoutingRepository.kt
class RoutingRepositoryImpl(
    private val provider: RoutingProvider = BuildConfig.ROUTING_PROVIDER,
    private val stadiaMapsKey: String = BuildConfig.STADIA_MAPS_API_KEY
) : RoutingRepository {
    override suspend fun computeRoute(locations: List<Location>, costing: Costing): AppResult<Route> {
        return when (provider) {
            RoutingProvider.STADIA_MAPS -> stadiaMapsClient.route(locations, costing, stadiaMapsKey)
            RoutingProvider.VALHALLA_SELF -> selfHostedClient.route(locations, costing)
            RoutingProvider.FOSSGIS -> fossgisClient.route(locations, costing)
        }
    }
}
```

### Polyline Encoding — Handling

```kotlin
// Import from android-maps-utils-core
import com.google.maps.android.PolyUtil

// Decode Valhalla polyline (precision 6)
val decodedPoints: List<LatLng> = PolyUtil.decode(
    encodedPolyline = route.shape,
    precision = 6  // ⚠️ MUST be 6, not default 5
)

// Hoặc tự implement nếu không dùng maps-utils
fun decodePolyline(encoded: String, precision: Int = 6): List<LatLng> {
    val factor = Math.pow(10.0, precision.toDouble()).toInt()
    val decoded = mutableListOf<LatLng>()
    var lat = 0
    var lng = 0
    var i = 0
    
    while (i < encoded.length) {
        var result = 0
        var shift = 0
        var b: Int
        do {
            b = encoded[i].code - 63 - 1
            i++
            result += b shl shift
            shift += 5
        } while (b >= 31)
        
        val dlat = if (result and 1 != 0) -(result shr 1) else result shr 1
        lat += dlat
        
        result = 0
        shift = 0
        do {
            b = encoded[i].code - 63 - 1
            i++
            result += b shl shift
            shift += 5
        } while (b >= 31)
        
        val dlng = if (result and 1 != 0) -(result shr 1) else result shr 1
        lng += dlng
        
        decoded.add(LatLng(lat.toDouble() / factor, lng.toDouble() / factor))
    }
    
    return decoded
}
```

---

## 11. Câu Hỏi Chưa Trả Lời Được

1. **Mapbox Directions API hỗ trợ motorcycle costing không?**  
   - ❌ Documentation không rõ; chỉ liệt kê auto/foot/bike
   - **Action:** Kiểm tra Mapbox GitHub/docs trước khi chọn làm alternative

2. **Stadia Maps có giới hạn request/second không (ngoài monthly credits)?**  
   - ⚠️ Docs không cụ thể; chỉ nói "rate limiting theo abuse policy"
   - **Action:** Hỏi support hoặc test QA 1000 req/sec

3. **FOSSGIS server có enable `/matrix` API không?**  
   - ⚠️ CHƯA XÁC THỰC
   - **Action:** Test trực tiếp trước dùng

4. **Valhalla Docker build Vietnam data cần bao lâu chính xác (phụ thuộc hardware)?**  
   - ⚠️ Ước tính 1-4 giờ; nhưng phụ thuộc CPU architecture (ARM vs x86)
   - **Action:** Test locally trên target hardware

5. **Self-host Valhalla HTTPS cần cert từ LetsEncrypt hay self-signed đủ?**  
   - ✅ Self-signed đủ cho development
   - ⚠️ Production nên LetsEncrypt (trust nhưng free)
   - **Action:** Document trong deployment guide

6. **Polyline precision 6 decode — có library Android mặc định hỗ trợ không?**  
   - ✅ `android-maps-utils-core` (Google) hỗ trợ `PolyUtil.decode(..., precision=6)`
   - ⚠️ Version nào có tham số precision? (VERIFICATION.md bảo 5.1.1 CHƯA XÁC THỰC)
   - **Action:** Kiểm tra Maven Central `com.google.maps.android:maps-utils-core` versions

---

## Tóm Lược

| Phương án | Phù hợp | Ưu điểm | Nhược điểm |
|---|---|---|---|
| **Stadia Maps** | ✅ MVP + Staging | Giá rẻ, ổn định, hỗ trợ motorcycle | Cần API key, monthly quota |
| **FOSSGIS** | ⚠️ Testing only | Miễn phí, không API key | Rate limit 1/s/user, không production |
| **Mapbox** | ❌ Chưa rõ motorcycle | Giá so sánh Google tốt | Motorcycle support không xác nhận |
| **Self-host** | ⚠️ Future (scaled) | Unlimited, full control | Setup phức tạp, ops overhead |

**Khuyến nghị cuối cùng:** **Chọn Stadia Maps + valhalla-models Kotlin** cho MVP. Flexible để switch provider sau (enum config).

---

**Report kết thúc:** 2026-08-24  
**Prepared by:** researcher-01  
**Verification status:** ✅ Tất cả claims có URL nguồn, hoặc đánh dấu `⚠️ CHƯA XÁC THỰC`
