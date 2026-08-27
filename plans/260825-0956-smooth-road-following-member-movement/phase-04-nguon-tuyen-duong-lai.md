# Phase 04 — Nguồn tuyến đường lai 3 tầng (`:data`)

## Context Links

- [`plan.md`](plan.md) · [`decisions.md` §C2](decisions.md) (D5) — **đọc §C2 trước khi viết dòng đầu tiên**
- Yêu cầu: [PRD delta](docs/prd-delta-smooth-road-movement.md) §3.3 F7, US-41, US-45, Q8, Q13
- Nghiệm thu: **QA-SRM-04, 14, 15, 36**, UAT-01 · **QA-SRM-17 chỉ nửa "chuyển nguồn không gây cú
  nhảy vị trí"** — nửa "dialog tự tắt khi có mạng lại" là của [phase 07](phase-07-chan-man-ban-do-khi-mat-internet.md),
  và phase đó sở hữu ca QA-SRM-17. **QA-SRM-13 và UAT-04 đã chuyển hẳn sang phase 07** (D8: mất
  internet bị CHẶN, không hạ cấp im lặng) — phase 04 chỉ còn chịu trách nhiệm ca *vẫn có internet
  mà nhà cung cấp hỏng*
- Research: [researcher-01](research/researcher-01-road-following-simulation.md) §E (rủi ro pháp lý), §G (seam) — **§A `round_trip` không dùng**
- Pháp lý: [`docs/routing-and-map-attribution.md`](../../docs/routing-and-map-attribution.md) §3, §5 · `LLM.md` §13 Open #9, #10, #11, #13
- Kiến trúc: `LLM.md` §2 (chiều phụ thuộc), §12 (nơi đặt provider/DTO), §6 (Koin)

## Overview

| | |
|---|---|
| **Ưu tiên** | P1 |
| **Trạng thái** | Steps 1–8 + docs (Step 12) xong, xanh. Steps 9–11 (chạy thật + đếm request + fixture thật) CHƯA làm — xem `reports/dev-phase-04-report.md` |
| **Ước lượng** | 5h |
| **Phụ thuộc** | Phase 02 (seam `RoamStep.NeedPath` phải tồn tại) |

Thay **đúng một hàm** — `MemberMovementSimulator.pathFor()` — bằng một nguồn 3 tầng. Nếu phải sửa
thêm bất cứ gì trong `:domain` hay `:ui` ở phase này thì seam của phase 02 đã đặt sai chỗ.

## Key Insights

1. **Không dùng `round_trip`.** Mô hình chặng của `MemberRoamer` là điểm→điểm, khớp đúng chữ ký
   `RoutingProvider.directions(from, to)` đã có. Bỏ toàn bộ §A của researcher-01 xoá được một tham
   số API, một nhánh lỗi và một thứ phải test.
2. **Hạn ngạch chỉ được chặn nhờ bearing tất định của phase 02.** Với bearing ngẫu nhiên mỗi vòng,
   mỗi chặng là một cặp `(from, to)` mới ⇒ cache không bao giờ trúng ⇒ ~190 request/giờ với 3
   thành viên ⇒ cháy 500 credit/ngày sau ~2.6 giờ. Với bearing suy từ `(memberSeed, zoneId, kind)`,
   tổng số request cho cả phiên là **`thành viên × zone × 2`** — demo 3 thành viên, 2 zone = **12
   request**, và từ vòng thứ hai trở đi là **0**.
3. **Bản clone mới không có khoá vẫn chạy đủ chức năng.** Tầng 3 (`SyntheticPath`, phase 02) **tự
   sinh**, không cần khoá, không cần mạng, không cần file nào trong `assets/`. Đây là câu trả lời
   cho §13 Open #10 và là lý do câu hỏi pháp lý §13 Open #11 **không chặn** việc viết code này.
4. **Tầng 3 KHÔNG đạt US-41.** Đường cong tổng hợp không phải đường thật. Nói thẳng trong `LLM.md`
   §13 Open, đừng để người đọc sau tưởng là lỗi.
