package web.restaurant.swp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DefaultPosLayoutResetter implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        Integer branchCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM branches", Integer.class);
        if (branchCount == null || branchCount == 0) {
            log.info("No branches found - skipping default POS layout reset.");
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
}
