# Phase 06 — Đo, gate chất lượng, đóng nợ tài liệu

## Context Links

- [`plan.md`](plan.md) · [`decisions.md` §C4, §C5](decisions.md)
- Yêu cầu: [PRD delta](docs/prd-delta-smooth-road-movement.md) §6 (nợ tài liệu N1–N4), §8 (Q8–Q14), §9
- Nghiệm thu: **QA-SRM-29, 35, 36** · **UAT-01 → UAT-08** · danh sách hồi quy QA §5
- Kiến trúc: `LLM.md` §11 (test), §13 (sai lệch đã biết), quy tắc cập nhật ở `.claude/CLAUDE.md`
- Tài liệu phải sửa: `LLM.md`, `docs/routing-and-map-attribution.md`, `docs/project-changelog.md`,
  `docs/prd-delta-smooth-road-movement.md`

## Overview

| | |
|---|---|
| **Ưu tiên** | P1 |
| **Trạng thái** | pending |
| **Ước lượng** | 3h |
| **Phụ thuộc** | Tất cả các phase trước |

Ba việc: **đo** những con số mà PRD delta §9 nói thẳng là "cần đo, không cần đoán"; **chạy hết**
bộ gate và UAT; **đóng** mọi nợ tài liệu mà năm phase trước tạo ra. Phase này không thêm tính năng.

## Gates

### G-A — phải đúng TRƯỚC khi bắt đầu implement

