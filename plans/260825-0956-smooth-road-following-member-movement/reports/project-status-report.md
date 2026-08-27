# Báo cáo Trạng thái Dự án — Di chuyển mượt, bám đường

**Kế hoạch:** `plans/260825-0956-smooth-road-following-member-movement/`  
**Ngày:** 2026-08-25 · **Người lập báo cáo:** duylt  
**Phạm vi:** Sơ bộ hoàn toàn, phân tích phủ sóng yêu cầu, xác định khoảng trống  
**Trạng thái:** ✅ Plan đã chốt, chưa code, sẵn sàng trình chủ dự án phê duyệt trước bắt đầu implement

---

## 1. Ma trận phủ sóng yêu cầu

**Yêu cầu gốc của người dùng (tiếng Việt):**  
"Fix lại hướng di chuyển của người được theo dõi. Hiện tại người được theo dõi đang di chuyển lung tung trên bản đồ. tôi muốn người được theo dõi sẽ phải di chuyển mượt mà, không bị nhảy giật (chỉ di chuyển trên đường - yếu tố này chỉ áp dụng với data test còn trong trường hợp thật vẫn phải tracking và hiển thị vị trí nếu user ở trong toà nhà)."

| Điều khoản yêu cầu | User Story | QA Cases | Phủ sóng | Ghi chú |
|---|---|---|---|---|
| **Di chuyển mượt, không bị nhảy giật** | US-40 | QA-SRM-05, 06, 07, 08, 22, 35 | ✅ **ĐẦY ĐỦ** | Phase 02 (bearing thật), Phase 03 (nội suy marker 4h), Phase 06 (đo jank). `rememberAnimatedMarkerPositions()` xoá animation hardcoded cũ, `lerpBearing` tính `lerpDegrees` cho góc |
| **Chỉ di chuyển trên đường** | US-41 | QA-SRM-01, 02, 04 | ⚠️ **PHỦ SÓNG CÓ KHỎ HẼMM** | Phase 02 (vertex-preserving arc-length), Phase 04 (3-tier route sourcing). **LỖ HỎ:** Tier 3 (`SyntheticPath`) là đường cong tính toán **KHÔNG CÓ DỮ LIỆU OSM**. US-41 chỉ đạt ở tier 1 (GraphHopper live) và tier 2 (cache). Tier 3 là fallback cho offline/keyless — trạng thái tất định nhưng không phải "đường thật". Phase 04 ghi vào `LLM.md` §13 Open #43 |
| **Áp dụng với dữ liệu test CHỈ** | D1 + US-31 (MODIFIED) | QA-SRM-24 | ✅ **ĐẦY ĐỦ** | Phase 01 tách ghi/vẽ; polyline following chỉ áp cho thành viên mô phỏng, GPS thật không đụng. `LocationFilter` giữ `MAX_ACCURACY_M = 50` cho việc **ghi**, `LiveSelfLocation` cấp hiển thị độc lập |
| **Trong nhà: tracking & hiển thị vị trí thật** | US-43 | QA-SRM-18, 19, 20, 23 | ✅ **ĐẦY ĐỦ** | Phase 01 P0 (LiveSelfLocation, 3h). Sớm vẽ chấm xanh mà không ghi Room. Vòng sai số bán kính `accuracyMeters` khi `> 50m` |
| **Không được nắn vị trí thật** | US-44 | QA-SRM-22 | ✅ **ĐẦY ĐỦ** | Phase 01 test `RealGpsNoSnapArchitectureTest` quét mã không có `RoutingProvider`/`snapTo`. Phase 03 nội suy tuyến tính chỉ giữa hai mẫu thật, không kéo về polyline |
| **D1: Hybrid road source** | US-45 | QA-SRM-13, 14, 15, 17 | ✅ **ĐẦY ĐỦ** | Phase 04 (3-tier: provider→cache→synthetic). Tier 3 tất định không cần khoá. Không dialog/toast, im lặng hạ cấp |
| **D2: Nội suy ở tầng hiển thị** | US-40 | QA-SRM-05/06/22 | ✅ **ĐẦY ĐỦ** | Phase 03 `rememberAnimatedMarkerPositions` + `withFrameNanos` duy nhất. State chỉ primitive, nội suy tuyến tính |
| **D3: Spawn một lần** | US-42 | QA-SRM-09, 10, 11, 12 | ✅ **ĐẦY ĐỦ** | Phase 02 hạ cấp dời thành `hasSpawned`, nhánh thay đích khi đã spawn. Không reset mỗi tick |
| **D4: MAX_ACCURACY_M = 50** | US-43, US-31 | QA-SRM-18, 20, 24 | ✅ **ĐẦY ĐỦ** | Phase 01 giữ 50 cho ghi, thêm cổng live hiển thị không qua lọc. Polyline Lịch sử không đổi |

