# Routing từ OpenStreetMap trên nền bản đồ Google — hợp đồng tuân thủ

**Kiểm tra điều khoản lần cuối:** 2026-08-24 · **Người kiểm:** duylt (lothanhduy2003@gmail.com)
**Kiểm tra ràng buộc nhà cung cấp lần cuối:** 2026-08-24 — gọi thật cả hai API, xem mục 5
**Kiểm tra lại khi:** đổi nhà cung cấp routing, phát hành thương mại, hoặc 6 tháng một lần.

Đây không phải tư vấn pháp lý. Phân tích đầy đủ, gồm cả lập luận của phía phản đối, nằm ở
[`plans/260824-1335-pluggable-routing-provider/docs/legal-memo-decision.md`](../plans/260824-1335-pluggable-routing-provider/docs/legal-memo-decision.md).
File này chỉ ghi **những gì code phải làm và không được làm**.

---

## 1. Việc đang làm là gì

App dùng **Maps SDK for Android** làm bản đồ nền duy nhất. Tuyến đường dẫn đường được tính bởi
**GraphHopper Cloud** hoặc **Valhalla** từ dữ liệu **OpenStreetMap**, rồi vẽ lên bản đồ đó bằng
`Polyline` của maps-compose.

Không có bản đồ nền thứ hai. Không có tile của bên thứ ba. Chỉ một polyline dữ liệu khách hàng
phủ lên basemap của Google.

## 2. Vì sao được phép

Điều khoản hay bị viện dẫn để nói "cấm" là:

> "Customers must not use Google Maps Core Services with or near a **non-Google Map** in a customer
> application…"

Đối tượng bị cấm là một **bản đồ**, không phải mọi nội dung của bên thứ ba. Và chính Google viết
quy tắc cho việc phủ dữ liệu bên thứ ba lên basemap của họ — [Policies, Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/policies):

> "When overlaying third-party geospatial data with Google Maps data as a basemap, you must not
> overlap or obscure the Google data attribution with third-party data attribution, and the
> attribution of third-party data must clearly be disassociated from Google's data attributions."

