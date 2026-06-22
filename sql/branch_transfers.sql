-- Branch Transfers SQL data
INSERT INTO branch_transfers (id, source_branch_id, target_branch_id, request_date, approve_date, status) VALUES
(1, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-11 10:00:00', '2026-05-11 14:00:00', 'COMPLETED'),
(2, '01-2thang9', '21-HaiPhong', '2026-05-12 10:00:00', '2026-05-12 14:00:00', 'COMPLETED'),
(3, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-13 10:00:00', '2026-05-13 14:00:00', 'COMPLETED'),
(4, '01-2thang9', '21-HaiPhong', '2026-05-14 10:00:00', '2026-05-14 14:00:00', 'COMPLETED'),
(5, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-15 10:00:00', '2026-05-15 14:00:00', 'COMPLETED'),
(6, '01-2thang9', '21-HaiPhong', '2026-05-16 10:00:00', '2026-05-16 14:00:00', 'COMPLETED'),
(7, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-17 10:00:00', '2026-05-17 14:00:00', 'COMPLETED'),
(8, '01-2thang9', '21-HaiPhong', '2026-05-18 10:00:00', '2026-05-18 14:00:00', 'COMPLETED'),
(9, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-19 10:00:00', '2026-05-19 14:00:00', 'COMPLETED'),
(10, '01-2thang9', '21-HaiPhong', '2026-05-20 10:00:00', '2026-05-20 14:00:00', 'COMPLETED'),
(11, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-21 10:00:00', '2026-05-21 14:00:00', 'COMPLETED'),
(12, '01-2thang9', '21-HaiPhong', '2026-05-22 10:00:00', '2026-05-22 14:00:00', 'COMPLETED'),
(13, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-23 10:00:00', '2026-05-23 14:00:00', 'COMPLETED'),
(14, '01-2thang9', '21-HaiPhong', '2026-05-24 10:00:00', '2026-05-24 14:00:00', 'COMPLETED'),
(15, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-25 10:00:00', '2026-05-25 14:00:00', 'COMPLETED'),
(16, '01-2thang9', '21-HaiPhong', '2026-05-26 10:00:00', '2026-05-26 14:00:00', 'COMPLETED'),
(17, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-27 10:00:00', '2026-05-27 14:00:00', 'COMPLETED'),
(18, '01-2thang9', '21-HaiPhong', '2026-05-28 10:00:00', '2026-05-28 14:00:00', 'COMPLETED'),
(19, '11-NguyenHuuTho', '21-HaiPhong', '2026-05-29 10:00:00', '2026-05-29 14:00:00', 'COMPLETED'),
(20, '01-2thang9', '21-HaiPhong', '2026-05-30 10:00:00', '2026-05-30 14:00:00', 'COMPLETED')
ON CONFLICT (id) DO NOTHING;