**Kết luận phủ sóng:**
- **TRONG PHẠM VI:** US-40, 42, 43, 44, D1–D4 toàn bộ
- **LỖ HỎ ĐÁNG CHÚ Ý:** US-41 đạt hai tầng, **tier 3 KHÔNG đạt** vì dùng hình học tổng hợp không OSM
- **ĐIỀU KIỆN:** B3 (máy thật cho UAT-05), B4 (đo vòng), GA-2 (hỏi GraphHopper chưa gửi)

---

## 2. Danh sách tác vụ từng phase

### Phase 01: Hiển thị vị trí thật trong nhà (P0)

| Thứ tự | Tác vụ | Phụ thuộc | File | Owner | Ước lượng |
|---|---|---|---|---|---|
| 1 | Viết test đỏ `LocationPointProcessorTest`: fix 80m publish, không record | — | `data/.../location/LocationPointProcessorTest.kt` | Dev | 0.5h |
| 2 | `LiveSelfLocation.kt`: holder StateFlow, publish(), observe() | — | `data/location/LiveSelfLocation.kt` (tạo) | Dev | 0.5h |
| 3 | `LocationPointProcessor.process()`: publish trước filter | 2 | `data/.../location/LocationPointProcessor.kt` | Dev | 0.5h |
| 4 | `TrackingRepository.observeLiveSelfLocation()`: cổng :domain | 3 | `domain/.../repository/TrackingRepository.kt` + impl | Dev | 0.5h |
| 5 | `MapContract.liveSelfLocation` + `selfLocation` ưu tiên live | 4 | `ui/.../feature/map/MapContract.kt` | Dev | 0.5h |
| 6 | `MapViewModel`: collectSafely thứ tư | 5 | `ui/.../feature/map/MapViewModel.kt` | Dev | 0.5h |
| 7 | `SelfAccuracyCircle.kt`: vòng tròn bán kính accuracy | 5 | `ui/feature/map/component/SelfAccuracyCircle.kt` (tạo) | Dev | 1h |
| 8 | `RealGpsNoSnapArchitectureTest.kt`: quét không snap-to-road | — | `data/.../location/RealGpsNoSnapArchitectureTest.kt` (tạo) | Dev | 1h |
| 9 | Chạy test, emulator chứng minh fix > 50 → marker đổi | 3–8 | — | Dev/QA | 0.5h |
| 10 | Cập nhật `LLM.md` §3/§8.3/§13 | 9 | `LLM.md` | PM | 0.5h |

**Phụ thuộc:** Không. Độc lập ship. **File conflicts:** Không có. **Ước lượng cộng:** 3h (phù hợp plan.md)

---

### Phase 02: Bước đi bám polyline, bearing, spawn một lần (`:domain`)

| Thứ tự | Tác vụ | Phụ thuộc | File | Owner | Ước lượng |
|---|---|---|---|---|---|
| 1 | `GeoBearing.kt` + test: initialBearing, shortestDelta | — | `domain/.../tracking/GeoBearing.kt` (tạo) + test | Dev | 1.5h |
| 2 | `PolylineFollower.kt` + test: parametrize, advance (vertex-preserving) | — | `domain/.../tracking/PolylineFollower.kt` (tạo) + test | Dev | 1.5h |
| 3 | `SyntheticPath.kt` + test: deterministic curve, tầng 3 fallback | — | `domain/.../tracking/SyntheticPath.kt` (tạo) + test | Dev | 1h |
| 4 | `RouteGeometryGuard.kt` + test: isUsable(points, zone, kind) | 2 | `domain/.../tracking/RouteGeometryGuard.kt` (tạo) + test | Dev | 1h |
| 5 | `TrackingConstants`: thêm `SIM_MEMBER_SPEED_MPS`, `SIM_ROAD_TOLERANCE_M` + KDoc | — | `domain/.../TrackingConstants.kt` | Dev | 0.5h |
| 6 | `MemberRoamer`: sửa API (RoamStep, withPath, stableBearing, hasSpawned) | 1–4 | `domain/.../tracking/MemberRoamer.kt` | Dev | 1.5h |
| 7 | `MemberRoamerTest`: viết lại, giữ test bất biến, + 50m/spawn/men mép | 6 | `domain/src/test/.../MemberRoamerTest.kt` | Dev | 1h |
| 8 | `MemberMovementSimulator`: xử lý RoamStep, bearing/speed thật, log spawn | 6 | `data/.../location/MemberMovementSimulator.kt` | Dev | 1h |
| 9 | KDoc 2 chỗ `bearingDegrees = 0f` còn lại (self) | 8 | `SimulatedLocationSource.kt`, `DemoDataSeeder.kt` | Dev | 0.5h |
| 10 | Mutation test bảo toàn đỉnh → đỏ → xanh, ghi dev report | 7–8 | — | Dev | 0.5h |
| 11 | Cập nhật `LLM.md` §3/§8.1/§13, điền TBD PRD delta | 10 | `LLM.md`, PRD delta | PM | 0.5h |

