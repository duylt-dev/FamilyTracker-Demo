# Tầng (b) — dựng AVD Google APIs + bơm GPX

## AVD đã dùng (đã có sẵn trên máy này, xác nhận qua `~/.android/avd/*.avd/config.ini`)

| Thuộc tính | Giá trị |
|---|---|
| Tên AVD | `Pixel_10_Pro_XL` |
| Ảnh hệ thống | `system-images/android-37.1/google_apis_playstore_ps16k/arm64-v8a/` — **Google APIs PlayStore**, không phải AOSP/Google APIs trơn |
| `tag.display` | `Google APIs PlayStore` |
| RAM | 4096 MB |
| Serial khi chạy | `emulator-5554` |

**Vì sao phải là "Google APIs PlayStore", không phải AOSP:** AOSP không có Play Services →
`GeofencingClient` không tồn tại, geofence không đăng ký được — nhầm tưởng code sai trong khi thực
ra là sai ảnh AVD. Kiểm nhanh trước khi test bất cứ gì:
```bash
adb -s emulator-5554 shell pm list packages | grep -i gms
# phải thấy: com.google.android.gms, com.google.android.gms.supervision
```

## Chạy emulator

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_10_Pro_XL &
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
export ANDROID_SERIAL=emulator-5554   # bắt buộc nếu máy thật cũng đang cắm — ENV-BRIEFING.md §3
```

## Bơm vị trí — **sửa lại so với ENV-BRIEFING.md §4, đã kiểm chứng thật trên máy này**

`ENV-BRIEFING.md` §4 ghi `adb emu geo gpxfile /path/route.gpx` (phát cả tuyến GPX qua console lệnh).
**Lệnh đó không tồn tại trên console emulator thật của máy này.** Xác nhận bằng
`adb emu help geo`:
```
available sub-commands:
    nmea             send a GPS NMEA sentence
    fix              send a simple GPS fix
    gnss             send a GNSS sentence
```
Không có `playback` hay `gpxfile`. Tính năng "Import GPX/KML rồi Play" **chỉ tồn tại trong Extended
Controls UI của Android Studio** (nút Location bên trái cửa sổ Emulator) — kênh gRPC riêng, không đi
qua console telnet mà `adb emu` dùng. Trong môi trường CLI/headless (không mở Android Studio), cách
duy nhất bơm cả tuyến là gọi `geo fix` lặp lại theo từng điểm, có `sleep` giữa các lần để mô phỏng
nhịp thời gian thật.

**Một điểm:**
```bash
adb -s emulator-5554 emu geo fix <lng> <lat>     # LƯU Ý: lon TRƯỚC, lat SAU
```

**Phát cả tuyến từ `reports/demo-route.gpx`** (20 điểm, span 30s, cắt thẳng qua tâm "Zone mẫu"
`10.7769, 106.7009`, bán kính mặc định 150m — cùng hình dạng `RouteBlueprint` sinh ra ở phase-09, để
một kịch bản chạy được ở cả hai tầng như spec yêu cầu):

```bash
python3 - <<'PYEOF'
import subprocess, time, xml.etree.ElementTree as ET

ns = {"g": "http://www.topografix.com/GPX/1/1"}
tree = ET.parse("reports/demo-route.gpx")
pts = tree.getroot().findall(".//g:trkpt", ns)
prev_t = None
for pt in pts:
    lat, lon = pt.get("lat"), pt.get("lon")
    t_text = pt.find("g:time", ns).text
    t = time.strptime(t_text, "%Y-%m-%dT%H:%M:%SZ")
    if prev_t is not None:
        time.sleep(max(0, time.mktime(t) - time.mktime(prev_t)))
    prev_t = t
    subprocess.run(["adb", "-s", "emulator-5554", "emu", "geo", "fix", lon, lat], check=True)
    print(f"fix lat={lat} lon={lon}")
PYEOF
```

**Bẫy đã gặp lúc kiểm chứng lệnh này:** `dumpsys location` đôi lúc trả về toạ độ CŨ (giữ nguyên từ
lần `geo fix` trước đó rất lâu, thấy rõ qua `et=` — elapsed time — vài giờ) ngay sau khi vừa gọi
`geo fix` mới. Nguyên nhân: fused location provider của GMS chỉ thật sự xử lý fix mới khi có ai đó
đang ACTIVE request cập nhật vị trí — bật công tắc "Theo dõi vị trí" trong app (hoặc bất kỳ app nào
đang xin `FusedLocationProviderClient` updates) TRƯỚC khi bắt đầu bơm `geo fix`, nếu không các fix
sẽ bị bỏ qua âm thầm, không lỗi, không log.

## Xác nhận đã cắt qua zone

Sau khi bơm xong (bật theo dõi từ trước): kiểm `adb -s emulator-5554 logcat -d -s FTD_EVENT:D | grep
zone_event_raised` (chỉ thấy trên **bản debug** — bản release câm log, G7) hoặc mở tab "Nhật ký"
trong app xem có đúng 1 cặp ENTER/EXIT mới.

## Việc này dùng để làm gì trong phase-11

- Kịch bản GPX này là cách "gõ cửa" hệ thống từ BÊN NGOÀI app (qua GPS mock thật của emulator, đi
  qua toàn bộ chuỗi Play Services → `GeofenceBroadcastReceiver`), khác với nút "Mô phỏng lộ trình"
  trong app (đi qua `SimulatedLocationSource` nội bộ, đường FOREGROUND). Cả hai đường đều hợp lệ để
  kiểm G1/G4, nhưng GPX này là bằng chứng độc lập, không phụ thuộc vào code mô phỏng của chính app.
- Số đo G4/G1 chính thức trong `dev-phase-11-report.md`/`g1-p0-story-checklist.md` dùng nút mô phỏng
  trong app (nhanh hơn, và đó là chính xác thứ US-33 mô tả) — script GPX ở đây là công cụ tái lập
  cho lần sau, không phải bằng chứng gate đã chấm trong phase này.
