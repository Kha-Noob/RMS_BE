-- Tables SQL data
INSERT INTO tables (id, name, room_id, status, capacity, guest_count) VALUES
(1, 'Bàn 1', 2, 'OCCUPIED', 6, 2),
(2, 'Bàn 2', 3, 'RESERVED', 4, 0),
(3, 'Bàn 3', 4, 'EMPTY', 6, 0),
(4, 'Bàn 4', 1, 'OCCUPIED', 4, 1),
(5, 'Bàn 5', 2, 'RESERVED', 6, 0),
(6, 'Bàn 6', 3, 'EMPTY', 4, 0),
(7, 'Bàn 7', 4, 'OCCUPIED', 6, 4),
(8, 'Bàn 8', 1, 'RESERVED', 4, 0),
(9, 'Bàn 9', 2, 'EMPTY', 6, 0),
(10, 'Bàn 10', 3, 'OCCUPIED', 4, 3),
(11, 'Bàn 11', 4, 'RESERVED', 6, 0),
(12, 'Bàn 12', 1, 'EMPTY', 4, 0),
(13, 'Bàn 13', 2, 'OCCUPIED', 6, 2),
(14, 'Bàn 14', 3, 'RESERVED', 4, 0),
(15, 'Bàn 15', 4, 'EMPTY', 6, 0),
(16, 'Bàn 16', 1, 'OCCUPIED', 4, 1),
(17, 'Bàn 17', 2, 'RESERVED', 6, 0),
(18, 'Bàn 18', 3, 'EMPTY', 4, 0),
(19, 'Bàn 19', 4, 'OCCUPIED', 6, 4),
(20, 'Bàn 20', 1, 'RESERVED', 4, 0)
ON CONFLICT (id) DO NOTHING;
