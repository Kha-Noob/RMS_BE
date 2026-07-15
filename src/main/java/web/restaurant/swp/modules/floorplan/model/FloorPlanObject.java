package web.restaurant.swp.modules.floorplan.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "floor_plan_objects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloorPlanObject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_plan_id", nullable = false)
    private FloorPlan floorPlan;

    @Column(name = "table_id")
    private Long tableId;

    @Column(name = "object_type", nullable = false)
    private String objectType; // table, wall, door, window, toilet, cashier, kitchen, bar, stairs, text, decoration, blocked_area

    private String label;

    @Column(nullable = false)
    private Double x = 0.0;

    @Column(nullable = false)
    private Double y = 0.0;

    @Column(nullable = false)
    private Double width = 80.0;

    @Column(nullable = false)
    private Double height = 80.0;

    @Column(nullable = false)
    private Double rotation = 0.0;

    private String shape; // circle, rectangle, line, arc

    @Column(name = "z_index", nullable = false)
    private Integer zIndex = 0;

    @Column(name = "style_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> styleJson;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadataJson;

    @Column(name = "is_visible")
    private Boolean isVisible = true;

    @Column(name = "is_locked")
    private Boolean isLocked = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
