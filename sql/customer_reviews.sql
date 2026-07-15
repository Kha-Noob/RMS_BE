-- Customer Reviews SQL data
INSERT INTO customer_reviews (id, customer_name, customer_phone, rating, comment, order_id, branch_id, created_at) VALUES
(1, 'Nguyễn Văn Hùng', '0912345601', 5, 'Món ăn ngon, phục vụ tốt!', 1, '01-2thang9', '2026-06-24 09:30:00'),
(2, 'Trần Thị Lan', '0912345602', 4, 'Không gian đẹp, đồ ăn ổn', 2, '01-2thang9', '2026-06-24 09:45:00'),
(3, 'Lê Hoàng Nam', '0912345603', 5, 'Rất hài lòng, sẽ quay lại', 3, '01-2thang9', '2026-06-24 10:30:00'),
(4, 'Vũ Quốc Anh', '0912345606', 4, 'Đồ ăn ngon, chờ hơi lâu', 4, '11-NguyenHuuTho', '2026-06-24 10:00:00'),
(5, 'Đặng Thùy Chi', '0912345607', 5, 'Phục vụ tuyệt vời!', 5, '11-NguyenHuuTho', '2026-06-24 11:30:00'),
(6, 'Bùi Tiến Dũng', '0912345608', 3, 'Đồ ăn bình thường', 6, '11-NguyenHuuTho', '2026-06-24 11:45:00'),
(7, 'Hồ Bảo Trâm', '0912345611', 4, 'Không gian thoáng, sạch sẽ', 7, '21-HaiPhong', '2026-06-24 11:00:00'),
(8, 'Dương Chí Kiên', '0912345612', 5, 'Món nướng tuyệt hảo!', 8, '21-HaiPhong', '2026-06-24 11:30:00'),
(9, 'Lý Kim Ngọc', '0912345613', 4, 'Giá hợp lý, đồ ăn ngon', 9, '21-HaiPhong', '2026-06-24 12:30:00'),
(10, 'Trịnh Gia Huy', '0912345616', 5, 'Quán rất đẹp, đồ ăn ngon', 10, '01-2thang9', '2026-06-23 11:00:00'),
(11, 'Đinh Ngọc Diệp', '0912345617', 4, 'Phục vụ nhanh, đồ ăn ổn', 11, '01-2thang9', '2026-06-23 12:00:00'),
(12, 'Lâm Quốc Khánh', '0912345618', 5, 'Rất tốt, quay lại lần nữa', 12, '11-NguyenHuuTho', '2026-06-23 11:30:00'),
(13, 'Cao Mỹ Linh', '0912345619', 3, 'Đồ ăn ổn, phục vụ hơi chậm', 13, '11-NguyenHuuTho', '2026-06-23 12:30:00'),
(14, 'Mai Hữu Phước', '0912345620', 4, 'Đồ ăn ngon, không gian đẹp', 14, '21-HaiPhong', '2026-06-23 11:00:00'),
(15, 'Phạm Minh Tuấn', '0912345604', 4, 'Món khai vị rất ngon', NULL, '01-2thang9', '2026-06-24 14:00:00'),
(16, 'Ngô Thanh Hằng', '0912345609', 3, 'Đồ ăn ổn', NULL, '11-NguyenHuuTho', '2026-06-24 15:00:00'),
(17, 'Phan Thanh Sơn', '0912345614', 4, 'Phục vụ tốt, đồ ăn ngon', NULL, '21-HaiPhong', '2026-06-24 13:00:00'),
(18, 'Hoàng Thu Thảo', '0912345605', 5, 'Rất hài lòng!', NULL, '01-2thang9', '2026-06-24 16:00:00'),
(19, 'Đỗ Minh Đức', '0912345610', 4, 'Đồ ăn ngon, giá hợp lý', NULL, '11-NguyenHuuTho', '2026-06-24 16:30:00'),
(20, 'Võ Hồng Nhung', '0912345615', 5, 'Không gian đẹp, phục vụ tốt', NULL, '21-HaiPhong', '2026-06-24 17:00:00')
ON CONFLICT (id) DO NOTHING;
