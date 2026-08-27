# Lập luận Steelman: Vẽ Polyline Từ OSM (Valhalla/GraphHopper) Lên Google Maps **BỊ CẤUI THEO ToS**

**⚠️ Tuyên bố:** Đây là bài tập steelman có chủ đích dựng lập luận một phía mạnh nhất cho phía "VI PHẠM". Đây **KHÔNG** phải tư vấn pháp lý chính thức, không phải kết luận cuối cùng, và sẽ bị đối chiếu với lập luận chiều ngược lại trên cùng bộ điều khoản.

---

## 1. Các Điều Khoản Cấm Chính Từ Google Maps Platform ToS

### 1.1 Restriction: "No Use With Non-Google Maps" (2025)

**Trích nguyên văn từ Google Maps Platform Service Specific Terms (hiện hành, last modified 27 Jan 2025):**

> "Customer will not use the Google Maps Core Services in a Customer Application that contains a non-Google map."

**Nguồn:** [Google Maps Platform Acceptable Use Policy](https://cloud.google.com/maps-platform/terms/aup) — truy cập 2025-01-27

**Định nghĩa từ các search results chính thức:**
- "To avoid quality issues and/or brand confusion, customers are prohibited from using Google Maps Core Services with or near a non-Google Map in a customer application."
- Ví dụ được liệt kê: "(i) display Places listings on a non-Google map, or (ii) display Street View imagery and non-Google maps in the same Customer Application"
- **Phạm vi:** Restriction này áp dụng cho các Core Services bao gồm Maps SDK for Android (sản phẩm đang dùng).

**Nguồn:** Archived Google Maps Platform Terms of Service (2018-07-09, confirmed consistent in 2025-01-30 version) — [https://cloud.google.com/archive/maps-platform/terms-20250130](https://cloud.google.com/archive/maps-platform/terms-20250130)

### 1.2 Cấm Cụ Thể Với Các Service Khác

**Trích nguyên văn từ Service Specific Terms:**

- **Directions API:** "Customers must not use Google Maps Content from the Directions API in conjunction with a non-Google map."
- **Geolocation API:** "Customers must not use Google Maps Content from the Geolocation API in conjunction with a non-Google map."  
- **Navigation SDK:** "Customers must not use Google Maps Content from the Navigation SDK in conjunction with a non-Google map."

**Nguồn:** [Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms) (current version, search result June 2026) — và [Archived version 2025-03-31](https://cloud.google.com/archive/maps-platform/terms/maps-service-terms-20250331)

**Điểm quan trọng:** Mặc dù FamilyTrackerDemo không dùng trực tiếp Directions/Routes API của Google, nhưng sự cấm chung về "use with non-Google map" áp dụng cho **toàn bộ Google Maps Core Services**, bao gồm **Maps SDK for Android** (base map rendering engine).

### 1.3 Định Nghĩa "Google Maps Core Services"

**Định nghĩa:**
- Google Maps Core Services là các dịch vụ được mô tả tại Google Maps Platform terms
- Bao gồm: map tiles, satellite imagery, geocoding data, routing information, place information
- **Maps SDK for Android** là một Core Service

**Nguồn:** [Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms) — search result index 4 from cloud.google.com

### 1.4 Định Nghĩa "Non-Google Map"

**Định nghĩa từ chính sách:**
- Non-Google Map = Third-party mapping services (OpenStreetMap, Mapbox, MapKit, HERE, v.v.)
- Hay: Competing map platforms
- Hay: Custom maps created outside Google's ecosystem

**⚠️ GHI CHÚ QUAN TRỌNG:** Tài liệu Google **KHÔNG** cung cấp định nghĩa chính thức, ngoài văn bản về các "examples". Định nghĩa này được suy luận từ context và practice description.

**Nguồn:** Deduced from [Google Maps Platform third-party platforms FAQ](https://developers.google.com/maps/third-party-platforms/faq) và language in Service Specific Terms

### 1.5 Mục Đích Của Restriction

**Mục đích được nêu rõ:**
> "To avoid quality issues and/or brand confusion..."

**Điều này có nghĩa:**
1. Google lo lắng về sự nhầm lẫn người dùng — họ có thể nghĩ tất cả dữ liệu (bản đồ + polyline) đều từ Google
2. Google lo lắng về quality — dữ liệu từ bên thứ ba có thể không đạt tiêu chuẩn Google, khiến người dùng có trải nghiệm tệ hơn

---

## 2. Lập Luận "VI PHẠM" — Tình Huống Cụ Thể

### 2.1 Polyline Từ Valhalla/GraphHopper Có Phải "Non-Google Map Content" Không?

**Lập luận VI PHẠM:**

1. **Polyline là dữ liệu mapping** — nó biểu diễn tuyến đường trên bản đồ. Vathalla/GraphHopper là routing engine dựa trên OpenStreetMap, không phải Google Maps data.

2. **OpenStreetMap = "non-Google map" thứ yếu** — mặc dù Valhalla chỉ là engine, nhưng dữ liệu gốc (OSM) là từ một "non-Google map" platform. Việc render polyline từ OSM lên trên Google Maps là việc kết hợp (mixing) hai source.

3. **"Use with non-Google map" được diễn giải rộng** — restriction nói "use Google Maps Core Services with or near a non-Google Map". Từ "with" có thể có nghĩa là:
   - "cùng lúc trên cùng màn hình" (specific example: Street View + non-Google maps)
   - **Hay** "cùng dữ liệu trong cùng ứng dụng" (broader interpretation)
   
   Polyline từ OSM được render lên Google Maps UI là việc "use [Google Maps Core Services (Maps SDK)] with [OSM data (non-Google map content)]".

4. **Linking restriction áp dụng** — Restriction nói "link a Google Map to non-Google Maps content or a non-Google map". Polyline chính là dạng "linking": Google Maps base layer + Valhalla polyline = một visualization kết hợp.

### 2.2 Khó Khăn: App Chưa "Chạy Toàn Bộ Trên Non-Google Map"

**Lập luận VI PHẠM có phần yếu ở đây:**
- App vẫn dùng **Google Maps SDK** làm base layer
- Không có một map layer thứ hai từ non-Google platform

**Nhưng, lập luận VI PHẠM phản biện:**
- Restriction không nói "dùng **chỉ** non-Google maps" — nó nói "dùng Google Maps Core Services **với** non-Google map"
- "Với" (with) có nghĩa là kết hợp, không nhất thiết phải có hai bản đồ toàn diện trên màn hình
- Polyline từ Valhalla **là** sự kết hợp — mixing dữ liệu từ hai source trong cùng một visualization

### 2.3 Cấm Cấp Cao: "No Re-Creating Google Products or Features"

**Nếu một restriction kiểu này tồn tại trong ToS:**
> "You must not use the Services to develop or improve a competing product or service" 
> [hoặc] "You must not use Google Maps Content to augment, enhance, or create a competing map service"

**Thì lập luận VI PHẠM sẽ nói:**
- Đang dùng Google Maps SDK + Valhalla polyline = tạo ra một "hybrid routing product" 
- Điều này có thể coi là việc "augment" dữ liệu Google Maps bằng data từ competing source (OSM)

**⚠️ STATUS:** Tôi **KHÔNG** tìm thấy clause này một cách rõ ràng trong Service Specific Terms. Sử dụng nguyên tắc thận trọng: **không được bịa.**

---

## 3. Attribution Requirements: Điều Khoản Mô Tả Cho Phép Mixing (⚠️ YẾU ĐIỂM)

### 3.1 Requirement Khi Mixing Content

**Từ search results chính thức:**

> "Customers are responsible for complying with Google's attribution requirements, including making it clear to end users what is Google Maps Content and what content is not from Google."

**Cũng từ policies:**

> "When overlaying third-party geospatial data with Google Maps data as a basemap, you must not overlap or obscure the Google data attribution with third-party data attribution, and the attribution of third-party data must clearly be disassociated from Google's data attributions."

**Nguồn:** [Policies and attributions for Maps JavaScript API](https://developers.google.com/maps/documentation/javascript/policies) and [Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/policies)

### 3.2 Vấn Đề Với Lập Luận "VI PHẠM"

**Điều khoản này NGẦM CHO PHÉP mixing content** — nếu tuyệt đối cấm mixing, tại sao Google lại nêu requirements cho việc mixing? Điều này gợi ý rằng:

1. Mixing **là** khả thi, nhưng kèm điều kiện
2. Attribution rõ ràng làm cho mixing trở thành chấp nhận được
3. Restriction "use with non-Google map" có thể không áp dụng cho **dữ liệu** bên thứ ba, mà chỉ áp dụng cho **map platforms** bên thứ ba (ví dụ: hiển thị cạnh nhau Mapbox + Google Maps)

**👉 Đây là YẾU ĐIỂM lập luận "VI PHẠM"** — tôi sẽ nêu rõ ở cuối.

---

## 4. Exceptions Có Liên Quan: EEA Rules (Từ Tháng 7/2025)

### 4.1 EEA Customers Now Allowed

**Từ EEA FAQ (chính thức):**

> "EEA customers may now use Google Maps Platform Services for real-time navigation with a third-party map, as long as that real-time navigation usage complies with Google's safety requirements."

**Effective date:** 8 July 2025

**Nguồn:** [Google Maps Platform EEA FAQ](https://developers.google.com/maps/comms/eea/faq)

### 4.2 Hàm Ý Của EEA Exception

**Lập luận VI PHẠM:**
- EEA được cho phép "use Google Maps Platform Services... with a third-party map" — và polyline là một "Google Maps Platform Service" (rendering on Maps SDK)
- Tuy nhiên, **non-EEA customers vẫn bị cấm**
- Vì FamilyTrackerDemo là app demo, không rõ target geography — nhưng nếu target non-EEA users, thì restriction vẫn áp dụng

**Điểm yếu:** EEA exception chỉ **cụ thể nói** "real-time navigation with a third-party map" — polyline tĩnh từ Valhalla trong một demo family tracker **không phải** "navigation use case" theo nghĩa formal của Google.

---

## 5. Không Tìm Thấy Bằng Chứng Chính Thức Khác

### 5.1 Evidence Tìm Kiếm

🔍 **Tìm kiếm:** Google staff comments, Stack Overflow, GitHub issues, blogs  
📍 **Kết quả:** Không tìm thấy phát biểu chính thức nào từ Google engineers nói rõ liệu polyline từ Valhalla/GraphHopper lên Google Maps có bị cấm hay không.

🔍 **Tìm kiếm:** Case study — app nào từng bị Google cảnh báo/khoá key vì lý do này?  
📍 **Kết quả:** ⚠️ Không tìm thấy.

⚠️ **KẾT LUẬN:** Không có bằng chứng trực tiếp từ Google về vấn đề này — chỉ có restriction văn bản trong ToS.

---

## 6. Lập Luận Mạnh Nhất Của Phía VI PHẠM

1. **Restriction rõ ràng tồn tại:**
   > "Customer will not use the Google Maps Core Services in a Customer Application that contains a non-Google map."
   
   Maps SDK for Android **là** Core Service. Valhalla/GraphHopper **không phải** Google Maps data. Polyline từ OSM được render lên Google Maps = "Core Service with non-Google content".

2. **Mục đích của restriction có liên quan:**
   - Restriction nhằm "avoid quality issues and/or brand confusion"
   - Polyline từ OSM có thể gây confusion (người dùng không biết dữ liệu nào từ Google)
   - Polyline từ OSM có thể có chất lượng khác biệt so với Google Directions (khác data source)

3. **"Linking" interpretation:**
   - "Link a Google Map to non-Google Maps content" — polyline từ Valhalla **là** linking: base Google Maps + overlay OSM routing
   - Không cần phải là hai map platforms riêng biệt; linking có thể là việc kết hợp dữ liệu

4. **Broad reading của "use with":**
   - "Use Google Maps Core Services with non-Google map" — "with" không chỉ nghĩa là "cùng màn hình" mà còn có thể là "cùng dữ liệu" trong cùng visualization

---

## 7. Chỗ Lập Luận Này Yếu Nhất

1. **Attribution clause cho phép mixing (CRITICAL WEAK POINT):**
   - Google explicitly nêu requirements cho "overlaying third-party geospatial data with Google Maps data as a basemap"
   - Nếu tuyệt đối cấm, tại sao Google lại viết requirements cho nó?
   - Clause này gợi ý: mixing **CÓ THỂ** chấp nhận được nếu kèm attribution rõ ràng
   - **Lập luận VI PHẠM không thể giải quyết tốt điểm này**

2. **Polyline không phải "map platform":**
   - Valhalla/GraphHopper là routing engines, không phải map platforms
   - "Non-Google map" trong restrict có thể chỉ nói đến map platforms competing (Mapbox, OpenStreetMap web, v.v.), không phải routing data sources
   - Polyline là **dữ liệu**, không phải **platform**

3. **Không cấm "polyline" một cách tường minh:**
   - Restriction nêu ví dụ cụ thể: Places, Street View, Directions API
   - Polyline từ third-party routing **không** nằm trong ví dụ
   - Có thể Google chỉ cấm các Core Services content cụ thể (Places, SV), không cấm arbitrary third-party data

4. **EEA exception áp dụng:**
   - Dù chỉ cho EEA, nhưng nó cho thấy Google **đã bắt đầu** lơi lỏng restriction này (tính từ tháng 7/2025)
   - Điều này gợi ý Google không coi việc mixing Google Maps + third-party content là vi phạm **bản chất**, mà chỉ là rủi ro chất lượng/brand

5. **Không có definition chính thức của "non-Google Map":**
   - Tôi **KHÔNG** tìm thấy definition chính thức từ Google
   - Suy luận từ examples không đủ mạnh — có thể sai lệch

6. **Tình huống app này khác với ví dụ trong ToS:**
   - Ví dụ ToS: "display Street View + non-Google maps" = hai map UIs cạnh nhau
   - Tình huống app: Google Maps base + Valhalla polyline = một single visualization, không phải "two maps"
   - Mức độ violation có thể khác biệt so với ví dụ trong ToS

---

## 8. Kết Luận Tạm Của Bài Steelman

**Từ góc độ steelman "VI PHẠM":**
- Restriction "No Use With Non-Google Maps" **có thể** áp dụng nếu polyline từ OSM được xem là "non-Google map content"
- Mục đích restriction (quality/brand confusion) có liên quan đến tình huống
- Điều này **rủi ro** theo ngữ pháp ToS

**Nhưng:**
- Attribution requirements cho phép mixing nếu có ghi rõ
- Definition của "non-Google map" không rõ ràng
- Polyline không phải "map platform"
- Không có case study hoặc warning chính thức từ Google về scenario tương tự
- EEA exception cho thấy Google đang lơi lỏng quy tắc

**Độ chắc chắn:** ~60% risk (medium-high) dựa trên văn bản ToS, nhưng không phải high-certainty violation. Cần legal review từ Google hoặc lawyer để kết luận chắc chắn.

---

## Nguồn Được Trích Dẫn

- [Google Maps Platform Acceptable Use Policy](https://cloud.google.com/maps-platform/terms/aup) — 27 Jan 2025
- [Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms) — current version
- [Google Maps Platform Archived Terms (20250331)](https://cloud.google.com/archive/maps-platform/terms/maps-service-terms-20250331)
- [Google Maps Platform Archived Terms (20250130)](https://cloud.google.com/archive/maps-platform/terms-20250130)
- [Google Maps Platform EEA FAQ](https://developers.google.com/maps/comms/eea/faq) — effective 8 July 2025
- [Policies and attributions for Maps JavaScript API](https://developers.google.com/maps/documentation/javascript/policies)
- [Policies and attributions for Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/policies)
- Web search results from Google Cloud domains (searched 2026-08-24)
