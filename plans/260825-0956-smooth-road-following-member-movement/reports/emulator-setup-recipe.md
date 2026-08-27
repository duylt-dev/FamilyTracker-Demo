# Công thức dựng môi trường nghiệm thu trên emulator

Dùng cho phase 04/05/06/07. Ba cái bẫy dưới đây đã làm mất thời gian một lần — đừng vấp lại.

## Bẫy 1 — `adb emu geo fix` chỉ ăn KHI ĐANG CÓ location request

App đọc vị trí qua `FusedLocationProviderClient` (`FusedLocationSource`, Koin qualifier
`named("fused")`). Trên ảnh `sdk_gphone*`, `adb emu geo fix <lon> <lat>` gửi lúc app **chưa** bật
theo dõi thì **không có tác dụng gì** — self vẫn báo Mountain View (vị trí mặc định của AVD), gửi
lại 8 lần cũng vậy.

Nó **có** ăn sau khi công tắc "Theo dõi gia đình" đã bật: lúc đó có một location request đang sống,
và fix kế tiếp được giao vào Fused. Xác nhận bằng mắt — chấm xanh self nhảy từ Googleplex về đúng
toạ độ đã gửi.

**Hệ quả thực hành:** đừng kết luận `geo fix` hỏng khi thử lúc chưa bật theo dõi. Thứ tự đúng là
bật theo dõi trước, gửi `geo fix` sau — nhưng xem Bẫy 2, vì thứ tự đó lại phá camera.

## Bẫy 2 — camera bám self, mà self thì ở Mountain View

`MapContract.kt:49`:

```kotlin
val initialCameraTarget get() = selfLocation?.lastLocation
    ?: memberLocations.firstNotNullOfOrNull { it.lastLocation }
```

Self được ưu tiên. Một khi theo dõi đã bật, `FusedLocationSource` ghi điểm Mountain View cho self ⇒
`initialCameraTarget` = Mountain View ⇒ camera bay khỏi HCMC, nơi Minh/Lan đang đi. Đây đúng là
triệu chứng reviewer phase-03 ghi lại ("Self ở HN, camera ở HCMC") — S4 của phase 03 trượt nghiệm
thu vì nó.

**Cách né:** mở app **TRƯỚC KHI** bật theo dõi. Lúc đó self chưa có `lastLocation` nào, nhánh `?:`
rơi về thành viên đầu tiên = HCMC. Camera canh xong thì `hasCenteredOnce = true` giữ nguyên khung
kể cả khi self bắt đầu ghi điểm Mountain View sau đó.

## Bẫy 3 — `DemoDataSeeder` KHÔNG seed zone nào

Nó chỉ seed 1 self + 2 thành viên (Minh, Lan) quanh `DEMO_CENTER` = (10.7769, 106.7009).
**Không có zone.** Mà `MemberRoamer` không có zone thì mọi chặng là `WANDER`, và `WANDER` đi thẳng
`SyntheticPath`, **không bao giờ chạm `MemberRouteSource`**.

**Hệ quả:** không tạo zone bằng tay thì tầng 1/2 không bao giờ chạy, và mọi ca nghiệm thu
QA-SRM-04/14/15/36 đều vô nghĩa — log sẽ im lặng chứ không báo lỗi gì.

Cũng lưu ý: plan nói "3 thành viên", nhưng `MemberMovementSimulator` lọc `filterNot { it.isSelf }`
nên chỉ **2 thành viên** thật sự di chuyển. Trần request là `2 × zone × 2`, không phải `3 × zone × 2`.

## Bẫy 4 — toạ độ công tắc DI CHUYỂN sau phase 05, và `input tap` trượt thì im lặng

`input tap` không báo lỗi khi chạm vào chỗ trống. Triệu chứng duy nhất là
`grep -c "tracking_toggled enabled=true"` ra **0** — trông hệt như app không phản hồi.

Hai nguồn sai toạ độ, đã dính cả hai:

1. **Ước lượng từ ảnh chụp bị thu nhỏ** — lệch ~90px. Ảnh `screencap` là 1344×2992; đọc toạ độ
   trên bản đã resize để xem rồi nhân lại là sai. **Luôn lấy bounds bằng `uiautomator dump`.**
2. **Dải ghi công của phase 05 đẩy cả bố cục lên** — bản đồ nằm trong `Box(weight(1f))`, nên khi
   dải hiện, mọi thứ neo `BottomEnd` trong khung bản đồ dịch lên đúng chiều cao dải.

| Trạng thái dải ghi công | bounds công tắc | tâm để chạm |
|---|---|---|
| ẩn (chưa nguồn nào được publish — máy vừa `pm clear`) | `[1092,2464][1248,2608]` | **(1170, 2536)** |
| hiện (đã có `sim_route_loaded` bất kỳ tầng nào) | `[1092,2368][1248,2512]` | **(1170, 2440)** |

Dải **không tự ẩn lại** khi tắt theo dõi: `RouteSourceAggregator` giữ aggregate cuối có chủ ý
(vẫn ghi công cho marker đang hiển thị). Nên sau lần bật theo dõi ĐẦU TIÊN, toạ độ đúng là 2440
cho tới khi `pm clear`.

**Cách làm đúng, không phải nhớ số:**

```bash
adb -s $EMU shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb -s $EMU shell cat /sdcard/ui.xml | tr '<' '\n<' | grep 'checkable="true"' \
  | grep -oE 'checked="[a-z]+"|bounds="[^"]*"'
```

Cho ra cả trạng thái hiện tại lẫn bounds. Chạm xong thì dump lại và đọc `checked=` để **xác nhận**,
đừng cho rằng cú chạm đã ăn.

