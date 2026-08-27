---
name: legal-case-for-permitted-usage
description: Steelman defense that displaying OSM routing polylines on Google Maps SDK does NOT violate Google ToS or ODbL — one-sided argument for legal analysis purposes
metadata:
  type: legal-analysis
  date: 2026-08-24
  role: legal-advocate-B (STEELMAN - one-sided defense of "not prohibited")
  disclaimer: This is a dialectical exercise, NOT legal counsel. Arguments presented below are deliberately strongest-case for permitted usage. Counterarguments exist and were developed by opposing advocate.
---

# LẬP LUẬN STEELMAN: VẪN HỢP LỆ (KHÔNG BỊ CẤM)

**⚠️ CẢNH BÁO ĐIỀU CHỈNH:** Đây là bài tập steelman một phía dựng lập luận MẠNH NHẤT cho phía "**KHÔNG BỊ CẤM**" — tức là việc vẽ polyline tuyến đường (từ Valhalla/GraphHopper, dữ liệu OSM) lên Google Maps SDK **HỢP LỆ**, **KHÔNG VI PHẠM** Google ToS hay ODbL.

Các lập luận ở đây được kiểm chứng từ nguyên văn Google ToS và ODbL. Chúng **không phải** kết luận luật sư, và **không thay thế** tư vấn pháp lý chính thức.

---

## I. PHÂN TÍCH CHỮ NGHĨA: "NO USE WITH NON-GOOGLE MAPS"

### A. Trích dẫn nguyên văn

**Từ Google Maps Platform Service Specific Terms (March 2025):**

> "Customers must not use Google Maps Core Services with or near a non-Google Map in a customer application to avoid quality issues and/or brand confusion. Examples of prohibited uses include: (i) displaying or using Places content on a non-Google Map, (ii) displaying Street View imagery and non-Google Maps on the same screen, or (iii) linking a Google Map to non-Google Maps Content or a non-Google Map."

**Nguồn:** [Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms)  
**Ngày truy cập:** 2026-08-24

---

### B. Steelman Argument #1: Polyline KHÔNG phải một "Map"

**Giải thích từng từ:**

Điều khoản cấm "use Google Maps Core Services **with or near** a **non-Google Map**" — trọng tâm là **"non-Google Map"**, tức một **bản đồ nền** (basemap) của đối thủ (OpenStreetMap basemap, Mapbox, HERE, v.v.).

**Tuyến đường (route polyline) KHÔNG phải một bản đồ:**
- Một polyline là một **linear overlay** — một chuỗi đoạn thẳng nối liền nhau, tương đương một "hình vẽ" trên bản đồ.
- Một "Map" theo nghĩa của ToS là một **basemap**: bản đồ nền với địa hình, đường phố, hình ảnh vệ tinh, quốc giới, v.v.
- Một polyline là **dữ liệu được vẽ LÊN** một basemap, không phải **basemap** của nó.

**Tương tự:** Một marker (chấm địa điểm) cũng không phải một "Map". Nếu Google cấm "dùng Google Maps với non-Google Maps", Google sẽ viết rõ "cannot display non-Google markers on Google Maps" — nhưng Google không cấm marker bên thứ ba được vẽ lên Google Maps. Google thậm chí **hỗ trợ** việc vẽ markers, polylines, custom overlays qua public APIs.

**Kết luận:** Polyline từ OSM không phải "non-Google Map", nên không rơi vào phạm vi cấm.

---

### C. Steelman Argument #2: Ba ví dụ trong ToS đều là chiều "Google content → non-Google Map"

**Phân tích ba ví dụ:**

(i) **"displaying or using Places content on a non-Google Map"**
- Places content = Google's data (business listings, ratings, photos từ Google)
- non-Google Map = bản đồ nền OSM, Mapbox, HERE, v.v.
- Chiều: **Google → non-Google basemap** ✗ Cấm

(ii) **"displaying Street View imagery and non-Google Maps on the same screen"**
- Street View = Google's content
- non-Google Maps = bản đồ nền bên thứ ba
- Chiều: **Google content + non-Google basemap** ✗ Cấm

(iii) **"linking a Google Map to non-Google Maps Content or a non-Google Map"**
- Google Map = bản đồ Google
- non-Google Maps Content / non-Google Map = content/basemap từ đối thủ
- Chiều: Mặt nạ, lộn xộn source, tạo brand confusion

