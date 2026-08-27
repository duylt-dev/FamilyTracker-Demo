# Phase 03 — `:ui`: Zone List hiện ai đang ở trong

**Ưu tiên:** P0 · **Trạng thái:** ✅ Hoàn thành

## Key Insight

1. **`ZoneListItem.isInside: Boolean` không đủ chỗ chứa câu trả lời mới.** Câu hỏi đổi từ "tôi có ở
   trong không" (nhị phân) sang "những ai đang ở trong" (danh sách). Thay bằng
   `membersInside: List<ZoneMemberChip>` mang sẵn `name` + `colorArgb` — dựng ở ViewModel, không để
   composable tự join member với zone (MVI doc §4).
2. **Màu chấm lấy từ `Member.colorArgb`, không từ theme.** Cùng nguồn màu với `MemberMarkers` trên
   bản đồ, nên chấm cam ở Zone List và marker cam trên Map là cùng một người — không cần chú giải.

## Related Code Files

**Sửa:**
- `ui/feature/zone/ZoneListContract.kt` — `ZoneMemberChip`, `membersInside`
- `ui/feature/zone/ZoneListViewModel.kt`
- `ui/feature/zone/component/ZoneRow.kt`
- `ui/res/values/strings.xml`
- `ui/src/test/.../zone/ZoneListViewModelTest.kt`

## Todo

- [x] `ZoneMemberChip` + `membersInside`
- [x] `ZoneRow` vẽ chấm màu + tên, hoặc "Chưa có ai trong zone"
- [x] Strings mới, xoá `zone_status_inside`/`zone_status_outside`
- [x] Test ViewModel viết lại

## Success Criteria

- Tạo zone quanh vị trí của mình → dòng zone hiện "Chưa có ai trong zone".
- Minh đi vào → dòng zone hiện chấm cam + "Minh".
