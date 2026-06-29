package web.restaurant.swp.modules.review.model;

import web.restaurant.swp.modules.inventory.model.Product;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_branch_status_created", columnList = "branch_id, status, created_at DESC"),
    @Index(name = "idx_post_author_phone", columnList = "author_phone")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"taggedProducts"})
@EqualsAndHashCode(exclude = {"taggedProducts"})
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_phone")
    private String authorPhone;

    @Column(name = "user_id")
    private Long userId; // Optional link to authenticated user

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "media_urls", columnDefinition = "TEXT")
    private String mediaUrls; // Semicolon-separated image/video paths or URLs

    @Column(nullable = false)
    private Integer rating; // 1-5 stars

    @Column(name = "table_check_in")
    private String tableCheckIn; // e.g. "Tầng thượng - Bàn 12"

    @Column(name = "branch_id", length = 36)
    private String branchId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "post_products",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @Builder.Default
    private Set<Product> taggedProducts = new HashSet<>();

    @Builder.Default
    @Column(name = "likes_count", nullable = false)
    private Integer likesCount = 0;

    @Builder.Default
    @Column(name = "report_count", nullable = false)
    private Integer reportCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private String status = "PUBLIC"; // PUBLIC, HIDDEN, PENDING_MODERATION

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "is_edited", nullable = false)
    private Boolean isEdited = false;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "restaurant_reply", columnDefinition = "TEXT")
    private String restaurantReply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "reply_author_name")
    private String replyAuthorName;
}
