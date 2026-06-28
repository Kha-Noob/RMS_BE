package web.restaurant.swp.modules.review.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "post_reports",
    uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reporter_phone"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "reporter_phone", nullable = false)
    private String reporterPhone;

    @Column(name = "reason")
    private String reason;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
