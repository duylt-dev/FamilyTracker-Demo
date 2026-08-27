# Chỉ đường realtime đến thành viên đang theo dõi

**Ngày:** 2026-08-24 · **Trạng thái:** 🔀 **SUPERSEDED** — đã gộp vào
[`260824-1335-pluggable-routing-provider`](../260824-1335-pluggable-routing-provider/plan.md)

> Plan này không được implement theo dạng đang viết ở đây. Lý do gộp: câu hỏi chặn ("dùng routing
> của bên thứ ba trên nền bản đồ Google có được không") đã được trả lời là **được, kèm 5 điều kiện**
> — xem [memo pháp lý](../260824-1335-pluggable-routing-provider/docs/legal-memo-decision.md). Cùng
> lúc đó nguồn tuyến đường đổi từ **Google Routes API** sang **GraphHopper Cloud / Valhalla (OSM)**,
> nên phần lớn phần kỹ thuật ở đây phải viết lại.
>
> **Còn giá trị và đã được mang sang:** chính sách reroute (ngưỡng 45m / 3 mẫu / 200m / debounce 60s,
> arrival 50m với hysteresis 70m) → [phase-04](../260824-1335-pluggable-routing-provider/phase-04-domain-reroute-and-arrival.md);
> quyết định đặt `RerouteEvaluator` ở `:domain/tracking/`; ba chặn kỹ thuật (quyền `INTERNET`, HTTP
> client chưa có trong catalog, `PolyUtil` không có tham số precision).
>
> **Không còn dùng:** toàn bộ phần Google Routes API — giá, endpoint, giới hạn API key.
>
> Giữ file này để research bên dưới còn địa chỉ trích dẫn. Đừng lập kế hoạch từ nó.

## Phạm vi dự kiến

Từ vị trí GPS thật của mình, vẽ và cập nhật liên tục tuyến đường đến thành viên đang được follow
(Minh/Lan — vị trí do `MemberMovementSimulator` sinh ra mỗi 2.5s), kèm logic reroute khi đi lệch
tuyến hoặc khi đích di chuyển.

## Tình trạng hiện tại

Đã chạy 5 researcher song song + 1 lượt fact-check đối kháng. **Chưa có phase file, chưa viết code.**
Kế hoạch thay thế nằm ở [`260824-1335-pluggable-routing-provider`](../260824-1335-pluggable-routing-provider/plan.md).

| Tài liệu | Nội dung |
|---|---|
| [researcher-01](research/researcher-01-google-routes-api.md) | Google Routes API: endpoint, giá, giới hạn API key |
| [researcher-02](research/researcher-02-network-layer.md) | Tầng network đầu tiên cho `:data` — đề xuất Ktor 3.5.2 |
| [researcher-03](research/researcher-03-maps-compose-route.md) | Vẽ + animate polyline realtime với maps-compose 8.3.1 |
| [researcher-04](research/researcher-04-reroute-policy.md) | Chính sách reroute: ngưỡng lệch tuyến, đến đích, hysteresis |
| [researcher-05](research/researcher-05-codebase-integration-map.md) | Bản đồ tích hợp vào codebase hiện tại (MVI contract, DI, module) |
| [VERIFICATION](research/VERIFICATION.md) | Fact-check: cái gì đã xác nhận, cái gì sai, 10 lỗ hổng, 5 câu hỏi mở |

## Quyết định đã chốt trong lúc nghiên cứu

1. **Reroute logic đặt ở `:domain/tracking/RerouteEvaluator.kt`** (haversine thuần). Ba report đề
   xuất ba chỗ khác nhau; hai chỗ còn lại (`:ui`, `:data`) vi phạm hướng phụ thuộc module hoặc khiến
   ViewModel không đọc được quyết định reroute mà không vượt biên module.
2. **Dùng Routes API, không dùng Directions API.** Ngày deprecate cụ thể trong report 01 chưa xác
   thực được — không hard-code ngày đó vào tài liệu.

## Chặn kỹ thuật phải xử lý trước khi mở lại task

- **API key**: key đang bị restrict theo Android app thì **không gọi được REST web service**. Cần key
  riêng không restrict theo platform (chấp nhận rủi ro cho demo, đặt quota chặn chi phí) hoặc proxy
  qua backend.
- **`android.permission.INTERNET` chưa được khai báo** trong `app/src/main/AndroidManifest.xml`.
  Không có nó thì mọi lời gọi mạng fail ngay.
- **Ktor chưa có trong `gradle/libs.versions.toml`** — cần 5 entry, và phải build thử ngay vì mức
  Kotlin tối thiểu của Ktor 3.5.2 chưa xác thực được.
- **`android-maps-utils-core` 5.1.1 chưa xác nhận tồn tại**; Maven Central đang có `6.0.0-rc01`.

## Câu hỏi còn mở (cần trả lời khi lập phase)

1. Navigation screen mở khi tracking đang OFF thì xử lý sao — bật cưỡng chế, báo lỗi, hay hỏi dialog?
2. Tracking service dừng giữa lúc đang dẫn đường thì UI hiện gì?
3. Route offline (fallback đường thẳng) tồn tại bao lâu, có persist không?

## Bước tiếp theo

Không có. Ba câu hỏi mở ở trên đã được trả lời trong plan thay thế:
[phase-05](../260824-1335-pluggable-routing-provider/phase-05-navigation-screen-and-attribution.md)
Requirements #10 (tracking tắt → banner + nút bật, không tự bật thay người dùng) và Key Insight #3
+ Requirements #5 (mất tuyến → đường thẳng "ước tính", không persist).