5. **Cache là caching, không phải redistribution.** Kết quả của nhà cung cấp được ghi vào
   `context.filesDir/routes/`, **không** vào `assets/`, **không** vào git. Đây là mẫu chuẩn của mọi
   app di động và là con đường duy nhất né được dòng ⬜ ở `routing-and-map-attribution.md` §5.
   Attribution được ghi **cùng file cache** — một tuyến không có credit thì không đọc lại được.
6. **`RouteGeometryGuard` (phase 02) phải chạy trên kết quả của nhà cung cấp, không chỉ trên đường
   tổng hợp.** Đường thật có thể vòng vèo vào/ra ranh giới zone nhiều lần ⇒ dội ENTER/EXIT ⇒ vỡ
   đúng bất biến mà `LLM.md` §11 gọi là "lời hứa duy nhất cả tính năng dựa vào".
7. **`ObserveNavigationUseCase` thôi là nơi duy nhất gọi `RoutingProvider`.** `LLM.md` §3 đang ghi
   câu đó. Sửa nó **trong cùng commit** — không phải "ghi chú lại sau".

## Requirements

**Chức năng**

- FR-1 Có khoá + có mạng → dãy điểm đến từ `RoutingProvider`, thành viên đi trên đường thật (US-41, QA-SRM-04).
- FR-2 Kết quả thành công được ghi cache trên máy kèm attribution; lần sau đọc cache, không gọi mạng.
- FR-3 Nhà cung cấp không dùng được **trong khi máy vẫn có internet** (thiếu khoá / 401 / 429 / 400
  / timeout) → **im lặng** rơi về cache rồi về `SyntheticPath`. Chuyển động **không đứt**,
  **không** dialog/toast/màn hình lỗi (US-45, QA-SRM-14/15/40).
  **Ca mất internet KHÔNG thuộc FR này** — D8 chặn nó bằng dialog trước khi tới đây; xem
  [phase 07](phase-07-chan-man-ban-do-khi-mat-internet.md). Trộn hai đường vào nhau là hỏng demo:
  dialog bật vì 401 sẽ không bao giờ tự tắt được, vì điều kiện tắt (có internet) đã đúng sẵn từ đầu.
- FR-4 Có mạng trở lại → chuyển nguồn **không gây cú nhảy vị trí** (QA-SRM-17).
- FR-5 Tuyến trượt `RouteGeometryGuard` bị **từ chối**, rơi về tầng thấp hơn.
- FR-6 Trạng thái nguồn hiện tại phát ra qua một cổng ở `:domain` để phase 05 hiện ghi công.
- FR-7 Ba thành viên đi ba tuyến khác nhau (PRD Q13).

**Phi chức năng**

- NFR-1 Mọi lần chờ mạng bị chặn thời gian (`withTimeoutOrNull`, 10 s) — MVI doc §3.
- NFR-2 Số request nhà cung cấp trong 10 phút chạy với 3 thành viên: **≤ 12** (QA-SRM-36).
- NFR-3 `MemberMovementSimulatorTest` giữ JVM thuần: `MemberRouteSource` được tiêm qua một
  interface, test dùng fake viết ngay trong file (LLM.md §11, không thư viện mock).
- NFR-4 Không đổi lược đồ Room. Cache là file, không phải bảng.

## Architecture