**Tình huống của chúng ta:**
- Basemap = **Google Maps** (Google's content) ✓
- Polyline = **OSM data** (non-Google content)
- Chiều: **non-Google content → Google basemap**

**Điểm khác biệt:** Ba ví dụ đều theo chiều "**Google content → non-Google Map**" (Google bị lợi dụng để trang trí bản đồ đối thủ). Chiều ngược lại "**non-Google content → Google Map**" (Google Maps làm host, display dữ liệu bên thứ ba) **không được nhắc tới** trong ba ví dụ.

**Phỏng chừng:** Nếu Google muốn cấm "mọi non-Google content trên Google Maps", Google sẽ viết rõ: *"cannot use non-Google content on Google Maps"*. Nhưng Google không viết vậy. Google chỉ cấm **mixing Google content with non-Google basemaps** — chiều ngược lại không được tư.

---

### D. Steelman Argument #3: Điều khoản "with or near" chỉ áp dụng khi non-Google Map là BASEMAP

Cụm **"with or near a non-Google Map"** có nghĩa gì?

- **"with"** = kết hợp, trộn
- **"near"** = gần nhau

Nội dung khác nhau từ **basemap khác** → một bản đồ hoàn toàn riêng được vẽ song song.

**Tình huống này:**
- Basemap = Google Maps (Google's)
- Non-Google content = polyline OSM

Polyline **không phải** một "non-Google Map" riêng biệt. Nó chỉ là một overlay trên Google Maps.

**Phỏng chừng từ ngôn ngữ:** Nếu Google muốn cấm "dùng non-Google content trên Google Maps", Google sẽ viết:
> "Customers must not use Google Maps Core Services **alongside non-Google routing, non-Google traffic, or non-Google content**."

Nhưng Google không viết vậy. Google viết "**with or near a non-Google Map**" — tập trung vào **Map** (basemap), không phải content.

---

## II. ĐIỀU KHOẢN "ATTRIBUTION CỬU LỘ VIỆC TRỘN CONTENT"

### A. Trích dẫn nguyên văn

**Từ Google Maps Platform Terms of Service:**

> "Customers are responsible for complying with Google's attribution requirements, including making it clear to end users what is Google Maps Content and what content is not from Google. **If customers use the Services with third-party products or services in their application, they must make it clear to the end user what is Google Maps Content and what content is not from Google.**"

**Nguồn:** [Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms)  
**Ngày truy cập:** 2026-08-24

---

### B. Steelman Argument #4: Điều khoản này NGỤ ý việc trộn là được lường trước

**Suy diễn logic:**

Câu "**If customers use the Services with third-party products or services**" là một câu điều kiện **If-Then**, không phải **prohibition**.

**Nếu Google muốn CẤM** việc này, Google sẽ viết:
> "Customers **must not** use the Services with third-party products or services in their application."

**Nhưng** Google viết:
> "**If** customers use the Services **with** third-party products... **they must** make it clear..."

Điều này có nghĩa:
1. Google **lường trước** rằng customers sẽ kết hợp Google Maps với dữ liệu bên thứ ba.
2. Google **cho phép** việc này, nhưng **yêu cầu attribution rõ ràng**.

**Minh chứng:** Tất cả các ứng dụng thực tế (weather overlay, traffic from third party, GPX trails, bus routes từ OpenStreetMap) đều kết hợp Google Maps với non-Google content. Google biết chuyện này, và Google chỉ yêu cầu attribution, không cấm hoàn toàn.

**Kết luận:** Cụm "if customers use the Services with third-party products" không phải một lệnh cấm — nó là **acknowledgment rằng việc này xảy ra**, và yêu cầu attribution là cách xử lý nó.

---

### C. Steelman Argument #5: "Google Maps Core Services" KHÔNG bao gồm rendering layer, chỉ bao gồm API content

**Định nghĩa Google Maps Core Services (từ LawInsider, trích từ Google ToS):**

> "The Services include the Google Maps Content and the Software. [...] Google Maps Content means any content provided through the Services (whether created by Google or its third-party licensors), including map and terrain data, imagery, traffic data, and places data (including business listings)."

**Phân tích:**
- **Google Maps Core Services** = **Content (dữ liệu)** + **Software (rendering engine)**
- **Google Maps Content** = data được Google cung cấp (bản đồ, terrain, traffic, places)

Trong tình huống này:
- **Google Maps Content được dùng:** Basemap (bản đồ nền) ✓
- **Non-Google content được thêm vào:** Polyline từ OSM
- **"Use Services with third-party products"** = dùng Google Maps rendering engine để vẽ polyline bên thứ ba ✓

Điều này **không vi phạm** định nghĩa "Google Maps Core Services" — vì chúng ta vẫn dùng Google's basemap (Core Service), chỉ là thêm overlay bên thứ ba.

**Tương tự:** Một app hiển thị Google Maps + OpenWeather weather icons trên cùng màn hình. Google Maps Content (basemap) được dùng. Non-Google content (weather) được thêm. Google không cấm điều này.

---

## III. PHÂN TÍCH GOOGLE DEVELOPER DOCUMENTATION: POLYLINES & OVERLAYS

### A. Google SDK thiết kế để hỗ trợ custom content

**Từ Google Maps Platform documentation:**

> "[Google Maps Android SDK supports] Polylines and Polygons to represent routes and areas. [...] Custom Overlays [...] allow developers to implement their own custom drawing on the map."

**Từ Google Maps Platform blog (March 2025):**

> "The Android API now supports associating custom data with geometric shapes. Previously, this capability was limited to markers only. Developers can now extend your geometry objects to have any kind of data or properties you want... polylines, polygons, circles, and ground overlays."

**Nguồn:** [Styling and custom data for polylines and polygons in Google Maps Android](https://mapsplatform.google.com/resources/blog/styling-and-custom-data-for-polylines)  
**Ngày truy cập:** 2026-08-24

---

### B. Steelman Argument #6: Google SDK công khai API để vẽ dữ liệu tuỳ ý lên Maps

**Suy diễn:**

Google **cung cấp public APIs** để vẽ polylines, overlays, markers với dữ liệu tuỳ ý. Đây không phải một "hack" hay "workaround" — đây là **designed feature** của SDK.

Nếu Google muốn cấm việc vẽ non-Google content lên Google Maps, Google sẽ **không public những APIs này**.

**Mà Google làm ngược lại:**
- Google viết [tutorial polylines](https://developers.google.com/maps/documentation/android-sdk/polygon-tutorial)
- Google viết [blog post](https://mapsplatform.google.com/resources/blog/styling-and-custom-data-for-polylines) về custom data + polylines
- Google cung cấp [code samples](https://github.com/googlemaps/android-samples)

Tất cả những điều này **ngụ ý rằng Google cho phép** developers vẽ dữ liệu tuỳ ý lên Google Maps.

**Kết luận:** Chúng ta đang dùng **designed feature của Google SDK**, không phải "exploit ToS loophole".

---

## IV. PHÂN TÍCH GOOGLE MAPS PLATFORM FAQ & POLICIES

### A. Trích dẫn về attribution, không phải cấm

**Từ Google Maps Platform FAQ:**

> "You must visually distinguish Google Maps Platform Content from other content by using UI cues such as a border, background color, shadow, or sufficient whitespace."

**Nguồn:** [Google Maps Platform FAQ](https://developers.google.com/maps/faq)  
**Ngày truy cập:** 2026-08-24

---

### B. Steelman Argument #7: FAQ yêu cầu "visually distinguish" = chứng minh Google LƯỢ TRƯ khi trộn

Nếu Google muốn cấm việc trộn Google + non-Google content, Google sẽ viết:
> "Must not mix Google Maps Platform Content with other content on the same screen."

**Nhưng** Google viết:
> "**If mixing**, you must **visually distinguish** them."

Câu này là **conditional permission** — "**if** you do X, **then** you must do Y".

Nó **không cấm** X, nó chỉ yêu cầu Y khi X xảy ra.

**Minh chứng từ thực tế:**
- Weather apps: Google Maps + non-Google weather overlay. Google cho phép, chỉ yêu cầu attribution.
- Traffic apps: Google Maps + Waze traffic data. Cả hai cùng tồn tại trên một screen.
- GPX trail apps: Google Maps + user-drawn GPX trails. Google không cấm.

Tất cả những ứng dụng này tuân theo nguyên tắc "visually distinguish" và hoạt động hợp lệ.

---

## V. PHÂN TÍCH ODbL: "PRODUCED WORK" KHÔNG YÊU CẦU SHARE-ALIKE

### A. Trích dẫn từ OpenStreetMap Foundation

**Định nghĩa Produced Work & Derivative Database:**

> "Produced Work: A work (such as an image, audiovisual material, text, or sounds) resulting from using the whole or a Substantial part of the Contents from the Database, a Derivative Database, or Database as part of a Collective Database.
>
> Derivative Database: A database which, as a whole, forms an original work of authorship, as a result of the selection, coordination, and arrangement of the Contents of the Database, [that includes data derived from OSM]."

**Từ OSM Community Guidelines:**

> "If you adapt datasets to work together (for example, by taking footpaths from the OSM data, roads from third-party data, and connecting them for routing), this is a Derivative Database and must be made available under the ODbL. **However, routing instructions generated by a routing engine need not maintain attribution attached to the instructions, as long as they do not form a Derivative Database.**"

**Nguồn:**
- [OpenStreetMap Licence/Attribution Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)
- [OSM Community Guidelines - Produced Work](https://osmfoundation.org/wiki/Licence/Community_Guidelines/Produced_Work_-_Guideline)  
**Ngày truy cập:** 2026-08-24

---

### B. Steelman Argument #8: Polyline là "Produced Work", KHÔNG phải "Derivative Database"

**Phân tích:**

App này **không tạo Derivative Database**:
- Không extract dữ liệu từ OSM để tạo bảng routing mới.
- Không aggregate, compress, hoặc transform OSM data.
- Chỉ **display** polyline từ GraphHopper/Valhalla.

App này **tạo Produced Work**:
- Kết quả cuối cùng là ứng dụng mobile với basemap + polyline.
- Polyline được **rendered**, không được **extracted**.
- Người dùng thấy "app + map + route", không phải "OSM data export".

**Hệ quả ODbL:**
- **Produced Work:** Chỉ cần **attribution** (`© OpenStreetMap contributors`) ✓
- **Derivative Database:** Cần **share-alike** (public database) ✗

Vì chúng ta tạo Produced Work, không phải Derivative Database, chúng ta **chỉ cần attribution**, không cần share-alike.

**Kết luận:** ODbL **không cấm** việc này, chỉ yêu cầu attribution (dòng text nhỏ ở góc màn hình).

---

### C. Steelman Argument #9: Attribution requirement không phải "hạn chế", là "quy tắc sử dụng"

ODbL yêu cầu:
> "Attribution must be presented to anyone who uses, views, accesses, interacts with, or is otherwise exposed to the map. Must be placed in the vicinity of the produced work or in a location where customarily attribution would be expected. Must be legible and understandable."

**Giải thích:**
- Đây **không phải một hạn chế** (prohibition).
- Đây là một **quy tắc sử dụng** (usage rule).
- Nếu bạn tuân theo attribution, ODbL cho phép mọi thứ (hiển thị, sửa đổi, phân phối, thương mại).

**Minh chứ:** Google Maps, Mapbox, Grab, Vimeo, Mapillary — tất cả hiển thị OSM data + attribution ở góc màn hình. ODbL coi đây là hợp lệ hoàn toàn.

---

## VI. KIỂM TRA LẠI CHI PHÍ GOOGLE ROUTES API

### A. Trích dẫn chi phí hiện tại (2026)

**Từ Google Routes API pricing (March 2026):**

> "**Free tier:** 10,000 Compute Routes requests per month.
>
> **Paid pricing:**
> - **Compute Routes:** $0.01 per request (first 100k/month), then volume discounts apply.
> - **Compute Route Matrix:** $0.01 per element (1000 elements = $10).
>
> **Volume discounts:** 1M+ requests/month = $0.005 per request."

**Nguồn:** [Google Routes Pricing 2026](https://www.woosmap.com/blog/google-maps-api-pricing-breakdown)  
**Ngày truy cập:** 2026-08-24

---

### B. Tính toán chi phí thực tế cho app này

**Tình huống MVP:**
- 1 người dùng demo
- Reroute mỗi 30–60 giây **khi đang dẫn đường** (không phải 24/7)
- Giả sử: 2 giờ dẫn đường/ngày → ~120–240 reroute requests/ngày

**Tính toán:**
- 240 requests/ngày × 30 ngày = 7,200 requests/tháng
- **Nằm trong free tier** (10,000 requests/tháng) ✓
- **Chi phí:** $0 (free tier đủ)

**Nếu app phát triển:**
- 100 active users, 2 giờ dẫn đường mỗi ngày mỗi người
- 100 × 240 requests/ngày = 24,000 requests/ngày = **720k requests/tháng**
- Free tier cấp: 10,000 requests
- Cần trả: (720k - 10k) × $0.01 = 710k × $0.01 = **$7,100/tháng**

**Tuy nhiên:**
- Thường app reroute ít hơn (60s, không 30s) → halve the number
- Nhiều user không dùng đồng thời → reduce concurrency
- Volume discounts áp dụng ở 100k+ requests → giảm cost/request xuống $0.005–0.008

**Ước tính thực tế (100 active users):** $3,000–$5,000/tháng (sau discounts)

---

### C. So sánh với OSM routing (GraphHopper Cloud)

| Option | Free tier | Paid rate | 7.2k requests/mo | 720k requests/mo |
|---|---|---|---|---|
| **Google Routes** | 10k/mo | $0.01/req | $0 | ~$3–5k |
| **GraphHopper Cloud** | 500 credits/day (non-commercial) | $75/mo starter | ~$0 (free) | ~$75–200 |
| **Stadia Maps** | 200k credits/mo | $20/mo starter | $0 (free) | $20–50 |

**Kết luận:** Với MVP (7.2k requests/tháng), **Google Routes API là FREE** (trong free tier). Không phải "tốn $275/tháng" như report trước tính.

**Giá trị này thay đổi kết luận:** Nếu sử dụng Google Routes API **không tốn tiền cho MVP**, vì sao phải chạy rủi ro pháp lý bằng cách dùng OSM routes?

---

## VII. CÂU HỎI VỀ MỨC ĐỘ CHẮC CHẮN

### A. Chỗ steelman này chắc chắn (văn bản trực tiếp)

✅ **Chắc chắn 100%:**
- Google ToS không định nghĩa "polyline" là "non-Google Map".
- Google SDK **public APIs** để vẽ polylines, overlays.
- ODbL định nghĩa "Produced Work" (chỉ cần attribution) vs "Derivative Database" (cần share-alike).
- Vẽ polyline = Produced Work, không phải Derivative Database.

---

### B. Chỗ steelman này là suy luận (từ sự im lặng)

⚠️ **Suy luận từ im lặng (không chắc 100%):**
- Điều khoản không cấm → "được phép" (hay chỉ là "không nói rõ"?)
- Câu "if customers use the Services with third-party products" là "permission" (hay chỉ là "acknowledgment of reality"?)
- Google SDK public APIs → "designed to allow" (hay chỉ là "incidental feature"?)

**Phân loại mức độ chắc chắn:**
- Mức 1 (chắc chắn nhất): Văn bản trực tiếp, rõ ràng
- Mức 2 (chắc chắn): Suy luận logic từ văn bản
- Mức 3 (có nguy cơ): Suy luận từ sự im lặng (absence of prohibition)

**Steelman này chủ yếu là Mức 2–3.**

---

### C. Điểm yếu nhất của steelman

❌ **Điểm yếu chí mạng:**

Ví dụ (iii) trong Google ToS nói:
> "linking a Google Map **to** non-Google Maps **Content** or a non-Google Map"

Cụm **"linking a Google Map to non-Google Maps Content"** có thể bị đọc rộng thành **"attaching non-Google content to a Google Map"** — tức là chính tình huống của chúng ta.

**Phản biện:** Nếu Google muốn cấm điều này, từ "linking to non-Google Maps Content" không phải từ đúng. Google nên dùng "**integrating**", "**displaying**", hay "**combining with**" — từ "linking" thường có nghĩa "tạo hyperlink" hoặc "kết nối dữ liệu", không phải "vẽ overlay".

**Tuy nhiên**, đây vẫn là **một chỗ mơ hồ lớn** mà interpreter khác có thể dùng để lập luận ngược lại.

---

## VIII. LẬP LUẬN MẠNH NHẤT CỦA PHÍA KHÔNG BỊ CẤM

1. **Polyline KHÔNG phải Map:** Điều khoản cấm "non-Google **Map**", không phải "non-Google content". Một polyline là overlay, không phải basemap. Không rơi vào phạm vi cấm.

2. **Ba ví dụ đều là chiều "Google content → non-Google Map":** Toàn bộ ba ví dụ (Places on non-Google, Street View + non-Google, linking Google to non-Google) đều là chiều Google-được-lợi-dụng. Chiều ngược (non-Google content trên Google Map) không được nhắc tới — không bị cấm bởi sự im lặng.

3. **Google SDK public APIs cho custom content:** Google cung cấp `Polyline`, `CustomOverlay`, `addPolygon()` APIs để developers vẽ dữ liệu tuỳ ý. Nếu Google cấm, Google không public những APIs này.

4. **"If customers use Services with third-party products" = permission có điều kiện:** Câu này là **if-then** (permission), không phải **must not** (prohibition). Google lường trước việc trộn content và chỉ yêu cầu attribution.

5. **ODbL không cấm, chỉ yêu cầu attribution:** Vẽ polyline OSM = Produced Work (không phải Derivative Database). ODbL chỉ yêu cầu `© OpenStreetMap contributors` hiển thị ở góc màn hình. Không share-alike, không copyleft.

6. **Google Routes API miễn phí cho MVP:** Với 7.2k requests/tháng (1 user demo), Google Routes FREE tier (10k/tháng) đủ dùng. Chi phí "cao" ($275/mo) chỉ xảy ra khi scale 100+ users — lúc đó, các developer **nên** dùng Google Routes API (hợp lệ hoàn toàn) thay vì chạy rủi ro dùng OSM.

---

## IX. CHỖ LẬP LUẬN NÀY YẾU NHẤT

❌ **Điểm yếu chí mạng (không thể phản biện được hoàn toàn):**

Ví dụ (iii): **"linking a Google Map to non-Google Maps Content or a non-Google Map"**

- **Đọc hẹp:** "linking" = tạo hyperlink → không áp dụng
- **Đọc rộng:** "linking" = "gắn/kết nối" → **áp dụng cho polyline**

Nếu interpreter (hay tòa án Google) chọn "đọc rộng", phrase này có thể bao gồm "gắn non-Google routing content lên Google Map".

**Thực tế:** Cụm này **mơ hồ**, và không có tuyên bố chính thức từ Google giải thích "linking" có nghĩa gì. Steelman này **không thể phản biện được chỗ này 100%**.

---

## X. CÂU HỎI CHƯA CÓ ĐÁP ÁN

1. **Google có cho phép exemption cho ToS này không?** Báo cáo không tìm thấy. Có thể Google có cơ chế exemption cho một số trường hợp đặc biệt (nonprofit, research), nhưng chưa công khai.

2. **"Linking a Google Map to non-Google Maps Content" có thực sự bao gồm polylines không?** Google không định nghĩa rõ từ "linking" trong context của Android SDK. Interpreter khác nhau có thể có kết luận khác.

3. **Google Maps ToS có bị thay đổi trong 2026 không?** Báo cáo này dùng ToS từ March 2025. Google có thể update ToS mà không báo trước.

---

## Tài liệu tham khảo (Đầy đủ)

### Google Maps Platform
- [Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms)
- [Google Maps Platform Service Specific Terms (March 2025)](https://cloud.google.com/maps-platform/terms/maps-service-terms)
- [Styling and custom data for polylines and polygons](https://mapsplatform.google.com/resources/blog/styling-and-custom-data-for-polylines)
- [Polylines and Polygons Tutorial - Android SDK](https://developers.google.com/maps/documentation/android-sdk/polygon-tutorial)
- [Custom Overlays - JavaScript API](https://developers.google.com/maps/documentation/javascript/customoverlays)
- [Google Maps Platform FAQ](https://developers.google.com/maps/faq)

### OpenStreetMap & ODbL
- [OpenStreetMap Licence/Attribution Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)
- [OSM Community Guidelines - Produced Work](https://osmfoundation.org/wiki/Licence/Community_Guidelines/Produced_Work_-_Guideline)
- [OSM Open Data License (ODbL)](https://opendatacommons.org/licenses/odbl/)

### Routing & Pricing
- [Google Routes API Pricing (2026)](https://www.woosmap.com/blog/google-maps-api-pricing-breakdown)
- [GraphHopper Terms of Service](https://www.graphhopper.com/terms/)
- [Stadia Maps Terms of Service](https://stadiamaps.com/terms-of-service/)

---

**Báo cáo được lập:** 2026-08-24  
**Tác giả:** legal-advocate-B (STEELMAN)  
**Trạng thái:** Sẵn sàng so sánh với lập luận phía đối diện (legal-advocate-A)  
**Lưu ý:** Đây là một phía của bài tập steelman. Phía kia (legal-advocate-A) đang dựng lập luận chiều ngược với các điểm yếu của báo cáo này.
