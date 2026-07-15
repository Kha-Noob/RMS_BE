package web.restaurant.swp.modules.review.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(nullable = false)
    private Integer rating; // 1-5

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "branch_id", length = 36, nullable = false)
    private String branchId;

    @Builder.Default
    @Column(name = "source", length = 50, nullable = false, columnDefinition = "varchar(50) default 'SYSTEM'")
    private String source = "SYSTEM"; // SYSTEM, GOOGLE_MAPS, FACEBOOK, TRIPADVISOR

    @Column(name = "sentiment", length = 50)
    private String sentiment; // POSITIVE, NEGATIVE, NEUTRAL

    @Column(name = "response_en", columnDefinition = "TEXT")
    private String responseEn;

    @Column(name = "response_vi", columnDefinition = "TEXT")
    private String responseVi;

    @Builder.Default
    @Column(name = "is_approved", nullable = false, columnDefinition = "boolean default false")
    private Boolean isApproved = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
