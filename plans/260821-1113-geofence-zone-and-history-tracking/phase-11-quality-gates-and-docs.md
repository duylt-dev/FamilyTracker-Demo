# Phase 11 — Quality gate G1–G8 và đồng bộ tài liệu

## Context Links

- [`plan.md`](plan.md) · tất cả phase 01–10
- PRD v1.2 §11.1 **quality gates G1–G8** · §7.2 (build đem demo là `release`) · §7.3 · §10 telemetry
- [`LLM.md`](../../LLM.md) §10 (ký release, R8 tắt, bẫy configuration cache) · §13 (sai lệch đã biết) · §1
- [`.claude/rules/documentation-management.md`](../../.claude/rules/documentation-management.md)

## Overview

| | |
|---|---|
| Priority | **P0** — không đạt thì không demo |
| Status | pending |
| Effort | 8h (chưa tính thời gian đi bộ ngoài trời cho G5) |
| Gate | **G1 → G8** |

Phase này không thêm tính năng. Nó biến 8 dòng trong PRD §11.1 thành 8 kết quả đo được, dựng ba tầng
kiểm thử cho phần còn lại của dự án, và trả các tài liệu về đúng trạng thái code.

## Key Insights

1. **Ba tầng kiểm thử, và chúng không thay thế được cho nhau.**

   | Tầng | Chạy ở đâu | Chứng minh được gì | Chi phí một vòng |
   |---|---|---|---|
   | (a) Unit test JVM cho `:domain` | Không cần thiết bị | Toàn bộ luật vào/ra zone, lọc nhiễu, gom chuyến, thống kê | vài giây |
   | (b) Emulator ảnh **Google APIs** + GPX | Máy dev | Toàn bộ luồng: quyền, foreground service, geofence, thông báo, vẽ bản đồ. **Vòng lặp hằng ngày.** | vài phút |
   | (c) Một lượt trên **máy thật, ngoài trời** | Chỉ cho **G5** | Hệ điều hành có tự đánh thức app hay không | ~20 phút, phải ra khỏi phòng |

2. **Emulator KHÔNG chứng minh được điều gì về G5.** G5 tồn tại đúng để đo cái mà emulator mô phỏng
   lỏng lẻo nhất: Doze thật khác Doze giả lập, và không có emulator nào tái hiện việc Xiaomi/Oppo tự
   kill foreground service. Chạy G5 trên emulator rồi tick là cách phổ biến nhất để buổi demo thất
   bại đúng ở phần "app đóng vẫn báo".
3. **G4 và G5 đo hai đường khác nhau.** G4 (mô phỏng, ≤ 40 giây) đo đường foreground; G5 (đóng app,
   đi bộ, ≤ 3 phút) đo `GeofencingClient`. Cả hai đều P0.
4. **Bản đem demo là `release`** (PRD v1.2 §7.2). Hệ quả tốt: gate G7 giờ bảo vệ **chính** bản chạy
   trước mặt khách, chứ không phải một bản chỉ tồn tại để đóng gate. Hệ quả phải nhớ: mọi phép đo
   hiệu năng ở §7.1 chỉ có ý nghĩa khi đo trên APK release (debug có composition tracing và live
   literals).
5. **G6 trong PRD vẫn viết trên `assembleDebug` — giữ nguyên, và nó cần baseline từ phase-01.**
   Vì thế phase này chạy **cả hai** variant: `assembleDebug` cho G6, `assembleRelease` cho G8 và cho
   mọi phép đo còn lại. "Không warning mới" là câu không kiểm chứng được nếu không có baseline.
6. **R8 đang tắt và sẽ tiếp tục tắt** (PRD v1.2 §7.2). Trình rút gọn **không** xoá lời gọi log hộ ở
   release. Việc chặn rò toạ độ ra logcat là việc của một cờ trong code, không của công cụ.
   Bật R8 sẽ kéo theo keep-rule cho Room, Koin và kotlinx-serialization — rủi ro không tương xứng.
7. **G8 tồn tại vì `assembleRelease` của template cho ra APK chưa ký.** Nó cũng bắt luôn trường hợp
   SHA-1 của bản release không nằm trong hạn chế API key — triệu chứng là bản đồ xám **chỉ ở**
   release, và nó rất dễ bị quy oan cho code.
