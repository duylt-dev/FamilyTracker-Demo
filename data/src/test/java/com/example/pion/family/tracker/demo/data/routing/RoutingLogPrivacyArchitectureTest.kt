package com.example.pion.family.tracker.demo.data.routing

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gate **G7** — khoá việc `MemberRouteSource` thật sự ĐI QUA [sanitizeRoutingErrorMessage] trước khi
 * ghi `AppError.message` vào log. Cùng khuôn `RealGpsNoSnapArchitectureTest` (`data/location/`) và
 * `CoroutineSafetyArchitectureTest` (`:ui`): đọc mã nguồn, khẳng định một luật mà không test hành vi
 * nào chạm tới được.
 *
 * **Vì sao phải là test đọc-mã-nguồn, không phải test hành vi** (thêm ở lượt soát thứ hai của
 * `code-reviewer`, phase-04): `RoutingErrorSanitizerTest` chứng minh BỘ LỌC đúng, nhưng không
 * chứng minh nó ĐƯỢC GỌI. Đã đo bằng mutation: thay cả 4 lời gọi
 * `sanitizeRoutingErrorMessage(error.message)` trong `MemberRouteSource.reasonFor` bằng
 * `error.message.orEmpty()` ⇒ **301/301 test vẫn XANH**. Lý do không quan sát được qua hành vi:
 * `FtdLog` câm trong unit test (`debugBuild = false` mặc định) và bật nó lên thì
 * `android.util.Log` ném `not mocked` (`:data` không có Robolectric). Đường duy nhất còn lại để
 * khoá dây nối này là đọc chính mã nguồn.
 *
 * Toạ độ người dùng lọt vào logcat là vi phạm G7 (PRD §7.3, phase-04 Security Considerations), và
 * `message` là chuỗi do NHÀ CUNG CẤP sinh ra — body lỗi 400 của GraphHopper echo lại toạ độ vừa gửi.
 */
class RoutingLogPrivacyArchitectureTest {

    /**
     * Mọi dòng đọc `.message` trong `MemberRouteSource.kt` phải nằm trong một lời gọi
     * [sanitizeRoutingErrorMessage]. Bỏ bộ lọc ở bất kỳ nhánh `AppError` nào ⇒ ca này đỏ.
     */
    @Test
    fun `MemberRouteSource never reads an AppError message without sanitizing it`() {
        val violations = memberRouteSourceLines()
            .filter { (_, line) -> line.contains(".message") }
            .filterNot { (_, line) -> line.contains("sanitizeRoutingErrorMessage(") }
            .map { (number, line) -> "MemberRouteSource.kt:$number  ${line.trim()}" }

        assertTrue(
            "G7: `AppError.message` do nhà cung cấp sinh ra, có thể chứa toạ độ — phải qua " +
                "`sanitizeRoutingErrorMessage(...)` trước khi vào log:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /**
     * Bịt đường vòng của ca trên: `AppError` là `sealed class` với `data class` con, nên
     * `"reason=${result.error}"` in ra CẢ `message` mà không hề viết chữ `.message` nào. Một dòng
     * `FtdLog` chạm tới `.error` chỉ hợp lệ khi nó đi qua `reasonFor(...)` — nơi duy nhất lọc.
     */
    @Test
    fun `no FtdLog line interpolates a raw AppError value`() {
        val violations = memberRouteSourceLines()
            .filter { (_, line) -> line.contains("FtdLog.") && line.contains(".error") }
            .filterNot { (_, line) -> line.contains("reasonFor(") }
            .map { (number, line) -> "MemberRouteSource.kt:$number  ${line.trim()}" }

        assertTrue(
            "G7: in thẳng một `AppError` cũng in cả `message` của nó — phải đi qua `reasonFor(...)`:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /** Đánh số dòng từ 1, đúng như trình biên dịch báo, để thông điệp lỗi mở được thẳng bằng IDE. */
    private fun memberRouteSourceLines(): List<Pair<Int, String>> =
        File(findDataRoutingDir(), "MemberRouteSource.kt").readLines()
            .mapIndexed { index, line -> (index + 1) to line }

    /** Gradle chạy test với working dir là thư mục module (`data/`) — cùng mẫu
     * `RealGpsNoSnapArchitectureTest.findDataLocationDir()`. */
    private fun findDataRoutingDir(): File {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir = File(workingDir)
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/example/pion/family/tracker/demo/data/routing")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate data/routing from working dir $workingDir")
    }
}
