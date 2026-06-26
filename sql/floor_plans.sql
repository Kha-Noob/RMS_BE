-- Floor Plans seed data
INSERT INTO floor_plans (id, branch_id, name, floor_number, width, height, background_image_url, panorama_360_url, status, is_table_selection_enabled, created_by, updated_by, created_at, updated_at) VALUES
(1, '01-2thang9', 'Tầng 1 - Main Hall', 1, 1200, 800, NULL, NULL, 'published', true, NULL, NULL, NOW(), NOW()),
(2, '01-2thang9', 'Tầng 2 - VIP Area', 2, 1000, 600, NULL, NULL, 'draft', false, NULL, NULL, NOW(), NOW()),
(3, '11-NguyenHuuTho', 'Tầng 1 - Sảnh chính', 1, 1200, 800, NULL, NULL, 'published', true, NULL, NULL, NOW(), NOW()),
(4, '11-NguyenHuuTho', 'Tầng 2 - Phòng tiệc', 2, 1000, 700, NULL, NULL, 'draft', false, NULL, NULL, NOW(), NOW()),
(5, '21-HaiPhong', 'Tầng 1 - Khu vực chính', 1, 1400, 900, NULL, NULL, 'published', true, NULL, NULL, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('floor_plans_id_seq', COALESCE((SELECT MAX(id) FROM floor_plans), 1));
