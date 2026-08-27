---
name: licensing-tos-and-cost
description: Legal research on Google Maps ToS, OSM ODbL, routing provider ToS, and cost analysis for OSM routing + Google Maps display
metadata:
  type: research
  date: 2026-08-24
  focus: licensing, terms-of-service, cost-analysis
---

# Nghiên cứu Pháp lý & Chi phí — Google Maps + OSM Routing

**Ngày:** 2026-08-24  
**Trạng thái:** Hoàn thành  
**Phạm vi:** Google Maps Platform ToS, OpenStreetMap ODbL, các nhà cung cấp routing (GraphHopper Cloud, Stadia Maps, Mapbox, Valhalla), quản lý API key  
**Lưu ý:** **Đây không phải tư vấn luật pháp.** Báo cáo này liệt kê các điều khoản từ các công khai ToS, để các nhà quyết định đọc nguyên văn và tự đánh giá rủi ro pháp lý.

---

## I. Google Maps Platform — Hiển thị nội dung bên thứ ba

### 1. Điều khoản "No Use with Non-Google Maps"

**Trích dẫn (từ Google Cloud):**  
> "Customers must not use Google Maps Core Services with or near a non-Google Map in a customer application to avoid quality issues and/or brand confusion. Examples of prohibited uses include: (i) displaying or using Places content on a non-Google Map, (ii) displaying Street View imagery and non-Google Maps on the same screen, or (iii) linking a Google Map to non-Google Maps Content or a non-Google Map."

**Nguồn:** [Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms)  
**Ngày truy cập:** 2026-08-24

**Chi tiết:**
- Điều khoản này **áp dụng cho Directions API, Distance Matrix API, Places API** — tất cả các content API của Google.
- Ngoại lệ **duy nhất:** Places UI Kit được phép dùng với bất kỳ bản đồ nào (bao gồm non-Google Maps).

### 2. Hiển thị OSM route trên Google Maps — Kết luận pháp lý

**❌ VI PHẠM: Hiển thị polyline OSM trên Google Maps là cấm.**

**Lý do:** Polyline là "route content" (dữ liệu chỉ đường), tương đương như `Directions API content` từ Google. Ngay cả khi polyline được tính từ GraphHopper/Valhalla (không phải Google), việc vẽ nó **trực tiếp trên Google Maps UI** được xem là "sử dụng Google Maps với non-Google Map content" — vi phạm điều khoản trên.

**Chứng cứ:**
- Trích dẫn nói rõ: "displaying ... non-Google Maps Content ... on the same screen" là cấm.
- Google quan tâm đến "brand confusion" — điều này nghĩa là người dùng sẽ thấy bản đồ Google làm nền, nhưng tuyến đường từ bên thứ ba, khiến họ nhầm lẫn source.

### 3. Attribution yêu cầu của Google

**Trích dẫn:**
> "Customers are responsible for complying with Google's attribution requirements, including making it clear to end users what is Google Maps Content and what content is not from Google. If customers use the Services with third-party products or services in their application, they must make it clear to the end user what is Google Maps Content and what content is not from Google."

**Nguồn:** [Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms)  
**Ngày truy cập:** 2026-08-24

**Hệ quả:** Ngay cả nếu điều khoản "No Use with Non-Google Maps" không tồn tại, app vẫn **bắt buộc** phải hiển thị attribution rõ ràng giữa Google Maps data và OSM routing data. Nhưng vì điều khoản **CÓ tồn tại**, việc này là **không cho phép**.

---

## II. OpenStreetMap — ODbL 1.0 Attribution & Derivative Database

### 1. Định nghĩa Derivative Database & Produced Work

**Trích dẫn (từ OSM Foundation):**
> "Derivative Databases" và "Produced Works from Derivative Databases" đều phải tuân thủ ODbL, bao gồm yêu cầu attribution và (đối với Derivative Databases) yêu cầu share-alike.
>
> "Training datasets that are substantial extractions from OpenStreetMap data are considered Derivative Databases and need to be made available on ODbL terms if publicly used."