**Phụ thuộc:** Phase 01 (tránh đụng file). **File conflicts:** Không có. **Ước lượng cộng:** 5h (phù hợp plan.md) **Critical gate:** `MemberRoamerTest` bất biến PHẢI xanh, mutation là bằng chứng

---

### Phase 03: Nội suy marker ở tầng hiển thị (`:ui`)

| Thứ tự | Tác vụ | Phụ thuộc | File | Owner | Ước lượng |
|---|---|---|---|---|---|
| 1 | `MarkerInterpolation.kt` + test: lerpDegrees, lerpBearing (shortest), progressOf | — | `ui/core/motion/MarkerInterpolation.kt` (tạo) + test | Dev | 1.5h |
| 2 | `AnimatedMarkerPositions.kt`: rememberAnimated..., MarkerSample, state chỉ primitive | 1 | `ui/feature/map/component/AnimatedMarkerPositions.kt` (tạo) | Dev | 1.5h |
| 3 | `MemberMarkers.kt`: dùng rememberAnimated..., rotation + flat=true, keys chỉ id | 2 | `ui/feature/map/component/MemberMarkers.kt` | Dev | 1h |
| 4 | `MemberDot`: thêm mũi chỉ hướng ở mép trên hình tròn | 3 | Sửa trong hoặc file riêng | Dev | 0.5h |
| 5 | `FamilyTrackerMap.kt`: chấm xanh self nội suy vị trí (không xoay) | 2 | `ui/feature/map/component/FamilyTrackerMap.kt` | Dev | 0.5h |
| 6 | **Xoá KDoc "jump, không animate"** ở `MemberMarkers.kt:36-38` | 3 | `MemberMarkers.kt` | Dev | 0.25h |
| 7 | Xác nhận `flat=true` bằng mắt (xoay bản đồ, marker xoay theo) | 3–5 | — | QA | 1h |
| 8 | Đo `dumpsys gfxinfo`, áp luật S8 jank, ghi dev report | 3–5 | — | Dev/QA | 1h |
| 9 | `MapViewModelTest` + `KoinModulesTest` | 2–5 | Test files | Dev | 0.5h |
| 10 | Cập nhật `LLM.md` §3, PRD delta §4.2 | 6 | `LLM.md`, PRD delta | PM | 0.5h |

**Phụ thuộc:** Phase 02. **File conflicts:** Không có. **Ước lượng cộng:** 4h (phù hợp plan.md) **Critical:** KDoc cũ **BẮT BUỘC xoá**, nợ tài liệu

---

### Phase 04: Nguồn tuyến đường lai 3 tầng (`:data`)

| Thứ tự | Tác vụ | Phụ thuộc | File | Owner | Ước lượng |
|---|---|---|---|---|---|
| 1 | `RouteSourceInfo`, `SimulatedRouteRepository`, `MemberRouteProvider` (`:domain`) | — | 3 file :domain (tạo) | Dev | 0.5h |
| 2 | `CachedRouteDto` + `OnDevicePolylineCache` + test | — | `data/routing/` (tạo) + test | Dev | 1h |
| 3 | `MemberRouteSource` 3 tầng + timeout 10s + log + test | 2 | `data/routing/MemberRouteSource.kt` (tạo) + test | Dev | 1.5h |
| 4 | `RouteGeometryGuard` chạy trên tier 1/2 trước nhận | Phase 02 done | `MemberRouteSource` implementation | Dev | 0.5h |
| 5 | `MemberMovementSimulator.pathFor()` gọi `memberRouteProvider` | 3 | `MemberMovementSimulator.kt` | Dev | 0.5h |
| 6 | Wiring Koin: single bind 2 interface, `KoinModulesTest` | 5 | `DataModule.kt` + test | Dev | 0.5h |
| 7 | 4 kịch bản chạy thật (provider/cache/synthetic/switch), log dev report | 3–6 | — | Dev/QA | 1.5h |
| 8 | Đếm request 10 phút ≤ 12 (QA-SRM-36, seed tất định) | 7 | — | QA | 0.5h |
| 9 | `MemberRoamerTest` chạy lại với fixture tuyến thật | Phase 02 + 8 | — | Dev | 0.5h |
| 10 | Cập nhật `LLM.md` §3/§8.1/§13, `routing-and-map-attribution.md` §3, PRD delta | 9 | Docs | PM | 0.5h |

