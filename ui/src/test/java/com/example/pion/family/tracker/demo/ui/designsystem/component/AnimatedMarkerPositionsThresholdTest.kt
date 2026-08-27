package com.example.pion.family.tracker.demo.ui.designsystem.component

import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.ui.core.motion.isSpawnJump
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Khoá GIÁ TRỊ của ngưỡng snap (quyết định A / F-6), không phải phép so sánh — phép so sánh đã do
 * `MarkerInterpolationTest` khoá.
 *
 * **Vì sao cần một file riêng cho đúng hai hằng số.** `MarkerInterpolationTest` gọi
 * `isSpawnJump(distance, threshold)` với ngưỡng truyền vào như một tham số, nên mỗi ca ở đó tự mang
 * hằng số của riêng nó. Hệ quả: đổi [SPAWN_SNAP_THRESHOLD_M] trong mã sản phẩm từ 207.5 thành 2.0
 * thì **toàn bộ test hiện có vẫn XANH**, trong khi bản sửa F-6 đã chết — mọi bước đi bình thường bị
 * hiểu nhầm là spawn, marker ngừng nội suy góc và đứng im một hướng. Đây đúng loại "test không thể
 * đỏ" mà reviewer phase-02 đã bắt ở F-2/F-5; ghi lại ở đây để không tái diễn.
 *
 * **[NORMAL_TICK_STEP_M] được TÍNH LẠI từ `TrackingConstants`, không so với 20.75 chép tay.** Đó là
 * điểm chính của file này: `SIM_MEMBER_SPEED_MPS` **sẽ bị đổi** — phase-06/B4 giao đúng việc chốt
 * lại con số đó sau khi đo vòng thật (`decisions.md` §C5). Khi điều đó xảy ra, 20.75 trở thành một
 * giá trị dẫn xuất đã mục, và ca dưới đây ĐỎ ngay, buộc người đổi phải xem lại ngưỡng snap thay vì
 * để nó rữa âm thầm.
 */
class AnimatedMarkerPositionsThresholdTest {

    @Test
    fun `NORMAL_TICK_STEP_M stays derived from the real domain constants, not a stale copy`() {
        val derived = TrackingConstants.SIM_MEMBER_SPEED_MPS *
            TrackingConstants.MEMBER_ROAM_INTERVAL_MS / MILLIS_PER_SECOND
        assertEquals(
            "NORMAL_TICK_STEP_M không còn khớp SIM_MEMBER_SPEED_MPS x MEMBER_ROAM_INTERVAL_MS. " +
                "Nếu bạn vừa đổi tốc độ mô phỏng (phase-06/B4), hãy tính lại ngưỡng snap của F-6 " +
                "trong MarkerSpawnThreshold.kt thay vì sửa con số trong test này.",
            derived,
            NORMAL_TICK_STEP_M,
            1e-9,
        )
    }

    @Test
    fun `SPAWN_SNAP_THRESHOLD_M is ten times one continuous step`() {
        assertEquals(NORMAL_TICK_STEP_M * 10.0, SPAWN_SNAP_THRESHOLD_M, 1e-9)
    }

    /**
     * Ngưỡng phải nằm HẲN giữa hai đại lượng vật lý, không chỉ "là một số nào đó": trên cận trên
     * của một bước đi liên tục, và dưới cận dưới của một cú spawn thật. Vi phạm bên nào cũng hỏng
     * theo một kiểu khác nhau — quá thấp thì bước đi bình thường bị nhầm là spawn (mất nội suy góc),
     * quá cao thì cú spawn bị nhận nhầm là bước đi (marker "trượt" 2 km qua thành phố).
     */
    @Test
    fun `SPAWN_SNAP_THRESHOLD_M separates a normal step from the smallest real spawn`() {
        assertFalse(
            "một bước đi liên tục dài nhất không được bị coi là spawn",
            isSpawnJump(NORMAL_TICK_STEP_M, SPAWN_SNAP_THRESHOLD_M),
        )
        assertTrue(
            "cú spawn ngắn nhất có thể phải được nhận ra là spawn",
            isSpawnJump(SMALLEST_REAL_SPAWN_M, SPAWN_SNAP_THRESHOLD_M),
        )
        assertTrue(
            "ngưỡng phải nằm hẳn giữa hai cận, không chạm cận nào",
            SPAWN_SNAP_THRESHOLD_M > NORMAL_TICK_STEP_M && SPAWN_SNAP_THRESHOLD_M < SMALLEST_REAL_SPAWN_M,
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0

        /**
         * `MemberRoamer.MAX_WALK_M` (5 000 m) − `approachRadiusMeters` lớn nhất
         * (`ZONE_RADIUS_MAX_M` × 1.4 + `LEAVE_MARGIN_M` = 2 920 m) = 2 080 m.
         *
         * Chép tay CÓ CHỦ Ý, không tính lại được như [NORMAL_TICK_STEP_M]: `MAX_WALK_M` và
         * `LEAVE_MARGIN_M` là `internal` trong `:domain`, và `internal` của Kotlin có phạm vi theo
         * module Gradle nên `:ui` không thấy chúng — cùng ranh giới đã buộc `haversineMeters` phải
         * nhân bản (LLM.md §13 Open #12). Mở chúng thành `public` chỉ để một test ở module khác đọc
         * được thì phá đúng ranh giới đó, nên ở đây chấp nhận bản chép, kèm biên an toàn ~10× ở hai
         * phía để một thay đổi nhỏ ở `:domain` không lặng lẽ làm sai ca này.
         */
        const val SMALLEST_REAL_SPAWN_M = 2_080.0
    }
}
