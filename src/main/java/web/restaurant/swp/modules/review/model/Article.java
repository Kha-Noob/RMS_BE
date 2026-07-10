package web.restaurant.swp.modules.review.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "media_urls", columnDefinition = "TEXT")
    private String mediaUrls; // Semicolon-separated URLs

    @Column(nullable = false)
    private String status; // DRAFT, PUBLISHED, SCHEDULED, ARCHIVED

    @Builder.Default
    @Column(name = "is_global", nullable = false)
    private Boolean isGlobal = true;

    @Builder.Default
    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    @Column(name = "branch_id", length = 36)
    private String branchId; // Null if global

    @Column(name = "publish_at")
    private LocalDateTime publishAt; // Null if not scheduled

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", nullable = false)
    private String createdBy;
}
