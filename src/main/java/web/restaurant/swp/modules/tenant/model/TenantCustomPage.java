package web.restaurant.swp.modules.tenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_custom_pages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantCustomPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 36)
    private String tenantId;

    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "style_prompt", columnDefinition = "TEXT")
    private String stylePrompt;

    @Column(name = "primary_color", length = 10)
    @Builder.Default
    private String primaryColor = "#25439b";

    @Column(name = "secondary_color", length = 10)
    @Builder.Default
    private String secondaryColor = "#3b82f6";

    @Column(name = "background_color", length = 10)
    @Builder.Default
    private String backgroundColor = "#ffffff";

    @Column(name = "text_color", length = 10)
    @Builder.Default
    private String textColor = "#0f172a";

    @Column(name = "font_family", length = 50)
    @Builder.Default
    private String fontFamily = "Inter";

    @Column(name = "layout_style", length = 20)
    @Builder.Default
    private String layoutStyle = "modern";

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "show_posts")
    @Builder.Default
    private Boolean showPosts = true;

    @Column(name = "show_events")
    @Builder.Default
    private Boolean showEvents = true;

    @Column(name = "show_reviews")
    @Builder.Default
    private Boolean showReviews = true;

    @JsonProperty("published")
    @Column(name = "is_published")
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
