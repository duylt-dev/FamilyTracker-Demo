# Playbook dùng Claude cho project này

Đúc kết từ 5 session thực tế (21/08 → 22/08/2026) dựng FamilyTrackerDemo từ một req
hai dòng của BA thành app Android 4 module, 11 phase, 14 commit.

---

## Phần 1 — Đã thực sự diễn ra

| # | Session | Thời lượng | Số prompt của bạn | Kết quả |
|---|---|---|---|---|
| 1 | `a1f15907` | 21/08 03:46→06:42 (~3h) | 6 | LLM.md, PRD, 3 báo cáo research, plan 11 phase |
| 2 | `0c0ca4a1` | 21/08 06:43→23:32 (~17h) | **1** | 11 phase code xong, 11 commit, 30 lượt agent |
| 3 | `f87ef493` | 22/08 03:22→03:26 (4 phút) | 1 | Hướng dẫn sử dụng app |
| 4 | `c5d5504b` | 22/08 03:44→05:06 (~1.5h) | 3 | Sửa bug nghiệp vụ: zone theo dõi nhầm chủ thể |
| 5 | `e9d11602` | 22/08 09:27 | 1 | Chính tài liệu này |

### Session 1 — Khởi động (quan trọng nhất)

1. Bạn đưa req thô từ BA + nói rõ **LLM.md là copy từ project khác, sẽ sửa dần**.
2. Bạn nói: *"hãy hỏi tôi các thông tin cần thiết"* → tôi hỏi **8 câu** bằng AskUserQuestion,
   chốt được: multi-module + **Koin**, **Google Maps Compose**, **GeofencingClient + fallback
   foreground**, phạm vi 1 thiết bị local với member giả, và cả *quy trình đi tiếp*.
3. Sửa `LLM.md` §2–§6 thành hợp đồng kiến trúc thật của project.
4. `/product-requirements` → `docs/FTD001_FamilyTrackerDemo_PRD.md` (36 user story, 5 feature, **8 quality gate**).
5. Bạn xác nhận PRD với BA → hỏi *"bước tiếp theo là gì"* → **3 agent `researcher` chạy song song**
   (geofencing, maps-compose, multi-module toolchain) → gộp thành `research/VERSIONS-VERIFIED.md`,
   file này **thắng mọi số version trong 3 báo cáo**.
6. Bạn ra **3 quyết định chủ dự án** (bỏ bảng `track_sessions` · demo bằng bản **release** ·
   emulator là vòng lặp test chính) → agent `planner` → `plans/260821-1113-.../` 11 phase.

### Session 2 — Thi công (hình mẫu cần lặp lại)

Chỉ **một prompt** chạy suốt 17 giờ:

> *"Triển khai agents dev, test, fix lần lượt và luân phiên cho từng phase khi đã lên kế hoạch
> cho tới khi hoàn thành bản MVP. Build test thì build máy ảo nhé. Có gì không hiểu hãy hỏi lại tôi."*

Tôi hỏi lại 3 câu (commit thế nào · emulator nào · MVP gồm phase nào) rồi chạy vòng lặp:

```
fullstack-developer → tester → debugger   ×11 phase
30 lượt agent · 185 lệnh Bash (gradle, adb, emulator) · commit sau mỗi phase pass
```

Kết thúc: 11/11 phase completed, **G4/G5 hoãn** vì không có máy thật mở khoá.

### Session 4 — Change request

Bạn phát hiện app thông báo cho **chính mình** thay vì cho người được theo dõi → yêu cầu
"lên kế hoạch lại rồi dev, test, fix, verify". Tôi hỏi 2 câu chốt phương án, tạo plan
`260822-1059-zone-tracking-follows-family-members` (3 phase) rồi sửa.

> ⚠️ **Lệch quy trình:** session này tôi **không gọi một subagent nào**, tự làm inline
> (122 lệnh Bash). Bạn yêu cầu "dev, test, fix" nhưng không nêu đích danh tên agent nên nó
> bị hiểu thành mô tả công việc chứ không phải chỉ thị gọi agent.

---

## Phần 2 — Quy trình chuẩn 7 bước

### Bước 0 — Hợp đồng kiến trúc (một lần cho mỗi project)

Sửa `LLM.md` §2–§11 cho khớp module thật **trước khi viết dòng code đầu tiên**.
`docs/android-mvi-best-practices.md` giữ nguyên, không cần sửa.

> Prompt: `LLM.md đang copy từ project khác. Hãy hỏi tôi các thông tin cần thiết để sửa lại §2–§6 cho đúng project này.`

### Bước 1 — Chốt quyết định bằng hỏi–đáp ⭐ bước lời nhất

> Prompt: `Đây là req từ BA: "<dán req>". Trước khi làm gì, hãy hỏi tôi tất cả thông tin bạn cần.`

Bắt buộc phải chốt được, nếu tôi không hỏi thì bạn chủ động khai:

