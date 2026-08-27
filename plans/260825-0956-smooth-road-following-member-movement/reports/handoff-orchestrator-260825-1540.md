# Bàn giao orchestrator — 2026-08-25 15:40

Dừng theo yêu cầu ("clear agent để tiết kiệm token"). Không agent nào của phiên này còn chạy.

## Đã chốt với chủ dự án (4 câu hỏi + 2 câu hỏi xung đột)

1. Phạm vi: đủ 6 phase, thứ tự `02 → 03 → 04 → 05 → 07 → 06`.
2. Đội hình: 6 agent thường trú (dev/simplifier/tester/reviewer/PM/debugger), tái dùng qua
   SendMessage. Trần 12 subagent. **Không dùng Workflow** (sẽ vượt trần).
3. Commit: orchestrator commit sau khi reviewer đóng từng phase; code + `LLM.md`/PRD **cùng một
   commit** (luật `.claude/CLAUDE.md`). Không push.
4. Phase 04: **bật tầng 1/2 mặc định**; ghi rõ B1 vẫn chặn PHÁT HÀNH vào `LLM.md` §13 Open.
5. Xung đột phase 02: **để session kia viết xong, dev tiếp quản phần còn lại** (không viết lại).
6. Lỗi tốc độ: **giãn đỉnh `SyntheticPath`**, không sửa `PolylineFollower`.

## Chặn đã tự đóng, không cần hỏi lại

- **B3** hết chặn — máy thật `SM-A165F` (serial `RF8Y60B9NCZ`) đang cắm USB → UAT-05 + S14 nghiệm
  thu thật được.
- **B5** hết điểm chờ — phase-07 Risk #1 đã chốt `NoInternetOverlay`, KHÔNG phải `AlertDialog`.
- **GA-3** xanh: `./gradlew :domain:test :data:test :ui:test :app:test --no-configuration-cache`
  → BUILD SUCCESSFUL trên `main` lúc 15:0x, TRƯỚC khi bất kỳ ai sửa phase 02.

## Trạng thái working tree khi dừng (15:37)

Phase 02 do **một session Claude khác** viết (không phải agent của tôi — dev của tôi ghi 0 file,
đúng luật file-ownership). Session đó **vẫn đang chạy lúc 15:37:03**.

```
M  domain/.../MemberRoamer.kt · TrackingConstants.kt · MemberRoamerTest.kt
M  data/.../MemberMovementSimulator.kt · SimulatedLocationSource.kt · DemoDataSeeder.kt
   · MemberMovementSimulatorTest.kt
?? domain/.../GeoBearing · PolylineFollower · SyntheticPath · RouteGeometryGuard
   · MemberRoamerModel · MemberRoamerGeometry  (+ 4 file test)
```

**CHƯA ai chạm:** `LLM.md`, PRD delta, file `phase-02-*.md` (Todo List chưa tick, trạng thái vẫn
`pending`), `reports/dev-phase-02-report.md` chưa tồn tại.

## KHUYẾT TẬT đã kiểm chứng trong code — chưa sửa

`SyntheticPath.kt:53` đặt khoảng cách đỉnh `= STEP_METERS / 2` ≈ **10.375m**; `PolylineFollower.kt`
`advance()` dừng ở đỉnh ĐẦU TIÊN trong `(cursor, cursor+step]`. Giao của hai luật ⇒ trên
`SyntheticPath` **mọi tick chỉ đi 10.375m**, không bao giờ 20.75m ⇒ tốc độ thực **4.15 m/s**, đúng
một nửa `SIM_MEMBER_SPEED_MPS = 8.3`. `speedMps` ghi xuống Room cũng sai một nửa, và phép đo vòng
của **B4/phase-06 sẽ lệch 2×**.

Cách sửa đã chốt: giãn đỉnh `SyntheticPath` lên `~2 × STEP_METERS`, giữ nguyên bảo toàn đỉnh (D2),
thêm test khoá "một tick đi đủ 20.75m".

**Giới hạn của cách sửa này — phải ghi vào `LLM.md` §13 Open, không được giấu:** nó KHÔNG cứu được
phase 04. Polyline OSM thật có đỉnh cách ~5m ⇒ cùng cơ chế sẽ kéo tốc độ xuống ~2 m/s. Phase 04
phải tự giải quyết ở đúng chỗ của nó.

## Việc tiếp theo khi chạy lại

1. Kiểm tra session kia đã dừng chưa (so `stat -f %m` các file `.kt`; **KHÔNG dùng `find -newermt`**
   — không hoạt động trên BSD find của macOS, trả rỗng và cho kết luận "lặng" sai).
2. Spawn dev → audit phase 02 của session kia theo spec, sửa lỗi tốc độ, đóng nợ tài liệu.
3. simplifier → tester → reviewer → commit → phase 03.