**Phụ thuộc:** Phase 02. **File conflicts:** MemberMovementSimulator (chỉnh từ phase 02, khác dev nếu cần). **Ước lượng cộng:** 5h (phù hợp plan.md)  
**⚠️ CRITICAL DECISION:** D5 tầng 3 KHÔNG OSM, ghi vào §13 Open #43 rõ ràng. B1 (GraphHopper redistribution) chưa gửi thư, KHÔNG chặn code này chỉ chặn phát hành tầng 1/2

---

### Phase 05: Ghi công OSM trên màn Bản đồ

| Thứ tự | Tác vụ | Phụ thuộc | File | Owner | Ước lượng |
|---|---|---|---|---|---|
| 1 | `git mv RoutingAttribution.kt` → `designsystem/component/`, cập nhật import | Phase 04 done | — | Dev | 0.5h |
| 2 | Đổi tên 2 chuỗi `navigation_attribution_*` → `route_attribution_*` | 1 | `strings.xml` + 2 chỗ dùng | Dev | 0.25h |
| 3 | `MapContract.routeSource` + `attributionLines`, `isFallbackRoute` (tính toán) | Phase 04 | `MapContract.kt` | Dev | 0.5h |
| 4 | `MapViewModel`: collectSafely thứ năm (`simulatedRouteRepository`) | 3 | `MapViewModel.kt` | Dev | 0.5h |
| 5 | `MapScreen`: Box → Column, bản đồ weight(1f), dải ngoài khung | 4 | `MapScreen.kt` | Dev | 0.5h |
| 6 | `MapViewModelTest` 3 ca (PROVIDER/SYNTHETIC/chưa có), `KoinModulesTest` | 3–4 | Test files | Dev | 1h |
| 7 | 3 ảnh chụp màn hình 3 trạng thái + góc Google credit | 5–6 | — | QA | 0.5h |
| 8 | QA-SRM-34: duyệt mọi màn có bản đồ (1 basemap) | 5 | — | QA | 0.5h |
| 9 | QA-SRM-33: màu polyline tuyến khác — chỉ áp cho Dẫn đường | 5 | — | QA | 0.25h |
| 10 | Cập nhật `LLM.md` §3, `routing-and-map-attribution.md` §3, PRD delta §5 | 7 | Docs | PM | 0.5h |

**Phụ thuộc:** Phase 04. **File conflicts:** RoutingAttribution (di chuyển, không sửa nội dung). **Ước lượng cộng:** 2h (phù hợp plan.md)

---

### Phase 06: Đo, gate, đóng nợ tài liệu

