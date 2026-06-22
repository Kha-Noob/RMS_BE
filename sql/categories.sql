-- Categories SQL data
INSERT INTO categories (id, name, tenant_id) VALUES
(1, 'Món chính', 'tenant-1'),
(2, 'Đồ uống', 'tenant-1'),
(3, 'Tráng miệng', 'tenant-1'),
(4, 'Khai vị', 'tenant-1'),
(5, 'Món nước', 'tenant-1'),
(6, 'Món nướng', 'tenant-1'),
(7, 'Lẩu', 'tenant-1'),
(8, 'Ăn nhẹ', 'tenant-1')
ON CONFLICT (id) DO NOTHING;
