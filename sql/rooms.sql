-- Reset POS floor/area demo state and create the default 4-floor structure
-- for every existing branch.

DELETE FROM order_details;
DELETE FROM customer_reviews;
DELETE FROM orders;
DELETE FROM table_sessions;
DELETE FROM bookings;
DELETE FROM floor_plan_objects;
DELETE FROM floor_plans;
DELETE FROM tables;
DELETE FROM rooms;

INSERT INTO rooms (
    name,
    branch_id,
    floor_plan_image_url,
    background_mode,
    panorama_url,
    panorama_type,
    display_order,
    floor_plan_width,
    floor_plan_height
)
SELECT
    'Tầng ' || floor_number,
    b.branch_id,
    NULL,
    NULL,
    NULL,
    NULL,
    floor_number - 1,
    NULL,
    NULL
FROM branches b
CROSS JOIN generate_series(1, 4) AS floor_number
ORDER BY b.branch_id, floor_number;
