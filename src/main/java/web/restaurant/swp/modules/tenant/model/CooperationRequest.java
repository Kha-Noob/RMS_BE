package web.restaurant.swp.modules.tenant.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import web.restaurant.swp.modules.auth.model.User;

@Entity
@Table(name = "cooperation_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CooperationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "domain")
    private String domain;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(name = "request_type", nullable = false)
    private String requestType; // EVENT_ONLY, APP_LIFETIME, APP_SUBSCRIPTION

    @Column(name = "payment_amount", nullable = false)
    private Double paymentAmount;

    @Builder.Default
    @Column(name = "status", nullable = false)
    private String status = "PAYMENT_PENDING"; // PAYMENT_PENDING, PENDING, APPROVED, REJECTED

    @Column(name = "order_code")
    private Long orderCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PAYMENT_PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