```
              :domain                                    :data
  ┌──────────────────────────┐          ┌──────────────────────────────────────────┐
  │ model/RouteSourceInfo.kt │◄─────────┤ routing/MemberRouteSource.kt             │
  │   kind: PROVIDER|CACHE|  │          │   implements MemberRouteProvider          │
  │         SYNTHETIC        │          │              + SimulatedRouteRepository   │
  │   attribution: List<Str> │          │                                           │
  │                          │          │   suspend fun path(req): List<GeoPoint>   │
  │ repository/              │          │     1. cache.get(key)         -> CACHE    │
  │   SimulatedRouteRepo.kt  │◄─────────┤     2. withTimeoutOrNull(10s) {           │
  │   observeSource():Flow<> │          │          provider.directions(from,to) }   │
  │                          │          │        + RouteGeometryGuard.isUsable      │
  │ tracking/SyntheticPath   │◄─────────┤        + cache.put(key, points, attrib)   │
  │ tracking/RouteGeometry…  │◄─────────┤                                -> PROVIDER│
  └──────────────────────────┘          │     3. SyntheticPath.between  -> SYNTHETIC│
                                        │                                           │
                                        │ routing/OnDevicePolylineCache.kt          │
                                        │   filesDir/routes/{key}.json              │
                                        │   { schemaVersion, engineId, attribution, │
                                        │     points:[[lat,lng],…], createdAtMs }   │
                                        └──────────────────────────────────────────┘
                                                        ▲
                                        MemberMovementSimulator.pathFor(step)  ← chỗ DUY NHẤT đổi
```

**Khoá cache** = `"{memberId}_{zoneId}_{kind}_{lat5}_{lng5}_{r}"` với `lat5`/`lng5` là toạ độ tâm
zone làm tròn 5 chữ số thập phân (~1.1 m) và `r` là bán kính làm tròn về mét. Sửa zone ⇒ khoá đổi ⇒
tự miss, không cần cơ chế vô hiệu hoá riêng (trả lời researcher-01 Q3). `schemaVersion` khác thì
xoá file và coi như miss (trả lời researcher-01 Q5, không cần migration).

**Chặng `WANDER` không bao giờ gọi nhà cung cấp** — chỉ xảy ra khi chưa có zone nào, và nó không
lặp lại nên cache vô nghĩa. Dùng thẳng `SyntheticPath`. Đây là một nửa của luật hạn ngạch.

**Vì sao `MemberRouteSource` mang HAI vai trò** (nguồn tuyến cho `:data`, và `SimulatedRouteRepository`
cho `:ui`): nó là nơi duy nhất biết tuyến hiện tại đến từ tầng nào. Tách thành hai lớp thì phải có
một kênh đồng bộ giữa chúng — một `MutableStateFlow` đi vòng, đúng loại phức tạp không mua được gì.
Koin đăng ký một `single` và `bind` hai lần (mẫu đã có ở `SimulatedLocationSource`, `DataModule.kt:73-74`).

## Related Code Files

**Tạo**

| Đường dẫn | Việc |
|---|---|
| `domain/src/main/kotlin/.../domain/model/RouteSourceInfo.kt` | `RouteSourceKind` enum + `attribution: List<String>` |
| `domain/src/main/kotlin/.../domain/repository/SimulatedRouteRepository.kt` | `fun observeSource(): Flow<RouteSourceInfo>` |
| `domain/src/main/kotlin/.../domain/repository/MemberRouteProvider.kt` | Cổng `:data`-nội-bộ mà `MemberMovementSimulator` thấy: `suspend fun path(request): List<GeoPoint>` — tồn tại để test JVM thuần có fake |
| `data/src/main/java/.../data/routing/MemberRouteSource.kt` | 3 tầng + phát `RouteSourceInfo` |
| `data/src/main/java/.../data/routing/OnDevicePolylineCache.kt` | Đọc/ghi `filesDir/routes/*.json`, `schemaVersion` |
| `data/src/main/java/.../data/routing/CachedRouteDto.kt` | `@Serializable` — hình dạng trên đĩa. **Không** đặt ở `:data/remote/dto/`: §12 dành thư mục đó cho DTO của một lời gọi HTTP; đây là định dạng lưu trữ do chính lớp cache sở hữu. Ghi lý do vào `LLM.md` §3 |
| `data/src/test/java/.../data/routing/MemberRouteSourceTest.kt` | Fake `RoutingProvider` + cache tạm, 3 tầng + 3 mã lỗi + timeout |
| `data/src/test/java/.../data/routing/OnDevicePolylineCacheTest.kt` | Vòng ghi→đọc, `schemaVersion` sai, file hỏng |

