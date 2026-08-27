# Phase 07 — Chặn màn Bản đồ khi mất internet (D8)

## Context Links

- [`plan.md`](plan.md) · [`decisions.md` §C2 → **D8**](decisions.md) — nguồn chân lý của phase này.
  Đọc nguyên khối "✅ Xác nhận của chủ dự án — 2026-08-25 → D8" **trước khi viết dòng đầu tiên**:
  bảng D8, đoạn "Ranh giới phải giữ, gộp vào là hỏng demo", và mục "Chưa code — bốn việc phải làm".
- Yêu cầu: [PRD delta](docs/prd-delta-smooth-road-movement.md) **US-47** (mới), US-45 (đã thu hẹp), §3.6 F7
- Nghiệm thu: [QA/UAT](docs/qa-uat-smooth-road-movement.md) **QA-SRM-13, 17, 37, 38, 39, 40**, UAT-04
- Kiến trúc: `LLM.md` §2 (chiều phụ thuộc), §3, §6 (Koin), §10 (quyền và manifest), §11 (bố cục test), §12 (file mới nằm ở đâu), §13
- MVI: `docs/android-mvi-best-practices.md` §1 (State vs Effect), §2 (Contract), §3 (ViewModel), §4 (Screen), §9 (checklist)
- Phase trước: [04](phase-04-nguon-tuyen-duong-lai.md) (`MemberRouteSource` — phía bên kia của ranh
  giới sống còn), [05](phase-05-ghi-cong-osm-tren-man-ban-do.md) (`MapScreen` đã là `Column`,
  `MapViewModel` đã có 5 `collectSafely`)

## Overview

| | |
|---|---|
| **Ưu tiên** | **P0** — US-47 là P0; QA-SRM-13/37/38/40 đều P0 |
| **Trạng thái** | pending |
| **Ước lượng** | 3h |
| **Phụ thuộc** | Phase 05. Không phải phụ thuộc logic — phụ thuộc **file**: 05 đổi `MapScreen` từ `Box` sang `Column` và thêm `collectSafely` thứ năm vào `MapViewModel`. Chạy 07 trước thì phase 05 phải giải xung đột trên đúng hai file đó. Thứ tự chốt: 04 → 05 → **07** → 06 |

Mất internet thì app **nói thẳng**, không hạ cấp im lặng. Một cờ trong `MapState`, một **lớp phủ
không đóng được** trùm lên **đúng khung bản đồ**, tự tắt khi internet đã kiểm chứng trở lại. Theo
dõi GPS thật và mô phỏng gia đình **không dừng một nhịp nào** phía sau lớp phủ.

Phase này **không** chạm `:data/location/`, **không** chạm `LocationTrackingService`, **không**
chạm `MemberRouteSource`. Nếu phải sửa một trong ba chỗ đó thì ranh giới D8 đã bị đọc chéo — dừng
lại và đọc lại Key Insight #1.

## Key Insights

1. **Ranh giới sống còn: *mất internet* ≠ *lỗi nhà cung cấp*.** `ConnectivityManager` quyết định
   dialog; mã lỗi HTTP quyết định chọn tầng nguồn tuyến (phase 04). **Không chỗ nào đọc chéo.**
   *Cái giá nếu gộp:* 401 bật dialog "mất mạng" ⇒ điều kiện tắt dialog (có internet) đã đúng sẵn từ
   đầu ⇒ dialog **không bao giờ tự tắt được**, người demo kẹt vĩnh viễn trong một hộp thoại không
   đóng được, trên một máy wifi đầy vạch. Đây là chế độ hỏng tệ nhất D8 có thể sinh ra. QA-SRM-40 là
   ca âm khoá đúng chỗ này, và nó là P0.
2. **"Có internet" ≠ "có sóng".** Điều kiện kích hoạt là `NetworkCapabilities` thiếu
   `NET_CAPABILITY_INTERNET` **hoặc** thiếu `NET_CAPABILITY_VALIDATED`. *Cái giá nếu chỉ kiểm
   `INTERNET`:* wifi quán cà phê chưa qua captive portal có đủ `INTERNET` nhưng chưa `VALIDATED` —
   app im lặng, mọi request routing timeout, người dùng nhìn một bản đồ trông như đang chạy. Đó
   chính là ca demo-ngoài-văn-phòng hay gặp nhất (QA-SRM-37, P0).
3. **`registerDefaultNetworkCallback` KHÔNG bắn gì khi máy đang hoàn toàn không có mạng.** Không
   `onAvailable`, không `onLost` (không có gì để mất), không `onUnavailable` (callback đó chỉ dành
   cho `requestNetwork` có timeout). *Cái giá nếu chỉ dựa vào callback:* mở app **khi đã** ở chế độ
   máy bay thì lớp phủ **không bao giờ hiện** — đúng ca người dùng cần nó nhất. Vì vậy `callbackFlow`
   phải **tự đọc trạng thái hiện tại và phát nó ra trước**, rồi mới đăng ký callback.
4. **Là state, không phải Effect** (MVI doc §1). Effect là cho việc xảy ra **đúng một lần**; mất
   mạng là một **điều kiện đang kéo dài**. *Cái giá nếu làm bằng Effect:* xoay màn hình → `Channel`
   đã giao effect rồi, không giao lại → lớp phủ biến mất trong khi vẫn đang mất mạng, và người dùng
   nhìn thấy đúng cái bản đồ giả vờ đang chạy mà US-47 sinh ra để chặn. QA-SRM-13 bước 5 (xoay màn
   hình) là ca khoá điều này.
5. **Repo hiện KHÔNG có `ConnectivityManager` ở bất kỳ đâu** — đã kiểm bằng
   `grep -rn "ConnectivityManager\|NetworkCallback\|NetworkCapabilities\|ACCESS_NETWORK_STATE" app ui data domain`
   → 0 kết quả. Manifest hiện chỉ có `INTERNET`. Toàn bộ nguồn trạng thái mạng là code mới, không
   có pattern cũ nào để copy — và cũng không có pattern cũ nào để copy **sai**.
6. **Không dừng mô phỏng là một ràng buộc, không phải sự lười.** `MemberMovementSimulator` chạy tiếp
   phía sau lớp phủ. *Cái giá nếu dừng:* lúc lớp phủ tự tắt, thành viên nhảy một quãng đúng bằng thời
   gian mất mạng, vượt `MEMBER_RENDER_MAX_JUMP_M` ⇒ trượt QA-SRM-05 **và** QA-SRM-17. Cách bảo đảm
   rẻ nhất: phase này không có một dòng nào chạm `:data/location/` — S8 kiểm bằng `git diff --name-only`.
7. **Lớp phủ nằm TRONG khung bản đồ, không phải một window riêng.** Đây là chỗ phương án
   `AlertDialog` bị bác (2026-08-25): `Dialog` của Compose dựng một **window** mới nuốt mọi chạm,
   nên thanh tab bên dưới không bấm được ⇒ người mở app lúc đang ngoại tuyến kẹt ở màn Bản đồ, vỡ
   thẳng AC "Zone, Lịch sử, Cài đặt vẫn dùng bình thường" của US-47 và trúng tiêu chí KHÔNG ĐẠT
   "cả app bị khoá cứng" của UAT-04. Lớp phủ trong nội dung giữ nguyên 100% tinh thần D8 (không
   đóng được, chỉ màn Bản đồ, tự tắt khi mạng về) mà **không** cướp thanh tab. `NoInternetOverlay`
   là `internal`, nằm ở `feature/map/component/` — feature khác muốn dùng phải import chéo feature,
   đúng thứ cấu trúc package đang chặn (LLM.md §12).

