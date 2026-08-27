# Reviewer — Phase 07 (chặn màn Bản đồ khi mất internet, US-47/D8)

**Ngày:** 2026-08-26 · **Phạm vi:** `git diff HEAD` + 5 file mới · Không sửa file sản phẩm, không
chạy `adb`, không đụng `.md` nào ngoài file này.

---

## KẾT LUẬN: **KHÔNG có BLOCKING — commit được**

**S8 đạt:** diff không chạm một dòng nào ở `data/location/`, `LocationTrackingService.kt`,
`MemberRouteSource.kt` — đã kiểm bằng `git status`.

**Ranh giới sống còn: không có đường nào nối hai phía**, kể cả gián tiếp — đã soi ba tầng, xem §1.

Ba việc **nên sửa**, không cái nào chặn commit: một tham số của `shareIn` (§2), một lỗ trong phạm vi
quét của test ranh giới (§3), và một hệ quả của scrim mà không QA nào phủ (§4).

Suite sau khi tôi thêm ca: `:domain` **131** · `:data` **85** · `:ui` **120** · `:app` **1** =
**337**, xanh 100 % (trước: 331 — khớp con số bạn đưa).

---

## 1. Ranh giới *mất internet* ≠ *lỗi nhà cung cấp* — soi ba tầng

| Tầng | Có bắc cầu được không |
|---|---|
| **`:data`** | Không. `AndroidNetworkMonitor` không import gì của `routing/`; `MemberRouteSource`/`OnDevicePolylineCache` không biết `NetworkMonitor`. `InternetBlockerBoundaryTest` quét hai chiều — nhưng phạm vi quét có lỗ, xem §3 |
| **`:ui` — chỗ hai phía THỰC SỰ gặp nhau** | Không, và **đã đo**. `MapState` là nơi duy nhất trong repo giữ CẢ `routeSource` (do mã lỗi HTTP quyết) LẪN `hasInternet` (do `ConnectivityManager` quyết) — nên đây là chỗ dễ nối nhất, chỉ cần thêm một toán tử. Mutation `showNoInternetOverlay get() = !hasInternet \|\| isFallbackRoute` — đúng chế độ hỏng thảm hoạ mà D8 mô tả — làm **1 ca ĐỎ**: `provider failure while online never blocks the map`. Ca âm đó không phải trang trí, nó là thứ duy nhất chặn |
| **Koin / thứ tự khởi tạo** | Không. `single<NetworkMonitor>` và `single { MemberRouteSource(...) }` là hai định nghĩa độc lập, không cái nào `get()` cái kia. `MapViewModel` nhận cả hai nhưng mỗi `collectSafely` ghi vào một field riêng, không có `combine`, không có nhánh nào đọc chéo |

Thêm hai điểm đã kiểm: `NetworkMonitor` KDoc ghi thẳng lệnh cấm ở `:domain` (nơi cả hai bên đều
nhìn thấy), và `MapContract.showNoInternetOverlay` KDoc lặp lại nó ngay tại chỗ dễ vi phạm nhất.

---

## 2. Bản sửa `shareIn` — đúng, nhưng thiếu một tham số

**Rò rỉ: không có.** `WhileSubscribed(stopTimeoutMillis = 5_000)` huỷ upstream 5 s sau khi collector
cuối rời đi ⇒ `awaitClose` chạy ⇒ `unregisterNetworkCallback`. `scope` giữ đúng một coroutine chia
sẻ và sống bằng vòng đời tiến trình — đúng với một `single` của Koin, không phải rò.

**Nhưng `replay = 1` đang giữ giá trị CŨ lâu hơn cần thiết — nên sửa.**
`SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000)` để `replayExpirationMillis` ở mặc định
`Long.MAX_VALUE`, nghĩa là **bộ đệm replay sống sót sau khi upstream đã dừng**. Một collector mới
(vào sau > 5 s không ai đăng ký) nhận **giá trị cũ trước**, rồi vài mili-giây sau mới nhận giá trị
`readVerifiedInternet()` đọc lại. Hai chiều đều xấu, và chiều thứ hai đúng bằng thứ mà quyết định
"`hasInternet` mặc định `true`" được chọn để tránh:

