-- Shift Templates SQL data
INSERT INTO shift_templates (id, name, start_time, end_time, duration_hours, tenant_id) VALUES
(1, 'Ca Sáng', '08:00', '16:00', 8.0, 'tenant-1'),
(2, 'Ca Chiều', '16:00', '00:00', 8.0, 'tenant-1'),
(3, 'Ca Đêm', '00:00', '08:00', 8.0, 'tenant-1'),
(4, 'Ca Gãy 1', '10:00', '14:00', 4.0, 'tenant-1'),
(5, 'Ca Gãy 2', '18:00', '22:00', 4.0, 'tenant-1'),
(6, 'Ca Tùy Biến 6', '09:00', '17:00', 8.0, 'tenant-1'),
(7, 'Ca Tùy Biến 7', '09:00', '17:00', 8.0, 'tenant-1'),
(8, 'Ca Tùy Biến 8', '09:00', '17:00', 8.0, 'tenant-1'),
(9, 'Ca Tùy Biến 9', '09:00', '17:00', 8.0, 'tenant-1'),
(10, 'Ca Tùy Biến 10', '09:00', '17:00', 8.0, 'tenant-1'),
(11, 'Ca Tùy Biến 11', '09:00', '17:00', 8.0, 'tenant-1'),
(12, 'Ca Tùy Biến 12', '09:00', '17:00', 8.0, 'tenant-1'),
(13, 'Ca Tùy Biến 13', '09:00', '17:00', 8.0, 'tenant-1'),
(14, 'Ca Tùy Biến 14', '09:00', '17:00', 8.0, 'tenant-1'),
(15, 'Ca Tùy Biến 15', '09:00', '17:00', 8.0, 'tenant-1'),
(16, 'Ca Tùy Biến 16', '09:00', '17:00', 8.0, 'tenant-1'),
(17, 'Ca Tùy Biến 17', '09:00', '17:00', 8.0, 'tenant-1'),
(18, 'Ca Tùy Biến 18', '09:00', '17:00', 8.0, 'tenant-1'),
(19, 'Ca Tùy Biến 19', '09:00', '17:00', 8.0, 'tenant-1'),
(20, 'Ca Tùy Biến 20', '09:00', '17:00', 8.0, 'tenant-1')
ON CONFLICT (id) DO NOTHING;