- **Ai là actor, ai là subject** — ai dùng app, ai bị theo dõi *(bỏ sót câu này chính là nguyên nhân bug session 4)*
- Thư viện/SDK khoá cứng (map, geofence, DI)
- Phạm vi: mấy thiết bị, có backend không, dữ liệu thật hay seed
- Build đem demo là `debug` hay `release`
- **Ràng buộc môi trường**: có máy thật không, emulator nào, có API key chưa

Trả lời gọn theo số thứ tự (`1. đồng ý  2. release  3. test trên emulator`) — kiểu trả lời này
ở session 1 đã ngấm thẳng vào toàn bộ 11 phase của plan.

### Bước 2 — PRD

> Prompt: `/product-requirements` (hoặc: `chạy skill product-requirements`)

Ra `docs/FTDxxx_*_PRD.md`. **Đưa BA duyệt trước khi đi tiếp** — bạn đã làm đúng ở session 1.

### Bước 3 — Research song song

> Prompt: `Chạy song song các agent researcher cho <chủ đề A>, <chủ đề B>, <chủ đề C>, rồi gộp version thành một file VERSIONS-VERIFIED.md làm nguồn duy nhất.`

Bắt buộc với thư viện Android đổi API nhanh (geofence, maps-compose, AGP/Kotlin/KSP).

### Bước 4 — Plan

> Prompt: `Dùng agent planner tạo plan từ PRD + LLM.md + VERSIONS-VERIFIED.md, chia phase, mỗi phase gắn quality gate.`

Ra `plans/<YYMMDD-HHMM-slug>/plan.md` + `phase-XX-*.md`. **Đọc bảng phase trước khi duyệt.**

### Bước 5 — Vòng lặp thi công (một prompt là đủ)

> Prompt: `Triển khai lần lượt từng phase trong plan: agent fullstack-developer code → agent tester chạy test → agent debugger fix, lặp cho tới khi phase pass rồi mới sang phase kế. Build và test trên emulator. Commit sau mỗi phase pass. Có gì không hiểu hãy hỏi lại tôi.`

Ba chi tiết làm nên khác biệt giữa session 2 và session 4:

1. **Gọi đích danh tên agent** (`fullstack-developer`, `tester`, `debugger`) — nói "dev, test, fix" chung chung thì tôi làm inline.
2. **"cho tới khi... rồi mới sang phase kế"** — khoá được thứ tự, không nhảy cóc.
3. **"Có gì không hiểu hãy hỏi lại tôi"** — cho phép tôi dừng hỏi thay vì đoán bừa.

### Bước 6 — Quality gate + đồng bộ tài liệu

Phase cuối cùng của plan. Chạy đủ 3 tầng: JVM test → emulator + GPX → máy thật (chỉ cho gate cần).
Gate nào không chạy được thì **ghi HOÃN kèm lý do**, không đánh dấu pass khống.
Cùng commit đó cập nhật `LLM.md` §3/§11 và `docs/`.

### Bước 7 — Change request

Quay lại **bước 1 rút gọn**: mô tả hiện trạng sai + kỳ vọng đúng → tôi hỏi 1–2 câu chốt phương án
→ plan mới trong `plans/` → **lặp lại bước 5 với đúng chỉ thị gọi agent**.

> Prompt mẫu: `Hiện tại <mô tả sai>. Tôi muốn <kỳ vọng>. Hỏi tôi những gì cần chốt, rồi tạo plan mới và chạy vòng lặp fullstack-developer → tester → debugger như cũ.`

---

## Bốn bài học rút ra

| Bài học | Bằng chứng |
|---|---|
| Càng chốt kỹ ở bước 1 thì bước 5 càng chạy tự động | Session 1 hỏi 8 câu → session 2 chạy 17h chỉ với 1 prompt |
| Phải gọi đích danh tên agent | Session 2: 30 lượt agent · Session 4: 0 lượt, cùng một cách diễn đạt "dev, test, fix" |
| PRD thiếu "ai theo dõi ai" thì bug lọt tới tận lúc demo | Bug zone tự theo dõi bản thân, phát hiện sau khi cả 11 phase đã xong |
| Ràng buộc thiết bị phải khai trước khi lên plan | G4/G5 treo tới cuối vì không có máy thật mở khoá |

## Bảng tra nhanh

| Việc cần làm | Gọi gì |
|---|---|
| Chốt yêu cầu | Câu "hãy hỏi tôi các thông tin cần thiết" |
| Sinh PRD | `/product-requirements` |
| Sinh test case / UAT | `/qa-uat` |
| Tra thư viện, version | agent `researcher` (chạy song song nhiều agent) |
| Lên kế hoạch | agent `planner` |
| Viết code một phase | agent `fullstack-developer` |
| Chạy test một phase | agent `tester` |
| Sửa lỗi test/bug | agent `debugger` |
| Review trước khi merge | agent `code-reviewer` |
| Commit / push | agent `git-manager` hoặc `/git` |
| Cập nhật docs | agent `docs-manager` hoặc `/docs` |