| Cache còn lại | Sự thật khi quay lại màn Bản đồ | Người dùng thấy |
|---|---|---|
| `true` | đã bật chế độ máy bay | vài ms KHÔNG có lớp phủ, rồi lớp phủ hiện |
| `false` | mạng đã về | **nháy một lớp phủ KHÔNG ĐÓNG ĐƯỢC**, rồi tự biến mất |

**Bản vá đề xuất** (một tham số, không đổi hành vi nào khác):

```kotlin
.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_STOP_TIMEOUT_MS, replayExpirationMillis = 0), replay = 1)
```

`replayExpirationMillis = 0` xoá bộ đệm ngay khi upstream dừng: collector mới không nhận gì cho tới
lần đọc mới, và khoảng trống đó đã được `MapState.hasInternet = true` che đúng như thiết kế mô tả.
Chuyển tab nhanh (< 5 s) không bị ảnh hưởng — upstream chưa hề dừng nên replay vẫn hoạt động.

**Ghi chú phụ cho người đếm log ở QA-SRM-13/37:** mỗi lần upstream khởi động lại (rời màn Bản đồ
> 5 s rồi quay lại) sinh thêm **một dòng `network_state` cùng giá trị** — `distinctUntilChanged()`
nằm *upstream* của `shareIn` nên trạng thái lọc của nó reset theo. Không sai chức năng; chỉ cần biết
khi đếm dòng thay vì đếm lần đổi.

---

## 3. Phạm vi quét của `InternetBlockerBoundaryTest` có thể âm thầm hẹp lại — nên sửa

Test liệt kê **tường minh ba file**: `MemberRouteSource.kt`, `OnDevicePolylineCache.kt`,
`AndroidNetworkMonitor.kt`. Thêm một file mới vào `data/routing/` (ví dụ một provider thứ ba) hoặc
vào `data/network/` là **ra ngoài tầm quét, không ai biết**.

`RealGpsNoSnapArchitectureTest` — chính là khuôn mà test này đi theo — đã gặp đúng vấn đề đó và
được sửa ở phase-02 review bằng một ca thứ hai: *"every file under data location is classified as
real-GPS or explicitly exempt"*. Test ranh giới D8 chưa có ca tương đương.

**Bản vá đề xuất:** một ca thứ ba đối chiếu danh sách file quét với đĩa —
`data/routing/*.kt` phải nằm trong "được quét" hoặc "miễn, kèm lý do", và `data/network/*.kt` cũng
vậy. Tôi **không** tự thêm: nó đòi một danh sách miễn trừ có ý kiến (`GraphHopperRoutingProvider`,
`ValhallaRoutingProvider`, `RoutingErrorMapper`… có nên bị quét không?) và đó là quyết định của
người sở hữu ranh giới, không phải của người soát.

---

## 4. Scrim nuốt luôn `TrackingToggle` — hệ quả không QA nào phủ

`NoInternetOverlay()` là phần tử **cuối** trong `Box(weight(1f))`, nên nó nằm trên `TrackingToggle`
và `.clickable{}` của scrim nuốt mọi chạm rơi vào khung bản đồ — kể cả chạm vào công tắc theo dõi.

Với người dùng **đã bật** theo dõi thì đúng ý D8 (FR-8: theo dõi chạy tiếp phía sau, không ai tắt
nhầm được). Nhưng ca ngược lại chưa ai xét: **mở app lúc đang offline mà theo dõi đang TẮT ⇒ không
bật được cho tới khi có mạng trở lại** ⇒ suốt thời gian mất mạng **không một `location_points` nào
được ghi**. Đó đúng là cái lỗ mà D8 nêu tên khi từ chối phương án dừng mô phỏng — *"GPS không cần
mạng — dừng nó là tự tay đục một lỗ hổng thật vào tab Lịch sử"* — chỉ là tới bằng một đường khác.
Không QA-SRM nào phủ ca này (37/38/39/40 đều giả định theo dõi đã bật).

**Hai lựa chọn, chọn một:**
- đặt `NoInternetOverlay()` **trước** `TrackingToggle` trong `Box` ⇒ công tắc nổi lên trên scrim và
  vẫn bấm được (bản đồ vẫn bị chặn hoàn toàn — đó là thứ US-47 đòi, không phải "chặn cả công tắc");
