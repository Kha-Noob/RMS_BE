-- Leave Requests SQL data
INSERT INTO leave_requests (id, employee_id, start_date, end_date, reason, status, leave_type, created_at) VALUES
(1, 1, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 1', 'PENDING', 'ANNUAL', '2026-06-10 10:12:00'),
(2, 2, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 2', 'APPROVED', 'ANNUAL', '2026-06-10 11:14:00'),
(3, 3, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 3', 'PENDING', 'ANNUAL', '2026-06-10 12:16:00'),
(4, 4, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 4', 'APPROVED', 'ANNUAL', '2026-06-10 13:18:00'),
(5, 5, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 5', 'PENDING', 'ANNUAL', '2026-06-10 14:20:00'),
(6, 6, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 6', 'APPROVED', 'ANNUAL', '2026-06-10 15:22:00'),
(7, 7, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 7', 'PENDING', 'ANNUAL', '2026-06-10 16:24:00'),
(8, 8, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 8', 'APPROVED', 'ANNUAL', '2026-06-10 09:26:00'),
(9, 9, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 9', 'PENDING', 'ANNUAL', '2026-06-10 10:28:00'),
(10, 10, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 10', 'APPROVED', 'ANNUAL', '2026-06-10 11:30:00'),
(11, 11, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 11', 'PENDING', 'ANNUAL', '2026-06-10 12:32:00'),
(12, 12, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 12', 'APPROVED', 'ANNUAL', '2026-06-10 13:34:00'),
(13, 13, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 13', 'PENDING', 'ANNUAL', '2026-06-10 14:36:00'),
(14, 14, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 14', 'APPROVED', 'ANNUAL', '2026-06-10 15:38:00'),
(15, 15, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 15', 'PENDING', 'ANNUAL', '2026-06-10 16:40:00'),
(16, 16, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 16', 'APPROVED', 'ANNUAL', '2026-06-10 09:42:00'),
(17, 17, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 17', 'PENDING', 'ANNUAL', '2026-06-10 10:44:00'),
(18, 18, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 18', 'APPROVED', 'ANNUAL', '2026-06-10 11:46:00'),
(19, 19, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 19', 'PENDING', 'ANNUAL', '2026-06-10 12:48:00'),
(20, 20, '2026-06-12', '2026-06-13', 'Lý do xin nghỉ phép 20', 'APPROVED', 'ANNUAL', '2026-06-10 13:50:00')
ON CONFLICT (id) DO NOTHING;
