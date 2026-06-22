-- Audit Logs SQL data
INSERT INTO audit_logs (id, user_id, action, description, created_at, ip_address, user_name) VALUES
(1, 2, 'UPDATE_STOCK', 'Chi tiết hành động audit 1', '2026-05-29 12:01:00', '192.168.1.11', 'Nhân viên 2'),
(2, 3, 'LOGIN', 'Chi tiết hành động audit 2', '2026-05-29 12:02:00', '192.168.1.12', 'Nhân viên 3'),
(3, 4, 'UPDATE_STOCK', 'Chi tiết hành động audit 3', '2026-05-29 12:03:00', '192.168.1.13', 'Nhân viên 4'),
(4, 5, 'LOGIN', 'Chi tiết hành động audit 4', '2026-05-29 12:04:00', '192.168.1.14', 'Nhân viên 5'),
(5, 6, 'UPDATE_STOCK', 'Chi tiết hành động audit 5', '2026-05-29 12:05:00', '192.168.1.15', 'Nhân viên 6'),
(6, 7, 'LOGIN', 'Chi tiết hành động audit 6', '2026-05-29 12:06:00', '192.168.1.16', 'Nhân viên 7'),
(7, 8, 'UPDATE_STOCK', 'Chi tiết hành động audit 7', '2026-05-29 12:07:00', '192.168.1.17', 'Nhân viên 8'),
(8, 9, 'LOGIN', 'Chi tiết hành động audit 8', '2026-05-29 12:08:00', '192.168.1.18', 'Nhân viên 9'),
(9, 10, 'UPDATE_STOCK', 'Chi tiết hành động audit 9', '2026-05-29 12:09:00', '192.168.1.19', 'Nhân viên 10'),
(10, 1, 'LOGIN', 'Chi tiết hành động audit 10', '2026-05-29 12:10:00', '192.168.1.20', 'Nhân viên 1'),
(11, 2, 'UPDATE_STOCK', 'Chi tiết hành động audit 11', '2026-05-29 12:11:00', '192.168.1.21', 'Nhân viên 2'),
(12, 3, 'LOGIN', 'Chi tiết hành động audit 12', '2026-05-29 12:12:00', '192.168.1.22', 'Nhân viên 3'),
(13, 4, 'UPDATE_STOCK', 'Chi tiết hành động audit 13', '2026-05-29 12:13:00', '192.168.1.23', 'Nhân viên 4'),
(14, 5, 'LOGIN', 'Chi tiết hành động audit 14', '2026-05-29 12:14:00', '192.168.1.24', 'Nhân viên 5'),
(15, 6, 'UPDATE_STOCK', 'Chi tiết hành động audit 15', '2026-05-29 12:15:00', '192.168.1.25', 'Nhân viên 6'),
(16, 7, 'LOGIN', 'Chi tiết hành động audit 16', '2026-05-29 12:16:00', '192.168.1.26', 'Nhân viên 7'),
(17, 8, 'UPDATE_STOCK', 'Chi tiết hành động audit 17', '2026-05-29 12:17:00', '192.168.1.27', 'Nhân viên 8'),
(18, 9, 'LOGIN', 'Chi tiết hành động audit 18', '2026-05-29 12:18:00', '192.168.1.28', 'Nhân viên 9'),
(19, 10, 'UPDATE_STOCK', 'Chi tiết hành động audit 19', '2026-05-29 12:19:00', '192.168.1.29', 'Nhân viên 10'),
(20, 1, 'LOGIN', 'Chi tiết hành động audit 20', '2026-05-29 12:20:00', '192.168.1.30', 'Nhân viên 1')
ON CONFLICT (id) DO NOTHING;
