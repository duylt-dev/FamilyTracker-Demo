
---

# ĐÍNH CHÍNH của orchestrator — 2026-08-21

> Mục A ở trên kết luận **BLOCKER: zone Circle không render**, nguyên nhân do `DemoDataSeeder`
> không seed zone. **Kết luận đó sai.** Ghi lại ở đây vì các phase sau sẽ đọc báo cáo này.

## Vì sao kết luận cũ tự mâu thuẫn

Mục A viết đồng thời hai câu:
- *"Database contains test zone: 'Test Zone' at (10.7769, 106.7009), radius 500m ✓"*
- *"ZoneCircles composable receives empty zones list (0 zones confirmed via log)"*

Nếu zone **đã** nằm trong DB thì `DemoDataSeeder` không còn liên quan — seeder chỉ chạy khi bảng
`members` rỗng, và nó chưa bao giờ có nhiệm vụ seed zone. Hai câu trên chỉ cùng đúng khi có một lỗi
thật ở đường `DAO → Repository → ViewModel`. Nhưng không có lỗi đó.

## Thí nghiệm dựng lại cho sạch

Nguyên nhân thật: **zone mà test agent tưởng đã chèn chưa bao giờ vào được DB.** Kéo file DB về đo
trực tiếp: `zones before: 0`.

Lỗi phương pháp: DB chạy ở chế độ **WAL**. Ghi vào `family_tracker.db` trong khi `-wal` còn dữ liệu
chưa checkpoint, hoặc trong khi app vẫn giữ handle, thì bản ghi bị mất im lặng — không báo lỗi.
Dấu vết còn lại của lần thử đó: hai file rác `databases/ue3.db` + `ue3.db-journal` trên máy ảo
(đã dọn). Chi tiết `colorArgb` ra `0xff2087be` thay vì màu định chèn cũng là dấu hiệu của chính lần
ghi hỏng này, không phải lỗi mã hoá màu như mục "Unresolved Questions" phỏng đoán.

Quy trình đúng đã dùng để kiểm lại:
1. `am force-stop` app — thả handle DB.
2. Kéo cả ba file `family_tracker.db`, `-wal`, `-shm`.
3. Mở bằng `python3 sqlite3` (tự replay WAL), `INSERT`, `commit`, rồi `pragma wal_checkpoint(TRUNCATE)`.
4. Đẩy file `.db` ngược lại, **xoá `-wal`/`-shm` trên máy** (đã checkpoint hết vào file chính).
5. Mở app, chụp màn hình.

## Kết quả thật: US-07 PASS

Chèn 2 zone (`Nhà` tím `#FF7B3FF2` r=500m tại 10.7769/106.7009; `Trường` cam `#FFE5820C` r=300m tại
10.7820/106.6950). Ảnh: `scratchpad/zone-verify.png`.

Quan sát: **cả hai Circle render đúng** — fill nhạt, có viền, có nhãn tên đặt giữa, bán kính tương
quan đúng (vòng 500m lớn hơn rõ rệt vòng 300m), màu khớp giá trị đã chèn, camera tự căn vừa cả hai
zone. Không có lỗi nào ở `ZoneCircles`.

## Đề xuất trong mục A phải bị bác bỏ

Mục A đề xuất thêm seed zone vào `DemoDataSeeder`. **Không làm.** Zone là dữ liệu **do người dùng
tạo** ở phase-06 (US-12→US-21); nhét zone giả vào seeder sẽ trái thiết kế đã chốt ở phase-02, và làm
hỏng chính kịch bản mà phase-06 cần kiểm: tạo zone đầu tiên từ trạng thái rỗng.

## Hai mục "Unresolved Questions" — đã giải quyết

| Câu hỏi | Trả lời |
|---|---|
| Số test: dev báo 62, test agent grep ra 36 | `grep -rho "@Test" --include="*.kt" */src/test \| wc -l` → **64**. Con số 36 là do grep hụt module. |
| `colorArgb` ra `0xff2087be` — lỗi signed/unsigned? | Không. Đó là hệ quả của lần ghi DB hỏng ở trên. Chèn đúng quy trình thì màu ra chính xác. |

## US-10 — cũng đã xác minh trên máy

Mục D để US-10 ở trạng thái "code ✓, chưa test trên máy". Đã test: long-press trên bản đồ điều hướng
đúng sang Zone Editor (`topResumedActivity` giữ nguyên `MainActivity`, nội dung đổi sang placeholder
`Zone Editor — phase-06`). Ảnh: `scratchpad/longpress.png`.

## Kết luận sau đính chính

**Phase-05 PASS.** Cả US-06 → US-11 đều đã xác minh bằng quan sát thật trên `emulator-5554`.
Không có blocker. Gate G8 phần "bản đồ hiện đúng" — món nợ treo từ phase-01 — đã trả xong.

**Bài học phương pháp cho các phase sau:** khi cần sửa dữ liệu trong SQLite của app đang chạy, luôn
`force-stop` trước, xử lý cả `-wal`/`-shm`, và **đọc ngược lại từ máy để xác nhận** trước khi kết luận
về hành vi UI. Một lần ghi hỏng im lặng ở đây suýt biến thành một "blocker" không tồn tại và một
thay đổi seeder sai thiết kế.
