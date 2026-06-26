package web.restaurant.swp.modules.floorplan.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.branch.model.Branch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "floor_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloorPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private String name;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber = 1;

    @Column(nullable = false)
    private Integer width = 1200;

    @Column(nullable = false)
    private Integer height = 800;

    @Column(name = "background_image_url")
    private String backgroundImageUrl;

    @Column(name = "panorama_360_url")
    private String panorama360Url;

    @Column(nullable = false)
    private String status = "draft"; // draft, published

    @Column(name = "is_table_selection_enabled", nullable = false)
    private Boolean isTableSelectionEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "floorPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FloorPlanObject> floorPlanObjects = new ArrayList<>();
}