8. **G1 là 22 story P0, không phải "cảm giác đã xong".** Cần bảng đối chiếu, và người kiểm không nên
   là người vừa viết story đó.

## Requirements

**Đóng đủ 8 gate:**

| # | Gate | Tầng | Cách đo | Phase sinh ra bằng chứng |
|---|---|---|---|---|
| G1 | Toàn bộ story P0 đạt acceptance criteria | b + c | Bảng đối chiếu 22 dòng, kiểm tay trên APK release | 04–10 |
| G2 | Unit test `ZoneEvaluator`/`LocationFilter`/`RouteStats` xanh, phủ biên | a | `./gradlew :domain:test` | 03 |
| G3 | `KoinModulesTest.checkModules()` xanh | a | `./gradlew test` | 01–10 |
| G4 | Mô phỏng sinh cả thông báo vào và ra ≤ 40s trên máy thật | c | Đồng hồ bấm tay + `FTD_EVENT`, trên APK release | 09 |
| G5 | Đóng app khỏi recents, bước qua ranh giới → thông báo ≤ 3 phút | **c bắt buộc** | Đi bộ thật, đo 3 lần | 07 |
| G6 | `./gradlew assembleDebug` không lỗi, không warning mới | — | So với baseline phase-01 | 01 |
| G7 | Không có toạ độ hay API key trong log build release | — | `assembleRelease` + `adb logcat` + grep | 04–10 |
| G8 | `assembleRelease` ra APK **đã ký**, cài được, bản đồ hiện đúng | — | `adb install` + mở app | 01 |

**Tài liệu phải đồng bộ trong cùng đợt**
- `LLM.md`: §0 đã xoá (phase-01); §3 phản ánh package thật (kể cả `ZoneEventDeduper`, `RouteSplitter`,
  `RouteBlueprint`); §7 chữ ký route thật; §13 cập nhật.
  §9 (bỏ `track_sessions`) và §10 (ký release, R8, configuration cache) **đã đúng từ trước** — chỉ đối chiếu.
- PRD §11.1: 5 dòng feature chuyển từ "Chưa bắt đầu" sang trạng thái thật; ghi số đo G4/G5.
- `VERSIONS-VERIFIED.md`: chuyển 4 câu hỏi "chưa kiểm chứng" sang phần đã xác nhận, thêm các dòng
  phát sinh (coroutines, serialization-json, android-maps-utils nếu có).

## Architecture

Không có thay đổi kiến trúc. Ba việc duy nhất được phép sửa code ở phase này: chặn log ở release (G7),
cấu hình ký nếu phase-01 bỏ sót (G8), và các lỗi phát hiện khi chạy bảng G1.

## Related Code Files

**Tạo**
- `reports/g1-p0-story-checklist.md` — bảng 22 dòng
- `reports/gate-evidence.md` — số đo G4, G5, G6, G7, G8
- `reports/demo-route.gpx` — lộ trình GPX cắt qua zone mẫu, dùng cho tầng (b)
- `reports/emulator-setup.md` — dựng AVD ảnh Google APIs, nạp GPX, các lệnh `adb emu geo`
- `data/util/FtdLog.kt` (nếu chưa có) — cổng log duy nhất, im lặng ở release

**Sửa**
- Mọi chỗ đang gọi `Log.d`/`Log.i` trực tiếp → chuyển sang `FtdLog`
- `LLM.md` §3, §7, §13 · `docs/FTD001_FamilyTrackerDemo_PRD.md` §11.1
- `research/VERSIONS-VERIFIED.md`

## Implementation Steps

1. **Dựng tầng (b) trước, vì 9 bước còn lại đều nhanh hơn nhờ nó.**
   AVD ảnh **Google APIs** (không phải "Google Play" hay AOSP — AOSP không có Play Services thì
   Geofencing API không tồn tại). Viết `reports/emulator-setup.md` gồm:
   ```bash
   # một điểm
   adb emu geo fix <lng> <lat>
   # phát lại cả lộ trình, tốc độ chỉnh trong Extended Controls > Location
   adb emu geo playback reports/demo-route.gpx
   ```
   `reports/demo-route.gpx` phải cắt qua zone mẫu, vào rồi ra, giống hình dạng mà `RouteBlueprint`
   (phase-09) sinh ra — để cùng một kịch bản chạy được ở cả hai tầng.
