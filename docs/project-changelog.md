# Project Changelog

**Project:** FamilyTrackerDemo  
**Last Updated:** 2026-08-24

## v1.0

> **Về cách đánh số phase.** Repo có HAI hệ đánh số độc lập và chúng va nhau nếu trộn:
> `phase-01`…`phase-11` là các đợt dựng app (phase-05 của repo là **màn Map**), còn
> `plans/260824-1335-pluggable-routing-provider/` có phase 01–06 **riêng của nó**. Ở đây,
> tính năng dẫn đường luôn được gọi là **plan routing**, không bao giờ là "Phase 05".

### Features

#### Phase 01-04 (repo): Zone Tracking & Notification — hoàn thành
- Zone creation and management (US-12 to US-21)
- Real-time zone entry/exit detection (US-22 to US-24)
- Permission flow for location access (US-01 to US-05)
- Foreground service for background tracking

#### Plan routing (pluggable routing provider) — hoàn thành
- **Data source:** OpenStreetMap (OSM) via GraphHopper Cloud or Valhalla
- **Routing providers:** GraphHopper (free: car/bike/foot), Valhalla (free: motorcycle)
- **Implementation:** Pluggable architecture with `RoutingProvider` interface
- **Components:**
  - `GraphHopperRoutingProvider` – HTTP client for GraphHopper Cloud API
  - `ValhallaRoutingProvider` – HTTP client for Valhalla API (Stadia/FOSSGIS/self-hosted)
  - Navigation screen with live polyline + ETA
  - Attribution display (OSM + provider credits on separate band)
- **Legal compliance:** See [`routing-and-map-attribution.md`](./routing-and-map-attribution.md)
- **API key management:** Read from `local.properties`, no hardcode in git

#### Plan smooth road following (Phase 01–03) — Phase 03 đóng
- **Phase 01:** Live real location display indoor (P0), separate display path from write path
- **Phase 02:** Member movement follow polyline with bearing + vertex-preserving step + spawn-once pattern
- **Phase 03:** Marker interpolation at UI layer — smooth animation between samples, no exrapolation
  - **Implementation:** Single `withFrameNanos` loop for all markers, state primitive-only
  - **Measurements (device real):** Jank **0.62%** @ 90 Hz (33/5360 frames, p50=10ms); marker step **0.046 m/frame** (43× under 2.0 m threshold); bearing interpolation error **1.2°–4.0°** vs. `flat=true` prediction; Room row count **24/60s = 2.50 s/row** (matches sampling rate)
  - **Known limitation:** S4 (self dot animation) not validated due to test setup (self in Hà Nội, camera in HCMC)
  - **Debt transfer to Phase 06:** `SyntheticPath.kt:53` speed bug remains (each tick half-step); must fix before Phase 06 B4 lap-time measurement or result will be 2× off

#### Phase 06-10 (repo): History, Timeline, Zone Editor — hoàn thành
- Route history with polyline simplification (US-27 to US-32)
- Zone event timeline (US-34 to US-36)
- Interactive zone editor with map (US-16 to US-21)

### Bug Fixes & Improvements

#### Phase 01 (fix-phase-01)
- Removed direct `android.util.Log` from `MviViewModel` (MVI doc violation)
- Implemented `AppLogger` interface for proper error logging

#### Phase 02 (fix-phase-02)
- Fixed Koin `verify()` to detect cross-module binding errors
- Added `androidx.test:runner` dependency for instrumented tests

#### Phase 03-04 (fix-phase-04)
- Fixed `MapViewModel` coroutine safety (`collectSafely` instead of `launchIn`)
- Added architecture test to prevent unsafe coroutine patterns

#### Phase 04-07 (fix-zone-follows-members)
- **Breaking change:** Zone tracking now follows family members, not self
- Removed Geofencing API (Play Services)
- Removed `BootCompletedReceiver` and associated receiver infrastructure
- Zone events now only generated from family member movement simulation

#### Phase 06 (fix-phase-06)
- Fixed `ZoneEditorViewModel` fallback camera center (no longer defaults to (0,0))
- Fixed `ZoneRow` delete background clipping in `SwipeToDismissBox`
- Fixed `SaveZoneUseCase` to allow editing existing zones at capacity limit