**Sửa**

| Đường dẫn | Việc |
|---|---|
| `data/src/main/java/.../data/location/MemberMovementSimulator.kt` | `+ memberRouteProvider` ở constructor; `pathFor(step)` gọi nó thay vì `SyntheticPath` trực tiếp |
| `data/src/main/java/.../data/di/DataModule.kt` | `single { MemberRouteSource(...) } bind MemberRouteProvider::class bind SimulatedRouteRepository::class`; `singleOf(::OnDevicePolylineCache)`; cập nhật `MemberMovementSimulator` |
| `data/src/test/java/.../data/location/MemberMovementSimulatorTest.kt` | Fake `MemberRouteProvider`; + ca nhà cung cấp lỗi → chuyển động không đứt |
| `app/src/test/java/.../KoinModulesTest.kt` | Binding mới |
| `LLM.md` | §3 (`:data/routing/` thêm 3 file, `:domain` thêm 3 file) · **sửa dòng "`ObserveNavigationUseCase` là nơi DUY NHẤT gọi RoutingProvider"** · §8.1 (sơ đồ: nguồn tuyến của chuyển động gia đình) · §13 Open (dòng mới: tầng 3 không đạt US-41; và hạn ngạch free tier khi bật tầng 1) |
| `docs/routing-and-map-attribution.md` | §3 bảng "Thực hiện ở": thêm `MemberRouteSource` là nơi thứ hai giữ attribution đi kèm dữ liệu |
| `docs/prd-delta-smooth-road-movement.md` | Trả lời Q8 (không cần đóng gói tuyến của nhà cung cấp) và Q13 (mỗi thành viên một tuyến, cache theo `memberId`) |

**Xoá:** không.

## Implementation Steps

1. `RouteSourceInfo` + `SimulatedRouteRepository` + `MemberRouteProvider` ở `:domain`. Ba file nhỏ,
   không logic.
2. `CachedRouteDto` + `OnDevicePolylineCache` + test. Dùng `Json` đã có trong Koin
   (`DataModule.kt:94`). Mọi thao tác file bọc `runCatching` — cache hỏng **không bao giờ** được
   làm chết chuyển động; hỏng thì xoá file và trả `null`.
3. `MemberRouteSource` + test. Thứ tự tầng đúng như sơ đồ. Với mỗi tầng, log đúng một dòng:
   - `sim_route_loaded source=PROVIDER|CACHE|SYNTHETIC pointCount=N` (QA §3);
   - `sim_route_failed reason=<HTTP code|TIMEOUT|GEOMETRY>` khi tầng 1 trượt.
   **Không log `lat`/`lng`** (PRD §7.3, gate G7).
4. Gọi `RouteGeometryGuard.isUsable(points, zone, kind)` **trước** khi nhận kết quả tầng 1 hoặc
   tầng 2. Trượt → `reason=GEOMETRY`, xuống tầng dưới.
5. `withTimeoutOrNull(10.seconds)` bọc `provider.directions(...)`. Hết giờ = một nhánh lỗi bình
   thường, không ném ra ngoài.
6. `MemberMovementSimulator`: `pathFor(step)` gọi `memberRouteProvider.path(...)`; chặng `WANDER`
   đi thẳng `SyntheticPath` không qua nguồn. Nhịp lấy tuyến **không ghi điểm** (luật phase 02).
