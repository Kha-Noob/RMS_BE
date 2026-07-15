package web.restaurant.swp.modules.floorplan.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.pos.model.Room;

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber = 1;

    @Builder.Default
    @Column(nullable = false)
    private Integer width = 1200;

    @Builder.Default
    @Column(nullable = false)
    private Integer height = 800;

    @Column(name = "floor_diagram_image_url")
    private String floorDiagramImageUrl;

    @Column(name = "floor_diagram_image_key")
    private String floorDiagramImageKey;

    @Builder.Default
    @Column(name = "floor_diagram_fit_mode")
    private String floorDiagramFitMode = "contain"; // contain, cover, fill

    @Builder.Default
    @Column(name = "floor_diagram_x")
    private Double floorDiagramX = 0.0;

    @Builder.Default
    @Column(name = "floor_diagram_y")
    private Double floorDiagramY = 0.0;

    @Builder.Default
    @Column(name = "floor_diagram_width")
    private Double floorDiagramWidth = 100.0;

    @Builder.Default
    @Column(name = "floor_diagram_height")
    private Double floorDiagramHeight = 100.0;

    @Builder.Default
    @Column(name = "floor_diagram_scale")
    private Double floorDiagramScale = 1.0;

    @Builder.Default
    @Column(name = "floor_diagram_rotation")
    private Double floorDiagramRotation = 0.0;

    @Builder.Default
    @Column(name = "background_mode", nullable = false)
    private String backgroundMode = "DEFAULT_WOOD"; // DEFAULT_WOOD, DEFAULT_TILE, DEFAULT_GRID, CUSTOM_IMAGE

    @Column(name = "panorama_url")
    private String panoramaUrl;

    @Column(name = "panorama_key")
    private String panoramaKey;

    @Column(name = "panorama_type")
    private String panoramaType; // IMAGE_360, EXTERNAL_LINK

    @Builder.Default
    @Column(nullable = false)
    private String status = "draft"; // draft, published

    @Builder.Default
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
