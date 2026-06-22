package web.restaurant.swp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import web.restaurant.swp.modules.auth.repository.RoleRepository;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (roleRepository.count() > 0) {
            log.info("Database is already seeded.");
            return;
        }

        log.info("Starting database seeding from SQL files...");

        List<String> sqlFiles = Arrays.asList(
            "tenants.sql",
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
            "payroll_entries.sql"
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
            "loyalty_transactions", "payroll_runs", "payroll_entries"
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

        log.info("Database seeding successfully completed.");
    }
}

