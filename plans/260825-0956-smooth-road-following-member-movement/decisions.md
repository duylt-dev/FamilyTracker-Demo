# Quyết định — giải các xung đột giữa research, BA và mã nguồn

> Đây là phần "vì sao" của [`plan.md`](plan.md). Mỗi mục dưới đây là một chỗ mà hai nguồn nói
> ngược nhau. Không mục nào để ở trạng thái "sẽ điều tra sau": mỗi mục có một quyết định, lý do,
> và con số đi kèm.

---

## C1 — Độ mượt được sinh ra ở đâu

**Xung đột.** researcher-01 §F đề nghị `MEMBER_ROAM_INTERVAL_MS = 250` + `STEP_METERS = 1.5`
(4 lần ghi Room / giây / thành viên). researcher-02 đề nghị nội suy ở composable. Hai bên đang
giải **cùng một bài toán ở hai tầng khác nhau**, và quyết định đã chốt #2 của chủ dự án đã giao
việc đó cho tầng hiển thị.

**Quyết định: độ mượt sinh ra ở tầng hiển thị (D3). Nhịp lấy mẫu KHÔNG đổi (D1).**

| | Hôm nay | researcher-01 §F | **Chốt** |
|---|---|---|---|
| `MEMBER_ROAM_INTERVAL_MS` | 2 500 ms | 250 ms | **2 500 ms — không đổi** |
| Tốc độ | 20 m/s (72 km/h) | 6 m/s | **`SIM_MEMBER_SPEED_MPS` = 8.3 m/s (≈30 km/h)** |
| `STEP_METERS` | 50.0 (hằng số tự do) | 1.5 | **suy ra = 8.3 × 2.5 = 20.75 m** |
| Ghi Room / giây / thành viên | 0.4 | 4.0 | **≈0.55** (0.4 nhịp + mẫu tại đỉnh polyline, xem D2) |
| Điểm / giờ / thành viên | 1 440 | **14 400** | **≈1 980** |
| `DWELL_TICKS` | 30 | 246 | **30 — không đổi** |

**Vì sao không lấy 250 ms:**

1. **Nó không mua được gì.** Sau D3, mắt người thấy 60 khung hình/giây do composable dựng, không
   thấy nhịp lấy mẫu. Hạ nhịp lấy mẫu xuống 250 ms là trả giá lưu trữ cho một thứ đã có miễn phí.
2. **Nó nhân `DWELL_TICKS` lên 8.2×** (30 → 246) và kéo theo phải tính lại toàn bộ bài toán bất
   biến ENTER/EXIT ở §C4. Giữ 2 500 ms thì `DWELL_TICKS` không đổi một chữ và bất biến giữ
   nguyên căn cứ đã đo (LLM.md §8.1: ENTER→ENTER ~90s ở zone 150m).
3. **14 400 điểm/giờ/thành viên** vượt quy mô thiết kế PRD §7.1 (8 640 điểm cho **cả ngày**) ngay
   trong 36 phút. LLM.md §13 Fixed #16 ghi `PolyUtil.simplify` khoá main thread **570–650 ms** đo
   thật ở đúng quy mô 8 640 điểm.

**Đính chính một lập luận, để không ai dựa vào nó sai:** tab Lịch sử **chỉ vẽ lộ trình của self**
(`HistoryViewModel` tự nạp `memberId` của self qua `ObserveMembersWithLastLocationUseCase`). Điểm
của Minh/Lan **không** đi vào polyline Lịch sử, nên hồi quy 570–650 ms không nổ trực tiếp từ đây.
Cái thật sự phình là bảng `location_points`, chỉ mục `(memberId, recordedAt)`,
`latestPerMember()` chạy mỗi lần phát, và khối lượng `PurgeOldHistoryUseCase` phải xoá mỗi lần
khởi động (lưu trữ 7 ngày). Ở 250 ms và 3 thành viên chạy liên tục 8 tiếng/ngày × 7 ngày:
**2.4 triệu dòng**. Ở nhịp chốt: **330 nghìn dòng**. Cả hai con số đều đủ để nói rằng lựa chọn này
không phải chuyện thẩm mỹ.

