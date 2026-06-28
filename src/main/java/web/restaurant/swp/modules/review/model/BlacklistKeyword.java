package web.restaurant.swp.modules.review.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "blacklist_keywords")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyword;
}
