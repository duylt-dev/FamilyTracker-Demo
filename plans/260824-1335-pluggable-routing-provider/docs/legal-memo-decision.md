# Memo quyết định — Vẽ tuyến đường nguồn OSM lên nền bản đồ Google

**Ngày:** 2026-08-24 · **Trạng thái:** cần bạn quyết · **Không phải tư vấn pháp lý**

Memo này tổng hợp từ ba nguồn: [researcher-04](../research/researcher-04-licensing-tos-and-cost.md)
(kết luận "VI PHẠM"), [legal-A](legal-A-case-for-prohibited.md) (steelman phía cấm),
[legal-B](legal-B-case-for-permitted.md) (steelman phía không cấm). Mọi trích dẫn dưới đây đều
xuất hiện trùng khớp ở ít nhất hai trong ba file, hoặc được ghi rõ là chỉ có một nguồn.

## Câu hỏi

App dùng **Maps SDK for Android** làm nền bản đồ, và vẽ lên đó **một polyline tuyến đường** do
Valhalla hoặc GraphHopper tính từ dữ liệu OpenStreetMap. Không có bản đồ nền thứ hai trên màn hình.
Điều này có bị điều khoản của Google cấm không?

## 1. Điều khoản được viện dẫn — nguyên văn

Đây là điều khoản duy nhất mà cả ba báo cáo đều dựa vào, từ
[Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms):

> "Customers must not use Google Maps Core Services with or near a non-Google Map in a customer
> application to avoid quality issues and/or brand confusion. Examples of prohibited uses include:
> (i) displaying or using Places content on a non-Google Map, (ii) displaying Street View imagery
> and non-Google Maps on the same screen, or (iii) linking a Google Map to non-Google Maps Content
> or a non-Google Map."

