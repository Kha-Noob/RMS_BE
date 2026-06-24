package web.restaurant.swp.modules.booking.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "booking_time", nullable = false)
    private LocalDateTime bookingTime;

    @Column(nullable = false)
    private Integer guests;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, CONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW

    @Column(nullable = false)
    private String source = "OFFLINE"; // ONLINE, OFFLINE

    @Column(nullable = false)
    private Boolean depositPaid = false;

    @Column(name = "branch_id", length = 36, nullable = false)
    private String branchId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
