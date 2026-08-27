---
title: "FamilyTrackerDemo v1.0 — Geofence Zone & History Tracking"
description: "Dựng app demo Android 4 module từ template trống tới đủ 5 feature F1–F5 và qua 8 quality gate."
status: pending
priority: P1
effort: 69h
branch: main
tags: [android, geofencing, maps-compose, room, koin, mvi, multi-module]
created: 2026-08-21
---

# Kế hoạch triển khai FamilyTrackerDemo v1.0

**Nguồn phạm vi:** [`docs/FTD001_FamilyTrackerDemo_PRD.md`](../../docs/FTD001_FamilyTrackerDemo_PRD.md) **v1.2** — 36 user story, 5 feature, **8** quality gate.
**Hợp đồng kiến trúc:** [`LLM.md`](../../LLM.md) · [`docs/android-mvi-best-practices.md`](../../docs/android-mvi-best-practices.md)
**Bảng version có thẩm quyền:** [`research/VERSIONS-VERIFIED.md`](research/VERSIONS-VERIFIED.md) — **thắng mọi version trong 3 báo cáo researcher**.

## Ba quyết định của chủ dự án (2026-08-21) — đã ngấm vào toàn bộ plan

| # | Quyết định | Hệ quả trong plan |
|---|---|---|
| 1 | **Không có bảng `track_sessions`.** `TrackSession` là model `:domain`, **suy ra lúc đọc** bằng cách gom điểm cách nhau dưới `SESSION_GAP_MS` | 02 bỏ entity/DAO/bảng · 03 `RouteSplitter` cạnh `RouteStats` + 4 test bắt buộc · 08 gom lúc query · 11 kiểm schema đúng 4 bảng |
| 2 | **Bản đem demo là `release`** (đo hiệu năng Compose đúng môi trường thật) | `SIMULATOR_ENABLED` **không** gắn `BuildConfig.DEBUG` (09, 08, 10) · ký release bằng **debug keystore** (01) · R8 giữ **tắt**, chặn log bằng cờ (11) · **gate G8** mới (01, 11) · mọi lệnh đóng gói/cài đặt ở mọi phase dùng `assembleRelease`/APK release |
| 3 | **Emulator là vòng lặp test chính**, nhưng **G5 vẫn một lượt trên máy thật** | 11 dựng ba tầng kiểm thử (a) JVM · (b) emulator Google APIs + GPX · (c) máy thật chỉ cho G5 |

## Phase

| # | Phase | Feature / Story | Gate | Effort | Trạng thái |
|---|---|---|---|---|---|
| 01 | [Module skeleton + version catalog + ký release + lõi MVI](phase-01-module-skeleton-and-version-catalog.md) | — | G6 · **G8** | 5h | completed |
| 02 | [Domain model + Room schema (4 bảng) + repository + seed](phase-02-domain-model-and-room-persistence.md) | nền F1/F3/F4 | — | 6h | completed |
| 03 | [Thuật toán tracking thuần + unit test](phase-03-domain-tracking-algorithms.md) | US-25,26,29,30,31 | **G2** | 5h | completed |
| 04 | [Quyền 3 bước + nav shell + foreground service](phase-04-permissions-and-tracking-service.md) | US-01→05, US-09 | — | 8h | completed |
| 05 | [Màn Map + vẽ zone + member marker](phase-05-map-screen.md) | F1 · US-06→11 | — | 6h | completed |
| 06 | [Zone List + Zone Editor](phase-06-zone-list-and-editor.md) | F1 · US-12→21 | — | 8h | completed |
| 07 | [Geofence + notification + khử trùng lặp](phase-07-geofence-and-notification.md) | F2 · US-22→26 | **G5** | 6h | completed (G5 HOÃN — chờ máy thật) |
| 08 | [History + polyline + chuyến đi + thống kê](phase-08-history-and-route-playback.md) | F3 · US-27→32 | — | 8h | completed |
| 09 | [Route Simulator](phase-09-route-simulator.md) | F5 · US-33 | **G4** | 5h | completed (G4 đo trên emulator-5554, cần đo lại máy thật ở phase-11) |
| 10 | [Timeline](phase-10-zone-timeline.md) | F4 · US-34→36 | — | 4h | completed |
| 11 | [Ba tầng kiểm thử + gate G1–G8 + đồng bộ tài liệu](phase-11-quality-gates-and-docs.md) | tất cả | **G1–G8** | 8h | completed (G4/G5 HOÃN — cần máy thật mở khoá) |