Một biến thể ngắn hơn cùng nội dung xuất hiện trong
[Acceptable Use Policy](https://cloud.google.com/maps-platform/terms/aup):

> "Customer will not use the Google Maps Core Services in a Customer Application that contains a
> non-Google map."

**Điểm mấu chốt:** đối tượng bị cấm là `a non-Google Map` — một **bản đồ**. Cụm này **không được
Google định nghĩa chính thức ở đâu** (cả A và B đều đi tìm và đều không tìm ra). Hai trong ba ví dụ
đi theo chiều *content của Google → đặt lên bản đồ của bên khác*, tức chiều ngược với tình huống
của chúng ta.

## 2. Bằng chứng cho thấy việc trộn là được lường trước và ĐƯỢC ĐIỀU CHỈNH, không bị cấm

Đây là phần làm đổ kết luận "VI PHẠM" của researcher-04. Mạnh nhất là bằng chứng thứ nhất, và nó
do chính agent được giao nhiệm vụ bảo vệ phía "cấm" tìm ra rồi tự xếp là điểm yếu chí tử của mình.

| # | Nguồn | Nguyên văn / nội dung | Ý nghĩa |
|---|---|---|---|
| 1 | [Policies — Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/policies) | "When overlaying third-party geospatial data with Google Maps data as a basemap, you must not overlap or obscure the Google data attribution with third-party data attribution, and the attribution of third-party data must clearly be disassociated from Google's data attributions." | Google **có văn bản hướng dẫn cách làm** đúng việc chúng ta định làm. Một hành vi bị cấm tuyệt đối thì không được viết quy tắc attribution cho nó. |
| 2 | [Terms of Service](https://cloud.google.com/maps-platform/terms) | "If customers use the Services with third-party products or services in their application, they must make it clear to the end user what is Google Maps Content and what content is not from Google." | Câu điều kiện, không phải câu cấm. Google lường trước việc kết hợp và ra nghĩa vụ kèm theo. |
| 3 | [Maps Platform FAQ](https://developers.google.com/maps/faq) | "You must visually distinguish Google Maps Platform Content from other content by using UI cues such as a border, background color, shadow, or sufficient whitespace." | Cùng logic: quy định cách trộn, không cấm trộn. |
| 4 | [EEA FAQ](https://developers.google.com/maps/comms/eea/faq) (hiệu lực 2025-07-08) | "EEA customers may now use Google Maps Platform Services for real-time navigation with a third-party map, as long as that real-time navigation usage complies with Google's safety requirements." | Điều khoản đang được **nới**, kể cả cho trường hợp nặng hơn ta nhiều (bản đồ nền của bên thứ ba). |
| 5 | Maps SDK API surface | `addPolyline()`, custom overlay, ground overlay là API công khai, có [blog chính thức](https://mapsplatform.google.com/resources/blog/styling-and-custom-data-for-polylines) hướng dẫn gắn dữ liệu tuỳ ý vào polyline | Vẽ dữ liệu của khách hàng lên bản đồ là công dụng được thiết kế sẵn. |

## 3. Chỗ mơ hồ còn lại — không bên nào giải quyết được

Ví dụ (iii) của điều khoản: *"linking a Google Map to non-Google Maps **Content** or a non-Google Map"*.

Cụm này là chỗ duy nhất trong toàn bộ điều khoản có nhắc tới **content** thay vì **map**, và nó có
thể bị đọc rộng thành "gắn nội dung non-Google vào bản đồ Google". Cả hai advocate đều thừa nhận
không phản biện được 100%.

Hai điều làm giảm sức nặng của cách đọc rộng đó, nhưng không xoá được nó:

- `linking` hiểu tự nhiên là **liên kết / dẫn sang** (click-through), không phải **vẽ lên**.
- Nếu đọc (iii) theo nghĩa rộng thì nó **tự mâu thuẫn** với bằng chứng #1 và #5 ở trên: Google vừa
  cấm phủ dữ liệu bên thứ ba, vừa viết quy tắc attribution cho việc phủ đó và cung cấp API để làm.

## 4. Phía OpenStreetMap / ODbL — rõ ràng, không phải vấn đề

- Polyline tuyến đường là **Produced Work**, không phải Derivative Database → chỉ phát sinh nghĩa vụ
  **attribution**, không kéo share-alike sang dữ liệu bản đồ của Google. Không có "lây nhiễm" giấy phép.
- OSMF nói thẳng về đúng trường hợp routing
  ([Licence/Community Guidelines](https://osmfoundation.org/wiki/Licence)):
  > "routing instructions generated by a routing engine need not maintain attribution attached to the
  > instructions, as long as they do not form a Derivative Database."
- Nghĩa vụ còn lại: hiện `© OpenStreetMap contributors` ở nơi người dùng thấy được, dễ đọc, **không
  che và tách biệt rõ** với attribution của Google — trùng khít với yêu cầu #1 của Google.

## 5. Chi phí — con số của researcher-04 không đứng được

researcher-04 dùng `~$275/tháng` cho Google Routes API làm một lý do để bỏ Google. Tính lại cho mức
dùng thật của app này (1 người dùng, reroute mỗi 30–60 giây và chỉ khi đang dẫn đường ≈ **7.2k
request/tháng**): nằm trong free tier 10.000 request/tháng → **$0**.

> ⚠️ Nguồn giá mà advocate B dùng là [một blog của woosmap](https://www.woosmap.com/blog/google-maps-api-pricing-breakdown),
> **không phải trang giá của Google**. Con số free tier 10k/tháng cần xác thực lại trên chính
> Google Cloud Console / trang pricing trước khi dựa vào nó để quyết định.

**Hệ quả cho việc ra quyết định:** chi phí **không phải** lý do để đổi sang OSM routing ở quy mô này.
Nếu lý do duy nhất là tiền thì Google Routes API đang miễn phí và tranh luận này là vô nghĩa. Lý do
thật của refactor là **có thể cắm-rút provider** — đó là một mục tiêu kiến trúc chính đáng, nhưng
hãy gọi đúng tên nó, đừng biện minh bằng chi phí.

## 6. Kết luận của tôi

**Kết luận "VI PHẠM" của researcher-04 không có cơ sở.** Nó đạt được kết luận đó bằng cách tự nâng
cụm `non-Google Map` thành "mọi nội dung của bên thứ ba", rồi bỏ qua chính điều khoản attribution mà
nó có trích dẫn. Mức rủi ro thực tế: **thấp đến trung bình**, và giảm được bằng đúng những việc
Google yêu cầu.

Đây không phải tư vấn pháp lý. Nếu app này có ngày phát hành thương mại, cho người có thẩm quyền
đọc lại mục 1 và mục 3.

### Điều kiện phải đưa vào kế hoạch nếu chọn hướng này

1. Hiện `© OpenStreetMap contributors` (và tên nhà cung cấp routing) trên màn hình bản đồ —
   **không che** attribution của Google, **tách biệt rõ** khỏi nó.
2. Phân biệt trực quan tuyến đường bên thứ ba với nội dung của Google (màu/nhãn), theo FAQ #3.
3. Không bao giờ hiển thị bản đồ nền thứ hai cùng lúc — đây là hành vi bị cấm rõ ràng, khác hẳn
   trường hợp của ta.
4. **Không dùng Mapbox hoặc HERE làm nguồn routing.** Rủi ro ở đây không nằm ở phía Google mà ở
   phía họ: điều khoản của các nhà cung cấp này bị nghi cấm hiển thị kết quả của họ trên bản đồ
   không phải của họ. researcher-04 xếp Mapbox là "KHÔNG RÕ" và không tìm được câu chữ. Nếu về sau
   muốn thêm Mapbox vào enum thì phải xác thực điều khoản của Mapbox trước, tách riêng khỏi memo này.
5. Ghi lại ngày kiểm tra điều khoản. Điều khoản của Google có thay đổi (bằng chứng #4 là một thay
   đổi trong năm 2025).

## 7. Ghi chú về chất lượng nguồn

Hai chỗ trong [legal-B](legal-B-case-for-permitted.md) dùng blockquote cho **câu do agent tự đặt ra
để phản chứng** ("nếu Google muốn cấm thì đã viết là…"), cụ thể ở dòng 95, 121, 209 và 212. Chúng
**không phải câu chữ của Google**. Trong file gốc có ghi rõ ngữ cảnh, nhưng định dạng dễ gây đọc
nhầm nếu ai đó chỉ trích blockquote ra. Đừng mang những dòng đó đi trích dẫn.

Tương tự, [legal-A](legal-A-case-for-prohibited.md) dòng 99–100 đưa hai phương án câu chữ nối nhau
bằng "[hoặc]" cho điều khoản "competing product" — đó là diễn giải, chưa xác thực nguyên văn.

## 8. Việc còn lại trước khi lập kế hoạch

| # | Việc | Vì sao chặn |
|---|---|---|
| 1 | Bạn quyết: đi tiếp với Google Maps + routing OSM, hay đổi hướng | Quyết định này định hình toàn bộ kế hoạch |
| 2 | Xác thực free tier Google Routes API trên trang giá của Google | Đang dựa vào blog bên thứ ba |
| 3 | Chốt enum theo *engine* (`VALHALLA`, `GRAPHHOPPER`) hay theo *nhà cung cấp hosting* | Hai report mâu thuẫn, xem VERIFICATION.md |
| 4 | Chốt kiểu config: build-time `BuildConfig` / runtime Settings / product flavor | Câu hỏi đã hỏi, bạn hoãn để xử lý pháp lý trước |
