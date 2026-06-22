package web.restaurant.swp.modules.promotion.service;

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


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionEngine {
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderDetailRepository orderDetailRepository;

    // REQ-PRO-02: B1G1 Item auto-insert
    @Transactional
    public void processBuyOneGetOne(Order order, List<OrderDetail> currentDetails) {
        List<Promotion> activePromos = promotionRepository.findByIsActiveTrue();
        LocalDate today = LocalDate.now();

        for (Promotion promo : activePromos) {
            if ("Buy1Get1".equalsIgnoreCase(promo.getType())) {
                // Check dates
                if (promo.getStartDate() != null && promo.getStartDate().isAfter(today)) continue;
                if (promo.getEndDate() != null && promo.getEndDate().isBefore(today)) continue;

                // Scan details for trigger product
                boolean hasTrigger = currentDetails.stream()
                        .anyMatch(d -> d.getVariant().getProduct().getId().equals(promo.getTriggerProductId()));

                if (hasTrigger) {
                    // Check if reward product already inserted
                    boolean hasReward = currentDetails.stream()
                            .anyMatch(d -> d.getVariant().getProduct().getId().equals(promo.getRewardProductId()) && d.getPrice() == 0.0);

                    if (!hasReward) {
                        // Retrieve the default variant of the reward product
                        List<ProductVariant> variants = productVariantRepository.findByProductId(promo.getRewardProductId());
                        if (!variants.isEmpty()) {
                            ProductVariant rewardVariant = variants.get(0);
                            OrderDetail freeDetail = OrderDetail.builder()
                                    .order(order)
                                    .variant(rewardVariant)
                                    .quantity(1)
                                    .status("SENT")
                                    .notes("Món tặng (B1G1): " + promo.getName())
                                    .price(0.0) // 0 VND
                                    .build();
                            orderDetailRepository.save(freeDetail);
                            log.info("[PROMOTION B1G1] Tự động chèn món tặng: {} vào Order #{}", rewardVariant.getProduct().getName(), order.getId());
                        }
                    }
                }
            }
        }
    }

    // REQ-PRO-03: Coupon checking
    public Promotion validateCoupon(String code, double cartValue) {
        Optional<Promotion> promoOpt = promotionRepository.findByPromoCodeAndIsActiveTrue(code);
        if (promoOpt.isEmpty()) {
            throw new RuntimeException("Mã coupon không tồn tại hoặc đã hết hạn.");
        }

        Promotion promo = promoOpt.get();
        LocalDate today = LocalDate.now();

        // Condition 1: Expiry date
        if (promo.getStartDate() != null && promo.getStartDate().isAfter(today)) {
            throw new RuntimeException("Chương trình chưa bắt đầu.");
        }
        if (promo.getEndDate() != null && promo.getEndDate().isBefore(today)) {
            throw new RuntimeException("Mã coupon đã hết hạn sử dụng.");
        }

        // Condition 2: Minimum cart value
        if (cartValue < promo.getMinOrderValue()) {
            throw new RuntimeException("Giá trị đơn hàng chưa đạt tối thiểu: " + promo.getMinOrderValue() + " VNĐ");
        }

        // Condition 3: Usage limit
        if (promo.getMaxUsageCount() != null && promo.getCurrentUsageCount() >= promo.getMaxUsageCount()) {
            throw new RuntimeException("Mã coupon đã hết lượt sử dụng.");
        }

        return promo;
    }

    // REQ-PRO-04: Apply best eligible promotion automatically
    @Transactional
    public double applyOptimalPromotion(Order order, List<OrderDetail> details) {
        List<Promotion> activePromotions = promotionRepository.findByIsActiveTrue();
        LocalDate today = LocalDate.now();

        double cartValue = details.stream().mapToDouble(d -> d.getPrice() * d.getQuantity()).sum();
        Promotion bestPromo = null;
        double maxDiscount = 0.0;

        for (Promotion promo : activePromotions) {
            // Basic validity check
            if (promo.getStartDate() != null && promo.getStartDate().isAfter(today)) continue;
            if (promo.getEndDate() != null && promo.getEndDate().isBefore(today)) continue;
            if (cartValue < promo.getMinOrderValue()) continue;
            if (promo.getMaxUsageCount() != null && promo.getCurrentUsageCount() >= promo.getMaxUsageCount()) continue;

            double discount = 0.0;
            if ("PercentDiscount".equalsIgnoreCase(promo.getType())) {
                discount = cartValue * (promo.getDiscountValue() / 100.0);
            } else if ("FlatDiscount".equalsIgnoreCase(promo.getType())) {
                discount = promo.getDiscountValue();
            }

            if (discount > maxDiscount) {
                maxDiscount = discount;
                bestPromo = promo;
            }
        }

        // Apply best promotion
        if (bestPromo != null && maxDiscount > 0.0) {
            // Prevent negative bills
            maxDiscount = Math.min(maxDiscount, cartValue);

            bestPromo.setCurrentUsageCount(bestPromo.getCurrentUsageCount() + 1);
            promotionRepository.save(bestPromo);

            PromotionUsage usage = PromotionUsage.builder()
                    .promotion(bestPromo)
                    .order(order)
                    .discountApplied(maxDiscount)
                    .usedDate(java.time.LocalDateTime.now())
                    .build();
            promotionUsageRepository.save(usage);

            log.info("[PROMOTION ENGINE] Áp dụng tự động tối ưu nhất: {} (Giảm {} VNĐ)", bestPromo.getName(), maxDiscount);
            return maxDiscount;
        }

        return 0.0;
    }
}