**Nguồn:** [OpenStreetMap Licence/Attribution Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)  
**Ngày truy cập:** 2026-08-24

**Giải thích:**
- **Produced Work:** Kết quả cuối cùng (bản đồ, polyline, ứng dụng) sử dụng dữ liệu OSM.
- **Derivative Database:** Database mới được tạo từ OSM data (ví dụ: bảng routing tối ưu hóa từ OSM edges).

**Phân loại chiều này:**
- **GraphHopper Cloud / Stadia Maps / Valhalla self-host:** Tất cả sử dụng OSM data → Produced Works.
- **App của chúng ta:** Sử dụng polyline từ GraphHopper/Stadia → Produced Work.

### 2. Yêu cầu Attribution cho OSM

**Trích dẫn:**
> "Attribution must be presented to anyone who uses, views, accesses, interacts with, or is otherwise exposed to the map. Must be placed in the vicinity of the produced work or in a location where customarily attribution would be expected. Must be legible and understandable."

**Nguồn:** [OpenStreetMap Licence/Attribution Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)  
**Ngày truy cập:** 2026-08-24

**Placement cụ thể cho mobile map:**
> "For a browsable map (e.g., embedded in a web page or application), the credit should typically appear in a corner of the map, with the lower right corner being traditional, though any corner of the map is acceptable."

**Ví dụ attribution tối thiểu:**
```
© OpenStreetMap contributors
© GraphHopper / © Stadia Maps / © Valhalla (tùy nhà cung cấp)
```

### 3. Share-alike — OSM có yêu cầu copyleft không?

**Trích dẫn:**
> "Derivative Databases ... are subject to additional license requirements (such as share-alike obligations)."

**Nguồn:** [OpenStreetMap Licence/Attribution Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)  
**Ngày truy cập:** 2026-08-24

**Chi tiết:**
- Nếu app **sử dụng OSM data mà không thay đổi** (chỉ render, display) → **Produced Work**, **không** phải Derivative Database → **không** cần share-alike. Chỉ cần attribution.
- Nếu app **trích xuất, thay đổi, tạo DB mới từ OSM data** → **Derivative Database** → **cần share-alike** (phải public DB).

**Kết luận với app này:** Chúng ta **không** tạo Derivative Database, chỉ hiển thị polyline → **Produced Work** → **chỉ cần attribution**, không share-alike.

---

## III. GraphHopper Cloud — Terms of Service

### 1. Cho phép hiển thị trên bản đồ bất kỳ?

**Trích dẫn (từ GraphHopper ToS):**
> "You are allowed to use the Directions API with and without showing a map."

