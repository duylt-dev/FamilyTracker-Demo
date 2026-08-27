# Fixture cho test routing

Response **thật**, lấy bằng `curl` chứ không tự bịa — JSON bịa chỉ test được mapper chống lại
trí tưởng tượng của người viết test (plan 260824-1335, phase-02 Implementation Step 2).

| File | Nguồn | Ngày lấy | Lệnh |
|---|---|---|---|
| `graphhopper-route-hanoi.json` | GraphHopper Cloud, `profile=car`, free tier | 2026-08-24 | `curl 'https://graphhopper.com/api/1/route?point=21.0285,105.8542&point=21.0378,105.8342&profile=car&locale=vi&key=KEY'` |
| `valhalla-route-hanoi.json` | FOSSGIS `valhalla1.openstreetmap.de`, `costing=auto`, `units=kilometers` | 2026-08-24 | `curl -X POST 'https://valhalla1.openstreetmap.de/route' -H 'Content-Type: application/json' -d '{"locations":[{"lat":21.0285,"lon":105.8542},{"lat":21.0378,"lon":105.8342}],"costing":"auto","units":"kilometers"}'` |

Cùng một cặp điểm (Hồ Gươm → Văn Miếu) cho cả hai, để so được kết quả hai engine.

**Không chứa API key** — GraphHopper không echo key vào response. Kiểm lại bằng
`grep -r "key" data/src/test/resources/` nếu thay fixture mới.

Fixture là **ghim hợp đồng**, không phải giám sát API. Nó cũ đi so với API thật là chuyện bình
thường; chỉ thay khi hợp đồng đổi thật, và khi thay thì cập nhật ngày ở bảng trên.