#### Phase 07 (fix-phase-07)
- Fixed zone event deduplication race condition with `Mutex`
- Fixed notification tap to open Timeline (added `FLAG_ACTIVITY_SINGLE_TOP`)

#### Phase 08 (fix-phase-08)
- Fixed history performance measurement (renamed `renderMs` → `frameMs`)
- Moved polyline simplification to background thread (`Dispatchers.Default`)

#### Phase 09 (fix-phase-09)
- Fixed Geofence initial trigger causing duplicate events
- Fixed `registerAll()` being called multiple times on cold start

#### Phase 10 (fix-phase-10)
- Fixed Timeline sticky header crash (`LazyListScope` Bundle safety)

#### Phase 11 (fix-phase-11)
- Removed Koin from `FtdLog` (prevents crash before `startKoin`)
- Moved `debugBuild` to `@Volatile var` assigned in `FamilyTrackerApp.onCreate`

### Data Source & Attribution

**Routing data source:** OpenStreetMap (OSM)  
**Routing providers:** GraphHopper Cloud, Valhalla (Stadia Maps, FOSSGIS, self-hosted)  
**Attribution requirement:** Always display "© OpenStreetMap contributors" + provider name on separate band

For full legal analysis, see [`routing-and-map-attribution.md`](./routing-and-map-attribution.md).

### Known Limitations & Open Issues

| Issue | Status | Details |
|-------|--------|---------|
| API key rotation | ⬜ Open | Key in `BuildConfig`, not server-side. Requires APK rebuild if compromised. Mitigation: quota caps on provider console. |
| GraphHopper redistribution | ⬜ Open | Điều khoản redistribution chưa được làm rõ. **Thư hỏi đã SOẠN 2026-08-24 nhưng CHƯA GỬI** — nội dung ở `plans/260824-1335-pluggable-routing-provider/reports/graphhopper-redistribution-enquiry.md`. Chặn phát hành. Im lặng không phải là đồng ý. |
| Zone tracking offline | ⬜ Open | No geofence API available when process dies. Feature requires backend or device-persistent service. |
| Free tier limitations | ✅ Known | GraphHopper free: `car/bike/foot` only (no motorcycle). Valhalla free: motorcycle available. Doc in place. |

### Testing Status

- **Unit test:** 271 xanh, 0 fail, 0 error (`:domain` 125, `:ui` 102, `:data` 43, `:app` 1) — từ `TEST-*.xml` sau `--rerun-tasks`
- **Gate G1–G9 (plan routing):** xanh, log và ảnh chứng ở `plans/260824-1335-pluggable-routing-provider/reports/`
  (`gate-log-2026-08-24.txt`, `g7-attribution-graphhopper.png`, `g6-attribution-valhalla.png`)
- **Phase 03 device validation:** Jank **0.62%**, marker movement **0.046 m/frame**, bearing error **1.2°–4.0°**
- **Instrumented test:** KHÔNG chạy trong đợt này — `:data:connectedDebugAndroidTest` nằm ngoài
  danh sách gate. Đừng ghi là "passing" khi chưa chạy.

### Dependencies

**Thêm mới (plan routing):**
- `com.squareup.okhttp3:okhttp` 5.5.0 — HTTP client
- `com.squareup.okhttp3:mockwebserver3-junit4` 5.5.0 — chỉ `testImplementation`. Là `mockwebserver3`,
  KHÔNG phải artifact `mockwebserver` bản cầu nối cũ

**Ktor KHÔNG được thêm.** Nó được cân nhắc rồi loại, và lý do là một con số đọc từ POM thật:
Ktor 3.5.2 khai báo `kotlin-stdlib 2.3.21`, lớn hơn 2.2.10 của dự án, nên kéo theo phải nâng
toolchain KSP/Room/compose compiler. OkHttp 5.5.0 khai báo `2.1.21`, nhỏ hơn 2.2.10, nên an toàn.
Ghi ở đây vì "vì sao KHÔNG dùng X" là thứ hay bị hỏi lại nhất, và trả lời bằng trí nhớ thì sai.

---

## v0.x (Pre-release)

Not applicable. Project started at v1.0 scope (Phase 01+).

---

## Future Roadmap

- **v1.1:** Turn-by-turn directions (maneuvers from provider)
- **v2.0:** Backend-driven zone sync, server-side routing cache, multi-polygon zones
- **v3.0:** Offline routing, local OSM data sync