| # | Điều kiện | Trạng thái | Nếu chưa đúng |
|---|---|---|---|
| GA-1 | `decisions.md` đã được người có thẩm quyền đọc, đặc biệt §C2 (nguồn ngoại tuyến) và §C3 (`MAX_ACCURACY_M`) | ⬜ | Dừng. Đây là hai chỗ plan lệch khỏi khuyến nghị của research |
| GA-2 | Câu hỏi redistribution của GraphHopper (`LLM.md` §13 Open #11) | ⬜ Chưa gửi thư | **KHÔNG chặn phase 01–03, 06.** Chặn **phát hành** bản bật tầng 1/2 — và đã chặn từ trước plan này (màn Dẫn đường). Xem `decisions.md` §C2 |
| GA-3 | `./gradlew test` xanh trên `main` trước khi bắt đầu | ⬜ | Không có đường phân biệt hồi quy do plan này gây ra với hồi quy có sẵn |
| GA-4 | Máy thật để nghiệm thu US-43 trong nhà | ⬜ | UAT-05 hoãn, ghi vào `LLM.md` §13 Open — **không** được đánh dấu đạt bằng emulator |

### G-B — phải đúng TRƯỚC khi phát hành

| # | Điều kiện | Nguồn |
|---|---|---|
| GB-1 | `./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache` xanh, `:domain:test` < 5 s | LLM.md §11 |
| GB-2 | `./gradlew assembleRelease` xanh | PRD §7.2 (bản demo là release) |
| GB-3 | `MemberRoamerTest`'s test bất biến ENTER/EXIT xanh với **cả** đường tổng hợp **và** fixture tuyến thật | `decisions.md` §C4 |
| GB-4 | Không file nào trong `assets/` chứa dữ liệu của nhà cung cấp routing | `decisions.md` §C2 |
| GB-5 | `FTD_EVENT` câm hoàn toàn trên release (gate G7 cũ) — không log toạ độ ở bất kỳ nhánh mới nào | PRD §7.3 |
| GB-6 | Ghi công hiển thị đúng ba trạng thái, credit Google không bị che | QA-SRM-30/31/32 |
| GB-7 | Điều khoản redistribution của GraphHopper (GA-2) | `routing-and-map-attribution.md` §5 — **vẫn ⬜** |
| GB-8 | UAT-05 (trong nhà, máy thật) đạt | QA §4 — câu nghiệm thu đầu bảng |

## Key Insights

1. **Không được ngoại suy thời gian một vòng.** Con số thật duy nhất là 63 giây ở 20 m/s
   (`LLM.md` §13 Fixed #23). Phép nhân thẳng ra ~150 s là **sai** vì bám đường làm quãng đường
   dài hơn đường chim bay (hệ số vòng vèo đô thị 1.2–1.4×) và spawn-một-lần đổi điểm xuất phát.
2. **Đo hai lớp, vì mỗi lớp trả lời một câu khác nhau.** Lớp tất định (JUnit) cho **cận dưới** và
   gắn được vào CI. Lớp thật (emulator) cho con số dùng để xếp lịch buổi demo.
3. **Bất biến ENTER/EXIT phải được kiểm lại lần cuối với đúng bộ hằng số cuối cùng** — nếu B4 làm
   `SIM_MEMBER_SPEED_MPS` đổi ở phase này, mọi phép tính vòng ở phase 02 đổi theo.
4. **Nợ tài liệu là khuyết tật, không phải việc dọn dẹp.** `.claude/CLAUDE.md` nói rõ: sửa trong
   cùng commit gây ra. Phase này chỉ dọn phần **liên phase** (bảng tổng, §13, changelog) — nợ của
   từng phase đã phải đóng trong chính phase đó.
5. **PRD là của BA, không phải của dev.** Phase này cập nhật `prd-delta-*.md` (tài liệu đề xuất
   trộn) chứ **không** tự sửa `docs/FTD001_FamilyTrackerDemo_PRD.md`. N1/N2/N4 của PRD delta §6 ghi
   lại, không tự làm.

## Requirements

- FR-1 Đo thời gian ENTER→EXIT và ENTER→ENTER, hai lớp, cho cả Minh và Lan.
- FR-2 Áp luật chốt `SIM_MEMBER_SPEED_MPS` ở `decisions.md` §C5; đóng chặn B4.
- FR-3 Chạy hết 36 ca QA-SRM và 8 kịch bản UAT; ca nào hoãn thì ghi **lý do** và **điều kiện để mở lại**.
- FR-4 Đo lại nhịp khung hình sau khi tầng 1/2 đã bật (số ở phase 03 đo với tầng 3).
- FR-5 Mọi dòng `LLM.md` §13 mở/đóng bởi plan này đều có mặt trong bảng, kèm mã commit ở dòng Fixed.
- NFR-1 Không thêm mã sản phẩm nào ở phase này ngoài một file test đo (`MemberRoamerLapTimeTest`).

## Architecture

Không có kiến trúc mới. Phase này là quy trình:

```
  Lớp 1 (tất định, CI)        Lớp 2 (thật, emulator/máy thật)      Đóng sổ
  ──────────────────────      ────────────────────────────────      ─────────
  MemberRoamerLapTimeTest     adb logcat -s FTD_EVENT               LLM.md §13
   ├ đếm nhịp giữa 2 ENTER     ├ zone_event_raised ENTER/EXIT       routing-and-map-attribution.md §5
   ├ × MEMBER_ROAM_INTERVAL_MS ├ notification_posted ×2/vòng        project-changelog.md
   └ assert > 60_000 ms        ├ sim_route_loaded source=…          prd-delta §6, §8
                               ├ sim_spawn ×1/thành viên
                               └ dumpsys gfxinfo (janky %)
```

## Related Code Files

**Tạo**

| Đường dẫn | Việc |
|---|---|
| `domain/src/test/kotlin/.../domain/tracking/MemberRoamerLapTimeTest.kt` | Đếm nhịp giữa hai `ENTER` liên tiếp qua `ZoneEvaluator` thật; assert `> EVENT_DEDUPE_WINDOW_MS`; **in** con số ms ra để dev report chép lại |
| `plans/260825-0956-smooth-road-following-member-movement/reports/` | Dev report của từng phase + bảng kết quả QA/UAT (đường dẫn báo cáo bắt buộc, `.claude/rules/orchestration-protocol.md`) |

**Sửa**

| Đường dẫn | Việc |
|---|---|
| `domain/.../tracking/TrackingConstants.kt` | Chỉ khi luật C5 buộc đổi `SIM_MEMBER_SPEED_MPS`; đổi thì sửa luôn KDoc kèm số đo |
| `LLM.md` | §13 — **Fixed**: D6/US-43 (phase 01), "spawn mỗi tick" (phase 02), KDoc "jump, không animate" (phase 03). **Open**: tầng 3 không đạt US-41; marker vị trí thật không có chỉ báo "cũ" (D7); hạn ngạch free tier khi bật tầng 1. §13 Open #7 — cập nhật tỉ lệ truy nguyên PRD §6 lên 14/21 |
| `docs/routing-and-map-attribution.md` | Cập nhật ngày "Kiểm tra điều khoản lần cuối"; §5 giữ ⬜ cho GraphHopper (im lặng không phải là đồng ý) |
| `docs/project-changelog.md` | Một mục mới cho thay đổi này: khuyết tật D1–D6, cách sửa, số đo |
| `docs/prd-delta-smooth-road-movement.md` | §4 xoá hết ô "TBD — planner xác nhận" (điền số thật đã đo); §8 trả lời Q8–Q13; §9 ba câu chưa giải được → đã giải |
| `docs/qa-uat-smooth-road-movement.md` | Cột kết quả cho 36 ca; ca hoãn ghi lý do |

**Xoá:** không.

## Implementation Steps

1. **Lớp 1 — đo tất định.** Viết `MemberRoamerLapTimeTest`. Chạy cho zone 150m và zone 50m, cho
   cả đường tổng hợp và fixture tuyến thật (lưu từ phase 04 Step 9a). Ghi 4 con số.
2. **Lớp 2 — đo thật trên `emulator-5554`**, bản **debug** (log cần thiết):
   ```
   adb logcat -c
   adb logcat -s FTD_EVENT -v time > lap.log     # chạy 6 phút
   ```
   Trích: `sim_spawn` (phải đúng 1 dòng mỗi thành viên), `zone_event_raised type=ENTER/EXIT`,
   `notification_posted`. Tính ENTER→EXIT và ENTER→ENTER cho từng thành viên.
3. **Áp luật C5** với con số ENTER→EXIT đo được:
   | Đo được | Hành động |
   |---|---|
   | ≤ 180 s | Giữ `SIM_MEMBER_SPEED_MPS = 8.3`, đóng B4 |
   | 180–260 s | Đặt `11.1`, quay lại bước 1–2 **một lần** |
   | > 260 s | Đặt `13.9` (trần cứng), quay lại bước 1–2. Vẫn > 260 s → giảm `LEAVE_MARGIN_M` 120 → 60 (vẫn > `ZONE_EXIT_BUFFER_M` = 30) và ghi vào `LLM.md` §13 |
   Mỗi lần đổi số: chạy lại `MemberRoamerTest` (GB-3) trước khi đo lại.
4. **QA-SRM-29 đầu-cuối:** một vòng đầy đủ → đúng 1 thông báo "đã đến" + đúng 1 "đã rời" cho mỗi
   thành viên; tab Nhật ký xen kẽ; **mọi dòng mang tên Minh hoặc Lan, không dòng nào mang "Tôi"**
   (bất biến §8.1, phải kiểm lại vì plan này đụng vào đường sinh sự kiện).
5. **Đo lại nhịp khung hình** (FR-4) với tầng 1/2 bật, 3 thành viên + 5 zone, cùng phương pháp
   phase 03 Step 8. So với số nền của phase 03; chênh > 5 điểm phần trăm thì điều tra trước khi đóng.
6. **Chạy hết 36 ca QA-SRM.** Nhắc: `FTD_EVENT` **câm trên release** — mọi ca "kiểm bằng log" chạy
   trên **debug**; UAT chạy trên **release** và kiểm bằng mắt.
7. **Chạy 8 kịch bản UAT** trên bản **release**. **UAT-05 (trong nhà, máy thật) là câu nghiệm thu
   đầu bảng** — nếu GA-4 chưa có máy, ghi HOÃN kèm điều kiện mở lại, **không** đánh dấu đạt.
8. **Chạy danh sách hồi quy QA §5** — đặc biệt các đường không nằm trong plan này: F5 "Mô phỏng lộ
   trình" (US-33), polyline tab Lịch sử (US-31), màn Dẫn đường (đã bị chuyển nhà composable ở phase 05).
9. **Đóng sổ tài liệu**, một commit:
   - `LLM.md` §13: 3 dòng chuyển sang Fixed kèm mã commit; 3 dòng Open mới; §13 Open #7 cập nhật 14/21;
   - `routing-and-map-attribution.md`: ngày kiểm tra + §3 đường dẫn `RoutingAttribution.kt`;
   - `project-changelog.md`: mục mới kèm số đo;
   - `prd-delta-*.md`: xoá TBD, trả lời Q8–Q13, đóng §9;
   - `qa-uat-*.md`: điền kết quả 36 ca.
10. `./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache` và
    `./gradlew assembleRelease` — GB-1, GB-2.

## Todo List

- [ ] `MemberRoamerLapTimeTest` + 4 con số lớp 1
- [ ] Đo lớp 2 trên emulator, trích `lap.log`
- [ ] Áp luật C5, chốt `SIM_MEMBER_SPEED_MPS`, đóng B4
- [ ] `MemberRoamerTest` xanh với bộ hằng số cuối cùng (GB-3)
- [ ] QA-SRM-29: đúng 2 thông báo/vòng, Nhật ký không dòng nào mang tên "Tôi"
- [ ] Đo lại janky frames với tầng 1/2 bật
- [ ] Chạy 36 ca QA-SRM (debug cho ca đọc log)
- [ ] Chạy 8 UAT trên release; UAT-05 đạt **hoặc** ghi HOÃN kèm điều kiện mở lại
- [ ] Danh sách hồi quy QA §5, gồm F5 / Lịch sử / Dẫn đường
- [ ] `LLM.md` §13: 3 Fixed + 3 Open + cập nhật #7 (14/21)
- [ ] `routing-and-map-attribution.md` ngày kiểm + đường dẫn
- [ ] `project-changelog.md` mục mới kèm số đo
- [ ] `prd-delta` xoá TBD + trả lời Q8–Q13 + đóng §9
- [ ] `qa-uat` điền kết quả 36 ca
- [ ] GB-1 + GB-2 xanh

## Success Criteria

| # | Điều kiện | Cách kiểm | Nguồn |
|---|---|---|---|
| S1 | Thời gian ENTER→EXIT và ENTER→ENTER có **số thật** trong dev report, cho cả 2 thành viên, ở cả 2 lớp đo | `lap.log` + output test | `decisions.md` §C5, PRD delta §9.1 |
| S2 | B4 đóng: `SIM_MEMBER_SPEED_MPS` có giá trị cuối và KDoc mang số đo | `git diff TrackingConstants.kt` | plan.md §Chặn |
| S3 | Đúng 1 `sim_spawn` mỗi thành viên trong 6 phút | `lap.log` | QA-SRM-09/11 |
| S4 | Đúng 2 thông báo mỗi vòng mỗi thành viên; Nhật ký xen kẽ; không dòng nào tên "Tôi" | Shade + tab Nhật ký | QA-SRM-29, LLM.md §8.1 |
| S5 | Janky frames không xấu hơn số nền phase 03 quá 5 điểm phần trăm | `dumpsys gfxinfo` | QA-SRM-35 |
| S6 | 36/36 ca QA-SRM có kết quả (đạt / không đạt / hoãn-kèm-lý-do). **Không ô trống** | `qa-uat-*.md` | QA §2 |
| S7 | 8/8 UAT có kết quả; UAT-05 đạt hoặc HOÃN kèm điều kiện mở lại rõ ràng | `qa-uat-*.md` §4 | QA §4 |
| S8 | `LLM.md` §13 không còn dòng nào mô tả sai hiện trạng; 3 dòng Fixed có mã commit | Đọc §13 | `.claude/CLAUDE.md` |
| S9 | Không ô "TBD — planner xác nhận" nào còn lại trong PRD delta §4 | `grep -c "TBD" docs/prd-delta-smooth-road-movement.md` | PRD delta §4 |
| S10 | GB-1 → GB-6 xanh. GB-7 vẫn ⬜ và **được ghi rõ là chặn phát hành**, không bị coi là đã đóng | Output Gradle + đọc §5 | plan.md §Chặn |

## Risk Assessment

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| **Thời gian một vòng dài tới mức hỏng nhịp buổi demo** | **Trung bình** | Luật C5 có 3 nấc và một trần cứng; nấc cuối là đổi hình học (`LEAVE_MARGIN_M`) chứ không phải nâng tốc độ vô hạn |
| Đổi `SIM_MEMBER_SPEED_MPS` ở phase này làm vỡ bất biến ENTER/EXIT | **Cao** | GB-3 chạy lại **sau mỗi lần đổi số**, không phải một lần cuối |
| UAT-05 không chạy được vì không có máy thật ⇒ ai đó đánh dấu "đạt" bằng emulator | **Cao** | S7 đòi ghi HOÃN + điều kiện mở lại; đây đúng loại chặn đã làm G4/G5 hoãn ở phase-11 (PRD §11.1), có tiền lệ |
| Emulator không sinh được fix `accuracy > 50m` để kiểm US-43 ở lớp thật | Trung bình | Lớp JUnit của phase 01 (S1–S4) đã khoá logic; lớp thật chỉ xác nhận trải nghiệm. Ghi rõ phân chia đó trong dev report |
| Nợ tài liệu bị đẩy sang "lần sau" | Trung bình | S8 và S9 là tiêu chí đóng phase, đo bằng `grep`, không bằng thiện chí |
| Hồi quy ở màn Dẫn đường sau khi chuyển nhà `RoutingAttribution` | Thấp | Bước 8 chạy hồi quy màn đó |

## Security Considerations

- **GB-5 là cổng bảo mật, không phải cổng chất lượng.** Xác nhận bằng cách chạy bản **release** và
  `adb logcat -s FTD_EVENT` — phải **trống hoàn toàn**. Một dòng lọt ra trên release là rò rỉ vị
  trí của người dùng thật.
- Kiểm bằng mắt một file cache thật (`adb shell run-as <pkg> cat files/routes/*.json`): **không**
  chứa khoá API, **không** chứa dữ liệu định danh người dùng.
- `lap.log` và mọi log dán vào dev report phải được **xoá toạ độ** trước khi commit — file trong
  `plans/` đi vào git.
- Xác nhận không quyền mới nào được thêm vào manifest trong cả 6 phase.

## Next Steps

- Gửi thư hỏi GraphHopper (GB-7) — nó chặn phát hành và đã chặn từ trước plan này.
- Nếu UAT-05 hoãn: mở một dòng `LLM.md` §13 Open kèm điều kiện mở lại, và **không** tuyên bố
  US-43 đạt.
- Ba dòng Open mới ở §13 (tầng 3 không đạt US-41, không có chỉ báo "cũ", hạn ngạch free tier) là
  đầu vào cho vòng plan kế tiếp — đừng để chúng nằm im.
