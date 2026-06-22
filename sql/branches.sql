-- Branches SQL data
INSERT INTO branches (branch_id, name, address, phone, is_active, tenant_id) VALUES
('01-2thang9', 'Chi nhánh 2 Tháng 9', '01 Đường 2 Tháng 9, Hải Châu, Đà Nẵng', '02363123456', true, 'tenant-1'),
('11-NguyenHuuTho', 'Chi nhánh Nguyễn Hữu Thọ', '11 Đường Nguyễn Hữu Thọ, Hải Châu, Đà Nẵng', '02363987654', true, 'tenant-1'),
('21-HaiPhong', 'Chi nhánh Hải Phòng', '21 Đường Hải Phòng, Thạch Thang, Đà Nẵng', '02363555444', true, 'tenant-1')
ON CONFLICT (branch_id) DO NOTHING;