## Requirements

> **Thuật ngữ.** D8, US-47 và QA-SRM-13/17/37→40 gọi nó là *dialog* — đó là **hành vi người dùng
> thấy**, và văn bản gốc không sửa. Cơ chế hiện thực đã chốt là **lớp phủ trong nội dung**
> (Implementation Step 6). Trong phase này, "dialog" ở phần Requirements là trích hành vi; "lớp phủ"
> ở mọi phần còn lại là cùng một thứ, nói ở tầng code.

**Chức năng**

- FR-1 Máy không có internet **đã kiểm chứng** (thiếu `NET_CAPABILITY_INTERNET` **hoặc** thiếu
  `NET_CAPABILITY_VALIDATED`) → màn Bản đồ hiện dialog "mất mạng" trong ≤ 5 giây (US-47, QA-SRM-13).
- FR-2 Dialog **không đóng được**: không nút đóng, chạm ngoài không đóng, nút Back không đóng (QA-SRM-13).
- FR-3 Dialog **sống sót qua xoay màn hình** — nó là state, không phải sự kiện (QA-SRM-13 bước 5).
- FR-4 Có internet đã kiểm chứng trở lại → dialog **tự đóng** trong ≤ 10 giây, không cần thao tác nào (QA-SRM-17).
- FR-5 Mở app **khi đã** mất mạng sẵn → dialog vẫn hiện (Key Insight #3).
- FR-6 Wifi captive portal chưa đăng nhập → tính là mất internet, dialog vẫn hiện (QA-SRM-37).
- FR-7 **Chỉ** màn Bản đồ. Zone, Lịch sử, Nhật ký, Cài đặt không bị chặn (QA-SRM-39).
- FR-8 Theo dõi GPS thật + mô phỏng gia đình **chạy liên tục** phía sau dialog: service sống,
  `location_points` không thủng, ENTER/EXIT vẫn nổ (QA-SRM-38).
- FR-9 **Ca âm:** vẫn có internet mà nhà cung cấp lỗi (thiếu khoá / 401 / 429 / 400 / timeout) →
  **không** dialog nào. Giữ nguyên hạ cấp im lặng của phase 04 (QA-SRM-40, US-45).

**Phi chức năng**

- NFR-1 `MapViewModel` không import `android.*` / Compose. Nguồn là `Flow<Boolean>` từ `:domain/repository/`.
- NFR-2 Quan sát qua `collectSafely`, không `launchIn(viewModelScope)` (LLM.md §4 điểm 5 —
  `CoroutineSafetyArchitectureTest` sẽ bắt nếu quên).
- NFR-3 Callback được huỷ đăng ký khi flow đóng. *Cái giá nếu quên:* mỗi lần vào lại màn Bản đồ là
  một callback rò; hệ thống giới hạn ~100 callback cho mỗi uid rồi ném `TooManyRequestsException` —
  app chết giữa buổi demo, sau khoảng 100 lần chuyển tab.
- NFR-4 Không đổi lược đồ Room, không tăng version DB, không thêm hằng số vào `TrackingConstants`
  (§13 Open #7 đang lệch, đừng làm lệch thêm).
- NFR-5 `MapScreen.kt` giữ dưới 200 dòng (LLM.md §5). Hiện 179; phase 05 thêm ~8; phase này được
  phép thêm tối đa ~5 ở call site — thân lớp phủ nằm ở `component/`.
- NFR-6 Không log SSID, không log toạ độ, không log tên mạng. Đúng một dòng
  `network_state hasInternet=true|false` mỗi lần **đổi** trạng thái (gate G7).

## Architecture

```
                         AndroidManifest.xml  + ACCESS_NETWORK_STATE   (quyền normal, không hỏi runtime)
                                     │
   :data ────────────────────────────┼───────────────────────────────────────────────
   network/AndroidNetworkMonitor.kt  │   ← package MỚI, cố ý KHÔNG nằm cạnh routing/
        callbackFlow {               │
          1. trySend(readNow())      │   ← BẮT BUỘC: máy đang offline thì callback không bắn gì
          2. cm.registerDefaultNetworkCallback(cb)
             cb.onCapabilitiesChanged(n, caps) -> trySend(caps.INTERNET && caps.VALIDATED)
             cb.onLost(n)                      -> trySend(false)
             (KHÔNG override onAvailable — xem Step 3)
          3. awaitClose { cm.unregisterNetworkCallback(cb) }
        }.distinctUntilChanged()
                    │  implements
                    ▼
   :domain ─────────────────────────────────────────────────────────────────────────
   repository/NetworkMonitor.kt        fun observeHasInternet(): Flow<Boolean>
                    │
   :ui ─────────────┼─────────────────────────────────────────────────────────────────
                    ▼
   MapViewModel.init  networkMonitor.observeHasInternet()
                          .collectSafely { setState { copy(hasInternet = it) } }   ← collect thứ SÁU
                    │
                    ▼
   MapState.hasInternet: Boolean = true
       val showNoInternetOverlay: Boolean get() = !hasInternet      ← val tính toán, không lưu trùng
                    │
                    ▼
   MapScreen  Scaffold {                       bottomBar = FamilyTrackerBottomBar  ← LUÔN chạm được
                  Column {
                      Box(weight(1f)) {                            ← phase 05
                          FamilyTrackerMap …
                          PermissionBanner / TrackingToggle …
                          if (state.showNoInternetOverlay) {
                              NoInternetOverlay()                  ← phase 07: TRONG Box này,
                          }                                           chồng lên bản đồ, KHÔNG phải
                      }                                               một window Dialog
                      RoutingAttribution(…)                        ← phase 05
                  }
              }

   ════════════ RANH GIỚI KHÔNG ĐƯỢC BẮC CẦU (Key Insight #1) ════════════
   ConnectivityManager ──► lớp phủ         |  mã lỗi HTTP ──► chọn tầng nguồn tuyến
   :data/network/AndroidNetworkMonitor     |  :data/routing/MemberRouteSource  (phase 04)
   không biết gì về RoutingProvider        |  không biết gì về NetworkMonitor
                    ▲                                          ▲
                    └────── InternetBlockerBoundaryTest quét mã nguồn cả hai phía ──────┘
```

**Vì sao lớp phủ nằm TRONG `Box(weight(1f))` chứ không phải ở `Scaffold`, `FamilyTrackerNavHost`
hay một `Dialog`:** đó là **toàn bộ** thứ quyết định thanh tab còn chạm được. Nằm trong `Box` đó thì
scrim chỉ nuốt chạm của khung bản đồ; `bottomBar` của `Scaffold` là anh em ngoài `Column` nội dung
nên không bị che một pixel nào, và người dùng vẫn rời màn Bản đồ được (QA-SRM-39, S14). Nâng nó lên
một bậc — ra ngoài `Column`, lên `Scaffold`, hay đổi về `Dialog` — là lấy lại đúng chế độ hỏng "cả
app bị khoá cứng" mà UAT-04 liệt kê là KHÔNG ĐẠT.

**Vì sao interface nằm ở `:domain/repository/` dù nó không phải "repository":** §12 không có dòng
riêng cho "cổng tới một khả năng của nền tảng", nhưng dự án đã có **hai tiền lệ** đúng hình dạng
này trong cùng thư mục — `LocationSource.kt` (cổng cấp toạ độ, §8.4) và `RoutingProvider.kt` (cổng
tuyến đường). Đẻ thêm `:domain/port/` cho file thứ ba là tạo chỗ thứ tư phải đi tìm cùng một loại
thứ. *Cái giá của việc để nó ở `:data`:* `MapViewModel` sẽ không thấy được nó — `:ui` không phụ
thuộc `:data` (§2), đó là lỗi biên dịch chứ không phải lời khuyên.

**Vì sao impl nằm ở `:data/network/` chứ không phải `:data/remote/`:** `remote/` do đường HTTP của
routing sở hữu (`RoutingHttpClient` + DTO). Đặt bộ quan sát mạng cạnh nó là **gợi ý bằng cấu trúc**
rằng hai thứ đó liên quan nhau — đúng cái đọc chéo mà Key Insight #1 cấm. Cây package là chỗ rẻ
nhất để ranh giới đó nhìn thấy được. §12 dòng "Một API Android (sensor, service, receiver) → `:data/`"
cho phép; mẫu đã có là `location/`, `notification/`, `routing/` — mỗi khả năng nền tảng một package.

**Vì sao `hasInternet` mặc định `true` chứ không phải `false`:** giữa lúc `MapViewModel` khởi tạo và
lần phát đầu tiên của flow có một khoảng vài mili-giây. *Cái giá nếu mặc định `false`:* một lớp phủ
**không đóng được** nháy lên ở **mọi lần mở app**, kể cả khi mạng hoàn toàn bình thường — hồi quy
nhìn thấy được bằng mắt trên mọi lần chạy. Khoảng trống đó an toàn **chỉ vì** Step 3 bắt flow phải
phát trạng thái đọc-ngay-lập-tức làm giá trị đầu tiên; bỏ bước đó thì `true` trở thành "không bao
giờ hiện lớp phủ khi mở app lúc đang offline" (Key Insight #3). Hai quyết định này đi cùng nhau,
không tách rời được.

## Related Code Files

**Tạo**

| Đường dẫn | Việc |
|---|---|
| `domain/src/main/kotlin/com/example/pion/family/tracker/demo/domain/repository/NetworkMonitor.kt` | `fun observeHasInternet(): Flow<Boolean>` + KDoc: "đã kiểm chứng" nghĩa là gì, và **cấm** dùng cổng này để chọn tầng nguồn tuyến |
| `data/src/main/java/com/example/pion/family/tracker/demo/data/network/AndroidNetworkMonitor.kt` | package MỚI. `callbackFlow` + `registerDefaultNetworkCallback` + đọc trạng thái ban đầu + `awaitClose` |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/component/NoInternetOverlay.kt` | `internal @Composable` — scrim `Box(fillMaxSize)` bắt chạm + `Surface`/`Card` giữa khung mang 2 chuỗi, **không nút nào** + `BackHandler(enabled = true) {}`. **Không** `Dialog`, **không** `AlertDialog` |
| `data/src/test/java/com/example/pion/family/tracker/demo/data/network/InternetBlockerBoundaryTest.kt` | Test khoá ranh giới D8, quét mã nguồn hai chiều (QA-SRM-40 phía cấu trúc) |
| `ui/src/test/java/com/example/pion/family/tracker/demo/ui/feature/map/MapBlockerIsNotADialogTest.kt` | Quét `ui/feature/map/`: cấm `AlertDialog`/`DialogProperties`/`window.Dialog` — khoá quyết định 2026-08-25 (Risk #1) |

**Sửa**

| Đường dẫn | Việc |
|---|---|
| `app/src/main/AndroidManifest.xml` | `+ <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` cạnh khối `INTERNET`, kèm comment trỏ US-47/D8 |
| `data/src/main/java/com/example/pion/family/tracker/demo/data/di/DataModule.kt` | `single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }` — viết tay, **không** `singleOf` (cùng lý do `TrackingRepositoryImpl`, §6) |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/MapContract.kt` | `+ val hasInternet: Boolean = true`; `+ val showNoInternetOverlay: Boolean get() = !hasInternet` |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/MapViewModel.kt` | `+ networkMonitor: NetworkMonitor` ở constructor; `collectSafely` thứ sáu |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/feature/map/MapScreen.kt` | 3 dòng: `if (state.showNoInternetOverlay) NoInternetOverlay()` **bên trong `Box(weight(1f))`** của bản đồ — không phải ở `Scaffold`, không phải ngoài `Column` |
| `ui/src/main/res/values/strings.xml` | 2 chuỗi mới — xem Step 5 |
| `ui/src/main/java/com/example/pion/family/tracker/demo/ui/di/UiModule.kt` | Không sửa nếu `viewModelOf(::MapViewModel)` vẫn resolve — **kiểm bằng `KoinModulesTest`**, không bằng mắt |
| `ui/src/test/java/.../ui/feature/map/MapViewModelTest.kt` | Helper `viewModel()` nhận thêm `networkMonitor` (mặc định `FakeNetworkMonitor(true)` ⇒ 0 test cũ phải sửa); + 3 ca mới |
| `ui/src/test/java/.../ui/feature/map/MapViewModelLaunchSafetyTest.kt` | Cập nhật lời gọi `MapViewModel(...)` ở dòng ~87 (thêm tham số); + 1 ca: flow mạng ném lỗi thì VM không chết |
| `app/src/test/java/.../KoinModulesTest.kt` | Binding mới phải resolve. `extraTypes` **không** cần thêm gì — `Context::class` đã có sẵn từ phase-04 |
| `LLM.md` | §3 (3 file mới + package `data/network/`) · **§10 (khối quyền: 7 → 8, thêm `ACCESS_NETWORK_STATE`, ghi lý do)** · §11 (dòng cho `InternetBlockerBoundaryTest`) · §13 **không thêm dòng Open nào** — rủi ro modal đã đóng bằng thiết kế (Risk #1) |
| `plans/.../docs/prd-delta-smooth-road-movement.md` | US-47 đã có implementation |
| `plans/.../docs/qa-uat-smooth-road-movement.md` | QA-SRM-13/17/37→40 trỏ về phase 07 |

**Xoá:** không.

## Implementation Steps

1. **Manifest.** Thêm vào `app/src/main/AndroidManifest.xml`, ngay dưới khối `INTERNET`:
   ```xml
   <!-- phase-07, US-47/D8 — ConnectivityManager.registerDefaultNetworkCallback() ném
        SecurityException nếu thiếu quyền này. Quyền `normal`: hệ thống cấp lúc cài, KHÔNG
        hỏi runtime, KHÔNG thêm bước nào vào luồng onboarding 3 bước (LLM.md §10). -->
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   ```
   `LLM.md` §10 đang ghi "**7 quyền**" — sửa thành 8 kèm một câu lý do, **trong cùng commit**.

2. **`:domain` — `NetworkMonitor.kt`.** Một hàm, không logic:
   ```kotlin
   interface NetworkMonitor {
       /**
        * `true` khi máy có internet ĐÃ KIỂM CHỨNG. Wifi đầy vạch nhưng chưa qua captive portal
        * trả `false` (QA-SRM-37).
        *
        * KHÔNG dùng cổng này để chọn tầng nguồn tuyến (D8, Key Insight #1): tầng do mã lỗi HTTP
        * quyết. Trộn hai đường vào nhau làm dialog "mất mạng" không bao giờ tự tắt được.
        * `InternetBlockerBoundaryTest` khoá luật này.
        */
       fun observeHasInternet(): Flow<Boolean>
   }
   ```

3. **`:data/network/AndroidNetworkMonitor.kt`.** `callbackFlow`, đúng thứ tự này:
   a. `val cm = context.getSystemService(ConnectivityManager::class.java)`;
   b. **`trySend(cm.readVerifiedInternet())` TRƯỚC khi đăng ký** — `private fun ConnectivityManager.readVerifiedInternet(): Boolean`
      = `getNetworkCapabilities(activeNetwork)?.isVerified() == true`. Đây là nguồn `false` **duy
      nhất** khi mở app lúc đang ở chế độ máy bay (Key Insight #3);
   c. `registerDefaultNetworkCallback(callback)` với đúng **hai** override:
      - `onCapabilitiesChanged(network, caps)` → `trySend(caps.isVerified())`
      - `onLost(network)` → `trySend(false)`
      **KHÔNG override `onAvailable`.** *Cái giá nếu override và `trySend(true)` ở đó:*
      `onAvailable` bắn **trước** khi hệ thống kiểm chứng xong, nên nối wifi captive portal sẽ đóng
      lớp phủ trong ~1 giây rồi mở lại — đúng ca QA-SRM-37 và là kiểu nháy khó chịu nhất. `onCapabilitiesChanged`
      luôn bắn ngay sau `onAvailable`, nên không mất sự kiện nào.
      `onUnavailable` **không** liên quan: nó chỉ bắn cho `requestNetwork` có timeout, không bao giờ
      bắn cho `registerDefaultNetworkCallback`;
   d. `private fun NetworkCapabilities.isVerified() = hasCapability(NET_CAPABILITY_INTERNET) && hasCapability(NET_CAPABILITY_VALIDATED)`
      — đúng nguyên văn điều kiện D8, một chỗ duy nhất, `&&` không phải `||`;
   e. `awaitClose { cm.unregisterNetworkCallback(callback) }` (NFR-3);
   f. `.distinctUntilChanged()` ở cuối chuỗi. *Vì sao bắt buộc:* `onCapabilitiesChanged` bắn cả khi
      băng thông ước lượng đổi — không lọc thì log `network_state` spam hàng chục dòng mỗi phút và
      QA-SRM-13/37 không đếm được gì;
   g. Log đúng một dòng mỗi lần đổi, qua `FtdLog` của `:data` (`data/util/FtdLog.kt`, gate G7):
      `network_state hasInternet=true|false`. **Không** SSID, **không** tên mạng, **không** toạ độ.

4. **`MapContract`.** `+ val hasInternet: Boolean = true` vào `MapState`, và
   `val showNoInternetOverlay: Boolean get() = !hasInternet` — `val` tính toán, cùng mẫu
   `showNotificationsBanner`/`showLocationDegradedBanner` đã có (MVI doc §2 "Derive, don't
   duplicate"). *Cái giá nếu lưu hai field:* có ngày chúng nói hai chuyện khác nhau, và ngày đó
   lớp phủ hoặc kẹt hoặc không hiện.

5. **Chuỗi** — `ui/src/main/res/values/strings.xml`, đúng hai key, đặt cạnh khối `map_*`:
   ```xml
   <string name="map_no_internet_title">Không có kết nối internet</string>
   <string name="map_no_internet_message">Màn hình Bản đồ cần internet để lấy tuyến đường. Việc theo dõi vẫn đang chạy — thông báo này tự đóng khi có mạng trở lại.</string>
   ```
   Không chuỗi nút nào: lớp phủ không có nút. Câu thứ hai nói thẳng hai sự thật người dùng cần
   (theo dõi không dừng — QA-SRM-38; tự đóng — QA-SRM-17), bằng tiếng người, không mã lỗi (UAT-04).

6. **`NoInternetOverlay.kt`** (`ui/feature/map/component/`, `internal`). **Lớp phủ trong nội dung,
   KHÔNG `Dialog`/`AlertDialog`** (chốt 2026-08-25 — xem Key Insight #7):
   ```kotlin
   @Composable
   internal fun NoInternetOverlay(modifier: Modifier = Modifier) {
       // Back không rời màn Bản đồ trong lúc đang bị chặn — thay cho
       // DialogProperties(dismissOnBackPress = false) của phương án AlertDialog đã bị bác.
       BackHandler(enabled = true) {}
       Box(
           modifier = modifier
               .fillMaxSize()
               .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
               // nuốt MỌI chạm rơi vào khung bản đồ; null/null = không ripple, không nảy
               .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
           contentAlignment = Alignment.Center,
       ) {
           Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
               Column(
                   modifier = Modifier.padding(Dimens.ScreenPadding),
                   verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
               ) {
                   Text(stringResource(R.string.map_no_internet_title), style = …titleMedium)
                   Text(stringResource(R.string.map_no_internet_message), style = …bodyMedium)
               }                       // KHÔNG nút nào — không có đường đóng bằng thao tác
           }
       }
   }
   ```
   Ba thứ **bắt buộc**, mỗi thứ khoá một bước của QA-SRM-13:
   - `BackHandler(enabled = true) {}` → Back **không** đóng, cũng không pop nav (bước 3). *Cái giá
     nếu quên:* Back rời màn Bản đồ, người dùng thấy đúng cái bản đồ giả-vờ-đang-chạy khi quay lại;
   - `.clickable(indication = null) {}` trên scrim → chạm vào bản đồ **không** xuyên xuống được
     (bước 4). *Cái giá nếu quên:* nhấn giữ vẫn mở được trình sửa zone qua một bản đồ đang bị chặn;
   - **không nút nào** trong `Surface` → không có đường đóng bằng thao tác. Đóng chỉ do `hasInternet`
     đổi (FR-4).
   `Dimens.ScreenPadding`/`Dimens.SpaceSm` và `MaterialTheme.colorScheme` lấy từ
   `designsystem/theme/` — **không** số ma thuật, **không** màu literal trong composable (LLM.md §12).
   Alpha `0.6f` của scrim là hằng số duy nhất viết thẳng; nếu muốn đúng chuẩn thì thêm
   `Dimens.OVERLAY_SCRIM_ALPHA` cạnh `ZONE_FILL_ALPHA` đã có.

7. **`MapViewModel`.** Thêm `private val networkMonitor: NetworkMonitor` vào constructor và trong
   `init`:
   ```kotlin
   networkMonitor.observeHasInternet().collectSafely { has -> setState { copy(hasInternet = has) } }
   ```
   Đây là `collectSafely` **thứ sáu** (3 gốc + live self location của phase 01 + `routeSource` của
   phase 05 + cái này). **Không** `combine` với năm cái kia — nguồn độc lập thì collect độc lập
   (phase-05 Implementation Step 3). **Không** `launchIn` — `CoroutineSafetyArchitectureTest` sẽ đỏ.

8. **`MapScreen`.** Ba dòng, đặt **bên trong `Box(Modifier.weight(1f))`** của khung bản đồ (cái
   `Box` phase 05 dựng ra để `PermissionBanner`/`TrackingToggle` còn `align()` được), là phần tử
   **cuối** của `Box` đó để nằm trên mọi lớp khác:
   ```kotlin
   if (state.showNoInternetOverlay) {
       NoInternetOverlay()
   }
   ```
   **Không** ở `Scaffold`'s content lambda ngoài `Column`, **không** ở `FamilyTrackerNavHost`,
   **không** ở `MainActivity`, **không** bọc trong `Dialog`. Vị trí này là toàn bộ cơ chế của
   FR-7/QA-SRM-39: scrim chỉ phủ khung bản đồ, `bottomBar` nằm ngoài `Column` nên **vẫn bấm được**,
   và lớp phủ sống-chết cùng composition của màn Bản đồ. Kiểm lại `wc -l MapScreen.kt` < 200.

9. **Koin.** `DataModule.kt`: `single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }`.
   Viết tay chứ **không** `singleOf(::AndroidNetworkMonitor) bind NetworkMonitor::class`: constructor
   nhận `Context`, mà `verify()` phân tích tĩnh qua constructor và sẽ đòi một Koin definition cho
   `Context` (§6, cùng bẫy đã dính ở `TrackingRepositoryImpl`). `KoinModulesTest` không phải sửa —
   `extraTypes` đã có `Context::class`.

10. **Test `:ui`** — `MapViewModelTest`. Thêm fake viết tay ngay trong file (LLM.md §11, không thư
    viện mock):
    ```kotlin
    private class FakeNetworkMonitor(initial: Boolean = true) : NetworkMonitor {
        val flow = MutableStateFlow(initial)
        override fun observeHasInternet(): Flow<Boolean> = flow
    }
    ```
    Cho helper `viewModel()` một tham số `networkMonitor: NetworkMonitor = FakeNetworkMonitor()` ⇒
    **không test cũ nào phải sửa**. Ba ca mới:
    - `no verified internet shows the blocking overlay` — phát `false` → `showNoInternetOverlay` true;
    - `internet back closes the overlay without any intent` — phát `false` rồi `true`, **không bơm
      Intent nào** → `showNoInternetOverlay` false (FR-4: tự tắt, không thao tác);
    - **`provider failure while online never blocks the map`** — ca âm sống còn của
      QA-SRM-40: `FakeNetworkMonitor(true)` + `routeSource = RouteSourceInfo(kind = SYNTHETIC, …)`
      (tức nhà cung cấp vừa trả 401 và phase 04 đã hạ cấp) → `state.hasInternet` **true** và
      `showNoInternetOverlay` **false**. KDoc của ca này ghi thẳng cái giá: hỏng nó ⇒ lớp phủ kẹt vĩnh
      viễn vì điều kiện tắt đã đúng sẵn từ đầu.

11. **Test `:ui`** — `MapViewModelLaunchSafetyTest`: cập nhật lời gọi constructor, thêm một ca
    `NetworkMonitor` có flow **ném lỗi** → ViewModel không chết, các nguồn khác vẫn cập nhật
    (`collectSafely` là sàn, LLM.md §4 điểm 2).

12. **Test `:data` khoá ranh giới** — `InternetBlockerBoundaryTest`. Cùng mẫu quét-mã-nguồn với
    `RealGpsNoSnapArchitectureTest` (phase 01): `File("src/main/java/…")`, Gradle chạy test với
    working dir = thư mục module. Hai khẳng định, hai chiều:
    - `data/routing/MemberRouteSource.kt` + `data/routing/OnDevicePolylineCache.kt` **không chứa**
      `ConnectivityManager`, `NetworkMonitor`, `NetworkCapabilities`, `hasInternet`;
    - `data/network/AndroidNetworkMonitor.kt` **không chứa** `RoutingProvider`, `MemberRouteSource`,
      `AppError`, `401`, `429`.
    Và một khẳng định thứ ba, ở `:ui` (`ui/src/test/.../ui/feature/map/MapBlockerIsNotADialogTest.kt`,
    cùng mẫu quét mã nguồn): không file nào dưới `ui/src/main/java/.../ui/feature/map/` chứa
    `AlertDialog`, `DialogProperties`, hay `androidx.compose.ui.window.Dialog`. Đây là Risk #1 —
    xem bảng đó cho cái giá.
    KDoc nói rõ test này tồn tại để **lần sau** ai đó nối hai đường lại sẽ thấy đỏ, không phải để mô
    tả hiện trạng. Không có test nào ở `:data` cho bản thân `AndroidNetworkMonitor`: nó là adapter
    thuần quanh API Android, dự án **không** dùng Robolectric (LLM.md §11), và một fake
    `ConnectivityManager` chỉ chứng minh được rằng fake hoạt động. Hành vi thật của nó được nghiệm
    thu ở Step 14/15 trên máy thật — ghi thẳng lý do này vào KDoc để người sau không tưởng là sót.

13. `./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache`.

14. **Chạy thật trên emulator Pixel** (`emulator-5554`) — bơm trạng thái mạng, `adb logcat -s FTD_EVENT`:
    a. Mở app ở màn Bản đồ, bật theo dõi → bật chế độ máy bay → lớp phủ hiện ≤ 5 s, log
       `network_state hasInternet=false` đúng **một** dòng (QA-SRM-13);
    b. Bấm Back, chạm vào bản đồ dưới scrim, nhấn giữ bản đồ → **không** đóng, **không** mở trình
       sửa zone. Xoay màn hình → lớp phủ **vẫn còn** (QA-SRM-13 bước 3–5). **Rồi bấm tab Zone →
       chuyển được** (S14/QA-SRM-39), quay lại tab Bản đồ → lớp phủ vẫn còn;
    c. Tắt máy bay, **không chạm màn hình** → lớp phủ tự đóng ≤ 10 s, đúng một dòng
       `network_state hasInternet=true`, và không cú nhảy marker nào (QA-SRM-17);
    d. **Đóng app hẳn, bật máy bay, mở lại app** → lớp phủ hiện ngay (FR-5 / Key Insight #3). Ca này
       là ca duy nhất bắt được lỗi "chỉ dựa vào callback";
    e. Build giả 401 (khoá sai trong `local.properties`) **với mạng bình thường** → **không** lớp phủ
       nào trong 3 chặng liên tiếp, log `sim_route_failed reason=401` + `sim_route_loaded source=SYNTHETIC`
       (QA-SRM-40 phía hành vi).

15. **Chạy thật trên máy `SM-A165F`** (Android 16, serial `RF8Y60B9NCZ`) — hai ca emulator không
    làm được:
    a. **QA-SRM-37** — nối một wifi captive portal (hotspot điện thoại có cổng đăng nhập, hoặc wifi
       quán), **không** đăng nhập → lớp phủ **vẫn hiện** dù máy đầy vạch sóng;
    b. **QA-SRM-38** — bật theo dõi, bật máy bay, **đi bộ thật 5 phút**, tắt máy bay, mở tab Lịch sử
       → polyline **không thủng** đúng khoảng thời gian đó; `zone_event_raised` vẫn có trong logcat
       lúc mất mạng. Đây là ca P0 chứng minh FR-8, và nó chỉ đúng nếu Step 8 không chạm `:data/location/`;
    c. **QA-SRM-39** — bật máy bay **trong lúc đang ở tab Zone**, rồi duyệt Zone → sửa zone → Lịch
       sử → Cài đặt: không lớp phủ nào, cả bốn dùng bình thường. **Và** làm lại một lượt theo chiều
       ngược: đứng ở màn Bản đồ **đang bị chặn** rồi bấm tab Zone → phải chuyển được (S14).

16. Cập nhật `LLM.md` §3/§10/§11 (§13 **không** thêm dòng Open nào — xem Risk #1), PRD delta US-47,
    QA doc — **trong cùng commit** với code.

## Todo List

- [x] `ACCESS_NETWORK_STATE` vào `app/src/main/AndroidManifest.xml` + comment lý do
- [x] `NetworkMonitor.kt` (`:domain/repository/`) + KDoc cấm đọc chéo
- [x] `AndroidNetworkMonitor.kt` (`:data/network/`, package mới): đọc trạng thái ban đầu → đăng ký → `awaitClose` → `distinctUntilChanged`
- [x] Đúng 2 override: `onCapabilitiesChanged` + `onLost`. **Không** `onAvailable`
- [x] Log `network_state hasInternet=` qua `FtdLog`, không SSID/toạ độ
- [x] `MapState.hasInternet = true` + `val showNoInternetOverlay`
- [x] 2 chuỗi `map_no_internet_title` / `map_no_internet_message`
- [x] `NoInternetOverlay.kt` — scrim nuốt chạm + `Surface` 2 chuỗi **không nút** + `BackHandler(true) {}`. **Không** `Dialog`/`AlertDialog`
- [x] `MapViewModel` `collectSafely` thứ sáu + Koin `single<NetworkMonitor>` viết tay
- [x] `MapScreen`: 3 dòng gọi lớp phủ **trong `Box(weight(1f))`**, không ở `Scaffold`/NavHost. `wc -l` < 200
- [x] `MapViewModelTest` + `FakeNetworkMonitor` + 3 ca (gồm ca âm QA-SRM-40)
- [x] `MapViewModelLaunchSafetyTest` cập nhật constructor + ca flow mạng ném lỗi
- [x] `InternetBlockerBoundaryTest` quét mã nguồn hai chiều
- [x] `MapBlockerIsNotADialogTest` — `ui/feature/map/` không được có `AlertDialog`/`Dialog`
- [x] `KoinModulesTest` xanh, không thêm `extraTypes`
- [x] Emulator: 5 kịch bản 14a–14e, log dán vào dev report (14b gồm cả bước chuyển tab, S14)
- [ ] `SM-A165F`: captive portal (37), đi bộ 5 phút lúc mất mạng (38), tab khác không bị chặn (39) — **CÒN NỢ, cần chủ dự án chạy tay**
- [x] `git diff --name-only` không có file nào trong `data/location/`
- [x] `LLM.md` §3 + **§10 (7 → 8 quyền)** + §11, cùng commit — **§13 CÓ thêm Open #23**, khác dự kiến của phase doc: lỗi điều hướng tích luỹ ViewModel là lỗi có sẵn mà phase này phơi ra, không phải rủi ro modal mà Risk #1 đã đóng bằng thiết kế
- [x] PRD delta US-47 + QA doc 13/17/37→40 trỏ về phase 07

## Success Criteria

| # | Điều kiện | Cách kiểm | QA |
|---|---|---|---|
| S1 | Mất internet → lớp phủ hiện ≤ 5 s, và **không** đóng được bằng Back / chạm vào bản đồ / nút nào (không có nút nào) | Chạy thật 14a–14b | QA-SRM-13 |
| S2 | Xoay màn hình khi lớp phủ đang hiện → lớp phủ **vẫn còn** | Chạy thật 14b | QA-SRM-13 bước 5 |
| S3 | Có internet lại → lớp phủ **tự** đóng ≤ 10 s, không một thao tác nào | Chạy thật 14c + `MapViewModelTest` (phát `false` rồi `true`, không bơm Intent) | QA-SRM-17 |
| S4 | Mở app **khi đã** ở chế độ máy bay → lớp phủ vẫn hiện | Chạy thật 14d | FR-5 |
| S5 | Wifi captive portal chưa đăng nhập → lớp phủ vẫn hiện | `SM-A165F` 15a | QA-SRM-37 |
| S6 | **Ca âm:** 401 khi vẫn có internet → `MapState.hasInternet` **true**, `showNoInternetOverlay` **false**, không lớp phủ nào trên màn | `MapViewModelTest` (unit) + chạy thật 14e | **QA-SRM-40** |
| S7 | `MemberRouteSource`/`OnDevicePolylineCache` không tham chiếu `NetworkMonitor`; `AndroidNetworkMonitor` không tham chiếu `RoutingProvider`/mã lỗi; `ui/feature/map/` không có `AlertDialog`/`Dialog` | `InternetBlockerBoundaryTest` + `MapBlockerIsNotADialogTest` | QA-SRM-40 (cấu trúc), Risk #1 |
| S8 | Diff của phase này **không chạm** file nào trong `data/location/`, `LocationTrackingService.kt`, `MemberRouteSource.kt` | `git diff --name-only` trước khi commit | FR-8, QA-SRM-38 |
| S9 | Mất mạng 5 phút có di chuyển thật → `location_points` liên tục, polyline Lịch sử không đứt, `zone_event_raised` vẫn nổ | `SM-A165F` 15b | **QA-SRM-38** |
| S10 | Zone / Lịch sử / Nhật ký / Cài đặt dùng bình thường khi máy bay bật | `SM-A165F` 15c | QA-SRM-39 |
| S11 | Đúng **một** dòng `network_state` mỗi lần đổi trạng thái, không SSID, không toạ độ | `adb logcat -s FTD_EVENT` trong 14a/14c | G7, NFR-6 |
| S12 | `MapScreen.kt` < 200 dòng; toàn bộ `:ui` không có `launchIn(viewModelScope)` mới | `wc -l` + `CoroutineSafetyArchitectureTest` | LLM.md §4, §5 |
| S13 | Mọi test cũ của `MapViewModelTest` xanh **không sửa một assertion nào** | `./gradlew :ui:test` | Hồi quy |
| **S14** | **Đang hiện lớp phủ mà vẫn bấm được thanh tab: chuyển sang Zone và Lịch sử bình thường, quay lại Bản đồ thì lớp phủ vẫn còn** | Bằng tay trên `SM-A165F` (14b + 15c). Đây là tiêu chí mua được bằng việc bỏ `AlertDialog` — hỏng nó là quay về "cả app bị khoá cứng" | **QA-SRM-39**, UAT-04 |

### Bằng chứng nghiệm thu — Step 14, emulator-5554 (API 37), 2026-08-26

`adb shell cmd connectivity airplane-mode enable|disable` chạy được không cần root; mốc thời gian
đọc từ dấu thời gian dòng `network_state` trong `FTD_EVENT`, **không** đọc từ `ping` (lớp phủ đóng
theo `NET_CAPABILITY_VALIDATED`, mà hệ thống xác thực xong SAU khi đường mạng đã thông).

| Ca | Đo được | Trần | Kết quả |
|---|---|---|---|
| **14a** lớp phủ hiện khi mất mạng | **396 ms** (lệnh 15:28:06.081 → log 15:28:06.477) | 5 s | ĐẠT |
| **14b** Back / chạm dưới scrim / nhấn giữ | không đóng, không rời màn, **không** mở trình sửa zone | — | ĐẠT |
| **14b** xoay màn hình | lớp phủ **vẫn còn**, và **không** sinh dòng `network_state` mới | — | ĐẠT |
| **S14** bấm tab Zone khi đang bị chặn | chuyển được (ZoneA/ZoneB hiện, **không** lớp phủ ở đó); quay lại Bản đồ thì lớp phủ vẫn còn | — | ĐẠT |
| **14c** tự đóng khi có mạng lại | **2,5 s** (15:30:29.336 → 15:30:31.871), không chạm màn hình | 10 s | ĐẠT |
| **14d** mở app KHI ĐÃ ở chế độ máy bay | lớp phủ hiện, `hasInternet=false` sau **1,9 s** kể cả khởi động nguội | — | ĐẠT |
| **14e** ca âm: khoá sai + mạng bình thường | `hasInternet=true`, `sim_route_failed reason=NETWORK:Wrong credentials…`, `sim_route_loaded source=SYNTHETIC`, **0 lớp phủ** | — | ĐẠT |
| **G7** | 0 lần khoá API xuất hiện trong log, 0 toạ độ | 0 | ĐẠT |

**S11 trượt ở lần đo đầu, đã sửa và đo lại.** Kịch bản 14 chạy đúng như viết thì S11 xanh giả: nó
chỉ xanh khi màn Bản đồ được mở đúng một lần. Thực tế đo được **5 dòng `network_state` cho MỘT lần
đổi trạng thái** sau 3 lần chuyển tab — vì `callbackFlow` là flow lạnh và `FamilyTrackerNavHost`
tạo một `MapViewModel` mới mỗi lần bấm tab (`LLM.md` §13 Open #23). Đó cũng chính là chế độ hỏng
**NFR-3** mô tả: mỗi collector là một `registerDefaultNetworkCallback`, hệ thống chặn ở ~100 rồi
ném `TooManyRequestsException`.

Sửa: `AndroidNetworkMonitor` bọc `shareIn(WhileSubscribed)` ⇒ N collector dùng chung đúng một đăng
ký. Đo lại bằng **đúng phép đo đã phơi ra lỗi**:

| | trước | sau |
|---|---|---|
| khởi động | 1 dòng | 1 dòng |
| sau 3 lần chuyển tab | 4 dòng | **1 dòng** |
| một lần đổi trạng thái | **5 dòng** | **1 dòng** |

**Bài học đáng giữ:** kịch bản nghiệm thu viết sẵn chỉ chứng minh được thứ nó nghĩ tới. Bước làm lộ
lỗi (chuyển tab) nằm trong Step 14b vì lý do khác hẳn (S14 — thanh tab còn bấm được), và nếu không
tình cờ chạy nó TRƯỚC 14c thì lỗi callback tích luỹ đã đi thẳng vào commit với S11 báo xanh.

### Code review tìm thêm hai thứ sau khi đo xong

**Đã sửa — `replayExpirationMillis` mặc định là `Long.MAX_VALUE`.** Bản `shareIn` đầu tiên (bản vá
cho lỗi 5-callback ở trên) để tham số này ở mặc định, nghĩa là **bộ đệm replay sống sót sau khi
upstream dừng**. Chiều hỏng: app ở nền quá `stopTimeoutMillis` lúc đang mất mạng ⇒ cache giữ
`false`; mở lại khi mạng ĐÃ về thì collector mới nhận `false` cũ trước khi `readVerifiedInternet()`
kịp chạy ⇒ nháy một lớp phủ **không đóng được** trên máy mạng bình thường. Đúng thứ mà quyết định
"`hasInternet` mặc định `true`" được chọn để tránh, mở lại bằng cửa khác. Nay `replayExpirationMillis = 0`,
và `AndroidNetworkMonitorContractTest` ghim cả tham số này. Đo lại sau khi sửa: vẫn 1/1/1.

**Đã sửa sau khi hỏi chủ dự án — xem `LLM.md` §13 Fixed #33.** Scrim phủ cả `TrackingToggle`. Với người đã bật theo dõi
thì đúng ý D8; ca ngược lại chưa ai xét: **mở app lúc ngoại tuyến mà theo dõi đang TẮT thì không
bật được cho tới khi có mạng**, nên suốt thời gian đó không một `location_points` nào được ghi.
Xác nhận trên emulator: chạm đúng bounds công tắc ⇒ `tracking_toggled enabled=true` = 0, công tắc
không còn trong cây accessibility. Và chuỗi `map_no_internet_message` đang khẳng định "Việc theo dõi
vẫn đang chạy" — **sai** trong đúng trạng thái đó. Không QA-SRM nào phủ ca này (QA-SRM-38 giả định
theo dõi đã bật từ trước).

**Chủ dự án chọn cho công tắc nổi lên trên (2026-08-26).** Thứ tự trong `Box` nay là: bản đồ →
banner → nút "Chỉ đường" → **lớp phủ** → công tắc. Luật thành văn: *thứ gì chạy được ngoại tuyến thì
không được chặn*; nút "Chỉ đường" cố ý ở dưới scrim vì màn Dẫn đường thật sự cần mạng. Đây là **sửa
đổi có chủ ý so với Step 8** ("lớp phủ là phần tử cuối") — Step 8 viết ra để lớp phủ nằm trên bản đồ,
không phải để chặn theo dõi; ghi rõ tại chỗ trong KDoc cả hai file. Chuỗi thông báo viết lại cho
đúng ở cả hai trạng thái. `MapScreen.kt` chạm 204 dòng nên tách `NavigateToMemberButton.kt` (còn 197).

**Mutation (baseline 331 → 337).** Năm đột biến sống sót trước review, mỗi cái hỏng một yêu cầu:
`&&` → `||` (captive portal, QA-SRM-37); bỏ `trySend(readVerifiedInternet())` (FR-5); thêm
`onAvailable { trySend(true) }` (nháy lớp phủ ở captive portal); `hasInternet` mặc định `false`;
và — đáng chú ý nhất — **gỡ hẳn `shareIn`**, tức chính lỗi 5-callback vừa mất một buổi đo để tìm ra,
mà 331/331 vẫn xanh. Tất cả đã ghim bằng `AndroidNetworkMonitorContractTest`.

### Còn nợ — cần chủ dự án chạy trên `SM-A165F`

Emulator không dựng được hai ca này:

- **QA-SRM-37 / S5** — nối wifi captive portal (hotspot có cổng đăng nhập, hoặc wifi quán), **không**
  đăng nhập → lớp phủ phải **vẫn hiện** dù máy đầy vạch sóng. Đây là ca demo-ngoài-văn-phòng hay gặp
  nhất, và là lý do điều kiện dùng `VALIDATED` chứ không chỉ `INTERNET`.
- **QA-SRM-38 / S9** — bật theo dõi, bật máy bay, **đi bộ thật 5 phút**, tắt máy bay, mở tab Lịch sử
  → polyline không thủng, `zone_event_raised` vẫn nổ trong lúc mất mạng. S8 (diff không chạm
  `data/location/`) là bảo đảm cấu trúc cho ca này, nhưng không thay được phép đo.

## Risk Assessment

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| **Ai đó "sửa cho gọn" bằng cách đổi lớp phủ về `AlertDialog`/`Dialog`.** Nó *trông* đúng hơn, ngắn hơn, và là phản xạ đầu tiên của mọi người khi thấy hai `Text` trong một `Surface`. Cái giá: `Dialog` dựng một window riêng nuốt hết chạm ⇒ thanh tab chết ⇒ vỡ AC "Zone, Lịch sử, Cài đặt vẫn dùng bình thường" của US-47 và trúng tiêu chí KHÔNG ĐẠT "cả app bị khoá cứng" của UAT-04 — đúng lý do phương án `AlertDialog` bị bác ngày 2026-08-25 | **Trung bình** | Ba lớp: (1) KDoc đầu `NoInternetOverlay.kt` ghi thẳng lệnh cấm + ngày + lý do, không chỉ mô tả code; (2) `MapBlockerIsNotADialogTest` ở **`:ui`** (phải ở `:ui` — test quét mã nguồn chạy với working dir = thư mục module, `:data` không với tới `ui/src/main` được): `ui/feature/map/` **không** chứa `AlertDialog`/`DialogProperties`/`androidx.compose.ui.window.Dialog`, ~15 dòng; (3) S14 là ca nghiệm thu bằng tay, hỏng ngay lần đầu ai đó đổi |
| Ai đó "đơn giản hoá" bằng cách cho `MemberRouteSource` đọc `NetworkMonitor` để khỏi gọi mạng khi offline | **Cao** | S7 là cổng chặn; KDoc của `NetworkMonitor` ghi lệnh cấm; Key Insight #1 ghi cái giá. Nghe rất hợp lý và đó chính là lý do nó nguy hiểm |
| Override `onAvailable` → lớp phủ nháy khi nối wifi captive portal | Trung bình | Step 3c cấm thẳng và ghi lý do; S5 bắt được trên máy thật |
| Quên `awaitClose { unregisterNetworkCallback }` | Trung bình | NFR-3; triệu chứng chỉ lộ sau ~100 lần chuyển tab, tức là **giữa buổi demo** chứ không phải trong CI. Code review đọc đúng dòng này |
| Chỉ dựa vào callback, không đọc trạng thái ban đầu ⇒ mở app lúc offline không có lớp phủ nào | **Cao** | S4 / 14d là ca duy nhất bắt được. Đừng bỏ ca đó vì "chắc chắn nó chạy" |
| Mặc định `hasInternet = false` ⇒ lớp phủ nháy mỗi lần mở app | Trung bình | Mặc định `true` + phát trạng thái đọc-ngay làm giá trị đầu tiên. Hai thứ đi cặp, xem §Architecture |
| Lớp phủ và snackbar lỗi (`MapEffect.ShowError`) chồng nhau | Thấp | Không còn là vấn đề sau khi bỏ `Dialog`: `snackbarHost` của `Scaffold` nằm **ngoài** `Column` nội dung nên không bị scrim che, snackbar vẫn đọc được. `Channel` cũng không đánh rơi effect nào (LLM.md §4 điểm 1) |
| `activeNetwork` trả giá trị trễ ngay lúc chuyển mạng | Thấp | Chỉ dùng ở lần đọc đầu tiên; mọi giá trị sau đến từ tham số `caps` của callback, không đọc lại `activeNetwork` |

## Security Considerations

- `ACCESS_NETWORK_STATE` là quyền **normal**: hệ thống cấp lúc cài, không hỏi runtime, không thêm
  bước nào vào luồng onboarding 3 bước (LLM.md §10). Nó **không** cho đọc SSID, không cho đọc vị
  trí, không cho đọc lưu lượng — chỉ trạng thái kết nối.
- **Không log** SSID, tên mạng, transport, hay toạ độ. Đúng một trường boolean
  (`network_state hasInternet=`), gate G7 qua `FtdLog` của `:data` — câm ở release.
- Không dữ liệu nào rời thiết bị vì thay đổi này. Cổng mới chỉ **đọc**; `:ui` không có đường nào ghi
  vào nó.
- Chuỗi của lớp phủ là chuỗi tĩnh trong `strings.xml`, không ghép từ dữ liệu mạng ⇒ không bề mặt inject
  và không rò mã lỗi kỹ thuật ra màn hình người dùng (US-47, UAT-04).
- Lớp phủ **không** làm dừng foreground service: người dùng đã đồng ý theo dõi thì việc theo dõi vẫn
  chạy và thông báo thường trực vẫn hiện — không có cửa sổ nào app theo dõi im lặng mà người dùng
  tưởng đã dừng.

## Next Steps

- Phase 06 chạy **UAT-04** dựa trên đúng log và ảnh chụp của Step 14–15, và chạy lại
  `MemberRoamerTest` với bộ hằng số cuối cùng. Không có ngưỡng nào của phase này cần phase 06 chốt số.
- **Quyết định 2026-08-25 đã chốt: lớp phủ trong nội dung, bỏ `AlertDialog`** — lý do đầy đủ ở Key
  Insight #7, cách chặn tái diễn ở Risk #1. `decisions.md` D8 vẫn ghi nguyên chữ "dialog": đó là
  quyết định gốc và là dấu vết, **không sửa**; chỉ cơ chế trình bày đổi, tinh thần D8 giữ 100%.
- Nếu sau này cách trình bày lại đổi lần nữa, thay đổi vẫn khu trú trong `NoInternetOverlay.kt`:
  `MapState`, `NetworkMonitor`, `AndroidNetworkMonitor` và toàn bộ test `MapViewModelTest` **không
  đổi một dòng**. Đó là lý do cờ nằm ở state chứ không nằm ở composable.
- Nếu sau này có màn hình thứ hai thật sự cần internet, `NetworkMonitor` đã sẵn sàng — nhưng
  `NoInternetOverlay` thì **không**: nó `internal` trong `feature/map/`, và cách nó neo vào
  `Box(weight(1f))` là đặc thù của màn Bản đồ. Đủ 2 chỗ dùng thật thì chuyển lên
  `designsystem/component/` (§12) — đừng import chéo feature, và đừng nhân bản nó rồi để một bản
  hoá thành `Dialog`.
