-- Branches SQL data
INSERT INTO branches (branch_id, name, address, phone, latitude, longitude, is_active, tenant_id) VALUES
('01-2thang9', 'Chi nhánh 2 Tháng 9', '01 Đường 2 Tháng 9, Hải Châu, Đà Nẵng', '02363123456', 16.0596, 108.2238, true, 'tenant-1'),
('11-NguyenHuuTho', 'Chi nhánh Nguyễn Hữu Thọ', '11 Đường Nguyễn Hữu Thọ, Hải Châu, Đà Nẵng', '02363987654', 16.0378, 108.2105, true, 'tenant-1'),
('21-HaiPhong', 'Chi nhánh Hải Phòng', '21 Đường Hải Phòng, Thạch Thang, Đà Nẵng', '02363555444', 16.0725, 108.2198, true, 'tenant-1'),
('02-external', 'Chi nhánh Hợp Tác 2', '45 Đường Lê Lợi, Hải Châu, Đà Nẵng', '02363777888', 16.0743, 108.2215, true, 'tenant-2'),
('03-sushi', 'Chi nhánh Hợp Tác 3', '99 Đường Hùng Vương, Hải Châu, Đà Nẵng', '02363999000', 16.0682, 108.2173, true, 'tenant-3')
ON CONFLICT (branch_id) DO NOTHING;
