-- Users SQL data
INSERT INTO users (id, email, password, name, is_active, failed_login_attempts, branch_id, is_two_factor_enabled, tenant_id) VALUES
(1, 'admin@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Chủ chuỗi Admin', true, 0, NULL, false, 'tenant-1'),
(2, 'phamhadacdung2301@gmail.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phạm Hà Đắc Dũng (Google Admin)', true, 0, NULL, false, 'tenant-1'),
(3, 'Cen-HR-NguyenVanAn@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Văn An', true, 0, '01-2thang9', false, 'tenant-1'),
(4, 'Cen-Employee-TranThiBinh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trần Thị Bình', true, 0, '01-2thang9', false, 'tenant-1'),
(5, 'Cen-Employee-LeHoangCuong@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lê Hoàng Cường', true, 0, '01-2thang9', false, 'tenant-1'),
(6, 'Cen-Employee-PhamMinhDuc@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phạm Minh Đức', true, 0, '01-2thang9', false, 'tenant-1'),
(7, 'Cen-Employee-HoangThuGiang@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Hoàng Thu Giang', true, 0, '01-2thang9', false, 'tenant-1'),
(8, 'Cen-Employee-PhanVanHung@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phan Văn Hùng', true, 0, '01-2thang9', false, 'tenant-1'),
(9, 'Cen-Employee-VoMyLinh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Võ Mỹ Linh', true, 0, '01-2thang9', false, 'tenant-1'),
(10, 'Cen-Employee-DangQuocNam@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Đặng Quốc Nam', true, 0, '01-2thang9', false, 'tenant-1'),
(11, 'Cen-Employee-BuiHongNhung@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Bùi Hồng Nhung', true, 0, '01-2thang9', false, 'tenant-1'),
(12, 'Cen-Employee-DoChiThanh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Đỗ Chí Thanh', true, 0, '01-2thang9', false, 'tenant-1'),
(13, 'Cen-Employee-NgoPhuongThao@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Ngô Phương Thảo', true, 0, '01-2thang9', false, 'tenant-1'),
(14, 'Cen-Chef-LyGiaBao@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lý Gia Bảo', true, 0, '01-2thang9', false, 'tenant-1'),
(15, 'Cen-Chef-DuongMinhHoang@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Dương Minh Hoàng', true, 0, '01-2thang9', false, 'tenant-1'),
(16, 'Cen-Chef-TrinhTienDat@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trịnh Tiến Đạt', true, 0, '01-2thang9', false, 'tenant-1'),
(17, 'Cen-Cashier-MaiPhuongAnh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Mai Phương Anh', true, 0, '01-2thang9', false, 'tenant-1'),
(18, 'Cen-Cashier-NguyenThuTrang@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Thu Trang', true, 0, '01-2thang9', false, 'tenant-1'),
(19, 'Nht-HR-TranVanDung@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trần Văn Dũng', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(20, 'Nht-Employee-NguyenThiHanh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Thị Hạnh', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(21, 'Nht-Employee-LeVanHai@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lê Văn Hải', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(22, 'Nht-Employee-PhamDucThang@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phạm Đức Thắng', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(23, 'Nht-Employee-HoangKimOanh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Hoàng Kim Oanh', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(24, 'Nht-Employee-PhanThanhSon@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phan Thanh Sơn', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(25, 'Nht-Employee-VoThiTuyet@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Võ Thị Tuyết', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(26, 'Nht-Employee-DangVanLam@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Đặng Văn Lâm', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(27, 'Nht-Employee-BuiMinhTuan@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Bùi Minh Tuấn', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(28, 'Nht-Employee-DoThuHa@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Đỗ Thu Hà', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(29, 'Nht-Employee-NgoVanPhong@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Ngô Văn Phong', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(30, 'Nht-Chef-LyHuuPhuoc@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lý Hữu Phước', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(31, 'Nht-Chef-DuongTheVinh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Dương Thế Vinh', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(32, 'Nht-Chef-TrinhQuocViet@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trịnh Quốc Việt', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(33, 'Nht-Cashier-MaiKhanhLinh@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Mai Khánh Linh', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(34, 'Nht-Cashier-NguyenHoaiNam@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Hoài Nam', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(35, 'Hp-HR-LeThiHong@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lê Thị Hồng', true, 0, '21-HaiPhong', false, 'tenant-1'),
(36, 'Hp-Employee-NguyenMinhTriet@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Minh Triết', true, 0, '21-HaiPhong', false, 'tenant-1'),
(37, 'Hp-Employee-TranQuangKhai@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trần Quang Khải', true, 0, '21-HaiPhong', false, 'tenant-1'),
(38, 'Hp-Employee-PhamVanDong@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phạm Văn Đồng', true, 0, '21-HaiPhong', false, 'tenant-1'),
(39, 'Hp-Employee-HoangThiLoan@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Hoàng Thị Loan', true, 0, '21-HaiPhong', false, 'tenant-1'),
(40, 'Hp-Employee-PhanHuyLe@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phan Huy Lê', true, 0, '21-HaiPhong', false, 'tenant-1'),
(41, 'Hp-Employee-VoNguyenGiap@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Võ Nguyên Giáp', true, 0, '21-HaiPhong', false, 'tenant-1'),
(42, 'Hp-Employee-DangThuyTram@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Đặng Thùy Trâm', true, 0, '21-HaiPhong', false, 'tenant-1'),
(43, 'Hp-Employee-BuiXuanPhai@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Bùi Xuân Phái', true, 0, '21-HaiPhong', false, 'tenant-1'),
(44, 'Hp-Employee-DoCaoTri@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Đỗ Cao Trí', true, 0, '21-HaiPhong', false, 'tenant-1'),
(45, 'Hp-Employee-NgoQuyen@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Ngô Quyền', true, 0, '21-HaiPhong', false, 'tenant-1'),
(46, 'Hp-Chef-LyThaiTo@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lý Thái Tổ', true, 0, '21-HaiPhong', false, 'tenant-1'),
(47, 'Hp-Chef-DuongQuangHam@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Dương Quảng Hàm', true, 0, '21-HaiPhong', false, 'tenant-1'),
(48, 'Hp-Chef-TrinhCongSon@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trịnh Công Sơn', true, 0, '21-HaiPhong', false, 'tenant-1'),
(49, 'Hp-Cashier-MaiHacDe@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Mai Hắc Đế', true, 0, '21-HaiPhong', false, 'tenant-1'),
(50, 'Hp-Cashier-NguyenDu@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Du', true, 0, '21-HaiPhong', false, 'tenant-1'),
(51, 'manager-2thang9@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Quản Lý - Chi nhánh 2 Tháng 9', true, 0, '01-2thang9', false, 'tenant-1'),
(52, 'manager-nguyenhuutho@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trần Quản Lý - Chi nhánh Nguyễn Hữu Thọ', true, 0, '11-NguyenHuuTho', false, 'tenant-1'),
(53, 'warehouse-2thang9@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trần Thủ Kho', true, 0, '01-2thang9', false, 'tenant-1'),
(54, 'procurement-2thang9@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lê Thu Mua', true, 0, '01-2thang9', false, 'tenant-1'),
(55, 'customer-test@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Khách Hàng Test', true, 0, NULL, false, 'tenant-1'),
(56, 'customer1@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Lê Minh Anh', true, 0, NULL, false, 'tenant-1'),
(57, 'customer2@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phạm Hoàng Nam', true, 0, NULL, false, 'tenant-1'),
(58, 'customer3@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nguyễn Khánh Vy', true, 0, NULL, false, 'tenant-1'),
(59, 'customer4@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Trần Quốc Bảo', true, 0, NULL, false, 'tenant-1'),
(60, 'customer5@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Vũ Gia Linh', true, 0, NULL, false, 'tenant-1'),
(61, 'customer6@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Phan Minh Khang', true, 0, NULL, false, 'tenant-1'),
(62, 'customer7@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Đỗ Thanh Trúc', true, 0, NULL, false, 'tenant-1'),
(63, 'customer8@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Bùi Đức Thắng', true, 0, NULL, false, 'tenant-1'),
(64, 'customer9@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Ngô Mai Chi', true, 0, NULL, false, 'tenant-1'),
(65, 'customer10@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Dương Minh Quân', true, 0, NULL, false, 'tenant-1'),
(66, 'cooperator1@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nhà hàng Hợp Tác 1', true, 0, NULL, false, 'tenant-1'),
(67, 'cooperator2@liteflow.com', '$2a$10$7eq/t4lk9f9gu4CNmWPIZOJ4ktC6wwMeSazvIZsiP8l.ceOVCVe4y', 'Nhà hàng Hợp Tác 2', true, 0, NULL, false, 'tenant-1')
ON CONFLICT (id) DO NOTHING;

UPDATE users SET phone = '0912345601' WHERE email = 'customer-test@liteflow.com';
UPDATE users SET phone = '0912345656' WHERE id = 56;
UPDATE users SET phone = '0912345657' WHERE id = 57;
UPDATE users SET phone = '0912345658' WHERE id = 58;
UPDATE users SET phone = '0912345659' WHERE id = 59;
UPDATE users SET phone = '0912345660' WHERE id = 60;
UPDATE users SET phone = '0912345661' WHERE id = 61;
UPDATE users SET phone = '0912345662' WHERE id = 62;
UPDATE users SET phone = '0912345663' WHERE id = 63;
UPDATE users SET phone = '0912345664' WHERE id = 64;
UPDATE users SET phone = '0912345665' WHERE id = 65;
UPDATE users SET phone = '0912345666' WHERE id = 66;
UPDATE users SET phone = '0912345667' WHERE id = 67;