Một hành vi bị cấm tuyệt đối thì không được viết quy tắc attribution cho nó. Cùng chiều: ToS ra
nghĩa vụ *có điều kiện* khi kết hợp ("must make it clear to the end user what is Google Maps
Content and what content is not"), FAQ yêu cầu phân biệt trực quan, và `addPolyline()` là API công
khai có tài liệu chính thức hướng dẫn gắn dữ liệu tuỳ ý.

**Phía OpenStreetMap:** polyline tuyến đường là *Produced Work*, không phải Derivative Database →
chỉ phát sinh nghĩa vụ **attribution**, không kéo share-alike sang bất cứ thứ gì khác. OSMF nói
thẳng về routing trong [Licence/Community Guidelines](https://osmfoundation.org/wiki/Licence).

**Hai nghĩa vụ trùng khít nhau:** Google đòi credit của bên thứ ba phải tách bạch và không che
credit của Google; ODbL đòi phải có credit của OSM. Làm đúng một lần là xong cả hai.

## 3. Năm điều code phải giữ

| # | Ràng buộc | Thực hiện ở | Vi phạm thì sao |
|---|---|---|---|
| 1 | Hiện `© OpenStreetMap contributors` + tên nhà cung cấp routing ở nơi người dùng thấy, **không che** và **tách bạch rõ** khỏi attribution/logo của Google | Chuỗi credit đi kèm dữ liệu trong `Directions.attribution: List<String>` (bắt buộc, không nullable); `ui/designsystem/component/RoutingAttribution.kt` chỉ hiển thị nó, trên dải riêng **bên ngoài** khung bản đồ. **plan routing phase-04:** `data/routing/MemberRouteSource.kt` là nơi THỨ HAI giữ attribution đi kèm dữ liệu — cache trên máy (`filesDir/routes/*.json`) ghi lại `attribution` NGUYÊN VĂN cùng hình học, và tầng `SyntheticPath` phát `attribution = emptyList()` (không có gì để ghi công, tầng đó không chứa dữ liệu OSM). **plan routing phase-05:** màn Bản đồ là nơi THỨ HAI hiện dải này (`ui/feature/map/MapScreen.kt`, đọc `MapState.attributionLines`/`isFallbackRoute` suy từ `SimulatedRouteRepository.observeSource()`) — cùng composable, cùng luật ba trạng thái, không phải logic mới | Mất chính lập luận đã dùng để cho phép tính năng. Vi phạm cả ODbL |
| 2 | Polyline tuyến đường phân biệt trực quan được với nội dung của Google (màu riêng) | `designsystem/theme/Color.kt` | Vi phạm yêu cầu "visually distinguish" của FAQ |
| 3 | **Không bao giờ** hiển thị bản đồ nền thứ hai cùng lúc | Toàn app | Đây là hành vi bị cấm **rõ ràng**, không mơ hồ. Khác hẳn trường hợp hiện tại |
| 4 | **Không** dùng Mapbox hoặc HERE làm nguồn routing | `RoutingEngine` chỉ có `GRAPHHOPPER`, `VALHALLA` | Rủi ro nằm ở phía **họ**, không phải Google: điều khoản của họ bị nghi cấm hiển thị kết quả trên bản đồ không phải của họ. Thêm vào enum thì phải xác thực ToS của bên đó trước, tách riêng khỏi tài liệu này |
| 5 | Ghi lại ngày kiểm tra điều khoản | Đầu file này | Điều khoản Google có thay đổi — [EEA FAQ 2025-07-08](https://developers.google.com/maps/comms/eea/faq) là một lần nới thật |

**Chỉ hiện credit OSM khi đang thật sự hiển thị dữ liệu OSM.** Ở chế độ giảm cấp (đường thẳng tự
vẽ khi mất mạng), không có dữ liệu OSM nào trên màn hình — ghi credit lúc đó là ghi sai nguồn.

**Phạm vi của nghĩa vụ ghi công: theo PHIÊN ĐANG CHẠY, không kéo dài qua các phiên.**
(Chủ dự án chốt 2026-08-26; `LLM.md` §13 Open #22 giữ mở làm hồ sơ của lựa chọn này.)

Ca cụ thể: toạ độ thành viên nằm trong Room và sống sót khi app bị đóng, còn nguồn tuyến
(`RouteSourceAggregator`) chỉ nằm trong RAM. Nên mở lại app lúc chưa bật theo dõi, màn Bản đồ vẽ
marker ở **những toạ độ đã sinh ra bằng cách đi trên polyline OSM ở phiên trước**, mà dải ghi công
thì ẩn.

Câu "chỉ hiện credit OSM khi đang thật sự hiển thị dữ liệu OSM" không tự phân xử được ca này, vì nó
phụ thuộc cách đọc *dữ liệu OSM đang hiển thị*:

| Cách đọc | Kết luận | Đã chọn |
|---|---|---|
| **Hình học tuyến** — polyline lấy từ nhà cung cấp | Hình học đó không còn trên màn ⇒ không phải ghi công | ✅ |
| **Vị trí suy ra từ hình học đó** — marker vẫn đang hiển thị | Là *Produced Work* của ODbL ⇒ phải ghi công | ⬜ |

**Lý do chọn cách đọc thứ nhất, nói thẳng cả điểm yếu:** thứ tính năng này hiển thị từ dữ liệu OSM
là **đường đi**, và ghi công đi kèm đúng vòng đời của đường đi đó. Một toạ độ rời rạc của một nhân
vật demo, sau khi tuyến sinh ra nó đã bị quên, không còn mang thông tin bản đồ nào truy ngược được
về OSM. **Điểm yếu:** đây là một diễn giải ODbL do dự án tự chọn, không phải một quy tắc được bên
nào xác nhận, và nó nghiêng về phía **thiếu** ghi công — chiều nguy hiểm hơn về pháp lý so với ghi
thừa. Nếu có tư vấn pháp lý cho câu redistribution của GraphHopper (§5, `LLM.md` §13 Open #11) thì
hỏi luôn câu này; đảo lại chỉ tốn một chỗ lưu `RouteSourceInfo` cuối xuống đĩa.

**Không áp dụng cho màn Lịch sử:** màn đó chỉ vẽ self, tức GPS thật, không phải dữ liệu OSM.

**Credit lấy từ nhà cung cấp, không tự viết.** GraphHopper trả sẵn
`info.copyrights: ["GraphHopper", "OpenStreetMap contributors"]` trong mỗi response (kiểm thật
2026-08-24) — mapper chép thẳng vào `Directions.attribution`. Valhalla không trả field này, nên
provider dựng theo host: Stadia Maps → `["Stadia Maps", "OpenStreetMap contributors"]`; FOSSGIS và
self-host → `["Valhalla", "OpenStreetMap contributors"]`. Ở mọi nhánh,
`OpenStreetMap contributors` không bao giờ được vắng.

Vì sao không tự ghép chuỗi từ tên engine: nhà cung cấp đổi yêu cầu attribution bằng cách đổi field
đó, không bằng cách gửi email cho chúng ta.

## 4. Chỗ mơ hồ còn lại

Ví dụ (iii) của điều khoản — *"linking a Google Map to non-Google Maps **Content** or a non-Google
Map"* — là chỗ duy nhất nhắc tới *content* thay vì *map*, và có thể đọc rộng thành "gắn nội dung
non-Google vào bản đồ Google".

Hai điều làm nhẹ cách đọc đó nhưng không xoá được nó: `linking` hiểu tự nhiên là *liên kết/dẫn sang*
chứ không phải *vẽ lên*; và đọc rộng thì (iii) tự mâu thuẫn với quy tắc attribution ở mục 2 của
chính Google.

**Mức rủi ro đánh giá: thấp đến trung bình.** Nếu app có ngày phát hành thương mại, cho người có
thẩm quyền đọc lại mục 2 và mục 4.

## 5. Ràng buộc từ phía nhà cung cấp routing

Đây là loại rủi ro **khác** với rủi ro phía Google, và nó chưa được đóng.

| Nhà cung cấp | Ràng buộc | Trạng thái |
|---|---|---|
| **GraphHopper Cloud** | ToS: *"To redistribute the Directions API you need a custom package and agreement with GraphHopper"*. Hiển thị polyline của họ trên bản đồ Google có tính là redistribution không? | ⬜ **Chưa hỏi.** Chặn việc phát hành — **đây là mục duy nhất còn mở trong cả tài liệu này**. Nội dung thư đã soạn: `plans/260824-1335-pluggable-routing-provider/reports/graphhopper-redistribution-enquiry.md`. Khi có trả lời, dán **nguyên văn** vào đây kèm ngày, không dán bản tóm tắt. **Không trả lời cũng không đóng được mục này — im lặng không phải là đồng ý.** |
| **GraphHopper free tier** | Non-commercial only, 500 credit/ngày, tối đa 5 điểm/request | ✅ Đã biết. Demo nội bộ thì đủ; thương mại thì phải trả phí |
| **GraphHopper free tier — profile** | Chỉ `car`, `bike`, `foot`. `motorcycle` và `scooter` trả **400**: *"For your account the profile parameter can only be one of [car, bike, foot]"* | ✅ **Kiểm thật 2026-08-24** bằng chính key của dự án. App dùng `car`. Muốn xe máy: trả phí GraphHopper, hoặc đổi `ROUTING_ENGINE=VALHALLA` (có `motorcycle`, miễn phí) |
| **Stadia Maps free tier** | 200.000 credit/tháng (~10.000 request routing), non-commercial | ✅ Đã biết |
| **FOSSGIS** (`valhalla1.openstreetmap.de`) | Fair-use 1 req/user/giây. Publish app dùng server này phải báo maintainer qua GitHub Discussions và gửi header `X-Client-Id` | ✅ Đã biết — **chỉ dùng khi dev**, không cho bản phát hành |
| **Valhalla self-host** | Không có bên thứ ba nào để ràng buộc. Chỉ còn ODbL của OSM | ✅ Đường thoát nếu mục trên chặn |

## 6. Về chi phí — đừng dùng nó làm lý do

Con số `~$275/tháng` cho Google Routes API xuất hiện trong research ban đầu không đứng được ở quy
mô này. Với 1 người dùng và debounce 60 giây, một phiên dẫn đường 1 giờ tốn khoảng 60 request.
Google Routes API nhiều khả năng **$0** ở mức đó (cần tự xác thực trên trang giá của Google —
con số free tier đang dựa vào một blog bên thứ ba, không phải nguồn của Google).

Lý do thật của kiến trúc này là **cắm-rút được provider**. Đó là một mục tiêu chính đáng. Gọi đúng
tên nó, đừng biện minh bằng chi phí.

## 7. Nguồn

- [Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms)
- [Google Maps Platform Acceptable Use Policy](https://cloud.google.com/maps-platform/terms/aup)
- [Policies — Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/policies)
- [Google Maps Platform FAQ](https://developers.google.com/maps/faq)
- [EEA FAQ](https://developers.google.com/maps/comms/eea/faq) (hiệu lực 2025-07-08)
- [OSMF Licence / Community Guidelines](https://osmfoundation.org/wiki/Licence)
- [GraphHopper Attribution](https://www.graphhopper.com/attribution/) · [Terms](https://www.graphhopper.com/terms/)
- [Stadia Maps Pricing](https://stadiamaps.com/pricing/)
