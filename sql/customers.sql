-- Customers SQL data
INSERT INTO customers (id, name, phone, birth_date, membership_tier, loyalty_points, total_spent, tenant_id) VALUES
(1, 'Nguyễn Văn Hùng', '0912345601', '1991-05-11', 'Silver', 120, 1200000.0, 'tenant-1'),
(2, 'Trần Thị Lan', '0912345602', '1992-05-12', 'Gold', 240, 2400000.0, 'tenant-1'),
(3, 'Lê Hoàng Nam', '0912345603', '1993-05-13', 'Platinum', 360, 3600000.0, 'tenant-1'),
(4, 'Phạm Minh Tuấn', '0912345604', '1994-05-14', 'Bronze', 480, 4800000.0, 'tenant-1'),
(5, 'Hoàng Thu Thảo', '0912345605', '1995-05-15', 'Silver', 600, 6000000.0, 'tenant-1'),
(6, 'Vũ Quốc Anh', '0912345606', '1996-05-16', 'Gold', 720, 7200000.0, 'tenant-1'),
(7, 'Đặng Thùy Chi', '0912345607', '1997-05-17', 'Platinum', 840, 8400000.0, 'tenant-1'),
(8, 'Bùi Tiến Dũng', '0912345608', '1998-05-18', 'Bronze', 960, 9600000.0, 'tenant-1'),
(9, 'Ngô Thanh Hằng', '0912345609', '1999-05-19', 'Silver', 1080, 10800000.0, 'tenant-1'),
(10, 'Đỗ Minh Đức', '0912345610', '1990-05-20', 'Gold', 1200, 12000000.0, 'tenant-1'),
(11, 'Hồ Bảo Trâm', '0912345611', '1991-05-21', 'Platinum', 1320, 13200000.0, 'tenant-1'),
(12, 'Dương Chí Kiên', '0912345612', '1992-05-22', 'Bronze', 1440, 14400000.0, 'tenant-1'),
(13, 'Lý Kim Ngọc', '0912345613', '1993-05-23', 'Silver', 1560, 15600000.0, 'tenant-1'),
(14, 'Phan Thanh Sơn', '0912345614', '1994-05-24', 'Gold', 1680, 16800000.0, 'tenant-1'),
(15, 'Võ Hồng Nhung', '0912345615', '1995-05-25', 'Platinum', 1800, 18000000.0, 'tenant-1'),
(16, 'Trịnh Gia Huy', '0912345616', '1996-05-26', 'Bronze', 1920, 19200000.0, 'tenant-1'),
(17, 'Đinh Ngọc Diệp', '0912345617', '1997-05-27', 'Silver', 2040, 20400000.0, 'tenant-1'),
(18, 'Lâm Quốc Khánh', '0912345618', '1998-05-28', 'Gold', 2160, 21600000.0, 'tenant-1'),
(19, 'Cao Mỹ Linh', '0912345619', '1999-05-29', 'Platinum', 2280, 22800000.0, 'tenant-1'),
(20, 'Mai Hữu Phước', '0912345620', '1990-05-30', 'Bronze', 2400, 24000000.0, 'tenant-1')
ON CONFLICT (id) DO NOTHING;