7. Wiring Koin + `KoinModulesTest`.
8. `./gradlew :domain:test :data:test :app:test --no-configuration-cache`.
9. **Chạy thật, 3 kịch bản**, ghi log vào dev report. Tầng 2 (cache) **không** kiểm ở đây bằng chế
   độ máy bay: ca đó nay bị dialog của phase 07 chặn, và một fake `RoutingProvider` ném lỗi/timeout
   chứng minh đúng cùng một điều nhưng tất định và chạy được trong CI — xem S2.
   a. Có khoá + có mạng → `sim_route_loaded source=PROVIDER`, marker bám vệt đường (QA-SRM-04);
   b. Build với `local.properties` để trống khoá → `source=SYNTHETIC`, không màn hình lỗi (QA-SRM-14);
   c. Khôi phục khoá đúng **mà không đụng chế độ máy bay** → chặng kế tiếp quay lại `PROVIDER`,
      **không** có cú nhảy vị trí (nhờ luật `from` = vị trí đang hiển thị ở phase 03). Ca bật/tắt
      chế độ máy bay bằng tay thuộc **phase 07** (QA-SRM-13/17).
10. **Đếm request** (QA-SRM-36): chạy 10 phút với 3 thành viên, đếm dòng
    `sim_route_loaded source=PROVIDER`. Phải ≤ 12. Nhiều hơn nghĩa là bearing chưa tất định — quay
    lại phase 02 Step 6c.
11. Chạy lại `MemberRoamerTest` với tuyến thật (fixture lưu từ 9a) để chứng minh bất biến
    ENTER/EXIT sống sót trên hình học đường thật, không chỉ trên đường tổng hợp.
12. Cập nhật `LLM.md`, `routing-and-map-attribution.md`, PRD delta trong cùng commit.

## Todo List