- hoặc chấp nhận có chủ ý, ghi vào AC của US-47 + `LLM.md` §13 Open, để lần sau không ai coi là bug.

---

## 5. Câu hỏi "5 nguồn còn lại có trần cứng không" — **không, chỉ callback mạng có**

| Nguồn | Bản chất | Trần cứng? | Chi phí khi ViewModel nhân bản |
|---|---|---|---|
| `isTracking()` | `LocationTrackingService.isRunning` — `StateFlow` ở companion | Không | ~0, hot flow dùng chung |
| `observeLiveSelfLocation()` | `LiveSelfLocation` `StateFlow` | Không | ~0 |
| `observeSource()` | `RouteSourceAggregator` `StateFlow` + `filterNotNull()` | Không | ~0 (toán tử lạnh trên một hot flow) |
| `observeZones()` | Room `Flow` | Không | **N** observer của `InvalidationTracker` + **N** lần chạy lại truy vấn mỗi lần ghi |
| `observeMembersWithLastLocation()` | Room `Flow` | Không | như trên; bộ mô phỏng ghi ~1.1 dòng/giây nên đây là nguồn tốn nhất |
| `observeHasInternet()` | `registerDefaultNetworkCallback` | **CÓ — ~100/uid** | đã chặn bằng `shareIn` |

**Kết luận:** `NetworkMonitor` là nguồn **duy nhất** có trần cứng của hệ điều hành; bốn nguồn kia
chỉ lãng phí tuyến tính (CPU truy vấn + N bản `MapState` giữ trong RAM). Open #23 vẫn nên sửa, nhưng
nó không còn là quả bom hẹn giờ thứ hai — và `shareIn` là phòng thủ ĐÚNG CHỖ cho một nguồn đăng ký
với hệ thống, không phải bản vá cho lỗi điều hướng.

---

## 6. Bảng mutation

Harness như các lượt trước (`--continue`, xoá `build/test-results` trước mỗi lần, khôi phục +
`diff` xác nhận). Baseline **331**.

| # | Đột biến trên lớp sản phẩm | ĐỎ trước | ĐỎ sau khi tôi thêm ca |
|---|---|---|---|
| Q1 | `showNoInternetOverlay`: `!hasInternet` → `hasInternet` | **3** | 3 |
| Q6 | **Bắc cầu ranh giới:** `!hasInternet \|\| isFallbackRoute` | **1** | 1 |
| Q2 | `isVerified()`: `&&` → `\|\|` (captive portal, QA-SRM-37) | **0** ✗ | **1** |
| Q3 | Bỏ `trySend(readVerifiedInternet())` trước khi đăng ký (FR-5) | **0** ✗ | **1** |
| Q4 | Thêm `override fun onAvailable { trySend(true) }` | **0** ✗ | **1** |
| Q5 | `hasInternet` mặc định `true` → `false` | **0** ✗ | **1** |
| Q7 | Gỡ `shareIn` — chính lỗi bạn vừa sửa | **0** ✗ | **1** |
| Q8 | Gỡ `distinctUntilChanged()` | **0** ✗ | **1** |

**Đọc bảng:** hai đột biến ở `MapState` chết ngay — phần `:ui` được khoá tốt, và quan trọng nhất là
**Q6 (chế độ hỏng thảm hoạ) có ca bắt**. Sáu đột biến sống sót đều nằm trong `AndroidNetworkMonitor`
(năm cái) và ở **giá trị mặc định** của `MapState.hasInternet` (một cái) — tức toàn bộ vùng mà bạn
đã gọi tên là vùng trắng. **Q7 đáng chú ý nhất: bản sửa bạn vừa mất công đo trên emulator không có
gì giữ nó lại** — người sau "dọn dẹp" `shareIn` cho gọn thì 331/331 vẫn xanh và lỗi 5-callback quay
lại nguyên vẹn.

---

## 7. Test tôi thêm (6 ca, không chạm file sản phẩm)

**`data/src/test/.../data/network/AndroidNetworkMonitorContractTest.kt`** (mới, 5 ca) — cùng khuôn
`MapBlockerIsNotADialogTest`/`RoutingAttributionContractTest`, đọc mã nguồn vì `:data` không có
Robolectric:

