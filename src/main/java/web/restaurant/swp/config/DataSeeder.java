package web.restaurant.swp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import web.restaurant.swp.modules.auth.repository.RoleRepository;
import web.restaurant.swp.modules.auth.repository.UserRepository;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ConfigurableEnvironment environment;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final web.restaurant.swp.modules.floorplan.repository.FloorPlanRepository floorPlanRepository;
    private final web.restaurant.swp.modules.floorplan.repository.FloorPlanObjectRepository floorPlanObjectRepository;
    private final web.restaurant.swp.modules.pos.repository.TableRepository tableRepository;

    @Override
    public void run(String... args) {
        if (environment.getActiveProfiles().length > 0 && Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            log.info("Test profile active - skipping database seeding.");
            return;
        }

        // Align product names and variants with menu items
        log.info("====== Aligning database product names and variants with menu items ======");
        try {
            // Self-healing migration for image_path length and quantity column
            try {
                jdbcTemplate.execute("ALTER TABLE products ALTER COLUMN image_path TYPE TEXT");
                jdbcTemplate.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS quantity INT");
            } catch (Exception ex) {
                log.info("Column migration skipped or already exists: {}", ex.getMessage());
            }

            // Seed initial quantities for items needing stock (beverages, flan, desserts)
            try {
                jdbcTemplate.execute("UPDATE products SET quantity = 50 WHERE category_id = (SELECT id FROM categories WHERE name = 'Đồ uống' LIMIT 1) AND (quantity IS NULL OR quantity = 0)");
                jdbcTemplate.execute("UPDATE products SET quantity = 30 WHERE (name LIKE '%Flan%' OR name LIKE '%Chè%' OR name LIKE '%Kem%') AND (quantity IS NULL OR quantity = 0)");
                jdbcTemplate.execute("UPDATE tenants SET bank_name = 'MB Bank', bank_account_no = '0862807412', bank_account_name = 'LE DUC THUAN' WHERE tenant_id = 'tenant-1'");
                jdbcTemplate.execute("UPDATE bank_settings SET bank_name = 'MB Bank', account_number = '0862807412', account_holder = 'LE DUC THUAN'");
            } catch (Exception ex) {
                log.warn("Seeding initial quantities/bank info skipped: {}", ex.getMessage());
            }

            // 1. Update product names to match menu names exactly
            jdbcTemplate.execute("UPDATE products SET name = 'Cơm Tấm Sườn Nướng' WHERE id = 1");
            jdbcTemplate.execute("UPDATE products SET name = 'Gỏi Cuốn' WHERE id = 3");
            jdbcTemplate.execute("UPDATE products SET name = 'Phở Bò' WHERE id = 18");
            jdbcTemplate.execute("UPDATE products SET name = 'Bún Bò Huế' WHERE id = 19");
            jdbcTemplate.execute("UPDATE products SET name = 'Lẩu Thái Hải Sản' WHERE id = 25");
            jdbcTemplate.execute("UPDATE products SET name = 'Nem Rán' WHERE id = 28");
            jdbcTemplate.execute("UPDATE products SET name = 'Bánh Flan Caramel' WHERE id = 12");

            // 2. Update variant names to match menu variants exactly
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Sườn nướng' WHERE id = 1");
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Sườn + bì' WHERE id = 2");
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Cà phê đen' WHERE id = 9");
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Cà phê sữa' WHERE id = 10");
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Phở tái' WHERE id = 26");
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Phở chín' WHERE id = 27");
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Lẩu cho 2 người' WHERE id = 40");
            jdbcTemplate.execute("UPDATE product_variants SET name = 'Lẩu cho 4 người' WHERE id = 41");

            // 3. Insert missing products
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(31, 'Bánh Xèo', 'Bánh xèo giòn rụm, nhân tôm thịt, giá đỗ, ăn kèm rau sống và nước chấm.', 'https://images.unsplash.com/photo-1551024601-bec78aea704b?w=500', 1, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(32, 'Cá Kho Tộ', 'Cá basa kho tộ caramel, thịt ba chỉ, ăn với cơm trắng.', 'https://images.unsplash.com/photo-1547592165-e1d17fed6005?w=500', 1, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(33, 'Chả Giò', 'Chả giò chiên giòn, nhân tôm thịt, ăn kèm rau sống.', 'https://images.unsplash.com/photo-1544025162-d76694265947?w=500', 4, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(34, 'Rau Muống Xào Tỏi', 'Rau muống xào tỏi, món ăn kèm thanh mát.', 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500', 4, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(35, 'Đậu Hũ Chiên Sả Ớt', 'Đậu hũ chiên giòn, sốt sả ớt cay nồng.', 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=500', 4, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(36, 'Trà Đá', 'Trà đá truyền thống, refreshing sau bữa ăn.', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=500', 2, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(37, 'Nước Chanh Tươi', 'Nước chanh tươi vắt, đường, đá, giải khát.', 'https://images.unsplash.com/photo-1589733901241-5e5148685df5?w=500', 2, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(38, 'Bia Sài Gòn', 'Bia Sài Gòn đặc biệt, 330ml.', 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=500', 2, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(39, 'Chè Ba Màu', 'Chè ba màu: đậu xanh, đậu đỏ, thạch, nước cốt dừa.', 'https://images.unsplash.com/photo-1563729784474-d77dbb933a9e?w=500', 3, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES " +
                    "(40, 'Kem Chuối', 'Kem chuối dừa, đậu phộng rang, topping socola.', 'https://images.unsplash.com/photo-1541795795328-f073b763494e?w=500', 3, true, 'tenant-1') " +
                    "ON CONFLICT (id) DO NOTHING");

            // 4. Insert missing product variants
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(51, 18, 'Phở gầu', 95000.0, 40000.0, 'SKU-18-G', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(52, 31, 'Bánh Xèo', 65000.0, 30000.0, 'SKU-BX-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(53, 32, 'Cá Kho Tộ', 120000.0, 50000.0, 'SKU-CKT-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(54, 33, 'Chả Giò', 50000.0, 20000.0, 'SKU-CG-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(55, 34, 'Rau Muống Xào Tỏi', 40000.0, 15000.0, 'SKU-RMXT-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(56, 35, 'Đậu Hũ Chiên Sả Ớt', 45000.0, 18000.0, 'SKU-DHCSO-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(57, 36, 'Trà Đá', 15000.0, 2000.0, 'SKU-TD-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(58, 37, 'Nước Chanh Tươi', 25000.0, 8000.0, 'SKU-NCT-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(59, 38, 'Bia Sài Gòn', 25000.0, 12000.0, 'SKU-BSG-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(60, 38, 'Bia Hà Nội', 28000.0, 13000.0, 'SKU-BHN-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(61, 39, 'Chè Ba Màu', 35000.0, 15000.0, 'SKU-CBM-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.execute("INSERT INTO product_variants (id, product_id, name, price, original_price, sku, is_topping) VALUES " +
                    "(62, 40, 'Kem Chuối', 30000.0, 12000.0, 'SKU-KC-STD', false) " +
                    "ON CONFLICT (id) DO NOTHING");

            // 5. Reset sequence generators to avoid next value collisions
            jdbcTemplate.execute("SELECT setval('products_id_seq', (SELECT COALESCE(MAX(id), 0) FROM products))");
            jdbcTemplate.execute("SELECT setval('product_variants_id_seq', (SELECT COALESCE(MAX(id), 0) FROM product_variants))");

            log.info("====== Database product names and variants aligned successfully! ======");
        } catch (Exception e) {
            log.error("Failed to align database product names and variants", e);
        }

        if (roleRepository.count() > 0) {
            log.info("Database is already seeded.");
            return;
        }

        log.info("Starting database seeding from SQL files...");

        List<String> sqlFiles = Arrays.asList(
            "tenants.sql",
            "tenant_custom_pages.sql",
            "roles.sql",
            "branches.sql",
            "users.sql",
            "user_roles.sql",
            "employees.sql",
            "categories.sql",
            "products.sql",
            "product_variants.sql",
            "customers.sql",
            "rooms.sql",
            "tables.sql",
            "table_sessions.sql",
            "orders.sql",
            "order_details.sql",
            "shift_templates.sql",
            "employee_shift_assignments.sql",
            "employee_attendances.sql",
            "leave_requests.sql",
            "forgot_clock_requests.sql",
            "suppliers.sql",
            "inventory_items.sql",
            "branch_inventory.sql",
            "product_stocks.sql",
            "purchase_orders.sql",
            "purchase_order_items.sql",
            "goods_receipts.sql",
            "goods_receipt_items.sql",
            "promotions.sql",
            "promotion_usage.sql",
            "user_sessions.sql",
            "audit_logs.sql",
            "branch_transfers.sql",
            "branch_transfer_items.sql",
            "inventory_logs.sql",
            "loyalty_transactions.sql",
            "payroll_runs.sql",
            "payroll_entries.sql",
            "bookings.sql",
            "customer_reviews.sql",
            "menu.sql",
            "posts.sql",
            "events.sql",
            "floor_plans.sql",
            "floor_plan_objects.sql"
        );

        // Try to locate the sql directory.
        File sqlDir = new File("sql");
        if (!sqlDir.exists() || !sqlDir.isDirectory()) {
            sqlDir = new File("d:/swp/sql");
        }

        if (!sqlDir.exists() || !sqlDir.isDirectory()) {
            log.error("Could not find the SQL seeding directory at ./sql or d:/swp/sql. Seeding failed.");
            return;
        }

        for (String filename : sqlFiles) {
            File sqlFile = new File(sqlDir, filename);
            if (!sqlFile.exists()) {
                log.warn("SQL file not found: {}", sqlFile.getAbsolutePath());
                continue;
            }

            try {
                log.info("Executing SQL file: {}", filename);
                String sqlContent = Files.readString(sqlFile.toPath(), StandardCharsets.UTF_8);
                
                // For products.sql, bypass the DROP TABLE and CREATE TABLE DDL 
                // to preserve Hibernate-created foreign key constraints in other tables.
                if ("products.sql".equals(filename)) {
                    int insertIndex = sqlContent.indexOf("INSERT INTO products");
                    if (insertIndex != -1) {
                        sqlContent = sqlContent.substring(insertIndex);
                    }
                }
                
                jdbcTemplate.execute(sqlContent);
                log.info("Successfully executed SQL file: {}", filename);
            } catch (Exception e) {
                log.error("Error executing SQL file: " + filename, e);
                throw new RuntimeException("Seeding failed due to SQL execution error in file: " + filename, e);
            }
        }

        // Reset PostgreSQL sequence generators to prevent duplicate key errors due to explicit IDs in SQL files
        log.info("Resetting PostgreSQL sequences...");
        List<String> tablesToReset = Arrays.asList(
            "roles", "users", "employees", "categories", "products", "product_variants", "customers", 
            "rooms", "tables", "table_sessions", "orders", "order_details", "shift_templates", 
            "employee_shift_assignments", "employee_attendances", "leave_requests", "forgot_clock_requests", 
            "suppliers", "inventory_items", "branch_inventory", "product_stocks", "purchase_orders", 
            "purchase_order_items", "goods_receipts", "goods_receipt_items", "promotions", "promotion_usage", 
            "user_sessions", "audit_logs", "branch_transfers", "branch_transfer_items", "inventory_logs", 
            "loyalty_transactions", "payroll_runs", "payroll_entries",
            "bookings", "customer_reviews", "posts", "events",
            "floor_plans", "floor_plan_objects", "tenant_custom_pages"
        );

        for (String tableName : tablesToReset) {
            try {
                String seqName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')", String.class, tableName);
                if (seqName != null) {
                    jdbcTemplate.execute(
                        String.format("SELECT setval('%s', COALESCE((SELECT MAX(id) FROM %s), 1))", seqName, tableName)
                    );
                    log.debug("Reset sequence for table: {} to max ID", tableName);
                }
            } catch (Exception e) {
                log.warn("Could not reset sequence for table: {}. Might not have an auto-increment id.", tableName);
            }
        }
        // 1. Link floor plans to rooms
        log.info("Linking floor plans to rooms...");
        try {
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Lầu 1' AND branch_id = '01-2thang9') WHERE id = 1");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Khu Vực 6' AND branch_id = '01-2thang9') WHERE id = 2");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Sân Trước' AND branch_id = '11-NguyenHuuTho') WHERE id = 3");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Lầu 2' AND branch_id = '11-NguyenHuuTho') WHERE id = 4");
            jdbcTemplate.execute("UPDATE floor_plans SET room_id = (SELECT id FROM rooms WHERE name = 'Phòng Lạnh' AND branch_id = '21-HaiPhong') WHERE id = 5");
        } catch (Exception e) {
            log.error("Failed to link floor plans to rooms", e);
        }

        // 2. Sync seeded floor plan objects with TableEntities
        log.info("Optimizing and linking floor plan objects to existing tables...");
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
        } catch (Exception e) {
            log.error("Failed to link floor plan objects to existing tables", e);
        }

        log.info("Seeded Users list in Database:");
        userRepository.findAll().forEach(u -> log.info(" -> Email: [{}], Name: [{}], Password Hash: [{}]", u.getEmail(), u.getName(), u.getPassword()));

        log.info("Database seeding successfully completed.");
    }
}
