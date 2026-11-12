# Báo cáo CSDL

Ngày kiểm tra: 12/11/2026. Schema hiện hành giữ **11 bảng**, đúng ER đã xác nhận. Không thêm bảng báo cáo, cột thống kê hay quan hệ mới.

## Phạm vi

CSDL quản lý hồ sơ, hợp đồng, đăng ký, trận đấu, lần ra sân, y tế và chuyển nhượng của CLB. Q01–Q15 chỉ đọc dữ liệu theo CLB, mùa và ngày báo cáo; không tạo bảng kết quả riêng.

`update-db.bat --check` kiểm tra dữ liệu hiện có, không chạy DDL cập nhật. Trạng thái của file migration trong repo không thay thế việc đối chiếu trigger và cột trên DB đang dùng.

## 15 truy vấn mẫu

`db/05_reports.sql` chứa 15 câu `SELECT` phục vụ ứng dụng, không phải file tạm sinh ra để kiểm thử. Ứng dụng đọc marker `Q01`–`Q15` để hiển thị ở tab Báo cáo.

File SQL nằm trong repo; dữ liệu nguồn nằm trong 11 bảng MySQL. Các truy vấn chưa được lưu thành view hoặc thủ tục trong DB. Chạy file sẽ tính và trả kết quả từ dữ liệu hiện có; không tạo bảng báo cáo, không thêm/sửa bản ghi. Có thể chạy trực tiếp trong MySQL mà không cần mở web hay có token API.

| Query | Nội dung |
| --- | --- |
| Q01 | Cầu thủ đang đăng ký cho CLB ở mùa và ngày báo cáo; gồm vị trí, số áo và thời hạn đăng ký. |
| Q02 | Lịch và kết quả mọi trận của CLB trong mùa, gồm trận sắp tới. |
| Q03 | Thành tích CLB từ trận `COMPLETED`: số trận, thắng, hòa, thua, bàn, hiệu số và điểm. Đây không phải BXH toàn EPL. |
| Q04 | Danh sách cầu thủ ghi bàn cho CLB trong mùa đến ngày báo cáo. |
| Q05 | Top 10 cầu thủ có tổng phút thi đấu cao nhất trong mùa. |
| Q06 | Tổng trận, phút, kiến tạo, thẻ vàng và thẻ đỏ theo cầu thủ; kiến tạo giữ `NULL` nếu một thành phần chưa biết. |
| Q07 | Cầu thủ còn đăng ký nhưng chưa có lần ra sân `COMPLETED` trong mùa. |
| Q08 | Cầu thủ đang có đợt chấn thương cấm thi đấu tại ngày báo cáo. |
| Q09 | Hợp đồng đã biết ngày bắt đầu và sắp hết hạn trong 90 ngày; ngày kết thúc là biên loại trừ. |
| Q10 | Thành viên ban huấn luyện còn nhiệm kỳ tại CLB ở ngày báo cáo. |
| Q11 | Toàn bộ lịch sử hợp đồng của CLB, không giới hạn một mùa. |
| Q12 | Cầu thủ có đăng ký và hợp đồng hợp lệ, không có chấn thương đang cấm thi đấu. Đây không phải kết luận y tế. |
| Q13 | Số lần gặp từng đối thủ qua các mùa đến ngày báo cáo. |
| Q14 | Phân bố vị trí của cầu thủ đang đăng ký; mỗi cầu thủ được tính một lần. |
| Q15 | Các giao dịch chuyển nhượng liên quan CLB; phí `NULL` nghĩa là chưa biết. |

Kết quả trên seed demo (`2025-26`, ngày `2026-05-24`) lần lượt có số dòng: **33, 38, 1, 14, 10, 27, 6, 0, 0, 1, 34, 33, 19, 4, 5**. Q08 và Q09 rỗng vì seed không có ca chấn thương đang cấm hoặc hợp đồng đã biết sắp hết hạn; không nên thêm dữ liệu giả chỉ để làm bảng có dòng.

## Có nên lưu kết quả vào DB?

**Không cần tạo 15 bảng kết quả, view vật hóa hoặc bảng phụ.** Kết quả phụ thuộc mùa, CLB và ngày báo cáo; lưu bản sao sẽ dễ cũ, lặp dữ liệu và làm lệch ER. Cách hiện tại phù hợp để trình bày:

1. Giữ câu SQL có marker trong `db/05_reports.sql`.
2. Chạy file trong Workbench khi cần xem trực tiếp.
3. Mở ứng dụng để chọn mùa/ngày; backend truyền tham số và tab Báo cáo hiển thị kết quả.

Seed hiện có đủ dữ liệu để minh họa các nhóm đăng ký, trận, lần ra sân, hợp đồng, BHL, đối thủ và chuyển nhượng. Không sửa lịch sử hoặc điền `0` thay cho giá trị `NULL`. Chỉ cân nhắc view/thủ tục riêng nếu sau này có yêu cầu vận hành cụ thể; việc đó không cần cho bài hiện tại.

## Cấu trúc và quy tắc giữ nguyên

11 bảng: `ViTri`, `CauThu`, `CauLacBo`, `HopDong`, `MuaGiai`, `CauThu_MuaGiai`, `ThanhVienBHL`, `TranDau`, `CauThu_TranDau`, `ChanThuong`, `ChuyenNhuong`.

- Hợp đồng và đăng ký dùng khoảng `[ngay_bat_dau, ngay_ket_thuc)`; không chồng lấn.
- Lần ra sân giữ khóa ghép `(tran_dau_id, cau_thu_id)`; migration 003 chặn đổi định danh, vẫn cho sửa thống kê hợp lệ.
- Trận chưa `COMPLETED` giữ tỷ số `NULL`; kiến tạo chưa biết giữ `NULL`.
- Tiếp nhận và chuyển nhượng kiểm tra liên kết trong một giao dịch; lỗi phải rollback.
- Không dùng provider ID API thay cho ID DB, không tạo hợp đồng/đăng ký từ dữ liệu API thiếu định danh.

## Tài liệu và cách chạy

- Schema, trigger, procedure, seed, báo cáo: `db/01_schema.sql` đến `db/05_reports.sql`.
- Cài đặt, cấu hình và chạy từ máy mới: [README.md](README.md).

Trong Workbench có thể chạy:

```sql
USE csdl1_mu_2025_26;
SET @season_id=(SELECT id FROM MuaGiai WHERE ma_mua_giai='2025-26');
SET @club_id=(SELECT id FROM CauLacBo WHERE la_clb_quan_ly=TRUE);
SET @as_of=DATE('2026-05-24');
```

Sau đó chỉ chạy phần `Q01`–`Q15` cần xem trong cùng kết nối. Chạy lại toàn file sẽ đặt lại tham số về mùa/ngày minh họa ở đầu file. Truy vấn SQL chỉ đọc dữ liệu đã lưu trong DB, không đọc cache BXH hoặc ESPN của Java.

Với DB cũ, dùng `app/update-db.bat --check` để kiểm tra trước; không chạy lại `submission.sql`, `01_schema.sql` hoặc `04_seed.sql` trên CSDL đang có dữ liệu.
