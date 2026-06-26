-- Floor Plan Objects seed data
-- Floor Plan 1: Tầng 1 - Main Hall (branch 01-2thang9)
INSERT INTO floor_plan_objects (id, floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES
-- Walls
(1, 1, 'wall', 'Wall North', 0, 0, 1200, 20, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(2, 1, 'wall', 'Wall South', 0, 780, 1200, 20, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(3, 1, 'wall', 'Wall West', 0, 0, 20, 800, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(4, 1, 'wall', 'Wall East', 1180, 0, 20, 800, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
-- Door
(5, 1, 'door', 'Main Door', 540, 760, 120, 40, 0, 'arc', 5, '{"color":"#8B4513"}', '{"doorType":"entrance"}', NOW(), NOW()),
-- Windows
(6, 1, 'window', 'Window 1', 200, 0, 100, 15, 0, 'rectangle', 2, '{"color":"#87CEEB"}', NULL, NOW(), NOW()),
(7, 1, 'window', 'Window 2', 500, 0, 100, 15, 0, 'rectangle', 2, '{"color":"#87CEEB"}', NULL, NOW(), NOW()),
(8, 1, 'window', 'Window 3', 800, 0, 100, 15, 0, 'rectangle', 2, '{"color":"#87CEEB"}', NULL, NOW(), NOW()),
-- Cashier
(9, 1, 'cashier', 'Quầy thu ngân', 50, 650, 150, 80, 0, 'rectangle', 3, '{"color":"#DAA520"}', NULL, NOW(), NOW()),
-- Kitchen
(10, 1, 'kitchen', 'Bếp', 1000, 50, 150, 120, 0, 'rectangle', 3, '{"color":"#FF6347"}', NULL, NOW(), NOW()),
-- Tables - Round
(11, 1, 'table', 'T01', 150, 150, 80, 80, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T01","capacity":4,"isMergeable":true,"zone":"Main Hall"}', NOW(), NOW()),
(12, 1, 'table', 'T02', 300, 150, 80, 80, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T02","capacity":4,"isMergeable":true,"zone":"Main Hall"}', NOW(), NOW()),
(13, 1, 'table', 'T03', 450, 150, 80, 80, 0, 'circle', 10, '{"fillColor":"#ef4444"}', '{"tableCode":"T03","capacity":6,"isMergeable":true,"zone":"Main Hall"}', NOW(), NOW()),
(14, 1, 'table', 'T04', 600, 150, 80, 80, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T04","capacity":4,"isMergeable":true,"zone":"Main Hall"}', NOW(), NOW()),
-- Tables - Rectangle
(15, 1, 'table', 'T05', 150, 350, 120, 60, 0, 'rectangle', 10, '{"fillColor":"#f59e0b"}', '{"tableCode":"T05","capacity":2,"isMergeable":false,"zone":"Window Side"}', NOW(), NOW()),
(16, 1, 'table', 'T06', 350, 350, 120, 60, 0, 'rectangle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T06","capacity":2,"isMergeable":false,"zone":"Window Side"}', NOW(), NOW()),
-- Decorations
(17, 1, 'decoration', 'Plant', 100, 700, 40, 40, 0, 'circle', 15, '{"fillColor":"#228B22"}', NULL, NOW(), NOW()),
(18, 1, 'decoration', 'Plant', 1060, 700, 40, 40, 0, 'circle', 15, '{"fillColor":"#228B22"}', NULL, NOW(), NOW()),
-- Text
(19, 1, 'text', 'Main Hall', 500, 450, 200, 40, 0, 'rectangle', 20, '{"fontSize":"16","color":"#666666"}', NULL, NOW(), NOW());

-- Floor Plan 3: Tầng 1 - Sảnh chính (branch 11-NguyenHuuTho)
INSERT INTO floor_plan_objects (id, floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES
(20, 3, 'wall', 'Wall North', 0, 0, 1200, 20, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(21, 3, 'wall', 'Wall South', 0, 780, 1200, 20, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(22, 3, 'wall', 'Wall West', 0, 0, 20, 800, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(23, 3, 'wall', 'Wall East', 1180, 0, 20, 800, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(24, 3, 'door', 'Main Door', 540, 760, 120, 40, 0, 'arc', 5, '{"color":"#8B4513"}', '{"doorType":"entrance"}', NOW(), NOW()),
(25, 3, 'table', 'T01', 200, 200, 80, 80, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T01","capacity":4,"zone":"Main"}', NOW(), NOW()),
(26, 3, 'table', 'T02', 400, 200, 80, 80, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T02","capacity":4,"zone":"Main"}', NOW(), NOW()),
(27, 3, 'table', 'T03', 600, 200, 80, 80, 0, 'circle', 10, '{"fillColor":"#ef4444"}', '{"tableCode":"T03","capacity":6,"zone":"Main"}', NOW(), NOW()),
(28, 3, 'table', 'T04', 200, 400, 100, 60, 0, 'rectangle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T04","capacity":2,"zone":"Side"}', NOW(), NOW()),
(29, 3, 'table', 'T05', 400, 400, 100, 60, 0, 'rectangle', 10, '{"fillColor":"#f59e0b"}', '{"tableCode":"T05","capacity":2,"zone":"Side"}', NOW(), NOW()),
(30, 3, 'cashier', 'Quầy thu ngân', 50, 650, 150, 80, 0, 'rectangle', 3, '{"color":"#DAA520"}', NULL, NOW(), NOW()),
(31, 3, 'kitchen', 'Bếp', 1000, 50, 150, 120, 0, 'rectangle', 3, '{"color":"#FF6347"}', NULL, NOW(), NOW());

-- Floor Plan 5: Tầng 1 - Khu vực chính (branch 21-HaiPhong)
INSERT INTO floor_plan_objects (id, floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES
(32, 5, 'wall', 'Wall North', 0, 0, 1400, 20, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(33, 5, 'wall', 'Wall South', 0, 880, 1400, 20, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(34, 5, 'wall', 'Wall West', 0, 0, 20, 900, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(35, 5, 'wall', 'Wall East', 1380, 0, 20, 900, 0, 'rectangle', 1, '{"color":"#333333"}', NULL, NOW(), NOW()),
(36, 5, 'door', 'Main Door', 640, 860, 120, 40, 0, 'arc', 5, '{"color":"#8B4513"}', '{"doorType":"entrance"}', NOW(), NOW()),
(37, 5, 'door', 'Back Door', 1360, 400, 40, 100, 0, 'arc', 5, '{"color":"#8B4513"}', '{"doorType":"exit"}', NOW(), NOW()),
(38, 5, 'table', 'T01', 150, 150, 90, 90, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T01","capacity":4,"zone":"Zone A"}', NOW(), NOW()),
(39, 5, 'table', 'T02', 350, 150, 90, 90, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T02","capacity":4,"zone":"Zone A"}', NOW(), NOW()),
(40, 5, 'table', 'T03', 550, 150, 90, 90, 0, 'circle', 10, '{"fillColor":"#ef4444"}', '{"tableCode":"T03","capacity":6,"zone":"Zone A"}', NOW(), NOW()),
(41, 5, 'table', 'T04', 750, 150, 90, 90, 0, 'circle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T04","capacity":4,"zone":"Zone A"}', NOW(), NOW()),
(42, 5, 'table', 'T05', 150, 380, 120, 70, 0, 'rectangle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T05","capacity":2,"zone":"Zone B"}', NOW(), NOW()),
(43, 5, 'table', 'T06', 350, 380, 120, 70, 0, 'rectangle', 10, '{"fillColor":"#f59e0b"}', '{"tableCode":"T06","capacity":2,"zone":"Zone B"}', NOW(), NOW()),
(44, 5, 'table', 'T07', 550, 380, 120, 70, 0, 'rectangle', 10, '{"fillColor":"#22c55e"}', '{"tableCode":"T07","capacity":2,"zone":"Zone B"}', NOW(), NOW()),
(45, 5, 'bar', 'Quầy bar', 1100, 100, 200, 80, 0, 'rectangle', 3, '{"color":"#4A90D9"}', NULL, NOW(), NOW()),
(46, 5, 'cashier', 'Quầy thu ngân', 50, 750, 160, 90, 0, 'rectangle', 3, '{"color":"#DAA520"}', NULL, NOW(), NOW()),
(47, 5, 'kitchen', 'Bếp', 1150, 300, 200, 200, 0, 'rectangle', 3, '{"color":"#FF6347"}', NULL, NOW(), NOW()),
(48, 5, 'text', 'Khu vực chính', 500, 600, 200, 40, 0, 'rectangle', 20, '{"fontSize":"18","color":"#666666"}', NULL, NOW(), NOW());

-- Reset sequences
SELECT setval('floor_plan_objects_id_seq', COALESCE((SELECT MAX(id) FROM floor_plan_objects), 1));
