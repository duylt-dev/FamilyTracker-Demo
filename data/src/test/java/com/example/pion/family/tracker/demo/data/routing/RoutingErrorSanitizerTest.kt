package com.example.pion.family.tracker.demo.data.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Gate G7 (code review phase-04 "VIỆC A"/"G-2") — `AppError.message` của GraphHopper có thể echo lại
 * toạ độ vừa gửi lên trong body lỗi 400; log nguyên văn là rò toạ độ người dùng ra logcat.
 * `sanitizeRoutingErrorMessage` phải xoá đúng những thứ cần xoá (số thập phân, `key=<...>`) và giữ
 * nguyên phần còn lại.
 */
class RoutingErrorSanitizerTest {

    @Test
    fun `a message with coordinate-like decimal numbers has them stripped`() {
        val sanitized = sanitizeRoutingErrorMessage("Point 10.762081,106.660172 is not in the routing graph")

        assertFalse(
            "không được còn cụm số thập phân nào — đó là dạng toạ độ",
            Regex("""[-+]?\d+\.\d+""").containsMatchIn(sanitized),
        )
    }

    /** Đo thật khi build với khoá rỗng — câu này không mang số thập phân nên phải đi qua NGUYÊN VẸN. */
    @Test
    fun `a message with no decimal numbers passes through untouched`() {
        val message = "No API key specified. Please register and see documentation: " +
            "https://www.graphhopper.com/developers/"

        assertEquals(message, sanitizeRoutingErrorMessage(message))
    }

    /** G-2 — repo đã coi "không log URL vì nó mang `key=`" là luật (KDoc `RoutingErrorMapper`);
     * bộ lọc phải giữ đúng lời hứa đó cho message của nhà cung cấp, không chỉ cho URL request. */
    @Test
    fun `a message embedding a key query param has the key and its value stripped`() {
        val sanitized = sanitizeRoutingErrorMessage("failed for url https://graphhopper.com/api/1/route?point=1,2&key=AIzaSyD_realsecretvalue&profile=car")

        assertFalse("key= không được còn trong message đã lọc", sanitized.contains("key=", ignoreCase = true))
        assertFalse("giá trị của key cũng không được sót lại", sanitized.contains("AIzaSyD_realsecretvalue"))
    }

    @Test
    fun `a standalone key=value with no surrounding URL is also stripped`() {
        val sanitized = sanitizeRoutingErrorMessage("Invalid key=abc123secret supplied")

        assertFalse(sanitized.contains("abc123secret"))
    }

    @Test
    fun `a null message sanitizes to an empty string`() {
        assertEquals("", sanitizeRoutingErrorMessage(null))
    }

    @Test
    fun `a message longer than maxLength is truncated`() {
        val long = "a".repeat(200)

        assertEquals(50, sanitizeRoutingErrorMessage(long, maxLength = 50).length)
    }
}