## Phụ thuộc chính

```
01 ──▶ 02 ──▶ 03 ──▶ 04 ──▶ 05 ──▶ 06 ──▶ 07 ──▶ 09 ──▶ 11
                                    │       │      │
                                    └──▶ 08 ┘      └──▶ 10
```

- **03 phải xong trước mọi phase UI (05→10).** Toàn bộ luật vào/rời zone nằm ở `:domain`, test được
  bằng JUnit thuần. Để sau UI thì cách duy nhất kiểm tra một trường hợp biên là emulator + mock
  location — tức là thực tế không ai kiểm tra.
- **07 cần 06** (phải lưu được zone mới có gì để đăng ký geofence).
- **09 cần 07 và 08** (nút mô phỏng phải sinh ra thông báo thật và polyline thật).
- **08 chỉ cần 05** (dùng chung `GoogleMap`), chạy song song với 06/07 được nếu có 2 người.
- **10 cần 07** (Timeline đọc `zone_events`, cần có nguồn ghi sự kiện).

## Sườn khác đề xuất ban đầu

- **Đảo phase-03 ⇄ phase-04:** service là hộ tiêu thụ đầu tiên của `LocationFilter`/`ZoneEvaluator` — dựng nó trước thì hoặc viết stub rồi viết lại, hoặc nhét logic vào service rồi mới bóc ra.
- **Tách F1 thành 2 phase (05 Map, 06 Zone List+Editor):** F1 gồm 3 màn hình và 14 story; gộp một phase thì tiêu chí "xong" không kiểm chứng được từng phần.

## Ràng buộc xuyên suốt

- Mọi phase kết thúc bằng **một lệnh chạy được**. Lệnh đóng gói/cài đặt dùng **variant `release`**;
  `connected*AndroidTest` giữ `debug` (lý do ở phase-02); `assembleDebug` chỉ còn cho gate G6.
- Không hardcode version trong `build.gradle.kts` — tất cả qua `gradle/libs.versions.toml` (LLM.md §14).
- File code > 200 dòng phải tách (LLM.md §5).
- Sửa cấu trúc → cập nhật `LLM.md` **trong cùng commit**; sai lệch không sửa ngay → thêm dòng vào §13.

## Câu hỏi còn mở

| # | Câu hỏi | Chặn phase | Giả định đang dùng |
|---|---|---|---|
| Q-E | Thiết bị demo là máy nào? Xiaomi/Oppo/Vivo làm **G5** hỏng vì OEM chứ không vì code | 07, 11 | Pixel hoặc Samsung |

**Đã đóng:** chữ ký `ZoneEditorRoute`/`HistoryRoute` (PRD v1.2 §9 đã mở rộng đủ toạ độ cho US-10/US-35) ·
`allowBackup="false"` (PRD v1.2 §7.3) · số điểm mỗi ngày (PRD v1.2 §7.1: 8.640 thô) · bảng `track_sessions` ·
build đem demo · **Q-D** — `ZoneEventDeduper` tách thành hàm thuần ở `:domain/tracking/`, `LLM.md`
Phụ lục A.1 đã viết (phase-03). **Mâu thuẫn version giữa các báo cáo** ghi ở
[phase-01](phase-01-module-skeleton-and-version-catalog.md) Key Insights #2, #3, #7.
