package com.example.pion.family.tracker.demo.data.routing

import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.model.AppError
import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Directions
import com.example.pion.family.tracker.demo.domain.model.GeoPoint
import com.example.pion.family.tracker.demo.domain.model.RouteSourceInfo
import com.example.pion.family.tracker.demo.domain.model.RouteSourceKind
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteProvider
import com.example.pion.family.tracker.demo.domain.repository.MemberRouteRequest
import com.example.pion.family.tracker.demo.domain.repository.RoutingProvider
import com.example.pion.family.tracker.demo.domain.repository.SimulatedRouteRepository
import com.example.pion.family.tracker.demo.domain.tracking.MemberRoamer
import com.example.pion.family.tracker.demo.domain.tracking.RouteGeometryGuard
import com.example.pion.family.tracker.demo.domain.tracking.SyntheticPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Nguồn tuyến 3 tầng của D5 (`decisions.md` §C2) — số hiệu tầng khớp NGUYÊN VĂN bảng đó, KHÔNG
 * theo thứ tự kiểm tra: **tầng 1** = `RoutingProvider` mạng, **tầng 2** = cache trên máy, **tầng 3**
 * = [SyntheticPath]. Implement CẢ HAI [MemberRouteProvider] (`:data` nội bộ, đọc bởi
 * `MemberMovementSimulator`) và [SimulatedRouteRepository] (`:domain` cổng, đọc bởi `:ui` phase-05)
 * trên CÙNG MỘT instance Koin — xem KDoc [SimulatedRouteRepository] cho lý do không tách hai lớp.
 * Ghi công tổng hợp theo TỪNG thành viên (không phải ghi-sau-thắng) — xem [RouteSourceAggregator].
 *
 * **Thứ tự KIỂM lại ngược số hiệu — cache (tầng 2) trước, provider (tầng 1) sau.** Đây là thứ tự
 * đúng của Architecture phase-04 (`decisions.md` §C2 Key Insight #2): FR-2 đòi "lần sau đọc cache,
 * không gọi mạng"; NFR-2/QA-SRM-36 đòi tổng request cả phiên là `thành viên × zone × 2`, **0 từ
 * vòng thứ hai trở đi** — cả hai chỉ đúng nếu một khoá đã có trong cache thì KHÔNG BAO GIỜ hỏi
 * provider lại. Đường SYNTHETIC → PROVIDER (S6/QA-SRM-17) vẫn sống: chặng synthetic không bao giờ
 * được ghi cache, nên lần kế tiếp là cache-miss và vẫn hỏi provider bình thường.
 *
 * Chặng `WANDER` KHÔNG BAO GIỜ gọi mạng lẫn đọc cache — [wander] luôn [SyntheticPath] (phase-04
 * Implementation Step 6, một nửa luật hạn ngạch NFR-2), nhưng VẪN cập nhật nguồn hiện tại của thành
 * viên đó qua [RouteSourceAggregator] — thiếu bước này thì một thành viên đang lang thang vẫn bị
 * tính là đang chạy nguồn OSM cũ.
 *
 * **Không bao giờ ném lỗi mạng ra ngoài.** Mọi thất bại của [routingProvider] (thiếu khoá, 401, 429,
 * 400, timeout) được hấp thụ ở đây và rơi xuống tầng dưới (FR-3) — người gọi
 * (`MemberMovementSimulator`) luôn nhận được MỘT dãy điểm, không bao giờ một exception.
 *
 * **Không log toạ độ** (gate G7) — hai HỌ dòng log: `sim_route_loaded source=… pointCount=N` khi
 * một tầng thành công (CHO MỘT THÀNH VIÊN, không phải trạng thái tổng hợp), `sim_route_failed
 * reason=…` khi tầng 1/2 bị từ chối trước khi rơi xuống tầng kế — `reason` là một trong `TIMEOUT`,
 * `GEOMETRY_CACHE`, `GEOMETRY_PROVIDER`, `STALE_ORIGIN`, hoặc `NETWORK:`/`VALIDATION:`/
 * `NOT_FOUND:`/`UNEXPECTED:` (message đã lọc, xem [sanitizeRoutingErrorMessage]).
 *
 * **`reason=GEOMETRY` tách theo nhánh (2026-08-26, đo thật cửa sổ 10 phút, cache ấm: 3 dòng GEOMETRY
 * trên 8 chặng) vì CHI PHÍ hai nhánh khác nhau** — cache (tầng 2): đọc file, không tốn request;
 * provider (tầng 1): đã gọi mạng rồi mới vứt kết quả, tốn đúng một credit. Gộp một chuỗi làm
 * QA-SRM-36 chỉ đếm được một khoảng, không ra một con số. `STALE_ORIGIN` cũng thuộc tầng 2, không
 * tốn request, giữ chuỗi riêng — không gộp vào `GEOMETRY_CACHE`. **Công thức đếm request tới nhà
 * cung cấp:**
 * ```
 * số request = count(sim_route_loaded source=PROVIDER)
 *            + count(sim_route_failed reason=GEOMETRY_PROVIDER)
 *            + count(sim_route_failed reason=TIMEOUT)
 *            + count(sim_route_failed reason=NETWORK:*|VALIDATION:*|NOT_FOUND:*|UNEXPECTED:*)
 * ```
 * (`GEOMETRY_CACHE`/`STALE_ORIGIN` không cộng vào công thức trên — không tốn request nào.)
 */
class MemberRouteSource(
    private val routingProvider: RoutingProvider,
    private val cache: OnDevicePolylineCache,
) : MemberRouteProvider, SimulatedRouteRepository {

    private val aggregator = RouteSourceAggregator()

    override fun observeSource(): Flow<RouteSourceInfo> = aggregator.current

    override suspend fun path(request: MemberRouteRequest): List<GeoPoint> {
        val key = cacheKeyFor(request)

        cache.get(key)?.let { cached ->
            if (passesGeometryGuard(cached.points, request, GEOMETRY_CACHE_REASON) && passesOriginGuard(cached.points, request)) {
                resolve(request.memberId, RouteSourceKind.CACHE, cached.attribution, cached.points.size)
                return cached.points
            }
        }

        fromProvider(request)?.let { directions ->
            if (passesGeometryGuard(directions.points, request, GEOMETRY_PROVIDER_REASON)) {
                cache.put(key, directions.points, directions.attribution, directions.engineId)
                resolve(request.memberId, RouteSourceKind.PROVIDER, directions.attribution, directions.points.size)
                return directions.points
            }
        }

        val synthetic = SyntheticPath.between(request.from, request.to, seed = request.memberId.hashCode())
        resolve(request.memberId, RouteSourceKind.SYNTHETIC, emptyList(), synthetic.size)
        return synthetic
    }

    override suspend fun wander(memberId: String, from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val synthetic = SyntheticPath.between(from, to, seed = memberId.hashCode())
        resolve(memberId, RouteSourceKind.SYNTHETIC, emptyList(), synthetic.size)
        return synthetic
    }

    /**
     * `null` cho MỌI thất bại (lỗi mạng, timeout) — một nhánh lỗi bình thường, không phải trường hợp
     * đặc biệt (NFR-1, `withTimeoutOrNull` chứ không `withTimeout`). Log lý do ở ĐÚNG một chỗ trước
     * khi trả `null`.
     */
    private suspend fun fromProvider(request: MemberRouteRequest): Directions? {
        val result = withTimeoutOrNull(PROVIDER_TIMEOUT) { routingProvider.directions(request.from, request.to) }
        return when (result) {
            null -> {
                FtdLog.d(TAG, "sim_route_failed reason=TIMEOUT")
                null
            }
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                FtdLog.d(TAG, "sim_route_failed reason=${reasonFor(result.error)}")
                null
            }
        }
    }

    /**
     * `true` khi tuyến qua được [RouteGeometryGuard]; ngược lại log ĐÚNG một dòng
     * `sim_route_failed reason=$failureReason` rồi trả `false` để [path] rơi xuống tầng dưới.
     * [failureReason] PHẢI khác nhau giữa hai nơi gọi (`GEOMETRY_CACHE`/`GEOMETRY_PROVIDER`, xem
     * KDoc lớp "reason=GEOMETRY tách theo nhánh") — chi phí hai nhánh khác nhau, gộp một chuỗi thì
     * QA-SRM-36 không đếm được request thật.
     */
    private fun passesGeometryGuard(points: List<GeoPoint>, request: MemberRouteRequest, failureReason: String): Boolean {
        if (RouteGeometryGuard.isUsable(points, request.zone, request.kind)) return true
        FtdLog.d(TAG, "sim_route_failed reason=$failureReason")
        return false
    }

    /**
     * **P0 fix (2026-08-26)** — CHỈ áp dụng cho nhánh cache (tầng 2). Tầng 1 (provider)/tầng 3
     * (synthetic) luôn neo vào `request.from` hiện tại nên không cần kiểm; tầng 2 trả hình học đã
     * tính cho một `from` CŨ (khoá cache cố ý không chứa `from`), và trước bản sửa này
     * `MemberRoamer.withPath` đặt `pathCursorMeters = 0.0` vô điều kiện ⇒ thành viên bị dời thẳng về
     * đỉnh đầu polyline — đo thật hai cú nhảy **346.14 m** và **872.99 m** trên `emulator-5554`
     * (`reports/dev-phase-04-report.md` §0 "VIỆC 1"). Trượt guard này ⇒ coi cache như miss, rơi
     * xuống tầng 1 (provider) — tầng đó `cache.put` ĐÈ lên đúng khoá cũ với gốc mới, nên chặng lặp
     * lại lần sau lại trúng cache (tự lành).
     */
    private fun passesOriginGuard(points: List<GeoPoint>, request: MemberRouteRequest): Boolean {
        if (RouteGeometryGuard.startsNear(points, request.from, MemberRoamer.STEP_METERS)) return true
        FtdLog.d(TAG, "sim_route_failed reason=STALE_ORIGIN")
        return false
    }

    /** Cập nhật [aggregator] cho [memberId] rồi log `sim_route_loaded` — một chỗ gọi cho mọi tầng. */
    private fun resolve(memberId: String, kind: RouteSourceKind, attribution: List<String>, pointCount: Int) {
        aggregator.update(memberId, RouteSourceInfo(kind, attribution))
        FtdLog.d(TAG, "sim_route_loaded source=$kind pointCount=$pointCount")
    }

    /**
     * [AppError] không giữ mã HTTP nguyên văn — `RoutingErrorMapper` gộp 401/429/5xx vào
     * [AppError.Network] và 400/501 vào [AppError.Validation], không giữ lại con số. LUÔN có tiền tố
     * loại lỗi rồi mới tới message ĐÃ LỌC qua [sanitizeRoutingErrorMessage] (gate G7 — message có
     * thể echo lại toạ độ đã gửi, xem KDoc hàm đó). Giới hạn còn lại, biết trước, không sửa được ở
     * tầng này: **401 và 429 log ra CÙNG tiền tố `NETWORK:`** — `RoutingErrorMapper` đã gộp hai mã
     * đó từ trước phase-04.
     */
    private fun reasonFor(error: AppError): String = when (error) {
        is AppError.Network -> "NETWORK:${sanitizeRoutingErrorMessage(error.message)}"
        is AppError.Validation -> "VALIDATION:${sanitizeRoutingErrorMessage(error.message)}"
        is AppError.NotFound -> "NOT_FOUND:${sanitizeRoutingErrorMessage(error.message)}"
        is AppError.Unexpected -> "UNEXPECTED:${sanitizeRoutingErrorMessage(error.message)}"
    }

    private companion object {
        const val TAG = "FTD_EVENT"
        val PROVIDER_TIMEOUT = 10.seconds

        /** Nhánh cache (tầng 2) — đọc file, KHÔNG tốn request nào. Không gộp với [GEOMETRY_PROVIDER_REASON]. */
        const val GEOMETRY_CACHE_REASON = "GEOMETRY_CACHE"

        /** Nhánh provider (tầng 1) — mạng ĐÃ được gọi (tốn đúng một credit) trước khi kết quả bị vứt. */
        const val GEOMETRY_PROVIDER_REASON = "GEOMETRY_PROVIDER"
    }
}