## Bẫy 5 — bật/tắt chế độ máy bay (phase 07)

`adb shell cmd connectivity airplane-mode enable|disable` chạy được trên ảnh API 37, **không cần
root**, không in gì khi thành công. Kiểm bằng `settings get global airplane_mode_on`.

Đo thật 2026-08-26: tắt máy bay xong, `ping graphhopper.com` thông lại trong **≤ 5 giây** — thoải
mái dưới ngân sách ≤ 10 s của FR-4, nên phép đo QA-SRM-17 không bị thời gian phục hồi của emulator
ăn mất. **Nhưng đừng dùng ping làm mốc:** lớp phủ đóng theo `NET_CAPABILITY_VALIDATED`, mà hệ thống
xác thực (kiểm captive portal) xong sau khi đường mạng đã thông. Đọc mốc bằng dấu thời gian dòng
`network_state hasInternet=true` trong `FTD_EVENT`, không bằng ping.

## Quy trình (chạy từ đầu, ~2 phút)

```bash
EMU=emulator-5554
PKG=com.example.pion.family.tracker.demo

# 1. Xoá sạch dữ liệu và cấp lại quyền (pm clear thu hồi hết quyền runtime)
adb -s $EMU shell pm clear $PKG
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do
  adb -s $EMU shell pm grant $PKG android.permission.$p
done

# 2. Mở app — CHƯA bật theo dõi. Camera tự canh về HCMC.
adb -s $EMU shell am start -n $PKG/.MainActivity
sleep 8

# 3. Tạo ZoneA (long-press = swipe cùng toạ độ, 900ms)
adb -s $EMU shell input swipe 898 1796 898 1796 900 ; sleep 3
adb -s $EMU shell input tap 671 1284      # ô "Tên zone"
adb -s $EMU shell input text "ZoneA"
adb -s $EMU shell input keyevent 111      # ESC, đóng bàn phím
adb -s $EMU shell input tap 1243 254      # nút "Lưu"
sleep 3

# 4. Tạo ZoneB
adb -s $EMU shell input swipe 599 1047 599 1047 900 ; sleep 3
adb -s $EMU shell input tap 671 1284
adb -s $EMU shell input text "ZoneB"
adb -s $EMU shell input keyevent 111
adb -s $EMU shell input tap 1243 254
sleep 3

# 5. Bật theo dõi (chỉ SAU khi camera đã canh xong)
adb -s $EMU shell input tap 1168 2534
```

Toạ độ tap tính cho khung **1344×2992** (Pixel_10_Pro_XL). Ảnh chụp `screencap` trả đúng kích thước
đó; nếu đọc ảnh ở kích thước hiển thị nhỏ hơn thì nhân lại trước khi tap.

Cả hai zone để mặc định **bán kính 150 m**, bật cả "Thông báo khi vào" lẫn "Thông báo khi rời".

## Ghi chú giữ lại được qua lần cài lại

`adb install -r` **giữ nguyên dữ liệu app** — zone vừa tạo sống sót qua mọi lần build lại. Chỉ
`pm clear` mới xoá. Nên dựng zone một lần rồi build lại thoải mái.

## Sai lệch so với văn bản QA

Thanh tab thật có **4 mục: Bản đồ / Zone / Lịch sử / Nhật ký**. **Không có tab "Cài đặt"** như
QA-SRM-39 và phase-07 FR-7 liệt kê. Ca đó phải đọc lại là "3 tab còn lại", hoặc màn Cài đặt phải
được tìm ở chỗ khác trước khi tuyên bố đạt.

---

## Bẫy 4 — DNS của emulator chết sau khi máy host ngủ

Triệu chứng: `sim_route_failed reason=TIMEOUT` liên tục, mọi chặng rơi xuống `SYNTHETIC`, trong khi
khoá API vẫn đúng và nhà cung cấp vẫn sống.

Cách phân biệt lỗi môi trường với lỗi code — gọi thẳng API từ **máy host**:

```bash
GH=$(grep '^GRAPHHOPPER_API_KEY=' local.properties | cut -d= -f2)
curl -s -o /tmp/gh.json -w '%{http_code}\n' --max-time 15 \
  "https://graphhopper.com/api/1/route?point=10.7769,106.7009&point=10.7869,106.6987&profile=car&locale=vi&key=$GH"
```

HTTP 200 ở host + `ping: unknown host graphhopper.com` trong emulator ⇒ **DNS emulator hỏng**, không
phải lỗi app.

`svc wifi disable/enable` **không** cứu được. Phải khởi động lại emulator, và nên chỉ định DNS:

```bash
adb -s emulator-5554 emu kill
~/Library/Android/sdk/emulator/emulator -avd Pixel_10_Pro_XL -no-snapshot-load -no-boot-anim \
  -dns-server 8.8.8.8,8.8.4.4 &
```

Dữ liệu app (zone đã tạo, cache tuyến) nằm trong AVD nên **không mất** khi khởi động lại.

## Bẫy 5 — toạ độ tap đổi sau phase 05

Dải ghi công OSM chiếm chỗ dọc ⇒ khung bản đồ thấp đi ⇒ công tắc "Theo dõi gia đình" **dịch lên**.

| | Trước phase 05 | Sau phase 05 |
|---|---|---|
| Công tắc theo dõi | `input tap 1168 2534` | `input tap 1168 2444` |

Tap trượt thì **không có lỗi nào báo** — chỉ là theo dõi không bật, log im lặng, và mọi phép đo sau đó
đo nhầm một trạng thái tắt. Luôn xác nhận bằng `tracking_toggled enabled=true` trong logcat trước khi
tin vào một cửa sổ đo.
