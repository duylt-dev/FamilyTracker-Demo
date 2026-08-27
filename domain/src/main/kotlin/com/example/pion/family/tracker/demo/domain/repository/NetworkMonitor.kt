package com.example.pion.family.tracker.demo.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * phase-07 (US-47, D8) — cổng tới một khả năng của nền tảng, không một "repository" theo nghĩa
 * Room, đặt cùng thư mục vì đây là tiền lệ thứ ba đúng hình dạng ([LocationSource], [RoutingProvider]
 * là hai cái trước). `:ui` không thấy `:data` (LLM.md §2), nên interface phải sống ở đây để
 * `MapViewModel` nhìn thấy được.
 */
interface NetworkMonitor {
    /**
     * `true` khi máy có internet ĐÃ KIỂM CHỨNG. Wifi đầy vạch nhưng chưa qua captive portal
     * trả `false` (QA-SRM-37).
     *
     * KHÔNG dùng cổng này để chọn tầng nguồn tuyến (D8, Key Insight #1): tầng do mã lỗi HTTP
     * quyết. Trộn hai đường vào nhau làm dialog "mất mạng" không bao giờ tự tắt được — điều kiện
     * tắt (có internet) đã đúng sẵn từ đầu, nên một 401 lỡ bật cờ này sẽ kẹt vĩnh viễn.
     * `InternetBlockerBoundaryTest` (`:data`) khoá luật này từ phía ngược lại.
     */
    fun observeHasInternet(): Flow<Boolean>
}
