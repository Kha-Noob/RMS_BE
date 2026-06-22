-- Rooms SQL data
INSERT INTO rooms (id, name, branch_id) VALUES
(1, 'Sân Trước', '11-NguyenHuuTho'),
(2, 'Phòng Lạnh', '21-HaiPhong'),
(3, 'Lầu 1', '01-2thang9'),
(4, 'Lầu 2', '11-NguyenHuuTho'),
(5, 'Khu Vực 5', '21-HaiPhong'),
(6, 'Khu Vực 6', '01-2thang9'),
(7, 'Khu Vực 7', '11-NguyenHuuTho'),
(8, 'Khu Vực 8', '21-HaiPhong'),
(9, 'Khu Vực 9', '01-2thang9'),
(10, 'Khu Vực 10', '11-NguyenHuuTho'),
(11, 'Khu Vực 11', '21-HaiPhong'),
(12, 'Khu Vực 12', '01-2thang9'),
(13, 'Khu Vực 13', '11-NguyenHuuTho'),
(14, 'Khu Vực 14', '21-HaiPhong'),
(15, 'Khu Vực 15', '01-2thang9'),
(16, 'Khu Vực 16', '11-NguyenHuuTho'),
(17, 'Khu Vực 17', '21-HaiPhong'),
(18, 'Khu Vực 18', '01-2thang9'),
(19, 'Khu Vực 19', '11-NguyenHuuTho'),
(20, 'Khu Vực 20', '21-HaiPhong')
ON CONFLICT (id) DO NOTHING;