**Vì sao phải tách nhịp lấy mẫu khỏi độ mượt được — mà không cần cơ chế "ghi mỗi N bước":**
`PolylineFollower` (D2) đảm bảo hai mẫu liên tiếp **luôn nằm trên cùng một đoạn thẳng của
polyline**, nên nội suy tuyến tính ở tầng hiển thị giữa chúng cũng nằm đúng trên đường. Không cần
bước đi mịn trong bộ nhớ, không cần bộ đệm, không cần luật "ghi mỗi N". Một tầng ít hơn.

---

## C2 — Nửa "ngoại tuyến" của quyết định #1 có bị chặn về pháp lý không

**Xung đột.** Quyết định #1 nói *Hybrid: GraphHopper live khi có khoá + mạng, còn lại đi đường
ngoại tuyến*. Nhưng: (a) LLM.md §13 Open #11 — điều khoản redistribution của GraphHopper chưa được
làm rõ, nên **không** được commit polyline của họ vào `assets/`; (b) researcher-01 §E chuyển sang
"fetch-once, cache trên máy", mà bản clone mới / CI **không có khoá** (§13 Open #10) nên không có
gì để fetch và không có gì trong cache — đúng lúc cần fallback nhất thì fallback rỗng.

**Quyết định (D5): nguồn đường 3 tầng, tầng 3 là vòng TỰ SINH, không phải file tải sẵn.**

| Tầng | Nguồn | Dữ liệu OSM? | Ghi công | Khi nào |
|---|---|---|---|---|
| 1 | `RoutingProvider.directions()` (GraphHopper) | Có | `Directions.attribution` → hiện | Có khoá + có mạng |
| 2 | Cache trên máy (`filesDir/routes/`), ghi lại nguyên văn kết quả tầng 1 kèm attribution | Có | Như tầng 1 | Đã fetch thành công ít nhất một lần |
| 3 | `SyntheticPath` — hàm **thuần** ở `:domain/tracking/`, dựng một đường cong nhẹ giữa hai đầu của chặng bằng lượng giác | **Không** | Nhãn "đường thẳng ước tính", **không** credit OSM | Mọi trường hợp còn lại **mà máy vẫn có internet** — xem D8 bên dưới: mất internet không rơi xuống đây nữa, nó bị chặn trước |

**App làm gì trên một bản clone mới không có khoá:** chạy **tầng 3**. Thành viên đi mượt trên đường
cong tổng hợp của từng chặng (vào zone → dwell → ra khỏi zone → zone khác); bất biến ENTER/EXIT giữ
nguyên và còn *dễ* kiểm hơn đường thật vì đường tổng hợp là tất định; dải ghi công hiện trạng thái
"ước tính", **không** hiện credit OSM. Không màn hình lỗi, không toast, không dialog. Đây là hành vi
nghiệm thu của QA-SRM-14 và QA-SRM-32.

**US-41 nói thẳng ở đây:** ở tầng 3, thành viên **không** đi trên đường thật. US-41 chỉ đạt ở tầng
1 và 2. Đây là giới hạn có chủ ý, phải vào `LLM.md` §13 Open ở phase 04 — không được để người đọc
sau tưởng là lỗi.

### ✅ Xác nhận của chủ dự án — 2026-08-25 → D8: mất internet thì CHẶN, không hạ cấp

**Đây là chỗ C2 bị lật một nửa.** Bản C2 ở trên — và US-45 bản đầu — giả định: mất mạng thì im
lặng tụt xuống tầng dưới, người dùng không cần biết. Chủ dự án bác chính giả định đó.

**D8 — mất internet ⇒ màn Bản đồ bị chặn bằng dialog, không hạ cấp im lặng.**

| Điểm | Chốt |
|---|---|
| Kích hoạt | Mạng **không có internet đã kiểm chứng**: `NetworkCapabilities` thiếu `NET_CAPABILITY_INTERNET` **hoặc** thiếu `NET_CAPABILITY_VALIDATED`. Bắt được cả ca wifi đầy vạch nhưng chưa qua captive portal |
| Hành vi | Dialog "mất mạng", `cancelable = false` — không nút đóng, chạm ra ngoài không đóng, nút Back không đóng |
| Phạm vi | **Chỉ màn Bản đồ.** Zone, Lịch sử, Cài đặt vẫn dùng bình thường — chúng đọc Room, không gọi mạng |
| Tự tắt | Có internet đã kiểm chứng trở lại ⇒ dialog **tự đóng**, không cần thao tác nào |
| Theo dõi GPS thật | **Vẫn chạy nền** trong lúc dialog hiện: foreground service, ghi `location_points`, đánh giá ENTER/EXIT. GPS không cần mạng — dừng nó là tự tay đục một lỗ hổng thật vào tab Lịch sử |
| **Không** áp dụng cho | **Lỗi nhà cung cấp khi vẫn có internet**: thiếu khoá, 401, 429, 400, timeout. Ca đó giữ nguyên hạ cấp im lặng xuống tầng 3 (QA-SRM-14, QA-SRM-15) |

**Ranh giới phải giữ, gộp vào là hỏng demo:** *mất internet* ≠ *lỗi nhà cung cấp*. Nếu để 401 cũng
bật dialog "mất mạng" thì dialog đó **không bao giờ tự tắt được** — điều kiện tắt là có internet,
mà internet vẫn đang có. Người demo kẹt trong một hộp thoại không đóng được, trên một máy wifi đầy
vạch. Đây là chế độ hỏng tệ nhất D8 có thể sinh ra, và nó chỉ tránh được bằng cách cho hai đường
dùng hai nguồn tín hiệu khác nhau: `ConnectivityManager` quyết định dialog, mã lỗi HTTP quyết định
chọn tầng. Không chỗ nào được đọc chéo.

**Suy ra từ "theo dõi vẫn chạy nền" — mô phỏng cũng không dừng:** `MemberMovementSimulator` chạy
tiếp phía sau dialog. Nếu dừng, lúc dialog tự tắt thành viên sẽ **nhảy** một quãng đúng bằng thời
gian mất mạng, vượt `MEMBER_RENDER_MAX_JUMP_M` và trượt QA-SRM-05. Đây là suy ra, không phải bạn
nói — muốn dừng hẳn mô phỏng thì nói một câu, nhưng khi đó phải chốt luôn cách xử lý cú nhảy.

**Cái D8 KHÔNG lật — giới hạn tầng 3 vẫn được chấp nhận như đã xác nhận.** Tầng 3 vẫn sống, chỉ
đổi khách hàng: nó không còn phục vụ ca ngoại tuyến (D8 chặn trước khi tới) mà phục vụ ba ca **vẫn
có internet** — không có khoá, nhà cung cấp trả lỗi, tuyến bị `RouteGeometryGuard` từ chối. US-41
vẫn chỉ đạt ở tầng 1/2; marker cắt qua nhà ở tầng 3 vẫn không phải lỗi.

**Tầng 2 (cache) đổi lý do tồn tại — ghi lại kẻo sau này có người xoá nhầm cho gọn.** Nó sinh ra
để phục vụ nửa ngoại tuyến; D8 lấy mất việc đó. Cái còn lại, và vẫn đủ để giữ nó: tiết kiệm hạn
ngạch 500 credit/ngày (QA-SRM-36) và bỏ độ trễ mạng ở mỗi chặng mới.

**Những thứ phải viết lại vì D8 — đã làm trong cùng lần ghi này, không để trôi sang sau:**

| Chỗ | Trước | Sau |
|---|---|---|
| PRD delta US-45 | "mất mạng **hoặc** dịch vụ tuyến không dùng được ⇒ vẫn thấy thành viên đi mượt" | Thu về đúng nửa còn đúng: dịch vụ tuyến hỏng **trong khi vẫn có internet** |
| PRD delta US-47 | — | **MỚI** — mất internet ⇒ chặn màn Bản đồ + dialog tự tắt khi mạng về |
| QA-SRM-13 | Máy bay ⇒ vẫn di chuyển | Máy bay ⇒ dialog `cancelable=false`, màn Bản đồ bị chặn (nâng lên P0) |
| QA-SRM-17 | Có mạng lại ⇒ chuyển nguồn êm | Có mạng lại ⇒ dialog **tự** tắt, và không cú nhảy nào khi màn hiện lại |
| QA-SRM-37→40 | — | **MỚI** — captive portal, theo dõi vẫn chạy nền, màn khác không bị chặn, và ca âm 401-không-hiện-dialog |
| UAT-04 | "Mất mạng, app vẫn chạy như thường" | "Mất mạng, app nói thẳng" |

**Chưa code — bốn việc phải làm khi bắt đầu:**

1. `app/src/main/AndroidManifest.xml` thêm `ACCESS_NETWORK_STATE`. Hiện **chỉ có** `INTERNET`.
   Quyền normal, không hỏi runtime.
2. Nguồn trạng thái mạng quan sát được **chưa tồn tại** — không một chỗ nào trong repo dùng
   `ConnectivityManager` hay `NetworkCallback`. Đặt theo LLM.md §2: interface ở `:domain`,
   `NetworkCallback` ở `:data`, `MapViewModel` tiêu thụ.
3. **Là state, không phải Effect** (MVI doc §1): dialog phản ánh một điều kiện đang kéo dài và phải
   sống sót qua xoay màn hình ⇒ một cờ trong `MapState`. Effect là cho việc xảy ra đúng một lần.
4. **Đã xếp phase** — [`phase-07-chan-man-ban-do-khi-mat-internet.md`](phase-07-chan-man-ban-do-khi-mat-internet.md),
   chạy sau phase 05 và trước phase 06. D8 chạm manifest + `:domain` + `:data` + `:ui`, không nằm
   trong 6 phase ban đầu; không nhét vào phase 05, phase đó chỉ chạm dải ghi công.

**Vì sao KHÔNG chọn ba phương án còn lại:**

- **Polyline tự vẽ tay từ OSM thô / lấy một lần từ Valhalla-FOSSGIS rồi commit vào `assets/`** —
  hợp pháp (polyline tuyến đường là *Produced Work*, chỉ phát sinh nghĩa vụ attribution;
  `docs/routing-and-map-attribution.md` §2), nhưng **vô dụng ở đây**: nó chỉ đúng tại toạ độ của
  chính nó, còn zone thì người dùng tạo ở vị trí GPS của máy lúc demo (emulator: Mountain View;
  máy thật: bất kỳ đâu). Một vòng HCMC đóng sẵn không giúp gì cho một zone ở Mountain View. Muốn
  đúng ở mọi nơi thì phải đóng gói cả mạng lưới đường — ngoài phạm vi tuyệt đối.
  **Ghi lại như một lựa chọn đã cân nhắc và cố ý không làm:** nếu sau này buổi demo được chốt cứng
  ở MỘT toạ độ, nâng tầng 3 lên "vòng OSM đóng sẵn cho vùng đó" là một thay đổi nhỏ và khi đó
  credit OSM phải bật lại.
- **Hạ cấp về đường chim bay hiện tại** — vi phạm AC của US-45 ("không quay về đường chim bay
  xuyên nhà") và tái tạo đúng khuyết tật D1.
- **Tắt hẳn tính năng khi không có polyline** — vi phạm AC của US-45 ("thành viên **không** đứng yên").

**Hệ quả về chặn phát hành, nói thẳng:** dòng ⬜ ở `docs/routing-and-map-attribution.md` §5 **đã**
chặn phát hành từ trước plan này (nó chặn màn Dẫn đường). Bật tầng 1/2 làm tính năng này **thừa
hưởng** đúng cái chặn đó, **không tạo thêm chặn mới**. Phase 01–03 và 06 không chạm dữ liệu nhà
cung cấp nên không thừa hưởng gì.

---

## C3 — `MAX_ACCURACY_M`: nới ngưỡng hay tách đường hiển thị

**Xung đột.** researcher-03 §B khuyên nới/phân tầng ngưỡng (B2). BA khuyên giữ 50 cho việc **ghi**
và gỡ nó khỏi việc **vẽ**.

**Quyết định (D4): giữ `MAX_ACCURACY_M = 50.0`, không đổi một dòng nào của `LocationFilter`.
Thêm một cổng hiển thị riêng, không đi qua bộ lọc.**

```
FusedLocationSource.stream()
        │
        ▼
LocationPointProcessor.process(point)
        ├── LUÔN LUÔN → LiveSelfLocation.publish(point)   ← MỚI: nuôi chấm xanh (US-06/43)
        └── chỉ khi Accept → trackingRepository.record()   ← KHÔNG ĐỔI: location_points, Lịch sử (US-31)
```

**Đối chiếu với hai chế độ hỏng đã xác nhận:**

| | Chế độ (a) — mở app trong nhà, chưa từng có fix tốt | Chế độ (b) — đi bộ trong nhà sau khi đã có fix ngoài trời |
|---|---|---|
| Hôm nay | `lastLocation == null` → `FamilyTrackerMap` không vẽ chấm xanh; thành viên khác trượt `MemberMarkers.kt:43` | Chấm xanh **đứng im ở vị trí ngoài trời cuối cùng**, trông vẫn "live" — tệ hơn (a) vì nó nói dối |
| Sau D4 | Fix đầu tiên (dù `accuracy = 80m`) lên `LiveSelfLocation` → chấm xanh vẽ ngay, kèm vòng sai số 80m | Mọi fix đều lên `LiveSelfLocation` → chấm đi theo bước chân; vòng sai số phình ra nói cho người dùng biết độ tin cậy |
| Sau B2 của researcher-03 | Cũng vẽ được | Cũng đi theo được |

**Vì sao không lấy B2 dù nó cũng chữa được cả hai chế độ:**

| Tiêu chí | D4 (chốt) | B2 (nới/phân tầng ngưỡng) |
|---|---|---|
| Khối lượng `location_points` | **0 thay đổi** | Tăng: mọi fix 50–150m trong nhà nay được ghi. Đứng yên trong nhà 1 giờ ở nhịp 10s ≈ +360 điểm/giờ (luật `MIN_DISTANCE_M` = 10m KHÔNG chặn được, vì nhiễu 80m tự nó nhảy > 10m mỗi lần) |
| Polyline tab Lịch sử | **Không đổi.** US-31 giữ nguyên nguyên văn | **Vỡ US-31**: "điểm có `accuracy > 50m` bị loại trước khi vẽ" không còn đúng. Polyline nhảy qua cả khu phố — đúng lý do luật này tồn tại (LLM.md §8.3) |
| ENTER/EXIT | **0 ảnh hưởng** | 0 ảnh hưởng (xem C-D6 dưới) |
| Số dòng sửa | ~40 (1 lớp mới 15 dòng + 1 method cổng + state) | ~20, nhưng thêm một heuristic "đoán đang ở trong nhà" phải tự bảo trì |
| Rủi ro | Thấp — không đường dữ liệu cũ nào đổi hành vi | Đổi hành vi của một luật đang được 2 test khoá và 1 user story mô tả |

**D6 kèm theo — bỏ hẳn khuyến nghị §F của researcher-03 (gating độ chính xác trong `ZoneEvaluator`).**
Kịch bản spam ENTER/EXIT mà §F mô tả **không tồn tại được trong kiến trúc hiện tại**: từ
fix-zone-follows-members (LLM.md §8.1), `ZoneEvaluator` chỉ được gọi từ `MemberMovementSimulator`
cho thành viên `!isSelf`, với điểm mô phỏng `accuracy = 8f`; `ObserveZoneMembershipUseCase` lọc
`!isSelf` trước khi hỏi. **Không một điểm GPS thật nào chạm `ZoneEvaluator`.** Thêm gating là thêm
code chết cho một lỗi không thể xảy ra — và một hằng số nữa vào `TrackingConstants` đang đã lệch
chuẩn truy nguyên (§13 Open #7). Ghi lại: nếu ngày nào self quay lại làm chủ thể zone, §F phải
được đọc lại **trước** khi làm.

---

## C4 — Bất biến ENTER/EXIT phải sống sót

`MemberRoamerTest` (`domain/src/test/kotlin/.../tracking/MemberRoamerTest.kt:96-117`) khoá
"mỗi vòng đúng một ENTER rồi đúng một EXIT, xen kẽ, không dội". LLM.md §11 gọi nó là *lời hứa duy
nhất cả tính năng dựa vào*.

**Ba thứ trong plan này đụng vào nó, và đây là cách từng thứ được giữ:**

| Thay đổi | Ảnh hưởng lên bất biến | Cách giữ | Kiểm ở |
|---|---|---|---|
| Hạ tốc độ 20 → 8.3 m/s | Quãng đường mỗi nhịp giảm 2.4× ⇒ số nhịp mỗi chặng tăng 2.4× ⇒ **vòng dài ra**, cửa sổ khử trùng lặp càng dư | `DWELL_TICKS` **không đổi** (30 nhịp × 2 500 ms = 75 s > `EVENT_DEDUPE_WINDOW_MS` 60 s). Dwell một mình đã vượt cửa sổ; đi lại chỉ cộng thêm | QA-SRM-27, phase-02 |
| Bám polyline thay vì đường thẳng | Đường vòng vèo có thể **cắt ranh giới zone nhiều hơn một lần mỗi chặng** → dội | `RouteGeometryGuard.isUsable(points, zone)` chạy **trước** khi nhận một tuyến: đếm số lần dãy điểm cắt `d = radius` và số lần cắt `d = radius + ZONE_EXIT_BUFFER_M`; > 1 lần mỗi chiều thì **từ chối tuyến** và rơi về `SyntheticPath` | QA-SRM-25/26/28, phase-02 |
| Spawn một lần thay vì dời mỗi nhịp | Sau spawn, một zone mới tạo cách 13 000 km sẽ không bao giờ tới được → thành viên đi mãi, không ENTER nào | Sau spawn, đích xa hơn `MAX_WALK_M` **không** kích hoạt dời nữa mà bị **thay bằng đích đi loanh quanh**; luật ghi trong KDoc `RoamState.hasSpawned` | QA-SRM-09/11/12, phase-02 |

**Lịch kiểm bắt buộc:** `MemberRoamerTest` chạy lại ở **cuối mỗi phase 02 và 04**, và một lần nữa
ở phase 06 với đúng bộ hằng số cuối cùng. Đỏ ở bất kỳ lần nào thì phase đó **không đóng**.

---

## C5 — Thời gian một vòng: phải ĐO, không được ngoại suy

**Con số duy nhất có thật:** 63 giây từ lúc bật theo dõi tới `zone_event_raised type=ENTER` cho
Minh và Lan, `emulator-5554`, zone 150m, ở 20 m/s (LLM.md §13 Fixed #23 / §8.1).

**Ngoại suy thẳng ra ~150 s là sai** vì bám đường làm quãng đường dài hơn đường chim bay (hệ số
vòng vèo đô thị điển hình 1.2–1.4×), và spawn-một-lần đổi luôn điểm xuất phát.

**Cách đo, hai lớp — chi tiết ở [phase-06](phase-06-do-luong-gate-va-tai-lieu.md):**

1. **Tất định, không cần máy** — `MemberRoamerLapTimeTest` (JUnit thuần): chạy roamer qua
   `ZoneEvaluator` thật, đếm số nhịp giữa hai `ENTER` liên tiếp, nhân `MEMBER_ROAM_INTERVAL_MS`.
   Đây là **cận dưới** và nó tất định nên gắn được vào CI.
2. **Thật, trên `emulator-5554`** — bật theo dõi, `adb logcat -s FTD_EVENT`, lấy dấu thời gian
   giữa `zone_event_raised type=ENTER` và `type=EXIT` kế tiếp, và giữa hai `ENTER` liên tiếp, cho
   cả Minh và Lan. Đối chiếu với 63 s.

**Luật chốt số, không phải "xem rồi tính":**

| Kết quả đo (ENTER → EXIT, đo thật) | Hành động |
|---|---|
| ≤ 180 s | Giữ `SIM_MEMBER_SPEED_MPS = 8.3`. Đóng B4 | ← **ĐÃ ÁP DỤNG 2026-08-26: đo được 120,0 s (zone 150 m, cả hai nguồn tuyến). B4 đóng, hằng số giữ nguyên 8.3.** |
| 180–260 s | Nâng lên `11.1` (40 km/h), đo lại một lần |
| > 260 s | Nâng lên `13.9` (50 km/h) — **trần cứng**, đo lại. Vẫn > 260 s thì vấn đề nằm ở hình học tuyến, không ở tốc độ: giảm `LEAVE_MARGIN_M` từ 120 xuống 60 (vẫn > `ZONE_EXIT_BUFFER_M` = 30) và ghi vào §13 |

Không được vượt 13.9 m/s: trên mức đó là quay lại đúng cảm giác "xe chạy trên phố như trên cao
tốc" mà thay đổi này sinh ra để sửa.

---

## Câu trả lời cho các câu hỏi treo của research và BA

| Nguồn | Câu hỏi | Trả lời |
|---|---|---|
| PRD Q8 | Đóng gói tuyến của nhà cung cấp vào app có được không? | **Không cần trả lời để làm.** D5 tầng 3 tự sinh, không chứa dữ liệu nhà cung cấp. Thư hỏi vẫn nên gửi vì nó chặn phát hành của màn Dẫn đường |
| PRD Q9 | Có vẽ polyline tuyến lên màn Bản đồ không? | **Không vẽ.** Nhưng **vẫn hiện ghi công** khi đang chạy tầng 1/2 — vị trí marker mà người dùng nhìn thấy là *Produced Work* suy ra từ dữ liệu OSM. Rẻ (dùng lại `RoutingAttribution.kt`) và đóng luôn vùng xám |
| PRD Q10 | Tốc độ bao nhiêu? | 8.3 m/s để bắt đầu; luật đổi số ở C5 |
| PRD Q11 | Bám đường có mở rộng sang F5 không? | Không. `RouteBlueprint` không đổi một dòng |
| PRD Q12 | Hiện độ chính xác thấp thế nào? | Vòng sai số (`Circle` bán kính = `accuracyMeters`), không nhãn chữ ⇒ không chuỗi mới trong `strings.xml` |
| PRD Q13 | Ba thành viên ba tuyến khác nhau? | Có — `randomFor(member)` đã gieo riêng theo `member.id`, giữ nguyên. Hạn ngạch được chặn bằng bearing tất định (phase-04) |
| PRD Q14 | Backfill user story màn Dẫn đường? | Ngoài phạm vi. BA sở hữu PRD |
| r-01 Q1 | Làm Valhalla trong cùng PR? | Không. `RoutingProvider` đã trừu tượng đủ; đổi engine là đổi cấu hình |
| r-01 Q2 | Marker xoay theo bearing hay chỉ cần mượt vị trí? | **Xoay** — US-40 AC đòi. Chỉ marker thành viên; chấm xanh self là hình tròn nên xoay vô nghĩa (phase-03) |
| r-01 Q3 | Vô hiệu cache tuyến khi nào? | Khoá cache = `zoneId + toạ độ tâm làm tròn 5 chữ số + bán kính`. Sửa zone ⇒ khoá đổi ⇒ tự miss (phase-04) |
| r-01 Q4/Q5 | Thứ tự nhiều zone, versioning DTO cache | YAGNI. `nextTarget` chọn zone ngẫu nhiên như hôm nay; cache có `schemaVersion: Int`, đọc sai version thì xoá file |
| r-02 Q1/Q2 | Ngưỡng và cách hiển thị "cũ" (stale) | **Không làm** — D7 |
| r-02 Q3 | Thời lượng nội suy cố định hay theo tốc độ? | **Suy ra từ dữ liệu**: hiệu `recordedAt` của hai mẫu gần nhất, chặn trên bằng 5 s. Không thêm hằng số |
| r-02 Q4 | Tạo `compose-stability.conf` bây giờ? | Không — §13 Fixed #20 đã chốt dứt điểm. Kiến trúc phase-03 né bằng cách chỉ giữ primitive trong state animation |
| r-02 Q5 | 72 km/h là số cuối hay tạm? | Tạm. Xem C1/C5 |
| r-02 Q6 | `member.id` có đổi giữa phiên không? | Không — `DemoDataSeeder` gieo id một lần lúc cài; `remember` khoá theo id là an toàn |
| r-03 Q1 | B1/B2/B3 cho `MAX_ACCURACY_M`? | Không cái nào — xem C3 |
| r-03 Q2 | Vòng sai số cho mọi thành viên hay chỉ điểm kém? | **Chỉ self, và chỉ khi `accuracyMeters > MAX_ACCURACY_M`.** Điểm mô phỏng luôn 8m ⇒ vòng vô nghĩa; self ngoài trời 5m ⇒ vòng nhỏ tới mức nhiễu thị giác |
| r-03 Q3 | Ẩn hay xám điểm cũ? | Không làm — D7 |
| r-03 Q4 | Viết test trước hay sau khi sửa? | **Trước.** Phase-01 Implementation Step 1 là viết test đỏ |
| r-03 Q5 | Có nâng `ZONE_RADIUS_MIN_M` lên 100m? | Không. D4 không đổi cái gì được ghi, nên tương quan bán kính/độ chính xác không đổi so với hôm nay |

---

## Sai lệch phát hiện trong chính research, ghi lại để không ai dựa vào

1. **researcher-02 §C mô tả `flat` ngược.** Nó viết "`flat = false` ⇒ marker xoay THEO bản đồ".
   Thực tế Maps SDK: `flat = false` là billboard (marker dựng đứng theo màn hình, không nghiêng
   không xoay theo camera); `flat = true` là dán phẳng lên mặt bản đồ nên xoay/nghiêng theo camera
   — đó mới là thứ cần cho một mũi chỉ hướng. Phase-03 dùng `flat = true` và **bắt buộc xác nhận
   bằng mắt trên thiết bị** trước khi đóng phase.
2. **researcher-01 §D.3 chép sai `DWELL_TICKS`** ("= 30, KDoc: supra-safe") rồi lại tính lap dựa
   trên tốc độ đi bộ 1.4 m/s trong khi code chạy 20 m/s. Con số lap 4.7 phút trong đó **không dùng
   được**. Nguồn thật duy nhất là 63 s ở §13 Fixed #23 — xem C5.
3. **researcher-01 §F.4 ghi "`LocationFilter` cumulates sub-10m steps"** — sai. `LocationFilter`
   **không** đi vào đường mô phỏng (`MemberMovementSimulator.kt:35-38`), nên `MIN_DISTANCE_M`
   không hề chặn số ghi của thành viên mô phỏng. Đó là lý do bảng ghi Room ở C1 tính thẳng theo
   nhịp, không trừ gì.
4. **researcher-01 §A (round_trip) không cần tới.** Mô hình chặng của `MemberRoamer` là điểm→điểm,
   khớp đúng chữ ký `RoutingProvider.directions(from, to)` đang có. Bỏ toàn bộ §A giảm một tham số
   API, một nhánh lỗi, và một thứ phải test.
5. **Ba nơi ghi cứng `bearingDegrees = 0f`**, không phải một: `MemberMovementSimulator.kt:98`,
   `SimulatedLocationSource.kt:62`, `DemoDataSeeder.kt:39`. Plan này chỉ sửa nơi đầu tiên (thành
   viên mô phỏng, nơi duy nhất cần xoay marker); hai nơi còn lại phục vụ self — chấm tròn, không
   xoay — nên giữ `0f` và **ghi chú lý do vào KDoc** thay vì để người sau tưởng là sót.
