package web.restaurant.swp.modules.promotion.model;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.pos.service.*;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;
import web.restaurant.swp.modules.inventory.service.*;
import web.restaurant.swp.modules.procurement.model.*;
import web.restaurant.swp.modules.procurement.repository.*;
import web.restaurant.swp.modules.procurement.service.*;
import web.restaurant.swp.modules.hr.model.*;
import web.restaurant.swp.modules.hr.repository.*;
import web.restaurant.swp.modules.hr.service.*;
import web.restaurant.swp.modules.loyalty.model.*;
import web.restaurant.swp.modules.loyalty.repository.*;
import web.restaurant.swp.modules.loyalty.service.*;
import web.restaurant.swp.modules.promotion.model.*;
import web.restaurant.swp.modules.promotion.repository.*;
import web.restaurant.swp.modules.promotion.service.*;
import web.restaurant.swp.modules.analytics.service.*;
import web.restaurant.swp.modules.branch.model.*;
import web.restaurant.swp.modules.branch.repository.*;
import web.restaurant.swp.modules.tenant.model.*;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "promotions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "promo_code")
    private String promoCode; // e.g. LITEFLOW10 (nullable for auto-applied campaigns)

    @Column(nullable = false)
    private String type; // PercentDiscount, FlatDiscount, Buy1Get1

    @Builder.Default
    @Column(name = "discount_value", nullable = false)
    private Double discountValue = 0.0; // percent or flat currency reduction

    @Builder.Default
    @Column(name = "min_order_value", nullable = false)
    private Double minOrderValue = 0.0;

    @Column(name = "max_usage_count")
    private Integer maxUsageCount;

    @Builder.Default
    @Column(name = "current_usage_count", nullable = false)
    private Integer currentUsageCount = 0;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // Optional B1G1 product triggers
    @Column(name = "trigger_product_id")
    private Long triggerProductId; // product ID to buy

    @Column(name = "reward_product_id")
    private Long rewardProductId;  // product ID to give free

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;
}