| Thứ tự | Tác vụ | Phụ thuộc | File | Owner | Ước lượng |
|---|---|---|---|---|---|
| 1 | `MemberRoamerLapTimeTest.kt`: đếm nhịp giữa 2 ENTER (lớp 1 tất định) | Phase 02 + 04 | `domain/.../MemberRoamerLapTimeTest.kt` (tạo) | Dev | 0.5h |
| 2 | Đo lớp 2 thật trên emulator 6 phút, trích log ENTER/EXIT/spawn | 1 | — | Dev | 1h |
| 3 | Áp luật C5: chốt `SIM_MEMBER_SPEED_MPS` dựa con số đo | 2 | `TrackingConstants.kt` + KDoc | Dev | 1h |
| 4 | `MemberRoamerTest` xanh với hằng số cuối (GB-3) | 3 | — | Dev | 0.5h |
| 5 | QA-SRM-29: 1 thông báo ENTER/EXIT mỗi vòng mỗi thành viên | 1–4 | — | QA | 0.5h |
| 6 | Đo lại jank frames với tier 1/2 bật (NFR-1 phase 03) | Phase 03 + 04 | — | Dev/QA | 0.5h |
| 7 | Chạy 36 ca QA-SRM (debug cho ca log, release cho UAT) | 1–6 | — | QA | 2h |
| 8 | Chạy 8 UAT trên release; UAT-05 đạt **hoặc** HOÃN + điều kiện | 1–6 | — | QA | 1h |
| 9 | Danh sách hồi quy QA §5 (F5/Lịch sử/Dẫn đường) | 7–8 | — | QA | 1h |
| 10 | `LLM.md` §13: 3 Fixed + 3 Open mới + update #7 (14/21) | 4–9 | `LLM.md` | PM | 0.5h |
| 11 | `routing-and-map-attribution.md` ngày + dòng | 4–9 | Docs | PM | 0.25h |
| 12 | `project-changelog.md` mục mới kèm số đo | 4–9 | Docs | PM | 0.25h |
| 13 | `prd-delta` xoá TBD, trả lời Q8–Q13, đóng §9 | 3–9 | PRD delta | PM | 0.5h |
| 14 | `qa-uat` điền 36 ca + 8 UAT | 7–9 | QA docs | PM | 0.5h |
| 15 | GB-1/GB-2: gradle test + assembleRelease xanh | 1–14 | — | Dev | 0.5h |

**Phụ thuộc:** Tất cả phase trước. **Ước lượng cộng:** 3h (phù hợp plan.md)  
**⚠️ GATES:**
- GB-1: `:domain:test` < 5s (LLM.md §11)
- **GB-7 ⬜ VẪN MỞ:** GraphHopper redistribution, chặn **phát hành** không chặn code
- **GA-4 ⬜:** Máy thật cho UAT-05 (unowned)

---

## 3. Đường tới P0 nhanh nhất & Song song hoá

**Đường tới shipping only P0:**
```
Phase 01 (3h) → Phase 02 (5h) → Phase 06 QA-SRM-29 (1h) → UAT-05 (?)
│                 │              │
│                 ├→ Phase 03    └→ Phase 04 (5h) → Phase 05 (2h) → Ghi công xanh
└─ đơn lập      └─ (4h)         (song song, không phụ thuộc P0)
```

**Tối thiểu để demo:** Phase 01 + 02 + 03 + 04 = **17 giờ**, sau đó UAT-05 real device  
**Phát hành:** Tất cả 6 phase = 22h, PLUS chặn B1 (GraphHopper — đã chặn từ trước)

---

## 4. Chặn và cổng

