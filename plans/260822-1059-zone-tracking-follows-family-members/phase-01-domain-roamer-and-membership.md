# Phase 01 — `:domain`: roamer thuần + membership tính theo thành viên được follow

**Ưu tiên:** P0 · **Trạng thái:** ✅ Hoàn thành

## Key Insight

1. **"Ai đang ở trong zone" là câu hỏi hình học thuần** — không cần trạng thái tích luỹ. Giữ nguyên
   cách gọi `ZoneEvaluator.evaluate(point, zones, previouslyInside = emptySet()).insideAfter` của
   bản cũ, chỉ đổi *tập member* được hỏi: mọi member `!isSelf` thay vì đúng self.
2. **Bộ mô phỏng di chuyển phải là hàm thuần ở `:domain/tracking/`** — cùng luật với `RouteBlueprint`
   và `ZoneEvaluator` (`LLM.md` §8.2): quyết định "bước tiếp theo đi đâu" test được bằng JUnit,
   không cần emulator.
3. **Không đi bộ xuyên lục địa.** `DemoDataSeeder` đặt Minh/Lan ở trung tâm TP.HCM, nhưng zone người
   dùng tạo nằm ở vị trí GPS thật của máy — có thể cách hàng nghìn km (emulator mặc định ở
   Mountain View). Nếu chỉ "đi từng bước 50m về phía đích" thì không bao giờ tới.
   → `MemberRoamer.tick` dời thẳng thành viên tới rìa ngoài zone khi đích xa hơn `MAX_WALK_M`, rồi
   để vòng sau đi bộ vào. **Ranh giới vẫn được cắt qua thật** — chỉ bỏ qua chặng đường dài.
4. **Dừng chân trong zone (dwell) không phải chi tiết thẩm mỹ mà là điều kiện đúng đắn.** Cửa sổ khử
   trùng lặp là 60s cho khoá `(zoneId, memberId, type)` (`EVENT_DEDUPE_WINDOW_MS`). Vào rồi ra rồi
   vào lại cùng một zone trong dưới 60s sẽ bị `ZoneEventDeduper` nuốt mất lần ENTER thứ hai. Dwell
   20 tick × 2.5s = 50s đẩy một chu kỳ đầy đủ lên ~80s, nằm ngoài cửa sổ.

## Related Code Files

**Tạo:**
- `domain/tracking/MemberRoamer.kt`
- `domain/src/test/kotlin/.../domain/tracking/MemberRoamerTest.kt`

**Sửa:**
- `domain/usecase/ObserveZoneMembershipUseCase.kt` — trả `Map<zoneId, List<Member>>`
- `domain/repository/MemberRepository.kt` — thêm `recordLocation(memberId, point)`
- `domain/tracking/TrackingConstants.kt` — thêm `MEMBER_ROAM_INTERVAL_MS`
- `domain/usecase/SaveZoneUseCase.kt`, `DeleteZoneUseCase.kt`, `StartSimulationUseCase.kt` — bỏ `GeofenceGateway`

**Xoá:**
- `domain/repository/GeofenceGateway.kt`

## Todo

- [x] `MemberRoamer` + `RoamState`/`RoamTarget`
- [x] `ObserveZoneMembershipUseCase` trả map member
- [x] `MemberRepository.recordLocation`
- [x] Gỡ `GeofenceGateway` khỏi 3 use case
- [x] Test: `MemberRoamerTest`, `ObserveZoneMembershipUseCaseTest` viết lại

## Success Criteria

- `./gradlew :domain:test` xanh.
- `MemberRoamer` không import `android.*` (luật `LLM.md` §8.2).
