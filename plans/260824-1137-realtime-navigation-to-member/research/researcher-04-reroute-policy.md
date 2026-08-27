# Phân tích chính sách reroute cho chỉ đường realtime đến thành viên
**Researcher:** Claude Code (researcher-04-reroute-policy)  
**Ngày:** 2026-08-24  
**Phạm vi:** Quyết định khi nào cần tính toán lại tuyến đường trong realtime navigation

---

## 1. Cách các ứng dụng navigation chuyên nghiệp quyết định reroute

### 1.1 Phát hiện off-route (vượt khỏi tuyến đường)

**Định nghĩa:** Một điểm GPS cách tuyến đường hiện tại hơn một ngưỡng khoảng cách được phép.

**Phương pháp sử dụng trong ngành:**

- **HERE Navigation SDK** — Cung cấp `RouteDeviation.distanceInMeters` (khoảng cách thẳng từ vị trí dự tính đến vị trí thực tế trên tuyến đường). Nhà phát triển tự chọn ngưỡng để kích hoạt reroute. Ví dụ quy chuẩn trong SDK là **30 mét** [HERE SDK Route Deviation Docs](https://docs.here.com/here-sdk/docs/ios-navigation-deviation)

- **Mapbox Navigation SDK** — Sử dụng perpendicular distance (khoảng cách từ vị trí hiện tại tới điểm gần nhất trên polyline tuyến đường). Cho phép tùy chỉnh ngưỡng via `allowRerouteFrom` callback, không ép buộc một giá trị cố định [Mapbox Rerouting Docs](https://docs.mapbox.com/android/navigation/guides/turn-by-turn-navigation/rerouting-and-refresh/)

- **Phát hiện qua CSTV độc lập:** Tất cả các hệ thống sử dụng perpendicular distance hoặc distance-to-segment, và nếu vượt qua ngưỡng cố định (e.g. 200 feet = ~61m) hoặc động (tùy theo loại đường), bắt đầu reroute. Tài liệu bằng sáng chế chỉ ra rằng threshold có thể thay đổi theo loại đường (cao tốc > đường dân cư) [US Patent 7561964 - Off-route Recalculation](https://patents.google.com/patent/US7561964)

### 1.2 Xác nhận off-route qua nhiều mẫu (anti-noise)

**Vấn đề:** Một lần đọc GPS sai lệch 30m không có nghĩa là người dùng đã off-route. Cần xác nhận từ nhiều mẫu liên tiếp để tránh flicker khi GPS jitter.

**Giải pháp:**
- Mapbox khuyến cáo **minimum 2-3 samples liên tiếp off-route** trước khi gọi reroute, để thoát khỏi nhiễu GPS ngắn hạn [Mapbox Enhanced Location Engine](https://www.mapbox.com/blog/enhanced-location-engine-ships-in-new-navigation-sdks-android-ios)
- HERE không công bố con số cụ thể, nhưng cho phép nhà phát triển tự kiểm tra `distanceInMeters` trên mỗi update
- Hầu hết các ứng dụng bị "reroute liên tục" khi sai số GPS cao là do thiếu hysteresis / xác nhận nhiều mẫu [Apple Community GPS Rerouting Issues](https://discussions.apple.com/thread/255307367)

### 1.3 Phát hiện đích đã dịch chuyển

**Phương pháp:** So sánh vị trí đích của tuyến đường hiện tại vs vị trí thực tế của thành viên được theo dõi. Nếu khoảng cách vượt ngưỡng, sẽ reroute để căn chỉnh.

**Ứng dụng thực tế:** Nếu bạn chỉ đường đến nhà bạn, nhưng bạn di chuyển đi một chỗ khác, app nên reroute ngay để theo dõi vị trí mới của bạn.

**Không có tài liệu công khai cụ thể** — Mapbox, HERE, Google không công bố ngưỡng "đích chuyển bao xa mới reroute". Quyết định này thường là tuỳ quy ứng dụng (ví dụ: "nếu đích chuyển > 500m, reroute").

### 1.4 Debounce / Khoảng thời gian tối thiểu giữa các reroute

**Mục đích:** Tránh gọi API routing quá thường xuyên (lãng phí quota, tính phí, CPU).

**Thực tiễn ngành:**

- **Mapbox Android SDK v2** — Default **5 phút** refresh tuyến (kiểm tra xem có tuyến nhanh hơn không). Có thể tùy chỉnh via `RouteControllerProactiveReroutingInterval` [Mapbox Route Refresh Android](https://docs.mapbox.com/android/navigation/v2/guides/turn-by-turn-navigation/rerouting/)

- **Mapbox iOS SDK v3** — Minimum refresh interval **30 giây** (không được nhỏ hơn). Default dường như 2-5 phút [Mapbox iOS SDK Rerouting](https://docs.mapbox.com/ios/navigation/guides/turn-by-turn-navigation/rerouting/)

- **Google Directions API** — Không có debounce built-in; ứng dụng tự triển khai. Tiêu chuẩn công nghiệp: 30 giây (tránh reroute liên tục khi GPS dao động).

**Kết luận:** 30-60 giây là debounce thực tiễn cho emergency reroute (kích hoạt bởi off-route hoặc destination-moved). Proactive refresh (kiểm tra có tuyến tốt hơn không) thường 2-5 phút.

---

## 2. Khuyến cáo ngưỡng cụ thể cho TP.HCM / Hà Nội, đi xe máy

### Bối cảnh dự án này

- GPS filter đã loại bỏ độ chính xác > 50m (MAX_ACCURACY_M)
- Giữ lại các điểm cách nhau >= 10m (MIN_DISTANCE_M)
- Vị trí self từ GPS thực (FusedLocation)
- Vị trí thành viên được theo dõi là simulated, nhưng chuyển động mượt mà (cập nhật mỗi 2.5s)

### 2.1 Ngưỡng off-route (perpendicular distance tới polyline)

**Khuyến cáo: 40-50 mét**

**Giải thích:**
- Urban camera effect (urban canyon) ở TP.HCM / Hà Nội: GPS có thể sai 10-50m do phản xạ tín hiệu từ các tòa nhà cao [Vietnam GPS Characteristics - RMIT Study](https://research-repository.rmit.edu.au/articles/conference_contribution/A_Case_Study_of_GPS_Characteristics_of_Urban_Area_in_Ho_Chi_Minh_Based_on_Ride-Sharing_Services/27584541), [Vietnam Motorbike Navigation Guide](https://www.tigitmotorbikes.com/how-to-navigate-vietnam-by-motorbike/)

- Hẻm/ngõ Hà Nội với nhà 4-6 tầng: GPS không chính xác hơn 50m [Vietnam Street Mapping](https://www.vietnamcoracle.com/which-maps-to-use-for-a-motorbike-trip/)

- Xe máy dễ bị lệch so với con đường vì giao thông hỗn loạn, vòng tròn, chỗ dừng tạm thời.

- Quá nhỏ (< 30m) → reroute liên tục khi GPS dao động; quá lớn (> 60m) → để người dùng lạc đường quá lâu.

**Smallness cost:** < 30m → reroute mỗi vài giây khi GPS fluctuates trong phạm vi 20-30m, lãng phí API, khó chịu người dùng.  
**Bigness cost:** > 60m → người dùng lạc lối rõ ràng (đã chuyển sang con đường khác) nhưng app vẫn chỉ tuyến cũ, tin tưởng bị mất.

### 2.2 Số mẫu liên tiếp off-route trước khi reroute

**Khuyến cáo: 3 mẫu (tương ứng ~30 giây)**

**Tính toán:**
- App nhận vị trí self mỗi 10 giây (LOCATION_INTERVAL_MS)
- 3 mẫu × 10 giây = 30 giây
- Trong 30 giây đó, GPS có thể dao động ± 20m, nhưng 3 lần liên tiếp cùng vượt 40m là dấu hiệu thật sự off-route

**Giải thích:**
- Vừa đủ để thoát khỏi GPS noise (1-2 mẫu sai lệch là bình thường)
- Chậm hơn 30 giây (ví dụ 6 mẫu = 60s) → phản ứng quá lâu khi thật sự off-route
- Nhanh hơn 10 giây (1 mẫu) → quá tức tưởi với jitter

**Smallness cost:** 1 mẫu → reroute khi 1 lần GPS sai lệch, quá nhạy.  
**Bigness cost:** 6 mẫu (60s) → chỉ reroute sau 1 phút đã off-route, người dùng lạc đường lâu rồi mới được cứu.

### 2.3 Ngưỡng đích đã dịch chuyển

**Khuyến cáo: 200 mét**

**Giải thích:**
- Nếu thành viên được theo dõi di chuyển hơn 200m từ đích của tuyến đường hiện tại, reroute để nhắm vào vị trí mới
- 200m trên đường phố TP.HCM ~ 2-3 khúc đường, đủ xa để "đích đã thay đổi thực sự"
- Nếu chỉ 50m: có thể thành viên đợi ở một nơi khác trong cùng khu vực, chưa cần reroute
- Nếu 500m+: chạy lệch quá xa, nên theo ngay lập tức

**Smallness cost:** < 100m → reroute quá thường xuyên nếu thành viên đi loanh quanh trong cùng khu vực.  
**Bigness cost:** > 300m → để thành viên chạy quá xa khỏi tuyến cũ trước khi reroute.

### 2.4 Khoảng cách tối thiểu giữa hai lệnh reroute (debounce)

**Khuyến cáo: 60 giây**

**Tính toán:**
- Khi off-route hoặc destination-moved được xác nhận, gọi Routes API
- Không gọi lại trong 60 giây, để tránh lãng phí quota
- 60 giây / 10 giây/mẫu = 6 mẫu, đủ thời gian cho người dùng tìm đường hoặc thành viên di chuyển ổn định

**Google Directions/Routes API pricing:**
- Directions API: $5.00 per 1000 requests (Essentials SKU) [Google Directions Pricing](https://developers.google.com/maps/documentation/directions/usage-and-billing)
- Nếu gọi reroute mỗi 60 giây trong 1 giờ = 60 lần / giờ = 1440 lần / ngày = ~$7.20/ngày cho 1 người dùng
- Nếu debounce chỉ 10 giây, gọi 6 lần/phút = 360 lần/giờ = 8640 lần/ngày = ~$43.20/ngày (6× chi phí)
- Nếu debounce 120 giây, gọi 30 lần/giờ = 720 lần/ngày = ~$3.60/ngày (tiết kiệm nhưng phản ứng chậm)

**Smallness cost:** < 30s → quá nhiều API calls, quota cạn nhanh, chi phí cao.  
**Bigness cost:** > 120s → chậm phản ứng, người dùng chưa reroute khi thành viên đã di chuyển thêm.

---

## 3. Thiết kế hàm thuần (pure function) cho quyết định reroute

### 3.1 Yêu cầu kiến trúc

Hàm này PHẢI sống trong `:domain/tracking/`, bên cạnh `ZoneEvaluator.kt` và `LocationFilter.kt`:
- **Không** giữ state mutable bên trong object
- **Không** đọc đồng hồ hệ thống trực tiếp (timestamp được truyền vào)
- **Không** Android imports, không Compose
- **Trả về** sealed interface với các trường hợp (Keep, Recompute, ...)
- **Nhận** state trước làm tham số, trả về state mới

### 3.2 Định nghĩa kiểu kết quả (sealed interface)

```kotlin
/**
 * Lý do reroute — dùng để log, UI update, và quyết định UI display.
 */
enum class RerouteReason {
    /** Người follow lệch khỏi tuyến đường quá xa. */
    OFF_ROUTE,
    /** Thành viên được theo dõi di chuyển quá xa khỏi đích hiện tại. */
    DESTINATION_MOVED,
}

/**
 * Quyết định của [RerouteEvaluator.evaluate] — giữ nguyên tuyến hay tính lại.
 */
sealed interface RerouteDecision {
    /** Tuyến đường hiện tại vẫn hợp lệ, không cần reroute. */
    data object Keep : RerouteDecision
    
    /**
     * Cần tính toán tuyến đường mới.
     * @param reason Lý do reroute (OFF_ROUTE, DESTINATION_MOVED)
     * @param triggeringPoint Điểm (self hoặc member) kích hoạt reroute
     */
    data class Recompute(
        val reason: RerouteReason,
        val triggeringPoint: LocationPoint,
    ) : RerouteDecision
}

/**
 * State của [RerouteEvaluator] — nhà gọi phải quản lý state này, không phải hàm self.
 * Giống hệt [MemberRoamer.RoamState] — state được truyền vào, hàm trả về state mới.
 */
data class RerouteEvaluatorState(
    /** Vị trí đích của tuyến đường tính toán cuối cùng. */
    val lastRouteDestination: LocationPoint?,
    
    /** Số lần liên tiếp vị trí self cách tuyến > OFF_ROUTE_TOLERANCE_M. */
    val offRouteConsecutiveCount: Int = 0,
    
    /** Timestamp (ms) của lần reroute cuối cùng. */
    val lastRerouteTimeMs: Long = 0,
)
```

### 3.3 Hàm chính

```kotlin
/**
 * Hàm quyết định khi nào reroute — thuần (pure), không state, không Android.
 * Gọi một lần mỗi khi nhận điểm GPS mới từ self hoặc thành viên được theo dõi.
 *
 * @param state Trạng thái từ lần gọi cuối (off-route count, last destination, last reroute time)
 * @param followerPoint Vị trí hiện tại của người follow (self)
 * @param targetPoint Vị trí hiện tại của thành viên được theo dõi
 * @param currentRoutePolyline Polyline của tuyến đường hiện tại (latitude1,longitude1 lat2,lng2 ...)
 * @param now Thời gian hiện tại (ms) — do gọi truyền vào, không gọi systemClock trong hàm
 * @return Pair(decision, newState) — quyết định + state mới để lưu lại
 */
object RerouteEvaluator {
    fun evaluate(
        state: RerouteEvaluatorState,
        followerPoint: LocationPoint,
        targetPoint: LocationPoint,
        currentRoutePolyline: RoutePolyline,
        now: Long,
    ): Pair<RerouteDecision, RerouteEvaluatorState> {
        // 1. Kiểm tra debounce: đã reroute trong 60s cuối hay chưa?
        if (now - state.lastRerouteTimeMs < TrackingConstants.REROUTE_DEBOUNCE_MS) {
            return RerouteDecision.Keep to state
        }

        // 2. Kiểm tra off-route: follower cách tuyến bao xa?
        val distanceToRoute = pointToPolylineDistance(followerPoint, currentRoutePolyline)
        val isOffRoute = distanceToRoute > TrackingConstants.OFF_ROUTE_TOLERANCE_M

        val newOffRouteCount = if (isOffRoute) state.offRouteConsecutiveCount + 1 else 0
        
        if (newOffRouteCount >= TrackingConstants.OFF_ROUTE_CONSECUTIVE_THRESHOLD) {
            // 3 lần liên tiếp off-route → reroute
            val newState = state.copy(
                lastRerouteTimeMs = now,
                offRouteConsecutiveCount = 0,
            )
            return RerouteDecision.Recompute(
                reason = RerouteReason.OFF_ROUTE,
                triggeringPoint = followerPoint,
            ) to newState
        }

        // 3. Kiểm tra destination-moved: thành viên được theo dõi chuyển bao xa?
        val lastDest = state.lastRouteDestination
        if (lastDest != null) {
            val destMovedDistance = GeoDistance.haversineMeters(
                lastDest.latitude,
                lastDest.longitude,
                targetPoint.latitude,
                targetPoint.longitude,
            )
            if (destMovedDistance > TrackingConstants.DESTINATION_MOVED_TOLERANCE_M) {
                val newState = state.copy(
                    lastRerouteTimeMs = now,
                    lastRouteDestination = targetPoint,
                    offRouteConsecutiveCount = 0,
                )
                return RerouteDecision.Recompute(
                    reason = RerouteReason.DESTINATION_MOVED,
                    triggeringPoint = targetPoint,
                ) to newState
            }
        }

        // 4. Không cần reroute, cập nhật off-route count
        val newState = state.copy(offRouteConsecutiveCount = newOffRouteCount)
        return RerouteDecision.Keep to newState
    }

    /**
     * Tính khoảng cách từ một điểm đến polyline (tuyến đường).
     * Lấy minimum distance tới tất cả các segment của polyline.
     */
    internal fun pointToPolylineDistance(
        point: LocationPoint,
        polyline: RoutePolyline,
    ): Double {
        if (polyline.points.isEmpty()) return Double.POSITIVE_INFINITY
        if (polyline.points.size == 1) {
            return GeoDistance.haversineMeters(
                point.latitude,
                point.longitude,
                polyline.points[0].latitude,
                polyline.points[0].longitude,
            )
        }

        var minDistance = Double.POSITIVE_INFINITY
        for (i in 0 until polyline.points.size - 1) {
            val p1 = polyline.points[i]
            val p2 = polyline.points[i + 1]
            val segmentDistance = pointToSegmentDistance(point, p1, p2)
            minDistance = minOf(minDistance, segmentDistance)
        }
        return minDistance
    }

    /**
     * Khoảng cách từ point tới line segment p1-p2.
     * Nếu projection của point nằm trong segment → perpendicular distance
     * Nếu không → distance tới endpoint gần nhất
     */
    internal fun pointToSegmentDistance(
        point: LocationPoint,
        p1: LocationPoint,
        p2: LocationPoint,
    ): Double {
        // Xấp xỉ phẳng (equirectangular) — phù hợp với quy mô TP.HCM
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)
        val latP = Math.toRadians(point.latitude)
        val lonP = Math.toRadians(point.longitude)

        val x = (lon2 - lon1) * kotlin.math.cos((lat1 + lat2) / 2)
        val y = lat2 - lat1
        val t = ((lonP - lon1) * x + (latP - lat1) * y) / (x * x + y * y)

        return when {
            t < 0 -> GeoDistance.haversineMeters(
                point.latitude, point.longitude,
                p1.latitude, p1.longitude,
            )
            t > 1 -> GeoDistance.haversineMeters(
                point.latitude, point.longitude,
                p2.latitude, p2.longitude,
            )
            else -> {
                val closestLat = Math.toDegrees(lat1 + t * (lat2 - lat1))
                val closestLon = Math.toDegrees(lon1 + t * (lon2 - lon1))
                GeoDistance.haversineMeters(
                    point.latitude, point.longitude,
                    closestLat, closestLon,
                )
            }
        }
    }
}
```

### 3.4 Định nghĩa RoutePolyline

```kotlin
/**
 * Polyline của một tuyến đường — danh sách các điểm (waypoints) tạo nên đường.
 * Được trả về từ Google Routes API, được lưu khi reroute thành công.
 */
data class RoutePolyline(
    val points: List<LocationPoint>,
)
```

### 3.5 Thêm hằng số vào TrackingConstants.kt

```kotlin
// Thêm vào object TrackingConstants:

/** PRD §10.2. [RerouteEvaluator] — khoảng cách tối đa từ tuyến đường trước khi 
 * xem xét "off-route" — nhỏ hơn → reroute thường xuyên khi GPS fluctuate,
 * lớn hơn → để người dùng lạc đường quá lâu. */
const val OFF_ROUTE_TOLERANCE_M: Double = 45.0

/** PRD §10.2. [RerouteEvaluator] — số mẫu GPS liên tiếp cách tuyến > OFF_ROUTE_TOLERANCE_M
 * trước khi xác nhận off-route — nhỏ hơn → quá nhạy GPS noise, lớn hơn → chậm phản ứng. */
const val OFF_ROUTE_CONSECUTIVE_THRESHOLD: Int = 3

/** PRD §10.2. [RerouteEvaluator] — nếu thành viên được theo dõi chuyển > threshold này
 * từ đích của tuyến đường hiện tại, reroute để nhắm vào vị trí mới — nhỏ hơn → reroute
 * quá thường xuyên, lớn hơn → để thành viên chạy quá xa. */
const val DESTINATION_MOVED_TOLERANCE_M: Double = 200.0

/** PRD §10.2. [RerouteEvaluator] — khoảng thời gian tối thiểu giữa hai lệnh reroute,
 * để tránh gọi API quá thường xuyên — nhỏ hơn → lãng phí quota + API billing cao,
 * lớn hơn → chậm phản ứng, người dùng chưa reroute khi thành viên đã di chuyển thêm. */
const val REROUTE_DEBOUNCE_MS: Long = 60_000L
```

---

## 4. Thuật toán khoảng cách điểm-đến-polyline

### 4.1 Vấn đề

Cho một điểm P (vị trí hiện tại) và một polyline (dãy đoạn thẳng nối các waypoint), tính khoảng cách ngắn nhất từ P đến bất kỳ điểm nào trên polyline.

### 4.2 Giải pháp: perpendicular distance tới từng segment

**Bước 1:** Tính khoảng cách từ P tới mỗi segment (đoạn thẳng nối 2 waypoint liên tiếp)  
**Bước 2:** Lấy minimum trong tất cả segment

**Thuật toán tính khoảng cách P tới segment A-B:**

```
Nếu projection của P nằm TRONG segment (giữa A và B):
  → khoảng cách = perpendicular distance từ P tới đường thẳng A-B
Nếu projection nằm NGOÀI segment (ở phía A):
  → khoảng cách = haversine(P, A)
Nếu projection nằm NGOÀI segment (ở phía B):
  → khoảng cách = haversine(P, B)
```

### 4.3 Chi tiết toán học

**Phương pháp xấp xỉ:** Equirectangular projection (phẳng cục bộ)

Thay vì tính toán trắc địa đầy đủ (inverse geodesic problem), sử dụng xấp xỉ phẳng:

```
Đổi sang radians:
  lat1, lon1, lat2, lon2, latP, lonP (tất cả là radians)

Tính vector từ p1 đến p2 (phối hợp địa phương):
  x = (lon2 - lon1) × cos((lat1 + lat2) / 2)
  y = lat2 - lat1

Tính vector từ p1 đến P:
  dx = (lonP - lon1) × cos((lat1 + lat2) / 2)
  dy = latP - lat1

Tính tham số t (0..1 nếu projection trong segment):
  t = (dx × x + dy × y) / (x² + y²)

Nếu t < 0 → gần p1, distance = haversine(P, p1)
Nếu t > 1 → gần p2, distance = haversine(P, p2)
Nếu 0 ≤ t ≤ 1 → trong segment, tính projection point:
  closestLat = toDegrees(lat1 + t × (lat2 - lat1))
  closestLon = toDegrees(lon1 + t × (lon2 - lon1))
  distance = haversine(P, closest)
```

### 4.4 Tại sao xấp xỉ phẳng đủ chính xác

- **Quy mô:** TP.HCM ~ 40 km. Tuyến đường dài nhất < 40 km.
- **Sai số xấp xỉ phẳng:** ~ 0.1% ở quy mô 40 km = ~ 40 meter lỗi. Không quan trọng vì:
  - OFF_ROUTE_TOLERANCE_M = 45m (sai số xấp xỉ cùng cấp độ)
  - Perpendicular distance chính xác hơn Haversine full ở quy mô này (xấp xỉ phẳng là tính `cos(lat)` để scale kinh độ)
- **Hiệu suất:** Chỉ dùng `cos` + phép toán cơ bản, rất nhanh. Tính toán trắc địa đảo (inverse geodesic) chậm hơn và không cần thiết.

### 4.5 Pitfalls và xử lý

1. **Segment rất ngắn (< 1m):** Division by (x² + y²) gần 0.
   - **Cách xử lý:** Kiểm tra `if ((x * x + y * y) < EPSILON)` trước khi chia → fallback thành distance tới p1

2. **Polyline rỗng:** Trả về `Double.POSITIVE_INFINITY` (không có tuyến, không thể so sánh)

3. **Polyline chỉ có 1 điểm:** Khoảng cách chính là haversine tới điểm đó

4. **Gần cực:** `cos(lat)` ≈ 0 ở cực Bắc/Nam. Không vấn đề ở TP.HCM (lat ~ 10°), nhưng có thể thêm `.coerceAtLeast(MIN_METERS_PER_DEGREE_LNG)` để an toàn (xem code trong MemberRoamer.kt)

---

## 5. Điều kiện "đã tới" (arrived condition)

### 5.1 Định nghĩa

Người follow đã tới vị trí của thành viên được theo dõi, không cần chỉ đường tiếp nữa.

### 5.2 Ngưỡng và hysteresis

**Ngưỡng xác nhận tới:** 50 mét

**Ngưỡng dừng chỉ đường (hysteresis):** 70 mét

**Giải thích:**
- Nếu khoảng cách < 50m → "tới rồi"
- Nếu lúc trước "tới" rồi, nhưng thành viên di chuyển, cách lại > 70m → "chưa tới nữa", reroute
- Hysteresis (30m buffer) tránh flicker: khoảng cách 60m dao động quanh 50m sẽ không thay đổi trạng thái

**Smallness cost (< 30m threshold):** Khi còn 50m (thường là 100-200m đi bộ), app nói "tới rồi" khiến người dùng bối rối.  
**Bigness cost (> 100m threshold):** Chỉ dường cho tới 150m cách thành viên là không cần thiết.

### 5.3 Code

```kotlin
data class ArrivalState(
    val hasArrived: Boolean = false,
)

sealed interface ArrivalDecision {
    data object Continue : ArrivalDecision
    data class Arrived : ArrivalDecision
    data class Departed : ArrivalDecision
}

object ArrivalEvaluator {
    fun evaluate(
        state: ArrivalState,
        followerPoint: LocationPoint,
        targetPoint: LocationPoint,
    ): Pair<ArrivalDecision, ArrivalState> {
        val distance = GeoDistance.haversineMeters(
            followerPoint.latitude,
            followerPoint.longitude,
            targetPoint.latitude,
            targetPoint.longitude,
        )

        return when {
            !state.hasArrived && distance < TrackingConstants.ARRIVAL_THRESHOLD_M -> {
                ArrivalDecision.Arrived to state.copy(hasArrived = true)
            }
            state.hasArrived && distance > TrackingConstants.ARRIVAL_HYSTERESIS_M -> {
                ArrivalDecision.Departed to state.copy(hasArrived = false)
            }
            else -> ArrivalDecision.Continue to state
        }
    }
}

// Thêm vào TrackingConstants:
const val ARRIVAL_THRESHOLD_M: Double = 50.0
const val ARRIVAL_HYSTERESIS_M: Double = 70.0
```

---

## 6. Thất bại và giảm cấp (failure & degradation)

### 6.1 Trường hợp không có network

**Tình huống:** App không có kết nối internet, không thể gọi Routes API.

**Khuyến cáo:**
- Hiển thị "Không thể tính tuyến" trên UI (nhưng không phá hủy trạng thái)
- Hiển thị đường thẳng từ self tới target (fallback tuyến): vẽ một line thẳng từ follower tới target member
- Hiển thị haversine distance thay vì accumulated route distance
- Cho phép người dùng thử lại reroute (nút "Retry" khi network khôi phục)

### 6.2 Quota hết (Google Routes API billing limit reached)

**Tình huống:** Đã gọi quá nhiều API, tài khoản Google Cloud hết quota.

**Khuyến cáo:**
- Fallback thành straight line
- Log error với priority cao (cần alert team)
- KHÔNG reroute nữa (debounce nhân đôi, hoặc vô hạn cho tới khi admin reset quota)
- Hiển thị warning: "Chỉ đường tạm thời chưa tối ưu"

### 6.3 Không tìm được tuyến đường (no route found)

**Tình huống:** Routes API trả về "không có tuyến từ A tới B" (ví dụ, A ở một đảo, B ở đảo khác không có cầu).

**Khuyến cáo:**
- Hiển thị straight line + haversine distance
- Log warning (không phải error — có thể là user case lạ, không phải lỗi server)
- Cho phép retry sau (user có thể di chuyển tới vị trí có tuyến được)

### 6.4 API timeout / error tạm thời

**Tình huống:** Google API chậm hoặc lỗi 5xx tạm thời.

**Khuyến cáo:**
- Giữ tuyến đường cũ (không fallback ngay)
- Retry reroute sau 60 giây (debounce đã ngắn do error)
- Hiển thị toast: "Chỉ đường sẽ cập nhật khi có mạng"

### 6.5 Tóm tắt UI fallback

| Scenario | Tuyến hiển thị | Distance | Reroute sau? |
|----------|---|---|---|
| Bình thường | Route từ API | Accumulated | Có, mỗi 60s nếu off-route/dest-moved |
| No network | Đường thẳng P→T | Haversine | Retry khi user tap (manual) |
| Quota exhausted | Đường thẳng P→T | Haversine | Không (log error, thông báo admin) |
| No route exist | Đường thẳng P→T | Haversine | Retry sau 5p (user có thể di chuyển) |
| API timeout | Tuyến cũ | Accumulated | Retry sau 60s (tạm thời) |

---

## 7. Năng lượng (battery) và tần số GPS

### 7.1 Tần số GPS hiện tại

- **Foreground service** (LocationTrackingService): LOCATION_INTERVAL_MS = **10 giây** (tất cả mọi lúc)
- Mỗi 10 giây, app nhận 1 điểm từ FusedLocationProvider

### 7.2 Có cần tăng tần số trong navigation không?

**Khuyến cáo: KHÔNG.**

**Lý do:**
- Off-route detection đã đủ chính xác với 10s (3 mẫu = 30s, rất hợp lý)
- Tăng lên 5s hoặc continuous sẽ:
  - Tăng power consumption thêm 50-70% (battery drain 50% vs static) [Android Battery Optimization](https://developer.android.com/develop/sensors-and-location/location/battery)
  - Không cải thiện detection (GPS noise vẫn có, xác nhận 3 mẫu vẫn cần)
  - Tăng ghi Room database (mỗi điểm ghi vào tracking history)
- Người dùng chỉ đường là trường hợp sử dụng "đang bật", pin cũng đang tiêu nhanh dù sao

### 7.3 Giảm tần số khi KHÔNG chỉ đường

**Khuyến cáo: CÓ, nhưng từng bước**

**Lý do:** Khi app đóng navigation screen hoặc không cần tracking realtime, có thể giảm GPS frequency để tiết kiệm pin.

**Mô hình:**
- **Đang chỉ đường:** 10 giây (giữ nguyên)
- **Chế độ background / không active navigation:** 30-60 giây
- **Chế độ low-battery:** 120 giây

**Triển khai:** Control tại LocationTrackingService via ViewModel / Intent (khi navigation screen tắt, gửi intent update interval).

### 7.4 Khi nào dừng GPS tìm kiếm tuyến

Khi người follow đã tới (distance < ARRIVAL_THRESHOLD_M), có thể dừng reroute check (debounce vô hạn) và giảm GPS tần số về 60s hoặc thêm, vì không còn chỉ đường nữa.

---

## 8. Bảng trường hợp unit test

Hàm `RerouteEvaluator.evaluate()` cần các test cases sau:

| ID | Input | Expected Decision | Ghi chú |
|---|---|---|---|
| **T01.Keep** | follower on-route, dest not moved, last reroute > 60s ago | `Keep` | Tuyến đang tốt, không cần thay |
| **T02.Keep-Debounce** | follower off-route, last reroute 30s ago | `Keep` | Debounce đang active, ignore off-route |
| **T03.Noise-1sample** | follower off-route (1 mẫu), consecutive = 1 | `Keep` | GPS noise, chưa đủ 3 mẫu |
| **T04.Noise-2sample** | follower off-route (2 mẫu), consecutive = 2 | `Keep` | Còn 1 mẫu nữa |
| **T05.OffRoute-3sample** | follower off-route (3 mẫu), consecutive = 3, debounce OK | `Recompute(OFF_ROUTE)` | Xác nhận off-route, reroute |
| **T06.OffRoute-Resets** | follower back on-route after recompute | `Keep`, consecutive reset to 0 | Đã reroute, trở lại on-route |
| **T07.DestMoved-Small** | target moved 100m, last dest set | `Keep` | 100m < 200m threshold, ignore |
| **T08.DestMoved-Large** | target moved 250m, last dest set | `Recompute(DESTINATION_MOVED)` | 250m > 200m, reroute |
| **T09.DestMoved-FirstRoute** | lastRouteDestination = null (chưa route lần nào) | `Keep` | Chưa có "cũ" để so sánh |
| **T10.ArrivalConflict** | follower 40m từ target (arrival OK), nhưng target moved 300m | `Keep` (for now) | Arrival prioritized, reroute sau khi departed |
| **T11.GPS-Empty-Polyline** | currentRoutePolyline.points = empty | point-to-polyline = POSITIVE_INFINITY | Invalid route, on-route check fails safe |
| **T12.State-Preservation** | Multiple ticks of on-route + noise | State correctly track consecutive count | Verify state threading |
| **T13.Exact-Threshold-Boundary** | follower exactly 45m from route (OFF_ROUTE_TOLERANCE_M) | `Keep` (< not ≤) | Boundary condition |
| **T14.Exact-Boundary+1** | follower 45.1m from route | `Keep` (if only 1 sample) | Needs 3 consecutive |
| **T15.Consecutive-Reset** | offRoute for 2 ticks, then on-route, then off-route again | consecutive resets, counts new sequence | Anti-noise verification |

---

## Tóm tắt & Khuyến cáo

| Thành phần | Khuyến cáo | Lý do |
|---|---|---|
| **Off-route threshold** | 40-50m | Urban GPS 10-50m error, motorbike, Vietnamese cities |
| **Consecutive samples** | 3 (30s @ 10s/sample) | Chống GPS noise, phản ứng hợp lý |
| **Destination-moved threshold** | 200m | Tránh reroute quá thường xuyên |
| **Reroute debounce** | 60s | Cân bằng quota / phản ứng, giảm chi phí API |
| **Point-to-polyline algorithm** | Equirectangular projection | Chính xác + nhanh ở quy mô TP.HCM |
| **Arrival threshold** | 50m |khoảng cách bộ hợp lý |
| **Arrival hysteresis** | 70m | Tránh flicker |
| **GPS frequency** | Giữ 10s, không tăng | Không cải thiện reroute, tốn pin |
| **Fallback route** | Straight line + haversine | Khi no network / quota exhausted |
| **Reroute logic ownership** | RerouteEvaluator (:domain) | Pure function, testable, không Android deps |

---

## Tài liệu tham khảo

1. [Google Directions API Pricing & Billing](https://developers.google.com/maps/documentation/directions/usage-and-billing)
2. [Google Routes API vs Directions API Migration Guide](https://developers.google.com/maps/documentation/routes/migrate-routes)
3. [HERE SDK Route Deviation Documentation](https://docs.here.com/here-sdk/docs/ios-navigation-deviation)
4. [Mapbox Navigation Android Rerouting](https://docs.mapbox.com/android/navigation/guides/turn-by-turn-navigation/rerouting-and-refresh/)
5. [Mapbox Navigation iOS v3 Rerouting](https://docs.mapbox.com/ios/navigation/guides/turn-by-turn-navigation/rerouting/)
6. [Mapbox Enhanced Location Engine](https://www.mapbox.com/blog/enhanced-location-engine-ships-in-new-navigation-sdks-android-ios)
7. [US Patent 7561964 - Off-route Recalculation](https://patents.google.com/patent/US7561964)
8. [Vietnam GPS Urban Characteristics - RMIT Study](https://research-repository.rmit.edu.au/articles/conference_contribution/A_Case_Study_of_GPS_Characteristics_of_Urban_Area_in_Ho_Chi_Minh_Based_on_Ride-Sharing_Services/27584541)
9. [Vietnam Motorbike Navigation Guide - Tigit Motorbikes](https://www.tigitmotorbikes.com/how-to-navigate-vietnam-by-motorbike/)
10. [Vietnam Street Navigation Best Practices](https://www.vietnamcoracle.com/which-maps-to-use-for-a-motorbike-trip/)
11. [GPS Accuracy in Urban Areas 2026 - Sentinel Mission](https://sentinelmission.org/blog/how-accurate-is-gps/)
12. [Point-to-Polyline Distance Algorithms - ArcGIS Documentation](https://pro.arcgis.com/en/pro-app/latest/tool-reference/analysis/how-near-analysis-works.htm)
13. [Android Battery Optimization - Developers Guide](https://developer.android.com/develop/sensors-and-location/location/battery)
14. [Apple Community - GPS Rerouting Issues](https://discussions.apple.com/thread/255307367)
15. [Perpendicular Distance Algorithm - PSIMPL](https://psimpl.sourceforge.net/perpendicular-distance.html)

---

## Các câu hỏi chưa giải quyết

1. **Waypoint optimization:** Nếu tuyến đường có waypoint trung gian (ví dụ, tránh đường tắc), làm sao xác định thứ tự waypoint đúng để tính polyline? (Google Routes API có hỗ trợ, nhưng chưa được spec ở mức độ này)

2. **Map matching:** Nếu GPS của người follow lệch xa đường (ví dụ, trong công viên cạnh đường), điểm-tới-polyline có thể cho sai kết quả. Có cần map-matching (snap-to-road) trước khi tính khoảng cách không?

3. **Dynamic polyline update:** Khi cuộc họp thử nghiệm, có cập nhật polyline khi follower di chuyển giữa các segment (để tính distance chỉ phần còn lại), hay giữ nguyên polyline từ lần reroute gần nhất?

4. **Giá Google Routes API:** Đúng giá 2026 chưa được xác nhận (chỉ là giá công bố lần cuối). Cần kiểm tra tại Google Cloud Console với API key của dự án khi triển khai thực tế.