- [x] `RouteSourceInfo`, `SimulatedRouteRepository`, `MemberRouteProvider` (`:domain`)
- [x] `CachedRouteDto` + `OnDevicePolylineCache` + test (gồm ca file hỏng và `schemaVersion` sai)
- [x] `MemberRouteSource` 3 tầng + timeout 10s + log + test
- [x] `RouteGeometryGuard` chạy trên kết quả tầng 1/2 (chạy ở `MemberRouteSource`, `:data` — không ở
      `MemberRoamer.withPath`; xem dev report cho lý do và `LLM.md` §13 Fixed #27)
- [x] `MemberMovementSimulator.pathFor()` + chặng `WANDER` không gọi nhà cung cấp
- [x] Koin `binds` hai interface + `KoinModulesTest`
- [ ] 3 kịch bản chạy thật (9a–9c), log dán vào dev report — ca chế độ máy bay thuộc phase 07 — **CHƯA LÀM, orchestrator/Step 9 sau**
- [ ] Đếm request 10 phút ≤ 12 (QA-SRM-36) — **CHƯA LÀM, Step 10 sau**
- [ ] `MemberRoamerTest` chạy lại với fixture tuyến thật — **CHƯA LÀM, Step 11 sau**
- [x] `LLM.md` §3 (gồm sửa câu "nơi DUY NHẤT gọi RoutingProvider") + §8.1 + §13 Open
- [x] `routing-and-map-attribution.md` §3 + PRD delta Q8/Q13

## Success Criteria

| # | Điều kiện | Cách kiểm | QA |
|---|---|---|---|
| S1 | Có khoá + mạng: marker nằm trên vệt đường của bản đồ suốt 2 phút | Quan sát thật ở zoom đủ gần | QA-SRM-04, UAT-01 |
| S2 | Lời gọi nhà cung cấp thất bại (lỗi / timeout) **sau khi đã fetch thành công một lần**: tầng 2 được dùng, `source=CACHE`, chuyển động **không đứt** | `MemberRouteSourceTest` — fake `RoutingProvider` ném lỗi / treo quá 10 s, cache đã có sẵn khoá đó. **Không** kiểm bằng chế độ máy bay bằng tay | US-45 |
| S3 | Khoá để trống: chuyển động **không đứt**, `source=SYNTHETIC`, **không** dialog/toast | Chạy thật | QA-SRM-14 |
| S4 | 401 / 429 / 400 giả lập: cả 3 lần chuyển động không đứt, lỗi **chỉ** trong log | `MemberRouteSourceTest` | QA-SRM-15 |
| S5 | Timeout 10 s: cùng hành vi như S4 | `MemberRouteSourceTest` | US-45 |
| S6 | Chuyển nguồn khi nhà cung cấp phục hồi (`SYNTHETIC`/`CACHE` → `PROVIDER`, máy **vẫn có internet** suốt): không cú nhảy nào vượt `MEMBER_RENDER_MAX_JUMP_M` | Quan sát thật, 9c | QA-SRM-17 (nửa "không cú nhảy"; nửa "dialog tự tắt" ở phase 07) |
| S7 | 10 phút / 3 thành viên: ≤ 12 request tới nhà cung cấp | Đếm log | QA-SRM-36 |
| S8 | Tuyến men mép zone từ nhà cung cấp bị từ chối (`reason=GEOMETRY`), rơi về tầng dưới | `MemberRouteSourceTest` | QA-SRM-28 |
| S9 | Bất biến ENTER/EXIT xanh với fixture tuyến **thật** | `MemberRoamerTest` | QA-SRM-25/26 |
| S10 | Không file nào trong `assets/` chứa dữ liệu của nhà cung cấp | `git status` + `ls app/src/main/assets` | `decisions.md` §C2 |

## Risk Assessment

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| **Cháy hạn ngạch 500 credit/ngày giữa buổi demo** | **Trung bình** | Bearing tất định (phase 02) + cache + `WANDER` không gọi mạng ⇒ 12 request/phiên. S7 là cổng đo, không phải hy vọng |
| Free tier là **non-commercial** (§13 Open #9) | Trung bình | Không đổi bởi phase này; giữ Open, nhắc lại trong `LLM.md` §13 |
| Tuyến thật từ nhà cung cấp phá bất biến ENTER/EXIT | **Cao** | `RouteGeometryGuard` chặn trước khi nhận (S8); S9 chứng minh trên fixture thật |
| Ghi file trên `Dispatchers.Default` (scope của Service) làm nghẽn | Thấp | Đọc/ghi cache bọc `withContext(Dispatchers.IO)`; file ~1–3 KB |
| Cache đầy dần theo số zone người dùng tạo | Thấp | Mỗi file ~1–3 KB; xoá thư mục `routes/` khi `schemaVersion` đổi. Không cần LRU (YAGNI) |
| Ai đó "tối ưu" bằng cách commit một tuyến vào `assets/` | **Cao (pháp lý)** | S10 là cổng; `decisions.md` §C2 ghi lý do; `LLM.md` §13 Open #11 vẫn ⬜ |
| Tầng 3 không đạt US-41 mà không ai biết | Trung bình | Ghi thẳng vào `LLM.md` §13 Open ở Step 12, và dải ghi công ở phase 05 hiện đúng trạng thái "ước tính" |

## Security Considerations

- **Khoá API vẫn ở `BuildConfig`** (§13 Open #6) — phase này **không** làm xấu thêm, và **không**
  ghi khoá vào file cache. Kiểm bằng mắt trên một file cache thật ở Step 9a.
- File cache nằm trong `context.filesDir` (private tới app), không phải `getExternalFilesDir()`.
  Nó chứa **hình học tuyến quanh zone của người dùng** — tức là dữ liệu suy ra được vị trí. Không
  bao giờ ghi ra vùng nhớ chia sẻ.
- Không log toạ độ ở bất kỳ nhánh nào của `MemberRouteSource` (gate G7).
- `Json { ignoreUnknownKeys = true }` đã có; đọc file cache luôn bọc `runCatching` — một file bị
  sửa tay không được làm crash app.

## Next Steps

- Phase 05 tiêu thụ `SimulatedRouteRepository.observeSource()` để hiện ghi công. Không phải sửa gì ở
  phase này thêm.
- Nếu sau này chuyển sang Valhalla self-host, chỉ đổi binding `RoutingProvider` — `MemberRouteSource`
  không biết engine nào đang chạy, đúng mục tiêu của cổng đó.
