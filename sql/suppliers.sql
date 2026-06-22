-- Suppliers SQL data
INSERT INTO suppliers (id, code, name, contact_email, phone, address, tenant_id) VALUES
(1, 'SUP-001', 'Thực phẩm sạch Metro', 'contact1@supplier.com', '0901234501', 'Địa chỉ nhà cung cấp 1', 'tenant-1'),
(2, 'SUP-002', 'Nông sản sạch Đà Lạt', 'contact2@supplier.com', '0901234502', 'Địa chỉ nhà cung cấp 2', 'tenant-1'),
(3, 'SUP-003', 'Hải sản tươi Nha Trang', 'contact3@supplier.com', '0901234503', 'Địa chỉ nhà cung cấp 3', 'tenant-1'),
(4, 'SUP-004', 'Thịt gia súc CP', 'contact4@supplier.com', '0901234504', 'Địa chỉ nhà cung cấp 4', 'tenant-1'),
(5, 'SUP-005', 'Sữa Ba Vì', 'contact5@supplier.com', '0901234505', 'Địa chỉ nhà cung cấp 5', 'tenant-1'),
(6, 'SUP-006', 'Hương liệu Việt', 'contact6@supplier.com', '0901234506', 'Địa chỉ nhà cung cấp 6', 'tenant-1'),
(7, 'SUP-007', 'Thiết bị nhà hàng Horeca', 'contact7@supplier.com', '0901234507', 'Địa chỉ nhà cung cấp 7', 'tenant-1'),
(8, 'SUP-008', 'Gas công nghiệp Hà Nội', 'contact8@supplier.com', '0901234508', 'Địa chỉ nhà cung cấp 8', 'tenant-1'),
(9, 'SUP-009', 'Rau quả tươi VinEco', 'contact9@supplier.com', '0901234509', 'Địa chỉ nhà cung cấp 9', 'tenant-1'),
(10, 'SUP-010', 'Hải sản tươi Phan Thiết', 'contact10@supplier.com', '0901234510', 'Địa chỉ nhà cung cấp 10', 'tenant-1'),
(11, 'SUP-011', 'Nước giải khát Coca', 'contact11@supplier.com', '0901234511', 'Địa chỉ nhà cung cấp 11', 'tenant-1'),
(12, 'SUP-012', 'Bia Sài Gòn', 'contact12@supplier.com', '0901234512', 'Địa chỉ nhà cung cấp 12', 'tenant-1'),
(13, 'SUP-013', 'Gạo ngon ST25', 'contact13@supplier.com', '0901234513', 'Địa chỉ nhà cung cấp 13', 'tenant-1'),
(14, 'SUP-014', 'Gia vị Cholimex', 'contact14@supplier.com', '0901234514', 'Địa chỉ nhà cung cấp 14', 'tenant-1'),
(15, 'SUP-015', 'Bánh kẹo Kinh Đô', 'contact15@supplier.com', '0901234515', 'Địa chỉ nhà cung cấp 15', 'tenant-1'),
(16, 'SUP-016', 'Khăn lạnh miền Bắc', 'contact16@supplier.com', '0901234516', 'Địa chỉ nhà cung cấp 16', 'tenant-1'),
(17, 'SUP-017', 'Đồng phục nhà hàng', 'contact17@supplier.com', '0901234517', 'Địa chỉ nhà cung cấp 17', 'tenant-1'),
(18, 'SUP-018', 'Túi bóng tự hủy', 'contact18@supplier.com', '0901234518', 'Địa chỉ nhà cung cấp 18', 'tenant-1'),
(19, 'SUP-019', 'Văn phòng phẩm Hồng Hà', 'contact19@supplier.com', '0901234519', 'Địa chỉ nhà cung cấp 19', 'tenant-1'),
(20, 'SUP-020', 'Hóa chất tẩy rửa Unilever', 'contact20@supplier.com', '0901234520', 'Địa chỉ nhà cung cấp 20', 'tenant-1')
ON CONFLICT (id) DO NOTHING;
