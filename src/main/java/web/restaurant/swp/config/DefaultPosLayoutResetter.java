package web.restaurant.swp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Arrays;
import web.restaurant.swp.modules.pos.model.TableEntity;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DefaultPosLayoutResetter implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final web.restaurant.swp.modules.floorplan.repository.FloorPlanRepository floorPlanRepository;
    private final web.restaurant.swp.modules.floorplan.repository.FloorPlanObjectRepository floorPlanObjectRepository;
    private final web.restaurant.swp.modules.pos.repository.TableRepository tableRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Integer branchCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM branches", Integer.class);
        if (branchCount == null || branchCount == 0) {
            log.info("No branches found - skipping default POS layout reset.");
            return;
        }

        Integer roomCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class);
        if (roomCount != null && roomCount > 0) {
            log.info("Rooms already exist in the database - skipping default POS layout reset.");
            
            boolean seededNew = false;
            Integer count2 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM floor_plan_objects WHERE floor_plan_id = 2", Integer.class);
            Integer count4 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM floor_plan_objects WHERE floor_plan_id = 4", Integer.class);
            if ((count2 == null || count2 == 0) || (count4 == null || count4 == 0)) {
                seedDefaultObjectsForPlans2And4();
                seededNew = true;
            }

            log.info("Running self-healing migration to keep tables and floor plans synchronized...");
            runSelfHealingMigration();
            return;
        }

        log.info("Resetting default POS floors/tables for {} branches.", branchCount);

        jdbcTemplate.execute("DELETE FROM order_details");
        jdbcTemplate.execute("DELETE FROM customer_reviews");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM table_sessions");
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM floor_plan_objects");
        jdbcTemplate.execute("DELETE FROM floor_plans");
        jdbcTemplate.execute("DELETE FROM tables");
        jdbcTemplate.execute("DELETE FROM rooms");

        jdbcTemplate.execute("""
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
                ORDER BY b.branch_id, floor_number
                """);

        jdbcTemplate.execute("""
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
                ORDER BY r.branch_id, r.display_order, table_slot
                """);

        resetSequence("rooms");
        resetSequence("tables");
        resetSequence("table_sessions");
        resetSequence("orders");
        resetSequence("order_details");
        resetSequence("bookings");
        resetSequence("floor_plans");
        resetSequence("floor_plan_objects");
        resetSequence("customer_reviews");

        log.info("Default POS layout reset complete: 4 floors and 20 EMPTY tables per branch.");
    }

    private void resetSequence(String tableName) {
        try {
            String sequenceName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')",
                    String.class,
                    tableName
            );
            if (sequenceName != null) {
                jdbcTemplate.execute(String.format(
                        "SELECT setval('%s', COALESCE((SELECT MAX(id) FROM %s), 1))",
                        sequenceName,
                        tableName
                ));
            }
        } catch (Exception e) {
            log.debug("Could not reset sequence for {}", tableName, e);
        }
    }

    private void runSelfHealingMigration() {
        seedFloorPlanObjectsIfEmpty();

        // 1. Link floor plans to rooms
        try {
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Lầu 1' AND branch_id = '01-2thang9') WHERE id = 1");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Khu Vực 6' AND branch_id = '01-2thang9') WHERE id = 2");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Sân Trước' AND branch_id = '11-NguyenHuuTho') WHERE id = 3");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Lầu 2' AND branch_id = '11-NguyenHuuTho') WHERE id = 4");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Phòng Lạnh' AND branch_id = '21-HaiPhong') WHERE id = 5");
            jdbcTemplate.execute("UPDATE floor_plans SET floor_diagram_image_url = (SELECT floor_plan_image_url FROM rooms WHERE id = floor_plans.room_id) WHERE room_id IS NOT NULL");
            jdbcTemplate.execute("UPDATE floor_plans SET status = 'published' WHERE id IN (1, 2, 3, 4, 5)");
        } catch (Exception e) {
            log.error("Failed to link floor plans to rooms in self-healing migration", e);
        }

        // 2. Sync seeded floor plan objects with TableEntities
        try {
            List<Long> floorPlanIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            for (Long floorPlanId : floorPlanIds) {
                web.restaurant.swp.modules.floorplan.model.FloorPlan plan = floorPlanRepository.findById(floorPlanId).orElse(null);
                if (plan == null || plan.getRoom() == null) continue;
                
                List<web.restaurant.swp.modules.pos.model.TableEntity> roomTables = tableRepository.findByRoomId(plan.getRoom().getId());
                List<web.restaurant.swp.modules.floorplan.model.FloorPlanObject> tableObjects = floorPlanObjectRepository.findByFloorPlanIdOrdered(floorPlanId)
                        .stream()
                        .filter(obj -> {
                            String type = obj.getObjectType() != null ? obj.getObjectType().toLowerCase() : "";
                            return "table".equals(type) || type.startsWith("round_table_") || type.startsWith("square_table_") || type.startsWith("rectangle_table_") || "vip_sofa".equals(type) || "booth".equals(type);
                        })
                        .toList();
                
                for (int i = 0; i < tableObjects.size(); i++) {
                    web.restaurant.swp.modules.floorplan.model.FloorPlanObject obj = tableObjects.get(i);
                    web.restaurant.swp.modules.pos.model.TableEntity table;
                    if (i < roomTables.size()) {
                        table = roomTables.get(i);
                        table.setName(obj.getLabel() != null && !obj.getLabel().isBlank() ? obj.getLabel() : table.getName());
                        table.setDisplayLabel(obj.getLabel() != null && !obj.getLabel().isBlank() ? obj.getLabel() : table.getName());
                        table.setLayoutX(obj.getX());
                        table.setLayoutY(obj.getY());
                        table.setLayoutWidth(obj.getWidth());
                        table.setLayoutHeight(obj.getHeight());
                        table.setLayoutRotation(obj.getRotation());
                        table = tableRepository.save(table);
                    } else {
                        table = new web.restaurant.swp.modules.pos.model.TableEntity();
                        table.setName(obj.getLabel() != null && !obj.getLabel().isBlank() ? obj.getLabel() : "Bàn");
                        table.setDisplayLabel(table.getName());
                        table.setRoom(plan.getRoom());
                        table.setStatus("EMPTY");
                        table.setGuestCount(0);
                        table.setCapacity(4);
                        table.setTableStyle("ROUND");
                        table.setShape("circle");
                        table.setLayoutX(obj.getX());
                        table.setLayoutY(obj.getY());
                        table.setLayoutWidth(obj.getWidth());
                        table.setLayoutHeight(obj.getHeight());
                        table.setLayoutRotation(obj.getRotation());
                        table = tableRepository.save(table);
                    }
                    
                    java.util.Map<String, Object> metadata = obj.getMetadataJson();
                    if (metadata == null) metadata = new java.util.LinkedHashMap<>();
                    metadata.put("tableEntityId", table.getId());
                    metadata.put("tableId", table.getId());
                    metadata.put("linkedTableId", table.getId());
                    metadata.put("tableName", table.getName());
                    metadata.put("capacity", table.getCapacity());
                    metadata.put("tableStyle", table.getTableStyle());
                    
                    obj.setMetadataJson(metadata);
                    obj.setTableId(table.getId());
                    floorPlanObjectRepository.save(obj);
                }
            }
            log.info("Self-healing floor plan migration completed successfully.");
        } catch (Exception e) {
            log.error("Failed to link floor plan objects to existing tables in self-healing migration", e);
        }
    }

    private void seedDefaultObjectsForPlans2And4() {
        try {
            // Check if floor_plan_id = 2 is empty
            Integer count2 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM floor_plan_objects WHERE floor_plan_id = 2", Integer.class);
            if (count2 == null || count2 == 0) {
                log.info("Seeding default objects for Floor Plan 2...");
                // Walls
                jdbcTemplate.execute("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES " +
                        "(2, 'wall', 'Wall North', 0, 0, 1200, 20, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW()), " +
                        "(2, 'wall', 'Wall South', 0, 780, 1200, 20, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW()), " +
                        "(2, 'wall', 'Wall West', 0, 0, 20, 800, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW()), " +
                        "(2, 'wall', 'Wall East', 1180, 0, 20, 800, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW())");
                // Tables
                jdbcTemplate.execute("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES " +
                        "(2, 'table', 'Bàn 21', 150, 150, 80, 80, 0, 'circle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 21\",\"capacity\":4,\"zone\":\"Outdoor\"}', NOW(), NOW()), " +
                        "(2, 'table', 'Bàn 22', 350, 150, 80, 80, 0, 'circle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 22\",\"capacity\":4,\"zone\":\"Outdoor\"}', NOW(), NOW()), " +
                        "(2, 'table', 'Bàn 23', 150, 350, 120, 60, 0, 'rectangle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 23\",\"capacity\":2,\"zone\":\"Outdoor\"}', NOW(), NOW()), " +
                        "(2, 'table', 'Bàn 24', 350, 350, 120, 60, 0, 'rectangle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 24\",\"capacity\":2,\"zone\":\"Outdoor\"}', NOW(), NOW())");
            }

            // Check if floor_plan_id = 4 is empty
            Integer count4 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM floor_plan_objects WHERE floor_plan_id = 4", Integer.class);
            if (count4 == null || count4 == 0) {
                log.info("Seeding default objects for Floor Plan 4...");
                // Walls
                jdbcTemplate.execute("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES " +
                        "(4, 'wall', 'Wall North', 0, 0, 1000, 20, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW()), " +
                        "(4, 'wall', 'Wall South', 0, 680, 1000, 20, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW()), " +
                        "(4, 'wall', 'Wall West', 0, 0, 20, 700, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW()), " +
                        "(4, 'wall', 'Wall East', 980, 0, 20, 700, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW())");
                // Tables
                jdbcTemplate.execute("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES " +
                        "(4, 'table', 'Bàn 3', 150, 150, 80, 80, 0, 'circle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 3\",\"capacity\":4,\"zone\":\"VIP\"}', NOW(), NOW()), " +
                        "(4, 'table', 'Bàn 7', 350, 150, 80, 80, 0, 'circle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 7\",\"capacity\":4,\"zone\":\"VIP\"}', NOW(), NOW()), " +
                        "(4, 'table', 'Bàn 11', 550, 150, 80, 80, 0, 'circle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 11\",\"capacity\":6,\"zone\":\"VIP\"}', NOW(), NOW()), " +
                        "(4, 'table', 'Bàn 15', 150, 350, 120, 60, 0, 'rectangle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 15\",\"capacity\":2,\"zone\":\"VIP\"}', NOW(), NOW()), " +
                        "(4, 'table', 'Bàn 19', 350, 350, 120, 60, 0, 'rectangle', 10, '{\"fillColor\":\"#22c55e\"}', '{\"tableCode\":\"Bàn 19\",\"capacity\":2,\"zone\":\"VIP\"}', NOW(), NOW())");
            }
        } catch (Exception e) {
            log.error("Failed to seed default objects for Floor Plans 2 and 4", e);
        }
    }

    private void seedFloorPlanObjectsIfEmpty() {
        try {
            List<Long> floorPlanIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            for (Long floorPlanId : floorPlanIds) {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM floor_plan_objects WHERE floor_plan_id = ?", Integer.class, floorPlanId);
                if (count == null || count == 0) {
                    log.info("Floor plan {} has no objects. Seeding from room tables...", floorPlanId);
                    web.restaurant.swp.modules.floorplan.model.FloorPlan plan = floorPlanRepository.findById(floorPlanId).orElse(null);
                    if (plan == null || plan.getRoom() == null) continue;
                    
                    List<TableEntity> tables = tableRepository.findByRoomId(plan.getRoom().getId());
                    
                    // Insert walls first to make it look nice
                    jdbcTemplate.update("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES (?, 'wall', 'Wall North', 0, 0, ?, 20, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW())", floorPlanId, plan.getWidth());
                    jdbcTemplate.update("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES (?, 'wall', 'Wall South', 0, ?, ?, 20, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW())", floorPlanId, plan.getHeight() - 20, plan.getWidth());
                    jdbcTemplate.update("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES (?, 'wall', 'Wall West', 0, 0, 20, ?, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW())", floorPlanId, plan.getHeight());
                    jdbcTemplate.update("INSERT INTO floor_plan_objects (floor_plan_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES (?, 'wall', 'Wall East', ?, 0, 20, ?, 0, 'rectangle', 1, '{\"color\":\"#333333\"}', NULL, NOW(), NOW())", floorPlanId, plan.getWidth() - 20, plan.getHeight());
                    
                    for (int i = 0; i < tables.size(); i++) {
                        TableEntity table = tables.get(i);
                        int cols = 4;
                        int col = i % cols;
                        int row = i / cols;
                        double x = 100.0 + col * 250.0;
                        double y = 100.0 + row * 200.0;
                        double width = 90.0;
                        double height = 90.0;
                        String shape = "ROUND".equalsIgnoreCase(table.getTableStyle()) ? "circle" : "rectangle";
                        
                        String styleJson = "{\"fillColor\":\"#22c55e\"}";
                        String metadataJson = String.format(
                            "{\"tableEntityId\":%d,\"tableId\":%d,\"linkedTableId\":%d,\"tableName\":\"%s\",\"capacity\":%d}",
                            table.getId(), table.getId(), table.getId(), table.getName(), table.getCapacity()
                        );
                        
                        jdbcTemplate.update(
                            "INSERT INTO floor_plan_objects (floor_plan_id, table_id, object_type, label, x, y, width, height, rotation, shape, z_index, style_json, metadata_json, created_at, updated_at) VALUES (?, ?, 'table', ?, ?, ?, ?, ?, 0, ?, 10, CAST(? AS jsonb), CAST(? AS jsonb), NOW(), NOW())",
                            floorPlanId, table.getId(), table.getName(), x, y, width, height, shape, styleJson, metadataJson
                        );
                        
                        // Also update table layout coordinates
                        table.setLayoutX(x);
                        table.setLayoutY(y);
                        table.setLayoutWidth(width);
                        table.setLayoutHeight(height);
                        table.setLayoutRotation(0.0);
                        table.setShape(shape);
                        tableRepository.save(table);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to seed floor plan objects from room tables", e);
        }
    }
}