| Ca | Ghim | Bắt |
|---|---|---|
| `the current state is read and emitted before the callback is registered` | FR-5, Key Insight #3 (kể cả THỨ TỰ) | Q3 |
| `verified internet requires BOTH capabilities, not either` | FR-6/QA-SRM-37, `&&` chứ không `\|\|` | Q2 |
| `onAvailable is never overridden` | Step 3c, chống nháy lớp phủ trên captive portal | Q4 |
| `emissions are de-duplicated before they are logged` | NFR-6/S11, và `distinctUntilChanged` phải nằm TRƯỚC log | Q8 |
| `the flow is shared so one callback serves every collector` | NFR-3 (+ `replay = 1`, + `awaitClose`/`unregister`) | Q7 |

**`ui/.../MapViewModelTest#a freshly built MapState never shows the blocking overlay`** (1 ca) —
ghim giá trị mặc định `hasInternet = true`: mặc định `false` làm lớp phủ **không đóng được** nháy
lên ở mọi lần mở app. Bắt Q5.

KDoc của cả hai nói rõ đây là **giải pháp cho hạn chế hạ tầng**, không phải mẫu đáng nhân rộng: có
Robolectric / `compose-ui-test` thì thay bằng ca hành vi thật rồi xoá.

---

## 8. Đã kiểm và ĐẠT

- **S8** — không chạm `data/location/`, `LocationTrackingService.kt`, `MemberRouteSource.kt`.
- **MVI** — `collectSafely` thứ sáu (không `launchIn`); `MapViewModel` không import `android.*`/
  Compose; `showNoInternetOverlay` là `val` tính toán; lớp phủ là **state**, không phải Effect, nên
  sống sót xoay màn hình (FR-3); `networkMonitor` không `private val` vì chỉ dùng trong `init`.
- **Cơ chế lớp phủ** — `NoInternetOverlay` là phần tử cuối trong `Box(weight(1f))` ⇒ nằm trên mọi
  lớp khác nhưng **không** phủ `bottomBar` (anh em ngoài `Column`) ⇒ thanh tab còn bấm được (FR-7/
  S14). `BackHandler(enabled = true) {}`, `.clickable(indication = null) {}`, không nút nào — cả ba
  đã được `MapBlockerIsNotADialogTest` ghim, gồm cả ca kiểm **vị trí** trong `MapScreen`.
- **Koin** — `single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }` viết tay, đúng lý
  do đã ghi (`verify()` phân tích tĩnh qua constructor); `KoinModulesTest` xanh, `extraTypes` không
  cần thêm.
- **Manifest** — `ACCESS_NETWORK_STATE` là quyền `normal`, không thêm bước onboarding nào.
- **G7** — dòng log duy nhất là `network_state hasInternet=true|false`: không SSID, không tên mạng,
  không toạ độ.
- **Tài liệu** — `LLM.md` §3 (package `data/network/` + 2 file), §10 (**7 → 8 quyền**, kèm lý do),
  §11 (dòng cho `InternetBlockerBoundaryTest`), §13 Open #23 (lỗi điều hướng) đều đã cập nhật trong
  cùng lần thay đổi.

## 9. Góp ý nhỏ

- `MapBlockerIsNotADialogTest#the overlay is rendered inside the map box` so khớp chuỗi nguyên văn
  `Box(modifier = Modifier.weight(1f))`. Đổi cách viết modifier (thêm `.testTag`, xuống dòng) sẽ
  làm ca đỏ giả. Đỏ giả an toàn hơn xanh giả nên tôi không đề nghị đổi — chỉ nên ghi một câu vào
  KDoc để người sau không tưởng mình vừa làm hỏng lớp phủ.
- `AndroidNetworkMonitor.scope` dùng `Dispatchers.Default`: `registerDefaultNetworkCallback` không
  đòi main looper nên hợp lệ; callback bắn trên thread của hệ thống và chỉ gọi `trySend`, không
  chạm UI. Không có vấn đề.

## 10. Việc còn lại

1. `replayExpirationMillis = 0` (§2).
2. Ca "phạm vi quét không âm thầm hẹp lại" cho `InternetBlockerBoundaryTest` (§3).
3. Quyết định về `TrackingToggle` dưới scrim (§4) — sửa vị trí hoặc ghi vào AC/§13 Open.
