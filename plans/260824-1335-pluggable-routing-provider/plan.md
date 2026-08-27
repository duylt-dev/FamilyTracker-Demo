# Routing cắm-rút được (GraphHopper Cloud / Valhalla) trên nền Google Maps

**Ngày:** 2026-08-24 · **Trạng thái:** ✅ Đã verify, sẵn sàng implement · **Ưu tiên:** P1
**Verify lần cuối:** 2026-08-24 — POM đọc từ Maven Central, hai API gọi thật, fixture đã lưu.
Chi tiết những gì đã đo và những gì đã sửa: [`VERIFY-2026-08-24.md`](VERIFY-2026-08-24.md).

Bản đồ nền vẫn là **Maps SDK for Android**. Tuyến đường do **GraphHopper Cloud** (mặc định) hoặc
**Valhalla** tính từ dữ liệu OpenStreetMap, vẽ lên bản đồ đó bằng `Polyline` của maps-compose.
Cơ sở pháp lý và 5 điều kiện ràng buộc: [`docs/legal-memo-decision.md`](docs/legal-memo-decision.md);
hợp đồng tuân thủ ở runtime: [`../../docs/routing-and-map-attribution.md`](../../docs/routing-and-map-attribution.md).

**Plan này gộp luôn** [`260824-1137-realtime-navigation-to-member`](../260824-1137-realtime-navigation-to-member/plan.md)
(đã đánh dấu superseded). Tầng routing không có người tiêu thụ thì không demo được và không test
end-to-end được — tách hai plan chỉ tạo ra một tầng hạ tầng chết.

## Quyết định đã chốt

| # | Quyết định | Thay cho |
|---|---|---|
| 1 | **Enum theo *engine*** (`RoutingEngine.GRAPHHOPPER` / `VALHALLA`), hosting là chi tiết bên trong mỗi provider | Enum theo hosting của researcher-01 — xem VERIFICATION "Enum Design" |
| 2 | **Chọn provider lúc build**, `:app` đọc `BuildConfig` rồi đăng ký vào Koin — đúng tiền lệ `appConfigModule`/`simulatorEnabled` | Runtime DataStore (YAGNI), product flavor (nhân đôi biến thể build) |
| 3 | **GraphHopper Cloud là mặc định** — polyline precision 5 trùng chuẩn Google, ít đường sai nhất | Valhalla mặc định |
| 4 | **OkHttp 5.5.0**, KHÔNG Ktor | Ktor 3.5.2 (`kotlin-stdlib 2.3.21` > 2.2.10 của dự án). Cũng thay cho OkHttp 4.12.0 của bản nháp trước: 4.x đã dừng ở bản cuối 10/2023, và lý do từng dùng để loại 5.x là sai — xem phase-01 Key Insight #1 |
| 5 | Model tuyến đường tên **`Directions`**, KHÔNG phải `Route` | `Route` đã có nghĩa "chuyến đi lịch sử" trong repo (`RouteSplitter`, `RouteStats`, `RoutePolyline`) |
| 6 | Polyline **decode ở `:domain`**, `:ui` chỉ nhận `List<GeoPoint>` | `:ui` tự decode — đường dẫn thẳng tới lỗi lệch 10x của Valhalla |
| 7 | **Credit đi kèm dữ liệu**: `Directions.attribution: List<String>`, bắt buộc, không nullable | Dựng câu credit trong `:ui` từ `engineId` — GraphHopper đã tự trả `info.copyrights`, dùng đúng thứ họ đòi thay vì đoán hộ |

## Các phase

| Phase | Nội dung | Trạng thái |
|---|---|---|
| [01](phase-01-network-foundation-and-routing-port.md) | Version catalog, quyền INTERNET, cổng `RoutingProvider` ở `:domain`, HTTP client ở `:data`, `PolylineDecoder` | ✅ `070a28a` |
| [02](phase-02-graphhopper-provider.md) | `GraphHopperRoutingProvider` + DTO + mapper lỗi + wiring config qua `appConfigModule` | ✅ `07e20ca` |
| [03](phase-03-valhalla-provider.md) | `ValhallaRoutingProvider` (Stadia Maps / FOSSGIS / self-host), precision 6 | ✅ `ee09c98` |
| [04](phase-04-domain-reroute-and-arrival.md) | `RoutingGeometry`, `RerouteEvaluator`, ngưỡng vào `TrackingConstants`, use case | ✅ `09d4365` |
| [05](phase-05-navigation-screen-and-attribution.md) | Màn `navigation` (MVI), polyline trên Google Map, **attribution**, giảm cấp khi mất mạng | ✅ `9c049db` |
| [06](phase-06-quality-gates-and-docs.md) | Gate build/test, cập nhật `LLM.md`, `docs/`, ghi ngày kiểm tra điều khoản | ✅ `0dcd892` |

Phụ thuộc tuyến tính 01 → 02 → 04 → 05 → 06. **Phase 03 độc lập sau 02** và là thứ chứng minh
abstraction có thật: nếu thêm engine thứ hai mà phải sửa `:domain` hay `:ui`, cổng đã thiết kế sai.

## Chặn — phải xử lý, không được bỏ qua

| # | Việc | Chặn phase | Trạng thái |
|---|---|---|---|
| 1 | Lấy API key GraphHopper Cloud, đặt vào `local.properties` | 02 | ✅ **Đóng 2026-08-24.** Key đã có, gọi thật ra 200, fixture đã lưu |
| 2a | Profile `motorcycle` có trong free tier không? | 02 | ✅ **Đóng 2026-08-24 — KHÔNG.** Free tier chỉ `[car, bike, foot]`; `motorcycle`/`scooter` trả 400. Dùng `car`; muốn xe máy thì trả phí GraphHopper **hoặc** đổi sang Valhalla (có `motorcycle` miễn phí). Ghi vào `LLM.md` §13 Open #9 |
| 2b | **Hỏi GraphHopper: hiển thị polyline của họ trên Google Map có tính là "redistribution" không?** | 05 (ship) | ⬜ **CHƯA GỬI — chặn duy nhất còn lại của việc phát hành.** ToS đòi "custom package and agreement" để redistribute (researcher-02 §5). Rủi ro ở phía GraphHopper, không phải Google. Thư đã soạn ở `reports/graphhopper-redistribution-enquiry.md`, **chưa gửi**. Ghi vào `LLM.md` §13 Open **#11**. Im lặng không phải là đồng ý |
| 3 | Free tier GraphHopper ghi rõ **non-commercial only** | 06 | ✅ **Việc ghi lại đã xong 2026-08-24** — `LLM.md` §13 Open #9. **Ràng buộc thì vẫn còn hiệu lực:** đóng ở đây nghĩa là đã ghi vào repo, KHÔNG nghĩa là được dùng thương mại |
| 4 | Tính năng dẫn đường **không có user story nào trong PRD** | 06 | ✅ **Việc ghi lại đã xong 2026-08-24** — `LLM.md` §13 Open #8. **PRD vẫn chưa có US nào cho tính năng này**; BA sở hữu PRD, dev không tự thêm. Nghiệm thu vẫn chưa có hợp đồng để đối chiếu |

## Không làm trong plan này

Turn-by-turn (đọc `maneuvers`), giọng nói, offline tiles, Matrix/Isochrone API, đổi provider lúc
đang chạy. Tất cả đều cộng thêm được mà không phải sửa cổng `RoutingProvider` — đó là mục tiêu.
