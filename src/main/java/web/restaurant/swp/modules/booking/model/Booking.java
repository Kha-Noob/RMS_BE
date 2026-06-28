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

    @Builder.Default
    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, CONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW

    @Builder.Default
    @Column(nullable = false)
    private String source = "OFFLINE"; // ONLINE, OFFLINE

    @Builder.Default
    @Column(nullable = false)
    private Boolean depositPaid = false;

    @Column(name = "branch_id", length = 36, nullable = false)
    private String branchId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "table_id")
    private Long tableId;

    @Column(name = "table_label")
    private String tableLabel;

    @Column(name = "dietary_notes")
    private String dietaryNotes;

    @Builder.Default
    @Column(name = "allergy_peanut")
    private Boolean allergyPeanut = false;

    @Builder.Default
    @Column(name = "allergy_gluten")
    private Boolean allergyGluten = false;

    @Column(name = "allergy_others")
    private String allergyOthers;

    @Column(name = "ordered_items_json", columnDefinition = "TEXT")
    private String orderedItemsJson;

    @Builder.Default
    @Column(name = "deposit_amount")
    private Double depositAmount = 0.0;

    @Column(name = "payment_method")
    private String paymentMethod; // QR_PAY, CARD, WALLET

    @Builder.Default
    @Column(name = "payment_status")
    private String paymentStatus = "PENDING"; // PENDING, PAID, REFUNDED

    @Builder.Default
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 120;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
