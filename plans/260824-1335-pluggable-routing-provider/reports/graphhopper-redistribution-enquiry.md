# Câu hỏi gửi GraphHopper — điều khoản "redistribution"

**Trạng thái:** ⬜ Chưa gửi · **Chặn:** phát hành (Chặn #2b ở [plan.md](../plan.md)), **không** chặn code
**Soạn:** 2026-08-24 · **Người gửi:** _(điền)_ · **Ngày gửi:** _(điền)_ · **Ngày nhận trả lời:** _(điền)_

## Vì sao phải hỏi

ToS của GraphHopper: *"To redistribute the Directions API you need a custom package and agreement
with GraphHopper"* (researcher-02 §5). Câu này không định nghĩa "redistribute". App vẽ polyline do
GraphHopper trả về lên bản đồ Google — hành vi đó nằm ở đâu giữa "sử dụng" và "phân phối lại" là
điều **chỉ GraphHopper trả lời được**. Đây là rủi ro phía *nhà cung cấp routing*, khác hẳn và độc
lập với rủi ro phía Google (đã phân tích xong ở [`docs/routing-and-map-attribution.md`](../../../docs/routing-and-map-attribution.md) §2).

Đoán hộ họ thì rẻ và sai. Hỏi thì mất vài ngày và đóng được một mục.

## Gửi đi đâu

- Support form: <https://www.graphhopper.com/contact/> (chọn mục về API/licensing)
- Hoặc email support ghi trên <https://www.graphhopper.com/terms/>
- Kèm **API key ID** (không bao giờ kèm giá trị key) để họ tra đúng tài khoản free tier

## Nội dung — copy nguyên văn

> Subject: Does displaying Directions API polylines on a Google Maps basemap count as redistribution?
>
> Hello,
>
> We are building an Android family-location demo app that uses the Maps SDK for Android as its
> only basemap. We call the Directions API (free tier, `profile=car`) to compute a route between
> two family members, decode the returned `points` polyline, and draw it as a single overlay
> polyline on that Google basemap. We display the `info.copyrights` values your API returns
> ("GraphHopper", "OpenStreetMap contributors") as visible on-screen attribution, in a strip
> outside the map frame so it does not overlap Google's own attribution.
>
> We do not cache, store, resell, or expose the route data through any API of our own. The route
> is held in memory for the duration of one navigation session and is never written to disk.
>
> Your Terms state: "To redistribute the Directions API you need a custom package and agreement
> with GraphHopper."
>
> Our question: **does rendering your Directions API polyline on a third-party basemap (Google
> Maps) inside our own end-user app count as "redistribution" under that clause, or is it ordinary
> use of the API?**
>
> Two follow-ups, in case the answer depends on them:
>
> 1. Does the answer change if the app is published publicly (free, non-commercial) rather than
>    used only internally for a demo?
> 2. Is there any attribution wording or placement you require beyond displaying the strings you
>    already return in `info.copyrights`?
>
> Thank you.

## Ba kết quả có thể, và mỗi kết quả thì làm gì

| Trả lời | Hệ quả | Hành động |
|---|---|---|
| **"Đó là sử dụng bình thường"** | Đóng Chặn #2b. GraphHopper vẫn là mặc định | Dán nguyên văn trả lời + ngày vào `docs/routing-and-map-attribution.md` §5, đổi ⬜ thành ✅ |
| **"Cần gói trả phí / thoả thuận riêng"** | Free tier không ship được | Đổi `ROUTING_ENGINE=VALHALLA`. Đường thoát đã có sẵn trong code từ phase-03 — không phải sửa `:domain` hay `:ui` dòng nào. Nếu Stadia cũng vướng thì self-host, nơi không còn bên thứ ba nào để ràng buộc |
| **Không trả lời sau 2 tuần** | Vẫn là chỗ mơ hồ | Giữ Open trong `LLM.md` §13. **Không** coi im lặng là đồng ý. Bản demo nội bộ chạy tiếp; bản phát hành công khai thì dùng Valhalla |

## Lưu ý khi gửi

- **Không dán giá trị `GRAPHHOPPER_API_KEY` vào email.** Key ID hoặc tên tài khoản là đủ.
- Lưu lại **nguyên văn** trả lời, không phải bản tóm tắt. Mục 6 điều kiện #5 của memo đòi ghi lại
  ngày kiểm tra điều khoản; một bản tóm tắt không đứng được khi ai đó đọc lại sau sáu tháng.
- Trả lời về rồi thì cập nhật đúng ba chỗ: file này, `docs/routing-and-map-attribution.md` §5,
  và `LLM.md` §13.
