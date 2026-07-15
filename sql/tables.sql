-- Create exactly 20 available tables per branch:
-- Tầng 1: Bàn 1 - Bàn 5
-- Tầng 2: Bàn 6 - Bàn 10
-- Tầng 3: Bàn 11 - Bàn 15
-- Tầng 4: Bàn 16 - Bàn 20

INSERT INTO tables (
    name,
    room_id,
    status,
    capacity,
    guest_count,
    layout_x,
    layout_y,
    layout_width,
    layout_height,
    layout_rotation,
    layout_radius,
    display_label,
    table_style,
    shape
)
SELECT
    'Bàn ' || ((r.display_order * 5) + table_slot),
    r.id,
    'EMPTY',
    4,
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'Bàn ' || ((r.display_order * 5) + table_slot),
    'ROUND',
    'circle'
FROM rooms r
CROSS JOIN generate_series(1, 5) AS table_slot
WHERE r.name IN ('Tầng 1', 'Tầng 2', 'Tầng 3', 'Tầng 4')
ORDER BY r.branch_id, r.display_order, table_slot;
