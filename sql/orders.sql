-- Orders SQL data
INSERT INTO orders (id, session_id, order_date, status, total_amount, branch_id, source) VALUES
(1, 1, '2026-06-24 08:15:00', 'SERVED', 125000.0, '01-2thang9', 'OFFLINE'),
(2, 2, '2026-06-24 08:30:00', 'SERVED', 85000.0, '01-2thang9', 'ONLINE'),
(3, 3, '2026-06-24 09:00:00', 'SERVED', 210000.0, '01-2thang9', 'OFFLINE'),
(4, 4, '2026-06-24 09:15:00', 'SERVED', 95000.0, '11-NguyenHuuTho', 'ONLINE'),
(5, 5, '2026-06-24 09:30:00', 'SERVED', 175000.0, '11-NguyenHuuTho', 'OFFLINE'),
(6, 6, '2026-06-24 10:00:00', 'SERVED', 145000.0, '11-NguyenHuuTho', 'ONLINE'),
(7, 7, '2026-06-24 10:15:00', 'SERVED', 220000.0, '21-HaiPhong', 'OFFLINE'),
(8, 8, '2026-06-24 10:30:00', 'SERVED', 165000.0, '21-HaiPhong', 'ONLINE'),
(9, 9, '2026-06-24 11:00:00', 'SERVED', 90000.0, '21-HaiPhong', 'OFFLINE'),
(10, 10, '2026-06-24 11:15:00', 'SERVED', 180000.0, '01-2thang9', 'ONLINE'),
(11, 11, '2026-06-24 11:30:00', 'SERVED', 130000.0, '01-2thang9', 'OFFLINE'),
(12, 12, '2026-06-24 12:00:00', 'SERVED', 250000.0, '11-NguyenHuuTho', 'ONLINE'),
(13, 13, '2026-06-24 12:15:00', 'SERVED', 110000.0, '21-HaiPhong', 'OFFLINE'),
(14, 14, '2026-06-24 12:30:00', 'SENT', 95000.0, '01-2thang9', 'ONLINE'),
(15, 15, '2026-06-24 13:00:00', 'PENDING', 140000.0, '11-NguyenHuuTho', 'OFFLINE'),
(16, 16, '2026-06-24 13:15:00', 'PENDING', 185000.0, '21-HaiPhong', 'ONLINE'),
(17, 17, '2026-06-23 08:00:00', 'SERVED', 155000.0, '01-2thang9', 'OFFLINE'),
(18, 18, '2026-06-23 09:30:00', 'SERVED', 230000.0, '11-NguyenHuuTho', 'ONLINE'),
(19, 19, '2026-06-23 10:45:00', 'SERVED', 175000.0, '21-HaiPhong', 'OFFLINE'),
(20, 20, '2026-06-23 14:00:00', 'SERVED', 195000.0, '01-2thang9', 'ONLINE')
ON CONFLICT (id) DO NOTHING;
