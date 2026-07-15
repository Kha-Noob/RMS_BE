-- Rooms SQL data
-- floorPlanImageUrl: 2D floor diagram SVGs (NOT restaurant photos)
-- panoramaUrl: actual restaurant photos for 360 view (can stay as photos)
INSERT INTO rooms (id, name, branch_id, floor_plan_image_url, panorama_url, panorama_type, display_order, floor_plan_width, floor_plan_height) VALUES
-- Branch 01-2thang9: 2D diagram for floor plan, photo for 360
(3, 'Lầu 1', '01-2thang9', '/floor-plans/branch-01-2thang9-tang1.svg', 'https://images.unsplash.com/photo-1552566626-52f8b828add9?w=1200', 'IMAGE_360', 0, 1000, 800),
(6, 'Khu Vực 6', '01-2thang9', '/floor-plans/branch-01-2thang9-tang1.svg', 'https://images.unsplash.com/photo-1559329007-40df8a9345d8?w=1200', 'IMAGE_360', 1, 1000, 800),
(9, 'Khu Vực 9', '01-2thang9', '/floor-plans/branch-01-2thang9-tang1.svg', NULL, NULL, 2, NULL, NULL),

-- Branch 11-NguyenHuuTho: 2D diagram for floor plan, photo for 360
(1, 'Sân Trước', '11-NguyenHuuTho', '/floor-plans/branch-11-NguyenHuuTho-tang1.svg', 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=1200', 'IMAGE_360', 0, 1000, 800),
(4, 'Lầu 2', '11-NguyenHuuTho', '/floor-plans/branch-11-NguyenHuuTho-tang1.svg', 'https://images.unsplash.com/photo-1559329007-40df8a9345d8?w=1200', 'IMAGE_360', 1, 1000, 800),
(7, 'Khu Vực 7', '11-NguyenHuuTho', '/floor-plans/branch-11-NguyenHuuTho-tang1.svg', NULL, NULL, 2, NULL, NULL),

-- Branch 21-HaiPhong: 2D diagram for floor plan, photo for 360
(2, 'Phòng Lạnh', '21-HaiPhong', '/floor-plans/branch-21-HaiPhong-tang1.svg', 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=1200', 'IMAGE_360', 0, 1200, 900),
(5, 'Khu Vực 5', '21-HaiPhong', '/floor-plans/branch-21-HaiPhong-tang1.svg', 'https://images.unsplash.com/photo-1550966871-3ed3cdb51f3a?w=1200', 'IMAGE_360', 1, 1200, 900),
(8, 'Khu Vực 8', '21-HaiPhong', '/floor-plans/branch-21-HaiPhong-tang1.svg', NULL, NULL, 2, NULL, NULL),

-- Rooms without floor plans (fallback to grid)
(10, 'Khu Vực 10', '11-NguyenHuuTho', NULL, NULL, NULL, 3, NULL, NULL),
(11, 'Khu Vực 11', '21-HaiPhong', NULL, NULL, NULL, 3, NULL, NULL),
(12, 'Khu Vực 12', '01-2thang9', NULL, NULL, NULL, 3, NULL, NULL),
(13, 'Khu Vực 13', '11-NguyenHuuTho', NULL, NULL, NULL, 4, NULL, NULL),
(14, 'Khu Vực 14', '21-HaiPhong', NULL, NULL, NULL, 4, NULL, NULL),
(15, 'Khu Vực 15', '01-2thang9', NULL, NULL, NULL, 4, NULL, NULL),
(16, 'Khu Vực 16', '11-NguyenHuuTho', NULL, NULL, NULL, 5, NULL, NULL),
(17, 'Khu Vực 17', '21-HaiPhong', NULL, NULL, NULL, 5, NULL, NULL),
(18, 'Khu Vực 18', '01-2thang9', NULL, NULL, NULL, 5, NULL, NULL),
(19, 'Khu Vực 19', '11-NguyenHuuTho', NULL, NULL, NULL, 6, NULL, NULL),
(20, 'Khu Vực 20', '21-HaiPhong', NULL, NULL, NULL, 6, NULL, NULL)
ON CONFLICT (id) DO NOTHING;