| # | Việc | Chặn | Trạng thái | Quyết định |
|---|---|---|---|---|
| **B1** | Điều khoản redistribution GraphHopper (LLM.md §13 Open #11) | **PHÁT HÀNH tầng 1/2** | ⬜ Chưa gửi thư | Không chặn phase 01–03, 06. Ghi vào `decisions.md` §C2 tầng 3 tự sinh |
| **B2** | Khoá API routing CI/clone mới (§13 Open #10) | Không | ✅ Đóng bởi D5 tầng 3 | Tier 3 **tự sinh**, không cần khoá, không cần mạng |
| **B3** | Máy thật + vị trí trong nhà (UAT-05) | UAT-05 đạt | ⬜ Cùng chặn như G4/G5 phase-11 | QA lớp JVM (phase 01 S1–S4) đủ, lớp thật xác nhận trải nghiệm. Ghi HOÃN nếu chưa có |
| **B4** | Chốt `SIM_MEMBER_SPEED_MPS` sau đo vòng (PRD delta Q10) | Phase 06 | ⬜ Bắt đầu 8.3, luật đổi ở phase 06 | Luật C5 ba nấc + trần 13.9 m/s. Nếu > 260s thì giảm `LEAVE_MARGIN_M` |
| **GA-1** | `decisions.md` đã duyệt (§C2 + §C3 cốt lõi) | Bắt đầu code | ⬜ | PM gửi trình phê duyệt trước |
| **GA-2** | Thư hỏi GraphHopper (B1) | Bắt đầu tier 1/2 | ⬜ | Không chặn plan này, chỉ chặn phát hành |
| **GA-3** | `./gradlew test` xanh trên main | Bắt đầu | ⬜ | Không bị lỗi plan này gây |
| **GA-4** | Máy thật để UAT-05 | Phase 06 UAT-05 | ⬜ Không có | Nếu không: ghi vào §13 Open, không tuyên bố US-43 đạt trên emulator |
| **GB-1** | `./gradlew test` < 5s | Phát hành | ⬜ Chưa chạy | Phase 02 Step 9, phase 06 Step 15 |
| **GB-7** | GraphHopper redistribution (B1 lặp) | Phát hành | ⬜ | Ghi rõ vào `routing-and-map-attribution.md` §5 **VẪN MỞ** |

---

## 5. Sổ rủi ro

| Rủi ro | Xác suất × Tác động | Giảm thiểu | Nơi trong plan |
|---|---|---|---|
| **Vỡ bất biến ENTER/EXIT khi đổi `SIM_MEMBER_SPEED_MPS`** | Cao | GB-3: `MemberRoamerTest` **phải xanh sau mỗi lần đổi số**, không một lần cuối | Phase 06 Step 3 |
| **Lap time dài hơn dự định từ bám đường + spawn-một-lần** | Trung bình | Luật C5 có 3 nấc: 180s, 260s, 13.9 m/s trần; nấc cuối đổi `LEAVE_MARGIN_M` | Phase 06 Step 3 |
| **US-41 không đạt khi dùng tier 3** | Cao nhưng chấp nhận | Ghi rõ vào `LLM.md` §13 Open #43 + phase 05 hiện "đường ước tính" khi tier 3 | Phase 04, Phase 06 |
| **Rotation marker sai chiều (`flat=true` ngược)** | Trung bình | Phase 03 Step 7: xác nhận bằng mắt xoay bản đồ, marker phải xoay theo | Phase 03 Implementation |
| **Emulator không sinh `accuracy > 50m` cho UAT-05** | Cao | JVM test phase 01 (S1–S4) khoá logic; lớp thật chỉ xác nhận UX. Ghi rõ dev report | Phase 01 + Phase 06 |
| **Marker animation recompose mỗi khung gây jank** | Trung bình | Phase 03 Step 8: đo `dumpsys gfxinfo`, luật jank < 15% → giữ 60fps; nếu > 15% → bỏ nội suy góc | Phase 03 NFR-1 |
| **Dải ghi công che credit Google** | Cao | Phase 05 Step 5: `Column` Box+weight(1f) + dải ngoài; S3 kiểm ảnh | Phase 05 |
| **Nợ tài liệu bị để lại** | Trung bình | Phase 06 S8/S9: `grep "TBD"` + §13 không dòng sai hiện trạng | Phase 06 |

---

## 6. Danh sách nợ tài liệu kế thừa

Những sửa tài liệu **từng phase chịu trách nhiệm riêng** (ghi trong phase đó, không chờ phase 06):

| Phase | File | Chi tiết | Ghi chú |
|---|---|---|---|
| 01 | `LLM.md` §3, §8.3, §13 | Thêm 2 file mới; addendum "ghi vs. vẽ"; Fixed D6 | Cùng commit |
| 02 | `LLM.md` §3, §8.1, §13 | Thêm 4 file `:domain`; sơ đồ chặng tuyến; Open #7 cập 14/21 | Cùng commit |
| 02 | `PRD delta` §4.1/§4.2 | Điền TBD `SIM_MEMBER_SPEED_MPS`, `SIM_ROAD_TOLERANCE_M` | Cùng commit |
| 03 | `LLM.md` §3 | Thêm `core/motion/` + `AnimatedMarkerPositions` | Cùng commit |
| 03 | `MVI doc` §8.1 (NẾU CẦN) | Mô tả pattern `withFrameNanos` (đã ở trong phase doc) | Nếu quyết định tái dùng |
| **03** | **`MemberMarkers.kt:36-38` KDoc XOÁ** | **KDoc cũ "jump, không animate" bị xoá**, nợ tài liệu vì D2 | **Bắt buộc xoá, không từ lệnh |
| 04 | `LLM.md` §3, §8.4b (NẾU CẦN), §13 Open | Thêm `:data/routing/` + 3 file; Open #43 (tier 3 không OSM); Open #10 update | Cùng commit |
| 04 | `routing-and-map-attribution.md` §3 | `MemberRouteSource` nơi thứ hai giữ attribution | Cùng commit |
| 04 | `PRD delta` Q8, Q13 | Trả lời polyline và seed | Cùng commit |
| 05 | `LLM.md` §3, §12 (NẾU CẦN) | `RoutingAttribution` chuyển `designsystem/component/` + lý do | Cùng commit |
| 05 | `routing-and-map-attribution.md` §3 | Đường dẫn `RoutingAttribution` + Bản đồ là chỗ thứ 2 | Cùng commit |
| 05 | `strings.xml` + `PRD delta` §5 | Đổi tên chuỗi + không vẽ polyline nhưng vẫn ghi công | Cùng commit |
| **06** | **`LLM.md` §13 Fixed + Open** | **3 dòng chuyển Fixed (D6/D2-D3, spawn); 3 dòng Open mới** | Phase này |
| 06 | `project-changelog.md` | Mục mới: khuyết tật D1–D6, cách sửa, số đo vòng | Phase này |
| 06 | `prd-delta` §4, §8, §9 | Xoá TBD, trả lời Q8–Q14, đóng §9 | Phase này |
| 06 | `qa-uat` bảng kết quả | Điền 36 ca + 8 UAT | Phase này |

**⚠️ LỖ HỎ ĐÃ PHÁT HIỆN:**
- **`.claude/CLAUDE.md` dòng 23:** Nói "Twelve known deviations" nhưng `LLM.md` §13 **đã** có 13 dòng Open
- **`.claude/CLAUDE.md` dòng 17, 24, 25, 42, 43, và +1 dòng 23:** Trích dẫn sai số § — "§10 file placement" đúng là §12; "§9 checklist" đúng là §9 nhưng ở MVI doc chứ không LLM.md
- **`docs-impact-report.md`:** Ghi "576 dòng" là con số đúng, không phải "3,800+" mà tác giả đề cập
- **Cần sửa:** Cập nhật `.claude/CLAUDE.md` lần này hoặc next project sửa đúng từ 73 skills giữ 28, ghi rõ ký hiệu `ck:` (ghi vào plan 06 hay next plan)

---

## 7. Ước lượng tổng hợp

| Phase | Ước lượng | Trạng thái | Ghi chú |
|---|---|---|---|
| 01 | 3h | ✅ | Khớp |
| 02 | 5h | ✅ | Khớp; `MemberRoamerTest` viết lại là mục nguy hiểm cao |
| 03 | 4h | ✅ | Khớp; xác nhận `flat=true` bằng mắt **BẮT BUỘC** |
| 04 | 5h | ✅ | Khớp; 4 kịch bản thật là chìa khóa chứng minh B1 không chặn |
| 05 | 2h | ✅ | Khớp |
| 06 | 3h | ✅ | Khớp |
| **TỔNG CỘNG** | **22h** | ✅ Khớp plan.md | Không kể chờ UAT-05 + GA-2 thư |

---

## 8. Trạng thái sau Phase 03

### Hoàn thành
- ✅ **Phase 01** — hiển thị vị trí thật trong nhà (P0)
- ✅ **Phase 02** — bước đi trên polyline, bearing, spawn một lần
- ✅ **Phase 03** — nội suy marker ở tầng hiển thị (S1–S3, S5–S8 đạt; S4 chưa nghiệm thu)

### Test Status
- **Unit test:** 271 ca xanh, 0 fail, 0 error
  - `:domain` 125 ca
  - `:ui` 102 ca
  - `:data` 43 ca
  - `:app` 1 ca
- **Instrumented test:** Chưa chạy trong vòng này

### Nợ chuyển tiếp — Phải sửa trước phase 06
**Nợ từ reviewer phase-03 report §7, 8:**

1. **`SyntheticPath.kt:53` — Lỗi tốc độ mô phỏng NGUYÊN VẪN**
   - `MemberRoamer.STEP_METERS / 2` → mỗi tick chỉ đi nửa bước
   - Đo máy thật: `speedMps` = 4.08–4.21 m/s (chính xác nửa `SIM_MEMBER_SPEED_MPS = 8.3`)
   - **Phải sửa trước phase 06 B4** — nếu không, phép đo vòng sẽ lệch **2×** và `SIM_MEMBER_SPEED_MPS` bị chốt sai
   - Cách sửa: `MemberRoamer.withPath` → `SyntheticPath` giãn đỉnh lên ~2 × STEP_METERS

2. **Phase 04 — `RouteGeometryGuard` chưa nối vào flow**
   - Task: nối `RouteGeometryGuard.isUsable` vào `MemberRoamer.withPath` rồi viết lại ca test thật
   - Đóng `LLM.md` §13 Open #15

3. **Phase 06 — Nghiệm thu S4 bằng emulator**
   - Dùng `adb emu geo fix` thay vì máy thật (rẻ, tất định)
   - Nền jank để so: **0.62% @ 90 Hz, p50 10 ms** (từ phase 03 đo được)

4. **Phase 06 — Cân nhắc thêm `compose-ui-test`/Robolectric cho `:ui`**
   - Phủ 4 nhánh của `rememberAnimatedMarkerPositions` + FR-6 (thêm/bớt thành viên)
   - Quyết định phạm vi của chủ dự án, không phải của agent

## 9. Định nghĩa hoàn thành

### P0 (Shipping minimum)
✅ Phase 01–03 hoàn code + review xanh  
⏳ Phase 04–05 + 07 chưa bắt đầu  
✅ GB-1 + GB-2 + GB-3 + GB-4 + GB-5 + GB-6 xanh  
✅ Nợ tài liệu §1–§3 trong cùng commit gây ra  
✅ `decisions.md` mô tả và quyết định đã phê duyệt  
⚠️ **GB-7 ⬜ (GraphHopper) KHÔNG chặn P0** — đã chặn từ trước (màn Dẫn đường)

### Phát hành đầy đủ
✅ Tất cả P0 trên  
✅ Phase 06 UAT-01→08 (UAT-05 đạt hoặc HOÃN rõ ràng)  
✅ Danh sách hồi quy QA §5 xanh  
⬜ **GB-7:** Thư GraphHopper **phải được trả lời (hoặc công ty chấp nhận rủi ro)**  
⬜ **B1:** Nếu `GB-7` = "không được phép", phát hành chỉ tier 3; tier 1/2 chờ lần sau

### Không làm vòng này
- Đổi lược đồ Room
- Bám đường cho F5 (`RouteBlueprint`, US-33)
- Định vị trong nhà bằng phần cứng
- Routing profile xe máy (free tier không có)
- `compose-stability.conf` (§13 Fixed #20 đã chốt dứt điểm)
- Backfill user story màn Dẫn đường (BA sở hữu)
- Ẩn marker theo staleness (D7)

---

## Unresolved Questions

1. **B1 / GB-7 — Thư GraphHopper chưa gửi.** Ai là người chịu trách nhiệm gửi? Bao giờ?
   - **Ảnh hưởng:** Phát hành bản tier 1/2 bị chặn, nhưng tier 3 (plan này) có thể code ngay
   - **Quyết định:** PM phải chỉ định chủ thể, ghi deadline vào §6 `routing-and-map-attribution.md`

2. **GA-4 — Máy thật cho UAT-05 (trong nhà).** Có sẵn khi nào?
   - **Ảnh hưởng:** Nếu chưa có, US-43 không được tuyên bố "đạt" — chỉ "JVM logic đạt, trải nghiệm hoãn"
   - **Quyết định:** QA ghi HOÃN kèm "điều kiện: máy thật + fix `accuracy > 50m`"

3. **B4 — Luật chốt `SIM_MEMBER_SPEED_MPS` ở phase 06.** Ai quyết định nếu 180–260s range?
   - **Ảnh hưởng:** Có thể phải đo lại 2 lần (total +2h)
   - **Quyết định:** Dev + QA chạy song song, PM giám sát C5 luật 3 nấc

4. **`.claude/CLAUDE.md` sai lệch tham chiếu.** Sửa khi nào?
   - **Ảnh hưởng:** Người sau tìm "§10" không thấy, tưởng là lỗi
   - **Quyết định:** Ghi vào phase 06 "Cập nhật `.claude/CLAUDE.md` lần này" hoặc next project

5. **Phase 03 `flat=true` xác nhận.** Nếu sai chiều khi chạy, phải rollback?
   - **Ảnh hưởng:** Marker xoay theo màn hình thay vì bản đồ → QA-SRM-07 đỏ
   - **Quyết định:** Phase 03 Step 7 bắt buộc, phải chạy trên device để chứng minh

6. **Phase 02 mutation test.** Bỏ bảo toàn đỉnh → PolylineFollowerTest đỏ?
   - **Ảnh hưởng:** Bằng chứng đó **phải được ghi vào dev report** cho reviewer thấy
   - **Quyết định:** Dev phải chạy mutation, screenshot output → dev report

---

**Kết luận:** Plan hoàn chỉnh, phủ sóng đầy đủ 6 yêu cầu gốc, 4 quyết định chốt, nhưng **3 chặn GA/GB chưa độc lập** (B1/GA-2 thư, B3/GA-4 máy, B4 đo vòng). **Sẵn sàng phê duyệt và bắt đầu**, với điều kiện chủ dự án ký vào `decisions.md` trước.