2. **Tầng (a) — G2, G3.** `./gradlew :domain:test test`. Đọc **tên** các test của `:domain`, đối chiếu
   với bảng 14 trường hợp biên ở phase-03. Test xanh mà thiếu trường hợp biên thì gate chưa đóng.
3. **G6.** `./gradlew clean assembleDebug`, so số warning với `reports/baseline-build-debug.log` từ
   phase-01. Warning mới nào cũng phải hoặc sửa, hoặc có một dòng trong `LLM.md` §13 nói vì sao để lại.
4. **G8.**
   ```bash
   ./gradlew clean assembleRelease
   ls -l app/build/outputs/apk/release/app-release.apk
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk   # phải in ra chứng chỉ
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
   Mở app trên chính APK đó: bản đồ phải hiện đường phố. Xám hoặc watermark = SHA-1 của bản release
   không nằm trong hạn chế API key (`LLM.md` §10) — sửa ở Cloud Console, **không** sửa code.
5. **G7.** Gom mọi lời gọi log về `FtdLog`; ở release `FtdLog` không phát gì. R8 tắt nên không có gì
   xoá hộ (Key Insight #6).
   ```bash
   adb logcat -c && adb logcat > /tmp/ftd-release.log &
   # thao tác đủ luồng trên APK release: cấp quyền, tạo zone, bật theo dõi, chạy mô phỏng
   grep -nE "AIza|[0-9]{1,3}\.[0-9]{5,}" /tmp/ftd-release.log      # phải rỗng
   grep -c "FTD_EVENT" /tmp/ftd-release.log                        # phải là 0
   grep -rn "AIza" --include='*' . | grep -v '^./reports/'         # phải rỗng
   ```
   **Lưu ý mới do QĐ demo chạy release:** `FTD_EVENT` im lặng ở release nghĩa là **mọi bước kiểm chứng
   dựa vào logcat ở phase 04–10 phải chạy trên bản debug**, còn phép đo cuối cùng chạy trên release.
   Đó là lý do bản debug vẫn phải build được, dù không đem đi demo.
6. **G4 — tầng (c).** APK release, tạo 1 zone bán kính 200m, bấm "Mô phỏng lộ trình", bấm giờ tới
   thông báo "Đã rời". Lặp 3 lần, ghi cả 3 số. Bất kỳ lần nào > 40 giây là gate mở.
   Chạy thử trên emulator trước cho nhanh, nhưng số ghi vào `gate-evidence.md` là số trên thiết bị thật.
7. **G5 — tầng (c), bắt buộc ngoài trời.** Hướng dẫn cho người chạy:
   ```
   Chuẩn bị (trong phòng, 5 phút)
     1. Cài APK release, cấp đủ 3 quyền, chọn "Cho phép mọi lúc"
     2. Kiểm:  adb shell dumpsys package com.example.pion.family.tracker.demo | grep BACKGROUND_LOCATION
        Không thấy "granted=true" thì DỪNG — phép đo sẽ vô nghĩa
     3. Tạo zone bán kính 150m quanh chỗ đứng, bật cả hai công tắc thông báo
     4. Bật công tắc theo dõi, chờ thấy zone chuyển "Đang ở trong"
   Đo (ngoài trời, 3 lượt)
     5. VUỐT app khỏi recents. KHÔNG dùng adb shell am force-stop — force-stop xoá geofence,
        và PRD nói rõ "force-stop không tính"
     6. Đi bộ ra khỏi bán kính ít nhất 200m (buffer 30m + sai số GPS), bấm giờ từ lúc bước qua mép
     7. Ghi số giây tới lúc thông báo hiện. Quay lại, chờ 2 phút, lặp
   Ghi lại:  3 số giây, model thiết bị, phiên bản Android, trời quang hay có nhà cao tầng
   ```
8. **G1.** Dựng `reports/g1-p0-story-checklist.md` gồm 22 dòng P0: ID · acceptance criteria rút gọn ·
   tầng kiểm (a/b/c) · kết quả · người kiểm · ngày. Chạy trên **APK release**, theo đúng thứ tự 3 flow
   ở PRD §4.3. Phần lớn kiểm ở tầng (b); chỉ US-24 và các story phụ thuộc geofence nền cần tầng (c).
9. **Đồng bộ tài liệu.** Đọc `LLM.md` từ đầu tới cuối và sửa mọi chỗ mô tả sai thực tế — đặc biệt §3
   (package thật), §7 (chữ ký route thật), §13 (dòng `compose-stability.conf` vẫn "Open" hay đã
   "Fixed"; thêm dòng mới cho mọi sai lệch phát hiện trong 10 phase mà không sửa ngay).
   Đối chiếu §9 và §10 — hai mục này đã đúng, chỉ xác nhận code khớp.
10. Cập nhật PRD §11.1 trạng thái 5 feature, ghi số đo G4/G5. Cập nhật `VERSIONS-VERIFIED.md`.
11. `node .claude/scripts/validate-docs.cjs`, sửa link hỏng.
12. Viết kịch bản demo: thiết bị, quyền cấp trước, thứ tự thao tác, và câu trả lời sẵn cho câu hỏi
    "sao thông báo chậm thế" (30s–3 phút là đặc tính của Play Services, PRD §3.2).

## Todo List

- [x] Tầng (b): AVD ảnh Google APIs + `reports/demo-route.gpx` + `reports/emulator-setup.md`
- [x] G2: `:domain:test` xanh **và** đủ 14 trường hợp biên ở phase-03
- [x] G3: `checkModules()` xanh với đầy đủ binding
- [x] G6: `assembleDebug` xanh, warning không tăng so với baseline phase-01
- [x] G8: APK release **đã ký**, `apksigner verify` in ra chứng chỉ, cài được, bản đồ hiện đúng
- [x] G7: `FtdLog` im lặng ở release; grep toạ độ/API key trên log release trả rỗng
- [ ] G4: 3 phép đo ≤ 40 giây **trên APK release**, ghi số  — **HOÃN**: PRD §11.1 đòi 'trên thiết bị demo thật'; máy thật khoá bằng mật khẩu. Rehearsal emulator 17,4s + 17,5s (2/2, trần 40s).
- [ ] G5: 3 phép đo ≤ 3 phút ngoài trời, kiểm quyền background trước, ghi số + model máy  — **HOÃN**: cần người cầm máy đi bộ qua ranh giới. 8 bước cụ thể ở `reports/dev-phase-11-report.md`.
- [x] G1: bảng 22 story P0, kiểm trên APK release, ghi tầng kiểm cho từng dòng  — **26** story P0 thật (PRD §2 ghi 22, tự mâu thuẫn với cột ưu tiên); 25/26 Đạt, US-24 HOÃN cùng lý do G5. Xem `reports/g1-p0-story-checklist.md`.
- [x] Nút "Mô phỏng lộ trình" **có mặt** trên APK release (PRD v1.2 §6)
- [x] `LLM.md` §3, §7, §13 khớp code thật; §9, §10 đối chiếu
- [x] PRD §11.1 cập nhật trạng thái 5 feature + số đo
- [x] `VERSIONS-VERIFIED.md` chuyển 4 dòng "chưa kiểm chứng" sang đã xác nhận
- [x] `validate-docs.cjs` xanh — **EXIT=0**. Script trước đó treo vô hạn: nó `spawnSync('grep','-r',…)` cho MỖI tham chiếu code, quét cả `*/build` (236MB/331MB cây thư mục) nên mỗi lần grep chạm trần timeout 5s. Đã sửa bằng `--exclude-dir=build/.git/.gradle/node_modules`. Lệnh đúng cho dự án đa module: `node .claude/scripts/validate-docs.cjs --src domain/src,ui/src,data/src,app/src` (mặc định `src,lib,app,scripts,.claude` không khớp bố cục Android). Kết quả: 24 tham chiếu xác nhận, 110+27 cảnh báo là **báo động giả** — script kiểm hằng số Kotlin (`MAX_SPEED_KMH`) với `.env.example` và coi hằng số Android SDK là tham chiếu code dự án.
- [x] Kịch bản demo viết ra

## Success Criteria

```bash
./gradlew clean :domain:test test          # tầng (a) — G2, G3
./gradlew assembleDebug                    # G6, so với reports/baseline-build-debug.log
./gradlew assembleRelease                  # G8
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
node .claude/scripts/validate-docs.cjs
```
- `reports/gate-evidence.md` có **số đo thật** cho G4 (3 lần) và G5 (3 lần), không phải chữ "OK".
- `reports/g1-p0-story-checklist.md` có 22 dòng, tất cả "Đạt", mỗi dòng có tầng kiểm, người kiểm, ngày.
- `reports/emulator-setup.md` đủ chi tiết để người khác dựng lại AVD và chạy GPX mà không hỏi ai.
- `git diff` cho thấy `LLM.md` và PRD được cập nhật **trong cùng đợt** với lần commit cuối.

## Risk Assessment

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| **Emulator dễ và tiện tới mức G5 bị tick bằng emulator** | Gate P0 mở mà không ai biết, hỏng đúng phần "app đóng vẫn báo" | Cột "tầng" trong `g1-p0-story-checklist.md` bắt phải ghi (a/b/c); G5 chỉ nhận (c), kèm model máy và điều kiện thời tiết |
| G5 phải đo ngoài trời và không ai xếp lịch cho nó | Phát hiện muộn, sát ngày demo | Đặt lịch đo ngay khi phase-07 xong, không đợi phase này |
| Thiết bị demo là Xiaomi/Oppo/Vivo | G5 fail vì OEM, không vì code | Chốt Pixel/Samsung từ trước; nếu buộc dùng, bật Autostart và ghi vào kịch bản demo |
| Dùng AVD ảnh AOSP thay vì Google APIs | Geofencing API không tồn tại, tưởng code sai | `emulator-setup.md` ghi rõ tên ảnh; kiểm bằng `adb shell pm list packages \| grep gms` |
| Không có baseline warning từ phase-01 | G6 không kiểm chứng được | Baseline lấy ở phase-01 và lưu vào `reports/` |
| Chỉ build release nên bản debug mục ruỗng dần | Tới lúc cần logcat để gỡ lỗi thì debug không build được | `assembleDebug` vẫn nằm trong Success Criteria của phase này và trong G6 |
| Sửa lỗi phát hiện ở G1 làm hỏng thứ đã xanh | Vòng lặp không kết thúc | Sau mỗi lần sửa, chạy lại **toàn bộ** khối Success Criteria, không chỉ phần vừa sửa |
| Tài liệu cập nhật ở một commit riêng sau cùng | Drift quay lại ngay tuần sau | Luật `LLM.md` §1: cập nhật trong cùng commit gây ra thay đổi |

## Security Considerations

- G7 là gate an ninh chính, và vì bản demo **là** bản release, nó bảo vệ đúng thứ chạy trước mặt khách.
- Kiểm thêm ngoài yêu cầu PRD: `git log -p -- local.properties` phải rỗng. Key từng bị commit dù một
  lần thì phải **thu hồi** ở Cloud Console, không chỉ xoá khỏi file.
- Xác nhận `allowBackup="false"` (phase-04, PRD v1.2 §7.3) còn nguyên trong manifest **của bản release**
  — manifest merger có thể đưa `allowBackup` từ thư viện vào; kiểm bằng
  `apkanalyzer manifest print app/build/outputs/apk/release/app-release.apk`.
- Ký bằng debug keystore là **lựa chọn cho demo**. Nếu bản này bị đem phát hành thật, khoá riêng nằm
  trong `~/.android/debug.keystore` với mật khẩu công khai `android` — ghi cảnh báo đó vào kịch bản
  bàn giao.

## Next Steps

Sau khi 8 gate đóng: bàn giao kịch bản demo + `gate-evidence.md` + `g1-p0-story-checklist.md` cho BA.
Hạng mục cho bản sau đã liệt kê ở PRD §11.2 (backend, đăng nhập, zone đa giác, migration Room thật,
cổng Compose stability, và **keystore phát hành thật** thay cho debug keystore).
