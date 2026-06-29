package web.restaurant.swp.modules.event.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "event_date_range", nullable = false)
    private String date;

    @Column(nullable = false)
    private String time;

    @Column(nullable = false)
    private String location;

    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName;

    @Column(nullable = false)
    private String tag;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(nullable = false)
    private String price;

    @Column(nullable = false)
    private String capacity;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "highlights_str", columnDefinition = "TEXT")
    private String highlights;

    @Column(name = "branch_id")
    private String branchId;

    @Column(name = "event_dates_str")
    private String eventDates;

    @Column(name = "created_by")
    private String createdBy;

    @Column(nullable = false)
    private String status;

    @Column(name = "commission_rate", nullable = false)
    private Double commissionRate;

    @Column(name = "is_using_system_web", nullable = false)
    private Boolean isUsingSystemWeb;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
