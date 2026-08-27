package com.example.pion.family.tracker.demo.data.routing

/**
 * **Gate G7 (2026-08-26, code review phase-04 "VIỆC A") — `message` của `AppError` có thể là chuỗi
 * do CHÍNH nhà cung cấp sinh ra, và body lỗi 400 thường echo lại toạ độ vừa gửi lên** (ví dụ
 * `"Point 10.762081,106.660172 is not in the routing graph"`). Log nguyên văn message đó là đưa
 * toạ độ người dùng vào logcat — vi phạm thẳng gate G7 (PRD §7.3, phase-04 Security Considerations)
 * dù tiền tố loại lỗi (`NETWORK:`/…) tự nó không rò gì. Toạ độ luôn ở dạng số thập phân
 * (`[-+]?\d+\.\d+`), nên xoá mọi cụm khớp mẫu đó khỏi message trước khi ghép tiền tố — một câu
 * tiếng Anh không mang số thập phân (ví dụ "No API key specified. Please register and see
 * documentation: https://www.graphhopper.com/developers/", đo thật khi khoá rỗng) đi qua NGUYÊN
 * VẸN. Cắt về [maxLength] (mặc định 120 ký tự) để một body lỗi dài không làm phình dòng log.
 *
 * **G-2 (code review lượt hai) — bản đầu chỉ xoá số thập phân, KHÔNG xoá `key=<...>`.** Repo đã coi
 * "không log URL vì nó mang `key=`" là luật thành văn (`GraphHopperRoutingProvider.buildUrl` gắn
 * thẳng `config.graphHopperApiKey` vào query string; `RoutingErrorMapper`'s KDoc ghi rõ "never logs
 * the request URL, which carries `key=`" — bộ lọc lỗi ĐẦU không giữ đúng lời hứa đó cho message của
 * nhà cung cấp: chưa tìm được đường rò thật (GraphHopper không echo `key=` vào body lỗi ở các mã đã
 * quan sát), nhưng nếu một phiên bản API sau này làm vậy thì bộ lọc phải đã chặn sẵn — phòng trước,
 * không đợi đo được rò rỉ thật mới sửa. [API_KEY_LIKE_PATTERN] xoá `key=` (không phân biệt hoa/thường)
 * cùng giá trị của nó tới ký tự khoảng trắng hoặc `&` kế tiếp.
 *
 * `internal` (không `private`), tách riêng khỏi `MemberRouteSource.kt` (giữ file đó dưới 200 dòng,
 * `.claude/rules/development-rules.md`) để `MemberRouteSourceTest` (cùng module `:data`) test được
 * bộ lọc trực tiếp, không phải suy luận qua log không quan sát được (`FtdLog` câm ở test,
 * `debugBuild = false` mặc định).
 */
internal fun sanitizeRoutingErrorMessage(message: String?, maxLength: Int = 120): String =
    message.orEmpty()
        .replace(COORDINATE_LIKE_PATTERN, "")
        .replace(API_KEY_LIKE_PATTERN, "")
        .take(maxLength)

private val COORDINATE_LIKE_PATTERN = Regex("""[-+]?\d+\.\d+""")

/** `key=` không phân biệt hoa/thường, giá trị dừng ở khoảng trắng hoặc `&` — khớp cả một tham số
 * URL query đơn lẻ (`key=AIzaSy...`) lẫn giữa hai tham số khác (`...&key=abc123&point=...`). */
private val API_KEY_LIKE_PATTERN = Regex("""(?i)key=[^&\s]+""")