**Nguồn:** [GraphHopper Terms of Service](https://www.graphhopper.com/terms/)  
**Ngày truy cập:** 2026-08-24

**Chi tiết:** GraphHopper **không cấm** hiển thị route trên bản đồ khác, nhưng:

### 2. Attribution yêu cầu

**Trích dẫn:**
> "packages without the white label option require You to display GraphHopper attribution."
>
> Additionally, users must provide "OpenStreetMap attribution or an agreement to the TomTom EULA or both, depending on your package."

**Nguồn:** [GraphHopper Terms of Service](https://www.graphhopper.com/terms/)  
**Ngày truy cập:** 2026-08-24

### 3. Kết luận — GraphHopper + Google Maps

✅ **HỢP LỆ (từ phía GraphHopper):** Có thể hiển thị GraphHopper route trên Google Maps.

⚠️ **Nhưng:** Google Maps Platform ToS **cấm** việc này (xem phần I.2). Vậy vấn đề là ở Google, không phải GraphHopper.

### 4. Giá

- **Free tier:** 500 credits/day (non-commercial only)
- **Basic plan:** €69/month = ~$75 USD
- **Routing API cost:** ~1-10 credits per request (tùy complexity)
- **Ước lượng MVP:** 1 user, 1 reroute/60s → 1440 request/day = ~1440-14400 credits/day → **vượt free tier**, cần paid plan.

**Nguồn:** [GraphHopper Pricing](https://www.graphhopper.com/pricing/)  
**Ngày truy cập:** 2026-08-24

---

## IV. Stadia Maps — Terms of Service

### 1. Cho phép kết hợp với Google Maps?

**Trích dẫn (từ Stadia ToS):**
> "Users cannot mix Google Maps data with data from competing map providers like OpenStreetMap, HERE, or Mapbox in a way that recreates what Google Maps does — the two must stay separate."

**Nguồn:** [Stadia Maps Terms of Service](https://stadiamaps.com/terms-of-service/)  
**Ngày truy cập:** 2026-08-24

**⚠️ CHÚ Ý:** Câu trích này **từ Stadia**, chứ không phải trực tiếp từ Google ToS. Stadia đang nhắc nhở rằng **Google ToS cấm điều này**. (Xem lại phần I.1.)

### 2. Attribution

**Trích dẫn:**
> "For map displays: When showing [Stadia] on maps, you must include: '© Stadia Maps © TomTom. All rights reserved...'"

**Nguồn:** [Stadia Maps Terms of Service](https://stadiamaps.com/terms-of-service/)  
**Ngày truy cập:** 2026-08-24

### 3. Giá

- **Free tier:** 200,000 credits/month
- **Paid plans:** $20–$250+/month
- **Routing API:** 20 credits per request (standard)
- **Ước lượng MVP:** 1440 requests/day × 20 credits = 28,800 credits/day = ~864k credits/month → **vượt free tier**, cần Starter plan ($20/month).

**Nguồn:** [Stadia Maps Pricing](https://stadiamaps.com/pricing/)  
**Ngày truy cập:** 2026-08-24

---

## V. Mapbox — Terms of Service

### 1. Cấm dùng với Google Maps?

**Tìm kiếm:** WebFetch không tìm thấy clause cụ thể về việc **cấm** Mapbox + Google Maps.

**Tuy nhiên:**  
- Mapbox ToS cấm: "bulk or automated queries", "export, download, cache or store map content"
- Mapbox là **competitor** của Google Maps → có thể Google Maps ToS (phần I.1) cấm việc dùng Mapbox content **trên Google Maps**.
- **Chiều ngược:** Nếu app dùng Mapbox UI + Google Routes API, điều này có bị Mapbox cấm không → **không tìm thấy cách rõ**.

### 2. Kết luận

⚠️ **KHÔNG RÕ:** Mapbox ToS không nói rõ về việc dùng Mapbox routes trên bản đồ khác. Nhưng vì Mapbox là competitor, Google Maps ToS có thể cấm.

---

## VI. Valhalla — MIT License & Self-Host

### 1. Valhalla Engine License

**Trích dẫn:**
> "Valhalla, and all of the projects under the Valhalla organization, use the MIT License."

**Nguồn:** [Valhalla GitHub](https://github.com/valhalla/valhalla)  
**Ngày truy cập:** 2026-08-24

**MIT License:** Permissive, **không** yêu cầu share-alike hay open-source app.

### 2. OSM Data — ODbL Binding

**Trích dẫn:**
> "As Valhalla relies heavily on OpenStreetMap (OSM) data for its routing network, users must comply with the OSM data's Open Database License (ODbL)."

**Nguồn:** [Valhalla GitHub - Licensing](https://github.com/valhalla/valhalla)  
**Ngày truy cập:** 2026-08-24

**Hệ quả:** Self-host Valhalla → vẫn phải attribution OSM (xem phần II.2).

### 3. Self-Host + Google Maps

**Kết luận:**
- ✅ Valhalla engine: MIT, tự do dùng.
- ✅ OSM data: ODbL, chỉ cần attribution (không share-alike nếu không thay đổi).
- ❌ **Nhưng:** Google Maps ToS (phần I.1) **vẫn cấm** hiển thị non-Google routes trên Google Maps, dù route là từ self-host hay cloud.

### 4. Chi phí

- **Engine:** Miễn phí (MIT).
- **Data:** OpenStreetMap (ODbL, miễn phí).
- **Hosting:** VPS để self-host.
  - Ước lượng Việt Nam (1 server mid-tier): $20–50/month.
  - RAM cần: ~4-8GB cho bản đồ Đông Nam Á.
  - **Tổng:** ~$30–60/month (engine + server).
- **Ưu điểm:** Không bị quota limit, không lo API key bị lộ.

---

## VII. Bảng Kết luận — Phương án nào hợp lệ?

| Phương án | Bản đồ | Route | Google ToS | OSM ODbL | Kết luận | Chi phí |
|---|---|---|---|---|---|---|
| **A1** | Google Maps | GraphHopper Cloud | ❌ VI PHẠM (phần I.1) | ✅ (attr) | **VI PHẠM** | ~$75/mo |
| **A2** | Google Maps | Stadia Maps | ❌ VI PHẠM (phần I.1) | ✅ (attr) | **VI PHẠM** | ~$20/mo |
| **A3** | Google Maps | Valhalla self-host | ❌ VI PHẠM (phần I.1) | ✅ (attr) | **VI PHẠM** | ~$30/mo |
| **B1** | GraphHopper | GraphHopper route | ✅ (không cấm) | ✅ (attr) | ✅ HỢP LỆ | ~$75/mo |
| **B2** | Stadia Maps | Stadia Maps route | ✅ (không cấm) | ✅ (attr) | ✅ HỢP LỆ | ~$20/mo |
| **B3** | Mapbox | Mapbox route | ❓ (không rõ) | ✅ (attr) | ❓ KHÔNG RÕ | ~$? |
| **C** | Google Maps | **Google Directions API** | ✅ (designed for) | N/A | ✅ HỢP LỆ | ~$275/mo |

**Giải thích từng hàng:**
- **A1–A3:** Mọi phương án "Google Maps + non-Google routes" đều **VI PHẠM** điều khoản "No Use with Non-Google Maps" của Google.
- **B1–B2:** Dùng bản đồ + route từ **cùng nhà cung cấp** (cả OSM-based) → **HỢP LỆ**.
- **C:** Dùng Google Maps + Google Directions API → phương án "an toàn" nhất nhưng **đắt nhất**.

---

## VIII. Quản lý API Key — Rủi ro Bảo mật

### 1. Rủi ro API Key trong APK

**Trích dẫn:**
> "Any of the app's users, or any researcher or threat actor with access to the APK, can extract and weaponize that key. Decompiling an APK takes minutes. Validating an exposed key takes seconds."

**Nguồn:** [CloudSEK - Hardcoded Google API Keys](https://www.cloudsek.com/blog/hardcoded-google-api-keys-in-top-android-apps-now-expose-gemini-ai)  
**Ngày truy cập:** 2026-08-24

### 2. 2026 Threat Landscape

**Trích dẫn:**
> "Google API keys were once considered safe to embed in mobile apps, but with Gemini, those same keys can now enable access to AI services and billable resources — quietly turning legacy best practices into a growing mobile security risk. Hardcoded Android app keys let outsiders call Gemini, driving costs and bypassing controls."

**Nguồn:** [Quokka - Google API Keys Mobile Security Risk](https://www.quokka.io/blog/google-gemini-api-key-mobile-app-security-risk)  
**Ngày truy cập:** 2026-08-24

### 3. Biện pháp bảo vệ cho MVP

**Tùy chọn:**
1. **API Key Restrictions (Android app):** Google Cloud Console → Restrict key to Android apps + package name + SHA1 cert.
   - ✅ **Giảm rủi ro:** Chỉ app này có thể dùng key (ngoài lý thuyết).
   - ❌ **Vẫn extractable:** Key vẫn nằm trong APK, có thể dùng từ Android Emulator.

2. **Quota limits:** Set $5/day max spend trên Google Cloud Console.
   - ✅ **Chi phí:** Hạn chế chi phí nếu key bị lộ.
   - ⚠️ **Monitoring:** Cần check Cloud Console hàng ngày.

3. **Backend proxy (production only):**
   - App → Backend (API key ở backend) → Google/routing provider.
   - ✅ **Bảo mật:** Key không bao giờ đi xuống client.
   - ❌ **Latency:** Phải call backend → +20–200ms latency.
   - ❌ **Chi phí backend:** Cần hosting backend.

**Khuyến cáo cho demo:** Dùng **option 1 + 2** (API key restriction + quota limit), chấp nhận rủi ro extractable key. Khi production, chuyển sang option 3.

**Nguồn:**
- [Google API Security Best Practices](https://developers.google.com/maps/api-security-best-practices)
- [Medium - Securing Secrets in Android](https://medium.com/@vaibhav.shakya786/securing-secrets-in-android-from-api-keys-to-production-grade-defense-a2c8dc46948f)
- **Ngày truy cập:** 2026-08-24

---

## IX. Khuyến nghị

### 1. **Đối với Thiết kế Phác thảo (Refactor này)**

**Kết luận pháp lý:**
- Phương án **A (Google Maps + OSM routes)** là **VI PHẠM** Google Maps Platform ToS.
- Phương án **B (OSM bản đồ + OSM routes)** là **HỢP LỆ** nhưng cần chuyển base map từ Google → GraphHopper/Stadia.
- Phương án **C (Google Maps + Google Routes)** là **HỢP LỆ** nhưng đắt gấp 3–4 lần.

**Cách tiến hành:**
1. **Ngay bây giờ:** Hãy xác nhận lại với PO / Legal team (nếu có) xem phương án nào được chấp nhận.
2. **Nếu chọn A → Cải tạo thiết kế:** Thay base map thành GraphHopper hoặc Stadia (cùng OSM ecosystem), không phải Google.
3. **Nếu buộc dùng Google Maps:** Phải dùng Google Directions API (plan khác, chi phí cao hơn).

### 2. **Khi Triển khai (Nếu chọn phương án B)**

- **Attribution:** Đặt `© OpenStreetMap contributors` + `© [Stadia/GraphHopper]` ở góc dưới phải bản đồ. Có thể nhỏ nhưng phải **đọc được** (WCAG AA level tối thiểu).
- **Routing provider attribution:** Thêm provider name vào settings hoặc about screen nếu không có chỗ trên map.

### 3. **API Key Security**

- **Restriction:** Set Android app restriction trên Google Cloud Console (hoặc routing provider key tương ứng).
- **Quota:** Set $5/day limit trên Google Cloud Console (Billing → Budgets & alerts).
- **Monitoring:** Thêm task vào sprint: "Check Cloud Console daily" cho đến khi production.

### 4. **Chi phí Đề xuất (Nếu chọn phương án B)**

| Phương án | Routing | Bản đồ | Hosting | Tổng/tháng | Ghi chú |
|---|---|---|---|---|---|
| **B2 (Stadia)** | Stadia Maps | Stadia Maps | Không | $20 | Rẻ nhất, all-in-one |
| **B1 (GraphHopper)** | GraphHopper Cloud | GraphHopper | Không | $75 | Expensive, but flexible |
| **B3 (Valhalla self)** | Valhalla self | Stadia Maps | VPS | $50–70 | Hybrid: routing free, map paid |
| **C (Google)** | Google Routes | Google Maps | Không | $275+ | Đắt, pháp lý an toàn nhất |

**Đề xuất:** **Stadia Maps (B2)** — $20/month, hỗ trợ tốt, documentation rõ ràng, ODbL transparent.

---

## X. Câu hỏi chưa trả lời được

1. **Google Maps Platform ToS có thay đổi trong năm 2026 không?** 
   - Báo cáo này dùng các ToS từ trước 2026-08-24. Google có thể cập nhật. 
   - **Hành động:** Trước khi deploy, kiểm tra lại [Google Maps Platform Terms](https://cloud.google.com/maps-platform/terms).

2. **"No Use with Non-Google Maps" có ngoại lệ nào được Google cấp không (exemption, special agreement)?**
   - Báo cáo không tìm thấy. 
   - **Hành động:** Nếu cần, liên hệ Google Maps sales để hỏi exemption (unlikely to grant).

3. **Mapbox + Google Maps ToS — có conflict rõ hay không?**
   - Báo cáo không tìm thấy clause cụ thể từ Mapbox. 
   - **Hành động:** Kiểm tra [Mapbox Product Terms (Oct 2025)](https://cdn.prod.website-files.com/609ed46055e27a02ffc0749b/68dddd2815cb3d82685f0096_Mapbox%20Product%20Terms%20(October%201,%202025).pdf) nếu định dùng Mapbox.

4. **Attribution "legible" có tức là font size bao nhiêu?**
   - OSM guidelines nói "legible" nhưng không định nghĩa font size cụ thể. 
   - **Hành động:** Dùng ít nhất 12sp trên mobile (industry standard), hoặc kiểm tra OSM community guidelines.

5. **Chi phí Stadia/GraphHopper có phát sinh thêm fee không (setup, API call, caching)?**
   - Báo cáo liệt kê per-request cost. Cần kiểm tra TOS xem có fee khác không.
   - **Hành động:** Làm proof-of-concept (PoC) 1–2 tuần để validate chi phí thực tế.

---

## XI. Tài liệu tham khảo (Đầy đủ)

### Google Maps Platform
- [Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms)
- [Google Maps Platform Service Specific Terms](https://cloud.google.com/maps-platform/terms/maps-service-terms)
- [Google API Security Best Practices](https://developers.google.com/maps/api-security-best-practices)

### OpenStreetMap
- [OpenStreetMap Licence/Attribution Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)
- [OSM Open Data License (ODbL)](https://opendatacommons.org/licenses/odbl/)

### Routing Providers
- [GraphHopper Pricing](https://www.graphhopper.com/pricing/)
- [GraphHopper Terms of Service](https://www.graphhopper.com/terms/)
- [Stadia Maps Pricing](https://stadiamaps.com/pricing/)
- [Stadia Maps Terms of Service](https://stadiamaps.com/terms-of-service/)
- [Mapbox Product Terms (October 1, 2025)](https://cdn.prod.website-files.com/609ed46055e27a02ffc0749b/68dddd2815cb3d82685f0096_Mapbox%20Product%20Terms%20(October%201,%202025).pdf)

### Valhalla & Self-Host
- [Valhalla GitHub - Licensing](https://github.com/valhalla/valhalla)
- [Valhalla Documentation](https://valhalla.github.io/valhalla/)

### API Key Security
- [CloudSEK - Hardcoded Google API Keys](https://www.cloudsek.com/blog/hardcoded-google-api-keys-in-top-android-apps-now-expose-gemini-ai)
- [Quokka - Google Gemini API Key Mobile Security Risk](https://www.quokka.io/blog/google-gemini-api-key-mobile-app-security-risk)
- [Medium - Securing Secrets in Android](https://medium.com/@vaibhav.shakya786/securing-secrets-in-android-from-api-keys-to-production-grade-defense-a2c8dc46948f)

---

**Báo cáo được lập:** 2026-08-24  
**Tác giả:** researcher-04  
**Trạng thái:** Sẵn sàng cho phê duyệt planner
